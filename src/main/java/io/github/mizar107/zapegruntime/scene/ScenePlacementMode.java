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
    PLAYER_RELATIVE,
    /**
     * A seeded direction at a stage-determined distance far beyond loaded
     * chunks, with the anchor's height pinned to the target's feet. Only for
     * render-only colossus silhouettes: nothing collides, so no ground scan
     * is needed, and at those distances fog hides the implied ground line.
     */
    HORIZON
}
