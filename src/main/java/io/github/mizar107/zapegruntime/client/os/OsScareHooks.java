package io.github.mizar107.zapegruntime.client.os;

import io.github.mizar107.zapegruntime.scene.OsCapabilityState;
import io.github.mizar107.zapegruntime.scene.OsCleanupState;
import io.github.mizar107.zapegruntime.scene.OsEffect;
import io.github.mizar107.zapegruntime.scene.OsEffectReason;
import io.github.mizar107.zapegruntime.scene.OsPrimaryState;
import java.util.function.Consumer;

/** Side-effect boundary with independent capability, delivery and cleanup. */
public interface OsScareHooks {

    record CapabilityResult(OsCapabilityState state, OsEffectReason reason) {}
    record PrimaryResult(OsPrimaryState state, OsEffectReason reason) {}
    record CleanupResult(OsCleanupState state, OsEffectReason reason) {}
    record LifecycleUpdate(PrimaryResult primary, CleanupResult cleanup) {}

    CapabilityResult preflight(OsEffect effect);

    /**
     * Emits show/fade lifecycle updates. APPLIED requires a showing window on
     * the selected monitor and either observed nonzero opacity or the explicit
     * opaque-window degradation path.
     */
    void showFacePopup(
            int visibleMillis,
            int fadeMillis,
            Consumer<LifecycleUpdate> completion);

    /** Queue/perform popup disposal and report its independently verified result. */
    CleanupResult closePopup(Consumer<CleanupResult> completion);

    /** GLFW has no title getter: success means REQUESTED/UNVERIFIED, not APPLIED. */
    PrimaryResult applyTitle(boolean glitched, long seed, int step);

    /** Motion may report APPLIED only after position readback matches. */
    PrimaryResult applyWindowPulse(int dx, int dy);

    /** Title restore is also an unverified void request. */
    CleanupResult cleanupTitle();

    /** Restore captured position; retain the origin until readback proves success. */
    CleanupResult cleanupWindowMotion();

    /** Attention is a void GLFW request and therefore remains unverified. */
    PrimaryResult flashTaskbar();

    OsScareHooks NOOP = new OsScareHooks() {
        @Override
        public CapabilityResult preflight(OsEffect effect) {
            return new CapabilityResult(
                    OsCapabilityState.UNSUPPORTED, OsEffectReason.PLATFORM_UNSUPPORTED);
        }

        @Override
        public void showFacePopup(
                int visibleMillis,
                int fadeMillis,
                Consumer<LifecycleUpdate> completion) {
            completion.accept(new LifecycleUpdate(
                    new PrimaryResult(OsPrimaryState.FAILED, OsEffectReason.TOOLKIT_FAILURE),
                    new CleanupResult(OsCleanupState.NOT_REQUIRED, OsEffectReason.NONE)));
        }

        @Override
        public CleanupResult closePopup(Consumer<CleanupResult> completion) {
            return new CleanupResult(OsCleanupState.NOT_REQUIRED, OsEffectReason.NONE);
        }

        @Override
        public PrimaryResult applyTitle(boolean glitched, long seed, int step) {
            return new PrimaryResult(OsPrimaryState.FAILED, OsEffectReason.GLFW_FAILURE);
        }

        @Override
        public PrimaryResult applyWindowPulse(int dx, int dy) {
            return new PrimaryResult(OsPrimaryState.FAILED, OsEffectReason.GLFW_FAILURE);
        }

        @Override
        public CleanupResult cleanupTitle() {
            return new CleanupResult(OsCleanupState.NOT_REQUIRED, OsEffectReason.NONE);
        }

        @Override
        public CleanupResult cleanupWindowMotion() {
            return new CleanupResult(OsCleanupState.NOT_REQUIRED, OsEffectReason.NONE);
        }

        @Override
        public PrimaryResult flashTaskbar() {
            return new PrimaryResult(OsPrimaryState.FAILED, OsEffectReason.GLFW_FAILURE);
        }
    };
}
