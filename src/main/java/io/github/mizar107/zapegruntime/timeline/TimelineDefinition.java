package io.github.mizar107.zapegruntime.timeline;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Immutable, canonical result of parsing one timeline datapack resource. */
public record TimelineDefinition(
        ResourceLocation id,
        int durationTicks,
        TimelinePolicies policies,
        List<TimelineAction> actions,
        String fingerprint) {

    public static final int FORMAT_VERSION = 1;
    public static final int MIN_DURATION_TICKS = 20;
    public static final int MAX_DURATION_TICKS = 12_000;
    public static final int MAX_ACTIONS = 64;

    public TimelineDefinition(
            ResourceLocation id,
            int durationTicks,
            TimelinePolicies policies,
            List<TimelineAction> actions) {
        this(id, durationTicks, policies, canonicalActions(actions), "");
    }

    public TimelineDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(policies, "policies");
        Objects.requireNonNull(actions, "actions");
        actions = canonicalActions(actions);
        if (durationTicks < MIN_DURATION_TICKS
                || durationTicks > MAX_DURATION_TICKS) {
            throw new IllegalArgumentException(
                    "timeline duration must be between " + MIN_DURATION_TICKS
                            + " and " + MAX_DURATION_TICKS + " ticks");
        }
        if (actions.isEmpty() || actions.size() > MAX_ACTIONS) {
            throw new IllegalArgumentException(
                    "timeline must contain between 1 and " + MAX_ACTIONS + " actions");
        }
        Set<String> ids = new HashSet<>();
        for (TimelineAction action : actions) {
            if (!ids.add(action.id())) {
                throw new IllegalArgumentException(
                        "duplicate timeline action id: " + action.id());
            }
            if (action.deadlineTick() > durationTicks) {
                throw new IllegalArgumentException(
                        "timeline action " + action.id() + " exceeds duration");
            }
        }
        actions = List.copyOf(actions);
        String canonicalFingerprint = calculateFingerprint(id, durationTicks, policies, actions);
        if (!fingerprint.isEmpty() && !canonicalFingerprint.equals(fingerprint)) {
            throw new IllegalArgumentException("timeline fingerprint is not canonical");
        }
        fingerprint = canonicalFingerprint;
    }

    private static List<TimelineAction> canonicalActions(List<TimelineAction> input) {
        Objects.requireNonNull(input, "actions");
        List<TimelineAction> canonical = new ArrayList<>(input);
        canonical.sort(Comparator.comparingInt(TimelineAction::atTick)
                .thenComparing(TimelineAction::id));
        return canonical;
    }

    private static String calculateFingerprint(
            ResourceLocation id,
            int durationTicks,
            TimelinePolicies policies,
            List<TimelineAction> actions) {
        StringBuilder canonical = new StringBuilder()
                .append(FORMAT_VERSION).append('|')
                .append(id).append('|')
                .append(durationTicks).append('|')
                .append(policies.disconnect()).append('|')
                .append(policies.restart()).append('|')
                .append(policies.dimensionChange()).append('|')
                .append(policies.death());
        for (TimelineAction action : actions) {
            canonical.append('|').append(action.id())
                    .append(':').append(action.atTick())
                    .append(':').append(action.deadlineTick())
                    .append(':').append(action.retryIntervalTicks())
                    .append(':').append(action.required())
                    .append(':').append(action.profile().serializedName())
                    .append(':').append(action.ttlTicks())
                    .append(':').append(action.stage());
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
