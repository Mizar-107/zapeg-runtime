package io.github.mizar107.zapegruntime.network;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.scene.CancelReason;
import io.github.mizar107.zapegruntime.scene.SceneAck;
import io.github.mizar107.zapegruntime.scene.SceneDescriptor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class SceneNetwork {

    // v5 adds the bounded escalation stage to the spawn descriptor (used by
    // colossus_01, wire id 11); a v4 client would misread the longer payload,
    // so the channel version must refuse the mismatch instead.
    public static final String PROTOCOL = "5";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ZapeGRuntime.MOD_ID, "scenes"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);
    private static int nextMessageId;

    private SceneNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(
                        SceneSpawnS2C.class,
                        nextMessageId++,
                        NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SceneSpawnS2C::encode)
                .decoder(SceneSpawnS2C::decode)
                .consumerMainThread(SceneSpawnS2C::handle)
                .add();
        CHANNEL.messageBuilder(
                        SceneCancelS2C.class,
                        nextMessageId++,
                        NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SceneCancelS2C::encode)
                .decoder(SceneCancelS2C::decode)
                .consumerMainThread(SceneCancelS2C::handle)
                .add();
        CHANNEL.messageBuilder(
                        SceneAckC2S.class,
                        nextMessageId++,
                        NetworkDirection.PLAY_TO_SERVER)
                .encoder(SceneAckC2S::encode)
                .decoder(SceneAckC2S::decode)
                .consumerMainThread(SceneAckC2S::handle)
                .add();
    }

    public static void spawnFor(ServerPlayer target, SceneDescriptor descriptor) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> target), new SceneSpawnS2C(descriptor));
    }

    public static void cancelFor(ServerPlayer target, java.util.UUID eventId, CancelReason reason) {
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> target),
                new SceneCancelS2C(eventId, reason));
    }

    public static void acknowledge(
            java.util.UUID eventId,
            java.util.UUID targetId,
            SceneAck acknowledgement) {
        CHANNEL.sendToServer(new SceneAckC2S(eventId, targetId, acknowledgement));
    }
}
