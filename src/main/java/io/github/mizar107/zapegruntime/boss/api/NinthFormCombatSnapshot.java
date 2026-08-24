package io.github.mizar107.zapegruntime.boss.api;

import java.util.Objects;
import java.util.UUID;

/** Loaded-entity observation returned by the combat gateway. */
public record NinthFormCombatSnapshot(
        NinthFormIdentity identity,
        UUID entityId,
        NinthFormPhase phase,
        String dimensionId,
        double x,
        double y,
        double z,
        double health,
        double maxHealth,
        int participantCount,
        CombatState combatState,
        VitalState vitalState,
        long observedGameTick) {

    private static final double MAX_COORDINATE = 30_000_000.0D;

    public NinthFormCombatSnapshot {
        Objects.requireNonNull(identity, "identity");
        identity.validateEntityId(entityId);
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(dimensionId, "dimensionId");
        if (dimensionId.isBlank() || dimensionId.length() > 255) {
            throw new IllegalArgumentException("dimensionId must contain 1..255 characters");
        }
        requireCoordinate(x, "x");
        if (!Double.isFinite(y) || y < -2048.0D || y > 2048.0D) {
            throw new IllegalArgumentException("y is outside the supported world range");
        }
        requireCoordinate(z, "z");
        if (!Double.isFinite(maxHealth) || maxHealth <= 0.0D || maxHealth > 1_000_000.0D) {
            throw new IllegalArgumentException("maxHealth must be finite and in (0, 1000000]");
        }
        if (!Double.isFinite(health) || health < 0.0D || health > maxHealth) {
            throw new IllegalArgumentException("health must be finite and in [0, maxHealth]");
        }
        if (participantCount < 1 || participantCount > 8) {
            throw new IllegalArgumentException("participantCount must be in [1, 8]");
        }
        Objects.requireNonNull(combatState, "combatState");
        Objects.requireNonNull(vitalState, "vitalState");
        vitalState.validateMask(combatState.brokenPointMask());
        double observedFraction = health / maxHealth;
        if (Math.abs(observedFraction - vitalState.parentHealthFraction()) > 0.000_001D) {
            throw new IllegalArgumentException("parent health fraction does not match health/maxHealth");
        }
        if (observedGameTick < 0L) {
            throw new IllegalArgumentException("observedGameTick cannot be negative");
        }
        if (phase.terminal() && health != 0.0D) {
            throw new IllegalArgumentException("a defeated snapshot must have zero health");
        }
    }

    private static void requireCoordinate(double value, String name) {
        if (!Double.isFinite(value) || Math.abs(value) > MAX_COORDINATE) {
            throw new IllegalArgumentException(name + " is outside the supported world border");
        }
    }

    /** Durable combat cursor; the entity mirrors this state but never owns it. */
    public record CombatState(
            int brokenPointMask, long attackCycle, String attackId, int attackTick) {

        public CombatState {
            if ((brokenPointMask & ~0b111) != 0) {
                throw new IllegalArgumentException("brokenPointMask is limited to three weak points");
            }
            if (attackCycle < 0L) {
                throw new IllegalArgumentException("attackCycle cannot be negative");
            }
            Objects.requireNonNull(attackId, "attackId");
            if (!attackId.matches("[a-z0-9_]{1,32}")) {
                throw new IllegalArgumentException("attackId must be a bounded lowercase identifier");
            }
            if (attackTick < 0 || attackTick > 72_000) {
                throw new IllegalArgumentException("attackTick must be in [0, 72000]");
            }
        }
    }

    /** Normalized parent and weak-point vitality persisted for exact recovery. */
    public record VitalState(
            double parentHealthFraction,
            double prowHealthFraction,
            double portHealthFraction,
            double starboardHealthFraction) {

        public VitalState {
            requireFraction(parentHealthFraction, "parentHealthFraction");
            requireFraction(prowHealthFraction, "prowHealthFraction");
            requireFraction(portHealthFraction, "portHealthFraction");
            requireFraction(starboardHealthFraction, "starboardHealthFraction");
        }

        public static VitalState pristine() {
            return new VitalState(1.0D, 1.0D, 1.0D, 1.0D);
        }

        public void validateMask(int brokenPointMask) {
            requirePointConsistency(brokenPointMask, 0b001, prowHealthFraction, "prow");
            requirePointConsistency(brokenPointMask, 0b010, portHealthFraction, "port");
            requirePointConsistency(brokenPointMask, 0b100, starboardHealthFraction, "starboard");
        }

        private static void requireFraction(double value, String name) {
            if (!Double.isFinite(value) || value < 0.0D || value > 1.0D) {
                throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
            }
        }

        private static void requirePointConsistency(
                int mask, int bit, double fraction, String point) {
            boolean broken = (mask & bit) != 0;
            if ((broken && fraction != 0.0D) || (!broken && fraction <= 0.0D)) {
                throw new IllegalArgumentException(
                        point + " weak-point health conflicts with brokenPointMask");
            }
        }
    }
}
