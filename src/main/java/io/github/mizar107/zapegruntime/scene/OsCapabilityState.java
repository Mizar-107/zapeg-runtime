package io.github.mizar107.zapegruntime.scene;

import java.util.Arrays;

public enum OsCapabilityState {
    READY(0, "ready"),
    DISABLED(1, "disabled"),
    UNSUPPORTED(2, "unsupported"),
    FAILED(3, "failed");

    private final int wireId;
    private final String serializedName;

    OsCapabilityState(int wireId, String serializedName) {
        this.wireId = wireId;
        this.serializedName = serializedName;
    }

    public int wireId() { return wireId; }
    public String serializedName() { return serializedName; }

    public static OsCapabilityState fromWireId(int wireId) {
        return Arrays.stream(values()).filter(value -> value.wireId == wireId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown OS capability wire id: " + wireId));
    }
}
