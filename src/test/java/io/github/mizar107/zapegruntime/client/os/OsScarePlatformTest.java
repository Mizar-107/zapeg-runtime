package io.github.mizar107.zapegruntime.client.os;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pins the OS gate for the AWT-adjacent beats: Windows only, everything
 * else fails silent before any Toolkit class can load. A macOS AWT init
 * under {@code -XstartOnFirstThread} can hang the JVM outright, so the gate
 * — not a catch block — is the safety boundary.
 */
class OsScarePlatformTest {

    @Test
    void onlyWindowsAdmitsThePopupBeats() {
        assertTrue(OsScarePlatform.popupBeatsAllowed("Windows 10"));
        assertTrue(OsScarePlatform.popupBeatsAllowed("Windows 11"));
        assertTrue(OsScarePlatform.popupBeatsAllowed("windows server 2022"));
    }

    @Test
    void everyOtherPlatformFailsSilent() {
        assertFalse(OsScarePlatform.popupBeatsAllowed("Mac OS X"));
        assertFalse(OsScarePlatform.popupBeatsAllowed("Darwin"));
        assertFalse(OsScarePlatform.popupBeatsAllowed("Linux"));
        assertFalse(OsScarePlatform.popupBeatsAllowed("FreeBSD"));
        assertFalse(OsScarePlatform.popupBeatsAllowed(""));
        assertFalse(OsScarePlatform.popupBeatsAllowed(null));
        // "win" alone must not be enough: nothing but a real Windows name.
        assertFalse(OsScarePlatform.popupBeatsAllowed("darwin-like win aire"));
    }

    @Test
    void theFaceResourceConstantMatchesTheShippedBoringPath() {
        // The Java constant and the shipped file must move together, and
        // the path itself must stay deniable: no "face", no "visitation".
        assertTrue(PlatformOsScare.FACE_RESOURCE.endsWith(
                "/textures/misc/calibration_b.png"));
        assertFalse(PlatformOsScare.FACE_RESOURCE.contains("face"));
        assertFalse(PlatformOsScare.FACE_RESOURCE.contains("visitation"));
        assertTrue(
                PlatformOsScare.class.getResource(PlatformOsScare.FACE_RESOURCE) != null,
                "the constant must point at a resource that actually ships");
    }
}
