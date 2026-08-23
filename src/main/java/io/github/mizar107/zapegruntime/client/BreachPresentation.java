package io.github.mizar107.zapegruntime.client;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * One render-thread coordinator for every breach surface. Routing is mutually
 * exclusive, and an event may claim at most one presentation attempt in a
 * rendered level frame even if another mod causes duplicate Forge callbacks.
 */
final class BreachPresentation {

    enum Surface {
        HUD_POST,
        SCREEN_POST,
        HIDDEN_HUD_AFTER_LEVEL
    }

    private long frameId;
    private final Set<UUID> claimedEvents = new HashSet<>();

    /** Called exactly once from the AFTER_LEVEL stage. */
    void beginFrame() {
        frameId = frameId == Long.MAX_VALUE ? 0L : frameId + 1L;
        claimedEvents.clear();
    }

    boolean present(Surface surface, GuiGraphics graphics, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientSceneManager.BreachPresentationSnapshot snapshot =
                ClientSceneManager.breachPresentationSnapshot(partialTick);
        if (snapshot == null || graphics == null) {
            return false;
        }
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        return present(
                surface,
                minecraft.screen != null,
                minecraft.options.hideGui,
                snapshot.eventId(),
                () -> BreachRenderer.render(
                        graphics,
                        width,
                        height,
                        snapshot.bodyAge(),
                        snapshot.bodyTicks(),
                        snapshot.visualSeed()),
                graphics::flush,
                () -> ClientSceneManager.markBreachFramePresented(
                        snapshot.eventId(), snapshot.profile()));
    }

    /** Pure routing/deduplication seam used by focused tests. */
    boolean present(
            Surface surface,
            boolean hasScreen,
            boolean hideGui,
            UUID eventId,
            BooleanSupplier renderer,
            Runnable submit,
            Runnable proof) {
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(renderer, "renderer");
        Objects.requireNonNull(submit, "submit");
        Objects.requireNonNull(proof, "proof");
        if (!routesTo(surface, hasScreen, hideGui) || !claimedEvents.add(eventId)) {
            return false;
        }
        boolean presented = renderer.getAsBoolean();
        if (presented) {
            // GuiGraphics is buffered. Status becomes proof only after the
            // nonempty primitive batch is successfully submitted.
            submit.run();
            proof.run();
        }
        return presented;
    }

    static boolean routesTo(Surface surface, boolean hasScreen, boolean hideGui) {
        return switch (surface) {
            case HUD_POST -> !hasScreen && !hideGui;
            case SCREEN_POST -> hasScreen;
            case HIDDEN_HUD_AFTER_LEVEL -> !hasScreen && hideGui;
        };
    }
}
