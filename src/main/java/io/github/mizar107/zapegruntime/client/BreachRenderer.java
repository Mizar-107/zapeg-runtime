package io.github.mizar107.zapegruntime.client;

import io.github.mizar107.zapegruntime.scene.BreachChoreography;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Texture-free screen-space renderer for the breach sequence. It uses only
 * bounded translucent rectangles, so it remains available with shaders,
 * entity culling, low render distance and unloaded terrain.
 */
final class BreachRenderer {

    private BreachRenderer() {}

    /** Returns true only after at least one non-zero presentation primitive. */
    static boolean render(
            GuiGraphics graphics,
            int width,
            int height,
            double bodyAge,
            int bodyTicks,
            long seed) {
        if (graphics == null || width < 8 || height < 8) {
            return false;
        }
        return render(new ProofCanvas(graphics, width, height), bodyAge, bodyTicks, seed);
    }

    /** Pure proof seam used to test sub-tick ages and clipped geometry. */
    static boolean hasVisiblePrimitives(
            int width,
            int height,
            double bodyAge,
            int bodyTicks,
            long seed) {
        if (width < 8 || height < 8) {
            return false;
        }
        return render(new ProofCanvas(null, width, height), bodyAge, bodyTicks, seed);
    }

    /** Pure contract for the exact clipping/alpha rule behind render proof. */
    static boolean hasClippedNonTransparentArea(
            int width,
            int height,
            int x0,
            int y0,
            int x1,
            int y1,
            int color) {
        ProofCanvas canvas = new ProofCanvas(null, width, height);
        canvas.fill(x0, y0, x1, y1, color);
        return canvas.presented();
    }

    private static boolean render(
            ProofCanvas canvas,
            double bodyAge,
            int bodyTicks,
            long seed) {
        BreachChoreography.Frame frame =
                BreachChoreography.frame(bodyAge, bodyTicks, seed);

        int veilAlpha = alpha(154.0D * frame.veilOpacity());
        if (veilAlpha > 0) {
            canvas.fill(0, 0, canvas.width(), canvas.height(), argb(veilAlpha, 1, 2, 4));
            drawVignette(canvas, frame.veilOpacity());
        }

        int width = canvas.width();
        int height = canvas.height();
        int preferredWidth = Math.max(42, width * 7 / 20);
        int preferredHeight = Math.max(54, height * 4 / 5);
        int doorWidth = Math.max(4, Math.min(width - 4, preferredWidth));
        int doorHeight = Math.max(4, Math.min(height - 4, preferredHeight));
        int driftPixels = (int) Math.round(frame.horizontalDrift() * Math.min(9, width / 40));
        int left = Math.max(
                2,
                Math.min(width - doorWidth - 2, (width - doorWidth) / 2 + driftPixels));
        int top = (height - doorHeight) / 2;
        int right = left + doorWidth;
        int bottom = top + doorHeight;

        drawClosingRoom(canvas, left, right, frame.doorwayClosure());
        drawManifestation(canvas, left, top, right, bottom, frame);
        drawDoorFrame(canvas, left, top, right, bottom, frame.seamOpacity());
        drawSlowFaults(canvas, seed, bodyAge, frame.seamOpacity());
        return canvas.presented();
    }

    private static void drawVignette(ProofCanvas canvas, double opacity) {
        int width = canvas.width();
        int height = canvas.height();
        int bands = 7;
        int xBand = Math.max(1, width / 28);
        int yBand = Math.max(1, height / 24);
        for (int band = 0; band < bands; band++) {
            double weight = (bands - band) / (double) bands;
            int alpha = alpha(46.0D * opacity * weight);
            int xInset = band * xBand;
            int yInset = band * yBand;
            canvas.fill(xInset, yInset, width - xInset, yInset + yBand, argb(alpha, 0, 0, 1));
            canvas.fill(
                    xInset,
                    height - yInset - yBand,
                    width - xInset,
                    height - yInset,
                    argb(alpha, 0, 0, 1));
            canvas.fill(xInset, yInset, xInset + xBand, height - yInset, argb(alpha, 0, 0, 1));
            canvas.fill(
                    width - xInset - xBand,
                    yInset,
                    width - xInset,
                    height - yInset,
                    argb(alpha, 0, 0, 1));
        }
    }

    /** The room narrows around a central impossible doorway. */
    private static void drawClosingRoom(
            ProofCanvas canvas,
            int doorLeft,
            int doorRight,
            double closure) {
        int sideAlpha = alpha(176.0D * closure);
        if (sideAlpha <= 0) {
            return;
        }
        int width = canvas.width();
        int height = canvas.height();
        int leftEdge = (int) Math.round(doorLeft * closure);
        int rightEdge = width - (int) Math.round((width - doorRight) * closure);
        canvas.fill(0, 0, leftEdge, height, argb(sideAlpha, 0, 1, 2));
        canvas.fill(rightEdge, 0, width, height, argb(sideAlpha, 0, 1, 2));
    }

    private static void drawManifestation(
            ProofCanvas canvas,
            int left,
            int top,
            int right,
            int bottom,
            BreachChoreography.Frame frame) {
        int alpha = alpha(216.0D * frame.manifestationOpacity());
        if (alpha <= 0) {
            return;
        }
        int width = right - left;
        int height = bottom - top;
        int center = (left + right) / 2;
        int headWidth = Math.max(8, width / 5);
        int headTop = top + height / 7;
        int headBottom = headTop + Math.max(12, height / 5);
        int shoulderTop = headBottom - Math.max(1, height / 45);
        int bodyBottom = bottom - Math.max(2, height / 18);

        // The body is deliberately not a texture or entity. Five irregular
        // slabs imply a too-tall witness without relying on model rendering.
        canvas.fill(
                center - headWidth / 2,
                headTop,
                center + headWidth / 2,
                headBottom,
                argb(alpha, 0, 0, 1));
        canvas.fill(
                center - width / 4,
                shoulderTop,
                center + width / 4,
                shoulderTop + Math.max(3, height / 16),
                argb(alpha, 0, 0, 1));
        canvas.fill(
                center - width / 6,
                shoulderTop,
                center + width / 6,
                bodyBottom,
                argb(alpha, 0, 0, 1));
        canvas.fill(
                center - width / 4,
                shoulderTop + height / 18,
                center - width / 7,
                bodyBottom - height / 9,
                argb(alpha * 3 / 4, 0, 0, 1));
        canvas.fill(
                center + width / 7,
                shoulderTop + height / 18,
                center + width / 4,
                bodyBottom - height / 9,
                argb(alpha * 3 / 4, 0, 0, 1));

        int eyeAlpha = alpha(174.0D * frame.eyeOpacity());
        if (eyeAlpha > 0) {
            int eyeY = headTop + Math.max(3, (headBottom - headTop) * 2 / 5);
            int eyeWidth = Math.max(2, headWidth / 5);
            int gap = Math.max(2, headWidth / 8);
            canvas.fill(
                    center - gap - eyeWidth,
                    eyeY,
                    center - gap,
                    eyeY + Math.max(1, height / 90),
                    argb(eyeAlpha, 68, 92, 94));
            canvas.fill(
                    center + gap,
                    eyeY,
                    center + gap + eyeWidth,
                    eyeY + Math.max(1, height / 90),
                    argb(eyeAlpha, 68, 92, 94));
        }
    }

    private static void drawDoorFrame(
            ProofCanvas canvas,
            int left,
            int top,
            int right,
            int bottom,
            double opacity) {
        int alpha = alpha(94.0D * opacity);
        if (alpha <= 0) {
            return;
        }
        int color = argb(alpha, 22, 39, 42);
        canvas.fill(left, top, left + 2, bottom, color);
        canvas.fill(right - 2, top, right, bottom, color);
        canvas.fill(left, top, right, top + 2, color);
        int center = (left + right) / 2;
        canvas.fill(center, top + 2, center + 1, bottom, argb(alpha / 2, 37, 53, 54));
    }

    /** Sparse, slowly moving seams; never a full-screen strobe. */
    private static void drawSlowFaults(
            ProofCanvas canvas,
            long seed,
            double age,
            double opacity) {
        int alpha = alpha(36.0D * opacity);
        if (alpha <= 0) {
            return;
        }
        int width = canvas.width();
        int height = canvas.height();
        int span = Math.max(16, width / 5);
        for (int index = 0; index < 3; index++) {
            long laneSeed = seed >>> (index * 9);
            int y = Math.floorMod((int) (laneSeed + index * 71L), Math.max(1, height));
            int travel = (int) Math.round(Math.sin(age * 0.035D + index * 1.7D) * width / 12.0D);
            int x = Math.floorMod((int) (laneSeed >>> 17) + travel, width + span) - span;
            canvas.fill(x, y, Math.min(width, x + span), y + 1, argb(alpha, 31, 50, 51));
        }
    }

    /**
     * Counts only a nontransparent rectangle with a nonempty intersection
     * with the actual GUI viewport. The optional graphics target lets the
     * exact same path drive both rendering and pure proof tests.
     */
    private static final class ProofCanvas {
        private final GuiGraphics graphics;
        private final int width;
        private final int height;
        private boolean presented;

        private ProofCanvas(GuiGraphics graphics, int width, int height) {
            this.graphics = graphics;
            this.width = width;
            this.height = height;
        }

        private int width() {
            return width;
        }

        private int height() {
            return height;
        }

        private boolean presented() {
            return presented;
        }

        private void fill(int x0, int y0, int x1, int y1, int color) {
            if (((color >>> 24) & 0xFF) == 0) {
                return;
            }
            int left = Math.max(0, Math.min(x0, x1));
            int top = Math.max(0, Math.min(y0, y1));
            int right = Math.min(width, Math.max(x0, x1));
            int bottom = Math.min(height, Math.max(y0, y1));
            if (left >= right || top >= bottom) {
                return;
            }
            if (graphics != null) {
                graphics.fill(left, top, right, bottom, color);
            }
            presented = true;
        }
    }

    private static int alpha(double value) {
        return Math.max(0, Math.min(255, (int) Math.round(value)));
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return ((alpha & 0xFF) << 24)
                | ((red & 0xFF) << 16)
                | ((green & 0xFF) << 8)
                | (blue & 0xFF);
    }
}
