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

    public static double smoothstep(double edge0, double edge1, double value) {
        if (!(edge1 > edge0) || !Double.isFinite(value)) {
            return 0.0D;
        }
        double t = Math.min(1.0D, Math.max(0.0D, (value - edge0) / (edge1 - edge0)));
        return t * t * (3.0D - 2.0D * t);
    }

    /**
     * 0→1 over the first {@code fadeInTicks} of a scene, 1→0 over its last
     * {@code fadeOutTicks}, so apparitions never pop in or out within a frame.
     */
    public static double lifeEnvelope(
            double ageTicks,
            double ttlTicks,
            double fadeInTicks,
            double fadeOutTicks) {
        if (!Double.isFinite(ageTicks) || !Double.isFinite(ttlTicks) || ttlTicks <= 0.0D) {
            return 0.0D;
        }
        double fadeIn = smoothstep(0.0D, Math.max(1.0D, fadeInTicks), ageTicks);
        double fadeOut = smoothstep(0.0D, Math.max(1.0D, fadeOutTicks), ttlTicks - ageTicks);
        return Math.min(fadeIn, fadeOut);
    }
}
