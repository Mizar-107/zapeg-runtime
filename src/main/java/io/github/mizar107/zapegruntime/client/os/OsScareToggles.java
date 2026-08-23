package io.github.mizar107.zapegruntime.client.os;

/**
 * Per-client opt-in for external OS effects. The master switch gates
 * everything and defaults off; each sub-toggle selects a beat only after the
 * player opts in. {@link #ALL_OFF} is the fail-closed unloaded/error state.
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
