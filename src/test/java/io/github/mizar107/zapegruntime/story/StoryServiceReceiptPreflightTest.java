package io.github.mizar107.zapegruntime.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class StoryServiceReceiptPreflightTest {

    private static final UUID PLAYER = uuid("preflight-player");

    @Test
    void exactReplayIsCertifiedButEveryIdentityMutationConflicts() {
        StoryCampaignDefinition campaign = StoryCampaignTestFixtures.campaign();
        StoryNode entry = campaign.nodes().get(0);
        StoryFact recorded = new StoryFact(
                uuid("preflight-fact"),
                PLAYER,
                campaign.id(),
                campaign.revision(),
                0L,
                entry.id(),
                entry.advanceOn().type(),
                entry.advanceOn().subject());
        StoryWorldData data = new StoryWorldData();
        assertEquals(
                StoryWorldData.ApplyStatus.ADVANCED,
                data.applyFact(campaign, recorded).status());

        assertEquals(
                StoryService.SubmissionStatus.ALREADY_PROCESSED,
                preflight(data, campaign, recorded.factId(), PLAYER,
                                recorded.type(), recorded.subject())
                        .status());
        assertEquals(
                StoryService.SubmissionStatus.FACT_ID_CONFLICT,
                preflight(data, campaign, recorded.factId(), PLAYER,
                                recorded.type(), StoryCampaignTestFixtures.id("mutated_subject"))
                        .status());
        assertEquals(
                StoryService.SubmissionStatus.FACT_ID_CONFLICT,
                preflight(data, campaign, recorded.factId(), PLAYER,
                                StoryFactType.SERVANT_DEFEATED, recorded.subject())
                        .status());

        StoryCampaignDefinition revised = new StoryCampaignDefinition(
                campaign.id(), 2, campaign.entryNodeId(), campaign.nodes());
        assertEquals(
                StoryService.SubmissionStatus.FACT_ID_CONFLICT,
                preflight(data, revised, recorded.factId(), PLAYER,
                                recorded.type(), recorded.subject())
                        .status());
        StoryCampaignDefinition anotherCampaign = new StoryCampaignDefinition(
                StoryCampaignTestFixtures.id("another_campaign"),
                campaign.revision(),
                campaign.entryNodeId(),
                campaign.nodes());
        assertEquals(
                StoryService.SubmissionStatus.FACT_ID_CONFLICT,
                preflight(data, anotherCampaign, recorded.factId(), PLAYER,
                                recorded.type(), recorded.subject())
                        .status());
        assertEquals(
                StoryService.SubmissionStatus.FACT_ID_CONFLICT,
                preflight(data, campaign, recorded.factId(), uuid("other-player"),
                                recorded.type(), recorded.subject())
                        .status());

        assertTrue(StoryService.preflightReceipt(
                        data,
                        campaign,
                        uuid("unseen-fact"),
                        PLAYER,
                        recorded.type(),
                        recorded.subject())
                .isEmpty());
    }

    private static StoryService.SubmissionResult preflight(
            StoryWorldData data,
            StoryCampaignDefinition campaign,
            UUID factId,
            UUID playerId,
            StoryFactType type,
            ResourceLocation subject) {
        return StoryService.preflightReceipt(
                        data, campaign, factId, playerId, type, subject)
                .orElseThrow();
    }

    private static UUID uuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
}
