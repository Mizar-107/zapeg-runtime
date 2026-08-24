package io.github.mizar107.zapegruntime.boss.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NinthFormProofRetryTest {

    @Test
    void terminalBarrierOwnerRetriesWithoutAnyActiveEncounter() {
        NinthFormEncounterManager.RetryBook<UUID> retries =
                new NinthFormEncounterManager.RetryBook<>(4, 1_200L);
        UUID defeatedTarget = UUID.randomUUID();
        NinthFormEncounterManager.reconcileProofResults(
                retries,
                defeatedTarget,
                100L,
                List.of(result(NinthFormProgressionSync.SyncStatus.NOT_READY)));
        assertEquals(1, retries.size());
        assertEquals(120L, retries.dueGameTick(defeatedTarget).orElseThrow());
        assertFalse(retries.due(119L).contains(defeatedTarget));
        assertTrue(retries.due(120L).contains(defeatedTarget));

        NinthFormEncounterManager.reconcileProofResults(
                retries,
                defeatedTarget,
                120L,
                List.of(result(NinthFormProgressionSync.SyncStatus.REFUSED)));
        assertEquals(160L, retries.dueGameTick(defeatedTarget).orElseThrow());
        NinthFormEncounterManager.reconcileProofResults(
                retries,
                defeatedTarget,
                160L,
                List.of(result(NinthFormProgressionSync.SyncStatus.APPLIED)));
        assertEquals(0, retries.size());
    }

    @Test
    void permanentEnvelopeMismatchClearsRetryAndTableRefusesOverflow() {
        NinthFormEncounterManager.RetryBook<UUID> retries =
                new NinthFormEncounterManager.RetryBook<>(1, 1_200L);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertTrue(retries.schedule(first, 0L));
        assertFalse(retries.schedule(second, 0L));
        NinthFormEncounterManager.reconcileProofResults(
                retries,
                first,
                20L,
                List.of(
                        result(NinthFormProgressionSync.SyncStatus.NOT_READY),
                        result(NinthFormProgressionSync.SyncStatus.ENVELOPE_MISMATCH)));
        assertEquals(0, retries.size());
    }

    @Test
    void exponentialDelayIsBoundedAtOneMinute() {
        NinthFormEncounterManager.RetryBook<UUID> retries =
                new NinthFormEncounterManager.RetryBook<>(1, 1_200L);
        UUID target = UUID.randomUUID();
        long now = 0L;
        for (int index = 0; index < 20; index++) {
            retries.schedule(target, now);
            long due = retries.dueGameTick(target).orElseThrow();
            assertTrue(due - now <= 1_200L);
            now = due;
        }
    }

    private static NinthFormProgressionSync.SyncResult result(
            NinthFormProgressionSync.SyncStatus status) {
        return new NinthFormProgressionSync.SyncResult(status, UUID.randomUUID(), "test");
    }
}
