package io.github.mizar107.zapegruntime.client;

import io.github.mizar107.zapegruntime.network.OsScareStatusC2S;
import io.github.mizar107.zapegruntime.scene.OsScareReport;
import io.github.mizar107.zapegruntime.scene.SceneAck;
import java.util.Objects;
import java.util.UUID;

/**
 * Owns the single bounded client diagnostic session. A closing session stays
 * reserved until its final physical-cleanup report is sent, so a later
 * visitation can never steal its event id.
 */
final class OsScareStatusTracker {

    @FunctionalInterface
    interface Sender {
        void send(UUID eventId, UUID targetId, int sequence, OsScareReport report);
    }

    private Session current;

    SceneAck gateNewVisitation() {
        // Physical cleanup is the normal BUSY reason. A settled session also
        // remains reserved for the brief interval before its terminal packet
        // is actually sent and deterministically evicted.
        return current == null ? SceneAck.RECEIVED : SceneAck.BUSY;
    }

    boolean open(UUID eventId, UUID targetId) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(targetId, "targetId");
        if (current != null) {
            return false;
        }
        current = new Session(eventId, targetId);
        return true;
    }

    void markSceneClosing() {
        if (current != null) {
            current.closing = true;
        }
    }

    void retainForLevelUnload() {
        markSceneClosing();
    }

    void dropForLogout() {
        current = null;
    }

    /**
     * Sends at most one changed report. Sequence {@code MAX_SEQUENCE} is
     * reserved for a terminal report, guaranteeing bounded final delivery.
     * Returns true only when a terminal report was sent/already sent and the
     * session was evicted.
     */
    boolean poll(
            OsScareReport report,
            boolean physicalCleanupPending,
            Sender sender) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(sender, "sender");
        Session session = current;
        if (session == null) {
            return false;
        }
        boolean terminal = session.closing && !physicalCleanupPending;
        session.sendIfChanged(report, terminal, sender);
        if (terminal && report.equals(session.lastReport)) {
            current = null;
            return true;
        }
        return false;
    }

    boolean hasSession() {
        return current != null;
    }

    UUID eventId() {
        return current == null ? null : current.eventId;
    }

    UUID targetId() {
        return current == null ? null : current.targetId;
    }

    int nextSequence() {
        return current == null ? -1 : current.nextSequence;
    }

    private static final class Session {
        private final UUID eventId;
        private final UUID targetId;
        private OsScareReport lastReport;
        private int nextSequence;
        private boolean closing;

        private Session(UUID eventId, UUID targetId) {
            this.eventId = eventId;
            this.targetId = targetId;
        }

        private void sendIfChanged(
                OsScareReport report,
                boolean terminal,
                Sender sender) {
            if (report.equals(lastReport)) {
                return;
            }
            if (nextSequence > OsScareStatusC2S.MAX_SEQUENCE
                    || (!terminal && nextSequence >= OsScareStatusC2S.MAX_SEQUENCE)) {
                return;
            }
            sender.send(eventId, targetId, nextSequence, report);
            lastReport = report;
            nextSequence++;
        }
    }
}
