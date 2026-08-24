package io.github.mizar107.zapegruntime.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.mizar107.zapegruntime.story.StoryCampaignDefinition;
import io.github.mizar107.zapegruntime.story.StoryCampaignJsonParser;
import io.github.mizar107.zapegruntime.story.StoryCampaignRegistry;
import io.github.mizar107.zapegruntime.story.StoryWorldData;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuestStoryAccessTest {

    private static final UUID PLAYER =
            UUID.fromString("00000000-0000-0000-0000-000000000311");

    @Test
    void absentStateResolvesOnlyTheCampaignEntryAtEpochZero() {
        StoryCampaignDefinition campaign = campaign();
        QuestStoryAccess.ExpectedAction expected =
                QuestStoryAccess.resolve(campaign, Optional.empty(), PLAYER, true).orElseThrow();
        assertEquals(QuestAction.ASHEN_SCRATCH, expected.action());
        assertEquals(0L, expected.recoveryEpoch());
        assertEquals(campaign.entryNodeId(), expected.nodeId());
    }

    @Test
    void persistedStateResolvesOnlyItsExactOwnedTriggerAndRecoveryEpoch() {
        StoryCampaignDefinition campaign = campaign();
        StoryWorldData.PlayerSnapshot ritualState = snapshot(campaign, PLAYER, "name_refused", 7L);
        QuestStoryAccess.ExpectedAction expected =
                QuestStoryAccess.resolve(campaign, Optional.of(ritualState), PLAYER, true).orElseThrow();
        assertEquals(QuestAction.NAME_REFUSAL, expected.action());
        assertEquals(7L, expected.recoveryEpoch());

        StoryWorldData.PlayerSnapshot sceneState =
                snapshot(campaign, PLAYER, "voice_without_air", 7L);
        assertTrue(QuestStoryAccess.resolve(campaign, Optional.of(sceneState), PLAYER, true).isEmpty());
    }

    @Test
    void mismatchedUuidOrDefinitionFailsClosed() {
        StoryCampaignDefinition campaign = campaign();
        StoryWorldData.PlayerSnapshot state = snapshot(campaign, PLAYER, "ninth_bell", 2L);
        UUID another = UUID.fromString("00000000-0000-0000-0000-000000000312");
        assertTrue(QuestStoryAccess.resolve(campaign, Optional.of(state), another, true).isEmpty());

        StoryWorldData.PlayerSnapshot badFingerprint = new StoryWorldData.PlayerSnapshot(
                PLAYER,
                campaign.id(),
                campaign.revision(),
                "0".repeat(64),
                2L,
                "ninth_bell",
                List.of(),
                0,
                0);
        assertTrue(QuestStoryAccess.resolve(campaign, Optional.of(badFingerprint), PLAYER, true).isEmpty());
    }

    @Test
    void readOnlyOrCorruptEmptyStateNeverFallsBackToCampaignEntry() {
        StoryCampaignDefinition campaign = campaign();
        assertTrue(QuestStoryAccess.resolve(campaign, Optional.empty(), PLAYER, false).isEmpty());
    }

    private static StoryWorldData.PlayerSnapshot snapshot(
            StoryCampaignDefinition campaign, UUID playerId, String nodeId, long epoch) {
        return new StoryWorldData.PlayerSnapshot(
                playerId,
                campaign.id(),
                campaign.revision(),
                campaign.fingerprint(),
                epoch,
                nodeId,
                List.of(),
                0,
                0);
    }

    private static StoryCampaignDefinition campaign() {
        InputStreamReader reader = new InputStreamReader(
                java.util.Objects.requireNonNull(QuestStoryAccessTest.class.getResourceAsStream(
                        "/data/zapeg_runtime/heraldor_story/heraldor.json")),
                StandardCharsets.UTF_8);
        try (reader) {
            return StoryCampaignJsonParser.parse(
                    StoryCampaignRegistry.HERALDOR_CAMPAIGN,
                    JsonParser.parseReader(reader));
        } catch (java.io.IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
