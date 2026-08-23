package io.github.mizar107.zapegruntime.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mizar107.zapegruntime.scene.SceneProfile;
import org.junit.jupiter.api.Test;

class ClientSceneManagerPolicyTest {

    @Test
    void visitationCannotClaimGenericRenderVisibility() {
        assertFalse(ClientSceneManager.usesRenderObservation(SceneProfile.VISITATION_01));
        assertFalse(ClientSceneManager.usesRenderObservation(SceneProfile.BREACH_01));
        assertFalse(ClientSceneManager.usesRenderObservation(SceneProfile.FOOTSTEPS_01));
        assertFalse(ClientSceneManager.usesRenderObservation(SceneProfile.WHISPER_STEPS_01));
        assertTrue(ClientSceneManager.usesRenderObservation(SceneProfile.ECHO_01));
        assertTrue(ClientSceneManager.usesRenderObservation(SceneProfile.RIFT_01));
    }

    @Test
    void visitationAndBreachOwnTheGuaranteedScreenSpacePresentation() {
        assertTrue(ClientSceneManager.usesInGamePresentation(SceneProfile.VISITATION_01));
        assertTrue(ClientSceneManager.usesInGamePresentation(SceneProfile.BREACH_01));
        assertTrue(ClientSceneManager.usesBreachPresentation(SceneProfile.VISITATION_01));
        assertTrue(ClientSceneManager.usesBreachPresentation(SceneProfile.BREACH_01));
        assertFalse(ClientSceneManager.usesBreachPresentation(SceneProfile.ECHO_01));
        assertEquals(0, SceneProfile.VISITATION_01.preludeTicks());
        assertEquals(0, SceneProfile.VISITATION_01.uneaseLevel());
        assertTrue(ClientSceneManager.usesInGamePresentation(SceneProfile.ECHO_01));
    }
}
