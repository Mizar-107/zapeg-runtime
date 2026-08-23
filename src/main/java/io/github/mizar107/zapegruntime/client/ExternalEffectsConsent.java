package io.github.mizar107.zapegruntime.client;

import io.github.mizar107.zapegruntime.client.os.OsScareToggles;

/** Pure migration boundary for versioned external-effect consent. */
final class ExternalEffectsConsent {

    private ExternalEffectsConsent() {}

    /**
     * Resolve loaded config values. A null V2 value models an older config in
     * which the key does not exist. The legacy value is accepted only so the
     * migration behavior is explicit and testable; it never grants consent.
     */
    static OsScareToggles resolve(
            Boolean externalEffectsOptInV2,
            boolean ignoredLegacyEnabled,
            boolean facePopup,
            boolean windowWrongness,
            boolean taskbarFlash) {
        boolean consented = Boolean.TRUE.equals(externalEffectsOptInV2);
        return new OsScareToggles(
                consented, facePopup, windowWrongness, taskbarFlash);
    }
}
