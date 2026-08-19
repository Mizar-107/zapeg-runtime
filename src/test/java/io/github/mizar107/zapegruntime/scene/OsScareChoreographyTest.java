package io.github.mizar107.zapegruntime.scene;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OsScareChoreographyTest {

    @Test
    void theFaceBlinkIsBriefAndNeverAFlashbang() {
        // The whole blink stays inside the promised ~1-2 second window.
        int totalTicks = OsScareChoreography.popupTotalTicks();
        assertTrue(totalTicks >= 16, "long enough to register");
        assertTrue(totalTicks <= 40, "never longer than two seconds");
        assertEquals(0.0D, OsScareChoreography.popupOpacity(
                OsScareChoreography.POPUP_START_TICK - 1.0D));
        assertEquals(0.0D, OsScareChoreography.popupOpacity(
                OsScareChoreography.POPUP_START_TICK + totalTicks));
        double peak = 0.0D;
        for (double age = 0.0D; age < 60.0D; age += 0.25D) {
            double opacity = OsScareChoreography.popupOpacity(age);
            assertTrue(opacity >= 0.0D && opacity <= OsScareChoreography.POPUP_PEAK_OPACITY);
            peak = Math.max(peak, opacity);
        }
        assertEquals(OsScareChoreography.POPUP_PEAK_OPACITY, peak, 1.0E-6D);
        assertTrue(OsScareChoreography.POPUP_PEAK_OPACITY < 1.0F,
                "the face never reaches full opacity");
        assertEquals(0.0D, OsScareChoreography.popupOpacity(Double.NaN));
    }

    @Test
    void theTitleBeatAlwaysEndsRestored() {
        int start = OsScareChoreography.TITLE_FLICKER_START_TICK;
        int end = start + OsScareChoreography.TITLE_FLICKER_TICKS;
        assertFalse(OsScareChoreography.titleIsGlitched(start - 1));
        assertFalse(OsScareChoreography.titleIsGlitched(end));
        assertTrue(OsScareChoreography.titleIsGlitched(start));
        // Alternation: two ticks wrong, two ticks restored.
        assertTrue(OsScareChoreography.titleIsGlitched(start + 1));
        assertFalse(OsScareChoreography.titleIsGlitched(start + 2));
        assertFalse(OsScareChoreography.titleIsGlitched(start + 3));
        assertTrue(OsScareChoreography.titleIsGlitched(start + 4));
        // The final two ticks of the window are the restored state.
        assertFalse(OsScareChoreography.titleIsGlitched(end - 1));
        assertFalse(OsScareChoreography.titleIsGlitched(end - 2));
    }

    @Test
    void glitchedTitlesAreWordlessDeterministicRuns() {
        for (long seed : new long[] {0L, 1L, 42L, -7L, 1L << 40}) {
            for (int step = 0; step < 6; step++) {
                String title = OsScareChoreography.glitchedTitle(seed, step);
                assertEquals(title, OsScareChoreography.glitchedTitle(seed, step),
                        "deterministic per (seed, step)");
                assertTrue(title.length() >= 7 && title.length() <= 11);
                for (int index = 0; index < title.length(); index++) {
                    char glyph = title.charAt(index);
                    assertFalse(Character.isLetterOrDigit(glyph),
                            "never a letter or digit: " + title);
                    assertFalse(Character.isWhitespace(glyph));
                }
            }
            assertNotEquals(
                    OsScareChoreography.glitchedTitle(seed, 0),
                    OsScareChoreography.glitchedTitle(seed, 1),
                    "each flicker step reads differently");
        }
    }

    @Test
    void theWindowPulseIsSmallAndSettlesToZero() {
        assertArrayEquals(new int[] {0, 0},
                OsScareChoreography.windowPulseOffset(
                        OsScareChoreography.WINDOW_PULSE_START_TICK - 1, 99L));
        assertArrayEquals(new int[] {0, 0},
                OsScareChoreography.windowPulseOffset(
                        OsScareChoreography.WINDOW_PULSE_START_TICK
                                + OsScareChoreography.WINDOW_PULSE_TICKS, 99L));
        for (long seed : new long[] {0L, 7L, -3L}) {
            for (int age = 0; age < 60; age++) {
                int[] offset = OsScareChoreography.windowPulseOffset(age, seed);
                assertTrue(Math.abs(offset[0]) <= OsScareChoreography.WINDOW_PULSE_MAX_PIXELS);
                assertTrue(Math.abs(offset[1]) <= OsScareChoreography.WINDOW_PULSE_MAX_PIXELS);
            }
            // The tremor decays: the last tick moves less than the first.
            int[] first = OsScareChoreography.windowPulseOffset(
                    OsScareChoreography.WINDOW_PULSE_START_TICK, seed);
            int[] last = OsScareChoreography.windowPulseOffset(
                    OsScareChoreography.WINDOW_PULSE_START_TICK
                            + OsScareChoreography.WINDOW_PULSE_TICKS - 1, seed);
            assertTrue(Math.hypot(last[0], last[1]) < Math.hypot(first[0], first[1]));
        }
    }

    @Test
    void theTaskbarFlashRidesTheFaceBlink() {
        assertEquals(
                OsScareChoreography.POPUP_START_TICK,
                OsScareChoreography.taskbarFlashTick());
    }
}
