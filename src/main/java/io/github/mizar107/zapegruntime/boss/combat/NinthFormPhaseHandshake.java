package io.github.mizar107.zapegruntime.boss.combat;

import io.github.mizar107.zapegruntime.boss.api.NinthFormPhase;
import java.util.Objects;

/** Pure phase-one entity/proof handshake policy. */
final class NinthFormPhaseHandshake {

    private NinthFormPhaseHandshake() {}

    static Action next(NinthFormPhase phase, int brokenMask, boolean proofEmitted) {
        Objects.requireNonNull(phase, "phase");
        if (brokenMask != 0b111 || proofEmitted) {
            return Action.NONE;
        }
        return switch (phase) {
            case FIRST -> Action.TRANSITION_TO_INTERLUDE;
            case INTERLUDE -> Action.EMIT_FIRST_PHASE_PROOF;
            default -> Action.NONE;
        };
    }

    enum Action {
        NONE,
        TRANSITION_TO_INTERLUDE,
        EMIT_FIRST_PHASE_PROOF
    }
}
