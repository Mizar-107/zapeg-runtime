package io.github.mizar107.zapegruntime.servant.client;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.servant.ServantEntities;
import net.minecraft.client.renderer.entity.WitherSkeletonRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Vanilla renderer for Batch 1; a GeckoLib renderer can replace it later. */
@Mod.EventBusSubscriber(
        modid = ZapeGRuntime.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class ServantClientEvents {

    private ServantClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ServantEntities.SERVANT.get(), WitherSkeletonRenderer::new);
    }
}
