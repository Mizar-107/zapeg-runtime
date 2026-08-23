package io.github.mizar107.zapegruntime.scene;

import java.util.Objects;

/**
 * Fixed lifecycle for one effect. Capability, primary delivery,
 * in-game fallback and cleanup are independent so one success cannot conceal
 * another unverified or failed stage.
 */
public record OsEffectOutcome(
        OsEffect effect,
        OsCapabilityState capability,
        OsEffectReason capabilityReason,
        OsPrimaryState primary,
        OsEffectReason primaryReason,
        OsFallbackState fallback,
        OsEffectReason fallbackReason,
        OsCleanupState cleanup,
        OsEffectReason cleanupReason) {

    public OsEffectOutcome {
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(capabilityReason, "capabilityReason");
        Objects.requireNonNull(primary, "primary");
        Objects.requireNonNull(primaryReason, "primaryReason");
        Objects.requireNonNull(fallback, "fallback");
        Objects.requireNonNull(fallbackReason, "fallbackReason");
        Objects.requireNonNull(cleanup, "cleanup");
        Objects.requireNonNull(cleanupReason, "cleanupReason");
        validateCapability(capability, capabilityReason);
        validatePrimary(primary, primaryReason);
        validateFallback(fallback, fallbackReason);
        validateCleanup(cleanup, cleanupReason);
    }

    public static OsEffectOutcome initial(
            OsEffect effect,
            OsCapabilityState capability,
            OsEffectReason capabilityReason) {
        return new OsEffectOutcome(
                effect,
                capability,
                capabilityReason,
                OsPrimaryState.NOT_REQUESTED,
                OsEffectReason.NONE,
                OsFallbackState.AVAILABLE,
                OsEffectReason.NONE,
                OsCleanupState.NOT_REQUIRED,
                OsEffectReason.NONE);
    }

    public OsEffectOutcome withPrimary(OsPrimaryState state, OsEffectReason reason) {
        return new OsEffectOutcome(
                effect, capability, capabilityReason, state, reason,
                fallback, fallbackReason, cleanup, cleanupReason);
    }

    public OsEffectOutcome withFallback(OsFallbackState state, OsEffectReason reason) {
        return new OsEffectOutcome(
                effect, capability, capabilityReason, primary, primaryReason,
                state, reason, cleanup, cleanupReason);
    }

    public OsEffectOutcome withCleanup(OsCleanupState state, OsEffectReason reason) {
        return new OsEffectOutcome(
                effect, capability, capabilityReason, primary, primaryReason,
                fallback, fallbackReason, state, reason);
    }

    public String compactString() {
        return effect.serializedName()
                + "{c=" + capability.serializedName() + suffix(capabilityReason)
                + ",p=" + primary.serializedName() + suffix(primaryReason)
                + ",f=" + fallback.serializedName() + suffix(fallbackReason)
                + ",x=" + cleanup.serializedName() + suffix(cleanupReason) + "}";
    }

    private static String suffix(OsEffectReason reason) {
        return reason == OsEffectReason.NONE ? "" : ":" + reason.serializedName();
    }

    private static void validateCapability(
            OsCapabilityState state, OsEffectReason reason) {
        switch (state) {
            case READY -> requireNone("READY capability", reason);
            case DISABLED -> requireOneOf(
                    "DISABLED capability", reason,
                    OsEffectReason.MASTER_DISABLED, OsEffectReason.EFFECT_DISABLED);
            case UNSUPPORTED -> requireOneOf(
                    "UNSUPPORTED capability", reason,
                    OsEffectReason.PLATFORM_UNSUPPORTED,
                    OsEffectReason.HEADLESS,
                    OsEffectReason.FULLSCREEN);
            case FAILED -> requireFailure("FAILED capability", reason);
        }
    }

    private static void validatePrimary(OsPrimaryState state, OsEffectReason reason) {
        switch (state) {
            case NOT_REQUESTED, APPLIED -> requireNone(state.name(), reason);
            case REQUESTED -> requireOneOf(
                    "REQUESTED primary", reason,
                    OsEffectReason.NONE, OsEffectReason.UNVERIFIED_API);
            case FAILED -> requireFailure("FAILED primary", reason);
        }
    }

    private static void validateFallback(OsFallbackState state, OsEffectReason reason) {
        switch (state) {
            case NOT_AVAILABLE -> requireOneOf(
                    "NOT_AVAILABLE fallback", reason,
                    OsEffectReason.FALLBACK_NOT_IMPLEMENTED);
            case NOT_NEEDED, REQUESTED, APPLIED, AVAILABLE -> requireNone(state.name(), reason);
            case FAILED -> requireFailure("FAILED fallback", reason);
        }
    }

    private static void validateCleanup(OsCleanupState state, OsEffectReason reason) {
        switch (state) {
            case NOT_REQUIRED, APPLIED -> requireNone(state.name(), reason);
            case PENDING -> requireOneOf(
                    "PENDING cleanup", reason,
                    OsEffectReason.NONE,
                    OsEffectReason.UNVERIFIED_API,
                    OsEffectReason.CLEANUP_PENDING);
            case FAILED -> requireFailure("FAILED cleanup", reason);
        }
    }

    private static void requireNone(String label, OsEffectReason reason) {
        requireOneOf(label, reason, OsEffectReason.NONE);
    }

    private static void requireFailure(String label, OsEffectReason reason) {
        if (reason == OsEffectReason.NONE
                || reason == OsEffectReason.MASTER_DISABLED
                || reason == OsEffectReason.EFFECT_DISABLED
                || reason == OsEffectReason.PLATFORM_UNSUPPORTED
                || reason == OsEffectReason.HEADLESS
                || reason == OsEffectReason.FULLSCREEN
                || reason == OsEffectReason.FALLBACK_NOT_IMPLEMENTED
                || reason == OsEffectReason.UNVERIFIED_API
                || reason == OsEffectReason.CLEANUP_PENDING) {
            throw new IllegalArgumentException(label + " requires a bounded failure reason");
        }
    }

    private static void requireOneOf(
            String label, OsEffectReason reason, OsEffectReason... allowed) {
        for (OsEffectReason candidate : allowed) {
            if (candidate == reason) {
                return;
            }
        }
        throw new IllegalArgumentException(label + " has invalid reason " + reason);
    }
}
