package io.github.mizar107.zapegruntime.client;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.scene.SceneDescriptor;
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
        SceneDescriptor descriptor = ClientSceneManager.observe(event);
        if (descriptor != null) {
            ApparitionRenderer.render(descriptor, event);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        float intensity = ClientSceneManager.effectIntensity(event.getPartialTick());
        if (intensity <= 0.0F) {
            return;
        }
        drawEdgeFaults(
                event.getGuiGraphics(),
                event.getWindow().getGuiScaledWidth(),
                event.getWindow().getGuiScaledHeight(),
                intensity,
                ClientSceneManager.visualSeed(),
                ClientSceneManager.ageWithPartial(event.getPartialTick()));
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

    private static int argb(int alpha, int red, int green, int blue) {
        return (alpha & 0xFF) << 24
                | (red & 0xFF) << 16
                | (green & 0xFF) << 8
                | (blue & 0xFF);
    }
}
