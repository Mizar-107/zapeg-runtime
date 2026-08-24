package io.github.mizar107.zapegruntime.director;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.scene.CancelReason;
import io.github.mizar107.zapegruntime.scene.OsScareReport;
import io.github.mizar107.zapegruntime.scene.SceneAck;
import io.github.mizar107.zapegruntime.scene.SceneProfile;
import io.github.mizar107.zapegruntime.server.SceneServerManager;
import io.github.mizar107.zapegruntime.story.StoryCampaignDefinition;
import io.github.mizar107.zapegruntime.story.StoryCampaignRegistry;
import io.github.mizar107.zapegruntime.story.StoryFactType;
import io.github.mizar107.zapegruntime.story.StoryNode;
import io.github.mizar107.zapegruntime.story.StoryService;
import io.github.mizar107.zapegruntime.story.StoryTrigger;
import io.github.mizar107.zapegruntime.story.StoryWorldData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Online-only native campaign Director. */
public final class HeraldorDirector {

    public static final int DRIVE_INTERVAL_TICKS = 20;
    public static final int PERIODIC_RECONCILE_TICKS = 100;
    public static final int ACK_GRACE_TICKS = 200;
    private static final int DEFAULT_RETRY_TICKS = 100;
    private static final int DEFAULT_COOLDOWN_TICKS = 600;
    private static final int MAX_QUEUED_TARGETS = HeraldorDirectorData.MAX_TARGETS;
    private static final Map<MinecraftServer, LinkedHashSet<UUID>> reconciliationQueue =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<MinecraftServer, Map<UUID, ServantBarrierReconciler.ReconcileResult>>
            reconciliationStatus = Collections.synchronizedMap(new WeakHashMap<>());

    private HeraldorDirector() {}

    public static void queueReconciliation(MinecraftServer server, UUID targetId) {
        if (server == null || targetId == null) {
            return;
        }
        synchronized (reconciliationQueue) {
            LinkedHashSet<UUID> queue = reconciliationQueue.computeIfAbsent(
                    server, ignored -> new LinkedHashSet<>());
            if (queue.contains(targetId) || queue.size() < MAX_QUEUED_TARGETS) {
                queue.add(targetId);
            } else {
                ZapeGRuntime.LOGGER.error(
                        "Director reconciliation queue is full; target={} remains periodic",
                        targetId);
            }
        }
    }

    public static void onServerStarted(MinecraftServer server) {
        for (ServerPlayer player : orderedOnline(server)) {
            queueReconciliation(server, player.getUUID());
        }
    }

    public static void onServerStopping(MinecraftServer server) {
        synchronized (reconciliationQueue) {
            reconciliationQueue.remove(server);
        }
        synchronized (reconciliationStatus) {
            reconciliationStatus.remove(server);
        }
        ServantBarrierReconciler.clear(server);
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % DRIVE_INTERVAL_TICKS != 0) {
            return;
        }
        Set<UUID> explicitlyDue = drainQueue(server);
        boolean periodic = server.getTickCount() % PERIODIC_RECONCILE_TICKS == 0;
        for (ServerPlayer player : orderedOnline(server)) {
            UUID targetId = player.getUUID();
            if (explicitlyDue.contains(targetId)) {
                rememberReconciliation(server, targetId, ServantBarrierReconciler.reconcile(
                        server, targetId, ServantBarrierReconciler.ScanMode.FULL));
            } else if (periodic) {
                rememberReconciliation(server, targetId, ServantBarrierReconciler.reconcile(
                        server, targetId, ServantBarrierReconciler.ScanMode.CURSOR));
            }
            drivePlayer(server, player);
        }
    }

    /** Called only from SceneServerManager after active identity/profile validation. */
    public static void onAcknowledgement(
            MinecraftServer server,
            DirectorSceneIdentity identity,
            SceneProfile profile,
            SceneAck acknowledgement) {
        if (server == null || identity == null || profile == null || acknowledgement == null) {
            return;
        }
        HeraldorDirectorData data = HeraldorDirectorData.get(server);
        Optional<HeraldorDirectorData.DispatchRecord> current = data.record(identity.targetId());
        if (current.isEmpty()
                || !current.get().identity().equals(identity)
                || current.get().state() != HeraldorDirectorData.DispatchState.AWAITING) {
            return;
        }
        DirectorPresentationPolicy.Proof proof =
                DirectorPresentationPolicy.acknowledgementProof(
                        identity.factType(), profile, acknowledgement);
        long now = gameTime(server);
        if (proof != DirectorPresentationPolicy.Proof.NONE) {
            if (data.markProven(identity, proof, now)) {
                processProof(server, data, identity.targetId(), now);
            }
            return;
        }
        if (acknowledgement.terminal()) {
            data.markFailure(
                    identity,
                    safeAdd(now, retryTicks(identity)),
                    "terminal_" + acknowledgement.name().toLowerCase(Locale.ROOT));
        }
    }

    /** Called only after the accepted visitation status was committed to its ledger. */
    public static void onOsScareStatus(
            MinecraftServer server,
            DirectorSceneIdentity identity,
            SceneProfile profile,
            OsScareReport report) {
        if (server == null || identity == null || profile == null || report == null) {
            return;
        }
        HeraldorDirectorData data = HeraldorDirectorData.get(server);
        Optional<HeraldorDirectorData.DispatchRecord> current = data.record(identity.targetId());
        if (current.isEmpty()
                || !current.get().identity().equals(identity)
                || current.get().state() != HeraldorDirectorData.DispatchState.AWAITING) {
            return;
        }
        DirectorPresentationPolicy.Proof proof = DirectorPresentationPolicy.fallbackProof(
                identity.factType(), profile, report);
        if (proof == DirectorPresentationPolicy.Proof.NONE) {
            return;
        }
        long now = gameTime(server);
        if (data.markProven(identity, proof, now)) {
            processProof(server, data, identity.targetId(), now);
        }
    }

    /** A server-side expiry/cancel is never completion evidence. */
    public static void onCancelled(
            MinecraftServer server,
            DirectorSceneIdentity identity,
            CancelReason reason) {
        if (server == null || identity == null || reason == null || reason == CancelReason.SERVER_STOP) {
            return;
        }
        HeraldorDirectorData data = HeraldorDirectorData.get(server);
        Optional<HeraldorDirectorData.DispatchRecord> current = data.record(identity.targetId());
        if (current.isEmpty()
                || !current.get().identity().equals(identity)
                || current.get().state() != HeraldorDirectorData.DispatchState.AWAITING) {
            return;
        }
        long now = gameTime(server);
        data.markFailure(
                identity,
                safeAdd(now, retryTicks(identity)),
                "cancel_" + reason.name().toLowerCase(Locale.ROOT));
    }

    public static String statusFor(MinecraftServer server, UUID targetId) {
        HeraldorDirectorData data = HeraldorDirectorData.get(server);
        HeraldorDirectorData.SchemaStatus schema = data.schemaStatus();
        Optional<HeraldorDirectorData.DispatchRecord> record = data.record(targetId);
        String base = "target_uuid=" + targetId
                + " schema=" + schema.loadedVersion() + '/' + schema.currentVersion()
                + " health=" + schema.health().name().toLowerCase(Locale.ROOT)
                + " writable=" + schema.writable()
                + " registry_generation=" + DirectorSceneRegistry.current().generation()
                + reconciliationStatus(server, targetId);
        if (record.isEmpty()) {
            return base + " state=idle";
        }
        HeraldorDirectorData.DispatchRecord value = record.get();
        return base
                + " state=" + value.state().name().toLowerCase(Locale.ROOT)
                + " node=" + value.nodeId()
                + " fact=" + value.factType().serializedName() + ':' + value.subject()
                + " event=" + value.eventId()
                + " attempt=" + value.attempt()
                + " proof=" + value.proof().name().toLowerCase(Locale.ROOT)
                + " retry_after=" + value.retryAfterGameTime()
                + " outcome=" + value.lastOutcome();
    }

    public static String diagnoseFor(MinecraftServer server, UUID targetId) {
        String status = statusFor(server, targetId);
        Optional<StoryWorldData.PlayerSnapshot> story = StoryService.snapshot(server, targetId);
        String storyState = story.map(value -> " story_node=" + value.currentNodeId()
                        + " story_epoch=" + value.progressEpoch())
                .orElse(" story_node=uninitialized");
        Optional<HeraldorDirectorData.DispatchRecord> record =
                HeraldorDirectorData.get(server).record(targetId);
        String active = record.map(value -> " scene_active="
                        + SceneServerManager.isDirectorSceneActive(targetId, value.eventId()))
                .orElse(" scene_active=false");
        return status + storyState + active;
    }

    private static void drivePlayer(MinecraftServer server, ServerPlayer target) {
        HeraldorDirectorData data = HeraldorDirectorData.get(server);
        long now = gameTime(server);
        Optional<HeraldorDirectorData.DispatchRecord> retained = data.record(target.getUUID());
        if (retained.isPresent()
                && retained.get().state() == HeraldorDirectorData.DispatchState.PROVEN) {
            processProof(server, data, target.getUUID(), now);
            retained = data.record(target.getUUID());
            if (retained.isPresent()
                    && retained.get().state() == HeraldorDirectorData.DispatchState.PROVEN) {
                return;
            }
        }

        Optional<StoryCampaignDefinition> loaded = StoryCampaignRegistry.current()
                .find(StoryCampaignRegistry.HERALDOR_CAMPAIGN);
        Optional<StoryWorldData.PlayerSnapshot> snapshot = StoryService.snapshot(
                server, target.getUUID());
        if (loaded.isEmpty() || snapshot.isEmpty()) {
            return;
        }
        StoryCampaignDefinition campaign = loaded.get();
        StoryNode node = campaign.node(snapshot.get().currentNodeId());
        if (node == null
                || node.terminal()
                || (node.advanceOn().type() != StoryFactType.SCENE_COMPLETED
                        && node.advanceOn().type() != StoryFactType.SCENE_PRESENTED)) {
            return;
        }
        Optional<DirectorSceneBinding> binding = DirectorSceneRegistry.current()
                .find(campaign.id())
                .flatMap(catalog -> catalog.find(node.advanceOn()));
        if (binding.isEmpty() || !bindingCanProve(binding.get())) {
            return;
        }

        Optional<HeraldorDirectorData.DispatchRecord> current = data.record(target.getUUID());
        boolean active = current.isPresent()
                && SceneServerManager.isDirectorSceneActive(
                        target.getUUID(), current.get().eventId());
        HeraldorDirectorData.PlanResult plan = data.plan(
                campaign, snapshot.get(), node, binding.get(), now, active);
        if (plan.status() == HeraldorDirectorData.PlanStatus.PROOF_READY) {
            processProof(server, data, target.getUUID(), now);
            return;
        }
        if (plan.status() != HeraldorDirectorData.PlanStatus.DISPATCH
                || plan.record() == null) {
            return;
        }
        dispatch(server, target, data, plan.record(), binding.get(), now);
    }

    private static void dispatch(
            MinecraftServer server,
            ServerPlayer target,
            HeraldorDirectorData data,
            HeraldorDirectorData.DispatchRecord record,
            DirectorSceneBinding binding,
            long now) {
        StoryWorldData.ReceiptStatus receipt = StoryWorldData.get(server).receiptStatus(
                record.targetId(),
                record.eventId(),
                record.campaignId(),
                record.campaignRevision(),
                record.factType(),
                record.subject());
        if (receipt == StoryWorldData.ReceiptStatus.EXACT) {
            data.markCooldown(
                    record.identity(),
                    safeAdd(now, binding.cooldownTicks()),
                    "receipt_already_applied");
            return;
        }
        if (receipt == StoryWorldData.ReceiptStatus.CONFLICT
                || receipt == StoryWorldData.ReceiptStatus.UNVERIFIABLE) {
            data.markBlocked(record.identity(), "receipt_identity_conflict");
            return;
        }
        if (receipt == StoryWorldData.ReceiptStatus.DATA_UNAVAILABLE) {
            return;
        }

        SceneServerManager.DispatchResult result = SceneServerManager.dispatchDirector(
                target,
                record.identity(),
                binding.profile(),
                binding.ttlTicks(),
                binding.stage());
        if (result.success()) {
            long proofDeadline = safeAdd(
                    now,
                    binding.profile().occupancyTicks(binding.ttlTicks()) + ACK_GRACE_TICKS);
            if (!data.markAwaiting(record.identity(), proofDeadline)) {
                SceneServerManager.cancelForPlayer(target.getUUID(), CancelReason.REPLACED);
                data.markFailure(
                        record.identity(),
                        safeAdd(now, binding.retryTicks()),
                        "state_commit_failed");
            }
            return;
        }

        String outcome = normalizeOutcome(result.message());
        long retryAfter = safeAdd(now, binding.retryTicks());
        if ("event_id_is_already_consumed".equals(outcome)
                || "event_replay_identity_is_rejected".equals(outcome)
                || "event_replay_identity_could_not_be_committed".equals(outcome)) {
            data.markFailure(record.identity(), retryAfter, outcome);
        } else {
            data.markBackoff(record.identity(), retryAfter, outcome);
        }
    }

    private static void processProof(
            MinecraftServer server,
            HeraldorDirectorData data,
            UUID targetId,
            long now) {
        Optional<HeraldorDirectorData.DispatchRecord> retained = data.record(targetId);
        if (retained.isEmpty()
                || retained.get().state() != HeraldorDirectorData.DispatchState.PROVEN) {
            return;
        }
        HeraldorDirectorData.DispatchRecord record = retained.get();
        StoryWorldData storyData = StoryWorldData.get(server);
        StoryWorldData.ReceiptStatus receipt = storyData.receiptStatus(
                record.targetId(),
                record.eventId(),
                record.campaignId(),
                record.campaignRevision(),
                record.factType(),
                record.subject());
        if (receipt == StoryWorldData.ReceiptStatus.EXACT) {
            int cooldown = bindingFor(record)
                    .map(DirectorSceneBinding::cooldownTicks)
                    .orElse(DEFAULT_COOLDOWN_TICKS);
            data.markCooldown(
                    record.identity(), safeAdd(now, cooldown), "story_receipt_recovered");
            return;
        }
        if (receipt == StoryWorldData.ReceiptStatus.CONFLICT
                || receipt == StoryWorldData.ReceiptStatus.UNVERIFIABLE) {
            data.markBlocked(record.identity(), "story_fact_identity_conflict");
            return;
        }
        if (receipt == StoryWorldData.ReceiptStatus.DATA_UNAVAILABLE) {
            return;
        }
        Optional<DirectorSceneBinding> binding = bindingFor(record);
        if (binding.isEmpty()) {
            data.markBlocked(record.identity(), "binding_definition_mismatch");
            return;
        }
        Optional<StoryWorldData.PlayerSnapshot> before = StoryService.snapshot(server, targetId);
        boolean exactEnvelope = before.isPresent()
                && before.get().campaignId().equals(record.campaignId())
                && before.get().campaignRevision() == record.campaignRevision()
                && before.get().definitionFingerprint().equals(record.campaignFingerprint())
                && before.get().progressEpoch() == record.progressEpoch()
                && before.get().currentNodeId().equals(record.nodeId());
        if (!exactEnvelope) {
            data.markCooldown(
                    record.identity(),
                    safeAdd(now, binding.get().cooldownTicks()),
                    "proof_superseded");
            return;
        }
        StoryService.SubmissionResult result = StoryService.submitIfExpected(
                server,
                record.eventId(),
                record.targetId(),
                record.campaignId(),
                record.factType(),
                record.subject());
        switch (result.status()) {
            case APPLIED, ALREADY_PROCESSED -> data.markCooldown(
                    record.identity(),
                    safeAdd(now, binding.get().cooldownTicks()),
                    result.status() == StoryService.SubmissionStatus.APPLIED
                            ? "story_applied"
                            : "story_receipt_recovered");
            case NOT_EXPECTED, STATE_NOT_READY -> {
                Optional<StoryWorldData.PlayerSnapshot> current =
                        StoryService.snapshot(server, targetId);
                boolean superseded = current.isPresent()
                        && (current.get().progressEpoch() != record.progressEpoch()
                                || !current.get().currentNodeId().equals(record.nodeId()));
                if (superseded) {
                    data.markCooldown(
                            record.identity(),
                            safeAdd(now, binding.get().cooldownTicks()),
                            "proof_superseded");
                }
            }
            case FACT_ID_CONFLICT -> data.markBlocked(
                    record.identity(), "story_fact_identity_conflict");
            default -> {
                // Durable PROVEN state remains retryable without redispatch.
            }
        }
    }

    private static Optional<DirectorSceneBinding> bindingFor(
            HeraldorDirectorData.DispatchRecord record) {
        StoryTrigger trigger = new StoryTrigger(record.factType(), record.subject());
        return DirectorSceneRegistry.current()
                .find(record.campaignId())
                .flatMap(catalog -> catalog.find(trigger))
                .filter(binding -> binding.fingerprint().equals(record.bindingFingerprint())
                        && binding.presentationVariant() == record.presentationVariant());
    }

    private static int retryTicks(DirectorSceneIdentity identity) {
        return DirectorSceneRegistry.current()
                .find(identity.campaignId())
                .flatMap(catalog -> catalog.find(
                        new StoryTrigger(identity.factType(), identity.subject())))
                .filter(binding -> binding.fingerprint().equals(identity.bindingFingerprint()))
                .map(DirectorSceneBinding::retryTicks)
                .orElse(DEFAULT_RETRY_TICKS);
    }

    private static boolean bindingCanProve(DirectorSceneBinding binding) {
        return binding.factType() == StoryFactType.SCENE_COMPLETED
                || DirectorPresentationPolicy.visibleMeansPresented(binding.profile())
                || binding.profile() == SceneProfile.VISITATION_01;
    }

    private static Set<UUID> drainQueue(MinecraftServer server) {
        synchronized (reconciliationQueue) {
            LinkedHashSet<UUID> queued = reconciliationQueue.get(server);
            if (queued == null || queued.isEmpty()) {
                return Set.of();
            }
            Set<UUID> result = Set.copyOf(queued);
            queued.clear();
            return result;
        }
    }

    private static void rememberReconciliation(
            MinecraftServer server,
            UUID targetId,
            ServantBarrierReconciler.ReconcileResult result) {
        synchronized (reconciliationStatus) {
            Map<UUID, ServantBarrierReconciler.ReconcileResult> statuses =
                    reconciliationStatus.computeIfAbsent(server, ignored -> new java.util.HashMap<>());
            if (statuses.containsKey(targetId) || statuses.size() < MAX_QUEUED_TARGETS) {
                statuses.put(targetId, result);
            }
        }
        if (result.status() == ServantBarrierReconciler.ReconcileStatus.SCAN_LIMIT) {
            ZapeGRuntime.LOGGER.debug(
                    "Director Servant reconciliation scan_limit target={} budget={}",
                    targetId,
                    ServantBarrierReconciler.PERIODIC_SCAN_BUDGET);
        }
    }

    private static String reconciliationStatus(MinecraftServer server, UUID targetId) {
        synchronized (reconciliationStatus) {
            ServantBarrierReconciler.ReconcileResult result =
                    reconciliationStatus.getOrDefault(server, Map.of()).get(targetId);
            return result == null
                    ? " servant_reconcile=untried"
                    : " servant_reconcile="
                            + result.status().name().toLowerCase(Locale.ROOT)
                            + " servant_advances=" + result.advances();
        }
    }

    private static List<ServerPlayer> orderedOnline(MinecraftServer server) {
        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        players.sort(Comparator.comparing(player -> player.getUUID().toString()));
        return List.copyOf(players);
    }

    private static long gameTime(MinecraftServer server) {
        return Math.max(0L, server.overworld().getGameTime());
    }

    private static long safeAdd(long value, int delta) {
        return value > Long.MAX_VALUE - delta ? Long.MAX_VALUE : value + delta;
    }

    private static String normalizeOutcome(String message) {
        if (message == null || message.isBlank()) {
            return "dispatch_failed";
        }
        String normalized = message.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isEmpty()) {
            return "dispatch_failed";
        }
        return normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
    }

    static void resetForTests() {
        synchronized (reconciliationQueue) {
            reconciliationQueue.clear();
        }
        synchronized (reconciliationStatus) {
            reconciliationStatus.clear();
        }
        ServantBarrierReconciler.resetForTests();
    }
}
