package io.github.mizar107.zapegruntime.client;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/** Original hooded figure mesh. Classic 64x64 humanoid UVs plus cloak/hood. */
public final class ApparitionModel {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ZapeGRuntime.MOD_ID, "apparition"), "main");
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            ZapeGRuntime.MOD_ID, "textures/entity/apparition.png");

    private ApparitionModel() {}

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
        root.getChild("head")
                .addOrReplaceChild(
                        "hood",
                        CubeListBuilder.create()
                                .texOffs(32, 0)
                                .addBox(
                                        -4.6F,
                                        -8.7F,
                                        -4.6F,
                                        9.2F,
                                        9.2F,
                                        9.2F,
                                        new CubeDeformation(0.28F)),
                        PartPose.ZERO);
        root.getChild("body")
                .addOrReplaceChild(
                        "cloak",
                        CubeListBuilder.create()
                                .texOffs(0, 32)
                                .addBox(-5.0F, 0.4F, 1.5F, 10.0F, 15.0F, 3.0F),
                        PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }
}
