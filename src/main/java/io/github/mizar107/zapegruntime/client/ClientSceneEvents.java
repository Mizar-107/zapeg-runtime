package io.github.mizar107.zapegruntime.client;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.scene.SceneProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = ZapeGRuntime.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientSceneEvents {

    private ClientSceneEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientSceneManager.tick();
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        ClientSceneManager.RenderSnapshot snapshot = ClientSceneManager.observe(event);
        if (snapshot != null) {
            ApparitionRenderer.render(snapshot, event);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        float intensity = ClientSceneManager.guiEffectIntensity(event.getPartialTick());
        if (intensity <= 0.0F) {
            return;
        }
        SceneProfile profile = ClientSceneManager.activeProfile();
        if (profile == null) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        long seed = ClientSceneManager.visualSeed();
        double age = ClientSceneManager.ageWithPartial(event.getPartialTick());
        switch (profile) {
            case ECHO_01 -> drawEdgeFaults(graphics, width, height, intensity, seed, age);
            case THRESHOLD_01 -> drawThresholdSlit(
                    graphics,
                    width,
                    height,
                    intensity,
                    seed,
                    age,
                    ClientSceneManager.gazeProgress());
            case MOTION_ECHO_01 -> drawMotionEchoFaults(
                    graphics,
                    width,
                    height,
                    intensity,
                    seed,
                    age);
            case LIGHT_FAULT_01 -> drawLightFault(
                    graphics,
                    width,
                    height,
                    intensity,
                    seed,
                    age);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientSceneManager.clearWithoutAcknowledgement();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel) {
            ClientSceneManager.clearWithoutAcknowledgement();
        }
    }

    private static void drawEdgeFaults(
            GuiGraphics graphics,
            int width,
            int height,
            float intensity,
            long seed,
            double age) {
        int baseAlpha = Math.max(5, Math.min(42, Math.round(42.0F * intensity)));
        int phase = (int) Math.floor(age * 0.65D + Math.floorMod(seed, 97L));
        for (int index = 0; index < 4; index++) {
            int y = Math.floorMod(phase * 17 + index * 53, Math.max(1, height));
            int length = 10 + Math.floorMod((int) (seed >>> (index * 7)), Math.max(11, width / 5));
            int thickness = index == 0 ? 2 : 1;
            int alpha = Math.max(3, baseAlpha - index * 5);
            int dark = argb(alpha, 2, 2, 5);
            int cyan = argb(Math.max(2, alpha / 2), 0, 90, 105);
            int red = argb(Math.max(2, alpha / 2), 120, 0, 8);
            graphics.fill(0, y, Math.min(width, length), Math.min(height, y + thickness), dark);
            graphics.fill(
                    Math.max(0, width - length),
                    Math.max(0, height - y - thickness),
                    width,
                    Math.max(0, height - y),
                    dark);
            graphics.fill(0, Math.min(height, y + 2), Math.min(width, length / 2), Math.min(height, y + 3), cyan);
            graphics.fill(
                    Math.max(0, width - length / 2),
                    Math.max(0, height - y - 3),
                    width,
                    Math.max(0, height - y - 2),
                    red);
        }
    }

    private static void drawThresholdSlit(
            GuiGraphics graphics,
            int width,
            int height,
            float intensity,
            long seed,
            double age,
            float gazeProgress) {
        boolean left = (seed & 1L) == 0L;
        int maxDepth = Math.max(3, Math.min(18, width / 18));
        int depth = Math.max(
                2,
                Math.round(maxDepth * intensity * (1.0F - gazeProgress * 0.55F)));
        int alpha = Math.max(8, Math.min(58, Math.round(58.0F * intensity)));
        int edgeColor = argb(alpha, 1, 2, 4);
        int seamColor = argb(Math.max(3, alpha / 3), 4, 66, 70);
        if (left) {
            graphics.fill(0, 0, depth, height, edgeColor);
            graphics.fill(depth, 0, Math.min(width, depth + 1), height, seamColor);
        } else {
            graphics.fill(Math.max(0, width - depth), 0, width, height, edgeColor);
            graphics.fill(
                    Math.max(0, width - depth - 1),
                    0,
                    Math.max(0, width - depth),
                    height,
                    seamColor);
        }

        int slitY = Math.floorMod(
                (int) Math.floor(age * 0.33D + Math.floorMod(seed, 113L)),
                Math.max(1, height));
        int slitLength = Math.max(8, width / 9);
        int slitStart = left ? 0 : Math.max(0, width - slitLength);
        graphics.fill(
                slitStart,
                slitY,
                left ? Math.min(width, slitLength) : width,
                Math.min(height, slitY + 1),
                argb(Math.max(2, alpha / 4), 55, 1, 7));
    }

    private static void drawMotionEchoFaults(
            GuiGraphics graphics,
            int width,
            int height,
            float intensity,
            long seed,
            double age) {
        int alpha = Math.max(3, Math.min(24, Math.round(24.0F * intensity)));
        int drift = Math.floorMod((int) Math.floor(age * 0.9D), 11) - 5;
        for (int copy = 0; copy < 3; copy++) {
            int y = Math.floorMod(
                    (int) (seed >>> (copy * 11)) + copy * 71 + (int) age,
                    Math.max(1, height));
            int inset = 8 + copy * 6;
            int color = argb(Math.max(2, alpha - copy * 5), 8, 45, 52);
            graphics.fill(
                    Math.max(0, inset + drift * copy),
                    y,
                    Math.max(0, width - inset + drift * copy),
                    Math.min(height, y + 1),
                    color);
        }
    }

    private static void drawLightFault(
            GuiGraphics graphics,
            int width,
            int height,
            float intensity,
            long seed,
            double age) {
        int washAlpha = Math.max(4, Math.min(30, Math.round(30.0F * intensity)));
        graphics.fill(0, 0, width, height, argb(washAlpha, 1, 5, 8));

        int step = Math.max(1, Math.min(width, height) / 90);
        int edgeAlpha = Math.max(8, Math.min(64, Math.round(64.0F * intensity)));
        for (int layer = 0; layer < 6; layer++) {
            int inset = layer * step;
            int next = inset + step;
            int alpha = Math.max(2, edgeAlpha - layer * Math.max(1, edgeAlpha / 7));
            int color = argb(alpha, 0, 1, 3);
            graphics.fill(inset, inset, Math.max(inset, width - inset), next, color);
            graphics.fill(
                    inset,
                    Math.max(next, height - next),
                    Math.max(inset, width - inset),
                    Math.max(next, height - inset),
                    color);
            graphics.fill(inset, next, next, Math.max(next, height - next), color);
            graphics.fill(
                    Math.max(next, width - next),
                    next,
                    Math.max(next, width - inset),
                    Math.max(next, height - next),
                    color);
        }

        int bandX = Math.floorMod(
                (int) Math.floor(age * 0.21D + Math.floorMod(seed, 131L)),
                Math.max(1, width));
        graphics.fill(
                bandX,
                0,
                Math.min(width, bandX + Math.max(1, width / 180)),
                height,
                argb(Math.max(2, washAlpha / 2), 2, 38, 43));
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (alpha & 0xFF) << 24
                | (red & 0xFF) << 16
                | (green & 0xFF) << 8
                | (blue & 0xFF);
    }
}
