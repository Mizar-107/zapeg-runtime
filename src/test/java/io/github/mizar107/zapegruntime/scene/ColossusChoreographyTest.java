package io.github.mizar107.zapegruntime.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ColossusChoreographyTest {

    @Test
    void stagesComeStrictlyCloserAndStayColossal() {
        // Five approach stages, each nearer than the last, and even the
        // finale stays far enough that the figure can never be reached.
        double previous = Double.MAX_VALUE;
        for (int stage = 0; stage < ColossusChoreography.STAGE_COUNT; stage++) {
            double distance = ColossusChoreography.stageDistance(stage);
            assertTrue(distance >= 60.0D, "finale must remain a near-presence, not a melee");
            assertTrue(distance <= 320.0D, "horizon stage must stay inside the far plane");
            assertTrue(distance < previous, "every stage must come closer");
            previous = distance;
        }
        assertEquals(5, ColossusChoreography.STAGE_COUNT);
    }

    @Test
    void fogWrongnessIncreasesAsItNears() {
        // Far stages dissolve into the fog; near stages read through it.
        double previous = Double.MAX_VALUE;
        for (int stage = 0; stage < ColossusChoreography.STAGE_COUNT; stage++) {
            double strength = ColossusChoreography.fogStrength(stage);
            assertTrue(strength > 0.5D && strength < 1.0D);
            assertTrue(strength < previous, "fog strength must fall as the colossus nears");
            previous = strength;
        }
    }

    @Test
    void presenceAndShakeGrowButStayCapped() {
        double previousAlpha = 0.0D;
        double previousShake = 0.0D;
        for (int stage = 0; stage < ColossusChoreography.STAGE_COUNT; stage++) {
            double alpha = ColossusChoreography.baseAlpha(stage);
            assertTrue(alpha > previousAlpha, "silhouette must solidify as it nears");
            assertTrue(alpha <= 1.0D);
            previousAlpha = alpha;

            double shake = ColossusChoreography.shakeDegrees(stage);
            assertTrue(shake > previousShake, "footfalls must hit harder as it nears");
            assertTrue(
                    shake <= CameraUnease.MAX_HEAVY_SHAKE_DEGREES,
                    "stage shake must never exceed the heavy hard cap");
            previousShake = shake;
        }
    }

    @Test
    void footfallsAreSlowAndTheFinaleStopsEarly() {
        // A colossus never hurries: at most one footfall every two seconds.
        assertTrue(ColossusChoreography.STEP_INTERVAL_TICKS >= 40);
        for (int stage = 0; stage < ColossusChoreography.MAX_STAGE; stage++) {
            assertEquals(4, ColossusChoreography.stepsForStage(stage));
            assertEquals(-1, ColossusChoreography.vanishTick(stage));
            assertFalse(ColossusChoreography.isFinale(stage));
        }
        // The finale: two footfalls, a held watch, then it is simply gone.
        assertTrue(ColossusChoreography.isFinale(ColossusChoreography.MAX_STAGE));
        assertEquals(2, ColossusChoreography.stepsForStage(ColossusChoreography.MAX_STAGE));
        int vanish = ColossusChoreography.vanishTick(ColossusChoreography.MAX_STAGE);
        assertEquals(
                ColossusChoreography.stepTick(1) + ColossusChoreography.FINALE_WATCH_TICKS,
                vanish);
    }

    @Test
    void elapsedStepsFollowTheCadence() {
        int stage = 0;
        assertEquals(0, ColossusChoreography.elapsedSteps(stage, 0.0D));
        assertEquals(0, ColossusChoreography.elapsedSteps(stage, 17.9D));
        assertEquals(1, ColossusChoreography.elapsedSteps(stage, 18.0D));
        assertEquals(1, ColossusChoreography.elapsedSteps(stage, 61.9D));
        assertEquals(2, ColossusChoreography.elapsedSteps(stage, 62.0D));
        assertEquals(4, ColossusChoreography.elapsedSteps(stage, 10_000.0D));
        assertEquals(2, ColossusChoreography.elapsedSteps(
                ColossusChoreography.MAX_STAGE, 10_000.0D));
        assertEquals(0, ColossusChoreography.elapsedSteps(stage, Double.NaN));
    }

    @Test
    void advanceIsBoundedAndMonotonic() {
        for (int stage = 0; stage < ColossusChoreography.STAGE_COUNT; stage++) {
            double previous = -1.0D;
            for (int steps = 0; steps <= 6; steps++) {
                double advance = ColossusChoreography.advanceBlocks(stage, steps);
                assertTrue(advance >= previous, "advance must never retreat");
                assertTrue(
                        advance <= ColossusChoreography.MAX_TOTAL_ADVANCE_BLOCKS,
                        "a single scene never closes much distance");
                previous = advance;
            }
            // The approach saturates at the stage's step count.
            assertEquals(
                    ColossusChoreography.advanceBlocks(
                            stage, ColossusChoreography.stepsForStage(stage)),
                    ColossusChoreography.advanceBlocks(stage, 99));
        }
    }

    @Test
    void stepRockAlternatesAndSettles() {
        // Right after a footfall the rock is real; before the first step
        // there is none.
        double atStep = Math.abs(ColossusChoreography.stepRockDegrees(0, 19.0D, 42L));
        assertTrue(atStep > 0.01D);
        assertEquals(0.0D, ColossusChoreography.stepRockDegrees(0, 5.0D, 42L));
        // Consecutive steps lean to opposite sides.
        double first = ColossusChoreography.stepRockDegrees(0, 19.0D, 42L);
        double second = ColossusChoreography.stepRockDegrees(0, 63.0D, 42L);
        assertTrue(Math.signum(first) != Math.signum(second), "steps must alternate sides");
        // The sway stays bounded to about a degree at every age...
        for (double age = 0.0D; age <= 320.0D; age += 0.5D) {
            assertTrue(
                    Math.abs(ColossusChoreography.stepRockDegrees(4, age, 42L)) <= 1.100001D,
                    "rock must stay near a degree");
        }
        // ...and once the stage's last footfall is a few seconds past, the
        // figure stands still again.
        int lastStep = ColossusChoreography.stepTick(ColossusChoreography.stepsForStage(0) - 1);
        double settled = Math.abs(ColossusChoreography.stepRockDegrees(0, lastStep + 64.0D, 42L));
        assertTrue(settled < 0.05D, "the rock must settle after the last footfall");
    }

    @Test
    void foggedColorMixesTowardTheFog() {
        float[] fog = {0.6F, 0.7F, 0.9F, 1.0F};
        double[] clear = ColossusChoreography.foggedColor(0.01, 0.02, 0.03, fog, 0.0D);
        assertEquals(0.01, clear[0], 1.0E-9D);
        assertEquals(0.02, clear[1], 1.0E-9D);
        double[] drowned = ColossusChoreography.foggedColor(0.01, 0.02, 0.03, fog, 1.0D);
        assertEquals(0.6, drowned[0], 1.0E-6D);
        assertEquals(0.9, drowned[2], 1.0E-6D);
        double[] half = ColossusChoreography.foggedColor(0.0, 0.0, 0.0, fog, 0.5D);
        assertEquals(0.3, half[0], 1.0E-6D);
        // Out-of-range factors clamp instead of extrapolating.
        double[] clamped = ColossusChoreography.foggedColor(0.0, 0.0, 0.0, fog, 4.0D);
        assertEquals(0.6, clamped[0], 1.0E-6D);
    }

    @Test
    void eyesSitOnTheFaceSlightlyTooFarApart() {
        // The head box spans x -7.5..7.5, y 82..96, face at z -7.5.
        double halfHead = 7.5D;
        double outerEdge = ColossusChoreography.EYE_HALF_SPACING
                + ColossusChoreography.EYE_WIDTH * 0.5D;
        assertTrue(outerEdge < halfHead, "eyes must stay on the face");
        double spacingRatio = ColossusChoreography.EYE_HALF_SPACING / halfHead;
        assertTrue(spacingRatio > 0.35D, "the wrongness: eyes wider than any face");
        assertTrue(spacingRatio < 0.60D, "but still plausibly a face");
        assertTrue(ColossusChoreography.EYE_CENTER_Y > 82.0D
                && ColossusChoreography.EYE_CENTER_Y < 96.0D);
        assertTrue(ColossusChoreography.EYE_FACE_Z < 0.0D, "the face is the -z side");
        // Large enough to read at the horizon stage: at 280 blocks a block
        // is already only a few pixels, so sub-block eyes would vanish.
        assertTrue(ColossusChoreography.EYE_WIDTH >= 2.0D);
    }

    @Test
    void onlyTheFinaleNarrowsItsEyes() {
        for (int stage = 0; stage < ColossusChoreography.MAX_STAGE; stage++) {
            assertEquals(1.0D, ColossusChoreography.eyeNarrow(stage, 0.0D));
            assertEquals(1.0D, ColossusChoreography.eyeNarrow(stage, 500.0D));
        }
        int finale = ColossusChoreography.MAX_STAGE;
        double watchStart = ColossusChoreography.stepTick(
                ColossusChoreography.stepsForStage(finale) - 1);
        double vanish = ColossusChoreography.vanishTick(finale);
        assertEquals(1.0D, ColossusChoreography.eyeNarrow(finale, watchStart));
        double previous = 2.0D;
        for (double age = watchStart; age <= vanish; age += 1.0D) {
            double narrow = ColossusChoreography.eyeNarrow(finale, age);
            assertTrue(narrow <= previous, "the watch only narrows, never widens");
            assertTrue(narrow >= ColossusChoreography.EYE_MIN_NARROW - 1.0E-9D);
            previous = narrow;
        }
        assertEquals(
                ColossusChoreography.EYE_MIN_NARROW,
                ColossusChoreography.eyeNarrow(finale, vanish),
                1.0E-9D);
    }

    @Test
    void outOfRangeStagesClampIntoTheTable() {
        assertEquals(0, ColossusChoreography.clampStage(-3));
        assertEquals(
                ColossusChoreography.MAX_STAGE,
                ColossusChoreography.clampStage(ColossusChoreography.MAX_STAGE + 5));
        assertEquals(
                ColossusChoreography.stageDistance(0),
                ColossusChoreography.stageDistance(-1));
        assertEquals(
                ColossusChoreography.stageDistance(ColossusChoreography.MAX_STAGE),
                ColossusChoreography.stageDistance(99));
    }
}
