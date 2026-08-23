package io.github.mizar107.zapegruntime.scene;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Fixed-size report carried by visitation diagnostics. */
public record OsScareReport(
        OsEffectOutcome windowTitle,
        OsEffectOutcome windowMotion,
        OsEffectOutcome externalPopup,
        OsEffectOutcome taskbar) {

    public OsScareReport {
        requireEffect(windowTitle, OsEffect.WINDOW_TITLE);
        requireEffect(windowMotion, OsEffect.WINDOW_MOTION);
        requireEffect(externalPopup, OsEffect.EXTERNAL_POPUP);
        requireEffect(taskbar, OsEffect.TASKBAR);
    }

    public static OsScareReport from(Map<OsEffect, OsEffectOutcome> outcomes) {
        Objects.requireNonNull(outcomes, "outcomes");
        EnumMap<OsEffect, OsEffectOutcome> copy = new EnumMap<>(OsEffect.class);
        copy.putAll(outcomes);
        return new OsScareReport(
                copy.get(OsEffect.WINDOW_TITLE),
                copy.get(OsEffect.WINDOW_MOTION),
                copy.get(OsEffect.EXTERNAL_POPUP),
                copy.get(OsEffect.TASKBAR));
    }

    public OsEffectOutcome outcome(OsEffect effect) {
        return switch (effect) {
            case WINDOW_TITLE -> windowTitle;
            case WINDOW_MOTION -> windowMotion;
            case EXTERNAL_POPUP -> externalPopup;
            case TASKBAR -> taskbar;
        };
    }

    public String compactString() {
        return windowTitle.compactString() + " "
                + windowMotion.compactString() + " "
                + externalPopup.compactString() + " "
                + taskbar.compactString();
    }

    private static void requireEffect(OsEffectOutcome outcome, OsEffect expected) {
        Objects.requireNonNull(outcome, expected.serializedName());
        if (outcome.effect() != expected) {
            throw new IllegalArgumentException(
                    "Expected " + expected + " outcome, got " + outcome.effect());
        }
    }
}
