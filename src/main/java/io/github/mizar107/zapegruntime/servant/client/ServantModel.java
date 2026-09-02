package io.github.mizar107.zapegruntime.servant.client;

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

/** Original Servant silhouette: humanoid body with a cowl and trailing wrap. */
public final class ServantModel {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ZapeGRuntime.MOD_ID, "servant"), "main");

    private ServantModel() {}

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.05F), 0.0F);
        PartDefinition root = mesh.getRoot();
        root.getChild("head")
                .addOrReplaceChild(
                        "cowl",
                        CubeListBuilder.create()
                                .texOffs(32, 0)
                                .addBox(
                                        -4.4F,
                                        -8.5F,
                                        -4.4F,
                                        8.8F,
                                        9.0F,
                                        8.8F,
                                        new CubeDeformation(0.45F)),
                        PartPose.ZERO);
        root.getChild("body")
                .addOrReplaceChild(
                        "wrap",
                        CubeListBuilder.create()
                                .texOffs(0, 32)
                                .addBox(-4.6F, 8.0F, -2.6F, 9.2F, 8.0F, 5.2F),
                        PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }
}
