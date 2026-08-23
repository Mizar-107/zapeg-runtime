package io.github.mizar107.zapegruntime.timeline;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Durable per-player sessions and non-evicting duplicate/result barriers. */
public final class TimelineSessionData extends SavedData {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MAX_ACTIVE_SESSIONS = 256;
    public static final int MAX_TERMINAL_RESULTS = 4_096;

    private static final String DATA_NAME = "zapeg_runtime_timelines";
    private static final String SCHEMA_VERSION = "SchemaVersion";
    private static final String ACTIVE = "Active";
    private static final String TERMINAL = "Terminal";
    private static final String SESSION_ID = "SessionId";
    private static final String TARGET_ID = "TargetId";
    private static final String TIMELINE_ID = "TimelineId";
    private static final String FINGERPRINT = "Fingerprint";
    private static final String DIMENSION = "Dimension";
    private static final String SEED = "Seed";
    private static final String ELAPSED = "Elapsed";
    private static final String NEXT_ACTION = "NextAction";
    private static final String ATTEMPTS = "Attempts";
    private static final String RETRY_AT = "RetryAt";
    private static final String STATUS = "Status";
    private static final String REASON = "Reason";
    private static final Set<String> ROOT_FIELDS =
            Set.of(SCHEMA_VERSION, ACTIVE, TERMINAL);
    private static final Set<String> ACTIVE_FIELDS = Set.of(
            SESSION_ID,
            TARGET_ID,
            TIMELINE_ID,
            FINGERPRINT,
            DIMENSION,
            SEED,
            ELAPSED,
            NEXT_ACTION,
            ATTEMPTS,
            RETRY_AT,
            STATUS);
    private static final Set<String> TERMINAL_FIELDS = Set.of(
            SESSION_ID,
            TARGET_ID,
            TIMELINE_ID,
            FINGERPRINT,
            STATUS,
            REASON);

    private final Map<UUID, TimelineSession> activeByTarget = new HashMap<>();
    private final Map<UUID, TerminalResult> terminalBySession = new HashMap<>();
    private final DataHealth health;
    private final CompoundTag preservedRoot;
    private final String dataIssue;

    public TimelineSessionData() {
        health = DataHealth.HEALTHY;
        preservedRoot = null;
        dataIssue = "none";
    }

    private TimelineSessionData(
            DataHealth health, CompoundTag preservedRoot, String dataIssue) {
        if (health == DataHealth.HEALTHY) {
            throw new IllegalArgumentException("healthy data cannot be quarantined");
        }
        this.health = health;
        this.preservedRoot = preservedRoot.copy();
        this.dataIssue = Objects.requireNonNull(dataIssue, "dataIssue");
    }

    public static TimelineSessionData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                TimelineSessionData::load,
                TimelineSessionData::new,
                DATA_NAME);
    }

    public static TimelineSessionData load(CompoundTag root) {
        Objects.requireNonNull(root, "root");
        Tag schemaTag = root.get(SCHEMA_VERSION);
        if (schemaTag == null) {
            return quarantined(DataHealth.UNSUPPORTED, root, "schema_missing");
        }
        if (schemaTag.getId() != Tag.TAG_INT) {
            return quarantined(DataHealth.CORRUPT, root, "schema_type");
        }
        int schemaVersion = root.getInt(SCHEMA_VERSION);
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            return quarantined(
                    DataHealth.UNSUPPORTED,
                    root,
                    "schema_" + schemaVersion);
        }

        try {
            requireExactFields(root, ROOT_FIELDS, "root");
            ListTag encodedTerminal = requireCompoundList(root, TERMINAL);
            ListTag encodedActive = requireCompoundList(root, ACTIVE);
            if (encodedTerminal.size() > MAX_TERMINAL_RESULTS) {
                throw corrupt("terminal_capacity");
            }
            if (encodedActive.size() > MAX_ACTIVE_SESSIONS) {
                throw corrupt("active_capacity");
            }
            if ((long) encodedTerminal.size() + encodedActive.size()
                    > MAX_TERMINAL_RESULTS) {
                throw corrupt("reserved_capacity");
            }

            TimelineSessionData data = new TimelineSessionData();
            for (int index = 0; index < encodedTerminal.size(); index++) {
                CompoundTag encoded = encodedTerminal.getCompound(index);
                requireExactFields(encoded, TERMINAL_FIELDS, "terminal[" + index + "]");
                requireTerminalTypes(encoded, index);
                TerminalResult result = readTerminal(encoded);
                if (data.terminalBySession.putIfAbsent(result.sessionId(), result) != null) {
                    throw corrupt("duplicate_terminal_session");
                }
            }

            Set<UUID> activeSessionIds = new HashSet<>();
            for (int index = 0; index < encodedActive.size(); index++) {
                CompoundTag encoded = encodedActive.getCompound(index);
                requireExactFields(encoded, ACTIVE_FIELDS, "active[" + index + "]");
                requireActiveTypes(encoded, index);
                TimelineSession loaded = readSession(encodedActive.getCompound(index));
                if (data.terminalBySession.containsKey(loaded.sessionId())) {
                    throw corrupt("active_terminal_session_conflict");
                }
                if (data.activeByTarget.containsKey(loaded.targetId())) {
                    throw corrupt("duplicate_active_target");
                }
                if (!activeSessionIds.add(loaded.sessionId())) {
                    throw corrupt("duplicate_active_session");
                }
                data.activeByTarget.put(
                        loaded.targetId(), TimelineEngine.pauseForRestart(loaded));
            }
            if (!data.activeByTarget.isEmpty()) {
                data.setDirty();
            }
            return data;
        } catch (StructuralCorruption invalid) {
            return quarantined(DataHealth.CORRUPT, root, invalid.getMessage());
        } catch (IllegalArgumentException invalid) {
            return quarantined(
                    DataHealth.CORRUPT,
                    root,
                    "record_invalid:" + invalid.getMessage());
        }
    }

    public boolean supportsCurrentSchema() {
        return health == DataHealth.HEALTHY;
    }

    public DataDiagnostic dataDiagnostic() {
        return new DataDiagnostic(health, dataIssue);
    }

    public BeginResult begin(TimelineSession proposed) {
        Objects.requireNonNull(proposed, "proposed");
        if (!supportsCurrentSchema()) {
            return new BeginResult(
                    health == DataHealth.CORRUPT
                            ? BeginStatus.CORRUPT_DATA
                            : BeginStatus.UNSUPPORTED_SCHEMA,
                    null,
                    null);
        }
        TerminalResult terminal = terminalBySession.get(proposed.sessionId());
        if (terminal != null) {
            if (terminal.matches(proposed)) {
                return new BeginResult(
                        BeginStatus.IDEMPOTENT_TERMINAL, null, terminal);
            }
            return new BeginResult(BeginStatus.SESSION_ID_CONFLICT, null, terminal);
        }
        Optional<TimelineSession> sameSession = findBySession(proposed.sessionId());
        if (sameSession.isPresent()) {
            TimelineSession existing = sameSession.get();
            return new BeginResult(
                    existing.sameStartRequest(proposed)
                            ? BeginStatus.IDEMPOTENT_ACTIVE
                            : BeginStatus.SESSION_ID_CONFLICT,
                    existing,
                    null);
        }
        TimelineSession busy = activeByTarget.get(proposed.targetId());
        if (busy != null) {
            return new BeginResult(BeginStatus.TARGET_BUSY, busy, null);
        }
        if (activeByTarget.size() >= MAX_ACTIVE_SESSIONS) {
            return new BeginResult(BeginStatus.ACTIVE_CAPACITY_EXHAUSTED, null, null);
        }
        if (reservedTerminalSlots() >= MAX_TERMINAL_RESULTS) {
            return new BeginResult(BeginStatus.RESULT_CAPACITY_EXHAUSTED, null, null);
        }
        activeByTarget.put(proposed.targetId(), proposed);
        setDirty();
        return new BeginResult(BeginStatus.STARTED, proposed, null);
    }

    public boolean update(TimelineSession updated) {
        Objects.requireNonNull(updated, "updated");
        if (!supportsCurrentSchema()) {
            return false;
        }
        TimelineSession current = activeByTarget.get(updated.targetId());
        if (current == null
                || !current.sessionId().equals(updated.sessionId())
                || !current.sameIdentity(updated)) {
            return false;
        }
        if (!current.equals(updated)) {
            activeByTarget.put(updated.targetId(), updated);
            setDirty();
        }
        return true;
    }

    public FinishStatus finish(
            UUID sessionId,
            UUID targetId,
            TimelineEngine.Terminal terminal) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(terminal, "terminal");
        if (!supportsCurrentSchema()) {
            return health == DataHealth.CORRUPT
                    ? FinishStatus.CORRUPT_DATA
                    : FinishStatus.UNSUPPORTED_SCHEMA;
        }
        if (terminalBySession.containsKey(sessionId)) {
            return FinishStatus.ALREADY_TERMINAL;
        }
        TimelineSession active = activeByTarget.get(targetId);
        if (active == null || !active.sessionId().equals(sessionId)) {
            return FinishStatus.NOT_ACTIVE;
        }
        activeByTarget.remove(targetId);
        terminalBySession.put(sessionId, new TerminalResult(
                sessionId,
                targetId,
                active.timelineId(),
                active.definitionFingerprint(),
                terminal.status(),
                terminal.reason()));
        setDirty();
        return FinishStatus.RECORDED;
    }

    public FinishStatus cancelForTarget(UUID targetId) {
        TimelineSession active = activeByTarget.get(targetId);
        if (active == null) {
            return supportsCurrentSchema()
                    ? FinishStatus.NOT_ACTIVE
                    : health == DataHealth.CORRUPT
                            ? FinishStatus.CORRUPT_DATA
                            : FinishStatus.UNSUPPORTED_SCHEMA;
        }
        return finish(
                active.sessionId(),
                targetId,
                new TimelineEngine.Terminal(
                        TimelineEngine.TerminalStatus.CANCELLED,
                        TimelineEngine.TerminalReason.OPERATOR_CANCEL));
    }

    public void pauseAllForRestart() {
        if (!supportsCurrentSchema()) {
            return;
        }
        boolean changed = false;
        for (Map.Entry<UUID, TimelineSession> entry : activeByTarget.entrySet()) {
            TimelineSession paused = TimelineEngine.pauseForRestart(entry.getValue());
            if (!paused.equals(entry.getValue())) {
                entry.setValue(paused);
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    public Optional<TimelineSession> activeFor(UUID targetId) {
        return supportsCurrentSchema()
                ? Optional.ofNullable(activeByTarget.get(targetId))
                : Optional.empty();
    }

    public Optional<TimelineSession> findBySession(UUID sessionId) {
        if (!supportsCurrentSchema()) {
            return Optional.empty();
        }
        return activeByTarget.values().stream()
                .filter(session -> session.sessionId().equals(sessionId))
                .findFirst();
    }

    public Optional<TerminalResult> terminal(UUID sessionId) {
        return supportsCurrentSchema()
                ? Optional.ofNullable(terminalBySession.get(sessionId))
                : Optional.empty();
    }

    public Collection<TimelineSession> activeSessions() {
        if (!supportsCurrentSchema()) {
            return List.of();
        }
        List<TimelineSession> ordered = new ArrayList<>(activeByTarget.values());
        ordered.sort(Comparator.comparing(session -> session.targetId().toString()));
        return Collections.unmodifiableList(ordered);
    }

    public int terminalCount() {
        return supportsCurrentSchema() ? terminalBySession.size() : 0;
    }

    int reservedTerminalSlots() {
        return supportsCurrentSchema()
                ? terminalBySession.size() + activeByTarget.size()
                : 0;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        if (!supportsCurrentSchema()) {
            return preservedRoot.copy();
        }
        root.putInt(SCHEMA_VERSION, CURRENT_SCHEMA_VERSION);

        ListTag active = new ListTag();
        activeSessions().forEach(session -> active.add(writeSession(session)));
        root.put(ACTIVE, active);

        ListTag terminal = new ListTag();
        terminalBySession.values().stream()
                .sorted(Comparator.comparing(result -> result.sessionId().toString()))
                .forEach(result -> terminal.add(writeTerminal(result)));
        root.put(TERMINAL, terminal);
        return root;
    }

    private static TimelineSessionData quarantined(
            DataHealth health, CompoundTag root, String issue) {
        return new TimelineSessionData(health, root, issue);
    }

    private static ListTag requireCompoundList(CompoundTag root, String field) {
        Tag raw = root.get(field);
        if (!(raw instanceof ListTag list)) {
            throw corrupt(field + "_list_type");
        }
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            throw corrupt(field + "_element_type");
        }
        return list;
    }

    private static void requireExactFields(
            CompoundTag encoded, Set<String> expected, String context) {
        if (!encoded.getAllKeys().equals(expected)) {
            throw corrupt(context + "_fields");
        }
    }

    private static void requireActiveTypes(CompoundTag encoded, int index) {
        String context = "active[" + index + ']';
        requireUuid(encoded, SESSION_ID, context);
        requireUuid(encoded, TARGET_ID, context);
        requireType(encoded, TIMELINE_ID, Tag.TAG_STRING, context);
        requireType(encoded, FINGERPRINT, Tag.TAG_STRING, context);
        requireType(encoded, DIMENSION, Tag.TAG_STRING, context);
        requireType(encoded, SEED, Tag.TAG_LONG, context);
        requireType(encoded, ELAPSED, Tag.TAG_INT, context);
        requireType(encoded, NEXT_ACTION, Tag.TAG_INT, context);
        requireType(encoded, ATTEMPTS, Tag.TAG_INT, context);
        requireType(encoded, RETRY_AT, Tag.TAG_INT, context);
        requireType(encoded, STATUS, Tag.TAG_STRING, context);
    }

    private static void requireTerminalTypes(CompoundTag encoded, int index) {
        String context = "terminal[" + index + ']';
        requireUuid(encoded, SESSION_ID, context);
        requireUuid(encoded, TARGET_ID, context);
        requireType(encoded, TIMELINE_ID, Tag.TAG_STRING, context);
        requireType(encoded, FINGERPRINT, Tag.TAG_STRING, context);
        requireType(encoded, STATUS, Tag.TAG_STRING, context);
        requireType(encoded, REASON, Tag.TAG_STRING, context);
    }

    private static void requireUuid(
            CompoundTag encoded, String field, String context) {
        requireType(encoded, field, Tag.TAG_INT_ARRAY, context);
        if (!encoded.hasUUID(field)) {
            throw corrupt(context + '_' + field + "_uuid");
        }
    }

    private static void requireType(
            CompoundTag encoded, String field, int expectedType, String context) {
        Tag value = encoded.get(field);
        if (value == null || value.getId() != expectedType) {
            throw corrupt(context + '_' + field + "_type");
        }
    }

    private static StructuralCorruption corrupt(String issue) {
        return new StructuralCorruption(issue);
    }

    private static CompoundTag writeSession(TimelineSession session) {
        CompoundTag encoded = new CompoundTag();
        encoded.putUUID(SESSION_ID, session.sessionId());
        encoded.putUUID(TARGET_ID, session.targetId());
        encoded.putString(TIMELINE_ID, session.timelineId().toString());
        encoded.putString(FINGERPRINT, session.definitionFingerprint());
        encoded.putString(DIMENSION, session.boundDimension().toString());
        encoded.putLong(SEED, session.seed());
        encoded.putInt(ELAPSED, session.elapsedTicks());
        encoded.putInt(NEXT_ACTION, session.nextActionIndex());
        encoded.putInt(ATTEMPTS, session.actionAttempts());
        encoded.putInt(RETRY_AT, session.retryAtElapsedTick());
        encoded.putString(STATUS, session.status().name());
        return encoded;
    }

    private static TimelineSession readSession(CompoundTag encoded) {
        if (!encoded.hasUUID(SESSION_ID) || !encoded.hasUUID(TARGET_ID)) {
            throw new IllegalArgumentException("timeline UUID missing");
        }
        return new TimelineSession(
                encoded.getUUID(SESSION_ID),
                encoded.getUUID(TARGET_ID),
                parseResource(encoded.getString(TIMELINE_ID), TIMELINE_ID),
                encoded.getString(FINGERPRINT),
                parseResource(encoded.getString(DIMENSION), DIMENSION),
                encoded.getLong(SEED),
                encoded.getInt(ELAPSED),
                encoded.getInt(NEXT_ACTION),
                encoded.getInt(ATTEMPTS),
                encoded.getInt(RETRY_AT),
                parseEnum(TimelineSession.Status.class, encoded.getString(STATUS), STATUS));
    }

    private static CompoundTag writeTerminal(TerminalResult result) {
        CompoundTag encoded = new CompoundTag();
        encoded.putUUID(SESSION_ID, result.sessionId());
        encoded.putUUID(TARGET_ID, result.targetId());
        encoded.putString(TIMELINE_ID, result.timelineId().toString());
        encoded.putString(FINGERPRINT, result.definitionFingerprint());
        encoded.putString(STATUS, result.status().name());
        encoded.putString(REASON, result.reason().name());
        return encoded;
    }

    private static TerminalResult readTerminal(CompoundTag encoded) {
        if (!encoded.hasUUID(SESSION_ID) || !encoded.hasUUID(TARGET_ID)) {
            throw new IllegalArgumentException("timeline result UUID missing");
        }
        return new TerminalResult(
                encoded.getUUID(SESSION_ID),
                encoded.getUUID(TARGET_ID),
                parseResource(encoded.getString(TIMELINE_ID), TIMELINE_ID),
                encoded.getString(FINGERPRINT),
                parseEnum(
                        TimelineEngine.TerminalStatus.class,
                        encoded.getString(STATUS),
                        STATUS),
                parseEnum(
                        TimelineEngine.TerminalReason.class,
                        encoded.getString(REASON),
                        REASON));
    }

    private static ResourceLocation parseResource(String raw, String field) {
        ResourceLocation parsed = ResourceLocation.tryParse(raw);
        if (parsed == null) {
            throw new IllegalArgumentException("invalid " + field);
        }
        return parsed;
    }

    private static <T extends Enum<T>> T parseEnum(
            Class<T> type, String raw, String field) {
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("invalid " + field, invalid);
        }
    }

    public enum BeginStatus {
        STARTED,
        IDEMPOTENT_ACTIVE,
        IDEMPOTENT_TERMINAL,
        TARGET_BUSY,
        SESSION_ID_CONFLICT,
        ACTIVE_CAPACITY_EXHAUSTED,
        RESULT_CAPACITY_EXHAUSTED,
        CORRUPT_DATA,
        UNSUPPORTED_SCHEMA
    }

    public record BeginResult(
            BeginStatus status,
            TimelineSession active,
            TerminalResult terminal) {}

    public enum FinishStatus {
        RECORDED,
        ALREADY_TERMINAL,
        NOT_ACTIVE,
        CORRUPT_DATA,
        UNSUPPORTED_SCHEMA
    }

    public enum DataHealth {
        HEALTHY,
        CORRUPT,
        UNSUPPORTED
    }

    public record DataDiagnostic(DataHealth health, String issue) {

        public DataDiagnostic {
            Objects.requireNonNull(health, "health");
            Objects.requireNonNull(issue, "issue");
        }
    }

    private static final class StructuralCorruption extends IllegalArgumentException {

        private StructuralCorruption(String message) {
            super(message);
        }
    }

    public record TerminalResult(
            UUID sessionId,
            UUID targetId,
            ResourceLocation timelineId,
            String definitionFingerprint,
            TimelineEngine.TerminalStatus status,
            TimelineEngine.TerminalReason reason) {

        public TerminalResult {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(targetId, "targetId");
            Objects.requireNonNull(timelineId, "timelineId");
            Objects.requireNonNull(definitionFingerprint, "definitionFingerprint");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(reason, "reason");
            if (sessionId.equals(new UUID(0L, 0L))
                    || targetId.equals(new UUID(0L, 0L))
                    || sessionId.equals(targetId)) {
                throw new IllegalArgumentException("invalid terminal UUIDs");
            }
            if (!definitionFingerprint.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("invalid terminal fingerprint");
            }
            if (status == TimelineEngine.TerminalStatus.SUCCEEDED
                    && reason != TimelineEngine.TerminalReason.COMPLETED) {
                throw new IllegalArgumentException(
                        "successful timeline result must be completed");
            }
            if (reason == TimelineEngine.TerminalReason.COMPLETED
                    && status != TimelineEngine.TerminalStatus.SUCCEEDED) {
                throw new IllegalArgumentException(
                        "completed timeline result must be successful");
            }
        }

        boolean matches(TimelineSession session) {
            return sessionId.equals(session.sessionId())
                    && targetId.equals(session.targetId())
                    && timelineId.equals(session.timelineId())
                    && definitionFingerprint.equals(session.definitionFingerprint());
        }
    }
}
