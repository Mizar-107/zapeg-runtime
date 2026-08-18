package io.github.mizar107.zapegruntime.network;

import io.github.mizar107.zapegruntime.client.ClientSceneManager;
import io.github.mizar107.zapegruntime.scene.SceneDescriptor;
import io.github.mizar107.zapegruntime.scene.SceneProfile;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record SceneSpawnS2C(SceneDescriptor descriptor) {

    public static void encode(SceneSpawnS2C message, FriendlyByteBuf buffer) {
        SceneDescriptor scene = message.descriptor;
        buffer.writeUUID(scene.eventId());
        buffer.writeUUID(scene.targetId());
        buffer.writeResourceLocation(scene.dimension());
        buffer.writeDouble(scene.anchor().x);
        buffer.writeDouble(scene.anchor().y);
        buffer.writeDouble(scene.anchor().z);
        buffer.writeFloat(scene.yawDegrees());
        buffer.writeVarInt(scene.ttlTicks());
        buffer.writeLong(scene.visualSeed());
        buffer.writeByte(scene.profile().wireId());
        buffer.writeBoolean(scene.rehearsal());
        buffer.writeVarInt(scene.stage());
    }

    public static SceneSpawnS2C decode(FriendlyByteBuf buffer) {
        SceneDescriptor descriptor = new SceneDescriptor(
                buffer.readUUID(),
                buffer.readUUID(),
                buffer.readResourceLocation(),
                new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                buffer.readFloat(),
                buffer.readVarInt(),
                buffer.readLong(),
                SceneProfile.fromWireId(buffer.readUnsignedByte()),
                buffer.readBoolean(),
                buffer.readVarInt());
        return new SceneSpawnS2C(descriptor);
    }

    public static void handle(
            SceneSpawnS2C message,
            Supplier<NetworkEvent.Context> contextSupplier) {
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientSceneManager.accept(message.descriptor()));
    }
}
