package io.github.mizar107.zapegruntime.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import org.junit.jupiter.api.Test;

class OsScareReportTest {

    @Test
    void everyWireEnumRoundTripsAndFixedReportOrderIsStable() {
        for (OsEffect effect : OsEffect.values()) {
            assertEquals(effect, OsEffect.fromWireId(effect.wireId()));
        }
        for (OsCapabilityState state : OsCapabilityState.values()) {
            assertEquals(state, OsCapabilityState.fromWireId(state.wireId()));
        }
        for (OsPrimaryState state : OsPrimaryState.values()) {
            assertEquals(state, OsPrimaryState.fromWireId(state.wireId()));
        }
        for (OsFallbackState state : OsFallbackState.values()) {
            assertEquals(state, OsFallbackState.fromWireId(state.wireId()));
        }
        for (OsCleanupState state : OsCleanupState.values()) {
            assertEquals(state, OsCleanupState.fromWireId(state.wireId()));
        }
        for (OsEffectReason reason : OsEffectReason.values()) {
            assertEquals(reason, OsEffectReason.fromWireId(reason.wireId()));
        }

        EnumMap<OsEffect, OsEffectOutcome> outcomes = new EnumMap<>(OsEffect.class);
        for (OsEffect effect : OsEffect.values()) {
            outcomes.put(effect, OsEffectOutcome.initial(
                    effect, OsCapabilityState.READY, OsEffectReason.NONE));
        }
        assertEquals(
                "title{c=ready,p=not_requested,f=not_available:fallback_not_implemented,x=not_required} "
                        + "motion{c=ready,p=not_requested,f=not_available:fallback_not_implemented,x=not_required} "
                        + "popup{c=ready,p=not_requested,f=not_available:fallback_not_implemented,x=not_required} "
                        + "taskbar{c=ready,p=not_requested,f=not_available:fallback_not_implemented,x=not_required}",
                OsScareReport.from(outcomes).compactString());
    }

    @Test
    void independentDimensionsRejectImpossibleStateReasonPairs() {
        OsEffectOutcome ready = OsEffectOutcome.initial(
                OsEffect.EXTERNAL_POPUP,
                OsCapabilityState.READY,
                OsEffectReason.NONE);
        assertThrows(IllegalArgumentException.class, () -> ready.withPrimary(
                OsPrimaryState.APPLIED, OsEffectReason.ASSET_INVALID));
        assertThrows(IllegalArgumentException.class, () -> ready.withPrimary(
                OsPrimaryState.FAILED, OsEffectReason.NONE));
        assertThrows(IllegalArgumentException.class, () -> ready.withCleanup(
                OsCleanupState.APPLIED, OsEffectReason.CLEANUP_FAILED));
        assertThrows(IllegalArgumentException.class, () -> OsEffectOutcome.initial(
                OsEffect.EXTERNAL_POPUP,
                OsCapabilityState.DISABLED,
                OsEffectReason.HEADLESS));
        assertThrows(IllegalArgumentException.class, () -> OsEffectOutcome.initial(
                OsEffect.WINDOW_MOTION,
                OsCapabilityState.FAILED,
                OsEffectReason.CLEANUP_PENDING));
        assertThrows(IllegalArgumentException.class, () -> OsEffect.fromWireId(255));
        assertTrue(OsEffectReason.values().length < 32, "reason stays one bounded byte");
    }
}
