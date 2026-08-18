package io.github.mizar107.zapegruntime.scene;

/** Server-side anchor policy selected only by an allowlisted scene profile. */
public enum ScenePlacementMode {
    DISTANT_SAFE_GROUND,
    CLIENT_MOTION_HISTORY,
    LOCAL_CAMERA_FOCUS,
    /**
     * The anchor is simply the target's own position: the profile renders
     * relative to the target (sky, screen) and needs no world placement.
     */
    PLAYER_RELATIVE
}
