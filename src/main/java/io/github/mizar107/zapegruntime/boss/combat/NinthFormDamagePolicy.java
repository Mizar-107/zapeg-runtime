package io.github.mizar107.zapegruntime.boss.combat;

import io.github.mizar107.zapegruntime.boss.api.NinthFormPhase;

/** Pure hit-surface routing, multipliers, caps, and the phase-one health gate. */
public final class NinthFormDamagePolicy {

    public static final double WEAK_POINT_BASE_HEALTH = 120.0D;
    public static final double PHASE_ONE_FLOOR = 0.55D;
    public static final double PHASE_ONE_HULL_MULTIPLIER = 0.35D;
    public static final double PHASE_ONE_HULL_CAP_FRACTION = 0.02D;
    public static final double WEAK_POINT_MULTIPLIER = 1.0D;
    public static final double WEAK_POINT_CAP_FRACTION = 0.04D;
    public static final double KEEL_HEART_MULTIPLIER = 1.6D;
    public static final double KEEL_HEART_CAP_FRACTION = 0.05D;
    public static final double AFT_MULTIPLIER = 0.15D;
    public static final double AFT_CAP_FRACTION = 14.0D / NinthFormBoss.BASE_HEALTH;
    public static final double DIRECT_MULTIPLIER = 0.12D;
    public static final double DIRECT_CAP_FRACTION = 12.0D / NinthFormBoss.BASE_HEALTH;

    private NinthFormDamagePolicy() {}

    public static DamageDecision routePart(
            NinthFormPhase phase,
            NinthFormPartKind part,
            int brokenPointMask,
            double rawDamage,
            double parentHealth,
            double parentMaxHealth,
            double weakPointFraction,
            double healthScale) {
        if (part == null
                || (brokenPointMask & ~0b111) != 0
                || !validInputs(
                        phase,
                        rawDamage,
                        parentHealth,
                        parentMaxHealth,
                        weakPointFraction,
                        healthScale)) {
            return DamageDecision.immune("invalid_or_inactive");
        }
        if (phase == NinthFormPhase.FIRST
                && part.weakPoint()
                && (brokenPointMask & part.weakPointBit()) == 0) {
            double remaining = WEAK_POINT_BASE_HEALTH * healthScale * weakPointFraction;
            double applied = Math.min(
                    remaining,
                    Math.min(rawDamage * WEAK_POINT_MULTIPLIER,
                            parentMaxHealth * WEAK_POINT_CAP_FRACTION));
            return applied > 0.0D
                    ? new DamageDecision(DamageTarget.WEAK_POINT, applied, "weak_point")
                    : DamageDecision.immune("weak_point_depleted");
        }
        if (phase == NinthFormPhase.FIRST) {
            if (brokenPointMask == 0b111) {
                return DamageDecision.immune("phase_one_complete");
            }
            return parentDecision(
                    rawDamage,
                    PHASE_ONE_HULL_MULTIPLIER,
                    PHASE_ONE_HULL_CAP_FRACTION,
                    parentHealth,
                    parentMaxHealth,
                    PHASE_ONE_FLOOR,
                    "phase_one_hull");
        }
        if (phase == NinthFormPhase.FINAL && part == NinthFormPartKind.KEEL_HEART) {
            return parentDecision(
                    rawDamage,
                    KEEL_HEART_MULTIPLIER,
                    KEEL_HEART_CAP_FRACTION,
                    parentHealth,
                    parentMaxHealth,
                    0.0D,
                    "keel_heart");
        }
        if (phase == NinthFormPhase.FINAL) {
            return parentDecision(
                    rawDamage,
                    AFT_MULTIPLIER,
                    AFT_CAP_FRACTION,
                    parentHealth,
                    parentMaxHealth,
                    0.0D,
                    "armored_surface");
        }
        return DamageDecision.immune("phase_invulnerable");
    }

    public static DamageDecision routeParent(
            NinthFormPhase phase,
            int brokenPointMask,
            double rawDamage,
            double parentHealth,
            double parentMaxHealth) {
        if ((brokenPointMask & ~0b111) != 0
                || !validParentInputs(phase, rawDamage, parentHealth, parentMaxHealth)) {
            return DamageDecision.immune("invalid_or_inactive");
        }
        if (phase != NinthFormPhase.FIRST && phase != NinthFormPhase.FINAL) {
            return DamageDecision.immune("phase_invulnerable");
        }
        if (phase == NinthFormPhase.FIRST && brokenPointMask == 0b111) {
            return DamageDecision.immune("phase_one_complete");
        }
        double floor = phase == NinthFormPhase.FIRST && brokenPointMask != 0b111
                ? PHASE_ONE_FLOOR
                : 0.0D;
        return parentDecision(
                rawDamage,
                DIRECT_MULTIPLIER,
                DIRECT_CAP_FRACTION,
                parentHealth,
                parentMaxHealth,
                floor,
                "direct_parent");
    }

    private static DamageDecision parentDecision(
            double rawDamage,
            double multiplier,
            double capFraction,
            double health,
            double maximum,
            double floorFraction,
            String reason) {
        double floor = maximum * floorFraction;
        double available = Math.max(0.0D, health - floor);
        double applied = Math.min(
                available,
                Math.min(rawDamage * multiplier, maximum * capFraction));
        return applied > 0.0D
                ? new DamageDecision(DamageTarget.PARENT, applied, reason)
                : DamageDecision.immune(floorFraction > 0.0D
                        ? "phase_one_floor"
                        : "parent_depleted");
    }

    private static boolean validInputs(
            NinthFormPhase phase,
            double rawDamage,
            double parentHealth,
            double parentMaxHealth,
            double weakPointFraction,
            double healthScale) {
        return validParentInputs(phase, rawDamage, parentHealth, parentMaxHealth)
                && Double.isFinite(weakPointFraction)
                && weakPointFraction >= 0.0D
                && weakPointFraction <= 1.0D
                && Double.isFinite(healthScale)
                && healthScale >= 0.25D
                && healthScale <= 8.0D;
    }

    private static boolean validParentInputs(
            NinthFormPhase phase,
            double rawDamage,
            double parentHealth,
            double parentMaxHealth) {
        return phase != null
                && Double.isFinite(rawDamage)
                && rawDamage > 0.0D
                && Double.isFinite(parentMaxHealth)
                && parentMaxHealth > 0.0D
                && Double.isFinite(parentHealth)
                && parentHealth > 0.0D
                && parentHealth <= parentMaxHealth;
    }

    public enum DamageTarget {
        IMMUNE,
        WEAK_POINT,
        PARENT
    }

    public record DamageDecision(DamageTarget target, double appliedDamage, String reason) {

        public DamageDecision {
            if (target == null
                    || !Double.isFinite(appliedDamage)
                    || appliedDamage < 0.0D
                    || reason == null
                    || !reason.matches("[a-z0-9_]{1,32}")) {
                throw new IllegalArgumentException("invalid Ninth Form damage decision");
            }
            if ((target == DamageTarget.IMMUNE) != (appliedDamage == 0.0D)) {
                throw new IllegalArgumentException("damage target conflicts with amount");
            }
        }

        static DamageDecision immune(String reason) {
            return new DamageDecision(DamageTarget.IMMUNE, 0.0D, reason);
        }
    }
}
