package io.github.mizar107.zapegruntime.boss.presentation;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.boss.combat.NinthFormEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only model and renderer registration, kept within the boss presentation slice. */
@Mod.EventBusSubscriber(
        modid = ZapeGRuntime.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class NinthFormClientEvents {

    private NinthFormClientEvents() {}

    @SubscribeEvent
    public static void registerLayerDefinitions(
            EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                NinthFormModel.LAYER_LOCATION, NinthFormModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(NinthFormEntities.NINTH_FORM.get(), NinthFormRenderer::new);
    }
}
