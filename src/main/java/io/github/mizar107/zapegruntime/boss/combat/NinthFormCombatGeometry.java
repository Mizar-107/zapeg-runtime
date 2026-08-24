package io.github.mizar107.zapegruntime.boss.combat;

import net.minecraft.world.phys.Vec3;

/** Pure horizontal arena and authored attack-shape math. */
public final class NinthFormCombatGeometry {

    public static final double CONFINEMENT_RADIUS = 48.0D;
    public static final float MAX_WINDUP_YAW_STEP = 4.0F;

    private NinthFormCombatGeometry() {}

    public static boolean insideConfinement(Vec3 origin, Vec3 point) {
        return horizontalDistanceSquared(origin, point)
                <= CONFINEMENT_RADIUS * CONFINEMENT_RADIUS;
    }

    public static Vec3 confinementImpulse(Vec3 origin, Vec3 point) {
        double deltaX = origin.x - point.x;
        double deltaZ = origin.z - point.z;
        double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        if (!Double.isFinite(distance) || distance <= CONFINEMENT_RADIUS || distance == 0.0D) {
            return Vec3.ZERO;
        }
        double overflow = Math.min(16.0D, distance - CONFINEMENT_RADIUS);
        double strength = Math.min(0.9D, 0.18D + overflow * 0.04D);
        return new Vec3(deltaX / distance * strength, 0.08D, deltaZ / distance * strength);
    }

    /** Advances along the shortest yaw arc without allowing a telegraph to snap. */
    public static float boundedYawToward(
            float currentYaw, Vec3 origin, Vec3 target, float maximumStep) {
        if (!Float.isFinite(currentYaw)
                || !Float.isFinite(maximumStep)
                || maximumStep <= 0.0F
                || maximumStep > 45.0F) {
            throw new IllegalArgumentException("invalid bounded-yaw input");
        }
        double deltaX = target.x - origin.x;
        double deltaZ = target.z - origin.z;
        if (!Double.isFinite(deltaX)
                || !Double.isFinite(deltaZ)
                || deltaX * deltaX + deltaZ * deltaZ <= 0.0001D) {
            return wrapDegrees(currentYaw);
        }
        float desiredYaw = (float) Math.toDegrees(Math.atan2(-deltaX, deltaZ));
        float delta = wrapDegrees(desiredYaw - currentYaw);
        float bounded = Math.max(-maximumStep, Math.min(maximumStep, delta));
        return wrapDegrees(currentYaw + bounded);
    }

    public static boolean insideKeelSweep(Vec3 origin, Vec3 point) {
        return Math.abs(point.y - origin.y) <= 5.0D
                && horizontalDistanceSquared(origin, point) <= 16.0D * 16.0D;
    }

    public static boolean insideAnchorfall(Vec3 impact, Vec3 point) {
        return Math.abs(point.y - impact.y) <= 6.0D
                && horizontalDistanceSquared(impact, point) <= 5.0D * 5.0D;
    }

    public static boolean insideBroadside(Vec3 origin, float yawDegrees, Vec3 point) {
        LocalPoint local = localPoint(origin, yawDegrees, point);
        return Math.abs(point.y - origin.y) <= 7.0D
                && Math.abs(local.forward()) <= 12.0D
                && Math.abs(local.lateral()) >= 5.0D
                && Math.abs(local.lateral()) <= 34.0D;
    }

    public static boolean insideWakeCharge(Vec3 origin, float yawDegrees, Vec3 point) {
        LocalPoint local = localPoint(origin, yawDegrees, point);
        return Math.abs(point.y - origin.y) <= 6.0D
                && local.forward() >= 0.0D
                && local.forward() <= 38.0D
                && Math.abs(local.lateral()) <= 4.0D + local.forward() * 0.22D;
    }

    public static boolean insideNinefoldGaze(Vec3 origin, float yawDegrees, Vec3 point) {
        LocalPoint local = localPoint(origin, yawDegrees, point);
        double horizontal = Math.sqrt(
                local.forward() * local.forward() + local.lateral() * local.lateral());
        if (horizontal <= 0.0D || horizontal > CONFINEMENT_RADIUS || local.forward() <= 0.0D) {
            return false;
        }
        return local.forward() / horizontal >= Math.cos(Math.toRadians(11.0D))
                && Math.abs(point.y - origin.y) <= 9.0D;
    }

    public static LocalPoint localPoint(Vec3 origin, float yawDegrees, Vec3 point) {
        double deltaX = point.x - origin.x;
        double deltaZ = point.z - origin.z;
        double radians = Math.toRadians(yawDegrees);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        double lateral = deltaX * cos + deltaZ * sin;
        double forward = -deltaX * sin + deltaZ * cos;
        return new LocalPoint(lateral, forward);
    }

    private static double horizontalDistanceSquared(Vec3 first, Vec3 second) {
        double x = first.x - second.x;
        double z = first.z - second.z;
        return x * x + z * z;
    }

    private static float wrapDegrees(float value) {
        float wrapped = value % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    public record LocalPoint(double lateral, double forward) {}
}
