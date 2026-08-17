package io.github.mizar107.zapegruntime.scene;

import java.util.Arrays;

public enum SceneAck {
    RECEIVED(0, false),
    VISIBLE(1, false),
    GAZE(2, true),
    TIMEOUT(3, true),
    ABORTED(4, true),
    BUSY(5, true),
    REJECTED(6, true);

    private final int wireId;
    private final boolean terminal;

    SceneAck(int wireId, boolean terminal) {
        this.wireId = wireId;
        this.terminal = terminal;
    }

    public int wireId() {
        return wireId;
    }

    public boolean terminal() {
        return terminal;
    }

    public static SceneAck fromWireId(int wireId) {
        return Arrays.stream(values())
                .filter(ack -> ack.wireId == wireId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown scene acknowledgement wire id: " + wireId));
    }
}
