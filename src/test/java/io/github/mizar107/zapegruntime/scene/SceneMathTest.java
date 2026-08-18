package io.github.mizar107.zapegruntime.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SceneMathTest {

    @Test
    void gazeConeUsesNormalizedDirections() {
        assertTrue(SceneMath.withinAngle(new Vec3(0.0D, 0.0D, 2.0D), new Vec3(0.0D, 0.0D, 10.0D), 4.0D));
        assertFalse(SceneMath.withinAngle(new Vec3(0.0D, 0.0D, 1.0D), new Vec3(1.0D, 0.0D, 0.0D), 4.0D));
        assertFalse(SceneMath.withinAngle(Vec3.ZERO, new Vec3(0.0D, 0.0D, 1.0D), 4.0D));
    }

    @Test
    void pulseIsBoundedAndRejectsBadPeriods() {
        for (int tick = 0; tick < 100; tick++) {
            double value = SceneMath.easedPulse(tick, 19.0D);
            assertTrue(value >= 0.0D && value <= 1.0D);
        }
        assertTrue(SceneMath.easedPulse(10.0D, 0.0D) == 0.0D);
        assertTrue(SceneMath.easedPulse(Double.NaN, 19.0D) == 0.0D);
    }

    @Test
    void smoothstepIsClampedMonotonicAndSafe() {
        assertEquals(0.0D, SceneMath.smoothstep(0.0D, 1.0D, -0.5D));
        assertEquals(0.0D, SceneMath.smoothstep(0.0D, 1.0D, 0.0D));
        assertEquals(1.0D, SceneMath.smoothstep(0.0D, 1.0D, 1.0D));
        assertEquals(1.0D, SceneMath.smoothstep(0.0D, 1.0D, 2.0D));
        assertEquals(0.5D, SceneMath.smoothstep(0.0D, 1.0D, 0.5D), 1.0E-9D);

        double previous = -1.0D;
        for (int step = 0; step <= 20; step++) {
            double value = SceneMath.smoothstep(0.0D, 1.0D, step / 20.0D);
            assertTrue(value >= previous && value >= 0.0D && value <= 1.0D);
            previous = value;
        }

        assertEquals(0.0D, SceneMath.smoothstep(1.0D, 1.0D, 0.5D));
        assertEquals(0.0D, SceneMath.smoothstep(0.0D, 1.0D, Double.NaN));
    }

    @Test
    void lifeEnvelopeFadesInHoldsAndFadesOut() {
        double ttl = 200.0D;
        assertEquals(0.0D, SceneMath.lifeEnvelope(0.0D, ttl, 9.0D, 6.0D));
        assertEquals(1.0D, SceneMath.lifeEnvelope(9.0D, ttl, 9.0D, 6.0D));
        assertEquals(1.0D, SceneMath.lifeEnvelope(100.0D, ttl, 9.0D, 6.0D));
        assertEquals(1.0D, SceneMath.lifeEnvelope(ttl - 6.0D, ttl, 9.0D, 6.0D));
        assertEquals(0.0D, SceneMath.lifeEnvelope(ttl, ttl, 9.0D, 6.0D));

        double rising = SceneMath.lifeEnvelope(4.5D, ttl, 9.0D, 6.0D);
        assertTrue(rising > 0.0D && rising < 1.0D);
        double falling = SceneMath.lifeEnvelope(ttl - 3.0D, ttl, 9.0D, 6.0D);
        assertTrue(falling > 0.0D && falling < 1.0D);

        assertEquals(0.0D, SceneMath.lifeEnvelope(Double.NaN, ttl, 9.0D, 6.0D));
        assertEquals(0.0D, SceneMath.lifeEnvelope(10.0D, 0.0D, 9.0D, 6.0D));
        assertEquals(0.0D, SceneMath.lifeEnvelope(10.0D, -5.0D, 9.0D, 6.0D));
    }

    @Test
    void skyMarkDirectionIsAUnitVectorAboveTheHorizon() {
        for (long seed = 0L; seed < 512L; seed++) {
            double[] direction = SceneMath.skyMarkDirection(seed * 7919L - 104729L);
            double length = Math.sqrt(
                    direction[0] * direction[0]
                            + direction[1] * direction[1]
                            + direction[2] * direction[2]);
            assertEquals(1.0D, length, 1.0E-9D);
            // Elevation between 24° and 41°: always above the horizon, never
            // at the zenith, so it reads as a sky object, not an overhead.
            assertTrue(direction[1] >= Math.sin(Math.toRadians(23.9D)));
            assertTrue(direction[1] <= Math.sin(Math.toRadians(41.1D)));
        }
    }

    @Test
    void nearMissOffsetStaysBehindTheTargetAtEveryCrossingPoint() {
        for (long seed = 0L; seed < 64L; seed++) {
            for (int yaw = 0; yaw < 360; yaw += 15) {
                double lookX = -Math.sin(Math.toRadians(yaw));
                double lookZ = Math.cos(Math.toRadians(yaw));
                for (int step = 0; step <= 10; step++) {
                    double[] offset = SceneMath.nearMissOffset(seed, yaw, step / 10.0D);
                    // The dot product with the look vector must stay
                    // negative: the figure never steps in front of the
                    // target, so it can never touch the crosshair uninvited.
                    double forwardness = offset[0] * lookX + offset[2] * lookZ;
                    assertTrue(forwardness < 0.0D);
                    double distance = Math.sqrt(offset[0] * offset[0] + offset[2] * offset[2]);
                    assertTrue(distance >= 2.9D && distance <= 4.2D);
                    assertEquals(0.0D, offset[1]);
                }
            }
        }
    }

    @Test
    void nearMissOffsetMovesFromOneSideToTheOther() {
        double[] start = SceneMath.nearMissOffset(0L, 0.0F, 0.0D);
        double[] end = SceneMath.nearMissOffset(0L, 0.0F, 1.0D);
        // Facing +Z (yaw 0): the crossing runs perpendicular to the view, so
        // the X component flips sign between the start and the end.
        assertTrue(start[0] * end[0] < 0.0D);
        // The other seed parity crosses from the opposite side.
        double[] otherStart = SceneMath.nearMissOffset(1L, 0.0F, 0.0D);
        assertTrue(otherStart[0] * start[0] < 0.0D);
    }
}
