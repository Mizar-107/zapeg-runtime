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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class SceneServerManager {

    private static final Map<UUID, ActiveScene> activeByTarget = new HashMap<>();
    private static final OsScareStatusLedger osScareStatuses =
            new OsScareStatusLedger();

    private SceneServerManager() {}

    public record DispatchResult(boolean success, String message, UUID eventId) {}

    /** Stable result vocabulary used by the deterministic timeline adapter. */
    public enum TimelineDispatchStatus {
        APPLIED,
        ALREADY_APPLIED,
        RETRYABLE,
        REJECTED
    }

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
        return dispatchInternal(
                target,
                eventId,
                profile,
                rehearsal,
                ttlOverrideTicks,
                hintX,
                hintZ,
                stage,
                null);
    }

    /**
     * Timeline-only entry point. Its stable event id and visual seed make a
     * replay after restart deterministic without changing the legacy wire
     * descriptor or command surface.
     */
    public static TimelineDispatchStatus dispatchTimeline(
            ServerPlayer target,
            UUID eventId,
            SceneProfile profile,
            int ttlTicks,
            int stage,
            long visualSeed) {
        MinecraftServer server = target.getServer();
        if (server != null && SceneLedgerData.get(server).contains(eventId)) {
            return TimelineDispatchStatus.ALREADY_APPLIED;
        }
        DispatchResult result = dispatchInternal(
                target,
                eventId,
                profile,
                false,
                ttlTicks,
                null,
                null,
                stage,
                visualSeed);
        if (result.success()) {
            return TimelineDispatchStatus.APPLIED;
        }
        return switch (result.message()) {
            case "event id is already consumed" -> TimelineDispatchStatus.ALREADY_APPLIED;
            case "target already has an active scene", "no valid loaded scene anchor" ->
                    TimelineDispatchStatus.RETRYABLE;
            default -> TimelineDispatchStatus.REJECTED;
        };
    }

    private static DispatchResult dispatchInternal(
            ServerPlayer target,
            UUID eventId,
            SceneProfile profile,
            boolean rehearsal,
            int ttlOverrideTicks,
            Double hintX,
            Double hintZ,
            int stage,
            Long visualSeed) {
        MinecraftServer server = target.getServer();
        if (server == null) {
            return failure("server unavailable", eventId);
        }
        if (stage < 0 || stage > profile.maxStage()) {
            return failure(
                    "stage is not meaningful for " + profile.serializedName(), eventId);
        }
        int boundedStage = stage;
        if (activeByTarget.containsKey(target.getUUID())) {
            return failure("target already has an active scene", eventId);
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
                    visualSeed == null ? target.getRandom().nextLong() : visualSeed,
                    profile,
                    rehearsal,
                    boundedStage);
        } catch (IllegalArgumentException invalid) {
            return failure(invalid.getMessage(), eventId);
        }
        if (!rehearsal && !SceneLedgerData.get(server).consume(eventId)) {
            return failure("event id is already consumed", eventId);
        }
        // This target's slot stays occupied for the body TTL plus the full
        // encore, so a false all-clear cannot overlap another private scene
        // for the same player even if the terminal acknowledgement is lost.
        activeByTarget.put(target.getUUID(), new ActiveScene(
                descriptor,
                server.getTickCount() + profile.occupancyTicks(descriptor.ttlTicks()),
                null,
                null,
                -1));
        if (profile == SceneProfile.VISITATION_01) {
            // Preserve an older closing event until the new client actually
            // proves it accepted this visitation by sending status sequence
            // zero. A BUSY acknowledgement must not orphan old cleanup.
            osScareStatuses.onDispatch(target.getUUID(), eventId);
        }
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
        ActiveScene current = activeByTarget.get(sender.getUUID());
        if (current == null
                || !current.descriptor.eventId().equals(message.eventId())
                || !current.descriptor.targetId().equals(sender.getUUID())
                || !message.targetId().equals(sender.getUUID())) {
            return;
        }
        if (!current.descriptor.profile()
                .acceptsAcknowledgement(message.acknowledgement())) {
            ZapeGRuntime.LOGGER.warn(
                    "Ignored scene acknowledgement event={} profile={} ack={} target={}",
                    message.eventId(),
                    current.descriptor.profile().serializedName(),
                    message.acknowledgement().name().toLowerCase(Locale.ROOT),
                    sender.getGameProfile().getName());
            return;
        }
        activeByTarget.put(
                sender.getUUID(), current.withAcknowledgement(message.acknowledgement()));
        ZapeGRuntime.LOGGER.info(
                "Scene {} acknowledgement={} target={}",
                message.eventId(),
                message.acknowledgement().name().toLowerCase(Locale.ROOT),
                sender.getGameProfile().getName());
        if (message.acknowledgement().terminal()) {
            if (current.descriptor.profile() == SceneProfile.VISITATION_01
                    && message.acknowledgement() == SceneAck.BUSY) {
                osScareStatuses.onBusy(sender.getUUID(), message.eventId());
            }
            activeByTarget.remove(sender.getUUID(), current.withAcknowledgement(
                    message.acknowledgement()));
        }
    }

    public static void handleOsScareStatus(ServerPlayer sender, OsScareStatusC2S message) {
        ActiveScene current = activeByTarget.get(sender.getUUID());
        if (!message.targetId().equals(sender.getUUID())) {
            return;
        }
        boolean activeMatch = current != null
                && current.descriptor.profile() == SceneProfile.VISITATION_01
                && current.descriptor.eventId().equals(message.eventId())
                && current.descriptor.targetId().equals(sender.getUUID());
        int activeSequence = activeMatch ? current.osScareSequence : -1;
        if (!osScareStatuses.acceptsStatus(
                sender.getUUID(),
                message.eventId(),
                message.sequence(),
                activeMatch,
                activeSequence)) {
            return;
        }
        if (activeMatch) {
            activeByTarget.put(
                    sender.getUUID(),
                    current.withOsScareStatus(message.report(), message.sequence()));
        }
        osScareStatuses.recordStatus(
                sender.getUUID(),
                message.eventId(),
                message.sequence(),
                message.report());
        ZapeGRuntime.LOGGER.info(
                "Scene {} OS status sequence={} target={} {}",
                message.eventId(),
                message.sequence(),
                sender.getGameProfile().getName(),
                message.report().compactString());
    }

    public static void tick(MinecraftServer server) {
        for (ActiveScene current : new ArrayList<>(activeByTarget.values())) {
            ServerPlayer target =
                    server.getPlayerList().getPlayer(current.descriptor.targetId());
            if (target == null) {
                cancelOne(current.descriptor.targetId(), CancelReason.LOGOUT, server);
            } else if (!target.isAlive()) {
                cancelOne(current.descriptor.targetId(), CancelReason.DEATH, server);
            } else if (!target.level().dimension().location()
                    .equals(current.descriptor.dimension())) {
                cancelOne(
                        current.descriptor.targetId(),
                        CancelReason.DIMENSION_CHANGE,
                        server);
            } else if (server.getTickCount() >= current.expiresAtServerTick) {
                cancelOne(current.descriptor.targetId(), CancelReason.EXPIRED, server);
            }
        }
    }

    public static void cancelForPlayer(UUID playerId, CancelReason reason) {
        cancelOne(
                playerId,
                reason,
                net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());
    }

    public static boolean cancel(CancelReason reason) {
        if (activeByTarget.isEmpty()) {
            return false;
        }
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        for (UUID targetId : new ArrayList<>(activeByTarget.keySet())) {
            cancelOne(targetId, reason, server);
        }
        return true;
    }

    private static boolean cancelOne(
            UUID targetId, CancelReason reason, MinecraftServer server) {
        ActiveScene current = activeByTarget.remove(targetId);
        if (current == null) {
            return false;
        }
        if (server != null) {
            ServerPlayer target = server.getPlayerList().getPlayer(targetId);
            if (target != null) {
                SceneNetwork.cancelFor(target, current.descriptor.eventId(), reason);
            }
        }
        ZapeGRuntime.LOGGER.info(
                "Cancelled scene {} reason={}",
                current.descriptor.eventId(),
                reason.name().toLowerCase(Locale.ROOT));
        return true;
    }

    /**
     * Ends all JVM-local scene state at the server lifecycle boundary.
     *
     * <p>The retained OS report ledger deliberately outlives a player logout
     * while the same server is running so late cleanup truth can still arrive.
     * It must not, however, outlive the server itself: integrated-server world
     * switches reuse the client JVM and would otherwise expose the previous
     * world's visitation report in the next world's diagnostics.</p>
     */
    public static void shutdown() {
        cancel(CancelReason.SERVER_STOP);
        active = null;
        osScareStatuses.clear();
    }

    public static String status() {
        if (activeByTarget.isEmpty()) {
            return "active=0";
        }
        if (activeByTarget.size() > 1) {
            return "active=" + activeByTarget.size();
        }
        ActiveScene current = activeByTarget.values().iterator().next();
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
        ActiveScene current = activeByTarget.get(target.getUUID());
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
        OsScareStatusLedger.Entry previous = osScareStatuses.get(target.getUUID());
        if (previous == null) {
            return "target=" + target.getGameProfile().getName()
                    + " protocol=" + SceneNetwork.PROTOCOL
                    + " active=0 os=none";
        }
        return "target=" + target.getGameProfile().getName()
                + " protocol=" + SceneNetwork.PROTOCOL
                + " active=0 last_event=" + previous.eventId()
                + " sequence=" + previous.sequence()
                + " os=" + (previous.report() == null
                        ? "awaiting"
                        : previous.report().compactString());
    }

    /** Target-scoped scene summary for the native Heraldor diagnostic tree. */
    public static String statusFor(UUID playerId) {
        ActiveScene current = activeByTarget.get(playerId);
        if (current == null) {
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
        activeByTarget.clear();
        osScareStatuses.clear();
    }

    private static DispatchResult failure(String message, UUID eventId) {
        return new DispatchResult(false, message, eventId);
    }
}
