package io.github.mizar107.zapegruntime.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StoryCampaignJsonParserTest {

    @Test
    void bundledCampaignIsAnExactThirtyNodeLinearQuest() {
        StoryCampaignDefinition campaign = StoryCampaignTestFixtures.campaign();

        assertEquals(StoryCampaignRegistry.HERALDOR_CAMPAIGN, campaign.id());
        assertEquals(1, campaign.revision());
        assertEquals("first_scratch", campaign.entryNodeId());
        assertEquals(30, campaign.nodes().size());
        assertEquals("after_ninth", campaign.nodes().get(29).id());
        assertTrue(campaign.nodes().get(29).terminal());
        assertEquals(64, campaign.fingerprint().length());

        Set<StoryTrigger> predicates = new HashSet<>();
        for (int index = 0; index < campaign.nodes().size(); index++) {
            StoryNode node = campaign.nodes().get(index);
            assertEquals(index, node.ordinal());
            if (!node.terminal()) {
                assertEquals(campaign.nodes().get(index + 1).id(), node.nextNodeId());
                assertTrue(predicates.add(node.advanceOn()));
            }
        }
    }

    @Test
    void fingerprintIsStableAcrossIndependentParses() {
        assertEquals(
                StoryCampaignTestFixtures.campaign().fingerprint(),
                StoryCampaignTestFixtures.campaign().fingerprint());
    }

    @Test
    void strictDocumentRejectsDuplicateKeysAndTrailingDocuments() {
        assertThrows(
                JsonParseException.class,
                () -> StoryStrictJsonDocument.parse(
                        new StringReader("{\"schema\":1,\"schema\":1}")));
        assertThrows(
                JsonParseException.class,
                () -> StoryStrictJsonDocument.parse(new StringReader("{} {}")));
    }

    @Test
    void parserRejectsUnknownFieldsAndFractionalIntegers() {
        JsonObject unknown = StoryCampaignTestFixtures.document().getAsJsonObject();
        unknown.addProperty("surprise", true);
        assertThrows(
                JsonParseException.class,
                () -> StoryCampaignJsonParser.parse(
                        StoryCampaignRegistry.HERALDOR_CAMPAIGN, unknown));

        JsonObject fractional = StoryCampaignTestFixtures.document().getAsJsonObject();
        fractional.addProperty("revision", 1.5D);
        assertThrows(
                JsonParseException.class,
                () -> StoryCampaignJsonParser.parse(
                        StoryCampaignRegistry.HERALDOR_CAMPAIGN, fractional));
    }

    @Test
    void parserRejectsShortCampaignSkippedEdgesAndDuplicatePredicates() {
        JsonObject shortCampaign = StoryCampaignTestFixtures.document().getAsJsonObject();
        shortCampaign.getAsJsonArray("nodes").remove(29);
        assertThrows(
                JsonParseException.class,
                () -> StoryCampaignJsonParser.parse(
                        StoryCampaignRegistry.HERALDOR_CAMPAIGN, shortCampaign));

        JsonObject skipped = StoryCampaignTestFixtures.document().getAsJsonObject();
        skipped.getAsJsonArray("nodes").get(0).getAsJsonObject()
                .addProperty("next", "tracks_against_rain");
        assertThrows(
                JsonParseException.class,
                () -> StoryCampaignJsonParser.parse(
                        StoryCampaignRegistry.HERALDOR_CAMPAIGN, skipped));

        JsonObject duplicate = StoryCampaignTestFixtures.document().getAsJsonObject();
        JsonArray nodes = duplicate.getAsJsonArray("nodes");
        JsonObject firstPredicate = nodes.get(0).getAsJsonObject()
                .getAsJsonObject("advance_on")
                .deepCopy();
        nodes.get(1).getAsJsonObject().add("advance_on", firstPredicate);
        assertThrows(
                JsonParseException.class,
                () -> StoryCampaignJsonParser.parse(
                        StoryCampaignRegistry.HERALDOR_CAMPAIGN, duplicate));
    }

    @Test
    void campaignIdMustMatchCanonicalDatapackPath() {
        JsonObject document = StoryCampaignTestFixtures.document().getAsJsonObject();
        document.addProperty("campaign_id", "zapeg_runtime:someone_else");
        JsonParseException failure = assertThrows(
                JsonParseException.class,
                () -> StoryCampaignJsonParser.parse(
                        StoryCampaignRegistry.HERALDOR_CAMPAIGN, document));
        assertTrue(failure.getMessage().contains("must match datapack resource id"));
    }
}
