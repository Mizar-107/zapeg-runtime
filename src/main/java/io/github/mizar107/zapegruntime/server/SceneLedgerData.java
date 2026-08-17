package io.github.mizar107.zapegruntime.server;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class SceneLedgerData extends SavedData {

    private static final String DATA_NAME = "zapeg_runtime_scene_ledger";
    private static final String CONSUMED_KEY = "Consumed";
    private static final int MAX_CONSUMED = 256;

    private final ArrayDeque<UUID> consumedInOrder = new ArrayDeque<>();
    private final Set<UUID> consumed = new HashSet<>();

    public static SceneLedgerData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                SceneLedgerData::load,
                SceneLedgerData::new,
                DATA_NAME);
    }

    public static SceneLedgerData load(CompoundTag root) {
        SceneLedgerData data = new SceneLedgerData();
        ListTag list = root.getList(CONSUMED_KEY, Tag.TAG_STRING);
        for (int index = 0; index < list.size(); index++) {
            try {
                data.addLoaded(UUID.fromString(list.getString(index)));
            } catch (IllegalArgumentException ignored) {
                // Corrupt entries are ignored; bounded valid entries still protect replay.
            }
        }
        return data;
    }

    public boolean contains(UUID eventId) {
        return consumed.contains(eventId);
    }

    public boolean consume(UUID eventId) {
        if (!consumed.add(eventId)) {
            return false;
        }
        consumedInOrder.addLast(eventId);
        evictOldest();
        setDirty();
        return true;
    }

    int size() {
        return consumed.size();
    }

    private void addLoaded(UUID eventId) {
        if (consumed.add(eventId)) {
            consumedInOrder.addLast(eventId);
            evictOldest();
        }
    }

    private void evictOldest() {
        while (consumedInOrder.size() > MAX_CONSUMED) {
            UUID removed = consumedInOrder.removeFirst();
            consumed.remove(removed);
        }
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        ListTag list = new ListTag();
        for (UUID eventId : consumedInOrder) {
            list.add(StringTag.valueOf(eventId.toString()));
        }
        root.put(CONSUMED_KEY, list);
        return root;
    }
}
