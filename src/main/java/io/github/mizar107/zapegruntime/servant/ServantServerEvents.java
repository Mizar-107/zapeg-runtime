package io.github.mizar107.zapegruntime.servant;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge lifecycle bridge. Command registration stays caller-owned for clean integration. */
@Mod.EventBusSubscriber(modid = ZapeGRuntime.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServantServerEvents {

    private ServantServerEvents() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ServantEncounterManager.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()
                && event.getEntity() instanceof ServerPlayer player
                && player.getServer() != null) {
            ServantEncounterManager.cancelForTarget(
                    player.getServer(),
                    player.getUUID(),
                    ServantEncounterManager.CloseReason.TARGET_DEATH);
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof HeraldorServant servant
                && event.getLevel() instanceof net.minecraft.server.level.ServerLevel level
                && !ServantEncounterManager.acceptsJoinedEntity(servant, level)) {
            // A pre-recovery entity that loads later cannot become a twin.
            servant.discard();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.getServer() != null
                && !ServantEncounterManager.isServerStopping(player.getServer())) {
            ServantEncounterManager.cancelForTarget(
                    player.getServer(),
                    player.getUUID(),
                    ServantEncounterManager.CloseReason.LOGOUT);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            ServantEncounterManager.cancelForTarget(
                    player.getServer(),
                    player.getUUID(),
                    ServantEncounterManager.CloseReason.DIMENSION_CHANGE);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServantEncounterManager.onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ServantEncounterManager.onServerStopping(event.getServer());
    }
}
