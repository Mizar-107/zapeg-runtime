package io.github.mizar107.zapegruntime.scene;

import net.minecraft.world.phys.Vec3;

public final class SceneMath {

    private SceneMath() {}

    public static boolean withinAngle(Vec3 lookDirection, Vec3 toTarget, double degrees) {
        if (lookDirection.lengthSqr() < 1.0E-12D || toTarget.lengthSqr() < 1.0E-12D) {
            return false;
        }
        double dot = lookDirection.normalize().dot(toTarget.normalize());
        double threshold = Math.cos(Math.toRadians(degrees));
        return dot >= threshold;
    }

    public static double easedPulse(double ageTicks, double periodTicks) {
        if (!Double.isFinite(ageTicks) || !Double.isFinite(periodTicks) || periodTicks <= 0) {
            return 0.0D;
        }
        return 0.5D + 0.5D * Math.sin(ageTicks * Math.PI * 2.0D / periodTicks);
    }
}
