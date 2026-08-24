package io.github.mizar107.zapegruntime.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QuestBellPolicyTest {

    @Test
    void ninthDeliberateAcceptedRingCompletesInsideWindow() {
        QuestBellPolicy.Progress progress = null;
        for (int strike = 0; strike < 8; strike++) {
            progress = QuestBellPolicy.recordAcceptedRing(progress, "minecraft:overworld", 42L, strike * 6L);
            assertFalse(QuestBellPolicy.complete(progress));
        }
        progress = QuestBellPolicy.recordAcceptedRing(progress, "minecraft:overworld", 42L, 48L);
        assertTrue(QuestBellPolicy.complete(progress));
        assertEquals(9, progress.strikes());
    }

    @Test
    void rapidRingsActuallyRingButDoNotCountAsDeliberateStrikes() {
        QuestBellPolicy.Progress first =
                QuestBellPolicy.recordAcceptedRing(null, "minecraft:overworld", 42L, 100L);
        QuestBellPolicy.Progress rapid =
                QuestBellPolicy.recordAcceptedRing(first, "minecraft:overworld", 42L, 105L);
        assertSame(first, rapid);
        assertEquals(1, rapid.strikes());
    }

    @Test
    void bellDimensionAndWindowChangesResetTheSequence() {
        QuestBellPolicy.Progress first =
                QuestBellPolicy.recordAcceptedRing(null, "minecraft:overworld", 42L, 0L);
        QuestBellPolicy.Progress second =
                QuestBellPolicy.recordAcceptedRing(first, "minecraft:overworld", 42L, 6L);
        assertEquals(2, second.strikes());

        QuestBellPolicy.Progress otherBell =
                QuestBellPolicy.recordAcceptedRing(second, "minecraft:overworld", 43L, 12L);
        assertEquals(1, otherBell.strikes());
        QuestBellPolicy.Progress otherDimension =
                QuestBellPolicy.recordAcceptedRing(otherBell, "minecraft:the_nether", 43L, 18L);
        assertEquals(1, otherDimension.strikes());
        QuestBellPolicy.Progress expired = QuestBellPolicy.recordAcceptedRing(
                otherDimension,
                "minecraft:the_nether",
                43L,
                otherDimension.firstTick() + QuestBellPolicy.WINDOW_TICKS + 1L);
        assertEquals(1, expired.strikes());
    }
}
