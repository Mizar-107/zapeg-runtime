package io.github.mizar107.zapegruntime.client.os;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.scene.OsCapabilityState;
import io.github.mizar107.zapegruntime.scene.OsCleanupState;
import io.github.mizar107.zapegruntime.scene.OsEffect;
import io.github.mizar107.zapegruntime.scene.OsEffectReason;
import io.github.mizar107.zapegruntime.scene.OsPrimaryState;
import io.github.mizar107.zapegruntime.scene.OsScareChoreography;
import java.awt.Color;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
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
    private static final PopupOwnership POPUP_OWNERSHIP = new PopupOwnership();
    private static final AtomicBoolean PLACEMENT_DEGRADATION_LOGGED = new AtomicBoolean();
    private static final Set<String> LOGGED_FAILURES = ConcurrentHashMap.newKeySet();

    private static volatile BufferedImage faceImage;
    private static volatile OsEffectReason faceImageFailure;
    private static volatile PopupSession currentPopup;

    private Integer originalX;
    private Integer originalY;
    private boolean motionActive;

    private record PopupTarget(
            GraphicsConfiguration configuration,
            PopupPlacementPolicy.Rect usableBounds,
            PopupPlacementPolicy.Placement placement) {}

    private record PopupTargetProbe(PopupTarget target, OsEffectReason failureReason) {
        static PopupTargetProbe ready(PopupTarget target) {
            return new PopupTargetProbe(Objects.requireNonNull(target), OsEffectReason.NONE);
        }

        static PopupTargetProbe failed(OsEffectReason reason) {
            return new PopupTargetProbe(null, Objects.requireNonNull(reason));
        }
    }

    private record PopupPreparation(
            CapabilityResult capability,
            PopupPlacementPolicy.Rect minecraftWindow) {
        static PopupPreparation ready(PopupPlacementPolicy.Rect minecraftWindow) {
            return new PopupPreparation(
                    new CapabilityResult(OsCapabilityState.READY, OsEffectReason.NONE),
                    Objects.requireNonNull(minecraftWindow));
        }

        static PopupPreparation unavailable(CapabilityResult capability) {
            return new PopupPreparation(Objects.requireNonNull(capability), null);
        }
    }

    private record UsableBounds(PopupPlacementPolicy.Rect bounds, boolean degraded) {}

    /** Mutable only on the EDT; the token is the cross-thread authority. */
    private static final class PopupSession {
        private final long token;
        private final JWindow window;
        private final Consumer<LifecycleUpdate> completion;
        private PrimaryResult primary =
                new PrimaryResult(OsPrimaryState.REQUESTED, OsEffectReason.NONE);
        private Timer timer;
        private boolean presentationProved;

        private PopupSession(
                long token, JWindow window, Consumer<LifecycleUpdate> completion) {
            this.token = token;
            this.window = window;
            this.completion = completion;
        }
    }

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
        return preparePopup().capability();
    }

    /** Called on the Minecraft client thread, before any EDT work is enqueued. */
    private PopupPreparation preparePopup() {
        if (!OsScarePlatform.popupBeatsAllowed()) {
            return PopupPreparation.unavailable(new CapabilityResult(
                    OsCapabilityState.UNSUPPORTED, OsEffectReason.PLATFORM_UNSUPPORTED));
        }
        if (POPUP_OWNERSHIP.ownerToken() != PopupOwnership.NO_OWNER) {
            return PopupPreparation.unavailable(
                    capabilityFailed(OsEffect.EXTERNAL_POPUP, OsEffectReason.ALREADY_ACTIVE));
        }
        try {
            if (GraphicsEnvironment.isHeadless()) {
                return PopupPreparation.unavailable(new CapabilityResult(
                        OsCapabilityState.UNSUPPORTED, OsEffectReason.HEADLESS));
            }
        } catch (Throwable failure) {
            return PopupPreparation.unavailable(capabilityFailed(
                    OsEffect.EXTERNAL_POPUP, OsEffectReason.TOOLKIT_FAILURE));
        }
        BufferedImage image = faceImage();
        if (image == null) {
            return PopupPreparation.unavailable(capabilityFailed(
                    OsEffect.EXTERNAL_POPUP,
                    faceImageFailure == null
                            ? OsEffectReason.ASSET_INVALID
                            : faceImageFailure));
        }
        // Minecraft and GLFW are client-thread-only. From this point onward,
        // the EDT sees only this immutable integer rectangle.
        PopupPlacementPolicy.Rect minecraftWindow = minecraftWindowBoundsOnClientThread();
        if (minecraftWindow == null) {
            return PopupPreparation.unavailable(capabilityFailed(
                    OsEffect.EXTERNAL_POPUP, OsEffectReason.WINDOW_UNAVAILABLE));
        }
        PopupTargetProbe target = resolvePopupTarget(image, minecraftWindow);
        if (target.target() == null) {
            return PopupPreparation.unavailable(capabilityFailed(
                    OsEffect.EXTERNAL_POPUP, target.failureReason()));
        }
        return PopupPreparation.ready(minecraftWindow);
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
        PopupPreparation preparation = preparePopup();
        CapabilityResult capability = preparation.capability();
        if (capability.state() != OsCapabilityState.READY) {
            PrimaryResult primary = capability.state() == OsCapabilityState.FAILED
                    ? primaryFailed(OsEffect.EXTERNAL_POPUP, capability.reason())
                    : new PrimaryResult(OsPrimaryState.NOT_REQUESTED, OsEffectReason.NONE);
            emit(completion, new LifecycleUpdate(
                    primary,
                    new CleanupResult(OsCleanupState.NOT_REQUIRED, OsEffectReason.NONE)));
            return;
        }
        long token = POPUP_OWNERSHIP.tryAcquire();
        if (token == PopupOwnership.NO_OWNER) {
            emit(completion, new LifecycleUpdate(
                    primaryFailed(OsEffect.EXTERNAL_POPUP, OsEffectReason.ALREADY_ACTIVE),
                    new CleanupResult(OsCleanupState.NOT_REQUIRED, OsEffectReason.NONE)));
            return;
        }
        try {
            SwingUtilities.invokeLater(
                    () -> runPopup(
                            token,
                            preparation.minecraftWindow(),
                            visibleMillis,
                            fadeMillis,
                            completion));
        } catch (Throwable failure) {
            POPUP_OWNERSHIP.release(token);
            PrimaryResult primary = primaryFailed(
                    OsEffect.EXTERNAL_POPUP, OsEffectReason.EDT_UNAVAILABLE);
            emit(completion, new LifecycleUpdate(
                    primary,
                    new CleanupResult(OsCleanupState.NOT_REQUIRED, OsEffectReason.NONE)));
        }
    }

    private static void runPopup(
            long token,
            PopupPlacementPolicy.Rect minecraftWindow,
            int visibleMillis,
            int fadeMillis,
            Consumer<LifecycleUpdate> completion) {
        PopupSession session = null;
        try {
            if (!POPUP_OWNERSHIP.owns(token)) {
                return;
            }
            BufferedImage image = faceImage();
            if (image == null) {
                failPopupBeforeShow(token, completion,
                        faceImageFailure == null
                                ? OsEffectReason.ASSET_INVALID
                                : faceImageFailure);
                return;
            }
            PopupTargetProbe probe = resolvePopupTarget(image, minecraftWindow);
            if (probe.target() == null) {
                failPopupBeforeShow(token, completion, probe.failureReason());
                return;
            }
            PopupTarget target = probe.target();
            JWindow window = new JWindow(target.configuration());
            session = new PopupSession(token, window, completion);
            if (!installPopup(session)) {
                window.dispose();
                return;
            }
            window.setFocusableWindowState(false);
            window.setAlwaysOnTop(true);
            window.setBackground(new Color(0, 0, 0, 0));
            PopupPlacementPolicy.Rect popupBounds = target.placement().popupBounds();
            Image rendered = image.getScaledInstance(
                    popupBounds.width(), popupBounds.height(), Image.SCALE_SMOOTH);
            window.getContentPane().add(new JLabel(
                    new ImageIcon(rendered), javax.swing.SwingConstants.CENTER));
            window.pack();
            window.setBounds(
                    popupBounds.x(),
                    popupBounds.y(),
                    popupBounds.width(),
                    popupBounds.height());
            if (target.placement().metricsDegraded()
                    && PLACEMENT_DEGRADATION_LOGGED.compareAndSet(false, true)) {
                ZapeGRuntime.LOGGER.info(
                        "OS popup placement used safe DPI-metrics degradation");
            }
            boolean opacitySupported = true;
            try {
                window.setOpacity(0.0F);
            } catch (Throwable unsupported) {
                opacitySupported = false;
            }
            window.setVisible(true);
            if (!window.isShowing()) {
                failPopup(session, OsEffectReason.POPUP_NOT_SHOWING);
                return;
            }
            if (!placementMatches(window, target)) {
                failPopup(session, OsEffectReason.READBACK_MISMATCH);
                return;
            }
            if (!opacitySupported) {
                // Opaque degradation: isShowing is sufficient because no zero
                // opacity was applied, and placement was read back on the
                // selected monitor before this APPLIED report.
                session.presentationProved = true;
                session.primary = new PrimaryResult(
                        OsPrimaryState.APPLIED, OsEffectReason.NONE);
                emit(session.completion, new LifecycleUpdate(
                        session.primary,
                        new CleanupResult(OsCleanupState.PENDING, OsEffectReason.NONE)));
            }
            startFade(session, visibleMillis, fadeMillis, opacitySupported);
        } catch (Throwable failure) {
            if (session == null) {
                failPopupBeforeShow(token, completion, OsEffectReason.TOOLKIT_FAILURE);
            } else {
                failPopup(session, OsEffectReason.TOOLKIT_FAILURE);
            }
        }
    }

    private static void startFade(
            PopupSession session,
            int visibleMillis,
            int fadeMillis,
            boolean opacitySupported) {
        JWindow window = session.window;
        long started = System.nanoTime();
        Timer timer = new Timer(40, null);
        session.timer = timer;
        timer.addActionListener(event -> {
            try {
                if (!ownsCurrentPopup(session)) {
                    timer.stop();
                    return;
                }
                if (!window.isDisplayable()) {
                    timer.stop();
                    finishPopupFromTimer(session);
                    return;
                }
                long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
                if (elapsedMillis >= visibleMillis) {
                    timer.stop();
                    finishPopupFromTimer(session);
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
                    if (!session.presentationProved
                            && window.isShowing()
                            && window.getOpacity() > 0.0F
                            && window.isDisplayable()) {
                        session.presentationProved = true;
                        session.primary = new PrimaryResult(
                                OsPrimaryState.APPLIED, OsEffectReason.NONE);
                        emit(session.completion, new LifecycleUpdate(
                                session.primary,
                                new CleanupResult(
                                        OsCleanupState.PENDING, OsEffectReason.NONE)));
                    }
                }
            } catch (Throwable failure) {
                timer.stop();
                failPopup(session, OsEffectReason.TOOLKIT_FAILURE);
            }
        });
        timer.setRepeats(true);
        timer.start();
    }

    private static void finishPopupFromTimer(PopupSession session) {
        if (!ownsCurrentPopup(session)) {
            return;
        }
        CleanupResult cleanup = disposeAndVerify(session);
        if (!session.presentationProved) {
            session.primary = primaryFailed(
                    OsEffect.EXTERNAL_POPUP, OsEffectReason.POPUP_NOT_SHOWING);
        } else if (cleanup.state() == OsCleanupState.FAILED) {
            // A timer lifecycle failure must not leave the primary APPLIED.
            session.primary = primaryFailed(
                    OsEffect.EXTERNAL_POPUP, OsEffectReason.CLEANUP_FAILED);
        }
        emit(session.completion, new LifecycleUpdate(session.primary, cleanup));
    }

    private static void failPopupBeforeShow(
            long token,
            Consumer<LifecycleUpdate> completion,
            OsEffectReason reason) {
        if (!POPUP_OWNERSHIP.release(token)) {
            return;
        }
        PrimaryResult primary = primaryFailed(OsEffect.EXTERNAL_POPUP, reason);
        emit(completion, new LifecycleUpdate(
                primary,
                new CleanupResult(OsCleanupState.NOT_REQUIRED, OsEffectReason.NONE)));
    }

    private static void failPopup(
            PopupSession session, OsEffectReason reason) {
        if (!ownsCurrentPopup(session)) {
            return;
        }
        CleanupResult cleanup = disposeAndVerify(session);
        session.primary = primaryFailed(OsEffect.EXTERNAL_POPUP, reason);
        emit(session.completion, new LifecycleUpdate(session.primary, cleanup));
    }

    @Override
    public CleanupResult closePopup(Consumer<CleanupResult> completion) {
        long ownerToken = POPUP_OWNERSHIP.ownerToken();
        if (ownerToken == PopupOwnership.NO_OWNER) {
            return new CleanupResult(OsCleanupState.APPLIED, OsEffectReason.NONE);
        }
        try {
            SwingUtilities.invokeLater(() -> {
                CleanupResult cleanup = cleanupOwnedPopup(ownerToken);
                emit(completion, cleanup);
            });
            return new CleanupResult(
                    OsCleanupState.PENDING, OsEffectReason.CLEANUP_PENDING);
        } catch (Throwable failure) {
            return cleanupFailed(OsEffect.EXTERNAL_POPUP, OsEffectReason.EDT_UNAVAILABLE);
        }
    }

    /** EDT-only. A missing materialised window can release only its exact lease. */
    private static CleanupResult cleanupOwnedPopup(long token) {
        if (!POPUP_OWNERSHIP.owns(token)) {
            return new CleanupResult(OsCleanupState.APPLIED, OsEffectReason.NONE);
        }
        PopupSession session = currentPopup;
        if (session == null) {
            return POPUP_OWNERSHIP.release(token)
                    ? new CleanupResult(OsCleanupState.APPLIED, OsEffectReason.NONE)
                    : cleanupFailed(
                            OsEffect.EXTERNAL_POPUP, OsEffectReason.CLEANUP_FAILED);
        }
        if (session.token != token) {
            return cleanupFailed(OsEffect.EXTERNAL_POPUP, OsEffectReason.CLEANUP_FAILED);
        }
        return disposeAndVerify(session);
    }

    /** Failed disposal deliberately retains both the session and its lease. */
    private static CleanupResult disposeAndVerify(PopupSession session) {
        if (!ownsCurrentPopup(session)) {
            return new CleanupResult(OsCleanupState.APPLIED, OsEffectReason.NONE);
        }
        try {
            if (session.timer != null) {
                session.timer.stop();
                session.timer = null;
            }
            // Hiding is a best-effort visual failsafe, independent from the
            // stricter disposal proof below. Neither call may prevent the
            // other from running.
            try {
                session.window.setAlwaysOnTop(false);
            } catch (Throwable ignored) {
                // Disposal still gets its bounded attempt.
            }
            try {
                session.window.setVisible(false);
            } catch (Throwable ignored) {
                // Disposal still gets its bounded attempt.
            }
            session.window.dispose();
            if (session.window.isDisplayable()) {
                return cleanupFailed(
                        OsEffect.EXTERNAL_POPUP, OsEffectReason.CLEANUP_FAILED);
            }
            if (currentPopup != session) {
                return cleanupFailed(
                        OsEffect.EXTERNAL_POPUP, OsEffectReason.CLEANUP_FAILED);
            }
            currentPopup = null;
            if (!POPUP_OWNERSHIP.release(session.token)) {
                currentPopup = session;
                return cleanupFailed(
                        OsEffect.EXTERNAL_POPUP, OsEffectReason.CLEANUP_FAILED);
            }
            return new CleanupResult(OsCleanupState.APPLIED, OsEffectReason.NONE);
        } catch (Throwable failure) {
            return cleanupFailed(
                    OsEffect.EXTERNAL_POPUP, OsEffectReason.CLEANUP_FAILED);
        }
    }

    private static boolean installPopup(PopupSession session) {
        if (!POPUP_OWNERSHIP.owns(session.token) || currentPopup != null) {
            return false;
        }
        currentPopup = session;
        return true;
    }

    private static boolean ownsCurrentPopup(PopupSession session) {
        return session != null
                && currentPopup == session
                && POPUP_OWNERSHIP.owns(session.token);
    }

    private static PopupTargetProbe resolvePopupTarget(
            BufferedImage image, PopupPlacementPolicy.Rect minecraftWindow) {
        Objects.requireNonNull(minecraftWindow, "minecraftWindow");
        try {
            GraphicsDevice[] devices = GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .getScreenDevices();
            if (devices == null || devices.length == 0) {
                return PopupTargetProbe.failed(OsEffectReason.TOOLKIT_FAILURE);
            }
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            List<GraphicsConfiguration> configurations = new ArrayList<>();
            List<PopupPlacementPolicy.Monitor> monitors = new ArrayList<>();
            for (GraphicsDevice device : devices) {
                try {
                    GraphicsConfiguration configuration = device.getDefaultConfiguration();
                    if (configuration == null) {
                        continue;
                    }
                    Rectangle rawBounds = configuration.getBounds();
                    PopupPlacementPolicy.Rect bounds = new PopupPlacementPolicy.Rect(
                            rawBounds.x, rawBounds.y, rawBounds.width, rawBounds.height);

                    UsableBounds usable;
                    try {
                        usable = safeUsableBounds(
                                bounds, toolkit.getScreenInsets(configuration));
                    } catch (Throwable unsupportedInsets) {
                        usable = conservativeUsableBounds(bounds);
                    }

                    double scaleX;
                    double scaleY;
                    boolean transformDegraded = false;
                    try {
                        var transform = configuration.getDefaultTransform();
                        scaleX = transform.getScaleX();
                        scaleY = transform.getScaleY();
                    } catch (Throwable unsupportedTransform) {
                        scaleX = Double.NaN;
                        scaleY = Double.NaN;
                        transformDegraded = true;
                    }

                    int index = configurations.size();
                    configurations.add(configuration);
                    monitors.add(new PopupPlacementPolicy.Monitor(
                            index,
                            bounds,
                            usable.bounds(),
                            scaleX,
                            scaleY,
                            usable.degraded() || transformDegraded));
                } catch (Throwable unusableDevice) {
                    // A broken device must not prevent another valid monitor
                    // from hosting the popup. No device names leave this boundary.
                }
            }
            var placement = PopupPlacementPolicy.place(
                    minecraftWindow,
                    monitors,
                    image.getWidth(),
                    image.getHeight());
            if (placement.isEmpty()) {
                return PopupTargetProbe.failed(OsEffectReason.TOOLKIT_FAILURE);
            }
            PopupPlacementPolicy.Placement selected = placement.get();
            PopupPlacementPolicy.Rect usable = monitors
                    .get(selected.monitorIndex())
                    .usableBounds();
            return PopupTargetProbe.ready(new PopupTarget(
                    configurations.get(selected.monitorIndex()), usable, selected));
        } catch (Throwable failure) {
            return PopupTargetProbe.failed(OsEffectReason.TOOLKIT_FAILURE);
        }
    }

    private static PopupPlacementPolicy.Rect minecraftWindowBoundsOnClientThread() {
        Minecraft minecraft;
        long handle;
        try {
            minecraft = Minecraft.getInstance();
            if (minecraft == null
                    || !minecraft.isSameThread()
                    || minecraft.getWindow() == null) {
                return null;
            }
            handle = minecraft.getWindow().getWindow();
        } catch (Throwable failure) {
            return null;
        }
        if (handle == 0L) {
            return null;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var x = stack.callocInt(1);
            var y = stack.callocInt(1);
            var width = stack.callocInt(1);
            var height = stack.callocInt(1);
            GLFW.glfwGetWindowPos(handle, x, y);
            GLFW.glfwGetWindowSize(handle, width, height);
            if (width.get(0) <= 0 || height.get(0) <= 0) {
                return null;
            }
            return new PopupPlacementPolicy.Rect(
                    x.get(0), y.get(0), width.get(0), height.get(0));
        } catch (Throwable failure) {
            return null;
        }
    }

    private static UsableBounds safeUsableBounds(
            PopupPlacementPolicy.Rect bounds, Insets insets) {
        if (insets == null) {
            return conservativeUsableBounds(bounds);
        }
        long left = Math.max(0, insets.left);
        long right = Math.max(0, insets.right);
        long top = Math.max(0, insets.top);
        long bottom = Math.max(0, insets.bottom);
        long width = (long) bounds.width() - left - right;
        long height = (long) bounds.height() - top - bottom;
        long x = (long) bounds.x() + left;
        long y = (long) bounds.y() + top;
        if (width <= 0L
                || height <= 0L
                || x < Integer.MIN_VALUE
                || x > Integer.MAX_VALUE
                || y < Integer.MIN_VALUE
                || y > Integer.MAX_VALUE
                || width > Integer.MAX_VALUE
                || height > Integer.MAX_VALUE) {
            return conservativeUsableBounds(bounds);
        }
        return new UsableBounds(new PopupPlacementPolicy.Rect(
                (int) x, (int) y, (int) width, (int) height), false);
    }

    /**
     * If platform insets cannot be trusted, reserve one eighth of every edge.
     * This keeps the small popup away from taskbars, docks and mixed-DPI seams
     * without claiming that unavailable toolkit metrics were observed.
     */
    private static UsableBounds conservativeUsableBounds(
            PopupPlacementPolicy.Rect bounds) {
        int horizontalMargin = bounds.width() >= 8 ? bounds.width() / 8 : 0;
        int verticalMargin = bounds.height() >= 8 ? bounds.height() / 8 : 0;
        int width = bounds.width() - horizontalMargin * 2;
        int height = bounds.height() - verticalMargin * 2;
        if (width <= 0 || height <= 0) {
            return new UsableBounds(bounds, true);
        }
        return new UsableBounds(new PopupPlacementPolicy.Rect(
                bounds.x() + horizontalMargin,
                bounds.y() + verticalMargin,
                width,
                height), true);
    }

    private static boolean placementMatches(JWindow window, PopupTarget target) {
        try {
            Rectangle raw = window.getBounds();
            PopupPlacementPolicy.Rect actual = new PopupPlacementPolicy.Rect(
                    raw.x, raw.y, raw.width, raw.height);
            GraphicsConfiguration actualConfiguration = window.getGraphicsConfiguration();
            return target.usableBounds().contains(actual)
                    && actualConfiguration != null
                    && actualConfiguration.getDevice() == target.configuration().getDevice();
        } catch (Throwable failure) {
            return false;
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
