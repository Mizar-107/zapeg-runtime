package io.github.mizar107.zapegruntime.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
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
        TimelineSession progressed = started.withProgress(17, 1, 3, 25);
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
    void corruptRecordsAreIsolatedAndDuplicateTargetsFailClosed() {
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
        assertEquals(1, loaded.activeSessions().size());
        assertEquals(TARGET, loaded.activeSessions().iterator().next().targetId());
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
