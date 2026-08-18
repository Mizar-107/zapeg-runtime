package io.github.mizar107.zapegruntime.client;

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
            case FOOTSTEPS_01 -> {
                // The first footstep is itself the arrival beat.
            }
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
            case FOOTSTEPS_01 -> {
                // Footsteps carry their own rhythm; no extra beat.
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
            case FOOTSTEPS_01 -> {
                // Ends in silence (TIMEOUT): the steps simply stop.
            }
        }
    }

    static void playFootstep(SceneDescriptor descriptor, Vec3 position, int stepIndex) {
        float gait = 0.52F + (stepIndex % 2) * 0.06F;
        play(descriptor, position, SoundEvents.SCULK_BLOCK_STEP, 0.50F, gait);
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
