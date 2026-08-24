package io.github.mizar107.zapegruntime.boss.encounter;

import io.github.mizar107.zapegruntime.boss.api.NinthFormCombatSignal;
import io.github.mizar107.zapegruntime.boss.api.NinthFormCombatSnapshot;
import io.github.mizar107.zapegruntime.boss.api.NinthFormEntityGateway;
import io.github.mizar107.zapegruntime.boss.api.NinthFormPhase;
import io.github.mizar107.zapegruntime.story.StoryCampaignDefinition;
import io.github.mizar107.zapegruntime.story.StoryCampaignRegistry;
import io.github.mizar107.zapegruntime.story.StoryService;
import io.github.mizar107.zapegruntime.story.StoryWorldData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Durable scheduler, recovery coordinator, and story/reward integration owner. */
public final class NinthFormEncounterManager {

    public static final int RECONCILE_INTERVAL_TICKS = 20;
    public static final int MAX_RETRY_TARGETS = 4_096;
    public static final long MAX_RETRY_BACKOFF_TICKS = 1_200L;

    private static final Set<MinecraftServer> STOPPING_SERVERS =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<MinecraftServer, LinkedHashSet<UUID>> QUEUED_TARGETS =
            new WeakHashMap<>();
    private static final Map<MinecraftServer, RetryBook<UUID>> PROOF_RETRIES =
            new WeakHashMap<>();
    private static final Map<MinecraftServer, RetryBook<UUID>> START_RETRIES =
            new WeakHashMap<>();

    private NinthFormEncounterManager() {}

    /** Story-owned live start. Rehearsals use {@link #rehearse(ServerPlayer)}. */
    public static StartResult startIfEligible(ServerPlayer target) {
        return start(target, false);
    }

    public static StartResult rehearse(ServerPlayer target) {
        return start(target, true);
    }

    public static Optional<NinthFormEncounter> activeFor(
            MinecraftServer server, UUID targetId) {
        requireServerThread(server);
        return NinthFormEncounterData.get(server).activeFor(targetId);
    }

    /** Combat callbacks enter here; story work is deliberately deferred to a later tick. */
    public static void onCombatSignal(MinecraftServer server, NinthFormCombatSignal signal) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(signal, "signal");
        if (!server.isSameThread()) {
            server.execute(() -> onCombatSignal(server, signal));
            return;
        }
        NinthFormEncounterData data = NinthFormEncounterData.get(server);
        switch (signal.kind()) {
            case PHASE_COMPLETED -> {
                NinthFormEncounterData.ProofResult result = data.recordPhaseCompletion(signal);
                if (result == NinthFormEncounterData.ProofResult.RECORDED
                        || result == NinthFormEncounterData.ProofResult.REPLAYED) {
                    queueTarget(server, signal.identity().targetId());
                }
            }
            case DEFEATED -> {
                NinthFormEncounterData.ProofResult result = data.recordDefeat(signal);
                if (result == NinthFormEncounterData.ProofResult.RECORDED
                        || result == NinthFormEncounterData.ProofResult.REPLAYED) {
                    queueTarget(server, signal.identity().targetId());
                }
            }
            case SUSPENDED -> suspendFromSignal(
                    data, NinthFormGatewayRegistry.current(server), signal);
        }
    }

    static NinthFormEncounterData.MutationResult suspendFromSignal(
            NinthFormEncounterData data,
            NinthFormEntityGateway gateway,
            NinthFormCombatSignal signal) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(gateway, "gateway");
        Objects.requireNonNull(signal, "signal");
        if (signal.kind() != NinthFormCombatSignal.Kind.SUSPENDED) {
            return NinthFormEncounterData.MutationResult.STATE_MISMATCH;
        }
        NinthFormEncounterData.MutationResult result =
                data.suspend(signal.identity(), signal.entityId());
        if (result == NinthFormEncounterData.MutationResult.APPLIED
                || result == NinthFormEncounterData.MutationResult.IDEMPOTENT) {
            gateway.discardLoaded(signal.identity(), signal.entityId());
        }
        return result;
    }

    /** Exact NBT identity/generation gate used by the combat entity join hook. */
    public static boolean acceptsEntity(
            MinecraftServer server,
            io.github.mizar107.zapegruntime.boss.api.NinthFormIdentity identity,
            UUID entityId) {
        return server != null
                && server.isSameThread()
                && NinthFormEncounterData.get(server).acceptsEntity(identity, entityId);
    }

    /** Neutral story events enqueue only; they never cause an inline story mutation. */
    public static void queueStoryAdvance(MinecraftServer server, UUID targetId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(targetId, "targetId");
        if (server.isSameThread()) {
            queueTarget(server, targetId);
        } else {
            server.execute(() -> queueTarget(server, targetId));
        }
    }

    public static void tick(MinecraftServer server) {
        requireServerThread(server);
        if (STOPPING_SERVERS.contains(server)
                || server.overworld().getGameTime() % RECONCILE_INTERVAL_TICKS != 0L) {
            return;
        }
        long gameTick = server.overworld().getGameTime();
        LinkedHashSet<UUID> work = new LinkedHashSet<>(drainTargets(server));
        work.addAll(retryBook(server).due(gameTick));
        work.addAll(startRetryBook(server).due(gameTick));
        for (UUID targetId : work) {
            List<NinthFormProgressionSync.SyncResult> results =
                    NinthFormProgressionSync.replayTarget(server, targetId);
            reconcileProofResults(retryBook(server), targetId, gameTick, results);
            awardDefeatToast(server, targetId);
            ServerPlayer target = server.getPlayerList().getPlayer(targetId);
            if (target != null) {
                StartResult start = startIfEligible(target);
                reconcileStartResult(startRetryBook(server), targetId, gameTick, start.status());
            } else {
                // Login reconstructs the immediate queue; do not poll an offline
                // player every reconcile interval.
                startRetryBook(server).clear(targetId);
            }
        }

        Collection<NinthFormEncounter> active =
                NinthFormEncounterData.get(server).activeEncounters();
        for (NinthFormEncounter encounter : active) {
            reconcile(server, encounter.encounterId());
        }
    }

    public static void onPlayerAvailable(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server != null) {
            queueTarget(server, player.getUUID());
        }
    }

    public static void suspendForTarget(MinecraftServer server, UUID targetId) {
        requireServerThread(server);
        NinthFormEncounterData.get(server).activeFor(targetId)
                .ifPresent(encounter -> suspendEncounter(server, encounter));
    }

    public static void onServerStarted(MinecraftServer server) {
        requireServerThread(server);
        STOPPING_SERVERS.remove(server);
        NinthFormProgressionSync.replayAll(server);
        NinthFormEncounterData data = NinthFormEncounterData.get(server);
        data.activeEncounters().forEach(encounter -> queueTarget(server, encounter.targetId()));
        data.immutableBarriers().forEach(barrier -> queueTarget(server, barrier.targetId()));
        server.getPlayerList().getPlayers().forEach(player -> queueTarget(server, player.getUUID()));
    }

    public static void onServerStopping(MinecraftServer server) {
        requireServerThread(server);
        STOPPING_SERVERS.add(server);
        for (NinthFormEncounter encounter : NinthFormEncounterData.get(server).activeEncounters()) {
            suspendEncounter(server, encounter);
        }
        QUEUED_TARGETS.remove(server);
        PROOF_RETRIES.remove(server);
        START_RETRIES.remove(server);
    }

    private static StartResult start(ServerPlayer target, boolean rehearsal) {
        Objects.requireNonNull(target, "target");
        MinecraftServer server = target.getServer();
        if (server == null) {
            return StartResult.failed(StartStatus.NO_SERVER, "target has no server");
        }
        requireServerThread(server);
        if (STOPPING_SERVERS.contains(server)) {
            return StartResult.failed(StartStatus.SERVER_STOPPING, "server is stopping");
        }
        if (!NinthFormGatewayRegistry.available(server)) {
            return StartResult.failed(StartStatus.GATEWAY_UNAVAILABLE, "combat gateway is not installed");
        }
        NinthFormEncounterData data = NinthFormEncounterData.get(server);
        if (!data.schemaStatus().writable()) {
            return StartResult.failed(StartStatus.DATA_UNAVAILABLE, data.schemaStatus().detail());
        }
        Optional<NinthFormEncounter> existing = data.activeFor(target.getUUID());
        if (existing.isPresent()) {
            return new StartResult(
                    StartStatus.ALREADY_ACTIVE,
                    existing.get().encounterId(),
                    existing.get().entityId(),
                    "target already owns a Ninth Form attempt");
        }

        Optional<StoryCampaignDefinition> loaded = StoryCampaignRegistry.current()
                .find(StoryCampaignRegistry.HERALDOR_CAMPAIGN);
        if (loaded.isEmpty() || !NinthFormStoryGate.definitionIsExact(loaded.get())) {
            return StartResult.failed(
                    StartStatus.STORY_NOT_READY,
                    "exact Heraldor boss campaign definition is not loaded");
        }
        StoryCampaignDefinition campaign = loaded.get();
        Optional<StoryWorldData.PlayerSnapshot> snapshot = StoryService.snapshot(
                server, target.getUUID());
        NinthFormStoryGate.Envelope envelope;
        if (rehearsal) {
            envelope = snapshot
                    .filter(state -> state.campaignId().equals(campaign.id())
                            && state.campaignRevision() == campaign.revision()
                            && state.definitionFingerprint().equals(campaign.fingerprint()))
                    .map(state -> new NinthFormStoryGate.Envelope(
                            campaign.id(), campaign.revision(), campaign.fingerprint(), state.progressEpoch()))
                    .orElseGet(() -> new NinthFormStoryGate.Envelope(
                            campaign.id(), campaign.revision(), campaign.fingerprint(), 0L));
        } else {
            if (!StoryWorldData.get(server).schemaStatus().writable()) {
                return StartResult.failed(StartStatus.STORY_NOT_READY, "story data is read-only");
            }
            NinthFormStoryGate.Decision gate = NinthFormStoryGate.forStart(campaign, snapshot);
            if (gate.status() != NinthFormStoryGate.Status.ELIGIBLE) {
                return StartResult.failed(StartStatus.NOT_EXPECTED, gate.detail());
            }
            envelope = gate.envelope().orElseThrow();
        }

        ServerLevel level = target.serverLevel();
        BlockPos center = target.blockPosition();
        if (level.isOutsideBuildHeight(center)) {
            return StartResult.failed(StartStatus.ARENA_UNAVAILABLE, "target is outside build height");
        }
        List<NinthFormArenaPolicy.OccupiedArena> loadedBosses = loadedArenas(server, null);
        NinthFormArenaPolicy.PlanResult plan = NinthFormArenaPolicy.plan(
                level.dimension().location().toString(),
                center.getX(),
                center.getY(),
                center.getZ(),
                loadedBosses,
                level.getChunkSource()::hasChunk);
        if (plan.status() != NinthFormArenaPolicy.Status.READY) {
            return StartResult.failed(StartStatus.ARENA_UNAVAILABLE, plan.detail());
        }
        int participants = participantCount(level, center);
        NinthFormScalingPolicy.Scale scale = NinthFormScalingPolicy.forParticipants(participants);
        NinthFormArenaPolicy.Arena arena = plan.arena();
        UUID encounterId = UUID.randomUUID();
        UUID phaseFactId = NinthFormFactIds.forProof(
                encounterId,
                target.getUUID(),
                envelope,
                NinthFormBarrier.Kind.PHASE_ONE_COMPLETED);
        UUID defeatFactId = NinthFormFactIds.forProof(
                encounterId,
                target.getUUID(),
                envelope,
                NinthFormBarrier.Kind.DEFEATED);
        NinthFormEncounter encounter = new NinthFormEncounter(
                encounterId,
                target.getUUID(),
                UUID.randomUUID(),
                phaseFactId,
                defeatFactId,
                0,
                rehearsal,
                envelope.campaignId(),
                envelope.campaignRevision(),
                envelope.campaignFingerprint(),
                envelope.progressEpoch(),
                arena.dimensionId(),
                arena.centerX(),
                arena.centerY(),
                arena.centerZ(),
                NinthFormPhase.PRELUDE,
                NinthFormEncounter.Lifecycle.PREPARED,
                scale.participantCount(),
                scale.healthScale(),
                scale.damageScale(),
                new NinthFormCombatSnapshot.CombatState(0, 0L, "idle", 0),
                NinthFormCombatSnapshot.VitalState.pristine(),
                level.getGameTime());
        NinthFormEncounterData.BeginResult begun = data.begin(encounter);
        if (begun.status() != NinthFormEncounterData.BeginStatus.STARTED
                && begun.status() != NinthFormEncounterData.BeginStatus.IDEMPOTENT) {
            return StartResult.failed(StartStatus.DATA_REFUSED, begun.detail());
        }
        boolean spawned = spawnPrepared(server, begun.encounter());
        return new StartResult(
                spawned ? StartStatus.STARTED : StartStatus.SUSPENDED_FOR_RECOVERY,
                encounter.encounterId(),
                encounter.entityId(),
                spawned ? "Ninth Form started" : "attempt is durable and waiting for loaded recovery");
    }

    private static void reconcile(MinecraftServer server, UUID encounterId) {
        NinthFormEncounterData data = NinthFormEncounterData.get(server);
        NinthFormEncounter encounter = data.findByEncounter(encounterId).orElse(null);
        if (encounter == null) {
            return;
        }
        ServerPlayer target = server.getPlayerList().getPlayer(encounter.targetId());
        if (target == null
                || !target.isAlive()
                || !target.level().dimension().location().toString().equals(encounter.dimensionId())
                || !NinthFormArenaPolicy.contains(
                        encounter.arenaX(),
                        encounter.arenaZ(),
                        target.getX(),
                        target.getZ())) {
            suspendEncounter(server, encounter);
            return;
        }
        if (!campaignEnvelopeAllowsResume(server, encounter)) {
            suspendEncounter(server, encounter);
            return;
        }
        if (encounter.lifecycle() == NinthFormEncounter.Lifecycle.SUSPENDED) {
            resumeSuspended(server, target, encounter);
            return;
        }
        if (encounter.lifecycle() == NinthFormEncounter.Lifecycle.PREPARED) {
            spawnPrepared(server, encounter);
            return;
        }
        NinthFormEntityGateway gateway = NinthFormGatewayRegistry.current(server);
        Optional<NinthFormCombatSnapshot> observed =
                gateway.observeLoaded(encounter.identity(), encounter.entityId());
        if (observed.isEmpty()) {
            suspendEncounter(server, encounter);
            return;
        }
        NinthFormEncounterData.MutationResult stored = data.storeSnapshot(observed.get());
        if (stored == NinthFormEncounterData.MutationResult.IDENTITY_MISMATCH
                || stored == NinthFormEncounterData.MutationResult.STATE_MISMATCH) {
            suspendEncounter(server, encounter);
            return;
        }
        NinthFormEncounter refreshed = data.findByEncounter(encounterId).orElse(null);
        if (refreshed != null) {
            driveCheckpoint(server, refreshed);
        }
    }

    private static boolean spawnPrepared(MinecraftServer server, NinthFormEncounter encounter) {
        NinthFormEncounterData data = NinthFormEncounterData.get(server);
        ServerLevel level = level(server, encounter.dimensionId());
        if (level == null || encounter.lifecycle() != NinthFormEncounter.Lifecycle.PREPARED) {
            return false;
        }
        List<NinthFormArenaPolicy.OccupiedArena> others =
                loadedArenas(server, encounter.encounterId());
        NinthFormArenaPolicy.PlanResult plan = NinthFormArenaPolicy.plan(
                encounter.dimensionId(),
                encounter.arenaX(),
                encounter.arenaY(),
                encounter.arenaZ(),
                others,
                level.getChunkSource()::hasChunk);
        if (plan.status() != NinthFormArenaPolicy.Status.READY) {
            data.suspend(encounter.identity(), encounter.entityId());
            return false;
        }
        NinthFormEntityGateway gateway = NinthFormGatewayRegistry.current(server);
        NinthFormEntityGateway.SpawnResult spawned = gateway.spawnLoaded(encounter.spawnRequest());
        if (spawned.status() != NinthFormEntityGateway.Status.APPLIED
                || spawned.entityId().isEmpty()
                || !spawned.entityId().get().equals(encounter.entityId())) {
            spawned.entityId().ifPresent(entityId ->
                    gateway.discardLoaded(encounter.identity(), entityId));
            data.suspend(encounter.identity(), encounter.entityId());
            return false;
        }
        NinthFormEncounterData.MutationResult activated =
                data.activate(encounter.identity(), encounter.entityId());
        if (activated != NinthFormEncounterData.MutationResult.APPLIED
                && activated != NinthFormEncounterData.MutationResult.IDEMPOTENT) {
            gateway.discardLoaded(encounter.identity(), encounter.entityId());
            return false;
        }
        NinthFormEncounter active = data.findByEncounter(encounter.encounterId()).orElseThrow();
        driveCheckpoint(server, active);
        return true;
    }

    private static void driveCheckpoint(MinecraftServer server, NinthFormEncounter encounter) {
        NinthFormPhase next;
        if (encounter.phase() == NinthFormPhase.PRELUDE) {
            next = NinthFormPhase.FIRST;
        } else if (encounter.phase() == NinthFormPhase.INTERLUDE) {
            if (!encounter.rehearsal()) {
                Optional<StoryCampaignDefinition> campaign = StoryCampaignRegistry.current()
                        .find(encounter.campaignId());
                if (campaign.isEmpty()) {
                    return;
                }
                NinthFormStoryGate.Decision gate = NinthFormStoryGate.forFinalPhase(
                        campaign.get(),
                        StoryService.snapshot(server, encounter.targetId()),
                        encounter);
                if (gate.status() != NinthFormStoryGate.Status.ELIGIBLE) {
                    return;
                }
            }
            next = NinthFormPhase.FINAL;
        } else {
            return;
        }
        NinthFormEntityGateway.ControlResult result = NinthFormGatewayRegistry.current(server)
                .transitionLoaded(encounter.identity(), encounter.entityId(), encounter.phase(), next);
        if (result.status() == NinthFormEntityGateway.Status.APPLIED) {
            NinthFormEncounterData.get(server).advanceActivePhase(
                    encounter.identity(), encounter.entityId(), encounter.phase(), next);
        } else if (result.status() == NinthFormEntityGateway.Status.NOT_FOUND
                || result.status() == NinthFormEntityGateway.Status.NOT_LOADED
                || result.status() == NinthFormEntityGateway.Status.IDENTITY_MISMATCH) {
            suspendEncounter(server, encounter);
        }
    }

    private static void resumeSuspended(
            MinecraftServer server, ServerPlayer target, NinthFormEncounter encounter) {
        ServerLevel level = level(server, encounter.dimensionId());
        if (level == null
                || target.serverLevel() != level
                || !NinthFormArenaPolicy.contains(
                        encounter.arenaX(),
                        encounter.arenaZ(),
                        target.getX(),
                        target.getZ())) {
            return;
        }
        List<NinthFormArenaPolicy.OccupiedArena> others =
                loadedArenas(server, encounter.encounterId());
        NinthFormArenaPolicy.PlanResult plan = NinthFormArenaPolicy.plan(
                encounter.dimensionId(),
                encounter.arenaX(),
                encounter.arenaY(),
                encounter.arenaZ(),
                others,
                level.getChunkSource()::hasChunk);
        if (plan.status() != NinthFormArenaPolicy.Status.READY) {
            return;
        }
        NinthFormEntityGateway gateway = NinthFormGatewayRegistry.current(server);
        NinthFormEntityGateway.ControlResult discarded =
                gateway.discardLoaded(encounter.identity(), encounter.entityId());
        if (discarded.status() != NinthFormEntityGateway.Status.APPLIED
                && discarded.status() != NinthFormEntityGateway.Status.NOT_FOUND
                && discarded.status() != NinthFormEntityGateway.Status.NOT_LOADED) {
            return;
        }
        NinthFormEncounterData.RotationResult rotated = NinthFormEncounterData.get(server)
                .rotateGeneration(encounter.encounterId(), UUID.randomUUID());
        if (rotated.status() == NinthFormEncounterData.RotationStatus.ROTATED) {
            spawnPrepared(server, rotated.encounter());
        }
    }

    private static void suspendEncounter(MinecraftServer server, NinthFormEncounter encounter) {
        NinthFormEntityGateway gateway = NinthFormGatewayRegistry.current(server);
        Optional<NinthFormCombatSnapshot> observed =
                gateway.observeLoaded(encounter.identity(), encounter.entityId());
        observed.ifPresent(snapshot -> NinthFormEncounterData.get(server).storeSnapshot(snapshot));
        NinthFormEncounterData.get(server).suspend(encounter.identity(), encounter.entityId());
        gateway.suspendLoaded(encounter.identity(), encounter.entityId());
    }

    private static boolean campaignEnvelopeAllowsResume(
            MinecraftServer server, NinthFormEncounter encounter) {
        if (encounter.rehearsal()) {
            return true;
        }
        Optional<StoryCampaignDefinition> loaded =
                StoryCampaignRegistry.current().find(encounter.campaignId());
        Optional<StoryWorldData.PlayerSnapshot> snapshot = StoryService.snapshot(
                server, encounter.targetId());
        if (loaded.isEmpty() || snapshot.isEmpty()) {
            return false;
        }
        StoryCampaignDefinition campaign = loaded.get();
        StoryWorldData.PlayerSnapshot state = snapshot.get();
        if (!NinthFormStoryGate.definitionIsExact(campaign)
                || !state.campaignId().equals(encounter.campaignId())
                || state.campaignRevision() != encounter.campaignRevision()
                || !state.definitionFingerprint().equals(encounter.campaignFingerprint())
                || state.progressEpoch() != encounter.progressEpoch()) {
            return false;
        }
        int ordinal = campaign.ordinalOf(state.currentNodeId());
        return switch (encounter.phase()) {
            case PRELUDE, FIRST -> ordinal == NinthFormStoryGate.FIRST_SHAPE_ORDINAL;
            case INTERLUDE -> ordinal == NinthFormStoryGate.FIRST_SHAPE_ORDINAL
                    || ordinal == NinthFormStoryGate.LAST_SHAPE_ORDINAL;
            case FINAL -> ordinal == NinthFormStoryGate.LAST_SHAPE_ORDINAL;
            case BANISHED -> false;
        };
    }

    private static List<NinthFormArenaPolicy.OccupiedArena> loadedArenas(
            MinecraftServer server, UUID excludedEncounterId) {
        NinthFormEntityGateway gateway = NinthFormGatewayRegistry.current(server);
        List<NinthFormArenaPolicy.OccupiedArena> loaded = new ArrayList<>();
        for (NinthFormEncounter encounter : NinthFormEncounterData.get(server).activeEncounters()) {
            if ((excludedEncounterId != null
                            && excludedEncounterId.equals(encounter.encounterId()))) {
                continue;
            }
            if (gateway.observeLoaded(encounter.identity(), encounter.entityId()).isPresent()) {
                loaded.add(new NinthFormArenaPolicy.OccupiedArena(
                        encounter.dimensionId(), encounter.arenaX(), encounter.arenaZ()));
            }
        }
        return List.copyOf(loaded);
    }

    private static int participantCount(ServerLevel level, BlockPos center) {
        double radiusSquared = (double) NinthFormEncounter.ARENA_RADIUS
                * NinthFormEncounter.ARENA_RADIUS;
        long count = level.players().stream()
                .filter(player -> player.isAlive() && !player.isSpectator())
                .filter(player -> player.distanceToSqr(
                        center.getX() + 0.5D,
                        center.getY() + 0.5D,
                        center.getZ() + 0.5D) <= radiusSquared)
                .limit(NinthFormScalingPolicy.MAX_PARTICIPANTS)
                .count();
        return Math.max(1, Math.toIntExact(count));
    }

    private static ServerLevel level(MinecraftServer server, String dimensionId) {
        ResourceLocation parsed = ResourceLocation.tryParse(dimensionId);
        if (parsed == null) {
            return null;
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, parsed);
        return server.getLevel(key);
    }

    private static void awardDefeatToast(MinecraftServer server, UUID targetId) {
        ServerPlayer player = server.getPlayerList().getPlayer(targetId);
        if (player == null) {
            return;
        }
        NinthFormEncounterData.get(server).immutableBarriers().stream()
                .filter(barrier -> barrier.targetId().equals(targetId)
                        && barrier.kind() == NinthFormBarrier.Kind.DEFEATED)
                .findFirst()
                .ifPresent(barrier -> NinthFormRewardService.award(player, barrier));
    }

    private static void queueTarget(MinecraftServer server, UUID targetId) {
        QUEUED_TARGETS.computeIfAbsent(server, ignored -> new LinkedHashSet<>()).add(targetId);
    }

    private static Set<UUID> drainTargets(MinecraftServer server) {
        LinkedHashSet<UUID> queued = QUEUED_TARGETS.remove(server);
        return queued == null ? Set.of() : Set.copyOf(queued);
    }

    static void reconcileProofResults(
            RetryBook<UUID> retries,
            UUID targetId,
            long gameTick,
            List<NinthFormProgressionSync.SyncResult> results) {
        Objects.requireNonNull(retries, "retries");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(results, "results");
        boolean permanentMismatch = results.stream().anyMatch(result ->
                result.status() == NinthFormProgressionSync.SyncStatus.ENVELOPE_MISMATCH);
        boolean retryable = results.stream().anyMatch(result ->
                result.status() == NinthFormProgressionSync.SyncStatus.NOT_READY
                        || result.status() == NinthFormProgressionSync.SyncStatus.REFUSED);
        if (permanentMismatch || !retryable) {
            retries.clear(targetId);
        } else {
            retries.schedule(targetId, gameTick);
        }
    }

    static void reconcileStartResult(
            RetryBook<UUID> retries,
            UUID targetId,
            long gameTick,
            StartStatus status) {
        Objects.requireNonNull(retries, "retries");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(status, "status");
        boolean retryable = status == StartStatus.GATEWAY_UNAVAILABLE
                || status == StartStatus.DATA_UNAVAILABLE
                || status == StartStatus.STORY_NOT_READY
                || status == StartStatus.ARENA_UNAVAILABLE;
        if (retryable) {
            retries.schedule(targetId, gameTick);
        } else {
            retries.clear(targetId);
        }
    }

    private static RetryBook<UUID> retryBook(MinecraftServer server) {
        return PROOF_RETRIES.computeIfAbsent(
                server, ignored -> new RetryBook<>(MAX_RETRY_TARGETS, MAX_RETRY_BACKOFF_TICKS));
    }

    private static RetryBook<UUID> startRetryBook(MinecraftServer server) {
        return START_RETRIES.computeIfAbsent(
                server, ignored -> new RetryBook<>(MAX_RETRY_TARGETS, MAX_RETRY_BACKOFF_TICKS));
    }

    private static void requireServerThread(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (!server.isSameThread()) {
            throw new IllegalStateException("Ninth Form authority requires the server thread");
        }
    }

    /** Pure bounded exponential-backoff table; durable barriers remain the queue authority. */
    static final class RetryBook<K> {
        private final int capacity;
        private final long maxBackoffTicks;
        private final Map<K, RetryState> states = new HashMap<>();

        RetryBook(int capacity, long maxBackoffTicks) {
            if (capacity < 1 || maxBackoffTicks < RECONCILE_INTERVAL_TICKS) {
                throw new IllegalArgumentException("invalid retry bounds");
            }
            this.capacity = capacity;
            this.maxBackoffTicks = maxBackoffTicks;
        }

        boolean schedule(K key, long gameTick) {
            Objects.requireNonNull(key, "key");
            if (gameTick < 0L) {
                throw new IllegalArgumentException("gameTick cannot be negative");
            }
            RetryState previous = states.get(key);
            if (previous == null && states.size() >= capacity) {
                return false;
            }
            int attempt = previous == null ? 1 : Math.min(previous.attempt() + 1, 30);
            long multiplier = 1L << Math.min(attempt - 1, 20);
            long delay = Math.min(maxBackoffTicks, RECONCILE_INTERVAL_TICKS * multiplier);
            long due = gameTick > Long.MAX_VALUE - delay ? Long.MAX_VALUE : gameTick + delay;
            states.put(key, new RetryState(attempt, due));
            return true;
        }

        void clear(K key) {
            states.remove(key);
        }

        Set<K> due(long gameTick) {
            LinkedHashSet<K> result = new LinkedHashSet<>();
            states.forEach((key, state) -> {
                if (state.dueGameTick() <= gameTick) {
                    result.add(key);
                }
            });
            return Set.copyOf(result);
        }

        int size() {
            return states.size();
        }

        Optional<Long> dueGameTick(K key) {
            return Optional.ofNullable(states.get(key)).map(RetryState::dueGameTick);
        }
    }

    private record RetryState(int attempt, long dueGameTick) {}

    public enum StartStatus {
        STARTED,
        SUSPENDED_FOR_RECOVERY,
        ALREADY_ACTIVE,
        NO_SERVER,
        SERVER_STOPPING,
        GATEWAY_UNAVAILABLE,
        DATA_UNAVAILABLE,
        STORY_NOT_READY,
        NOT_EXPECTED,
        ARENA_UNAVAILABLE,
        DATA_REFUSED
    }

    public record StartResult(
            StartStatus status, UUID encounterId, UUID entityId, String detail) {

        public boolean success() {
            return status == StartStatus.STARTED
                    || status == StartStatus.SUSPENDED_FOR_RECOVERY
                    || status == StartStatus.ALREADY_ACTIVE;
        }

        private static StartResult failed(StartStatus status, String detail) {
            return new StartResult(status, null, null, detail);
        }
    }
}
