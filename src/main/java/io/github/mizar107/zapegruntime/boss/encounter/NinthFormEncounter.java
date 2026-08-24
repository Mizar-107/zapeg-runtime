package io.github.mizar107.zapegruntime.boss.encounter;

import io.github.mizar107.zapegruntime.boss.api.NinthFormCombatSnapshot;
import io.github.mizar107.zapegruntime.boss.api.NinthFormEntityGateway;
import io.github.mizar107.zapegruntime.boss.api.NinthFormIdentity;
import io.github.mizar107.zapegruntime.boss.api.NinthFormPhase;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Exact durable authority for one live or rehearsal Ninth Form attempt. */
public record NinthFormEncounter(
        UUID encounterId,
        UUID targetId,
        UUID entityId,
        UUID phaseFactId,
        UUID defeatFactId,
        int generation,
        boolean rehearsal,
        ResourceLocation campaignId,
        int campaignRevision,
        String campaignFingerprint,
        long progressEpoch,
        String dimensionId,
        int arenaX,
        int arenaY,
        int arenaZ,
        NinthFormPhase phase,
        Lifecycle lifecycle,
        int participantCount,
        double healthScale,
        double damageScale,
        NinthFormCombatSnapshot.CombatState combatState,
        NinthFormCombatSnapshot.VitalState vitalState,
        long lastObservedGameTick) {

    public static final int ARENA_RADIUS = 48;
    static final int MAX_DIMENSION_ID_LENGTH = 128;

    private static final UUID NIL_UUID = new UUID(0L, 0L);
    private static final Pattern FINGERPRINT = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern ATTACK_ID_PATTERN = Pattern.compile("[a-z0-9_]{1,32}");

    private static final String ENCOUNTER_ID = "EncounterId";
    private static final String TARGET_ID = "TargetId";
    private static final String ENTITY_ID = "EntityId";
    private static final String PHASE_FACT_ID = "PhaseFactId";
    private static final String DEFEAT_FACT_ID = "DefeatFactId";
    private static final String GENERATION = "Generation";
    private static final String REHEARSAL = "Rehearsal";
    private static final String CAMPAIGN_ID = "CampaignId";
    private static final String CAMPAIGN_REVISION = "CampaignRevision";
    private static final String CAMPAIGN_FINGERPRINT = "CampaignFingerprint";
    private static final String PROGRESS_EPOCH = "ProgressEpoch";
    private static final String DIMENSION_ID = "DimensionId";
    private static final String ARENA_X = "ArenaX";
    private static final String ARENA_Y = "ArenaY";
    private static final String ARENA_Z = "ArenaZ";
    private static final String PHASE = "Phase";
    private static final String LIFECYCLE = "Lifecycle";
    private static final String PARTICIPANT_COUNT = "ParticipantCount";
    private static final String HEALTH_SCALE = "HealthScale";
    private static final String DAMAGE_SCALE = "DamageScale";
    private static final String BROKEN_POINT_MASK = "BrokenPointMask";
    private static final String ATTACK_CYCLE = "AttackCycle";
    private static final String ATTACK_ID = "AttackId";
    private static final String ATTACK_TICK = "AttackTick";
    private static final String PARENT_HEALTH = "ParentHealth";
    private static final String PROW_HEALTH = "ProwHealth";
    private static final String PORT_HEALTH = "PortHealth";
    private static final String STARBOARD_HEALTH = "StarboardHealth";
    private static final String LAST_OBSERVED_TICK = "LastObservedTick";

    static final Set<String> FIELDS = Set.of(
            ENCOUNTER_ID,
            TARGET_ID,
            ENTITY_ID,
            PHASE_FACT_ID,
            DEFEAT_FACT_ID,
            GENERATION,
            REHEARSAL,
            CAMPAIGN_ID,
            CAMPAIGN_REVISION,
            CAMPAIGN_FINGERPRINT,
            PROGRESS_EPOCH,
            DIMENSION_ID,
            ARENA_X,
            ARENA_Y,
            ARENA_Z,
            PHASE,
            LIFECYCLE,
            PARTICIPANT_COUNT,
            HEALTH_SCALE,
            DAMAGE_SCALE,
            BROKEN_POINT_MASK,
            ATTACK_CYCLE,
            ATTACK_ID,
            ATTACK_TICK,
            PARENT_HEALTH,
            PROW_HEALTH,
            PORT_HEALTH,
            STARBOARD_HEALTH,
            LAST_OBSERVED_TICK);

    public NinthFormEncounter {
        requireUuid(encounterId, "encounterId");
        requireUuid(targetId, "targetId");
        requireUuid(entityId, "entityId");
        requireUuid(phaseFactId, "phaseFactId");
        requireUuid(defeatFactId, "defeatFactId");
        if (Set.of(encounterId, targetId, entityId, phaseFactId, defeatFactId).size() != 5) {
            throw new IllegalArgumentException("encounter UUID roles must be distinct");
        }
        if (generation < 0) {
            throw new IllegalArgumentException("generation cannot be negative");
        }
        Objects.requireNonNull(campaignId, "campaignId");
        if (campaignRevision < 1 || campaignRevision > 1_000_000) {
            throw new IllegalArgumentException("campaignRevision is outside the supported range");
        }
        Objects.requireNonNull(campaignFingerprint, "campaignFingerprint");
        if (!FINGERPRINT.matcher(campaignFingerprint).matches()) {
            throw new IllegalArgumentException("campaignFingerprint must be a SHA-256 hex value");
        }
        if (progressEpoch < 0L) {
            throw new IllegalArgumentException("progressEpoch cannot be negative");
        }
        validateDimension(dimensionId);
        if (Math.abs((long) arenaX) > 30_000_000L || Math.abs((long) arenaZ) > 30_000_000L) {
            throw new IllegalArgumentException("arena center is outside the world border");
        }
        if (arenaY < -2048 || arenaY > 2048) {
            throw new IllegalArgumentException("arenaY is outside the supported world range");
        }
        Objects.requireNonNull(phase, "phase");
        if (phase.terminal()) {
            throw new IllegalArgumentException("terminal encounters are represented by barriers");
        }
        Objects.requireNonNull(lifecycle, "lifecycle");
        if (participantCount < 1 || participantCount > 8) {
            throw new IllegalArgumentException("participantCount must be in [1, 8]");
        }
        requireScale(healthScale, "healthScale");
        requireScale(damageScale, "damageScale");
        Objects.requireNonNull(combatState, "combatState");
        Objects.requireNonNull(vitalState, "vitalState");
        vitalState.validateMask(combatState.brokenPointMask());
        if (lastObservedGameTick < 0L) {
            throw new IllegalArgumentException("lastObservedGameTick cannot be negative");
        }
    }

    public NinthFormIdentity identity() {
        return new NinthFormIdentity(encounterId, targetId, generation, rehearsal);
    }

    public NinthFormEntityGateway.SpawnRequest spawnRequest() {
        return new NinthFormEntityGateway.SpawnRequest(
                identity(),
                entityId,
                dimensionId,
                arenaX + 0.5D,
                arenaY,
                arenaZ + 0.5D,
                phase,
                participantCount,
                healthScale,
                damageScale,
                combatState,
                vitalState);
    }

    public NinthFormEncounter withLifecycle(Lifecycle next) {
        return copy(entityId, generation, phase, next, combatState, vitalState, lastObservedGameTick);
    }

    public NinthFormEncounter advanceTo(NinthFormPhase next) {
        if (!phase.canAdvanceTo(next) || next.terminal()) {
            throw new IllegalArgumentException("illegal active phase transition " + phase + " -> " + next);
        }
        return copy(entityId, generation, next, lifecycle, combatState, vitalState, lastObservedGameTick);
    }

    /**
     * Canonical checkpoint written atomically with the first-phase proof.
     * Sampling may lag combat by up to one scheduler interval, so weak-point
     * completion cannot be inferred from the last observation.
     */
    public NinthFormEncounter completeFirstPhase() {
        if (phase != NinthFormPhase.FIRST) {
            throw new IllegalStateException("only FIRST may complete its checkpoint");
        }
        NinthFormCombatSnapshot.CombatState completedCombat =
                new NinthFormCombatSnapshot.CombatState(
                        0b111, combatState.attackCycle(), "idle", 0);
        NinthFormCombatSnapshot.VitalState completedVital =
                new NinthFormCombatSnapshot.VitalState(
                        vitalState.parentHealthFraction(), 0.0D, 0.0D, 0.0D);
        return copy(
                entityId,
                generation,
                NinthFormPhase.INTERLUDE,
                lifecycle,
                completedCombat,
                completedVital,
                lastObservedGameTick);
    }

    public NinthFormEncounter withSnapshot(NinthFormCombatSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!identity().equals(snapshot.identity())
                || !entityId.equals(snapshot.entityId())
                || phase != snapshot.phase()
                || !dimensionId.equals(snapshot.dimensionId())
                || participantCount != snapshot.participantCount()) {
            throw new IllegalArgumentException("snapshot does not match durable encounter authority");
        }
        double dx = snapshot.x() - (arenaX + 0.5D);
        double dz = snapshot.z() - (arenaZ + 0.5D);
        if (dx * dx + dz * dz > (double) ARENA_RADIUS * ARENA_RADIUS) {
            throw new IllegalArgumentException("snapshot entity escaped the virtual arena");
        }
        if (snapshot.observedGameTick() < lastObservedGameTick) {
            throw new IllegalArgumentException("snapshot game tick moved backwards");
        }
        return copy(
                entityId,
                generation,
                phase,
                lifecycle,
                snapshot.combatState(),
                snapshot.vitalState(),
                snapshot.observedGameTick());
    }

    public NinthFormEncounter rotateGeneration(UUID replacementEntityId) {
        if (generation == Integer.MAX_VALUE) {
            throw new IllegalStateException("generation is exhausted");
        }
        requireUuid(replacementEntityId, "replacementEntityId");
        if (Set.of(encounterId, targetId, replacementEntityId, phaseFactId, defeatFactId).size() != 5) {
            throw new IllegalArgumentException("replacement entity UUID aliases durable authority");
        }
        return copy(
                replacementEntityId,
                generation + 1,
                phase,
                Lifecycle.PREPARED,
                combatState,
                vitalState,
                lastObservedGameTick);
    }

    private NinthFormEncounter copy(
            UUID nextEntityId,
            int nextGeneration,
            NinthFormPhase nextPhase,
            Lifecycle nextLifecycle,
            NinthFormCombatSnapshot.CombatState nextCombatState,
            NinthFormCombatSnapshot.VitalState nextVitalState,
            long nextObservedTick) {
        return new NinthFormEncounter(
                encounterId,
                targetId,
                nextEntityId,
                phaseFactId,
                defeatFactId,
                nextGeneration,
                rehearsal,
                campaignId,
                campaignRevision,
                campaignFingerprint,
                progressEpoch,
                dimensionId,
                arenaX,
                arenaY,
                arenaZ,
                nextPhase,
                nextLifecycle,
                participantCount,
                healthScale,
                damageScale,
                nextCombatState,
                nextVitalState,
                nextObservedTick);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(ENCOUNTER_ID, encounterId);
        tag.putUUID(TARGET_ID, targetId);
        tag.putUUID(ENTITY_ID, entityId);
        tag.putUUID(PHASE_FACT_ID, phaseFactId);
        tag.putUUID(DEFEAT_FACT_ID, defeatFactId);
        tag.putInt(GENERATION, generation);
        tag.putBoolean(REHEARSAL, rehearsal);
        tag.putString(CAMPAIGN_ID, campaignId.toString());
        tag.putInt(CAMPAIGN_REVISION, campaignRevision);
        tag.putString(CAMPAIGN_FINGERPRINT, campaignFingerprint);
        tag.putLong(PROGRESS_EPOCH, progressEpoch);
        tag.putString(DIMENSION_ID, dimensionId);
        tag.putInt(ARENA_X, arenaX);
        tag.putInt(ARENA_Y, arenaY);
        tag.putInt(ARENA_Z, arenaZ);
        tag.putString(PHASE, phase.name().toLowerCase(java.util.Locale.ROOT));
        tag.putString(LIFECYCLE, lifecycle.id());
        tag.putInt(PARTICIPANT_COUNT, participantCount);
        tag.putDouble(HEALTH_SCALE, healthScale);
        tag.putDouble(DAMAGE_SCALE, damageScale);
        tag.putInt(BROKEN_POINT_MASK, combatState.brokenPointMask());
        tag.putLong(ATTACK_CYCLE, combatState.attackCycle());
        tag.putString(ATTACK_ID, combatState.attackId());
        tag.putInt(ATTACK_TICK, combatState.attackTick());
        tag.putDouble(PARENT_HEALTH, vitalState.parentHealthFraction());
        tag.putDouble(PROW_HEALTH, vitalState.prowHealthFraction());
        tag.putDouble(PORT_HEALTH, vitalState.portHealthFraction());
        tag.putDouble(STARBOARD_HEALTH, vitalState.starboardHealthFraction());
        tag.putLong(LAST_OBSERVED_TICK, lastObservedGameTick);
        return tag;
    }

    static NinthFormEncounter load(CompoundTag tag) {
        NinthFormBarrier.requireExactFields(tag, FIELDS, "active encounter");
        for (String key : Set.of(
                ENCOUNTER_ID, TARGET_ID, ENTITY_ID, PHASE_FACT_ID, DEFEAT_FACT_ID)) {
            NinthFormBarrier.requireUuidField(tag, key);
        }
        requireTypes(tag);
        ResourceLocation campaignId = ResourceLocation.tryParse(tag.getString(CAMPAIGN_ID));
        if (campaignId == null || !campaignId.toString().equals(tag.getString(CAMPAIGN_ID))) {
            throw new IllegalArgumentException("active CampaignId is invalid");
        }
        NinthFormPhase phase = phaseFromId(tag.getString(PHASE));
        Lifecycle lifecycle = Lifecycle.fromId(tag.getString(LIFECYCLE));
        if (phase == null || lifecycle == null) {
            throw new IllegalArgumentException("active phase or lifecycle is invalid");
        }
        String attackId = tag.getString(ATTACK_ID);
        if (!ATTACK_ID_PATTERN.matcher(attackId).matches()) {
            throw new IllegalArgumentException("active AttackId is invalid");
        }
        return new NinthFormEncounter(
                tag.getUUID(ENCOUNTER_ID),
                tag.getUUID(TARGET_ID),
                tag.getUUID(ENTITY_ID),
                tag.getUUID(PHASE_FACT_ID),
                tag.getUUID(DEFEAT_FACT_ID),
                tag.getInt(GENERATION),
                tag.getBoolean(REHEARSAL),
                campaignId,
                tag.getInt(CAMPAIGN_REVISION),
                tag.getString(CAMPAIGN_FINGERPRINT),
                tag.getLong(PROGRESS_EPOCH),
                tag.getString(DIMENSION_ID),
                tag.getInt(ARENA_X),
                tag.getInt(ARENA_Y),
                tag.getInt(ARENA_Z),
                phase,
                lifecycle,
                tag.getInt(PARTICIPANT_COUNT),
                tag.getDouble(HEALTH_SCALE),
                tag.getDouble(DAMAGE_SCALE),
                new NinthFormCombatSnapshot.CombatState(
                        tag.getInt(BROKEN_POINT_MASK),
                        tag.getLong(ATTACK_CYCLE),
                        attackId,
                        tag.getInt(ATTACK_TICK)),
                new NinthFormCombatSnapshot.VitalState(
                        tag.getDouble(PARENT_HEALTH),
                        tag.getDouble(PROW_HEALTH),
                        tag.getDouble(PORT_HEALTH),
                        tag.getDouble(STARBOARD_HEALTH)),
                tag.getLong(LAST_OBSERVED_TICK));
    }

    private static void requireTypes(CompoundTag tag) {
        NinthFormBarrier.requireType(tag, GENERATION, Tag.TAG_INT);
        NinthFormBarrier.requireType(tag, REHEARSAL, Tag.TAG_BYTE);
        NinthFormBarrier.requireType(tag, CAMPAIGN_ID, Tag.TAG_STRING);
        NinthFormBarrier.requireType(tag, CAMPAIGN_REVISION, Tag.TAG_INT);
        NinthFormBarrier.requireType(tag, CAMPAIGN_FINGERPRINT, Tag.TAG_STRING);
        NinthFormBarrier.requireType(tag, PROGRESS_EPOCH, Tag.TAG_LONG);
        NinthFormBarrier.requireType(tag, DIMENSION_ID, Tag.TAG_STRING);
        NinthFormBarrier.requireType(tag, ARENA_X, Tag.TAG_INT);
        NinthFormBarrier.requireType(tag, ARENA_Y, Tag.TAG_INT);
        NinthFormBarrier.requireType(tag, ARENA_Z, Tag.TAG_INT);
        NinthFormBarrier.requireType(tag, PHASE, Tag.TAG_STRING);
        NinthFormBarrier.requireType(tag, LIFECYCLE, Tag.TAG_STRING);
        NinthFormBarrier.requireType(tag, PARTICIPANT_COUNT, Tag.TAG_INT);
        NinthFormBarrier.requireType(tag, HEALTH_SCALE, Tag.TAG_DOUBLE);
        NinthFormBarrier.requireType(tag, DAMAGE_SCALE, Tag.TAG_DOUBLE);
        NinthFormBarrier.requireType(tag, BROKEN_POINT_MASK, Tag.TAG_INT);
        NinthFormBarrier.requireType(tag, ATTACK_CYCLE, Tag.TAG_LONG);
        NinthFormBarrier.requireType(tag, ATTACK_ID, Tag.TAG_STRING);
        NinthFormBarrier.requireType(tag, ATTACK_TICK, Tag.TAG_INT);
        NinthFormBarrier.requireType(tag, PARENT_HEALTH, Tag.TAG_DOUBLE);
        NinthFormBarrier.requireType(tag, PROW_HEALTH, Tag.TAG_DOUBLE);
        NinthFormBarrier.requireType(tag, PORT_HEALTH, Tag.TAG_DOUBLE);
        NinthFormBarrier.requireType(tag, STARBOARD_HEALTH, Tag.TAG_DOUBLE);
        NinthFormBarrier.requireType(tag, LAST_OBSERVED_TICK, Tag.TAG_LONG);
    }

    private static NinthFormPhase phaseFromId(String id) {
        for (NinthFormPhase value : NinthFormPhase.values()) {
            if (value.name().toLowerCase(java.util.Locale.ROOT).equals(id)) {
                return value;
            }
        }
        return null;
    }

    private static void validateDimension(String dimensionId) {
        Objects.requireNonNull(dimensionId, "dimensionId");
        if (dimensionId.isBlank() || dimensionId.length() > MAX_DIMENSION_ID_LENGTH) {
            throw new IllegalArgumentException("dimensionId has an invalid length");
        }
        ResourceLocation parsed = ResourceLocation.tryParse(dimensionId);
        if (parsed == null || !parsed.toString().equals(dimensionId)) {
            throw new IllegalArgumentException("dimensionId is invalid");
        }
    }

    private static void requireScale(double value, String name) {
        if (!Double.isFinite(value) || value < 0.25D || value > 8.0D) {
            throw new IllegalArgumentException(name + " must be finite and in [0.25, 8]");
        }
    }

    private static void requireUuid(UUID value, String name) {
        Objects.requireNonNull(value, name);
        if (NIL_UUID.equals(value)) {
            throw new IllegalArgumentException(name + " cannot be nil");
        }
    }

    public enum Lifecycle {
        PREPARED("prepared"),
        ACTIVE("active"),
        SUSPENDED("suspended");

        private final String id;

        Lifecycle(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        static Lifecycle fromId(String id) {
            for (Lifecycle value : values()) {
                if (value.id.equals(id)) {
                    return value;
                }
            }
            return null;
        }
    }
}
