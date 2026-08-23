package io.github.mizar107.zapegruntime.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mizar107.zapegruntime.scene.OsCapabilityState;
import io.github.mizar107.zapegruntime.scene.OsCleanupState;
import io.github.mizar107.zapegruntime.scene.OsEffect;
import io.github.mizar107.zapegruntime.scene.OsEffectOutcome;
import io.github.mizar107.zapegruntime.scene.OsEffectReason;
import io.github.mizar107.zapegruntime.scene.OsScareReport;
import java.util.EnumMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OsScareStatusLedgerTest {

    @Test
    void oldRetainedEventSurvivesNewDispatchAndAcceptsItsTerminalCleanup() {
        OsScareStatusLedger ledger = new OsScareStatusLedger();
        UUID playerId = UUID.randomUUID();
        UUID oldEvent = UUID.randomUUID();
        UUID newEvent = UUID.randomUUID();
        ledger.onDispatch(playerId, oldEvent);
        ledger.recordStatus(playerId, oldEvent, 0, report(OsCleanupState.PENDING));

        ledger.onDispatch(playerId, newEvent);

        assertEquals(oldEvent, ledger.get(playerId).eventId());
        assertTrue(ledger.acceptsStatus(playerId, oldEvent, 1, false, -1));
        ledger.recordStatus(playerId, oldEvent, 1, report(OsCleanupState.APPLIED));
        assertEquals(OsCleanupState.APPLIED,
                ledger.get(playerId).report().externalPopup().cleanup());
    }

    @Test
    void firstValidNewStatusReplacesOldAndRejectsEveryLaterOldPacket() {
        OsScareStatusLedger ledger = new OsScareStatusLedger();
        UUID playerId = UUID.randomUUID();
        UUID oldEvent = UUID.randomUUID();
        UUID newEvent = UUID.randomUUID();
        ledger.onDispatch(playerId, oldEvent);
        ledger.recordStatus(playerId, oldEvent, 4, report(OsCleanupState.PENDING));
        ledger.onDispatch(playerId, newEvent);

        assertFalse(ledger.acceptsStatus(playerId, newEvent, 1, true, -1),
                "a new active event must begin at sequence zero");
        assertEquals(oldEvent, ledger.get(playerId).eventId(),
                "a forged nonzero first status must not evict retained cleanup");
        assertTrue(ledger.acceptsStatus(playerId, newEvent, 0, true, -1));
        ledger.recordStatus(playerId, newEvent, 0, report(OsCleanupState.PENDING));

        assertEquals(newEvent, ledger.get(playerId).eventId());
        assertFalse(ledger.acceptsStatus(playerId, oldEvent, 5, false, -1));
    }

    @Test
    void busyNewVisitationDoesNotReplaceOldRetainedEvent() {
        OsScareStatusLedger ledger = new OsScareStatusLedger();
        UUID playerId = UUID.randomUUID();
        UUID oldEvent = UUID.randomUUID();
        UUID busyEvent = UUID.randomUUID();
        ledger.onDispatch(playerId, oldEvent);
        ledger.recordStatus(playerId, oldEvent, 2, report(OsCleanupState.PENDING));
        ledger.onDispatch(playerId, busyEvent);

        ledger.onBusy(playerId, busyEvent);

        assertEquals(oldEvent, ledger.get(playerId).eventId());
        assertTrue(ledger.acceptsStatus(playerId, oldEvent, 3, false, -1));

        UUID otherPlayer = UUID.randomUUID();
        ledger.onDispatch(otherPlayer, busyEvent);
        ledger.onBusy(otherPlayer, busyEvent);
        assertNull(ledger.get(otherPlayer), "a BUSY placeholder is not a fake report");
    }

    @Test
    void serverLifecycleClearDropsEveryWorldLocalReport() {
        OsScareStatusLedger ledger = new OsScareStatusLedger();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        ledger.recordStatus(firstPlayer, UUID.randomUUID(), 3, report(OsCleanupState.APPLIED));
        ledger.onDispatch(secondPlayer, UUID.randomUUID());

        ledger.clear();

        assertEquals(0, ledger.size());
        assertNull(ledger.get(firstPlayer));
        assertNull(ledger.get(secondPlayer));
    }

    private static OsScareReport report(OsCleanupState popupCleanup) {
        EnumMap<OsEffect, OsEffectOutcome> outcomes = new EnumMap<>(OsEffect.class);
        for (OsEffect effect : OsEffect.values()) {
            outcomes.put(effect, OsEffectOutcome.initial(
                    effect, OsCapabilityState.READY, OsEffectReason.NONE));
        }
        OsEffectReason reason = popupCleanup == OsCleanupState.PENDING
                ? OsEffectReason.CLEANUP_PENDING
                : OsEffectReason.NONE;
        outcomes.put(
                OsEffect.EXTERNAL_POPUP,
                outcomes.get(OsEffect.EXTERNAL_POPUP)
                        .withCleanup(popupCleanup, reason));
        return OsScareReport.from(outcomes);
    }
}
