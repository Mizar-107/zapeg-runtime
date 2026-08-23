package io.github.mizar107.zapegruntime.client.os;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.scene.OsEffect;
import io.github.mizar107.zapegruntime.scene.OsEffectOutcome;
import io.github.mizar107.zapegruntime.scene.OsEffectReason;
import io.github.mizar107.zapegruntime.scene.OsEffectState;
import io.github.mizar107.zapegruntime.scene.OsScareChoreography;
import io.github.mizar107.zapegruntime.scene.OsScareReport;
import io.github.mizar107.zapegruntime.scene.SceneProfile;
import java.util.EnumMap;

/** Drives visitation beats and retains a truthful, thread-safe effect report. */
public final class OsScareDriver {

    private static final OsScareDriver INSTANCE =
            new OsScareDriver(PlatformOsScare.create());

    private final OsScareHooks hooks;
    private final EnumMap<OsEffect, OsEffectOutcome> outcomes =
            new EnumMap<>(OsEffect.class);
    private OsScareToggles toggles = OsScareToggles.ALL_OFF;
    private long seed;
    private long generation;
    private boolean sceneActive;
    private boolean popupRequested;
    private boolean taskbarRequested;
    private boolean titleActive;
    private boolean pulseActive;

    public OsScareDriver(OsScareHooks hooks) {
        this.hooks = hooks == null ? OsScareHooks.NOOP : hooks;
        initialiseUnsupported();
    }

    public static OsScareDriver instance() {
        return INSTANCE;
    }

    /** Validate all capabilities without applying an effect. */
    public synchronized OsScareReport preflight() {
        EnumMap<OsEffect, OsEffectOutcome> probed = new EnumMap<>(OsEffect.class);
        for (OsEffect effect : OsEffect.values()) {
            probed.put(effect, safePreflight(effect));
        }
        return OsScareReport.from(probed);
    }

    /** Begin a visitation and capture the client's current opt-outs. */
    public synchronized void begin(long sceneSeed, OsScareToggles currentToggles) {
        reset();
        generation++;
        seed = sceneSeed;
        toggles = currentToggles == null ? OsScareToggles.ALL_OFF : currentToggles;
        for (OsEffect effect : OsEffect.values()) {
            outcomes.put(effect, initialOutcome(effect));
        }
        sceneActive = true;
    }

    /** Advance the active visitation choreography to this body age. */
    public synchronized void tick(SceneProfile profile, int bodyAgeTicks) {
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
                && !popupRequested
                && bodyAgeTicks >= OsScareChoreography.POPUP_START_TICK
                && isAttemptable(OsEffect.EXTERNAL_POPUP)) {
            popupRequested = true;
            outcomes.put(
                    OsEffect.EXTERNAL_POPUP,
                    OsEffectOutcome.pending(OsEffect.EXTERNAL_POPUP));
            long requestGeneration = generation;
            try {
                hooks.showFacePopup(
                        OsScareChoreography.popupTotalMillis(),
                        OsScareChoreography.POPUP_FADE_IN_TICKS * 50,
                        result -> acceptPopupOutcome(requestGeneration, result));
            } catch (Throwable failure) {
                outcomes.put(
                        OsEffect.EXTERNAL_POPUP,
                        internalFailure(
                                OsEffect.EXTERNAL_POPUP, OsEffectReason.EDT_UNAVAILABLE));
            }
        }
        if (toggles.taskbarFlashEnabled()
                && !taskbarRequested
                && bodyAgeTicks >= OsScareChoreography.taskbarFlashTick()
                && isAttemptable(OsEffect.TASKBAR)) {
            taskbarRequested = true;
            outcomes.put(OsEffect.TASKBAR, safeTaskbar());
        }
    }

    public synchronized OsScareReport report() {
        return OsScareReport.from(outcomes);
    }

    private void tickWindowWrongness(int bodyAgeTicks) {
        int titleEnd = OsScareChoreography.TITLE_FLICKER_START_TICK
                + OsScareChoreography.TITLE_FLICKER_TICKS;
        if (bodyAgeTicks < titleEnd && isAttemptable(OsEffect.WINDOW_TITLE)) {
            boolean glitched = OsScareChoreography.titleIsGlitched(bodyAgeTicks);
            if (glitched || titleActive) {
                titleActive = true;
                retainApplied(OsEffect.WINDOW_TITLE, safeTitle(
                        glitched,
                        seed,
                        Math.max(0, bodyAgeTicks
                                - OsScareChoreography.TITLE_FLICKER_START_TICK) / 2));
            }
        } else if (titleActive) {
            titleActive = false;
            restoreWindowSafely();
        }

        int pulseEnd = OsScareChoreography.WINDOW_PULSE_START_TICK
                + OsScareChoreography.WINDOW_PULSE_TICKS;
        if (bodyAgeTicks >= OsScareChoreography.WINDOW_PULSE_START_TICK
                && bodyAgeTicks < pulseEnd
                && isAttemptable(OsEffect.WINDOW_MOTION)) {
            int[] offset = OsScareChoreography.windowPulseOffset(bodyAgeTicks, seed);
            pulseActive = true;
            retainApplied(
                    OsEffect.WINDOW_MOTION,
                    safeWindowPulse(offset[0], offset[1]));
        } else if (pulseActive) {
            pulseActive = false;
            safeWindowPulse(0, 0);
        }
    }

    private synchronized void acceptPopupOutcome(
            long requestGeneration, OsEffectOutcome outcome) {
        if (requestGeneration != generation
                || !sceneActive
                || outcome == null
                || outcome.effect() != OsEffect.EXTERNAL_POPUP) {
            return;
        }
        if (outcome.state() == OsEffectState.APPLIED
                || outcome.state() == OsEffectState.FAILED
                || outcome.state() == OsEffectState.UNSUPPORTED) {
            outcomes.put(OsEffect.EXTERNAL_POPUP, outcome);
        } else {
            outcomes.put(
                    OsEffect.EXTERNAL_POPUP,
                    internalFailure(
                            OsEffect.EXTERNAL_POPUP, OsEffectReason.POPUP_NOT_SHOWING));
        }
    }

    private OsEffectOutcome initialOutcome(OsEffect effect) {
        if (!toggles.master()) {
            return OsEffectOutcome.disabled(effect, OsEffectReason.MASTER_DISABLED);
        }
        boolean enabled = switch (effect) {
            case WINDOW_TITLE, WINDOW_MOTION -> toggles.windowWrongness();
            case EXTERNAL_POPUP -> toggles.facePopup();
            case TASKBAR -> toggles.taskbarFlash();
        };
        return enabled
                ? safePreflight(effect)
                : OsEffectOutcome.disabled(effect, OsEffectReason.EFFECT_DISABLED);
    }

    private boolean isAttemptable(OsEffect effect) {
        OsEffectState state = outcomes.get(effect).state();
        return state == OsEffectState.READY || state == OsEffectState.APPLIED;
    }

    private void retainApplied(OsEffect effect, OsEffectOutcome next) {
        OsEffectOutcome previous = outcomes.get(effect);
        if (previous.state() != OsEffectState.APPLIED) {
            outcomes.put(effect, next);
        }
    }

    private OsEffectOutcome safePreflight(OsEffect effect) {
        try {
            OsEffectOutcome result = hooks.preflight(effect);
            if (result == null
                    || result.effect() != effect
                    || (result.state() != OsEffectState.READY
                            && result.state() != OsEffectState.UNSUPPORTED
                            && result.state() != OsEffectState.FAILED)) {
                return internalFailure(effect, failureReason(effect));
            }
            return result;
        } catch (Throwable failure) {
            return internalFailure(effect, failureReason(effect));
        }
    }

    private OsEffectOutcome safeTitle(boolean glitched, long value, int step) {
        try {
            return checkedAttempt(
                    OsEffect.WINDOW_TITLE,
                    hooks.applyTitle(glitched, value, step),
                    OsEffectReason.GLFW_FAILURE);
        } catch (Throwable failure) {
            return internalFailure(OsEffect.WINDOW_TITLE, OsEffectReason.GLFW_FAILURE);
        }
    }

    private OsEffectOutcome safeWindowPulse(int dx, int dy) {
        try {
            return checkedAttempt(
                    OsEffect.WINDOW_MOTION,
                    hooks.applyWindowPulse(dx, dy),
                    OsEffectReason.GLFW_FAILURE);
        } catch (Throwable failure) {
            return internalFailure(OsEffect.WINDOW_MOTION, OsEffectReason.GLFW_FAILURE);
        }
    }

    private OsEffectOutcome safeTaskbar() {
        try {
            return checkedAttempt(
                    OsEffect.TASKBAR,
                    hooks.flashTaskbar(),
                    OsEffectReason.GLFW_FAILURE);
        } catch (Throwable failure) {
            return internalFailure(OsEffect.TASKBAR, OsEffectReason.GLFW_FAILURE);
        }
    }

    private static OsEffectReason failureReason(OsEffect effect) {
        return effect == OsEffect.EXTERNAL_POPUP
                ? OsEffectReason.TOOLKIT_FAILURE
                : OsEffectReason.GLFW_FAILURE;
    }

    private static OsEffectOutcome checkedAttempt(
            OsEffect effect,
            OsEffectOutcome outcome,
            OsEffectReason invalidReason) {
        if (outcome == null
                || outcome.effect() != effect
                || (outcome.state() != OsEffectState.APPLIED
                        && outcome.state() != OsEffectState.UNSUPPORTED
                        && outcome.state() != OsEffectState.FAILED)) {
            return internalFailure(effect, invalidReason);
        }
        return outcome;
    }

    private static OsEffectOutcome internalFailure(
            OsEffect effect, OsEffectReason reason) {
        ZapeGRuntime.LOGGER.warn(
                "OS effect outcome effect={} state=failed reason={}",
                effect.serializedName(),
                reason.serializedName());
        return OsEffectOutcome.failed(effect, reason);
    }

    private void restoreWindowSafely() {
        try {
            hooks.restoreWindow();
        } catch (Throwable failure) {
            internalFailure(OsEffect.WINDOW_MOTION, OsEffectReason.GLFW_FAILURE);
        }
    }

    private void closePopupSafely() {
        try {
            hooks.closePopup();
        } catch (Throwable failure) {
            internalFailure(OsEffect.EXTERNAL_POPUP, OsEffectReason.TOOLKIT_FAILURE);
        }
    }

    /** Restore window state and invalidate any queued EDT callback. */
    public synchronized void reset() {
        if (sceneActive || popupRequested || titleActive || pulseActive) {
            generation++;
        }
        if (titleActive || pulseActive) {
            restoreWindowSafely();
        }
        if (popupRequested) {
            closePopupSafely();
        }
        titleActive = false;
        pulseActive = false;
        popupRequested = false;
        taskbarRequested = false;
        sceneActive = false;
    }

    private void initialiseUnsupported() {
        for (OsEffect effect : OsEffect.values()) {
            outcomes.put(effect, OsEffectOutcome.unsupported(
                    effect, OsEffectReason.PLATFORM_UNSUPPORTED));
        }
    }
}
