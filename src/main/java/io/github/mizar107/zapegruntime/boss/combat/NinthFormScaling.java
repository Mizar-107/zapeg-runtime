package io.github.mizar107.zapegruntime.boss.combat;

/** Closed participant scaling table; additional players never exceed the five-player tier. */
public final class NinthFormScaling {

    private static final double[] HEALTH_SCALES = {1.00D, 1.45D, 1.85D, 2.20D, 2.50D};
    private static final double[] DAMAGE_SCALES = {1.00D, 1.08D, 1.15D, 1.21D, 1.27D};

    private NinthFormScaling() {}

    public static double healthScale(int participantCount) {
        return scaleFor(HEALTH_SCALES, participantCount);
    }

    public static double damageScale(int participantCount) {
        return scaleFor(DAMAGE_SCALES, participantCount);
    }

    private static double scaleFor(double[] table, int participantCount) {
        if (participantCount < 1 || participantCount > 8) {
            throw new IllegalArgumentException("participantCount must be in [1, 8]");
        }
        return table[Math.min(participantCount, table.length) - 1];
    }
}
