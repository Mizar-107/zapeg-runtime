package io.github.mizar107.zapegruntime.quest;

import java.util.Objects;

/** Pure thresholds and reset rules for multi-tick quest actions. */
public final class QuestProgressPolicy {

    public static final int BACKWARD_MIN_TICKS = 40;
    public static final double BACKWARD_MIN_DISTANCE = 6.0D;
    public static final int DROWNED_MIN_TICKS = 60;
    public static final double DROWNED_MIN_DISTANCE = 8.0D;
    public static final int LEAN_MIN_TICKS = 60;
    public static final double LEAN_MAX_PATH_DRIFT = 0.50D;
    public static final double LEAN_MAX_NET_DRIFT = 0.25D;
    public static final double UNDERDOOR_MIN_DISPLACEMENT = 3.0D;
    public static final int WITNESS_MIN_TICKS = 40;

    static final double MAX_NATURAL_STEP = 1.25D;
    static final double MIN_BACKWARD_STEP = 0.005D;
    static final double MAX_LEAN_STEP = 0.035D;

    private QuestProgressPolicy() {}

    public static Progress start(double x, double z) {
        requireFinite(x, "x");
        requireFinite(z, "z");
        return new Progress(1, 0.0D, x, z, x, z);
    }

    /** Advances one continuous sample, restarting safely after teleport-sized movement. */
    public static Progress advance(Progress previous, double x, double z) {
        Objects.requireNonNull(previous, "previous");
        requireFinite(x, "x");
        requireFinite(z, "z");
        double step = horizontalDistance(previous.lastX(), previous.lastZ(), x, z);
        if (step > MAX_NATURAL_STEP) {
            return start(x, z);
        }
        return new Progress(
                previous.ticks() + 1,
                previous.pathDistance() + step,
                previous.originX(),
                previous.originZ(),
                x,
                z);
    }

    public static boolean isBackwardStep(
            double lastX,
            double lastZ,
            double x,
            double z,
            double lookX,
            double lookZ) {
        double moveX = x - lastX;
        double moveZ = z - lastZ;
        double moveLength = Math.hypot(moveX, moveZ);
        double lookLength = Math.hypot(lookX, lookZ);
        if (moveLength < MIN_BACKWARD_STEP || moveLength > MAX_NATURAL_STEP || lookLength < 1.0E-6D) {
            return false;
        }
        double alignment = (moveX * lookX + moveZ * lookZ) / (moveLength * lookLength);
        return alignment <= -0.65D;
    }

    public static boolean isStationaryStep(Progress previous, double x, double z) {
        Objects.requireNonNull(previous, "previous");
        return horizontalDistance(previous.lastX(), previous.lastZ(), x, z) <= MAX_LEAN_STEP;
    }

    public static boolean complete(QuestAction action, Progress progress) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(progress, "progress");
        return switch (action) {
            case BACKWARD_TRACKS -> progress.ticks() >= BACKWARD_MIN_TICKS
                    && progress.pathDistance() >= BACKWARD_MIN_DISTANCE;
            case DROWNED_ROAD -> progress.ticks() >= DROWNED_MIN_TICKS
                    && progress.pathDistance() >= DROWNED_MIN_DISTANCE;
            case LEANING_HOUSE -> progress.ticks() >= LEAN_MIN_TICKS
                    && progress.pathDistance() <= LEAN_MAX_PATH_DRIFT
                    && progress.displacement() <= LEAN_MAX_NET_DRIFT;
            case UNDERDOOR -> progress.displacement() >= UNDERDOOR_MIN_DISPLACEMENT;
            case NINTH_WITNESS -> progress.ticks() >= WITNESS_MIN_TICKS;
            default -> false;
        };
    }

    public static boolean isNight(long dayTime) {
        long normalized = Math.floorMod(dayTime, 24_000L);
        return normalized >= 13_000L && normalized < 23_000L;
    }

    private static double horizontalDistance(double x1, double z1, double x2, double z2) {
        return Math.hypot(x2 - x1, z2 - z1);
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    public record Progress(
            int ticks,
            double pathDistance,
            double originX,
            double originZ,
            double lastX,
            double lastZ) {

        public Progress {
            if (ticks < 1 || !Double.isFinite(pathDistance) || pathDistance < 0.0D) {
                throw new IllegalArgumentException("progress counters are invalid");
            }
            requireFinite(originX, "originX");
            requireFinite(originZ, "originZ");
            requireFinite(lastX, "lastX");
            requireFinite(lastZ, "lastZ");
        }

        public double displacement() {
            return horizontalDistance(originX, originZ, lastX, lastZ);
        }
    }
}
