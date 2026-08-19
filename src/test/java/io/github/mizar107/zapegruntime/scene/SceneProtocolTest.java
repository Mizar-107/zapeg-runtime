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
    void versionSixProfileIdsRemainExplicitAndBounded() {
        assertEquals("6", SceneNetwork.PROTOCOL);
        assertEquals(0, SceneProfile.ECHO_01.wireId());
        assertEquals(1, SceneProfile.THRESHOLD_01.wireId());
        assertEquals(2, SceneProfile.MOTION_ECHO_01.wireId());
        assertEquals(3, SceneProfile.LIGHT_FAULT_01.wireId());
        assertEquals(4, SceneProfile.PERIPHERAL_01.wireId());
        assertEquals(5, SceneProfile.FOOTSTEPS_01.wireId());
        assertEquals(6, SceneProfile.SKY_MARK_01.wireId());
        assertEquals(7, SceneProfile.FALSE_PASSAGE_01.wireId());
        assertEquals(8, SceneProfile.CHROMA_BREAK_01.wireId());
        assertEquals(9, SceneProfile.NEAR_MISS_01.wireId());
        assertEquals(10, SceneProfile.WHISPER_STEPS_01.wireId());
        assertEquals(11, SceneProfile.COLOSSUS_01.wireId());
        assertEquals(12, SceneProfile.VISITATION_01.wireId());

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
        assertEquals(
                ScenePlacementMode.DISTANT_SAFE_GROUND,
                SceneProfile.PERIPHERAL_01.placementMode());
        assertEquals(
                ScenePlacementMode.DISTANT_SAFE_GROUND,
                SceneProfile.FOOTSTEPS_01.placementMode());
        assertEquals(
                ScenePlacementMode.PLAYER_RELATIVE,
                SceneProfile.SKY_MARK_01.placementMode());
        assertEquals(
                ScenePlacementMode.DISTANT_SAFE_GROUND,
                SceneProfile.FALSE_PASSAGE_01.placementMode());
        assertEquals(
                ScenePlacementMode.PLAYER_RELATIVE,
                SceneProfile.CHROMA_BREAK_01.placementMode());
        assertEquals(
                ScenePlacementMode.CLIENT_MOTION_HISTORY,
                SceneProfile.NEAR_MISS_01.placementMode());
        assertEquals(
                ScenePlacementMode.CLIENT_MOTION_HISTORY,
                SceneProfile.WHISPER_STEPS_01.placementMode());
        assertEquals(
                ScenePlacementMode.HORIZON,
                SceneProfile.COLOSSUS_01.placementMode());

        assertTrue(SceneProfile.ECHO_01.rendersFigure());
        assertTrue(SceneProfile.THRESHOLD_01.rendersFigure());
        assertTrue(SceneProfile.MOTION_ECHO_01.rendersFigure());
        assertFalse(SceneProfile.LIGHT_FAULT_01.rendersFigure());
        assertTrue(SceneProfile.PERIPHERAL_01.rendersFigure());
        assertFalse(SceneProfile.FOOTSTEPS_01.rendersFigure());
        assertFalse(SceneProfile.SKY_MARK_01.rendersFigure());
        assertFalse(SceneProfile.FALSE_PASSAGE_01.rendersFigure());
        assertFalse(SceneProfile.CHROMA_BREAK_01.rendersFigure());
        assertTrue(SceneProfile.NEAR_MISS_01.rendersFigure());
        assertFalse(SceneProfile.WHISPER_STEPS_01.rendersFigure());
        // The colossus has its own silhouette renderer, not the humanoid path.
        assertFalse(SceneProfile.COLOSSUS_01.rendersFigure());
        assertTrue(SceneProfile.MOTION_ECHO_01.usesMotionHistory());
        assertFalse(SceneProfile.ECHO_01.usesMotionHistory());
        assertFalse(SceneProfile.FOOTSTEPS_01.usesMotionHistory());
        assertTrue(SceneProfile.NEAR_MISS_01.usesMotionHistory());
        assertTrue(SceneProfile.WHISPER_STEPS_01.usesMotionHistory());
        assertFalse(SceneProfile.SKY_MARK_01.usesMotionHistory());
        assertEquals(1_500, SceneProfile.LIGHT_FAULT_01.gazeDwellMillis());

        for (SceneProfile profile : SceneProfile.values()) {
            assertTrue(profile.defaultTtlTicks() >= SceneDescriptor.MIN_TTL_TICKS);
            assertTrue(profile.defaultTtlTicks() <= SceneDescriptor.MAX_TTL_TICKS);
            // The prelude is a dip before the body, never the whole scene.
            assertTrue(profile.preludeTicks() >= 0);
            assertTrue(profile.preludeTicks() < profile.defaultTtlTicks() / 2);
            assertTrue(profile.uneaseLevel() >= 0);
            assertTrue(profile.uneaseLevel() <= CameraUnease.MAX_LEVEL);
            // An encore is either absent or a real false all-clear gap.
            assertTrue(profile.encoreDelayTicks() == 0 || profile.encoreDelayTicks() >= 100);
            if (profile.gazeAngleDegrees() >= 360.0D) {
                // Sound-only and approach-resolved profiles carry no gaze.
                continue;
            }
            assertTrue(profile.gazeAngleDegrees() > 0.0D);
            assertTrue(profile.gazeAngleDegrees() <= 15.0D);
            assertTrue(profile.gazeDwellMillis() >= 75);
            assertTrue(profile.gazeDwellMillis() <= 2_000);
        }
    }

    @Test
    void occupancyAlwaysCoversTheHeldEncoreAcknowledgement() {
        for (SceneProfile profile : SceneProfile.values()) {
            int occupancy = profile.occupancyTicks(profile.defaultTtlTicks());
            assertTrue(occupancy >= profile.defaultTtlTicks() + 20);
            if (profile.encoreDelayTicks() > 0) {
                assertEquals(
                        profile.defaultTtlTicks() + 20
                                + profile.encoreDelayTicks()
                                + SceneProfile.ENCORE_BEAT_TICKS,
                        occupancy);
            } else {
                assertEquals(profile.defaultTtlTicks() + 20, occupancy);
            }
            // Even the longest Director-scaled TTL stays bounded.
            assertTrue(profile.occupancyTicks(SceneDescriptor.MAX_TTL_TICKS) <= 2_000);
        }
    }

    @Test
    void soundOnlyFootstepsCanNeverBeGazeResolved() {
        // The dwell (in ticks) must outlast the TTL so the scene always ends
        // in silence as TIMEOUT; the 360-degree cone means no look direction
        // ever counts as "looking at it" anyway.
        assertTrue(SceneProfile.FOOTSTEPS_01.gazeAngleDegrees() >= 360.0D);
        assertTrue(
                SceneProfile.FOOTSTEPS_01.gazeDwellMillis() / 50
                        >= SceneProfile.FOOTSTEPS_01.defaultTtlTicks());
    }

    @Test
    void soundOnlyWhisperStepsCanNeverBeGazeResolved() {
        // Same contract as footsteps: the replayed steps simply stop.
        assertTrue(SceneProfile.WHISPER_STEPS_01.gazeAngleDegrees() >= 360.0D);
        assertTrue(
                SceneProfile.WHISPER_STEPS_01.gazeDwellMillis() / 50
                        >= SceneProfile.WHISPER_STEPS_01.defaultTtlTicks());
    }

    @Test
    void falsePassageIsResolvedByApproachNeverByGaze() {
        // A 360-degree cone with an outlasting dwell means looking at the
        // doorway never resolves it; only walking up to it does (the client
        // maps that collapse onto the GAZE acknowledgement), and if the
        // target never commits the scene ends as TIMEOUT.
        assertTrue(SceneProfile.FALSE_PASSAGE_01.gazeAngleDegrees() >= 360.0D);
        assertTrue(
                SceneProfile.FALSE_PASSAGE_01.gazeDwellMillis() / 50
                        >= SceneProfile.FALSE_PASSAGE_01.defaultTtlTicks());
        // The false all-clear: the folded doorway gets one last beat later.
        assertTrue(SceneProfile.FALSE_PASSAGE_01.encoreDelayTicks() >= 100);
    }

    @Test
    void colossusIsNeverGazeResolvedAndHasNoEncore() {
        // The colossus is witnessed, never studied: no look direction counts
        // as gazing, the dwell outlasts the TTL, and the scene ends as
        // TIMEOUT (or a cleanup cancel) with no false all-clear after it.
        assertTrue(SceneProfile.COLOSSUS_01.gazeAngleDegrees() >= 360.0D);
        assertTrue(
                SceneProfile.COLOSSUS_01.gazeDwellMillis() / 50
                        >= SceneProfile.COLOSSUS_01.defaultTtlTicks());
        assertEquals(0, SceneProfile.COLOSSUS_01.encoreDelayTicks());
        // The heavy camera path is selected by the strongest unease tier.
        assertEquals(CameraUnease.MAX_LEVEL, SceneProfile.COLOSSUS_01.uneaseLevel());
        // The body must outlast the full finale timeline: last footfall, the
        // held watch, and the vanish, with room to settle afterwards.
        int finaleVanish = ColossusChoreography.vanishTick(ColossusChoreography.MAX_STAGE);
        assertTrue(finaleVanish > 0);
        assertTrue(
                SceneProfile.COLOSSUS_01.defaultTtlTicks()
                                - SceneProfile.COLOSSUS_01.preludeTicks()
                        > finaleVanish);
    }

    @Test
    void chromaBreakStaysInsideThePhotosensitivityBudget() {
        // The tear pulse is a 45-tick sine (~0.44 Hz), far under the
        // 3-flashes-per-second threshold, and the scene is short and capped.
        assertTrue(SceneProfile.CHROMA_BREAK_01.defaultTtlTicks() <= 200);
        assertEquals(SceneProfile.CHROMA_BREAK_01.uneaseLevel(), CameraUnease.MAX_LEVEL);
        assertEquals(0, SceneProfile.CHROMA_BREAK_01.preludeTicks());
        assertEquals(0, SceneProfile.CHROMA_BREAK_01.encoreDelayTicks());
    }

    @Test
    void descriptorAcceptsDirectorScaledTtlOverrides() {
        // Phase scaling can push a default past the old 240-tick bound; the
        // descriptor bound is the wire contract and must accept the full
        // server-clamped override range.
        assertEquals(1200, SceneDescriptor.MAX_TTL_TICKS);
        assertEquals(
                SceneDescriptor.MAX_TTL_TICKS,
                io.github.mizar107.zapegruntime.server.SceneServerManager.MAX_TTL_TICKS);
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
