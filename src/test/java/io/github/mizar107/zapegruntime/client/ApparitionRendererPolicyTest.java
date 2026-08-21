package io.github.mizar107.zapegruntime.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mizar107.zapegruntime.scene.SceneProfile;
import org.junit.jupiter.api.Test;

/**
 * Pins the silhouette policy: only the motion echo may wear the target's own
 * skin; the black-figure profiles must keep the generic humanoid silhouette
 * whose texture regions are fully opaque for the baked model layer.
 */
class ApparitionRendererPolicyTest {

    @Test
    void onlyMotionEchoWearsTheTargetsOwnSilhouette() {
        assertFalse(ApparitionRenderer.usesOwnSilhouette(SceneProfile.ECHO_01));
        assertFalse(ApparitionRenderer.usesOwnSilhouette(SceneProfile.THRESHOLD_01));
        assertTrue(ApparitionRenderer.usesOwnSilhouette(SceneProfile.MOTION_ECHO_01));
        assertFalse(ApparitionRenderer.usesOwnSilhouette(SceneProfile.LIGHT_FAULT_01));
        assertFalse(ApparitionRenderer.usesOwnSilhouette(SceneProfile.PERIPHERAL_01));
        assertFalse(ApparitionRenderer.usesOwnSilhouette(SceneProfile.FOOTSTEPS_01));
    }

    @Test
    void ownSilhouetteIsOnlyEverRequestedForFigureProfiles() {
        for (SceneProfile profile : SceneProfile.values()) {
            if (ApparitionRenderer.usesOwnSilhouette(profile)) {
                assertTrue(profile.rendersFigure());
            }
        }
    }

    @Test
    void everyHumanoidFigureCarriesTheGlowingEyes() {
        assertTrue(ApparitionRenderer.hasGlowingEyes(SceneProfile.ECHO_01));
        assertTrue(ApparitionRenderer.hasGlowingEyes(SceneProfile.THRESHOLD_01));
        assertTrue(ApparitionRenderer.hasGlowingEyes(SceneProfile.MOTION_ECHO_01));
        assertTrue(ApparitionRenderer.hasGlowingEyes(SceneProfile.PERIPHERAL_01));
        assertTrue(ApparitionRenderer.hasGlowingEyes(SceneProfile.NEAR_MISS_01));
        // Sound-only, sky, doorway and screen-space profiles render no
        // humanoid figure, so there is no face to light; the colossus draws
        // its own eyes in its dedicated renderer.
        for (SceneProfile profile : SceneProfile.values()) {
            if (ApparitionRenderer.hasGlowingEyes(profile)) {
                assertTrue(profile.rendersFigure());
            }
        }
        assertFalse(ApparitionRenderer.hasGlowingEyes(SceneProfile.FOOTSTEPS_01));
        assertFalse(ApparitionRenderer.hasGlowingEyes(SceneProfile.WHISPER_STEPS_01));
        assertFalse(ApparitionRenderer.hasGlowingEyes(SceneProfile.SKY_MARK_01));
        assertFalse(ApparitionRenderer.hasGlowingEyes(SceneProfile.FALSE_PASSAGE_01));
        assertFalse(ApparitionRenderer.hasGlowingEyes(SceneProfile.LIGHT_FAULT_01));
        assertFalse(ApparitionRenderer.hasGlowingEyes(SceneProfile.CHROMA_BREAK_01));
        assertFalse(ApparitionRenderer.hasGlowingEyes(SceneProfile.COLOSSUS_01));
    }

    @Test
    void thePeripheralDissolveIsWideSlowAndPatient() {
        // S-01: a mouse flick crosses ~25 degrees in a frame or two, so the
        // angular ramp must be wide and the dwell must outlast a saccade.
        assertEquals(1.0D, ApparitionRenderer.PERIPHERAL_RAMP_START_DEGREES);
        assertEquals(40.0D, ApparitionRenderer.PERIPHERAL_RAMP_END_DEGREES);
        assertEquals(5.0D, ApparitionRenderer.PERIPHERAL_ALPHA_LERP_TICKS);
        assertEquals(140, SceneProfile.PERIPHERAL_01.gazeDwellMillis());
    }

    @Test
    void thePresentedPeripheralAlphaEasesInsteadOfPopping() {
        // A one-tick frame moves only a fifth of the way to the target...
        float presented = ApparitionRenderer.presentedPeripheralAlpha(1.0F, 0.0F, 1.0D);
        assertEquals(0.8F, presented, 1.0E-6F);
        // ...so an instant angular collapse takes ~5 ticks to read out.
        for (int frame = 0; frame < 4; frame++) {
            presented = ApparitionRenderer.presentedPeripheralAlpha(presented, 0.0F, 1.0D);
        }
        assertTrue(presented < 0.35F, "five ticks in, the figure has mostly thinned");
        assertTrue(presented > 0.0F, "but it dissolved — it did not blink out");
        // It never overshoots, and a held target converges onto it.
        assertEquals(0.5F, ApparitionRenderer.presentedPeripheralAlpha(0.5F, 0.5F, 1.0D));
        assertEquals(
                0.7F, ApparitionRenderer.presentedPeripheralAlpha(0.2F, 0.7F, 10.0D), 1.0E-6F);
        // A stale frame gap (a fresh scene, a paused renderer) snaps to the
        // target instead of replaying an old figure's fade.
        assertEquals(
                0.9F, ApparitionRenderer.presentedPeripheralAlpha(0.1F, 0.9F, 400.0D));
        assertEquals(0.9F, ApparitionRenderer.presentedPeripheralAlpha(0.1F, 0.9F, 0.0D));
        assertEquals(
                0.9F, ApparitionRenderer.presentedPeripheralAlpha(0.1F, 0.9F, Double.NaN));
    }
}
