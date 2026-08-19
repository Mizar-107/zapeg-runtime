package io.github.mizar107.zapegruntime.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GazePullTest {

    @Test
    void onlyTheEchoAndTheFinalePull() {
        int bodyTicks = 160;
        assertTrue(GazePull.pullWindowTicks(SceneProfile.ECHO_01, 0, bodyTicks) >= 0L);
        for (int stage = 0; stage < ColossusChoreography.MAX_STAGE; stage++) {
            assertEquals(
                    -1L,
                    GazePull.pullWindowTicks(SceneProfile.COLOSSUS_01, stage, 320),
                    "only the finale watch pulls");
        }
        long finale = GazePull.pullWindowTicks(
                SceneProfile.COLOSSUS_01, ColossusChoreography.MAX_STAGE, 320);
        assertTrue(finale >= 0L);
        assertEquals(
                ColossusChoreography.stepTick(1), GazePull.windowStart(finale));
        assertEquals(
                ColossusChoreography.vanishTick(ColossusChoreography.MAX_STAGE),
                GazePull.windowEnd(finale));
        for (SceneProfile profile : SceneProfile.values()) {
            if (profile != SceneProfile.ECHO_01 && profile != SceneProfile.COLOSSUS_01) {
                assertEquals(-1L, GazePull.pullWindowTicks(profile, 0, bodyTicks),
                        profile + " must never pull");
            }
        }
    }

    @Test
    void theGripEasesInHoldsAndEasesOut() {
        long window = GazePull.pullWindowTicks(SceneProfile.ECHO_01, 0, 160);
        int start = GazePull.windowStart(window);
        int end = GazePull.windowEnd(window);
        assertEquals(0.0D, GazePull.response(start - 1.0D, window, 0.0F));
        assertEquals(0.0D, GazePull.response(start, window, 0.0F));
        assertEquals(1.0D, GazePull.response(
                start + GazePull.EASE_IN_TICKS + 1.0D, window, 0.0F));
        assertEquals(1.0D, GazePull.response(end - GazePull.EASE_OUT_TICKS - 1.0D, window, 0.0F));
        assertEquals(0.0D, GazePull.response(end, window, 0.0F));
        double previous = -1.0D;
        for (double age = start; age <= end; age += 0.5D) {
            double response = GazePull.response(age, window, 0.0F);
            assertTrue(response >= 0.0D && response <= 1.0D);
            previous = response;
        }
        assertTrue(previous >= 0.0D);
        assertEquals(0.0D, GazePull.response(Double.NaN, window, 0.0F));
        assertEquals(0.0D, GazePull.response(50.0D, -1L, 0.0F));
    }

    @Test
    void aResolvedFigureLetsGoEarly() {
        long window = GazePull.pullWindowTicks(SceneProfile.ECHO_01, 0, 160);
        double mid = GazePull.windowStart(window) + GazePull.EASE_IN_TICKS + 5.0D;
        assertEquals(1.0D, GazePull.response(mid, window, 0.0F));
        assertTrue(GazePull.response(mid, window, 0.4F) < 1.0D);
        assertEquals(0.0D, GazePull.response(mid, window, 1.0F));
    }

    @Test
    void theDesiredOffsetIsCappedByTheGrip() {
        // Far off-target with a weak grip: the offset is capped, not instant.
        float desired = GazePull.desiredOffset(120.0F, 0.0F, 0.25D, false);
        assertEquals(GazePull.MAX_PULL_DEGREES * 0.25F, desired, 1.0E-4F);
        // Full grip reaches all the way, but never past the hard cap.
        float full = GazePull.desiredOffset(150.0F, 0.0F, 1.0D, false);
        assertEquals(GazePull.MAX_PULL_DEGREES, full, 1.0E-4F);
        // A target 200 degrees away wraps the shortest arc: the pull turns
        // the other way, capped at the same magnitude.
        float wrappedCap = GazePull.desiredOffset(200.0F, 0.0F, 1.0D, false);
        assertEquals(-GazePull.MAX_PULL_DEGREES, wrappedCap, 1.0E-4F);
        // Shortest arc wraps across the ±180 seam.
        float wrapped = GazePull.desiredOffset(-170.0F, 170.0F, 1.0D, false);
        assertEquals(20.0F, wrapped, 1.0E-4F);
        // Pitch reach is narrower than yaw reach.
        float pitch = GazePull.desiredOffset(90.0F, 0.0F, 1.0D, true);
        assertEquals(GazePull.MAX_PULL_DEGREES * 0.8F, pitch, 1.0E-4F);
    }

    @Test
    void thePullIsRateLimitedAndNeverOvershoots() {
        // One tick can never swing more than the per-axis rate.
        float step = GazePull.stepOffset(0.0F, 30.0F, 1.0D, 1.0D, false);
        assertEquals(GazePull.MAX_YAW_RATE_PER_TICK, step, 1.0E-4F);
        float pitchStep = GazePull.stepOffset(0.0F, 30.0F, 1.0D, 1.0D, true);
        assertEquals(GazePull.MAX_PITCH_RATE_PER_TICK, pitchStep, 1.0E-4F);
        // Convergence without overshoot, even with a huge frame gap.
        float offset = 0.0F;
        for (int frame = 0; frame < 200; frame++) {
            offset = GazePull.stepOffset(offset, 20.0F, 1.0D, 1.0D, false);
            assertTrue(offset <= 20.0F + 1.0E-4F, "must never overshoot");
        }
        assertEquals(20.0F, offset, 1.0E-4F);
        // A stale clock after a pause clamps to a bounded step, not a jump.
        float afterGap = GazePull.stepOffset(0.0F, 34.0F, 1.0D, 400.0D, false);
        assertTrue(afterGap <= GazePull.MAX_YAW_RATE_PER_TICK * 5.0F + 1.0E-4F);
    }

    @Test
    void releaseWalksSmoothlyToExactlyZero() {
        float offset = 22.0F;
        int frames = 0;
        while (offset != 0.0F && frames < 500) {
            float next = GazePull.stepOffset(offset, 0.0F, 0.0D, 1.0D, false);
            assertTrue(Math.abs(next) < Math.abs(offset), "release must only decay");
            offset = next;
            frames++;
        }
        assertEquals(0.0F, offset);
        assertTrue(frames > 5, "release is a glide, not a snap");
    }

    @Test
    void theSumCapLeavesRoomForUnease() {
        assertTrue(GazePull.combinedCap(false) > GazePull.MAX_PULL_DEGREES);
        assertTrue(GazePull.combinedCap(true) < GazePull.combinedCap(false));
        assertTrue(GazePull.combinedCap(false) < 40.0F,
                "even the combined drag stays a slow turn, never a whip");
    }

    @Test
    void eyeTargetsMatchTheFigures() {
        assertEquals(
                ColossusChoreography.EYE_CENTER_Y,
                GazePull.eyeHeightBlocks(SceneProfile.COLOSSUS_01));
        assertTrue(GazePull.eyeHeightBlocks(SceneProfile.ECHO_01) > 1.0D
                && GazePull.eyeHeightBlocks(SceneProfile.ECHO_01) < 2.2D);
    }
}
