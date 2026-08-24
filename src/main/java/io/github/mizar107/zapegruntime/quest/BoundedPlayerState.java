package io.github.mizar107.zapegruntime.quest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Small in-memory UUID ledger with a hard capacity and explicit lifecycle cleanup. */
final class BoundedPlayerState<T> {

    private final int capacity;
    private final Map<UUID, T> entries = new LinkedHashMap<>();

    BoundedPlayerState(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    T get(UUID playerId) {
        return entries.get(Objects.requireNonNull(playerId, "playerId"));
    }

    boolean put(UUID playerId, T state) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(state, "state");
        if (!entries.containsKey(playerId) && entries.size() >= capacity) {
            return false;
        }
        entries.put(playerId, state);
        return true;
    }

    void remove(UUID playerId) {
        entries.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    void retainOnly(Set<UUID> playerIds) {
        Objects.requireNonNull(playerIds, "playerIds");
        entries.keySet().removeIf(playerId -> !playerIds.contains(playerId));
    }

    void clear() {
        entries.clear();
    }

    int size() {
        return entries.size();
    }
}
