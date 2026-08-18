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
    private static final int FOOTSTEP_MIN_INTERVAL_TICKS = 6;
    private static final int FOOTSTEP_INTERVAL_SPREAD = 5;
    private static final int FOOTSTEP_COUNT = 11;
    private static final double FOOTSTEP_START_DISTANCE = 13.0D;
    private static final double FOOTSTEP_END_DISTANCE = 3.25D;
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
        private boolean midBeatPlayed;
        private int footstepIndex;
        private int nextFootstepTick;
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
        if (current.descriptor.profile() == SceneProfile.FOOTSTEPS_01) {
            tickFootsteps(current, minecraft);
        } else {
            tickMidBeat(current);
        }
        if (current.ageTicks >= current.descriptor.ttlTicks()) {
            finish(SceneAck.TIMEOUT);
        }
    }

    /**
     * Sound-only scene: seeded steps circle from the anchor's direction toward
     * the target, stop just over three blocks away, and never arrive. The rest
     * of the TTL is silence; the scene ends as TIMEOUT with nothing to gaze at.
     */
    private static void tickFootsteps(ActiveScene current, Minecraft minecraft) {
        if (current.footstepIndex >= FOOTSTEP_COUNT
                || current.ageTicks < current.nextFootstepTick) {
            return;
        }
        long seed = current.descriptor.visualSeed();
        int interval = FOOTSTEP_MIN_INTERVAL_TICKS
                + (int) Math.floorMod(seed >>> (current.footstepIndex * 3),
                        FOOTSTEP_INTERVAL_SPREAD);
        current.nextFootstepTick = current.ageTicks + interval;

        Vec3 playerPosition = minecraft.player.position();
        Vec3 toward = current.descriptor.anchor().subtract(playerPosition);
        toward = new Vec3(toward.x, 0.0D, toward.z);
        double length = toward.length();
        Vec3 direction = length > 1.0E-4D ? toward.scale(1.0D / length) : new Vec3(1.0D, 0.0D, 1.0D);
        Vec3 lateral = new Vec3(-direction.z, 0.0D, direction.x);
        double wobble = (Math.floorMod(seed >>> 9, 5) * 0.18D - 0.36D)
                * (1.0D - (double) current.footstepIndex / FOOTSTEP_COUNT);
        double progress = (double) current.footstepIndex / (FOOTSTEP_COUNT - 1);
        double distance = FOOTSTEP_START_DISTANCE
                + (FOOTSTEP_END_DISTANCE - FOOTSTEP_START_DISTANCE) * progress;
        Vec3 step = playerPosition
                .add(direction.scale(distance))
                .add(lateral.scale(wobble));

        if (!current.visibleAcknowledged) {
            current.visibleAcknowledged = true;
            SceneNetwork.acknowledge(
                    current.descriptor.eventId(),
                    current.descriptor.targetId(),
                    SceneAck.VISIBLE);
        }
        SceneSounds.playFootstep(current.descriptor, step, current.footstepIndex);
        current.footstepIndex++;
    }

    /** One faint mid-scene beat so scenes read as arrive → linger → resolve. */
    private static void tickMidBeat(ActiveScene current) {
        if (current.midBeatPlayed) {
            return;
        }
        double fraction = 0.55D
                + ((current.descriptor.visualSeed() >>> 21) & 0x7L) / 7.0D * 0.15D;
        if (current.ageTicks < (int) (current.descriptor.ttlTicks() * fraction)) {
            return;
        }
        current.midBeatPlayed = true;
        Vec3 position = current.delayedMotionSample != null
                ? current.delayedMotionSample.anchor()
                : current.descriptor.anchor();
        SceneSounds.playMidBeat(current.descriptor, position);
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
        if (current.descriptor.profile() == SceneProfile.FOOTSTEPS_01) {
            // Sound-only: nothing to render and no gaze to measure; tick()
            // owns its lifecycle and its VISIBLE acknowledgement.
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
            SceneSounds.playArrival(descriptor, anchor);
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
        if (current.descriptor.profile() == SceneProfile.FOOTSTEPS_01) {
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
            SceneSounds.playArrival(current.descriptor, current.descriptor.anchor());
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
            case PERIPHERAL_01 -> 27.0D;
            case FOOTSTEPS_01 -> 41.0D;
        };
        double pulse = SceneMath.easedPulse(current.ageTicks + partialTick, period);
        double scale = switch (current.descriptor.profile()) {
            case ECHO_01 -> current.visibleAcknowledged ? 0.55D : 0.14D;
            case THRESHOLD_01 -> current.visibleAcknowledged ? 0.38D : 0.06D;
            case MOTION_ECHO_01 -> current.visibleAcknowledged ? 0.32D : 0.07D;
            case LIGHT_FAULT_01 -> current.visibleAcknowledged ? 0.78D : 0.18D;
            case PERIPHERAL_01 -> current.visibleAcknowledged ? 0.30D : 0.05D;
            case FOOTSTEPS_01 -> 0.0D;
        };
        double envelope = SceneMath.lifeEnvelope(
                current.ageTicks + partialTick,
                current.descriptor.ttlTicks(),
                9.0D,
                6.0D);
        return (float) (scale * (0.45D + pulse * 0.55D) * envelope);
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
        if (acknowledgement == SceneAck.GAZE) {
            Vec3 position = current.delayedMotionSample != null
                    ? current.delayedMotionSample.anchor()
                    : current.descriptor.anchor();
            SceneSounds.playResolved(current.descriptor, position);
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
