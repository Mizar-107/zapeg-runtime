package io.github.mizar107.zapegruntime.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mizar107.zapegruntime.story.StoryFactType;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class QuestActionTest {

    private static final UUID PLAYER =
            UUID.fromString("00000000-0000-0000-0000-000000000301");

    @Test
    void ownsExactlyTheTwelveAssignedCampaignPredicates() {
        assertEquals(12, QuestAction.values().length);
        assertEquals(
                7,
                Arrays.stream(QuestAction.values())
                        .filter(action -> action.factType() == StoryFactType.WORLD_DISCOVERY)
                        .count());
        assertEquals(
                5,
                Arrays.stream(QuestAction.values())
                        .filter(action -> action.factType() == StoryFactType.RITUAL_COMPLETED)
                        .count());
        Set<String> subjects = Arrays.stream(QuestAction.values())
                .map(action -> action.subject().toString())
                .collect(Collectors.toSet());
        assertEquals(12, subjects.size());
        assertTrue(subjects.contains("zapeg_runtime:ashen_scratch"));
        assertTrue(subjects.contains("zapeg_runtime:ninth_witness"));
        assertTrue(subjects.contains("zapeg_runtime:seal_03"));
        for (QuestAction action : QuestAction.values()) {
            assertEquals(action, QuestAction.forTrigger(action.trigger()).orElseThrow());
        }
    }

    @Test
    void factIdsAreStableAndBindPlayerEpochTypeAndSubject() {
        UUID first = QuestFactIds.forAction(PLAYER, 4L, QuestAction.SEAL_01);
        assertEquals(first, QuestFactIds.forAction(PLAYER, 4L, QuestAction.SEAL_01));
        assertNotEquals(first, QuestFactIds.forAction(PLAYER, 5L, QuestAction.SEAL_01));
        assertNotEquals(
                first,
                QuestFactIds.forAction(
                        UUID.fromString("00000000-0000-0000-0000-000000000302"),
                        4L,
                        QuestAction.SEAL_01));
        assertNotEquals(first, QuestFactIds.forAction(PLAYER, 4L, QuestAction.SEAL_02));
        assertEquals(3, first.version());
    }
}
