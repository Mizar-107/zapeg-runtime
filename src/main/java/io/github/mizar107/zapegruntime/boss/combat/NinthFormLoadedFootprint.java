package io.github.mizar107.zapegruntime.boss.combat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/** Bounded read-only chunk-residency preflight for the complete boss footprint. */
public final class NinthFormLoadedFootprint {

    public static final int MAX_FOOTPRINT_CHUNKS = 16;

    private NinthFormLoadedFootprint() {}

    public static boolean fullyLoaded(ServerLevel level, AABB bounds) {
        ChunkWindow window = chunkWindow(bounds);
        if (window == null || window.count() > MAX_FOOTPRINT_CHUNKS) {
            return false;
        }
        for (int chunkX = window.minChunkX(); chunkX <= window.maxChunkX(); chunkX++) {
            for (int chunkZ = window.minChunkZ(); chunkZ <= window.maxChunkZ(); chunkZ++) {
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    static ChunkWindow chunkWindow(AABB bounds) {
        if (bounds == null
                || !Double.isFinite(bounds.minX)
                || !Double.isFinite(bounds.minY)
                || !Double.isFinite(bounds.minZ)
                || !Double.isFinite(bounds.maxX)
                || !Double.isFinite(bounds.maxY)
                || !Double.isFinite(bounds.maxZ)
                || bounds.maxX <= bounds.minX
                || bounds.maxY <= bounds.minY
                || bounds.maxZ <= bounds.minZ) {
            return null;
        }
        int minChunkX = Mth.floor(bounds.minX) >> 4;
        int maxChunkX = Mth.floor(Math.nextDown(bounds.maxX)) >> 4;
        int minChunkZ = Mth.floor(bounds.minZ) >> 4;
        int maxChunkZ = Mth.floor(Math.nextDown(bounds.maxZ)) >> 4;
        long width = (long) maxChunkX - minChunkX + 1L;
        long depth = (long) maxChunkZ - minChunkZ + 1L;
        if (width <= 0L || depth <= 0L || width > Long.MAX_VALUE / depth) {
            return null;
        }
        return new ChunkWindow(minChunkX, maxChunkX, minChunkZ, maxChunkZ, width * depth);
    }

    record ChunkWindow(
            int minChunkX,
            int maxChunkX,
            int minChunkZ,
            int maxChunkZ,
            long count) {}
}
