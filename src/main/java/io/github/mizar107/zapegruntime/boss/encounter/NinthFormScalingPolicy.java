package io.github.mizar107.zapegruntime.boss.encounter;

/** Readable 1-8 player scaling locked when an attempt is prepared. */
public final class NinthFormScalingPolicy {

    public static final int MIN_PARTICIPANTS = 1;
    public static final int MAX_PARTICIPANTS = 8;
    private static final double[] HEALTH = {1.00D, 1.45D, 1.85D, 2.20D, 2.50D};
    private static final double[] DAMAGE = {1.00D, 1.08D, 1.15D, 1.21D, 1.27D};

    private NinthFormScalingPolicy() {}

    public static Scale forParticipants(int participantCount) {
        if (participantCount < MIN_PARTICIPANTS || participantCount > MAX_PARTICIPANTS) {
            throw new IllegalArgumentException("participantCount must be in [1, 8]");
        }
        int tableIndex = Math.min(participantCount, 5) - 1;
        return new Scale(participantCount, HEALTH[tableIndex], DAMAGE[tableIndex]);
    }

    public record Scale(int participantCount, double healthScale, double damageScale) {
        public Scale {
            if (participantCount < MIN_PARTICIPANTS || participantCount > MAX_PARTICIPANTS
                    || !Double.isFinite(healthScale)
                    || healthScale < 1.0D
                    || healthScale > 2.50D
                    || !Double.isFinite(damageScale)
                    || damageScale < 1.0D
                    || damageScale > 1.27D) {
                throw new IllegalArgumentException("invalid Ninth Form scale");
            }
        }
    }
}
