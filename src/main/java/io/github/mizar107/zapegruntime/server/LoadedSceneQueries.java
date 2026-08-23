package io.github.mizar107.zapegruntime.server;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The only placement gateway for world queries that may cross chunk bounds.
 * Every touched chunk is proven loaded with {@code ServerChunkCache.hasChunk}
 * before the underlying query runs; a missing or unbounded footprint fails
 * closed and never asks Minecraft to obtain a chunk.
 */
final class LoadedSceneQueries {

    /** Placement rays are at most tens of blocks; this is a corruption guard. */
    static final int MAX_PREFLIGHT_CHUNKS = 256;
    private static final double RAY_ENDPOINT_EXPANSION = 1.0E-7D;
    private static final double COLLISION_SCAN_EPSILON = 1.0E-7D;

    private LoadedSceneQueries() {}

    @FunctionalInterface
    interface ChunkAvailability {
        boolean isLoaded(int chunkX, int chunkZ);
    }

    record ChunkSpan(int minInclusive, int maxInclusive) {

        ChunkSpan {
            if (minInclusive > maxInclusive) {
                throw new IllegalArgumentException("reversed chunk span");
            }
        }

        long size() {
            return (long) maxInclusive - minInclusive + 1L;
        }
    }

    static Optional<BlockHitResult> clip(
            ServerLevel level, Vec3 start, Vec3 end, Entity source) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(source, "source");
        if (!allRayChunksLoaded(
                start,
                end,
                (chunkX, chunkZ) -> level.getChunkSource().hasChunk(chunkX, chunkZ))) {
            return Optional.empty();
        }
        return Optional.of(level.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                source)));
    }

    static boolean noCollision(ServerLevel level, AABB bounds) {
        Objects.requireNonNull(level, "level");
        if (!allCollisionChunksLoaded(
                bounds,
                (chunkX, chunkZ) -> level.getChunkSource().hasChunk(chunkX, chunkZ))) {
            return false;
        }
        return level.noCollision(bounds);
    }

    /**
     * Pure preflight for a block clip. Minecraft expands both ray endpoints
     * by 1e-7 before voxel traversal, so this mirrors that expansion and then
     * walks the horizontal chunk grid. Exact corner crossings conservatively
     * require both side-adjacent chunks as well as the diagonal chunk.
     */
    static boolean allRayChunksLoaded(
            Vec3 start, Vec3 end, ChunkAvailability availability) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(availability, "availability");
        if (!finite(start.x) || !finite(start.y) || !finite(start.z)
                || !finite(end.x) || !finite(end.y) || !finite(end.z)) {
            return false;
        }

        double deltaX = end.x - start.x;
        double deltaZ = end.z - start.z;
        double traversalStartX = start.x - deltaX * RAY_ENDPOINT_EXPANSION;
        double traversalStartZ = start.z - deltaZ * RAY_ENDPOINT_EXPANSION;
        double traversalEndX = end.x + deltaX * RAY_ENDPOINT_EXPANSION;
        double traversalEndZ = end.z + deltaZ * RAY_ENDPOINT_EXPANSION;
        if (!finite(traversalStartX)
                || !finite(traversalStartZ)
                || !finite(traversalEndX)
                || !finite(traversalEndZ)) {
            return false;
        }

        Integer startChunkX = chunkCoordinate(traversalStartX);
        Integer startChunkZ = chunkCoordinate(traversalStartZ);
        Integer endChunkX = chunkCoordinate(traversalEndX);
        Integer endChunkZ = chunkCoordinate(traversalEndZ);
        if (startChunkX == null || startChunkZ == null
                || endChunkX == null || endChunkZ == null) {
            return false;
        }

        int chunkX = startChunkX;
        int chunkZ = startChunkZ;
        int targetChunkX = endChunkX;
        int targetChunkZ = endChunkZ;
        int checks = 1;
        if (!availability.isLoaded(chunkX, chunkZ)) {
            return false;
        }
        if (chunkX == targetChunkX && chunkZ == targetChunkZ) {
            return true;
        }

        double traversalDeltaX = traversalEndX - traversalStartX;
        double traversalDeltaZ = traversalEndZ - traversalStartZ;
        int stepX = sign(traversalDeltaX);
        int stepZ = sign(traversalDeltaZ);
        double tDeltaX = stepX == 0
                ? Double.POSITIVE_INFINITY
                : 16.0D / Math.abs(traversalDeltaX);
        double tDeltaZ = stepZ == 0
                ? Double.POSITIVE_INFINITY
                : 16.0D / Math.abs(traversalDeltaZ);
        double tMaxX = firstBoundaryTime(
                traversalStartX, traversalDeltaX, chunkX, stepX);
        double tMaxZ = firstBoundaryTime(
                traversalStartZ, traversalDeltaZ, chunkZ, stepZ);

        while (chunkX != targetChunkX || chunkZ != targetChunkZ) {
            if (nearlyEqual(tMaxX, tMaxZ)) {
                int nextX = chunkX + stepX;
                int nextZ = chunkZ + stepZ;
                // A zero-width corner is still queried by Minecraft's
                // expanded voxel traversal; require both adjacent sides.
                if (++checks > MAX_PREFLIGHT_CHUNKS
                        || !availability.isLoaded(nextX, chunkZ)) {
                    return false;
                }
                if (++checks > MAX_PREFLIGHT_CHUNKS
                        || !availability.isLoaded(chunkX, nextZ)) {
                    return false;
                }
                chunkX = nextX;
                chunkZ = nextZ;
                tMaxX += tDeltaX;
                tMaxZ += tDeltaZ;
            } else if (tMaxX < tMaxZ) {
                chunkX += stepX;
                tMaxX += tDeltaX;
            } else {
                chunkZ += stepZ;
                tMaxZ += tDeltaZ;
            }
            if (++checks > MAX_PREFLIGHT_CHUNKS
                    || !availability.isLoaded(chunkX, chunkZ)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Pure preflight for {@code Level.noCollision(AABB)}. BlockCollisions
     * examines one block beyond the epsilon-expanded box, so the span covers
     * that real query footprint rather than only the apparent body bounds.
     */
    static boolean allCollisionChunksLoaded(
            AABB bounds, ChunkAvailability availability) {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(availability, "availability");
        if (!finite(bounds.minX) || !finite(bounds.minY) || !finite(bounds.minZ)
                || !finite(bounds.maxX) || !finite(bounds.maxY) || !finite(bounds.maxZ)
                || bounds.minX > bounds.maxX
                || bounds.minY > bounds.maxY
                || bounds.minZ > bounds.maxZ) {
            return false;
        }
        ChunkSpan xSpan = collisionChunkSpan(bounds.minX, bounds.maxX);
        ChunkSpan zSpan = collisionChunkSpan(bounds.minZ, bounds.maxZ);
        if (xSpan == null || zSpan == null) {
            return false;
        }
        long xSize = xSpan.size();
        long zSize = zSpan.size();
        if (xSize > MAX_PREFLIGHT_CHUNKS
                || zSize > MAX_PREFLIGHT_CHUNKS
                || xSize * zSize > MAX_PREFLIGHT_CHUNKS) {
            return false;
        }
        for (int chunkX = xSpan.minInclusive(); ; chunkX++) {
            for (int chunkZ = zSpan.minInclusive(); ; chunkZ++) {
                if (!availability.isLoaded(chunkX, chunkZ)) {
                    return false;
                }
                if (chunkZ == zSpan.maxInclusive()) {
                    break;
                }
            }
            if (chunkX == xSpan.maxInclusive()) {
                break;
            }
        }
        return true;
    }

    static ChunkSpan collisionChunkSpan(double min, double max) {
        if (!finite(min) || !finite(max) || min > max) {
            return null;
        }
        Long minBlock = floorToLong(min - COLLISION_SCAN_EPSILON);
        Long maxBlock = floorToLong(max + COLLISION_SCAN_EPSILON);
        if (minBlock == null || maxBlock == null
                || minBlock == Long.MIN_VALUE || maxBlock == Long.MAX_VALUE) {
            return null;
        }
        long minChunk = Math.floorDiv(minBlock - 1L, 16L);
        long maxChunk = Math.floorDiv(maxBlock + 1L, 16L);
        if (minChunk < Integer.MIN_VALUE || maxChunk > Integer.MAX_VALUE) {
            return null;
        }
        return new ChunkSpan((int) minChunk, (int) maxChunk);
    }

    private static double firstBoundaryTime(
            double start, double delta, int chunk, int step) {
        if (step == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double boundary = step > 0
                ? ((double) chunk + 1.0D) * 16.0D
                : (double) chunk * 16.0D;
        return (boundary - start) / delta;
    }

    private static Integer chunkCoordinate(double blockCoordinate) {
        double chunk = Math.floor(blockCoordinate / 16.0D);
        if (chunk < Integer.MIN_VALUE || chunk > Integer.MAX_VALUE) {
            return null;
        }
        return (int) chunk;
    }

    private static Long floorToLong(double value) {
        if (!finite(value) || value < Long.MIN_VALUE || value > Long.MAX_VALUE) {
            return null;
        }
        return (long) Math.floor(value);
    }

    private static boolean nearlyEqual(double left, double right) {
        if (!finite(left) || !finite(right)) {
            return false;
        }
        double scale = Math.max(1.0D, Math.max(Math.abs(left), Math.abs(right)));
        return Math.abs(left - right) <= 1.0E-12D * scale;
    }

    private static boolean finite(double value) {
        return Double.isFinite(value);
    }

    private static int sign(double value) {
        return value > 0.0D ? 1 : value < 0.0D ? -1 : 0;
    }
}
