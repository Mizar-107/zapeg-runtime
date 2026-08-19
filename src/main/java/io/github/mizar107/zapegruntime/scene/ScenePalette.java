package io.github.mizar107.zapegruntime.scene;

/**
 * The signature motif shared by every figure scene: ember-orange eyes on a
 * dark silhouette. Pure constants and envelope math so the client renderers
 * and the tests share one source of truth.
 *
 * <p>The glow is steady on purpose: eyes never flash, strobe or pulse fast,
 * so the motif stays photosensitivity-safe. The only motion is a slow,
 * bounded narrowing while the colossus holds its finale watch.
 */
public final class ScenePalette {

    private ScenePalette() {}

    /** Canonical eye color: a warm ember orange, clearly not red or yellow. */
    public static final float EYE_RED = 1.0F;
    public static final float EYE_GREEN = 0.42F;
    public static final float EYE_BLUE = 0.06F;

    /** Halo alpha relative to the eye core alpha. */
    public static final float EYE_HALO_ALPHA_SCALE = 0.18F;
    /** Halo size relative to the eye core, per axis. */
    public static final float EYE_HALO_SIZE_SCALE = 2.1F;

    /** Body envelope below which the eyes finally start to fade. */
    public static final double EYE_HOLD_ENVELOPE = 0.35D;

    /**
     * Eyes are the last thing visible: they hold at full strength while the
     * body fades, then dim over the final stretch of the envelope.
     */
    public static double eyeHold(double bodyEnvelope) {
        if (!Double.isFinite(bodyEnvelope)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, bodyEnvelope / EYE_HOLD_ENVELOPE));
    }

    /**
     * Eyes belong to the figure's face: they fade out as the camera leaves
     * the front hemisphere instead of shining through the back of the head.
     * {@code cosine} is the normalized dot between the figure's facing and
     * the horizontal anchor→camera vector.
     */
    public static double frontality(double cosine) {
        return SceneMath.smoothstep(-0.02D, 0.22D, cosine);
    }
}
