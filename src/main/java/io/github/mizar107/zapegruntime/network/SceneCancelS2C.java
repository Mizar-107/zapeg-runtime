package io.github.mizar107.zapegruntime.network;

import io.github.mizar107.zapegruntime.client.ClientSceneManager;
import io.github.mizar107.zapegruntime.scene.CancelReason;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record SceneCancelS2C(UUID eventId, CancelReason reason) {

    public static void encode(SceneCancelS2C message, FriendlyByteBuf buffer) {
        buffer.writeUUID(message.eventId);
        buffer.writeByte(message.reason.wireId());
    }

    public static SceneCancelS2C decode(FriendlyByteBuf buffer) {
        return new SceneCancelS2C(
                buffer.readUUID(),
                CancelReason.fromWireId(buffer.readUnsignedByte()));
    }

    public static void handle(
            SceneCancelS2C message,
            Supplier<NetworkEvent.Context> contextSupplier) {
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientSceneManager.cancel(message.eventId(), message.reason()));
    }
}
