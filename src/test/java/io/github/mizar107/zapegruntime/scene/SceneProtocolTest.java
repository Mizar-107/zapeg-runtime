package io.github.mizar107.zapegruntime.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mizar107.zapegruntime.network.SceneNetwork;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SceneProtocolTest {

    @Test
    void profileWireIdsAndNamesRoundTrip() {
        for (SceneProfile profile : SceneProfile.values()) {
            assertEquals(profile, SceneProfile.fromWireId(profile.wireId()));
            assertEquals(
                    profile,
                    SceneProfile.parse(profile.serializedName().toUpperCase(Locale.ROOT)));
        }
        assertEquals(
                SceneProfile.values().length,
                Arrays.stream(SceneProfile.values())
                        .map(SceneProfile::wireId)
                        .collect(Collectors.toSet())
                        .size());
        assertThrows(IllegalArgumentException.class, () -> SceneProfile.fromWireId(255));
        assertThrows(IllegalArgumentException.class, () -> SceneProfile.parse("unknown"));
    }

    @Test
    void versionTwoProfileIdsRemainExplicitAndBounded() {
        assertEquals("2", SceneNetwork.PROTOCOL);
        assertEquals(0, SceneProfile.ECHO_01.wireId());
        assertEquals(1, SceneProfile.THRESHOLD_01.wireId());
        assertEquals(2, SceneProfile.MOTION_ECHO_01.wireId());
        assertEquals(3, SceneProfile.LIGHT_FAULT_01.wireId());

        assertEquals(
                ScenePlacementMode.DISTANT_SAFE_GROUND,
                SceneProfile.ECHO_01.placementMode());
        assertEquals(
                ScenePlacementMode.DISTANT_SAFE_GROUND,
                SceneProfile.THRESHOLD_01.placementMode());
        assertEquals(
                ScenePlacementMode.CLIENT_MOTION_HISTORY,
                SceneProfile.MOTION_ECHO_01.placementMode());
        assertEquals(
                ScenePlacementMode.LOCAL_CAMERA_FOCUS,
                SceneProfile.LIGHT_FAULT_01.placementMode());

        assertTrue(SceneProfile.ECHO_01.rendersFigure());
        assertTrue(SceneProfile.THRESHOLD_01.rendersFigure());
        assertTrue(SceneProfile.MOTION_ECHO_01.rendersFigure());
        assertFalse(SceneProfile.LIGHT_FAULT_01.rendersFigure());
        assertTrue(SceneProfile.MOTION_ECHO_01.usesMotionHistory());
        assertFalse(SceneProfile.ECHO_01.usesMotionHistory());
        assertEquals(1_500, SceneProfile.LIGHT_FAULT_01.gazeDwellMillis());

        for (SceneProfile profile : SceneProfile.values()) {
            assertTrue(profile.defaultTtlTicks() >= SceneDescriptor.MIN_TTL_TICKS);
            assertTrue(profile.defaultTtlTicks() <= SceneDescriptor.MAX_TTL_TICKS);
            assertTrue(profile.gazeAngleDegrees() > 0.0D);
            assertTrue(profile.gazeAngleDegrees() <= 15.0D);
            assertTrue(profile.gazeDwellMillis() >= 75);
            assertTrue(profile.gazeDwellMillis() <= 2_000);
        }
    }

    @Test
    void acknowledgementAndCancelWireIdsRoundTrip() {
        for (SceneAck acknowledgement : SceneAck.values()) {
            assertEquals(
                    acknowledgement,
                    SceneAck.fromWireId(acknowledgement.wireId()));
        }
        for (CancelReason reason : CancelReason.values()) {
            assertEquals(reason, CancelReason.fromWireId(reason.wireId()));
        }
        assertThrows(IllegalArgumentException.class, () -> SceneAck.fromWireId(255));
        assertThrows(IllegalArgumentException.class, () -> CancelReason.fromWireId(255));
    }
}
