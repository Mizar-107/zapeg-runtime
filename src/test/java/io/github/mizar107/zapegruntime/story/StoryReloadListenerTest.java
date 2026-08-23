package io.github.mizar107.zapegruntime.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class StoryReloadListenerTest {

    @Test
    void validRegistryPublishesAtomicallyAndInvalidReloadRetainsIt() {
        StoryReloadListener listener = new StoryReloadListener();
        Map<ResourceLocation, JsonElement> valid = Map.of(
                StoryCampaignRegistry.HERALDOR_CAMPAIGN,
                StoryCampaignTestFixtures.document());
        listener.apply(valid, null, null);
        StoryCampaignRegistry.Snapshot published = StoryCampaignRegistry.current();
        assertTrue(published.find(StoryCampaignRegistry.HERALDOR_CAMPAIGN).isPresent());

        JsonObject invalid = StoryCampaignTestFixtures.document().getAsJsonObject();
        invalid.addProperty("revision", 1.25D);
        assertThrows(
                IllegalStateException.class,
                () -> listener.apply(
                        Map.of(StoryCampaignRegistry.HERALDOR_CAMPAIGN, invalid),
                        null,
                        null));

        StoryCampaignRegistry.Snapshot retained = StoryCampaignRegistry.current();
        assertEquals(published.generation(), retained.generation());
        assertEquals(
                published.find(StoryCampaignRegistry.HERALDOR_CAMPAIGN)
                        .orElseThrow()
                        .fingerprint(),
                retained.find(StoryCampaignRegistry.HERALDOR_CAMPAIGN)
                        .orElseThrow()
                        .fingerprint());
    }

    @Test
    void requiredCampaignAndRegistryBoundAreEnforced() {
        assertThrows(
                IllegalStateException.class,
                () -> StoryReloadListener.parseResources(Map.of()));

        Map<ResourceLocation, JsonElement> oversized = new LinkedHashMap<>();
        for (int index = 0; index <= StoryReloadListener.MAX_CAMPAIGNS; index++) {
            oversized.put(
                    StoryCampaignTestFixtures.id("example", "campaign_" + index),
                    StoryCampaignTestFixtures.document());
        }
        assertThrows(
                IllegalStateException.class,
                () -> StoryReloadListener.parseResources(oversized));
    }
}
