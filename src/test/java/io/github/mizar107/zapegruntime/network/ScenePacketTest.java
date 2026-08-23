package io.github.mizar107.zapegruntime.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.mizar107.zapegruntime.scene.CancelReason;
import io.github.mizar107.zapegruntime.scene.OsEffect;
import io.github.mizar107.zapegruntime.scene.OsEffectOutcome;
import io.github.mizar107.zapegruntime.scene.OsEffectReason;
import io.github.mizar107.zapegruntime.scene.OsScareReport;
import io.github.mizar107.zapegruntime.scene.SceneAck;
import io.github.mizar107.zapegruntime.scene.SceneDescriptor;
import io.github.mizar107.zapegruntime.scene.SceneProfile;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ScenePacketTest {

    @Test
    void spawnPacketRoundTripsEveryField() {
        for (SceneProfile profile : SceneProfile.values()) {
            int stage = profile.maxStage() > 0 ? Math.min(2, profile.maxStage()) : 0;
            SceneDescriptor descriptor = new SceneDescriptor(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether"),
                    new Vec3(12.25D, 70.0D, -18.75D),
                    122.5F,
                    profile.defaultTtlTicks(),
                    -123456789L,
                    profile,
                    false,
                    stage);
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                SceneSpawnS2C.encode(new SceneSpawnS2C(descriptor), buffer);
                assertEquals(descriptor, SceneSpawnS2C.decode(buffer).descriptor());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void acknowledgementAndCancelPacketsRoundTrip() {
        UUID eventId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        FriendlyByteBuf acknowledgementBuffer = new FriendlyByteBuf(Unpooled.buffer());
        SceneAckC2S.encode(
                new SceneAckC2S(eventId, targetId, SceneAck.GAZE),
                acknowledgementBuffer);
        assertEquals(
                new SceneAckC2S(eventId, targetId, SceneAck.GAZE),
                SceneAckC2S.decode(acknowledgementBuffer));
        acknowledgementBuffer.release();

        FriendlyByteBuf cancelBuffer = new FriendlyByteBuf(Unpooled.buffer());
        SceneCancelS2C.encode(
                new SceneCancelS2C(eventId, CancelReason.OPERATOR),
                cancelBuffer);
        assertEquals(
                new SceneCancelS2C(eventId, CancelReason.OPERATOR),
                SceneCancelS2C.decode(cancelBuffer));
        cancelBuffer.release();
    }

    @Test
    void osScareStatusPacketRoundTripsEveryBoundedOutcome() {
        UUID eventId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        OsScareReport report = new OsScareReport(
                OsEffectOutcome.applied(OsEffect.WINDOW_TITLE),
                OsEffectOutcome.unsupported(
                        OsEffect.WINDOW_MOTION, OsEffectReason.FULLSCREEN),
                OsEffectOutcome.failed(
                        OsEffect.EXTERNAL_POPUP, OsEffectReason.ASSET_INVALID),
                OsEffectOutcome.disabled(
                        OsEffect.TASKBAR, OsEffectReason.EFFECT_DISABLED));
        OsScareStatusC2S message = new OsScareStatusC2S(eventId, targetId, 4, report);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            OsScareStatusC2S.encode(message, buffer);
            assertEquals(message, OsScareStatusC2S.decode(buffer));
        } finally {
            buffer.release();
        }
        assertThrows(IllegalArgumentException.class, () -> new OsScareStatusC2S(
                eventId, targetId, -1, report));
        assertThrows(IllegalArgumentException.class, () -> new OsScareStatusC2S(
                eventId, targetId, OsScareStatusC2S.MAX_SEQUENCE + 1, report));
    }
}
