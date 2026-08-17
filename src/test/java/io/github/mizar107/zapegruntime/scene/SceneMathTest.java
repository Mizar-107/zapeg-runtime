package io.github.mizar107.zapegruntime.scene;

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
}
