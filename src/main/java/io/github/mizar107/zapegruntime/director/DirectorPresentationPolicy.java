package io.github.mizar107.zapegruntime.director;

import io.github.mizar107.zapegruntime.scene.OsEffect;
import io.github.mizar107.zapegruntime.scene.OsFallbackState;
import io.github.mizar107.zapegruntime.scene.OsScareReport;
import io.github.mizar107.zapegruntime.scene.SceneAck;
import io.github.mizar107.zapegruntime.scene.SceneProfile;
import io.github.mizar107.zapegruntime.story.StoryFactType;

/** Pure fail-closed classifier for accepted active-scene evidence. */
public final class DirectorPresentationPolicy {

    private DirectorPresentationPolicy() {}

    public static Proof acknowledgementProof(
            StoryFactType factType, SceneProfile profile, SceneAck acknowledgement) {
        if (factType == StoryFactType.SCENE_COMPLETED) {
            return switch (acknowledgement) {
                case GAZE -> Proof.GAZE;
                case TIMEOUT -> Proof.TIMEOUT;
                default -> Proof.NONE;
            };
        }
        if (factType != StoryFactType.SCENE_PRESENTED
                || acknowledgement != SceneAck.VISIBLE
                || !visibleMeansPresented(profile)) {
            return Proof.NONE;
        }
        return Proof.VISIBLE;
    }

    /**
     * Profiles in this allowlist issue VISIBLE only from their established
     * render/sound presentation hook. RIFT is deliberately absent: its client
     * currently acknowledges at the first body tick rather than after drawing.
     */
    public static boolean visibleMeansPresented(SceneProfile profile) {
        return switch (profile) {
            case ECHO_01,
                    THRESHOLD_01,
                    MOTION_ECHO_01,
                    LIGHT_FAULT_01,
                    PERIPHERAL_01,
                    FOOTSTEPS_01,
                    SKY_MARK_01,
                    FALSE_PASSAGE_01,
                    CHROMA_BREAK_01,
                    NEAR_MISS_01,
                    WHISPER_STEPS_01,
                    COLOSSUS_01,
                    BREACH_01 -> true;
            case VISITATION_01, RIFT_01 -> false;
        };
    }

    /** Accepted visitation status is proof only after its in-game fallback was applied. */
    public static Proof fallbackProof(
            StoryFactType factType, SceneProfile profile, OsScareReport report) {
        if (factType != StoryFactType.SCENE_PRESENTED
                || profile != SceneProfile.VISITATION_01
                || report == null) {
            return Proof.NONE;
        }
        for (OsEffect effect : OsEffect.values()) {
            if (report.outcome(effect).fallback() == OsFallbackState.APPLIED) {
                return Proof.FALLBACK_APPLIED;
            }
        }
        return Proof.NONE;
    }

    public enum Proof {
        NONE,
        VISIBLE,
        FALLBACK_APPLIED,
        GAZE,
        TIMEOUT
    }
}
