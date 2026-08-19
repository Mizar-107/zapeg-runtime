package io.github.mizar107.zapegruntime.client;

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
}
