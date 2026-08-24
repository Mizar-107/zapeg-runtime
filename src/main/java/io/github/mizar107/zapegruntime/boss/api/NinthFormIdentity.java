package io.github.mizar107.zapegruntime.boss.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable authority carried across the encounter and combat boundary.
 *
 * <p>The encounter id identifies one durable attempt. The integer generation
 * rotates whenever recovery authorizes a replacement entity, so an entity from
 * an old generation can never complete a current attempt. The target is the
 * sole player eligible for story credit; nearby participants do not become
 * targets. Rehearsal is persisted on the entity and is never inferred from a
 * command source or display name.
 */
public record NinthFormIdentity(
        UUID encounterId, UUID targetId, int generation, boolean rehearsal) {

    public NinthFormIdentity {
        requireNonNil(encounterId, "encounterId");
        requireNonNil(targetId, "targetId");
        if (encounterId.equals(targetId)) {
            throw new IllegalArgumentException("encounterId and targetId must be distinct");
        }
        if (generation < 0) {
            throw new IllegalArgumentException("generation cannot be negative");
        }
    }

    /** Checks the separate, preallocated in-world entity UUID. */
    public void validateEntityId(UUID entityId) {
        requireNonNil(entityId, "entityId");
        if (entityId.equals(encounterId) || entityId.equals(targetId)) {
            throw new IllegalArgumentException("entityId must be distinct from encounter authority");
        }
    }

    private static void requireNonNil(UUID value, String name) {
        Objects.requireNonNull(value, name);
        if (value.getMostSignificantBits() == 0L && value.getLeastSignificantBits() == 0L) {
            throw new IllegalArgumentException(name + " cannot be the nil UUID");
        }
    }
}
