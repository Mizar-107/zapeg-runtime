package io.github.mizar107.zapegruntime.client.os;

import java.util.Locale;

/**
 * Platform gate for the OS-scare beats that step outside GLFW. The title
 * flicker and window pulse are plain GLFW calls and stay available on every
 * platform the game itself runs on; the Swing face popup and the taskbar
 * attention flash are gated to Windows — the only platform this suite is
 * built and rehearsed for.
 *
 * <p>The gate must run before any AWT/Swing/Toolkit class is touched: on
 * macOS the first Toolkit initialisation inside a GLFW client launched with
 * {@code -XstartOnFirstThread} classically deadlocks or crashes the JVM,
 * and a hang is not a {@link Throwable} — the fail-silent catch blocks
 * cannot contain it. Elsewhere the beats simply do not happen.
 */
final class OsScarePlatform {

    private static final boolean POPUP_BEATS_ALLOWED =
            popupBeatsAllowed(System.getProperty("os.name"));

    private OsScarePlatform() {}

    /** The cached verdict for this JVM. */
    static boolean popupBeatsAllowed() {
        return POPUP_BEATS_ALLOWED;
    }

    /**
     * True only for a Windows {@code os.name}; anything else — macOS,
     * Linux, null, exotic — fails silent. Injectable so the gate itself is
     * unit-testable on any build machine.
     */
    static boolean popupBeatsAllowed(String osName) {
        return osName != null
                && osName.toLowerCase(Locale.ROOT).startsWith("windows");
    }
}
