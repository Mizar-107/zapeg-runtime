package io.github.mizar107.zapegruntime.director;

import io.github.mizar107.zapegruntime.story.StoryCampaignDefinition;
import io.github.mizar107.zapegruntime.story.StoryFactType;
import io.github.mizar107.zapegruntime.story.StoryNode;
import io.github.mizar107.zapegruntime.story.StoryWorldData;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Strict, bounded restart state for one outstanding Director scene per target. */
public final class HeraldorDirectorData extends SavedData {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MAX_TARGETS = 2_048;
    public static final int MAX_ATTEMPTS = 1_000_000;

    private static final String DATA_NAME = "zapeg_runtime_heraldor_director";
    private static final String SCHEMA = "SchemaVersion";
    private static final String RECORDS = "Records";
    private static final String TARGET_ID = "TargetId";
    private static final String EVENT_ID = "EventId";
    private static final String CAMPAIGN_ID = "CampaignId";
    private static final String CAMPAIGN_REVISION = "CampaignRevision";
    private static final String CAMPAIGN_FINGERPRINT = "CampaignFingerprint";
    private static final String PROGRESS_EPOCH = "ProgressEpoch";
    private static final String NODE_ID = "NodeId";
    private static final String FACT_TYPE = "FactType";
    private static final String SUBJECT = "Subject";
    private static final String BINDING_FINGERPRINT = "BindingFingerprint";
    private static final String PRESENTATION_VARIANT = "PresentationVariant";
    private static final String ATTEMPT = "Attempt";
    private static final String STATE = "State";
    private static final String PROOF = "Proof";
    private static final String RETRY_AFTER = "RetryAfterGameTime";
    private static final String LAST_OUTCOME = "LastOutcome";
    private static final Set<String> ROOT_FIELDS = Set.of(SCHEMA, RECORDS);
    private static final Set<String> RECORD_FIELDS = Set.of(
            TARGET_ID,
            EVENT_ID,
            CAMPAIGN_ID,
            CAMPAIGN_REVISION,
            CAMPAIGN_FINGERPRINT,
            PROGRESS_EPOCH,
            NODE_ID,
            FACT_TYPE,
            SUBJECT,
            BINDING_FINGERPRINT,
            PRESENTATION_VARIANT,
            ATTEMPT,
            STATE,
            PROOF,
            RETRY_AFTER,
            LAST_OUTCOME);
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern NODE = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");
    private static final Pattern OUTCOME = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private final Map<UUID, DispatchRecord> records = new HashMap<>();
    private final int loadedVersion;
    private final DataHealth health;
    private final String detail;
    private final CompoundTag preservedRoot;

    public HeraldorDirectorData() {
        this(CURRENT_SCHEMA_VERSION, DataHealth.OK, "ok", null);
    }

    private HeraldorDirectorData(
            int loadedVersion,
            DataHealth health,
            String detail,
            CompoundTag preservedRoot) {
        this.loadedVersion = loadedVersion;
        this.health = Objects.requireNonNull(health, "health");
        this.detail = Objects.requireNonNull(detail, "detail");
        this.preservedRoot = preservedRoot;
    }

    public static HeraldorDirectorData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                HeraldorDirectorData::load, HeraldorDirectorData::new, DATA_NAME);
    }

    public static HeraldorDirectorData load(CompoundTag root) {
        Objects.requireNonNull(root, "root");
        if (!root.contains(SCHEMA)) {
            if (root.getAllKeys().isEmpty()) {
                return new HeraldorDirectorData();
            }
            return unavailable(0, DataHealth.CORRUPT, "unversioned Director data", root);
        }
        if (!root.contains(SCHEMA, Tag.TAG_INT)) {
            return unavailable(-1, DataHealth.CORRUPT, "SchemaVersion must be an integer", root);
        }
        int schema = root.getInt(SCHEMA);
        if (schema != CURRENT_SCHEMA_VERSION) {
            return unavailable(
                    schema,
                    DataHealth.UNSUPPORTED,
                    "unsupported Director schema " + schema,
                    root);
        }
        try {
            requireExactFields(root, ROOT_FIELDS, "Director root");
            requireType(root, RECORDS, Tag.TAG_LIST, "Director root");
            Tag rawRecords = root.get(RECORDS);
            if (!(rawRecords instanceof ListTag encoded)) {
                throw format("Records must be a list");
            }
            if (!encoded.isEmpty() && encoded.getElementType() != Tag.TAG_COMPOUND) {
                throw format("Records must be a compound list");
            }
            if (encoded.size() > MAX_TARGETS) {
                throw format("Director target count exceeds " + MAX_TARGETS);
            }
            HeraldorDirectorData data = new HeraldorDirectorData();
            Set<UUID> eventIds = new HashSet<>();
            for (int index = 0; index < encoded.size(); index++) {
                DispatchRecord record = readRecord(encoded.getCompound(index), index);
                if (data.records.put(record.targetId(), record) != null) {
                    throw format("duplicate Director target at index " + index);
                }
                if (!eventIds.add(record.eventId())) {
                    throw format("duplicate Director event at index " + index);
                }
            }
            return data;
        } catch (DataFormatException invalid) {
            return unavailable(schema, DataHealth.CORRUPT, invalid.getMessage(), root);
        }
    }

    public SchemaStatus schemaStatus() {
        return new SchemaStatus(
                loadedVersion, CURRENT_SCHEMA_VERSION, health, detail, writable());
    }

    public Optional<DispatchRecord> record(UUID targetId) {
        Objects.requireNonNull(targetId, "targetId");
        return writable() ? Optional.ofNullable(records.get(targetId)) : Optional.empty();
    }

    public PlanResult plan(
            StoryCampaignDefinition campaign,
            StoryWorldData.PlayerSnapshot story,
            StoryNode node,
            DirectorSceneBinding binding,
            long now,
            boolean exactSceneActive) {
        Objects.requireNonNull(campaign, "campaign");
        Objects.requireNonNull(story, "story");
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(binding, "binding");
        if (!writable()) {
            return new PlanResult(PlanStatus.DATA_UNAVAILABLE, null, detail);
        }
        if (now < 0L
                || !story.campaignId().equals(campaign.id())
                || story.campaignRevision() != campaign.revision()
                || !story.definitionFingerprint().equals(campaign.fingerprint())
                || !story.currentNodeId().equals(node.id())
                || node.terminal()
                || !node.advanceOn().equals(binding.trigger())) {
            return new PlanResult(
                    PlanStatus.INVALID_STORY_STATE, null, "story and Director binding do not agree");
        }

        DispatchRecord current = records.get(story.playerId());
        if (current != null && current.state() == DispatchState.PROVEN) {
            return new PlanResult(PlanStatus.PROOF_READY, current, "durable proof awaits story");
        }
        if (current == null) {
            if (records.size() >= MAX_TARGETS) {
                return new PlanResult(
                        PlanStatus.CAPACITY_EXHAUSTED,
                        null,
                        "Director target capacity is exhausted");
            }
            current = fresh(campaign, story, node, binding, 0, now, "prepared");
            records.put(story.playerId(), current);
            setDirty();
            return new PlanResult(PlanStatus.DISPATCH, current, "new stable scene prepared");
        }

        if (!current.matches(campaign, story, node, binding)) {
            if (current.state() == DispatchState.BLOCKED
                    && current.matchesStoryEnvelope(campaign, story, node)) {
                return new PlanResult(
                        PlanStatus.BLOCKED,
                        current,
                        "blocked proof identity requires explicit story recovery");
            }
            if (current.state() == DispatchState.COOLDOWN && now < current.retryAfterGameTime()) {
                return new PlanResult(
                        PlanStatus.WAITING, current, "previous scene cooldown is active");
            }
            current = fresh(campaign, story, node, binding, 0, now, "story_changed");
            records.put(story.playerId(), current);
            setDirty();
            return new PlanResult(PlanStatus.DISPATCH, current, "new story scene prepared");
        }

        return switch (current.state()) {
            case PREPARED -> now >= current.retryAfterGameTime()
                    ? new PlanResult(PlanStatus.DISPATCH, current, "prepared scene is due")
                    : new PlanResult(PlanStatus.WAITING, current, "dispatch backoff is active");
            case AWAITING -> {
                if (exactSceneActive || now < current.retryAfterGameTime()) {
                    yield new PlanResult(
                            PlanStatus.WAITING, current, "active scene proof is pending");
                }
                DispatchRecord rotated = rotate(current, now, "unproved_attempt_expired");
                records.put(current.targetId(), rotated);
                setDirty();
                yield rotated.state() == DispatchState.BLOCKED
                        ? new PlanResult(PlanStatus.BLOCKED, rotated, "attempt capacity exhausted")
                        : new PlanResult(PlanStatus.DISPATCH, rotated, "expired attempt rotated");
            }
            case PROVEN -> new PlanResult(
                    PlanStatus.PROOF_READY, current, "durable proof awaits story");
            case COOLDOWN -> {
                if (now < current.retryAfterGameTime()) {
                    yield new PlanResult(
                            PlanStatus.WAITING, current, "Director cooldown is active");
                }
                DispatchRecord rotated = rotate(current, now, "cooldown_without_transition");
                records.put(current.targetId(), rotated);
                setDirty();
                yield rotated.state() == DispatchState.BLOCKED
                        ? new PlanResult(PlanStatus.BLOCKED, rotated, "attempt capacity exhausted")
                        : new PlanResult(PlanStatus.DISPATCH, rotated, "cooldown retry prepared");
            }
            case BLOCKED -> new PlanResult(
                    PlanStatus.BLOCKED, current, "Director record is fail-closed");
        };
    }

    public boolean markAwaiting(DirectorSceneIdentity identity, long retryAfter) {
        return replaceExact(
                identity,
                current -> retryAfter < 0L
                        ? current
                        : current.with(
                                DispatchState.AWAITING,
                                DirectorPresentationPolicy.Proof.NONE,
                                retryAfter,
                                "dispatched"));
    }

    public boolean markProven(
            DirectorSceneIdentity identity,
            DirectorPresentationPolicy.Proof proof,
            long now) {
        Objects.requireNonNull(proof, "proof");
        if (proof == DirectorPresentationPolicy.Proof.NONE || now < 0L) {
            return false;
        }
        return replaceExact(
                identity,
                current -> current.state() != DispatchState.AWAITING
                        ? current
                        : current.with(DispatchState.PROVEN, proof, now, "proof_"
                                + proof.name().toLowerCase(java.util.Locale.ROOT)));
    }

    public boolean markFailure(
            DirectorSceneIdentity identity, long retryAfter, String outcome) {
        if (retryAfter < 0L || !validOutcome(outcome)) {
            return false;
        }
        return replaceExact(identity, current -> rotate(current, retryAfter, outcome));
    }

    /** Keeps an unconsumed stable event id while applying bounded dispatch backoff. */
    public boolean markBackoff(
            DirectorSceneIdentity identity, long retryAfter, String outcome) {
        if (retryAfter < 0L || !validOutcome(outcome)) {
            return false;
        }
        return replaceExact(
                identity,
                current -> current.with(
                        DispatchState.PREPARED,
                        DirectorPresentationPolicy.Proof.NONE,
                        retryAfter,
                        outcome));
    }

    public boolean markCooldown(
            DirectorSceneIdentity identity, long retryAfter, String outcome) {
        if (retryAfter < 0L || !validOutcome(outcome)) {
            return false;
        }
        return replaceExact(
                identity,
                current -> current.with(
                        DispatchState.COOLDOWN,
                        current.proof(),
                        retryAfter,
                        outcome));
    }

    public boolean markBlocked(DirectorSceneIdentity identity, String outcome) {
        if (!validOutcome(outcome)) {
            return false;
        }
        return replaceExact(
                identity,
                current -> current.with(
                        DispatchState.BLOCKED,
                        DirectorPresentationPolicy.Proof.NONE,
                        current.retryAfterGameTime(),
                        outcome));
    }

    private boolean replaceExact(
            DirectorSceneIdentity identity,
            java.util.function.UnaryOperator<DispatchRecord> update) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(update, "update");
        if (!writable()) {
            return false;
        }
        DispatchRecord current = records.get(identity.targetId());
        if (current == null || !current.identity().equals(identity)) {
            return false;
        }
        DispatchRecord replacement = Objects.requireNonNull(update.apply(current), "replacement");
        if (replacement.equals(current)) {
            return false;
        }
        records.put(identity.targetId(), replacement);
        setDirty();
        return true;
    }

    private static DispatchRecord fresh(
            StoryCampaignDefinition campaign,
            StoryWorldData.PlayerSnapshot story,
            StoryNode node,
            DirectorSceneBinding binding,
            int attempt,
            long retryAfter,
            String outcome) {
        UUID eventId = stableEventId(campaign, story, node, binding, attempt);
        return new DispatchRecord(
                story.playerId(),
                eventId,
                campaign.id(),
                campaign.revision(),
                campaign.fingerprint(),
                story.progressEpoch(),
                node.id(),
                binding.factType(),
                binding.subject(),
                binding.fingerprint(),
                binding.presentationVariant(),
                attempt,
                DispatchState.PREPARED,
                DirectorPresentationPolicy.Proof.NONE,
                retryAfter,
                outcome);
    }

    private static DispatchRecord rotate(
            DispatchRecord current, long retryAfter, String outcome) {
        if (current.attempt() >= MAX_ATTEMPTS) {
            return current.with(
                    DispatchState.BLOCKED,
                    DirectorPresentationPolicy.Proof.NONE,
                    retryAfter,
                    "attempt_capacity_exhausted");
        }
        int nextAttempt = current.attempt() + 1;
        UUID eventId = stableEventId(current, nextAttempt);
        return new DispatchRecord(
                current.targetId(),
                eventId,
                current.campaignId(),
                current.campaignRevision(),
                current.campaignFingerprint(),
                current.progressEpoch(),
                current.nodeId(),
                current.factType(),
                current.subject(),
                current.bindingFingerprint(),
                current.presentationVariant(),
                nextAttempt,
                DispatchState.PREPARED,
                DirectorPresentationPolicy.Proof.NONE,
                retryAfter,
                outcome);
    }

    static UUID stableEventId(
            StoryCampaignDefinition campaign,
            StoryWorldData.PlayerSnapshot story,
            StoryNode node,
            DirectorSceneBinding binding,
            int attempt) {
        String key = "zapeg-runtime-director-v1|" + story.playerId() + '|'
                + campaign.id() + '|' + campaign.revision() + '|'
                + campaign.fingerprint() + '|' + story.progressEpoch() + '|'
                + node.id() + '|' + binding.factType().serializedName() + '|'
                + binding.subject() + '|' + binding.fingerprint() + '|' + attempt;
        return safeNameUuid(key, story.playerId());
    }

    private static UUID stableEventId(DispatchRecord current, int attempt) {
        String key = "zapeg-runtime-director-v1|" + current.targetId() + '|'
                + current.campaignId() + '|' + current.campaignRevision() + '|'
                + current.campaignFingerprint() + '|' + current.progressEpoch() + '|'
                + current.nodeId() + '|' + current.factType().serializedName() + '|'
                + current.subject() + '|' + current.bindingFingerprint() + '|' + attempt;
        return safeNameUuid(key, current.targetId());
    }

    private static UUID safeNameUuid(String key, UUID targetId) {
        UUID result = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
        if (NIL_UUID.equals(result) || targetId.equals(result)) {
            result = UUID.nameUUIDFromBytes((key + "|identity_guard")
                    .getBytes(StandardCharsets.UTF_8));
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        if (preservedRoot != null) {
            return preservedRoot.copy();
        }
        root.putInt(SCHEMA, CURRENT_SCHEMA_VERSION);
        ListTag encoded = new ListTag();
        records.values().stream()
                .sorted(Comparator.comparing(record -> record.targetId().toString()))
                .forEach(record -> encoded.add(writeRecord(record)));
        root.put(RECORDS, encoded);
        return root;
    }

    private static DispatchRecord readRecord(CompoundTag tag, int index) {
        String context = "record " + index;
        requireExactFields(tag, RECORD_FIELDS, context);
        requireUuid(tag, TARGET_ID, context);
        requireUuid(tag, EVENT_ID, context);
        ResourceLocation campaignId = requireResource(tag, CAMPAIGN_ID, context);
        requireType(tag, CAMPAIGN_REVISION, Tag.TAG_INT, context);
        String campaignFingerprint = requireFingerprint(tag, CAMPAIGN_FINGERPRINT, context);
        requireType(tag, PROGRESS_EPOCH, Tag.TAG_LONG, context);
        String nodeId = requireString(tag, NODE_ID, 64, context);
        requireType(tag, PRESENTATION_VARIANT, Tag.TAG_INT, context);
        requireType(tag, ATTEMPT, Tag.TAG_INT, context);
        requireType(tag, RETRY_AFTER, Tag.TAG_LONG, context);
        StoryFactType type;
        try {
            type = StoryFactType.parse(requireString(tag, FACT_TYPE, 32, context));
        } catch (IllegalArgumentException invalid) {
            throw format(context + " has an invalid FactType");
        }
        ResourceLocation subject = requireResource(tag, SUBJECT, context);
        String bindingFingerprint = requireFingerprint(tag, BINDING_FINGERPRINT, context);
        DispatchState state = parseEnum(
                DispatchState.class, requireString(tag, STATE, 32, context), context + " State");
        DirectorPresentationPolicy.Proof proof = parseEnum(
                DirectorPresentationPolicy.Proof.class,
                requireString(tag, PROOF, 32, context),
                context + " Proof");
        String outcome = requireString(tag, LAST_OUTCOME, 64, context);
        try {
            return new DispatchRecord(
                    tag.getUUID(TARGET_ID),
                    tag.getUUID(EVENT_ID),
                    campaignId,
                    tag.getInt(CAMPAIGN_REVISION),
                    campaignFingerprint,
                    tag.getLong(PROGRESS_EPOCH),
                    nodeId,
                    type,
                    subject,
                    bindingFingerprint,
                    tag.getInt(PRESENTATION_VARIANT),
                    tag.getInt(ATTEMPT),
                    state,
                    proof,
                    tag.getLong(RETRY_AFTER),
                    outcome);
        } catch (IllegalArgumentException invalid) {
            throw format(context + " is invalid: " + invalid.getMessage());
        }
    }

    private static CompoundTag writeRecord(DispatchRecord record) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TARGET_ID, record.targetId());
        tag.putUUID(EVENT_ID, record.eventId());
        tag.putString(CAMPAIGN_ID, record.campaignId().toString());
        tag.putInt(CAMPAIGN_REVISION, record.campaignRevision());
        tag.putString(CAMPAIGN_FINGERPRINT, record.campaignFingerprint());
        tag.putLong(PROGRESS_EPOCH, record.progressEpoch());
        tag.putString(NODE_ID, record.nodeId());
        tag.putString(FACT_TYPE, record.factType().serializedName());
        tag.putString(SUBJECT, record.subject().toString());
        tag.putString(BINDING_FINGERPRINT, record.bindingFingerprint());
        tag.putInt(PRESENTATION_VARIANT, record.presentationVariant());
        tag.putInt(ATTEMPT, record.attempt());
        tag.putString(STATE, record.state().name());
        tag.putString(PROOF, record.proof().name());
        tag.putLong(RETRY_AFTER, record.retryAfterGameTime());
        tag.putString(LAST_OUTCOME, record.lastOutcome());
        return tag;
    }

    private static HeraldorDirectorData unavailable(
            int schema, DataHealth health, String detail, CompoundTag root) {
        return new HeraldorDirectorData(schema, health, detail, root.copy());
    }

    private boolean writable() {
        return health == DataHealth.OK;
    }

    private static void requireExactFields(
            CompoundTag tag, Set<String> expected, String context) {
        if (!tag.getAllKeys().equals(expected)) {
            throw format(context + " fields are not exact");
        }
    }

    private static void requireType(
            CompoundTag tag, String key, int type, String context) {
        Tag value = tag.get(key);
        if (value == null || value.getId() != type) {
            throw format(context + ' ' + key + " has the wrong NBT type");
        }
    }

    private static void requireUuid(CompoundTag tag, String key, String context) {
        requireType(tag, key, Tag.TAG_INT_ARRAY, context);
        if (!tag.hasUUID(key)) {
            throw format(context + ' ' + key + " is not a UUID");
        }
    }

    private static String requireString(
            CompoundTag tag, String key, int maxLength, String context) {
        requireType(tag, key, Tag.TAG_STRING, context);
        String value = tag.getString(key);
        if (value.isEmpty() || value.length() > maxLength) {
            throw format(context + ' ' + key + " has an invalid length");
        }
        return value;
    }

    private static String requireFingerprint(
            CompoundTag tag, String key, String context) {
        String value = requireString(tag, key, 64, context);
        if (!SHA_256.matcher(value).matches()) {
            throw format(context + ' ' + key + " is not a SHA-256 fingerprint");
        }
        return value;
    }

    private static ResourceLocation requireResource(
            CompoundTag tag, String key, String context) {
        String raw = requireString(tag, key, 128, context);
        ResourceLocation value = ResourceLocation.tryParse(raw);
        if (value == null || !value.toString().equals(raw)) {
            throw format(context + ' ' + key + " is not canonical");
        }
        return value;
    }

    private static <T extends Enum<T>> T parseEnum(
            Class<T> type, String raw, String context) {
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException invalid) {
            throw format(context + " is invalid");
        }
    }

    private static boolean validOutcome(String outcome) {
        return outcome != null && OUTCOME.matcher(outcome).matches();
    }

    private static DataFormatException format(String message) {
        return new DataFormatException(message);
    }

    public enum DispatchState {
        PREPARED,
        AWAITING,
        PROVEN,
        COOLDOWN,
        BLOCKED
    }

    public enum PlanStatus {
        DISPATCH,
        WAITING,
        PROOF_READY,
        BLOCKED,
        DATA_UNAVAILABLE,
        INVALID_STORY_STATE,
        CAPACITY_EXHAUSTED
    }

    public enum DataHealth {
        OK,
        CORRUPT,
        UNSUPPORTED
    }

    public record SchemaStatus(
            int loadedVersion,
            int currentVersion,
            DataHealth health,
            String detail,
            boolean writable) {}

    public record PlanResult(PlanStatus status, DispatchRecord record, String detail) {}

    public record DispatchRecord(
            UUID targetId,
            UUID eventId,
            ResourceLocation campaignId,
            int campaignRevision,
            String campaignFingerprint,
            long progressEpoch,
            String nodeId,
            StoryFactType factType,
            ResourceLocation subject,
            String bindingFingerprint,
            int presentationVariant,
            int attempt,
            DispatchState state,
            DirectorPresentationPolicy.Proof proof,
            long retryAfterGameTime,
            String lastOutcome) {

        public DispatchRecord {
            Objects.requireNonNull(targetId, "targetId");
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(campaignId, "campaignId");
            Objects.requireNonNull(campaignFingerprint, "campaignFingerprint");
            Objects.requireNonNull(nodeId, "nodeId");
            Objects.requireNonNull(factType, "factType");
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(bindingFingerprint, "bindingFingerprint");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(proof, "proof");
            Objects.requireNonNull(lastOutcome, "lastOutcome");
            if (NIL_UUID.equals(targetId)
                    || NIL_UUID.equals(eventId)
                    || targetId.equals(eventId)) {
                throw new IllegalArgumentException("invalid record UUIDs");
            }
            if (campaignRevision < 1
                    || campaignRevision > StoryCampaignDefinition.MAX_CAMPAIGN_REVISION
                    || progressEpoch < 0L
                    || !NODE.matcher(nodeId).matches()
                    || !SHA_256.matcher(campaignFingerprint).matches()
                    || !SHA_256.matcher(bindingFingerprint).matches()
                    || presentationVariant < 0
                    || presentationVariant > 15
                    || attempt < 0
                    || attempt > MAX_ATTEMPTS
                    || retryAfterGameTime < 0L
                    || !validOutcome(lastOutcome)) {
                throw new IllegalArgumentException("record scalar is outside its bounds");
            }
            if (factType != StoryFactType.SCENE_COMPLETED
                    && factType != StoryFactType.SCENE_PRESENTED) {
                throw new IllegalArgumentException("record fact is not a scene fact");
            }
            if (state == DispatchState.PROVEN
                    && proof == DirectorPresentationPolicy.Proof.NONE) {
                throw new IllegalArgumentException("proven record requires proof");
            }
            if (state != DispatchState.PROVEN
                    && state != DispatchState.COOLDOWN
                    && proof != DirectorPresentationPolicy.Proof.NONE) {
                throw new IllegalArgumentException("unproven record cannot retain proof");
            }
        }

        public DirectorSceneIdentity identity() {
            return new DirectorSceneIdentity(
                    eventId,
                    targetId,
                    campaignId,
                    campaignRevision,
                    campaignFingerprint,
                    progressEpoch,
                    nodeId,
                    factType,
                    subject,
                    bindingFingerprint,
                    presentationVariant);
        }

        boolean matches(
                StoryCampaignDefinition campaign,
                StoryWorldData.PlayerSnapshot story,
                StoryNode node,
                DirectorSceneBinding binding) {
            return targetId.equals(story.playerId())
                    && campaignId.equals(campaign.id())
                    && campaignRevision == campaign.revision()
                    && campaignFingerprint.equals(campaign.fingerprint())
                    && progressEpoch == story.progressEpoch()
                    && nodeId.equals(node.id())
                    && factType == binding.factType()
                    && subject.equals(binding.subject())
                    && bindingFingerprint.equals(binding.fingerprint())
                    && presentationVariant == binding.presentationVariant();
        }

        boolean matchesStoryEnvelope(
                StoryCampaignDefinition campaign,
                StoryWorldData.PlayerSnapshot story,
                StoryNode node) {
            return targetId.equals(story.playerId())
                    && campaignId.equals(campaign.id())
                    && campaignRevision == campaign.revision()
                    && campaignFingerprint.equals(campaign.fingerprint())
                    && progressEpoch == story.progressEpoch()
                    && nodeId.equals(node.id())
                    && factType == node.advanceOn().type()
                    && subject.equals(node.advanceOn().subject());
        }

        DispatchRecord with(
                DispatchState newState,
                DirectorPresentationPolicy.Proof newProof,
                long retryAfter,
                String outcome) {
            return new DispatchRecord(
                    targetId,
                    eventId,
                    campaignId,
                    campaignRevision,
                    campaignFingerprint,
                    progressEpoch,
                    nodeId,
                    factType,
                    subject,
                    bindingFingerprint,
                    presentationVariant,
                    attempt,
                    newState,
                    newProof,
                    retryAfter,
                    outcome);
        }
    }

    private static final class DataFormatException extends RuntimeException {
        private DataFormatException(String message) {
            super(message);
        }
    }
}
