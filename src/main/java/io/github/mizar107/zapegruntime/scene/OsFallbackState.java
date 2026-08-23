package io.github.mizar107.zapegruntime.scene;

import java.util.Arrays;

public enum OsFallbackState {
    NOT_AVAILABLE(0, "not_available"),
    NOT_NEEDED(1, "not_needed"),
    REQUESTED(2, "requested"),
    APPLIED(3, "applied"),
    FAILED(4, "failed"),
    /** Implemented and ready, but no visitation presentation is active. */
    AVAILABLE(5, "available");

    private final int wireId;
    private final String serializedName;

    OsFallbackState(int wireId, String serializedName) {
        this.wireId = wireId;
        this.serializedName = serializedName;
    }

    public int wireId() { return wireId; }
    public String serializedName() { return serializedName; }

    public static OsFallbackState fromWireId(int wireId) {
        return Arrays.stream(values()).filter(value -> value.wireId == wireId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown OS fallback wire id: " + wireId));
    }
}
