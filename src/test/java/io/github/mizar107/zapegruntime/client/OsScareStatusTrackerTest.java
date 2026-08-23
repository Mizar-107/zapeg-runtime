package io.github.mizar107.zapegruntime.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mizar107.zapegruntime.network.OsScareStatusC2S;
import io.github.mizar107.zapegruntime.scene.OsCapabilityState;
import io.github.mizar107.zapegruntime.scene.OsCleanupState;
import io.github.mizar107.zapegruntime.scene.OsEffect;
import io.github.mizar107.zapegruntime.scene.OsEffectOutcome;
import io.github.mizar107.zapegruntime.scene.OsEffectReason;
import io.github.mizar107.zapegruntime.scene.OsPrimaryState;
import io.github.mizar107.zapegruntime.scene.OsScareReport;
import io.github.mizar107.zapegruntime.scene.SceneAck;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OsScareStatusTrackerTest {

    private record Sent(UUID eventId, UUID targetId, int sequence, OsScareReport report) {}

    @Test
    void dimensionUnloadRetainsPendingSessionUntilTerminalReportIsSent() {
        OsScareStatusTracker tracker = new OsScareStatusTracker();
        UUID eventId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        List<Sent> sent = new ArrayList<>();
        assertTrue(tracker.open(eventId, targetId));

        tracker.retainForLevelUnload();
        assertFalse(tracker.poll(popupPending(), true, sender(sent)));
        assertTrue(tracker.hasSession());
        assertEquals(eventId, tracker.eventId());

        assertTrue(tracker.poll(popupApplied(), false, sender(sent)));
        assertFalse(tracker.hasSession());
        assertEquals(List.of(0, 1), sent.stream().map(Sent::sequence).toList());
        assertEquals(OsCleanupState.APPLIED,
                sent.get(1).report().externalPopup().cleanup());
    }

    @Test
    void trueNetworkLogoutDropsSessionWithoutAttemptingAnotherSend() {
        OsScareStatusTracker tracker = new OsScareStatusTracker();
        List<Sent> sent = new ArrayList<>();
        assertTrue(tracker.open(UUID.randomUUID(), UUID.randomUUID()));
        tracker.retainForLevelUnload();

        tracker.dropForLogout();

        assertFalse(tracker.hasSession());
        assertFalse(tracker.poll(popupApplied(), false, sender(sent)));
        assertTrue(sent.isEmpty());
    }

    @Test
    void secondVisitationIsBusyAndCannotOverwriteClosingEvent() {
        OsScareStatusTracker tracker = new OsScareStatusTracker();
        UUID oldEvent = UUID.randomUUID();
        assertTrue(tracker.open(oldEvent, UUID.randomUUID()));
        tracker.markSceneClosing();

        assertEquals(SceneAck.BUSY, tracker.gateNewVisitation());
        assertFalse(tracker.open(UUID.randomUUID(), UUID.randomUUID()));
        assertEquals(oldEvent, tracker.eventId());
    }

    @Test
    void settledClosingSessionEvictsOnlyAfterTerminalSend() {
        OsScareStatusTracker tracker = new OsScareStatusTracker();
        List<Sent> sent = new ArrayList<>();
        assertTrue(tracker.open(UUID.randomUUID(), UUID.randomUUID()));
        tracker.markSceneClosing();

        assertTrue(tracker.poll(popupApplied(), false, sender(sent)));

        assertEquals(1, sent.size());
        assertFalse(tracker.hasSession());
        assertEquals(SceneAck.RECEIVED, tracker.gateNewVisitation());
    }

    @Test
    void sequenceBudgetAlwaysReservesTheFinalWireValueForTerminalTruth() {
        OsScareStatusTracker tracker = new OsScareStatusTracker();
        List<Sent> sent = new ArrayList<>();
        assertTrue(tracker.open(UUID.randomUUID(), UUID.randomUUID()));
        OsScareReport first = titlePrimary(OsPrimaryState.REQUESTED,
                OsEffectReason.UNVERIFIED_API);
        OsScareReport second = titlePrimary(OsPrimaryState.FAILED,
                OsEffectReason.GLFW_FAILURE);
        for (int update = 0; update < 80; update++) {
            tracker.poll(update % 2 == 0 ? first : second, false, sender(sent));
        }

        assertEquals(OsScareStatusC2S.MAX_SEQUENCE, tracker.nextSequence());
        assertEquals(OsScareStatusC2S.MAX_SEQUENCE, sent.size());
        tracker.markSceneClosing();
        assertTrue(tracker.poll(popupApplied(), false, sender(sent)));

        assertEquals(OsScareStatusC2S.MAX_SEQUENCE + 1, sent.size());
        assertEquals(OsScareStatusC2S.MAX_SEQUENCE,
                sent.get(sent.size() - 1).sequence());
        assertTrue(sent.stream().allMatch(
                item -> item.sequence() <= OsScareStatusC2S.MAX_SEQUENCE));
    }

    private static OsScareStatusTracker.Sender sender(List<Sent> sent) {
        return (eventId, targetId, sequence, report) ->
                sent.add(new Sent(eventId, targetId, sequence, report));
    }

    private static OsScareReport popupPending() {
        EnumMap<OsEffect, OsEffectOutcome> outcomes = readyOutcomes();
        outcomes.put(
                OsEffect.EXTERNAL_POPUP,
                outcomes.get(OsEffect.EXTERNAL_POPUP)
                        .withPrimary(OsPrimaryState.APPLIED, OsEffectReason.NONE)
                        .withCleanup(
                                OsCleanupState.PENDING,
                                OsEffectReason.CLEANUP_PENDING));
        return OsScareReport.from(outcomes);
    }

    private static OsScareReport popupApplied() {
        EnumMap<OsEffect, OsEffectOutcome> outcomes = readyOutcomes();
        outcomes.put(
                OsEffect.EXTERNAL_POPUP,
                outcomes.get(OsEffect.EXTERNAL_POPUP)
                        .withPrimary(OsPrimaryState.APPLIED, OsEffectReason.NONE)
                        .withCleanup(OsCleanupState.APPLIED, OsEffectReason.NONE));
        return OsScareReport.from(outcomes);
    }

    private static OsScareReport titlePrimary(
            OsPrimaryState state, OsEffectReason reason) {
        EnumMap<OsEffect, OsEffectOutcome> outcomes = readyOutcomes();
        outcomes.put(
                OsEffect.WINDOW_TITLE,
                outcomes.get(OsEffect.WINDOW_TITLE).withPrimary(state, reason));
        return OsScareReport.from(outcomes);
    }

    private static EnumMap<OsEffect, OsEffectOutcome> readyOutcomes() {
        EnumMap<OsEffect, OsEffectOutcome> outcomes = new EnumMap<>(OsEffect.class);
        for (OsEffect effect : OsEffect.values()) {
            outcomes.put(effect, OsEffectOutcome.initial(
                    effect, OsCapabilityState.READY, OsEffectReason.NONE));
        }
        return outcomes;
    }
}
