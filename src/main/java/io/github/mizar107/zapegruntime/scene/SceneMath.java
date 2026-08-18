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

    /**
     * Unit vector toward the impossible sky mark: a seeded azimuth at a
     * seeded elevation of 24°–41°, so it always sits above the horizon but
     * never at the zenith. Returned as a plain double[3] to stay testable.
     */
    public static double[] skyMarkDirection(long seed) {
        double azimuth = Math.toRadians(Math.floorMod(seed, 360L));
        double elevation = Math.toRadians(24.0D + Math.floorMod(seed >>> 8, 18L));
        double horizontal = Math.cos(elevation);
        return new double[] {
            horizontal * Math.cos(azimuth),
            Math.sin(elevation),
            horizontal * Math.sin(azimuth)
        };
    }

    /**
     * Offset from the target's position for the near-miss crossing figure at
     * crossing progress t (0..1): always behind the target's heading, sliding
     * perpendicular from one side to the other. Plain double[3] for tests.
     */
    public static double[] nearMissOffset(long seed, float playerYawDegrees, double progress) {
        double t = smoothstep(0.0D, 1.0D, Math.min(1.0D, Math.max(0.0D, progress)));
        double yaw = Math.toRadians(playerYawDegrees);
        double lookX = -Math.sin(yaw);
        double lookZ = Math.cos(yaw);
        double side = (seed & 1L) == 0L ? 1.0D : -1.0D;
        double perpX = -lookZ;
        double perpZ = lookX;
        double behind = 3.0D;
        double span = 2.8D * (1.0D - 2.0D * t) * side;
        return new double[] {
            -lookX * behind + perpX * span,
            0.0D,
            -lookZ * behind + perpZ * span
        };
    }
}
