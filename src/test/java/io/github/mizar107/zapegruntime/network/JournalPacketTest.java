package io.github.mizar107.zapegruntime.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.mizar107.zapegruntime.journal.JournalAction;
import io.github.mizar107.zapegruntime.journal.JournalView;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class JournalPacketTest {

    @Test
    void authorizedViewPacketIsAlwaysFiveBytes() {
        for (int ordinal = 0; ordinal < JournalView.ENTRY_COUNT; ordinal++) {
            JournalOpenS2C message = new JournalOpenS2C(JournalView.through(ordinal));
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                JournalOpenS2C.encode(message, buffer);
                assertEquals(JournalOpenS2C.WIRE_BYTES, buffer.readableBytes());
                assertEquals(message, JournalOpenS2C.decode(buffer));
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void actionPacketIsAlwaysOneClosedByte() {
        for (JournalAction action : JournalAction.values()) {
            JournalActionC2S message = new JournalActionC2S(action);
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                JournalActionC2S.encode(message, buffer);
                assertEquals(JournalActionC2S.WIRE_BYTES, buffer.readableBytes());
                assertEquals(message, JournalActionC2S.decode(buffer));
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void malformedViewAndUnknownActionFailDecode() {
        FriendlyByteBuf sparse = new FriendlyByteBuf(Unpooled.buffer());
        sparse.writeInt(0b101);
        sparse.writeByte(2);
        assertThrows(IllegalArgumentException.class, () -> JournalOpenS2C.decode(sparse));
        sparse.release();

        FriendlyByteBuf unknown = new FriendlyByteBuf(Unpooled.buffer());
        unknown.writeByte(255);
        assertThrows(IllegalArgumentException.class, () -> JournalActionC2S.decode(unknown));
        unknown.release();

        FriendlyByteBuf oversized = new FriendlyByteBuf(Unpooled.buffer());
        oversized.writeByte(JournalAction.REVEAL_PALIMPSEST.wireId());
        oversized.writeByte(0);
        assertThrows(IllegalArgumentException.class, () -> JournalActionC2S.decode(oversized));
        oversized.release();
    }
}
