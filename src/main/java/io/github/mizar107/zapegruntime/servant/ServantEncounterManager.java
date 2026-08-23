package io.github.mizar107.zapegruntime.servant;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;

/** Owns all Servant spawning, recovery, expiry, cancellation, and victory credit. */
public final class ServantEncounterManager {

    public static final int LIFETIME_TICKS = 120 * 20;
    private static final int RECONCILE_INTERVAL_TICKS = 20;
    private static final Set<MinecraftServer> STOPPING_SERVERS =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final int[][] SPAWN_OFFSETS = {
        {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1},
        {2, 0}, {-2, 0}, {0, 2}, {0, -2}, {1, 1}, {-1, -1}
    };

    private ServantEncounterManager() {}

    public static StartResult awaken(ServerPlayer target, boolean rehearsal) {
        return awaken(target, UUID.randomUUID(), rehearsal);
    }

    /**
     * Starts or idempotently resolves an encounter. Callers that retry the
     * same event UUID receive the existing entity instead of a duplicate.
     */
    public static StartResult awaken(
            ServerPlayer target,
            UUID encounterId,
            boolean rehearsal) {
        MinecraftServer server = target.getServer();
        if (server == null) {
            return StartResult.failed(StartStatus.NO_SERVER, encounterId, "target has no server");
        }
        ServerLevel level = target.serverLevel();
        HeraldorServant servant = ServantEntities.SERVANT.get().create(level);
        if (servant == null) {
            return StartResult.failed(StartStatus.SPAWN_FAILED, encounterId, "entity creation failed");
        }

        long deadline = server.overworld().getGameTime() + LIFETIME_TICKS;
        servant.configure(encounterId, target.getUUID(), rehearsal, deadline);
        placeNearTarget(servant, target);
        ChunkPos chunk = servant.chunkPosition();
        ServantEncounter proposed = new ServantEncounter(
                encounterId,
                target.getUUID(),
                servant.getUUID(),
                level.dimension().location().toString(),
                rehearsal,
                deadline,
                chunk.x,
                chunk.z);

        ServantEncounterData data = ServantEncounterData.get(server);
        ServantEncounterData.BeginResult reservation = data.begin(proposed);
        switch (reservation.status()) {
            case IDEMPOTENT -> {
                ServantEncounter existing = reservation.encounter();
                return new StartResult(
                        StartStatus.ALREADY_ACTIVE,
                        existing.encounterId(),
                        existing.servantId(),
                        "encounter already active");
            }
            case TARGET_BUSY -> {
                ServantEncounter existing = reservation.encounter();
                return new StartResult(
                        StartStatus.TARGET_BUSY,
                        existing.encounterId(),
                        existing.servantId(),
                        "target already has an active Servant");
            }
            case REPLAYED_TERMINAL -> {
                return StartResult.failed(
                        StartStatus.REPLAYED_TERMINAL,
                        encounterId,
                        "encounter id was already consumed");
            }
            case EVENT_ID_CONFLICT -> {
                return StartResult.failed(
                        StartStatus.EVENT_ID_CONFLICT,
                        encounterId,
                        "encounter id belongs to another target");
            }
            case STARTED -> {
                // Continue below.
            }
        }

        if (!level.addFreshEntity(servant)) {
            data.rollbackSpawn(encounterId);
            return StartResult.failed(StartStatus.SPAWN_FAILED, encounterId, "world rejected entity");
        }

        ZapeGRuntime.LOGGER.info(
                "Servant started encounter={} target={} entity={} rehearsal={} deadline={}",
                encounterId,
                target.getGameProfile().getName(),
                servant.getUUID(),
                rehearsal,
                deadline);
        return new StartResult(
                StartStatus.STARTED,
                encounterId,
                servant.getUUID(),
                "Servant awakened");
    }

    public static void tick(MinecraftServer server) {
        ServantEncounterData data = ServantEncounterData.get(server);
        long now = server.overworld().getGameTime();

        // Deadlines are checked every tick. Reconciliation is intentionally
        // less frequent because it can load a recorded entity chunk.
        for (ServantEncounter encounter : data.activeEncounters()) {
            if (encounter.isExpired(now)) {
                close(server, encounter, CloseReason.EXPIRED);
            }
        }
        if (server.getTickCount() % RECONCILE_INTERVAL_TICKS != 0) {
            return;
        }

        for (ServantEncounter encounter : data.activeEncounters()) {
            reconcile(server, encounter);
        }
        discardOrphansAndDuplicates(server, data);
    }

    public static void onServerStarted(MinecraftServer server) {
        STOPPING_SERVERS.remove(server);
    }

    public static void onServerStopping(MinecraftServer server) {
        // PlayerLoggedOutEvent is also emitted while a server shuts down. In
        // that path the entity and ledger must remain intact for restart
        // reconciliation, rather than being treated as a voluntary logout.
        STOPPING_SERVERS.add(server);
    }

    public static boolean isServerStopping(MinecraftServer server) {
        return STOPPING_SERVERS.contains(server);
    }

    public static void onDeath(HeraldorServant servant, DamageSource source) {
        if (!(servant.level() instanceof ServerLevel level)
                || servant.encounterId() == null
                || source.getEntity() == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        UUID killerId = source.getEntity().getUUID();
        ServantEncounterData data = ServantEncounterData.get(server);
        ServantEncounterData.FinishResult result = data.finishVictory(
                servant.encounterId(), servant.getUUID(), killerId);
        switch (result) {
            case LIVE_CREDITED -> {
                int victories = data.victoryCount(killerId);
                ZapeGRuntime.LOGGER.info(
                        "Servant live victory encounter={} target={} victories={}",
                        servant.encounterId(),
                        killerId,
                        victories);
                MinecraftForge.EVENT_BUS.post(new ServantVictoryEvent(
                        server,
                        servant.encounterId(),
                        killerId,
                        victories));
            }
            case REHEARSAL_COMPLETE -> ZapeGRuntime.LOGGER.info(
                    "Servant rehearsal completed without progression encounter={} target={}",
                    servant.encounterId(),
                    killerId);
            case ALREADY_TERMINAL, NOT_ACTIVE -> ZapeGRuntime.LOGGER.debug(
                    "Ignored repeated Servant death encounter={} result={}",
                    servant.encounterId(),
                    result);
            case IDENTITY_MISMATCH -> ZapeGRuntime.LOGGER.warn(
                    "Rejected Servant victory identity mismatch encounter={} entity={} killer={}",
                    servant.encounterId(),
                    servant.getUUID(),
                    killerId);
        }
    }

    public static boolean cancelForTarget(
            MinecraftServer server,
            UUID targetId,
            CloseReason reason) {
        Optional<ServantEncounter> active = ServantEncounterData.get(server).activeFor(targetId);
        if (active.isEmpty()) {
            return false;
        }
        return close(server, active.get(), reason);
    }

    public static Optional<ServantEncounter> activeFor(MinecraftServer server, UUID targetId) {
        return ServantEncounterData.get(server).activeFor(targetId);
    }

    public static int victoryCount(MinecraftServer server, UUID targetId) {
        return ServantEncounterData.get(server).victoryCount(targetId);
    }

    private static void reconcile(MinecraftServer server, ServantEncounter encounter) {
        ServerPlayer target = server.getPlayerList().getPlayer(encounter.targetId());
        if (target == null) {
            // Preserve restart state until the player reconnects or the
            // persisted game-time deadline expires. Do not force-load chunks.
            return;
        }

        ServerLevel level = resolveLevel(server, encounter.dimension());
        if (level == null || target.serverLevel() != level) {
            close(server, encounter, CloseReason.DIMENSION_CHANGE);
            return;
        }

        HeraldorServant servant = findExpected(level, encounter);
        if (servant == null) {
            // Loading the last recorded chunk first prevents creating a twin
            // of an entity that merely had not been loaded after restart.
            level.getChunk(encounter.chunkX(), encounter.chunkZ());
            servant = findExpected(level, encounter);
        }
        if (servant == null) {
            servant = findByEncounter(level, encounter);
            if (servant != null) {
                ChunkPos chunk = servant.chunkPosition();
                ServantEncounterData.get(server).replaceEntity(
                        encounter.encounterId(), servant.getUUID(), chunk.x, chunk.z);
                encounter = encounter.withEntity(servant.getUUID(), chunk.x, chunk.z);
            }
        }
        if (servant == null) {
            replaceMissingEntity(server, level, target, encounter);
            return;
        }

        if (!servant.identityMatches(encounter)) {
            servant.discard();
            replaceMissingEntity(server, level, target, encounter);
            return;
        }
        ChunkPos currentChunk = servant.chunkPosition();
        ServantEncounterData.get(server).updateLocation(
                encounter.encounterId(), currentChunk.x, currentChunk.z);
    }

    private static void replaceMissingEntity(
            MinecraftServer server,
            ServerLevel level,
            ServerPlayer target,
            ServantEncounter oldRecord) {
        HeraldorServant replacement = ServantEntities.SERVANT.get().create(level);
        if (replacement == null) {
            ZapeGRuntime.LOGGER.error(
                    "Could not recreate missing Servant encounter={}", oldRecord.encounterId());
            return;
        }
        replacement.configure(
                oldRecord.encounterId(),
                oldRecord.targetId(),
                oldRecord.rehearsal(),
                oldRecord.deadlineGameTime());
        placeNearTarget(replacement, target);
        ChunkPos chunk = replacement.chunkPosition();
        ServantEncounterData data = ServantEncounterData.get(server);
        if (!data.replaceEntity(
                oldRecord.encounterId(), replacement.getUUID(), chunk.x, chunk.z)) {
            return;
        }
        if (!level.addFreshEntity(replacement)) {
            data.replaceEntity(
                    oldRecord.encounterId(),
                    oldRecord.servantId(),
                    oldRecord.chunkX(),
                    oldRecord.chunkZ());
            ZapeGRuntime.LOGGER.error(
                    "World rejected replacement Servant encounter={}", oldRecord.encounterId());
            return;
        }
        ZapeGRuntime.LOGGER.warn(
                "Reconciled missing Servant encounter={} old_entity={} new_entity={}",
                oldRecord.encounterId(),
                oldRecord.servantId(),
                replacement.getUUID());
    }

    private static boolean close(
            MinecraftServer server,
            ServantEncounter encounter,
            CloseReason reason) {
        ServantEncounterData data = ServantEncounterData.get(server);
        if (!data.close(encounter.encounterId())) {
            return false;
        }
        ServerLevel level = resolveLevel(server, encounter.dimension());
        if (level != null) {
            Entity entity = level.getEntity(encounter.servantId());
            if (entity instanceof HeraldorServant servant) {
                servant.discard();
            }
        }
        ZapeGRuntime.LOGGER.info(
                "Servant closed encounter={} target={} reason={}",
                encounter.encounterId(),
                encounter.targetId(),
                reason);
        return true;
    }

    private static void discardOrphansAndDuplicates(
            MinecraftServer server,
            ServantEncounterData data) {
        for (ServerLevel level : server.getAllLevels()) {
            List<HeraldorServant> removals = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof HeraldorServant servant)) {
                    continue;
                }
                UUID eventId = servant.encounterId();
                Optional<ServantEncounter> record = eventId == null
                        ? Optional.empty()
                        : data.findByEncounter(eventId);
                if (record.isEmpty()
                        || !record.get().servantId().equals(servant.getUUID())
                        || !record.get().targetId().equals(servant.designatedTargetId())) {
                    removals.add(servant);
                }
            }
            removals.forEach(HeraldorServant::discard);
        }
    }

    @Nullable
    private static HeraldorServant findExpected(
            ServerLevel level,
            ServantEncounter encounter) {
        Entity entity = level.getEntity(encounter.servantId());
        return entity instanceof HeraldorServant servant ? servant : null;
    }

    @Nullable
    private static HeraldorServant findByEncounter(
            ServerLevel level,
            ServantEncounter encounter) {
        HeraldorServant selected = null;
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof HeraldorServant servant)
                    || !encounter.encounterId().equals(servant.encounterId())
                    || !encounter.targetId().equals(servant.designatedTargetId())) {
                continue;
            }
            if (selected == null
                    || servant.getUUID().toString().compareTo(selected.getUUID().toString()) < 0) {
                selected = servant;
            }
        }
        return selected;
    }

    @Nullable
    private static ServerLevel resolveLevel(MinecraftServer server, String rawDimension) {
        ResourceLocation id = ResourceLocation.tryParse(rawDimension);
        if (id == null) {
            return null;
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        return server.getLevel(key);
    }

    private static void placeNearTarget(HeraldorServant servant, ServerPlayer target) {
        Vec3 look = target.getLookAngle();
        double horizontalLength = Math.sqrt(look.x * look.x + look.z * look.z);
        double directionX = horizontalLength < 0.001D ? 0.0D : look.x / horizontalLength;
        double directionZ = horizontalLength < 0.001D ? 1.0D : look.z / horizontalLength;
        Vec3 base = target.position().subtract(directionX * 5.0D, 0.0D, directionZ * 5.0D);
        ServerLevel level = target.serverLevel();

        for (int yOffset : new int[] {0, 1, -1, 2, -2}) {
            for (int[] offset : SPAWN_OFFSETS) {
                double x = Math.floor(base.x) + 0.5D + offset[0];
                double y = target.getY() + yOffset;
                double z = Math.floor(base.z) + 0.5D + offset[1];
                servant.moveTo(x, y, z, target.getYRot() + 180.0F, 0.0F);
                if (level.noCollision(servant)) {
                    return;
                }
            }
        }

        // Last-resort deterministic placement. addFreshEntity still has the
        // final say; reconciliation can retry if the world rejects it.
        BlockPos fallback = target.blockPosition().offset(0, 1, 0);
        servant.moveTo(
                fallback.getX() + 0.5D,
                Mth.clamp(fallback.getY(), level.getMinBuildHeight() + 1, level.getMaxBuildHeight() - 3),
                fallback.getZ() + 0.5D,
                target.getYRot() + 180.0F,
                0.0F);
    }

    public enum StartStatus {
        STARTED,
        ALREADY_ACTIVE,
        TARGET_BUSY,
        REPLAYED_TERMINAL,
        EVENT_ID_CONFLICT,
        SPAWN_FAILED,
        NO_SERVER
    }

    public record StartResult(
            StartStatus status,
            UUID encounterId,
            @Nullable UUID servantId,
            String message) {

        public boolean success() {
            return status == StartStatus.STARTED || status == StartStatus.ALREADY_ACTIVE;
        }

        static StartResult failed(StartStatus status, UUID encounterId, String message) {
            return new StartResult(status, encounterId, null, message);
        }
    }

    public enum CloseReason {
        EXPIRED,
        OPERATOR,
        LOGOUT,
        TARGET_DEATH,
        DIMENSION_CHANGE
    }
}
