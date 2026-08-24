package io.github.mizar107.zapegruntime.director;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mizar107.zapegruntime.scene.OsCapabilityState;
import io.github.mizar107.zapegruntime.scene.OsCleanupState;
import io.github.mizar107.zapegruntime.scene.OsEffect;
import io.github.mizar107.zapegruntime.scene.OsEffectOutcome;
import io.github.mizar107.zapegruntime.scene.OsEffectReason;
import io.github.mizar107.zapegruntime.scene.OsFallbackState;
import io.github.mizar107.zapegruntime.scene.OsPrimaryState;
import io.github.mizar107.zapegruntime.scene.OsScareReport;
import io.github.mizar107.zapegruntime.scene.SceneAck;
import io.github.mizar107.zapegruntime.scene.SceneProfile;
import io.github.mizar107.zapegruntime.story.StoryFactType;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;

class DirectorPresentationPolicyTest {

    @Test
    void completedFactAcceptsOnlyEstablishedSuccessfulTerminalAcks() {
        for (SceneAck acknowledgement : SceneAck.values()) {
            DirectorPresentationPolicy.Proof expected = switch (acknowledgement) {
                case GAZE -> DirectorPresentationPolicy.Proof.GAZE;
                case TIMEOUT -> DirectorPresentationPolicy.Proof.TIMEOUT;
                default -> DirectorPresentationPolicy.Proof.NONE;
            };
            assertEquals(expected, DirectorPresentationPolicy.acknowledgementProof(
                    StoryFactType.SCENE_COMPLETED,
                    SceneProfile.BREACH_01,
                    acknowledgement));
        }
    }

    @Test
    void presentedFactNeedsVisibleFromAProfileWithPresentationSemantics() {
        assertEquals(
                DirectorPresentationPolicy.Proof.VISIBLE,
                DirectorPresentationPolicy.acknowledgementProof(
                        StoryFactType.SCENE_PRESENTED,
                        SceneProfile.PERIPHERAL_01,
                        SceneAck.VISIBLE));
        assertEquals(
                DirectorPresentationPolicy.Proof.VISIBLE,
                DirectorPresentationPolicy.acknowledgementProof(
                        StoryFactType.SCENE_PRESENTED,
                        SceneProfile.SKY_MARK_01,
                        SceneAck.VISIBLE));
        assertEquals(
                DirectorPresentationPolicy.Proof.VISIBLE,
                DirectorPresentationPolicy.acknowledgementProof(
                        StoryFactType.SCENE_PRESENTED,
                        SceneProfile.COLOSSUS_01,
                        SceneAck.VISIBLE));
        assertEquals(
                DirectorPresentationPolicy.Proof.NONE,
                DirectorPresentationPolicy.acknowledgementProof(
                        StoryFactType.SCENE_PRESENTED,
                        SceneProfile.RIFT_01,
                        SceneAck.VISIBLE));
        assertEquals(
                DirectorPresentationPolicy.Proof.NONE,
                DirectorPresentationPolicy.acknowledgementProof(
                        StoryFactType.SCENE_PRESENTED,
                        SceneProfile.PERIPHERAL_01,
                        SceneAck.TIMEOUT));
    }

    @Test
    void visitationPresentationNeedsAcceptedFallbackAppliedEvidence() {
        assertEquals(
                DirectorPresentationPolicy.Proof.FALLBACK_APPLIED,
                DirectorPresentationPolicy.fallbackProof(
                        StoryFactType.SCENE_PRESENTED,
                        SceneProfile.VISITATION_01,
                        report(OsFallbackState.APPLIED)));
        assertEquals(
                DirectorPresentationPolicy.Proof.NONE,
                DirectorPresentationPolicy.fallbackProof(
                        StoryFactType.SCENE_PRESENTED,
                        SceneProfile.VISITATION_01,
                        report(OsFallbackState.REQUESTED)));
        assertEquals(
                DirectorPresentationPolicy.Proof.NONE,
                DirectorPresentationPolicy.fallbackProof(
                        StoryFactType.SCENE_COMPLETED,
                        SceneProfile.VISITATION_01,
                        report(OsFallbackState.APPLIED)));
        assertEquals(
                DirectorPresentationPolicy.Proof.NONE,
                DirectorPresentationPolicy.fallbackProof(
                        StoryFactType.SCENE_PRESENTED,
                        SceneProfile.BREACH_01,
                        report(OsFallbackState.APPLIED)));
    }

    @Test
    void tickOnlyRiftAndVisitationAreExcludedFromVisibleProof() {
        assertFalse(DirectorPresentationPolicy.visibleMeansPresented(SceneProfile.RIFT_01));
        assertFalse(DirectorPresentationPolicy.visibleMeansPresented(SceneProfile.VISITATION_01));
        assertTrue(DirectorPresentationPolicy.visibleMeansPresented(SceneProfile.PERIPHERAL_01));
        assertTrue(DirectorPresentationPolicy.visibleMeansPresented(SceneProfile.SKY_MARK_01));
        assertTrue(DirectorPresentationPolicy.visibleMeansPresented(SceneProfile.COLOSSUS_01));
    }

    private static OsScareReport report(OsFallbackState fallback) {
        EnumMap<OsEffect, OsEffectOutcome> outcomes = new EnumMap<>(OsEffect.class);
        for (OsEffect effect : OsEffect.values()) {
            outcomes.put(effect, new OsEffectOutcome(
                    effect,
                    OsCapabilityState.READY,
                    OsEffectReason.NONE,
                    OsPrimaryState.NOT_REQUESTED,
                    OsEffectReason.NONE,
                    fallback,
                    OsEffectReason.NONE,
                    OsCleanupState.NOT_REQUIRED,
                    OsEffectReason.NONE));
        }
        return OsScareReport.from(outcomes);
    }
}
