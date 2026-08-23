package io.github.mizar107.zapegruntime.servant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class ServantLoadedChunksTest {

    @Test
    void exactChunkEdgesDoNotProbeAnUnoccupiedNeighbor() {
        ServantLoadedChunks.ChunkWindow one = ServantLoadedChunks.chunkWindow(
                new AABB(0.0D, 0.0D, 0.0D, 16.0D, 3.0D, 16.0D));
        assertEquals(new ServantLoadedChunks.ChunkWindow(0, 0, 0, 0, 1L), one);

        ServantLoadedChunks.ChunkWindow four = ServantLoadedChunks.chunkWindow(
                new AABB(15.5D, 0.0D, 15.5D, 16.5D, 3.0D, 16.5D));
        assertEquals(new ServantLoadedChunks.ChunkWindow(0, 1, 0, 1, 4L), four);
    }

    @Test
    void malformedOrEmptyAreasFailClosed() {
        assertNull(ServantLoadedChunks.chunkWindow(
                new AABB(1.0D, 0.0D, 1.0D, 1.0D, 2.0D, 2.0D)));
        assertFalse(ServantLoadedChunks.finite(new AABB(
                Double.NEGATIVE_INFINITY, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D)));
    }

    @Test
    void pathfindingWindowCoversVanillasEntireFollowRangePlusInitialPadding() {
        ServantLoadedChunks.ChunkWindow window = ServantLoadedChunks.pathfindingWindow(
                BlockPos.ZERO,
                ServantLoadedChunks.MAX_PATHFINDING_FOLLOW_RANGE);
        assertEquals(new ServantLoadedChunks.ChunkWindow(-4, 4, -4, 4, 81L), window);
        assertEquals(ServantLoadedChunks.MAX_PATHFINDING_CHUNKS, window.count());
        assertNull(ServantLoadedChunks.pathfindingWindow(BlockPos.ZERO, 48.001D));
        assertNull(ServantLoadedChunks.pathfindingWindow(BlockPos.ZERO, Double.NaN));
    }
}
