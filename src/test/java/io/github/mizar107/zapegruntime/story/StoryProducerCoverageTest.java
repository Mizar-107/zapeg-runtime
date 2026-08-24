package io.github.mizar107.zapegruntime.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.mizar107.zapegruntime.director.DirectorSceneCatalog;
import io.github.mizar107.zapegruntime.director.DirectorSceneJsonParser;
import io.github.mizar107.zapegruntime.journal.JournalAction;
import io.github.mizar107.zapegruntime.quest.QuestAction;
import io.github.mizar107.zapegruntime.servant.ServantArchetype;
import io.github.mizar107.zapegruntime.servant.ServantProgressionSync;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Prevents a campaign node from shipping without one intentional runtime producer. */
class StoryProducerCoverageTest {

    @Test
    void everyPreBossTriggerHasExactlyOnePlayableProducer() throws Exception {
        StoryCampaignDefinition campaign = campaign();
        Set<StoryTrigger> campaignTriggers = new HashSet<>();
        for (StoryNode node : campaign.nodes()) {
            if (!node.terminal()) {
                assertTrue(campaignTriggers.add(node.advanceOn()), node.id());
            }
        }

        Set<StoryTrigger> produced = new HashSet<>();
        for (QuestAction action : QuestAction.values()) {
            assertTrue(produced.add(action.trigger()), action.name());
        }
        for (JournalAction action : JournalAction.values()) {
            assertTrue(produced.add(new StoryTrigger(
                    StoryFactType.JOURNAL_DISCOVERY, action.subject())), action.name());
        }
        for (ServantArchetype archetype : ServantArchetype.values()) {
            assertTrue(produced.add(new StoryTrigger(
                    StoryFactType.SERVANT_DEFEATED,
                    ServantProgressionSync.storySubject(archetype))), archetype.name());
        }
        DirectorSceneCatalog director = director();
        for (StoryTrigger trigger : director.bindings().keySet()) {
            assertTrue(produced.add(trigger), trigger.toString());
        }

        assertEquals(27, produced.size());
        assertTrue(campaignTriggers.containsAll(produced));
        Set<StoryTrigger> intentionallyDeferredToBatchFour = new HashSet<>(campaignTriggers);
        intentionallyDeferredToBatchFour.removeAll(produced);
        assertEquals(Set.of(
                trigger(StoryFactType.BOSS_PHASE_COMPLETED, "ninth_form_phase_01"),
                trigger(StoryFactType.BOSS_DEFEATED, "ninth_form")),
                intentionallyDeferredToBatchFour);
    }

    private static StoryCampaignDefinition campaign() throws Exception {
        try (var stream = StoryProducerCoverageTest.class.getResourceAsStream(
                "/data/zapeg_runtime/heraldor_story/heraldor.json")) {
            if (stream == null) {
                throw new IllegalStateException("missing Heraldor campaign");
            }
            return StoryCampaignJsonParser.parse(
                    StoryCampaignRegistry.HERALDOR_CAMPAIGN,
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        }
    }

    private static DirectorSceneCatalog director() throws Exception {
        try (var stream = StoryProducerCoverageTest.class.getResourceAsStream(
                "/data/zapeg_runtime/heraldor_director/heraldor.json")) {
            if (stream == null) {
                throw new IllegalStateException("missing Heraldor Director catalog");
            }
            return DirectorSceneJsonParser.parse(
                    StoryCampaignRegistry.HERALDOR_CAMPAIGN,
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        }
    }

    private static StoryTrigger trigger(StoryFactType type, String subject) {
        return new StoryTrigger(
                type, ResourceLocation.fromNamespaceAndPath("zapeg_runtime", subject));
    }
}
