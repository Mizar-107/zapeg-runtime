package io.github.mizar107.zapegruntime.servant;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/**
 * Persistent identity and recovery coordinates for one Servant encounter.
 *
 * <p>The encounter UUID is the durable identity. The entity UUID may change
 * during reconciliation if Minecraft saved the ledger but not the entity's
 * chunk. This distinction is what makes retries idempotent without making a
 * missing entity permanently brick a player's story.</p>
 */
public record ServantEncounter(
        UUID encounterId,
        UUID targetId,
        UUID servantId,
        String dimension,
        boolean rehearsal,
        long deadlineGameTime,
        int chunkX,
        int chunkZ) {

    private static final String ENCOUNTER_ID = "EncounterId";
    private static final String TARGET_ID = "TargetId";
    private static final String SERVANT_ID = "ServantId";
    private static final String DIMENSION = "Dimension";
    private static final String REHEARSAL = "Rehearsal";
    private static final String DEADLINE = "Deadline";
    private static final String CHUNK_X = "ChunkX";
    private static final String CHUNK_Z = "ChunkZ";

    public ServantEncounter {
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(servantId, "servantId");
        Objects.requireNonNull(dimension, "dimension");
        if (dimension.isBlank()) {
            throw new IllegalArgumentException("dimension must not be blank");
        }
        if (deadlineGameTime < 0L) {
            throw new IllegalArgumentException("deadlineGameTime must be non-negative");
        }
    }

    public boolean isExpired(long gameTime) {
        return gameTime >= deadlineGameTime;
    }

    public ServantEncounter withEntity(UUID replacementId, int replacementChunkX, int replacementChunkZ) {
        return new ServantEncounter(
                encounterId,
                targetId,
                replacementId,
                dimension,
                rehearsal,
                deadlineGameTime,
                replacementChunkX,
                replacementChunkZ);
    }

    public ServantEncounter withLocation(int updatedChunkX, int updatedChunkZ) {
        if (updatedChunkX == chunkX && updatedChunkZ == chunkZ) {
            return this;
        }
        return withEntity(servantId, updatedChunkX, updatedChunkZ);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(ENCOUNTER_ID, encounterId);
        tag.putUUID(TARGET_ID, targetId);
        tag.putUUID(SERVANT_ID, servantId);
        tag.putString(DIMENSION, dimension);
        tag.putBoolean(REHEARSAL, rehearsal);
        tag.putLong(DEADLINE, deadlineGameTime);
        tag.putInt(CHUNK_X, chunkX);
        tag.putInt(CHUNK_Z, chunkZ);
        return tag;
    }

    static ServantEncounter load(CompoundTag tag) {
        if (!tag.hasUUID(ENCOUNTER_ID)
                || !tag.hasUUID(TARGET_ID)
                || !tag.hasUUID(SERVANT_ID)
                || !tag.contains(DIMENSION)) {
            throw new IllegalArgumentException("incomplete Servant encounter");
        }
        return new ServantEncounter(
                tag.getUUID(ENCOUNTER_ID),
                tag.getUUID(TARGET_ID),
                tag.getUUID(SERVANT_ID),
                tag.getString(DIMENSION),
                tag.getBoolean(REHEARSAL),
                tag.getLong(DEADLINE),
                tag.getInt(CHUNK_X),
                tag.getInt(CHUNK_Z));
    }
}
