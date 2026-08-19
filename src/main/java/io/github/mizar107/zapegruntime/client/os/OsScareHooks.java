package io.github.mizar107.zapegruntime.client.os;

/**
 * The side-effecting boundary of the OS-level scare layer. The driver and
 * the choreography are pure logic; every AWT/Swing/GLFW interaction lives
 * behind this interface so the beat logic is unit-testable without a
 * display, a window, or a mouse.
 *
 * <p>Implementations must fail silent: an unsupported platform, a headless
 * environment or any toolkit error means the beat simply does not happen.
 * Nothing here may steal focus, persist state, or touch anything outside
 * the game window and a single transient popup.
 */
public interface OsScareHooks {

    /** Show the bundled face for a brief blink with a fade, then dispose. */
    void showFacePopup(int visibleMillis, int fadeMillis);

    /** Set the window title to the glitched string for this step, or back
     *  to the captured original when {@code glitched} is false. */
    void applyTitle(boolean glitched, long seed, int step);

    /** Offset the window position by a small decaying pulse; {0, 0}
     *  restores the captured original geometry. */
    void applyWindowPulse(int dx, int dy);

    /** Restore the original title and window geometry immediately. */
    void restoreWindow();

    /** Best-effort taskbar/dock attention flash; may do nothing. */
    void flashTaskbar();

    OsScareHooks NOOP = new OsScareHooks() {
        @Override
        public void showFacePopup(int visibleMillis, int fadeMillis) {}

        @Override
        public void applyTitle(boolean glitched, long seed, int step) {}

        @Override
        public void applyWindowPulse(int dx, int dy) {}

        @Override
        public void restoreWindow() {}

        @Override
        public void flashTaskbar() {}
    };
}
