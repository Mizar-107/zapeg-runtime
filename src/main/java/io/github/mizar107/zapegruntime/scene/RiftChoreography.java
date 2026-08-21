package io.github.mizar107.zapegruntime.scene;

/**
 * The manifestation rift: a target-private, photosensitivity-safe world-bend
 * that escalates through four stages. Pure math so pulse rates, wash caps
 * and fog yields are unit-testable without a GUI or a shader pack.
 *
 * <p>Stage 0 {@code eclipse} — bounded near-black overlay and a strong
 * vanilla fog pull. Shader packs that own terrain fog override the plane
 * scale (existing yield policy); the overlay still darkens the HUD layer.
 * Stage 1 {@code tear} — the old chroma-break recording fault, now a stage
 * rather than a sibling profile. Stage 2 {@code unmoor} — slow hue rotate,
 * chromatic smear and a crawling warp; never a strobe. Stage 3 {@code
 * witness} — vanilla HUD overlays are cancelled and two steady ember eyes
 * fill the screen.
 */
public final class RiftChoreography {

    public static final int STAGE_ECLIPSE = 0;
    public static final int STAGE_TEAR = 1;
    public static final int STAGE_UNMOOR = 2;
    public static final int STAGE_WITNESS = 3;
    public static final int MAX_STAGE = STAGE_WITNESS;
    public static final int STAGE_COUNT = MAX_STAGE + 1;

    /**
     * Slowest pulse used by any rift overlay, in ticks. 50 ticks is 0.4 Hz
     * at 20 TPS — far under the 3-flashes-per-second photosensitivity line.
     */
    public static final double MIN_PULSE_TICKS = 50.0D;
    /**
     * Full-screen wash never exceeds this 0..255 alpha. Raised so the
     * eclipse (with the fog pull live) actually reads near-black at peak —
     * roughly 216 effective after the stage's 0.92 wash scale. Depth is
     * photosensitivity-safe: the hazard is flash frequency, and every wash
     * rides a slow {@code >= MIN_PULSE_TICKS} sine, never a strobe.
     */
    public static final int MAX_WASH_ALPHA = 235;
    /**
     * Vanilla fog far-plane pull for the eclipse. Shader packs that replace
     * terrain fog ignore this; the overlay is the darkness that survives.
     */
    public static final float ECLIPSE_FOG_FAR_SCALE = 0.42F;

    // The eclipse breathes on the slowest ease of the family: the darkness
    // is a hold that swells, not a flicker.
    private static final double[] PULSE_TICKS = {70.0D, 50.0D, 62.0D, 70.0D};
    private static final double[] WASH_SCALE = {0.92D, 0.55D, 0.70D, 0.80D};

    private RiftChoreography() {}

    public static int clampStage(int stage) {
        return Math.max(0, Math.min(MAX_STAGE, stage));
    }

    public static boolean isEclipse(int stage) {
        return clampStage(stage) == STAGE_ECLIPSE;
    }

    public static boolean isTear(int stage) {
        return clampStage(stage) == STAGE_TEAR;
    }

    public static boolean isUnmoor(int stage) {
        return clampStage(stage) == STAGE_UNMOOR;
    }

    public static boolean isWitness(int stage) {
        return clampStage(stage) == STAGE_WITNESS;
    }

    public static boolean hidesHud(int stage) {
        return isWitness(stage);
    }

    /** Overlay pulse period in ticks; always {@code >= MIN_PULSE_TICKS}. */
    public static double pulseTicks(int stage) {
        return PULSE_TICKS[clampStage(stage)];
    }

    /** 0..1 wash intensity scale before the scene envelope is applied. */
    public static double washScale(int stage) {
        return WASH_SCALE[clampStage(stage)];
    }

    /**
     * Extra vanilla fog pull-in on top of the shared ambience dip. Zero for
     * every stage except eclipse, so chroma/unmoor/witness do not also drown
     * the world in fog.
     */
    public static float extraFogDip(int stage) {
        return isEclipse(stage) ? 1.0F : 0.0F;
    }

    /**
     * Slow hue angle in degrees for the unmoor wash. A full rotation takes
     * well over ten seconds — a crawl, never a flicker.
     */
    public static double hueDegrees(double bodyAgeTicks, long seed) {
        if (!Double.isFinite(bodyAgeTicks)) {
            return 0.0D;
        }
        double phase = Math.floorMod(seed, 360L);
        double hue = bodyAgeTicks * 0.55D + phase;
        return hue - Math.floor(hue / 360.0D) * 360.0D;
    }

    /**
     * Horizontal warp displacement in GUI pixels: a slow sine of screen
     * position and age, capped so it reads as a smear rather than a shake.
     */
    public static int warpPixels(double bodyAgeTicks, int row, long seed) {
        if (!Double.isFinite(bodyAgeTicks)) {
            return 0;
        }
        double phase = (seed & 31L) * 0.19D;
        double wave = Math.sin(row * 0.045D + bodyAgeTicks * 0.11D + phase);
        return (int) Math.round(wave * 6.0D);
    }
}
