package io.github.mizar107.zapegruntime.journal;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World-owned active journal tokens. A corrupt/future ledger fails closed and is preserved. */
public final class JournalBindingData extends SavedData {

    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_BINDINGS = 2_048;
    private static final String DATA_NAME = "zapeg_runtime_heraldor_journals";
    private static final String SCHEMA_KEY = "SchemaVersion";
    private static final String BINDINGS_KEY = "Bindings";
    private static final String PLAYER_KEY = "PlayerId";
    private static final String TOKEN_KEY = "ActiveToken";
    private static final Set<String> ROOT_FIELDS = Set.of(SCHEMA_KEY, BINDINGS_KEY);
    private static final Set<String> BINDING_FIELDS = Set.of(PLAYER_KEY, TOKEN_KEY);
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private final Map<UUID, UUID> bindings = new HashMap<>();
    private final boolean writable;
    private final String healthDetail;
    private final CompoundTag preservedRoot;

    public JournalBindingData() {
        this(true, "ok", null);
    }

    private JournalBindingData(
            boolean writable, String healthDetail, CompoundTag preservedRoot) {
        this.writable = writable;
        this.healthDetail = Objects.requireNonNull(healthDetail, "healthDetail");
        this.preservedRoot = preservedRoot;
    }

    public static JournalBindingData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                JournalBindingData::load, JournalBindingData::new, DATA_NAME);
    }

    public static JournalBindingData load(CompoundTag root) {
        Objects.requireNonNull(root, "root");
        try {
            if (!root.getAllKeys().equals(ROOT_FIELDS)
                    || !root.contains(SCHEMA_KEY, Tag.TAG_INT)
                    || root.getInt(SCHEMA_KEY) != CURRENT_SCHEMA
                    || !root.contains(BINDINGS_KEY, Tag.TAG_LIST)) {
                return unavailable("unsupported or malformed journal binding root", root);
            }
            Tag encodedTag = root.get(BINDINGS_KEY);
            if (!(encodedTag instanceof ListTag encoded)
                    || (!encoded.isEmpty() && encoded.getElementType() != Tag.TAG_COMPOUND)
                    || encoded.size() > MAX_BINDINGS) {
                return unavailable("journal binding list is malformed or oversized", root);
            }
            JournalBindingData data = new JournalBindingData();
            Set<UUID> activeTokens = new java.util.HashSet<>();
            for (int index = 0; index < encoded.size(); index++) {
                CompoundTag entry = encoded.getCompound(index);
                if (!entry.getAllKeys().equals(BINDING_FIELDS)
                        || !entry.hasUUID(PLAYER_KEY)
                        || !entry.hasUUID(TOKEN_KEY)) {
                    return unavailable("journal binding entry is malformed", root);
                }
                UUID playerId = entry.getUUID(PLAYER_KEY);
                UUID token = entry.getUUID(TOKEN_KEY);
                if (playerId.equals(NIL_UUID)
                        || token.equals(NIL_UUID)
                        || data.bindings.put(playerId, token) != null
                        || !activeTokens.add(token)) {
                    return unavailable(
                            "journal binding UUID is nil, duplicated, or shared", root);
                }
            }
            return data;
        } catch (RuntimeException malformed) {
            return unavailable("journal binding data could not be decoded", root);
        }
    }

    private static JournalBindingData unavailable(String detail, CompoundTag root) {
        return new JournalBindingData(false, detail, root.copy());
    }

    public boolean writable() {
        return writable;
    }

    public String healthDetail() {
        return healthDetail;
    }

    public Optional<UUID> activeToken(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return writable ? Optional.ofNullable(bindings.get(playerId)) : Optional.empty();
    }

    public boolean bindInitial(UUID playerId, UUID token) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(token, "token");
        if (!writable
                || playerId.equals(NIL_UUID)
                || token.equals(NIL_UUID)
                || bindings.containsKey(playerId)
                || bindings.containsValue(token)
                || bindings.size() >= MAX_BINDINGS) {
            return false;
        }
        bindings.put(playerId, token);
        setDirty();
        return true;
    }

    public boolean rotate(UUID playerId, UUID token) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(token, "token");
        if (!writable
                || playerId.equals(NIL_UUID)
                || token.equals(NIL_UUID)
                || !bindings.containsKey(playerId)
                || bindings.entrySet().stream().anyMatch(entry ->
                        !entry.getKey().equals(playerId) && entry.getValue().equals(token))) {
            return false;
        }
        bindings.put(playerId, token);
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        if (preservedRoot != null) {
            return preservedRoot.copy();
        }
        root.putInt(SCHEMA_KEY, CURRENT_SCHEMA);
        ListTag encoded = new ListTag();
        bindings.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
                .forEach(entry -> {
                    CompoundTag item = new CompoundTag();
                    item.putUUID(PLAYER_KEY, entry.getKey());
                    item.putUUID(TOKEN_KEY, entry.getValue());
                    encoded.add(item);
                });
        root.put(BINDINGS_KEY, encoded);
        return root;
    }
}
