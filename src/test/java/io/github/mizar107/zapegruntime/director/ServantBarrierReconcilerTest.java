package io.github.mizar107.zapegruntime.director;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.mizar107.zapegruntime.servant.ServantArchetype;
import io.github.mizar107.zapegruntime.servant.ServantEncounterData;
import io.github.mizar107.zapegruntime.servant.ServantProgressionSync;
import io.github.mizar107.zapegruntime.story.StoryCampaignDefinition;
import io.github.mizar107.zapegruntime.story.StoryCampaignJsonParser;
import io.github.mizar107.zapegruntime.story.StoryFact;
import io.github.mizar107.zapegruntime.story.StoryFactType;
import io.github.mizar107.zapegruntime.story.StoryNode;
import io.github.mizar107.zapegruntime.story.StoryWorldData;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ServantBarrierReconcilerTest {

    private static final UUID TARGET =
            UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final ResourceLocation CAMPAIGN_ID = id("heraldor");

    @Test
    void campaignNodeSelectsArchetypeBeforeAnyUuidOrdering() {
        StoryCampaignDefinition campaign = campaign();
        assertEquals(
                ServantArchetype.STALKER,
                ServantBarrierReconciler.expectedArchetype(
                        campaign.node("servant_of_distance")).orElseThrow());
        assertEquals(
                ServantArchetype.HERALD,
                ServantBarrierReconciler.expectedArchetype(
                        campaign.node("herald_at_low_water")).orElseThrow());
        assertEquals(
                ServantArchetype.BINDER,
                ServantBarrierReconciler.expectedArchetype(
                        campaign.node("servant_of_names")).orElseThrow());
        assertTrue(ServantBarrierReconciler.expectedArchetype(
                campaign.node("voice_without_air")).isEmpty());
    }

    @Test
    void fullStartupScanFindsNewBarrierBehindMoreThanSixtyFourReceipts() {
        StoryCampaignDefinition campaign = campaign();
        StoryWorldData story = new StoryWorldData();
        List<ServantEncounterData.LiveVictory> barriers = new ArrayList<>();
        for (int index = 0; index < 70; index++) {
            UUID eventId = event(index);
            recordReceipt(story, campaign, eventId, ServantArchetype.STALKER);
            barriers.add(new ServantEncounterData.LiveVictory(
                    eventId, TARGET, ServantArchetype.STALKER));
        }
        UUID fresh = event(999);
        barriers.add(new ServantEncounterData.LiveVictory(
                fresh, TARGET, ServantArchetype.STALKER));

        ServantBarrierReconciler.Selection selection =
                ServantBarrierReconciler.selectUnprocessed(
                        story,
                        campaign,
                        TARGET,
                        ServantArchetype.STALKER,
                        barriers,
                        0,
                        barriers.size());
        assertEquals(ServantBarrierReconciler.SelectionStatus.SELECTED, selection.status());
        assertEquals(fresh, selection.barrier().orElseThrow().encounterId());
        assertEquals(71, selection.scanned());
    }

    @Test
    void periodicCursorSurfacesScanLimitThenCannotStarveAppendedBarrier() {
        StoryCampaignDefinition campaign = campaign();
        StoryWorldData story = new StoryWorldData();
        List<ServantEncounterData.LiveVictory> barriers = new ArrayList<>();
        for (int index = 0; index < 70; index++) {
            UUID eventId = event(index);
            recordReceipt(story, campaign, eventId, ServantArchetype.STALKER);
            barriers.add(new ServantEncounterData.LiveVictory(
                    eventId, TARGET, ServantArchetype.STALKER));
        }
        UUID appended = event(1000);
        barriers.add(new ServantEncounterData.LiveVictory(
                appended, TARGET, ServantArchetype.STALKER));

        ServantBarrierReconciler.Selection first =
                ServantBarrierReconciler.selectUnprocessed(
                        story,
                        campaign,
                        TARGET,
                        ServantArchetype.STALKER,
                        barriers,
                        0,
                        ServantBarrierReconciler.PERIODIC_SCAN_BUDGET);
        assertEquals(ServantBarrierReconciler.SelectionStatus.SCAN_LIMIT, first.status());
        assertEquals(64, first.scanned());

        ServantBarrierReconciler.Selection second =
                ServantBarrierReconciler.selectUnprocessed(
                        story,
                        campaign,
                        TARGET,
                        ServantArchetype.STALKER,
                        barriers,
                        first.nextIndex(),
                        ServantBarrierReconciler.PERIODIC_SCAN_BUDGET);
        assertEquals(ServantBarrierReconciler.SelectionStatus.SELECTED, second.status());
        assertEquals(appended, second.barrier().orElseThrow().encounterId());
        assertEquals(7, second.scanned());
    }

    @Test
    void payloadConflictIsExplicitInsteadOfNoMatching() {
        StoryCampaignDefinition campaign = campaign();
        StoryWorldData story = new StoryWorldData();
        UUID eventId = event(42);
        recordReceipt(story, campaign, eventId, ServantArchetype.HERALD);
        List<ServantEncounterData.LiveVictory> barriers = List.of(
                new ServantEncounterData.LiveVictory(
                        eventId, TARGET, ServantArchetype.STALKER));

        ServantBarrierReconciler.Selection selection =
                ServantBarrierReconciler.selectUnprocessed(
                        story,
                        campaign,
                        TARGET,
                        ServantArchetype.STALKER,
                        barriers,
                        0,
                        1);
        assertEquals(
                ServantBarrierReconciler.SelectionStatus.FACT_CONFLICT,
                selection.status());
    }

    private static void recordReceipt(
            StoryWorldData story,
            StoryCampaignDefinition campaign,
            UUID factId,
            ServantArchetype archetype) {
        StoryNode entry = campaign.node(campaign.entryNodeId());
        StoryWorldData.ApplyResult result = story.applyFact(campaign, new StoryFact(
                factId,
                TARGET,
                campaign.id(),
                campaign.revision(),
                0L,
                entry.id(),
                StoryFactType.SERVANT_DEFEATED,
                ServantProgressionSync.storySubject(archetype)));
        assertEquals(StoryWorldData.ApplyStatus.RECORDED_NO_MATCH, result.status());
    }

    private static StoryCampaignDefinition campaign() {
        try (var stream = ServantBarrierReconcilerTest.class.getResourceAsStream(
                "/data/zapeg_runtime/heraldor_story/heraldor.json")) {
            if (stream == null) {
                throw new IllegalStateException("missing campaign resource");
            }
            return StoryCampaignJsonParser.parse(
                    CAMPAIGN_ID,
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        } catch (java.io.IOException invalid) {
            throw new IllegalStateException(invalid);
        }
    }

    private static UUID event(int index) {
        return UUID.nameUUIDFromBytes(("servant-barrier-" + index)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("zapeg_runtime", path);
    }
}
