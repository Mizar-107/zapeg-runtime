package io.github.mizar107.zapegruntime.director;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.servant.ServantArchetype;
import io.github.mizar107.zapegruntime.servant.ServantEncounterData;
import io.github.mizar107.zapegruntime.servant.ServantProgressionSync;
import io.github.mizar107.zapegruntime.story.StoryCampaignDefinition;
import io.github.mizar107.zapegruntime.story.StoryCampaignRegistry;
import io.github.mizar107.zapegruntime.story.StoryFactType;
import io.github.mizar107.zapegruntime.story.StoryNode;
import io.github.mizar107.zapegruntime.story.StoryService;
import io.github.mizar107.zapegruntime.story.StoryWorldData;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.WeakHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Campaign-order consumer for durable Servant victory barriers. */
public final class ServantBarrierReconciler {

    public static final int MAX_CHAINED_ADVANCES = 4;
    public static final int PERIODIC_SCAN_BUDGET = 64;
    private static final Map<MinecraftServer, Map<CursorKey, Integer>> cursors =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ServantBarrierReconciler() {}

    public static ReconcileResult reconcile(MinecraftServer server, UUID targetId) {
        return reconcile(server, targetId, ScanMode.FULL);
    }

    public static ReconcileResult reconcile(
            MinecraftServer server, UUID targetId, ScanMode scanMode) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(scanMode, "scanMode");
        Optional<StoryCampaignDefinition> loaded = StoryCampaignRegistry.current()
                .find(StoryCampaignRegistry.HERALDOR_CAMPAIGN);
        if (loaded.isEmpty()) {
            return new ReconcileResult(ReconcileStatus.CAMPAIGN_UNAVAILABLE, 0);
        }
        StoryCampaignDefinition campaign = loaded.get();
        StoryWorldData storyData = StoryWorldData.get(server);
        ServantEncounterData servantData = ServantEncounterData.get(server);
        if (!storyData.schemaStatus().writable() || !servantData.supportsCurrentSchema()) {
            return new ReconcileResult(ReconcileStatus.DATA_UNAVAILABLE, 0);
        }

        int advances = 0;
        while (advances < MAX_CHAINED_ADVANCES) {
            Optional<StoryWorldData.PlayerSnapshot> snapshot = storyData.snapshot(targetId);
            if (snapshot.isEmpty()) {
                return new ReconcileResult(ReconcileStatus.NO_STORY_STATE, advances);
            }
            StoryNode current = campaign.node(snapshot.get().currentNodeId());
            Optional<ServantArchetype> expected = expectedArchetype(current);
            if (expected.isEmpty()) {
                return new ReconcileResult(
                        advances == 0
                                ? ReconcileStatus.NOT_A_SERVANT_NODE
                                : ReconcileStatus.ADVANCED,
                        advances);
            }
            List<ServantEncounterData.LiveVictory> barriers = servantData.liveVictories();
            CursorKey cursorKey = new CursorKey(targetId, expected.get());
            int startIndex = scanMode == ScanMode.FULL ? 0 : cursor(server, cursorKey);
            int budget = scanMode == ScanMode.FULL
                    ? Math.max(1, barriers.size())
                    : PERIODIC_SCAN_BUDGET;
            Selection selected = selectUnprocessed(
                    storyData,
                    campaign,
                    targetId,
                    expected.get(),
                    barriers,
                    startIndex,
                    budget);
            if (scanMode == ScanMode.CURSOR) {
                setCursor(server, cursorKey, selected.nextIndex());
            }
            if (selected.status() != SelectionStatus.SELECTED) {
                ReconcileStatus terminal = switch (selected.status()) {
                    case NONE -> advances == 0
                            ? ReconcileStatus.NO_MATCHING_BARRIER
                            : ReconcileStatus.ADVANCED;
                    case SCAN_LIMIT -> ReconcileStatus.SCAN_LIMIT;
                    case FACT_CONFLICT -> ReconcileStatus.FACT_CONFLICT;
                    case DATA_UNAVAILABLE -> ReconcileStatus.DATA_UNAVAILABLE;
                    case SELECTED -> throw new IllegalStateException("selected barrier missing");
                };
                return new ReconcileResult(terminal, advances);
            }

            ServantEncounterData.LiveVictory barrier = selected.barrier().orElseThrow();
            ServantProgressionSync.syncLegacyBarrier(server, barrier);
            StoryService.SubmissionResult submission =
                    ServantProgressionSync.submitStoryBarrier(server, barrier);
            if (submission.status() == StoryService.SubmissionStatus.APPLIED) {
                advances++;
                continue;
            }
            if (submission.status() == StoryService.SubmissionStatus.FACT_ID_CONFLICT) {
                ZapeGRuntime.LOGGER.error(
                        "Director refused conflicting Servant barrier encounter={} target={}",
                        barrier.encounterId(),
                        targetId);
                return new ReconcileResult(ReconcileStatus.FACT_CONFLICT, advances);
            }
            return new ReconcileResult(ReconcileStatus.DEFERRED, advances);
        }
        return new ReconcileResult(ReconcileStatus.CHAIN_LIMIT, advances);
    }

    static Selection selectUnprocessed(
            StoryWorldData storyData,
            StoryCampaignDefinition campaign,
            UUID targetId,
            ServantArchetype expected,
            List<ServantEncounterData.LiveVictory> barriers,
            int startIndex,
            int scanBudget) {
        Objects.requireNonNull(barriers, "barriers");
        if (startIndex < 0 || scanBudget < 1) {
            throw new IllegalArgumentException("invalid Servant scan cursor or budget");
        }
        if (barriers.isEmpty()) {
            return new Selection(SelectionStatus.NONE, Optional.empty(), 0, 0);
        }
        int size = barriers.size();
        int index = startIndex % size;
        int limit = Math.min(size, scanBudget);
        ResourceLocation subject = ServantProgressionSync.storySubject(expected);
        for (int scanned = 0; scanned < limit; scanned++) {
            ServantEncounterData.LiveVictory barrier = barriers.get(index);
            index = (index + 1) % size;
            if (!barrier.targetId().equals(targetId) || barrier.archetype() != expected) {
                continue;
            }
            StoryWorldData.ReceiptStatus receipt = storyData.receiptStatus(
                    targetId,
                    barrier.encounterId(),
                    campaign.id(),
                    campaign.revision(),
                    StoryFactType.SERVANT_DEFEATED,
                    subject);
            if (receipt == StoryWorldData.ReceiptStatus.ABSENT) {
                return new Selection(
                        SelectionStatus.SELECTED,
                        Optional.of(barrier),
                        index,
                        scanned + 1);
            }
            if (receipt == StoryWorldData.ReceiptStatus.CONFLICT
                    || receipt == StoryWorldData.ReceiptStatus.UNVERIFIABLE) {
                return new Selection(
                        SelectionStatus.FACT_CONFLICT, Optional.empty(), index, scanned + 1);
            }
            if (receipt == StoryWorldData.ReceiptStatus.DATA_UNAVAILABLE) {
                return new Selection(
                        SelectionStatus.DATA_UNAVAILABLE, Optional.empty(), index, scanned + 1);
            }
        }
        return new Selection(
                limit < size ? SelectionStatus.SCAN_LIMIT : SelectionStatus.NONE,
                Optional.empty(),
                index,
                limit);
    }

    static Optional<ServantArchetype> expectedArchetype(StoryNode current) {
        if (current == null
                || current.terminal()
                || current.advanceOn().type() != StoryFactType.SERVANT_DEFEATED) {
            return Optional.empty();
        }
        for (ServantArchetype archetype : ServantArchetype.values()) {
            if (ServantProgressionSync.storySubject(archetype)
                    .equals(current.advanceOn().subject())) {
                return Optional.of(archetype);
            }
        }
        return Optional.empty();
    }

    public enum ReconcileStatus {
        ADVANCED,
        CHAIN_LIMIT,
        NOT_A_SERVANT_NODE,
        NO_MATCHING_BARRIER,
        NO_STORY_STATE,
        CAMPAIGN_UNAVAILABLE,
        DATA_UNAVAILABLE,
        FACT_CONFLICT,
        SCAN_LIMIT,
        DEFERRED
    }

    public enum ScanMode {
        FULL,
        CURSOR
    }

    enum SelectionStatus {
        SELECTED,
        NONE,
        SCAN_LIMIT,
        FACT_CONFLICT,
        DATA_UNAVAILABLE
    }

    record Selection(
            SelectionStatus status,
            Optional<ServantEncounterData.LiveVictory> barrier,
            int nextIndex,
            int scanned) {}

    public record ReconcileResult(ReconcileStatus status, int advances) {}

    public static void clear(MinecraftServer server) {
        synchronized (cursors) {
            cursors.remove(server);
        }
    }

    static void resetForTests() {
        synchronized (cursors) {
            cursors.clear();
        }
    }

    private static int cursor(MinecraftServer server, CursorKey key) {
        synchronized (cursors) {
            return cursors.getOrDefault(server, Map.of()).getOrDefault(key, 0);
        }
    }

    private static void setCursor(MinecraftServer server, CursorKey key, int value) {
        synchronized (cursors) {
            cursors.computeIfAbsent(server, ignored -> new HashMap<>()).put(key, value);
        }
    }

    private record CursorKey(UUID targetId, ServantArchetype archetype) {}
}
