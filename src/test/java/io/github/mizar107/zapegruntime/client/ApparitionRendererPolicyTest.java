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
    }

    @Test
    void ownSilhouetteIsOnlyEverRequestedForFigureProfiles() {
        for (SceneProfile profile : SceneProfile.values()) {
            if (ApparitionRenderer.usesOwnSilhouette(profile)) {
                assertTrue(profile.rendersFigure());
            }
        }
    }
}
