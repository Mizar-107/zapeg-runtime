package io.github.mizar107.zapegruntime.servant;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Persistent identity for one Servant encounter. */
public record ServantEncounter(
        UUID encounterId,
        UUID targetId,
        UUID servantId,
        String dimension,
        boolean rehearsal,
        long deadlineGameTime,
        boolean recoveryAttempted,
        ServantArchetype archetype) {

    static final int MAX_DIMENSION_ID_LENGTH = 128;
    private static final UUID NIL_UUID = new UUID(0L, 0L);
    private static final String ENCOUNTER_ID = "EncounterId";
    private static final String TARGET_ID = "TargetId";
    private static final String SERVANT_ID = "ServantId";
    private static final String DIMENSION = "Dimension";
    private static final String REHEARSAL = "Rehearsal";
    private static final String DEADLINE = "Deadline";
    private static final String RECOVERY_ATTEMPTED = "RecoveryAttempted";
    private static final String ARCHETYPE = "Archetype";

    /** Compatibility constructor for pre-archetype callers and tests. */
    public ServantEncounter(
            UUID encounterId,
            UUID targetId,
            UUID servantId,
            String dimension,
            boolean rehearsal,
            long deadlineGameTime,
            boolean recoveryAttempted) {
        this(
                encounterId,
                targetId,
                servantId,
                dimension,
                rehearsal,
                deadlineGameTime,
                recoveryAttempted,
                ServantArchetype.STALKER);
    }

    public ServantEncounter {
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(servantId, "servantId");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(archetype, "archetype");
        if (NIL_UUID.equals(encounterId)
                || NIL_UUID.equals(targetId)
                || NIL_UUID.equals(servantId)) {
            throw new IllegalArgumentException("Servant UUIDs must not be nil");
        }
        if (encounterId.equals(targetId)
                || encounterId.equals(servantId)
                || targetId.equals(servantId)) {
            throw new IllegalArgumentException("Servant UUID roles must be distinct");
        }
        if (dimension.isBlank() || dimension.length() > MAX_DIMENSION_ID_LENGTH) {
            throw new IllegalArgumentException("invalid dimension length");
        }
        ResourceLocation parsedDimension = ResourceLocation.tryParse(dimension);
        if (parsedDimension == null || !parsedDimension.toString().equals(dimension)) {
            throw new IllegalArgumentException("invalid dimension id");
        }
        if (deadlineGameTime < 0L) {
            throw new IllegalArgumentException("deadlineGameTime must be non-negative");
        }
    }

    public boolean isExpired(long gameTime) {
        return gameTime >= deadlineGameTime;
    }

    public ServantEncounter claimRecovery() {
        if (recoveryAttempted) {
            return this;
        }
        return new ServantEncounter(
                encounterId,
                targetId,
                servantId,
                dimension,
                rehearsal,
                deadlineGameTime,
                true,
                archetype);
    }

    public ServantEncounter withRecoveredEntity(UUID replacementId) {
        return new ServantEncounter(
                encounterId,
                targetId,
                replacementId,
                dimension,
                rehearsal,
                deadlineGameTime,
                true,
                archetype);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(ENCOUNTER_ID, encounterId);
        tag.putUUID(TARGET_ID, targetId);
        tag.putUUID(SERVANT_ID, servantId);
        tag.putString(DIMENSION, dimension);
        tag.putBoolean(REHEARSAL, rehearsal);
        tag.putLong(DEADLINE, deadlineGameTime);
        tag.putBoolean(RECOVERY_ATTEMPTED, recoveryAttempted);
        tag.putString(ARCHETYPE, archetype.id());
        return tag;
    }

    static ServantEncounter load(CompoundTag tag) {
        return load(tag, false);
    }

    static ServantEncounter load(CompoundTag tag, boolean legacyV1) {
        if (!tag.hasUUID(ENCOUNTER_ID)
                || !tag.hasUUID(TARGET_ID)
                || !tag.hasUUID(SERVANT_ID)
                || !tag.contains(DIMENSION, Tag.TAG_STRING)
                || !tag.contains(REHEARSAL, Tag.TAG_BYTE)
                || !tag.contains(DEADLINE, Tag.TAG_LONG)
                || !tag.contains(RECOVERY_ATTEMPTED, Tag.TAG_BYTE)
                || (!legacyV1 && !tag.contains(ARCHETYPE, Tag.TAG_STRING))) {
            throw new IllegalArgumentException("incomplete Servant encounter");
        }
        ServantArchetype loadedArchetype = legacyV1
                ? ServantArchetype.STALKER
                : ServantArchetype.fromId(tag.getString(ARCHETYPE))
                        .orElseThrow(() -> new IllegalArgumentException(
                                "invalid Servant archetype"));
        return new ServantEncounter(
                tag.getUUID(ENCOUNTER_ID),
                tag.getUUID(TARGET_ID),
                tag.getUUID(SERVANT_ID),
                tag.getString(DIMENSION),
                tag.getBoolean(REHEARSAL),
                tag.getLong(DEADLINE),
                tag.getBoolean(RECOVERY_ATTEMPTED),
                loadedArchetype);
    }
}
