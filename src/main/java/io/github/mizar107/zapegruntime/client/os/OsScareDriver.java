package io.github.mizar107.zapegruntime.client.os;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.scene.OsCapabilityState;
import io.github.mizar107.zapegruntime.scene.OsCleanupState;
import io.github.mizar107.zapegruntime.scene.OsEffect;
import io.github.mizar107.zapegruntime.scene.OsEffectOutcome;
import io.github.mizar107.zapegruntime.scene.OsEffectReason;
import io.github.mizar107.zapegruntime.scene.OsPrimaryState;
import io.github.mizar107.zapegruntime.scene.OsScareChoreography;
import io.github.mizar107.zapegruntime.scene.OsScareReport;
import io.github.mizar107.zapegruntime.scene.SceneProfile;
import java.util.EnumMap;

/** Drives visitation while preserving independent delivery and cleanup truth. */
public final class OsScareDriver {

    static final int MAX_CLEANUP_ATTEMPTS = 3;
    static final int POPUP_CLEANUP_CALLBACK_TIMEOUT_TICKS = 20;
    private static final OsScareDriver INSTANCE =
            new OsScareDriver(PlatformOsScare.create());

    private final OsScareHooks hooks;
    private final EnumMap<OsEffect, OsEffectOutcome> outcomes =
            new EnumMap<>(OsEffect.class);
    private OsScareToggles toggles = OsScareToggles.ALL_OFF;
    private long seed;
    private long generation;
    private boolean sceneActive;
    private boolean popupAttempted;
    private boolean popupOutstanding;
    private boolean popupCleanupDue;
    private boolean popupCleanupQueued;
    private long popupOwnerGeneration = -1L;
    private long nextPopupCleanupToken;
    private long activePopupCleanupToken;
    private int popupCleanupPendingTicks;
    private boolean taskbarRequested;
    private boolean titleRequested;
    private boolean titleActive;
    private boolean titleCleanupDue;
    private long titleOwnerGeneration = -1L;
    private boolean motionRequested;
    private boolean pulseActive;
    private boolean motionCleanupDue;
    private long motionOwnerGeneration = -1L;
    private int popupCleanupAttempts;
    private int titleCleanupAttempts;
    private int motionCleanupAttempts;

    public OsScareDriver(OsScareHooks hooks) {
        this.hooks = hooks == null ? OsScareHooks.NOOP : hooks;
        initialiseUnsupported();
    }

    public static OsScareDriver instance() {
        return INSTANCE;
    }

    public synchronized OsScareReport preflight() {
        EnumMap<OsEffect, OsEffectOutcome> probed = new EnumMap<>(OsEffect.class);
        for (OsEffect effect : OsEffect.values()) {
            probed.put(effect, initialFromCapability(effect, safePreflight(effect)));
        }
        return OsScareReport.from(probed);
    }

    /** Begin after one final bounded cleanup attempt for the prior generation. */
    public synchronized void begin(long sceneSeed, OsScareToggles currentToggles) {
        reset();
        generation++;
        seed = sceneSeed;
        toggles = currentToggles == null ? OsScareToggles.ALL_OFF : currentToggles;
        popupAttempted = false;
        if (!popupOutstanding) {
            popupCleanupDue = false;
            popupCleanupQueued = false;
            popupOwnerGeneration = -1L;
            activePopupCleanupToken = 0L;
            popupCleanupPendingTicks = 0;
            popupCleanupAttempts = 0;
        }
        taskbarRequested = false;
        titleActive = false;
        if (!titleRequested) {
            titleCleanupDue = false;
            titleOwnerGeneration = -1L;
            titleCleanupAttempts = 0;
        }
        if (!motionRequested) {
            pulseActive = false;
            motionCleanupDue = false;
            motionOwnerGeneration = -1L;
            motionCleanupAttempts = 0;
        }
        for (OsEffect effect : OsEffect.values()) {
            outcomes.put(effect, initialOutcome(effect));
        }
        sceneActive = true;
    }

    public synchronized void tick(SceneProfile profile, int bodyAgeTicks) {
        if (profile != SceneProfile.VISITATION_01) {
            reset();
            return;
        }
        retryCleanup();
        if (!sceneActive) {
            return;
        }
        if (toggles.windowWrongnessEnabled()) {
            tickWindowWrongness(bodyAgeTicks);
        }
        if (toggles.facePopupEnabled()
                && !popupAttempted
                && bodyAgeTicks >= OsScareChoreography.POPUP_START_TICK
                && capabilityReady(OsEffect.EXTERNAL_POPUP)) {
            popupAttempted = true;
            popupOutstanding = true;
            popupCleanupDue = false;
            popupOwnerGeneration = generation;
            popupCleanupAttempts = 0;
            setPrimary(
                    OsEffect.EXTERNAL_POPUP,
                    OsPrimaryState.REQUESTED,
                    OsEffectReason.NONE);
            setCleanup(
                    OsEffect.EXTERNAL_POPUP,
                    OsCleanupState.PENDING,
                    OsEffectReason.NONE);
            long requestGeneration = generation;
            try {
                hooks.showFacePopup(
                        OsScareChoreography.popupTotalMillis(),
                        OsScareChoreography.POPUP_FADE_IN_TICKS * 50,
                        update -> acceptPopupLifecycle(requestGeneration, update));
            } catch (Throwable failure) {
                setPrimaryFailure(
                        OsEffect.EXTERNAL_POPUP, OsEffectReason.EDT_UNAVAILABLE);
                popupCleanupDue = true;
                setCleanup(
                        OsEffect.EXTERNAL_POPUP,
                        OsCleanupState.FAILED,
                        OsEffectReason.CLEANUP_FAILED);
            }
        }
        if (toggles.taskbarFlashEnabled()
                && !taskbarRequested
                && bodyAgeTicks >= OsScareChoreography.taskbarFlashTick()
                && capabilityReady(OsEffect.TASKBAR)) {
            taskbarRequested = true;
            applyPrimary(OsEffect.TASKBAR, safeTaskbar());
        }
    }

    public synchronized OsScareReport report() {
        return OsScareReport.from(outcomes);
    }

    private void tickWindowWrongness(int bodyAgeTicks) {
        int titleEnd = OsScareChoreography.TITLE_FLICKER_START_TICK
                + OsScareChoreography.TITLE_FLICKER_TICKS;
        if (bodyAgeTicks < titleEnd && capabilityReady(OsEffect.WINDOW_TITLE)) {
            boolean glitched = OsScareChoreography.titleIsGlitched(bodyAgeTicks);
            if (glitched || titleActive) {
                if (!titleRequested) {
                    titleOwnerGeneration = generation;
                    titleCleanupAttempts = 0;
                }
                titleRequested = true;
                titleActive = true;
                titleCleanupDue = false;
                applyPrimary(OsEffect.WINDOW_TITLE, safeTitle(
                        glitched,
                        seed,
                        Math.max(0, bodyAgeTicks
                                - OsScareChoreography.TITLE_FLICKER_START_TICK) / 2));
            }
        } else if (titleActive) {
            titleActive = false;
            titleCleanupDue = true;
            cleanupTitle();
        }

        int pulseEnd = OsScareChoreography.WINDOW_PULSE_START_TICK
                + OsScareChoreography.WINDOW_PULSE_TICKS;
        if (bodyAgeTicks >= OsScareChoreography.WINDOW_PULSE_START_TICK
                && bodyAgeTicks < pulseEnd
                && capabilityReady(OsEffect.WINDOW_MOTION)) {
            int[] offset = OsScareChoreography.windowPulseOffset(bodyAgeTicks, seed);
            if (!motionRequested) {
                motionOwnerGeneration = generation;
                motionCleanupAttempts = 0;
            }
            motionRequested = true;
            pulseActive = true;
            motionCleanupDue = false;
            applyPrimary(
                    OsEffect.WINDOW_MOTION,
                    safeWindowPulse(offset[0], offset[1]));
        } else if (pulseActive) {
            cleanupMotion();
        }
    }

    private synchronized void acceptPopupLifecycle(
            long requestGeneration, OsScareHooks.LifecycleUpdate update) {
        if (requestGeneration != popupOwnerGeneration || update == null) {
            return;
        }
        OsScareHooks.PrimaryResult primary = checkedPrimary(
                OsEffect.EXTERNAL_POPUP,
                update.primary(),
                OsEffectReason.TOOLKIT_FAILURE);
        OsScareHooks.CleanupResult cleanup = checkedCleanup(
                OsEffect.EXTERNAL_POPUP,
                update.cleanup(),
                OsEffectReason.CLEANUP_FAILED);
        if (cleanup.state() == OsCleanupState.FAILED
                && primary.state() == OsPrimaryState.APPLIED) {
            // A timer/show lifecycle failure supersedes an earlier visible proof.
            primary = new OsScareHooks.PrimaryResult(
                    OsPrimaryState.FAILED, OsEffectReason.CLEANUP_FAILED);
        }
        if (requestGeneration == generation) {
            applyPrimary(OsEffect.EXTERNAL_POPUP, primary);
            applyCleanup(OsEffect.EXTERNAL_POPUP, cleanup);
        }
        if (cleanup.state() != OsCleanupState.PENDING) {
            handlePopupCleanupResult(requestGeneration, cleanup);
        }
    }

    private synchronized void acceptPopupCleanup(
            long ownerGeneration,
            long cleanupToken,
            OsScareHooks.CleanupResult result) {
        if (ownerGeneration != popupOwnerGeneration
                || cleanupToken != activePopupCleanupToken) {
            return;
        }
        OsScareHooks.CleanupResult cleanup = checkedCleanup(
                OsEffect.EXTERNAL_POPUP,
                result,
                OsEffectReason.CLEANUP_FAILED);
        if (cleanup.state() == OsCleanupState.PENDING) {
            cleanup = cleanupFailure(
                    OsEffect.EXTERNAL_POPUP, OsEffectReason.CLEANUP_FAILED);
        }
        activePopupCleanupToken = 0L;
        popupCleanupQueued = false;
        popupCleanupPendingTicks = 0;
        if (ownerGeneration == generation) {
            applyCleanup(OsEffect.EXTERNAL_POPUP, cleanup);
        }
        handlePopupCleanupResult(ownerGeneration, cleanup);
    }

    /** Retry only state-retaining cleanup, never more than the fixed bound. */
    public synchronized void retryCleanup() {
        tickPopupCleanupWatchdog();
        if (titleRequested
                && titleCleanupDue
                && titleCleanupAttempts < MAX_CLEANUP_ATTEMPTS) {
            cleanupTitle();
        }
        if (motionRequested
                && !pulseActive
                && motionCleanupDue
                && motionCleanupAttempts < MAX_CLEANUP_ATTEMPTS) {
            cleanupMotion();
        }
        if (popupOutstanding
                && popupCleanupDue
                && !popupCleanupQueued
                && popupCleanupAttempts < MAX_CLEANUP_ATTEMPTS) {
            cleanupPopup();
        }
    }

    private void cleanupTitle() {
        if (!titleRequested) {
            return;
        }
        long ownerGeneration = titleOwnerGeneration;
        OsScareHooks.CleanupResult result = safeTitleCleanup();
        if (result.state() == OsCleanupState.PENDING
                && result.reason() != OsEffectReason.UNVERIFIED_API) {
            result = cleanupFailure(
                    OsEffect.WINDOW_TITLE, OsEffectReason.CLEANUP_FAILED);
        }
        if (ownerGeneration == generation) {
            applyCleanup(OsEffect.WINDOW_TITLE, result);
        }
        if (result.state() == OsCleanupState.APPLIED
                || result.state() == OsCleanupState.NOT_REQUIRED
                || (result.state() == OsCleanupState.PENDING
                        && result.reason() == OsEffectReason.UNVERIFIED_API)) {
            clearTitleCleanupTracking();
        } else if (result.state() == OsCleanupState.FAILED) {
            titleCleanupAttempts++;
            markExhaustedIfOwned(
                    OsEffect.WINDOW_TITLE, titleCleanupAttempts, ownerGeneration);
        }
    }

    private void cleanupMotion() {
        if (!motionRequested) {
            return;
        }
        long ownerGeneration = motionOwnerGeneration;
        motionCleanupDue = true;
        OsScareHooks.CleanupResult result = safeMotionCleanup();
        if (result.state() == OsCleanupState.PENDING) {
            result = cleanupFailure(
                    OsEffect.WINDOW_MOTION, OsEffectReason.CLEANUP_FAILED);
        }
        if (ownerGeneration == generation) {
            applyCleanup(OsEffect.WINDOW_MOTION, result);
        }
        if (result.state() == OsCleanupState.APPLIED
                || result.state() == OsCleanupState.NOT_REQUIRED) {
            clearMotionCleanupTracking();
        } else if (result.state() == OsCleanupState.FAILED) {
            // The hook deliberately retains its origin for the next bounded retry.
            pulseActive = false;
            motionCleanupAttempts++;
            markExhaustedIfOwned(
                    OsEffect.WINDOW_MOTION, motionCleanupAttempts, ownerGeneration);
        }
    }

    private void cleanupPopup() {
        if (!popupOutstanding || !popupCleanupDue || popupCleanupQueued) {
            return;
        }
        long ownerGeneration = popupOwnerGeneration;
        long cleanupToken = ++nextPopupCleanupToken;
        activePopupCleanupToken = cleanupToken;
        popupCleanupQueued = true;
        popupCleanupPendingTicks = 0;
        try {
            OsScareHooks.CleanupResult immediate = hooks.closePopup(
                    result -> acceptPopupCleanup(ownerGeneration, cleanupToken, result));
            if (ownerGeneration != popupOwnerGeneration
                    || cleanupToken != activePopupCleanupToken) {
                // A synchronous callback already settled this operation.
                return;
            }
            immediate = checkedCleanup(
                    OsEffect.EXTERNAL_POPUP,
                    immediate,
                    OsEffectReason.CLEANUP_FAILED);
            if (ownerGeneration == generation) {
                applyCleanup(OsEffect.EXTERNAL_POPUP, immediate);
            }
            if (immediate.state() != OsCleanupState.PENDING) {
                activePopupCleanupToken = 0L;
                popupCleanupQueued = false;
                popupCleanupPendingTicks = 0;
                handlePopupCleanupResult(ownerGeneration, immediate);
            }
        } catch (Throwable failure) {
            if (ownerGeneration == popupOwnerGeneration
                    && cleanupToken == activePopupCleanupToken) {
                activePopupCleanupToken = 0L;
                popupCleanupQueued = false;
                popupCleanupPendingTicks = 0;
                OsScareHooks.CleanupResult cleanup = cleanupFailure(
                        OsEffect.EXTERNAL_POPUP, OsEffectReason.CLEANUP_FAILED);
                if (ownerGeneration == generation) {
                    applyCleanup(OsEffect.EXTERNAL_POPUP, cleanup);
                }
                handlePopupCleanupResult(ownerGeneration, cleanup);
            }
        }
    }

    private void tickPopupCleanupWatchdog() {
        if (!popupCleanupQueued || activePopupCleanupToken == 0L) {
            return;
        }
        popupCleanupPendingTicks++;
        if (popupCleanupPendingTicks < POPUP_CLEANUP_CALLBACK_TIMEOUT_TICKS) {
            return;
        }
        long ownerGeneration = popupOwnerGeneration;
        activePopupCleanupToken = 0L;
        popupCleanupQueued = false;
        popupCleanupPendingTicks = 0;
        OsScareHooks.CleanupResult failure = cleanupFailure(
                OsEffect.EXTERNAL_POPUP, OsEffectReason.EDT_UNAVAILABLE);
        if (ownerGeneration == generation) {
            applyCleanup(OsEffect.EXTERNAL_POPUP, failure);
        }
        handlePopupCleanupResult(ownerGeneration, failure);
    }

    private void handlePopupCleanupResult(
            long ownerGeneration, OsScareHooks.CleanupResult cleanup) {
        if (ownerGeneration != popupOwnerGeneration) {
            return;
        }
        if (cleanup.state() == OsCleanupState.APPLIED
                || cleanup.state() == OsCleanupState.NOT_REQUIRED) {
            clearPopupCleanupTracking();
        } else if (cleanup.state() == OsCleanupState.FAILED) {
            popupCleanupDue = true;
            popupCleanupQueued = false;
            activePopupCleanupToken = 0L;
            popupCleanupPendingTicks = 0;
            popupCleanupAttempts++;
            markExhaustedIfOwned(
                    OsEffect.EXTERNAL_POPUP, popupCleanupAttempts, ownerGeneration);
        }
    }

    private void markExhaustedIfOwned(
            OsEffect effect, int attempts, long ownerGeneration) {
        if (attempts >= MAX_CLEANUP_ATTEMPTS && ownerGeneration == generation) {
            setCleanup(
                    effect,
                    OsCleanupState.FAILED,
                    OsEffectReason.CLEANUP_RETRY_EXHAUSTED);
        } else if (attempts >= MAX_CLEANUP_ATTEMPTS) {
            logFailure(effect, "cleanup", OsEffectReason.CLEANUP_RETRY_EXHAUSTED);
        }
    }

    private void clearPopupCleanupTracking() {
        popupOutstanding = false;
        popupCleanupDue = false;
        popupCleanupQueued = false;
        popupOwnerGeneration = -1L;
        activePopupCleanupToken = 0L;
        popupCleanupPendingTicks = 0;
        popupCleanupAttempts = 0;
    }

    private void clearTitleCleanupTracking() {
        titleRequested = false;
        titleActive = false;
        titleCleanupDue = false;
        titleOwnerGeneration = -1L;
        titleCleanupAttempts = 0;
    }

    private void clearMotionCleanupTracking() {
        pulseActive = false;
        motionRequested = false;
        motionCleanupDue = false;
        motionOwnerGeneration = -1L;
        motionCleanupAttempts = 0;
    }

    private OsEffectOutcome initialOutcome(OsEffect effect) {
        if (!toggles.master()) {
            return OsEffectOutcome.initial(
                    effect, OsCapabilityState.DISABLED, OsEffectReason.MASTER_DISABLED);
        }
        boolean enabled = switch (effect) {
            case WINDOW_TITLE, WINDOW_MOTION -> toggles.windowWrongness();
            case EXTERNAL_POPUP -> toggles.facePopup();
            case TASKBAR -> toggles.taskbarFlash();
        };
        if (!enabled) {
            return OsEffectOutcome.initial(
                    effect, OsCapabilityState.DISABLED, OsEffectReason.EFFECT_DISABLED);
        }
        if ((effect == OsEffect.WINDOW_TITLE && titleRequested)
                || (effect == OsEffect.WINDOW_MOTION && motionRequested)) {
            return OsEffectOutcome.initial(
                    effect, OsCapabilityState.FAILED, OsEffectReason.CLEANUP_FAILED);
        }
        return initialFromCapability(effect, safePreflight(effect));
    }

    private static OsEffectOutcome initialFromCapability(
            OsEffect effect, OsScareHooks.CapabilityResult capability) {
        return OsEffectOutcome.initial(effect, capability.state(), capability.reason());
    }

    private boolean capabilityReady(OsEffect effect) {
        return outcomes.get(effect).capability() == OsCapabilityState.READY;
    }

    private OsScareHooks.CapabilityResult safePreflight(OsEffect effect) {
        try {
            OsScareHooks.CapabilityResult result = hooks.preflight(effect);
            if (result == null) {
                throw new IllegalArgumentException("null capability");
            }
            // Constructor validation is the common fail-closed policy.
            OsEffectOutcome.initial(effect, result.state(), result.reason());
            return result;
        } catch (Throwable failure) {
            return capabilityFailure(effect, failureReason(effect));
        }
    }

    private OsScareHooks.PrimaryResult safeTitle(boolean glitched, long value, int step) {
        try {
            return checkedPrimary(
                    OsEffect.WINDOW_TITLE,
                    hooks.applyTitle(glitched, value, step),
                    OsEffectReason.GLFW_FAILURE);
        } catch (Throwable failure) {
            return primaryFailure(OsEffect.WINDOW_TITLE, OsEffectReason.GLFW_FAILURE);
        }
    }

    private OsScareHooks.PrimaryResult safeWindowPulse(int dx, int dy) {
        try {
            return checkedPrimary(
                    OsEffect.WINDOW_MOTION,
                    hooks.applyWindowPulse(dx, dy),
                    OsEffectReason.GLFW_FAILURE);
        } catch (Throwable failure) {
            return primaryFailure(OsEffect.WINDOW_MOTION, OsEffectReason.GLFW_FAILURE);
        }
    }

    private OsScareHooks.PrimaryResult safeTaskbar() {
        try {
            return checkedPrimary(
                    OsEffect.TASKBAR,
                    hooks.flashTaskbar(),
                    OsEffectReason.GLFW_FAILURE);
        } catch (Throwable failure) {
            return primaryFailure(OsEffect.TASKBAR, OsEffectReason.GLFW_FAILURE);
        }
    }

    private OsScareHooks.CleanupResult safeTitleCleanup() {
        try {
            return checkedCleanup(
                    OsEffect.WINDOW_TITLE,
                    hooks.cleanupTitle(),
                    OsEffectReason.GLFW_FAILURE);
        } catch (Throwable failure) {
            return cleanupFailure(OsEffect.WINDOW_TITLE, OsEffectReason.GLFW_FAILURE);
        }
    }

    private OsScareHooks.CleanupResult safeMotionCleanup() {
        try {
            return checkedCleanup(
                    OsEffect.WINDOW_MOTION,
                    hooks.cleanupWindowMotion(),
                    OsEffectReason.CLEANUP_FAILED);
        } catch (Throwable failure) {
            return cleanupFailure(OsEffect.WINDOW_MOTION, OsEffectReason.CLEANUP_FAILED);
        }
    }

    private static OsScareHooks.PrimaryResult checkedPrimary(
            OsEffect effect,
            OsScareHooks.PrimaryResult result,
            OsEffectReason failureReason) {
        if (result == null) {
            return primaryFailure(effect, failureReason);
        }
        try {
            OsEffectOutcome.initial(effect, OsCapabilityState.READY, OsEffectReason.NONE)
                    .withPrimary(result.state(), result.reason());
            return result;
        } catch (Throwable invalid) {
            return primaryFailure(effect, failureReason);
        }
    }

    private static OsScareHooks.CleanupResult checkedCleanup(
            OsEffect effect,
            OsScareHooks.CleanupResult result,
            OsEffectReason failureReason) {
        if (result == null) {
            return cleanupFailure(effect, failureReason);
        }
        try {
            OsEffectOutcome.initial(effect, OsCapabilityState.READY, OsEffectReason.NONE)
                    .withCleanup(result.state(), result.reason());
            return result;
        } catch (Throwable invalid) {
            return cleanupFailure(effect, failureReason);
        }
    }

    private void applyPrimary(OsEffect effect, OsScareHooks.PrimaryResult result) {
        if (result != null) {
            try {
                setPrimary(effect, result.state(), result.reason());
            } catch (Throwable invalid) {
                setPrimaryFailure(effect, failureReason(effect));
            }
        }
    }

    private void applyCleanup(OsEffect effect, OsScareHooks.CleanupResult result) {
        if (result != null) {
            try {
                setCleanup(effect, result.state(), result.reason());
            } catch (Throwable invalid) {
                setCleanup(effect, OsCleanupState.FAILED, OsEffectReason.CLEANUP_FAILED);
            }
        }
    }

    private void setPrimary(OsEffect effect, OsPrimaryState state, OsEffectReason reason) {
        outcomes.put(effect, outcomes.get(effect).withPrimary(state, reason));
    }

    private void setPrimaryFailure(OsEffect effect, OsEffectReason reason) {
        logFailure(effect, "primary", reason);
        setPrimary(effect, OsPrimaryState.FAILED, reason);
    }

    private void setCleanup(OsEffect effect, OsCleanupState state, OsEffectReason reason) {
        outcomes.put(effect, outcomes.get(effect).withCleanup(state, reason));
        if (state == OsCleanupState.FAILED) {
            logFailure(effect, "cleanup", reason);
        }
    }

    private static OsScareHooks.CapabilityResult capabilityFailure(
            OsEffect effect, OsEffectReason reason) {
        logFailure(effect, "capability", reason);
        return new OsScareHooks.CapabilityResult(OsCapabilityState.FAILED, reason);
    }

    private static OsScareHooks.PrimaryResult primaryFailure(
            OsEffect effect, OsEffectReason reason) {
        logFailure(effect, "primary", reason);
        return new OsScareHooks.PrimaryResult(OsPrimaryState.FAILED, reason);
    }

    private static OsScareHooks.CleanupResult cleanupFailure(
            OsEffect effect, OsEffectReason reason) {
        logFailure(effect, "cleanup", reason);
        return new OsScareHooks.CleanupResult(OsCleanupState.FAILED, reason);
    }

    private static OsEffectReason failureReason(OsEffect effect) {
        return effect == OsEffect.EXTERNAL_POPUP
                ? OsEffectReason.TOOLKIT_FAILURE
                : OsEffectReason.GLFW_FAILURE;
    }

    private static void logFailure(
            OsEffect effect, String stage, OsEffectReason reason) {
        ZapeGRuntime.LOGGER.warn(
                "OS effect outcome effect={} stage={} state=failed reason={}",
                effect.serializedName(),
                stage,
                reason.serializedName());
    }

    /** Initiate cleanup but retain the generation until callbacks settle. */
    public synchronized void reset() {
        sceneActive = false;
        if (titleRequested
                && titleCleanupAttempts < MAX_CLEANUP_ATTEMPTS) {
            titleCleanupDue = true;
            cleanupTitle();
        }
        if (motionRequested && motionCleanupAttempts < MAX_CLEANUP_ATTEMPTS) {
            motionCleanupDue = true;
            cleanupMotion();
        }
        if (popupOutstanding
                && !popupCleanupQueued
                && popupCleanupAttempts < MAX_CLEANUP_ATTEMPTS) {
            popupCleanupDue = true;
            cleanupPopup();
        }
        titleActive = false;
    }

    private void initialiseUnsupported() {
        for (OsEffect effect : OsEffect.values()) {
            outcomes.put(effect, OsEffectOutcome.initial(
                    effect,
                    OsCapabilityState.UNSUPPORTED,
                    OsEffectReason.PLATFORM_UNSUPPORTED));
        }
    }
}
