package io.github.mizar107.zapegruntime.quest;

import java.util.Objects;

/** Pure deliberate-strike window for the ninth-bell discovery. */
public final class QuestBellPolicy {

    public static final int REQUIRED_STRIKES = 9;
    public static final long MIN_STRIKE_SPACING_TICKS = 6L;
    public static final long WINDOW_TICKS = 600L;

    private QuestBellPolicy() {}

    public static Progress recordAcceptedRing(
            Progress previous, String dimension, long blockPos, long serverTick) {
        Objects.requireNonNull(dimension, "dimension");
        if (dimension.isBlank() || serverTick < 0L) {
            throw new IllegalArgumentException("bell sample identity is invalid");
        }
        if (previous == null
                || !previous.dimension().equals(dimension)
                || previous.blockPos() != blockPos
                || serverTick < previous.lastTick()
                || serverTick - previous.firstTick() > WINDOW_TICKS) {
            return new Progress(dimension, blockPos, serverTick, serverTick, 1);
        }
        if (serverTick - previous.lastTick() < MIN_STRIKE_SPACING_TICKS) {
            return previous;
        }
        return new Progress(
                dimension,
                blockPos,
                previous.firstTick(),
                serverTick,
                Math.min(REQUIRED_STRIKES, previous.strikes() + 1));
    }

    public static boolean complete(Progress progress) {
        return progress != null && progress.strikes() >= REQUIRED_STRIKES;
    }

    public record Progress(
            String dimension, long blockPos, long firstTick, long lastTick, int strikes) {

        public Progress {
            Objects.requireNonNull(dimension, "dimension");
            if (dimension.isBlank()
                    || firstTick < 0L
                    || lastTick < firstTick
                    || strikes < 1
                    || strikes > REQUIRED_STRIKES) {
                throw new IllegalArgumentException("bell progress is invalid");
            }
        }
    }
}
