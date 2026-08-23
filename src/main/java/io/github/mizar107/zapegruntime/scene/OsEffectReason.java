package io.github.mizar107.zapegruntime.scene;

import java.util.Arrays;

/**
 * Bounded, non-sensitive reason codes for an OS effect that was not applied.
 * Raw exception text, paths, monitor names and native handles never cross the
 * wire or enter an operator response.
 */
public enum OsEffectReason {
    NONE(0, "none"),
    MASTER_DISABLED(1, "master_disabled"),
    EFFECT_DISABLED(2, "effect_disabled"),
    PLATFORM_UNSUPPORTED(3, "platform_unsupported"),
    HEADLESS(4, "headless"),
    ASSET_MISSING(5, "asset_missing"),
    ASSET_INVALID(6, "asset_invalid"),
    WINDOW_UNAVAILABLE(7, "window_unavailable"),
    FULLSCREEN(8, "fullscreen"),
    ALREADY_ACTIVE(9, "already_active"),
    EDT_UNAVAILABLE(10, "edt_unavailable"),
    POPUP_NOT_SHOWING(11, "popup_not_showing"),
    TOOLKIT_FAILURE(12, "toolkit_failure"),
    GLFW_FAILURE(13, "glfw_failure"),
    FALLBACK_NOT_IMPLEMENTED(14, "fallback_not_implemented"),
    UNVERIFIED_API(15, "unverified_api"),
    CLEANUP_FAILED(16, "cleanup_failed"),
    CLEANUP_PENDING(17, "cleanup_pending"),
    READBACK_MISMATCH(18, "readback_mismatch"),
    CLEANUP_RETRY_EXHAUSTED(19, "cleanup_retry_exhausted");

    private final int wireId;
    private final String serializedName;

    OsEffectReason(int wireId, String serializedName) {
        this.wireId = wireId;
        this.serializedName = serializedName;
    }

    public int wireId() {
        return wireId;
    }

    public String serializedName() {
        return serializedName;
    }

    public static OsEffectReason fromWireId(int wireId) {
        return Arrays.stream(values())
                .filter(reason -> reason.wireId == wireId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown OS effect reason wire id: " + wireId));
    }
}
