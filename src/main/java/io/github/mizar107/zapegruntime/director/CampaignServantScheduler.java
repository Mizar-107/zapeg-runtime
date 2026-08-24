package io.github.mizar107.zapegruntime.director;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.servant.ServantArchetype;
import io.github.mizar107.zapegruntime.servant.ServantEncounter;
import io.github.mizar107.zapegruntime.servant.ServantEncounterData;
import io.github.mizar107.zapegruntime.servant.ServantEncounterManager;
import io.github.mizar107.zapegruntime.servant.ServantProgressionSync;
import io.github.mizar107.zapegruntime.story.StoryCampaignDefinition;
import io.github.mizar107.zapegruntime.story.StoryCampaignRegistry;
import io.github.mizar107.zapegruntime.story.StoryFactType;
import io.github.mizar107.zapegruntime.story.StoryNode;
import io.github.mizar107.zapegruntime.story.StoryTrigger;
import io.github.mizar107.zapegruntime.story.StoryWorldData;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Bounded automatic producer for the campaign's three Servant barriers. */
public final class CampaignServantScheduler {

    public static final int INITIAL_RECONCILE_GRACE_TICKS = 100;
    public static final int BASE_RETRY_TICKS = 100;
    public static final int MAX_RETRY_TICKS = 1_200;
    public static final int MAX_TRACKED_TARGETS = HeraldorDirectorData.MAX_TARGETS;

    private static final Map<ResourceLocation, ServantArchetype> ARCHETYPE_BY_SUBJECT =
            buildSubjectIndex();
    private static final Map<MinecraftServer, Map<UUID, RetryState>> retries =
            Collections.synchronizedMap(new WeakHashMap<>());

    private CampaignServantScheduler() {}

    public static ScheduleResult drive(
            MinecraftServer server,
            ServerPlayer target,
            StoryCampaignDefinition campaign,
            StoryWorldData.PlayerSnapshot story,
            StoryNode node) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(target, "target");
        if (!server.isSameThread()) {
            throw new IllegalStateException("campaign Servant scheduling requires the server thread");
        }
        Optional<Plan> planned = plan(campaign, story, node);
        if (planned.isEmpty() || target.getServer() != server) {
            clearTarget(server, target.getUUID());
            return new ScheduleResult(ScheduleStatus.NOT_EXPECTED, null, 0L);
        }
        Plan plan = planned.get();
        if (!target.isAlive() || target.isSpectator()) {
            return new ScheduleResult(ScheduleStatus.TARGET_INELIGIBLE, plan.encounterId(), 0L);
        }

        ServantEncounterData data = ServantEncounterData.get(server);
        if (!data.supportsCurrentSchema()) {
            return new ScheduleResult(ScheduleStatus.DATA_UNAVAILABLE, plan.encounterId(), 0L);
        }
        Optional<ServantEncounter> active = data.activeFor(target.getUUID());
        if (active.isPresent()) {
            clearTarget(server, target.getUUID());
            return new ScheduleResult(
                    exactAutomaticEncounter(active.get(), plan)
                            ? ScheduleStatus.ACTIVE_EXACT
                            : ScheduleStatus.ACTIVE_OTHER,
                    plan.encounterId(),
                    0L);
        }
        Optional<ServantEncounterData.LiveVictory> victory =
                data.liveVictory(plan.encounterId());
        if (victory.isPresent() && exactAutomaticVictory(victory.get(), plan)) {
            clearTarget(server, target.getUUID());
            HeraldorDirector.queueReconciliation(server, target.getUUID());
            return new ScheduleResult(
                    ScheduleStatus.VICTORY_PENDING_RECONCILIATION,
                    plan.encounterId(),
                    0L);
        }
        if (victory.isPresent()) {
            RetryState blocked = new RetryState(
                    plan.encounterId(), 1, Long.MAX_VALUE, true);
            putRetry(server, target.getUUID(), blocked);
            ZapeGRuntime.LOGGER.error(
                    "Campaign Servant victory identity conflict target_uuid={} encounter={} archetype={}",
                    target.getUUID(),
                    plan.encounterId(),
                    plan.archetype().id());
            return new ScheduleResult(
                    ScheduleStatus.IDENTITY_BLOCKED, plan.encounterId(), Long.MAX_VALUE);
        }

        long now = Math.max(0L, server.overworld().getGameTime());
        RetryState retry = retryState(server, plan, now);
        if (retry == null) {
            return new ScheduleResult(
                    ScheduleStatus.RETRY_CAPACITY_EXHAUSTED, plan.encounterId(), 0L);
        }
        if (retry.blocked()) {
            return new ScheduleResult(
                    ScheduleStatus.IDENTITY_BLOCKED, plan.encounterId(), Long.MAX_VALUE);
        }
        if (now < retry.nextAttemptGameTime()) {
            return new ScheduleResult(
                    retry.failures() == 0
                            ? ScheduleStatus.RECONCILIATION_GRACE
                            : ScheduleStatus.RETRY_BACKOFF,
                    plan.encounterId(),
                    retry.nextAttemptGameTime());
        }

        ServantEncounterManager.StartResult started = ServantEncounterManager.awaken(
                target, plan.encounterId(), plan.archetype(), false);
        return classifyAttempt(server, target.getUUID(), plan, retry, now, started);
    }

    static Optional<Plan> plan(
            StoryCampaignDefinition campaign,
            StoryWorldData.PlayerSnapshot story,
            StoryNode node) {
        Objects.requireNonNull(campaign, "campaign");
        Objects.requireNonNull(story, "story");
        Objects.requireNonNull(node, "node");
        if (!campaign.id().equals(StoryCampaignRegistry.HERALDOR_CAMPAIGN)
                || !story.campaignId().equals(campaign.id())
                || story.campaignRevision() != campaign.revision()
                || !story.definitionFingerprint().equals(campaign.fingerprint())
                || !story.currentNodeId().equals(node.id())
                || !story.completedNodes().equals(campaign.completedPrefixFor(node.id()))
                || node.terminal()
                || node.advanceOn() == null
                || node.advanceOn().type() != StoryFactType.SERVANT_DEFEATED) {
            return Optional.empty();
        }
        ServantArchetype archetype = ARCHETYPE_BY_SUBJECT.get(node.advanceOn().subject());
        if (archetype == null) {
            return Optional.empty();
        }
        UUID encounterId = CampaignServantIdentity.derive(
                story.playerId(),
                campaign.id(),
                campaign.revision(),
                campaign.fingerprint(),
                story.progressEpoch(),
                node.id(),
                node.advanceOn().type(),
                node.advanceOn().subject(),
                archetype);
        return Optional.of(new Plan(
                story.playerId(),
                story.progressEpoch(),
                node.id(),
                node.advanceOn(),
                archetype,
                encounterId));
    }

    public static Set<StoryTrigger> automaticTriggers() {
        LinkedHashSet<StoryTrigger> triggers = new LinkedHashSet<>();
        for (ServantArchetype archetype : ServantArchetype.values()) {
            triggers.add(new StoryTrigger(
                    StoryFactType.SERVANT_DEFEATED,
                    ServantProgressionSync.storySubject(archetype)));
        }
        return Set.copyOf(triggers);
    }

    static int retryDelayTicks(int failures) {
        if (failures < 1) {
            throw new IllegalArgumentException("retry failure count must be positive");
        }
        int shifts = Math.min(4, failures - 1);
        return Math.min(MAX_RETRY_TICKS, BASE_RETRY_TICKS << shifts);
    }

    static boolean exactAutomaticEncounter(ServantEncounter active, Plan plan) {
        Objects.requireNonNull(active, "active");
        Objects.requireNonNull(plan, "plan");
        return !active.rehearsal()
                && active.targetId().equals(plan.targetId())
                && active.encounterId().equals(plan.encounterId())
                && active.archetype() == plan.archetype();
    }

    static boolean exactAutomaticVictory(
            ServantEncounterData.LiveVictory victory, Plan plan) {
        Objects.requireNonNull(victory, "victory");
        Objects.requireNonNull(plan, "plan");
        return victory.encounterId().equals(plan.encounterId())
                && victory.targetId().equals(plan.targetId())
                && victory.archetype() == plan.archetype();
    }

    public static void clearTarget(MinecraftServer server, UUID targetId) {
        if (server == null || targetId == null) {
            return;
        }
        synchronized (retries) {
            Map<UUID, RetryState> serverRetries = retries.get(server);
            if (serverRetries == null) {
                return;
            }
            serverRetries.remove(targetId);
            if (serverRetries.isEmpty()) {
                retries.remove(server);
            }
        }
    }

    public static void clear(MinecraftServer server) {
        synchronized (retries) {
            retries.remove(server);
        }
    }

    static void resetForTests() {
        synchronized (retries) {
            retries.clear();
        }
    }

    private static ScheduleResult classifyAttempt(
            MinecraftServer server,
            UUID targetId,
            Plan plan,
            RetryState previous,
            long now,
            ServantEncounterManager.StartResult result) {
        return switch (result.status()) {
            case STARTED, ALREADY_ACTIVE -> {
                clearTarget(server, targetId);
                yield new ScheduleResult(
                        result.status() == ServantEncounterManager.StartStatus.STARTED
                                ? ScheduleStatus.STARTED
                                : ScheduleStatus.ACTIVE_EXACT,
                        plan.encounterId(),
                        0L);
            }
            case REPLAYED_LIVE_VICTORY -> {
                clearTarget(server, targetId);
                HeraldorDirector.queueReconciliation(server, targetId);
                yield new ScheduleResult(
                        ScheduleStatus.VICTORY_PENDING_RECONCILIATION,
                        plan.encounterId(),
                        0L);
            }
            case TARGET_BUSY -> {
                clearTarget(server, targetId);
                yield new ScheduleResult(ScheduleStatus.ACTIVE_OTHER, plan.encounterId(), 0L);
            }
            case NO_SAFE_SPAWN, SPAWN_FAILED, CAPACITY_EXHAUSTED -> {
                RetryState next = failedRetry(plan, previous, now, false);
                putRetry(server, targetId, next);
                ZapeGRuntime.LOGGER.debug(
                        "Campaign Servant retry target_uuid={} encounter={} archetype={} result={} retry_after={}",
                        targetId,
                        plan.encounterId(),
                        plan.archetype().id(),
                        result.status(),
                        next.nextAttemptGameTime());
                yield new ScheduleResult(
                        ScheduleStatus.RETRY_BACKOFF,
                        plan.encounterId(),
                        next.nextAttemptGameTime());
            }
            case EVENT_ID_CONFLICT, INVALID_REQUEST, NO_SERVER, UNSUPPORTED_SCHEMA -> {
                RetryState blocked = failedRetry(plan, previous, now, true);
                putRetry(server, targetId, blocked);
                ZapeGRuntime.LOGGER.error(
                        "Campaign Servant blocked target_uuid={} encounter={} archetype={} result={}",
                        targetId,
                        plan.encounterId(),
                        plan.archetype().id(),
                        result.status());
                yield new ScheduleResult(
                        ScheduleStatus.IDENTITY_BLOCKED, plan.encounterId(), Long.MAX_VALUE);
            }
        };
    }

    private static RetryState retryState(MinecraftServer server, Plan plan, long now) {
        synchronized (retries) {
            Map<UUID, RetryState> serverRetries = retries.computeIfAbsent(
                    server, ignored -> new HashMap<>());
            RetryState current = serverRetries.get(plan.targetId());
            if (current != null && current.encounterId().equals(plan.encounterId())) {
                return current;
            }
            if (current == null && serverRetries.size() >= MAX_TRACKED_TARGETS) {
                return null;
            }
            RetryState initial = new RetryState(
                    plan.encounterId(),
                    0,
                    safeAdd(now, INITIAL_RECONCILE_GRACE_TICKS),
                    false);
            serverRetries.put(plan.targetId(), initial);
            return initial;
        }
    }

    private static RetryState failedRetry(
            Plan plan, RetryState previous, long now, boolean blocked) {
        int failures = Math.min(31, previous.failures() + 1);
        return new RetryState(
                plan.encounterId(),
                failures,
                blocked ? Long.MAX_VALUE : safeAdd(now, retryDelayTicks(failures)),
                blocked);
    }

    private static void putRetry(MinecraftServer server, UUID targetId, RetryState retry) {
        synchronized (retries) {
            Map<UUID, RetryState> serverRetries = retries.computeIfAbsent(
                    server, ignored -> new HashMap<>());
            if (serverRetries.containsKey(targetId)
                    || serverRetries.size() < MAX_TRACKED_TARGETS) {
                serverRetries.put(targetId, retry);
            }
        }
    }

    private static long safeAdd(long now, int ticks) {
        return now > Long.MAX_VALUE - ticks ? Long.MAX_VALUE : now + ticks;
    }

    private static Map<ResourceLocation, ServantArchetype> buildSubjectIndex() {
        Map<ResourceLocation, ServantArchetype> index = new HashMap<>();
        for (ServantArchetype archetype : EnumSet.allOf(ServantArchetype.class)) {
            ResourceLocation subject = ServantProgressionSync.storySubject(archetype);
            if (index.put(subject, archetype) != null) {
                throw new IllegalStateException("duplicate campaign Servant story subject");
            }
        }
        return Map.copyOf(index);
    }

    public enum ScheduleStatus {
        NOT_EXPECTED,
        TARGET_INELIGIBLE,
        DATA_UNAVAILABLE,
        ACTIVE_EXACT,
        ACTIVE_OTHER,
        VICTORY_PENDING_RECONCILIATION,
        RECONCILIATION_GRACE,
        RETRY_BACKOFF,
        RETRY_CAPACITY_EXHAUSTED,
        IDENTITY_BLOCKED,
        STARTED
    }

    public record ScheduleResult(
            ScheduleStatus status, UUID encounterId, long retryAfterGameTime) {}

    record Plan(
            UUID targetId,
            long progressEpoch,
            String nodeId,
            StoryTrigger trigger,
            ServantArchetype archetype,
            UUID encounterId) {}

    private record RetryState(
            UUID encounterId, int failures, long nextAttemptGameTime, boolean blocked) {}
}
