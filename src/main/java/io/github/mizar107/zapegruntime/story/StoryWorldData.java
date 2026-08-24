package io.github.mizar107.zapegruntime.story;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World-scoped, UUID-keyed story state with strict bounded decoding.
 *
 * <p>Unsupported or corrupt roots are preserved byte-for-byte at the NBT tree
 * level and become read-only. Recovery commands can repair a structurally
 * valid player state or intentionally rebind a definition, but never overwrite
 * an unreadable root.
 */
public final class StoryWorldData extends SavedData {

    public static final int CURRENT_SCHEMA_VERSION = 2;
    public static final int MAX_PLAYERS = 2_048;
    public static final int MAX_PROCESSED_FACTS_PER_PLAYER = 256;
    public static final int MAX_RECOVERY_OPERATIONS_PER_PLAYER = 64;

    private static final int LEGACY_SCHEMA_VERSION = 1;
    private static final String DATA_NAME = "zapeg_runtime_heraldor_story";
    private static final String SCHEMA_KEY = "SchemaVersion";
    private static final String PLAYERS_KEY = "Players";
    private static final String PLAYER_ID_KEY = "PlayerId";
    private static final String CAMPAIGN_ID_KEY = "CampaignId";
    private static final String CAMPAIGN_REVISION_KEY = "CampaignRevision";
    private static final String FINGERPRINT_KEY = "DefinitionFingerprint";
    private static final String PROGRESS_EPOCH_KEY = "ProgressEpoch";
    private static final String CURRENT_NODE_KEY = "CurrentNode";
    private static final String COMPLETED_NODES_KEY = "CompletedNodes";
    private static final String PROCESSED_FACTS_KEY = "ProcessedFacts";
    private static final String RECOVERY_OPERATIONS_KEY = "RecoveryOperations";
    private static final String FACT_ID_KEY = "FactId";
    private static final String FACT_IDENTITY_KEY = "Identity";
    private static final String LEGACY_FACT_IDENTITY = "legacy";
    private static final Set<String> FACT_RECEIPT_FIELDS =
            Set.of(FACT_ID_KEY, FACT_IDENTITY_KEY);
    private static final Set<String> ROOT_FIELDS = Set.of(SCHEMA_KEY, PLAYERS_KEY);
    private static final Set<String> V1_PLAYER_FIELDS = Set.of(
            PLAYER_ID_KEY,
            CAMPAIGN_ID_KEY,
            CAMPAIGN_REVISION_KEY,
            FINGERPRINT_KEY,
            CURRENT_NODE_KEY,
            COMPLETED_NODES_KEY,
            PROCESSED_FACTS_KEY);
    private static final Set<String> V2_PLAYER_FIELDS = Set.of(
            PLAYER_ID_KEY,
            CAMPAIGN_ID_KEY,
            CAMPAIGN_REVISION_KEY,
            FINGERPRINT_KEY,
            PROGRESS_EPOCH_KEY,
            CURRENT_NODE_KEY,
            COMPLETED_NODES_KEY,
            PROCESSED_FACTS_KEY,
            RECOVERY_OPERATIONS_KEY);
    private static final Pattern FINGERPRINT = Pattern.compile("[0-9a-f]{64}");

    private final Map<UUID, MutablePlayerState> players = new HashMap<>();
    private final Map<UUID, ReceiptOwner> factOwners = new HashMap<>();
    private final int loadedSchemaVersion;
    private final DataHealth health;
    private final String healthDetail;
    private final CompoundTag preservedRoot;

    public StoryWorldData() {
        this(CURRENT_SCHEMA_VERSION, DataHealth.OK, "ok", null);
    }

    private StoryWorldData(
            int loadedSchemaVersion,
            DataHealth health,
            String healthDetail,
            CompoundTag preservedRoot) {
        this.loadedSchemaVersion = loadedSchemaVersion;
        this.health = health;
        this.healthDetail = Objects.requireNonNull(healthDetail, "healthDetail");
        this.preservedRoot = preservedRoot;
    }

    public static StoryWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                StoryWorldData::load, StoryWorldData::new, DATA_NAME);
    }

    public static StoryWorldData load(CompoundTag root) {
        Objects.requireNonNull(root, "root");
        if (!root.contains(SCHEMA_KEY)) {
            if (!root.getAllKeys().isEmpty()) {
                return unavailable(
                        0,
                        DataHealth.CORRUPT,
                        "unversioned story data contains unknown fields",
                        root);
            }
            StoryWorldData migrated =
                    new StoryWorldData(0, DataHealth.MIGRATED, "empty schema zero migrated", null);
            migrated.setDirty();
            return migrated;
        }
        if (!root.contains(SCHEMA_KEY, Tag.TAG_INT)) {
            return unavailable(
                    -1, DataHealth.CORRUPT, "SchemaVersion must be an integer", root);
        }
        int schema = root.getInt(SCHEMA_KEY);
        if (schema != LEGACY_SCHEMA_VERSION && schema != CURRENT_SCHEMA_VERSION) {
            return unavailable(
                    schema,
                    DataHealth.UNSUPPORTED,
                    "unsupported story schema " + schema,
                    root);
        }
        try {
            StoryWorldData loaded = decodeKnown(root, schema);
            if (schema == LEGACY_SCHEMA_VERSION) {
                loaded.setDirty();
            }
            return loaded;
        } catch (StoryDataFormatException corrupt) {
            return unavailable(schema, DataHealth.CORRUPT, corrupt.getMessage(), root);
        }
    }

    private static StoryWorldData decodeKnown(CompoundTag root, int schema) {
        requireExactFields(root, ROOT_FIELDS, "story root");
        requireType(root, PLAYERS_KEY, Tag.TAG_LIST);
        Tag encodedPlayersTag = root.get(PLAYERS_KEY);
        if (!(encodedPlayersTag instanceof ListTag encodedPlayers)) {
            throw format("Players must be a list");
        }
        if (encodedPlayers.getElementType() != Tag.TAG_COMPOUND && !encodedPlayers.isEmpty()) {
            throw format("Players must be a compound list");
        }
        if (encodedPlayers.size() > MAX_PLAYERS) {
            throw format("player count exceeds " + MAX_PLAYERS);
        }
        StoryWorldData data = new StoryWorldData(
                schema,
                schema == LEGACY_SCHEMA_VERSION ? DataHealth.MIGRATED : DataHealth.OK,
                schema == LEGACY_SCHEMA_VERSION ? "schema one migrated" : "ok",
                null);
        for (int index = 0; index < encodedPlayers.size(); index++) {
            CompoundTag encoded = encodedPlayers.getCompound(index);
            MutablePlayerState state = decodePlayer(encoded, schema, index);
            if (data.players.containsKey(state.playerId)) {
                throw format("duplicate PlayerId at index " + index);
            }
            for (Map.Entry<UUID, String> receipt : state.processedFacts.entrySet()) {
                ReceiptOwner previous = data.factOwners.put(
                        receipt.getKey(), new ReceiptOwner(state.playerId, receipt.getValue()));
                if (previous != null) {
                    throw format("fact UUID is reused across player entries: " + receipt.getKey());
                }
            }
            data.players.put(state.playerId, state);
        }
        return data;
    }

    private static MutablePlayerState decodePlayer(CompoundTag tag, int schema, int index) {
        requireExactFields(
                tag,
                schema == LEGACY_SCHEMA_VERSION ? V1_PLAYER_FIELDS : V2_PLAYER_FIELDS,
                "player " + index);
        if (!tag.hasUUID(PLAYER_ID_KEY)) {
            throw format("player " + index + " has an invalid PlayerId");
        }
        UUID playerId = tag.getUUID(PLAYER_ID_KEY);
        String campaignValue = requireBoundedString(tag, CAMPAIGN_ID_KEY, 128);
        ResourceLocation campaignId = ResourceLocation.tryParse(campaignValue);
        if (campaignId == null || !campaignId.toString().equals(campaignValue)) {
            throw format("player " + index + " has a non-canonical CampaignId");
        }
        requireType(tag, CAMPAIGN_REVISION_KEY, Tag.TAG_INT);
        int revision = tag.getInt(CAMPAIGN_REVISION_KEY);
        if (revision < 1 || revision > StoryCampaignDefinition.MAX_CAMPAIGN_REVISION) {
            throw format("player " + index + " has an invalid CampaignRevision");
        }
        String fingerprint = requireBoundedString(tag, FINGERPRINT_KEY, 64);
        if (!FINGERPRINT.matcher(fingerprint).matches()) {
            throw format("player " + index + " has an invalid DefinitionFingerprint");
        }
        long progressEpoch = schema == LEGACY_SCHEMA_VERSION
                ? 0L
                : requireNonNegativeLong(tag, PROGRESS_EPOCH_KEY);
        String currentNode = requireBoundedString(tag, CURRENT_NODE_KEY, 64);
        validatePersistedNodeId(currentNode, "player " + index + " CurrentNode");
        List<String> completed = readNodeIds(
                tag, COMPLETED_NODES_KEY, StoryCampaignDefinition.REQUIRED_NODE_COUNT - 1);
        LinkedHashMap<UUID, String> processed = readFactReceipts(tag, schema);
        LinkedHashSet<UUID> recoveries = schema == LEGACY_SCHEMA_VERSION
                ? new LinkedHashSet<>()
                : readUuids(
                        tag,
                        RECOVERY_OPERATIONS_KEY,
                        MAX_RECOVERY_OPERATIONS_PER_PLAYER);
        return new MutablePlayerState(
                playerId,
                campaignId,
                revision,
                fingerprint,
                progressEpoch,
                currentNode,
                completed,
                processed,
                recoveries);
    }

    public SchemaStatus schemaStatus() {
        return new SchemaStatus(
                loadedSchemaVersion,
                CURRENT_SCHEMA_VERSION,
                health,
                healthDetail,
                writable());
    }

    public Optional<PlayerSnapshot> snapshot(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!writable()) {
            return Optional.empty();
        }
        MutablePlayerState state = players.get(playerId);
        return state == null ? Optional.empty() : Optional.of(state.snapshot());
    }

    /** Diagnostic/reconciliation lookup which never creates a player entry. */
    public Optional<Boolean> hasProcessedFact(UUID playerId, UUID factId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(factId, "factId");
        if (!writable()) {
            return Optional.empty();
        }
        MutablePlayerState state = players.get(playerId);
        return Optional.of(state != null && state.processedFacts.containsKey(factId));
    }

    /**
     * Globally classifies a fact UUID without creating player state. A caller
     * may certify a replay only when both target UUID and payload identity match.
     */
    public ReceiptStatus receiptStatus(
            UUID playerId,
            UUID factId,
            ResourceLocation campaignId,
            int campaignRevision,
            StoryFactType type,
            ResourceLocation subject) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(factId, "factId");
        String offeredIdentity = StoryFact.replayIdentityFingerprint(
                factId, playerId, campaignId, campaignRevision, type, subject);
        if (!writable()) {
            return ReceiptStatus.DATA_UNAVAILABLE;
        }
        ReceiptOwner owner = factOwners.get(factId);
        if (owner == null) {
            return ReceiptStatus.ABSENT;
        }
        if (!owner.playerId().equals(playerId)) {
            return ReceiptStatus.CONFLICT;
        }
        if (owner.identity().equals(LEGACY_FACT_IDENTITY)) {
            return ReceiptStatus.UNVERIFIABLE;
        }
        if (owner.identity().equals(offeredIdentity)) {
            return ReceiptStatus.EXACT;
        }
        return ReceiptStatus.CONFLICT;
    }

    public ApplyResult applyFact(StoryCampaignDefinition campaign, StoryFact fact) {
        Objects.requireNonNull(campaign, "campaign");
        Objects.requireNonNull(fact, "fact");
        if (!writable()) {
            return ApplyResult.failure(ApplyStatus.DATA_UNAVAILABLE, healthDetail);
        }
        if (!campaign.id().equals(fact.campaignId())
                || campaign.revision() != fact.campaignRevision()) {
            return ApplyResult.failure(
                    ApplyStatus.DEFINITION_MISMATCH,
                    "fact campaign id or revision does not match the active definition");
        }

        MutablePlayerState state = players.get(fact.playerId());
        boolean newPlayer = state == null;
        if (state == null) {
            if (players.size() >= MAX_PLAYERS) {
                return ApplyResult.failure(
                        ApplyStatus.PLAYER_CAPACITY_EXHAUSTED,
                        "story player capacity is exhausted");
            }
            state = MutablePlayerState.atEntry(fact.playerId(), campaign);
        }
        StateValidation validation = validateState(state, campaign);
        if (validation == StateValidation.DEFINITION_MISMATCH) {
            return ApplyResult.failure(
                    ApplyStatus.DEFINITION_MISMATCH,
                    "persisted player state is bound to another campaign definition");
        }
        if (validation == StateValidation.INVALID) {
            return ApplyResult.failure(
                    ApplyStatus.INVALID_STATE,
                    "persisted player story prefix is inconsistent");
        }
        String factIdentity = fact.identityFingerprint();
        ReceiptStatus receipt = receiptStatus(
                fact.playerId(),
                fact.factId(),
                fact.campaignId(),
                fact.campaignRevision(),
                fact.type(),
                fact.subject());
        if (receipt != ReceiptStatus.ABSENT) {
            if (receipt == ReceiptStatus.CONFLICT
                    || receipt == ReceiptStatus.UNVERIFIABLE) {
                return new ApplyResult(
                        ApplyStatus.FACT_ID_CONFLICT,
                        state.currentNode,
                        state.currentNode,
                        receipt == ReceiptStatus.UNVERIFIABLE
                                ? "legacy fact receipt cannot prove target and payload identity"
                                : "fact UUID was previously bound to another target or payload",
                        Optional.of(state.snapshot()));
            }
            if (receipt == ReceiptStatus.DATA_UNAVAILABLE) {
                return ApplyResult.failure(ApplyStatus.DATA_UNAVAILABLE, healthDetail);
            }
            return new ApplyResult(
                    ApplyStatus.DUPLICATE,
                    state.currentNode,
                    state.currentNode,
                    "fact was already processed",
                    Optional.of(state.snapshot()));
        }
        if (state.progressEpoch != fact.progressEpoch()) {
            return new ApplyResult(
                    ApplyStatus.STALE_EPOCH,
                    state.currentNode,
                    state.currentNode,
                    "fact belongs to a superseded recovery epoch",
                    Optional.of(state.snapshot()));
        }
        if (state.processedFacts.size() >= MAX_PROCESSED_FACTS_PER_PLAYER) {
            return ApplyResult.failure(
                    ApplyStatus.FACT_CAPACITY_EXHAUSTED,
                    "processed fact capacity is exhausted; backed-up offline repair is required");
        }

        String previousNode = state.currentNode;
        if (newPlayer) {
            players.put(fact.playerId(), state);
        }
        state.processedFacts.put(fact.factId(), factIdentity);
        factOwners.put(fact.factId(), new ReceiptOwner(fact.playerId(), factIdentity));
        StoryTransitionEngine.Decision decision =
                StoryTransitionEngine.evaluate(
                        campaign, state.currentNode, state.progressEpoch, fact);
        ApplyStatus status = switch (decision.outcome()) {
            case ADVANCE -> {
                state.completedNodes.add(state.currentNode);
                state.currentNode = decision.resultingNodeId();
                yield ApplyStatus.ADVANCED;
            }
            case NO_MATCH -> ApplyStatus.RECORDED_NO_MATCH;
            case STALE_NODE -> ApplyStatus.RECORDED_STALE;
            case TERMINAL -> ApplyStatus.RECORDED_TERMINAL;
            case STALE_EPOCH -> ApplyStatus.STALE_EPOCH;
            case DEFINITION_MISMATCH -> ApplyStatus.DEFINITION_MISMATCH;
            case INVALID_STATE -> ApplyStatus.INVALID_STATE;
        };
        setDirty();
        return new ApplyResult(
                status,
                previousNode,
                state.currentNode,
                "fact recorded",
                Optional.of(state.snapshot()));
    }

    public RecoveryResult recover(
            StoryCampaignDefinition campaign,
            UUID playerId,
            UUID operationId,
            String targetNodeId) {
        Objects.requireNonNull(campaign, "campaign");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(targetNodeId, "targetNodeId");
        if (!writable()) {
            return RecoveryResult.failure(RecoveryStatus.DATA_UNAVAILABLE, healthDetail);
        }
        StoryNode target = campaign.node(targetNodeId);
        if (target == null) {
            return RecoveryResult.failure(
                    RecoveryStatus.INVALID_NODE, "target node is not in the active campaign");
        }
        MutablePlayerState state = players.get(playerId);
        if (state == null) {
            if (players.size() >= MAX_PLAYERS) {
                return RecoveryResult.failure(
                        RecoveryStatus.PLAYER_CAPACITY_EXHAUSTED,
                        "story player capacity is exhausted");
            }
            state = MutablePlayerState.atEntry(playerId, campaign);
            players.put(playerId, state);
        }
        if (state.recoveryOperations.contains(operationId)) {
            return new RecoveryResult(
                    RecoveryStatus.DUPLICATE,
                    state.currentNode,
                    "recovery operation was already applied",
                    Optional.of(state.snapshot()));
        }
        if (state.recoveryOperations.size() >= MAX_RECOVERY_OPERATIONS_PER_PLAYER) {
            return RecoveryResult.failure(
                    RecoveryStatus.RECOVERY_CAPACITY_EXHAUSTED,
                    "recovery operation capacity is exhausted");
        }
        if (state.progressEpoch == Long.MAX_VALUE) {
            return RecoveryResult.failure(
                    RecoveryStatus.EPOCH_EXHAUSTED,
                    "progress epoch is exhausted and cannot be rotated safely");
        }

        state.campaignId = campaign.id();
        state.campaignRevision = campaign.revision();
        state.definitionFingerprint = campaign.fingerprint();
        state.progressEpoch++;
        state.currentNode = target.id();
        state.completedNodes.clear();
        state.completedNodes.addAll(campaign.completedPrefixFor(target.id()));
        state.recoveryOperations.add(operationId);
        setDirty();
        RecoveryStatus status = target.id().equals(campaign.entryNodeId())
                ? RecoveryStatus.RESET
                : RecoveryStatus.MOVED;
        return new RecoveryResult(
                status,
                target.id(),
                "player story was rebound to the active definition",
                Optional.of(state.snapshot()));
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        if (preservedRoot != null) {
            return preservedRoot.copy();
        }
        root.putInt(SCHEMA_KEY, CURRENT_SCHEMA_VERSION);
        ListTag encodedPlayers = new ListTag();
        players.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
                .forEach(entry -> encodedPlayers.add(encodePlayer(entry.getValue())));
        root.put(PLAYERS_KEY, encodedPlayers);
        return root;
    }

    private static CompoundTag encodePlayer(MutablePlayerState state) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(PLAYER_ID_KEY, state.playerId);
        tag.putString(CAMPAIGN_ID_KEY, state.campaignId.toString());
        tag.putInt(CAMPAIGN_REVISION_KEY, state.campaignRevision);
        tag.putString(FINGERPRINT_KEY, state.definitionFingerprint);
        tag.putLong(PROGRESS_EPOCH_KEY, state.progressEpoch);
        tag.putString(CURRENT_NODE_KEY, state.currentNode);
        tag.put(COMPLETED_NODES_KEY, encodeStrings(state.completedNodes));
        tag.put(PROCESSED_FACTS_KEY, encodeFactReceipts(state.processedFacts));
        tag.put(RECOVERY_OPERATIONS_KEY, encodeUuids(state.recoveryOperations));
        return tag;
    }

    private static ListTag encodeStrings(Iterable<String> values) {
        ListTag result = new ListTag();
        for (String value : values) {
            result.add(StringTag.valueOf(value));
        }
        return result;
    }

    private static ListTag encodeUuids(Set<UUID> values) {
        List<UUID> ordered = new ArrayList<>(values);
        ordered.sort(Comparator.comparing(UUID::toString));
        ListTag result = new ListTag();
        for (UUID value : ordered) {
            result.add(StringTag.valueOf(value.toString()));
        }
        return result;
    }

    private static ListTag encodeFactReceipts(Map<UUID, String> receipts) {
        List<UUID> ordered = new ArrayList<>(receipts.keySet());
        ordered.sort(Comparator.comparing(UUID::toString));
        ListTag result = new ListTag();
        for (UUID factId : ordered) {
            CompoundTag encoded = new CompoundTag();
            encoded.putUUID(FACT_ID_KEY, factId);
            encoded.putString(FACT_IDENTITY_KEY, receipts.get(factId));
            result.add(encoded);
        }
        return result;
    }

    private static StateValidation validateState(
            MutablePlayerState state, StoryCampaignDefinition campaign) {
        if (!state.campaignId.equals(campaign.id())
                || state.campaignRevision != campaign.revision()
                || !state.definitionFingerprint.equals(campaign.fingerprint())) {
            return StateValidation.DEFINITION_MISMATCH;
        }
        StoryNode current = campaign.node(state.currentNode);
        if (current == null
                || !state.completedNodes.equals(campaign.completedPrefixFor(state.currentNode))) {
            return StateValidation.INVALID;
        }
        return StateValidation.VALID;
    }

    private boolean writable() {
        return health == DataHealth.OK || health == DataHealth.MIGRATED;
    }

    private static StoryWorldData unavailable(
            int schema, DataHealth health, String detail, CompoundTag root) {
        return new StoryWorldData(schema, health, detail, root.copy());
    }

    private static void requireExactFields(
            CompoundTag tag, Set<String> expected, String description) {
        Set<String> actual = new HashSet<>(tag.getAllKeys());
        if (!actual.equals(expected)) {
            Set<String> missing = new TreeSet<>(expected);
            missing.removeAll(actual);
            Set<String> unknown = new TreeSet<>(actual);
            unknown.removeAll(expected);
            throw format(
                    description + " fields mismatch; missing=" + missing + " unknown=" + unknown);
        }
    }

    private static void requireType(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) {
            throw format(key + " has the wrong NBT type");
        }
    }

    private static String requireBoundedString(CompoundTag tag, String key, int maxLength) {
        requireType(tag, key, Tag.TAG_STRING);
        String value = tag.getString(key);
        if (value.isEmpty() || value.length() > maxLength) {
            throw format(key + " is empty or exceeds " + maxLength + " characters");
        }
        return value;
    }

    private static long requireNonNegativeLong(CompoundTag tag, String key) {
        requireType(tag, key, Tag.TAG_LONG);
        long value = tag.getLong(key);
        if (value < 0L) {
            throw format(key + " cannot be negative");
        }
        return value;
    }

    private static List<String> readNodeIds(CompoundTag tag, String key, int limit) {
        ListTag list = requireStringList(tag, key);
        if (list.size() > limit) {
            throw format(key + " exceeds " + limit + " entries");
        }
        List<String> result = new ArrayList<>(list.size());
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < list.size(); index++) {
            String value = list.getString(index);
            validatePersistedNodeId(value, key + '[' + index + ']');
            if (!seen.add(value)) {
                throw format(key + " contains a duplicate node id");
            }
            result.add(value);
        }
        return result;
    }

    private static LinkedHashSet<UUID> readUuids(CompoundTag tag, String key, int limit) {
        ListTag list = requireStringList(tag, key);
        if (list.size() > limit) {
            throw format(key + " exceeds " + limit + " entries");
        }
        LinkedHashSet<UUID> result = new LinkedHashSet<>();
        for (int index = 0; index < list.size(); index++) {
            UUID value;
            try {
                value = UUID.fromString(list.getString(index));
            } catch (IllegalArgumentException invalid) {
                throw format(key + '[' + index + "] is not a UUID");
            }
            if (!result.add(value)) {
                throw format(key + " contains a duplicate UUID");
            }
        }
        return result;
    }

    private static LinkedHashMap<UUID, String> readFactReceipts(
            CompoundTag tag, int schema) {
        if (schema == LEGACY_SCHEMA_VERSION) {
            LinkedHashMap<UUID, String> migrated = new LinkedHashMap<>();
            for (UUID factId : readUuids(
                    tag, PROCESSED_FACTS_KEY, MAX_PROCESSED_FACTS_PER_PLAYER)) {
                migrated.put(factId, LEGACY_FACT_IDENTITY);
            }
            return migrated;
        }
        requireType(tag, PROCESSED_FACTS_KEY, Tag.TAG_LIST);
        Tag raw = tag.get(PROCESSED_FACTS_KEY);
        if (!(raw instanceof ListTag list)) {
            throw format(PROCESSED_FACTS_KEY + " must be a list");
        }
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            throw format(PROCESSED_FACTS_KEY + " must be a compound list");
        }
        if (list.size() > MAX_PROCESSED_FACTS_PER_PLAYER) {
            throw format(PROCESSED_FACTS_KEY + " exceeds "
                    + MAX_PROCESSED_FACTS_PER_PLAYER + " entries");
        }
        LinkedHashMap<UUID, String> result = new LinkedHashMap<>();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag encoded = list.getCompound(index);
            requireExactFields(
                    encoded, FACT_RECEIPT_FIELDS, PROCESSED_FACTS_KEY + '[' + index + ']');
            if (!encoded.hasUUID(FACT_ID_KEY)) {
                throw format(PROCESSED_FACTS_KEY + '[' + index + "] has an invalid FactId");
            }
            UUID factId = encoded.getUUID(FACT_ID_KEY);
            String identity = requireBoundedString(encoded, FACT_IDENTITY_KEY, 64);
            if (!identity.equals(LEGACY_FACT_IDENTITY)
                    && !FINGERPRINT.matcher(identity).matches()) {
                throw format(PROCESSED_FACTS_KEY + '[' + index + "] has an invalid Identity");
            }
            if (result.put(factId, identity) != null) {
                throw format(PROCESSED_FACTS_KEY + " contains a duplicate FactId");
            }
        }
        return result;
    }

    private static ListTag requireStringList(CompoundTag tag, String key) {
        requireType(tag, key, Tag.TAG_LIST);
        Tag encoded = tag.get(key);
        if (!(encoded instanceof ListTag list)) {
            throw format(key + " must be a list");
        }
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_STRING) {
            throw format(key + " must be a string list");
        }
        return list;
    }

    private static void validatePersistedNodeId(String nodeId, String description) {
        try {
            StoryCampaignDefinition.validateNodeId(nodeId);
        } catch (IllegalArgumentException invalid) {
            throw format(description + " is invalid");
        }
    }

    private static StoryDataFormatException format(String message) {
        return new StoryDataFormatException(message);
    }

    public enum DataHealth {
        OK,
        MIGRATED,
        UNSUPPORTED,
        CORRUPT
    }

    public record SchemaStatus(
            int loadedVersion,
            int currentVersion,
            DataHealth health,
            String detail,
            boolean writable) {}

    public record PlayerSnapshot(
            UUID playerId,
            ResourceLocation campaignId,
            int campaignRevision,
            String definitionFingerprint,
            long progressEpoch,
            String currentNodeId,
            List<String> completedNodes,
            int processedFactCount,
            int recoveryOperationCount) {

        public PlayerSnapshot {
            completedNodes = List.copyOf(completedNodes);
        }
    }

    public enum ApplyStatus {
        ADVANCED,
        RECORDED_NO_MATCH,
        RECORDED_STALE,
        RECORDED_TERMINAL,
        DUPLICATE,
        FACT_ID_CONFLICT,
        STALE_EPOCH,
        DATA_UNAVAILABLE,
        DEFINITION_MISMATCH,
        INVALID_STATE,
        PLAYER_CAPACITY_EXHAUSTED,
        FACT_CAPACITY_EXHAUSTED
    }

    public enum ReceiptStatus {
        ABSENT,
        EXACT,
        CONFLICT,
        UNVERIFIABLE,
        DATA_UNAVAILABLE
    }

    public record ApplyResult(
            ApplyStatus status,
            String previousNodeId,
            String currentNodeId,
            String detail,
            Optional<PlayerSnapshot> snapshot) {

        private static ApplyResult failure(ApplyStatus status, String detail) {
            return new ApplyResult(status, null, null, detail, Optional.empty());
        }
    }

    public enum RecoveryStatus {
        RESET,
        MOVED,
        DUPLICATE,
        DATA_UNAVAILABLE,
        INVALID_NODE,
        PLAYER_CAPACITY_EXHAUSTED,
        RECOVERY_CAPACITY_EXHAUSTED,
        EPOCH_EXHAUSTED
    }

    public record RecoveryResult(
            RecoveryStatus status,
            String currentNodeId,
            String detail,
            Optional<PlayerSnapshot> snapshot) {

        private static RecoveryResult failure(RecoveryStatus status, String detail) {
            return new RecoveryResult(status, null, detail, Optional.empty());
        }
    }

    private enum StateValidation {
        VALID,
        DEFINITION_MISMATCH,
        INVALID
    }

    private static final class MutablePlayerState {
        private final UUID playerId;
        private ResourceLocation campaignId;
        private int campaignRevision;
        private String definitionFingerprint;
        private long progressEpoch;
        private String currentNode;
        private final List<String> completedNodes;
        private final LinkedHashMap<UUID, String> processedFacts;
        private final LinkedHashSet<UUID> recoveryOperations;

        private MutablePlayerState(
                UUID playerId,
                ResourceLocation campaignId,
                int campaignRevision,
                String definitionFingerprint,
                long progressEpoch,
                String currentNode,
                List<String> completedNodes,
                LinkedHashMap<UUID, String> processedFacts,
                LinkedHashSet<UUID> recoveryOperations) {
            this.playerId = playerId;
            this.campaignId = campaignId;
            this.campaignRevision = campaignRevision;
            this.definitionFingerprint = definitionFingerprint;
            this.progressEpoch = progressEpoch;
            this.currentNode = currentNode;
            this.completedNodes = new ArrayList<>(completedNodes);
            this.processedFacts = new LinkedHashMap<>(processedFacts);
            this.recoveryOperations = recoveryOperations;
        }

        private static MutablePlayerState atEntry(
                UUID playerId, StoryCampaignDefinition campaign) {
            return new MutablePlayerState(
                    playerId,
                    campaign.id(),
                    campaign.revision(),
                    campaign.fingerprint(),
                    0L,
                    campaign.entryNodeId(),
                    List.of(),
                    new LinkedHashMap<>(),
                    new LinkedHashSet<>());
        }

        private PlayerSnapshot snapshot() {
            return new PlayerSnapshot(
                    playerId,
                    campaignId,
                    campaignRevision,
                    definitionFingerprint,
                    progressEpoch,
                    currentNode,
                    completedNodes,
                    processedFacts.size(),
                    recoveryOperations.size());
        }
    }

    private record ReceiptOwner(UUID playerId, String identity) {}

    private static final class StoryDataFormatException extends RuntimeException {
        private StoryDataFormatException(String message) {
            super(message);
        }
    }
}
