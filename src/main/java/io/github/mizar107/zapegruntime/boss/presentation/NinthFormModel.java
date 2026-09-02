package io.github.mizar107.zapegruntime.boss.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.boss.combat.NinthFormBoss;
import io.github.mizar107.zapegruntime.boss.combat.NinthFormPartKind;
import io.github.mizar107.zapegruntime.boss.presentation.NinthFormRenderState.AttackTiming;
import io.github.mizar107.zapegruntime.boss.presentation.NinthFormRenderState.VisualState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Original low-poly drowned reliquary aligned to all five server hit parts. */
public final class NinthFormModel extends EntityModel<NinthFormBoss> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ZapeGRuntime.MOD_ID, "ninth_form"), "main");

    private static final float MODEL_Y_AT_ENTITY_FLOOR = 24.0F;
    private static final float PIXELS_PER_BLOCK = 16.0F;

    private final ModelPart root;
    private final ModelPart parentHull;
    private final ModelPart prowLantern;
    private final ModelPart portMooring;
    private final ModelPart starboardMooring;
    private final ModelPart keelHeart;
    private final ModelPart armoredHullAft;

    public NinthFormModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        parentHull = root.getChild("parent_hull");
        prowLantern = root.getChild(NinthFormPartKind.PROW_LANTERN.serializedName());
        portMooring = root.getChild(NinthFormPartKind.PORT_MOORING.serializedName());
        starboardMooring =
                root.getChild(NinthFormPartKind.STARBOARD_MOORING.serializedName());
        keelHeart = root.getChild(NinthFormPartKind.KEEL_HEART.serializedName());
        armoredHullAft = root.getChild(NinthFormPartKind.ARMORED_HULL_AFT.serializedName());
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        NinthFormUvLayout.UvBox hullUv = NinthFormUvLayout.PARENT_HULL;
        CubeListBuilder hull = box(hullUv, -56.0F, -26.0F, -64.0F)
                .texOffs(NinthFormUvLayout.CROWN.u(), NinthFormUvLayout.CROWN.v())
                .addBox(-16.0F, -106.0F, -16.0F, 32.0F, 80.0F, 32.0F)
                .texOffs(NinthFormUvLayout.MAST_RIB.u(), NinthFormUvLayout.MAST_RIB.v())
                .addBox(-8.0F, -90.0F, 25.0F, 16.0F, 64.0F, 16.0F)
                .texOffs(NinthFormUvLayout.PORT_FIN.u(), NinthFormUvLayout.PORT_FIN.v())
                .addBox(-62.0F, -68.0F, -30.0F, 8.0F, 44.0F, 64.0F)
                .texOffs(
                        NinthFormUvLayout.STARBOARD_FIN.u(),
                        NinthFormUvLayout.STARBOARD_FIN.v())
                .addBox(54.0F, -68.0F, -30.0F, 8.0F, 44.0F, 64.0F)
                .texOffs(NinthFormUvLayout.PARENT_HULL.u(), NinthFormUvLayout.PARENT_HULL.v())
                .addBox(-48.0F, -30.0F, -58.0F, 96.0F, 8.0F, 18.0F)
                .texOffs(NinthFormUvLayout.CROWN.u(), NinthFormUvLayout.CROWN.v())
                .addBox(-6.0F, -118.0F, -6.0F, 12.0F, 14.0F, 12.0F);
        root.addOrReplaceChild(
                "parent_hull", hull, PartPose.offset(0.0F, modelY(3.0D), 0.0F));

        addNativePart(
                root,
                NinthFormPartKind.PROW_LANTERN,
                NinthFormUvLayout.PROW_LANTERN,
                -12.0F,
                -14.0F,
                -12.0F);
        addNativePart(
                root,
                NinthFormPartKind.PORT_MOORING,
                NinthFormUvLayout.PORT_MOORING,
                -16.0F,
                -16.0F,
                -16.0F);
        addNativePart(
                root,
                NinthFormPartKind.STARBOARD_MOORING,
                NinthFormUvLayout.STARBOARD_MOORING,
                -16.0F,
                -16.0F,
                -16.0F);
        addNativePart(
                root,
                NinthFormPartKind.KEEL_HEART,
                NinthFormUvLayout.KEEL_HEART,
                -20.0F,
                -20.0F,
                -16.0F);
        addNativePart(
                root,
                NinthFormPartKind.ARMORED_HULL_AFT,
                NinthFormUvLayout.ARMORED_HULL_AFT,
                -72.0F,
                -36.0F,
                -40.0F);

        return LayerDefinition.create(
                mesh, NinthFormUvLayout.WIDTH, NinthFormUvLayout.HEIGHT);
    }

    private static void addNativePart(
            PartDefinition root,
            NinthFormPartKind kind,
            NinthFormUvLayout.UvBox uv,
            float originX,
            float originY,
            float originZ) {
        CubeListBuilder cubes = box(uv, originX, originY, originZ);
        if (kind == NinthFormPartKind.PROW_LANTERN) {
            cubes.texOffs(uv.u(), uv.v()).addBox(originX + 6.0F, originY + 4.0F, originZ - 2.0F, 4.0F, 16.0F, 4.0F);
            cubes.texOffs(uv.u(), uv.v()).addBox(originX + 14.0F, originY + 4.0F, originZ - 2.0F, 4.0F, 16.0F, 4.0F);
        } else if (kind == NinthFormPartKind.PORT_MOORING
                || kind == NinthFormPartKind.STARBOARD_MOORING) {
            cubes.texOffs(uv.u(), uv.v()).addBox(originX + 12.0F, originY - 10.0F, originZ + 12.0F, 8.0F, 18.0F, 8.0F);
        } else if (kind == NinthFormPartKind.KEEL_HEART) {
            cubes.texOffs(uv.u(), uv.v()).addBox(originX + 8.0F, originY + 8.0F, originZ + 6.0F, 24.0F, 24.0F, 20.0F);
        }
        root.addOrReplaceChild(
                kind.serializedName(),
                cubes,
                PartPose.offset(
                        pixels(kind.lateralOffset()),
                        modelPartCenterY(kind),
                        modelForward(kind.forwardOffset())));
    }

    private static CubeListBuilder box(
            NinthFormUvLayout.UvBox uv, float x, float y, float z) {
        return CubeListBuilder.create()
                .texOffs(uv.u(), uv.v())
                .addBox(x, y, z, uv.sizeX(), uv.sizeY(), uv.sizeZ());
    }

    private static float pixels(double blocks) {
        return (float) (blocks * PIXELS_PER_BLOCK);
    }

    /* LivingEntityRenderer turns the model by 180 - yaw, so model +Z is world -forward. */
    private static float modelForward(double worldForward) {
        return -pixels(worldForward);
    }

    /* PartEntity AABBs use [verticalOffset, verticalOffset + height]. */
    private static float modelPartCenterY(NinthFormPartKind kind) {
        return modelY(kind.verticalOffset() + kind.height() / 2.0D);
    }

    private static float modelY(double worldHeight) {
        return MODEL_Y_AT_ENTITY_FLOOR - pixels(worldHeight);
    }

    @Override
    public void setupAnim(
            NinthFormBoss boss,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        setBaseVisibility(true);
        VisualState state = NinthFormRenderState.resolve(
                boss.combatPhase(),
                boss.attackId(),
                boss.attackTick(),
                boss.brokenPointMask(),
                ageInTicks);

        root.y = 0.32F * (float) Math.sin(ageInTicks * 0.055F);
        root.zRot = (float) Math.toRadians(state.rollDegrees());
        parentHull.xRot = 0.008F * (float) Math.sin(ageInTicks * 0.041F);
        prowLantern.yRot = 0.08F * (float) Math.sin(ageInTicks * 0.11F);

        float windup = state.windupProgress();
        if (state.telegraphing()) {
            animateTelegraph(state.attack(), windup, ageInTicks);
        }
        if (!state.prowAlive()) {
            prowLantern.xRot = 0.42F;
            prowLantern.y += 3.0F;
        }
        if (!state.portAlive()) {
            portMooring.zRot = 0.34F;
            portMooring.y += 4.0F;
        }
        if (!state.starboardAlive()) {
            starboardMooring.zRot = -0.34F;
            starboardMooring.y += 4.0F;
        }
        keelHeart.yScale = state.keelExposed() ? 1.0F : 0.78F;
    }

    private void animateTelegraph(AttackTiming attack, float progress, float ageInTicks) {
        float pulse = 0.5F + 0.5F * (float) Math.sin(ageInTicks * 0.42F);
        switch (attack) {
            case KEEL_SWEEP -> keelHeart.xRot = -0.52F * progress;
            case ANCHORFALL -> {
                portMooring.y -= 8.0F * progress;
                starboardMooring.y -= 8.0F * progress;
            }
            case UNDERTOW -> parentHull.xRot -= 0.06F * progress;
            case DROWNED_BROADSIDE -> {
                portMooring.zRot = -0.24F * progress;
                starboardMooring.zRot = 0.24F * progress;
            }
            case WAKE_CHARGE -> {
                root.xRot = 0.12F * progress;
                root.z += 18.0F * progress * progress;
            }
            case NINEFOLD_GAZE -> {
                float scale = 1.0F + 0.10F * progress * pulse;
                prowLantern.xScale = scale;
                prowLantern.yScale = scale;
                prowLantern.zScale = scale;
            }
            case IDLE -> {
                // No combat motion while idle.
            }
        }
    }

    public void configureEmissive(VisualState state) {
        parentHull.visible = state.telegraphing()
                || state.phase() == io.github.mizar107.zapegruntime.boss.api.NinthFormPhase.FINAL;
        prowLantern.visible = state.prowAlive();
        portMooring.visible = state.portAlive();
        starboardMooring.visible = state.starboardAlive();
        keelHeart.visible = state.keelExposed();
        armoredHullAft.visible = false;
    }

    private void setBaseVisibility(boolean visible) {
        parentHull.visible = visible;
        prowLantern.visible = visible;
        portMooring.visible = visible;
        starboardMooring.visible = visible;
        keelHeart.visible = visible;
        armoredHullAft.visible = visible;
    }

    @Override
    public void renderToBuffer(
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay,
            float red,
            float green,
            float blue,
            float alpha) {
        root.render(poseStack, consumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
