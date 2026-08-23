package io.github.mizar107.zapegruntime.story;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoryTransitionEngineTest {

    private static final UUID PLAYER =
            UUID.fromString("00000000-0000-0000-0000-000000000107");

    @Test
    void exactTypedFactAdvancesDeterministically() {
        StoryCampaignDefinition campaign = StoryCampaignTestFixtures.campaign();
        StoryNode current = campaign.nodes().get(0);
        StoryFact fact = fact(campaign, current, current.advanceOn());

        StoryTransitionEngine.Decision first =
                StoryTransitionEngine.evaluate(campaign, current.id(), 0L, fact);
        StoryTransitionEngine.Decision second =
                StoryTransitionEngine.evaluate(campaign, current.id(), 0L, fact);

        assertEquals(StoryTransitionEngine.Outcome.ADVANCE, first.outcome());
        assertEquals("voice_without_air", first.resultingNodeId());
        assertEquals(first, second);
    }

    @Test
    void wrongPredicateAndStaleExpectedNodeNeverAdvance() {
        StoryCampaignDefinition campaign = StoryCampaignTestFixtures.campaign();
        StoryNode current = campaign.nodes().get(0);
        StoryFact wrong = fact(
                campaign,
                current,
                new StoryTrigger(
                        StoryFactType.SCENE_COMPLETED,
                        StoryCampaignTestFixtures.id("not_the_trigger")));
        StoryFact stale = new StoryFact(
                UUID.fromString("10000000-0000-0000-0000-000000000002"),
                PLAYER,
                campaign.id(),
                campaign.revision(),
                0L,
                campaign.nodes().get(1).id(),
                current.advanceOn().type(),
                current.advanceOn().subject());

        assertEquals(
                StoryTransitionEngine.Outcome.NO_MATCH,
                StoryTransitionEngine.evaluate(campaign, current.id(), 0L, wrong).outcome());
        assertEquals(
                StoryTransitionEngine.Outcome.STALE_NODE,
                StoryTransitionEngine.evaluate(campaign, current.id(), 0L, stale).outcome());
    }

    @Test
    void revisionMismatchAndTerminalNodeFailClosed() {
        StoryCampaignDefinition campaign = StoryCampaignTestFixtures.campaign();
        StoryNode first = campaign.nodes().get(0);
        StoryFact wrongRevision = new StoryFact(
                UUID.fromString("10000000-0000-0000-0000-000000000003"),
                PLAYER,
                campaign.id(),
                campaign.revision() + 1,
                0L,
                first.id(),
                first.advanceOn().type(),
                first.advanceOn().subject());
        StoryNode terminal = campaign.nodes().get(29);
        StoryFact terminalFact = new StoryFact(
                UUID.fromString("10000000-0000-0000-0000-000000000004"),
                PLAYER,
                campaign.id(),
                campaign.revision(),
                0L,
                terminal.id(),
                StoryFactType.WORLD_DISCOVERY,
                StoryCampaignTestFixtures.id("irrelevant"));

        assertEquals(
                StoryTransitionEngine.Outcome.DEFINITION_MISMATCH,
                StoryTransitionEngine.evaluate(campaign, first.id(), 0L, wrongRevision).outcome());
        assertEquals(
                StoryTransitionEngine.Outcome.TERMINAL,
                StoryTransitionEngine.evaluate(campaign, terminal.id(), 0L, terminalFact).outcome());

        assertEquals(
                StoryTransitionEngine.Outcome.STALE_EPOCH,
                StoryTransitionEngine.evaluate(campaign, first.id(), 1L, fact(
                        campaign, first, first.advanceOn())).outcome());
    }

    private static StoryFact fact(
            StoryCampaignDefinition campaign, StoryNode node, StoryTrigger trigger) {
        return new StoryFact(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                PLAYER,
                campaign.id(),
                campaign.revision(),
                0L,
                node.id(),
                trigger.type(),
                trigger.subject());
    }
}
