package io.github.mizar107.zapegruntime.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScenePlacementPlanTest {

    @Test
    void planIsBoundedAndStaysNearTheTarget() {
        double[][] plan = ScenePlacement.candidatePlan();
        assertTrue(plan.length > 0);
        assertTrue(plan.length <= 32);
        for (double[] candidate : plan) {
            assertEquals(2, candidate.length);
            double angle = candidate[0];
            double distance = candidate[1];
            assertTrue(Double.isFinite(angle));
            assertTrue(Math.abs(angle) <= 180.0D);
            assertTrue(distance >= 16.0D && distance <= 40.0D);
        }
    }

    @Test
    void planIncludesBehindCandidatesForTurnAroundSightings() {
        double[][] plan = ScenePlacement.candidatePlan();
        long behind = java.util.Arrays.stream(plan)
                .filter(candidate -> Math.abs(candidate[0]) >= 150.0D)
                .count();
        assertTrue(behind >= 2, "expected at least two behind-the-target candidates");
    }

    @Test
    void returnedPlanIsADefensiveCopy() {
        double[][] first = ScenePlacement.candidatePlan();
        double[][] second = ScenePlacement.candidatePlan();
        assertNotSame(first, second);
        assertNotSame(first[0], second[0]);
        first[0][0] = 999.0D;
        assertTrue(ScenePlacement.candidatePlan()[0][0] != 999.0D);
    }
}
