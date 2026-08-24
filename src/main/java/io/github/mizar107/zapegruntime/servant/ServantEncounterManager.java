package io.github.mizar107.zapegruntime.servant;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Owns Servant spawning, bounded recovery, expiry, cancellation, and victory credit. */
public final class ServantEncounterManager {

    public static final int LIFETIME_TICKS = 120 * 20;
    private static final int RECONCILE_INTERVAL_TICKS = 20;
    private static final Set<MinecraftServer> STOPPING_SERVERS =
            Collections.newSetFromMap(new WeakHashMap<>());

    private ServantEncounterManager() {}

    public static StartResult awaken(ServerPlayer target, boolean rehearsal) {
        return awaken(target, UUID.randomUUID(), ServantArchetype.STALKER, rehearsal);
    }

    public static StartResult awaken(
            ServerPlayer target,
            ServantArchetype archetype,
            boolean rehearsal) {
        return awaken(target, UUID.randomUUID(), archetype, rehearsal);
    }

    /** Same event UUID is idempotent while active and replay-safe after a live victory. */
    public static StartResult awaken(
            ServerPlayer target,
            UUID encounterId,
            boolean rehearsal) {
        return awaken(target, encounterId, ServantArchetype.STALKER, rehearsal);
    }

    /** Same event UUID and archetype are idempotent while the encounter is active. */
    public static StartResult awaken(
            ServerPlayer target,
            UUID encounterId,
            ServantArchetype archetype,
            boolean rehearsal) {
        if (encounterId == null || archetype == null) {
            return StartResult.failed(
                    StartStatus.INVALID_REQUEST,
                    encounterId,
                    "encounter id and archetype are required");
        }
        MinecraftServer server = target.getServer();
        if (server == null) {
            return StartResult.failed(StartStatus.NO_SERVER, encounterId, "target has no server");
        }
        ServantEncounterData data = ServantEncounterData.get(server);
        if (!data.supportsCurrentSchema()) {
            return StartResult.failed(
                    StartStatus.UNSUPPORTED_SCHEMA,
                    encounterId,
                    "Servant data schema is unsupported; preserved read-only");
        }

        ServerLevel level = target.serverLevel();
        HeraldorServant servant = ServantEntities.SERVANT.get().create(level);
        if (servant == null) {
            return StartResult.failed(StartStatus.SPAWN_FAILED, encounterId, "entity creation failed");
        }
        long gameTime = server.overworld().getGameTime();
        if (gameTime < 0L || gameTime > Long.MAX_VALUE - LIFETIME_TICKS) {
            return StartResult.failed(
                    StartStatus.INVALID_REQUEST, encounterId, "invalid world game time");
        }
        long deadline = gameTime + LIFETIME_TICKS;
        servant.configure(encounterId, target.getUUID(), rehearsal, deadline, archetype);
        ServantEncounter proposed;
        try {
            proposed = new ServantEncounter(
                    encounterId,
                    target.getUUID(),
                    servant.getUUID(),
                    level.dimension().location().toString(),
                    rehearsal,
                    deadline,
                    false,
                    archetype);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            return StartResult.failed(
                    StartStatus.INVALID_REQUEST, encounterId, "invalid encounter identity");
        }
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
            case REPLAYED_LIVE_VICTORY -> {
                return StartResult.failed(
                        StartStatus.REPLAYED_LIVE_VICTORY,
                        encounterId,
                        "live victory already consumed this event");
            }
            case EVENT_ID_CONFLICT -> {
                return StartResult.failed(
                        StartStatus.EVENT_ID_CONFLICT,
                        encounterId,
                        "encounter id belongs to another target or mode");
            }
            case ACTIVE_CAPACITY_EXHAUSTED, VICTORY_CAPACITY_EXHAUSTED -> {
                return StartResult.failed(
                        StartStatus.CAPACITY_EXHAUSTED,
                        encounterId,
                        "Servant ledger capacity exhausted");
            }
            case UNSUPPORTED_SCHEMA -> {
                return StartResult.failed(
                        StartStatus.UNSUPPORTED_SCHEMA,
                        encounterId,
                        "Servant data schema is unsupported; preserved read-only");
            }
            case STARTED -> {
                // Continue below.
            }
        }

        if (ServantSpawnPolicy.placeSafely(level, servant, target).isEmpty()) {
            data.rollbackSpawn(encounterId);
            return StartResult.failed(
                    StartStatus.NO_SAFE_SPAWN,
                    encounterId,
                    "no safe loaded spawn candidate");
        }

        if (!level.addFreshEntity(servant)) {
            data.rollbackSpawn(encounterId);
            return StartResult.failed(StartStatus.SPAWN_FAILED, encounterId, "world rejected entity");
        }

        ZapeGRuntime.LOGGER.info(
                "Servant started encounter={} target_uuid={} entity={} archetype={} rehearsal={} deadline={}",
                encounterId,
                target.getUUID(),
                servant.getUUID(),
                archetype.id(),
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
        if (!data.supportsCurrentSchema()) {
            return;
        }
        long now = server.overworld().getGameTime();
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
    }

    /** Called only after LivingEntity.die changed its protected dead flag. */
    public static void onCommittedDeath(HeraldorServant servant, UUID killerId) {
        if (!(servant.level() instanceof ServerLevel level) || servant.encounterId() == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        ServantEncounterData data = ServantEncounterData.get(server);
        ServantEncounterData.FinishResult result = data.finishVictory(
                servant.encounterId(), servant.getUUID(), killerId, servant.archetype());
        switch (result) {
            case LIVE_CREDITED -> {
                data.liveVictory(servant.encounterId()).ifPresentOrElse(
                        barrier -> ServantProgressionSync.syncBarrier(server, barrier),
                        () -> ZapeGRuntime.LOGGER.error(
                                "Missing durable Servant victory after credit encounter={}",
                                servant.encounterId()));
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
            case UNSUPPORTED_SCHEMA -> ZapeGRuntime.LOGGER.error(
                    "Servant victory could not be recorded encounter={} result={}",
                    servant.encounterId(),
                    result);
        }
    }

    /**
     * EntityJoinLevelEvent calls this before accepting a persisted Servant.
     * A later-loaded pre-recovery entity fails the exact entity-UUID match.
     */
    public static boolean acceptsJoinedEntity(HeraldorServant servant, ServerLevel level) {
        ServantEncounterData data = ServantEncounterData.get(level.getServer());
        if (!data.supportsCurrentSchema()) {
            return true;
        }
        UUID encounterId = servant.encounterId();
        if (encounterId == null) {
            return false;
        }
        Optional<ServantEncounter> active = data.findByEncounter(encounterId);
        return ServantJoinPolicy.accepts(
                active.orElse(null),
                servant.encounterId(),
                servant.designatedTargetId(),
                servant.getUUID(),
                level.dimension().location().toString(),
                servant.rehearsal(),
                servant.deadlineGameTime(),
                servant.archetype());
    }

    public static boolean cancelForTarget(
            MinecraftServer server,
            UUID targetId,
            CloseReason reason) {
        Optional<ServantEncounter> active = ServantEncounterData.get(server).activeFor(targetId);
        return active.isPresent() && close(server, active.get(), reason);
    }

    public static Optional<ServantEncounter> activeFor(MinecraftServer server, UUID targetId) {
        return ServantEncounterData.get(server).activeFor(targetId);
    }

    public static int victoryCount(MinecraftServer server, UUID targetId) {
        return ServantProgressionSync.victoryCount(server, targetId);
    }

    public static int victoryCount(
            MinecraftServer server,
            UUID targetId,
            ServantArchetype archetype) {
        ServantEncounterData data = ServantEncounterData.get(server);
        return data.supportsCurrentSchema()
                ? data.victoryCount(targetId, archetype)
                : -1;
    }

    public static Optional<HeraldorServant.CombatSnapshot> combatSnapshot(
            MinecraftServer server,
            ServantEncounter encounter) {
        ServerLevel level = resolveLevel(server, encounter.dimension());
        if (level == null) {
            return Optional.empty();
        }
        Entity loaded = level.getEntity(encounter.servantId());
        return loaded instanceof HeraldorServant servant && servant.identityMatches(encounter)
                ? Optional.of(servant.combatSnapshot())
                : Optional.empty();
    }

    public static void onServerStarted(MinecraftServer server) {
        STOPPING_SERVERS.remove(server);
        ServantProgressionSync.replayAll(server);
    }

    public static void onServerStopping(MinecraftServer server) {
        STOPPING_SERVERS.add(server);
    }

    public static boolean isServerStopping(MinecraftServer server) {
        return STOPPING_SERVERS.contains(server);
    }

    private static void reconcile(MinecraftServer server, ServantEncounter encounter) {
        ServerPlayer target = server.getPlayerList().getPlayer(encounter.targetId());
        if (target == null) {
            return;
        }
        ServerLevel level = resolveLevel(server, encounter.dimension());
        if (level == null || target.serverLevel() != level) {
            close(server, encounter, CloseReason.DIMENSION_CHANGE);
            return;
        }

        Entity loaded = level.getEntity(encounter.servantId());
        if (loaded instanceof HeraldorServant servant && servant.identityMatches(encounter)) {
            return;
        }
        if (loaded instanceof HeraldorServant invalid) {
            invalid.discard();
        }

        ServantEncounterData data = ServantEncounterData.get(server);
        ServantEncounterData.RecoveryClaim claim = data.claimRecovery(encounter.encounterId());
        if (claim == ServantEncounterData.RecoveryClaim.CLAIMED) {
            recoverOnce(server, level, target, encounter);
        } else if (claim == ServantEncounterData.RecoveryClaim.ALREADY_ATTEMPTED) {
            close(server, encounter, CloseReason.MISSING_AFTER_RECOVERY);
        }
    }

    private static void recoverOnce(
            MinecraftServer server,
            ServerLevel level,
            ServerPlayer target,
            ServantEncounter oldRecord) {
        HeraldorServant replacement = ServantEntities.SERVANT.get().create(level);
        if (replacement == null) {
            close(server, oldRecord, CloseReason.RECOVERY_FAILED);
            return;
        }
        replacement.configure(
                oldRecord.encounterId(),
                oldRecord.targetId(),
                oldRecord.rehearsal(),
                oldRecord.deadlineGameTime(),
                oldRecord.archetype());
        if (ServantSpawnPolicy.placeSafely(level, replacement, target).isEmpty()) {
            close(server, oldRecord, CloseReason.NO_SAFE_RECOVERY_SPAWN);
            return;
        }

        ServantEncounterData data = ServantEncounterData.get(server);
        if (!data.replaceRecoveredEntity(oldRecord.encounterId(), replacement.getUUID())) {
            close(server, oldRecord, CloseReason.RECOVERY_FAILED);
            return;
        }
        if (!level.addFreshEntity(replacement)) {
            data.close(oldRecord.encounterId());
            ZapeGRuntime.LOGGER.error(
                    "World rejected replacement Servant encounter={}", oldRecord.encounterId());
            return;
        }
        ZapeGRuntime.LOGGER.warn(
                "Reconciled missing Servant once encounter={} old_entity={} new_entity={}",
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
            Entity loaded = level.getEntity(encounter.servantId());
            if (loaded instanceof HeraldorServant servant) {
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

    @Nullable
    private static ServerLevel resolveLevel(MinecraftServer server, String rawDimension) {
        ResourceLocation id = ResourceLocation.tryParse(rawDimension);
        if (id == null) {
            return null;
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        return server.getLevel(key);
    }

    public enum StartStatus {
        STARTED,
        ALREADY_ACTIVE,
        TARGET_BUSY,
        REPLAYED_LIVE_VICTORY,
        EVENT_ID_CONFLICT,
        CAPACITY_EXHAUSTED,
        NO_SAFE_SPAWN,
        SPAWN_FAILED,
        NO_SERVER,
        INVALID_REQUEST,
        UNSUPPORTED_SCHEMA
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
        DIMENSION_CHANGE,
        RECOVERY_FAILED,
        NO_SAFE_RECOVERY_SPAWN,
        MISSING_AFTER_RECOVERY
    }
}
