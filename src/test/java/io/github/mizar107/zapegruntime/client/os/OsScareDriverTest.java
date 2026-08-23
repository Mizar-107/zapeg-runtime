package io.github.mizar107.zapegruntime.client.os;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mizar107.zapegruntime.scene.OsEffect;
import io.github.mizar107.zapegruntime.scene.OsEffectOutcome;
import io.github.mizar107.zapegruntime.scene.OsEffectReason;
import io.github.mizar107.zapegruntime.scene.OsEffectState;
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
        private final EnumMap<OsEffect, OsEffectOutcome> capabilities =
                new EnumMap<>(OsEffect.class);
        private Consumer<OsEffectOutcome> popupCompletion;

        private RecordingHooks() {
            for (OsEffect effect : OsEffect.values()) {
                capabilities.put(effect, OsEffectOutcome.ready(effect));
            }
        }

        @Override
        public OsEffectOutcome preflight(OsEffect effect) {
            return capabilities.get(effect);
        }

        @Override
        public void showFacePopup(
                int visibleMillis,
                int fadeMillis,
                Consumer<OsEffectOutcome> completion) {
            calls.add("popup:" + visibleMillis + ":" + fadeMillis);
            popupCompletion = completion;
        }

        @Override
        public void closePopup() {
            calls.add("close");
        }

        @Override
        public OsEffectOutcome applyTitle(boolean glitched, long seed, int step) {
            calls.add("title:" + glitched + ":" + step);
            return OsEffectOutcome.applied(OsEffect.WINDOW_TITLE);
        }

        @Override
        public OsEffectOutcome applyWindowPulse(int dx, int dy) {
            calls.add("pulse:" + dx + "," + dy);
            return OsEffectOutcome.applied(OsEffect.WINDOW_MOTION);
        }

        @Override
        public void restoreWindow() {
            calls.add("restore");
        }

        @Override
        public OsEffectOutcome flashTaskbar() {
            calls.add("taskbar");
            return OsEffectOutcome.applied(OsEffect.TASKBAR);
        }

        private void completePopup(OsEffectOutcome outcome) {
            popupCompletion.accept(outcome);
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

    @Test
    void aFullSceneReportsOnlyActuallyAppliedBeats() {
        RecordingHooks hooks = new RecordingHooks();
        OsScareDriver driver = driven(hooks, OsScareToggles.ALL_ON);
        for (int age = 0; age <= 56; age++) {
            driver.tick(SceneProfile.VISITATION_01, age);
        }
        assertEquals(1, hooks.count("popup"));
        assertEquals(1, hooks.count("taskbar"));
        assertTrue(hooks.count("title:") >= 4);
        assertTrue(hooks.count("pulse:") >= OsScareChoreography.WINDOW_PULSE_TICKS);
        assertEquals(OsEffectState.APPLIED,
                driver.report().windowTitle().state());
        assertEquals(OsEffectState.APPLIED,
                driver.report().windowMotion().state());
        assertEquals(OsEffectState.PENDING,
                driver.report().externalPopup().state(),
                "queueing the EDT work is not proof that a popup showed");
        assertEquals(OsEffectState.APPLIED, driver.report().taskbar().state());

        hooks.completePopup(OsEffectOutcome.applied(OsEffect.EXTERNAL_POPUP));
        assertEquals(OsEffectState.APPLIED,
                driver.report().externalPopup().state());
        assertTrue(hooks.calls.contains(
                "popup:" + OsScareChoreography.popupTotalMillis() + ":"
                        + OsScareChoreography.POPUP_FADE_IN_TICKS * 50));
    }

    @Test
    void masterAndSubTogglesProduceExplicitDisabledReasons() {
        RecordingHooks hooks = new RecordingHooks();
        OsScareDriver masterOff = driven(hooks, OsScareToggles.ALL_OFF);
        assertTrue(hooks.calls.isEmpty());
        for (OsEffect effect : OsEffect.values()) {
            assertEquals(OsEffectState.DISABLED, masterOff.report().outcome(effect).state());
            assertEquals(OsEffectReason.MASTER_DISABLED,
                    masterOff.report().outcome(effect).reason());
        }

        RecordingHooks faceOnlyHooks = new RecordingHooks();
        OsScareDriver faceOnly = driven(
                faceOnlyHooks, new OsScareToggles(true, true, false, false));
        for (int age = 0; age <= 56; age++) {
            faceOnly.tick(SceneProfile.VISITATION_01, age);
        }
        assertEquals(OsEffectState.PENDING, faceOnly.report().externalPopup().state());
        assertEquals(OsEffectReason.EFFECT_DISABLED,
                faceOnly.report().windowTitle().reason());
        assertEquals(OsEffectReason.EFFECT_DISABLED,
                faceOnly.report().windowMotion().reason());
        assertEquals(OsEffectReason.EFFECT_DISABLED,
                faceOnly.report().taskbar().reason());
    }

    @Test
    void unsupportedCapabilityIsReportedAndNeverAttempted() {
        RecordingHooks hooks = new RecordingHooks();
        hooks.capabilities.put(
                OsEffect.EXTERNAL_POPUP,
                OsEffectOutcome.unsupported(
                        OsEffect.EXTERNAL_POPUP, OsEffectReason.HEADLESS));
        OsScareDriver driver = driven(hooks, OsScareToggles.ALL_ON);
        for (int age = 0; age <= 56; age++) {
            driver.tick(SceneProfile.VISITATION_01, age);
        }
        assertEquals(0, hooks.count("popup"));
        assertEquals(OsEffectState.UNSUPPORTED,
                driver.report().externalPopup().state());
        assertEquals(OsEffectReason.HEADLESS,
                driver.report().externalPopup().reason());
    }

    @Test
    void resetRestoresAndInvalidatesALatePopupCallback() {
        RecordingHooks hooks = new RecordingHooks();
        OsScareDriver driver = driven(hooks, OsScareToggles.ALL_ON);
        for (int age = 0; age <= OsScareChoreography.POPUP_START_TICK; age++) {
            driver.tick(SceneProfile.VISITATION_01, age);
        }
        assertEquals(OsEffectState.PENDING, driver.report().externalPopup().state());
        driver.reset();
        assertEquals(1, hooks.count("restore"));
        assertEquals(1, hooks.count("close"));
        hooks.completePopup(OsEffectOutcome.applied(OsEffect.EXTERNAL_POPUP));
        assertEquals(OsEffectState.PENDING, driver.report().externalPopup().state(),
                "a stale EDT callback cannot claim success after cleanup");
        driver.reset();
        assertEquals(1, hooks.count("restore"));
        assertEquals(1, hooks.count("close"));
    }

    @Test
    void otherProfilesNeverTouchTheWindow() {
        RecordingHooks hooks = new RecordingHooks();
        OsScareDriver driver = driven(hooks, OsScareToggles.ALL_ON);
        driver.tick(SceneProfile.ECHO_01, 10);
        driver.tick(SceneProfile.COLOSSUS_01, 40);
        assertTrue(hooks.calls.isEmpty());
    }

    @Test
    void aNewSceneStartsFromACleanSlate() {
        RecordingHooks hooks = new RecordingHooks();
        OsScareDriver driver = driven(hooks, OsScareToggles.ALL_ON);
        for (int age = 0; age <= 12; age++) {
            driver.tick(SceneProfile.VISITATION_01, age);
        }
        driver.begin(999L, OsScareToggles.ALL_ON);
        assertEquals(1, hooks.count("restore"));
        assertEquals(1, hooks.count("close"));
        for (int age = 0; age <= 56; age++) {
            driver.tick(SceneProfile.VISITATION_01, age);
        }
        assertEquals(2, hooks.count("popup"));
    }
}
