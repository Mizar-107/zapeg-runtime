package io.github.mizar107.zapegruntime.network;

import io.github.mizar107.zapegruntime.journal.JournalView;
import io.github.mizar107.zapegruntime.journal.client.JournalClient;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Fixed five-byte authorized journal view; it contains no node or localization key. */
public record JournalOpenS2C(JournalView view) {

    public static final int WIRE_BYTES = 5;

    public JournalOpenS2C {
        Objects.requireNonNull(view, "view");
    }

    public static void encode(JournalOpenS2C message, FriendlyByteBuf buffer) {
        buffer.writeInt(message.view().unlockedMask());
        buffer.writeByte(message.view().currentOrdinal());
    }

    public static JournalOpenS2C decode(FriendlyByteBuf buffer) {
        if (buffer.readableBytes() != WIRE_BYTES) {
            throw new IllegalArgumentException("journal view packet must be exactly five bytes");
        }
        return new JournalOpenS2C(
                new JournalView(buffer.readInt(), buffer.readUnsignedByte()));
    }

    public static void handle(
            JournalOpenS2C message,
            Supplier<NetworkEvent.Context> contextSupplier) {
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> JournalClient.open(message.view()));
    }
}
