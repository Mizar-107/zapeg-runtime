package io.github.mizar107.zapegruntime.network;

import io.github.mizar107.zapegruntime.journal.JournalAction;
import io.github.mizar107.zapegruntime.journal.JournalService;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** One-byte request. Sender identity, possession, and progress are derived server-side. */
public record JournalActionC2S(JournalAction action) {

    public static final int WIRE_BYTES = 1;

    public JournalActionC2S {
        Objects.requireNonNull(action, "action");
    }

    public static void encode(JournalActionC2S message, FriendlyByteBuf buffer) {
        buffer.writeByte(message.action().wireId());
    }

    public static JournalActionC2S decode(FriendlyByteBuf buffer) {
        if (buffer.readableBytes() != WIRE_BYTES) {
            throw new IllegalArgumentException("journal action packet must be exactly one byte");
        }
        return new JournalActionC2S(
                JournalAction.fromWireId(buffer.readUnsignedByte()));
    }

    public static void handle(
            JournalActionC2S message,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            JournalService.handleAction(sender, message.action());
        }
    }
}
