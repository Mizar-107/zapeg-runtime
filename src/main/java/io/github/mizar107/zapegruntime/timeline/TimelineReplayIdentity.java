package io.github.mizar107.zapegruntime.timeline;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;

/** Structured, non-ambiguous origin and payload for one timeline dispatch. */
public record TimelineReplayIdentity(
        UUID eventId,
        UUID sessionId,
        UUID targetId,
        ResourceLocation timelineId,
        String definitionFingerprint,
        String actionId,
        String payloadHash) {

    private static final UUID NIL_UUID = new UUID(0L, 0L);
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern ACTION = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,47}");

    public TimelineReplayIdentity {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(timelineId, "timelineId");
        Objects.requireNonNull(definitionFingerprint, "definitionFingerprint");
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(payloadHash, "payloadHash");
        if (NIL_UUID.equals(eventId)
                || NIL_UUID.equals(sessionId)
                || NIL_UUID.equals(targetId)
                || sessionId.equals(targetId)) {
            throw new IllegalArgumentException("timeline replay UUIDs are invalid");
        }
        if (!HASH.matcher(definitionFingerprint).matches()
                || !HASH.matcher(payloadHash).matches()) {
            throw new IllegalArgumentException("timeline replay hashes must be SHA-256");
        }
        if (!ACTION.matcher(actionId).matches()) {
            throw new IllegalArgumentException("timeline replay action id is invalid");
        }
    }

    public static TimelineReplayIdentity create(
            UUID sessionId,
            UUID targetId,
            TimelineDefinition definition,
            TimelineAction action) {
        return new TimelineReplayIdentity(
                TimelineDeterminism.actionEventId(
                        sessionId, targetId, definition, action),
                sessionId,
                targetId,
                definition.id(),
                definition.fingerprint(),
                action.id(),
                TimelineDeterminism.actionPayloadHash(definition, action));
    }

    public Origin origin() {
        return new Origin(
                sessionId,
                targetId,
                timelineId,
                definitionFingerprint,
                actionId);
    }

    public record Origin(
            UUID sessionId,
            UUID targetId,
            ResourceLocation timelineId,
            String definitionFingerprint,
            String actionId) {}
}
