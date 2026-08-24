package io.github.mizar107.zapegruntime.boss.encounter;

import io.github.mizar107.zapegruntime.boss.api.NinthFormCombatSignal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Bounded, deduplicating handoff from entity ticks to the server end-tick. */
final class NinthFormSignalInbox {

    static final int MAX_PENDING_SIGNALS = 64;

    private final int capacity;
    private final LinkedHashMap<Key, NinthFormCombatSignal> pending = new LinkedHashMap<>();

    NinthFormSignalInbox() {
        this(MAX_PENDING_SIGNALS);
    }

    NinthFormSignalInbox(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("signal capacity must be positive");
        }
        this.capacity = capacity;
    }

    synchronized boolean offer(NinthFormCombatSignal signal) {
        Objects.requireNonNull(signal, "signal");
        Key key = Key.from(signal);
        if (pending.containsKey(key)) {
            pending.put(key, signal);
            return true;
        }
        if (pending.size() >= capacity && !evictSuspensionFor(signal)) {
            return false;
        }
        pending.put(key, signal);
        return true;
    }

    synchronized List<NinthFormCombatSignal> drain() {
        List<NinthFormCombatSignal> result = new ArrayList<>(pending.values());
        pending.clear();
        return List.copyOf(result);
    }

    synchronized int size() {
        return pending.size();
    }

    private boolean evictSuspensionFor(NinthFormCombatSignal incoming) {
        if (incoming.kind() == NinthFormCombatSignal.Kind.SUSPENDED) {
            return false;
        }
        Optional<Key> expendable = pending.entrySet().stream()
                .filter(entry -> entry.getValue().kind()
                        == NinthFormCombatSignal.Kind.SUSPENDED)
                .map(java.util.Map.Entry::getKey)
                .findFirst();
        expendable.ifPresent(pending::remove);
        return expendable.isPresent();
    }

    private record Key(
            NinthFormCombatSignal.Kind kind,
            UUID encounterId,
            int generation,
            UUID entityId) {

        static Key from(NinthFormCombatSignal signal) {
            return new Key(
                    signal.kind(),
                    signal.identity().encounterId(),
                    signal.identity().generation(),
                    signal.entityId());
        }
    }
}
