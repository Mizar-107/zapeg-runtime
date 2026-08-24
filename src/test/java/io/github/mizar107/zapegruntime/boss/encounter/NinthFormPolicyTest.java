package io.github.mizar107.zapegruntime.boss.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mizar107.zapegruntime.boss.api.NinthFormPhase;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NinthFormPolicyTest {

    @Test
    void scalingIsMonotonicReadableAndHardBoundedAtEightPlayers() {
        double[] health = {1.00D, 1.45D, 1.85D, 2.20D, 2.50D, 2.50D, 2.50D, 2.50D};
        double[] damage = {1.00D, 1.08D, 1.15D, 1.21D, 1.27D, 1.27D, 1.27D, 1.27D};
        for (int participants = 1; participants <= 8; participants++) {
            NinthFormScalingPolicy.Scale scale =
                    NinthFormScalingPolicy.forParticipants(participants);
            assertEquals(health[participants - 1], scale.healthScale());
            assertEquals(damage[participants - 1], scale.damageScale());
        }
        assertThrows(IllegalArgumentException.class,
                () -> NinthFormScalingPolicy.forParticipants(0));
        assertThrows(IllegalArgumentException.class,
                () -> NinthFormScalingPolicy.forParticipants(9));
    }

    @Test
    void plannerRequiresEveryChunkInTheFortyEightBlockArena() {
        List<String> probes = new ArrayList<>();
        NinthFormArenaPolicy.PlanResult ready = NinthFormArenaPolicy.plan(
                "minecraft:overworld", 0, 64, 0, List.of(), (x, z) -> {
                    probes.add(x + ":" + z);
                    return true;
                });
        assertEquals(NinthFormArenaPolicy.Status.READY, ready.status());
        assertEquals(49, probes.size());
        assertTrue(probes.contains("-3:-3"));
        assertTrue(probes.contains("3:3"));

        NinthFormArenaPolicy.PlanResult missing = NinthFormArenaPolicy.plan(
                "minecraft:overworld", 0, 64, 0, List.of(), (x, z) -> x != 3 || z != 3);
        assertEquals(NinthFormArenaPolicy.Status.CHUNK_NOT_LOADED, missing.status());
    }

    @Test
    void suspendedAttemptOnlyResumesWhenTheTargetReturnsInsideItsArena() {
        assertTrue(NinthFormArenaPolicy.contains(0, 0, 0.5D, 0.5D));
        assertTrue(NinthFormArenaPolicy.contains(0, 0, 48.5D, 0.5D));
        assertFalse(NinthFormArenaPolicy.contains(0, 0, 48.500_1D, 0.5D));
        assertFalse(NinthFormArenaPolicy.contains(0, 0, Double.NaN, 0.5D));
    }

    @Test
    void plannerEnforcesFourLoadedBossesAndOneHundredTwentyEightBlockSeparation() {
        NinthFormArenaPolicy.OccupiedArena near = occupied(127, 0);
        assertEquals(
                NinthFormArenaPolicy.Status.TOO_CLOSE,
                NinthFormArenaPolicy.plan(
                        "minecraft:overworld", 0, 64, 0, List.of(near), (x, z) -> true)
                        .status());
        NinthFormArenaPolicy.OccupiedArena far = occupied(129, 0);
        assertEquals(
                NinthFormArenaPolicy.Status.READY,
                NinthFormArenaPolicy.plan(
                        "minecraft:overworld", 0, 64, 0, List.of(far), (x, z) -> true)
                        .status());
        assertEquals(
                NinthFormArenaPolicy.Status.LOADED_BOSS_CAPACITY,
                NinthFormArenaPolicy.plan(
                        "minecraft:overworld",
                        0,
                        64,
                        0,
                        List.of(
                                occupied(200, 0),
                                occupied(400, 0),
                                occupied(600, 0),
                                occupied(800, 0)),
                        (x, z) -> true)
                        .status());
    }

    @Test
    void exactBossPhaseSetHasNoHiddenDamagePhase() {
        assertEquals(
                List.of("PRELUDE", "FIRST", "INTERLUDE", "FINAL", "BANISHED"),
                java.util.Arrays.stream(NinthFormPhase.values()).map(Enum::name).toList());
        assertFalse(NinthFormPhase.FIRST.canAdvanceTo(NinthFormPhase.FINAL));
    }

    private static NinthFormArenaPolicy.OccupiedArena occupied(int x, int z) {
        return new NinthFormArenaPolicy.OccupiedArena("minecraft:overworld", x, z);
    }
}
