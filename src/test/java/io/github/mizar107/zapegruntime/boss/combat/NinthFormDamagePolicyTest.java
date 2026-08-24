package io.github.mizar107.zapegruntime.boss.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.github.mizar107.zapegruntime.boss.api.NinthFormPhase;
import org.junit.jupiter.api.Test;

class NinthFormDamagePolicyTest {

    @Test
    void participantHealthScalingCapsAtFivePlayers() {
        assertEquals(1.0D, NinthFormScaling.healthScale(1));
        assertEquals(1.45D, NinthFormScaling.healthScale(2));
        assertEquals(1.85D, NinthFormScaling.healthScale(3));
        assertEquals(2.2D, NinthFormScaling.healthScale(4));
        assertEquals(2.5D, NinthFormScaling.healthScale(5));
        assertEquals(2.5D, NinthFormScaling.healthScale(8));
        assertEquals(1.0D, NinthFormScaling.damageScale(1));
        assertEquals(1.08D, NinthFormScaling.damageScale(2));
        assertEquals(1.15D, NinthFormScaling.damageScale(3));
        assertEquals(1.21D, NinthFormScaling.damageScale(4));
        assertEquals(1.27D, NinthFormScaling.damageScale(5));
        assertEquals(1.27D, NinthFormScaling.damageScale(8));
    }

    @Test
    void multiplayerDamageScaleMustNotRoundTripThroughSyncedFloatStorage() {
        for (int participants = 2; participants <= 8; participants++) {
            double canonical = NinthFormScaling.damageScale(participants);
            assertNotEquals(canonical, (double) (float) canonical);
        }
    }

    @Test
    void phaseOneHullUsesAgreedMultiplierCapAndFloor() {
        NinthFormDamagePolicy.DamageDecision capped = NinthFormDamagePolicy.routePart(
                NinthFormPhase.FIRST,
                NinthFormPartKind.ARMORED_HULL_AFT,
                0,
                1000.0D,
                900.0D,
                900.0D,
                0.0D,
                1.0D);
        assertEquals(NinthFormDamagePolicy.DamageTarget.PARENT, capped.target());
        assertEquals(18.0D, capped.appliedDamage());

        NinthFormDamagePolicy.DamageDecision floor = NinthFormDamagePolicy.routePart(
                NinthFormPhase.FIRST,
                NinthFormPartKind.ARMORED_HULL_AFT,
                0b011,
                100.0D,
                500.0D,
                900.0D,
                0.0D,
                1.0D);
        assertEquals(5.0D, floor.appliedDamage(), 1.0E-9D);
        assertEquals("phase_one_hull", floor.reason());

        NinthFormDamagePolicy.DamageDecision stopped = NinthFormDamagePolicy.routePart(
                NinthFormPhase.FIRST,
                NinthFormPartKind.ARMORED_HULL_AFT,
                0b011,
                100.0D,
                495.0D,
                900.0D,
                0.0D,
                1.0D);
        assertEquals(NinthFormDamagePolicy.DamageTarget.IMMUNE, stopped.target());
        assertEquals("phase_one_floor", stopped.reason());
    }

    @Test
    void weakPointsAndFinalHeartUseAgreedCaps() {
        NinthFormDamagePolicy.DamageDecision weak = NinthFormDamagePolicy.routePart(
                NinthFormPhase.FIRST,
                NinthFormPartKind.PROW_LANTERN,
                0,
                1000.0D,
                900.0D,
                900.0D,
                1.0D,
                1.0D);
        assertEquals(NinthFormDamagePolicy.DamageTarget.WEAK_POINT, weak.target());
        assertEquals(36.0D, weak.appliedDamage());

        NinthFormDamagePolicy.DamageDecision heart = NinthFormDamagePolicy.routePart(
                NinthFormPhase.FINAL,
                NinthFormPartKind.KEEL_HEART,
                0b111,
                1000.0D,
                900.0D,
                900.0D,
                0.0D,
                1.0D);
        assertEquals(NinthFormDamagePolicy.DamageTarget.PARENT, heart.target());
        assertEquals(45.0D, heart.appliedDamage());
    }

    @Test
    void aftAndDirectParentStayConservativeAndExplicit() {
        NinthFormDamagePolicy.DamageDecision aft = NinthFormDamagePolicy.routePart(
                NinthFormPhase.FINAL,
                NinthFormPartKind.ARMORED_HULL_AFT,
                0b111,
                1000.0D,
                900.0D,
                900.0D,
                0.0D,
                1.0D);
        assertEquals(14.0D, aft.appliedDamage(), 1.0E-9D);
        NinthFormDamagePolicy.DamageDecision direct = NinthFormDamagePolicy.routeParent(
                NinthFormPhase.FINAL, 0b111, 1000.0D, 900.0D, 900.0D);
        assertEquals(12.0D, direct.appliedDamage(), 1.0E-9D);
    }

    @Test
    void completedPhaseOneWaitsForTheAuthoritativePhaseTransition() {
        NinthFormDamagePolicy.DamageDecision waiting = NinthFormDamagePolicy.routePart(
                NinthFormPhase.FIRST,
                NinthFormPartKind.ARMORED_HULL_AFT,
                0b111,
                100.0D,
                495.0D,
                900.0D,
                0.0D,
                1.0D);
        assertEquals(NinthFormDamagePolicy.DamageTarget.IMMUNE, waiting.target());
        assertEquals("phase_one_complete", waiting.reason());
    }
}
