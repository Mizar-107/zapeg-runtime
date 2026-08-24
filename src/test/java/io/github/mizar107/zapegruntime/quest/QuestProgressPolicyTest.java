package io.github.mizar107.zapegruntime.quest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QuestProgressPolicyTest {

    @Test
    void nightWindowHasExactVanillaBoundaries() {
        assertFalse(QuestProgressPolicy.isNight(12_999L));
        assertTrue(QuestProgressPolicy.isNight(13_000L));
        assertTrue(QuestProgressPolicy.isNight(22_999L));
        assertFalse(QuestProgressPolicy.isNight(23_000L));
        assertTrue(QuestProgressPolicy.isNight(37_000L));
    }

    @Test
    void backwardMotionMustOpposeTheLookVectorAndRejectTeleports() {
        assertTrue(QuestProgressPolicy.isBackwardStep(0, 0, -0.2, 0, 1, 0));
        assertFalse(QuestProgressPolicy.isBackwardStep(0, 0, 0.2, 0, 1, 0));
        assertFalse(QuestProgressPolicy.isBackwardStep(0, 0, 0, 0.2, 1, 0));
        assertFalse(QuestProgressPolicy.isBackwardStep(0, 0, -2.0, 0, 1, 0));
    }

    @Test
    void backwardAndDrownedThresholdsRequireBothTimeAndDistance() {
        QuestProgressPolicy.Progress backward = progress(40, 0.16D);
        assertTrue(QuestProgressPolicy.complete(QuestAction.BACKWARD_TRACKS, backward));
        assertFalse(QuestProgressPolicy.complete(
                QuestAction.BACKWARD_TRACKS, progress(39, 0.16D)));
        assertFalse(QuestProgressPolicy.complete(
                QuestAction.BACKWARD_TRACKS, progress(40, 0.10D)));

        QuestProgressPolicy.Progress drowned = progress(60, 0.14D);
        assertTrue(QuestProgressPolicy.complete(QuestAction.DROWNED_ROAD, drowned));
        assertFalse(QuestProgressPolicy.complete(
                QuestAction.DROWNED_ROAD, progress(59, 0.14D)));
    }

    @Test
    void leaningRequiresSixtyTicksAndBoundsCumulativeAndNetDrift() {
        QuestProgressPolicy.Progress still = progress(60, 0.0D);
        assertTrue(QuestProgressPolicy.complete(QuestAction.LEANING_HOUSE, still));
        assertFalse(QuestProgressPolicy.complete(
                QuestAction.LEANING_HOUSE, progress(59, 0.0D)));

        QuestProgressPolicy.Progress sliding = progress(60, 0.01D);
        assertTrue(sliding.pathDistance() > QuestProgressPolicy.LEAN_MAX_PATH_DRIFT);
        assertFalse(QuestProgressPolicy.complete(QuestAction.LEANING_HOUSE, sliding));

        QuestProgressPolicy.Progress netDrift = new QuestProgressPolicy.Progress(
                60, 0.30D, 0.0D, 0.0D, 0.30D, 0.0D);
        assertFalse(QuestProgressPolicy.complete(QuestAction.LEANING_HOUSE, netDrift));
    }

    @Test
    void underdoorUsesNetPassageAndWitnessUsesContinuousUseTime() {
        QuestProgressPolicy.Progress shortCrawl = progress(20, 0.15D);
        assertFalse(QuestProgressPolicy.complete(QuestAction.UNDERDOOR, shortCrawl));
        QuestProgressPolicy.Progress fullCrawl = progress(21, 0.15D);
        assertTrue(QuestProgressPolicy.complete(QuestAction.UNDERDOOR, fullCrawl));

        assertFalse(QuestProgressPolicy.complete(
                QuestAction.NINTH_WITNESS, progress(39, 0.0D)));
        assertTrue(QuestProgressPolicy.complete(
                QuestAction.NINTH_WITNESS, progress(40, 0.0D)));
    }

    @Test
    void teleportSizedSampleRestartsProgress() {
        QuestProgressPolicy.Progress first = QuestProgressPolicy.start(0.0D, 0.0D);
        QuestProgressPolicy.Progress reset = QuestProgressPolicy.advance(first, 10.0D, 0.0D);
        assertTrue(reset.ticks() == 1);
        assertTrue(reset.pathDistance() == 0.0D);
    }

    private static QuestProgressPolicy.Progress progress(int ticks, double step) {
        QuestProgressPolicy.Progress progress = QuestProgressPolicy.start(0.0D, 0.0D);
        for (int index = 1; index < ticks; index++) {
            progress = QuestProgressPolicy.advance(progress, index * step, 0.0D);
        }
        return progress;
    }
}
