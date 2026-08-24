package io.github.mizar107.zapegruntime.director;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.story.StoryAdvancedEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge lifecycle adapter; all campaign decisions remain in {@link HeraldorDirector}. */
@Mod.EventBusSubscriber(modid = ZapeGRuntime.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DirectorServerEvents {

    private DirectorServerEvents() {}

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new DirectorSceneReloadListener());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            HeraldorDirector.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onStoryAdvanced(StoryAdvancedEvent event) {
        // Forge events are synchronous. Queue only; never submit another fact
        // reentrantly from inside StoryService's committed transition.
        HeraldorDirector.queueReconciliation(event.server(), event.playerId());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            HeraldorDirector.queueReconciliation(player.getServer(), player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity().getServer() != null) {
            CampaignServantScheduler.clearTarget(
                    event.getEntity().getServer(), event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity().getServer() != null) {
            CampaignServantScheduler.clearTarget(
                    event.getEntity().getServer(), event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getEntity().getServer() != null) {
            CampaignServantScheduler.clearTarget(
                    event.getEntity().getServer(), event.getOriginal().getUUID());
            CampaignServantScheduler.clearTarget(
                    event.getEntity().getServer(), event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        HeraldorDirector.onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        HeraldorDirector.onServerStopping(event.getServer());
    }
}
