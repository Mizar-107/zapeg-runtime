package io.github.mizar107.zapegruntime.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class LoadedSceneQueriesTest {

    @Test
    void collisionSpanMatchesTheRealPaddedBlockQueryFootprint() {
        assertEquals(
                new LoadedSceneQueries.ChunkSpan(0, 0),
                LoadedSceneQueries.collisionChunkSpan(7.65D, 8.35D));
        assertEquals(
                new LoadedSceneQueries.ChunkSpan(0, 1),
                LoadedSceneQueries.collisionChunkSpan(15.65D, 15.95D));
        assertEquals(
                new LoadedSceneQueries.ChunkSpan(-1, 0),
                LoadedSceneQueries.collisionChunkSpan(-0.35D, 0.35D));
    }

    @Test
    void collisionRejectsAnUnloadedChunkAdjacentToTheBodyBoundary() {
        AABB nearBoundary = new AABB(
                15.65D, 64.0D, 7.65D,
                15.95D, 65.9D, 8.35D);
        Set<Long> onlyOrigin = Set.of(key(0, 0));
        assertFalse(LoadedSceneQueries.allCollisionChunksLoaded(
                nearBoundary, (x, z) -> onlyOrigin.contains(key(x, z))));

        Set<Long> complete = Set.of(key(0, 0), key(1, 0));
        assertTrue(LoadedSceneQueries.allCollisionChunksLoaded(
                nearBoundary, (x, z) -> complete.contains(key(x, z))));
    }

    @Test
    void collisionPreflightsEveryChunkInBothAabbAxes() {
        AABB corner = new AABB(
                15.65D, 64.0D, 15.65D,
                15.95D, 65.9D, 15.95D);
        Set<Long> allFour = Set.of(
                key(0, 0), key(0, 1), key(1, 0), key(1, 1));
        assertTrue(LoadedSceneQueries.allCollisionChunksLoaded(
                corner, (x, z) -> allFour.contains(key(x, z))));
        assertFalse(LoadedSceneQueries.allCollisionChunksLoaded(
                corner, (x, z) -> allFour.contains(key(x, z)) && !(x == 1 && z == 1)));
    }

    @Test
    void rayRejectsAnUnloadedIntermediateChunkBeforeQueryingTheWorld() {
        Vec3 start = new Vec3(1.0D, 65.0D, 8.0D);
        Vec3 end = new Vec3(47.0D, 65.0D, 8.0D);
        Set<Long> endpointsOnly = Set.of(key(0, 0), key(2, 0));
        assertFalse(LoadedSceneQueries.allRayChunksLoaded(
                start, end, (x, z) -> endpointsOnly.contains(key(x, z))));

        Set<Long> complete = Set.of(key(0, 0), key(1, 0), key(2, 0));
        assertTrue(LoadedSceneQueries.allRayChunksLoaded(
                start, end, (x, z) -> complete.contains(key(x, z))));
    }

    @Test
    void diagonalRayConservativelyPreflightsCornerAdjacentChunks() {
        Vec3 start = new Vec3(1.0D, 65.0D, 1.0D);
        Vec3 end = new Vec3(47.0D, 65.0D, 47.0D);
        Set<Long> visited = new HashSet<>();
        assertTrue(LoadedSceneQueries.allRayChunksLoaded(
                start,
                end,
                (x, z) -> {
                    visited.add(key(x, z));
                    return true;
                }));
        for (long expected : Set.of(
                key(0, 0), key(1, 0), key(0, 1), key(1, 1),
                key(2, 1), key(1, 2), key(2, 2))) {
            assertTrue(visited.contains(expected), "missing traversed/corner chunk " + expected);
        }
    }

    @Test
    void corruptOrUnboundedQueryFootprintsFailClosed() {
        assertFalse(LoadedSceneQueries.allRayChunksLoaded(
                new Vec3(Double.NaN, 0.0D, 0.0D),
                Vec3.ZERO,
                (x, z) -> true));
        assertFalse(LoadedSceneQueries.allRayChunksLoaded(
                Vec3.ZERO,
                new Vec3(16.0D * LoadedSceneQueries.MAX_PREFLIGHT_CHUNKS, 0.0D, 0.0D),
                (x, z) -> true));
        assertFalse(LoadedSceneQueries.allCollisionChunksLoaded(
                new AABB(0.0D, 0.0D, 0.0D, 16.0D * 300.0D, 1.0D, 1.0D),
                (x, z) -> true));
    }

    private static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }
}
