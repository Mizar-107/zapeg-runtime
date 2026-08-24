package io.github.mizar107.zapegruntime.boss.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.mizar107.zapegruntime.boss.api.NinthFormPhase;
import org.junit.jupiter.api.Test;

class NinthFormPhaseHandshakeTest {

    @Test
    void entityMustEnterInterludeBeforeFirstPhaseProof() {
        assertEquals(
                NinthFormPhaseHandshake.Action.TRANSITION_TO_INTERLUDE,
                NinthFormPhaseHandshake.next(NinthFormPhase.FIRST, 0b111, false));
        assertEquals(
                NinthFormPhaseHandshake.Action.EMIT_FIRST_PHASE_PROOF,
                NinthFormPhaseHandshake.next(NinthFormPhase.INTERLUDE, 0b111, false));
    }

    @Test
    void retryIsIdempotentAndRequiresTheCompleteWeakPointMask() {
        assertEquals(
                NinthFormPhaseHandshake.Action.NONE,
                NinthFormPhaseHandshake.next(NinthFormPhase.INTERLUDE, 0b111, true));
        assertEquals(
                NinthFormPhaseHandshake.Action.NONE,
                NinthFormPhaseHandshake.next(NinthFormPhase.FIRST, 0b011, false));
        assertEquals(
                NinthFormPhaseHandshake.Action.NONE,
                NinthFormPhaseHandshake.next(NinthFormPhase.FINAL, 0b111, false));
    }
}
