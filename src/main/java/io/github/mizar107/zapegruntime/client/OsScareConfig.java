package io.github.mizar107.zapegruntime.client;

import io.github.mizar107.zapegruntime.client.os.OsScareToggles;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client-side opt-out for the OS-level scare layer (the visitation_01
 * profile). All toggles default on for the friends-only server; any player
 * can disable any beat locally in {@code zapeg_runtime-client.toml} without
 * affecting anyone else. If the config is not loaded yet the beats default
 * to on, matching the shipped defaults.
 */
public final class OsScareConfig {

    private static final ForgeConfigSpec.BooleanValue MASTER;
    private static final ForgeConfigSpec.BooleanValue FACE_POPUP;
    private static final ForgeConfigSpec.BooleanValue WINDOW_WRONGNESS;
    private static final ForgeConfigSpec.BooleanValue TASKBAR_FLASH;

    public static final ForgeConfigSpec SPEC;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("osScares");
        MASTER = builder
                .comment(
                        "Master switch for the OS-level scare layer: the brief",
                        "face blink outside the game window, the wrong window",
                        "title, the small window pulse and the taskbar flash.",
                        "When false, visitation scenes do nothing on this client.")
                .define("enabled", true);
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
                return OsScareToggles.ALL_ON;
            }
            return new OsScareToggles(
                    MASTER.get(), FACE_POPUP.get(), WINDOW_WRONGNESS.get(), TASKBAR_FLASH.get());
        } catch (Throwable notLoadedYet) {
            return OsScareToggles.ALL_ON;
        }
    }
}
