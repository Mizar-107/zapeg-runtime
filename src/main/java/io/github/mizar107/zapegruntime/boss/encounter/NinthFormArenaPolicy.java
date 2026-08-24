package io.github.mizar107.zapegruntime.boss.encounter;

import java.util.Collection;
import java.util.Objects;

/** Pure loaded-only planner for a non-mutating 48-block virtual arena. */
public final class NinthFormArenaPolicy {

    public static final int ARENA_RADIUS = 48;
    public static final int MIN_BOSS_SEPARATION = 128;
    public static final int MAX_LOADED_BOSSES = 4;

    private NinthFormArenaPolicy() {}

    public static boolean contains(int centerX, int centerZ, double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            return false;
        }
        double dx = x - (centerX + 0.5D);
        double dz = z - (centerZ + 0.5D);
        return dx * dx + dz * dz <= (double) ARENA_RADIUS * ARENA_RADIUS;
    }

    public static PlanResult plan(
            String dimensionId,
            int centerX,
            int centerY,
            int centerZ,
            Collection<OccupiedArena> loadedBosses,
            ChunkPresence chunks) {
        Objects.requireNonNull(dimensionId, "dimensionId");
        Objects.requireNonNull(loadedBosses, "loadedBosses");
        Objects.requireNonNull(chunks, "chunks");
        if (loadedBosses.size() >= MAX_LOADED_BOSSES) {
            return PlanResult.refused(Status.LOADED_BOSS_CAPACITY, "four bosses are already loaded");
        }
        long separationSquared = (long) MIN_BOSS_SEPARATION * MIN_BOSS_SEPARATION;
        for (OccupiedArena other : loadedBosses) {
            if (!dimensionId.equals(other.dimensionId())) {
                continue;
            }
            long dx = (long) centerX - other.centerX();
            long dz = (long) centerZ - other.centerZ();
            if (dx * dx + dz * dz < separationSquared) {
                return PlanResult.refused(Status.TOO_CLOSE, "another loaded boss is within 128 blocks");
            }
        }
        int minChunkX = Math.floorDiv(centerX - ARENA_RADIUS, 16);
        int maxChunkX = Math.floorDiv(centerX + ARENA_RADIUS, 16);
        int minChunkZ = Math.floorDiv(centerZ - ARENA_RADIUS, 16);
        int maxChunkZ = Math.floorDiv(centerZ + ARENA_RADIUS, 16);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!chunks.loaded(chunkX, chunkZ)) {
                    return PlanResult.refused(
                            Status.CHUNK_NOT_LOADED,
                            "the complete 48-block virtual arena is not resident");
                }
            }
        }
        return new PlanResult(
                Status.READY,
                "loaded virtual arena is ready without chunk tickets or mutation",
                new Arena(dimensionId, centerX, centerY, centerZ));
    }

    @FunctionalInterface
    public interface ChunkPresence {
        boolean loaded(int chunkX, int chunkZ);
    }

    public enum Status {
        READY,
        LOADED_BOSS_CAPACITY,
        TOO_CLOSE,
        CHUNK_NOT_LOADED
    }

    public record Arena(String dimensionId, int centerX, int centerY, int centerZ) {
        public Arena {
            Objects.requireNonNull(dimensionId, "dimensionId");
        }
    }

    public record OccupiedArena(String dimensionId, int centerX, int centerZ) {
        public OccupiedArena {
            Objects.requireNonNull(dimensionId, "dimensionId");
        }
    }

    public record PlanResult(Status status, String detail, Arena arena) {
        public PlanResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(detail, "detail");
            if ((status == Status.READY) != (arena != null)) {
                throw new IllegalArgumentException("only READY may contain an arena");
            }
        }

        private static PlanResult refused(Status status, String detail) {
            return new PlanResult(status, detail, null);
        }
    }
}
