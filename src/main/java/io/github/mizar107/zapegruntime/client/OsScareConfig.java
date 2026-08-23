package io.github.mizar107.zapegruntime.client;

import io.github.mizar107.zapegruntime.client.os.OsScareToggles;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Explicit client-side opt-in for external OS effects in the visitation_01
 * profile. The versioned consent switch defaults off and is the only value
 * that can enable the layer. The legacy {@code enabled} value stays parseable
 * for migration but is ignored as consent. Missing, unloaded or unreadable
 * configuration always fails closed.
 */
public final class OsScareConfig {

    private static final ForgeConfigSpec.BooleanValue LEGACY_ENABLED;
    private static final ForgeConfigSpec.BooleanValue EXTERNAL_EFFECTS_OPT_IN_V2;
    private static final ForgeConfigSpec.BooleanValue FACE_POPUP;
    private static final ForgeConfigSpec.BooleanValue WINDOW_WRONGNESS;
    private static final ForgeConfigSpec.BooleanValue TASKBAR_FLASH;

    public static final ForgeConfigSpec SPEC;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("osScares");
        LEGACY_ENABLED = builder
                .comment(
                        "Deprecated 0.4.x switch. Retained only so old config files parse.",
                        "This value is ignored and never grants external-effect consent.")
                .define("enabled", false);
        EXTERNAL_EFFECTS_OPT_IN_V2 = builder
                .comment(
                        "Versioned consent for external OS effects: face popup, wrong",
                        "window title, small window pulse and taskbar attention flash.",
                        "Defaults false. Set true explicitly to opt in on this client.")
                .define("externalEffectsOptInV2", false);
        FACE_POPUP = builder
                .comment("The brief borderless always-on-top face blink.")
                .define("facePopup", true);
        WINDOW_WRONGNESS = builder
                .comment("The glitched window title and the small window pulse.")
                .define("windowWrongness", true);
        TASKBAR_FLASH = builder
                .comment("The taskbar/dock attention flash riding the face blink.")
                .define("taskbarFlash", true);
        builder.pop();
        SPEC = builder.build();
    }

    private OsScareConfig() {}

    public static OsScareToggles toggles() {
        try {
            if (!SPEC.isLoaded()) {
                return OsScareToggles.ALL_OFF;
            }
            return ExternalEffectsConsent.resolve(
                    EXTERNAL_EFFECTS_OPT_IN_V2.get(),
                    // Kept in the migration snapshot, deliberately ignored by resolve().
                    LEGACY_ENABLED.get(),
                    FACE_POPUP.get(),
                    WINDOW_WRONGNESS.get(),
                    TASKBAR_FLASH.get());
        } catch (Throwable notLoadedYet) {
            return OsScareToggles.ALL_OFF;
        }
    }
}
