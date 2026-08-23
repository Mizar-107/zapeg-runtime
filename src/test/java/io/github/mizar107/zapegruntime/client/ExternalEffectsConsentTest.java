package io.github.mizar107.zapegruntime.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mizar107.zapegruntime.client.os.OsScareToggles;
import org.junit.jupiter.api.Test;

class ExternalEffectsConsentTest {

    @Test
    void loadedLegacyTrueConfigWithV2KeyAbsentDoesNotGrantConsent() {
        OsScareToggles toggles = ExternalEffectsConsent.resolve(
                null, true, true, true, true);

        assertFalse(toggles.master());
        assertTrue(toggles.facePopup(), "sub-toggle remains selected behind the master");
        assertFalse(toggles.facePopupEnabled());
        assertFalse(toggles.windowWrongnessEnabled());
        assertFalse(toggles.taskbarFlashEnabled());
    }

    @Test
    void loadedLegacyTrueConfigWithV2FalseDoesNotGrantConsent() {
        OsScareToggles toggles = ExternalEffectsConsent.resolve(
                false, true, true, true, true);

        assertFalse(toggles.master());
        assertFalse(toggles.anythingEnabled());
    }

    @Test
    void changingOnlyLegacyValueCannotChangeResolvedAuthority() {
        OsScareToggles legacyFalse = ExternalEffectsConsent.resolve(
                false, false, true, false, true);
        OsScareToggles legacyTrue = ExternalEffectsConsent.resolve(
                false, true, true, false, true);

        assertEquals(legacyFalse, legacyTrue);
    }

    @Test
    void explicitV2TrueIsRequiredAndStillRespectsSubToggles() {
        OsScareToggles toggles = ExternalEffectsConsent.resolve(
                true, true, true, false, true);

        assertTrue(toggles.master());
        assertTrue(toggles.facePopupEnabled());
        assertFalse(toggles.windowWrongnessEnabled());
        assertTrue(toggles.taskbarFlashEnabled());
    }
}
