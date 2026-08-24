package io.github.mizar107.zapegruntime.boss.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class NinthFormStartRetryTest {

    @Test
    void unloadedArenaRetriesWithBackoffUntilAutomaticStartSucceeds() {
        NinthFormEncounterManager.RetryBook<UUID> retries =
                new NinthFormEncounterManager.RetryBook<>(4, 1_200L);
        UUID target = UUID.randomUUID();
        NinthFormEncounterManager.reconcileStartResult(
                retries,
                target,
                200L,
                NinthFormEncounterManager.StartStatus.ARENA_UNAVAILABLE);
        assertEquals(220L, retries.dueGameTick(target).orElseThrow());
        assertFalse(retries.due(219L).contains(target));
        assertTrue(retries.due(220L).contains(target));

        NinthFormEncounterManager.reconcileStartResult(
                retries,
                target,
                220L,
                NinthFormEncounterManager.StartStatus.ARENA_UNAVAILABLE);
        assertEquals(260L, retries.dueGameTick(target).orElseThrow());
        NinthFormEncounterManager.reconcileStartResult(
                retries,
                target,
                260L,
                NinthFormEncounterManager.StartStatus.STARTED);
        assertEquals(0, retries.size());
    }

    @Test
    void gatewayStoryAndDataAvailabilityRetryButPermanentStoryGateClears() {
        NinthFormEncounterManager.RetryBook<UUID> retries =
                new NinthFormEncounterManager.RetryBook<>(4, 1_200L);
        UUID target = UUID.randomUUID();
        for (NinthFormEncounterManager.StartStatus status : new NinthFormEncounterManager.StartStatus[] {
                NinthFormEncounterManager.StartStatus.GATEWAY_UNAVAILABLE,
                NinthFormEncounterManager.StartStatus.DATA_UNAVAILABLE,
                NinthFormEncounterManager.StartStatus.STORY_NOT_READY
        }) {
            NinthFormEncounterManager.reconcileStartResult(retries, target, 0L, status);
            assertTrue(retries.size() == 1, status.name());
        }
        NinthFormEncounterManager.reconcileStartResult(
                retries,
                target,
                20L,
                NinthFormEncounterManager.StartStatus.NOT_EXPECTED);
        assertEquals(0, retries.size());
    }

    @Test
    void durableOrExistingEncounterClearsStartRetry() {
        for (NinthFormEncounterManager.StartStatus status : new NinthFormEncounterManager.StartStatus[] {
                NinthFormEncounterManager.StartStatus.STARTED,
                NinthFormEncounterManager.StartStatus.SUSPENDED_FOR_RECOVERY,
                NinthFormEncounterManager.StartStatus.ALREADY_ACTIVE
        }) {
            NinthFormEncounterManager.RetryBook<UUID> retries =
                    new NinthFormEncounterManager.RetryBook<>(1, 1_200L);
            UUID target = UUID.randomUUID();
            retries.schedule(target, 0L);
            NinthFormEncounterManager.reconcileStartResult(retries, target, 20L, status);
            assertEquals(0, retries.size(), status.name());
        }
    }
}
