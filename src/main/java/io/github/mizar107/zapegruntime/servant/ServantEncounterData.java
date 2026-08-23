package io.github.mizar107.zapegruntime.servant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Server-authoritative Servant encounter state. */
public final class ServantEncounterData extends SavedData {

    private static final String DATA_NAME = "zapeg_runtime_servants";
    private static final String ACTIVE = "Active";
    private static final String TERMINAL = "Terminal";
    private static final String VICTORIES = "Victories";
    private static final String PLAYER_ID = "PlayerId";
    private static final String COUNT = "Count";

    private final Map<UUID, ServantEncounter> activeByTarget = new HashMap<>();
    private final Set<UUID> terminalEncounters = new HashSet<>();
    private final Map<UUID, Integer> liveVictories = new HashMap<>();

    public static ServantEncounterData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                ServantEncounterData::load,
                ServantEncounterData::new,
                DATA_NAME);
    }

    public static ServantEncounterData load(CompoundTag root) {
        ServantEncounterData data = new ServantEncounterData();

        ListTag active = root.getList(ACTIVE, Tag.TAG_COMPOUND);
        for (int index = 0; index < active.size(); index++) {
            try {
                ServantEncounter encounter = ServantEncounter.load(active.getCompound(index));
                // A corrupt save containing two encounters for one player is
                // resolved deterministically: the lexically smaller event id wins.
                data.activeByTarget.merge(
                        encounter.targetId(),
                        encounter,
                        (left, right) -> left.encounterId().toString()
                                        .compareTo(right.encounterId().toString()) <= 0
                                ? left
                                : right);
            } catch (IllegalArgumentException ignored) {
                // One damaged record must not prevent the rest of the world loading.
            }
        }

        ListTag terminal = root.getList(TERMINAL, Tag.TAG_STRING);
        for (int index = 0; index < terminal.size(); index++) {
            try {
                data.terminalEncounters.add(UUID.fromString(terminal.getString(index)));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed legacy entries.
            }
        }
        data.activeByTarget.entrySet().removeIf(
                entry -> data.terminalEncounters.contains(entry.getValue().encounterId()));

        ListTag victories = root.getList(VICTORIES, Tag.TAG_COMPOUND);
        for (int index = 0; index < victories.size(); index++) {
            CompoundTag victory = victories.getCompound(index);
            if (victory.hasUUID(PLAYER_ID)) {
                int count = Math.max(0, victory.getInt(COUNT));
                if (count > 0) {
                    data.liveVictories.put(victory.getUUID(PLAYER_ID), count);
                }
            }
        }
        return data;
    }

    public BeginResult begin(ServantEncounter proposed) {
        if (terminalEncounters.contains(proposed.encounterId())) {
            return new BeginResult(BeginStatus.REPLAYED_TERMINAL, null);
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

        activeByTarget.put(proposed.targetId(), proposed);
        setDirty();
        return new BeginResult(BeginStatus.STARTED, proposed);
    }

    /** Roll back a reservation only when spawning failed before an entity entered the world. */
    public boolean rollbackSpawn(UUID encounterId) {
        Optional<ServantEncounter> existing = findByEncounter(encounterId);
        if (existing.isEmpty()) {
            return false;
        }
        activeByTarget.remove(existing.get().targetId());
        setDirty();
        return true;
    }

    public FinishResult finishVictory(UUID encounterId, UUID servantId, UUID killerId) {
        if (terminalEncounters.contains(encounterId)) {
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
        terminalEncounters.add(encounterId);
        if (encounter.rehearsal()) {
            setDirty();
            return FinishResult.REHEARSAL_COMPLETE;
        }

        liveVictories.merge(encounter.targetId(), 1, Integer::sum);
        setDirty();
        return FinishResult.LIVE_CREDITED;
    }

    public boolean close(UUID encounterId) {
        if (terminalEncounters.contains(encounterId)) {
            return false;
        }
        Optional<ServantEncounter> match = findByEncounter(encounterId);
        if (match.isEmpty()) {
            return false;
        }
        activeByTarget.remove(match.get().targetId());
        terminalEncounters.add(encounterId);
        setDirty();
        return true;
    }

    public boolean replaceEntity(
            UUID encounterId,
            UUID replacementId,
            int chunkX,
            int chunkZ) {
        Optional<ServantEncounter> match = findByEncounter(encounterId);
        if (match.isEmpty()) {
            return false;
        }
        ServantEncounter updated = match.get().withEntity(replacementId, chunkX, chunkZ);
        activeByTarget.put(updated.targetId(), updated);
        setDirty();
        return true;
    }

    public boolean updateLocation(UUID encounterId, int chunkX, int chunkZ) {
        Optional<ServantEncounter> match = findByEncounter(encounterId);
        if (match.isEmpty()) {
            return false;
        }
        ServantEncounter updated = match.get().withLocation(chunkX, chunkZ);
        if (updated == match.get()) {
            return false;
        }
        activeByTarget.put(updated.targetId(), updated);
        setDirty();
        return true;
    }

    public Optional<ServantEncounter> activeFor(UUID targetId) {
        return Optional.ofNullable(activeByTarget.get(targetId));
    }

    public Optional<ServantEncounter> findByEncounter(UUID encounterId) {
        return activeByTarget.values().stream()
                .filter(encounter -> encounter.encounterId().equals(encounterId))
                .findFirst();
    }

    public Collection<ServantEncounter> activeEncounters() {
        return Collections.unmodifiableList(new ArrayList<>(activeByTarget.values()));
    }

    public boolean isTerminal(UUID encounterId) {
        return terminalEncounters.contains(encounterId);
    }

    public int victoryCount(UUID targetId) {
        return liveVictories.getOrDefault(targetId, 0);
    }

    int terminalCount() {
        return terminalEncounters.size();
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        ListTag active = new ListTag();
        activeByTarget.values().stream()
                .sorted((left, right) -> left.targetId().toString()
                        .compareTo(right.targetId().toString()))
                .forEach(encounter -> active.add(encounter.save()));
        root.put(ACTIVE, active);

        ListTag terminal = new ListTag();
        terminalEncounters.stream()
                .map(UUID::toString)
                .sorted()
                .map(StringTag::valueOf)
                .forEach(terminal::add);
        root.put(TERMINAL, terminal);

        ListTag victories = new ListTag();
        liveVictories.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    CompoundTag victory = new CompoundTag();
                    victory.putUUID(PLAYER_ID, entry.getKey());
                    victory.put(COUNT, IntTag.valueOf(entry.getValue()));
                    victories.add(victory);
                });
        root.put(VICTORIES, victories);
        return root;
    }

    public enum BeginStatus {
        STARTED,
        IDEMPOTENT,
        TARGET_BUSY,
        REPLAYED_TERMINAL,
        EVENT_ID_CONFLICT
    }

    public record BeginResult(BeginStatus status, ServantEncounter encounter) {}

    public enum FinishResult {
        LIVE_CREDITED,
        REHEARSAL_COMPLETE,
        ALREADY_TERMINAL,
        NOT_ACTIVE,
        IDENTITY_MISMATCH
    }
}
