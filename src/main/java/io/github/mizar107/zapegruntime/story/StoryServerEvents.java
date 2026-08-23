package io.github.mizar107.zapegruntime.story;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Isolated server event bridge; it does not touch scenes, servants, or chunks. */
@Mod.EventBusSubscriber(modid = ZapeGRuntime.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StoryServerEvents {

    private StoryServerEvents() {}

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new StoryReloadListener());
    }
}
