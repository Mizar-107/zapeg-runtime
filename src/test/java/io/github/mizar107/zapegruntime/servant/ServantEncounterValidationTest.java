package io.github.mizar107.zapegruntime.servant;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class ServantEncounterValidationTest {

    private static final UUID EVENT = UUID.fromString("5a574cc2-60fb-41db-b38c-d01a8777576e");
    private static final UUID TARGET = UUID.fromString("bb820c67-1fbc-47f5-9901-a57d82e972a8");
    private static final UUID ENTITY = UUID.fromString("cf8d5205-b102-49b9-97f3-a04f632f6a68");

    @Test
    void rejectsNilAliasedAndMalformedIdentityFields() {
        assertThrows(IllegalArgumentException.class, () -> encounter(new UUID(0L, 0L), TARGET, ENTITY,
                "minecraft:overworld", 1L));
        assertThrows(IllegalArgumentException.class, () -> encounter(EVENT, TARGET, TARGET,
                "minecraft:overworld", 1L));
        assertThrows(IllegalArgumentException.class, () -> encounter(EVENT, TARGET, ENTITY,
                "Not A Dimension", 1L));
        assertThrows(IllegalArgumentException.class, () -> encounter(EVENT, TARGET, ENTITY,
                "x:" + "a".repeat(ServantEncounter.MAX_DIMENSION_ID_LENGTH), 1L));
        assertThrows(IllegalArgumentException.class, () -> encounter(EVENT, TARGET, ENTITY,
                "minecraft:overworld", -1L));
    }

    @Test
    void nbtLoaderRequiresEveryTypedField() {
        CompoundTag incomplete = new CompoundTag();
        incomplete.putUUID("EncounterId", EVENT);
        incomplete.putUUID("TargetId", TARGET);
        incomplete.putUUID("ServantId", ENTITY);
        incomplete.putString("Dimension", "minecraft:overworld");
        incomplete.putBoolean("Rehearsal", false);
        incomplete.putLong("Deadline", 10L);
        // RecoveryAttempted deliberately missing.
        assertThrows(IllegalArgumentException.class, () -> ServantEncounter.load(incomplete));

        incomplete.putString("RecoveryAttempted", "wrong-type");
        assertThrows(IllegalArgumentException.class, () -> ServantEncounter.load(incomplete));
    }

    private static ServantEncounter encounter(
            UUID event,
            UUID target,
            UUID entity,
            String dimension,
            long deadline) {
        return new ServantEncounter(event, target, entity, dimension, false, deadline, false);
    }
}
