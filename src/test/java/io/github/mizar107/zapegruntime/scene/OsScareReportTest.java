package io.github.mizar107.zapegruntime.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import org.junit.jupiter.api.Test;

class OsScareReportTest {

    @Test
    void everyWireEnumRoundTripsAndTheReportOrderIsStable() {
        for (OsEffect effect : OsEffect.values()) {
            assertEquals(effect, OsEffect.fromWireId(effect.wireId()));
        }
        for (OsEffectState state : OsEffectState.values()) {
            assertEquals(state, OsEffectState.fromWireId(state.wireId()));
        }
        for (OsEffectReason reason : OsEffectReason.values()) {
            assertEquals(reason, OsEffectReason.fromWireId(reason.wireId()));
        }

        EnumMap<OsEffect, OsEffectOutcome> outcomes = new EnumMap<>(OsEffect.class);
        for (OsEffect effect : OsEffect.values()) {
            outcomes.put(effect, OsEffectOutcome.ready(effect));
        }
        assertEquals(
                "title=ready motion=ready popup=ready taskbar=ready",
                OsScareReport.from(outcomes).compactString());
    }

    @Test
    void impossibleStateReasonPairsFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> new OsEffectOutcome(
                OsEffect.EXTERNAL_POPUP,
                OsEffectState.APPLIED,
                OsEffectReason.ASSET_INVALID));
        assertThrows(IllegalArgumentException.class, () -> new OsEffectOutcome(
                OsEffect.EXTERNAL_POPUP,
                OsEffectState.FAILED,
                OsEffectReason.NONE));
        assertThrows(IllegalArgumentException.class, () -> OsEffect.fromWireId(255));
        assertTrue(OsEffectReason.values().length < 32, "reason stays one bounded byte");
    }
}
