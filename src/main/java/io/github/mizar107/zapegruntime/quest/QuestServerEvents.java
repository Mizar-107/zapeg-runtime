package io.github.mizar107.zapegruntime.quest;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge event bridge for server-authoritative quest actions. */
@Mod.EventBusSubscriber(modid = ZapeGRuntime.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class QuestServerEvents {

    private QuestServerEvents() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            QuestActionManager.tick(event.getServer());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        QuestActionManager.handleRightClick(event);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        QuestActionManager.reset(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        QuestActionManager.reset(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        QuestActionManager.reset(event.getOriginal().getUUID());
        QuestActionManager.reset(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            QuestActionManager.reset(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        QuestActionManager.clear();
    }
}
