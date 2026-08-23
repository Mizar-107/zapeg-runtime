package io.github.mizar107.zapegruntime.server;

import io.github.mizar107.zapegruntime.scene.OsScareReport;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded retained-event policy for post-terminal visitation diagnostics. */
final class OsScareStatusLedger {

    static final int MAX_PLAYERS = 64;

    record Entry(UUID eventId, int sequence, OsScareReport report) {}

    private final Map<UUID, Entry> entries = new HashMap<>();

    /** A new dispatch must not orphan an older cleanup session. */
    void onDispatch(UUID playerId, UUID eventId) {
        if (!entries.containsKey(playerId)) {
            remember(playerId, new Entry(eventId, -1, null));
        }
    }

    /** BUSY preserves an older event; a new never-started placeholder is removed. */
    void onBusy(UUID playerId, UUID rejectedEventId) {
        Entry retained = entries.get(playerId);
        if (retained != null
                && retained.eventId.equals(rejectedEventId)
                && retained.sequence == -1
                && retained.report == null) {
            entries.remove(playerId);
        }
    }

    boolean acceptsStatus(
            UUID playerId,
            UUID eventId,
            int sequence,
            boolean activeMatch,
            int activeSequence) {
        Entry retained = entries.get(playerId);
        boolean retainedMatch = retained != null && retained.eventId.equals(eventId);
        int retainedSequence = retainedMatch ? retained.sequence : -1;
        if (activeMatch && activeSequence < 0 && sequence != 0) {
            return false;
        }
        return (activeMatch || retainedMatch)
                && sequence > retainedSequence
                && (!activeMatch || sequence > activeSequence);
    }

    /** A valid first status for a new active event atomically replaces the old event. */
    void recordStatus(
            UUID playerId, UUID eventId, int sequence, OsScareReport report) {
        remember(playerId, new Entry(eventId, sequence, report));
    }

    Entry get(UUID playerId) {
        return entries.get(playerId);
    }

    void clear() {
        entries.clear();
    }

    int size() {
        return entries.size();
    }

    private void remember(UUID playerId, Entry status) {
        if (!entries.containsKey(playerId) && entries.size() >= MAX_PLAYERS) {
            UUID oldestArbitraryKey = entries.keySet().iterator().next();
            entries.remove(oldestArbitraryKey);
        }
        entries.put(playerId, status);
    }
}
