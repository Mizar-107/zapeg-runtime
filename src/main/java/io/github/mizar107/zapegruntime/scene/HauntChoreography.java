package io.github.mizar107.zapegruntime.scene;

/**
 * The haunt (footsteps family): circling steps that close in, then the
 * target's own delayed gait. Pure math so distances and stage meaning are
 * unit-testable without a level.
 *
 * <p>Stage 0 — distant circle that never arrives (the original
 * {@code footsteps_01}). Stage 1 — the same idea, closer and more insistent.
 * Stage 2 — whisper replay from the local motion trace (the original
 * {@code whisper_steps_01}).
 */
public final class HauntChoreography {

    public static final int STAGE_CIRCLE = 0;
    public static final int STAGE_CLOSING = 1;
    public static final int STAGE_WHISPER = 2;
    public static final int MAX_STAGE = STAGE_WHISPER;

    private static final double[] START_DISTANCE = {13.0D, 8.0D, 0.0D};
    private static final double[] END_DISTANCE = {3.25D, 1.55D, 0.0D};
    private static final int[] STEP_COUNT = {11, 14, 0};

    private HauntChoreography() {}

    public static int clampStage(int stage) {
        return Math.max(0, Math.min(MAX_STAGE, stage));
    }

    public static boolean isWhisper(int stage) {
        return clampStage(stage) == STAGE_WHISPER;
    }

    public static boolean isCircle(int stage) {
        return !isWhisper(stage);
    }

    public static double startDistance(int stage) {
        return START_DISTANCE[clampStage(stage)];
    }

    public static double endDistance(int stage) {
        return END_DISTANCE[clampStage(stage)];
    }

    public static int stepCount(int stage) {
        return STEP_COUNT[clampStage(stage)];
    }
}
