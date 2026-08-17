package io.github.mizar107.zapegruntime.scene;

import java.util.Arrays;

public enum CancelReason {
    OPERATOR(0),
    EXPIRED(1),
    LOGOUT(2),
    DEATH(3),
    DIMENSION_CHANGE(4),
    SERVER_STOP(5),
    REPLACED(6);

    private final int wireId;

    CancelReason(int wireId) {
        this.wireId = wireId;
    }

    public int wireId() {
        return wireId;
    }

    public static CancelReason fromWireId(int wireId) {
        return Arrays.stream(values())
                .filter(reason -> reason.wireId == wireId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown cancel reason wire id: " + wireId));
    }
}
