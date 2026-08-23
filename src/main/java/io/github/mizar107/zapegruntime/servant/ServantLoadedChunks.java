package io.github.mizar107.zapegruntime.servant;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.phys.AABB;

/** Read-only chunk-residency checks used before collision, path, and attack work. */
public final class ServantLoadedChunks {

    public static final int MAX_CORRIDOR_CHUNKS = 64;
    public static final int MAX_PATHFINDING_CHUNKS = 81;
    public static final int MAX_PATH_NODES = 2_048;
    public static final double MAX_PURSUIT_DISTANCE = 48.0D;
    public static final double MAX_PATHFINDING_FOLLOW_RANGE = 48.0D;
    public static final int VANILLA_INITIAL_PATH_REGION_PADDING = 16;

    private ServantLoadedChunks() {}

    public static boolean blockColumnLoaded(ServerLevel level, BlockPos feet) {
        return level.getChunkSource().hasChunk(feet.getX() >> 4, feet.getZ() >> 4)
                && level.getChunkSource().hasChunk(feet.below().getX() >> 4, feet.below().getZ() >> 4)
                && level.getChunkSource().hasChunk(feet.above(2).getX() >> 4, feet.above(2).getZ() >> 4);
    }

    /** Includes a one-block neighbor margin before any spawn block/shape read. */
    public static boolean spawnProbeLoaded(ServerLevel level, BlockPos feet) {
        return areaLoaded(level, new AABB(
                feet.getX() - 1.0D,
                feet.getY() - 1.0D,
                feet.getZ() - 1.0D,
                feet.getX() + 2.0D,
                feet.getY() + 3.0D,
                feet.getZ() + 2.0D));
    }

    public static boolean areaLoaded(ServerLevel level, AABB area) {
        return areaLoaded(level, area, MAX_CORRIDOR_CHUNKS);
    }

    private static boolean areaLoaded(ServerLevel level, AABB area, int maximumChunks) {
        ChunkWindow window = chunkWindow(area);
        if (window == null || window.count() > maximumChunks) {
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

    /**
     * Preflights the complete region vanilla 1.20.1 may inspect when
     * {@code MeleeAttackGoal.canUse()} calls
     * {@code PathNavigation.createPath(Entity, 0)}. That call contributes a
     * fixed 16-block region padding to the mob's FOLLOW_RANGE before
     * constructing {@code PathNavigationRegion}. The preflight happens before
     * the vanilla goal is entered, so its later region reads cannot request an
     * absent chunk.
     */
    public static boolean pathfindingFootprintLoaded(
            ServerLevel level,
            BlockPos servantPosition,
            double followRange) {
        ChunkWindow window = pathfindingWindow(servantPosition, followRange);
        if (window == null || window.count() > MAX_PATHFINDING_CHUNKS) {
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

    /** Every retained detour node is resident before navigation ticks it. */
    public static boolean pathNodesLoaded(ServerLevel level, Path path) {
        if (path == null) {
            return true;
        }
        int nodeCount = path.getNodeCount();
        if (nodeCount < 0 || nodeCount > MAX_PATH_NODES) {
            return false;
        }
        for (int index = Math.max(0, path.getNextNodeIndex()); index < nodeCount; index++) {
            Node node = path.getNode(index);
            if (!level.getChunkSource().hasChunk(node.x >> 4, node.z >> 4)) {
                return false;
            }
        }
        return true;
    }

    public static boolean movementCorridorLoaded(
            ServerLevel level,
            AABB servantBox,
            AABB targetBox) {
        double deltaX = servantBox.getCenter().x - targetBox.getCenter().x;
        double deltaZ = servantBox.getCenter().z - targetBox.getCenter().z;
        if (deltaX * deltaX + deltaZ * deltaZ
                > MAX_PURSUIT_DISTANCE * MAX_PURSUIT_DISTANCE) {
            return false;
        }
        return areaLoaded(level, servantBox.minmax(targetBox).inflate(1.0D));
    }

    static boolean finite(AABB area) {
        return Double.isFinite(area.minX)
                && Double.isFinite(area.minY)
                && Double.isFinite(area.minZ)
                && Double.isFinite(area.maxX)
                && Double.isFinite(area.maxY)
                && Double.isFinite(area.maxZ);
    }

    static ChunkWindow chunkWindow(AABB area) {
        if (!finite(area)
                || area.maxX <= area.minX
                || area.maxZ <= area.minZ) {
            return null;
        }
        int minChunkX = Mth.floor(area.minX) >> 4;
        int maxChunkX = Mth.floor(Math.nextDown(area.maxX)) >> 4;
        int minChunkZ = Mth.floor(area.minZ) >> 4;
        int maxChunkZ = Mth.floor(Math.nextDown(area.maxZ)) >> 4;
        long width = (long) maxChunkX - minChunkX + 1L;
        long depth = (long) maxChunkZ - minChunkZ + 1L;
        if (width <= 0L || depth <= 0L || width > Long.MAX_VALUE / depth) {
            return null;
        }
        return new ChunkWindow(minChunkX, maxChunkX, minChunkZ, maxChunkZ, width * depth);
    }

    static ChunkWindow pathfindingWindow(BlockPos position, double followRange) {
        if (!Double.isFinite(followRange)
                || followRange < 0.0D
                || followRange > MAX_PATHFINDING_FOLLOW_RANGE) {
            return null;
        }
        int radius = Mth.floor(followRange + VANILLA_INITIAL_PATH_REGION_PADDING);
        AABB footprint = new AABB(
                (double) position.getX() - radius,
                position.getY(),
                (double) position.getZ() - radius,
                (double) position.getX() + radius + 1.0D,
                position.getY() + 1.0D,
                (double) position.getZ() + radius + 1.0D);
        return chunkWindow(footprint);
    }

    record ChunkWindow(
            int minChunkX,
            int maxChunkX,
            int minChunkZ,
            int maxChunkZ,
            long count) {}
}
