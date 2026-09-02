package io.github.mizar107.zapegruntime.servant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.servant.HeraldorServant;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Custom Servant renderer: original mesh, archetype textures, no nametag. */
public final class ServantRenderer
        extends HumanoidMobRenderer<HeraldorServant, HumanoidModel<HeraldorServant>> {

    private static final ResourceLocation STALKER = ResourceLocation.fromNamespaceAndPath(
            ZapeGRuntime.MOD_ID, "textures/entity/servant_stalker.png");
    private static final ResourceLocation HERALD = ResourceLocation.fromNamespaceAndPath(
            ZapeGRuntime.MOD_ID, "textures/entity/servant_herald.png");
    private static final ResourceLocation BINDER = ResourceLocation.fromNamespaceAndPath(
            ZapeGRuntime.MOD_ID, "textures/entity/servant_binder.png");

    public ServantRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new HumanoidModel<>(context.bakeLayer(ServantModel.LAYER_LOCATION)),
                0.55F);
    }

    @Override
    protected boolean shouldShowName(HeraldorServant servant) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(HeraldorServant servant) {
        return switch (servant.archetype()) {
            case STALKER -> STALKER;
            case HERALD -> HERALD;
            case BINDER -> BINDER;
        };
    }

    @Override
    protected void scale(HeraldorServant servant, PoseStack pose, float partialTick) {
        pose.scale(1.06F, 1.14F, 1.06F);
    }
}
