package io.github.mizar107.zapegruntime.servant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Server-authoritative Servant encounter state with an explicit disk schema. */
public final class ServantEncounterData extends SavedData {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MAX_ACTIVE_ENCOUNTERS = 256;
    /**
     * A campaign is expected to award far fewer than this many victories.
     * The ledger never evicts. Completed victories and active non-rehearsal
     * encounters share this capacity, so every live encounter reserves its
     * future durable barrier before an entity is spawned.
     */
    public static final int MAX_LIVE_VICTORIES = 4_096;

    private static final String DATA_NAME = "zapeg_runtime_servants";
    private static final String SCHEMA_VERSION = "SchemaVersion";
    private static final String ACTIVE = "Active";
    private static final String LIVE_VICTORIES = "LiveVictories";
    private static final String ENCOUNTER_ID = "EncounterId";
    private static final String TARGET_ID = "TargetId";
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private final Map<UUID, ServantEncounter> activeByTarget = new HashMap<>();
    private final Map<UUID, UUID> liveVictoryTargetsByEvent = new HashMap<>();
    private final CompoundTag unsupportedRoot;

    public ServantEncounterData() {
        unsupportedRoot = null;
    }

    private ServantEncounterData(CompoundTag unsupportedRoot) {
        this.unsupportedRoot = unsupportedRoot.copy();
    }

    public static ServantEncounterData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                ServantEncounterData::load,
                ServantEncounterData::new,
                DATA_NAME);
    }

    public static ServantEncounterData load(CompoundTag root) {
        if (!root.contains(SCHEMA_VERSION, Tag.TAG_INT)
                || root.getInt(SCHEMA_VERSION) != CURRENT_SCHEMA_VERSION) {
            // Unknown past/future data is kept byte-for-byte and all mutation
            // is rejected. A migration can later understand it safely.
            return new ServantEncounterData(root);
        }

        ServantEncounterData data = new ServantEncounterData();
        ListTag victories = root.getList(LIVE_VICTORIES, Tag.TAG_COMPOUND);
        int victoryLimit = Math.min(victories.size(), MAX_LIVE_VICTORIES);
        for (int index = 0; index < victoryLimit; index++) {
            CompoundTag victory = victories.getCompound(index);
            if (!victory.hasUUID(ENCOUNTER_ID) || !victory.hasUUID(TARGET_ID)) {
                continue;
            }
            UUID eventId = victory.getUUID(ENCOUNTER_ID);
            UUID targetId = victory.getUUID(TARGET_ID);
            if (NIL_UUID.equals(eventId)
                    || NIL_UUID.equals(targetId)
                    || eventId.equals(targetId)) {
                continue;
            }
            data.liveVictoryTargetsByEvent.putIfAbsent(eventId, targetId);
        }

        List<ServantEncounter> candidates = new ArrayList<>();
        ListTag active = root.getList(ACTIVE, Tag.TAG_COMPOUND);
        int activeLimit = Math.min(active.size(), MAX_ACTIVE_ENCOUNTERS);
        for (int index = 0; index < activeLimit; index++) {
            try {
                candidates.add(ServantEncounter.load(active.getCompound(index)));
            } catch (IllegalArgumentException ignored) {
                // Isolate corrupt records; valid siblings remain usable.
            }
        }
        Set<UUID> seenEncounterIds = new HashSet<>();
        Set<UUID> seenServantIds = new HashSet<>();
        for (ServantEncounter encounter : candidates) {
            if (data.liveVictoryTargetsByEvent.containsKey(encounter.encounterId())
                    || data.activeByTarget.containsKey(encounter.targetId())
                    || !seenEncounterIds.add(encounter.encounterId())
                    || !seenServantIds.add(encounter.servantId())
                    || (!encounter.rehearsal()
                            && data.reservedLiveSlots() >= MAX_LIVE_VICTORIES)) {
                continue;
            }
            data.activeByTarget.put(encounter.targetId(), encounter);
        }
        return data;
    }

    public boolean supportsCurrentSchema() {
        return unsupportedRoot == null;
    }

    public BeginResult begin(ServantEncounter proposed) {
        if (!supportsCurrentSchema()) {
            return new BeginResult(BeginStatus.UNSUPPORTED_SCHEMA, null);
        }
        if (liveVictoryTargetsByEvent.containsKey(proposed.encounterId())) {
            return new BeginResult(BeginStatus.REPLAYED_LIVE_VICTORY, null);
        }

        Optional<ServantEncounter> sameEvent = findByEncounter(proposed.encounterId());
        if (sameEvent.isPresent()) {
            ServantEncounter existing = sameEvent.get();
            if (existing.targetId().equals(proposed.targetId())
                    && existing.rehearsal() == proposed.rehearsal()) {
                return new BeginResult(BeginStatus.IDEMPOTENT, existing);
            }
            return new BeginResult(BeginStatus.EVENT_ID_CONFLICT, existing);
        }

        ServantEncounter busy = activeByTarget.get(proposed.targetId());
        if (busy != null) {
            return new BeginResult(BeginStatus.TARGET_BUSY, busy);
        }
        if (activeByTarget.size() >= MAX_ACTIVE_ENCOUNTERS) {
            return new BeginResult(BeginStatus.ACTIVE_CAPACITY_EXHAUSTED, null);
        }
        if (!proposed.rehearsal()
                && reservedLiveSlots() >= MAX_LIVE_VICTORIES) {
            return new BeginResult(BeginStatus.VICTORY_CAPACITY_EXHAUSTED, null);
        }

        activeByTarget.put(proposed.targetId(), proposed);
        setDirty();
        return new BeginResult(BeginStatus.STARTED, proposed);
    }

    /** Roll back a reservation without consuming its campaign event id. */
    public boolean rollbackSpawn(UUID encounterId) {
        return removeActive(encounterId);
    }

    /**
     * Completes a committed entity death. Only a live victory creates a
     * permanent replay barrier; rehearsals are immediately retryable.
     */
    public FinishResult finishVictory(UUID encounterId, UUID servantId, UUID killerId) {
        if (!supportsCurrentSchema()) {
            return FinishResult.UNSUPPORTED_SCHEMA;
        }
        if (liveVictoryTargetsByEvent.containsKey(encounterId)) {
            return FinishResult.ALREADY_TERMINAL;
        }
        Optional<ServantEncounter> match = findByEncounter(encounterId);
        if (match.isEmpty()) {
            return FinishResult.NOT_ACTIVE;
        }
        ServantEncounter encounter = match.get();
        if (!encounter.servantId().equals(servantId)
                || !encounter.targetId().equals(killerId)) {
            return FinishResult.IDENTITY_MISMATCH;
        }

        activeByTarget.remove(encounter.targetId());
        if (encounter.rehearsal()) {
            setDirty();
            return FinishResult.REHEARSAL_COMPLETE;
        }

        liveVictoryTargetsByEvent.put(encounterId, encounter.targetId());
        setDirty();
        return FinishResult.LIVE_CREDITED;
    }

    /** Logout, operator cancellation, expiry, and failed recovery stay retryable. */
    public boolean close(UUID encounterId) {
        return removeActive(encounterId);
    }

    public RecoveryClaim claimRecovery(UUID encounterId) {
        if (!supportsCurrentSchema()) {
            return RecoveryClaim.UNSUPPORTED_SCHEMA;
        }
        Optional<ServantEncounter> match = findByEncounter(encounterId);
        if (match.isEmpty()) {
            return RecoveryClaim.NOT_ACTIVE;
        }
        ServantEncounter encounter = match.get();
        if (encounter.recoveryAttempted()) {
            return RecoveryClaim.ALREADY_ATTEMPTED;
        }
        activeByTarget.put(encounter.targetId(), encounter.claimRecovery());
        setDirty();
        return RecoveryClaim.CLAIMED;
    }

    public boolean replaceRecoveredEntity(UUID encounterId, UUID replacementId) {
        if (!supportsCurrentSchema()) {
            return false;
        }
        Optional<ServantEncounter> match = findByEncounter(encounterId);
        if (match.isEmpty() || !match.get().recoveryAttempted()) {
            return false;
        }
        ServantEncounter updated = match.get().withRecoveredEntity(replacementId);
        activeByTarget.put(updated.targetId(), updated);
        setDirty();
        return true;
    }

    public Optional<ServantEncounter> activeFor(UUID targetId) {
        return supportsCurrentSchema()
                ? Optional.ofNullable(activeByTarget.get(targetId))
                : Optional.empty();
    }

    public Optional<ServantEncounter> findByEncounter(UUID encounterId) {
        if (!supportsCurrentSchema()) {
            return Optional.empty();
        }
        return activeByTarget.values().stream()
                .filter(encounter -> encounter.encounterId().equals(encounterId))
                .findFirst();
    }

    public Collection<ServantEncounter> activeEncounters() {
        return supportsCurrentSchema()
                ? Collections.unmodifiableList(new ArrayList<>(activeByTarget.values()))
                : List.of();
    }

    public boolean isLiveVictory(UUID encounterId) {
        return supportsCurrentSchema() && liveVictoryTargetsByEvent.containsKey(encounterId);
    }

    /**
     * Deterministic durable replay/outbox barriers, sorted by encounter UUID.
     *
     * <p>Integration may replay this immutable snapshot into its own world
     * state on every startup and after a death. Consumers must apply each
     * {@code encounterId} idempotently.</p>
     */
    public List<LiveVictory> liveVictories() {
        if (!supportsCurrentSchema()) {
            return List.of();
        }
        List<LiveVictory> snapshot = new ArrayList<>(liveVictoryTargetsByEvent.size());
        liveVictoryTargetsByEvent.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new LiveVictory(entry.getKey(), entry.getValue()))
                .forEach(snapshot::add);
        return List.copyOf(snapshot);
    }

    public int victoryCount(UUID targetId) {
        if (!supportsCurrentSchema()) {
            return 0;
        }
        return Math.toIntExact(liveVictoryTargetsByEvent.values().stream()
                .filter(targetId::equals)
                .count());
    }

    int terminalCount() {
        return liveVictoryTargetsByEvent.size();
    }

    int reservedLiveSlots() {
        if (!supportsCurrentSchema()) {
            return 0;
        }
        long activeLive = activeByTarget.values().stream()
                .filter(encounter -> !encounter.rehearsal())
                .count();
        return Math.toIntExact(liveVictoryTargetsByEvent.size() + activeLive);
    }

    private boolean removeActive(UUID encounterId) {
        if (!supportsCurrentSchema()) {
            return false;
        }
        Optional<ServantEncounter> existing = findByEncounter(encounterId);
        if (existing.isEmpty()) {
            return false;
        }
        activeByTarget.remove(existing.get().targetId());
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        if (!supportsCurrentSchema()) {
            return unsupportedRoot.copy();
        }

        root.putInt(SCHEMA_VERSION, CURRENT_SCHEMA_VERSION);
        ListTag active = new ListTag();
        activeByTarget.values().stream()
                .sorted((left, right) -> left.targetId().toString()
                        .compareTo(right.targetId().toString()))
                .forEach(encounter -> active.add(encounter.save()));
        root.put(ACTIVE, active);

        ListTag victories = new ListTag();
        liveVictoryTargetsByEvent.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    CompoundTag victory = new CompoundTag();
                    victory.putUUID(ENCOUNTER_ID, entry.getKey());
                    victory.putUUID(TARGET_ID, entry.getValue());
                    victories.add(victory);
                });
        root.put(LIVE_VICTORIES, victories);
        return root;
    }

    public enum BeginStatus {
        STARTED,
        IDEMPOTENT,
        TARGET_BUSY,
        REPLAYED_LIVE_VICTORY,
        EVENT_ID_CONFLICT,
        ACTIVE_CAPACITY_EXHAUSTED,
        VICTORY_CAPACITY_EXHAUSTED,
        UNSUPPORTED_SCHEMA
    }

    public record BeginResult(BeginStatus status, ServantEncounter encounter) {}

    public record LiveVictory(UUID encounterId, UUID targetId) {}

    public enum FinishResult {
        LIVE_CREDITED,
        REHEARSAL_COMPLETE,
        ALREADY_TERMINAL,
        NOT_ACTIVE,
        IDENTITY_MISMATCH,
        UNSUPPORTED_SCHEMA
    }

    public enum RecoveryClaim {
        CLAIMED,
        ALREADY_ATTEMPTED,
        NOT_ACTIVE,
        UNSUPPORTED_SCHEMA
    }
}
