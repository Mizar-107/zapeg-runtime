package io.github.mizar107.zapegruntime.boss.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mizar107.zapegruntime.boss.api.NinthFormCombatSnapshot;
import io.github.mizar107.zapegruntime.boss.api.NinthFormPhase;
import io.github.mizar107.zapegruntime.story.StoryCampaignDefinition;
import io.github.mizar107.zapegruntime.story.StoryCampaignRegistry;
import io.github.mizar107.zapegruntime.story.StoryFactType;
import io.github.mizar107.zapegruntime.story.StoryNode;
import io.github.mizar107.zapegruntime.story.StoryTrigger;
import io.github.mizar107.zapegruntime.story.StoryWorldData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class NinthFormStoryGateTest {

    @Test
    void exactOrdinalsAndPredicatesAreRequired() {
        StoryCampaignDefinition campaign = campaign(false);
        assertTrue(NinthFormStoryGate.definitionIsExact(campaign));
        StoryWorldData.PlayerSnapshot atFirst = snapshot(campaign, "first_shape", 9L);
        NinthFormStoryGate.Decision eligible =
                NinthFormStoryGate.forStart(campaign, Optional.of(atFirst));
        assertEquals(NinthFormStoryGate.Status.ELIGIBLE, eligible.status());
        assertEquals(9L, eligible.envelope().orElseThrow().progressEpoch());

        StoryWorldData.PlayerSnapshot tooEarly = snapshot(campaign, "node_26", 9L);
        assertEquals(
                NinthFormStoryGate.Status.NOT_EXPECTED,
                NinthFormStoryGate.forStart(campaign, Optional.of(tooEarly)).status());
        assertEquals(
                NinthFormStoryGate.Status.STATE_NOT_READY,
                NinthFormStoryGate.forStart(campaign, Optional.empty()).status());
        assertFalse(NinthFormStoryGate.definitionIsExact(campaign(true)));
    }

    @Test
    void finalPhaseRequiresTheOriginalCampaignEnvelopeAfterPhaseStoryReplay() {
        StoryCampaignDefinition campaign = campaign(false);
        StoryWorldData.PlayerSnapshot atFirst = snapshot(campaign, "first_shape", 3L);
        NinthFormStoryGate.Envelope envelope = NinthFormStoryGate
                .forStart(campaign, Optional.of(atFirst))
                .envelope()
                .orElseThrow();
        UUID encounterId = UUID.randomUUID();
        UUID targetId = atFirst.playerId();
        NinthFormEncounter encounter = new NinthFormEncounter(
                encounterId,
                targetId,
                UUID.randomUUID(),
                NinthFormFactIds.forProof(
                        encounterId,
                        targetId,
                        envelope,
                        NinthFormBarrier.Kind.PHASE_ONE_COMPLETED),
                NinthFormFactIds.forProof(
                        encounterId,
                        targetId,
                        envelope,
                        NinthFormBarrier.Kind.DEFEATED),
                0,
                false,
                envelope.campaignId(),
                envelope.campaignRevision(),
                envelope.campaignFingerprint(),
                envelope.progressEpoch(),
                "minecraft:overworld",
                0,
                64,
                0,
                NinthFormPhase.INTERLUDE,
                NinthFormEncounter.Lifecycle.ACTIVE,
                1,
                1.0D,
                1.0D,
                new NinthFormCombatSnapshot.CombatState(0, 0L, "idle", 0),
                NinthFormCombatSnapshot.VitalState.pristine(),
                0L);
        assertEquals(
                NinthFormStoryGate.Status.ELIGIBLE,
                NinthFormStoryGate.forFinalPhase(
                                campaign,
                                Optional.of(snapshot(campaign, "last_shape", 3L)),
                                encounter)
                        .status());
        assertEquals(
                NinthFormStoryGate.Status.ENVELOPE_MISMATCH,
                NinthFormStoryGate.forFinalPhase(
                                campaign,
                                Optional.of(snapshot(campaign, "last_shape", 4L)),
                                encounter)
                        .status());
    }

    private static StoryWorldData.PlayerSnapshot snapshot(
            StoryCampaignDefinition campaign, String nodeId, long epoch) {
        return new StoryWorldData.PlayerSnapshot(
                UUID.fromString("30000000-0000-0000-0000-000000000003"),
                campaign.id(),
                campaign.revision(),
                campaign.fingerprint(),
                epoch,
                nodeId,
                List.of(),
                0,
                0);
    }

    private static StoryCampaignDefinition campaign(boolean wrongPhaseSubject) {
        List<StoryNode> nodes = new ArrayList<>();
        for (int ordinal = 0; ordinal < 30; ordinal++) {
            String id = switch (ordinal) {
                case 27 -> "first_shape";
                case 28 -> "last_shape";
                case 29 -> "after_ninth";
                default -> String.format("node_%02d", ordinal);
            };
            if (ordinal == 29) {
                nodes.add(new StoryNode(id, ordinal, 5, "journal.test." + id, true, null, null));
                continue;
            }
            String next = switch (ordinal + 1) {
                case 27 -> "first_shape";
                case 28 -> "last_shape";
                case 29 -> "after_ninth";
                default -> String.format("node_%02d", ordinal + 1);
            };
            StoryTrigger trigger;
            if (ordinal == 27) {
                trigger = new StoryTrigger(
                        StoryFactType.BOSS_PHASE_COMPLETED,
                        wrongPhaseSubject
                                ? ResourceLocation.tryBuild("zapeg_runtime", "wrong_phase")
                                : NinthFormStoryGate.PHASE_SUBJECT);
            } else if (ordinal == 28) {
                trigger = new StoryTrigger(StoryFactType.BOSS_DEFEATED, NinthFormStoryGate.DEFEAT_SUBJECT);
            } else {
                trigger = new StoryTrigger(
                        StoryFactType.WORLD_DISCOVERY,
                        ResourceLocation.tryBuild("zapeg_runtime", "test_" + ordinal));
            }
            nodes.add(new StoryNode(id, ordinal, 5, "journal.test." + id, false, trigger, next));
        }
        return new StoryCampaignDefinition(
                StoryCampaignRegistry.HERALDOR_CAMPAIGN,
                1,
                "node_00",
                nodes);
    }
}
