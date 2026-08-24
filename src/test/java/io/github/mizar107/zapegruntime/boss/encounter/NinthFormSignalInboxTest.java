package io.github.mizar107.zapegruntime.boss.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mizar107.zapegruntime.boss.api.NinthFormCombatSignal;
import io.github.mizar107.zapegruntime.boss.api.NinthFormIdentity;
import io.github.mizar107.zapegruntime.boss.api.NinthFormPhase;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NinthFormSignalInboxTest {

    @Test
    void duplicateSignalsCoalesceAndDrainInInsertionOrder() {
        NinthFormSignalInbox inbox = new NinthFormSignalInbox(2);
        NinthFormCombatSignal first = signal(
                NinthFormCombatSignal.Kind.PHASE_COMPLETED, NinthFormPhase.FIRST);
        NinthFormCombatSignal second = signal(
                NinthFormCombatSignal.Kind.DEFEATED, NinthFormPhase.BANISHED);

        assertTrue(inbox.offer(first));
        assertTrue(inbox.offer(first));
        assertTrue(inbox.offer(second));
        assertEquals(2, inbox.size());
        assertEquals(java.util.List.of(first, second), inbox.drain());
        assertEquals(0, inbox.size());
    }

    @Test
    void terminalProofMayEvictSuspensionButNeverAnotherProof() {
        NinthFormSignalInbox inbox = new NinthFormSignalInbox(1);
        NinthFormCombatSignal suspended = signal(
                NinthFormCombatSignal.Kind.SUSPENDED, NinthFormPhase.FIRST);
        NinthFormCombatSignal defeated = signal(
                NinthFormCombatSignal.Kind.DEFEATED, NinthFormPhase.BANISHED);
        NinthFormCombatSignal phase = signal(
                NinthFormCombatSignal.Kind.PHASE_COMPLETED, NinthFormPhase.FIRST);

        assertTrue(inbox.offer(suspended));
        assertTrue(inbox.offer(defeated));
        assertEquals(java.util.List.of(defeated), inbox.drain());
        assertTrue(inbox.offer(defeated));
        assertFalse(inbox.offer(phase));
        assertEquals(java.util.List.of(defeated), inbox.drain());
    }

    private static NinthFormCombatSignal signal(
            NinthFormCombatSignal.Kind kind, NinthFormPhase phase) {
        UUID target = UUID.randomUUID();
        NinthFormIdentity identity =
                new NinthFormIdentity(UUID.randomUUID(), target, 0, false);
        return new NinthFormCombatSignal(
                kind,
                identity,
                UUID.randomUUID(),
                phase,
                target,
                10L);
    }
}
