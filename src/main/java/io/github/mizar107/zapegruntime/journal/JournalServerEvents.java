package io.github.mizar107.zapegruntime.journal;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Bounded automatic issuance checks; never loads chunks and never drops an item. */
@Mod.EventBusSubscriber(modid = ZapeGRuntime.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class JournalServerEvents {

    private static final int RECONCILE_INTERVAL_TICKS = 100;

    private JournalServerEvents() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JournalService.reconcile(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END
                && event.player instanceof ServerPlayer player
                && player.tickCount % RECONCILE_INTERVAL_TICKS == 0) {
            JournalService.reconcile(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        JournalService.clearSessionNotices(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        JournalService.clearAllSessionNotices();
    }
}
