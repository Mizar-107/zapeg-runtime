package io.github.mizar107.zapegruntime.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoryFactGateTest {

    private static final UUID PLAYER =
            UUID.fromString("00000000-0000-0000-0000-000000000107");
    private static final UUID FACT =
            UUID.fromString("10000000-0000-0000-0000-000000000107");

    @Test
    void uninitializedPlayerGetsAnEpochZeroFactOnlyForTheEntryPredicate() {
        StoryCampaignDefinition campaign = StoryCampaignTestFixtures.campaign();
        StoryNode entry = campaign.nodes().get(0);

        StoryFactGate.Decision ready = StoryFactGate.prepare(
                campaign,
                Optional.empty(),
                FACT,
                PLAYER,
                entry.advanceOn().type(),
                entry.advanceOn().subject());
        assertEquals(StoryFactGate.Outcome.READY, ready.outcome());
        StoryFact fact = ready.fact().orElseThrow();
        assertEquals(0L, fact.progressEpoch());
        assertEquals(entry.id(), fact.expectedNodeId());

        StoryFactGate.Decision unrelated = StoryFactGate.prepare(
                campaign,
                Optional.empty(),
                FACT,
                PLAYER,
                StoryFactType.SCENE_COMPLETED,
                StoryCampaignTestFixtures.id("unrelated"));
        assertEquals(StoryFactGate.Outcome.NOT_EXPECTED, unrelated.outcome());
        assertTrue(unrelated.fact().isEmpty());
    }

    @Test
    void persistedEpochAndNodeAreCapturedExactly() {
        StoryCampaignDefinition campaign = StoryCampaignTestFixtures.campaign();
        StoryNode node = campaign.nodes().get(1);
        StoryWorldData.PlayerSnapshot state = new StoryWorldData.PlayerSnapshot(
                PLAYER,
                campaign.id(),
                campaign.revision(),
                campaign.fingerprint(),
                7L,
                node.id(),
                List.of(campaign.nodes().get(0).id()),
                1,
                1);

        StoryFactGate.Decision ready = StoryFactGate.prepare(
                campaign,
                Optional.of(state),
                FACT,
                PLAYER,
                node.advanceOn().type(),
                node.advanceOn().subject());

        assertEquals(StoryFactGate.Outcome.READY, ready.outcome());
        assertEquals(7L, ready.fact().orElseThrow().progressEpoch());
        assertEquals(node.id(), ready.fact().orElseThrow().expectedNodeId());
    }

    @Test
    void wrongPlayerOrDefinitionBindingFailsClosed() {
        StoryCampaignDefinition campaign = StoryCampaignTestFixtures.campaign();
        StoryWorldData.PlayerSnapshot wrongPlayer = new StoryWorldData.PlayerSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000000999"),
                campaign.id(),
                campaign.revision(),
                campaign.fingerprint(),
                0L,
                campaign.entryNodeId(),
                List.of(),
                0,
                0);
        StoryNode entry = campaign.nodes().get(0);

        assertEquals(
                StoryFactGate.Outcome.STATE_NOT_READY,
                StoryFactGate.prepare(
                                campaign,
                                Optional.of(wrongPlayer),
                                FACT,
                                PLAYER,
                                entry.advanceOn().type(),
                                entry.advanceOn().subject())
                        .outcome());
    }
}
