package io.github.mizar107.zapegruntime.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PresentedGazeTrackerTest {

    private static final long FRAME_NANOS = 50_000_000L;
    private static final long REQUIRED_NANOS = 300_000_000L;

    @Test
    void consecutivePresentedFramesAccumulateToCompletion() {
        PresentedGazeTracker tracker = new PresentedGazeTracker();
        long now = 1_000_000_000L;
        assertEquals(0.0F, tracker.present(true, now, REQUIRED_NANOS));
        for (int frame = 0; frame < 6; frame++) {
            now += FRAME_NANOS;
            tracker.present(true, now, REQUIRED_NANOS);
        }
        assertEquals(1.0F, tracker.present(true, now, REQUIRED_NANOS));
    }

    @Test
    void missingPresentationOrIndirectGazeResetsProgress() {
        PresentedGazeTracker tracker = new PresentedGazeTracker();
        long now = 1_000_000_000L;
        tracker.present(true, now, REQUIRED_NANOS);
        assertTrue(tracker.present(true, now + FRAME_NANOS, REQUIRED_NANOS) > 0.0F);

        now += 400_000_000L;
        assertEquals(0.0F, tracker.present(true, now, REQUIRED_NANOS));
        assertEquals(0.0F, tracker.present(false, now + FRAME_NANOS, REQUIRED_NANOS));
        assertEquals(0.0F, tracker.present(true, now + FRAME_NANOS * 2, REQUIRED_NANOS));
    }

    @Test
    void acceptsArbitraryNanoTimeOriginButRejectsInvalidRequirement() {
        PresentedGazeTracker tracker = new PresentedGazeTracker();
        assertEquals(0.0F, tracker.present(true, -FRAME_NANOS, REQUIRED_NANOS));
        assertTrue(tracker.present(true, 0L, REQUIRED_NANOS) > 0.0F);
        assertThrows(
                IllegalArgumentException.class,
                () -> tracker.present(true, 1L, 0L));
    }
}
