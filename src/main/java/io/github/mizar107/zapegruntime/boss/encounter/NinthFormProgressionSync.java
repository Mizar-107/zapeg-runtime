package io.github.mizar107.zapegruntime.boss.encounter;

import io.github.mizar107.zapegruntime.story.StoryCampaignDefinition;
import io.github.mizar107.zapegruntime.story.StoryCampaignRegistry;
import io.github.mizar107.zapegruntime.story.StoryService;
import io.github.mizar107.zapegruntime.story.StoryWorldData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Replays durable boss proofs into story state in causal order. */
public final class NinthFormProgressionSync {

    private NinthFormProgressionSync() {}

    public static List<SyncResult> replayAll(MinecraftServer server) {
        return replay(server, NinthFormEncounterData.get(server).immutableBarriers());
    }

    public static List<SyncResult> replayTarget(MinecraftServer server, UUID targetId) {
        return replay(
                server,
                NinthFormEncounterData.get(server).immutableBarriersForTarget(targetId));
    }

    private static List<SyncResult> replay(
            MinecraftServer server, List<NinthFormBarrier> source) {
        List<NinthFormBarrier> barriers = new ArrayList<>(source);
        barriers.sort(Comparator
                .comparing((NinthFormBarrier barrier) -> barrier.encounterId().toString())
                .thenComparingInt(barrier -> barrier.kind() == NinthFormBarrier.Kind.PHASE_ONE_COMPLETED
                        ? 0
                        : 1));
        List<SyncResult> results = new ArrayList<>(barriers.size());
        for (NinthFormBarrier barrier : barriers) {
            results.add(syncBarrier(server, barrier));
        }
        return List.copyOf(results);
    }

    public static SyncResult syncBarrier(MinecraftServer server, NinthFormBarrier barrier) {
        Optional<StoryCampaignDefinition> loaded =
                StoryCampaignRegistry.current().find(barrier.campaignId());
        if (loaded.isEmpty()) {
            return new SyncResult(SyncStatus.NOT_READY, barrier.factId(), "campaign is not loaded");
        }
        StoryCampaignDefinition campaign = loaded.get();
        if (!NinthFormStoryGate.definitionIsExact(campaign)
                || campaign.revision() != barrier.campaignRevision()
                || !campaign.fingerprint().equals(barrier.campaignFingerprint())) {
            return new SyncResult(
                    SyncStatus.ENVELOPE_MISMATCH,
                    barrier.factId(),
                    "barrier does not match the active campaign definition");
        }
        Optional<StoryWorldData.PlayerSnapshot> snapshot = StoryService.snapshot(
                server, barrier.targetId());
        if (snapshot.isEmpty()) {
            return new SyncResult(SyncStatus.NOT_READY, barrier.factId(), "story state is unavailable");
        }
        StoryWorldData.PlayerSnapshot state = snapshot.get();
        if (!state.campaignId().equals(barrier.campaignId())
                || state.campaignRevision() != barrier.campaignRevision()
                || !state.definitionFingerprint().equals(barrier.campaignFingerprint())
                || state.progressEpoch() != barrier.progressEpoch()) {
            return new SyncResult(
                    SyncStatus.ENVELOPE_MISMATCH,
                    barrier.factId(),
                    "player campaign envelope changed after the proof was created");
        }
        int currentOrdinal = campaign.ordinalOf(state.currentNodeId());
        int expectedOrdinal = barrier.kind() == NinthFormBarrier.Kind.PHASE_ONE_COMPLETED
                ? NinthFormStoryGate.FIRST_SHAPE_ORDINAL
                : NinthFormStoryGate.LAST_SHAPE_ORDINAL;
        if (currentOrdinal > expectedOrdinal) {
            return new SyncResult(
                    SyncStatus.ALREADY_ADVANCED,
                    barrier.factId(),
                    "story is already beyond this barrier");
        }
        if (currentOrdinal != expectedOrdinal) {
            return new SyncResult(
                    SyncStatus.NOT_READY,
                    barrier.factId(),
                    "story has not reached this barrier's exact ordinal");
        }
        StoryService.SubmissionResult submitted = StoryService.submitIfExpected(
                server,
                barrier.factId(),
                barrier.targetId(),
                barrier.campaignId(),
                barrier.kind().storyType(),
                barrier.kind().storySubject());
        SyncStatus status = switch (submitted.status()) {
            case APPLIED -> SyncStatus.APPLIED;
            case ALREADY_PROCESSED -> SyncStatus.ALREADY_ADVANCED;
            case NOT_EXPECTED, STATE_NOT_READY, CAMPAIGN_NOT_LOADED -> SyncStatus.NOT_READY;
            case PROCESSED, DATA_UNAVAILABLE, CAPACITY_EXHAUSTED, FACT_ID_CONFLICT ->
                    SyncStatus.REFUSED;
        };
        return new SyncResult(status, barrier.factId(), submitted.detail());
    }

    public static int barrierCountForTarget(MinecraftServer server, UUID targetId) {
        return NinthFormEncounterData.get(server).immutableBarrierCountForTarget(targetId);
    }

    public enum SyncStatus {
        APPLIED,
        ALREADY_ADVANCED,
        NOT_READY,
        ENVELOPE_MISMATCH,
        REFUSED
    }

    public record SyncResult(SyncStatus status, UUID factId, String detail) {}
}
