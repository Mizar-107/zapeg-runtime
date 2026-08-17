package io.github.mizar107.zapegruntime.client;

import io.github.mizar107.zapegruntime.network.SceneNetwork;
import io.github.mizar107.zapegruntime.scene.CancelReason;
import io.github.mizar107.zapegruntime.scene.SceneAck;
import io.github.mizar107.zapegruntime.scene.SceneDescriptor;
import io.github.mizar107.zapegruntime.scene.SceneMath;
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

    private static final long GAZE_DWELL_NANOS = 175_000_000L;
    private static ActiveScene active;

    private ClientSceneManager() {}

    private static final class ActiveScene {
        private final SceneDescriptor descriptor;
        private int ageTicks;
        private boolean visibleAcknowledged;
        private long gazeStartedNanos;

        private ActiveScene(SceneDescriptor descriptor) {
            this.descriptor = descriptor;
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
        SceneNetwork.acknowledge(descriptor.eventId(), localPlayerId, SceneAck.RECEIVED);
    }

    public static void cancel(UUID eventId, CancelReason reason) {
        ActiveScene current = active;
        if (current != null && current.descriptor.eventId().equals(eventId)) {
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
        if (current.ageTicks >= current.descriptor.ttlTicks()) {
            finish(SceneAck.TIMEOUT);
        }
    }

    /**
     * Returns the scene only when it is actually renderable in this frame. This is
     * also where the real client camera, frustum and block ray are evaluated.
     */
    public static SceneDescriptor observe(RenderLevelStageEvent event) {
        ActiveScene current = active;
        Minecraft minecraft = Minecraft.getInstance();
        if (current == null
                || minecraft.player == null
                || minecraft.level == null
                || !ApparitionRenderer.ready()) {
            return null;
        }
        ClientLevel level = minecraft.level;

        SceneDescriptor descriptor = current.descriptor;
        Vec3 anchor = descriptor.anchor();
        AABB bounds = new AABB(
                anchor.x - 0.55D,
                anchor.y,
                anchor.z - 0.55D,
                anchor.x + 0.55D,
                anchor.y + 2.25D,
                anchor.z + 0.55D);
        if (!event.getFrustum().isVisible(bounds)) {
            current.gazeStartedNanos = 0L;
            return null;
        }

        Camera camera = event.getCamera();
        Vec3 chest = anchor.add(0.0D, 1.35D, 0.0D);
        BlockHitResult hit = level.clip(new ClipContext(
                camera.getPosition(),
                chest,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                camera.getEntity() == null ? minecraft.player : camera.getEntity()));
        if (hit.getType() != HitResult.Type.MISS) {
            current.gazeStartedNanos = 0L;
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
                chest.subtract(camera.getPosition()),
                descriptor.profile().gazeAngleDegrees());
        if (!directGaze) {
            current.gazeStartedNanos = 0L;
            return descriptor;
        }

        long now = System.nanoTime();
        if (current.gazeStartedNanos == 0L) {
            current.gazeStartedNanos = now;
        } else if (now - current.gazeStartedNanos >= GAZE_DWELL_NANOS) {
            finish(SceneAck.GAZE);
            return null;
        }
        return descriptor;
    }

    public static float effectIntensity(float partialTick) {
        ActiveScene current = active;
        if (current == null) {
            return 0.0F;
        }
        double pulse = SceneMath.easedPulse(current.ageTicks + partialTick, 19.0D);
        double scale = current.visibleAcknowledged ? 0.55D : 0.14D;
        return (float) (scale * (0.45D + pulse * 0.55D));
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
        active = null;
    }

    static boolean hasActiveScene() {
        return active != null;
    }

    private static void finish(SceneAck acknowledgement) {
        ActiveScene current = active;
        if (current == null) {
            return;
        }
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
