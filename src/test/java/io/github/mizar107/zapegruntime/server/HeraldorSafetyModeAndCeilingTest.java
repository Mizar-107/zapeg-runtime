package io.github.mizar107.zapegruntime.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class HeraldorSafetyModeAndCeilingTest {

    @Test
    void orderingAndClampAreMonotonic() {
        assertTrue(HeraldorSafetyMode.AUTO.allows(HeraldorSafetyMode.LIVE));
        assertTrue(HeraldorSafetyMode.LIVE.allows(HeraldorSafetyMode.MANUAL));
        assertFalse(HeraldorSafetyMode.MANUAL.allows(HeraldorSafetyMode.LIVE));
        assertEquals(
                HeraldorSafetyMode.MANUAL,
                HeraldorSafetyMode.AUTO.clampTo(HeraldorSafetyMode.MANUAL));
        assertEquals(HeraldorSafetyMode.QUARANTINED, HeraldorSafetyMode.AUTO.clampTo(null));
    }

    @Test
    void parserIsCaseInsensitiveButRejectsUnknownModes() {
        assertEquals(HeraldorSafetyMode.AUTO, HeraldorSafetyMode.parse(" AuTo ").orElseThrow());
        assertTrue(HeraldorSafetyMode.parse("enabled").isEmpty());
    }

    @Test
    void missingCeilingDefaultsManualAndInvalidPresentValueQuarantines() {
        assertEquals(HeraldorSafetyMode.MANUAL, HeraldorSafetyCeiling.fromEnvironment(Map.of()));
        assertEquals(
                HeraldorSafetyMode.QUARANTINED,
                HeraldorSafetyCeiling.fromEnvironment(Map.of("HERALDOR_MAX_MODE", "typo")));
    }

    @Test
    void primaryCeilingWinsAndLegacyIsOnlyFallback() {
        assertEquals(
                HeraldorSafetyMode.LIVE,
                HeraldorSafetyCeiling.fromEnvironment(Map.of(
                        "HERALDOR_MAX_MODE", "live",
                        "HERALDOR_SAFETY_CEILING", "auto")));
        assertEquals(
                HeraldorSafetyMode.AUTO,
                HeraldorSafetyCeiling.fromEnvironment(Map.of(
                        "HERALDOR_SAFETY_CEILING", "auto")));
    }
}
