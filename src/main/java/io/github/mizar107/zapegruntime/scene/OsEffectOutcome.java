package io.github.mizar107.zapegruntime.scene;

import java.util.Objects;

/** One structured capability/application verdict. */
public record OsEffectOutcome(
        OsEffect effect, OsEffectState state, OsEffectReason reason) {

    public OsEffectOutcome {
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(reason, "reason");
        if ((state == OsEffectState.READY
                        || state == OsEffectState.PENDING
                        || state == OsEffectState.APPLIED)
                && reason != OsEffectReason.NONE) {
            throw new IllegalArgumentException(state + " must use reason NONE");
        }
        if ((state == OsEffectState.DISABLED
                        || state == OsEffectState.UNSUPPORTED
                        || state == OsEffectState.FAILED)
                && reason == OsEffectReason.NONE) {
            throw new IllegalArgumentException(state + " must carry a reason");
        }
        if (state == OsEffectState.DISABLED
                && reason != OsEffectReason.MASTER_DISABLED
                && reason != OsEffectReason.EFFECT_DISABLED) {
            throw new IllegalArgumentException("DISABLED reason is not an opt-out");
        }
        if (state == OsEffectState.UNSUPPORTED
                && reason != OsEffectReason.PLATFORM_UNSUPPORTED
                && reason != OsEffectReason.HEADLESS
                && reason != OsEffectReason.FULLSCREEN) {
            throw new IllegalArgumentException("UNSUPPORTED reason is not a capability gate");
        }
        if (state == OsEffectState.FAILED
                && (reason == OsEffectReason.MASTER_DISABLED
                        || reason == OsEffectReason.EFFECT_DISABLED
                        || reason == OsEffectReason.PLATFORM_UNSUPPORTED
                        || reason == OsEffectReason.HEADLESS
                        || reason == OsEffectReason.FULLSCREEN)) {
            throw new IllegalArgumentException("FAILED reason is not an application failure");
        }
    }

    public static OsEffectOutcome ready(OsEffect effect) {
        return new OsEffectOutcome(effect, OsEffectState.READY, OsEffectReason.NONE);
    }

    public static OsEffectOutcome pending(OsEffect effect) {
        return new OsEffectOutcome(effect, OsEffectState.PENDING, OsEffectReason.NONE);
    }

    public static OsEffectOutcome applied(OsEffect effect) {
        return new OsEffectOutcome(effect, OsEffectState.APPLIED, OsEffectReason.NONE);
    }

    public static OsEffectOutcome disabled(OsEffect effect, OsEffectReason reason) {
        return new OsEffectOutcome(effect, OsEffectState.DISABLED, reason);
    }

    public static OsEffectOutcome unsupported(OsEffect effect, OsEffectReason reason) {
        return new OsEffectOutcome(effect, OsEffectState.UNSUPPORTED, reason);
    }

    public static OsEffectOutcome failed(OsEffect effect, OsEffectReason reason) {
        return new OsEffectOutcome(effect, OsEffectState.FAILED, reason);
    }

    public String compactString() {
        return effect.serializedName() + "=" + state.serializedName()
                + (reason == OsEffectReason.NONE ? "" : ":" + reason.serializedName());
    }
}
