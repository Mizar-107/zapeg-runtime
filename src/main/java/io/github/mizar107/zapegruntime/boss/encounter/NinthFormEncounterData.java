package io.github.mizar107.zapegruntime.boss.encounter;

import io.github.mizar107.zapegruntime.boss.api.NinthFormCombatSignal;
import io.github.mizar107.zapegruntime.boss.api.NinthFormCombatSnapshot;
import io.github.mizar107.zapegruntime.boss.api.NinthFormIdentity;
import io.github.mizar107.zapegruntime.boss.api.NinthFormPhase;
import java.util.ArrayList;
import java.util.Collection;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Strict schema-1 encounter authority.
 *
 * <p>Unknown, future, or corrupt roots are preserved byte-for-byte and become
 * read-only. Phase and defeat barriers never evict. Each live attempt reserves
 * both future barriers before an entity may be spawned.</p>
 */
public final class NinthFormEncounterData extends SavedData {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MAX_ACTIVE_ENCOUNTERS = 32;
    public static final int MAX_IMMUTABLE_BARRIERS = 4_096;
    public static final String DATA_NAME = "zapeg_runtime_ninth_form";

    private static final String SCHEMA_VERSION = "SchemaVersion";
    private static final String ACTIVE = "Active";
    private static final String BARRIERS = "Barriers";
    private static final Set<String> ROOT_FIELDS = Set.of(SCHEMA_VERSION, ACTIVE, BARRIERS);

    private final Map<UUID, NinthFormEncounter> activeByTarget = new HashMap<>();
    private final Map<UUID, NinthFormBarrier> barriersByFact = new HashMap<>();
    private final DataHealth health;
    private final String healthDetail;
    private final CompoundTag preservedRoot;

    public NinthFormEncounterData() {
        this(DataHealth.OK, "schema 1 is writable", null);
    }

    private NinthFormEncounterData(
            DataHealth health, String healthDetail, CompoundTag preservedRoot) {
        this.health = Objects.requireNonNull(health, "health");
        this.healthDetail = Objects.requireNonNull(healthDetail, "healthDetail");
        this.preservedRoot = preservedRoot == null ? null : preservedRoot.copy();
    }

    public static NinthFormEncounterData get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(
                NinthFormEncounterData::load,
                NinthFormEncounterData::new,
                DATA_NAME);
    }

    public static NinthFormEncounterData load(CompoundTag root) {
        Objects.requireNonNull(root, "root");
        try {
            if (!root.contains(SCHEMA_VERSION, Tag.TAG_INT)) {
                return unavailable(DataHealth.CORRUPT, "missing integer SchemaVersion", root);
            }
            int schema = root.getInt(SCHEMA_VERSION);
            if (schema != CURRENT_SCHEMA_VERSION) {
                return unavailable(
                        DataHealth.UNSUPPORTED,
                        "unsupported Ninth Form schema " + schema,
                        root);
            }
            NinthFormBarrier.requireExactFields(root, ROOT_FIELDS, "Ninth Form root");
            ListTag activeTags = requireCompoundList(root, ACTIVE, MAX_ACTIVE_ENCOUNTERS);
            ListTag barrierTags = requireCompoundList(root, BARRIERS, MAX_IMMUTABLE_BARRIERS);

            NinthFormEncounterData data = new NinthFormEncounterData();
            Set<UUID> encounterIds = new HashSet<>();
            Set<UUID> activeAuthorityIds = new HashSet<>();
            for (int index = 0; index < activeTags.size(); index++) {
                NinthFormEncounter encounter = NinthFormEncounter.load(activeTags.getCompound(index));
                if (!encounterIds.add(encounter.encounterId())
                        || data.activeByTarget.put(encounter.targetId(), encounter) != null) {
                    throw new IllegalArgumentException("duplicate active encounter or target");
                }
                for (UUID id : List.of(
                        encounter.encounterId(),
                        encounter.targetId(),
                        encounter.entityId(),
                        encounter.phaseFactId(),
                        encounter.defeatFactId())) {
                    if (!activeAuthorityIds.add(id)) {
                        throw new IllegalArgumentException("active UUID roles alias across encounters");
                    }
                }
            }

            Map<BarrierKey, NinthFormBarrier> barrierKinds = new HashMap<>();
            for (int index = 0; index < barrierTags.size(); index++) {
                NinthFormBarrier barrier = NinthFormBarrier.load(barrierTags.getCompound(index));
                if (data.barriersByFact.put(barrier.factId(), barrier) != null) {
                    throw new IllegalArgumentException("duplicate barrier fact UUID");
                }
                BarrierKey key = new BarrierKey(barrier.encounterId(), barrier.kind());
                if (barrierKinds.put(key, barrier) != null) {
                    throw new IllegalArgumentException("duplicate barrier kind for encounter");
                }
            }
            validateBarrierFamilies(barrierKinds.values());
            for (NinthFormEncounter encounter : data.activeByTarget.values()) {
                validateActiveBarrierState(encounter, barrierKinds);
            }
            for (NinthFormBarrier barrier : barrierKinds.values()) {
                if (barrier.kind() == NinthFormBarrier.Kind.PHASE_ONE_COMPLETED
                        && !encounterIds.contains(barrier.encounterId())
                        && !barrierKinds.containsKey(new BarrierKey(
                                barrier.encounterId(), NinthFormBarrier.Kind.DEFEATED))) {
                    throw new IllegalArgumentException("orphan phase barrier has no active attempt");
                }
            }
            if (data.reservedBarrierSlots() > MAX_IMMUTABLE_BARRIERS) {
                throw new IllegalArgumentException("live attempts over-reserve immutable barriers");
            }
            return data;
        } catch (RuntimeException malformed) {
            return unavailable(
                    DataHealth.CORRUPT,
                    "schema 1 rejected: " + boundedDetail(malformed.getMessage()),
                    root);
        }
    }

    public SchemaStatus schemaStatus() {
        return new SchemaStatus(
                CURRENT_SCHEMA_VERSION,
                health,
                healthDetail,
                writable());
    }

    public BeginResult begin(NinthFormEncounter proposed) {
        Objects.requireNonNull(proposed, "proposed");
        if (!writable()) {
            return new BeginResult(BeginStatus.DATA_UNAVAILABLE, null, healthDetail);
        }
        if (proposed.generation() != 0
                || proposed.phase() != NinthFormPhase.PRELUDE
                || proposed.lifecycle() != NinthFormEncounter.Lifecycle.PREPARED
                || !proposed.combatState().equals(
                        new NinthFormCombatSnapshot.CombatState(0, 0L, "idle", 0))
                || !proposed.vitalState().equals(NinthFormCombatSnapshot.VitalState.pristine())) {
            return new BeginResult(
                    BeginStatus.INVALID_INITIAL_STATE,
                    null,
                    "new attempts must begin at the pristine generation-zero prelude");
        }
        Optional<NinthFormEncounter> sameEncounter = findByEncounter(proposed.encounterId());
        if (sameEncounter.isPresent()) {
            if (sameEncounter.get().equals(proposed)) {
                return new BeginResult(BeginStatus.IDEMPOTENT, sameEncounter.get(), "already prepared");
            }
            return new BeginResult(
                    BeginStatus.IDENTITY_CONFLICT,
                    sameEncounter.get(),
                    "encounter UUID is already bound to another payload");
        }
        if (barriersByFact.values().stream()
                .anyMatch(barrier -> barrier.encounterId().equals(proposed.encounterId()))) {
            return new BeginResult(
                    BeginStatus.ALREADY_TERMINAL,
                    null,
                    "encounter UUID already has immutable history");
        }
        NinthFormEncounter busy = activeByTarget.get(proposed.targetId());
        if (busy != null) {
            return new BeginResult(BeginStatus.TARGET_BUSY, busy, "target already owns an attempt");
        }
        if (activeByTarget.size() >= MAX_ACTIVE_ENCOUNTERS) {
            return new BeginResult(
                    BeginStatus.ACTIVE_CAPACITY_EXHAUSTED,
                    null,
                    "active encounter capacity is exhausted");
        }
        if (authorityConflicts(proposed)) {
            return new BeginResult(
                    BeginStatus.IDENTITY_CONFLICT,
                    null,
                    "one or more UUID roles are already reserved");
        }
        if (!proposed.rehearsal()
                && reservedBarrierSlots() + 2 > MAX_IMMUTABLE_BARRIERS) {
            return new BeginResult(
                    BeginStatus.BARRIER_CAPACITY_EXHAUSTED,
                    null,
                    "two immutable proof slots cannot be reserved");
        }
        activeByTarget.put(proposed.targetId(), proposed);
        setDirty();
        return new BeginResult(BeginStatus.STARTED, proposed, "encounter prepared durably");
    }

    public MutationResult activate(NinthFormIdentity identity, UUID entityId) {
        return updateLifecycle(identity, entityId, NinthFormEncounter.Lifecycle.PREPARED,
                NinthFormEncounter.Lifecycle.ACTIVE);
    }

    public MutationResult suspend(NinthFormIdentity identity, UUID entityId) {
        NinthFormEncounter encounter = exactActive(identity, entityId).orElse(null);
        if (!writable()) {
            return MutationResult.DATA_UNAVAILABLE;
        }
        if (encounter == null) {
            return MutationResult.IDENTITY_MISMATCH;
        }
        if (encounter.lifecycle() == NinthFormEncounter.Lifecycle.SUSPENDED) {
            return MutationResult.IDEMPOTENT;
        }
        replace(encounter.withLifecycle(NinthFormEncounter.Lifecycle.SUSPENDED));
        return MutationResult.APPLIED;
    }

    public MutationResult storeSnapshot(NinthFormCombatSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!writable()) {
            return MutationResult.DATA_UNAVAILABLE;
        }
        NinthFormEncounter encounter = exactActive(snapshot.identity(), snapshot.entityId())
                .orElse(null);
        if (encounter == null || encounter.lifecycle() != NinthFormEncounter.Lifecycle.ACTIVE) {
            return MutationResult.IDENTITY_MISMATCH;
        }
        try {
            NinthFormEncounter updated = encounter.withSnapshot(snapshot);
            if (updated.equals(encounter)) {
                return MutationResult.IDEMPOTENT;
            }
            replace(updated);
            return MutationResult.APPLIED;
        } catch (IllegalArgumentException invalid) {
            return MutationResult.STATE_MISMATCH;
        }
    }

    public MutationResult advanceActivePhase(
            NinthFormIdentity identity,
            UUID entityId,
            NinthFormPhase expected,
            NinthFormPhase next) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(next, "next");
        if (!writable()) {
            return MutationResult.DATA_UNAVAILABLE;
        }
        NinthFormEncounter encounter = exactActive(identity, entityId).orElse(null);
        if (encounter == null) {
            return MutationResult.IDENTITY_MISMATCH;
        }
        if (encounter.phase() == next) {
            return MutationResult.IDEMPOTENT;
        }
        if (encounter.lifecycle() != NinthFormEncounter.Lifecycle.ACTIVE
                || encounter.phase() != expected) {
            return MutationResult.STATE_MISMATCH;
        }
        boolean schedulerOwnedEdge = (expected == NinthFormPhase.PRELUDE
                        && next == NinthFormPhase.FIRST)
                || (expected == NinthFormPhase.INTERLUDE && next == NinthFormPhase.FINAL);
        if (!schedulerOwnedEdge) {
            return MutationResult.STATE_MISMATCH;
        }
        try {
            replace(encounter.advanceTo(next));
            return MutationResult.APPLIED;
        } catch (IllegalArgumentException invalid) {
            return MutationResult.STATE_MISMATCH;
        }
    }

    public RotationResult rotateGeneration(UUID encounterId, UUID replacementEntityId) {
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(replacementEntityId, "replacementEntityId");
        if (!writable()) {
            return new RotationResult(RotationStatus.DATA_UNAVAILABLE, null);
        }
        NinthFormEncounter encounter = findByEncounter(encounterId).orElse(null);
        if (encounter == null) {
            return new RotationResult(RotationStatus.NOT_ACTIVE, null);
        }
        if (encounter.lifecycle() != NinthFormEncounter.Lifecycle.SUSPENDED) {
            return new RotationResult(RotationStatus.STATE_MISMATCH, encounter);
        }
        if (allReservedUuids().contains(replacementEntityId)) {
            return new RotationResult(RotationStatus.IDENTITY_CONFLICT, encounter);
        }
        try {
            NinthFormEncounter rotated = encounter.rotateGeneration(replacementEntityId);
            replace(rotated);
            return new RotationResult(RotationStatus.ROTATED, rotated);
        } catch (IllegalStateException exhausted) {
            return new RotationResult(RotationStatus.GENERATION_EXHAUSTED, encounter);
        } catch (IllegalArgumentException conflict) {
            return new RotationResult(RotationStatus.IDENTITY_CONFLICT, encounter);
        }
    }

    public ProofResult recordPhaseCompletion(NinthFormCombatSignal signal) {
        Objects.requireNonNull(signal, "signal");
        if (signal.kind() != NinthFormCombatSignal.Kind.PHASE_COMPLETED) {
            return ProofResult.STATE_MISMATCH;
        }
        if (!writable()) {
            return ProofResult.DATA_UNAVAILABLE;
        }
        Optional<NinthFormBarrier> replay = barrierFor(
                signal.identity().encounterId(), NinthFormBarrier.Kind.PHASE_ONE_COMPLETED);
        if (replay.isPresent()) {
            return replayMatchesSignal(replay.get(), signal)
                    ? ProofResult.REPLAYED
                    : ProofResult.IDENTITY_MISMATCH;
        }
        NinthFormEncounter encounter = exactActive(signal.identity(), signal.entityId()).orElse(null);
        if (encounter == null) {
            return ProofResult.IDENTITY_MISMATCH;
        }
        if (encounter.lifecycle() != NinthFormEncounter.Lifecycle.ACTIVE
                || encounter.phase() != NinthFormPhase.FIRST) {
            return ProofResult.STATE_MISMATCH;
        }
        NinthFormEncounter advanced = encounter.completeFirstPhase();
        if (encounter.rehearsal()) {
            replace(advanced);
            return ProofResult.REHEARSAL;
        }
        NinthFormBarrier barrier = NinthFormBarrier.fromEncounter(
                encounter, NinthFormBarrier.Kind.PHASE_ONE_COMPLETED);
        barriersByFact.put(barrier.factId(), barrier);
        activeByTarget.put(advanced.targetId(), advanced);
        setDirty();
        return ProofResult.RECORDED;
    }

    public ProofResult recordDefeat(NinthFormCombatSignal signal) {
        Objects.requireNonNull(signal, "signal");
        if (signal.kind() != NinthFormCombatSignal.Kind.DEFEATED) {
            return ProofResult.STATE_MISMATCH;
        }
        if (!writable()) {
            return ProofResult.DATA_UNAVAILABLE;
        }
        Optional<NinthFormBarrier> replay = barrierFor(
                signal.identity().encounterId(), NinthFormBarrier.Kind.DEFEATED);
        if (replay.isPresent()) {
            return replayMatchesSignal(replay.get(), signal)
                    ? ProofResult.REPLAYED
                    : ProofResult.IDENTITY_MISMATCH;
        }
        NinthFormEncounter encounter = exactActive(signal.identity(), signal.entityId()).orElse(null);
        if (encounter == null) {
            return ProofResult.IDENTITY_MISMATCH;
        }
        if (encounter.lifecycle() != NinthFormEncounter.Lifecycle.ACTIVE
                || encounter.phase() != NinthFormPhase.FINAL) {
            return ProofResult.STATE_MISMATCH;
        }
        activeByTarget.remove(encounter.targetId());
        if (encounter.rehearsal()) {
            setDirty();
            return ProofResult.REHEARSAL;
        }
        Optional<NinthFormBarrier> phaseBarrier = barrierFor(
                encounter.encounterId(), NinthFormBarrier.Kind.PHASE_ONE_COMPLETED);
        if (phaseBarrier.isEmpty()) {
            // Never expose terminal state if its preceding durable proof vanished.
            activeByTarget.put(encounter.targetId(), encounter);
            return ProofResult.STATE_MISMATCH;
        }
        NinthFormBarrier barrier = NinthFormBarrier.fromEncounter(
                encounter, NinthFormBarrier.Kind.DEFEATED);
        barriersByFact.put(barrier.factId(), barrier);
        setDirty();
        return ProofResult.RECORDED;
    }

    public Optional<NinthFormEncounter> activeFor(UUID targetId) {
        if (!writable()) {
            return Optional.empty();
        }
        return Optional.ofNullable(activeByTarget.get(targetId));
    }

    public Optional<NinthFormEncounter> findByEncounter(UUID encounterId) {
        if (!writable()) {
            return Optional.empty();
        }
        return activeByTarget.values().stream()
                .filter(encounter -> encounter.encounterId().equals(encounterId))
                .findFirst();
    }

    public Collection<NinthFormEncounter> activeEncounters() {
        if (!writable()) {
            return List.of();
        }
        List<NinthFormEncounter> result = new ArrayList<>(activeByTarget.values());
        result.sort(Comparator.comparing(item -> item.encounterId().toString()));
        return List.copyOf(result);
    }

    public List<NinthFormBarrier> immutableBarriers() {
        if (!writable()) {
            return List.of();
        }
        List<NinthFormBarrier> result = new ArrayList<>(barriersByFact.values());
        result.sort(Comparator.comparing(item -> item.factId().toString()));
        return List.copyOf(result);
    }

    public Optional<NinthFormBarrier> barrierFor(UUID encounterId, NinthFormBarrier.Kind kind) {
        if (!writable()) {
            return Optional.empty();
        }
        return barriersByFact.values().stream()
                .filter(barrier -> barrier.encounterId().equals(encounterId)
                        && barrier.kind() == kind)
                .findFirst();
    }

    public boolean acceptsEntity(NinthFormIdentity identity, UUID entityId) {
        return exactActive(identity, entityId)
                .filter(encounter -> encounter.lifecycle() != NinthFormEncounter.Lifecycle.SUSPENDED)
                .isPresent();
    }

    int reservedBarrierSlots() {
        if (!writable()) {
            return 0;
        }
        int reserved = barriersByFact.size();
        for (NinthFormEncounter encounter : activeByTarget.values()) {
            if (encounter.rehearsal()) {
                continue;
            }
            if (barrierFor(encounter.encounterId(), NinthFormBarrier.Kind.PHASE_ONE_COMPLETED)
                    .isEmpty()) {
                reserved++;
            }
            if (barrierFor(encounter.encounterId(), NinthFormBarrier.Kind.DEFEATED).isEmpty()) {
                reserved++;
            }
        }
        return reserved;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        if (!writable()) {
            return preservedRoot.copy();
        }
        root.putInt(SCHEMA_VERSION, CURRENT_SCHEMA_VERSION);
        ListTag active = new ListTag();
        activeEncounters().forEach(encounter -> active.add(encounter.save()));
        root.put(ACTIVE, active);
        ListTag barriers = new ListTag();
        immutableBarriers().forEach(barrier -> barriers.add(barrier.save()));
        root.put(BARRIERS, barriers);
        return root;
    }

    private MutationResult updateLifecycle(
            NinthFormIdentity identity,
            UUID entityId,
            NinthFormEncounter.Lifecycle expected,
            NinthFormEncounter.Lifecycle next) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(entityId, "entityId");
        if (!writable()) {
            return MutationResult.DATA_UNAVAILABLE;
        }
        NinthFormEncounter encounter = exactActive(identity, entityId).orElse(null);
        if (encounter == null) {
            return MutationResult.IDENTITY_MISMATCH;
        }
        if (encounter.lifecycle() == next) {
            return MutationResult.IDEMPOTENT;
        }
        if (encounter.lifecycle() != expected) {
            return MutationResult.STATE_MISMATCH;
        }
        replace(encounter.withLifecycle(next));
        return MutationResult.APPLIED;
    }

    private Optional<NinthFormEncounter> exactActive(NinthFormIdentity identity, UUID entityId) {
        if (!writable() || identity == null || entityId == null) {
            return Optional.empty();
        }
        return findByEncounter(identity.encounterId())
                .filter(encounter -> encounter.identity().equals(identity)
                        && encounter.entityId().equals(entityId));
    }

    private void replace(NinthFormEncounter encounter) {
        activeByTarget.put(encounter.targetId(), encounter);
        setDirty();
    }

    private boolean authorityConflicts(NinthFormEncounter proposed) {
        Set<UUID> occupied = allReservedUuids();
        return occupied.contains(proposed.encounterId())
                || occupied.contains(proposed.targetId())
                || occupied.contains(proposed.entityId())
                || occupied.contains(proposed.phaseFactId())
                || occupied.contains(proposed.defeatFactId());
    }

    private Set<UUID> allReservedUuids() {
        Set<UUID> occupied = new HashSet<>();
        for (NinthFormEncounter encounter : activeByTarget.values()) {
            occupied.add(encounter.encounterId());
            occupied.add(encounter.targetId());
            occupied.add(encounter.entityId());
            occupied.add(encounter.phaseFactId());
            occupied.add(encounter.defeatFactId());
        }
        for (NinthFormBarrier barrier : barriersByFact.values()) {
            occupied.add(barrier.factId());
            occupied.add(barrier.encounterId());
            occupied.add(barrier.entityId());
            // A player may own later attempts; target UUID is not globally consumed.
        }
        return occupied;
    }

    private static boolean replayMatchesSignal(
            NinthFormBarrier barrier, NinthFormCombatSignal signal) {
        return barrier.encounterId().equals(signal.identity().encounterId())
                && barrier.targetId().equals(signal.identity().targetId())
                && barrier.entityId().equals(signal.entityId())
                && barrier.generation() == signal.identity().generation();
    }

    private static void validateBarrierFamilies(Collection<NinthFormBarrier> barriers) {
        Map<UUID, NinthFormBarrier> firstByEncounter = new HashMap<>();
        Set<UUID> phaseEncounters = new HashSet<>();
        Set<UUID> defeatedEncounters = new HashSet<>();
        for (NinthFormBarrier barrier : barriers) {
            if (barrier.kind() == NinthFormBarrier.Kind.PHASE_ONE_COMPLETED) {
                phaseEncounters.add(barrier.encounterId());
            } else {
                defeatedEncounters.add(barrier.encounterId());
            }
            NinthFormBarrier first = firstByEncounter.putIfAbsent(barrier.encounterId(), barrier);
            if (first != null
                    && (!first.targetId().equals(barrier.targetId())
                            || !first.campaignId().equals(barrier.campaignId())
                            || first.campaignRevision() != barrier.campaignRevision()
                            || !first.campaignFingerprint().equals(barrier.campaignFingerprint())
                            || first.progressEpoch() != barrier.progressEpoch())) {
                throw new IllegalArgumentException("barrier campaign envelope conflicts");
            }
        }
        if (!phaseEncounters.containsAll(defeatedEncounters)) {
            throw new IllegalArgumentException("defeat barrier exists without phase proof");
        }
    }

    private static void validateActiveBarrierState(
            NinthFormEncounter encounter, Map<BarrierKey, NinthFormBarrier> barrierKinds) {
        NinthFormBarrier phase = barrierKinds.get(new BarrierKey(
                encounter.encounterId(), NinthFormBarrier.Kind.PHASE_ONE_COMPLETED));
        NinthFormBarrier defeat = barrierKinds.get(new BarrierKey(
                encounter.encounterId(), NinthFormBarrier.Kind.DEFEATED));
        if (defeat != null) {
            throw new IllegalArgumentException("terminal encounter remains active");
        }
        if (encounter.rehearsal()) {
            if (phase != null) {
                throw new IllegalArgumentException("rehearsal owns a live story barrier");
            }
            return;
        }
        boolean requiresPhaseBarrier = encounter.phase() == NinthFormPhase.INTERLUDE
                || encounter.phase() == NinthFormPhase.FINAL;
        if (requiresPhaseBarrier != (phase != null)) {
            throw new IllegalArgumentException("active phase and phase barrier disagree");
        }
        if (phase != null && !phase.matches(encounter, NinthFormBarrier.Kind.PHASE_ONE_COMPLETED)) {
            // Recovery may rotate after phase completion; the barrier proves the
            // older generation, while the campaign envelope and fact UUID remain exact.
            if (!phase.factId().equals(encounter.phaseFactId())
                    || !phase.encounterId().equals(encounter.encounterId())
                    || !phase.targetId().equals(encounter.targetId())
                    || !phase.campaignId().equals(encounter.campaignId())
                    || phase.campaignRevision() != encounter.campaignRevision()
                    || !phase.campaignFingerprint().equals(encounter.campaignFingerprint())
                    || phase.progressEpoch() != encounter.progressEpoch()) {
                throw new IllegalArgumentException("phase barrier does not match active envelope");
            }
        }
    }

    private static ListTag requireCompoundList(CompoundTag root, String key, int limit) {
        NinthFormBarrier.requireType(root, key, Tag.TAG_LIST);
        Tag raw = root.get(key);
        if (!(raw instanceof ListTag list)) {
            throw new IllegalArgumentException(key + " is not a list");
        }
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException(key + " is not a compound list");
        }
        if (list.size() > limit) {
            throw new IllegalArgumentException(key + " exceeds " + limit + " entries");
        }
        return list;
    }

    private static NinthFormEncounterData unavailable(
            DataHealth health, String detail, CompoundTag root) {
        return new NinthFormEncounterData(health, detail, root);
    }

    private static String boundedDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return "unknown format error";
        }
        return detail.length() <= 256 ? detail : detail.substring(0, 256);
    }

    private boolean writable() {
        return health == DataHealth.OK;
    }

    private record BarrierKey(UUID encounterId, NinthFormBarrier.Kind kind) {}

    public enum DataHealth {
        OK,
        UNSUPPORTED,
        CORRUPT
    }

    public record SchemaStatus(
            int currentVersion, DataHealth health, String detail, boolean writable) {}

    public enum BeginStatus {
        STARTED,
        IDEMPOTENT,
        INVALID_INITIAL_STATE,
        TARGET_BUSY,
        ALREADY_TERMINAL,
        IDENTITY_CONFLICT,
        ACTIVE_CAPACITY_EXHAUSTED,
        BARRIER_CAPACITY_EXHAUSTED,
        DATA_UNAVAILABLE
    }

    public record BeginResult(
            BeginStatus status, NinthFormEncounter encounter, String detail) {}

    public enum MutationResult {
        APPLIED,
        IDEMPOTENT,
        IDENTITY_MISMATCH,
        STATE_MISMATCH,
        DATA_UNAVAILABLE
    }

    public enum RotationStatus {
        ROTATED,
        NOT_ACTIVE,
        STATE_MISMATCH,
        IDENTITY_CONFLICT,
        GENERATION_EXHAUSTED,
        DATA_UNAVAILABLE
    }

    public record RotationResult(RotationStatus status, NinthFormEncounter encounter) {}

    public enum ProofResult {
        RECORDED,
        REPLAYED,
        REHEARSAL,
        IDENTITY_MISMATCH,
        STATE_MISMATCH,
        DATA_UNAVAILABLE
    }
}
