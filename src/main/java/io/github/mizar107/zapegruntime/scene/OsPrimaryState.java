package io.github.mizar107.zapegruntime.scene;

import java.util.Arrays;

public enum OsPrimaryState {
    NOT_REQUESTED(0, "not_requested"),
    REQUESTED(1, "requested"),
    APPLIED(2, "applied"),
    FAILED(3, "failed");

    private final int wireId;
    private final String serializedName;

    OsPrimaryState(int wireId, String serializedName) {
        this.wireId = wireId;
        this.serializedName = serializedName;
    }

    public int wireId() { return wireId; }
    public String serializedName() { return serializedName; }

    public static OsPrimaryState fromWireId(int wireId) {
        return Arrays.stream(values()).filter(value -> value.wireId == wireId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown OS primary wire id: " + wireId));
    }
}
