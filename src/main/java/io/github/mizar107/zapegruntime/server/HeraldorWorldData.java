package io.github.mizar107.zapegruntime.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Authoritative, world-scoped Heraldor campaign state.
 *
 * <p>Player names are deliberately absent from the persistence schema. Names
 * are presentation data and can change; every campaign lookup is keyed by the
 * Mojang account UUID. Mutations which originate from a Director or encounter
 * carry a stable event UUID so replaying the same request is a no-op.
 */
public final class HeraldorWorldData extends SavedData {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    private static final String DATA_NAME = "zapeg_runtime_heraldor";
    private static final String SCHEMA_VERSION_KEY = "SchemaVersion";
    private static final String PLAYERS_KEY = "Players";
    private static final String PLAYER_ID_KEY = "PlayerId";
    private static final String VICTORIES_KEY = "Victories";
    private static final String MILESTONES_KEY = "Milestones";
    private static final String CONSUMED_EVENTS_KEY = "ConsumedEvents";
    private static final Pattern MILESTONE_ID =
            Pattern.compile("[a-z0-9][a-z0-9_.:-]{0,63}");

    private final Map<UUID, MutablePlayerState> players = new HashMap<>();
    private final int loadedSchemaVersion;
    private final CompoundTag preservedUnsupportedRoot;

    public HeraldorWorldData() {
        this(CURRENT_SCHEMA_VERSION, null);
    }

    private HeraldorWorldData(
            int loadedSchemaVersion,
            CompoundTag preservedUnsupportedRoot) {
        this.loadedSchemaVersion = loadedSchemaVersion;
        this.preservedUnsupportedRoot = preservedUnsupportedRoot;
    }

    public static HeraldorWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                HeraldorWorldData::load,
                HeraldorWorldData::new,
                DATA_NAME);
    }

    public static HeraldorWorldData load(CompoundTag root) {
        int schemaVersion = root.contains(SCHEMA_VERSION_KEY, Tag.TAG_INT)
                ? root.getInt(SCHEMA_VERSION_KEY)
                : 0;
        return switch (schemaVersion) {
            case 0 -> migrateSchemaZero(root);
            case CURRENT_SCHEMA_VERSION -> loadSchemaOne(root);
            default -> new HeraldorWorldData(schemaVersion, root.copy());
        };
    }

    private static HeraldorWorldData migrateSchemaZero(CompoundTag root) {
        HeraldorWorldData data = loadKnownSchema(root, 0);
        // computeIfAbsent will persist the migrated v1 representation on the
        // next world save even if no campaign event occurs this session.
        data.setDirty();
        return data;
    }

    private static HeraldorWorldData loadSchemaOne(CompoundTag root) {
        return loadKnownSchema(root, CURRENT_SCHEMA_VERSION);
    }

    private static HeraldorWorldData loadKnownSchema(CompoundTag root, int schemaVersion) {
        HeraldorWorldData data = new HeraldorWorldData(schemaVersion, null);
        ListTag encodedPlayers = root.getList(PLAYERS_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < encodedPlayers.size(); index++) {
            CompoundTag encoded = encodedPlayers.getCompound(index);
            if (!encoded.hasUUID(PLAYER_ID_KEY)) {
                continue;
            }
            UUID playerId = encoded.getUUID(PLAYER_ID_KEY);
            MutablePlayerState state = new MutablePlayerState();
            state.victories = Math.max(0, encoded.getInt(VICTORIES_KEY));
            readMilestones(encoded.getList(MILESTONES_KEY, Tag.TAG_STRING), state);
            readEvents(encoded.getList(CONSUMED_EVENTS_KEY, Tag.TAG_STRING), state);
            data.players.put(playerId, state);
        }
        return data;
    }

    /** Returns an immutable view without creating a saved player entry. */
    public PlayerSnapshot snapshot(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        requireSupportedSchema();
        MutablePlayerState state = players.get(playerId);
        return state == null ? PlayerSnapshot.EMPTY : state.snapshot();
    }

    /** Diagnostic-safe progress view; unknown schemas are never guessed at. */
    public Optional<PlayerSnapshot> snapshotForDiagnostics(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!schemaStatus().writable()) {
            return Optional.empty();
        }
        return Optional.of(snapshot(playerId));
    }

    public SchemaStatus schemaStatus() {
        return new SchemaStatus(
                loadedSchemaVersion,
                CURRENT_SCHEMA_VERSION,
                loadedSchemaVersion == 0,
                preservedUnsupportedRoot == null);
    }

    /**
     * Consumes an event exactly once for this player.
     *
     * @return {@code true} only for the first application
     */
    public boolean consumeEvent(UUID playerId, UUID eventId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(eventId, "eventId");
        requireSupportedSchema();
        MutablePlayerState state = stateFor(playerId);
        if (!state.consumedEvents.add(eventId)) {
            return false;
        }
        setDirty();
        return true;
    }

    /** Atomically consumes an event and increments the player's victory count. */
    public boolean recordVictory(UUID playerId, UUID eventId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(eventId, "eventId");
        requireSupportedSchema();
        MutablePlayerState state = stateFor(playerId);
        if (!state.consumedEvents.add(eventId)) {
            return false;
        }
        state.victories++;
        setDirty();
        return true;
    }

    /** Atomically consumes an event and records a campaign milestone. */
    public boolean applyMilestone(UUID playerId, UUID eventId, String milestoneId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(eventId, "eventId");
        requireSupportedSchema();
        String validatedMilestone = validateMilestoneId(milestoneId);
        MutablePlayerState state = stateFor(playerId);
        if (!state.consumedEvents.add(eventId)) {
            return false;
        }
        state.milestones.add(validatedMilestone);
        setDirty();
        return true;
    }

    /** Records an administrative or migration milestone without an event. */
    public boolean markMilestone(UUID playerId, String milestoneId) {
        Objects.requireNonNull(playerId, "playerId");
        requireSupportedSchema();
        String validatedMilestone = validateMilestoneId(milestoneId);
        if (!stateFor(playerId).milestones.add(validatedMilestone)) {
            return false;
        }
        setDirty();
        return true;
    }

    int loadedSchemaVersion() {
        return loadedSchemaVersion;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        if (preservedUnsupportedRoot != null) {
            // Never downgrade or reinterpret a save created by a newer (or
            // otherwise unsupported) schema. Returning the untouched payload
            // keeps even fields this runtime does not know about lossless.
            return preservedUnsupportedRoot.copy();
        }
        root.putInt(SCHEMA_VERSION_KEY, CURRENT_SCHEMA_VERSION);
        ListTag encodedPlayers = new ListTag();
        List<Map.Entry<UUID, MutablePlayerState>> orderedPlayers =
                new ArrayList<>(players.entrySet());
        orderedPlayers.sort(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)));
        for (Map.Entry<UUID, MutablePlayerState> entry : orderedPlayers) {
            CompoundTag encoded = new CompoundTag();
            encoded.putUUID(PLAYER_ID_KEY, entry.getKey());
            MutablePlayerState state = entry.getValue();
            encoded.putInt(VICTORIES_KEY, state.victories);
            encoded.put(MILESTONES_KEY, encodeStrings(state.milestones));
            encoded.put(CONSUMED_EVENTS_KEY, encodeUuids(state.consumedEvents));
            encodedPlayers.add(encoded);
        }
        root.put(PLAYERS_KEY, encodedPlayers);
        return root;
    }

    private MutablePlayerState stateFor(UUID playerId) {
        return players.computeIfAbsent(playerId, ignored -> new MutablePlayerState());
    }

    private void requireSupportedSchema() {
        if (preservedUnsupportedRoot != null) {
            throw new IllegalStateException(
                    "Heraldor data schema " + loadedSchemaVersion
                            + " is unsupported by runtime schema "
                            + CURRENT_SCHEMA_VERSION
                            + "; progress access and mutations are disabled");
        }
    }

    private static String validateMilestoneId(String milestoneId) {
        Objects.requireNonNull(milestoneId, "milestoneId");
        if (!MILESTONE_ID.matcher(milestoneId).matches()) {
            throw new IllegalArgumentException(
                    "milestone id must match " + MILESTONE_ID.pattern());
        }
        return milestoneId;
    }

    private static void readMilestones(ListTag encoded, MutablePlayerState state) {
        for (int index = 0; index < encoded.size(); index++) {
            String milestone = encoded.getString(index);
            if (MILESTONE_ID.matcher(milestone).matches()) {
                state.milestones.add(milestone);
            }
        }
    }

    private static void readEvents(ListTag encoded, MutablePlayerState state) {
        for (int index = 0; index < encoded.size(); index++) {
            try {
                state.consumedEvents.add(UUID.fromString(encoded.getString(index)));
            } catch (IllegalArgumentException ignored) {
                // A corrupt event entry must not make the world unloadable.
            }
        }
    }

    private static ListTag encodeStrings(Set<String> values) {
        ListTag encoded = new ListTag();
        for (String value : values) {
            encoded.add(StringTag.valueOf(value));
        }
        return encoded;
    }

    private static ListTag encodeUuids(Set<UUID> values) {
        List<UUID> ordered = new ArrayList<>(values);
        ordered.sort(Comparator.comparing(UUID::toString));
        ListTag encoded = new ListTag();
        for (UUID value : ordered) {
            encoded.add(StringTag.valueOf(value.toString()));
        }
        return encoded;
    }

    public record PlayerSnapshot(
            int victories,
            Set<String> milestones,
            int consumedEventCount) {

        private static final PlayerSnapshot EMPTY =
                new PlayerSnapshot(0, Collections.emptySet(), 0);

        public PlayerSnapshot {
            if (victories < 0 || consumedEventCount < 0) {
                throw new IllegalArgumentException("state counters cannot be negative");
            }
            milestones = Collections.unmodifiableSet(new LinkedHashSet<>(milestones));
        }
    }

    public record SchemaStatus(
            int loadedVersion,
            int currentVersion,
            boolean migratedFromLegacy,
            boolean writable) {}

    private static final class MutablePlayerState {
        private int victories;
        private final TreeSet<String> milestones = new TreeSet<>();
        private final Set<UUID> consumedEvents = new HashSet<>();

        private PlayerSnapshot snapshot() {
            return new PlayerSnapshot(victories, milestones, consumedEvents.size());
        }
    }
}
