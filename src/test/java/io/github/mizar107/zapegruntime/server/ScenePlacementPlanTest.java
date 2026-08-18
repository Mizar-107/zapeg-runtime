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

    @Test
    void hintOrderPrefersCandidatesNearestTheRememberedPlace() {
        // Player at origin looking along +X; the hint sits at (24, 0).
        int[] order = ScenePlacement.hintOrder(0.0D, 0.0D, 0.0D, 24.0D, 0.0D);
        double[][] plan = ScenePlacement.candidatePlan();
        assertEquals(plan.length, order.length);

        double previousDistance = -1.0D;
        for (int index : order) {
            double angle = Math.toRadians(plan[index][0]);
            double anchorX = Math.cos(angle) * plan[index][1];
            double anchorZ = Math.sin(angle) * plan[index][1];
            double distance = Math.hypot(anchorX - 24.0D, anchorZ);
            assertTrue(
                    distance >= previousDistance - 1.0E-9D,
                    "hint order must be sorted ascending by hint distance");
            previousDistance = distance;
        }
    }

    @Test
    void hintOrderIsAPermutationForAnyLookDirection() {
        for (double baseAngle = -Math.PI; baseAngle < Math.PI; baseAngle += 0.37D) {
            int[] order = ScenePlacement.hintOrder(baseAngle, 10.0D, -5.0D, 12.0D, 3.0D);
            boolean[] seen = new boolean[order.length];
            for (int index : order) {
                assertTrue(index >= 0 && index < order.length);
                assertTrue(!seen[index], "hint order must not repeat candidates");
                seen[index] = true;
            }
        }
    }
}
