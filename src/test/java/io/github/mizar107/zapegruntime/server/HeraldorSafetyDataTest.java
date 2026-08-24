package io.github.mizar107.zapegruntime.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class HeraldorSafetyDataTest {

    @Test
    void freshWorldIsWritableAndQuarantinedWithNonNilAuthorityIds() {
        HeraldorSafetyData data = new HeraldorSafetyData();

        assertEquals(HeraldorSafetyMode.QUARANTINED, data.configuredMode());
        assertEquals(0L, data.generation());
        assertTrue(data.schemaStatus().writable());
        assertNonNil(data.nonce());
        assertNonNil(data.incidentId());
    }

    @Test
    void nonceIsOneUseAndOnlyExactCurrentStateRetryIsAccepted() {
        HeraldorSafetyData data = new HeraldorSafetyData();
        UUID nonce = data.nonce();

        assertEquals(
                HeraldorSafetyData.TransitionStatus.APPLIED,
                data.transition(HeraldorSafetyMode.MANUAL, nonce).status());
        assertNotEquals(nonce, data.nonce());
        assertEquals(
                HeraldorSafetyData.TransitionStatus.DUPLICATE,
                data.transition(HeraldorSafetyMode.MANUAL, nonce).status());
        assertEquals(
                HeraldorSafetyData.TransitionStatus.STALE_NONCE,
                data.transition(HeraldorSafetyMode.LIVE, nonce).status());
    }

    @Test
    void emergencyStopInvalidatesReceiptAndIsIdempotentWhileQuarantined() {
        HeraldorSafetyData data = new HeraldorSafetyData();
        UUID armNonce = data.nonce();
        data.transition(HeraldorSafetyMode.LIVE, armNonce);

        assertEquals(
                HeraldorSafetyData.TransitionStatus.APPLIED,
                data.emergencyQuarantine().status());
        long generation = data.generation();
        UUID incident = data.incidentId();
        UUID nextNonce = data.nonce();
        assertEquals(
                HeraldorSafetyData.TransitionStatus.STALE_NONCE,
                data.transition(HeraldorSafetyMode.LIVE, armNonce).status());

        assertEquals(
                HeraldorSafetyData.TransitionStatus.DUPLICATE,
                data.emergencyQuarantine().status());
        assertEquals(generation, data.generation());
        assertEquals(incident, data.incidentId());
        assertEquals(nextNonce, data.nonce());
    }

    @Test
    void currentSchemaRoundTripsAllAuthorizationState() {
        HeraldorSafetyData data = new HeraldorSafetyData();
        data.transition(HeraldorSafetyMode.MANUAL, data.nonce());

        HeraldorSafetyData loaded = HeraldorSafetyData.load(data.save(new CompoundTag()));

        assertEquals(data.configuredMode(), loaded.configuredMode());
        assertEquals(data.generation(), loaded.generation());
        assertEquals(data.nonce(), loaded.nonce());
        assertEquals(data.incidentId(), loaded.incidentId());
    }

    @Test
    void lowerStartupCeilingDestroysHiddenAutoAuthorityBeforeCeilingCanRise() {
        HeraldorSafetyData data = new HeraldorSafetyData();
        data.transition(HeraldorSafetyMode.AUTO, data.nonce());
        long autoGeneration = data.generation();
        UUID autoNonce = data.nonce();
        UUID autoIncident = data.incidentId();

        assertEquals(
                HeraldorSafetyData.TransitionStatus.APPLIED,
                data.reconcileCeiling(HeraldorSafetyMode.MANUAL).status());
        assertEquals(HeraldorSafetyMode.QUARANTINED, data.configuredMode());
        assertEquals(autoGeneration + 1L, data.generation());
        assertNotEquals(autoNonce, data.nonce());
        assertNotEquals(autoIncident, data.incidentId());
        assertEquals(
                HeraldorSafetyMode.QUARANTINED,
                data.effectiveMode(HeraldorSafetyMode.AUTO));
    }

    @Test
    void generationExhaustionCanNeverDisableTheEmergencyBrake() {
        CompoundTag root = new CompoundTag();
        root.putInt("Schema", HeraldorSafetyData.CURRENT_SCHEMA_VERSION);
        root.putString("Mode", "auto");
        root.putLong("Generation", Long.MAX_VALUE);
        root.putUUID("Nonce", UUID.randomUUID());
        root.putUUID("Incident", UUID.randomUUID());
        HeraldorSafetyData data = HeraldorSafetyData.load(root);

        assertEquals(
                HeraldorSafetyData.TransitionStatus.APPLIED,
                data.emergencyQuarantine().status());
        assertEquals(HeraldorSafetyMode.QUARANTINED, data.configuredMode());
        assertEquals(Long.MAX_VALUE, data.generation());
    }

    @Test
    void futureAndCorruptSchemasArePreservedAndFailClosed() {
        CompoundTag future = new CompoundTag();
        future.putInt("Schema", 99);
        future.putString("FutureAuthority", "preserve-me");
        HeraldorSafetyData futureData = HeraldorSafetyData.load(future);
        assertFalse(futureData.schemaStatus().writable());
        assertEquals(HeraldorSafetyMode.QUARANTINED, futureData.configuredMode());
        assertEquals(future, futureData.save(new CompoundTag()));

        CompoundTag corrupt = new CompoundTag();
        corrupt.putInt("Schema", HeraldorSafetyData.CURRENT_SCHEMA_VERSION);
        corrupt.putString("Mode", "auto");
        HeraldorSafetyData corruptData = HeraldorSafetyData.load(corrupt);
        assertFalse(corruptData.schemaStatus().writable());
        assertEquals(HeraldorSafetyMode.QUARANTINED, corruptData.configuredMode());
        assertEquals(corrupt, corruptData.save(new CompoundTag()));
    }

    private static void assertNonNil(UUID value) {
        assertFalse(value.getMostSignificantBits() == 0L && value.getLeastSignificantBits() == 0L);
    }
}
