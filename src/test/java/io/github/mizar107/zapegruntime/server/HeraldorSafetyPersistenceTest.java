package io.github.mizar107.zapegruntime.server;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class HeraldorSafetyPersistenceTest {

    @Test
    void swallowedVanillaWriteFailureIsCaughtByReadBackAndRemainsDirty() {
        HeraldorSafetyData staleDisk = new HeraldorSafetyData();
        CompoundTag staleEnvelope = envelope(staleDisk);
        HeraldorSafetyData expected = HeraldorSafetyData.load(
                staleDisk.save(new CompoundTag()));
        expected.transition(HeraldorSafetyMode.MANUAL, expected.nonce());

        assertThrows(
                IllegalStateException.class,
                () -> HeraldorSafetyPersistence.flushAndVerify(
                        expected,
                        () -> expected.setDirty(false), // vanilla returned normally after failure
                        () -> staleEnvelope,
                        () -> {}));
        assertTrue(expected.isDirty());
    }

    @Test
    void exactForcedReadBackIsAccepted() {
        HeraldorSafetyData expected = new HeraldorSafetyData();
        CompoundTag exactEnvelope = envelope(expected);

        assertDoesNotThrow(() -> HeraldorSafetyPersistence.flushAndVerify(
                expected,
                () -> expected.setDirty(false),
                () -> exactEnvelope,
                () -> {}));
    }

    private static CompoundTag envelope(HeraldorSafetyData data) {
        CompoundTag envelope = new CompoundTag();
        envelope.put("data", data.save(new CompoundTag()));
        return envelope;
    }
}
