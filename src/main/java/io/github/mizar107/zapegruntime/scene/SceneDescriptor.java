package io.github.mizar107.zapegruntime.scene;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record SceneDescriptor(
        UUID eventId,
        UUID targetId,
        ResourceLocation dimension,
        Vec3 anchor,
        float yawDegrees,
        int ttlTicks,
        long visualSeed,
        SceneProfile profile,
        boolean rehearsal) {

    public static final int MIN_TTL_TICKS = 20;
    // 60 seconds: the Director scales scene TTLs up with campaign phase, so
    // the bound must head past the phase-scaled profile defaults while still
    // keeping every scene strictly time-boxed.
    public static final int MAX_TTL_TICKS = 1200;
    private static final double MAX_HORIZONTAL_COORDINATE = 30_000_000.0D;
    private static final double MAX_VERTICAL_COORDINATE = 2_048.0D;

    public SceneDescriptor {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(profile, "profile");
        if (ttlTicks < MIN_TTL_TICKS || ttlTicks > MAX_TTL_TICKS) {
            throw new IllegalArgumentException(
                    "Scene TTL must be between " + MIN_TTL_TICKS + " and "
                            + MAX_TTL_TICKS + " ticks");
        }
        if (!Float.isFinite(yawDegrees)) {
            throw new IllegalArgumentException("Scene yaw must be finite");
        }
        if (!finiteBounded(anchor.x, MAX_HORIZONTAL_COORDINATE)
                || !finiteBounded(anchor.z, MAX_HORIZONTAL_COORDINATE)
                || !finiteBounded(anchor.y, MAX_VERTICAL_COORDINATE)) {
            throw new IllegalArgumentException("Scene anchor is outside safe bounds");
        }
    }

    private static boolean finiteBounded(double value, double bound) {
        return Double.isFinite(value) && Math.abs(value) <= bound;
    }
}
