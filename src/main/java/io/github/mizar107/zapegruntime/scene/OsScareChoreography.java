package io.github.mizar107.zapegruntime.scene;

/**
 * Timing and envelope math for the visitation's OS-level beats: a brief
 * face blink outside the game window, a moment where the window title reads
 * wrong, a small window-position pulse, and an optional taskbar flash.
 *
 * <p>Pure and display-free so every bound is unit-testable: the face is
 * visible well under two seconds, the title restore is always requested, the
 * window pulse decays to exactly zero, and the glitched titles are built from a
 * fixed pool of block characters — never letters, never words, never a
 * name.
 */
public final class OsScareChoreography {

    /** Body tick at which the title first reads wrong. */
    public static final int TITLE_FLICKER_START_TICK = 4;
    /** Total length of the title beat; afterwards the original is restored. */
    public static final int TITLE_FLICKER_TICKS = 16;
    /** Body tick at which the face blink begins. */
    public static final int POPUP_START_TICK = 8;
    public static final int POPUP_FADE_IN_TICKS = 5;
    public static final int POPUP_HOLD_TICKS = 16;
    public static final int POPUP_FADE_OUT_TICKS = 7;
    /** Body tick at which the window-position pulse begins. */
    public static final int WINDOW_PULSE_START_TICK = 10;
    public static final int WINDOW_PULSE_TICKS = 12;
    /** The window never moves further than this from its true position. */
    public static final int WINDOW_PULSE_MAX_PIXELS = 12;
    /** Peak face opacity: present and readable, never a flashbang. */
    public static final float POPUP_PEAK_OPACITY = 0.92F;

    /** Block/symbol pool for the wrong titles: no letters, no digits, no
     *  words, no name — the title reads broken, never explanatory. */
    private static final String GLYPH_POOL = "▚▞▙▟░▒▓█▄▀■□▪◘◙";

    private OsScareChoreography() {}

    public static int popupTotalTicks() {
        return POPUP_FADE_IN_TICKS + POPUP_HOLD_TICKS + POPUP_FADE_OUT_TICKS;
    }

    public static int popupTotalMillis() {
        return popupTotalTicks() * 50;
    }

    /** Face opacity 0..peak at this body age; exactly 0 outside the blink. */
    public static double popupOpacity(double ageTicks) {
        if (!Double.isFinite(ageTicks)) {
            return 0.0D;
        }
        double age = ageTicks - POPUP_START_TICK;
        if (age < 0.0D || age >= popupTotalTicks()) {
            return 0.0D;
        }
        double fadeIn = SceneMath.smoothstep(0.0D, POPUP_FADE_IN_TICKS, age);
        double fadeOut = 1.0D - SceneMath.smoothstep(
                POPUP_FADE_IN_TICKS + POPUP_HOLD_TICKS, popupTotalTicks(), age);
        return POPUP_PEAK_OPACITY * fadeIn * fadeOut;
    }

    /** Whether the title shows the glitched string at this body age. The
     *  beat alternates two ticks wrong / two ticks restored, and always
     *  ends restored. */
    public static boolean titleIsGlitched(int ageTicks) {
        int age = ageTicks - TITLE_FLICKER_START_TICK;
        return age >= 0 && age < TITLE_FLICKER_TICKS && (age % 4) < 2;
    }

    /**
     * The wrong title for one flicker step: a short run of block glyphs,
     * deterministic per (seed, step), containing no letters or digits.
     */
    public static String glitchedTitle(long seed, int step) {
        int length = 7 + Math.floorMod((int) (seed >>> 3), 5);
        long state = seed * 0x9E3779B97F4A7C15L + step * 0xD1B54A32D192ED03L;
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            state = state * 6364136223846793005L + 1442695040888963407L;
            int glyph = Math.floorMod((int) (state >>> 33), GLYPH_POOL.length());
            builder.append(GLYPH_POOL.charAt(glyph));
        }
        return builder.toString();
    }

    /**
     * The window-position offset in pixels at this body age: a small
     * decaying tremor, alternating direction, exactly {0, 0} outside the
     * beat so the true geometry is always restored.
     */
    public static int[] windowPulseOffset(int ageTicks, long seed) {
        int age = ageTicks - WINDOW_PULSE_START_TICK;
        if (age < 0 || age >= WINDOW_PULSE_TICKS) {
            return new int[] {0, 0};
        }
        double falloff = 1.0D - (double) age / WINDOW_PULSE_TICKS;
        double amplitude = WINDOW_PULSE_MAX_PIXELS * falloff * falloff;
        double phase = age * 1.9D + Math.floorMod(seed, 61L);
        int dx = (int) Math.round(Math.sin(phase) * amplitude);
        int dy = (int) Math.round(Math.cos(phase * 0.7D) * amplitude * 0.6D);
        return new int[] {dx, dy};
    }

    /** The taskbar attention flash rides the face blink's first tick. */
    public static int taskbarFlashTick() {
        return POPUP_START_TICK;
    }
}
