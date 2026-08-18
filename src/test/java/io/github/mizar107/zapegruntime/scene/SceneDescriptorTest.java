package io.github.mizar107.zapegruntime.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SceneDescriptorTest {

    @Test
    void acceptsBoundedDescriptor() {
        SceneDescriptor descriptor = descriptor(200, 0.0F, new Vec3(12.5D, 80.0D, -3.5D));
        assertEquals(SceneProfile.ECHO_01, descriptor.profile());
        assertEquals(200, descriptor.ttlTicks());
    }

    @Test
    void acceptsDirectorScaledTtlOverride() {
        SceneDescriptor descriptor =
                descriptor(SceneDescriptor.MAX_TTL_TICKS, 0.0F, Vec3.ZERO);
        assertEquals(SceneDescriptor.MAX_TTL_TICKS, descriptor.ttlTicks());
    }

    @Test
    void rejectsUntrustedWireBounds() {
        assertThrows(IllegalArgumentException.class, () -> descriptor(19, 0.0F, Vec3.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () -> descriptor(SceneDescriptor.MAX_TTL_TICKS + 1, 0.0F, Vec3.ZERO));
        assertThrows(IllegalArgumentException.class, () -> descriptor(200, Float.NaN, Vec3.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () -> descriptor(200, 0.0F, new Vec3(30_000_001.0D, 64.0D, 0.0D)));
        assertThrows(
                IllegalArgumentException.class,
                () -> descriptor(200, 0.0F, new Vec3(0.0D, Double.NaN, 0.0D)));
    }

    @Test
    void stageIsBoundedAndReservedForTheColossus() {
        // Every profile carries the zero default; only colossus_01 may carry
        // an escalation stage, and never past the choreography's stage count.
        SceneDescriptor colossus = descriptor(
                SceneProfile.COLOSSUS_01, 200, 0.0F, Vec3.ZERO, ColossusChoreography.MAX_STAGE);
        assertEquals(ColossusChoreography.MAX_STAGE, colossus.stage());
        assertThrows(
                IllegalArgumentException.class,
                () -> descriptor(
                        SceneProfile.COLOSSUS_01, 200, 0.0F, Vec3.ZERO,
                        ColossusChoreography.MAX_STAGE + 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> descriptor(SceneProfile.COLOSSUS_01, 200, 0.0F, Vec3.ZERO, -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> descriptor(SceneProfile.ECHO_01, 200, 0.0F, Vec3.ZERO, 1));
    }

    private static SceneDescriptor descriptor(int ttl, float yaw, Vec3 anchor) {
        return descriptor(SceneProfile.ECHO_01, ttl, yaw, anchor, 0);
    }

    private static SceneDescriptor descriptor(
            SceneProfile profile, int ttl, float yaw, Vec3 anchor, int stage) {
        return new SceneDescriptor(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                anchor,
                yaw,
                ttl,
                42L,
                profile,
                true,
                stage);
    }
}
