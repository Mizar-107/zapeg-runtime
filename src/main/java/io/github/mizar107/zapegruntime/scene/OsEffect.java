package io.github.mizar107.zapegruntime.scene;

import java.util.Arrays;

/** The four bounded effects owned by the optional OS-scare layer. */
public enum OsEffect {
    WINDOW_TITLE(0, "title"),
    WINDOW_MOTION(1, "motion"),
    EXTERNAL_POPUP(2, "popup"),
    TASKBAR(3, "taskbar");

    private final int wireId;
    private final String serializedName;

    OsEffect(int wireId, String serializedName) {
        this.wireId = wireId;
        this.serializedName = serializedName;
    }

    public int wireId() {
        return wireId;
    }

    public String serializedName() {
        return serializedName;
    }

    public static OsEffect fromWireId(int wireId) {
        return Arrays.stream(values())
                .filter(effect -> effect.wireId == wireId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown OS effect wire id: " + wireId));
    }
}
