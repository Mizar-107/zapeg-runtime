package io.github.mizar107.zapegruntime.server;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.network.SceneAckC2S;
import io.github.mizar107.zapegruntime.network.SceneNetwork;
import io.github.mizar107.zapegruntime.network.OsScareStatusC2S;
import io.github.mizar107.zapegruntime.scene.CancelReason;
import io.github.mizar107.zapegruntime.scene.SceneAck;
import io.github.mizar107.zapegruntime.scene.SceneDescriptor;
import io.github.mizar107.zapegruntime.scene.SceneProfile;
import io.github.mizar107.zapegruntime.scene.OsScareReport;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class SceneServerManager {

    private static ActiveScene active;
    private static final int MAX_DIAGNOSTIC_PLAYERS = 64;
    private static final Map<UUID, LastOsScareStatus> lastOsScareStatuses = new HashMap<>();

    private SceneServerManager() {}

    public record DispatchResult(boolean success, String message, UUID eventId) {}

    private record ActiveScene(
            SceneDescriptor descriptor,
            int expiresAtServerTick,
            SceneAck lastAcknowledgement,
            OsScareReport osScareReport,
            int osScareSequence) {

        ActiveScene withAcknowledgement(SceneAck acknowledgement) {
            return new ActiveScene(
                    descriptor,
                    expiresAtServerTick,
                    acknowledgement,
                    osScareReport,
                    osScareSequence);
        }

        ActiveScene withOsScareStatus(OsScareReport report, int sequence) {
            return new ActiveScene(
                    descriptor,
                    expiresAtServerTick,
                    lastAcknowledgement,
                    report,
                    sequence);
        }
    }

    private record LastOsScareStatus(UUID eventId, int sequence, OsScareReport report) {}

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
     * @param stage bounded escalation stage; must be {@code 0} unless the
     *     profile declares a {@link SceneProfile#maxStage() max stage}
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
        if (stage < 0 || stage > profile.maxStage()) {
            return failure(
                    "stage is not meaningful for " + profile.serializedName(), eventId);
        }
        int boundedStage = stage;
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
        // Build (and thereby validate) the full wire descriptor BEFORE the
        // ledger consume: an out-of-bounds TTL or a border-adjacent anchor
        // must fail the dispatch without burning a deterministic Director
        // event id — a consumed id can never be retried.
        SceneDescriptor descriptor;
        try {
            descriptor = new SceneDescriptor(
                    eventId,
                    target.getUUID(),
                    target.level().dimension().location(),
                    placement.get().anchor(),
                    placement.get().yawDegrees(),
                    resolveTtlTicks(ttlOverrideTicks, profile),
                    target.getRandom().nextLong(),
                    profile,
                    rehearsal,
                    boundedStage);
        } catch (IllegalArgumentException invalid) {
            return failure(invalid.getMessage(), eventId);
        }
        if (!rehearsal && !SceneLedgerData.get(server).consume(eventId)) {
            return failure("event id is already consumed", eventId);
        }
        // The slot stays occupied for the body TTL plus the full encore, so a
        // false all-clear can never overlap a second scene even if the
        // client's held terminal acknowledgement never arrives.
        active = new ActiveScene(
                descriptor,
                server.getTickCount() + profile.occupancyTicks(descriptor.ttlTicks()),
                null,
                null,
                -1);
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

    public static void handleOsScareStatus(ServerPlayer sender, OsScareStatusC2S message) {
        ActiveScene current = active;
        if (current == null
                || current.descriptor.profile() != SceneProfile.VISITATION_01
                || !current.descriptor.eventId().equals(message.eventId())
                || !current.descriptor.targetId().equals(sender.getUUID())
                || !message.targetId().equals(sender.getUUID())
                || message.sequence() <= current.osScareSequence) {
            return;
        }
        active = current.withOsScareStatus(message.report(), message.sequence());
        rememberOsScareStatus(sender.getUUID(), new LastOsScareStatus(
                message.eventId(), message.sequence(), message.report()));
        ZapeGRuntime.LOGGER.info(
                "Scene {} OS status sequence={} target={} {}",
                message.eventId(),
                message.sequence(),
                sender.getGameProfile().getName(),
                message.report().compactString());
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
                        : current.lastAcknowledgement.name().toLowerCase(Locale.ROOT))
                + (current.descriptor.profile() == SceneProfile.VISITATION_01
                        ? " os=" + (current.osScareReport == null
                                ? "awaiting"
                                : current.osScareReport.compactString())
                        : "");
    }

    /** Permission-gated operator view of the latest bounded client report. */
    public static String diagnose(ServerPlayer target) {
        ActiveScene current = active;
        if (current != null
                && current.descriptor.targetId().equals(target.getUUID())
                && current.descriptor.profile() == SceneProfile.VISITATION_01) {
            return "target=" + target.getGameProfile().getName()
                    + " protocol=" + SceneNetwork.PROTOCOL
                    + " active=1 event=" + current.descriptor.eventId()
                    + " os=" + (current.osScareReport == null
                            ? "awaiting"
                            : current.osScareReport.compactString());
        }
        LastOsScareStatus previous = lastOsScareStatuses.get(target.getUUID());
        if (previous == null) {
            return "target=" + target.getGameProfile().getName()
                    + " protocol=" + SceneNetwork.PROTOCOL
                    + " active=0 os=none";
        }
        return "target=" + target.getGameProfile().getName()
                + " protocol=" + SceneNetwork.PROTOCOL
                + " active=0 last_event=" + previous.eventId
                + " sequence=" + previous.sequence
                + " os=" + previous.report.compactString();
    }

    /** Target-scoped scene summary for the native Heraldor diagnostic tree. */
    public static String statusFor(UUID playerId) {
        ActiveScene current = active;
        if (current == null || !current.descriptor.targetId().equals(playerId)) {
            return "active=0";
        }
        return "active=1 event=" + current.descriptor.eventId()
                + " profile=" + current.descriptor.profile().serializedName()
                + " ack=" + (current.lastAcknowledgement == null
                        ? "none"
                        : current.lastAcknowledgement.name().toLowerCase(Locale.ROOT));
    }

    /**
     * Director-facing TTL resolution: non-positive overrides fall back to
     * the profile default, everything else clamps into the wire descriptor
     * bounds, so no requested length can ever fail descriptor validation.
     */
    static int resolveTtlTicks(int ttlOverrideTicks, SceneProfile profile) {
        if (ttlOverrideTicks <= 0) {
            return profile.defaultTtlTicks();
        }
        return Math.max(
                SceneDescriptor.MIN_TTL_TICKS,
                Math.min(ttlOverrideTicks, MAX_TTL_TICKS));
    }

    static void resetForTests() {
        active = null;
        lastOsScareStatuses.clear();
    }

    private static void rememberOsScareStatus(UUID playerId, LastOsScareStatus status) {
        if (!lastOsScareStatuses.containsKey(playerId)
                && lastOsScareStatuses.size() >= MAX_DIAGNOSTIC_PLAYERS) {
            UUID oldestArbitraryKey = lastOsScareStatuses.keySet().iterator().next();
            lastOsScareStatuses.remove(oldestArbitraryKey);
        }
        lastOsScareStatuses.put(playerId, status);
    }

    private static DispatchResult failure(String message, UUID eventId) {
        return new DispatchResult(false, message, eventId);
    }
}
