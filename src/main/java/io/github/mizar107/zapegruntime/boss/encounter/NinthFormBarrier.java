package io.github.mizar107.zapegruntime.boss.encounter;

import io.github.mizar107.zapegruntime.story.StoryFactType;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Immutable, replayable proof created before an encounter transition is exposed. */
public record NinthFormBarrier(
        UUID factId,
        UUID encounterId,
        UUID targetId,
        UUID entityId,
        int generation,
        ResourceLocation campaignId,
        int campaignRevision,
        String campaignFingerprint,
        long progressEpoch,
        Kind kind) {

    static final String FACT_ID = "FactId";
    static final String ENCOUNTER_ID = "EncounterId";
    static final String TARGET_ID = "TargetId";
    static final String ENTITY_ID = "EntityId";
    static final String GENERATION = "Generation";
    static final String CAMPAIGN_ID = "CampaignId";
    static final String CAMPAIGN_REVISION = "CampaignRevision";
    static final String CAMPAIGN_FINGERPRINT = "CampaignFingerprint";
    static final String PROGRESS_EPOCH = "ProgressEpoch";
    static final String KIND = "Kind";
    static final Set<String> FIELDS = Set.of(
            FACT_ID,
            ENCOUNTER_ID,
            TARGET_ID,
            ENTITY_ID,
            GENERATION,
            CAMPAIGN_ID,
            CAMPAIGN_REVISION,
            CAMPAIGN_FINGERPRINT,
            PROGRESS_EPOCH,
            KIND);

    private static final UUID NIL_UUID = new UUID(0L, 0L);
    private static final Pattern FINGERPRINT = Pattern.compile("[0-9a-f]{64}");

    public NinthFormBarrier {
        requireUuid(factId, "factId");
        requireUuid(encounterId, "encounterId");
        requireUuid(targetId, "targetId");
        requireUuid(entityId, "entityId");
        if (Set.of(factId, encounterId, targetId, entityId).size() != 4) {
            throw new IllegalArgumentException("barrier UUID roles must be distinct");
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
        Objects.requireNonNull(kind, "kind");
    }

    static NinthFormBarrier fromEncounter(NinthFormEncounter encounter, Kind kind) {
        Objects.requireNonNull(encounter, "encounter");
        Objects.requireNonNull(kind, "kind");
        UUID factId = kind == Kind.PHASE_ONE_COMPLETED
                ? encounter.phaseFactId()
                : encounter.defeatFactId();
        return new NinthFormBarrier(
                factId,
                encounter.encounterId(),
                encounter.targetId(),
                encounter.entityId(),
                encounter.generation(),
                encounter.campaignId(),
                encounter.campaignRevision(),
                encounter.campaignFingerprint(),
                encounter.progressEpoch(),
                kind);
    }

    boolean matches(NinthFormEncounter encounter, Kind expectedKind) {
        return kind == expectedKind
                && encounterId.equals(encounter.encounterId())
                && targetId.equals(encounter.targetId())
                && entityId.equals(encounter.entityId())
                && generation == encounter.generation()
                && campaignId.equals(encounter.campaignId())
                && campaignRevision == encounter.campaignRevision()
                && campaignFingerprint.equals(encounter.campaignFingerprint())
                && progressEpoch == encounter.progressEpoch()
                && factId.equals(expectedKind == Kind.PHASE_ONE_COMPLETED
                        ? encounter.phaseFactId()
                        : encounter.defeatFactId());
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(FACT_ID, factId);
        tag.putUUID(ENCOUNTER_ID, encounterId);
        tag.putUUID(TARGET_ID, targetId);
        tag.putUUID(ENTITY_ID, entityId);
        tag.putInt(GENERATION, generation);
        tag.putString(CAMPAIGN_ID, campaignId.toString());
        tag.putInt(CAMPAIGN_REVISION, campaignRevision);
        tag.putString(CAMPAIGN_FINGERPRINT, campaignFingerprint);
        tag.putLong(PROGRESS_EPOCH, progressEpoch);
        tag.putString(KIND, kind.id());
        return tag;
    }

    static NinthFormBarrier load(CompoundTag tag) {
        requireExactFields(tag, FIELDS, "barrier");
        requireUuidField(tag, FACT_ID);
        requireUuidField(tag, ENCOUNTER_ID);
        requireUuidField(tag, TARGET_ID);
        requireUuidField(tag, ENTITY_ID);
        requireType(tag, GENERATION, Tag.TAG_INT);
        requireType(tag, CAMPAIGN_ID, Tag.TAG_STRING);
        requireType(tag, CAMPAIGN_REVISION, Tag.TAG_INT);
        requireType(tag, CAMPAIGN_FINGERPRINT, Tag.TAG_STRING);
        requireType(tag, PROGRESS_EPOCH, Tag.TAG_LONG);
        requireType(tag, KIND, Tag.TAG_STRING);
        ResourceLocation campaignId = ResourceLocation.tryParse(tag.getString(CAMPAIGN_ID));
        if (campaignId == null || !campaignId.toString().equals(tag.getString(CAMPAIGN_ID))) {
            throw new IllegalArgumentException("barrier CampaignId is invalid");
        }
        Kind kind = Kind.fromId(tag.getString(KIND));
        if (kind == null) {
            throw new IllegalArgumentException("barrier Kind is invalid");
        }
        return new NinthFormBarrier(
                tag.getUUID(FACT_ID),
                tag.getUUID(ENCOUNTER_ID),
                tag.getUUID(TARGET_ID),
                tag.getUUID(ENTITY_ID),
                tag.getInt(GENERATION),
                campaignId,
                tag.getInt(CAMPAIGN_REVISION),
                tag.getString(CAMPAIGN_FINGERPRINT),
                tag.getLong(PROGRESS_EPOCH),
                kind);
    }

    static void requireExactFields(CompoundTag tag, Set<String> expected, String description) {
        Set<String> actual = new HashSet<>(tag.getAllKeys());
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException(description + " has missing or unknown fields");
        }
    }

    static void requireType(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) {
            throw new IllegalArgumentException(key + " has the wrong NBT type");
        }
    }

    static void requireUuidField(CompoundTag tag, String key) {
        if (!tag.hasUUID(key)) {
            throw new IllegalArgumentException(key + " is not a UUID");
        }
    }

    private static void requireUuid(UUID id, String name) {
        Objects.requireNonNull(id, name);
        if (NIL_UUID.equals(id)) {
            throw new IllegalArgumentException(name + " cannot be nil");
        }
    }

    public enum Kind {
        PHASE_ONE_COMPLETED(
                "phase_one_completed",
                StoryFactType.BOSS_PHASE_COMPLETED,
                "zapeg_runtime:ninth_form_phase_01"),
        DEFEATED(
                "defeated",
                StoryFactType.BOSS_DEFEATED,
                "zapeg_runtime:ninth_form");

        private final String id;
        private final StoryFactType storyType;
        private final ResourceLocation storySubject;

        Kind(String id, StoryFactType storyType, String storySubject) {
            this.id = id;
            this.storyType = storyType;
            this.storySubject = Objects.requireNonNull(ResourceLocation.tryParse(storySubject));
        }

        public String id() {
            return id;
        }

        public StoryFactType storyType() {
            return storyType;
        }

        public ResourceLocation storySubject() {
            return storySubject;
        }

        static Kind fromId(String id) {
            for (Kind value : values()) {
                if (value.id.equals(id)) {
                    return value;
                }
            }
            return null;
        }
    }
}
