package io.github.mizar107.zapegruntime.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mizar107.zapegruntime.scene.SceneProfile;
import org.junit.jupiter.api.Test;

class ClientSceneManagerPolicyTest {

    @Test
    void visitationCannotClaimGenericRenderVisibility() {
        assertFalse(ClientSceneManager.usesRenderObservation(SceneProfile.VISITATION_01));
        assertFalse(ClientSceneManager.usesRenderObservation(SceneProfile.FOOTSTEPS_01));
        assertFalse(ClientSceneManager.usesRenderObservation(SceneProfile.WHISPER_STEPS_01));
        assertTrue(ClientSceneManager.usesRenderObservation(SceneProfile.ECHO_01));
        assertTrue(ClientSceneManager.usesRenderObservation(SceneProfile.RIFT_01));
    }
}
