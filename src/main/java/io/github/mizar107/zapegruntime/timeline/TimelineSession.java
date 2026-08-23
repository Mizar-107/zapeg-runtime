package io.github.mizar107.zapegruntime.timeline;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;

/** Immutable restart-safe progress for one target's active timeline. */
public record TimelineSession(
        UUID sessionId,
        UUID targetId,
        ResourceLocation timelineId,
        String definitionFingerprint,
        ResourceLocation boundDimension,
        long seed,
        int elapsedTicks,
        int nextActionIndex,
        int actionAttempts,
        int retryAtElapsedTick,
        Status status) {

    private static final UUID NIL_UUID = new UUID(0L, 0L);
    private static final Pattern FINGERPRINT = Pattern.compile("[0-9a-f]{64}");

    public TimelineSession {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(timelineId, "timelineId");
        Objects.requireNonNull(definitionFingerprint, "definitionFingerprint");
        Objects.requireNonNull(boundDimension, "boundDimension");
        Objects.requireNonNull(status, "status");
        if (NIL_UUID.equals(sessionId) || NIL_UUID.equals(targetId)
                || sessionId.equals(targetId)) {
            throw new IllegalArgumentException("timeline session UUIDs are invalid");
        }
        if (!FINGERPRINT.matcher(definitionFingerprint).matches()) {
            throw new IllegalArgumentException("timeline fingerprint must be lowercase SHA-256");
        }
        if (elapsedTicks < 0 || elapsedTicks > TimelineDefinition.MAX_DURATION_TICKS) {
            throw new IllegalArgumentException("timeline elapsed ticks are outside bounds");
        }
        if (nextActionIndex < 0 || nextActionIndex > TimelineDefinition.MAX_ACTIONS) {
            throw new IllegalArgumentException("timeline action index is outside bounds");
        }
        if (actionAttempts < 0 || actionAttempts > TimelineEngine.MAX_ACTION_ATTEMPTS) {
            throw new IllegalArgumentException("timeline action attempts are outside bounds");
        }
        if (retryAtElapsedTick < 0
                || retryAtElapsedTick > TimelineDefinition.MAX_DURATION_TICKS) {
            throw new IllegalArgumentException("timeline retry tick is outside bounds");
        }
    }

    public static TimelineSession start(
            UUID sessionId,
            UUID targetId,
            TimelineDefinition definition,
            ResourceLocation dimension) {
        return new TimelineSession(
                sessionId,
                targetId,
                definition.id(),
                definition.fingerprint(),
                dimension,
                TimelineDeterminism.sessionSeed(sessionId, targetId, definition),
                0,
                0,
                0,
                0,
                Status.RUNNING);
    }

    public boolean hasDefinition(TimelineDefinition definition) {
        return timelineId.equals(definition.id())
                && definitionFingerprint.equals(definition.fingerprint());
    }

    public boolean sameIdentity(TimelineSession other) {
        return sessionId.equals(other.sessionId)
                && targetId.equals(other.targetId)
                && timelineId.equals(other.timelineId)
                && definitionFingerprint.equals(other.definitionFingerprint)
                && boundDimension.equals(other.boundDimension)
                && seed == other.seed;
    }

    public TimelineSession withStatus(Status nextStatus) {
        if (status == nextStatus) {
            return this;
        }
        return new TimelineSession(
                sessionId,
                targetId,
                timelineId,
                definitionFingerprint,
                boundDimension,
                seed,
                elapsedTicks,
                nextActionIndex,
                actionAttempts,
                retryAtElapsedTick,
                nextStatus);
    }

    public TimelineSession withProgress(
            int nextElapsedTicks,
            int nextActionIndex,
            int nextActionAttempts,
            int nextRetryAtElapsedTick) {
        return new TimelineSession(
                sessionId,
                targetId,
                timelineId,
                definitionFingerprint,
                boundDimension,
                seed,
                nextElapsedTicks,
                nextActionIndex,
                nextActionAttempts,
                nextRetryAtElapsedTick,
                Status.RUNNING);
    }

    public enum Status {
        RUNNING,
        PAUSED_DISCONNECT,
        PAUSED_RESTART,
        PAUSED_DIMENSION
    }
}
