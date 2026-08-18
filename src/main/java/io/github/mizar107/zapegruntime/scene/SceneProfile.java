package io.github.mizar107.zapegruntime.scene;

import java.util.Arrays;
import java.util.Locale;

public enum SceneProfile {
    ECHO_01(
            0, "echo_01", 200, 4.0D, 175, true,
            ScenePlacementMode.DISTANT_SAFE_GROUND),
    THRESHOLD_01(
            1, "threshold_01", 160, 6.0D, 110, true,
            ScenePlacementMode.DISTANT_SAFE_GROUND),
    MOTION_ECHO_01(
            2, "motion_echo_01", 220, 5.0D, 225, true,
            ScenePlacementMode.CLIENT_MOTION_HISTORY),
    LIGHT_FAULT_01(
            3, "light_fault_01", 140, 7.0D, 1_500, false,
            ScenePlacementMode.LOCAL_CAMERA_FOCUS),
    // A silhouette that only reads at the edge of vision: the narrow gaze cone
    // and blink-length dwell make a direct look resolve it almost instantly.
    PERIPHERAL_01(
            4, "peripheral_01", 140, 9.0D, 80, true,
            ScenePlacementMode.DISTANT_SAFE_GROUND),
    // Sound-only: footsteps circle closer with no figure to look at, so the
    // dwell must outlast the TTL and the scene always ends in silence (TIMEOUT).
    FOOTSTEPS_01(
            5, "footsteps_01", 160, 360.0D, 60_000, false,
            ScenePlacementMode.DISTANT_SAFE_GROUND);

    private final int wireId;
    private final String serializedName;
    private final int defaultTtlTicks;
    private final double gazeAngleDegrees;
    private final int gazeDwellMillis;
    private final boolean rendersFigure;
    private final ScenePlacementMode placementMode;

    SceneProfile(
            int wireId,
            String serializedName,
            int defaultTtlTicks,
            double gazeAngleDegrees,
            int gazeDwellMillis,
            boolean rendersFigure,
            ScenePlacementMode placementMode) {
        this.wireId = wireId;
        this.serializedName = serializedName;
        this.defaultTtlTicks = defaultTtlTicks;
        this.gazeAngleDegrees = gazeAngleDegrees;
        this.gazeDwellMillis = gazeDwellMillis;
        this.rendersFigure = rendersFigure;
        this.placementMode = placementMode;
    }

    public int wireId() {
        return wireId;
    }

    public String serializedName() {
        return serializedName;
    }

    public int defaultTtlTicks() {
        return defaultTtlTicks;
    }

    public double gazeAngleDegrees() {
        return gazeAngleDegrees;
    }

    public int gazeDwellMillis() {
        return gazeDwellMillis;
    }

    public boolean rendersFigure() {
        return rendersFigure;
    }

    public boolean usesMotionHistory() {
        return placementMode == ScenePlacementMode.CLIENT_MOTION_HISTORY;
    }

    public ScenePlacementMode placementMode() {
        return placementMode;
    }

    public static SceneProfile fromWireId(int wireId) {
        return Arrays.stream(values())
                .filter(profile -> profile.wireId == wireId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown scene profile wire id: " + wireId));
    }

    public static SceneProfile parse(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(profile -> profile.serializedName.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown scene profile: " + value));
    }
}
