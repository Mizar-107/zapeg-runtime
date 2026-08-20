package io.github.mizar107.zapegruntime.client;

import io.github.mizar107.zapegruntime.scene.RiftChoreography;
import io.github.mizar107.zapegruntime.scene.SceneDescriptor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Client-local vanilla sound pairing for scenes. Sounds play only on the
 * target's own client, positioned at the scene anchor; nothing is sent to
 * other players and no custom or remote audio asset is involved.
 */
final class SceneSounds {

    private SceneSounds() {}

    static void playArrival(SceneDescriptor descriptor, Vec3 position) {
        switch (descriptor.profile()) {
            case ECHO_01 -> play(descriptor, position, SoundEvents.ENDERMAN_STARE, 0.50F, 0.55F);
            case THRESHOLD_01 -> play(descriptor, position, SoundEvents.SCULK_CLICKING, 0.55F, 0.50F);
            case MOTION_ECHO_01 -> play(descriptor, position, SoundEvents.SOUL_ESCAPE, 0.55F, 0.70F);
            case LIGHT_FAULT_01 -> play(descriptor, position, SoundEvents.WARDEN_HEARTBEAT, 0.60F, 0.60F);
            case PERIPHERAL_01 -> play(descriptor, position, SoundEvents.ENDERMAN_AMBIENT, 0.35F, 0.60F);
            case SKY_MARK_01 -> playSkyMark(descriptor);
            case FALSE_PASSAGE_01 -> playPassageTear(descriptor, position);
            case CHROMA_BREAK_01 -> playChromaTear(descriptor);
            case NEAR_MISS_01 -> {
                // The first soft step behind the target is the arrival beat.
            }
            case WHISPER_STEPS_01 -> {
                // The first replayed step is itself the arrival beat.
            }
            case FOOTSTEPS_01 -> {
                // The first footstep is itself the arrival beat.
            }
            case COLOSSUS_01 -> {
                // The first ground-shaking footfall is the arrival beat.
            }
            case VISITATION_01 -> play(descriptor, position, SoundEvents.WARDEN_HEARTBEAT, 0.45F, 0.50F);
            case RIFT_01 -> playRiftArrival(descriptor, position);
        }
    }

    /** One faint mid-scene beat so scenes read as arrive → linger → resolve. */
    static void playMidBeat(SceneDescriptor descriptor, Vec3 position) {
        switch (descriptor.profile()) {
            case ECHO_01 -> play(descriptor, position, SoundEvents.ENDERMAN_AMBIENT, 0.30F, 0.50F);
            case THRESHOLD_01 -> play(descriptor, position, SoundEvents.SCULK_CLICKING, 0.30F, 0.42F);
            case MOTION_ECHO_01 -> play(descriptor, position, SoundEvents.SOUL_ESCAPE, 0.28F, 1.10F);
            case LIGHT_FAULT_01 -> play(descriptor, position, SoundEvents.WARDEN_HEARTBEAT, 0.35F, 0.50F);
            case PERIPHERAL_01 -> play(descriptor, position, SoundEvents.WARDEN_LISTENING, 0.25F, 0.60F);
            case SKY_MARK_01 -> play(descriptor, position, SoundEvents.WARDEN_LISTENING, 0.30F, 0.42F);
            case FALSE_PASSAGE_01 -> play(descriptor, position, SoundEvents.SCULK_CLICKING, 0.25F, 0.38F);
            case CHROMA_BREAK_01 -> {
                // The tear sound rides the pulse; no separate beat.
            }
            case NEAR_MISS_01 -> {
                // The crossing steps carry the scene; no extra beat.
            }
            case WHISPER_STEPS_01 -> {
                // The replayed steps carry the scene; no extra beat.
            }
            case FOOTSTEPS_01 -> {
                // Footsteps carry their own rhythm; no extra beat.
            }
            case COLOSSUS_01 -> {
                // The roar is scheduled by the colossus tick, not the generic
                // mid-beat, so it only sounds at the nearer stages.
            }
            case VISITATION_01 -> {
                // The window wrongness carries the scene; no in-game beat.
            }
            case RIFT_01 -> {
                // Overlay pulse carries the scene; arrival already sounded.
            }
        }
    }

    static void playResolved(SceneDescriptor descriptor, Vec3 position) {
        switch (descriptor.profile()) {
            case ECHO_01 -> play(descriptor, position, SoundEvents.ENDERMAN_TELEPORT, 0.45F, 0.60F);
            case THRESHOLD_01 -> play(descriptor, position, SoundEvents.SCULK_CLICKING_STOP, 0.50F, 0.55F);
            case MOTION_ECHO_01 -> play(descriptor, position, SoundEvents.ENDERMAN_TELEPORT, 0.35F, 0.90F);
            case LIGHT_FAULT_01 -> play(descriptor, position, SoundEvents.AMBIENT_CAVE.value(), 0.40F, 0.70F);
            case PERIPHERAL_01 -> play(descriptor, position, SoundEvents.ENDERMAN_TELEPORT, 0.30F, 1.30F);
            case SKY_MARK_01 -> play(descriptor, position, SoundEvents.ENDERMAN_TELEPORT, 0.35F, 1.10F);
            case FALSE_PASSAGE_01 -> playPassageCollapse(descriptor, position);
            case CHROMA_BREAK_01 -> play(descriptor, position, SoundEvents.SCULK_CLICKING_STOP, 0.35F, 0.60F);
            case NEAR_MISS_01 -> play(descriptor, position, SoundEvents.ENDERMAN_TELEPORT, 0.30F, 1.20F);
            case WHISPER_STEPS_01 -> {
                // Ends in silence (TIMEOUT): the replayed steps simply stop.
            }
            case FOOTSTEPS_01 -> {
                // Ends in silence (TIMEOUT): the steps simply stop.
            }
            case COLOSSUS_01 -> {
                // Never gaze-resolved: it recedes into the fog, or at the
                // finale it is simply gone. Nothing answers.
            }
            case VISITATION_01 -> {
                // The blink is simply over; nothing answers.
            }
            case RIFT_01 -> play(descriptor, position, SoundEvents.AMBIENT_CAVE.value(), 0.35F, 0.55F);
        }
    }

    static void playFootstep(SceneDescriptor descriptor, Vec3 position, int stepIndex) {
        float gait = 0.52F + (stepIndex % 2) * 0.06F;
        play(descriptor, position, SoundEvents.SCULK_BLOCK_STEP, 0.50F, gait);
    }

    /**
     * One of the target's own footsteps replayed from their past position.
     * Quieter and flatter than a live step — a memory, not a presence.
     */
    static void playWhisperStep(SceneDescriptor descriptor, Vec3 position, int stepIndex) {
        float gait = 0.40F + (stepIndex % 2) * 0.05F;
        play(descriptor, position, SoundEvents.SCULK_BLOCK_STEP, 0.38F, gait);
    }

    /** A soft, wrong-sounding step while the near-miss figure crosses behind. */
    static void playNearMissStep(SceneDescriptor descriptor, Vec3 position) {
        play(descriptor, position, SoundEvents.SOUL_SOIL_STEP, 0.42F, 0.55F);
    }

    /**
     * One colossus footfall: a deep boom felt through the ground. Played at
     * the target itself — no airborne sound carries three hundred blocks, but
     * the ground does. Pitch-seeded per scene like every scene sound.
     */
    static void playColossusStep(SceneDescriptor descriptor, int stage, int stepIndex) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        float pitch = 0.34F + stage * 0.02F + (stepIndex % 2) * 0.015F;
        float volume = 0.70F + stage * 0.05F;
        play(descriptor, minecraft.player.position(), SoundEvents.RAVAGER_STEP, volume, pitch);
    }

    /** A distant roar once the figure has fully arrived (nearer stages only). */
    static void playColossusRoar(SceneDescriptor descriptor, int stage) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        play(descriptor,
                minecraft.player.position(),
                SoundEvents.WARDEN_ROAR,
                0.80F,
                0.42F - stage * 0.01F);
    }

    /** The finale's held watch: a slow heartbeat while it stands there. */
    static void playColossusHeartbeat(SceneDescriptor descriptor) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        play(descriptor, minecraft.player.position(), SoundEvents.WARDEN_HEARTBEAT, 0.65F, 0.45F);
    }

    /** The vanish: one last deep rumble under the exact tick it is gone. */
    static void playColossusVanish(SceneDescriptor descriptor) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        play(descriptor, minecraft.player.position(), SoundEvents.RAVAGER_STEP, 0.85F, 0.26F);
    }

    /**
     * The sky mark's presence: a very low portal hum played at the target, so
     * it reads as pressure in the air rather than a sound from the sky.
     */
    static void playSkyMark(SceneDescriptor descriptor) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        play(descriptor, minecraft.player.position(), SoundEvents.PORTAL_AMBIENT, 0.45F, 0.50F);
    }

    /** The false passage announcing itself: stone that should not be there. */
    static void playPassageTear(SceneDescriptor descriptor, Vec3 position) {
        play(descriptor, position, SoundEvents.SCULK_CLICKING, 0.55F, 0.40F);
    }

    /** The doorway folding away once the target commits to approaching it. */
    static void playPassageCollapse(SceneDescriptor descriptor, Vec3 position) {
        play(descriptor, position, SoundEvents.SCULK_CLICKING_STOP, 0.60F, 0.45F);
    }

    /** The corrupted-recording tear: a short broken sculk shriek, kept quiet. */
    static void playChromaTear(SceneDescriptor descriptor) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        play(descriptor, minecraft.player.position(), SoundEvents.SCULK_CLICKING, 0.50F, 0.35F);
    }

    static void playRiftArrival(SceneDescriptor descriptor, Vec3 position) {
        int stage = descriptor.stage();
        if (RiftChoreography.isTear(stage)) {
            playChromaTear(descriptor);
            return;
        }
        if (RiftChoreography.isEclipse(stage)) {
            play(descriptor, position, SoundEvents.WARDEN_HEARTBEAT, 0.70F, 0.48F);
            return;
        }
        if (RiftChoreography.isUnmoor(stage)) {
            play(descriptor, position, SoundEvents.PORTAL_AMBIENT, 0.40F, 0.62F);
            return;
        }
        play(descriptor, position, SoundEvents.ENDERMAN_STARE, 0.55F, 0.42F);
    }

    /**
     * The ambience dip that opens a scene: a single low cave swell played at
     * the target itself, so it reads as the world tightening rather than a
     * sound source arriving.
     */
    static void playPrelude(SceneDescriptor descriptor) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        float pitch = 0.46F + Math.floorMod(descriptor.visualSeed() >>> 7, 3) * 0.03F;
        play(descriptor, minecraft.player.position(), SoundEvents.AMBIENT_CAVE.value(), 0.85F, pitch);
    }

    /** One faint clicking deep into the prelude, just before the body shows. */
    static void playPreludeClick(SceneDescriptor descriptor) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        play(descriptor, minecraft.player.position(), SoundEvents.SCULK_CLICKING, 0.30F, 0.45F);
    }

    /**
     * The false all-clear beat: exactly one quiet sound long after the scene
     * appeared to end. Profiles without an opinion stay silent.
     */
    static void playEncore(SceneDescriptor descriptor) {
        switch (descriptor.profile()) {
            case ECHO_01 -> play(
                    descriptor, descriptor.anchor(), SoundEvents.ENDERMAN_STARE, 0.50F, 0.40F);
            case PERIPHERAL_01 -> play(
                    descriptor, descriptor.anchor(), SoundEvents.WARDEN_LISTENING, 0.45F, 0.50F);
            case FALSE_PASSAGE_01 -> {
                // Half a minute after the doorway folded: one faint click
                // from where it stood. It was there. It remembers.
                play(descriptor, descriptor.anchor(), SoundEvents.SCULK_CLICKING, 0.35F, 0.40F);
            }
            case FOOTSTEPS_01 -> {
                // One last step, closer than the circling ever came, directly
                // behind wherever the target is facing now.
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player == null) {
                    return;
                }
                Vec3 look = minecraft.player.getLookAngle();
                Vec3 flat = new Vec3(look.x, 0.0D, look.z);
                if (flat.lengthSqr() < 1.0E-6D) {
                    flat = new Vec3(1.0D, 0.0D, 0.0D);
                }
                Vec3 behind = minecraft.player.position()
                        .subtract(flat.normalize().scale(2.0D));
                play(descriptor, behind, SoundEvents.SCULK_BLOCK_STEP, 0.70F, 0.50F);
            }
            default -> {
                // Profiles without an encore beat stay silent.
            }
        }
    }

    private static void play(
            SceneDescriptor descriptor,
            Vec3 position,
            SoundEvent event,
            float volume,
            float pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || position == null) {
            return;
        }
        // Volume also sets the audible radius (volume * 16 blocks). Scene
        // anchors sit 20+ blocks out, so an unranged volume would never reach
        // the target; scale it so the sound arrives faint instead of not at
        // all, letting distance attenuation do the work.
        double distance = minecraft.player.position().distanceTo(position);
        float rangedVolume = (float) Math.min(
                4.0D,
                Math.max(volume, distance / 16.0D * 1.08D));
        float jitter = 0.92F + ((descriptor.visualSeed() >>> 13) & 0xFL) / 15.0F * 0.16F;
        level.playLocalSound(
                position.x,
                position.y,
                position.z,
                event,
                SoundSource.HOSTILE,
                rangedVolume,
                pitch * jitter,
                false);
    }
}
