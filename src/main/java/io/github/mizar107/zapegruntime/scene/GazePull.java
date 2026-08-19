package io.github.mizar107.zapegruntime.scene;

/**
 * The forced gaze: while a scene's pull window is open, the rendered camera
 * is dragged toward the figure's glowing eyes. The player's mouse still
 * moves the real rotation — the pull is a render-layer offset that tracks
 * the eyes one-for-one up to a hard cap, so fighting it costs constant
 * motion and the moment the player stops, the camera completes its slow
 * turn. The grip eases in, never exceeds a bounded rate (dread, not
 * whiplash), and eases back out, so release never leaves residual rotation
 * or a snap.
 *
 * <p>Pure math: the client manager owns the per-frame offset state and the
 * camera sampling; every policy and envelope decision lives here so it is
 * unit-testable without a level, a mouse or a camera.
 */
public final class GazePull {

    /** The grip's reach: the rendered view never sits further than this
     *  from the player's real look direction. */
    public static final float MAX_PULL_DEGREES = 34.0F;
    /** Fastest the offset may swing the rendered view, per axis per tick. */
    public static final float MAX_YAW_RATE_PER_TICK = 1.6F;
    public static final float MAX_PITCH_RATE_PER_TICK = 1.0F;
    /** Release after the window closes is a little quicker, still smooth. */
    public static final float RELEASE_RATE_PER_TICK = 2.4F;
    /** The pull never drags the view toward the poles. */
    public static final float PITCH_LIMIT_DEGREES = 72.0F;
    public static final int EASE_IN_TICKS = 22;
    public static final int EASE_OUT_TICKS = 16;
    /** A resolved figure lets go: the grip fades over the last stretch of
     *  gaze progress. */
    public static final double GAZE_RELEASE_POINT = 0.85D;

    /** Humanoid eye height used as the pull target, in blocks above the
     *  anchor. */
    public static final double HUMANOID_EYE_HEIGHT = 1.80D;

    private GazePull() {}

    /**
     * The body-tick window [start, end] during which the pull grips, packed
     * as {@code start << 16 | end}; -1 when the profile never pulls. Only
     * two beats are wired: the echo holds your look until its own gaze
     * resolution answers it, and the colossus finale makes you watch the
     * watch.
     */
    public static long pullWindowTicks(SceneProfile profile, int stage, int bodyTicks) {
        if (profile == SceneProfile.ECHO_01) {
            int start = 16;
            int end = Math.max(start + EASE_IN_TICKS + 10, bodyTicks - 10);
            return pack(start, end);
        }
        if (profile == SceneProfile.COLOSSUS_01 && ColossusChoreography.isFinale(stage)) {
            int start = ColossusChoreography.stepTick(
                    ColossusChoreography.stepsForStage(stage) - 1);
            int end = ColossusChoreography.vanishTick(stage);
            if (end > start) {
                return pack(start, end);
            }
        }
        return -1L;
    }

    public static int windowStart(long packedWindow) {
        return (int) (packedWindow >>> 16);
    }

    public static int windowEnd(long packedWindow) {
        return (int) (packedWindow & 0xFFFFL);
    }

    /**
     * Grip strength 0..1 at this body age: smoothstep in over
     * {@link #EASE_IN_TICKS}, hold, smoothstep out over
     * {@link #EASE_OUT_TICKS}, and an early release as a gaze-resolved
     * figure answers the forced look.
     */
    public static double response(
            double ageTicks, long packedWindow, double gazeProgress) {
        if (packedWindow < 0L || !Double.isFinite(ageTicks)) {
            return 0.0D;
        }
        double start = windowStart(packedWindow);
        double end = windowEnd(packedWindow);
        double envelope = SceneMath.smoothstep(start, start + EASE_IN_TICKS, ageTicks)
                * (1.0D - SceneMath.smoothstep(end - EASE_OUT_TICKS, end, ageTicks));
        double gazeRelease = 1.0D
                - SceneMath.smoothstep(0.0D, GAZE_RELEASE_POINT, gazeProgress);
        return envelope * gazeRelease;
    }

    /** Eye height above the anchor for the pull target, in blocks. */
    public static double eyeHeightBlocks(SceneProfile profile) {
        return profile == SceneProfile.COLOSSUS_01
                ? ColossusChoreography.EYE_CENTER_Y
                : HUMANOID_EYE_HEIGHT;
    }

    /**
     * The offset the pull wants this frame: the shortest-arc delta from the
     * camera's real look to the eyes, capped by the grip's current reach.
     * Pitch is additionally kept away from the poles.
     */
    public static float desiredOffset(
            float targetDegrees, float currentDegrees, double response, boolean pitch) {
        double delta = wrapDegrees(targetDegrees - currentDegrees);
        double cap = MAX_PULL_DEGREES * (pitch ? 0.8D : 1.0D) * response;
        return (float) Math.max(-cap, Math.min(cap, delta));
    }

    /**
     * One rate-limited step of the held offset toward its desired value.
     * Never overshoots, never moves faster than the per-axis rate, and with
     * zero response walks smoothly to exactly zero — the clean release.
     *
     * @param dtTicks elapsed ticks since the previous frame, clamped by the
     *     caller to a sane frame-time range
     */
    public static float stepOffset(
            float currentOffset, float desiredOffset, double response, double dtTicks, boolean pitch) {
        double rate = (response > 0.0D
                        ? (pitch ? MAX_PITCH_RATE_PER_TICK : MAX_YAW_RATE_PER_TICK)
                        : RELEASE_RATE_PER_TICK)
                * Math.max(0.0D, Math.min(5.0D, dtTicks));
        double delta = desiredOffset - currentOffset;
        if (Math.abs(delta) <= rate) {
            return desiredOffset;
        }
        return (float) (currentOffset + Math.signum(delta) * rate);
    }

    /** Combined cap for the unease plus pull sum, per axis. */
    public static float combinedCap(boolean pitch) {
        return pitch
                ? MAX_PULL_DEGREES * 0.8F + CameraUnease.MAX_JITTER_PITCH_DEGREES
                        + CameraUnease.MAX_SHAKE_DEGREES
                : MAX_PULL_DEGREES + CameraUnease.MAX_JITTER_YAW_DEGREES
                        + CameraUnease.MAX_SHAKE_DEGREES;
    }

    public static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0D;
        if (wrapped >= 180.0D) {
            wrapped -= 360.0D;
        } else if (wrapped < -180.0D) {
            wrapped += 360.0D;
        }
        return wrapped;
    }

    private static long pack(int start, int end) {
        return ((long) start << 16) | (long) end;
    }
}
