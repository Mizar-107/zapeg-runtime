package io.github.mizar107.zapegruntime.director;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.mizar107.zapegruntime.servant.ServantArchetype;
import io.github.mizar107.zapegruntime.servant.ServantEncounter;
import io.github.mizar107.zapegruntime.servant.ServantEncounterData;
import io.github.mizar107.zapegruntime.story.StoryCampaignDefinition;
import io.github.mizar107.zapegruntime.story.StoryCampaignJsonParser;
import io.github.mizar107.zapegruntime.story.StoryCampaignRegistry;
import io.github.mizar107.zapegruntime.story.StoryFactType;
import io.github.mizar107.zapegruntime.story.StoryNode;
import io.github.mizar107.zapegruntime.story.StoryTrigger;
import io.github.mizar107.zapegruntime.story.StoryWorldData;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CampaignServantSchedulerTest {

    private static final UUID PLAYER = UUID.fromString("b7a3fc2e-d652-495a-a234-13773d204020");

    @AfterEach
    void reset() {
        CampaignServantScheduler.resetForTests();
    }

    @Test
    void allThreeCampaignServantNodesProduceExactAutomaticPlans() throws Exception {
        StoryCampaignDefinition campaign = campaign();
        Map<String, ServantArchetype> expected = Map.of(
                "servant_of_distance", ServantArchetype.STALKER,
                "herald_at_low_water", ServantArchetype.HERALD,
                "servant_of_names", ServantArchetype.BINDER);

        for (Map.Entry<String, ServantArchetype> entry : expected.entrySet()) {
            StoryNode node = campaign.node(entry.getKey());
            StoryWorldData.PlayerSnapshot story = snapshot(campaign, node, 7L);
            CampaignServantScheduler.Plan plan =
                    CampaignServantScheduler.plan(campaign, story, node).orElseThrow();

            assertEquals(entry.getValue(), plan.archetype());
            assertEquals(node.advanceOn(), plan.trigger());
            assertEquals(PLAYER, plan.targetId());
            assertEquals(7L, plan.progressEpoch());
            assertEquals(node.id(), plan.nodeId());
            assertEquals(
                    CampaignServantIdentity.derive(
                            PLAYER,
                            campaign.id(),
                            campaign.revision(),
                            campaign.fingerprint(),
                            7L,
                            node.id(),
                            node.advanceOn().type(),
                            node.advanceOn().subject(),
                            entry.getValue()),
                    plan.encounterId());
        }
    }

    @Test
    void nonServantOrMismatchedStoryEnvelopesCannotSchedule() throws Exception {
        StoryCampaignDefinition campaign = campaign();
        StoryNode questNode = campaign.node("first_scratch");
        assertTrue(CampaignServantScheduler.plan(
                        campaign, snapshot(campaign, questNode, 0L), questNode)
                .isEmpty());

        StoryNode servantNode = campaign.node("servant_of_distance");
        StoryWorldData.PlayerSnapshot wrongNode = new StoryWorldData.PlayerSnapshot(
                PLAYER,
                campaign.id(),
                campaign.revision(),
                campaign.fingerprint(),
                0L,
                "first_scratch",
                java.util.List.of(),
                0,
                0);
        assertTrue(CampaignServantScheduler.plan(campaign, wrongNode, servantNode).isEmpty());
    }

    @Test
    void recoveryEpochRotatesTheAutomaticEncounterIdentity() throws Exception {
        StoryCampaignDefinition campaign = campaign();
        StoryNode node = campaign.node("servant_of_distance");
        UUID epochZero = CampaignServantScheduler.plan(
                        campaign, snapshot(campaign, node, 0L), node)
                .orElseThrow()
                .encounterId();
        UUID epochOne = CampaignServantScheduler.plan(
                        campaign, snapshot(campaign, node, 1L), node)
                .orElseThrow()
                .encounterId();

        assertFalse(epochZero.equals(epochOne));
    }

    @Test
    void manualAndRehearsalEncountersBlockWithoutBecomingAutomatic() throws Exception {
        StoryCampaignDefinition campaign = campaign();
        StoryNode node = campaign.node("servant_of_distance");
        CampaignServantScheduler.Plan plan = CampaignServantScheduler.plan(
                        campaign, snapshot(campaign, node, 0L), node)
                .orElseThrow();
        ServantEncounter exact = encounter(
                plan.encounterId(), false, ServantArchetype.STALKER);
        ServantEncounter rehearsal = encounter(
                plan.encounterId(), true, ServantArchetype.STALKER);
        ServantEncounter manual = encounter(
                UUID.fromString("26f93b81-b1e5-453c-bac8-abdd705341a1"),
                false,
                ServantArchetype.STALKER);

        assertTrue(CampaignServantScheduler.exactAutomaticEncounter(exact, plan));
        assertFalse(CampaignServantScheduler.exactAutomaticEncounter(rehearsal, plan));
        assertFalse(CampaignServantScheduler.exactAutomaticEncounter(manual, plan));

        ServantEncounterData.LiveVictory exactVictory =
                new ServantEncounterData.LiveVictory(
                        plan.encounterId(), PLAYER, ServantArchetype.STALKER);
        ServantEncounterData.LiveVictory wrongVictory =
                new ServantEncounterData.LiveVictory(
                        plan.encounterId(), PLAYER, ServantArchetype.HERALD);
        assertTrue(CampaignServantScheduler.exactAutomaticVictory(exactVictory, plan));
        assertFalse(CampaignServantScheduler.exactAutomaticVictory(wrongVictory, plan));
    }

    @Test
    void retryBackoffIsBoundedAndInitialGraceCoversOnePeriodicReconcileWindow() {
        assertEquals(100, CampaignServantScheduler.INITIAL_RECONCILE_GRACE_TICKS);
        assertEquals(100, CampaignServantScheduler.retryDelayTicks(1));
        assertEquals(200, CampaignServantScheduler.retryDelayTicks(2));
        assertEquals(400, CampaignServantScheduler.retryDelayTicks(3));
        assertEquals(800, CampaignServantScheduler.retryDelayTicks(4));
        assertEquals(1_200, CampaignServantScheduler.retryDelayTicks(5));
        assertEquals(1_200, CampaignServantScheduler.retryDelayTicks(31));
    }

    @Test
    void producerSetIsExactlyTheThreeTypedServantBarriers() {
        assertEquals(Set.of(
                trigger("stalker_01"), trigger("herald_01"), trigger("binder_01")),
                CampaignServantScheduler.automaticTriggers());
    }

    private static ServantEncounter encounter(
            UUID encounterId, boolean rehearsal, ServantArchetype archetype) {
        return new ServantEncounter(
                encounterId,
                PLAYER,
                UUID.fromString("44d43c09-a7ab-454a-8154-ffb9911cd7ed"),
                "minecraft:overworld",
                rehearsal,
                1_000L,
                false,
                archetype);
    }

    private static StoryWorldData.PlayerSnapshot snapshot(
            StoryCampaignDefinition campaign, StoryNode node, long epoch) {
        return new StoryWorldData.PlayerSnapshot(
                PLAYER,
                campaign.id(),
                campaign.revision(),
                campaign.fingerprint(),
                epoch,
                node.id(),
                campaign.completedPrefixFor(node.id()),
                0,
                0);
    }

    private static StoryCampaignDefinition campaign() throws Exception {
        try (var stream = CampaignServantSchedulerTest.class.getResourceAsStream(
                "/data/zapeg_runtime/heraldor_story/heraldor.json")) {
            if (stream == null) {
                throw new IllegalStateException("missing Heraldor campaign");
            }
            return StoryCampaignJsonParser.parse(
                    StoryCampaignRegistry.HERALDOR_CAMPAIGN,
                    JsonParser.parseReader(
                            new InputStreamReader(stream, StandardCharsets.UTF_8)));
        }
    }

    private static StoryTrigger trigger(String path) {
        return new StoryTrigger(
                StoryFactType.SERVANT_DEFEATED,
                ResourceLocation.fromNamespaceAndPath("zapeg_runtime", path));
    }
}
