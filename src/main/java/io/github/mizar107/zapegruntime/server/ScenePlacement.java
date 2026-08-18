package io.github.mizar107.zapegruntime.server;

import io.github.mizar107.zapegruntime.scene.SceneProfile;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
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
    private static final double CAMERA_FOCUS_DISTANCE = 8.0D;
    private static final double CAMERA_FOCUS_PADDING = 0.35D;
    private static final double MIN_CAMERA_FOCUS_DISTANCE = 0.75D;

    private ScenePlacement() {}

    public record Placement(Vec3 anchor, float yawDegrees) {}

    public static Optional<Placement> find(
            ServerPlayer player,
            SceneProfile profile) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(profile, "profile");
        return switch (profile.placementMode()) {
            case DISTANT_SAFE_GROUND -> findDistantSafeGround(player);
            case CLIENT_MOTION_HISTORY -> findClientMotionAnchor(player);
            case LOCAL_CAMERA_FOCUS -> findLocalCameraFocus(player);
        };
    }

    static double[][] candidatePlan() {
        double[][] copy = new double[CANDIDATE_PLAN.length][];
        for (int index = 0; index < CANDIDATE_PLAN.length; index++) {
            copy[index] = CANDIDATE_PLAN[index].clone();
        }
        return copy;
    }

    private static Optional<Placement> findDistantSafeGround(ServerPlayer player) {
        // Prefer an anchor with a clear block line of sight so the figure can
        // be noticed at once. Indoors or in dense terrain that requirement
        // fails for every candidate, so fall back to any safe anchor rather
        // than refusing the scene outright.
        Optional<Placement> clear = scanDistantSafeGround(player, true);
        return clear.isPresent() ? clear : scanDistantSafeGround(player, false);
    }

    private static Optional<Placement> scanDistantSafeGround(
            ServerPlayer player,
            boolean requireClearLine) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        double baseAngle = Math.atan2(look.z, look.x);
        int offset = player.getRandom().nextInt(CANDIDATE_PLAN.length);

        for (int index = 0; index < CANDIDATE_PLAN.length; index++) {
            double[] candidate = CANDIDATE_PLAN[(index + offset) % CANDIDATE_PLAN.length];
            double angle = baseAngle + Math.toRadians(candidate[0]);
            double distance = candidate[1];
            double x = player.getX() + Math.cos(angle) * distance;
            double z = player.getZ() + Math.sin(angle) * distance;
            BlockPos sample = BlockPos.containing(x, player.getY(), z);
            if (!level.hasChunkAt(sample)) {
                continue;
            }

            BlockPos feet = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    sample);
            if (!safeFeet(level, feet) || !level.getWorldBorder().isWithinBounds(feet)) {
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
            if (!level.noCollision(bodyBounds)) {
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
        BlockHitResult hit = level.clip(new ClipContext(
                eye,
                farFocus,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player));
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
        BlockHitResult hit = level.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player));
        return hit.getType() == HitResult.Type.MISS;
    }
}
