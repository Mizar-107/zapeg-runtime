package io.github.mizar107.zapegruntime.servant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
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

        ServantEncounter retryWithUnusedEntity =
                encounter(event, TARGET, UUID.randomUUID(), false);
        ServantEncounterData.BeginResult retry = data.begin(retryWithUnusedEntity);
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
        assertEquals(1, data.activeEncounters().size());
    }

    @Test
    void liveVictoryIsCreditedExactlyOnceAndSurvivesReload() {
        ServantEncounterData data = new ServantEncounterData();
        ServantEncounter encounter =
                encounter(UUID.randomUUID(), TARGET, UUID.randomUUID(), false);
        data.begin(encounter);

        assertEquals(
                ServantEncounterData.FinishResult.IDENTITY_MISMATCH,
                data.finishVictory(encounter.encounterId(), UUID.randomUUID(), TARGET));
        assertEquals(0, data.victoryCount(TARGET));

        assertEquals(
                ServantEncounterData.FinishResult.LIVE_CREDITED,
                data.finishVictory(encounter.encounterId(), encounter.servantId(), TARGET));
        assertEquals(
                ServantEncounterData.FinishResult.ALREADY_TERMINAL,
                data.finishVictory(encounter.encounterId(), encounter.servantId(), TARGET));
        assertEquals(1, data.victoryCount(TARGET));

        ServantEncounterData loaded = ServantEncounterData.load(data.save(new CompoundTag()));
        assertEquals(1, loaded.victoryCount(TARGET));
        assertTrue(loaded.isTerminal(encounter.encounterId()));
        assertEquals(
                ServantEncounterData.BeginStatus.REPLAYED_TERMINAL,
                loaded.begin(encounter).status());
    }

    @Test
    void rehearsalCompletionNeverAdvancesVictoryCount() {
        ServantEncounterData data = new ServantEncounterData();
        ServantEncounter rehearsal =
                encounter(UUID.randomUUID(), TARGET, UUID.randomUUID(), true);
        data.begin(rehearsal);

        assertEquals(
                ServantEncounterData.FinishResult.REHEARSAL_COMPLETE,
                data.finishVictory(rehearsal.encounterId(), rehearsal.servantId(), TARGET));
        assertEquals(0, data.victoryCount(TARGET));
        assertTrue(data.isTerminal(rehearsal.encounterId()));
    }

    @Test
    void activeIdentityDeadlineAndLocationRoundTrip() {
        ServantEncounterData data = new ServantEncounterData();
        ServantEncounter original = new ServantEncounter(
                UUID.randomUUID(),
                TARGET,
                UUID.randomUUID(),
                "minecraft:the_nether",
                true,
                88_000L,
                -13,
                42);
        data.begin(original);

        ServantEncounterData loaded = ServantEncounterData.load(data.save(new CompoundTag()));
        assertEquals(original, loaded.activeFor(TARGET).orElseThrow());
        assertFalse(original.isExpired(87_999L));
        assertTrue(original.isExpired(88_000L));
        assertTrue(original.isExpired(100_000L));
    }

    @Test
    void spawnRollbackDoesNotConsumeEventId() {
        ServantEncounterData data = new ServantEncounterData();
        ServantEncounter encounter =
                encounter(UUID.randomUUID(), TARGET, UUID.randomUUID(), false);
        data.begin(encounter);
        assertTrue(data.rollbackSpawn(encounter.encounterId()));
        assertFalse(data.isTerminal(encounter.encounterId()));
        assertEquals(
                ServantEncounterData.BeginStatus.STARTED,
                data.begin(encounter).status());
    }

    @Test
    void lifetimeContractIsExactlyOneHundredTwentySeconds() {
        assertEquals(2_400, ServantEncounterManager.LIFETIME_TICKS);
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
                3,
                -2);
    }
}
