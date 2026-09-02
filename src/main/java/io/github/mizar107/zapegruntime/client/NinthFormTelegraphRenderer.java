package io.github.mizar107.zapegruntime.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.boss.combat.NinthFormBoss;
import io.github.mizar107.zapegruntime.boss.combat.NinthFormCombatGeometry;
import io.github.mizar107.zapegruntime.boss.presentation.NinthFormRenderState;
import io.github.mizar107.zapegruntime.boss.presentation.NinthFormRenderState.VisualState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Shared client telegraphs drawn from already-synced attack id/tick/yaw so
 * every nearby client sees the same authored geometry the server resolves.
 */
@Mod.EventBusSubscriber(
        modid = ZapeGRuntime.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NinthFormTelegraphRenderer {

    private NinthFormTelegraphRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        AABB search = minecraft.player.getBoundingBox().inflate(
                NinthFormCombatGeometry.CONFINEMENT_RADIUS + 16.0D);
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (NinthFormBoss boss : minecraft.level.getEntitiesOfClass(NinthFormBoss.class, search)) {
            VisualState state = NinthFormRenderState.resolve(
                    boss.combatPhase(),
                    boss.attackId(),
                    boss.attackTick(),
                    boss.brokenPointMask(),
                    boss.tickCount + event.getPartialTick());
            if (!state.telegraphing()) {
                continue;
            }
            float yaw = boss.attackYaw().orElse(boss.getYRot());
            pose.pushPose();
            pose.translate(-camera.x, -camera.y, -camera.z);
            draw(pose, lines, boss.position(), yaw, state.attack(), state.windupProgress());
            pose.popPose();
        }
        buffers.endBatch(RenderType.lines());
    }

    private static void draw(
            PoseStack pose,
            VertexConsumer lines,
            Vec3 origin,
            float yaw,
            NinthFormRenderState.AttackTiming attack,
            float progress) {
        float alpha = 0.18F + 0.55F * progress;
        switch (attack) {
            case KEEL_SWEEP -> ring(
                    pose, lines, origin, 16.0D, 0.2D, 0.72F, 0.86F, 0.90F, alpha, 48);
            case ANCHORFALL -> {
                // Anchor is not synced; the prow-forward disc matches the locked yaw.
                Vec3 impact = offset(origin, yaw, 0.0D, 12.0D);
                ring(pose, lines, impact, 5.0D, 0.15D, 0.55F, 0.78F, 0.92F, alpha, 28);
            }
            case UNDERTOW -> ring(
                    pose,
                    lines,
                    origin,
                    NinthFormCombatGeometry.CONFINEMENT_RADIUS,
                    0.05D,
                    0.30F,
                    0.62F,
                    0.86F,
                    alpha * 0.7F,
                    64);
            case DROWNED_BROADSIDE -> {
                box(pose, lines, origin, yaw, 5.0D, 34.0D, -12.0D, 12.0D, 7.0D, 0.86F, 0.42F, 0.38F, alpha);
                box(pose, lines, origin, yaw, -34.0D, -5.0D, -12.0D, 12.0D, 7.0D, 0.86F, 0.42F, 0.38F, alpha);
            }
            case WAKE_CHARGE -> corridor(pose, lines, origin, yaw, alpha);
            case NINEFOLD_GAZE -> cone(pose, lines, origin, yaw, alpha);
            case IDLE -> {
                // No telegraph while idle.
            }
        }
    }

    private static void ring(
            PoseStack pose,
            VertexConsumer lines,
            Vec3 origin,
            double radius,
            double y,
            float red,
            float green,
            float blue,
            float alpha,
            int segments) {
        Vec3 prev = origin.add(radius, y, 0.0D);
        for (int index = 1; index <= segments; index++) {
            double angle = index * (Math.PI * 2.0D) / segments;
            Vec3 next = origin.add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            line(pose, lines, prev, next, red, green, blue, alpha);
            prev = next;
        }
    }

    private static void box(
            PoseStack pose,
            VertexConsumer lines,
            Vec3 origin,
            float yaw,
            double latMin,
            double latMax,
            double fwdMin,
            double fwdMax,
            double halfHeight,
            float red,
            float green,
            float blue,
            float alpha) {
        Vec3 a = offset(origin, yaw, latMin, fwdMin);
        Vec3 b = offset(origin, yaw, latMax, fwdMin);
        Vec3 c = offset(origin, yaw, latMax, fwdMax);
        Vec3 d = offset(origin, yaw, latMin, fwdMax);
        AABB hull = new AABB(
                Math.min(Math.min(a.x, b.x), Math.min(c.x, d.x)),
                origin.y - halfHeight,
                Math.min(Math.min(a.z, b.z), Math.min(c.z, d.z)),
                Math.max(Math.max(a.x, b.x), Math.max(c.x, d.x)),
                origin.y + halfHeight,
                Math.max(Math.max(a.z, b.z), Math.max(c.z, d.z)));
        LevelRenderer.renderLineBox(pose, lines, hull, red, green, blue, alpha);
    }

    private static void corridor(
            PoseStack pose, VertexConsumer lines, Vec3 origin, float yaw, float alpha) {
        double[] forwards = {0.0D, 12.0D, 24.0D, 38.0D};
        Vec3 prevLeft = null;
        Vec3 prevRight = null;
        for (double forward : forwards) {
            double half = 4.0D + forward * 0.22D;
            Vec3 left = offset(origin, yaw, -half, forward);
            Vec3 right = offset(origin, yaw, half, forward);
            if (prevLeft != null) {
                line(pose, lines, prevLeft, left, 0.94F, 0.78F, 0.40F, alpha);
                line(pose, lines, prevRight, right, 0.94F, 0.78F, 0.40F, alpha);
            }
            line(pose, lines, left, right, 0.94F, 0.78F, 0.40F, alpha * 0.6F);
            prevLeft = left;
            prevRight = right;
        }
    }

    private static void cone(
            PoseStack pose, VertexConsumer lines, Vec3 origin, float yaw, float alpha) {
        double reach = NinthFormCombatGeometry.CONFINEMENT_RADIUS;
        double half = Math.tan(Math.toRadians(11.0D)) * reach;
        Vec3 left = offset(origin, yaw, -half, reach);
        Vec3 right = offset(origin, yaw, half, reach);
        line(pose, lines, origin, left, 0.62F, 0.92F, 0.86F, alpha);
        line(pose, lines, origin, right, 0.62F, 0.92F, 0.86F, alpha);
        line(pose, lines, left, right, 0.62F, 0.92F, 0.86F, alpha);
    }

    private static Vec3 offset(Vec3 origin, float yaw, double lateral, double forward) {
        double radians = Math.toRadians(yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        // Inverse of NinthFormCombatGeometry.localPoint.
        double dx = lateral * cos - forward * sin;
        double dz = lateral * sin + forward * cos;
        return origin.add(dx, 0.0D, dz);
    }

    private static void line(
            PoseStack pose,
            VertexConsumer consumer,
            Vec3 from,
            Vec3 to,
            float red,
            float green,
            float blue,
            float alpha) {
        Matrix4f matrix = pose.last().pose();
        Matrix3f normal = pose.last().normal();
        float dx = (float) (to.x - from.x);
        float dy = (float) (to.y - from.y);
        float dz = (float) (to.z - from.z);
        float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (length > 1.0E-4F) {
            dx /= length;
            dy /= length;
            dz /= length;
        }
        consumer.vertex(matrix, (float) from.x, (float) from.y, (float) from.z)
                .color(red, green, blue, alpha)
                .normal(normal, dx, dy, dz)
                .endVertex();
        consumer.vertex(matrix, (float) to.x, (float) to.y, (float) to.z)
                .color(red, green, blue, alpha)
                .normal(normal, dx, dy, dz)
                .endVertex();
    }
}
