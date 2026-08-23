package io.github.mizar107.zapegruntime.timeline;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.scene.CancelReason;
import io.github.mizar107.zapegruntime.server.SceneServerManager;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Minecraft adapter around the pure timeline engine and durable state. */
public final class TimelineServerManager {

    private TimelineServerManager() {}

    public static StartResult start(
            MinecraftServer server,
            ServerPlayer target,
            UUID sessionId,
            ResourceLocation timelineId) {
        Optional<TimelineDefinition> definition =
                TimelineRegistry.current().find(timelineId);
        if (definition.isEmpty()) {
            return new StartResult(false, "timeline definition is unavailable", sessionId);
        }
        if (!target.isAlive() || target.isSpectator()) {
            return new StartResult(false, "target is not eligible", sessionId);
        }
        TimelineSession proposed = TimelineSession.start(
                sessionId,
                target.getUUID(),
                definition.get(),
                target.level().dimension().location());
        TimelineSessionData.BeginResult result =
                TimelineSessionData.get(server).begin(proposed);
        boolean accepted = result.status() == TimelineSessionData.BeginStatus.STARTED
                || result.status() == TimelineSessionData.BeginStatus.IDEMPOTENT_ACTIVE
                || result.status() == TimelineSessionData.BeginStatus.IDEMPOTENT_TERMINAL;
        String message = result.status().name().toLowerCase(Locale.ROOT);
        ZapeGRuntime.LOGGER.info(
                "Timeline start session={} timeline={} target={} result={}",
                sessionId,
                timelineId,
                target.getUUID(),
                message);
        return new StartResult(accepted, message, sessionId);
    }

    public static void tick(MinecraftServer server) {
        TimelineSessionData data = TimelineSessionData.get(server);
        if (!data.supportsCurrentSchema()) {
            return;
        }
        for (TimelineSession session : data.activeSessions()) {
            Optional<TimelineDefinition> definition =
                    TimelineRegistry.current().find(session.timelineId());
            if (definition.isEmpty()) {
                finish(
                        data,
                        session,
                        new TimelineEngine.Terminal(
                                TimelineEngine.TerminalStatus.FAILED,
                                TimelineEngine.TerminalReason.DEFINITION_UNAVAILABLE));
                continue;
            }
            ServerPlayer target = server.getPlayerList().getPlayer(session.targetId());
            TimelineEngine.PlayerState player = target == null
                    ? TimelineEngine.PlayerState.offline()
                    : new TimelineEngine.PlayerState(
                            true,
                            target.isAlive(),
                            target.isSpectator(),
                            target.level().dimension().location());
            TimelineEngine.Step step = TimelineEngine.tick(
                    session,
                    definition.get(),
                    player,
                    invocation -> dispatch(target, invocation));
            apply(data, session, step);
        }
    }

    public static void onLogout(MinecraftServer server, UUID targetId) {
        applyLifecycle(server, targetId, TimelineEngine.PlayerState.offline());
    }

    public static void onDimensionChange(MinecraftServer server, ServerPlayer target) {
        applyLifecycle(
                server,
                target.getUUID(),
                new TimelineEngine.PlayerState(
                        true,
                        target.isAlive(),
                        target.isSpectator(),
                        target.level().dimension().location()));
    }

    public static void onDeath(MinecraftServer server, ServerPlayer target) {
        applyLifecycle(
                server,
                target.getUUID(),
                new TimelineEngine.PlayerState(
                        true,
                        false,
                        target.isSpectator(),
                        target.level().dimension().location()));
    }

    public static boolean cancel(MinecraftServer server, UUID targetId) {
        TimelineSessionData.FinishStatus result =
                TimelineSessionData.get(server).cancelForTarget(targetId);
        if (result == TimelineSessionData.FinishStatus.RECORDED) {
            SceneServerManager.cancelForPlayer(targetId, CancelReason.OPERATOR);
            return true;
        }
        return false;
    }

    public static void onServerStopping(MinecraftServer server) {
        TimelineSessionData.get(server).pauseAllForRestart();
    }

    public static String statusFor(MinecraftServer server, UUID targetId) {
        TimelineSessionData data = TimelineSessionData.get(server);
        if (!data.supportsCurrentSchema()) {
            return "schema=unsupported";
        }
        return data.activeFor(targetId)
                .map(session -> "active=1 session=" + session.sessionId()
                        + " timeline=" + session.timelineId()
                        + " state=" + session.status().name().toLowerCase(Locale.ROOT)
                        + " elapsed=" + session.elapsedTicks()
                        + " action=" + session.nextActionIndex()
                        + " attempts=" + session.actionAttempts())
                .orElse("active=0");
    }

    public static String resultFor(MinecraftServer server, UUID sessionId) {
        TimelineSessionData data = TimelineSessionData.get(server);
        if (!data.supportsCurrentSchema()) {
            return "schema=unsupported";
        }
        Optional<TimelineSession> active = data.findBySession(sessionId);
        if (active.isPresent()) {
            TimelineSession session = active.get();
            return "terminal=0 session=" + session.sessionId()
                    + " target=" + session.targetId()
                    + " timeline=" + session.timelineId()
                    + " state=" + session.status().name().toLowerCase(Locale.ROOT);
        }
        return data.terminal(sessionId)
                .map(result -> "terminal=1 session=" + result.sessionId()
                        + " target=" + result.targetId()
                        + " timeline=" + result.timelineId()
                        + " status=" + result.status().name().toLowerCase(Locale.ROOT)
                        + " reason=" + result.reason().name().toLowerCase(Locale.ROOT))
                .orElse("session=unknown");
    }

    private static void applyLifecycle(
            MinecraftServer server,
            UUID targetId,
            TimelineEngine.PlayerState player) {
        TimelineSessionData data = TimelineSessionData.get(server);
        Optional<TimelineSession> existing = data.activeFor(targetId);
        if (existing.isEmpty()) {
            return;
        }
        Optional<TimelineDefinition> definition =
                TimelineRegistry.current().find(existing.get().timelineId());
        TimelineEngine.Step step = definition
                .map(value -> TimelineEngine.tick(
                        existing.get(),
                        value,
                        player,
                        ignored -> TimelineEngine.ActionOutcome.REJECTED))
                .orElseGet(() -> new TimelineEngine.Step(
                        null,
                        new TimelineEngine.Terminal(
                                TimelineEngine.TerminalStatus.FAILED,
                                TimelineEngine.TerminalReason.DEFINITION_UNAVAILABLE)));
        apply(data, existing.get(), step);
    }

    private static TimelineEngine.ActionOutcome dispatch(
            ServerPlayer target, TimelineEngine.ActionInvocation invocation) {
        if (target == null) {
            return TimelineEngine.ActionOutcome.RETRYABLE;
        }
        SceneServerManager.TimelineDispatchStatus result =
                SceneServerManager.dispatchTimeline(
                        target,
                        invocation.eventId(),
                        invocation.action().profile(),
                        invocation.action().ttlTicks(),
                        invocation.action().stage(),
                        invocation.visualSeed());
        return TimelineEngine.ActionOutcome.valueOf(result.name());
    }

    private static void apply(
            TimelineSessionData data,
            TimelineSession previous,
            TimelineEngine.Step step) {
        if (step.finished()) {
            finish(data, previous, step.terminal());
        } else if (!data.update(step.active())) {
            ZapeGRuntime.LOGGER.error(
                    "Timeline state update rejected session={} target={}",
                    previous.sessionId(),
                    previous.targetId());
        }
    }

    private static void finish(
            TimelineSessionData data,
            TimelineSession session,
            TimelineEngine.Terminal terminal) {
        TimelineSessionData.FinishStatus result = data.finish(
                session.sessionId(), session.targetId(), terminal);
        ZapeGRuntime.LOGGER.info(
                "Timeline finish session={} timeline={} target={} status={} reason={} persisted={}",
                session.sessionId(),
                session.timelineId(),
                session.targetId(),
                terminal.status().name().toLowerCase(Locale.ROOT),
                terminal.reason().name().toLowerCase(Locale.ROOT),
                result.name().toLowerCase(Locale.ROOT));
    }

    public record StartResult(boolean success, String message, UUID sessionId) {}
}
