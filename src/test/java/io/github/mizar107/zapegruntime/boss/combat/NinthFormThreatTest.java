package io.github.mizar107.zapegruntime.boss.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NinthFormThreatTest {

    @Test
    void outgoingHitsAndWeakPointsMatchTheAtm9Budget() throws IOException {
        String engine = Files.readString(Path.of(
                "src", "main", "java", "io", "github", "mizar107", "zapegruntime",
                "boss", "combat", "NinthFormCombatEngine.java"));
        assertTrue(engine.contains("case KEEL_SWEEP -> 28.0D"));
        assertTrue(engine.contains("case ANCHORFALL -> 36.0D"));
        assertTrue(engine.contains("case UNDERTOW -> 10.0D"));
        assertTrue(engine.contains("case DROWNED_BROADSIDE -> 32.0D"));
        assertTrue(engine.contains("case WAKE_CHARGE -> 26.0D"));
        assertTrue(engine.contains("case NINEFOLD_GAZE -> 12.0D"));
        assertEquals(280.0D, NinthFormDamagePolicy.WEAK_POINT_BASE_HEALTH);
        assertEquals(0.018D, NinthFormDamagePolicy.WEAK_POINT_CAP_FRACTION);
        assertEquals(0.022D, NinthFormDamagePolicy.KEEL_HEART_CAP_FRACTION);
        assertEquals(0.008D, NinthFormDamagePolicy.PHASE_ONE_HULL_CAP_FRACTION);
        assertTrue(NinthFormAttack.MINIMUM_WINDUP_TICKS >= 24);
        assertTrue(engine.contains("MAX_WRECK_YAW_STEP"));
        assertTrue(engine.contains("NinthFormSounds.BED.get()"));
        assertFalseContainsExplode(engine);
    }

    private static void assertFalseContainsExplode(String engine) {
        org.junit.jupiter.api.Assertions.assertFalse(engine.contains("explode("));
        org.junit.jupiter.api.Assertions.assertFalse(engine.contains("GENERIC_EXPLODE"));
    }
}
