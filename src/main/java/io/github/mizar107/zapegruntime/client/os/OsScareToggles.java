package io.github.mizar107.zapegruntime.client.os;

/**
 * Per-client opt-out for the OS-level scare layer. The master switch gates
 * everything; each sub-toggle gates one beat. Defaults are all on for the
 * friends-only server; any player can turn any of it off locally without
 * affecting anyone else.
 */
public record OsScareToggles(
        boolean master, boolean facePopup, boolean windowWrongness, boolean taskbarFlash) {

    public static final OsScareToggles ALL_ON = new OsScareToggles(true, true, true, true);
    public static final OsScareToggles ALL_OFF = new OsScareToggles(false, false, false, false);

    public boolean facePopupEnabled() {
        return master && facePopup;
    }

    public boolean windowWrongnessEnabled() {
        return master && windowWrongness;
    }

    public boolean taskbarFlashEnabled() {
        return master && taskbarFlash;
    }

    /** With everything off the visitation scene is a silent no-op locally. */
    public boolean anythingEnabled() {
        return master && (facePopup || windowWrongness || taskbarFlash);
    }
}
