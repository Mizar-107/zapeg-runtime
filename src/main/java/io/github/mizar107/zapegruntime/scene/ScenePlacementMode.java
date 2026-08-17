package io.github.mizar107.zapegruntime.scene;

/** Server-side anchor policy selected only by an allowlisted scene profile. */
public enum ScenePlacementMode {
    DISTANT_SAFE_GROUND,
    CLIENT_MOTION_HISTORY,
    LOCAL_CAMERA_FOCUS
}
