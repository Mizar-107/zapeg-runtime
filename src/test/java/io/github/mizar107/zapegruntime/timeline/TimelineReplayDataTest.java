package io.github.mizar107.zapegruntime.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class TimelineReplayDataTest {

    private static final UUID TARGET = UUID.fromString(
            "60000000-0000-0000-0000-000000000006");

    @Test
    void exactStructuredReplayIsDurableAndIdempotent() {
        TimelineReplayIdentity identity = identity(1L);
        TimelineReplayData data = new TimelineReplayData();

        assertEquals(
                TimelineReplayData.ReserveStatus.RESERVED,
                data.reserve(identity));
        assertTrue(data.markApplied(identity));
        assertEquals(
                TimelineReplayData.ReserveStatus.EXACT_APPLIED,
                data.reserve(identity));

        TimelineReplayData loaded = TimelineReplayData.load(
                data.save(new CompoundTag()));
        assertEquals(
                TimelineReplayData.ReserveStatus.EXACT_APPLIED,
                loaded.reserve(identity));
        assertEquals(java.util.List.of(identity), loaded.appliedIdentities());
    }

    @Test
    void eventOriginTargetAndPayloadCollisionsNeverCertifyReplay() {
        TimelineReplayIdentity original = identity(2L);
        TimelineReplayData data = new TimelineReplayData();
        assertEquals(TimelineReplayData.ReserveStatus.RESERVED, data.reserve(original));
        assertTrue(data.markApplied(original));

        TimelineReplayIdentity differentTarget = new TimelineReplayIdentity(
                original.eventId(),
                original.sessionId(),
                new UUID(13L, 2L),
                original.timelineId(),
                original.definitionFingerprint(),
                original.actionId(),
                original.payloadHash());
        assertEquals(
                TimelineReplayData.ReserveStatus.IDENTITY_CONFLICT,
                data.reserve(differentTarget));

        TimelineReplayIdentity differentProfilePayload = new TimelineReplayIdentity(
                new UUID(14L, 2L),
                original.sessionId(),
                original.targetId(),
                original.timelineId(),
                original.definitionFingerprint(),
                original.actionId(),
                "b".repeat(64));
        assertEquals(
                TimelineReplayData.ReserveStatus.IDENTITY_CONFLICT,
                data.reserve(differentProfilePayload));
        assertEquals(1, data.size());
    }

    @Test
    void abandonedReservationIsNeverMistakenForAppliedAfterRestart() {
        TimelineReplayIdentity identity = identity(3L);
        TimelineReplayData data = new TimelineReplayData();
        data.reserve(identity);
        TimelineReplayData loaded = TimelineReplayData.load(
                data.save(new CompoundTag()));

        assertEquals(
                TimelineReplayData.ReserveStatus.EXACT_RESERVED,
                loaded.reserve(identity));
        assertTrue(loaded.appliedIdentities().isEmpty());
        assertTrue(loaded.rollbackReservation(identity));
        assertEquals(TimelineReplayData.ReserveStatus.RESERVED, loaded.reserve(identity));
    }

    @Test
    void exactReservationResumesWhenCrashPrecededLegacyConsume() {
        TimelineReplayIdentity identity = identity(31L);
        TimelineReplayData beforeCrash = new TimelineReplayData();
        assertEquals(
                TimelineReplayData.ReserveStatus.RESERVED,
                beforeCrash.reserve(identity));
        TimelineReplayData restarted = TimelineReplayData.load(
                beforeCrash.save(new CompoundTag()));

        assertEquals(
                TimelineReplayData.DispatchClaim.RESUME_RESERVED,
                restarted.claimForDispatch(identity, false));
        assertTrue(restarted.isReserved(identity));
        assertTrue(restarted.markApplied(identity));
        assertEquals(
                TimelineReplayData.DispatchClaim.ALREADY_APPLIED,
                restarted.claimForDispatch(identity, true));
    }

    @Test
    void exactReservationPromotesWhenCrashFollowedLegacyConsume() {
        TimelineReplayIdentity identity = identity(32L);
        TimelineReplayData beforeCrash = new TimelineReplayData();
        beforeCrash.reserve(identity);
        TimelineReplayData restarted = TimelineReplayData.load(
                beforeCrash.save(new CompoundTag()));

        assertEquals(
                TimelineReplayData.DispatchClaim.ALREADY_APPLIED,
                restarted.claimForDispatch(identity, true));
        assertFalse(restarted.isReserved(identity));
        assertEquals(
                TimelineReplayData.ReserveStatus.EXACT_APPLIED,
                restarted.reserve(identity));
    }

    @Test
    void freshReservationNeverAdoptsPreexistingLegacyCollision() {
        TimelineReplayIdentity identity = identity(33L);
        TimelineReplayData data = new TimelineReplayData();

        assertEquals(
                TimelineReplayData.DispatchClaim.REJECTED,
                data.claimForDispatch(identity, true));
        assertEquals(0, data.size());
        assertEquals(TimelineReplayData.ReserveStatus.RESERVED, data.reserve(identity));
    }

    @Test
    void directClaimsRemainStructuredBeyondLegacyEvictionWindow() {
        TimelineReplayData data = new TimelineReplayData();
        TimelineReplayData.ExternalSceneIdentity first = external(1L);
        assertEquals(
                TimelineReplayData.ExternalDispatchClaim.NEW_RESERVED,
                data.claimExternalForDispatch(first, false));
        assertTrue(data.markExternalApplied(first));
        for (long index = 2L; index <= 300L; index++) {
            TimelineReplayData.ExternalSceneIdentity next = external(index);
            assertEquals(
                    TimelineReplayData.ExternalReserveStatus.RESERVED,
                    data.reserveExternal(next));
            assertTrue(data.markExternalApplied(next));
        }

        TimelineReplayIdentity collidingTimeline = new TimelineReplayIdentity(
                first.eventId(),
                new UUID(72L, 1L),
                TARGET,
                ResourceLocation.fromNamespaceAndPath("zapeg_runtime", "test"),
                "a".repeat(64),
                "direct_collision",
                "c".repeat(64));
        assertEquals(
                TimelineReplayData.ReserveStatus.IDENTITY_CONFLICT,
                data.reserve(collidingTimeline));
        assertEquals(
                TimelineReplayData.ExternalDispatchClaim.ALREADY_APPLIED,
                data.claimExternalForDispatch(first, false));

        TimelineReplayData restarted = TimelineReplayData.load(
                data.save(new CompoundTag()));
        assertEquals(
                TimelineReplayData.ExternalDispatchClaim.ALREADY_APPLIED,
                restarted.claimExternalForDispatch(first, true));
        assertEquals(300, restarted.size());
    }

    @Test
    void directOriginOrPayloadCollisionIsRejectedExactly() {
        TimelineReplayData data = new TimelineReplayData();
        TimelineReplayData.ExternalSceneIdentity original = external(41L);
        assertEquals(
                TimelineReplayData.ExternalReserveStatus.RESERVED,
                data.reserveExternal(original));
        assertTrue(data.markExternalApplied(original));

        TimelineReplayData.ExternalSceneIdentity differentTarget =
                TimelineReplayData.ExternalSceneIdentity.create(
                        original.eventId(),
                        new UUID(99L, 1L),
                        "echo_01",
                        200,
                        0);
        TimelineReplayData.ExternalSceneIdentity differentPayload =
                TimelineReplayData.ExternalSceneIdentity.create(
                        original.eventId(),
                        TARGET,
                        "threshold_01",
                        200,
                        0);
        assertEquals(
                TimelineReplayData.ExternalReserveStatus.IDENTITY_CONFLICT,
                data.reserveExternal(differentTarget));
        assertEquals(
                TimelineReplayData.ExternalReserveStatus.IDENTITY_CONFLICT,
                data.reserveExternal(differentPayload));
    }

    @Test
    void boundedLedgerRefusesCapacityWithoutEvictingOldIdentity() {
        TimelineReplayData data = new TimelineReplayData();
        TimelineReplayIdentity first = identity(1L);
        for (long index = 1L; index <= TimelineReplayData.MAX_ENTRIES; index++) {
            TimelineReplayIdentity next = identity(index);
            assertEquals(TimelineReplayData.ReserveStatus.RESERVED, data.reserve(next));
            assertTrue(data.markApplied(next));
        }
        assertEquals(TimelineReplayData.MAX_ENTRIES, data.size());
        assertEquals(
                TimelineReplayData.ReserveStatus.CAPACITY_EXHAUSTED,
                data.reserve(identity(TimelineReplayData.MAX_ENTRIES + 1L)));
        assertEquals(
                TimelineReplayData.ReserveStatus.EXACT_APPLIED,
                data.reserve(first));
    }

    @Test
    void corruptReplaySchemaIsPreservedAndRejectsMutation() {
        CompoundTag corrupt = new CompoundTag();
        corrupt.putInt("SchemaVersion", TimelineReplayData.CURRENT_SCHEMA_VERSION);
        corrupt.putString("Entries", "wrong type");
        TimelineReplayData data = TimelineReplayData.load(corrupt);

        assertFalse(data.supportsCurrentSchema());
        assertEquals(TimelineReplayData.DataHealth.CORRUPT, data.dataHealth());
        assertEquals(
                TimelineReplayData.ReserveStatus.CORRUPT_DATA,
                data.reserve(identity(9L)));
        assertEquals(corrupt.toString(), data.save(new CompoundTag()).toString());
    }

    private static TimelineReplayIdentity identity(long index) {
        return new TimelineReplayIdentity(
                new UUID(11L, index),
                new UUID(12L, index),
                TARGET,
                ResourceLocation.fromNamespaceAndPath("zapeg_runtime", "test"),
                "a".repeat(64),
                "cue_" + index,
                "c".repeat(64));
    }

    private static TimelineReplayData.ExternalSceneIdentity external(long index) {
        return TimelineReplayData.ExternalSceneIdentity.create(
                new UUID(71L, index), TARGET, "echo_01", 200, 0);
    }
}
