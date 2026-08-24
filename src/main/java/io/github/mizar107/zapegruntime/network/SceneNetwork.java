package io.github.mizar107.zapegruntime.network;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.scene.CancelReason;
import io.github.mizar107.zapegruntime.scene.SceneAck;
import io.github.mizar107.zapegruntime.scene.SceneDescriptor;
import io.github.mizar107.zapegruntime.scene.OsScareReport;
import io.github.mizar107.zapegruntime.journal.JournalAction;
import io.github.mizar107.zapegruntime.journal.JournalView;
import java.util.UUID;
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
    // v6 adds visitation_01 (wire id 12); the descriptor layout is unchanged,
    // but a v5 client would fail closed on the unknown id mid-session, so the
    // handshake refuses the mismatch up front instead.
    // v7 adds rift_01 (wire id 13) and generalises the existing stage field
    // to haunt/rift families; a v6 client would reject non-colossus stages
    // (or unknown id 13) mid-session, so the handshake refuses the mismatch.
    // v8 adds a fixed-size visitation effect-status packet. It separates
    // verified OS outcomes from the generic scene acknowledgement stream;
    // mixed v7/v8 installations must fail the handshake rather than report a
    // false VISIBLE or silently discard diagnostics.
    /** Protocol 9 adds {@code breach_01} and the truthful AVAILABLE fallback state. */
    // Protocol 10 adds a fixed five-byte authorized journal view and a
    // one-byte closed journal action. Mixed installations must fail at login.
    public static final String PROTOCOL = "10";
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
        CHANNEL.messageBuilder(
                        OsScareStatusC2S.class,
                        nextMessageId++,
                        NetworkDirection.PLAY_TO_SERVER)
                .encoder(OsScareStatusC2S::encode)
                .decoder(OsScareStatusC2S::decode)
                .consumerMainThread(OsScareStatusC2S::handle)
                .add();
        CHANNEL.messageBuilder(
                        JournalOpenS2C.class,
                        nextMessageId++,
                        NetworkDirection.PLAY_TO_CLIENT)
                .encoder(JournalOpenS2C::encode)
                .decoder(JournalOpenS2C::decode)
                .consumerMainThread(JournalOpenS2C::handle)
                .add();
        CHANNEL.messageBuilder(
                        JournalActionC2S.class,
                        nextMessageId++,
                        NetworkDirection.PLAY_TO_SERVER)
                .encoder(JournalActionC2S::encode)
                .decoder(JournalActionC2S::decode)
                .consumerMainThread(JournalActionC2S::handle)
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

    public static void reportOsScare(
            UUID eventId,
            UUID targetId,
            int sequence,
            OsScareReport report) {
        CHANNEL.sendToServer(new OsScareStatusC2S(eventId, targetId, sequence, report));
    }

    public static void openJournal(ServerPlayer target, JournalView view) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> target), new JournalOpenS2C(view));
    }

    public static void journalAction(JournalAction action) {
        CHANNEL.sendToServer(new JournalActionC2S(action));
    }
}
