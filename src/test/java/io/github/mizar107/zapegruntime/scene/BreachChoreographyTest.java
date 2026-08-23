package io.github.mizar107.zapegruntime.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BreachChoreographyTest {

    @Test
    void defaultAndMinimumTimelinesKeepEveryCueOrderedAndOneShot() {
        for (int bodyTicks : new int[] {20, 170, 180, 1200}) {
            Set<Integer> ticks = new HashSet<>();
            int previous = -1;
            for (BreachChoreography.Cue cue : BreachChoreography.Cue.values()) {
                int tick = BreachChoreography.cueTick(cue, bodyTicks);
                assertTrue(tick >= 1 && tick < bodyTicks);
                assertTrue(tick > previous, "cues must retain their narrative order");
                assertTrue(ticks.add(tick), "one tick cannot accidentally double-play a cue");
                previous = tick;
            }
        }
    }

    @Test
    void frameIsFiniteAndBoundedForHostileInputsAndAllSupportedTtls() {
        long[] seeds = {Long.MIN_VALUE, -1L, 0L, 1L, 0x5A17C0DEL, Long.MAX_VALUE};
        int[] bodyLengths = {1, 20, 170, 180, SceneDescriptor.MAX_TTL_TICKS};
        double[] hostileAges = {
            Double.NEGATIVE_INFINITY, -10_000.0D, -1.0D, 0.0D,
            Double.NaN, Double.POSITIVE_INFINITY, 10_000.0D
        };
        for (long seed : seeds) {
            for (int bodyTicks : bodyLengths) {
                for (double age : hostileAges) {
                    assertBounded(BreachChoreography.frame(age, bodyTicks, seed));
                }
                for (double age = 0.0D; age <= bodyTicks; age += 0.25D) {
                    assertBounded(BreachChoreography.frame(age, bodyTicks, seed));
                }
            }
        }
    }

    @Test
    void defaultArcOpensClosesAndManifestsWithoutABrightFlash() {
        int bodyTicks = SceneProfile.VISITATION_01.defaultTtlTicks();
        BreachChoreography.Frame start = BreachChoreography.frame(0.0D, bodyTicks, 42L);
        BreachChoreography.Frame doorway = BreachChoreography.frame(80.0D, bodyTicks, 42L);
        BreachChoreography.Frame witness = BreachChoreography.frame(118.0D, bodyTicks, 42L);
        BreachChoreography.Frame end = BreachChoreography.frame(bodyTicks, bodyTicks, 42L);

        assertEquals(0.0D, start.veilOpacity());
        assertTrue(doorway.doorwayClosure() > 0.8D);
        assertTrue(witness.manifestationOpacity() > 0.6D);
        assertTrue(witness.eyeOpacity() > 0.2D);
        assertEquals(0.0D, end.veilOpacity());
        assertEquals(0.0D, end.manifestationOpacity());

        assertTrue(BreachChoreography.MAX_VEIL_OPACITY <= 0.72D);
        assertTrue(BreachChoreography.MAX_EYE_OPACITY <= 0.68D);
        double previousVeil = 0.0D;
        double previousManifestation = 0.0D;
        for (int tick = 0; tick <= bodyTicks; tick++) {
            BreachChoreography.Frame frame =
                    BreachChoreography.frame(tick, bodyTicks, 42L);
            assertTrue(Math.abs(frame.veilOpacity() - previousVeil) < 0.09D,
                    "the full-screen veil cannot strobe between ticks");
            assertTrue(Math.abs(frame.manifestationOpacity() - previousManifestation) < 0.10D,
                    "the manifestation must fade rather than flash");
            previousVeil = frame.veilOpacity();
            previousManifestation = frame.manifestationOpacity();
        }
    }

    @Test
    void footstepsStayWithinFiveBlocksOfTheTarget() {
        for (long seed : new long[] {Long.MIN_VALUE, -17L, 0L, 17L, Long.MAX_VALUE}) {
            double[] prior = null;
            for (int step = 0; step < 4; step++) {
                double[] offset = BreachChoreography.footstepOffset(seed, step);
                double distance = Math.hypot(offset[0], offset[1]);
                assertTrue(Double.isFinite(offset[0]) && Double.isFinite(offset[1]));
                assertTrue(distance >= 2.5D && distance <= 5.0D);
                if (prior != null) {
                    assertNotEquals(prior[0], offset[0]);
                    assertNotEquals(prior[1], offset[1]);
                }
                prior = offset;
            }
        }
    }

    @Test
    void seedChangesDriftButNeverTheSafetyEnvelope() {
        BreachChoreography.Frame first = BreachChoreography.frame(90.0D, 170, 1L);
        BreachChoreography.Frame second = BreachChoreography.frame(90.0D, 170, 999L);
        assertEquals(first.veilOpacity(), second.veilOpacity());
        assertEquals(first.doorwayClosure(), second.doorwayClosure());
        assertEquals(first.manifestationOpacity(), second.manifestationOpacity());
        assertNotEquals(first.horizontalDrift(), second.horizontalDrift());
    }

    private static void assertBounded(BreachChoreography.Frame frame) {
        assertUnit(frame.veilOpacity());
        assertUnit(frame.doorwayClosure());
        assertUnit(frame.seamOpacity());
        assertUnit(frame.manifestationOpacity());
        assertUnit(frame.eyeOpacity());
        assertTrue(Double.isFinite(frame.horizontalDrift()));
        assertTrue(frame.horizontalDrift() >= -1.0D && frame.horizontalDrift() <= 1.0D);
        assertTrue(frame.veilOpacity() <= BreachChoreography.MAX_VEIL_OPACITY);
        assertTrue(frame.manifestationOpacity()
                <= BreachChoreography.MAX_MANIFESTATION_OPACITY);
        assertTrue(frame.eyeOpacity() <= BreachChoreography.MAX_EYE_OPACITY);
    }

    private static void assertUnit(double value) {
        assertTrue(Double.isFinite(value));
        assertTrue(value >= 0.0D && value <= 1.0D);
    }
}
