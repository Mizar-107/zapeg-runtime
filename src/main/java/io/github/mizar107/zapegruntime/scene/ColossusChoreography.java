package io.github.mizar107.zapegruntime.scene;

/**
 * The far colossus: a ~100-block silhouette that stands on the horizon and
 * comes closer each time the Director escalates the encounter. Pure math so
 * the escalation contract (distances, fog wrongness, footfall cadence, shake
 * caps) is unit-testable without a level, a camera or a network.
 *
 * <p>The figure is render-only: no entity, no hitbox, no AI, no loot, and the
 * stage lives only on the wire descriptor and in the Director's SQLite — the
 * runtime itself persists nothing.
 */
public final class ColossusChoreography {

    /** Five approach stages: horizon silhouette → towering near-presence. */
    public static final int MAX_STAGE = 4;
    public static final int STAGE_COUNT = MAX_STAGE + 1;
    /** Total silhouette height; at arm's-distance stages it fills the sky. */
    public static final double HEIGHT_BLOCKS = 96.0D;
    /** The body fades in before the first footfall lands. */
    public static final int FIRST_STEP_TICK = 18;
    /** One footfall every 2.2 seconds: slow enough to feel enormous. */
    public static final int STEP_INTERVAL_TICKS = 44;
    /** The finale's held watch after its last step, before it is simply gone. */
    public static final int FINALE_WATCH_TICKS = 60;
    /** Each step closes this fraction of the stage distance... */
    public static final double STEP_ADVANCE_FRACTION = 0.025D;
    /** ...but a single scene never advances more than this, in blocks. */
    public static final double MAX_TOTAL_ADVANCE_BLOCKS = 30.0D;
    /** Fraction of the client's own visible range the far stages may use. */
    public static final double RENDER_CLIP_SAFETY = 0.9D;
    /** Only the two far stages ever need the render-distance clamp. */
    public static final int MAX_CLAMPED_STAGE = 1;

    private static final double[] STAGE_DISTANCES = {280.0D, 220.0D, 160.0D, 110.0D, 70.0D};
    // How strongly the silhouette mixes into the fog color. Below 1.0 it is
    // more visible than honest fog would allow — the wrongness dial.
    private static final double[] STAGE_FOG_STRENGTH = {0.97D, 0.92D, 0.85D, 0.74D, 0.60D};
    private static final double[] STAGE_ALPHA = {0.50D, 0.60D, 0.72D, 0.85D, 0.95D};
    private static final double[] STAGE_SHAKE_DEGREES = {0.90D, 1.20D, 1.60D, 2.00D, 2.40D};
    // The finale stops early: two footfalls, then the watch, then nothing.
    private static final int[] STAGE_STEPS = {4, 4, 4, 4, 2};

    // Head box of the slimmed silhouette: spans x -6.5..6.5, y 82..96,
    // z -6.5..6.5; the face is the -z side.
    public static final double HEAD_HALF_WIDTH = 6.5D;
    public static final double HEAD_MIN_Y = 82.0D;
    public static final double HEAD_MAX_Y = 96.0D;
    // Eye layout on the head's front face. The spacing is deliberately a
    // little too wide for the head — readable at 280 blocks, and wrong in a
    // way nobody can name.
    public static final double EYE_FACE_Z = -6.5D;
    public static final double EYE_CENTER_Y = 89.0D;
    public static final double EYE_HALF_SPACING = 3.6D;
    public static final double EYE_WIDTH = 2.8D;
    public static final double EYE_HEIGHT = 1.5D;
    /** The narrowest the finale watch ever squeezes the eyes (height scale). */
    public static final double EYE_MIN_NARROW = 0.35D;

    private ColossusChoreography() {}

    public static int clampStage(int stage) {
        return Math.max(0, Math.min(MAX_STAGE, stage));
    }

    public static boolean isFinale(int stage) {
        return clampStage(stage) == MAX_STAGE;
    }

    public static double stageDistance(int stage) {
        return STAGE_DISTANCES[clampStage(stage)];
    }

    public static double fogStrength(int stage) {
        return STAGE_FOG_STRENGTH[clampStage(stage)];
    }

    public static double baseAlpha(int stage) {
        return STAGE_ALPHA[clampStage(stage)];
    }

    /** Peak camera pulse per footfall at this stage, in degrees. */
    public static double shakeDegrees(int stage) {
        return STAGE_SHAKE_DEGREES[clampStage(stage)];
    }

    public static int stepsForStage(int stage) {
        return STAGE_STEPS[clampStage(stage)];
    }

    /** Body tick at which the given footfall lands. */
    public static int stepTick(int stepIndex) {
        return FIRST_STEP_TICK + stepIndex * STEP_INTERVAL_TICKS;
    }

    /**
     * Body tick at which the finale figure is simply gone; -1 for every
     * other stage, where the silhouette instead recedes into the fog with
     * the scene's normal fade-out.
     */
    public static int vanishTick(int stage) {
        if (!isFinale(stage)) {
            return -1;
        }
        return stepTick(stepsForStage(stage) - 1) + FINALE_WATCH_TICKS;
    }

    /** Footfalls that have landed by this body age (0 until the first). */
    public static int elapsedSteps(int stage, double bodyAgeTicks) {
        if (!Double.isFinite(bodyAgeTicks) || bodyAgeTicks < FIRST_STEP_TICK) {
            return 0;
        }
        int elapsed = (int) Math.floor((bodyAgeTicks - FIRST_STEP_TICK) / STEP_INTERVAL_TICKS) + 1;
        return Math.min(elapsed, stepsForStage(stage));
    }

    /** Cumulative approach after the elapsed footfalls, bounded. */
    public static double advanceBlocks(int stage, int elapsedSteps) {
        double raw = Math.min(Math.max(elapsedSteps, 0), stepsForStage(stage))
                * stageDistance(stage) * STEP_ADVANCE_FRACTION;
        return Math.min(raw, MAX_TOTAL_ADVANCE_BLOCKS);
    }

    /**
     * Render-only clamp factor (0..1] for the wire anchor's camera distance.
     * Stages 0–1 are authored beyond the far plane of a low-render-distance
     * client; the renderer pulls the silhouette in along the same bearing to
     * {@code (renderDistanceChunks - 1) * 16 * RENDER_CLIP_SAFETY} blocks
     * and scales the whole body by the same factor, so the angular read —
     * a horizon smudge — is unchanged while the figure survives the clip.
     * Never applied to stages 2–4 (already inside any playable far plane),
     * and never to the wire anchor: the escalation contract stays authored.
     */
    public static double renderApproachFactor(
            int stage, double anchorDistanceBlocks, int renderDistanceChunks) {
        if (clampStage(stage) > MAX_CLAMPED_STAGE
                || !Double.isFinite(anchorDistanceBlocks)
                || anchorDistanceBlocks <= 0.0D) {
            return 1.0D;
        }
        double visibleBlocks = Math.max(16.0D, (renderDistanceChunks - 1) * 16.0D)
                * RENDER_CLIP_SAFETY;
        if (anchorDistanceBlocks <= visibleBlocks) {
            return 1.0D;
        }
        return visibleBlocks / anchorDistanceBlocks;
    }

    /**
     * Slow side-to-side rock as the figure settles after each footfall:
     * alternating per step, decaying within a second, never more than about
     * a degree — at colossus distances that reads as walking sway.
     */
    public static double stepRockDegrees(int stage, double bodyAgeTicks, long seed) {
        int elapsed = elapsedSteps(stage, bodyAgeTicks);
        if (elapsed <= 0) {
            return 0.0D;
        }
        double sinceStep = bodyAgeTicks - stepTick(elapsed - 1);
        double settle = Math.exp(-sinceStep / 16.0D);
        double side = (seed & 1L) == 0L ? 1.0D : -1.0D;
        double alternating = (elapsed & 1) == 0 ? side : -side;
        return 1.1D * alternating * settle * Math.sin(sinceStep * 0.22D);
    }

    /**
     * Finale wrongness: while the colossus holds its watch, the eyes slowly
     * narrow from full height to {@link #EYE_MIN_NARROW}; every other stage
     * keeps them level. Bounded, slow and steady — a narrow, never a blink.
     */
    public static double eyeNarrow(int stage, double bodyAgeTicks) {
        if (!isFinale(stage)) {
            return 1.0D;
        }
        double watchStart = stepTick(stepsForStage(stage) - 1);
        double vanish = vanishTick(stage);
        if (vanish <= watchStart) {
            return 1.0D;
        }
        double t = SceneMath.smoothstep(watchStart, vanish, bodyAgeTicks);
        return 1.0D - (1.0D - EYE_MIN_NARROW) * t;
    }

    /**
     * Silhouette color pulled toward the fog color by {@code factor} (0..1).
     * A factor below the honest fog amount is what lets the figure read
     * through the fog at close stages.
     */
    public static double[] foggedColor(
            double red, double green, double blue, float[] fogColor, double factor) {
        double clamped = Math.max(0.0D, Math.min(1.0D, factor));
        return new double[] {
            red + (fogColor[0] - red) * clamped,
            green + (fogColor[1] - green) * clamped,
            blue + (fogColor[2] - blue) * clamped
        };
    }
}
