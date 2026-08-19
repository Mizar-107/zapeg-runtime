package io.github.mizar107.zapegruntime.client.os;

import io.github.mizar107.zapegruntime.scene.OsScareChoreography;
import io.github.mizar107.zapegruntime.scene.SceneProfile;

/**
 * Drives the visitation's OS-level beats from the client scene tick. Pure
 * dispatch logic over {@link OsScareHooks}: edge-triggered beats fire once,
 * window beats are re-applied per tick inside their choreography windows,
 * and every path — scene end, cancel, logout, dimension change — ends in
 * {@link #reset()}, which restores the original title and geometry exactly.
 *
 * <p>The toggles are captured when the scene starts so a mid-scene config
 * change can never leave a half-applied beat behind.
 */
public final class OsScareDriver {

    private static final OsScareDriver INSTANCE =
            new OsScareDriver(PlatformOsScare.create());

    private final OsScareHooks hooks;
    private OsScareToggles toggles = OsScareToggles.ALL_OFF;
    private long seed;
    private boolean sceneActive;
    private boolean popupShown;
    private boolean taskbarFlashed;
    private boolean titleActive;
    private boolean pulseActive;

    public OsScareDriver(OsScareHooks hooks) {
        this.hooks = hooks;
    }

    public static OsScareDriver instance() {
        return INSTANCE;
    }

    /** Begin a visitation scene; reads the client's current opt-out config. */
    public void begin(long sceneSeed, OsScareToggles currentToggles) {
        reset();
        this.seed = sceneSeed;
        this.toggles = currentToggles == null ? OsScareToggles.ALL_OFF : currentToggles;
        this.sceneActive = toggles.anythingEnabled();
    }

    /**
     * Advance the beats to this body age. Only meaningful while a
     * visitation scene is active; anything else resets the driver.
     */
    public void tick(SceneProfile profile, int bodyAgeTicks) {
        if (profile != SceneProfile.VISITATION_01) {
            reset();
            return;
        }
        if (!sceneActive) {
            return;
        }
        if (toggles.windowWrongnessEnabled()) {
            tickWindowWrongness(bodyAgeTicks);
        }
        if (toggles.facePopupEnabled()
                && !popupShown
                && bodyAgeTicks >= OsScareChoreography.POPUP_START_TICK) {
            popupShown = true;
            hooks.showFacePopup(
                    OsScareChoreography.popupTotalMillis(),
                    OsScareChoreography.POPUP_FADE_IN_TICKS * 50);
        }
        if (toggles.taskbarFlashEnabled()
                && !taskbarFlashed
                && bodyAgeTicks >= OsScareChoreography.taskbarFlashTick()) {
            taskbarFlashed = true;
            hooks.flashTaskbar();
        }
    }

    private void tickWindowWrongness(int bodyAgeTicks) {
        int titleEnd = OsScareChoreography.TITLE_FLICKER_START_TICK
                + OsScareChoreography.TITLE_FLICKER_TICKS;
        if (bodyAgeTicks < titleEnd) {
            boolean glitched = OsScareChoreography.titleIsGlitched(bodyAgeTicks);
            if (glitched || titleActive) {
                titleActive = true;
                hooks.applyTitle(
                        glitched,
                        seed,
                        Math.max(0, bodyAgeTicks
                                - OsScareChoreography.TITLE_FLICKER_START_TICK) / 2);
            }
        } else if (titleActive) {
            titleActive = false;
            hooks.restoreWindow();
        }

        int pulseEnd = OsScareChoreography.WINDOW_PULSE_START_TICK
                + OsScareChoreography.WINDOW_PULSE_TICKS;
        if (bodyAgeTicks >= OsScareChoreography.WINDOW_PULSE_START_TICK
                && bodyAgeTicks < pulseEnd) {
            int[] offset = OsScareChoreography.windowPulseOffset(bodyAgeTicks, seed);
            pulseActive = true;
            hooks.applyWindowPulse(offset[0], offset[1]);
        } else if (pulseActive) {
            pulseActive = false;
            hooks.applyWindowPulse(0, 0);
        }
    }

    /** Restore the original title and geometry; safe to call any time. */
    public void reset() {
        if (titleActive || pulseActive) {
            hooks.restoreWindow();
        }
        titleActive = false;
        pulseActive = false;
        popupShown = false;
        taskbarFlashed = false;
        sceneActive = false;
    }
}
