package io.github.mizar107.zapegruntime.server;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.network.SceneAckC2S;
import io.github.mizar107.zapegruntime.network.SceneNetwork;
import io.github.mizar107.zapegruntime.scene.CancelReason;
import io.github.mizar107.zapegruntime.scene.ColossusChoreography;
import io.github.mizar107.zapegruntime.scene.SceneAck;
import io.github.mizar107.zapegruntime.scene.SceneDescriptor;
import io.github.mizar107.zapegruntime.scene.SceneProfile;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class SceneServerManager {

    private static ActiveScene active;

    private SceneServerManager() {}

    public record DispatchResult(boolean success, String message, UUID eventId) {}

    private record ActiveScene(
            SceneDescriptor descriptor,
            int expiresAtServerTick,
            SceneAck lastAcknowledgement) {

        ActiveScene withAcknowledgement(SceneAck acknowledgement) {
            return new ActiveScene(descriptor, expiresAtServerTick, acknowledgement);
        }
    }

    /** Hard bound for Director-requested TTL overrides (60 seconds). */
    public static final int MAX_TTL_TICKS = SceneDescriptor.MAX_TTL_TICKS;

    public static DispatchResult rehearse(ServerPlayer target, SceneProfile profile) {
        return rehearse(target, profile, 0);
    }

    public static DispatchResult rehearse(
            ServerPlayer target, SceneProfile profile, int stage) {
        return dispatch(target, UUID.randomUUID(), profile, true, 0, null, null, stage);
    }

    public static DispatchResult dispatch(
            ServerPlayer target,
            UUID eventId,
            SceneProfile profile,
            boolean rehearsal) {
        return dispatch(target, eventId, profile, rehearsal, 0);
    }

    public static DispatchResult dispatch(
            ServerPlayer target,
            UUID eventId,
            SceneProfile profile,
            boolean rehearsal,
            int ttlOverrideTicks) {
        return dispatch(target, eventId, profile, rehearsal, ttlOverrideTicks, null, null);
    }

    public static DispatchResult dispatch(
            ServerPlayer target,
            UUID eventId,
            SceneProfile profile,
            boolean rehearsal,
            int ttlOverrideTicks,
            Double hintX,
            Double hintZ) {
        return dispatch(target, eventId, profile, rehearsal, ttlOverrideTicks, hintX, hintZ, 0);
    }

    /**
     * @param ttlOverrideTicks Director-computed TTL; non-positive falls back
     *     to the profile default, and every value is clamped to the wire bound
     * @param hintX optional Director stalking-memory anchor bias, ignored when
     *     the hint is missing, non-finite or unreasonably far from the target
     * @param stage bounded escalation stage; only colossus_01 may carry a
     *     non-zero stage, anything else fails closed
     */
    public static DispatchResult dispatch(
            ServerPlayer target,
            UUID eventId,
            SceneProfile profile,
            boolean rehearsal,
            int ttlOverrideTicks,
            Double hintX,
            Double hintZ,
            int stage) {
        MinecraftServer server = target.getServer();
        if (server == null) {
            return failure("server unavailable", eventId);
        }
        int boundedStage = ColossusChoreography.clampStage(stage);
        if (boundedStage != 0 && profile != SceneProfile.COLOSSUS_01) {
            return failure("stage is only meaningful for colossus_01", eventId);
        }
        if (active != null) {
            return failure("another scene is active", eventId);
        }
        if (!target.isAlive() || target.isSpectator()) {
            return failure("target is not eligible", eventId);
        }
        Optional<ScenePlacement.Placement> placement =
                ScenePlacement.find(target, profile, hintX, hintZ, boundedStage);
        if (placement.isEmpty()) {
            return failure("no valid loaded scene anchor", eventId);
        }
        if (!rehearsal && !SceneLedgerData.get(server).consume(eventId)) {
            return failure("event id is already consumed", eventId);
        }

        int ttlTicks = ttlOverrideTicks > 0
                ? Math.min(ttlOverrideTicks, MAX_TTL_TICKS)
                : profile.defaultTtlTicks();
        SceneDescriptor descriptor = new SceneDescriptor(
                eventId,
                target.getUUID(),
                target.level().dimension().location(),
                placement.get().anchor(),
                placement.get().yawDegrees(),
                ttlTicks,
                target.getRandom().nextLong(),
                profile,
                rehearsal,
                boundedStage);
        // The slot stays occupied for the body TTL plus the full encore, so a
        // false all-clear can never overlap a second scene even if the
        // client's held terminal acknowledgement never arrives.
        active = new ActiveScene(
                descriptor,
                server.getTickCount() + profile.occupancyTicks(ttlTicks),
                null);
        SceneNetwork.spawnFor(target, descriptor);
        ZapeGRuntime.LOGGER.info(
                "Dispatched scene {} profile={} target={} rehearsal={}",
                eventId,
                profile.serializedName(),
                target.getGameProfile().getName(),
                rehearsal);
        return new DispatchResult(true, "scene dispatched", eventId);
    }

    public static void handleAcknowledgement(ServerPlayer sender, SceneAckC2S message) {
        ActiveScene current = active;
        if (current == null
                || !current.descriptor.eventId().equals(message.eventId())
                || !current.descriptor.targetId().equals(sender.getUUID())
                || !message.targetId().equals(sender.getUUID())) {
            return;
        }
        active = current.withAcknowledgement(message.acknowledgement());
        ZapeGRuntime.LOGGER.info(
                "Scene {} acknowledgement={} target={}",
                message.eventId(),
                message.acknowledgement().name().toLowerCase(Locale.ROOT),
                sender.getGameProfile().getName());
        if (message.acknowledgement().terminal()) {
            active = null;
        }
    }

    public static void tick(MinecraftServer server) {
        ActiveScene current = active;
        if (current == null) {
            return;
        }
        ServerPlayer target = server.getPlayerList().getPlayer(current.descriptor.targetId());
        if (target == null) {
            active = null;
            return;
        }
        if (!target.isAlive()) {
            cancel(CancelReason.DEATH);
            return;
        }
        if (!target.level().dimension().location().equals(current.descriptor.dimension())) {
            cancel(CancelReason.DIMENSION_CHANGE);
            return;
        }
        if (server.getTickCount() >= current.expiresAtServerTick) {
            cancel(CancelReason.EXPIRED);
        }
    }

    public static void cancelForPlayer(UUID playerId, CancelReason reason) {
        ActiveScene current = active;
        if (current != null && current.descriptor.targetId().equals(playerId)) {
            cancel(reason);
        }
    }

    public static boolean cancel(CancelReason reason) {
        ActiveScene current = active;
        if (current == null) {
            return false;
        }
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer target = server.getPlayerList().getPlayer(current.descriptor.targetId());
            if (target != null) {
                SceneNetwork.cancelFor(
                        target,
                        current.descriptor.eventId(),
                        reason);
            }
        }
        ZapeGRuntime.LOGGER.info(
                "Cancelled scene {} reason={}",
                current.descriptor.eventId(),
                reason.name().toLowerCase(Locale.ROOT));
        active = null;
        return true;
    }

    public static String status() {
        ActiveScene current = active;
        if (current == null) {
            return "active=0";
        }
        return "active=1 event=" + current.descriptor.eventId()
                + " target=" + current.descriptor.targetId()
                + " profile=" + current.descriptor.profile().serializedName()
                + " ack=" + (current.lastAcknowledgement == null
                        ? "none"
                        : current.lastAcknowledgement.name().toLowerCase(Locale.ROOT));
    }

    static void resetForTests() {
        active = null;
    }

    private static DispatchResult failure(String message, UUID eventId) {
        return new DispatchResult(false, message, eventId);
    }
}
