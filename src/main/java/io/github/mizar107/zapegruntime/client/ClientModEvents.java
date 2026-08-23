package io.github.mizar107.zapegruntime.client;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.client.os.OsScareDriver;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(
        modid = ZapeGRuntime.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientModEvents {

    private ClientModEvents() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            var toggles = OsScareConfig.toggles();
            if (!toggles.master()) {
                ZapeGRuntime.LOGGER.info(
                        "OS scare V2 opt-in disabled; legacy enabled is ignored");
                return;
            }
            ZapeGRuntime.LOGGER.info(
                    "OS scare V2 opt-in enabled; effect preflight {}",
                    OsScareDriver.instance().preflight().compactString());
        });
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        ApparitionRenderer.installModel(event.getEntityModels());
    }
}
