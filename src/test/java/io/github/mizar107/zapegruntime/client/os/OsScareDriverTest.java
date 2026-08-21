package io.github.mizar107.zapegruntime.client.os;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mizar107.zapegruntime.scene.OsScareChoreography;
import io.github.mizar107.zapegruntime.scene.SceneProfile;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OsScareDriverTest {

    private static final class RecordingHooks implements OsScareHooks {
        private final List<String> calls = new ArrayList<>();

        @Override
        public void showFacePopup(int visibleMillis, int fadeMillis) {
            calls.add("popup:" + visibleMillis + ":" + fadeMillis);
        }

        @Override
        public void closePopup() {
            calls.add("close");
        }

        @Override
        public void applyTitle(boolean glitched, long seed, int step) {
            calls.add("title:" + glitched + ":" + step);
        }

        @Override
        public void applyWindowPulse(int dx, int dy) {
            calls.add("pulse:" + dx + "," + dy);
        }

        @Override
        public void restoreWindow() {
            calls.add("restore");
        }

        @Override
        public void flashTaskbar() {
            calls.add("taskbar");
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
    void aFullSceneFiresEveryBeatOnceAndRestores() {
        RecordingHooks hooks = new RecordingHooks();
        OsScareDriver driver = driven(hooks, OsScareToggles.ALL_ON);
        for (int age = 0; age <= 56; age++) {
            driver.tick(SceneProfile.VISITATION_01, age);
        }
        assertEquals(1, hooks.count("popup"), "the face blinks exactly once");
        assertEquals(1, hooks.count("taskbar"), "the taskbar flashes exactly once");
        assertTrue(hooks.count("title:") >= 4, "the title flickers through the window");
        assertTrue(hooks.count("pulse:") >= OsScareChoreography.WINDOW_PULSE_TICKS);
        assertEquals(1, hooks.count("restore"),
                "the title comes back exactly once when its window closes");
        assertEquals("pulse:0,0", hooks.calls.get(hooks.calls.size() - 1),
                "the scene's last window call settles the pulse to zero");
        // The popup carries the choreography's bounded blink length.
        assertTrue(hooks.calls.contains(
                "popup:" + OsScareChoreography.popupTotalMillis() + ":"
                        + OsScareChoreography.POPUP_FADE_IN_TICKS * 50));
    }

    @Test
    void theMasterToggleSilencesEverything() {
        RecordingHooks hooks = new RecordingHooks();
        OsScareDriver driver = driven(hooks, OsScareToggles.ALL_OFF);
        for (int age = 0; age <= 56; age++) {
            driver.tick(SceneProfile.VISITATION_01, age);
        }
        assertTrue(hooks.calls.isEmpty(), "opted-out clients see nothing at all");
    }

    @Test
    void subTogglesGateTheirOwnBeatOnly() {
        RecordingHooks faceOnly = new RecordingHooks();
        OsScareDriver driver = driven(
                faceOnly, new OsScareToggles(true, true, false, false));
        for (int age = 0; age <= 56; age++) {
            driver.tick(SceneProfile.VISITATION_01, age);
        }
        assertEquals(1, faceOnly.count("popup"));
        assertEquals(0, faceOnly.count("title:"));
        assertEquals(0, faceOnly.count("pulse:"));
        assertEquals(0, faceOnly.count("taskbar"));

        RecordingHooks windowOnly = new RecordingHooks();
        driver = driven(windowOnly, new OsScareToggles(true, false, true, false));
        for (int age = 0; age <= 56; age++) {
            driver.tick(SceneProfile.VISITATION_01, age);
        }
        assertEquals(0, windowOnly.count("popup"));
        assertTrue(windowOnly.count("title:") > 0);
        assertEquals(1, windowOnly.count("restore"));
    }

    @Test
    void aMidBeatResetRestoresImmediately() {
        RecordingHooks hooks = new RecordingHooks();
        OsScareDriver driver = driven(hooks, OsScareToggles.ALL_ON);
        for (int age = 0; age <= 12; age++) {
            driver.tick(SceneProfile.VISITATION_01, age);
        }
        assertTrue(hooks.count("title:") > 0, "mid-flicker");
        driver.reset();
        assertEquals(1, hooks.count("restore"),
                "logout or cancel mid-beat restores the window at once");
        driver.reset();
        assertEquals(1, hooks.count("restore"), "reset is idempotent");
    }

    @Test
    void aResetAfterTheBlinkBeganAlsoClosesThePopup() {
        // The documented contract: every path ends in reset(), which
        // restores everything — including a face popup mid-blink. A scene
        // cancel one tick after POPUP_START_TICK must dispose it, and a
        // reset on a scene whose blink never began must not touch it.
        RecordingHooks hooks = new RecordingHooks();
        OsScareDriver driver = driven(hooks, OsScareToggles.ALL_ON);
        for (int age = 0; age <= OsScareChoreography.POPUP_START_TICK; age++) {
            driver.tick(SceneProfile.VISITATION_01, age);
        }
        assertEquals(1, hooks.count("popup"), "the blink has begun");
        assertEquals(0, hooks.count("close"), "nothing closes mid-blink on its own");
        driver.reset();
        assertEquals(1, hooks.count("close"), "cancel/logout disposes the shown face");
        driver.reset();
        assertEquals(1, hooks.count("close"), "the close is not repeated once cleared");

        RecordingHooks early = new RecordingHooks();
        OsScareDriver earlyDriver = driven(early, OsScareToggles.ALL_ON);
        earlyDriver.tick(SceneProfile.VISITATION_01, 0);
        earlyDriver.reset();
        assertEquals(0, early.count("close"),
                "a reset before the blink ever began has no popup to close");
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
        assertEquals(1, hooks.count("restore"), "the interrupted beat restores first");
        for (int age = 0; age <= 56; age++) {
            driver.tick(SceneProfile.VISITATION_01, age);
        }
        assertEquals(2, hooks.count("popup"), "the second scene blinks again");
    }
}
