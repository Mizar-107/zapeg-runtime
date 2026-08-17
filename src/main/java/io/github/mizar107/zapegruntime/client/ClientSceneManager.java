package io.github.mizar107.zapegruntime.client;

import io.github.mizar107.zapegruntime.network.SceneNetwork;
import io.github.mizar107.zapegruntime.scene.CancelReason;
import io.github.mizar107.zapegruntime.scene.MotionHistory;
import io.github.mizar107.zapegruntime.scene.PresentedGazeTracker;
import io.github.mizar107.zapegruntime.scene.SceneAck;
import io.github.mizar107.zapegruntime.scene.SceneDescriptor;
import io.github.mizar107.zapegruntime.scene.SceneMath;
import io.github.mizar107.zapegruntime.scene.SceneProfile;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * Owns the target client's transient scene state. Nothing here is persisted, and no
 * camera or gaze data is sent to the server beyond a bounded acknowledgement enum.
 */
public final class ClientSceneManager {

    private static final int MOTION_HISTORY_CAPACITY = 32;
    private static final int MOTION_HISTORY_DELAY_TICKS = 12;
    private static ActiveScene active;

    private ClientSceneManager() {}

    public record RenderSnapshot(
            SceneDescriptor descriptor,
            Vec3 anchor,
            float yawDegrees,
            float gazeProgress) {}

    private record MotionSample(Vec3 anchor, float yawDegrees) {}

    private static final class ActiveScene {
        private final SceneDescriptor descriptor;
        private final MotionHistory motionHistory;
        private final PresentedGazeTracker presentedGazeTracker = new PresentedGazeTracker();
        private int ageTicks;
        private boolean visibleAcknowledged;
        private boolean lightPresentationPending;
        private long gazeStartedNanos;
        private float gazeProgress;
        private MotionSample delayedMotionSample;

        private ActiveScene(SceneDescriptor descriptor) {
            this.descriptor = descriptor;
            this.motionHistory = descriptor.profile().usesMotionHistory()
                    ? new MotionHistory(
                            MOTION_HISTORY_CAPACITY,
                            MOTION_HISTORY_DELAY_TICKS)
                    : null;
        }

        private void recordMotion(Vec3 position, float yawDegrees) {
            if (motionHistory == null) {
                return;
            }
            motionHistory.record(position, yawDegrees);
            delayedMotionSample = motionHistory.delayedSample()
                    .map(this::offsetMotionSample)
                    .orElse(null);
        }

        private MotionSample offsetMotionSample(MotionHistory.Sample sample) {
            float yaw = sample.yawDegrees();
            double radians = Math.toRadians(yaw);
            double side = (descriptor.visualSeed() & 1L) == 0L ? 1.0D : -1.0D;
            double lateralDistance = 1.65D;
            Vec3 anchor = new Vec3(
                    sample.position().x + Math.cos(radians) * side * lateralDistance,
                    sample.position().y,
                    sample.position().z + Math.sin(radians) * side * lateralDistance);
            return new MotionSample(anchor, yaw);
        }

        private void clearMotion() {
            if (motionHistory != null) {
                motionHistory.clear();
            }
            delayedMotionSample = null;
        }
    }

    public static void accept(SceneDescriptor descriptor) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        UUID localPlayerId = minecraft.player.getUUID();
        if (!descriptor.targetId().equals(localPlayerId)
                || !descriptor.dimension().equals(minecraft.level.dimension().location())) {
            SceneNetwork.acknowledge(descriptor.eventId(), localPlayerId, SceneAck.REJECTED);
            return;
        }
        if (active != null) {
            if (active.descriptor.eventId().equals(descriptor.eventId())) {
                SceneNetwork.acknowledge(descriptor.eventId(), localPlayerId, SceneAck.RECEIVED);
            } else {
                SceneNetwork.acknowledge(descriptor.eventId(), localPlayerId, SceneAck.BUSY);
            }
            return;
        }
        active = new ActiveScene(descriptor);
        if (descriptor.profile().usesMotionHistory()) {
            active.recordMotion(minecraft.player.position(), minecraft.player.getYRot());
        }
        SceneNetwork.acknowledge(descriptor.eventId(), localPlayerId, SceneAck.RECEIVED);
    }

    public static void cancel(UUID eventId, CancelReason reason) {
        ActiveScene current = active;
        if (current != null && current.descriptor.eventId().equals(eventId)) {
            current.clearMotion();
            active = null;
        }
    }

    public static void tick() {
        ActiveScene current = active;
        if (current == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.level == null
                || !minecraft.player.isAlive()
                || !current.descriptor.targetId().equals(minecraft.player.getUUID())
                || !current.descriptor.dimension().equals(minecraft.level.dimension().location())
                || minecraft.screen != null) {
            finish(SceneAck.ABORTED);
            return;
        }
        current.ageTicks++;
        if (current.descriptor.profile().usesMotionHistory()) {
            current.recordMotion(minecraft.player.position(), minecraft.player.getYRot());
        }
        if (current.ageTicks >= current.descriptor.ttlTicks()) {
            finish(SceneAck.TIMEOUT);
        }
    }

    /**
     * Returns the scene only when it is actually renderable in this frame. This is
     * also where the real client camera, frustum and block ray are evaluated.
     */
    public static RenderSnapshot observe(RenderLevelStageEvent event) {
        ActiveScene current = active;
        Minecraft minecraft = Minecraft.getInstance();
        if (current == null || minecraft.player == null || minecraft.level == null) {
            return null;
        }
        boolean lightFault = current.descriptor.profile() == SceneProfile.LIGHT_FAULT_01;
        if (lightFault && current.lightPresentationPending) {
            resetGaze(current);
        }
        current.lightPresentationPending = false;
        if (lightFault && minecraft.options.hideGui) {
            resetGaze(current);
            return null;
        }
        if (current.descriptor.profile().rendersFigure() && !ApparitionRenderer.ready()) {
            return null;
        }
        ClientLevel level = minecraft.level;

        SceneDescriptor descriptor = current.descriptor;
        MotionSample renderPose = resolveRenderPose(current);
        if (renderPose == null) {
            resetGaze(current);
            return null;
        }
        Vec3 anchor = renderPose.anchor();
        AABB bounds = lightFault
                ? new AABB(
                        anchor.x - 0.45D,
                        anchor.y - 0.45D,
                        anchor.z - 0.45D,
                        anchor.x + 0.45D,
                        anchor.y + 0.45D,
                        anchor.z + 0.45D)
                : new AABB(
                        anchor.x - 0.55D,
                        anchor.y,
                        anchor.z - 0.55D,
                        anchor.x + 0.55D,
                        anchor.y + 2.25D,
                        anchor.z + 0.55D);
        if (!event.getFrustum().isVisible(bounds)) {
            resetGaze(current);
            return null;
        }

        Camera camera = event.getCamera();
        Vec3 focus = lightFault ? anchor : anchor.add(0.0D, 1.35D, 0.0D);
        BlockHitResult hit = level.clip(new ClipContext(
                camera.getPosition(),
                focus,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                camera.getEntity() == null ? minecraft.player : camera.getEntity()));
        if (hit.getType() != HitResult.Type.MISS) {
            resetGaze(current);
            return null;
        }
        if (lightFault) {
            current.lightPresentationPending = true;
            return null;
        }

        if (!current.visibleAcknowledged) {
            current.visibleAcknowledged = true;
            SceneNetwork.acknowledge(
                    descriptor.eventId(),
                    descriptor.targetId(),
                    SceneAck.VISIBLE);
        }

        Vec3 cameraLook = new Vec3(camera.getLookVector());
        boolean directGaze = SceneMath.withinAngle(
                cameraLook,
                focus.subtract(camera.getPosition()),
                descriptor.profile().gazeAngleDegrees());
        if (!directGaze) {
            resetGaze(current);
            return new RenderSnapshot(
                    descriptor,
                    anchor,
                    renderPose.yawDegrees(),
                    0.0F);
        }

        long now = System.nanoTime();
        if (current.gazeStartedNanos == 0L) {
            current.gazeStartedNanos = now;
        }
        long gazeDwellNanos = descriptor.profile().gazeDwellMillis() * 1_000_000L;
        long gazeElapsedNanos = Math.max(0L, now - current.gazeStartedNanos);
        current.gazeProgress = (float) Math.min(
                1.0D,
                (double) gazeElapsedNanos / (double) gazeDwellNanos);
        if (gazeElapsedNanos >= gazeDwellNanos) {
            finish(SceneAck.GAZE);
            return null;
        }
        return new RenderSnapshot(
                descriptor,
                anchor,
                renderPose.yawDegrees(),
                current.gazeProgress);
    }

    public static float guiEffectIntensity(float partialTick) {
        ActiveScene current = active;
        if (current == null) {
            return 0.0F;
        }
        if (current.descriptor.profile() == SceneProfile.LIGHT_FAULT_01) {
            return presentLightFault(current, partialTick);
        }
        return calculateEffectIntensity(current, partialTick);
    }

    private static float presentLightFault(ActiveScene current, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!current.lightPresentationPending
                || minecraft.player == null
                || minecraft.level == null
                || minecraft.options.hideGui) {
            resetGaze(current);
            return 0.0F;
        }
        current.lightPresentationPending = false;

        if (!current.visibleAcknowledged) {
            current.visibleAcknowledged = true;
            SceneNetwork.acknowledge(
                    current.descriptor.eventId(),
                    current.descriptor.targetId(),
                    SceneAck.VISIBLE);
        }

        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 cameraLook = new Vec3(camera.getLookVector());
        boolean directGaze = SceneMath.withinAngle(
                cameraLook,
                current.descriptor.anchor().subtract(camera.getPosition()),
                current.descriptor.profile().gazeAngleDegrees());
        long requiredNanos = current.descriptor.profile().gazeDwellMillis() * 1_000_000L;
        current.gazeProgress = current.presentedGazeTracker.present(
                directGaze,
                System.nanoTime(),
                requiredNanos);
        if (current.gazeProgress >= 1.0F) {
            finish(SceneAck.GAZE);
            return 0.0F;
        }
        return calculateEffectIntensity(current, partialTick);
    }

    private static float calculateEffectIntensity(ActiveScene current, float partialTick) {
        double period = switch (current.descriptor.profile()) {
            case ECHO_01 -> 19.0D;
            case THRESHOLD_01 -> 31.0D;
            case MOTION_ECHO_01 -> 23.0D;
            case LIGHT_FAULT_01 -> 47.0D;
        };
        double pulse = SceneMath.easedPulse(current.ageTicks + partialTick, period);
        double scale = switch (current.descriptor.profile()) {
            case ECHO_01 -> current.visibleAcknowledged ? 0.55D : 0.14D;
            case THRESHOLD_01 -> current.visibleAcknowledged ? 0.38D : 0.06D;
            case MOTION_ECHO_01 -> current.visibleAcknowledged ? 0.32D : 0.07D;
            case LIGHT_FAULT_01 -> current.visibleAcknowledged ? 0.78D : 0.18D;
        };
        return (float) (scale * (0.45D + pulse * 0.55D));
    }

    public static SceneProfile activeProfile() {
        ActiveScene current = active;
        return current == null ? null : current.descriptor.profile();
    }

    public static float gazeProgress() {
        ActiveScene current = active;
        return current == null ? 0.0F : current.gazeProgress;
    }

    public static double ageWithPartial(float partialTick) {
        ActiveScene current = active;
        return current == null ? 0.0D : current.ageTicks + partialTick;
    }

    public static long visualSeed() {
        ActiveScene current = active;
        return current == null ? 0L : current.descriptor.visualSeed();
    }

    public static void clearWithoutAcknowledgement() {
        ActiveScene current = active;
        if (current != null) {
            current.clearMotion();
        }
        active = null;
    }

    static boolean hasActiveScene() {
        return active != null;
    }

    private static MotionSample resolveRenderPose(ActiveScene current) {
        if (current.descriptor.profile().usesMotionHistory()) {
            return current.delayedMotionSample;
        }
        return new MotionSample(
                current.descriptor.anchor(),
                current.descriptor.yawDegrees());
    }

    private static void resetGaze(ActiveScene current) {
        current.gazeStartedNanos = 0L;
        current.gazeProgress = 0.0F;
        current.lightPresentationPending = false;
        current.presentedGazeTracker.reset();
    }

    private static void finish(SceneAck acknowledgement) {
        ActiveScene current = active;
        if (current == null) {
            return;
        }
        current.clearMotion();
        active = null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
                && minecraft.getConnection() != null
                && current.descriptor.targetId().equals(minecraft.player.getUUID())) {
            SceneNetwork.acknowledge(
                    current.descriptor.eventId(),
                    current.descriptor.targetId(),
                    acknowledgement);
        }
    }
}
