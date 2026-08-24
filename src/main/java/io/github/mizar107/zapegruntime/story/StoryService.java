package io.github.mizar107.zapegruntime.story;

import io.github.mizar107.zapegruntime.server.HeraldorSafetyController;
import io.github.mizar107.zapegruntime.server.HeraldorSafetyMode;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;

/** Stable typed integration seam for scenes, encounters, and later boss code. */
public final class StoryService {

    private StoryService() {}

    public static SubmissionResult submit(MinecraftServer server, StoryFact fact) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(fact, "fact");
        requireServerThread(server);
        if (!HeraldorSafetyController.allows(server, HeraldorSafetyMode.AUTO)) {
            return unavailable(
                    SubmissionStatus.DATA_UNAVAILABLE,
                    HeraldorSafetyController.denial(server, HeraldorSafetyMode.AUTO));
        }
        Optional<StoryCampaignDefinition> campaign =
                StoryCampaignRegistry.current().find(fact.campaignId());
        if (campaign.isEmpty()) {
            return unavailable(
                    SubmissionStatus.CAMPAIGN_NOT_LOADED,
                    "campaign is not present in the active datapack registry");
        }
        StoryWorldData.ApplyResult applied =
                StoryWorldData.get(server).applyFact(campaign.get(), fact);
        return publishAdvance(server, fact, classifyExpected(applied));
    }

    /**
     * Reconciles a durable typed barrier only when it is the predicate expected
     * by the player's current node. Non-matching barriers are not consumed and
     * may be reconsidered after legitimate progress.
     */
    public static SubmissionResult submitIfExpected(
            MinecraftServer server,
            UUID factId,
            UUID playerId,
            ResourceLocation campaignId,
            StoryFactType type,
            ResourceLocation subject) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(factId, "factId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(campaignId, "campaignId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(subject, "subject");
        requireServerThread(server);
        if (!HeraldorSafetyController.allows(server, HeraldorSafetyMode.AUTO)) {
            return unavailable(
                    SubmissionStatus.DATA_UNAVAILABLE,
                    HeraldorSafetyController.denial(server, HeraldorSafetyMode.AUTO));
        }

        Optional<StoryCampaignDefinition> loaded =
                StoryCampaignRegistry.current().find(campaignId);
        if (loaded.isEmpty()) {
            return unavailable(
                    SubmissionStatus.CAMPAIGN_NOT_LOADED,
                    "campaign is not present in the active datapack registry");
        }
        StoryCampaignDefinition campaign = loaded.get();
        StoryWorldData data = StoryWorldData.get(server);
        StoryWorldData.SchemaStatus schema = data.schemaStatus();
        if (!schema.writable()) {
            return unavailable(
                    SubmissionStatus.DATA_UNAVAILABLE,
                    "story saved data is not writable: " + schema.detail());
        }
        Optional<SubmissionResult> receiptResult = preflightReceipt(
                data, campaign, factId, playerId, type, subject);
        if (receiptResult.isPresent()) {
            return receiptResult.get();
        }

        StoryFactGate.Decision gate = StoryFactGate.prepare(
                campaign, data.snapshot(playerId), factId, playerId, type, subject);
        if (gate.outcome() == StoryFactGate.Outcome.NOT_EXPECTED) {
            return unavailable(SubmissionStatus.NOT_EXPECTED, gate.detail());
        }
        if (gate.outcome() == StoryFactGate.Outcome.STATE_NOT_READY) {
            return unavailable(SubmissionStatus.STATE_NOT_READY, gate.detail());
        }
        StoryFact fact = gate.fact().orElseThrow();
        StoryWorldData.ApplyResult applied = data.applyFact(campaign, fact);
        return publishAdvance(server, fact, classifyExpected(applied));
    }

    /** Pure receipt coordinator kept executable without a live server fixture. */
    static Optional<SubmissionResult> preflightReceipt(
            StoryWorldData data,
            StoryCampaignDefinition campaign,
            UUID factId,
            UUID playerId,
            StoryFactType type,
            ResourceLocation subject) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(campaign, "campaign");
        StoryWorldData.ReceiptStatus receipt = data.receiptStatus(
                playerId,
                factId,
                campaign.id(),
                campaign.revision(),
                type,
                subject);
        return switch (receipt) {
            case ABSENT -> Optional.empty();
            case EXACT -> Optional.of(unavailable(
                    SubmissionStatus.ALREADY_PROCESSED,
                    "durable fact and payload were already processed"));
            case CONFLICT -> Optional.of(unavailable(
                    SubmissionStatus.FACT_ID_CONFLICT,
                    "fact UUID was previously bound to another target or payload"));
            case UNVERIFIABLE -> Optional.of(unavailable(
                    SubmissionStatus.FACT_ID_CONFLICT,
                    "legacy fact receipt cannot prove target and payload identity"));
            case DATA_UNAVAILABLE -> Optional.of(unavailable(
                    SubmissionStatus.DATA_UNAVAILABLE,
                    "story receipt ledger is unavailable"));
        };
    }

    public static Optional<StoryWorldData.PlayerSnapshot> snapshot(
            MinecraftServer server, UUID playerId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(playerId, "playerId");
        requireServerThread(server);
        return StoryWorldData.get(server).snapshot(playerId);
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("story mutations and reads require the server thread");
        }
    }

    private static SubmissionResult unavailable(SubmissionStatus status, String detail) {
        return new SubmissionResult(status, detail, Optional.empty());
    }

    static SubmissionResult classifyExpected(StoryWorldData.ApplyResult applied) {
        Objects.requireNonNull(applied, "applied");
        SubmissionStatus status = switch (applied.status()) {
            case ADVANCED -> SubmissionStatus.APPLIED;
            case DUPLICATE -> SubmissionStatus.ALREADY_PROCESSED;
            case FACT_ID_CONFLICT -> SubmissionStatus.FACT_ID_CONFLICT;
            case PLAYER_CAPACITY_EXHAUSTED, FACT_CAPACITY_EXHAUSTED ->
                    SubmissionStatus.CAPACITY_EXHAUSTED;
            case DATA_UNAVAILABLE -> SubmissionStatus.DATA_UNAVAILABLE;
            case DEFINITION_MISMATCH, INVALID_STATE, STALE_EPOCH ->
                    SubmissionStatus.STATE_NOT_READY;
            case RECORDED_NO_MATCH, RECORDED_STALE, RECORDED_TERMINAL ->
                    SubmissionStatus.PROCESSED;
        };
        return new SubmissionResult(status, applied.detail(), Optional.of(applied));
    }

    private static SubmissionResult publishAdvance(
            MinecraftServer server, StoryFact fact, SubmissionResult result) {
        if (result.status() != SubmissionStatus.APPLIED) {
            return result;
        }
        StoryWorldData.ApplyResult applied = result.application().orElseThrow();
        if (applied.previousNodeId() == null
                || applied.currentNodeId() == null
                || applied.previousNodeId().equals(applied.currentNodeId())) {
            throw new IllegalStateException("APPLIED story result lacks a node transition");
        }
        MinecraftForge.EVENT_BUS.post(new StoryAdvancedEvent(
                server,
                fact.playerId(),
                fact.factId(),
                fact.type(),
                fact.subject(),
                applied.previousNodeId(),
                applied.currentNodeId()));
        return result;
    }

    public enum SubmissionStatus {
        PROCESSED,
        APPLIED,
        ALREADY_PROCESSED,
        NOT_EXPECTED,
        CAMPAIGN_NOT_LOADED,
        DATA_UNAVAILABLE,
        STATE_NOT_READY,
        CAPACITY_EXHAUSTED,
        FACT_ID_CONFLICT
    }

    public record SubmissionResult(
            SubmissionStatus status,
            String detail,
            Optional<StoryWorldData.ApplyResult> application) {

        public SubmissionResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(detail, "detail");
            Objects.requireNonNull(application, "application");
        }
    }
}
