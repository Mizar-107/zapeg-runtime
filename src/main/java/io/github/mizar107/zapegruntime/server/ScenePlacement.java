package io.github.mizar107.zapegruntime.server;

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

    private static final double[] DISTANCES = {20.0D, 26.0D, 32.0D, 18.0D, 36.0D};
    private static final double[] ANGLES = {28.0D, -28.0D, 38.0D, -38.0D, 20.0D, -20.0D};

    private ScenePlacement() {}

    public record Placement(Vec3 anchor, float yawDegrees) {}

    public static Optional<Placement> find(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        double baseAngle = Math.atan2(look.z, look.x);
        int offset = player.getRandom().nextInt(ANGLES.length);

        for (int index = 0; index < ANGLES.length; index++) {
            double angleDegrees = ANGLES[(index + offset) % ANGLES.length];
            double angle = baseAngle + Math.toRadians(angleDegrees);
            double distance = DISTANCES[(index + offset) % DISTANCES.length];
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
            if (!clearLine(level, player.getEyePosition(), chest, player)) {
                continue;
            }

            float yaw = (float) (Mth.atan2(
                    player.getZ() - anchor.z,
                    player.getX() - anchor.x) * Mth.RAD_TO_DEG) - 90.0F;
            return Optional.of(new Placement(anchor, yaw));
        }
        return Optional.empty();
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
