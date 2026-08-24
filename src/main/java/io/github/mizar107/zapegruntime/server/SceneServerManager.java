package io.github.mizar107.zapegruntime.server;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.director.DirectorSceneIdentity;
import io.github.mizar107.zapegruntime.director.HeraldorDirector;
import io.github.mizar107.zapegruntime.director.VoiceRehearsalManager;
import io.github.mizar107.zapegruntime.director.VoiceRehearsalPlan;
import io.github.mizar107.zapegruntime.network.SceneAckC2S;
import io.github.mizar107.zapegruntime.network.SceneNetwork;
import io.github.mizar107.zapegruntime.network.OsScareStatusC2S;
import io.github.mizar107.zapegruntime.scene.CancelReason;
import io.github.mizar107.zapegruntime.scene.SceneAck;
import io.github.mizar107.zapegruntime.scene.SceneDescriptor;
import io.github.mizar107.zapegruntime.scene.SceneProfile;
import io.github.mizar107.zapegruntime.scene.OsScareReport;
import io.github.mizar107.zapegruntime.timeline.TimelineReplayData;
import io.github.mizar107.zapegruntime.timeline.TimelineReplayIdentity;
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
            int osScareSequence,
            DirectorSceneIdentity directorIdentity) {

        ActiveScene withAcknowledgement(SceneAck acknowledgement) {
            return new ActiveScene(
                    descriptor,
                    expiresAtServerTick,
                    acknowledgement,
                    osScareReport,
                    osScareSequence,
                    directorIdentity);
        }

        ActiveScene withOsScareStatus(OsScareReport report, int sequence) {
            return new ActiveScene(
                    descriptor,
                    expiresAtServerTick,
                    lastAcknowledgement,
                    report,
                    sequence,
                    directorIdentity);
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
                null,
                null,
                null,
                null);
    }

    /**
     * Exact authored Voice presentation with rehearsal authority only. The
     * descriptor is explicitly rehearsal-marked and carries neither timeline
     * replay identity nor Director proof identity.
     */
    public static DispatchResult dispatchVoiceRehearsal(
            ServerPlayer target,
            UUID eventId,
            VoiceRehearsalPlan plan) {
        return dispatchInternal(
                target,
                eventId,
                plan.profile(),
                true,
                plan.ttlTicks(),
                null,
                null,
                plan.stage(),
                plan.visualSeed(eventId),
                null,
                null,
                null);
    }

    /**
     * Director-only provenance path. Manual commands, rehearsals, and timeline
     * actions use the legacy entry points above and therefore carry no story
     * identity even when an operator supplies the same UUID.
     */
    public static DispatchResult dispatchDirector(
            ServerPlayer target,
            DirectorSceneIdentity identity,
            SceneProfile profile,
            int ttlTicks,
            int stage) {
        if (identity == null || !identity.targetId().equals(target.getUUID())) {
            UUID eventId = identity == null ? new UUID(0L, 0L) : identity.eventId();
            return failure("Director identity mismatch", eventId);
        }
        return dispatchInternal(
                target,
                identity.eventId(),
                profile,
                false,
                ttlTicks,
                null,
                null,
                stage,
                identity.visualSeed(),
                identity.placementSeed(),
                null,
                identity);
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
            long visualSeed,
            long placementSeed,
            TimelineReplayIdentity replayIdentity) {
        MinecraftServer server = target.getServer();
        if (server == null
                || replayIdentity == null
                || !eventId.equals(replayIdentity.eventId())
                || !target.getUUID().equals(replayIdentity.targetId())) {
            return TimelineDispatchStatus.REJECTED;
        }
        if (!HeraldorSafetyController.allows(server, HeraldorSafetyMode.LIVE)) {
            return TimelineDispatchStatus.REJECTED;
        }
        TimelineReplayData replayData = TimelineReplayData.get(server);
        TimelineReplayData.DispatchClaim claim = replayData.claimForDispatch(
                replayIdentity, SceneLedgerData.get(server).contains(eventId));
        if (claim == TimelineReplayData.DispatchClaim.ALREADY_APPLIED) {
            return TimelineDispatchStatus.ALREADY_APPLIED;
        }
        if (!claim.mayDispatch()) {
            return TimelineDispatchStatus.REJECTED;
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
                visualSeed,
                placementSeed,
                replayIdentity,
                null);
        if (result.success()) {
            return replayData.markApplied(replayIdentity)
                    ? TimelineDispatchStatus.APPLIED
                    : TimelineDispatchStatus.REJECTED;
        }
        replayData.rollbackReservation(replayIdentity);
        return switch (result.message()) {
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
            Long visualSeed,
            Long placementSeed,
            TimelineReplayIdentity replayIdentity,
            DirectorSceneIdentity directorIdentity) {
        MinecraftServer server = target.getServer();
        if (server == null) {
            return failure("server unavailable", eventId);
        }
        HeraldorSafetyMode required = directorIdentity != null
                ? HeraldorSafetyMode.AUTO
                : rehearsal ? HeraldorSafetyMode.MANUAL : HeraldorSafetyMode.LIVE;
        if (!HeraldorSafetyController.allows(server, required)) {
            return failure(HeraldorSafetyController.denial(server, required), eventId);
        }
        if (directorIdentity != null
                && (!eventId.equals(directorIdentity.eventId())
                        || !target.getUUID().equals(directorIdentity.targetId())
                        || rehearsal
                        || replayIdentity != null)) {
            return failure("Director identity mismatch", eventId);
        }
        if (stage < 0 || stage > profile.maxStage()) {
            return failure(
                    "stage is not meaningful for " + profile.serializedName(), eventId);
        }
        TimelineReplayData replayData = null;
        if (!rehearsal) {
            replayData = TimelineReplayData.get(server);
            if (!replayData.supportsCurrentSchema()) {
                return failure("timeline replay data is unavailable", eventId);
            }
            if (replayIdentity != null
                    && (!eventId.equals(replayIdentity.eventId())
                            || !target.getUUID().equals(replayIdentity.targetId())
                            || !replayData.isReserved(replayIdentity))) {
                return failure("timeline replay identity is not reserved", eventId);
            }
        }
        int boundedStage = stage;
        if (activeByTarget.containsKey(target.getUUID())) {
            return failure("target already has an active scene", eventId);
        }
        if (!target.isAlive() || target.isSpectator()) {
            return failure("target is not eligible", eventId);
        }
        int resolvedTtlTicks = resolveTtlTicks(ttlOverrideTicks, profile);
        TimelineReplayData.ExternalSceneIdentity externalIdentity = null;
        if (!rehearsal && replayIdentity == null) {
            try {
                externalIdentity = TimelineReplayData.ExternalSceneIdentity.create(
                        eventId,
                        target.getUUID(),
                        profile.serializedName(),
                        resolvedTtlTicks,
                        boundedStage);
            } catch (IllegalArgumentException invalid) {
                return failure(invalid.getMessage(), eventId);
            }
            TimelineReplayData.ExternalDispatchClaim claim =
                    replayData.claimExternalForDispatch(
                            externalIdentity,
                            SceneLedgerData.get(server).contains(eventId));
            if (claim == TimelineReplayData.ExternalDispatchClaim.ALREADY_APPLIED) {
                return failure("event id is already consumed", eventId);
            }
            if (!claim.mayDispatch()) {
                return failure("event replay identity is rejected", eventId);
            }
        }
        Optional<ScenePlacement.Placement> placement = placementSeed == null
                ? ScenePlacement.find(target, profile, hintX, hintZ, boundedStage)
                : ScenePlacement.findSeeded(
                        target,
                        profile,
                        hintX,
                        hintZ,
                        boundedStage,
                        placementSeed);
        if (placement.isEmpty()) {
            rollbackExternalReservation(replayData, externalIdentity);
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
                    resolvedTtlTicks,
                    visualSeed == null ? target.getRandom().nextLong() : visualSeed,
                    profile,
                    rehearsal,
                    boundedStage);
        } catch (IllegalArgumentException invalid) {
            rollbackExternalReservation(replayData, externalIdentity);
            return failure(invalid.getMessage(), eventId);
        }
        if (!rehearsal && !SceneLedgerData.get(server).consume(eventId)) {
            return failure("event id is already consumed", eventId);
        }
        if (externalIdentity != null
                && !replayData.markExternalApplied(externalIdentity)) {
            return failure("event replay identity could not be committed", eventId);
        }
        // This target's slot stays occupied for the body TTL plus the full
        // encore, so a false all-clear cannot overlap another private scene
        // for the same player even if the terminal acknowledgement is lost.
        activeByTarget.put(target.getUUID(), new ActiveScene(
                descriptor,
                server.getTickCount() + profile.occupancyTicks(descriptor.ttlTicks()),
                null,
                null,
                -1,
                directorIdentity));
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
                target.getUUID(),
                rehearsal);
        return new DispatchResult(true, "scene dispatched", eventId);
    }

    private static void rollbackExternalReservation(
            TimelineReplayData replayData,
            TimelineReplayData.ExternalSceneIdentity externalIdentity) {
        if (replayData != null && externalIdentity != null) {
            replayData.rollbackExternalReservation(externalIdentity);
        }
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
                    sender.getUUID());
            return;
        }
        activeByTarget.put(
                sender.getUUID(), current.withAcknowledgement(message.acknowledgement()));
        VoiceRehearsalManager.onAcknowledgement(
                sender.getUUID(), message.eventId(), message.acknowledgement());
        ZapeGRuntime.LOGGER.info(
                "Scene {} acknowledgement={} target={}",
                message.eventId(),
                message.acknowledgement().name().toLowerCase(Locale.ROOT),
                sender.getUUID());
        if (current.directorIdentity != null) {
            HeraldorDirector.onAcknowledgement(
                    sender.getServer(),
                    current.directorIdentity,
                    current.descriptor.profile(),
                    message.acknowledgement());
        }
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
                sender.getUUID(),
                message.report().compactString());
        if (activeMatch && current.directorIdentity != null) {
            HeraldorDirector.onOsScareStatus(
                    sender.getServer(),
                    current.directorIdentity,
                    current.descriptor.profile(),
                    message.report());
        }
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
        return cancelAll(reason) > 0;
    }

    /** Cancels every in-memory scene and returns the exact number removed. */
    public static int cancelAll(CancelReason reason) {
        if (activeByTarget.isEmpty()) {
            return 0;
        }
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        int cancelled = 0;
        for (UUID targetId : new ArrayList<>(activeByTarget.keySet())) {
            if (cancelOne(targetId, reason, server)) {
                cancelled++;
            }
        }
        return cancelled;
    }

    public static int activeCount() {
        return activeByTarget.size();
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
        if (server != null && current.directorIdentity != null) {
            HeraldorDirector.onCancelled(server, current.directorIdentity, reason);
        }
        VoiceRehearsalManager.onCancelled(
                targetId, current.descriptor.eventId(), reason);
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
        activeByTarget.clear();
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

    /** Exact JVM-local liveness check; it never loads a player, level, or chunk. */
    public static boolean isDirectorSceneActive(UUID targetId, UUID eventId) {
        ActiveScene current = activeByTarget.get(targetId);
        return current != null
                && current.directorIdentity != null
                && current.descriptor.eventId().equals(eventId)
                && current.directorIdentity.eventId().equals(eventId)
                && current.directorIdentity.targetId().equals(targetId);
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
