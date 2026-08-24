package io.github.mizar107.zapegruntime.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.mizar107.zapegruntime.story.StoryService;
import org.junit.jupiter.api.Test;

class QuestRitualPolicyTest {

    @Test
    void offeringIsConsumedExactlyOnceAfterAppliedThenNeverOnReplay() {
        int remaining = 6;
        int firstConsumption = QuestRitualPolicy.committedConsumption(
                remaining, 3, StoryService.SubmissionStatus.APPLIED);
        assertEquals(3, firstConsumption);
        remaining -= firstConsumption;

        int replayConsumption = QuestRitualPolicy.committedConsumption(
                remaining, 3, StoryService.SubmissionStatus.ALREADY_PROCESSED);
        assertEquals(0, replayConsumption);
        remaining -= replayConsumption;
        assertEquals(3, remaining);
    }

    @Test
    void everyFailureAndNotExpectedResultPreservesTheOffering() {
        for (StoryService.SubmissionStatus status : StoryService.SubmissionStatus.values()) {
            if (status == StoryService.SubmissionStatus.APPLIED) {
                continue;
            }
            assertEquals(
                    0,
                    QuestRitualPolicy.committedConsumption(1, 1, status),
                    status.name());
        }
    }
}
