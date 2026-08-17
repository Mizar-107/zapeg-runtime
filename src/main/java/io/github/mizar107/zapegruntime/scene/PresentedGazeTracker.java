package io.github.mizar107.zapegruntime.scene;

/**
 * Credits gaze time only across consecutive frames that actually present a GUI
 * effect. Missing or widely separated presentation frames cannot consume a scene.
 */
public final class PresentedGazeTracker {

    private static final long MAX_PRESENTATION_GAP_NANOS = 250_000_000L;
    private static final long MAX_CREDIT_PER_FRAME_NANOS = 50_000_000L;

    private long lastPresentationNanos;
    private long accumulatedNanos;
    private boolean hasLastPresentation;

    public float present(
            boolean directGaze,
            long nowNanos,
            long requiredNanos) {
        if (requiredNanos <= 0L) {
            throw new IllegalArgumentException("Required presented gaze time must be positive");
        }
        if (!directGaze) {
            reset();
            return 0.0F;
        }

        if (hasLastPresentation) {
            long gap = nowNanos - lastPresentationNanos;
            if (gap < 0L || gap > MAX_PRESENTATION_GAP_NANOS) {
                accumulatedNanos = 0L;
            } else {
                accumulatedNanos = Math.min(
                        requiredNanos,
                        accumulatedNanos + Math.min(gap, MAX_CREDIT_PER_FRAME_NANOS));
            }
        }
        lastPresentationNanos = nowNanos;
        hasLastPresentation = true;
        return (float) Math.min(
                1.0D,
                (double) accumulatedNanos / (double) requiredNanos);
    }

    public void reset() {
        lastPresentationNanos = 0L;
        accumulatedNanos = 0L;
        hasLastPresentation = false;
    }
}
