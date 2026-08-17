package io.github.mizar107.zapegruntime.network;

import io.github.mizar107.zapegruntime.scene.SceneAck;
import io.github.mizar107.zapegruntime.server.SceneServerManager;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public record SceneAckC2S(UUID eventId, UUID targetId, SceneAck acknowledgement) {

    public static void encode(SceneAckC2S message, FriendlyByteBuf buffer) {
        buffer.writeUUID(message.eventId);
        buffer.writeUUID(message.targetId);
        buffer.writeByte(message.acknowledgement.wireId());
    }

    public static SceneAckC2S decode(FriendlyByteBuf buffer) {
        return new SceneAckC2S(
                buffer.readUUID(),
                buffer.readUUID(),
                SceneAck.fromWireId(buffer.readUnsignedByte()));
    }

    public static void handle(
            SceneAckC2S message,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            SceneServerManager.handleAcknowledgement(sender, message);
        }
    }
}
