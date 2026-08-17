package io.github.mizar107.zapegruntime.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SceneProtocolTest {

    @Test
    void profileWireIdsAndNamesRoundTrip() {
        for (SceneProfile profile : SceneProfile.values()) {
            assertEquals(profile, SceneProfile.fromWireId(profile.wireId()));
            assertEquals(profile, SceneProfile.parse(profile.serializedName().toUpperCase()));
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
