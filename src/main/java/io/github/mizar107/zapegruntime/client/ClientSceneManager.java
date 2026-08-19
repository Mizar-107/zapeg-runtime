package io.github.mizar107.zapegruntime.client;

import io.github.mizar107.zapegruntime.client.os.OsScareDriver;
import io.github.mizar107.zapegruntime.network.SceneNetwork;
import io.github.mizar107.zapegruntime.scene.CameraUnease;
import io.github.mizar107.zapegruntime.scene.CancelReason;
import io.github.mizar107.zapegruntime.scene.ColossusChoreography;
import io.github.mizar107.zapegruntime.scene.GazePull;
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
 *
 * <p>Every scene runs PRELUDE (ambience dip only, nothing shown) → BODY (the
 * profile's actual content) → ENCORE (a silent gap and one final beat for
 * profiles with an encore delay). The terminal acknowledgement is held until
 * the encore completes, so the server's one-active-scene invariant spans the
 * false all-clear.
 */
public final class ClientSceneManager {

    private static final int MOTION_HISTORY_CAPACITY = 32;
    private static final int MOTION_HISTORY_DELAY_TICKS = 12;
    private static final int FOOTSTEP_MIN_INTERVAL_TICKS = 6;
    private static final int FOOTSTEP_INTERVAL_SPREAD = 5;
    private static final int FOOTSTEP_COUNT = 11;
    private static final double FOOTSTEP_START_DISTANCE = 13.0D;
    private static final double FOOTSTEP_END_DISTANCE = 3.25D;
    // Whisper-name replay: a coarse always-on trace (one sample every five
    // ticks covers ~16 s) so the target can hear their own steps from ~10 s
    // ago. Client-local only, never sent anywhere, cleared on logout/unload.
    private static final int AMBIENT_TRACE_CAPACITY = 64;
    private static final int AMBIENT_TRACE_STRIDE_TICKS = 5;
    private static final int WHISPER_REPLAY_COUNT = 7;
    private static final double WHISPER_MAX_DISTANCE = 24.0D;
    private static final double WHISPER_MIN_DISTANCE = 1.5D;
    // Near-miss crossing: the figure passes behind the target over this many
    // body ticks, with soft steps, then the scene goes quiet until the TTL.
    private static final int NEAR_MISS_CROSSING_TICKS = 36;
    private static final int NEAR_MISS_STEP_INTERVAL_TICKS = 7;
    // False passage: once the target commits to within this distance, the
    // doorway folds away over this many ticks and the scene resolves.
    private static final double PASSAGE_COLLAPSE_DISTANCE = 9.0D;
    private static final int PASSAGE_COLLAPSE_TICKS = 18;
    private static final double SKY_MARK_DISTANCE = 512.0D;
    private static ActiveScene active;
    private static MotionHistory ambientTrace;
    private static int ambientTraceCountdown;
    // Forced-gaze state: the held render-layer offset, its frame clock, and
    // whether the grip was applied last frame (so its first frame never
    // inherits a stale, huge dt). Offsets decay smoothly to zero after the
    // scene ends — the release never snaps and never leaves residue.
    private static float pullYawOffset;
    private static float pullPitchOffset;
    private static long pullLastFrameNanos;
    private static boolean pullWasActive;

    private ClientSceneManager() {}

    public enum ScenePhase {
        PRELUDE,
        BODY,
        ENCORE
    }

    public record RenderSnapshot(
            SceneDescriptor descriptor,
            Vec3 anchor,
            float yawDegrees,
            float gazeProgress,
            float effectProgress) {}

    private record MotionSample(Vec3 anchor, float yawDegrees) {}

    private static final class ActiveScene {
        private final SceneDescriptor descriptor;
        private final MotionHistory motionHistory;
        private final PresentedGazeTracker presentedGazeTracker = new PresentedGazeTracker();
        private int ageTicks;
        private boolean visibleAcknowledged;
        private int visibleAckedTick = -1;
        private boolean lightPresentationPending;
        private boolean midBeatPlayed;
        private int footstepIndex;
        private int nextFootstepTick;
        private boolean preludeSwellPlayed;
        private boolean preludeClickPlayed;
        private SceneAck pendingTerminalAck;
        private int encoreStartTick = -1;
        private boolean encoreSoundPlayed;
        private int whisperIndex;
        private int nextWhisperTick;
        private int nearStepTick;
        private int collapseTicks;
        private int colossusStepIndex;
        private int lastColossusStepTick = -1;
        private int nextColossusHeartbeatTick;
        private boolean colossusVanishRumbled;
        private boolean visitationBegun;
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

        private ScenePhase phase() {
            if (pendingTerminalAck != null) {
                return ScenePhase.ENCORE;
            }
            return ageTicks < descriptor.profile().preludeTicks()
                    ? ScenePhase.PRELUDE
                    : ScenePhase.BODY;
        }

        private int encoreBeatStartTick() {
            return encoreStartTick + descriptor.profile().encoreDelayTicks();
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
        recordAmbientTrace();
        ActiveScene current = active;
        if (current == null) {
            // Any OS-level beat that outlived its scene restores the window.
            OsScareDriver.instance().reset();
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
        switch (current.phase()) {
            case PRELUDE -> tickPrelude(current);
            case ENCORE -> tickEncore(current);
            case BODY -> {
                switch (current.descriptor.profile()) {
                    case FOOTSTEPS_01 -> tickFootsteps(current, minecraft);
                    case WHISPER_STEPS_01 -> tickWhisperSteps(current, minecraft);
                    case NEAR_MISS_01 -> tickNearMiss(current, minecraft);
                    case COLOSSUS_01 -> tickColossus(current, minecraft);
                    case VISITATION_01 -> tickVisitation(current);
                    case FALSE_PASSAGE_01 -> {
                        tickFalsePassage(current, minecraft);
                        tickMidBeat(current);
                    }
                    default -> tickMidBeat(current);
                }
                if (current.ageTicks >= current.descriptor.ttlTicks()) {
                    finishBody(current, SceneAck.TIMEOUT);
                }
            }
        }
    }

    /** The coarse always-on trace behind the whisper replay. */
    private static void recordAmbientTrace() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (ambientTrace == null) {
            ambientTrace = new MotionHistory(AMBIENT_TRACE_CAPACITY, 1);
        }
        if (--ambientTraceCountdown <= 0) {
            ambientTraceCountdown = AMBIENT_TRACE_STRIDE_TICKS;
            ambientTrace.record(minecraft.player.position(), minecraft.player.getYRot());
        }
    }

    /**
     * Whisper-name: the target hears their own footsteps replayed from where
     * they stood roughly ten seconds ago, walking forward in time toward —
     * but never reaching — the present. Sound-only; nothing ever shows.
     */
    private static void tickWhisperSteps(ActiveScene current, Minecraft minecraft) {
        if (current.whisperIndex >= WHISPER_REPLAY_COUNT
                || current.ageTicks < current.nextWhisperTick) {
            return;
        }
        long seed = current.descriptor.visualSeed();
        int interval = 9 + (int) Math.floorMod(seed >>> (current.whisperIndex * 4), 5);
        current.nextWhisperTick = current.ageTicks + interval;

        if (ambientTrace != null) {
            // 40 samples back at the 5-tick stride is ~10 s ago; each step
            // replays a little newer, so the sequence approaches the present.
            int samplesBack = Math.max(1, 40 - current.whisperIndex * 4);
            Vec3 playerPosition = minecraft.player.position();
            ambientTrace.sampleBack(samplesBack).ifPresent(sample -> {
                double distance = playerPosition.distanceTo(sample.position());
                if (distance >= WHISPER_MIN_DISTANCE && distance <= WHISPER_MAX_DISTANCE) {
                    markVisible(current);
                    SceneSounds.playWhisperStep(
                            current.descriptor, sample.position(), current.whisperIndex);
                }
            });
        }
        current.whisperIndex++;
    }

    /** Soft wrong-sounding steps while the near-miss figure crosses behind. */
    private static void tickNearMiss(ActiveScene current, Minecraft minecraft) {
        int bodyAge = current.ageTicks - current.descriptor.profile().preludeTicks();
        if (bodyAge > NEAR_MISS_CROSSING_TICKS || current.ageTicks < current.nearStepTick) {
            return;
        }
        current.nearStepTick = current.ageTicks + NEAR_MISS_STEP_INTERVAL_TICKS;
        Vec3 figure = nearMissPosition(
                current,
                minecraft.player.position(),
                minecraft.player.getYRot(),
                bodyAge / (double) NEAR_MISS_CROSSING_TICKS);
        // The crossing happened even if the target never turned around: the
        // first step is the VISIBLE acknowledgement.
        markVisible(current);
        SceneSounds.playNearMissStep(current.descriptor, figure);
    }

    /**
     * The colossus: slow footfalls that shake the ground, one distant roar at
     * the nearer stages, and at the finale a held watch with a heartbeat —
     * then it is simply gone. The figure itself is render-only; this method
     * owns only the sound and the shake-pulse timing.
     */
    /**
     * The visitation renders nothing in the world; its body ticks drive the
     * OS-level beats (face blink, wrong title, window pulse, taskbar flash)
     * through the driver, gated by the client's own opt-out config.
     */
    private static void tickVisitation(ActiveScene current) {
        OsScareDriver driver = OsScareDriver.instance();
        if (!current.visitationBegun) {
            current.visitationBegun = true;
            driver.begin(current.descriptor.visualSeed(), OsScareConfig.toggles());
        }
        driver.tick(
                SceneProfile.VISITATION_01,
                current.ageTicks - current.descriptor.profile().preludeTicks());
    }

    private static void tickColossus(ActiveScene current, Minecraft minecraft) {
        SceneDescriptor descriptor = current.descriptor;
        int stage = descriptor.stage();
        int bodyAge = current.ageTicks - descriptor.profile().preludeTicks();
        int steps = ColossusChoreography.stepsForStage(stage);
        if (current.colossusStepIndex < steps
                && bodyAge >= ColossusChoreography.stepTick(current.colossusStepIndex)) {
            markVisible(current);
            current.lastColossusStepTick = current.ageTicks;
            SceneSounds.playColossusStep(descriptor, stage, current.colossusStepIndex);
            current.colossusStepIndex++;
        }
        // One distant roar once the figure has fully arrived (near stages).
        if (!current.midBeatPlayed
                && stage >= 2
                && bodyAge >= (int) (bodyTicks(current) * 0.55D)) {
            current.midBeatPlayed = true;
            SceneSounds.playColossusRoar(descriptor, stage);
        }
        int vanishTick = ColossusChoreography.vanishTick(stage);
        if (vanishTick >= 0) {
            int lastStepTick = ColossusChoreography.stepTick(steps - 1);
            if (bodyAge >= lastStepTick + 12
                    && bodyAge < vanishTick
                    && current.ageTicks >= current.nextColossusHeartbeatTick) {
                current.nextColossusHeartbeatTick = current.ageTicks + 26;
                SceneSounds.playColossusHeartbeat(descriptor);
            }
            if (!current.colossusVanishRumbled && bodyAge >= vanishTick) {
                // One last rumble under the exact tick the figure is gone.
                current.colossusVanishRumbled = true;
                current.lastColossusStepTick = current.ageTicks;
                SceneSounds.playColossusVanish(descriptor);
            }
        }
    }

    /** The doorway folds only once the target commits to approaching it. */
    private static void tickFalsePassage(ActiveScene current, Minecraft minecraft) {
        if (current.collapseTicks >= PASSAGE_COLLAPSE_TICKS) {
            return;
        }
        double distance = minecraft.player.position().distanceTo(current.descriptor.anchor());
        if (distance <= PASSAGE_COLLAPSE_DISTANCE) {
            current.collapseTicks++;
            if (current.collapseTicks >= PASSAGE_COLLAPSE_TICKS) {
                // Resolved by approach: the passage gives up and is gone.
                finishBody(current, SceneAck.GAZE);
            }
        }
    }

    private static Vec3 nearMissPosition(
            ActiveScene current, Vec3 playerPosition, float playerYaw, double progress) {
        double[] offset = SceneMath.nearMissOffset(
                current.descriptor.visualSeed(), playerYaw, progress);
        return playerPosition.add(offset[0], offset[1], offset[2]);
    }

    /**
     * The dip before anything is shown: a low cave swell, one faint clicking,
     * and the fog/brightness shift the render hooks read via fogDip(). No
     * acknowledgement leaves the client during a prelude.
     */
    private static void tickPrelude(ActiveScene current) {
        if (!current.preludeSwellPlayed) {
            current.preludeSwellPlayed = true;
            SceneSounds.playPrelude(current.descriptor);
            return;
        }
        int preludeTicks = current.descriptor.profile().preludeTicks();
        if (!current.preludeClickPlayed
                && current.ageTicks >= (int) (preludeTicks * 0.6D)) {
            current.preludeClickPlayed = true;
            SceneSounds.playPreludeClick(current.descriptor);
        }
    }

    /**
     * The false all-clear: the scene has apparently ended. After the profile's
     * silent delay, one final beat fires, and only then does the held terminal
     * acknowledgement release the server-side scene slot.
     */
    private static void tickEncore(ActiveScene current) {
        int beatStart = current.encoreBeatStartTick();
        if (!current.encoreSoundPlayed && current.ageTicks >= beatStart) {
            current.encoreSoundPlayed = true;
            SceneSounds.playEncore(current.descriptor);
        }
        if (current.ageTicks >= beatStart + SceneProfile.ENCORE_BEAT_TICKS) {
            SceneAck acknowledgement = current.pendingTerminalAck;
            current.pendingTerminalAck = null;
            finish(acknowledgement == null ? SceneAck.TIMEOUT : acknowledgement);
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

        markVisible(current);
        SceneSounds.playFootstep(current.descriptor, step, current.footstepIndex);
        current.footstepIndex++;
    }

    /** One faint mid-scene beat so scenes read as arrive → linger → resolve. */
    private static void tickMidBeat(ActiveScene current) {
        if (current.midBeatPlayed) {
            return;
        }
        int bodyTicks = current.descriptor.ttlTicks()
                - current.descriptor.profile().preludeTicks();
        double fraction = 0.55D
                + ((current.descriptor.visualSeed() >>> 21) & 0x7L) / 7.0D * 0.15D;
        int beatTick = current.descriptor.profile().preludeTicks()
                + (int) (bodyTicks * fraction);
        if (current.ageTicks < beatTick) {
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
        ScenePhase phase = current.phase();
        if (phase == ScenePhase.ENCORE) {
            // The scene is over as far as the world is concerned; the encore
            // beat is sound and screen-space only.
            return null;
        }
        if (phase == ScenePhase.PRELUDE) {
            resetGaze(current);
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
        if (current.descriptor.profile() == SceneProfile.FOOTSTEPS_01
                || current.descriptor.profile() == SceneProfile.WHISPER_STEPS_01) {
            // Sound-only: nothing to render and no gaze to measure; tick()
            // owns the lifecycle and the VISIBLE acknowledgement.
            return null;
        }
        if (current.descriptor.profile() == SceneProfile.SKY_MARK_01) {
            return observeSkyMark(current, event);
        }
        if (current.descriptor.profile() == SceneProfile.FALSE_PASSAGE_01) {
            return observeFalsePassage(current, event, minecraft);
        }
        if (current.descriptor.profile() == SceneProfile.NEAR_MISS_01) {
            return observeNearMiss(current, event, minecraft);
        }
        if (current.descriptor.profile() == SceneProfile.COLOSSUS_01) {
            return observeColossus(current, event);
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
            markVisible(current);
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
                    0.0F,
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
            finishBody(current, SceneAck.GAZE);
            return null;
        }
        return new RenderSnapshot(
                descriptor,
                anchor,
                renderPose.yawDegrees(),
                current.gazeProgress,
                0.0F);
    }

    /**
     * The impossible sky mark hangs at a seeded azimuth/elevation relative to
     * the target. No block ray: it is above the terrain by construction, and
     * depth testing lets real mountains occlude it. A held direct look of
     * about a second resolves it.
     */
    private static RenderSnapshot observeSkyMark(
            ActiveScene current, RenderLevelStageEvent event) {
        Camera camera = event.getCamera();
        double[] direction = SceneMath.skyMarkDirection(current.descriptor.visualSeed());
        // Stay comfortably inside the frustum far plane at any render
        // distance: the mark is far, but never clipped away.
        double distance = Math.min(
                SKY_MARK_DISTANCE,
                Math.max(96.0D, Minecraft.getInstance().gameRenderer.getDepthFar() * 0.5D));
        Vec3 skyPoint = camera.getPosition().add(
                direction[0] * distance,
                direction[1] * distance,
                direction[2] * distance);
        AABB bounds = new AABB(
                skyPoint.x - 48.0D, skyPoint.y - 48.0D, skyPoint.z - 48.0D,
                skyPoint.x + 48.0D, skyPoint.y + 48.0D, skyPoint.z + 48.0D);
        if (!event.getFrustum().isVisible(bounds)) {
            resetGaze(current);
            return null;
        }
        if (!current.visibleAcknowledged) {
            markVisible(current);
            SceneSounds.playArrival(current.descriptor, skyPoint);
        }
        Vec3 cameraLook = new Vec3(camera.getLookVector());
        boolean directGaze = SceneMath.withinAngle(
                cameraLook,
                skyPoint.subtract(camera.getPosition()),
                current.descriptor.profile().gazeAngleDegrees());
        if (!directGaze) {
            resetGaze(current);
            return new RenderSnapshot(
                    current.descriptor, skyPoint, 0.0F, 0.0F, 0.0F);
        }
        long now = System.nanoTime();
        if (current.gazeStartedNanos == 0L) {
            current.gazeStartedNanos = now;
        }
        long dwellNanos = current.descriptor.profile().gazeDwellMillis() * 1_000_000L;
        long elapsed = Math.max(0L, now - current.gazeStartedNanos);
        current.gazeProgress = (float) Math.min(1.0D, (double) elapsed / (double) dwellNanos);
        if (elapsed >= dwellNanos) {
            finishBody(current, SceneAck.GAZE);
            return null;
        }
        return new RenderSnapshot(
                current.descriptor, skyPoint, 0.0F, current.gazeProgress, 0.0F);
    }

    /**
     * The false passage renders its doorway while it is visible and standing;
     * the collapse itself is driven by tick() distance checks and surfaced
     * here as effectProgress. It never resolves by gaze.
     */
    private static RenderSnapshot observeFalsePassage(
            ActiveScene current, RenderLevelStageEvent event, Minecraft minecraft) {
        Vec3 anchor = current.descriptor.anchor();
        AABB bounds = new AABB(
                anchor.x - 1.3D, anchor.y - 0.2D, anchor.z - 1.3D,
                anchor.x + 1.3D, anchor.y + 3.0D, anchor.z + 1.3D);
        if (!event.getFrustum().isVisible(bounds)) {
            return null;
        }
        Camera camera = event.getCamera();
        BlockHitResult hit = minecraft.level.clip(new ClipContext(
                camera.getPosition(),
                anchor.add(0.0D, 1.3D, 0.0D),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                camera.getEntity() == null ? minecraft.player : camera.getEntity()));
        if (hit.getType() != HitResult.Type.MISS) {
            return null;
        }
        if (!current.visibleAcknowledged) {
            markVisible(current);
            SceneSounds.playArrival(current.descriptor, anchor);
        }
        float collapse = Math.min(
                1.0F, current.collapseTicks / (float) PASSAGE_COLLAPSE_TICKS);
        return new RenderSnapshot(
                current.descriptor,
                anchor,
                current.descriptor.yawDegrees(),
                0.0F,
                collapse);
    }

    /**
     * The colossus stands far beyond loaded chunks, so the usual block ray is
     * meaningless: depth testing lets real terrain occlude the silhouette,
     * and out past the loaded world there is nothing to occlude it — it is
     * the horizon. The frustum test spans the whole figure, not just the
     * anchor, because a 100-block body is visible long before its feet are.
     * Never gaze-resolved: it is witnessed, never studied.
     */
    private static RenderSnapshot observeColossus(
            ActiveScene current, RenderLevelStageEvent event) {
        SceneDescriptor descriptor = current.descriptor;
        int stage = descriptor.stage();
        double bodyAge = current.ageTicks
                + event.getPartialTick()
                - descriptor.profile().preludeTicks();
        int vanishTick = ColossusChoreography.vanishTick(stage);
        if (vanishTick >= 0 && bodyAge >= vanishTick) {
            return null;
        }
        Vec3 anchor = colossusAnchor(descriptor, bodyAge);
        double halfWidth = 24.0D;
        AABB bounds = new AABB(
                anchor.x - halfWidth,
                anchor.y - 4.0D,
                anchor.z - halfWidth,
                anchor.x + halfWidth,
                anchor.y + ColossusChoreography.HEIGHT_BLOCKS + 8.0D,
                anchor.z + halfWidth);
        if (!event.getFrustum().isVisible(bounds)) {
            return null;
        }
        if (!current.visibleAcknowledged) {
            markVisible(current);
            SceneSounds.playArrival(descriptor, anchor);
        }
        return new RenderSnapshot(descriptor, anchor, descriptor.yawDegrees(), 0.0F, 0.0F);
    }

    /**
     * The wire anchor plus the bounded approach the elapsed footfalls have
     * walked, along the facing the server placed (toward the target).
     */
    private static Vec3 colossusAnchor(SceneDescriptor descriptor, double bodyAge) {
        int elapsed = ColossusChoreography.elapsedSteps(descriptor.stage(), bodyAge);
        double advance = ColossusChoreography.advanceBlocks(descriptor.stage(), elapsed);
        if (advance <= 0.0D) {
            return descriptor.anchor();
        }
        double yawRadians = Math.toRadians(descriptor.yawDegrees());
        return descriptor.anchor().add(
                -Math.sin(yawRadians) * advance,
                0.0D,
                Math.cos(yawRadians) * advance);
    }

    /**
     * The near-miss figure crosses just behind the target's current heading.
     * While the target looks forward it never enters the view; a fast glance
     * dwell resolves it the moment it is actually looked at.
     */
    private static RenderSnapshot observeNearMiss(
            ActiveScene current, RenderLevelStageEvent event, Minecraft minecraft) {
        double progress = Math.min(
                1.0D,
                (current.ageTicks + event.getPartialTick()
                        - current.descriptor.profile().preludeTicks())
                        / (double) NEAR_MISS_CROSSING_TICKS);
        if (progress >= 1.0D) {
            // The crossing is over; the remaining TTL is silence.
            return null;
        }
        Camera camera = event.getCamera();
        Vec3 figure = nearMissPosition(
                current,
                minecraft.player.position(),
                minecraft.player.getYRot(),
                progress);
        AABB bounds = new AABB(
                figure.x - 0.55D, figure.y, figure.z - 0.55D,
                figure.x + 0.55D, figure.y + 2.25D, figure.z + 0.55D);
        if (!event.getFrustum().isVisible(bounds)) {
            resetGaze(current);
            return null;
        }
        BlockHitResult hit = minecraft.level.clip(new ClipContext(
                camera.getPosition(),
                figure.add(0.0D, 1.35D, 0.0D),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                camera.getEntity() == null ? minecraft.player : camera.getEntity()));
        if (hit.getType() != HitResult.Type.MISS) {
            resetGaze(current);
            return null;
        }

        // Facing along the walking direction: from the seeded side toward the
        // other side of the rear arc. With look = (-sin yaw, cos yaw), the
        // crossing direction is (cos yaw * side, sin yaw * side).
        double side = (current.descriptor.visualSeed() & 1L) == 0L ? 1.0D : -1.0D;
        double yawRadians = Math.toRadians(minecraft.player.getYRot());
        double dirX = Math.cos(yawRadians) * side;
        double dirZ = Math.sin(yawRadians) * side;
        float walkYaw = (float) (Math.atan2(-dirX, dirZ) * 180.0D / Math.PI);

        Vec3 cameraLook = new Vec3(camera.getLookVector());
        boolean directGaze = SceneMath.withinAngle(
                cameraLook,
                figure.add(0.0D, 1.35D, 0.0D).subtract(camera.getPosition()),
                current.descriptor.profile().gazeAngleDegrees());
        if (!directGaze) {
            resetGaze(current);
            return new RenderSnapshot(
                    current.descriptor, figure, walkYaw, 0.0F, (float) progress);
        }
        long now = System.nanoTime();
        if (current.gazeStartedNanos == 0L) {
            current.gazeStartedNanos = now;
        }
        long dwellNanos = current.descriptor.profile().gazeDwellMillis() * 1_000_000L;
        long elapsed = Math.max(0L, now - current.gazeStartedNanos);
        current.gazeProgress = (float) Math.min(1.0D, (double) elapsed / (double) dwellNanos);
        if (elapsed >= dwellNanos) {
            finishBody(current, SceneAck.GAZE);
            return null;
        }
        return new RenderSnapshot(
                current.descriptor, figure, walkYaw, current.gazeProgress, (float) progress);
    }

    public static float guiEffectIntensity(float partialTick) {
        ActiveScene current = active;
        if (current == null) {
            return 0.0F;
        }
        ScenePhase phase = current.phase();
        if (phase == ScenePhase.PRELUDE) {
            return (float) (0.14D * preludeDim(current, partialTick));
        }
        if (phase == ScenePhase.ENCORE) {
            return (float) (0.50D * encoreBeatEnvelope(current, partialTick));
        }
        if (current.descriptor.profile() == SceneProfile.FOOTSTEPS_01
                || current.descriptor.profile() == SceneProfile.WHISPER_STEPS_01
                || current.descriptor.profile() == SceneProfile.NEAR_MISS_01
                || current.descriptor.profile() == SceneProfile.COLOSSUS_01) {
            // Sound-only, crossing and colossus scenes keep a clean screen;
            // their unease is audio, silhouette and the ground itself, never
            // an overlay.
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
            markVisible(current);
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
            finishBody(current, SceneAck.GAZE);
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
            case SKY_MARK_01 -> 34.0D;
            case FALSE_PASSAGE_01 -> 13.0D;
            // A 45-tick sine is a ~0.44 Hz smooth swell — far under the
            // 3-flashes-per-second photosensitivity threshold, and never a
            // hard-edged full-screen flash.
            case CHROMA_BREAK_01 -> 45.0D;
            case NEAR_MISS_01 -> 41.0D;
            case WHISPER_STEPS_01 -> 41.0D;
            case COLOSSUS_01 -> 53.0D;
            case VISITATION_01 -> 41.0D;
        };
        double pulse = SceneMath.easedPulse(bodyAge(current, partialTick), period);
        double scale = switch (current.descriptor.profile()) {
            case ECHO_01 -> current.visibleAcknowledged ? 0.55D : 0.14D;
            case THRESHOLD_01 -> current.visibleAcknowledged ? 0.38D : 0.06D;
            case MOTION_ECHO_01 -> current.visibleAcknowledged ? 0.32D : 0.07D;
            case LIGHT_FAULT_01 -> current.visibleAcknowledged ? 0.78D : 0.18D;
            case PERIPHERAL_01 -> current.visibleAcknowledged ? 0.30D : 0.05D;
            case FOOTSTEPS_01 -> 0.0D;
            case SKY_MARK_01 -> current.visibleAcknowledged ? 0.22D : 0.08D;
            case FALSE_PASSAGE_01 -> current.visibleAcknowledged ? 0.34D : 0.10D;
            case CHROMA_BREAK_01 -> 0.85D;
            case NEAR_MISS_01 -> 0.0D;
            case WHISPER_STEPS_01 -> 0.0D;
            case COLOSSUS_01 -> 0.0D;
            // The visitation's scare lives outside the game window; the
            // screen itself stays clean.
            case VISITATION_01 -> 0.0D;
        };
        double envelope = SceneMath.lifeEnvelope(
                bodyAge(current, partialTick),
                bodyTicks(current),
                9.0D,
                6.0D);
        return (float) (scale * (0.45D + pulse * 0.55D) * envelope);
    }

    public static SceneProfile activeProfile() {
        ActiveScene current = active;
        return current == null ? null : current.descriptor.profile();
    }

    public static ScenePhase scenePhase() {
        ActiveScene current = active;
        return current == null ? null : current.phase();
    }

    public static float gazeProgress() {
        ActiveScene current = active;
        return current == null ? 0.0F : current.gazeProgress;
    }

    /** Age of the scene body (after the prelude), including the partial tick. */
    public static double bodyAgeWithPartial(float partialTick) {
        ActiveScene current = active;
        return current == null ? 0.0D : bodyAge(current, partialTick);
    }

    /** Length of the scene body: the TTL minus the ambience-dip prelude. */
    public static int bodyTtlTicks() {
        ActiveScene current = active;
        return current == null ? 0 : bodyTicks(current);
    }

    public static long visualSeed() {
        ActiveScene current = active;
        return current == null ? 0L : current.descriptor.visualSeed();
    }

    /**
     * 0..1 fog pull-in factor: strongest at the end of the prelude dip, a
     * low simmer during the body, one last breath during the encore beat.
     * The render hook applies at most a few percent of the fog distance.
     */
    public static float fogDip(float partialTick) {
        ActiveScene current = active;
        if (current == null) {
            return 0.0F;
        }
        return switch (current.phase()) {
            case PRELUDE -> (float) preludeDim(current, partialTick);
            case BODY -> (float) (0.35D * SceneMath.lifeEnvelope(
                    bodyAge(current, partialTick), bodyTicks(current), 9.0D, 6.0D));
            case ENCORE -> (float) (0.25D * encoreBeatEnvelope(current, partialTick));
        };
    }

    /**
     * The bounded camera offset for this frame: positional jitter, a brief
     * reveal jolt, an unnatural roll drift, and — for the profiles wired in
     * {@link GazePull} — a slow forced gaze toward the figure's eyes. The
     * unease layers are capped by CameraUnease, the pull by GazePull, and
     * the sum is clamped again so they never compound into something
     * nauseating.
     */
    public static float[] cameraPerturbation(
            float partialTick, float cameraYaw, float cameraPitch) {
        ActiveScene current = active;
        if (current == null) {
            return releasePull();
        }
        float[] base;
        if (current.descriptor.profile() == SceneProfile.COLOSSUS_01) {
            // The heavy path: ground sway plus one deep pulse per footfall,
            // hard-capped by CameraUnease and decaying to zero with the scene
            // envelope, so it never fights the player's control for long.
            float heavyIntensity = switch (current.phase()) {
                case PRELUDE -> (float) (0.30D * preludeDim(current, partialTick));
                case BODY -> (float) SceneMath.lifeEnvelope(
                        bodyAge(current, partialTick), bodyTicks(current), 12.0D, 10.0D);
                case ENCORE -> (float) (0.50D * encoreBeatEnvelope(current, partialTick));
            };
            int sinceStep = current.lastColossusStepTick >= 0
                    ? current.ageTicks - current.lastColossusStepTick
                    : -1;
            base = CameraUnease.colossusPerturbation(
                    current.descriptor.stage(),
                    bodyAge(current, partialTick),
                    current.descriptor.visualSeed(),
                    heavyIntensity,
                    sinceStep);
        } else {
            int level = current.descriptor.profile().uneaseLevel();
            float intensity = switch (current.phase()) {
                case PRELUDE -> (float) (0.30D * preludeDim(current, partialTick));
                case BODY -> (float) (SceneMath.lifeEnvelope(
                                bodyAge(current, partialTick), bodyTicks(current), 9.0D, 6.0D)
                        * (0.55D + 0.45D * SceneMath.easedPulse(
                                bodyAge(current, partialTick), 31.0D)));
                case ENCORE -> (float) (0.50D * encoreBeatEnvelope(current, partialTick));
            };
            int shakeTicks = current.visibleAckedTick >= 0
                    ? current.ageTicks - current.visibleAckedTick
                    : -1;
            base = CameraUnease.perturbation(
                    level,
                    bodyAge(current, partialTick),
                    current.descriptor.visualSeed(),
                    intensity,
                    shakeTicks);
        }
        float[] pull = gazePullOffset(current, partialTick, cameraYaw, cameraPitch);
        float yawCap = GazePull.combinedCap(false);
        float pitchCap = GazePull.combinedCap(true);
        return new float[] {
            clamp(base[0] + pull[0], yawCap),
            clamp(base[1] + pull[1], pitchCap),
            base[2]
        };
    }

    /**
     * The forced-gaze offset for this frame. While the profile's pull window
     * is open the rendered view is dragged toward the figure's eyes at a
     * bounded rate; outside it the held offset walks smoothly back to zero.
     */
    private static float[] gazePullOffset(
            ActiveScene current, float partialTick, float cameraYaw, float cameraPitch) {
        long window = GazePull.pullWindowTicks(
                current.descriptor.profile(),
                current.descriptor.stage(),
                bodyTicks(current));
        double response = GazePull.response(
                bodyAge(current, partialTick), window, current.gazeProgress);
        long now = System.nanoTime();
        double dtTicks = pullWasActive
                ? (now - pullLastFrameNanos) / 50_000_000.0D
                : 0.0D;
        pullLastFrameNanos = now;
        pullWasActive = true;

        float desiredYaw = 0.0F;
        float desiredPitch = 0.0F;
        if (response > 0.0D) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                // The pull aims where the figure actually is this frame: the
                // colossus anchor walks closer with each footfall.
                Vec3 figureAnchor =
                        current.descriptor.profile() == SceneProfile.COLOSSUS_01
                                ? colossusAnchor(
                                        current.descriptor,
                                        bodyAge(current, partialTick))
                                : current.descriptor.anchor();
                Vec3 eyes = figureAnchor.add(
                        0.0D,
                        GazePull.eyeHeightBlocks(current.descriptor.profile()),
                        0.0D);
                Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
                double dx = eyes.x - camera.x;
                double dy = eyes.y - camera.y;
                double dz = eyes.z - camera.z;
                double horizontal = Math.hypot(dx, dz);
                float targetYaw = (float) (Math.atan2(-dx, dz) * 180.0D / Math.PI);
                float targetPitch = (float) Math.max(
                        -GazePull.PITCH_LIMIT_DEGREES,
                        Math.min(
                                GazePull.PITCH_LIMIT_DEGREES,
                                Math.atan2(-dy, horizontal) * 180.0D / Math.PI));
                desiredYaw = GazePull.desiredOffset(targetYaw, cameraYaw, response, false);
                desiredPitch =
                        GazePull.desiredOffset(targetPitch, cameraPitch, response, true);
            }
        }
        pullYawOffset = GazePull.stepOffset(pullYawOffset, desiredYaw, response, dtTicks, false);
        pullPitchOffset =
                GazePull.stepOffset(pullPitchOffset, desiredPitch, response, dtTicks, true);
        return new float[] {pullYawOffset, pullPitchOffset};
    }

    /** After the scene ends the grip lets go over a handful of frames. */
    private static float[] releasePull() {
        if (pullYawOffset == 0.0F && pullPitchOffset == 0.0F) {
            pullWasActive = false;
            return new float[3];
        }
        long now = System.nanoTime();
        double dtTicks = pullWasActive
                ? (now - pullLastFrameNanos) / 50_000_000.0D
                : 0.0D;
        pullLastFrameNanos = now;
        pullWasActive = true;
        pullYawOffset = GazePull.stepOffset(pullYawOffset, 0.0F, 0.0D, dtTicks, false);
        pullPitchOffset = GazePull.stepOffset(pullPitchOffset, 0.0F, 0.0D, dtTicks, true);
        return new float[] {pullYawOffset, pullPitchOffset, 0.0F};
    }

    private static float clamp(float value, float cap) {
        return Math.max(-cap, Math.min(cap, value));
    }

    public static void clearWithoutAcknowledgement() {
        ActiveScene current = active;
        if (current != null) {
            current.clearMotion();
        }
        active = null;
        // The ambient whisper trace is client-local memory of where the
        // player has been; it must not survive logout or a dimension change.
        ambientTrace = null;
        ambientTraceCountdown = 0;
        // Neither may an OS-level beat: title and geometry restore at once.
        OsScareDriver.instance().reset();
    }

    static boolean hasActiveScene() {
        return active != null;
    }

    private static double bodyAge(ActiveScene current, float partialTick) {
        return Math.max(
                0.0D,
                current.ageTicks + partialTick
                        - current.descriptor.profile().preludeTicks());
    }

    private static int bodyTicks(ActiveScene current) {
        return Math.max(
                1,
                current.descriptor.ttlTicks()
                        - current.descriptor.profile().preludeTicks());
    }

    /** Builds and releases the ambience dip across the prelude window. */
    private static double preludeDim(ActiveScene current, float partialTick) {
        int preludeTicks = current.descriptor.profile().preludeTicks();
        if (preludeTicks <= 0) {
            return 0.0D;
        }
        double age = current.ageTicks + partialTick;
        return SceneMath.smoothstep(0.0D, preludeTicks * 0.4D, age)
                * SceneMath.smoothstep(0.0D, 6.0D, preludeTicks - age);
    }

    /** Smooth 0→1→0 across the single final beat of an encore. */
    private static double encoreBeatEnvelope(ActiveScene current, float partialTick) {
        if (current.pendingTerminalAck == null) {
            return 0.0D;
        }
        double beatAge = current.ageTicks + partialTick - current.encoreBeatStartTick();
        if (beatAge < 0.0D || beatAge > SceneProfile.ENCORE_BEAT_TICKS) {
            return 0.0D;
        }
        return Math.sin(Math.PI * beatAge / SceneProfile.ENCORE_BEAT_TICKS);
    }

    private static void markVisible(ActiveScene current) {
        if (current.visibleAcknowledged) {
            return;
        }
        current.visibleAcknowledged = true;
        current.visibleAckedTick = current.ageTicks;
        SceneNetwork.acknowledge(
                current.descriptor.eventId(),
                current.descriptor.targetId(),
                SceneAck.VISIBLE);
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

    /**
     * The scene body resolved (gaze or timeout). Profiles with an encore hold
     * the terminal acknowledgement through a silent gap and one final beat;
     * everything else finishes immediately.
     */
    private static void finishBody(ActiveScene current, SceneAck acknowledgement) {
        if (acknowledgement == SceneAck.GAZE) {
            Vec3 position = current.delayedMotionSample != null
                    ? current.delayedMotionSample.anchor()
                    : current.descriptor.anchor();
            SceneSounds.playResolved(current.descriptor, position);
        }
        if (acknowledgement != SceneAck.ABORTED
                && current.descriptor.profile().encoreDelayTicks() > 0) {
            current.pendingTerminalAck = acknowledgement;
            current.encoreStartTick = current.ageTicks;
            current.clearMotion();
            resetGaze(current);
            return;
        }
        finish(acknowledgement);
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
