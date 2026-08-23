package io.github.mizar107.zapegruntime.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class HeraldorWorldDataTest {

    private static final UUID PLAYER_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID PLAYER_B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    @Test
    void versionedRoundTripRetainsUuidKeyedProgress() {
        HeraldorWorldData data = new HeraldorWorldData();
        assertTrue(data.markMilestone(PLAYER_A, "chapter.01.arrival"));
        assertTrue(data.markMilestone(PLAYER_A, "chapter.02.witness"));
        assertTrue(data.recordVictory(
                PLAYER_A,
                UUID.fromString("10000000-0000-0000-0000-000000000001")));

        CompoundTag encoded = data.save(new CompoundTag());
        assertEquals(HeraldorWorldData.CURRENT_SCHEMA_VERSION, encoded.getInt("SchemaVersion"));

        HeraldorWorldData loaded = HeraldorWorldData.load(encoded);
        assertEquals(HeraldorWorldData.CURRENT_SCHEMA_VERSION, loaded.loadedSchemaVersion());
        assertEquals(
                new HeraldorWorldData.PlayerSnapshot(
                        1,
                        Set.of("chapter.01.arrival", "chapter.02.witness"),
                        1),
                loaded.snapshot(PLAYER_A));
        assertEquals(
                new HeraldorWorldData.PlayerSnapshot(0, Set.of(), 0),
                loaded.snapshot(PLAYER_B));
    }

    @Test
    void eventConsumptionIsIdempotentPerPlayerAndPersists() {
        HeraldorWorldData data = new HeraldorWorldData();
        UUID eventId = UUID.fromString("20000000-0000-0000-0000-000000000002");

        assertTrue(data.consumeEvent(PLAYER_A, eventId));
        assertFalse(data.consumeEvent(PLAYER_A, eventId));
        assertTrue(data.consumeEvent(PLAYER_B, eventId));

        HeraldorWorldData loaded = HeraldorWorldData.load(data.save(new CompoundTag()));
        assertFalse(loaded.consumeEvent(PLAYER_A, eventId));
        assertFalse(loaded.consumeEvent(PLAYER_B, eventId));
        assertEquals(1, loaded.snapshot(PLAYER_A).consumedEventCount());
        assertEquals(1, loaded.snapshot(PLAYER_B).consumedEventCount());
    }

    @Test
    void eventBackedMutationsAreAtomicAndCannotReplay() {
        HeraldorWorldData data = new HeraldorWorldData();
        UUID victory = UUID.fromString("30000000-0000-0000-0000-000000000003");
        UUID milestone = UUID.fromString("40000000-0000-0000-0000-000000000004");

        assertTrue(data.recordVictory(PLAYER_A, victory));
        assertFalse(data.recordVictory(PLAYER_A, victory));
        assertTrue(data.applyMilestone(PLAYER_A, milestone, "servant.stalker.defeated"));
        assertFalse(data.applyMilestone(PLAYER_A, milestone, "servant.herald.defeated"));

        HeraldorWorldData.PlayerSnapshot state = data.snapshot(PLAYER_A);
        assertEquals(1, state.victories());
        assertEquals(Set.of("servant.stalker.defeated"), state.milestones());
        assertEquals(2, state.consumedEventCount());
    }

    @Test
    void invalidMilestoneDoesNotConsumeItsEvent() {
        HeraldorWorldData data = new HeraldorWorldData();
        UUID eventId = UUID.fromString("50000000-0000-0000-0000-000000000005");

        assertThrows(
                IllegalArgumentException.class,
                () -> data.applyMilestone(PLAYER_A, eventId, "Unsafe Milestone"));
        assertTrue(data.consumeEvent(PLAYER_A, eventId));
    }

    @Test
    void unversionedEmptyStateMigratesOnSave() {
        HeraldorWorldData loaded = HeraldorWorldData.load(new CompoundTag());
        assertEquals(0, loaded.loadedSchemaVersion());
        assertTrue(loaded.isDirty());
        assertEquals(
                new HeraldorWorldData.SchemaStatus(0, 1, true, true),
                loaded.schemaStatus());
        assertEquals(
                HeraldorWorldData.CURRENT_SCHEMA_VERSION,
                loaded.save(new CompoundTag()).getInt("SchemaVersion"));
    }

    @Test
    void schemaZeroMigrationRetainsRecognizedProgress() {
        HeraldorWorldData original = new HeraldorWorldData();
        original.markMilestone(PLAYER_A, "legacy.arrival");
        original.recordVictory(
                PLAYER_A,
                UUID.fromString("70000000-0000-0000-0000-000000000007"));
        CompoundTag legacyRoot = original.save(new CompoundTag());
        legacyRoot.remove("SchemaVersion");

        HeraldorWorldData migrated = HeraldorWorldData.load(legacyRoot);
        assertTrue(migrated.isDirty());
        assertEquals(1, migrated.snapshot(PLAYER_A).victories());
        assertEquals(Set.of("legacy.arrival"), migrated.snapshot(PLAYER_A).milestones());
    }

    @Test
    void futureSchemaIsReadOnlyAndRoundTripsWithoutDowngradeOrFieldLoss() {
        CompoundTag futureRoot = new CompoundTag();
        futureRoot.putInt("SchemaVersion", 8);
        futureRoot.putString("FutureOnly", "keep-me-verbatim");
        CompoundTag nested = new CompoundTag();
        nested.putLong("UnknownCounter", 99L);
        futureRoot.put("UnknownPayload", nested);

        HeraldorWorldData loaded = HeraldorWorldData.load(futureRoot);
        assertEquals(
                new HeraldorWorldData.SchemaStatus(8, 1, false, false),
                loaded.schemaStatus());
        assertTrue(loaded.snapshotForDiagnostics(PLAYER_A).isEmpty());
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> loaded.recordVictory(
                        PLAYER_A,
                        UUID.fromString("60000000-0000-0000-0000-000000000006")));
        assertTrue(failure.getMessage().contains("schema 8"));

        CompoundTag preserved = loaded.save(new CompoundTag());
        assertEquals(8, preserved.getInt("SchemaVersion"));
        assertEquals("keep-me-verbatim", preserved.getString("FutureOnly"));
        assertEquals(99L, preserved.getCompound("UnknownPayload").getLong("UnknownCounter"));
    }
}
