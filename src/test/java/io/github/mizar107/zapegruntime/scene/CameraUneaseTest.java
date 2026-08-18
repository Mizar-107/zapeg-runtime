package io.github.mizar107.zapegruntime.scene;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CameraUneaseTest {

    private static final float YAW_CAP =
            CameraUnease.MAX_JITTER_YAW_DEGREES + CameraUnease.MAX_SHAKE_DEGREES;
    private static final float PITCH_CAP =
            CameraUnease.MAX_JITTER_PITCH_DEGREES + CameraUnease.MAX_SHAKE_DEGREES;

    @Test
    void perturbationNeverExceedsHardCaps() {
        // Sweep levels, seeds, ages and the full shake window: unease must
        // stay bounded everywhere, never motion sickness.
        for (int level = 0; level <= CameraUnease.MAX_LEVEL + 2; level++) {
            for (long seed : new long[] {0L, 1L, -1L, 123456789L, Long.MIN_VALUE}) {
                for (double age = 0.0D; age <= 400.0D; age += 0.5D) {
                    for (int shake = -1; shake <= CameraUnease.SHAKE_DECAY_TICKS + 2; shake++) {
                        float[] offset = CameraUnease.perturbation(
                                level, age, seed, 1.0F, shake);
                        assertEquals(3, offset.length);
                        assertTrue(Math.abs(offset[0]) <= YAW_CAP + 1.0E-4F,
                                "yaw cap exceeded");
                        assertTrue(Math.abs(offset[1]) <= PITCH_CAP + 1.0E-4F,
                                "pitch cap exceeded");
                        assertTrue(Math.abs(offset[2])
                                <= CameraUnease.MAX_ROLL_DEGREES + 1.0E-4F,
                                "roll cap exceeded");
                    }
                }
            }
        }
    }

    @Test
    void zeroLevelOrZeroIntensityIsPerfectlyStill() {
        assertArrayEquals(
                new float[3],
                CameraUnease.perturbation(0, 100.0D, 42L, 1.0F, 3));
        assertArrayEquals(
                new float[3],
                CameraUnease.perturbation(3, 100.0D, 42L, 0.0F, 3));
        assertArrayEquals(
                new float[3],
                CameraUnease.perturbation(2, Double.NaN, 42L, 1.0F, 3));
    }

    @Test
    void shakeAlwaysDecaysToNothingWithinTheWindow() {
        for (int level = 1; level <= CameraUnease.MAX_LEVEL; level++) {
            float[] during = CameraUnease.perturbation(level, 50.0D, 7L, 1.0F, 0);
            float[] after = CameraUnease.perturbation(
                    level, 50.0D, 7L, 1.0F, CameraUnease.SHAKE_DECAY_TICKS);
            float[] without = CameraUnease.perturbation(level, 50.0D, 7L, 1.0F, -1);
            // Once the window closes the shake contributes exactly nothing.
            assertArrayEquals(without, after, 1.0E-6F);
            // And the jolt is a real signal while it lasts.
            double jitterOnly = Math.abs(without[0]) + Math.abs(without[1]);
            double withShake = Math.abs(during[0]) + Math.abs(during[1]);
            assertTrue(withShake >= jitterOnly - 1.0E-4D);
        }
    }

    @Test
    void heavyColossusPulsesNeverExceedTheirHardCaps() {
        float yawCap = CameraUnease.HEAVY_SWAY_DEGREES + CameraUnease.MAX_HEAVY_SHAKE_DEGREES;
        float pitchCap = CameraUnease.HEAVY_SWAY_DEGREES
                + CameraUnease.MAX_HEAVY_SHAKE_DEGREES * 0.7F;
        for (int stage = -1; stage <= ColossusChoreography.MAX_STAGE + 2; stage++) {
            for (long seed : new long[] {0L, 1L, -1L, 987654321L, Long.MIN_VALUE}) {
                for (double age = 0.0D; age <= 400.0D; age += 0.5D) {
                    for (int sinceStep = -1;
                            sinceStep <= CameraUnease.HEAVY_SHAKE_DECAY_TICKS + 2;
                            sinceStep++) {
                        float[] offset = CameraUnease.colossusPerturbation(
                                stage, age, seed, 1.0F, sinceStep);
                        assertEquals(3, offset.length);
                        assertTrue(Math.abs(offset[0]) <= yawCap + 1.0E-4F,
                                "heavy yaw cap exceeded");
                        assertTrue(Math.abs(offset[1]) <= pitchCap + 1.0E-4F,
                                "heavy pitch cap exceeded");
                        assertTrue(Math.abs(offset[2])
                                <= CameraUnease.MAX_ROLL_DEGREES + 1.0E-4F,
                                "heavy roll cap exceeded");
                    }
                }
            }
        }
    }

    @Test
    void heavyPulseAlwaysDecaysToTheGroundSwayWithinTheWindow() {
        for (int stage = 0; stage <= ColossusChoreography.MAX_STAGE; stage++) {
            float[] during = CameraUnease.colossusPerturbation(stage, 50.0D, 7L, 1.0F, 0);
            float[] after = CameraUnease.colossusPerturbation(
                    stage, 50.0D, 7L, 1.0F, CameraUnease.HEAVY_SHAKE_DECAY_TICKS);
            float[] without = CameraUnease.colossusPerturbation(stage, 50.0D, 7L, 1.0F, -1);
            // Once the window closes the footfall pulse contributes nothing.
            assertArrayEquals(without, after, 1.0E-6F);
            // And the pulse is a real signal while it lasts.
            double swayOnly = Math.abs(without[0]) + Math.abs(without[1]);
            double withPulse = Math.abs(during[0]) + Math.abs(during[1]);
            assertTrue(withPulse >= swayOnly - 1.0E-4D);
        }
    }

    @Test
    void heavyPathIsStillWhenTheSceneEnvelopeIsZero() {
        assertArrayEquals(
                new float[3],
                CameraUnease.colossusPerturbation(2, 100.0D, 42L, 0.0F, 3));
        assertArrayEquals(
                new float[3],
                CameraUnease.colossusPerturbation(2, Double.NaN, 42L, 1.0F, 3));
    }

    @Test
    void jitterIsContinuousEnoughToNeverJumpBetweenFrames() {
        // Adjacent frames must differ by far less than a degree, or the
        // perturbation itself would read as a stutter.
        for (int level = 1; level <= CameraUnease.MAX_LEVEL; level++) {
            for (double age = 0.0D; age < 200.0D; age += 1.0D) {
                float[] first = CameraUnease.perturbation(level, age, 99L, 0.8F, -1);
                float[] second = CameraUnease.perturbation(level, age + 0.05D, 99L, 0.8F, -1);
                for (int axis = 0; axis < 3; axis++) {
                    assertTrue(
                            Math.abs(first[axis] - second[axis]) < 0.12F,
                            "frame-to-frame jump too large on axis " + axis);
                }
            }
        }
    }
}
