package io.github.mizar107.zapegruntime.timeline;

import io.github.mizar107.zapegruntime.scene.SceneDescriptor;
import io.github.mizar107.zapegruntime.scene.SceneProfile;
import java.util.Objects;
import java.util.regex.Pattern;

/** One bounded, retryable, server-dispatched scene cue in a timeline. */
public record TimelineAction(
        String id,
        int atTick,
        int deadlineTick,
        int retryIntervalTicks,
        boolean required,
        SceneProfile profile,
        int ttlTicks,
        int stage) {

    public static final int MAX_RETRY_INTERVAL_TICKS = 200;
    private static final Pattern ACTION_ID =
            Pattern.compile("[a-z0-9][a-z0-9_.-]{0,47}");

    public TimelineAction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(profile, "profile");
        if (!ACTION_ID.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "timeline action id must match " + ACTION_ID.pattern());
        }
        if (atTick < 0) {
            throw new IllegalArgumentException("timeline action tick cannot be negative");
        }
        if (deadlineTick < atTick) {
            throw new IllegalArgumentException(
                    "timeline action deadline cannot precede its scheduled tick");
        }
        if (retryIntervalTicks < 1
                || retryIntervalTicks > MAX_RETRY_INTERVAL_TICKS) {
            throw new IllegalArgumentException(
                    "timeline retry interval must be between 1 and "
                            + MAX_RETRY_INTERVAL_TICKS + " ticks");
        }
        if (ttlTicks < SceneDescriptor.MIN_TTL_TICKS
                || ttlTicks > SceneDescriptor.MAX_TTL_TICKS) {
            throw new IllegalArgumentException(
                    "timeline scene TTL must be between "
                            + SceneDescriptor.MIN_TTL_TICKS + " and "
                            + SceneDescriptor.MAX_TTL_TICKS + " ticks");
        }
        if (stage < 0 || stage > profile.maxStage()) {
            throw new IllegalArgumentException(
                    "timeline stage is not meaningful for " + profile.serializedName());
        }
    }
}
