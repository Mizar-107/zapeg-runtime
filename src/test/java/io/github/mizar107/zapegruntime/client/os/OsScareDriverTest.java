package io.github.mizar107.zapegruntime.client.os;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mizar107.zapegruntime.scene.OsCapabilityState;
import io.github.mizar107.zapegruntime.scene.OsCleanupState;
import io.github.mizar107.zapegruntime.scene.OsEffect;
import io.github.mizar107.zapegruntime.scene.OsEffectReason;
import io.github.mizar107.zapegruntime.scene.OsFallbackState;
import io.github.mizar107.zapegruntime.scene.OsPrimaryState;
import io.github.mizar107.zapegruntime.scene.OsScareChoreography;
import io.github.mizar107.zapegruntime.scene.SceneProfile;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class OsScareDriverTest {

    private static final class RecordingHooks implements OsScareHooks {
        private final List<String> calls = new ArrayList<>();
        private final EnumMap<OsEffect, CapabilityResult> capabilities =
                new EnumMap<>(OsEffect.class);
        private Consumer<LifecycleUpdate> popupLifecycle;
        private Consumer<CleanupResult> popupCleanup;
        private boolean popupActive;
        private int windowX = 100;
        private Integer capturedOrigin;
        private int motionCleanupFailuresRemaining;

        private RecordingHooks() {
            for (OsEffect effect : OsEffect.values()) {
                capabilities.put(effect, new CapabilityResult(
                        OsCapabilityState.READY, OsEffectReason.NONE));
            }
        }

        @Override
        public CapabilityResult preflight(OsEffect effect) {
            if (effect == OsEffect.EXTERNAL_POPUP && popupActive) {
                return new CapabilityResult(
                        OsCapabilityState.FAILED, OsEffectReason.ALREADY_ACTIVE);
            }
            if (effect == OsEffect.WINDOW_MOTION && capturedOrigin != null) {
                return new CapabilityResult(
                        OsCapabilityState.FAILED, OsEffectReason.CLEANUP_FAILED);
            }
            return capabilities.get(effect);
        }

        @Override
        public void showFacePopup(
                int visibleMillis,
                int fadeMillis,
                Consumer<LifecycleUpdate> completion) {
            calls.add("popup:" + visibleMillis + ":" + fadeMillis);
            popupActive = true;
            popupLifecycle = completion;
        }

        @Override
        public CleanupResult closePopup(Consumer<CleanupResult> completion) {
            calls.add("close");
            popupCleanup = completion;
            return new CleanupResult(
                    OsCleanupState.PENDING, OsEffectReason.CLEANUP_PENDING);
        }

        @Override
        public PrimaryResult applyTitle(boolean glitched, long seed, int step) {
            calls.add("title:" + glitched + ":" + step);
            return new PrimaryResult(
                    OsPrimaryState.REQUESTED, OsEffectReason.UNVERIFIED_API);
        }

        @Override
        public PrimaryResult applyWindowPulse(int dx, int dy) {
            if (capturedOrigin == null) {
                capturedOrigin = windowX;
            }
            windowX = capturedOrigin + dx;
            calls.add("pulse:" + dx + "," + dy);
            return new PrimaryResult(OsPrimaryState.APPLIED, OsEffectReason.NONE);
        }

        @Override
        public CleanupResult cleanupTitle() {
            calls.add("title-cleanup");
            return new CleanupResult(
                    OsCleanupState.PENDING, OsEffectReason.UNVERIFIED_API);
        }

        @Override
        public CleanupResult cleanupWindowMotion() {
            calls.add("motion-cleanup");
            if (motionCleanupFailuresRemaining-- > 0) {
                return new CleanupResult(
                        OsCleanupState.FAILED, OsEffectReason.READBACK_MISMATCH);
            }
            if (capturedOrigin == null) {
                return new CleanupResult(
                        OsCleanupState.NOT_REQUIRED, OsEffectReason.NONE);
            }
            windowX = capturedOrigin;
            capturedOrigin = null;
            return new CleanupResult(OsCleanupState.APPLIED, OsEffectReason.NONE);
        }

        @Override
        public PrimaryResult flashTaskbar() {
            calls.add("taskbar");
            return new PrimaryResult(
                    OsPrimaryState.REQUESTED, OsEffectReason.UNVERIFIED_API);
        }

        private void popupApplied() {
            popupLifecycle.accept(new LifecycleUpdate(
                    new PrimaryResult(OsPrimaryState.APPLIED, OsEffectReason.NONE),
                    new CleanupResult(OsCleanupState.PENDING, OsEffectReason.NONE)));
        }

        private void popupTimerFailed() {
            popupLifecycle.accept(new LifecycleUpdate(
                    new PrimaryResult(
                            OsPrimaryState.FAILED, OsEffectReason.TOOLKIT_FAILURE),
                    new CleanupResult(
                            OsCleanupState.FAILED, OsEffectReason.CLEANUP_FAILED)));
        }

        private void popupFinished() {
            popupActive = false;
            popupLifecycle.accept(new LifecycleUpdate(
                    new PrimaryResult(OsPrimaryState.APPLIED, OsEffectReason.NONE),
                    new CleanupResult(OsCleanupState.APPLIED, OsEffectReason.NONE)));
        }

        private void popupCleanupApplied() {
            popupActive = false;
            popupCleanup.accept(new CleanupResult(
                    OsCleanupState.APPLIED, OsEffectReason.NONE));
        }

        private long count(String prefix) {
            return calls.stream().filter(call -> call.startsWith(prefix)).count();
        }
    }

    private static OsScareDriver driven(RecordingHooks hooks, OsScareToggles toggles) {
        OsScareDriver driver = new OsScareDriver(hooks);
        driver.begin(1234L, toggles);
        return driver;
    }

    private static void runBody(OsScareDriver driver, int endTick) {
        for (int age = 0; age <= endTick; age++) {
            driver.tick(SceneProfile.VISITATION_01, age);
        }
    }

    @Test
    void deliveryProofsStayIndependentAndLatePopupFailureWins() {
        RecordingHooks hooks = new RecordingHooks();
        OsScareDriver driver = driven(hooks, OsScareToggles.ALL_ON);
        runBody(driver, 56);

        assertEquals(OsPrimaryState.REQUESTED, driver.report().windowTitle().primary());
        assertEquals(OsEffectReason.UNVERIFIED_API,
                driver.report().windowTitle().primaryReason());
        assertEquals(OsPrimaryState.APPLIED, driver.report().windowMotion().primary());
        assertEquals(OsCleanupState.APPLIED, driver.report().windowMotion().cleanup());
        assertEquals(OsPrimaryState.REQUESTED, driver.report().externalPopup().primary());
        assertEquals(OsCleanupState.PENDING, driver.report().externalPopup().cleanup());
        assertEquals(OsPrimaryState.REQUESTED, driver.report().taskbar().primary());
        assertEquals(OsEffectReason.UNVERIFIED_API,
                driver.report().taskbar().primaryReason());
        for (OsEffect effect : OsEffect.values()) {
            assertEquals(OsFallbackState.NOT_AVAILABLE,
                    driver.report().outcome(effect).fallback());
            assertEquals(OsEffectReason.FALLBACK_NOT_IMPLEMENTED,
                    driver.report().outcome(effect).fallbackReason());
        }

        hooks.popupApplied();
        assertEquals(OsPrimaryState.APPLIED, driver.report().externalPopup().primary());
        hooks.popupTimerFailed();
        assertEquals(OsPrimaryState.FAILED, driver.report().externalPopup().primary(),
                "a later timer failure cannot leave an earlier APPLIED claim");
        assertEquals(OsCleanupState.FAILED, driver.report().externalPopup().cleanup());
    }

    @Test
    void masterAndSubTogglesAreCapabilitiesNotFakeDeliveries() {
        RecordingHooks hooks = new RecordingHooks();
        OsScareDriver off = driven(hooks, OsScareToggles.ALL_OFF);
        runBody(off, 56);
        for (OsEffect effect : OsEffect.values()) {
            assertEquals(OsCapabilityState.DISABLED, off.report().outcome(effect).capability());
            assertEquals(OsEffectReason.MASTER_DISABLED,
                    off.report().outcome(effect).capabilityReason());
            assertEquals(OsPrimaryState.NOT_REQUESTED,
                    off.report().outcome(effect).primary());
            assertEquals(OsCleanupState.NOT_REQUIRED,
                    off.report().outcome(effect).cleanup());
        }
        assertTrue(hooks.calls.isEmpty());

        RecordingHooks faceOnlyHooks = new RecordingHooks();
        OsScareDriver faceOnly = driven(
                faceOnlyHooks, new OsScareToggles(true, true, false, false));
        runBody(faceOnly, 56);
        assertEquals(OsPrimaryState.REQUESTED, faceOnly.report().externalPopup().primary());
        assertEquals(OsCapabilityState.DISABLED, faceOnly.report().windowTitle().capability());
        assertEquals(OsEffectReason.EFFECT_DISABLED,
                faceOnly.report().windowTitle().capabilityReason());
    }

    @Test
    void unsupportedPopupIsNeverRequested() {
        RecordingHooks hooks = new RecordingHooks();
        hooks.capabilities.put(OsEffect.EXTERNAL_POPUP, new OsScareHooks.CapabilityResult(
                OsCapabilityState.UNSUPPORTED, OsEffectReason.HEADLESS));
        OsScareDriver driver = driven(hooks, OsScareToggles.ALL_ON);
        runBody(driver, 56);
        assertEquals(0, hooks.count("popup"));
        assertEquals(OsCapabilityState.UNSUPPORTED,
                driver.report().externalPopup().capability());
        assertEquals(OsPrimaryState.NOT_REQUESTED,
                driver.report().externalPopup().primary());
    }

    @Test
    void popupCleanupSettlesAfterResetAndOldCallbackCannotMutateNextScene() {
        RecordingHooks hooks = new RecordingHooks();
        OsScareDriver driver = driven(hooks, OsScareToggles.ALL_ON);
        runBody(driver, OsScareChoreography.POPUP_START_TICK);
        hooks.popupApplied();
        Consumer<OsScareHooks.LifecycleUpdate> oldLifecycle = hooks.popupLifecycle;

        driver.reset();
        assertEquals(OsCleanupState.PENDING, driver.report().externalPopup().cleanup());
        assertTrue(driver.hasPendingPhysicalCleanup());
        hooks.popupCleanupApplied();
        assertEquals(OsCleanupState.APPLIED, driver.report().externalPopup().cleanup());
        assertFalse(driver.hasPendingPhysicalCleanup());

        driver.begin(999L, OsScareToggles.ALL_ON);
        oldLifecycle.accept(new OsScareHooks.LifecycleUpdate(
                new OsScareHooks.PrimaryResult(
                        OsPrimaryState.FAILED, OsEffectReason.TOOLKIT_FAILURE),
                new OsScareHooks.CleanupResult(
                        OsCleanupState.FAILED, OsEffectReason.CLEANUP_FAILED)));
        assertEquals(OsPrimaryState.NOT_REQUESTED,
                driver.report().externalPopup().primary(),
                "an old EDT callback cannot mutate a later generation");
    }

    @Test
    void naturallyFinishedPopupRemainsOneShotForTheWholeScene() {
        RecordingHooks hooks = new RecordingHooks();
        OsScareDriver driver = driven(hooks, OsScareToggles.ALL_ON);
        runBody(driver, OsScareChoreography.POPUP_START_TICK);
        hooks.popupApplied();
        hooks.popupFinished();

        for (int age = OsScareChoreography.POPUP_START_TICK + 1; age <= 70; age++) {
            driver.tick(SceneProfile.VISITATION_01, age);
        }

        assertEquals(1, hooks.count("popup:"));
        assertEquals(OsPrimaryState.APPLIED, driver.report().externalPopup().primary());
        assertEquals(OsCleanupState.APPLIED, driver.report().externalPopup().cleanup());
    }

    @Test
    void pendingPopupCleanupSurvivesGenerationRolloverWithoutMutatingIt() {
        RecordingHooks hooks = new RecordingHooks();
        OsScareToggles popupOnly = new OsScareToggles(true, true, false, false);
        OsScareDriver driver = driven(hooks, popupOnly);
        runBody(driver, OsScareChoreography.POPUP_START_TICK);
        hooks.popupApplied();

        driver.reset();
        Consumer<OsScareHooks.CleanupResult> oldCleanup = hooks.popupCleanup;
        driver.begin(222L, popupOnly);
        assertEquals(OsCapabilityState.FAILED,
                driver.report().externalPopup().capability());
        assertEquals(OsEffectReason.CLEANUP_FAILED,
                driver.report().externalPopup().capabilityReason());

        oldCleanup.accept(new OsScareHooks.CleanupResult(
                OsCleanupState.FAILED, OsEffectReason.CLEANUP_FAILED));
        assertTrue(driver.hasPendingPhysicalCleanup());
        assertEquals(OsCleanupState.NOT_REQUIRED,
                driver.report().externalPopup().cleanup(),
                "an old cleanup failure must not rewrite the later scene report");

        driver.retryCleanup();
        assertEquals(2, hooks.count("close"), "the retained popup receives a bounded retry");
        hooks.popupCleanupApplied();
        assertEquals(OsCleanupState.NOT_REQUIRED,
                driver.report().externalPopup().cleanup());

        driver.begin(333L, popupOnly);
        assertEquals(OsCapabilityState.READY,
                driver.report().externalPopup().capability(),
                "verified old cleanup frees a future scene without stale state");
    }

    @Test
    void missingPopupCleanupCallbackTimesOutIntoThreeBoundedAttempts() {
        RecordingHooks hooks = new RecordingHooks();
        OsScareToggles popupOnly = new OsScareToggles(true, true, false, false);
        OsScareDriver driver = driven(hooks, popupOnly);
        runBody(driver, OsScareChoreography.POPUP_START_TICK);
        hooks.popupApplied();
        driver.reset();

        for (int tick = 0;
                tick < OsScareDriver.POPUP_CLEANUP_CALLBACK_TIMEOUT_TICKS
                        * OsScareDriver.MAX_CLEANUP_ATTEMPTS;
                tick++) {
            driver.retryCleanup();
        }

        assertEquals(OsScareDriver.MAX_CLEANUP_ATTEMPTS, hooks.count("close"));
        assertEquals(OsCleanupState.FAILED, driver.report().externalPopup().cleanup());
        assertEquals(OsEffectReason.CLEANUP_RETRY_EXHAUSTED,
                driver.report().externalPopup().cleanupReason());
        assertFalse(driver.hasPendingPhysicalCleanup());

        long popupRequests = hooks.count("popup:");
        driver.begin(9876L, popupOnly);
        assertEquals(OsCapabilityState.FAILED,
                driver.report().externalPopup().capability());
        assertEquals(OsEffectReason.CLEANUP_FAILED,
                driver.report().externalPopup().capabilityReason());
        runBody(driver, OsScareChoreography.POPUP_START_TICK);
        assertEquals(popupRequests, hooks.count("popup:"),
                "exhausted cleanup ownership must fail closed without a second popup");
    }

    @Test
    void terminallyUnverifiedTitleRestoreIsReportedButNotStrandedAsAsyncWork() {
        RecordingHooks hooks = new RecordingHooks();
        OsScareDriver driver = driven(
                hooks, new OsScareToggles(true, false, true, false));
        runBody(driver, OsScareChoreography.TITLE_FLICKER_START_TICK
                + OsScareChoreography.TITLE_FLICKER_TICKS);

        assertEquals(OsCleanupState.PENDING, driver.report().windowTitle().cleanup());
        assertEquals(OsEffectReason.UNVERIFIED_API,
                driver.report().windowTitle().cleanupReason());
        assertEquals(1, hooks.count("title-cleanup"));
        assertFalse(driver.hasPendingPhysicalCleanup(),
                "terminal PENDING/UNVERIFIED is not asynchronous physical work");

        driver.reset();
        driver.retryCleanup();
        driver.retryCleanup();
        assertEquals(1, hooks.count("title-cleanup"),
                "PENDING/UNVERIFIED is a terminal observation, not a missing callback");
    }

    @Test
    void failedMotionRestoreKeepsItsRetryOwnershipAcrossScenes() {
        RecordingHooks hooks = new RecordingHooks();
        hooks.motionCleanupFailuresRemaining = 2;
        OsScareToggles windowOnly = new OsScareToggles(true, false, true, false);
        OsScareDriver driver = driven(hooks, windowOnly);
        runBody(driver, OsScareChoreography.WINDOW_PULSE_START_TICK);

        driver.reset();
        driver.begin(444L, windowOnly);
        assertEquals(OsCapabilityState.FAILED,
                driver.report().windowMotion().capability());
        driver.retryCleanup();
        assertNull(hooks.capturedOrigin, "the third bounded attempt restores the old origin");
        assertEquals(OsCleanupState.NOT_REQUIRED,
                driver.report().windowMotion().cleanup(),
                "old cleanup cannot rewrite the later scene's cleanup dimension");

        driver.begin(555L, windowOnly);
        assertEquals(OsCapabilityState.READY,
                driver.report().windowMotion().capability());
    }

    @Test
    void motionCleanupRetriesAreBoundedAndRetainTheOriginOnFailure() {
        RecordingHooks hooks = new RecordingHooks();
        hooks.motionCleanupFailuresRemaining = 10;
        OsScareDriver driver = driven(
                hooks, new OsScareToggles(true, false, true, false));
        runBody(driver, 56);
        assertEquals(OsScareDriver.MAX_CLEANUP_ATTEMPTS,
                hooks.count("motion-cleanup"));
        assertEquals(OsCleanupState.FAILED, driver.report().windowMotion().cleanup());
        assertEquals(OsEffectReason.CLEANUP_RETRY_EXHAUSTED,
                driver.report().windowMotion().cleanupReason());
        assertFalse(driver.hasPendingPhysicalCleanup(),
                "exhausted cleanup is terminal and the next preflight fails closed");
        assertEquals(100, hooks.capturedOrigin);

        driver.begin(222L, new OsScareToggles(true, false, true, false));
        assertEquals(OsCapabilityState.FAILED,
                driver.report().windowMotion().capability());
        assertEquals(OsEffectReason.CLEANUP_FAILED,
                driver.report().windowMotion().capabilityReason());
    }

    @Test
    void twoVisitsCaptureFreshWindowOriginsAfterVerifiedCleanup() {
        RecordingHooks hooks = new RecordingHooks();
        OsScareDriver driver = driven(
                hooks, new OsScareToggles(true, false, true, false));
        runBody(driver, 56);
        assertNull(hooks.capturedOrigin);
        assertEquals(100, hooks.windowX);

        hooks.windowX = 500;
        driver.begin(333L, new OsScareToggles(true, false, true, false));
        runBody(driver, 56);
        assertNull(hooks.capturedOrigin);
        assertEquals(500, hooks.windowX,
                "the second cleanup restores its fresh origin, not the first visit's origin");
    }
}
