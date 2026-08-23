package io.github.mizar107.zapegruntime.servant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
    }

    @Test
    void onlyLiveVictoryIsTerminalAndIsCreditedExactlyOnce() {
        ServantEncounterData data = new ServantEncounterData();
        ServantEncounter live = encounter(UUID.randomUUID(), TARGET, UUID.randomUUID(), false);
        data.begin(live);

        assertEquals(
                ServantEncounterData.FinishResult.IDENTITY_MISMATCH,
                data.finishVictory(live.encounterId(), UUID.randomUUID(), TARGET));
        assertEquals(
                ServantEncounterData.FinishResult.LIVE_CREDITED,
                data.finishVictory(live.encounterId(), live.servantId(), TARGET));
        assertEquals(
                ServantEncounterData.FinishResult.ALREADY_TERMINAL,
                data.finishVictory(live.encounterId(), live.servantId(), TARGET));
        assertEquals(1, data.victoryCount(TARGET));
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
                true);
        data.begin(original);

        CompoundTag saved = data.save(new CompoundTag());
        assertEquals(ServantEncounterData.CURRENT_SCHEMA_VERSION, saved.getInt("SchemaVersion"));
        ServantEncounterData loaded = ServantEncounterData.load(saved);
        assertTrue(loaded.supportsCurrentSchema());
        assertEquals(original, loaded.activeFor(TARGET).orElseThrow());
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
    void fullVictoryLedgerRefusesNewLiveWorkInsteadOfEvictingHistory() {
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", ServantEncounterData.CURRENT_SCHEMA_VERSION);
        root.put("Active", new ListTag());
        ListTag victories = new ListTag();
        for (long index = 1; index <= ServantEncounterData.MAX_LIVE_VICTORIES; index++) {
            CompoundTag victory = new CompoundTag();
            victory.putUUID("EncounterId", new UUID(1L, index));
            victory.putUUID("TargetId", TARGET);
            victories.add(victory);
        }
        root.put("LiveVictories", victories);

        ServantEncounterData loaded = ServantEncounterData.load(root);
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
}
