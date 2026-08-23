package io.github.mizar107.zapegruntime.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class StoryServiceClassificationTest {

    @Test
    void expectedBarrierSuccessIsExplicitlyApplied() {
        StoryService.SubmissionResult result = classify(StoryWorldData.ApplyStatus.ADVANCED);
        assertEquals(StoryService.SubmissionStatus.APPLIED, result.status());
        assertTrue(result.application().isPresent());
    }

    @Test
    void reconciliationFailuresRemainRetryableOrDiagnosableAtTopLevel() {
        assertEquals(
                StoryService.SubmissionStatus.STATE_NOT_READY,
                classify(StoryWorldData.ApplyStatus.INVALID_STATE).status());
        assertEquals(
                StoryService.SubmissionStatus.CAPACITY_EXHAUSTED,
                classify(StoryWorldData.ApplyStatus.FACT_CAPACITY_EXHAUSTED).status());
        assertEquals(
                StoryService.SubmissionStatus.FACT_ID_CONFLICT,
                classify(StoryWorldData.ApplyStatus.FACT_ID_CONFLICT).status());
        assertEquals(
                StoryService.SubmissionStatus.ALREADY_PROCESSED,
                classify(StoryWorldData.ApplyStatus.DUPLICATE).status());
    }

    private static StoryService.SubmissionResult classify(
            StoryWorldData.ApplyStatus status) {
        return StoryService.classifyExpected(new StoryWorldData.ApplyResult(
                status, null, null, "test", Optional.empty()));
    }
}
