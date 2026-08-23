package io.github.mizar107.zapegruntime.scene;

import java.util.Arrays;

public enum OsCleanupState {
    NOT_REQUIRED(0, "not_required"),
    PENDING(1, "pending"),
    APPLIED(2, "applied"),
    FAILED(3, "failed");

    private final int wireId;
    private final String serializedName;

    OsCleanupState(int wireId, String serializedName) {
        this.wireId = wireId;
        this.serializedName = serializedName;
    }

    public int wireId() { return wireId; }
    public String serializedName() { return serializedName; }

    public static OsCleanupState fromWireId(int wireId) {
        return Arrays.stream(values()).filter(value -> value.wireId == wireId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown OS cleanup wire id: " + wireId));
    }
}
