package io.github.mizar107.zapegruntime.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class QuestSessionKeyTest {

    @Test
    void sameContextIsStableButRecoveryNodeActionAndDimensionResetIt() {
        QuestSessionKey original = key(QuestAction.NINTH_BELL, "ninth_bell", 4L, "minecraft:overworld");
        assertEquals(
                original,
                key(QuestAction.NINTH_BELL, "ninth_bell", 4L, "minecraft:overworld"));
        assertNotEquals(
                original,
                key(QuestAction.NINTH_BELL, "ninth_bell", 5L, "minecraft:overworld"));
        assertNotEquals(
                original,
                key(QuestAction.NINTH_BELL, "recovered_ninth_bell", 4L, "minecraft:overworld"));
        assertNotEquals(
                original,
                key(QuestAction.DROWNED_ROAD, "ninth_bell", 4L, "minecraft:overworld"));
        assertNotEquals(
                original,
                key(QuestAction.NINTH_BELL, "ninth_bell", 4L, "minecraft:the_nether"));
    }

    private static QuestSessionKey key(
            QuestAction action, String nodeId, long epoch, String dimension) {
        return QuestSessionKey.from(
                new QuestStoryAccess.ExpectedAction(action, epoch, nodeId), dimension);
    }
}
