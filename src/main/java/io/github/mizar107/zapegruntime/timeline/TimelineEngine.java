package io.github.mizar107.zapegruntime.timeline;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Pure deterministic state machine; Minecraft access is supplied by adapters. */
public final class TimelineEngine {

    public static final int MAX_ACTION_ATTEMPTS = 64;

    private TimelineEngine() {}

    public static Step tick(
            TimelineSession input,
            TimelineDefinition definition,
            PlayerState player,
            ActionExecutor executor) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(executor, "executor");

        if (!input.timelineId().equals(definition.id())) {
            return terminal(TerminalStatus.FAILED, TerminalReason.DEFINITION_UNAVAILABLE);
        }
        if (!input.hasDefinition(definition)) {
            return terminal(TerminalStatus.FAILED, TerminalReason.DEFINITION_CHANGED);
        }
        TimelinePolicies policies = definition.policies();
        if (input.status() == TimelineSession.Status.PAUSED_RESTART
                && policies.restart() == TimelinePolicies.Restart.FAIL) {
            return terminal(TerminalStatus.FAILED, TerminalReason.SERVER_RESTART);
        }
        if (!player.online()) {
            if (policies.disconnect() == TimelinePolicies.Disconnect.FAIL) {
                return terminal(TerminalStatus.FAILED, TerminalReason.DISCONNECTED);
            }
            TimelineSession.Status pause = input.status() == TimelineSession.Status.PAUSED_RESTART
                    ? TimelineSession.Status.PAUSED_RESTART
                    : TimelineSession.Status.PAUSED_DISCONNECT;
            return active(input.withStatus(pause));
        }
        if (!player.alive()) {
            return policies.death() == TimelinePolicies.Death.CANCEL
                    ? terminal(TerminalStatus.CANCELLED, TerminalReason.TARGET_DIED)
                    : terminal(TerminalStatus.FAILED, TerminalReason.TARGET_DIED);
        }
        if (player.spectator()) {
            return terminal(TerminalStatus.FAILED, TerminalReason.TARGET_INELIGIBLE);
        }
        if (!input.boundDimension().equals(player.dimension())) {
            if (policies.dimensionChange() == TimelinePolicies.DimensionChange.FAIL) {
                return terminal(TerminalStatus.FAILED, TerminalReason.DIMENSION_CHANGED);
            }
            return active(input.withStatus(TimelineSession.Status.PAUSED_DIMENSION));
        }

        int elapsed = Math.min(
                definition.durationTicks(), input.elapsedTicks() + 1);
        TimelineSession running = input.withProgress(
                elapsed,
                input.nextActionIndex(),
                input.actionAttempts(),
                input.retryAtElapsedTick());
        if (running.nextActionIndex() >= definition.actions().size()) {
            return terminal(TerminalStatus.SUCCEEDED, TerminalReason.COMPLETED);
        }

        TimelineAction action = definition.actions().get(running.nextActionIndex());
        if (elapsed > action.deadlineTick()) {
            return action.required()
                    ? terminal(TerminalStatus.FAILED, TerminalReason.ACTION_DEADLINE)
                    : skip(running, definition);
        }
        if (elapsed < action.atTick() || elapsed < running.retryAtElapsedTick()) {
            return elapsed >= definition.durationTicks()
                    ? terminal(TerminalStatus.FAILED, TerminalReason.TIMELINE_TIMEOUT)
                    : active(running);
        }

        UUID eventId = TimelineDeterminism.actionEventId(
                running.sessionId(), definition, action);
        long visualSeed = TimelineDeterminism.actionSeed(
                running.seed(), definition, action);
        ActionOutcome outcome = Objects.requireNonNull(
                executor.execute(new ActionInvocation(
                        running.sessionId(),
                        running.targetId(),
                        eventId,
                        visualSeed,
                        action)),
                "action outcome");
        return switch (outcome) {
            case APPLIED, ALREADY_APPLIED -> advance(running, definition);
            case RETRYABLE -> retry(running, definition, action);
            case REJECTED -> action.required()
                    ? terminal(TerminalStatus.FAILED, TerminalReason.ACTION_REJECTED)
                    : skip(running, definition);
        };
    }

    public static TimelineSession pauseForRestart(TimelineSession session) {
        return session.withStatus(TimelineSession.Status.PAUSED_RESTART);
    }

    private static Step retry(
            TimelineSession session,
            TimelineDefinition definition,
            TimelineAction action) {
        int attempts = session.actionAttempts() + 1;
        if (attempts >= MAX_ACTION_ATTEMPTS
                || session.elapsedTicks() >= action.deadlineTick()) {
            if (action.required()) {
                return terminal(
                        TerminalStatus.FAILED,
                        attempts >= MAX_ACTION_ATTEMPTS
                                ? TerminalReason.RETRY_LIMIT
                                : TerminalReason.ACTION_DEADLINE);
            }
            return skip(session, definition);
        }
        int retryAt = Math.min(
                definition.durationTicks(),
                session.elapsedTicks() + action.retryIntervalTicks());
        return active(session.withProgress(
                session.elapsedTicks(),
                session.nextActionIndex(),
                attempts,
                retryAt));
    }

    private static Step skip(
            TimelineSession session, TimelineDefinition definition) {
        return advance(session, definition);
    }

    private static Step advance(
            TimelineSession session, TimelineDefinition definition) {
        int nextIndex = session.nextActionIndex() + 1;
        if (nextIndex >= definition.actions().size()) {
            return terminal(TerminalStatus.SUCCEEDED, TerminalReason.COMPLETED);
        }
        return active(session.withProgress(
                session.elapsedTicks(), nextIndex, 0, session.elapsedTicks()));
    }

    private static Step active(TimelineSession session) {
        return new Step(session, null);
    }

    private static Step terminal(TerminalStatus status, TerminalReason reason) {
        return new Step(null, new Terminal(status, reason));
    }

    @FunctionalInterface
    public interface ActionExecutor {
        ActionOutcome execute(ActionInvocation invocation);
    }

    public enum ActionOutcome {
        APPLIED,
        ALREADY_APPLIED,
        RETRYABLE,
        REJECTED
    }

    public record ActionInvocation(
            UUID sessionId,
            UUID targetId,
            UUID eventId,
            long visualSeed,
            TimelineAction action) {

        public ActionInvocation {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(targetId, "targetId");
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(action, "action");
        }
    }

    public record PlayerState(
            boolean online,
            boolean alive,
            boolean spectator,
            ResourceLocation dimension) {

        public PlayerState {
            if (online) {
                Objects.requireNonNull(dimension, "online player dimension");
            }
        }

        public static PlayerState offline() {
            return new PlayerState(false, false, false, null);
        }
    }

    public record Step(TimelineSession active, Terminal terminal) {

        public Step {
            if ((active == null) == (terminal == null)) {
                throw new IllegalArgumentException(
                        "timeline step must be active or terminal, never both");
            }
        }

        public boolean finished() {
            return terminal != null;
        }
    }

    public record Terminal(TerminalStatus status, TerminalReason reason) {

        public Terminal {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(reason, "reason");
        }
    }

    public enum TerminalStatus {
        SUCCEEDED,
        FAILED,
        CANCELLED
    }

    public enum TerminalReason {
        COMPLETED,
        DEFINITION_UNAVAILABLE,
        DEFINITION_CHANGED,
        SERVER_RESTART,
        DISCONNECTED,
        DIMENSION_CHANGED,
        TARGET_DIED,
        TARGET_INELIGIBLE,
        ACTION_DEADLINE,
        ACTION_REJECTED,
        RETRY_LIMIT,
        TIMELINE_TIMEOUT,
        OPERATOR_CANCEL
    }
}
