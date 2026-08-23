package io.github.mizar107.zapegruntime.servant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ServantSpawnPolicyTest {

    @Test
    void acceptsOnlyACompleteSafeCandidate() {
        ServantSpawnPolicy.CandidateFacts safe = facts(true, false, true, true, true, true, true, true);
        assertTrue(ServantSpawnPolicy.isSafe(safe));

        assertFalse(ServantSpawnPolicy.isSafe(facts(false, false, true, true, true, true, true, true)));
        assertFalse(ServantSpawnPolicy.isSafe(facts(true, true, true, true, true, true, true, true)));
        assertFalse(ServantSpawnPolicy.isSafe(facts(true, false, false, true, true, true, true, true)));
        assertFalse(ServantSpawnPolicy.isSafe(facts(true, false, true, false, true, true, true, true)));
        assertFalse(ServantSpawnPolicy.isSafe(facts(true, false, true, true, false, true, true, true)));
        assertFalse(ServantSpawnPolicy.isSafe(facts(true, false, true, true, true, false, true, true)));
        assertFalse(ServantSpawnPolicy.isSafe(facts(true, false, true, true, true, true, false, true)));
        assertFalse(ServantSpawnPolicy.isSafe(facts(true, false, true, true, true, true, true, false)));
    }

    private static ServantSpawnPolicy.CandidateFacts facts(
            boolean floorSturdy,
            boolean floorHazardous,
            boolean threeBlockClearance,
            boolean noFluid,
            boolean insideWorldBorder,
            boolean noCollision,
            boolean insideBuildHeight,
            boolean chunkAlreadyLoaded) {
        return new ServantSpawnPolicy.CandidateFacts(
                floorSturdy,
                floorHazardous,
                threeBlockClearance,
                noFluid,
                insideWorldBorder,
                noCollision,
                insideBuildHeight,
                chunkAlreadyLoaded);
    }
}
