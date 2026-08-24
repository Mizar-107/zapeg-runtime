package io.github.mizar107.zapegruntime.quest;

import io.github.mizar107.zapegruntime.server.HeraldorSafetyController;
import io.github.mizar107.zapegruntime.server.HeraldorSafetyMode;
import io.github.mizar107.zapegruntime.story.StoryService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;

/** Server-thread runtime for the twelve non-scene, non-combat quest actions. */
final class QuestActionManager {

    static final int MAX_TRACKED_PLAYERS = 2_048;
    static final int MAX_WITNESS_RESULTS = 9;

    private static final BoundedPlayerState<Session> SESSIONS =
            new BoundedPlayerState<>(MAX_TRACKED_PLAYERS);
    private static final Direction[] HORIZONTAL_DIRECTIONS = {
        Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private QuestActionManager() {}

    static void tick(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (!HeraldorSafetyController.allows(server, HeraldorSafetyMode.AUTO)) {
            return;
        }
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            online.add(player.getUUID());
            tickPlayer(player);
        }
        SESSIONS.retainOnly(online);
    }

    static void handleRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND
                || event.isCanceled()
                || event.getUseBlock() == Event.Result.DENY) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (!HeraldorSafetyController.allows(server, HeraldorSafetyMode.AUTO)) {
            return;
        }
        Optional<QuestStoryAccess.ExpectedAction> expected = QuestStoryAccess.expected(player);
        if (expected.isEmpty()) {
            SESSIONS.remove(player.getUUID());
            return;
        }
        QuestAction action = expected.get().action();
        switch (action) {
            case ASHEN_SCRATCH -> handleAshenScratch(player, event, expected.get());
            case NINTH_BELL -> handleBell(player, event, expected.get());
            case NAME_REFUSAL, BINDER_KNOT, SEAL_01, SEAL_02, SEAL_03 ->
                    handleRitual(player, event, expected.get());
            default -> {
                // Multi-tick actions are sampled only by tickPlayer.
            }
        }
    }

    static void reset(UUID playerId) {
        SESSIONS.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    static void clear() {
        SESSIONS.clear();
    }

    static int trackedPlayerCount() {
        return SESSIONS.size();
    }

    private static void tickPlayer(ServerPlayer player) {
        Optional<QuestStoryAccess.ExpectedAction> expected = QuestStoryAccess.expected(player);
        if (expected.isEmpty()) {
            SESSIONS.remove(player.getUUID());
            return;
        }
        QuestStoryAccess.ExpectedAction context = expected.get();
        QuestAction action = context.action();
        if (action.mode() == QuestAction.Mode.BELL) {
            Session existing = SESSIONS.get(player.getUUID());
            if (existing != null && !existing.matches(context, dimensionId(player))) {
                SESSIONS.remove(player.getUUID());
            }
            return;
        }
        if (action.mode() != QuestAction.Mode.TRACKED) {
            SESSIONS.remove(player.getUUID());
            return;
        }

        ServerLevel level = player.serverLevel();
        String dimension = dimensionId(player);
        Session session = matchingSession(player.getUUID(), context, dimension);
        QuestProgressPolicy.Progress previous = session == null ? null : session.progress;
        QuestProgressPolicy.Progress updated = switch (action) {
            case BACKWARD_TRACKS -> updateBackward(player, level, previous);
            case DROWNED_ROAD -> updateDrownedRoad(player, level, previous);
            case LEANING_HOUSE -> updateLeaningHouse(player, level, previous);
            case UNDERDOOR -> updateUnderdoor(player, level, previous);
            case NINTH_WITNESS -> updateNinthWitness(player, level, previous);
            default -> null;
        };
        if (updated == null) {
            SESSIONS.remove(player.getUUID());
            return;
        }
        if (session == null) {
            session = allocate(player.getUUID(), context, dimension);
            if (session == null) {
                return;
            }
        }
        session.progress = updated;
        if (QuestProgressPolicy.complete(action, updated)) {
            StoryService.SubmissionResult submission = QuestStoryAccess.submit(player, expected.get());
            SESSIONS.remove(player.getUUID());
            if (submission.status() == StoryService.SubmissionStatus.APPLIED) {
                feedback(player, action);
            }
        }
    }

    private static QuestProgressPolicy.Progress updateBackward(
            ServerPlayer player,
            ServerLevel level,
            QuestProgressPolicy.Progress previous) {
        BlockState footing = loadedBlockState(level, blockBelow(player)).orElse(null);
        boolean validEnvironment = footing != null
                && (footing.is(Blocks.MUD)
                        || footing.is(Blocks.COARSE_DIRT)
                        || footing.is(Blocks.DIRT_PATH))
                && level.hasChunkAt(player.blockPosition())
                && level.isRainingAt(player.blockPosition());
        if (!validEnvironment) {
            return null;
        }
        if (previous == null) {
            return QuestProgressPolicy.start(player.getX(), player.getZ());
        }
        Vec3 look = player.getLookAngle();
        if (!QuestProgressPolicy.isBackwardStep(
                previous.lastX(),
                previous.lastZ(),
                player.getX(),
                player.getZ(),
                look.x,
                look.z)) {
            return null;
        }
        return QuestProgressPolicy.advance(previous, player.getX(), player.getZ());
    }

    private static QuestProgressPolicy.Progress updateDrownedRoad(
            ServerPlayer player,
            ServerLevel level,
            QuestProgressPolicy.Progress previous) {
        BlockState footing = loadedBlockState(level, blockBelow(player)).orElse(null);
        if (!player.isEyeInFluid(FluidTags.WATER) || footing == null || !footing.is(Blocks.GRAVEL)) {
            return null;
        }
        return previous == null
                ? QuestProgressPolicy.start(player.getX(), player.getZ())
                : QuestProgressPolicy.advance(previous, player.getX(), player.getZ());
    }

    private static QuestProgressPolicy.Progress updateLeaningHouse(
            ServerPlayer player,
            ServerLevel level,
            QuestProgressPolicy.Progress previous) {
        boolean environment = QuestProgressPolicy.isNight(level.getDayTime())
                && player.isShiftKeyDown()
                && touchesClosedWoodenDoor(level, player);
        if (!environment) {
            return null;
        }
        if (previous == null) {
            return QuestProgressPolicy.start(player.getX(), player.getZ());
        }
        if (!QuestProgressPolicy.isStationaryStep(previous, player.getX(), player.getZ())) {
            return null;
        }
        QuestProgressPolicy.Progress advanced =
                QuestProgressPolicy.advance(previous, player.getX(), player.getZ());
        if (advanced.pathDistance() > QuestProgressPolicy.LEAN_MAX_PATH_DRIFT
                || advanced.displacement() > QuestProgressPolicy.LEAN_MAX_NET_DRIFT) {
            return null;
        }
        return advanced;
    }

    private static QuestProgressPolicy.Progress updateUnderdoor(
            ServerPlayer player,
            ServerLevel level,
            QuestProgressPolicy.Progress previous) {
        boolean dryCrawl = player.getY() < 0.0D
                && player.getPose() == Pose.SWIMMING
                && !player.isInWater()
                && !player.isEyeInFluid(FluidTags.WATER);
        if (!dryCrawl) {
            return null;
        }
        if (previous == null) {
            if (!hasClosedTrapdoorOverhead(level, player)) {
                return null;
            }
            return QuestProgressPolicy.start(player.getX(), player.getZ());
        }
        QuestProgressPolicy.Progress advanced =
                QuestProgressPolicy.advance(previous, player.getX(), player.getZ());
        return advanced.ticks() == 1 ? null : advanced;
    }

    private static QuestProgressPolicy.Progress updateNinthWitness(
            ServerPlayer player,
            ServerLevel level,
            QuestProgressPolicy.Progress previous) {
        if (!QuestProgressPolicy.isNight(level.getDayTime())
                || !player.isUsingItem()
                || !player.getUseItem().is(Items.SPYGLASS)) {
            return null;
        }
        // Nine is a hard result cap: the predicate only distinguishes exactly
        // eight from every larger crowd, so allocating a tenth witness is waste.
        List<ArmorStand> nearby = new ArrayList<>(MAX_WITNESS_RESULTS);
        level.getEntities(
                EntityTypeTest.forClass(ArmorStand.class),
                player.getBoundingBox().inflate(5.0D),
                stand -> stand.isAlive() && stand.distanceToSqr(player) <= 25.0D,
                nearby,
                MAX_WITNESS_RESULTS);
        int witnesses = nearby.size();
        if (witnesses != 8) {
            return null;
        }
        return previous == null
                ? QuestProgressPolicy.start(player.getX(), player.getZ())
                : QuestProgressPolicy.advance(previous, player.getX(), player.getZ());
    }

    private static void handleAshenScratch(
            ServerPlayer player,
            PlayerInteractEvent.RightClickBlock event,
            QuestStoryAccess.ExpectedAction expected) {
        ServerLevel level = player.serverLevel();
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!player.isShiftKeyDown()
                || !event.getItemStack().is(Items.BRUSH)
                || !state.is(Blocks.CAMPFIRE)
                || state.getValue(CampfireBlock.LIT)
                || !QuestProgressPolicy.isNight(level.getDayTime())) {
            return;
        }
        StoryService.SubmissionResult submission = QuestStoryAccess.submit(player, expected);
        if (submission.status() == StoryService.SubmissionStatus.APPLIED) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
            feedback(player, QuestAction.ASHEN_SCRATCH);
        }
    }

    private static void handleBell(
            ServerPlayer player,
            PlayerInteractEvent.RightClickBlock event,
            QuestStoryAccess.ExpectedAction expected) {
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(event.getPos());
        if (!QuestProgressPolicy.isNight(level.getDayTime())
                || !(state.getBlock() instanceof BellBlock bell)
                || !(level.getBlockEntity(event.getPos()) instanceof BellBlockEntity)) {
            return;
        }
        boolean accepted = bell.onHit(level, state, event.getHitVec(), player, true);
        if (!accepted) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);

        String dimension = dimensionId(player);
        Session session = matchingSession(player.getUUID(), expected, dimension);
        if (session == null) {
            session = allocate(player.getUUID(), expected, dimension);
            if (session == null) {
                return;
            }
        }
        session.bell = QuestBellPolicy.recordAcceptedRing(
                session.bell,
                dimension,
                event.getPos().asLong(),
                Objects.requireNonNull(player.getServer()).getTickCount());
        if (!QuestBellPolicy.complete(session.bell)) {
            return;
        }
        StoryService.SubmissionResult submission = QuestStoryAccess.submit(player, expected);
        if (submission.status() == StoryService.SubmissionStatus.APPLIED) {
            SESSIONS.remove(player.getUUID());
            feedback(player, QuestAction.NINTH_BELL);
        } else if (submission.status() == StoryService.SubmissionStatus.NOT_EXPECTED
                || submission.status() == StoryService.SubmissionStatus.ALREADY_PROCESSED
                || submission.status() == StoryService.SubmissionStatus.FACT_ID_CONFLICT) {
            SESSIONS.remove(player.getUUID());
        }
    }

    private static void handleRitual(
            ServerPlayer player,
            PlayerInteractEvent.RightClickBlock event,
            QuestStoryAccess.ExpectedAction expected) {
        QuestAction action = expected.action();
        ItemStack stack = event.getItemStack();
        BlockState state = player.serverLevel().getBlockState(event.getPos());
        RitualRequirement requirement = ritualRequirement(action);
        if (!player.isShiftKeyDown()
                || requirement == null
                || !requirement.matches(player.serverLevel(), event.getPos(), state, stack)) {
            return;
        }

        StoryService.SubmissionResult submission = QuestStoryAccess.submit(player, expected);
        if (!QuestRitualPolicy.consumeAfterApplied(stack, requirement.count, submission)) {
            return;
        }
        player.getInventory().setChanged();
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
        feedback(player, action);
    }

    private static RitualRequirement ritualRequirement(QuestAction action) {
        return switch (action) {
            case NAME_REFUSAL -> new RitualRequirement(
                    Items.NAME_TAG, 1, Blocks.SOUL_CAMPFIRE, null, true, true);
            case BINDER_KNOT ->
                    new RitualRequirement(Items.LEAD, 1, Blocks.TRIPWIRE_HOOK, null, false, false);
            case SEAL_01 -> new RitualRequirement(
                    Items.BLAZE_POWDER, 3, Blocks.CHISELED_DEEPSLATE, Blocks.RED_CANDLE, false, false);
            case SEAL_02 -> new RitualRequirement(
                    Items.PRISMARINE_CRYSTALS,
                    3,
                    Blocks.CHISELED_DEEPSLATE,
                    Blocks.BLUE_CANDLE,
                    false,
                    false);
            case SEAL_03 -> new RitualRequirement(
                    Items.ECHO_SHARD, 1, Blocks.CHISELED_DEEPSLATE, Blocks.BLACK_CANDLE, false, false);
            default -> null;
        };
    }

    private static Session matchingSession(
            UUID playerId, QuestStoryAccess.ExpectedAction expected, String dimension) {
        Session existing = SESSIONS.get(playerId);
        if (existing == null) {
            return null;
        }
        if (!existing.matches(expected, dimension)) {
            SESSIONS.remove(playerId);
            return null;
        }
        return existing;
    }

    private static Session allocate(
            UUID playerId, QuestStoryAccess.ExpectedAction expected, String dimension) {
        Session created = new Session(expected, dimension);
        return SESSIONS.put(playerId, created) ? created : null;
    }

    private static Optional<BlockState> loadedBlockState(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) ? Optional.of(level.getBlockState(pos)) : Optional.empty();
    }

    private static BlockPos blockBelow(ServerPlayer player) {
        return BlockPos.containing(
                player.getX(), player.getBoundingBox().minY - 0.05D, player.getZ());
    }

    private static boolean touchesClosedWoodenDoor(ServerLevel level, ServerPlayer player) {
        BlockPos origin = player.blockPosition();
        for (int yOffset = 0; yOffset <= 1; yOffset++) {
            BlockPos center = origin.offset(0, yOffset, 0);
            if (touchesClosedWoodenDoor(level, player, center)) {
                return true;
            }
            for (Direction direction : HORIZONTAL_DIRECTIONS) {
                if (touchesClosedWoodenDoor(level, player, center.relative(direction))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean touchesClosedWoodenDoor(
            ServerLevel level, ServerPlayer player, BlockPos pos) {
        Optional<BlockState> loaded = loadedBlockState(level, pos);
        if (loaded.isEmpty()
                || !loaded.get().is(BlockTags.WOODEN_DOORS)
                || !loaded.get().hasProperty(BlockStateProperties.OPEN)
                || loaded.get().getValue(BlockStateProperties.OPEN)) {
            return false;
        }
        List<net.minecraft.world.phys.AABB> worldCollision = loaded.get()
                .getCollisionShape(level, pos)
                .toAabbs()
                .stream()
                .map(box -> box.move(pos.getX(), pos.getY(), pos.getZ()))
                .toList();
        return QuestDoorContactPolicy.touchesClosedDoor(
                player.horizontalCollision, player.getBoundingBox(), worldCollision);
    }

    private static boolean hasClosedTrapdoorOverhead(ServerLevel level, ServerPlayer player) {
        BlockPos head = BlockPos.containing(
                player.getX(), player.getBoundingBox().maxY + 0.02D, player.getZ());
        return isClosedTrapdoor(level, head) || isClosedTrapdoor(level, head.above());
    }

    private static boolean isClosedTrapdoor(ServerLevel level, BlockPos pos) {
        Optional<BlockState> loaded = loadedBlockState(level, pos);
        return loaded.isPresent()
                && loaded.get().getBlock() instanceof TrapDoorBlock
                && loaded.get().hasProperty(TrapDoorBlock.OPEN)
                && !loaded.get().getValue(TrapDoorBlock.OPEN);
    }

    private static boolean isLitCandle(ServerLevel level, BlockPos pos, Block candle) {
        Optional<BlockState> loaded = loadedBlockState(level, pos);
        return loaded.isPresent()
                && loaded.get().is(candle)
                && loaded.get().hasProperty(CandleBlock.LIT)
                && loaded.get().getValue(CandleBlock.LIT);
    }

    private static String dimensionId(ServerPlayer player) {
        return player.serverLevel().dimension().location().toString();
    }

    private static void feedback(ServerPlayer player, QuestAction action) {
        String translationKey = "message.zapeg_runtime.quest."
                + action.subject().getPath()
                + ".complete";
        player.sendSystemMessage(
                Component.translatable(translationKey).withStyle(ChatFormatting.DARK_GRAY));
        player.playNotifySound(
                SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS,
                0.7F,
                0.78F + (action.ordinal() % 5) * 0.05F);
    }

    private static final class Session {
        private final QuestSessionKey key;
        private QuestProgressPolicy.Progress progress;
        private QuestBellPolicy.Progress bell;

        private Session(QuestStoryAccess.ExpectedAction expected, String dimension) {
            this.key = QuestSessionKey.from(expected, dimension);
        }

        private boolean matches(
                QuestStoryAccess.ExpectedAction candidate, String candidateDimension) {
            return key.equals(QuestSessionKey.from(candidate, candidateDimension));
        }
    }

    private record RitualRequirement(
            Item item,
            int count,
            Block altar,
            Block candle,
            boolean requiresLitAltar,
            boolean requiresUnrenamed) {

        private RitualRequirement {
            Objects.requireNonNull(item, "item");
            Objects.requireNonNull(altar, "altar");
            if (count < 1) {
                throw new IllegalArgumentException("ritual count must be positive");
            }
        }

        private boolean matches(
                ServerLevel level, BlockPos pos, BlockState state, ItemStack stack) {
            if (!stack.is(item) || stack.getCount() < count || !state.is(altar)) {
                return false;
            }
            if (requiresUnrenamed && stack.hasCustomHoverName()) {
                return false;
            }
            if (requiresLitAltar
                    && (!state.hasProperty(CampfireBlock.LIT)
                            || !state.getValue(CampfireBlock.LIT))) {
                return false;
            }
            if (altar == Blocks.TRIPWIRE_HOOK
                    && (!state.hasProperty(TripWireHookBlock.ATTACHED)
                            || !state.getValue(TripWireHookBlock.ATTACHED))) {
                return false;
            }
            return candle == null || isLitCandle(level, pos.above(), candle);
        }
    }
}
