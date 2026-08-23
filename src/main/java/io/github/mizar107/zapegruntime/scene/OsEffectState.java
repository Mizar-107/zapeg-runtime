package io.github.mizar107.zapegruntime.scene;

import java.util.Arrays;

/** Capability and attempted-application state for one OS effect. */
public enum OsEffectState {
    READY(0, "ready"),
    PENDING(1, "pending"),
    APPLIED(2, "applied"),
    DISABLED(3, "disabled"),
    UNSUPPORTED(4, "unsupported"),
    FAILED(5, "failed");

    private final int wireId;
    private final String serializedName;

    OsEffectState(int wireId, String serializedName) {
        this.wireId = wireId;
        this.serializedName = serializedName;
    }

    public int wireId() {
        return wireId;
    }

    public String serializedName() {
        return serializedName;
    }

    public static OsEffectState fromWireId(int wireId) {
        return Arrays.stream(values())
                .filter(state -> state.wireId == wireId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown OS effect state wire id: " + wireId));
    }
}
