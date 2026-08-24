package io.github.mizar107.zapegruntime.server;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.boss.encounter.NinthFormCommands;
import io.github.mizar107.zapegruntime.director.DirectorCommands;
import io.github.mizar107.zapegruntime.director.VoiceCommands;
import io.github.mizar107.zapegruntime.director.VoiceRehearsalManager;
import io.github.mizar107.zapegruntime.scene.CancelReason;
import io.github.mizar107.zapegruntime.servant.ServantCommands;
import io.github.mizar107.zapegruntime.timeline.TimelineCommands;
import io.github.mizar107.zapegruntime.timeline.TimelineReloadListener;
import io.github.mizar107.zapegruntime.timeline.TimelineServerManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;

@Mod.EventBusSubscriber(modid = ZapeGRuntime.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SceneServerEvents {

    private SceneServerEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        SceneCommands.register(event);
        HeraldorCommands.register(event, root -> {
            DirectorCommands.attach(root);
            VoiceCommands.attach(root);
            ServantCommands.attach(root);
            TimelineCommands.attach(root);
            NinthFormCommands.attach(root);
        });
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new TimelineReloadListener());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            SceneServerManager.tick(event.getServer());
            TimelineServerManager.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity().getServer() != null) {
            TimelineServerManager.onLogout(
                    event.getEntity().getServer(), event.getEntity().getUUID());
        }
        SceneServerManager.cancelForPlayer(event.getEntity().getUUID(), CancelReason.LOGOUT);
        VoiceRehearsalManager.clearTarget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.getServer() != null) {
            TimelineServerManager.onDimensionChange(player.getServer(), player);
        }
        SceneServerManager.cancelForPlayer(
                event.getEntity().getUUID(),
                CancelReason.DIMENSION_CHANGE);
        VoiceRehearsalManager.clearTarget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.getServer() != null) {
                TimelineServerManager.onDeath(player.getServer(), player);
            }
            SceneServerManager.cancelForPlayer(player.getUUID(), CancelReason.DEATH);
            VoiceRehearsalManager.clearTarget(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TimelineServerManager.onServerStopping(event.getServer());
        SceneServerManager.shutdown();
        VoiceRehearsalManager.shutdown();
    }
}
