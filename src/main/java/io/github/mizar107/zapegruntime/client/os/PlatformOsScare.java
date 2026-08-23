package io.github.mizar107.zapegruntime.client.os;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.scene.OsCapabilityState;
import io.github.mizar107.zapegruntime.scene.OsCleanupState;
import io.github.mizar107.zapegruntime.scene.OsEffect;
import io.github.mizar107.zapegruntime.scene.OsEffectReason;
import io.github.mizar107.zapegruntime.scene.OsPrimaryState;
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

/** Production GLFW/Swing boundary with proof-aware lifecycle results. */
final class PlatformOsScare implements OsScareHooks {

    static final String FACE_RESOURCE =
            "/assets/zapeg_runtime/textures/misc/calibration_b.png";
    private static final AtomicBoolean POPUP_ACTIVE = new AtomicBoolean();
    private static final Set<String> LOGGED_FAILURES = ConcurrentHashMap.newKeySet();

    private static volatile BufferedImage faceImage;
    private static volatile OsEffectReason faceImageFailure;
    private static volatile JWindow currentPopup;
    private static volatile PrimaryResult currentPopupPrimary =
            new PrimaryResult(OsPrimaryState.NOT_REQUESTED, OsEffectReason.NONE);

    private Integer originalX;
    private Integer originalY;
    private boolean motionActive;

    static OsScareHooks create() {
        return new PlatformOsScare();
    }

    @Override
    public CapabilityResult preflight(OsEffect effect) {
        return switch (effect) {
            case WINDOW_TITLE -> windowPreflight(effect, false, false);
            case WINDOW_MOTION -> windowPreflight(effect, true, true);
            case EXTERNAL_POPUP -> popupPreflight();
            case TASKBAR -> taskbarPreflight();
        };
    }

    private CapabilityResult windowPreflight(
            OsEffect effect, boolean requireWindowed, boolean requireCleanOrigin) {
        if (requireCleanOrigin && (motionActive || originalX != null || originalY != null)) {
            return capabilityFailed(effect, OsEffectReason.CLEANUP_FAILED);
        }
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.getWindow() == null || windowHandle() == 0L) {
                return capabilityFailed(effect, OsEffectReason.WINDOW_UNAVAILABLE);
            }
            if (requireWindowed && minecraft.getWindow().isFullscreen()) {
                return new CapabilityResult(
                        OsCapabilityState.UNSUPPORTED, OsEffectReason.FULLSCREEN);
            }
            return new CapabilityResult(OsCapabilityState.READY, OsEffectReason.NONE);
        } catch (Throwable failure) {
            return capabilityFailed(effect, OsEffectReason.GLFW_FAILURE);
        }
    }

    private CapabilityResult popupPreflight() {
        if (!OsScarePlatform.popupBeatsAllowed()) {
            return new CapabilityResult(
                    OsCapabilityState.UNSUPPORTED, OsEffectReason.PLATFORM_UNSUPPORTED);
        }
        if (POPUP_ACTIVE.get() || currentPopup != null) {
            return capabilityFailed(OsEffect.EXTERNAL_POPUP, OsEffectReason.ALREADY_ACTIVE);
        }
        try {
            if (GraphicsEnvironment.isHeadless()) {
                return new CapabilityResult(
                        OsCapabilityState.UNSUPPORTED, OsEffectReason.HEADLESS);
            }
        } catch (Throwable failure) {
            return capabilityFailed(
                    OsEffect.EXTERNAL_POPUP, OsEffectReason.TOOLKIT_FAILURE);
        }
        return faceImage() == null
                ? capabilityFailed(
                        OsEffect.EXTERNAL_POPUP,
                        faceImageFailure == null
                                ? OsEffectReason.ASSET_INVALID
                                : faceImageFailure)
                : new CapabilityResult(OsCapabilityState.READY, OsEffectReason.NONE);
    }

    private CapabilityResult taskbarPreflight() {
        if (!OsScarePlatform.popupBeatsAllowed()) {
            return new CapabilityResult(
                    OsCapabilityState.UNSUPPORTED, OsEffectReason.PLATFORM_UNSUPPORTED);
        }
        return windowPreflight(OsEffect.TASKBAR, false, false);
    }

    @Override
    public void showFacePopup(
            int visibleMillis,
            int fadeMillis,
            Consumer<LifecycleUpdate> completion) {
        CapabilityResult capability = popupPreflight();
        if (capability.state() != OsCapabilityState.READY) {
            emit(completion, new LifecycleUpdate(
                    primaryFailed(OsEffect.EXTERNAL_POPUP, OsEffectReason.TOOLKIT_FAILURE),
                    new CleanupResult(OsCleanupState.NOT_REQUIRED, OsEffectReason.NONE)));
            return;
        }
        if (!POPUP_ACTIVE.compareAndSet(false, true)) {
            emit(completion, new LifecycleUpdate(
                    primaryFailed(OsEffect.EXTERNAL_POPUP, OsEffectReason.ALREADY_ACTIVE),
                    new CleanupResult(OsCleanupState.NOT_REQUIRED, OsEffectReason.NONE)));
            return;
        }
        currentPopupPrimary =
                new PrimaryResult(OsPrimaryState.REQUESTED, OsEffectReason.NONE);
        try {
            SwingUtilities.invokeLater(
                    () -> runPopup(visibleMillis, fadeMillis, completion));
        } catch (Throwable failure) {
            POPUP_ACTIVE.set(false);
            currentPopupPrimary = primaryFailed(
                    OsEffect.EXTERNAL_POPUP, OsEffectReason.EDT_UNAVAILABLE);
            emit(completion, new LifecycleUpdate(
                    currentPopupPrimary,
                    new CleanupResult(OsCleanupState.NOT_REQUIRED, OsEffectReason.NONE)));
        }
    }

    private static void runPopup(
            int visibleMillis,
            int fadeMillis,
            Consumer<LifecycleUpdate> completion) {
        JWindow window = null;
        try {
            BufferedImage image = faceImage();
            if (image == null) {
                failPopupBeforeShow(completion,
                        faceImageFailure == null
                                ? OsEffectReason.ASSET_INVALID
                                : faceImageFailure);
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
                failPopup(window, completion, OsEffectReason.POPUP_NOT_SHOWING);
                return;
            }
            if (!opacitySupported) {
                // Opaque degradation: isShowing is sufficient because no zero
                // opacity was applied.
                currentPopupPrimary =
                        new PrimaryResult(OsPrimaryState.APPLIED, OsEffectReason.NONE);
                emit(completion, new LifecycleUpdate(
                        currentPopupPrimary,
                        new CleanupResult(OsCleanupState.PENDING, OsEffectReason.NONE)));
            }
            startFade(window, visibleMillis, fadeMillis, opacitySupported, completion);
        } catch (Throwable failure) {
            if (window == null) {
                failPopupBeforeShow(completion, OsEffectReason.TOOLKIT_FAILURE);
            } else {
                failPopup(window, completion, OsEffectReason.TOOLKIT_FAILURE);
            }
        }
    }

    private static void startFade(
            JWindow window,
            int visibleMillis,
            int fadeMillis,
            boolean opacitySupported,
            Consumer<LifecycleUpdate> completion) {
        long started = System.nanoTime();
        boolean[] presentationProved = {!opacitySupported};
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
                    finishPopupFromTimer(window, completion, presentationProved[0]);
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
                    if (!presentationProved[0]
                            && window.isShowing()
                            && window.getOpacity() > 0.0F) {
                        presentationProved[0] = true;
                        currentPopupPrimary = new PrimaryResult(
                                OsPrimaryState.APPLIED, OsEffectReason.NONE);
                        emit(completion, new LifecycleUpdate(
                                currentPopupPrimary,
                                new CleanupResult(
                                        OsCleanupState.PENDING, OsEffectReason.NONE)));
                    }
                }
            } catch (Throwable failure) {
                timer.stop();
                failPopup(window, completion, OsEffectReason.TOOLKIT_FAILURE);
            }
        });
        timer.setRepeats(true);
        timer.start();
    }

    private static void finishPopupFromTimer(
            JWindow window,
            Consumer<LifecycleUpdate> completion,
            boolean presentationProved) {
        CleanupResult cleanup = disposeAndVerify(window);
        if (!presentationProved) {
            currentPopupPrimary = primaryFailed(
                    OsEffect.EXTERNAL_POPUP, OsEffectReason.POPUP_NOT_SHOWING);
        } else if (cleanup.state() == OsCleanupState.FAILED) {
            // A timer lifecycle failure must not leave the primary APPLIED.
            currentPopupPrimary = primaryFailed(
                    OsEffect.EXTERNAL_POPUP, OsEffectReason.CLEANUP_FAILED);
        }
        emit(completion, new LifecycleUpdate(currentPopupPrimary, cleanup));
    }

    private static void failPopupBeforeShow(
            Consumer<LifecycleUpdate> completion, OsEffectReason reason) {
        POPUP_ACTIVE.set(false);
        currentPopup = null;
        currentPopupPrimary = primaryFailed(OsEffect.EXTERNAL_POPUP, reason);
        emit(completion, new LifecycleUpdate(
                currentPopupPrimary,
                new CleanupResult(OsCleanupState.NOT_REQUIRED, OsEffectReason.NONE)));
    }

    private static void failPopup(
            JWindow window,
            Consumer<LifecycleUpdate> completion,
            OsEffectReason reason) {
        CleanupResult cleanup = disposeAndVerify(window);
        currentPopupPrimary = primaryFailed(OsEffect.EXTERNAL_POPUP, reason);
        emit(completion, new LifecycleUpdate(currentPopupPrimary, cleanup));
    }

    @Override
    public CleanupResult closePopup(Consumer<CleanupResult> completion) {
        if (!POPUP_ACTIVE.get() && currentPopup == null) {
            return new CleanupResult(OsCleanupState.APPLIED, OsEffectReason.NONE);
        }
        try {
            SwingUtilities.invokeLater(() -> {
                CleanupResult cleanup = disposeAndVerify(currentPopup);
                emit(completion, cleanup);
            });
            return new CleanupResult(
                    OsCleanupState.PENDING, OsEffectReason.CLEANUP_PENDING);
        } catch (Throwable failure) {
            return cleanupFailed(OsEffect.EXTERNAL_POPUP, OsEffectReason.EDT_UNAVAILABLE);
        }
    }

    /** EDT-only; failed disposal deliberately retains both reference and active flag. */
    private static CleanupResult disposeAndVerify(JWindow window) {
        if (window == null) {
            if (POPUP_ACTIVE.get()) {
                return new CleanupResult(
                        OsCleanupState.PENDING, OsEffectReason.CLEANUP_PENDING);
            }
            return new CleanupResult(OsCleanupState.APPLIED, OsEffectReason.NONE);
        }
        try {
            window.dispose();
            if (window.isDisplayable()) {
                currentPopup = window;
                POPUP_ACTIVE.set(true);
                return cleanupFailed(
                        OsEffect.EXTERNAL_POPUP, OsEffectReason.CLEANUP_FAILED);
            }
            if (currentPopup == window) {
                currentPopup = null;
            }
            POPUP_ACTIVE.set(false);
            return new CleanupResult(OsCleanupState.APPLIED, OsEffectReason.NONE);
        } catch (Throwable failure) {
            currentPopup = window;
            POPUP_ACTIVE.set(true);
            return cleanupFailed(
                    OsEffect.EXTERNAL_POPUP, OsEffectReason.CLEANUP_FAILED);
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
    public PrimaryResult flashTaskbar() {
        CapabilityResult capability = taskbarPreflight();
        if (capability.state() != OsCapabilityState.READY) {
            return primaryFailed(OsEffect.TASKBAR, OsEffectReason.GLFW_FAILURE);
        }
        try {
            GLFW.glfwRequestWindowAttention(windowHandle());
            return new PrimaryResult(
                    OsPrimaryState.REQUESTED, OsEffectReason.UNVERIFIED_API);
        } catch (Throwable failure) {
            return primaryFailed(OsEffect.TASKBAR, OsEffectReason.GLFW_FAILURE);
        }
    }

    @Override
    public PrimaryResult applyTitle(boolean glitched, long seed, int step) {
        CapabilityResult capability = windowPreflight(OsEffect.WINDOW_TITLE, false, false);
        if (capability.state() != OsCapabilityState.READY) {
            return primaryFailed(OsEffect.WINDOW_TITLE, OsEffectReason.GLFW_FAILURE);
        }
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (glitched) {
                minecraft.getWindow().setTitle(OsScareChoreography.glitchedTitle(seed, step));
            } else {
                minecraft.updateTitle();
            }
            return new PrimaryResult(
                    OsPrimaryState.REQUESTED, OsEffectReason.UNVERIFIED_API);
        } catch (Throwable failure) {
            return primaryFailed(OsEffect.WINDOW_TITLE, OsEffectReason.GLFW_FAILURE);
        }
    }

    @Override
    public CleanupResult cleanupTitle() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.getWindow() == null) {
                return cleanupFailed(
                        OsEffect.WINDOW_TITLE, OsEffectReason.WINDOW_UNAVAILABLE);
            }
            minecraft.updateTitle();
            return new CleanupResult(
                    OsCleanupState.PENDING, OsEffectReason.UNVERIFIED_API);
        } catch (Throwable failure) {
            return cleanupFailed(OsEffect.WINDOW_TITLE, OsEffectReason.GLFW_FAILURE);
        }
    }

    @Override
    public PrimaryResult applyWindowPulse(int dx, int dy) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            long handle = windowHandle();
            if (minecraft == null || minecraft.getWindow() == null || handle == 0L) {
                return primaryFailed(
                        OsEffect.WINDOW_MOTION, OsEffectReason.WINDOW_UNAVAILABLE);
            }
            if (minecraft.getWindow().isFullscreen()) {
                return primaryFailed(OsEffect.WINDOW_MOTION, OsEffectReason.GLFW_FAILURE);
            }
            if (!motionActive) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    var xBuffer = stack.mallocInt(1);
                    var yBuffer = stack.mallocInt(1);
                    GLFW.glfwGetWindowPos(handle, xBuffer, yBuffer);
                    originalX = xBuffer.get(0);
                    originalY = yBuffer.get(0);
                    motionActive = true;
                }
            }
            int expectedX = originalX + dx;
            int expectedY = originalY + dy;
            GLFW.glfwSetWindowPos(handle, expectedX, expectedY);
            return positionMatches(handle, expectedX, expectedY)
                    ? new PrimaryResult(OsPrimaryState.APPLIED, OsEffectReason.NONE)
                    : primaryFailed(
                            OsEffect.WINDOW_MOTION, OsEffectReason.READBACK_MISMATCH);
        } catch (Throwable failure) {
            return primaryFailed(OsEffect.WINDOW_MOTION, OsEffectReason.GLFW_FAILURE);
        }
    }

    @Override
    public CleanupResult cleanupWindowMotion() {
        if (!motionActive || originalX == null || originalY == null) {
            return new CleanupResult(OsCleanupState.NOT_REQUIRED, OsEffectReason.NONE);
        }
        try {
            long handle = windowHandle();
            if (handle == 0L) {
                return cleanupFailed(
                        OsEffect.WINDOW_MOTION, OsEffectReason.WINDOW_UNAVAILABLE);
            }
            GLFW.glfwSetWindowPos(handle, originalX, originalY);
            if (!positionMatches(handle, originalX, originalY)) {
                return cleanupFailed(
                        OsEffect.WINDOW_MOTION, OsEffectReason.READBACK_MISMATCH);
            }
            // Clear only after readback proves restoration. The next visit
            // captures the then-current position, never a stale prior origin.
            originalX = null;
            originalY = null;
            motionActive = false;
            return new CleanupResult(OsCleanupState.APPLIED, OsEffectReason.NONE);
        } catch (Throwable failure) {
            return cleanupFailed(
                    OsEffect.WINDOW_MOTION, OsEffectReason.CLEANUP_FAILED);
        }
    }

    private static boolean positionMatches(long handle, int expectedX, int expectedY) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var xBuffer = stack.mallocInt(1);
            var yBuffer = stack.mallocInt(1);
            GLFW.glfwGetWindowPos(handle, xBuffer, yBuffer);
            return xBuffer.get(0) == expectedX && yBuffer.get(0) == expectedY;
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

    private static CapabilityResult capabilityFailed(
            OsEffect effect, OsEffectReason reason) {
        logFailure(effect, "capability", reason);
        return new CapabilityResult(OsCapabilityState.FAILED, reason);
    }

    private static PrimaryResult primaryFailed(OsEffect effect, OsEffectReason reason) {
        logFailure(effect, "primary", reason);
        return new PrimaryResult(OsPrimaryState.FAILED, reason);
    }

    private static CleanupResult cleanupFailed(OsEffect effect, OsEffectReason reason) {
        logFailure(effect, "cleanup", reason);
        return new CleanupResult(OsCleanupState.FAILED, reason);
    }

    private static void logFailure(
            OsEffect effect, String stage, OsEffectReason reason) {
        String key = effect.serializedName() + ":" + stage + ":" + reason.serializedName();
        if (LOGGED_FAILURES.add(key)) {
            ZapeGRuntime.LOGGER.warn(
                    "OS effect outcome effect={} stage={} state=failed reason={}",
                    effect.serializedName(),
                    stage,
                    reason.serializedName());
        }
    }

    private static <T> void emit(Consumer<T> completion, T outcome) {
        try {
            completion.accept(outcome);
        } catch (Throwable ignored) {
            // Reporting cannot strand a popup or block verified cleanup.
        }
    }
}
