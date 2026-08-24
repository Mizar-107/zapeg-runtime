package io.github.mizar107.zapegruntime.boss.api;

import java.util.Objects;
import java.util.UUID;

/** Server-authored combat evidence; consumers still validate it against durable state. */
public record NinthFormCombatSignal(
        Kind kind,
        NinthFormIdentity identity,
        UUID entityId,
        NinthFormPhase phase,
        UUID creditedPlayerId,
        long emittedGameTick) {

    public NinthFormCombatSignal {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(identity, "identity");
        identity.validateEntityId(entityId);
        Objects.requireNonNull(phase, "phase");
        requireNonNil(creditedPlayerId, "creditedPlayerId");
        if (!identity.targetId().equals(creditedPlayerId)) {
            throw new IllegalArgumentException("combat credit must remain bound to the encounter target");
        }
        if (emittedGameTick < 0L) {
            throw new IllegalArgumentException("emittedGameTick cannot be negative");
        }
        if (kind == Kind.PHASE_COMPLETED && phase != NinthFormPhase.FIRST) {
            throw new IllegalArgumentException("only phase one emits PHASE_COMPLETED");
        }
        if (kind == Kind.DEFEATED && phase != NinthFormPhase.BANISHED) {
            throw new IllegalArgumentException("DEFEATED signal requires the terminal phase");
        }
        if (kind == Kind.SUSPENDED && phase.terminal()) {
            throw new IllegalArgumentException("a defeated entity cannot emit SUSPENDED");
        }
    }

    private static void requireNonNil(UUID value, String name) {
        Objects.requireNonNull(value, name);
        if (value.getMostSignificantBits() == 0L && value.getLeastSignificantBits() == 0L) {
            throw new IllegalArgumentException(name + " cannot be the nil UUID");
        }
    }

    public enum Kind {
        PHASE_COMPLETED,
        DEFEATED,
        SUSPENDED
    }
}
