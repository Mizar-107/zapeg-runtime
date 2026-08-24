package io.github.mizar107.zapegruntime.boss.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The only encounter-layer authority for touching a Ninth Form entity.
 *
 * <p>Implementations must refuse unloaded positions and must never request or
 * create chunk tickets. All calls are server-thread calls.</p>
 */
public interface NinthFormEntityGateway {

    SpawnResult spawnLoaded(SpawnRequest request);

    Optional<NinthFormCombatSnapshot> observeLoaded(NinthFormIdentity identity, UUID entityId);

    ControlResult transitionLoaded(
            NinthFormIdentity identity, UUID entityId, NinthFormPhase expected, NinthFormPhase next);

    ControlResult suspendLoaded(NinthFormIdentity identity, UUID entityId);

    ControlResult discardLoaded(NinthFormIdentity identity, UUID entityId);

    record SpawnRequest(
            NinthFormIdentity identity,
            UUID entityId,
            String dimensionId,
            double x,
            double y,
            double z,
            NinthFormPhase phase,
            int participantCount,
            double healthScale,
            double damageScale,
            NinthFormCombatSnapshot.CombatState combatState,
            NinthFormCombatSnapshot.VitalState vitalState) {

        public SpawnRequest {
            Objects.requireNonNull(identity, "identity");
            identity.validateEntityId(entityId);
            Objects.requireNonNull(dimensionId, "dimensionId");
            if (dimensionId.isBlank() || dimensionId.length() > 255) {
                throw new IllegalArgumentException("dimensionId must contain 1..255 characters");
            }
            requireCoordinate(x, "x");
            if (!Double.isFinite(y) || y < -2048.0D || y > 2048.0D) {
                throw new IllegalArgumentException("y is outside the supported world range");
            }
            requireCoordinate(z, "z");
            Objects.requireNonNull(phase, "phase");
            if (phase.terminal()) {
                throw new IllegalArgumentException("cannot spawn a defeated combat phase");
            }
            if (participantCount < 1 || participantCount > 8) {
                throw new IllegalArgumentException("participantCount must be in [1, 8]");
            }
            requireScale(healthScale, "healthScale");
            requireScale(damageScale, "damageScale");
            Objects.requireNonNull(combatState, "combatState");
            Objects.requireNonNull(vitalState, "vitalState");
            vitalState.validateMask(combatState.brokenPointMask());
        }

        private static void requireCoordinate(double value, String name) {
            if (!Double.isFinite(value) || Math.abs(value) > 30_000_000.0D) {
                throw new IllegalArgumentException(name + " is outside the supported world border");
            }
        }

        private static void requireScale(double value, String name) {
            if (!Double.isFinite(value) || value < 0.25D || value > 8.0D) {
                throw new IllegalArgumentException(name + " must be finite and in [0.25, 8]");
            }
        }
    }

    record SpawnResult(Status status, Optional<UUID> entityId, String detail) {
        public SpawnResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(entityId, "entityId");
            Objects.requireNonNull(detail, "detail");
            if (detail.isBlank() || detail.length() > 512) {
                throw new IllegalArgumentException("detail must contain 1..512 characters");
            }
            if (status == Status.APPLIED) {
                UUID id = entityId.orElseThrow(
                        () -> new IllegalArgumentException("APPLIED spawn requires an entity UUID"));
                if (id.getMostSignificantBits() == 0L && id.getLeastSignificantBits() == 0L) {
                    throw new IllegalArgumentException("entityId cannot be the nil UUID");
                }
            } else if (entityId.isPresent()) {
                throw new IllegalArgumentException("refused spawn cannot expose an entity UUID");
            }
        }
    }

    record ControlResult(Status status, String detail) {
        public ControlResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(detail, "detail");
            if (detail.isBlank() || detail.length() > 512) {
                throw new IllegalArgumentException("detail must contain 1..512 characters");
            }
        }
    }

    enum Status {
        APPLIED,
        NOT_LOADED,
        NOT_FOUND,
        IDENTITY_MISMATCH,
        STATE_MISMATCH,
        CAPACITY_REACHED,
        FAILED
    }
}
