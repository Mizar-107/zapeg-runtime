package io.github.mizar107.zapegruntime.client.os;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.scene.OsEffect;
import io.github.mizar107.zapegruntime.scene.OsEffectOutcome;
import io.github.mizar107.zapegruntime.scene.OsEffectReason;
import io.github.mizar107.zapegruntime.scene.OsEffectState;
import io.github.mizar107.zapegruntime.scene.OsScareChoreography;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

/** Production GLFW/Swing hooks with bounded, observable outcomes. */
final class PlatformOsScare implements OsScareHooks {

    static final String FACE_RESOURCE =
            "/assets/zapeg_runtime/textures/misc/calibration_b.png";
    private static final AtomicBoolean POPUP_SHOWING = new AtomicBoolean();
    private static final Set<String> LOGGED_FAILURES = ConcurrentHashMap.newKeySet();

    private static volatile BufferedImage faceImage;
    private static volatile OsEffectReason faceImageFailure;
    private static volatile JWindow currentPopup;

    private Integer originalX;
    private Integer originalY;

    static OsScareHooks create() {
        return new PlatformOsScare();
    }

    @Override
    public OsEffectOutcome preflight(OsEffect effect) {
        return switch (effect) {
            case WINDOW_TITLE -> windowPreflight(effect, false);
            case WINDOW_MOTION -> windowPreflight(effect, true);
            case EXTERNAL_POPUP -> popupPreflight();
            case TASKBAR -> taskbarPreflight();
        };
    }

    private OsEffectOutcome windowPreflight(OsEffect effect, boolean requireWindowed) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.getWindow() == null || windowHandle() == 0L) {
                return failed(effect, OsEffectReason.WINDOW_UNAVAILABLE);
            }
            if (requireWindowed && minecraft.getWindow().isFullscreen()) {
                return OsEffectOutcome.unsupported(effect, OsEffectReason.FULLSCREEN);
            }
            return OsEffectOutcome.ready(effect);
        } catch (Throwable failure) {
            return failed(effect, OsEffectReason.GLFW_FAILURE);
        }
    }

    private OsEffectOutcome popupPreflight() {
        if (!OsScarePlatform.popupBeatsAllowed()) {
            return OsEffectOutcome.unsupported(
                    OsEffect.EXTERNAL_POPUP, OsEffectReason.PLATFORM_UNSUPPORTED);
        }
        try {
            if (GraphicsEnvironment.isHeadless()) {
                return OsEffectOutcome.unsupported(
                        OsEffect.EXTERNAL_POPUP, OsEffectReason.HEADLESS);
            }
        } catch (Throwable failure) {
            return failed(OsEffect.EXTERNAL_POPUP, OsEffectReason.TOOLKIT_FAILURE);
        }
        return faceImage() == null
                ? failed(
                        OsEffect.EXTERNAL_POPUP,
                        faceImageFailure == null
                                ? OsEffectReason.ASSET_INVALID
                                : faceImageFailure)
                : OsEffectOutcome.ready(OsEffect.EXTERNAL_POPUP);
    }

    private OsEffectOutcome taskbarPreflight() {
        if (!OsScarePlatform.popupBeatsAllowed()) {
            return OsEffectOutcome.unsupported(
                    OsEffect.TASKBAR, OsEffectReason.PLATFORM_UNSUPPORTED);
        }
        return windowPreflight(OsEffect.TASKBAR, false);
    }

    @Override
    public void showFacePopup(
            int visibleMillis,
            int fadeMillis,
            Consumer<OsEffectOutcome> completion) {
        OsEffectOutcome preflight = popupPreflight();
        if (preflight.state() != OsEffectState.READY) {
            complete(completion, preflight);
            return;
        }
        if (!POPUP_SHOWING.compareAndSet(false, true)) {
            complete(completion, failed(
                    OsEffect.EXTERNAL_POPUP, OsEffectReason.ALREADY_ACTIVE));
            return;
        }
        try {
            SwingUtilities.invokeLater(
                    () -> runPopup(visibleMillis, fadeMillis, completion));
        } catch (Throwable failure) {
            POPUP_SHOWING.set(false);
            complete(completion, failed(
                    OsEffect.EXTERNAL_POPUP, OsEffectReason.EDT_UNAVAILABLE));
        }
    }

    @Override
    public void closePopup() {
        if (!OsScarePlatform.popupBeatsAllowed() || !POPUP_SHOWING.get()) {
            return;
        }
        try {
            SwingUtilities.invokeLater(PlatformOsScare::disposePopup);
        } catch (Throwable failure) {
            logFailure(OsEffect.EXTERNAL_POPUP, OsEffectReason.EDT_UNAVAILABLE);
            POPUP_SHOWING.set(false);
        }
    }

    private static void disposePopup() {
        JWindow popup = currentPopup;
        currentPopup = null;
        try {
            if (popup != null && popup.isDisplayable()) {
                popup.dispose();
            }
        } catch (Throwable failure) {
            logFailure(OsEffect.EXTERNAL_POPUP, OsEffectReason.TOOLKIT_FAILURE);
        } finally {
            POPUP_SHOWING.set(false);
        }
    }

    private static void runPopup(
            int visibleMillis,
            int fadeMillis,
            Consumer<OsEffectOutcome> completion) {
        JWindow window = null;
        try {
            BufferedImage image = faceImage();
            if (image == null) {
                POPUP_SHOWING.set(false);
                complete(completion, failed(
                        OsEffect.EXTERNAL_POPUP,
                        faceImageFailure == null
                                ? OsEffectReason.ASSET_INVALID
                                : faceImageFailure));
                return;
            }
            window = new JWindow();
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
            if (!window.isShowing()) {
                finishPopup(window);
                complete(completion, failed(
                        OsEffect.EXTERNAL_POPUP, OsEffectReason.POPUP_NOT_SHOWING));
                return;
            }
            // This is the only path allowed to claim that the popup applied.
            complete(completion, OsEffectOutcome.applied(OsEffect.EXTERNAL_POPUP));
            startFade(window, visibleMillis, fadeMillis, opacitySupported);
        } catch (Throwable failure) {
            if (window != null) {
                finishPopup(window);
            } else {
                currentPopup = null;
                POPUP_SHOWING.set(false);
            }
            complete(completion, failed(
                    OsEffect.EXTERNAL_POPUP, OsEffectReason.TOOLKIT_FAILURE));
        }
    }

    private static void startFade(
            JWindow window, int visibleMillis, int fadeMillis, boolean opacitySupported) {
        long started = System.nanoTime();
        Timer timer = new Timer(40, null);
        timer.addActionListener(event -> {
            try {
                if (!window.isDisplayable()) {
                    timer.stop();
                    return;
                }
                long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
                if (elapsedMillis >= visibleMillis) {
                    timer.stop();
                    finishPopup(window);
                    return;
                }
                if (opacitySupported) {
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
            } catch (Throwable failure) {
                logFailure(OsEffect.EXTERNAL_POPUP, OsEffectReason.TOOLKIT_FAILURE);
                timer.stop();
                finishPopup(window);
            }
        });
        timer.setRepeats(true);
        timer.start();
    }

    /** EDT-only bounded cleanup; state clears even when native disposal fails. */
    private static void finishPopup(JWindow window) {
        try {
            if (window.isDisplayable()) {
                window.dispose();
            }
        } catch (Throwable failure) {
            logFailure(OsEffect.EXTERNAL_POPUP, OsEffectReason.TOOLKIT_FAILURE);
        } finally {
            if (currentPopup == window) {
                currentPopup = null;
            }
            POPUP_SHOWING.set(false);
        }
    }

    private static BufferedImage faceImage() {
        BufferedImage image = faceImage;
        if (image != null || faceImageFailure != null) {
            return image;
        }
        synchronized (PlatformOsScare.class) {
            if (faceImage == null && faceImageFailure == null) {
                try (var stream = PlatformOsScare.class.getResourceAsStream(FACE_RESOURCE)) {
                    PngAssetValidator.Validation validation = PngAssetValidator.validate(stream);
                    if (!validation.valid()) {
                        faceImageFailure = validation.reason();
                    }
                } catch (Throwable invalid) {
                    faceImageFailure = OsEffectReason.ASSET_INVALID;
                }
                if (faceImageFailure == null) {
                    try (var stream = PlatformOsScare.class.getResourceAsStream(FACE_RESOURCE)) {
                        faceImage = ImageIO.read(stream);
                        if (faceImage == null) {
                            faceImageFailure = OsEffectReason.ASSET_INVALID;
                        }
                    } catch (Throwable invalid) {
                        faceImageFailure = OsEffectReason.ASSET_INVALID;
                    }
                }
            }
            return faceImage;
        }
    }

    @Override
    public OsEffectOutcome flashTaskbar() {
        OsEffectOutcome preflight = taskbarPreflight();
        if (preflight.state() != OsEffectState.READY) {
            return preflight;
        }
        try {
            GLFW.glfwRequestWindowAttention(windowHandle());
            return OsEffectOutcome.applied(OsEffect.TASKBAR);
        } catch (Throwable failure) {
            return failed(OsEffect.TASKBAR, OsEffectReason.GLFW_FAILURE);
        }
    }

    @Override
    public OsEffectOutcome applyTitle(boolean glitched, long seed, int step) {
        OsEffectOutcome preflight = windowPreflight(OsEffect.WINDOW_TITLE, false);
        if (preflight.state() != OsEffectState.READY) {
            return preflight;
        }
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (glitched) {
                minecraft.getWindow().setTitle(OsScareChoreography.glitchedTitle(seed, step));
            } else {
                restoreTitle(minecraft);
            }
            return OsEffectOutcome.applied(OsEffect.WINDOW_TITLE);
        } catch (Throwable failure) {
            return failed(OsEffect.WINDOW_TITLE, OsEffectReason.GLFW_FAILURE);
        }
    }

    private static void restoreTitle(Minecraft minecraft) {
        minecraft.updateTitle();
    }

    @Override
    public OsEffectOutcome applyWindowPulse(int dx, int dy) {
        OsEffectOutcome preflight = windowPreflight(OsEffect.WINDOW_MOTION, true);
        if (preflight.state() != OsEffectState.READY) {
            return preflight;
        }
        try {
            long handle = windowHandle();
            if (originalX == null) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    var xBuffer = stack.mallocInt(1);
                    var yBuffer = stack.mallocInt(1);
                    GLFW.glfwGetWindowPos(handle, xBuffer, yBuffer);
                    originalX = xBuffer.get(0);
                    originalY = yBuffer.get(0);
                }
            }
            int expectedX = originalX + dx;
            int expectedY = originalY + dy;
            GLFW.glfwSetWindowPos(handle, expectedX, expectedY);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var xBuffer = stack.mallocInt(1);
                var yBuffer = stack.mallocInt(1);
                GLFW.glfwGetWindowPos(handle, xBuffer, yBuffer);
                if (xBuffer.get(0) != expectedX || yBuffer.get(0) != expectedY) {
                    return failed(OsEffect.WINDOW_MOTION, OsEffectReason.GLFW_FAILURE);
                }
            }
            return OsEffectOutcome.applied(OsEffect.WINDOW_MOTION);
        } catch (Throwable failure) {
            return failed(OsEffect.WINDOW_MOTION, OsEffectReason.GLFW_FAILURE);
        }
    }

    @Override
    public void restoreWindow() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.getWindow() != null) {
                restoreTitle(minecraft);
            }
        } catch (Throwable failure) {
            logFailure(OsEffect.WINDOW_TITLE, OsEffectReason.GLFW_FAILURE);
        }
        try {
            long handle = windowHandle();
            if (handle != 0L && originalX != null && originalY != null) {
                GLFW.glfwSetWindowPos(handle, originalX, originalY);
            }
        } catch (Throwable failure) {
            logFailure(OsEffect.WINDOW_MOTION, OsEffectReason.GLFW_FAILURE);
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
        } catch (Throwable failure) {
            return 0L;
        }
    }

    private static OsEffectOutcome failed(OsEffect effect, OsEffectReason reason) {
        logFailure(effect, reason);
        return OsEffectOutcome.failed(effect, reason);
    }

    private static void logFailure(OsEffect effect, OsEffectReason reason) {
        String key = effect.serializedName() + ":" + reason.serializedName();
        if (LOGGED_FAILURES.add(key)) {
            ZapeGRuntime.LOGGER.warn(
                    "OS effect outcome effect={} state=failed reason={}",
                    effect.serializedName(),
                    reason.serializedName());
        }
    }

    private static void complete(
            Consumer<OsEffectOutcome> completion, OsEffectOutcome outcome) {
        try {
            completion.accept(outcome);
        } catch (Throwable ignored) {
            // Reporting cannot be allowed to strand an already-created popup.
        }
    }
}
