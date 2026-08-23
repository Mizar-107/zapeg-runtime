package io.github.mizar107.zapegruntime.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class TimelineSessionDataTest {

    private static final UUID SESSION = UUID.fromString(
            "30000000-0000-0000-0000-000000000003");
    private static final UUID TARGET = UUID.fromString(
            "40000000-0000-0000-0000-000000000004");
    private static final UUID OTHER_TARGET = UUID.fromString(
            "50000000-0000-0000-0000-000000000005");
    private static final ResourceLocation OVERWORLD = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "overworld");

    @Test
    void beginIsUuidScopedIdempotentAndConflictSafe() {
        TimelineDefinition definition = definition();
        TimelineSession proposed = TimelineSession.start(
                SESSION, TARGET, definition, OVERWORLD);
        TimelineSessionData data = new TimelineSessionData();

        assertEquals(
                TimelineSessionData.BeginStatus.STARTED,
                data.begin(proposed).status());
        assertEquals(
                TimelineSessionData.BeginStatus.IDEMPOTENT_ACTIVE,
                data.begin(proposed).status());
        assertEquals(
                TimelineSessionData.BeginStatus.TARGET_BUSY,
                data.begin(TimelineSession.start(
                                UUID.randomUUID(), TARGET, definition, OVERWORLD))
                        .status());
        assertEquals(
                TimelineSessionData.BeginStatus.SESSION_ID_CONFLICT,
                data.begin(TimelineSession.start(
                                SESSION, OTHER_TARGET, definition, OVERWORLD))
                        .status());
        assertEquals(1, data.activeSessions().size());
    }

    @Test
    void progressRoundTripPausesForRestartWithoutChangingSeedOrCursor() {
        TimelineDefinition definition = definition();
        TimelineSession started = TimelineSession.start(
                SESSION, TARGET, definition, OVERWORLD);
        TimelineSession progressed = started.withProgress(35, 1, 3, 40);
        TimelineSessionData data = new TimelineSessionData();
        data.begin(started);
        assertTrue(data.update(progressed));

        TimelineSessionData loaded = TimelineSessionData.load(
                data.save(new CompoundTag()));
        TimelineSession restored = loaded.activeFor(TARGET).orElseThrow();
        assertEquals(TimelineSession.Status.PAUSED_RESTART, restored.status());
        assertEquals(progressed.elapsedTicks(), restored.elapsedTicks());
        assertEquals(progressed.nextActionIndex(), restored.nextActionIndex());
        assertEquals(progressed.actionAttempts(), restored.actionAttempts());
        assertEquals(progressed.retryAtElapsedTick(), restored.retryAtElapsedTick());
        assertEquals(progressed.seed(), restored.seed());
        assertEquals(progressed.definitionFingerprint(), restored.definitionFingerprint());
    }

    @Test
    void terminalResultsNeverEvictAndBlockReplayAfterReload() {
        TimelineDefinition definition = definition();
        TimelineSession proposed = TimelineSession.start(
                SESSION, TARGET, definition, OVERWORLD);
        TimelineSessionData data = new TimelineSessionData();
        data.begin(proposed);
        assertEquals(
                TimelineSessionData.FinishStatus.RECORDED,
                data.finish(
                        SESSION,
                        TARGET,
                        new TimelineEngine.Terminal(
                                TimelineEngine.TerminalStatus.SUCCEEDED,
                                TimelineEngine.TerminalReason.COMPLETED)));
        assertTrue(data.activeFor(TARGET).isEmpty());
        assertEquals(1, data.terminalCount());

        TimelineSessionData loaded = TimelineSessionData.load(
                data.save(new CompoundTag()));
        assertEquals(
                TimelineSessionData.BeginStatus.IDEMPOTENT_TERMINAL,
                loaded.begin(proposed).status());
        TimelineSessionData.TerminalResult result = loaded.terminal(SESSION).orElseThrow();
        assertEquals(TimelineEngine.TerminalStatus.SUCCEEDED, result.status());
        assertEquals(TimelineEngine.TerminalReason.COMPLETED, result.reason());
    }

    @Test
    void staleUpdatesAndWrongFinishesCannotMutateAnotherSession() {
        TimelineDefinition definition = definition();
        TimelineSession proposed = TimelineSession.start(
                SESSION, TARGET, definition, OVERWORLD);
        TimelineSessionData data = new TimelineSessionData();
        data.begin(proposed);

        TimelineSession wrong = TimelineSession.start(
                UUID.randomUUID(), TARGET, definition, OVERWORLD);
        assertFalse(data.update(wrong));
        assertEquals(
                TimelineSessionData.FinishStatus.NOT_ACTIVE,
                data.finish(
                        UUID.randomUUID(),
                        TARGET,
                        new TimelineEngine.Terminal(
                                TimelineEngine.TerminalStatus.FAILED,
                                TimelineEngine.TerminalReason.ACTION_REJECTED)));
        assertTrue(data.activeFor(TARGET).isPresent());
    }

    @Test
    void unsupportedSchemaIsPreservedByteForByteAndRejectsMutation() {
        CompoundTag future = new CompoundTag();
        future.putInt("SchemaVersion", 99);
        future.putString("FuturePayload", "do-not-interpret");
        TimelineSessionData data = TimelineSessionData.load(future);

        assertFalse(data.supportsCurrentSchema());
        assertTrue(data.activeSessions().isEmpty());
        assertEquals(
                TimelineSessionData.BeginStatus.UNSUPPORTED_SCHEMA,
                data.begin(TimelineSession.start(
                                SESSION, TARGET, definition(), OVERWORLD))
                        .status());
        assertEquals(future.toString(), data.save(new CompoundTag()).toString());
    }

    @Test
    void anyCorruptRecordOrDuplicateTargetQuarantinesEntireRoot() {
        TimelineDefinition definition = definition();
        TimelineSession valid = TimelineSession.start(
                SESSION, TARGET, definition, OVERWORLD);
        TimelineSession duplicateTarget = TimelineSession.start(
                UUID.randomUUID(), TARGET, definition, OVERWORLD);
        TimelineSessionData encoded = new TimelineSessionData();
        encoded.begin(valid);
        CompoundTag root = encoded.save(new CompoundTag());
        ListTag active = root.getList("Active", net.minecraft.nbt.Tag.TAG_COMPOUND);
        TimelineSessionData duplicateEncoder = new TimelineSessionData();
        duplicateEncoder.begin(duplicateTarget);
        active.add(duplicateEncoder.save(new CompoundTag())
                .getList("Active", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0));
        CompoundTag corrupt = new CompoundTag();
        corrupt.putString("TimelineId", "not valid spaces");
        active.add(corrupt);
        root.put("Active", active);

        TimelineSessionData loaded = TimelineSessionData.load(root);
        assertFalse(loaded.supportsCurrentSchema());
        assertEquals(
                TimelineSessionData.DataHealth.CORRUPT,
                loaded.dataDiagnostic().health());
        assertEquals(
                TimelineSessionData.BeginStatus.CORRUPT_DATA,
                loaded.begin(TimelineSession.start(
                                UUID.randomUUID(), OTHER_TARGET, definition, OVERWORLD))
                        .status());
        assertEquals(root.toString(), loaded.save(new CompoundTag()).toString());
    }

    @Test
    void fullNonEvictingResultLedgerRefusesNewWork() {
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", TimelineSessionData.CURRENT_SCHEMA_VERSION);
        root.put("Active", new ListTag());
        ListTag terminal = new ListTag();
        for (int index = 1; index <= TimelineSessionData.MAX_TERMINAL_RESULTS; index++) {
            terminal.add(encodedTerminal(
                    new UUID(7L, index),
                    index == 1 ? TARGET : new UUID(8L, index)));
        }
        root.put("Terminal", terminal);
        TimelineSessionData data = TimelineSessionData.load(root);

        assertEquals(TimelineSessionData.MAX_TERMINAL_RESULTS, data.terminalCount());
        assertEquals(
                TimelineSessionData.BeginStatus.RESULT_CAPACITY_EXHAUSTED,
                data.begin(TimelineSession.start(
                                SESSION, TARGET, definition(), OVERWORLD))
                        .status());
    }

    @Test
    void missingWrongTypeMalformedDuplicateAndOverCapStructuresAreQuarantined() {
        List<CompoundTag> corruptRoots = new ArrayList<>();

        CompoundTag missingTerminal = healthyEmptyRoot();
        missingTerminal.remove("Terminal");
        corruptRoots.add(missingTerminal);

        CompoundTag extraRootField = healthyEmptyRoot();
        extraRootField.putInt("Unexpected", 1);
        corruptRoots.add(extraRootField);

        CompoundTag wrongTerminal = healthyEmptyRoot();
        wrongTerminal.putString("Terminal", "wrong");
        corruptRoots.add(wrongTerminal);

        CompoundTag wrongTerminalElementType = healthyEmptyRoot();
        ListTag stringElements = new ListTag();
        stringElements.add(net.minecraft.nbt.StringTag.valueOf("not-a-record"));
        wrongTerminalElementType.put("Terminal", stringElements);
        corruptRoots.add(wrongTerminalElementType);

        CompoundTag malformedTerminal = healthyEmptyRoot();
        ListTag malformed = new ListTag();
        malformed.add(new CompoundTag());
        malformedTerminal.put("Terminal", malformed);
        corruptRoots.add(malformedTerminal);

        CompoundTag duplicateTerminal = healthyEmptyRoot();
        ListTag duplicateList = new ListTag();
        CompoundTag repeated = encodedTerminal(new UUID(21L, 1L), TARGET);
        duplicateList.add(repeated);
        duplicateList.add(repeated.copy());
        duplicateTerminal.put("Terminal", duplicateList);
        corruptRoots.add(duplicateTerminal);

        CompoundTag wrongTerminalFieldType = healthyEmptyRoot();
        CompoundTag wrongTerminalRecord = encodedTerminal(new UUID(24L, 1L), TARGET);
        wrongTerminalRecord.putInt("Status", 1);
        ListTag wrongTerminalRecords = new ListTag();
        wrongTerminalRecords.add(wrongTerminalRecord);
        wrongTerminalFieldType.put("Terminal", wrongTerminalRecords);
        corruptRoots.add(wrongTerminalFieldType);

        TimelineSessionData activeEncoder = new TimelineSessionData();
        activeEncoder.begin(TimelineSession.start(
                SESSION, TARGET, definition(), OVERWORLD));
        CompoundTag wrongActiveFieldType = activeEncoder.save(new CompoundTag());
        wrongActiveFieldType.getList("Active", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0)
                .putInt("Seed", 7);
        corruptRoots.add(wrongActiveFieldType);

        CompoundTag duplicateActiveSession = activeEncoder.save(new CompoundTag());
        CompoundTag duplicateSessionRecord = duplicateActiveSession
                .getList("Active", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0)
                .copy();
        duplicateSessionRecord.putUUID("TargetId", OTHER_TARGET);
        duplicateActiveSession
                .getList("Active", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .add(duplicateSessionRecord);
        corruptRoots.add(duplicateActiveSession);

        CompoundTag crossConflict = activeEncoder.save(new CompoundTag());
        ListTag conflictingTerminal = new ListTag();
        conflictingTerminal.add(encodedTerminal(SESSION, TARGET));
        crossConflict.put("Terminal", conflictingTerminal);
        corruptRoots.add(crossConflict);

        CompoundTag overCap = healthyEmptyRoot();
        ListTag tooMany = new ListTag();
        for (long index = 1; index <= TimelineSessionData.MAX_TERMINAL_RESULTS + 1L; index++) {
            tooMany.add(encodedTerminal(new UUID(22L, index), new UUID(23L, index)));
        }
        overCap.put("Terminal", tooMany);
        corruptRoots.add(overCap);

        CompoundTag activeOverCap = healthyEmptyRoot();
        ListTag tooManyActive = new ListTag();
        CompoundTag activeTemplate = activeEncoder.save(new CompoundTag())
                .getList("Active", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0);
        for (long index = 1; index <= TimelineSessionData.MAX_ACTIVE_SESSIONS + 1L; index++) {
            CompoundTag record = activeTemplate.copy();
            record.putUUID("SessionId", new UUID(25L, index));
            record.putUUID("TargetId", new UUID(26L, index));
            tooManyActive.add(record);
        }
        activeOverCap.put("Active", tooManyActive);
        corruptRoots.add(activeOverCap);

        CompoundTag reservedCapacityOverCap = activeEncoder.save(new CompoundTag());
        ListTag fullTerminalReservation = new ListTag();
        for (long index = 1; index <= TimelineSessionData.MAX_TERMINAL_RESULTS; index++) {
            fullTerminalReservation.add(encodedTerminal(
                    new UUID(27L, index), new UUID(28L, index)));
        }
        reservedCapacityOverCap.put("Terminal", fullTerminalReservation);
        corruptRoots.add(reservedCapacityOverCap);

        for (CompoundTag root : corruptRoots) {
            TimelineSessionData data = TimelineSessionData.load(root);
            assertFalse(data.supportsCurrentSchema());
            assertEquals(
                    TimelineSessionData.DataHealth.CORRUPT,
                    data.dataDiagnostic().health());
            assertEquals(
                    TimelineSessionData.BeginStatus.CORRUPT_DATA,
                    data.begin(TimelineSession.start(
                                    SESSION, TARGET, definition(), OVERWORLD))
                            .status());
            assertEquals(root.toString(), data.save(new CompoundTag()).toString());
        }
    }

    @Test
    void definitionRelativeNbtCorruptionFailsDurablyWithoutDispatch() {
        TimelineDefinition definition = definition();
        List<Consumer<CompoundTag>> mutations = List.of(
                encoded -> encoded.putInt("NextAction", definition.actions().size()),
                encoded -> encoded.putLong("Seed", encoded.getLong("Seed") ^ 0x55AA55AAL),
                encoded -> encoded.putInt("Elapsed", definition.durationTicks() + 1),
                encoded -> encoded.putInt("NextAction", 1),
                encoded -> {
                    encoded.putInt("Attempts", 1);
                    encoded.putInt("RetryAt", 1);
                },
                encoded -> encoded.putInt("RetryAt", 1));

        for (Consumer<CompoundTag> mutation : mutations) {
            TimelineSessionData original = new TimelineSessionData();
            original.begin(TimelineSession.start(
                    SESSION, TARGET, definition, OVERWORLD));
            CompoundTag root = original.save(new CompoundTag());
            CompoundTag active = root.getList("Active", net.minecraft.nbt.Tag.TAG_COMPOUND)
                    .getCompound(0);
            mutation.accept(active);

            TimelineSessionData loaded = TimelineSessionData.load(root);
            assertTrue(loaded.supportsCurrentSchema());
            TimelineSession corrupt = loaded.activeFor(TARGET).orElseThrow();
            AtomicInteger dispatches = new AtomicInteger();
            TimelineEngine.Step step = TimelineEngine.tick(
                    corrupt,
                    definition,
                    new TimelineEngine.PlayerState(true, true, false, OVERWORLD),
                    ignored -> {
                        dispatches.incrementAndGet();
                        return TimelineEngine.ActionOutcome.APPLIED;
                    });
            assertTrue(step.finished());
            assertEquals(0, dispatches.get());
            assertEquals(
                    TimelineEngine.TerminalReason.STATE_CORRUPTION,
                    step.terminal().reason());
            assertEquals(
                    TimelineSessionData.FinishStatus.RECORDED,
                    loaded.finish(SESSION, TARGET, step.terminal()));

            TimelineSessionData reloaded = TimelineSessionData.load(
                    loaded.save(new CompoundTag()));
            TimelineSessionData.TerminalResult terminal =
                    reloaded.terminal(SESSION).orElseThrow();
            assertEquals(TimelineEngine.TerminalStatus.FAILED, terminal.status());
            assertEquals(TimelineEngine.TerminalReason.STATE_CORRUPTION, terminal.reason());
        }
    }

    @Test
    void identicalRetryAfterDimensionPauseKeepsOriginalCapturedState() {
        TimelineDefinition definition = definition();
        TimelineSessionData data = new TimelineSessionData();
        TimelineSession original = TimelineSession.start(
                SESSION, TARGET, definition, OVERWORLD).withProgress(9, 0, 0, 0)
                .withStatus(TimelineSession.Status.PAUSED_DIMENSION);
        data.begin(TimelineSession.start(SESSION, TARGET, definition, OVERWORLD));
        assertTrue(data.update(original));

        ResourceLocation nether = ResourceLocation.fromNamespaceAndPath(
                "minecraft", "the_nether");
        TimelineSession retry = TimelineSession.start(
                SESSION, TARGET, definition, nether);
        TimelineSessionData.BeginResult result = data.begin(retry);

        assertEquals(TimelineSessionData.BeginStatus.IDEMPOTENT_ACTIVE, result.status());
        assertEquals(OVERWORLD, result.active().boundDimension());
        assertEquals(9, result.active().elapsedTicks());
        assertEquals(TimelineSession.Status.PAUSED_DIMENSION, result.active().status());
    }

    private static CompoundTag encodedTerminal(UUID sessionId, UUID targetId) {
        CompoundTag result = new CompoundTag();
        result.putUUID("SessionId", sessionId);
        result.putUUID("TargetId", targetId);
        result.putString("TimelineId", TimelineDefinitionTest.id().toString());
        result.putString("Fingerprint", "a".repeat(64));
        result.putString("Status", TimelineEngine.TerminalStatus.FAILED.name());
        result.putString("Reason", TimelineEngine.TerminalReason.ACTION_REJECTED.name());
        return result;
    }

    private static CompoundTag healthyEmptyRoot() {
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", TimelineSessionData.CURRENT_SCHEMA_VERSION);
        root.put("Active", new ListTag());
        root.put("Terminal", new ListTag());
        return root;
    }

    private static TimelineDefinition definition() {
        return new TimelineDefinition(
                TimelineDefinitionTest.id(),
                100,
                TimelineDefinitionTest.policies(),
                List.of(
                        TimelineDefinitionTest.action("first", 1, 20),
                        TimelineDefinitionTest.action("second", 30, 60)));
    }
}
