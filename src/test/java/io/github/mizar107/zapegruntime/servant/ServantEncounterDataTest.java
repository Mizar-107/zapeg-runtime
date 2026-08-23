package io.github.mizar107.zapegruntime.servant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class ServantEncounterDataTest {

    private static final UUID TARGET = UUID.fromString("7fcd4560-360f-45d6-930f-3146cb27d046");
    private static final UUID OTHER_TARGET = UUID.fromString("74955ccd-a992-432a-b76d-dd8d2c9f184a");

    @Test
    void beginIsIdempotentAndAllowsOnlyOneEncounterPerPlayer() {
        ServantEncounterData data = new ServantEncounterData();
        UUID event = UUID.randomUUID();
        ServantEncounter first = encounter(event, TARGET, UUID.randomUUID(), false);

        assertEquals(ServantEncounterData.BeginStatus.STARTED, data.begin(first).status());
        ServantEncounterData.BeginResult retry =
                data.begin(encounter(event, TARGET, UUID.randomUUID(), false));
        assertEquals(ServantEncounterData.BeginStatus.IDEMPOTENT, retry.status());
        assertEquals(first.servantId(), retry.encounter().servantId());
        assertEquals(
                ServantEncounterData.BeginStatus.TARGET_BUSY,
                data.begin(encounter(UUID.randomUUID(), TARGET, UUID.randomUUID(), false)).status());
        assertEquals(
                ServantEncounterData.BeginStatus.EVENT_ID_CONFLICT,
                data.begin(encounter(event, OTHER_TARGET, UUID.randomUUID(), false)).status());
        assertEquals(
                ServantEncounterData.BeginStatus.EVENT_ID_CONFLICT,
                data.begin(encounter(event, TARGET, UUID.randomUUID(), true)).status());
        assertEquals(
                ServantEncounterData.BeginStatus.EVENT_ID_CONFLICT,
                data.begin(encounter(
                                event,
                                TARGET,
                                UUID.randomUUID(),
                                false,
                                ServantArchetype.BINDER))
                        .status());
    }

    @Test
    void onlyLiveVictoryIsTerminalAndIsCreditedExactlyOnce() {
        ServantEncounterData data = new ServantEncounterData();
        ServantEncounter live = encounter(UUID.randomUUID(), TARGET, UUID.randomUUID(), false);
        data.begin(live);

        assertEquals(
                ServantEncounterData.FinishResult.IDENTITY_MISMATCH,
                data.finishVictory(
                        live.encounterId(),
                        UUID.randomUUID(),
                        TARGET,
                        ServantArchetype.STALKER));
        assertEquals(
                ServantEncounterData.FinishResult.IDENTITY_MISMATCH,
                data.finishVictory(
                        live.encounterId(),
                        live.servantId(),
                        TARGET,
                        ServantArchetype.HERALD));
        assertEquals(
                ServantEncounterData.FinishResult.LIVE_CREDITED,
                data.finishVictory(live.encounterId(), live.servantId(), TARGET));
        assertEquals(
                ServantEncounterData.FinishResult.ALREADY_TERMINAL,
                data.finishVictory(live.encounterId(), live.servantId(), TARGET));
        assertEquals(1, data.victoryCount(TARGET));
        assertEquals(1, data.victoryCount(TARGET, ServantArchetype.STALKER));
        assertEquals(0, data.victoryCount(TARGET, ServantArchetype.HERALD));
        assertEquals(
                ServantArchetype.STALKER,
                data.liveVictory(live.encounterId()).orElseThrow().archetype());
        assertTrue(data.isLiveVictory(live.encounterId()));
        assertEquals(
                ServantEncounterData.BeginStatus.REPLAYED_LIVE_VICTORY,
                data.begin(live).status());
    }

    @Test
    void closeAndRehearsalCompletionLeaveEventRetryable() {
        ServantEncounterData data = new ServantEncounterData();
        UUID event = UUID.randomUUID();
        ServantEncounter first = encounter(event, TARGET, UUID.randomUUID(), false);
        data.begin(first);
        assertTrue(data.close(event));
        assertFalse(data.isLiveVictory(event));

        ServantEncounter retry = encounter(event, TARGET, UUID.randomUUID(), true);
        assertEquals(ServantEncounterData.BeginStatus.STARTED, data.begin(retry).status());
        assertEquals(
                ServantEncounterData.FinishResult.REHEARSAL_COMPLETE,
                data.finishVictory(event, retry.servantId(), TARGET));
        assertFalse(data.isLiveVictory(event));

        ServantEncounter liveRetry = encounter(event, TARGET, UUID.randomUUID(), false);
        assertEquals(ServantEncounterData.BeginStatus.STARTED, data.begin(liveRetry).status());
    }

    @Test
    void recoveryClaimIsPersistedAndCanOnlyBeTakenOnce() {
        ServantEncounterData data = new ServantEncounterData();
        ServantEncounter active = encounter(UUID.randomUUID(), TARGET, UUID.randomUUID(), false);
        data.begin(active);

        assertEquals(
                ServantEncounterData.RecoveryClaim.CLAIMED,
                data.claimRecovery(active.encounterId()));
        assertEquals(
                ServantEncounterData.RecoveryClaim.ALREADY_ATTEMPTED,
                data.claimRecovery(active.encounterId()));

        UUID replacementId = UUID.randomUUID();
        assertTrue(data.replaceRecoveredEntity(active.encounterId(), replacementId));

        ServantEncounterData loaded = ServantEncounterData.load(data.save(new CompoundTag()));
        assertTrue(loaded.activeFor(TARGET).orElseThrow().recoveryAttempted());
        assertEquals(replacementId, loaded.activeFor(TARGET).orElseThrow().servantId());
        assertEquals(
                ServantEncounterData.RecoveryClaim.ALREADY_ATTEMPTED,
                loaded.claimRecovery(active.encounterId()));
    }

    @Test
    void currentSchemaRoundTripsAllValidatedFields() {
        ServantEncounterData data = new ServantEncounterData();
        ServantEncounter original = new ServantEncounter(
                UUID.randomUUID(),
                TARGET,
                UUID.randomUUID(),
                "minecraft:the_nether",
                true,
                88_000L,
                true,
                ServantArchetype.BINDER);
        data.begin(original);

        CompoundTag saved = data.save(new CompoundTag());
        assertEquals(ServantEncounterData.CURRENT_SCHEMA_VERSION, saved.getInt("SchemaVersion"));
        ServantEncounterData loaded = ServantEncounterData.load(saved);
        assertTrue(loaded.supportsCurrentSchema());
        assertEquals(original, loaded.activeFor(TARGET).orElseThrow());
        assertEquals(ServantArchetype.BINDER, loaded.activeFor(TARGET).orElseThrow().archetype());
        assertFalse(original.isExpired(87_999L));
        assertTrue(original.isExpired(88_000L));
    }

    @Test
    void unsupportedFutureSchemaIsRejectedAndPreservedUnchanged() {
        CompoundTag future = new CompoundTag();
        future.putInt("SchemaVersion", ServantEncounterData.CURRENT_SCHEMA_VERSION + 1);
        future.putString("FutureField", "do-not-destroy");
        ServantEncounterData loaded = ServantEncounterData.load(future);

        assertFalse(loaded.supportsCurrentSchema());
        assertEquals(
                ServantEncounterData.BeginStatus.UNSUPPORTED_SCHEMA,
                loaded.begin(encounter(UUID.randomUUID(), TARGET, UUID.randomUUID(), false)).status());
        CompoundTag preserved = loaded.save(new CompoundTag());
        assertEquals(future, preserved);
        assertEquals("do-not-destroy", preserved.getString("FutureField"));
    }

    @Test
    void malformedAndDuplicateRecordsAreIsolated() {
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", ServantEncounterData.CURRENT_SCHEMA_VERSION);
        ListTag active = new ListTag();
        ServantEncounter valid = encounter(UUID.randomUUID(), TARGET, UUID.randomUUID(), false);
        active.add(valid.save());

        CompoundTag badDimension = encounter(
                        UUID.randomUUID(), OTHER_TARGET, UUID.randomUUID(), false)
                .save();
        badDimension.putString("Dimension", "Not A Resource Location");
        active.add(badDimension);

        CompoundTag badDeadline = encounter(
                        UUID.randomUUID(), OTHER_TARGET, UUID.randomUUID(), false)
                .save();
        badDeadline.putLong("Deadline", -1L);
        active.add(badDeadline);

        CompoundTag badArchetype = encounter(
                        UUID.randomUUID(), OTHER_TARGET, UUID.randomUUID(), false)
                .save();
        badArchetype.putString("Archetype", "not_a_servant");
        active.add(badArchetype);

        ServantEncounter duplicateEntity = new ServantEncounter(
                UUID.randomUUID(),
                OTHER_TARGET,
                valid.servantId(),
                "minecraft:overworld",
                false,
                42_000L,
                false);
        active.add(duplicateEntity.save());
        root.put("Active", active);
        root.put("LiveVictories", new ListTag());

        ServantEncounterData loaded = ServantEncounterData.load(root);
        assertEquals(1, loaded.activeEncounters().size());
        assertEquals(valid, loaded.activeFor(TARGET).orElseThrow());
    }

    @Test
    void spawnRollbackDoesNotConsumeEventId() {
        ServantEncounterData data = new ServantEncounterData();
        ServantEncounter active = encounter(UUID.randomUUID(), TARGET, UUID.randomUUID(), false);
        data.begin(active);
        assertTrue(data.rollbackSpawn(active.encounterId()));
        assertFalse(data.isLiveVictory(active.encounterId()));
        assertEquals(ServantEncounterData.BeginStatus.STARTED, data.begin(active).status());
    }

    @Test
    void lifetimeAndNonEvictingVictoryCapAreExplicit() {
        assertEquals(2_400, ServantEncounterManager.LIFETIME_TICKS);
        assertEquals(4_096, ServantEncounterData.MAX_LIVE_VICTORIES);
    }

    @Test
    void durableVictorySnapshotIsStableImmutableAndReplayableAfterReload() {
        ServantEncounterData data = new ServantEncounterData();
        UUID laterEvent = new UUID(0L, 2L);
        UUID earlierEvent = new UUID(0L, 1L);
        ServantEncounter later = encounter(
                laterEvent,
                TARGET,
                UUID.randomUUID(),
                false,
                ServantArchetype.BINDER);
        ServantEncounter earlier = encounter(
                earlierEvent,
                OTHER_TARGET,
                UUID.randomUUID(),
                false,
                ServantArchetype.HERALD);
        data.begin(later);
        data.finishVictory(
                later.encounterId(),
                later.servantId(),
                later.targetId(),
                later.archetype());
        data.begin(earlier);
        data.finishVictory(
                earlier.encounterId(),
                earlier.servantId(),
                earlier.targetId(),
                earlier.archetype());

        List<ServantEncounterData.LiveVictory> expected = List.of(
                new ServantEncounterData.LiveVictory(
                        earlierEvent, OTHER_TARGET, ServantArchetype.HERALD),
                new ServantEncounterData.LiveVictory(
                        laterEvent, TARGET, ServantArchetype.BINDER));
        List<ServantEncounterData.LiveVictory> beforeSave = data.liveVictories();
        assertEquals(expected, beforeSave);
        assertThrows(UnsupportedOperationException.class, () -> beforeSave.add(expected.get(0)));

        ServantEncounterData reloaded =
                ServantEncounterData.load(data.save(new CompoundTag()));
        assertEquals(expected, reloaded.liveVictories());

        Set<UUID> idempotentIntegration = new HashSet<>();
        reloaded.liveVictories().forEach(victory ->
                idempotentIntegration.add(victory.encounterId()));
        reloaded.liveVictories().forEach(victory ->
                idempotentIntegration.add(victory.encounterId()));
        assertEquals(Set.of(earlierEvent, laterEvent), idempotentIntegration);
    }

    @Test
    void versionOneDataMigratesLosslesslyToTheDefaultStalkerArchetype() {
        UUID event = UUID.randomUUID();
        UUID victoryEvent = UUID.randomUUID();
        UUID entity = UUID.randomUUID();
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", 1);

        CompoundTag legacyActive = new CompoundTag();
        legacyActive.putUUID("EncounterId", event);
        legacyActive.putUUID("TargetId", TARGET);
        legacyActive.putUUID("ServantId", entity);
        legacyActive.putString("Dimension", "minecraft:overworld");
        legacyActive.putBoolean("Rehearsal", false);
        legacyActive.putLong("Deadline", 42_000L);
        legacyActive.putBoolean("RecoveryAttempted", false);
        ListTag active = new ListTag();
        active.add(legacyActive);
        root.put("Active", active);
        CompoundTag legacyVictory = new CompoundTag();
        legacyVictory.putUUID("EncounterId", victoryEvent);
        legacyVictory.putUUID("TargetId", OTHER_TARGET);
        ListTag victories = new ListTag();
        victories.add(legacyVictory);
        root.put("LiveVictories", victories);

        ServantEncounterData loaded = ServantEncounterData.load(root);
        assertTrue(loaded.supportsCurrentSchema());
        assertEquals(
                ServantArchetype.STALKER,
                loaded.activeFor(TARGET).orElseThrow().archetype());
        assertEquals(
                ServantArchetype.STALKER,
                loaded.liveVictory(victoryEvent).orElseThrow().archetype());

        CompoundTag migrated = loaded.save(new CompoundTag());
        assertEquals(ServantEncounterData.CURRENT_SCHEMA_VERSION, migrated.getInt("SchemaVersion"));
        assertEquals(
                "stalker",
                migrated.getList("Active", Tag.TAG_COMPOUND)
                        .getCompound(0)
                        .getString("Archetype"));
        assertEquals(
                "stalker",
                migrated.getList("LiveVictories", Tag.TAG_COMPOUND)
                        .getCompound(0)
                        .getString("Archetype"));
    }

    @Test
    void fullVictoryLedgerRefusesNewLiveWorkInsteadOfEvictingHistory() {
        ServantEncounterData loaded = ServantEncounterData.load(
                rootWithVictories(ServantEncounterData.MAX_LIVE_VICTORIES));
        assertEquals(ServantEncounterData.MAX_LIVE_VICTORIES, loaded.terminalCount());
        assertEquals(
                ServantEncounterData.BeginStatus.VICTORY_CAPACITY_EXHAUSTED,
                loaded.begin(encounter(UUID.randomUUID(), OTHER_TARGET, UUID.randomUUID(), false))
                        .status());
        assertEquals(
                ServantEncounterData.BeginStatus.STARTED,
                loaded.begin(encounter(UUID.randomUUID(), OTHER_TARGET, UUID.randomUUID(), true))
                        .status());
    }

    @Test
    void activeLiveEncountersReserveTerminalCapacityBeforeCompletion() {
        ServantEncounterData data = ServantEncounterData.load(
                rootWithVictories(ServantEncounterData.MAX_LIVE_VICTORIES - 2));
        UUID secondTarget = UUID.fromString("c0a102bc-70e6-49b5-b10b-1e00c6d133a3");
        UUID thirdTarget = UUID.fromString("4c23e650-bd08-4025-b527-3fd82ca3d4e3");
        ServantEncounter first = encounter(
                new UUID(2L, 1L), OTHER_TARGET, UUID.randomUUID(), false);
        ServantEncounter second = encounter(
                new UUID(2L, 2L), secondTarget, UUID.randomUUID(), false);

        assertEquals(ServantEncounterData.BeginStatus.STARTED, data.begin(first).status());
        assertEquals(ServantEncounterData.BeginStatus.STARTED, data.begin(second).status());
        data = ServantEncounterData.load(data.save(new CompoundTag()));
        assertEquals(ServantEncounterData.MAX_LIVE_VICTORIES, data.reservedLiveSlots());
        assertEquals(
                ServantEncounterData.BeginStatus.VICTORY_CAPACITY_EXHAUSTED,
                data.begin(encounter(new UUID(2L, 3L), thirdTarget, UUID.randomUUID(), false))
                        .status());
        assertTrue(data.activeFor(first.targetId()).isPresent());
        assertTrue(data.activeFor(second.targetId()).isPresent());

        assertEquals(
                ServantEncounterData.FinishResult.LIVE_CREDITED,
                data.finishVictory(first.encounterId(), first.servantId(), first.targetId()));
        assertEquals(
                ServantEncounterData.FinishResult.LIVE_CREDITED,
                data.finishVictory(second.encounterId(), second.servantId(), second.targetId()));
        assertEquals(ServantEncounterData.MAX_LIVE_VICTORIES, data.terminalCount());
        assertEquals(ServantEncounterData.MAX_LIVE_VICTORIES, data.reservedLiveSlots());
        assertTrue(data.activeFor(first.targetId()).isEmpty());
        assertTrue(data.activeFor(second.targetId()).isEmpty());

        assertEquals(
                ServantEncounterData.BeginStatus.STARTED,
                data.begin(encounter(new UUID(2L, 4L), thirdTarget, UUID.randomUUID(), true))
                        .status());
    }

    private static CompoundTag rootWithVictories(int count) {
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", ServantEncounterData.CURRENT_SCHEMA_VERSION);
        root.put("Active", new ListTag());
        ListTag victories = new ListTag();
        for (long index = 1; index <= count; index++) {
            CompoundTag victory = new CompoundTag();
            victory.putUUID("EncounterId", new UUID(1L, index));
            victory.putUUID("TargetId", TARGET);
            victory.putString("Archetype", ServantArchetype.STALKER.id());
            victories.add(victory);
        }
        root.put("LiveVictories", victories);
        return root;
    }

    private static ServantEncounter encounter(
            UUID encounterId,
            UUID targetId,
            UUID servantId,
            boolean rehearsal) {
        return new ServantEncounter(
                encounterId,
                targetId,
                servantId,
                "minecraft:overworld",
                rehearsal,
                42_000L,
                false);
    }

    private static ServantEncounter encounter(
            UUID encounterId,
            UUID targetId,
            UUID servantId,
            boolean rehearsal,
            ServantArchetype archetype) {
        return new ServantEncounter(
                encounterId,
                targetId,
                servantId,
                "minecraft:overworld",
                rehearsal,
                42_000L,
                false,
                archetype);
    }
}
