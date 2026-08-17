package io.github.mizar107.zapegruntime.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class SceneLedgerDataTest {

    @Test
    void consumeIsIdempotentAndPersists() {
        SceneLedgerData data = new SceneLedgerData();
        UUID eventId = UUID.randomUUID();
        assertTrue(data.consume(eventId));
        assertFalse(data.consume(eventId));

        SceneLedgerData loaded = SceneLedgerData.load(data.save(new CompoundTag()));
        assertTrue(loaded.contains(eventId));
        assertFalse(loaded.consume(eventId));
    }

    @Test
    void ledgerEvictsOldestEntriesAtBound() {
        SceneLedgerData data = new SceneLedgerData();
        UUID first = new UUID(0L, 1L);
        for (long index = 1L; index <= 300L; index++) {
            assertTrue(data.consume(new UUID(0L, index)));
        }
        assertEquals(256, data.size());
        assertFalse(data.contains(first));
        assertTrue(data.contains(new UUID(0L, 300L)));
    }
}
