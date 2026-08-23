package io.github.mizar107.zapegruntime.client.os;

import io.github.mizar107.zapegruntime.scene.OsEffect;
import io.github.mizar107.zapegruntime.scene.OsEffectOutcome;
import io.github.mizar107.zapegruntime.scene.OsEffectReason;
import java.util.function.Consumer;

/**
 * Side-effecting boundary of the OS-level scare layer. Every attempted beat
 * returns a bounded outcome; implementations may not hide failures behind a
 * successful scene acknowledgement.
 */
public interface OsScareHooks {

    /** Probe one effect without applying it. This also validates popup assets. */
    OsEffectOutcome preflight(OsEffect effect);

    /**
     * Queue the popup. The callback must report APPLIED only after the EDT has
     * confirmed that its window is actually showing; it reports one bounded
     * failure/unsupported verdict otherwise.
     */
    void showFacePopup(
            int visibleMillis,
            int fadeMillis,
            Consumer<OsEffectOutcome> completion);

    /** Dispose a shown or pending popup immediately. */
    void closePopup();

    /** Apply or restore the transient title beat. */
    OsEffectOutcome applyTitle(boolean glitched, long seed, int step);

    /** Apply the position pulse; {0, 0} restores the captured geometry. */
    OsEffectOutcome applyWindowPulse(int dx, int dy);

    /** Restore the original title and window geometry immediately. */
    void restoreWindow();

    /** Request attention for the game window's taskbar entry. */
    OsEffectOutcome flashTaskbar();

    OsScareHooks NOOP = new OsScareHooks() {
        @Override
        public OsEffectOutcome preflight(OsEffect effect) {
            return OsEffectOutcome.unsupported(effect, OsEffectReason.PLATFORM_UNSUPPORTED);
        }

        @Override
        public void showFacePopup(
                int visibleMillis,
                int fadeMillis,
                Consumer<OsEffectOutcome> completion) {
            completion.accept(OsEffectOutcome.unsupported(
                    OsEffect.EXTERNAL_POPUP, OsEffectReason.PLATFORM_UNSUPPORTED));
        }

        @Override
        public void closePopup() {}

        @Override
        public OsEffectOutcome applyTitle(boolean glitched, long seed, int step) {
            return OsEffectOutcome.unsupported(
                    OsEffect.WINDOW_TITLE, OsEffectReason.PLATFORM_UNSUPPORTED);
        }

        @Override
        public OsEffectOutcome applyWindowPulse(int dx, int dy) {
            return OsEffectOutcome.unsupported(
                    OsEffect.WINDOW_MOTION, OsEffectReason.PLATFORM_UNSUPPORTED);
        }

        @Override
        public void restoreWindow() {}

        @Override
        public OsEffectOutcome flashTaskbar() {
            return OsEffectOutcome.unsupported(
                    OsEffect.TASKBAR, OsEffectReason.PLATFORM_UNSUPPORTED);
        }
    };
}
