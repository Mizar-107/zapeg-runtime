package io.github.mizar107.zapegruntime.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class JournalBindingDataTest {

    private static final UUID PLAYER_A = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PLAYER_B = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID TOKEN_A = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID TOKEN_B = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");

    @Test
    void roundTripKeepsOneActiveTokenPerUuidAndRotationInvalidatesTheOldOne() {
        JournalBindingData data = new JournalBindingData();
        assertTrue(data.bindInitial(PLAYER_A, TOKEN_A));
        assertFalse(data.bindInitial(PLAYER_A, TOKEN_B));
        assertTrue(data.rotate(PLAYER_A, TOKEN_B));
        assertEquals(TOKEN_B, data.activeToken(PLAYER_A).orElseThrow());

        CompoundTag encoded = data.save(new CompoundTag());
        JournalBindingData decoded = JournalBindingData.load(encoded);
        assertTrue(decoded.writable());
        assertEquals(TOKEN_B, decoded.activeToken(PLAYER_A).orElseThrow());
        assertEquals(encoded, decoded.save(new CompoundTag()));
    }

    @Test
    void liveWritesRejectNilAndSharedTokens() {
        UUID nil = new UUID(0L, 0L);
        JournalBindingData data = new JournalBindingData();
        assertFalse(data.bindInitial(nil, TOKEN_A));
        assertFalse(data.bindInitial(PLAYER_A, nil));
        assertTrue(data.bindInitial(PLAYER_A, TOKEN_A));
        assertFalse(data.bindInitial(PLAYER_B, TOKEN_A));
        assertTrue(data.bindInitial(PLAYER_B, TOKEN_B));
        assertFalse(data.rotate(PLAYER_B, TOKEN_A));
    }

    @Test
    void duplicateActiveTokenFailsClosedAndPreservesTheRoot() {
        CompoundTag root = root(
                binding(PLAYER_A, TOKEN_A),
                binding(PLAYER_B, TOKEN_A));
        JournalBindingData decoded = JournalBindingData.load(root);
        assertFalse(decoded.writable());
        assertTrue(decoded.activeToken(PLAYER_A).isEmpty());
        assertEquals(root, decoded.save(new CompoundTag()));
    }

    @Test
    void nilPlayerOrTokenFailsClosedAndPreservesTheRoot() {
        UUID nil = new UUID(0L, 0L);
        for (CompoundTag root : new CompoundTag[] {
            root(binding(nil, TOKEN_A)), root(binding(PLAYER_A, nil))
        }) {
            JournalBindingData decoded = JournalBindingData.load(root);
            assertFalse(decoded.writable());
            assertEquals(root, decoded.save(new CompoundTag()));
        }
    }

    @Test
    void futureOrMalformedSchemaFailsClosed() {
        CompoundTag future = root(binding(PLAYER_A, TOKEN_A));
        future.putInt("SchemaVersion", JournalBindingData.CURRENT_SCHEMA + 1);
        JournalBindingData decoded = JournalBindingData.load(future);
        assertFalse(decoded.writable());
        assertEquals(future, decoded.save(new CompoundTag()));
    }

    private static CompoundTag root(CompoundTag... entries) {
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", JournalBindingData.CURRENT_SCHEMA);
        ListTag bindings = new ListTag();
        for (CompoundTag entry : entries) {
            bindings.add(entry);
        }
        root.put("Bindings", bindings);
        return root;
    }

    private static CompoundTag binding(UUID playerId, UUID token) {
        CompoundTag entry = new CompoundTag();
        entry.putUUID("PlayerId", playerId);
        entry.putUUID("ActiveToken", token);
        return entry;
    }
}
