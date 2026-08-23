package io.github.mizar107.zapegruntime.timeline;

import io.github.mizar107.zapegruntime.scene.SceneDescriptor;
import io.github.mizar107.zapegruntime.scene.SceneProfile;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Non-evicting structured authority for timeline action replay identity. */
public final class TimelineReplayData extends SavedData {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MAX_ENTRIES = 8_192;

    private static final String DATA_NAME = "zapeg_runtime_timeline_replay";
    private static final String SCHEMA = "SchemaVersion";
    private static final String ENTRIES = "Entries";
    private static final String EXTERNAL = "External";
    private static final String EVENT_ID = "EventId";
    private static final String SESSION_ID = "SessionId";
    private static final String TARGET_ID = "TargetId";
    private static final String TIMELINE_ID = "TimelineId";
    private static final String FINGERPRINT = "Fingerprint";
    private static final String ACTION_ID = "ActionId";
    private static final String PAYLOAD_HASH = "PayloadHash";
    private static final String STATE = "State";
    private static final String PROFILE = "Profile";
    private static final String TTL = "Ttl";
    private static final String STAGE = "Stage";
    private static final Set<String> ROOT_FIELDS = Set.of(SCHEMA, ENTRIES, EXTERNAL);
    private static final Set<String> ENTRY_FIELDS = Set.of(
            EVENT_ID,
            SESSION_ID,
            TARGET_ID,
            TIMELINE_ID,
            FINGERPRINT,
            ACTION_ID,
            PAYLOAD_HASH,
            STATE);
    private static final Set<String> EXTERNAL_FIELDS = Set.of(
            EVENT_ID,
            TARGET_ID,
            PROFILE,
            TTL,
            STAGE,
            PAYLOAD_HASH,
            STATE);

    private final Map<UUID, Entry> byEvent = new HashMap<>();
    private final Map<TimelineReplayIdentity.Origin, Entry> byOrigin = new HashMap<>();
    private final Map<UUID, ExternalEntry> externalByEvent = new HashMap<>();
    private final DataHealth health;
    private final CompoundTag preservedRoot;

    public TimelineReplayData() {
        health = DataHealth.HEALTHY;
        preservedRoot = null;
    }

    private TimelineReplayData(DataHealth health, CompoundTag preservedRoot) {
        this.health = health;
        this.preservedRoot = preservedRoot.copy();
    }

    public static TimelineReplayData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                TimelineReplayData::load,
                TimelineReplayData::new,
                DATA_NAME);
    }

    public static TimelineReplayData load(CompoundTag root) {
        Objects.requireNonNull(root, "root");
        Tag schema = root.get(SCHEMA);
        if (schema == null || schema.getId() != Tag.TAG_INT) {
            return new TimelineReplayData(
                    schema == null ? DataHealth.UNSUPPORTED : DataHealth.CORRUPT,
                    root);
        }
        if (root.getInt(SCHEMA) != CURRENT_SCHEMA_VERSION) {
            return new TimelineReplayData(DataHealth.UNSUPPORTED, root);
        }
        try {
            if (!root.getAllKeys().equals(ROOT_FIELDS)) {
                throw new IllegalArgumentException("replay root fields");
            }
            Tag rawEntries = root.get(ENTRIES);
            Tag rawExternal = root.get(EXTERNAL);
            if (!(rawEntries instanceof ListTag entries)
                    || (!entries.isEmpty()
                            && entries.getElementType() != Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("replay entries structure");
            }
            if (!(rawExternal instanceof ListTag external)
                    || (!external.isEmpty()
                            && external.getElementType() != Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("external replay entries structure");
            }
            if ((long) entries.size() + external.size() > MAX_ENTRIES) {
                throw new IllegalArgumentException("replay capacity exceeded");
            }
            TimelineReplayData data = new TimelineReplayData();
            for (int index = 0; index < entries.size(); index++) {
                CompoundTag encoded = entries.getCompound(index);
                if (!encoded.getAllKeys().equals(ENTRY_FIELDS)) {
                    throw new IllegalArgumentException("replay entry fields");
                }
                requireUuid(encoded, EVENT_ID);
                requireUuid(encoded, SESSION_ID);
                requireUuid(encoded, TARGET_ID);
                requireType(encoded, TIMELINE_ID, Tag.TAG_STRING);
                requireType(encoded, FINGERPRINT, Tag.TAG_STRING);
                requireType(encoded, ACTION_ID, Tag.TAG_STRING);
                requireType(encoded, PAYLOAD_HASH, Tag.TAG_STRING);
                requireType(encoded, STATE, Tag.TAG_STRING);
                TimelineReplayIdentity identity = new TimelineReplayIdentity(
                        encoded.getUUID(EVENT_ID),
                        encoded.getUUID(SESSION_ID),
                        encoded.getUUID(TARGET_ID),
                        parseResource(encoded.getString(TIMELINE_ID)),
                        encoded.getString(FINGERPRINT),
                        encoded.getString(ACTION_ID),
                        encoded.getString(PAYLOAD_HASH));
                Entry entry = new Entry(
                        identity,
                        Enum.valueOf(EntryState.class, encoded.getString(STATE)));
                if (data.byEvent.putIfAbsent(identity.eventId(), entry) != null
                        || data.byOrigin.putIfAbsent(identity.origin(), entry) != null) {
                    throw new IllegalArgumentException("duplicate replay identity");
                }
            }
            for (int index = 0; index < external.size(); index++) {
                CompoundTag encoded = external.getCompound(index);
                if (!encoded.getAllKeys().equals(EXTERNAL_FIELDS)) {
                    throw new IllegalArgumentException("external replay entry fields");
                }
                requireUuid(encoded, EVENT_ID);
                requireUuid(encoded, TARGET_ID);
                requireType(encoded, PROFILE, Tag.TAG_STRING);
                requireType(encoded, TTL, Tag.TAG_INT);
                requireType(encoded, STAGE, Tag.TAG_INT);
                requireType(encoded, PAYLOAD_HASH, Tag.TAG_STRING);
                requireType(encoded, STATE, Tag.TAG_STRING);
                ExternalSceneIdentity identity = new ExternalSceneIdentity(
                        encoded.getUUID(EVENT_ID),
                        encoded.getUUID(TARGET_ID),
                        encoded.getString(PROFILE),
                        encoded.getInt(TTL),
                        encoded.getInt(STAGE),
                        encoded.getString(PAYLOAD_HASH));
                ExternalEntry entry = new ExternalEntry(
                        identity,
                        Enum.valueOf(EntryState.class, encoded.getString(STATE)));
                if (data.byEvent.containsKey(identity.eventId())
                        || data.externalByEvent.putIfAbsent(identity.eventId(), entry) != null) {
                    throw new IllegalArgumentException("cross-origin replay collision");
                }
            }
            return data;
        } catch (IllegalArgumentException invalid) {
            return new TimelineReplayData(DataHealth.CORRUPT, root);
        }
    }

    public boolean supportsCurrentSchema() {
        return health == DataHealth.HEALTHY;
    }

    public DataHealth dataHealth() {
        return health;
    }

    public ReserveStatus reserve(TimelineReplayIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        if (!supportsCurrentSchema()) {
            return health == DataHealth.CORRUPT
                    ? ReserveStatus.CORRUPT_DATA
                    : ReserveStatus.UNSUPPORTED_SCHEMA;
        }
        Entry eventMatch = byEvent.get(identity.eventId());
        Entry originMatch = byOrigin.get(identity.origin());
        if (externalByEvent.containsKey(identity.eventId())) {
            return ReserveStatus.IDENTITY_CONFLICT;
        }
        if (eventMatch != null || originMatch != null) {
            if (eventMatch != null
                    && eventMatch == originMatch
                    && eventMatch.identity().equals(identity)) {
                return eventMatch.state() == EntryState.APPLIED
                        ? ReserveStatus.EXACT_APPLIED
                        : ReserveStatus.EXACT_RESERVED;
            }
            return ReserveStatus.IDENTITY_CONFLICT;
        }
        if (totalSize() >= MAX_ENTRIES) {
            return ReserveStatus.CAPACITY_EXHAUSTED;
        }
        Entry reserved = new Entry(identity, EntryState.RESERVED);
        byEvent.put(identity.eventId(), reserved);
        byOrigin.put(identity.origin(), reserved);
        setDirty();
        return ReserveStatus.RESERVED;
    }

    /**
     * Atomically interprets a structured reservation together with the legacy
     * UUID ledger. An exact persisted reservation is safe to resume while its
     * event is absent. If the UUID was consumed before a crash could promote
     * that exact reservation, the structured claim is the only possible
     * origin and is durably promoted instead of being dispatched twice.
     */
    public DispatchClaim claimForDispatch(
            TimelineReplayIdentity identity, boolean legacyConsumed) {
        ReserveStatus status = reserve(identity);
        if (status == ReserveStatus.EXACT_APPLIED) {
            return DispatchClaim.ALREADY_APPLIED;
        }
        if (status == ReserveStatus.EXACT_RESERVED) {
            if (!legacyConsumed) {
                return DispatchClaim.RESUME_RESERVED;
            }
            return markApplied(identity)
                    ? DispatchClaim.ALREADY_APPLIED
                    : DispatchClaim.REJECTED;
        }
        if (status != ReserveStatus.RESERVED) {
            return DispatchClaim.REJECTED;
        }
        if (!legacyConsumed) {
            return DispatchClaim.NEW_RESERVED;
        }
        rollbackReservation(identity);
        return DispatchClaim.REJECTED;
    }

    public boolean markApplied(TimelineReplayIdentity identity) {
        if (!supportsCurrentSchema()) {
            return false;
        }
        Entry current = byEvent.get(identity.eventId());
        if (current == null || !current.identity().equals(identity)) {
            return false;
        }
        if (current.state() == EntryState.APPLIED) {
            return true;
        }
        Entry applied = new Entry(identity, EntryState.APPLIED);
        byEvent.put(identity.eventId(), applied);
        byOrigin.put(identity.origin(), applied);
        setDirty();
        return true;
    }

    public boolean rollbackReservation(TimelineReplayIdentity identity) {
        if (!supportsCurrentSchema()) {
            return false;
        }
        Entry current = byEvent.get(identity.eventId());
        if (current == null
                || current.state() != EntryState.RESERVED
                || !current.identity().equals(identity)) {
            return false;
        }
        byEvent.remove(identity.eventId());
        byOrigin.remove(identity.origin());
        setDirty();
        return true;
    }

    public boolean isReserved(TimelineReplayIdentity identity) {
        if (!supportsCurrentSchema()) {
            return false;
        }
        Entry current = byEvent.get(identity.eventId());
        return current != null
                && current.state() == EntryState.RESERVED
                && current.identity().equals(identity);
    }

    public boolean containsEvent(UUID eventId) {
        return supportsCurrentSchema()
                && (byEvent.containsKey(eventId) || externalByEvent.containsKey(eventId));
    }

    public int size() {
        return supportsCurrentSchema() ? totalSize() : 0;
    }

    public ExternalReserveStatus reserveExternal(ExternalSceneIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        if (!supportsCurrentSchema()) {
            return health == DataHealth.CORRUPT
                    ? ExternalReserveStatus.CORRUPT_DATA
                    : ExternalReserveStatus.UNSUPPORTED_SCHEMA;
        }
        if (byEvent.containsKey(identity.eventId())) {
            return ExternalReserveStatus.IDENTITY_CONFLICT;
        }
        ExternalEntry existing = externalByEvent.get(identity.eventId());
        if (existing != null) {
            if (!existing.identity().equals(identity)) {
                return ExternalReserveStatus.IDENTITY_CONFLICT;
            }
            return existing.state() == EntryState.APPLIED
                    ? ExternalReserveStatus.EXACT_APPLIED
                    : ExternalReserveStatus.EXACT_RESERVED;
        }
        if (totalSize() >= MAX_ENTRIES) {
            return ExternalReserveStatus.CAPACITY_EXHAUSTED;
        }
        externalByEvent.put(
                identity.eventId(), new ExternalEntry(identity, EntryState.RESERVED));
        setDirty();
        return ExternalReserveStatus.RESERVED;
    }

    /** Direct-scene counterpart to {@link #claimForDispatch}. */
    public ExternalDispatchClaim claimExternalForDispatch(
            ExternalSceneIdentity identity, boolean legacyConsumed) {
        ExternalReserveStatus status = reserveExternal(identity);
        if (status == ExternalReserveStatus.EXACT_APPLIED) {
            return ExternalDispatchClaim.ALREADY_APPLIED;
        }
        if (status == ExternalReserveStatus.EXACT_RESERVED) {
            if (!legacyConsumed) {
                return ExternalDispatchClaim.RESUME_RESERVED;
            }
            return markExternalApplied(identity)
                    ? ExternalDispatchClaim.ALREADY_APPLIED
                    : ExternalDispatchClaim.REJECTED;
        }
        if (status != ExternalReserveStatus.RESERVED) {
            return ExternalDispatchClaim.REJECTED;
        }
        if (!legacyConsumed) {
            return ExternalDispatchClaim.NEW_RESERVED;
        }
        rollbackExternalReservation(identity);
        return ExternalDispatchClaim.REJECTED;
    }

    public boolean markExternalApplied(ExternalSceneIdentity identity) {
        if (!supportsCurrentSchema()) {
            return false;
        }
        ExternalEntry existing = externalByEvent.get(identity.eventId());
        if (existing == null || !existing.identity().equals(identity)) {
            return false;
        }
        externalByEvent.put(
                identity.eventId(), new ExternalEntry(identity, EntryState.APPLIED));
        setDirty();
        return true;
    }

    public boolean rollbackExternalReservation(ExternalSceneIdentity identity) {
        if (!supportsCurrentSchema()) {
            return false;
        }
        ExternalEntry existing = externalByEvent.get(identity.eventId());
        if (existing == null
                || existing.state() != EntryState.RESERVED
                || !existing.identity().equals(identity)) {
            return false;
        }
        externalByEvent.remove(identity.eventId());
        setDirty();
        return true;
    }

    public List<TimelineReplayIdentity> appliedIdentities() {
        if (!supportsCurrentSchema()) {
            return List.of();
        }
        return byEvent.values().stream()
                .filter(entry -> entry.state() == EntryState.APPLIED)
                .map(Entry::identity)
                .sorted(Comparator.comparing(identity -> identity.eventId().toString()))
                .toList();
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        if (!supportsCurrentSchema()) {
            return preservedRoot.copy();
        }
        root.putInt(SCHEMA, CURRENT_SCHEMA_VERSION);
        ListTag entries = new ListTag();
        byEvent.values().stream()
                .sorted(Comparator.comparing(entry -> entry.identity().eventId().toString()))
                .forEach(entry -> entries.add(write(entry)));
        root.put(ENTRIES, entries);
        ListTag external = new ListTag();
        externalByEvent.values().stream()
                .sorted(Comparator.comparing(
                        entry -> entry.identity().eventId().toString()))
                .forEach(entry -> external.add(writeExternal(entry)));
        root.put(EXTERNAL, external);
        return root;
    }

    private int totalSize() {
        return byEvent.size() + externalByEvent.size();
    }

    private static CompoundTag write(Entry entry) {
        TimelineReplayIdentity identity = entry.identity();
        CompoundTag encoded = new CompoundTag();
        encoded.putUUID(EVENT_ID, identity.eventId());
        encoded.putUUID(SESSION_ID, identity.sessionId());
        encoded.putUUID(TARGET_ID, identity.targetId());
        encoded.putString(TIMELINE_ID, identity.timelineId().toString());
        encoded.putString(FINGERPRINT, identity.definitionFingerprint());
        encoded.putString(ACTION_ID, identity.actionId());
        encoded.putString(PAYLOAD_HASH, identity.payloadHash());
        encoded.putString(STATE, entry.state().name());
        return encoded;
    }

    private static CompoundTag writeExternal(ExternalEntry entry) {
        ExternalSceneIdentity identity = entry.identity();
        CompoundTag encoded = new CompoundTag();
        encoded.putUUID(EVENT_ID, identity.eventId());
        encoded.putUUID(TARGET_ID, identity.targetId());
        encoded.putString(PROFILE, identity.profile());
        encoded.putInt(TTL, identity.ttlTicks());
        encoded.putInt(STAGE, identity.stage());
        encoded.putString(PAYLOAD_HASH, identity.payloadHash());
        encoded.putString(STATE, entry.state().name());
        return encoded;
    }

    private static void requireUuid(CompoundTag encoded, String field) {
        requireType(encoded, field, Tag.TAG_INT_ARRAY);
        if (!encoded.hasUUID(field)) {
            throw new IllegalArgumentException("invalid replay UUID");
        }
    }

    private static void requireType(CompoundTag encoded, String field, int type) {
        Tag value = encoded.get(field);
        if (value == null || value.getId() != type) {
            throw new IllegalArgumentException("invalid replay field type");
        }
    }

    private static ResourceLocation parseResource(String raw) {
        ResourceLocation parsed = ResourceLocation.tryParse(raw);
        if (parsed == null) {
            throw new IllegalArgumentException("invalid replay timeline id");
        }
        return parsed;
    }

    public enum ReserveStatus {
        RESERVED,
        EXACT_APPLIED,
        EXACT_RESERVED,
        IDENTITY_CONFLICT,
        CAPACITY_EXHAUSTED,
        CORRUPT_DATA,
        UNSUPPORTED_SCHEMA
    }

    public enum DispatchClaim {
        NEW_RESERVED,
        RESUME_RESERVED,
        ALREADY_APPLIED,
        REJECTED;

        public boolean mayDispatch() {
            return this == NEW_RESERVED || this == RESUME_RESERVED;
        }
    }

    public enum ExternalReserveStatus {
        RESERVED,
        EXACT_APPLIED,
        EXACT_RESERVED,
        IDENTITY_CONFLICT,
        CAPACITY_EXHAUSTED,
        CORRUPT_DATA,
        UNSUPPORTED_SCHEMA
    }

    public enum ExternalDispatchClaim {
        NEW_RESERVED,
        RESUME_RESERVED,
        ALREADY_APPLIED,
        REJECTED;

        public boolean mayDispatch() {
            return this == NEW_RESERVED || this == RESUME_RESERVED;
        }
    }

    public enum DataHealth {
        HEALTHY,
        CORRUPT,
        UNSUPPORTED
    }

    private enum EntryState {
        RESERVED,
        APPLIED
    }

    private record Entry(TimelineReplayIdentity identity, EntryState state) {

        private Entry {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(state, "state");
        }
    }

    public record ExternalSceneIdentity(
            UUID eventId,
            UUID targetId,
            String profile,
            int ttlTicks,
            int stage,
            String payloadHash) {

        public ExternalSceneIdentity {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(targetId, "targetId");
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(payloadHash, "payloadHash");
            if (eventId.equals(new UUID(0L, 0L))
                    || targetId.equals(new UUID(0L, 0L))
                    || ttlTicks < SceneDescriptor.MIN_TTL_TICKS
                    || ttlTicks > SceneDescriptor.MAX_TTL_TICKS
                    || stage < 0
                    || !payloadHash.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("invalid external scene replay identity");
            }
            SceneProfile parsed = SceneProfile.parse(profile);
            if (!parsed.serializedName().equals(profile)
                    || stage > parsed.maxStage()
                    || !payloadHash.equals(TimelineDeterminism.directScenePayloadHash(
                            targetId, profile, ttlTicks, stage))) {
                throw new IllegalArgumentException("inconsistent external scene replay identity");
            }
        }

        public static ExternalSceneIdentity create(
                UUID eventId,
                UUID targetId,
                String profile,
                int ttlTicks,
                int stage) {
            return new ExternalSceneIdentity(
                    eventId,
                    targetId,
                    profile,
                    ttlTicks,
                    stage,
                    TimelineDeterminism.directScenePayloadHash(
                            targetId, profile, ttlTicks, stage));
        }
    }

    private record ExternalEntry(
            ExternalSceneIdentity identity, EntryState state) {

        private ExternalEntry {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(state, "state");
        }
    }
}
