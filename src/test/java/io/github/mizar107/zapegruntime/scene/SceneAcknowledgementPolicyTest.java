package io.github.mizar107.zapegruntime.scene;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SceneAcknowledgementPolicyTest {

    @Test
    void visitationRejectsForgedVisibilityAndTerminalGaze() {
        assertFalse(SceneProfile.VISITATION_01.acceptsAcknowledgement(SceneAck.VISIBLE));
        assertFalse(SceneProfile.VISITATION_01.acceptsAcknowledgement(SceneAck.GAZE));
        assertTrue(SceneProfile.VISITATION_01.acceptsAcknowledgement(SceneAck.RECEIVED));
        assertTrue(SceneProfile.VISITATION_01.acceptsAcknowledgement(SceneAck.TIMEOUT));
        assertTrue(SceneProfile.VISITATION_01.acceptsAcknowledgement(SceneAck.ABORTED));
    }

    @Test
    void breachAcceptsGuiProofButCanNeverBeGazeResolved() {
        assertTrue(SceneProfile.BREACH_01.acceptsAcknowledgement(SceneAck.VISIBLE));
        assertFalse(SceneProfile.BREACH_01.acceptsAcknowledgement(SceneAck.GAZE));
        assertTrue(SceneProfile.BREACH_01.acceptsAcknowledgement(SceneAck.RECEIVED));
        assertTrue(SceneProfile.BREACH_01.acceptsAcknowledgement(SceneAck.TIMEOUT));
    }

    @Test
    void gazeIsAllowedOnlyForProfilesWithARealGazeResolution() {
        assertTrue(SceneProfile.ECHO_01.acceptsAcknowledgement(SceneAck.GAZE));
        assertTrue(SceneProfile.SKY_MARK_01.acceptsAcknowledgement(SceneAck.GAZE));
        assertTrue(SceneProfile.FALSE_PASSAGE_01.acceptsAcknowledgement(SceneAck.GAZE));
        assertFalse(SceneProfile.FOOTSTEPS_01.acceptsAcknowledgement(SceneAck.GAZE));
        assertFalse(SceneProfile.COLOSSUS_01.acceptsAcknowledgement(SceneAck.GAZE));
        assertFalse(SceneProfile.RIFT_01.acceptsAcknowledgement(SceneAck.GAZE));
    }
}
