package io.github.mizar107.zapegruntime.boss.encounter;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.boss.api.NinthFormCombatSignal;
import io.github.mizar107.zapegruntime.boss.api.NinthFormIdentity;
import io.github.mizar107.zapegruntime.boss.combat.ForgeNinthFormEntityGateway;
import io.github.mizar107.zapegruntime.boss.combat.NinthFormBoss;
import io.github.mizar107.zapegruntime.story.StoryAdvancedEvent;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Exact Forge lifecycle binding between durable encounter authority and combat entities. */
@Mod.EventBusSubscriber(modid = ZapeGRuntime.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NinthFormServerEvents {

    private static final Map<MinecraftServer, ForgeNinthFormEntityGateway> GATEWAYS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<MinecraftServer, NinthFormSignalInbox> SIGNALS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private NinthFormServerEvents() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            drainSignals(event.getServer());
            NinthFormEncounterManager.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof NinthFormBoss boss)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = level.getServer();
        Optional<NinthFormIdentity> installed = boss.encounterIdentity();
        boolean accepted = installed.isPresent()
                && NinthFormEncounterManager.acceptsEntity(
                        server, installed.get(), boss.getUUID());
        ForgeNinthFormEntityGateway gateway = gatewayFor(server);
        if (accepted && gateway != null) {
            accepted = gateway.attachJoined(boss, installed.orElseThrow(), boss.getUUID());
        }
        if (!accepted) {
            ZapeGRuntime.LOGGER.warn(
                    "Rejected unauthorized Ninth Form entity_uuid={} dimension={}",
                    boss.getUUID(),
                    level.dimension().location());
            boss.discard();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onStoryAdvanced(StoryAdvancedEvent event) {
        NinthFormEncounterManager.queueStoryAdvance(event.server(), event.playerId());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NinthFormEncounterManager.onPlayerAvailable(player);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()
                && event.getEntity() instanceof ServerPlayer player
                && player.getServer() != null) {
            NinthFormEncounterManager.suspendForTarget(
                    player.getServer(), player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            NinthFormEncounterManager.suspendForTarget(
                    player.getServer(), player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            NinthFormEncounterManager.suspendForTarget(
                    player.getServer(), player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ForgeNinthFormEntityGateway gateway;
        synchronized (GATEWAYS) {
            gateway = GATEWAYS.computeIfAbsent(
                    server,
                    owner -> new ForgeNinthFormEntityGateway(
                            owner, signal -> queueSignal(owner, signal)));
        }
        if (!NinthFormGatewayRegistry.install(server, gateway)) {
            throw new IllegalStateException("Ninth Form combat gateway binding was refused");
        }
        NinthFormEncounterManager.onServerStarted(server);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // Terminal/phase proofs emitted earlier in this tick must become
        // durable before suspension discards their loaded projection.
        drainSignals(event.getServer());
        NinthFormEncounterManager.onServerStopping(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MinecraftServer server = event.getServer();
        ForgeNinthFormEntityGateway gateway;
        synchronized (GATEWAYS) {
            gateway = GATEWAYS.remove(server);
        }
        if (gateway != null && !NinthFormGatewayRegistry.uninstall(server, gateway)) {
            ZapeGRuntime.LOGGER.error("Ninth Form combat gateway unbind was refused");
        }
        synchronized (SIGNALS) {
            SIGNALS.remove(server);
        }
    }

    private static void queueSignal(MinecraftServer server, NinthFormCombatSignal signal) {
        NinthFormSignalInbox inbox;
        synchronized (SIGNALS) {
            inbox = SIGNALS.computeIfAbsent(server, ignored -> new NinthFormSignalInbox());
        }
        if (!inbox.offer(signal)) {
            ZapeGRuntime.LOGGER.error(
                    "Ninth Form signal inbox capacity exhausted kind={} encounter_uuid={} entity_uuid={}",
                    signal.kind(),
                    signal.identity().encounterId(),
                    signal.entityId());
        }
    }

    private static void drainSignals(MinecraftServer server) {
        NinthFormSignalInbox inbox;
        synchronized (SIGNALS) {
            inbox = SIGNALS.get(server);
        }
        if (inbox == null) {
            return;
        }
        for (NinthFormCombatSignal signal : inbox.drain()) {
            NinthFormEncounterManager.onCombatSignal(server, signal);
        }
    }

    private static ForgeNinthFormEntityGateway gatewayFor(MinecraftServer server) {
        synchronized (GATEWAYS) {
            return GATEWAYS.get(server);
        }
    }
}
