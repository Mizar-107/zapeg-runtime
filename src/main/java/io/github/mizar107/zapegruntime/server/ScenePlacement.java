package io.github.mizar107.zapegruntime.server;

import io.github.mizar107.zapegruntime.scene.ColossusChoreography;
import io.github.mizar107.zapegruntime.scene.SceneProfile;
import java.util.Objects;
import java.util.Optional;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ScenePlacement {

    /**
     * {angleDegreesOffLook, distance} pairs. Includes side and behind
     * candidates so a sighting can wait where the target has not looked yet;
     * the client still gates every frame on frustum and block line of sight.
     */
    private static final double[][] CANDIDATE_PLAN = {
        {28.0D, 20.0D}, {-28.0D, 26.0D}, {38.0D, 32.0D}, {-38.0D, 18.0D},
        {20.0D, 36.0D}, {-20.0D, 24.0D}, {65.0D, 30.0D}, {-65.0D, 34.0D},
        {90.0D, 22.0D}, {-90.0D, 28.0D}, {150.0D, 20.0D}, {-150.0D, 26.0D},
        {180.0D, 24.0D}
    };
    /**
     * echo_01 must read as a figure in the target's own space: close enough
     * that a humanoid is actually visible, and inside the forced-gaze pull
     * so the look can finish on it. Distances stay in the local 7–12 block
     * band; angles stay inside {@code GazePull.MAX_PULL_DEGREES}.
     */
    private static final double[][] ECHO_CANDIDATE_PLAN = {
        {18.0D, 8.0D}, {-18.0D, 10.0D}, {26.0D, 12.0D}, {-26.0D, 9.0D},
        {12.0D, 11.0D}, {-12.0D, 7.5D}, {32.0D, 8.5D}, {-32.0D, 10.5D}
    };
    /** Prefer the target's floor, then a short column, never the world roof. */
    private static final int LOCAL_Y_RANGE = 8;
    private static final double CAMERA_FOCUS_DISTANCE = 8.0D;
    private static final double CAMERA_FOCUS_PADDING = 0.35D;
    private static final double MIN_CAMERA_FOCUS_DISTANCE = 0.75D;
    /** Local domains keep the two placement decisions decorrelated. */
    private static final long CANDIDATE_ORDER_DOMAIN = 0x47A4D3C2B1908E6FL;
    private static final long HORIZON_AZIMUTH_DOMAIN = 0xC6BC279692B5CC83L;
    /**
     * A Director anchor hint is only honoured within this horizontal range of
     * the target; anything farther is treated as stale or bogus and ignored.
     */
    private static final double MAX_HINT_DISTANCE = 128.0D;

    private ScenePlacement() {}

    public record Placement(Vec3 anchor, float yawDegrees) {}

    public static Optional<Placement> find(
            ServerPlayer player,
            SceneProfile profile) {
        return find(player, profile, null, null, 0);
    }

    public static Optional<Placement> find(
            ServerPlayer player,
            SceneProfile profile,
            Double hintX,
            Double hintZ) {
        return find(player, profile, hintX, hintZ, 0);
    }

    public static Optional<Placement> find(
            ServerPlayer player,
            SceneProfile profile,
            Double hintX,
            Double hintZ,
            int stage) {
        return findInternal(player, profile, hintX, hintZ, stage, null);
    }

    /**
     * Timeline-only placement path. The supplied seed must already be
     * domain-separated from the action's visual seed. Unlike the legacy
     * overload, this path never consumes the player's random source.
     */
    public static Optional<Placement> findSeeded(
            ServerPlayer player,
            SceneProfile profile,
            Double hintX,
            Double hintZ,
            int stage,
            long placementSeed) {
        return findInternal(player, profile, hintX, hintZ, stage, placementSeed);
    }

    private static Optional<Placement> findInternal(
            ServerPlayer player,
            SceneProfile profile,
            Double hintX,
            Double hintZ,
            int stage,
            Long placementSeed) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(profile, "profile");
        Vec3 hint = sanitizeHint(player, hintX, hintZ);
        return switch (profile.placementMode()) {
            case DISTANT_SAFE_GROUND ->
                    findDistantSafeGround(player, profile, hint, placementSeed);
            case CLIENT_MOTION_HISTORY -> findClientMotionAnchor(player);
            case LOCAL_CAMERA_FOCUS -> findLocalCameraFocus(player);
            case PLAYER_RELATIVE -> findClientMotionAnchor(player);
            case HORIZON -> findHorizon(player, stage, placementSeed);
        };
    }

    /**
     * The colossus stands far beyond loaded chunks, so there is no ground to
     * scan: a seeded azimuth at the stage's distance, feet pinned to the
     * target's own height. Fog swallows the implied ground line.
     */
    private static Optional<Placement> findHorizon(
            ServerPlayer player, int stage, Long placementSeed) {
        double azimuth = horizonAzimuth(
                placementSeed, () -> player.getRandom().nextDouble());
        return Optional.of(horizonPlacement(
                player.getX(),
                player.getY(),
                player.getZ(),
                azimuth,
                ColossusChoreography.stageDistance(stage)));
    }

    /** Stable [0, 360) horizon choice with a placement-specific sub-domain. */
    static double seededHorizonAzimuth(long placementSeed) {
        long mixed = mix64(placementSeed ^ HORIZON_AZIMUTH_DOMAIN);
        return (mixed >>> 11) * 0x1.0p-53D * 360.0D;
    }

    static double horizonAzimuth(
            Long placementSeed, DoubleSupplier legacyRandomUnit) {
        Objects.requireNonNull(legacyRandomUnit, "legacyRandomUnit");
        return placementSeed == null
                ? legacyRandomUnit.getAsDouble() * 360.0D
                : seededHorizonAzimuth(placementSeed);
    }

    /** Pure so the horizon anchor contract is unit-testable without a level. */
    static Placement horizonPlacement(
            double playerX,
            double playerY,
            double playerZ,
            double azimuthDegrees,
            double distance) {
        double radians = Math.toRadians(azimuthDegrees);
        double x = playerX + Math.cos(radians) * distance;
        double z = playerZ + Math.sin(radians) * distance;
        // Face the target: same yaw convention as findDistantSafeGround.
        float yaw = (float) (Math.atan2(playerZ - z, playerX - x)
                        * 180.0D / Math.PI)
                - 90.0F;
        return new Placement(new Vec3(x, playerY, z), yaw);
    }

    private static Vec3 sanitizeHint(ServerPlayer player, Double hintX, Double hintZ) {
        if (hintX == null || hintZ == null
                || !Double.isFinite(hintX) || !Double.isFinite(hintZ)) {
            return null;
        }
        double dx = hintX - player.getX();
        double dz = hintZ - player.getZ();
        if (dx * dx + dz * dz > MAX_HINT_DISTANCE * MAX_HINT_DISTANCE) {
            return null;
        }
        return new Vec3(hintX, 0.0D, hintZ);
    }

    /**
     * Candidate indices ordered by anchor proximity to the hint. Pure math so
     * the stalking-memory bias is unit-testable without a level.
     */
    static int[] hintOrder(
            double baseAngleRadians,
            double playerX,
            double playerZ,
            double hintX,
            double hintZ) {
        return hintOrder(
                baseAngleRadians, playerX, playerZ, hintX, hintZ, CANDIDATE_PLAN);
    }

    static int[] hintOrder(
            double baseAngleRadians,
            double playerX,
            double playerZ,
            double hintX,
            double hintZ,
            double[][] plan) {
        Integer[] order = new Integer[plan.length];
        for (int index = 0; index < order.length; index++) {
            order[index] = index;
        }
        java.util.Arrays.sort(order, (left, right) -> {
            double leftDistance = hintDistanceSquared(
                    baseAngleRadians, playerX, playerZ, hintX, hintZ, left, plan);
            double rightDistance = hintDistanceSquared(
                    baseAngleRadians, playerX, playerZ, hintX, hintZ, right, plan);
            return Double.compare(leftDistance, rightDistance);
        });
        int[] result = new int[order.length];
        for (int index = 0; index < order.length; index++) {
            result[index] = order[index];
        }
        return result;
    }

    private static double hintDistanceSquared(
            double baseAngleRadians,
            double playerX,
            double playerZ,
            double hintX,
            double hintZ,
            int candidateIndex,
            double[][] plan) {
        double[] candidate = plan[candidateIndex];
        double angle = baseAngleRadians + Math.toRadians(candidate[0]);
        double anchorX = playerX + Math.cos(angle) * candidate[1];
        double anchorZ = playerZ + Math.sin(angle) * candidate[1];
        double dx = anchorX - hintX;
        double dz = anchorZ - hintZ;
        return dx * dx + dz * dz;
    }

    static double[][] candidatePlan() {
        return copyPlan(CANDIDATE_PLAN);
    }

    static double[][] echoCandidatePlan() {
        return copyPlan(ECHO_CANDIDATE_PLAN);
    }

    private static double[][] copyPlan(double[][] plan) {
        double[][] copy = new double[plan.length][];
        for (int index = 0; index < plan.length; index++) {
            copy[index] = plan[index].clone();
        }
        return copy;
    }

    /**
     * Y values to try for a same-area feet search: the target's floor first,
     * then one block down/up, then further, so a roof heightmap never wins
     * over the room the player is actually standing in.
     */
    static int[] localYSearchOrder(int originY, int range) {
        int[] order = new int[1 + range * 2];
        order[0] = originY;
        int write = 1;
        for (int delta = 1; delta <= range; delta++) {
            order[write++] = originY - delta;
            order[write++] = originY + delta;
        }
        return order;
    }

    /** Pure seeded rotation of the authored plan; no RandomSource involved. */
    static int[] seededCandidateOrder(int planLength, long placementSeed) {
        if (planLength <= 0) {
            throw new IllegalArgumentException("planLength must be positive");
        }
        int offset = (int) Math.floorMod(
                mix64(placementSeed ^ CANDIDATE_ORDER_DOMAIN), (long) planLength);
        return rotatedCandidateOrder(planLength, offset);
    }

    static int[] candidateOrder(
            int planLength, Long placementSeed, IntSupplier legacyOffset) {
        Objects.requireNonNull(legacyOffset, "legacyOffset");
        if (planLength <= 0) {
            throw new IllegalArgumentException("planLength must be positive");
        }
        return placementSeed == null
                ? rotatedCandidateOrder(
                        planLength, Math.floorMod(legacyOffset.getAsInt(), planLength))
                : seededCandidateOrder(planLength, placementSeed);
    }

    private static int[] rotatedCandidateOrder(int planLength, int offset) {
        int[] order = new int[planLength];
        for (int index = 0; index < planLength; index++) {
            order[index] = (index + offset) % planLength;
        }
        return order;
    }

    /** SplitMix64 finalizer: stable across JVMs and cheap enough for placement. */
    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static Optional<Placement> findDistantSafeGround(
            ServerPlayer player,
            SceneProfile profile,
            Vec3 hint,
            Long placementSeed) {
        // Prefer an anchor with a clear block line of sight so the figure can
        // be noticed at once. Indoors or in dense terrain that requirement
        // fails for every candidate, so fall back to any safe anchor rather
        // than refusing the scene outright.
        double[][] plan = planFor(profile);
        boolean localColumn = profile == SceneProfile.ECHO_01;
        Optional<Placement> clear = scanDistantSafeGround(
                player, true, hint, plan, localColumn, placementSeed);
        return clear.isPresent()
                ? clear
                : scanDistantSafeGround(
                        player, false, hint, plan, localColumn, placementSeed);
    }

    private static double[][] planFor(SceneProfile profile) {
        return profile == SceneProfile.ECHO_01 ? ECHO_CANDIDATE_PLAN : CANDIDATE_PLAN;
    }

    private static Optional<Placement> scanDistantSafeGround(
            ServerPlayer player,
            boolean requireClearLine,
            Vec3 hint,
            double[][] plan,
            boolean localColumn,
            Long placementSeed) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        double baseAngle = Math.atan2(look.z, look.x);
        // A stalking-memory hint replaces the random rotation with a
        // deterministic closest-first ordering toward the remembered place.
        int[] order;
        if (hint != null) {
            order = hintOrder(
                    baseAngle, player.getX(), player.getZ(), hint.x, hint.z, plan);
        } else if (placementSeed != null) {
            order = candidateOrder(
                    plan.length,
                    placementSeed,
                    () -> player.getRandom().nextInt(plan.length));
        } else {
            order = candidateOrder(
                    plan.length,
                    null,
                    () -> player.getRandom().nextInt(plan.length));
        }

        for (int index = 0; index < plan.length; index++) {
            int candidateIndex = order[index];
            double[] candidate = plan[candidateIndex];
            double angle = baseAngle + Math.toRadians(candidate[0]);
            double distance = candidate[1];
            double x = player.getX() + Math.cos(angle) * distance;
            double z = player.getZ() + Math.sin(angle) * distance;
            BlockPos sample = BlockPos.containing(x, player.getY(), z);
            if (!level.hasChunkAt(sample)) {
                continue;
            }

            BlockPos feet = localColumn
                    ? localColumnFeet(level, x, z, player.getY())
                    : level.getHeightmapPos(
                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            sample);
            if (feet == null
                    || !safeFeet(level, feet)
                    || !level.getWorldBorder().isWithinBounds(feet)) {
                continue;
            }
            if (Math.abs(feet.getY() - player.getY()) > 12.0D) {
                continue;
            }

            Vec3 anchor = Vec3.atBottomCenterOf(feet);
            AABB bodyBounds = new AABB(
                    anchor.x - 0.35D,
                    anchor.y,
                    anchor.z - 0.35D,
                    anchor.x + 0.35D,
                    anchor.y + 1.9D,
                    anchor.z + 0.35D);
            if (!LoadedSceneQueries.noCollision(level, bodyBounds)) {
                continue;
            }
            Vec3 chest = anchor.add(0.0D, 1.35D, 0.0D);
            if (requireClearLine && !clearLine(level, player.getEyePosition(), chest, player)) {
                continue;
            }

            float yaw = (float) (Mth.atan2(
                    player.getZ() - anchor.z,
                    player.getX() - anchor.x) * Mth.RAD_TO_DEG) - 90.0F;
            return Optional.of(new Placement(anchor, yaw));
        }
        return Optional.empty();
    }

    private static BlockPos localColumnFeet(
            ServerLevel level, double x, double z, double playerY) {
        int originY = Mth.floor(playerY);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y : localYSearchOrder(originY, LOCAL_Y_RANGE)) {
            cursor.set(Mth.floor(x), y, Mth.floor(z));
            if (!level.hasChunkAt(cursor)) {
                continue;
            }
            if (safeFeet(level, cursor)) {
                return cursor.immutable();
            }
        }
        return null;
    }

    private static Optional<Placement> findClientMotionAnchor(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 anchor = player.position();
        BlockPos anchorPos = BlockPos.containing(anchor);
        if (!level.hasChunkAt(anchorPos)
                || !level.getWorldBorder().isWithinBounds(anchorPos)) {
            return Optional.empty();
        }
        return Optional.of(new Placement(anchor, player.getYRot()));
    }

    private static Optional<Placement> findLocalCameraFocus(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        if (look.lengthSqr() < 1.0E-12D) {
            return Optional.empty();
        }
        look = look.normalize();
        Vec3 eye = player.getEyePosition();
        Vec3 farFocus = eye.add(look.scale(CAMERA_FOCUS_DISTANCE));
        Optional<BlockHitResult> loadedHit = LoadedSceneQueries.clip(
                level, eye, farFocus, player);
        if (loadedHit.isEmpty()) {
            return Optional.empty();
        }
        BlockHitResult hit = loadedHit.get();
        double distance = CAMERA_FOCUS_DISTANCE;
        if (hit.getType() != HitResult.Type.MISS) {
            distance = eye.distanceTo(hit.getLocation()) - CAMERA_FOCUS_PADDING;
        }
        if (distance < MIN_CAMERA_FOCUS_DISTANCE) {
            return Optional.empty();
        }

        Vec3 anchor = eye.add(look.scale(distance));
        BlockPos anchorPos = BlockPos.containing(anchor);
        if (!level.hasChunkAt(anchorPos)
                || !level.getWorldBorder().isWithinBounds(anchorPos)
                || !clearLine(level, eye, anchor, player)) {
            return Optional.empty();
        }
        return Optional.of(new Placement(anchor, player.getYRot()));
    }

    private static boolean safeFeet(ServerLevel level, BlockPos feet) {
        BlockState ground = level.getBlockState(feet.below());
        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(feet.above());
        return !ground.isAir()
                && ground.getFluidState().isEmpty()
                && ground.isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)
                && feetState.getFluidState().isEmpty()
                && headState.getFluidState().isEmpty()
                && feetState.getCollisionShape(level, feet).isEmpty()
                && headState.getCollisionShape(level, feet.above()).isEmpty();
    }

    private static boolean clearLine(
            ServerLevel level,
            Vec3 start,
            Vec3 end,
            ServerPlayer player) {
        return LoadedSceneQueries.clip(level, start, end, player)
                .map(hit -> hit.getType() == HitResult.Type.MISS)
                .orElse(false);
    }
}
