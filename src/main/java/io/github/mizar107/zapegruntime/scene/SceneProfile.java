package io.github.mizar107.zapegruntime.scene;

import java.util.Arrays;
import java.util.Locale;

public enum SceneProfile {
    ECHO_01(0, "echo_01", 200, 4.0D);

    private final int wireId;
    private final String serializedName;
    private final int defaultTtlTicks;
    private final double gazeAngleDegrees;

    SceneProfile(
            int wireId,
            String serializedName,
            int defaultTtlTicks,
            double gazeAngleDegrees) {
        this.wireId = wireId;
        this.serializedName = serializedName;
        this.defaultTtlTicks = defaultTtlTicks;
        this.gazeAngleDegrees = gazeAngleDegrees;
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
