package io.github.mizar107.zapegruntime.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mizar107.zapegruntime.scene.GazePull;
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
    void horizonPlacementSitsAtTheStageDistanceFacingTheTarget() {
        for (double azimuth = 0.0D; azimuth < 360.0D; azimuth += 27.0D) {
            ScenePlacement.Placement placement =
                    ScenePlacement.horizonPlacement(100.0D, 64.0D, -40.0D, azimuth, 220.0D);
            double dx = placement.anchor().x - 100.0D;
            double dz = placement.anchor().z - (-40.0D);
            assertEquals(220.0D, Math.hypot(dx, dz), 1.0E-6D);
            // Feet pinned to the target's own height: the fog hides the
            // implied ground line, and no chunk is ever touched.
            assertEquals(64.0D, placement.anchor().y);
            // The figure faces back toward the target: with look =
            // (-sin yaw, cos yaw), the facing vector must point at the player.
            double yawRadians = Math.toRadians(placement.yawDegrees());
            double facingX = -Math.sin(yawRadians);
            double facingZ = Math.cos(yawRadians);
            double dot = facingX * (-dx / 220.0D) + facingZ * (-dz / 220.0D);
            assertTrue(dot > 0.9999D, "horizon figure must face the target");
        }
    }

    @Test
    void echoPlanStaysInTheTargetsLocalVisibleRange() {
        double[][] plan = ScenePlacement.echoCandidatePlan();
        assertTrue(plan.length >= 4);
        assertTrue(plan.length <= 16);
        for (double[] candidate : plan) {
            assertEquals(2, candidate.length);
            double angle = candidate[0];
            double distance = candidate[1];
            assertTrue(Double.isFinite(angle));
            assertTrue(Math.abs(angle) <= GazePull.MAX_PULL_DEGREES);
            assertTrue(distance >= 6.0D && distance <= 14.0D);
        }
    }

    @Test
    void echoPlanIsCloserThanTheDistantSightingRing() {
        double echoMax = 0.0D;
        for (double[] candidate : ScenePlacement.echoCandidatePlan()) {
            echoMax = Math.max(echoMax, candidate[1]);
        }
        double distantMin = Double.POSITIVE_INFINITY;
        for (double[] candidate : ScenePlacement.candidatePlan()) {
            distantMin = Math.min(distantMin, candidate[1]);
        }
        assertTrue(echoMax < distantMin, "echo must spawn inside the distant ring");
    }

    @Test
    void echoPlanIsADefensiveCopy() {
        double[][] first = ScenePlacement.echoCandidatePlan();
        first[0][1] = 999.0D;
        assertTrue(ScenePlacement.echoCandidatePlan()[0][1] != 999.0D);
    }

    @Test
    void localYSearchPrefersThePlayersFloor() {
        int[] order = ScenePlacement.localYSearchOrder(64, 3);
        assertEquals(7, order.length);
        assertEquals(64, order[0]);
        assertEquals(63, order[1]);
        assertEquals(65, order[2]);
        assertEquals(61, order[5]);
        assertEquals(67, order[6]);
    }

    @Test
    void echoHintOrderUsesTheLocalPlan() {
        double[][] plan = ScenePlacement.echoCandidatePlan();
        // Player at origin looking along +X; hint sits at the nearest echo
        // candidate along that look (12°, 11 blocks).
        int[] order = ScenePlacement.hintOrder(0.0D, 0.0D, 0.0D, 11.0D, 0.0D, plan);
        assertEquals(plan.length, order.length);
        double firstX = Math.cos(Math.toRadians(plan[order[0]][0])) * plan[order[0]][1];
        double firstZ = Math.sin(Math.toRadians(plan[order[0]][0])) * plan[order[0]][1];
        assertTrue(Math.hypot(firstX - 11.0D, firstZ) < 4.0D);
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
