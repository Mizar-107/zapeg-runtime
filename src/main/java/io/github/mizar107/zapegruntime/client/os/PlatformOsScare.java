package io.github.mizar107.zapegruntime.client.os;

import io.github.mizar107.zapegruntime.scene.OsScareChoreography;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

/**
 * Production {@link OsScareHooks}: a borderless always-on-top Swing blink
 * for the face, and GLFW title/position/attention wrongness for the game
 * window.
 *
 * <p>Threading: GLFW calls happen on the client tick thread (which is the
 * thread Minecraft owns the window on); everything AWT/Swing is handed to
 * the event-dispatch thread with all state precomputed, and the EDT never
 * calls back into Minecraft. The Swing beats are additionally gated by
 * {@link OsScarePlatform} to Windows before any Toolkit class is touched —
 * a macOS AWT init under {@code -XstartOnFirstThread} can hang the JVM,
 * which no catch block contains. Every method fails silent: unsupported
 * platform, headless environment, fullscreen quirks or any toolkit
 * exception simply skips the beat. Nothing steals focus, nothing persists,
 * nothing touches files, the clipboard or the wallpaper.
 */
final class PlatformOsScare implements OsScareHooks {

    static final String FACE_RESOURCE =
            "/assets/zapeg_runtime/textures/misc/calibration_b.png";
    private static final AtomicBoolean POPUP_SHOWING = new AtomicBoolean();

    private static volatile BufferedImage faceImage;
    private static volatile boolean faceImageFailed;
    private static volatile JWindow currentPopup;

    private Integer originalX;
    private Integer originalY;

    static OsScareHooks create() {
        return new PlatformOsScare();
    }

    @Override
    public void showFacePopup(int visibleMillis, int fadeMillis) {
        // The platform gate must short-circuit first: even the isHeadless
        // probe belongs to AWT, and no Toolkit class may load off-Windows.
        if (!OsScarePlatform.popupBeatsAllowed()
                || GraphicsEnvironment.isHeadless()
                || !POPUP_SHOWING.compareAndSet(false, true)) {
            return;
        }
        try {
            SwingUtilities.invokeLater(() -> runPopup(visibleMillis, fadeMillis));
        } catch (Throwable ignored) {
            POPUP_SHOWING.set(false);
        }
    }

    @Override
    public void closePopup() {
        // POPUP_SHOWING covers both a visible popup and one still queued for
        // the EDT; when it is clear this returns without touching AWT at
        // all, so the driver's reset stays free on every platform.
        if (!OsScarePlatform.popupBeatsAllowed() || !POPUP_SHOWING.get()) {
            return;
        }
        try {
            // The EDT queue is ordered: a dispose queued after runPopup
            // always finds the window it has to close.
            SwingUtilities.invokeLater(PlatformOsScare::disposePopup);
        } catch (Throwable ignored) {
            // No toolkit, nothing shown, nothing to close.
        }
    }

    /** EDT-only: drop the popup right now; the fade timer notices and stops. */
    private static void disposePopup() {
        try {
            JWindow popup = currentPopup;
            currentPopup = null;
            if (popup != null) {
                popup.dispose();
            }
        } catch (Throwable ignored) {
            // Disposal is best-effort; the window dies with the process.
        } finally {
            POPUP_SHOWING.set(false);
        }
    }

    private static void runPopup(int visibleMillis, int fadeMillis) {
        try {
            BufferedImage image = faceImage();
            if (image == null) {
                POPUP_SHOWING.set(false);
                return;
            }
            JWindow window = new JWindow();
            window.setFocusableWindowState(false);
            window.setAlwaysOnTop(true);
            window.setBackground(new java.awt.Color(0, 0, 0, 0));
            window.getContentPane().add(new javax.swing.JLabel(
                    new javax.swing.ImageIcon(image), javax.swing.SwingConstants.CENTER));
            window.pack();
            java.awt.Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
            window.setLocation(
                    (screen.width - window.getWidth()) / 2,
                    (screen.height - window.getHeight()) / 2);
            boolean opacitySupported = true;
            try {
                window.setOpacity(0.0F);
            } catch (Throwable unsupported) {
                opacitySupported = false;
            }
            currentPopup = window;
            window.setVisible(true);
            startFade(window, visibleMillis, fadeMillis, opacitySupported);
        } catch (Throwable ignored) {
            POPUP_SHOWING.set(false);
        }
    }

    /** Fade in, hold, fade out, dispose — all on the EDT, never touching
     *  Minecraft state. Without per-window opacity the blink degrades to a
     *  plain hold-and-dispose. */
    private static void startFade(
            JWindow window, int visibleMillis, int fadeMillis, boolean opacitySupported) {
        long started = System.nanoTime();
        boolean[] failed = {false};
        Timer timer = new Timer(40, null);
        boolean fade = opacitySupported;
        timer.addActionListener(event -> {
            try {
                if (!window.isDisplayable()) {
                    // Closed early by a scene reset: nothing left to fade.
                    timer.stop();
                    return;
                }
                long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
                if (elapsedMillis >= visibleMillis) {
                    timer.stop();
                    window.dispose();
                    currentPopup = null;
                    POPUP_SHOWING.set(false);
                    return;
                }
                if (fade) {
                    float opacity;
                    if (elapsedMillis < fadeMillis) {
                        opacity = OsScareChoreography.POPUP_PEAK_OPACITY
                                * elapsedMillis / (float) fadeMillis;
                    } else if (elapsedMillis < visibleMillis - fadeMillis) {
                        opacity = OsScareChoreography.POPUP_PEAK_OPACITY;
                    } else {
                        opacity = OsScareChoreography.POPUP_PEAK_OPACITY
                                * (visibleMillis - elapsedMillis) / (float) fadeMillis;
                    }
                    window.setOpacity(Math.max(0.0F, Math.min(1.0F, opacity)));
                }
            } catch (Throwable ignored) {
                failed[0] = true;
                timer.stop();
                window.dispose();
                currentPopup = null;
                POPUP_SHOWING.set(false);
            }
        });
        timer.setRepeats(true);
        timer.start();
    }

    private static BufferedImage faceImage() {
        if (faceImageFailed) {
            return null;
        }
        BufferedImage image = faceImage;
        if (image == null) {
            synchronized (PlatformOsScare.class) {
                if (faceImage == null && !faceImageFailed) {
                    try (var stream = PlatformOsScare.class.getResourceAsStream(FACE_RESOURCE)) {
                        if (stream == null) {
                            faceImageFailed = true;
                        } else {
                            faceImage = ImageIO.read(stream);
                        }
                    } catch (Throwable ignored) {
                        faceImageFailed = true;
                    }
                }
                image = faceImage;
            }
        }
        return image;
    }

    @Override
    public void flashTaskbar() {
        // GLFW flashes the game window's own taskbar button, so the beat no
        // longer depends on the face popup existing (facePopup=false keeps
        // the flash) and touches no AWT at all. Still gated to Windows: the
        // suite is rehearsed there, and elsewhere it silently skips.
        if (!OsScarePlatform.popupBeatsAllowed()) {
            return;
        }
        try {
            long handle = windowHandle();
            if (handle != 0L) {
                GLFW.glfwRequestWindowAttention(handle);
            }
        } catch (Throwable ignored) {
            // Attention flash is a best-effort beat.
        }
    }

    @Override
    public void applyTitle(boolean glitched, long seed, int step) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.getWindow() == null) {
                return;
            }
            if (glitched) {
                minecraft.getWindow().setTitle(OsScareChoreography.glitchedTitle(seed, step));
            } else {
                restoreTitle(minecraft);
            }
        } catch (Throwable ignored) {
            // A title beat that cannot apply simply does not happen.
        }
    }

    /** GLFW has no title getter, so the current title cannot be captured for
     *  later restore. Instead the restore recomputes exactly what the game
     *  itself would show right now — the same call Minecraft makes on
     *  language and state changes — which is the exact original title for
     *  any client whose title is the vanilla one. */
    private static void restoreTitle(Minecraft minecraft) {
        minecraft.updateTitle();
    }

    @Override
    public void applyWindowPulse(int dx, int dy) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            long handle = windowHandle();
            if (handle == 0L || minecraft.getWindow().isFullscreen()) {
                return;
            }
            if (originalX == null) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    var xBuffer = stack.mallocInt(1);
                    var yBuffer = stack.mallocInt(1);
                    GLFW.glfwGetWindowPos(handle, xBuffer, yBuffer);
                    originalX = xBuffer.get(0);
                    originalY = yBuffer.get(0);
                }
            }
            GLFW.glfwSetWindowPos(handle, originalX + dx, originalY + dy);
        } catch (Throwable ignored) {
            // A window pulse that cannot apply simply does not happen.
        }
    }

    @Override
    public void restoreWindow() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.getWindow() != null) {
                restoreTitle(minecraft);
            }
            long handle = windowHandle();
            if (handle != 0L && originalX != null && originalY != null) {
                GLFW.glfwSetWindowPos(handle, originalX, originalY);
            }
        } catch (Throwable ignored) {
            // Restore is best-effort; the offsets are tiny and the title is
            // rewritten by the game on major state changes anyway.
        } finally {
            originalX = null;
            originalY = null;
        }
    }

    private static long windowHandle() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft == null || minecraft.getWindow() == null
                    ? 0L
                    : minecraft.getWindow().getWindow();
        } catch (Throwable ignored) {
            return 0L;
        }
    }
}
