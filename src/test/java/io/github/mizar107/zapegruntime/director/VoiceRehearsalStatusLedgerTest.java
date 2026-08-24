package io.github.mizar107.zapegruntime.director;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mizar107.zapegruntime.scene.CancelReason;
import io.github.mizar107.zapegruntime.scene.SceneAck;
import io.github.mizar107.zapegruntime.scene.SceneProfile;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VoiceRehearsalStatusLedgerTest {

    private static final UUID TARGET =
            UUID.fromString("4be542c5-2e26-4a78-8e82-061af67bf838");
    private static final UUID EVENT =
            UUID.fromString("93de9526-dbc6-456b-aac1-e123091244dd");

    @Test
    void acknowledgementsDistinguishReceiptVisibilityAndTerminalOutcome() {
        VoiceRehearsalStatusLedger ledger = new VoiceRehearsalStatusLedger(4);
        begin(ledger, TARGET, EVENT);

        ledger.acknowledge(TARGET, EVENT, SceneAck.RECEIVED);
        assertEquals(
                VoiceRehearsalStatusLedger.State.RECEIVED,
                ledger.get(TARGET).orElseThrow().state());
        assertFalse(ledger.get(TARGET).orElseThrow().visible());

        ledger.acknowledge(TARGET, EVENT, SceneAck.VISIBLE);
        assertTrue(ledger.get(TARGET).orElseThrow().visible());
        ledger.acknowledge(TARGET, EVENT, SceneAck.RECEIVED);
        assertEquals(
                VoiceRehearsalStatusLedger.State.VISIBLE,
                ledger.get(TARGET).orElseThrow().state(),
                "a late receipt must not erase observed presentation");

        ledger.acknowledge(TARGET, EVENT, SceneAck.TIMEOUT);
        VoiceRehearsalStatusLedger.Entry terminal = ledger.get(TARGET).orElseThrow();
        assertEquals(VoiceRehearsalStatusLedger.State.TIMEOUT, terminal.state());
        assertFalse(terminal.active());
        assertTrue(terminal.visible());
        assertTrue(ledger.statusFor(TARGET).contains("state=timeout"));
        assertTrue(ledger.statusFor(TARGET).contains("visible=1"));

        ledger.acknowledge(TARGET, EVENT, SceneAck.ABORTED);
        assertEquals(
                VoiceRehearsalStatusLedger.State.TIMEOUT,
                ledger.get(TARGET).orElseThrow().state(),
                "terminal truth is immutable");
    }

    @Test
    void wrongEventCannotMutateTheTargetResult() {
        VoiceRehearsalStatusLedger ledger = new VoiceRehearsalStatusLedger(2);
        begin(ledger, TARGET, EVENT);

        UUID replacement = UUID.randomUUID();
        assertFalse(ledger.reserve(TARGET, replacement, VoiceRehearsalPlan.VOICE_02));
        assertEquals(EVENT, ledger.get(TARGET).orElseThrow().eventId());
        ledger.acknowledge(TARGET, UUID.randomUUID(), SceneAck.TIMEOUT);
        assertEquals(
                VoiceRehearsalStatusLedger.State.DISPATCHED,
                ledger.get(TARGET).orElseThrow().state());
    }

    @Test
    void retryFailureIsTruthfulAndDoesNotLookActive() {
        VoiceRehearsalStatusLedger ledger = new VoiceRehearsalStatusLedger(2);
        assertTrue(ledger.reserve(TARGET, EVENT, VoiceRehearsalPlan.VOICE_01));
        ledger.failed(
                TARGET,
                EVENT,
                VoiceRehearsalStatusLedger.State.BUSY,
                "target already has an active scene");

        String status = ledger.statusFor(TARGET);
        assertTrue(status.contains("active=0"));
        assertTrue(status.contains("state=busy"));
        assertTrue(status.contains("detail=target_already_has_an_active_scene"));
        assertTrue(status.contains("profile=none"));
    }

    @Test
    void boundNeverEvictsAnActiveRehearsal() {
        VoiceRehearsalStatusLedger ledger = new VoiceRehearsalStatusLedger(2);
        UUID secondTarget = UUID.randomUUID();
        UUID secondEvent = UUID.randomUUID();
        UUID thirdTarget = UUID.randomUUID();
        UUID thirdEvent = UUID.randomUUID();
        begin(ledger, TARGET, EVENT);
        begin(ledger, secondTarget, secondEvent);

        assertFalse(ledger.reserve(
                thirdTarget, thirdEvent, VoiceRehearsalPlan.VOICE_02));
        assertEquals(2, ledger.size());
        assertTrue(ledger.get(TARGET).orElseThrow().active());
        assertTrue(ledger.get(secondTarget).orElseThrow().active());

        ledger.acknowledge(TARGET, EVENT, SceneAck.TIMEOUT);
        assertTrue(ledger.reserve(
                thirdTarget, thirdEvent, VoiceRehearsalPlan.VOICE_02));
        assertTrue(ledger.get(TARGET).isEmpty());
        assertTrue(ledger.get(secondTarget).isPresent());
        assertTrue(ledger.get(thirdTarget).isPresent());
        assertEquals(2, ledger.size());
    }

    @Test
    void lifecycleBoundariesClearInsteadOfRetainingStaleBackoffTruth() {
        for (CancelReason reason : java.util.List.of(
                CancelReason.LOGOUT,
                CancelReason.DEATH,
                CancelReason.DIMENSION_CHANGE,
                CancelReason.SERVER_STOP)) {
            VoiceRehearsalStatusLedger ledger = new VoiceRehearsalStatusLedger(2);
            begin(ledger, TARGET, EVENT);
            ledger.cancelled(TARGET, EVENT, reason);
            assertTrue(ledger.get(TARGET).isEmpty(), reason.name());
        }
    }

    @Test
    void expiryAndOperatorCancellationRemainDiagnosable() {
        VoiceRehearsalStatusLedger ledger = new VoiceRehearsalStatusLedger(2);
        begin(ledger, TARGET, EVENT);
        ledger.cancelled(TARGET, EVENT, CancelReason.EXPIRED);
        assertTrue(ledger.statusFor(TARGET).contains("state=expired"));

        UUID nextEvent = UUID.randomUUID();
        begin(ledger, TARGET, nextEvent);
        ledger.cancelled(TARGET, nextEvent, CancelReason.OPERATOR);
        assertTrue(ledger.statusFor(TARGET).contains("state=cancelled"));
        assertTrue(ledger.statusFor(TARGET).contains("detail=operator"));

        ledger.clear(TARGET);
        assertEquals(
                "voice delivery=native_target_private active=0 last=none",
                ledger.statusFor(TARGET));
    }

    private static void begin(
            VoiceRehearsalStatusLedger ledger,
            UUID targetId,
            UUID eventId) {
        assertTrue(ledger.reserve(targetId, eventId, VoiceRehearsalPlan.VOICE_01));
        assertTrue(ledger.bind(targetId, eventId, new VoiceRehearsalPlan(
                VoiceRehearsalPlan.VOICE_01,
                SceneProfile.BREACH_01,
                140,
                0,
                1)));
        ledger.dispatched(targetId, eventId);
    }
}
