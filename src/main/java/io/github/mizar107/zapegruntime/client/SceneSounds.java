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
        }
    }

    static void playResolved(SceneDescriptor descriptor, Vec3 position) {
        switch (descriptor.profile()) {
            case ECHO_01 -> play(descriptor, position, SoundEvents.ENDERMAN_TELEPORT, 0.45F, 0.60F);
                case THRESHOLD_01 -> play(descriptor, position, SoundEvents.SCULK_CLICKING_STOP, 0.50F, 0.55F);
                case MOTION_ECHO_01 -> play(descriptor, position, SoundEvents.ENDERMAN_TELEPORT, 0.35F, 0.90F);
                case LIGHT_FAULT_01 -> play(descriptor, position, SoundEvents.AMBIENT_CAVE.value(), 0.40F, 0.70F);
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
        float jitter = 0.92F + ((descriptor.visualSeed() >>> 13) & 0xFL) / 15.0F * 0.16F;
        level.playLocalSound(
                position.x,
                position.y,
                position.z,
                event,
                SoundSource.HOSTILE,
                volume,
                pitch * jitter,
                false);
    }
}
