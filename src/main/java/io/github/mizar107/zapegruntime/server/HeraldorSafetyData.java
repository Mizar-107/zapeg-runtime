package io.github.mizar107.zapegruntime.server;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World-scoped safety authority. Unknown state is preserved byte-for-byte and quarantined. */
public final class HeraldorSafetyData extends SavedData {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String DATA_NAME = "zapeg_runtime_heraldor_safety";

    private static final String SCHEMA = "Schema";
    private static final String MODE = "Mode";
    private static final String GENERATION = "Generation";
    private static final String NONCE = "Nonce";
    private static final String INCIDENT = "Incident";
    private static final String LAST_NONCE = "LastNonce";
    private static final String LAST_REQUEST = "LastRequest";

    private final int loadedSchemaVersion;
    private final CompoundTag preservedUnsupportedRoot;
    private HeraldorSafetyMode configuredMode;
    private long generation;
    private UUID nonce;
    private UUID incidentId;
    private UUID lastNonce;
    private HeraldorSafetyMode lastRequest;

    public HeraldorSafetyData() {
        this(
                CURRENT_SCHEMA_VERSION,
                null,
                HeraldorSafetyMode.QUARANTINED,
                0L,
                randomNonNilUuid(),
                randomNonNilUuid(),
                null,
                null);
        setDirty();
    }

    private HeraldorSafetyData(
            int loadedSchemaVersion,
            CompoundTag preservedUnsupportedRoot,
            HeraldorSafetyMode configuredMode,
            long generation,
            UUID nonce,
            UUID incidentId,
            UUID lastNonce,
            HeraldorSafetyMode lastRequest) {
        this.loadedSchemaVersion = loadedSchemaVersion;
        this.preservedUnsupportedRoot = preservedUnsupportedRoot;
        this.configuredMode = Objects.requireNonNull(configuredMode, "configuredMode");
        this.generation = generation;
        this.nonce = Objects.requireNonNull(nonce, "nonce");
        this.incidentId = Objects.requireNonNull(incidentId, "incidentId");
        this.lastNonce = lastNonce;
        this.lastRequest = lastRequest;
    }

    public static HeraldorSafetyData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                HeraldorSafetyData::load, HeraldorSafetyData::new, DATA_NAME);
    }

    public static HeraldorSafetyData load(CompoundTag root) {
        Objects.requireNonNull(root, "root");
        if (!root.contains(SCHEMA, Tag.TAG_INT)) {
            return unsupported(0, root);
        }
        int schema = root.getInt(SCHEMA);
        if (schema != CURRENT_SCHEMA_VERSION) {
            return unsupported(schema, root);
        }
        try {
            if (!root.contains(MODE, Tag.TAG_STRING)
                    || !root.contains(GENERATION, Tag.TAG_LONG)
                    || !root.hasUUID(NONCE)
                    || !root.hasUUID(INCIDENT)) {
                return unsupported(schema, root);
            }
            HeraldorSafetyMode mode = HeraldorSafetyMode.parse(root.getString(MODE))
                    .orElseThrow(() -> new IllegalArgumentException("invalid safety mode"));
            long generation = root.getLong(GENERATION);
            UUID nonce = requireNonNil(root.getUUID(NONCE), "nonce");
            UUID incident = requireNonNil(root.getUUID(INCIDENT), "incident");
            if (generation < 0L) {
                throw new IllegalArgumentException("negative generation");
            }
            UUID lastNonce = root.hasUUID(LAST_NONCE)
                    ? requireNonNil(root.getUUID(LAST_NONCE), "last nonce")
                    : null;
            HeraldorSafetyMode lastRequest = null;
            if (root.contains(LAST_REQUEST, Tag.TAG_STRING)) {
                lastRequest = HeraldorSafetyMode.parse(root.getString(LAST_REQUEST))
                        .orElseThrow(() -> new IllegalArgumentException("invalid last request"));
            }
            if ((lastNonce == null) != (lastRequest == null)) {
                throw new IllegalArgumentException("partial replay receipt");
            }
            return new HeraldorSafetyData(
                    schema, null, mode, generation, nonce, incident, lastNonce, lastRequest);
        } catch (RuntimeException corrupt) {
            return unsupported(schema, root);
        }
    }

    private static HeraldorSafetyData unsupported(int schema, CompoundTag root) {
        return new HeraldorSafetyData(
                schema,
                root.copy(),
                HeraldorSafetyMode.QUARANTINED,
                0L,
                stableFallbackUuid(root, "nonce"),
                stableFallbackUuid(root, "incident"),
                null,
                null);
    }

    public HeraldorSafetyMode configuredMode() {
        return writable() ? configuredMode : HeraldorSafetyMode.QUARANTINED;
    }

    public HeraldorSafetyMode effectiveMode(HeraldorSafetyMode ceiling) {
        return configuredMode().clampTo(ceiling);
    }

    public long generation() {
        return generation;
    }

    public UUID nonce() {
        return nonce;
    }

    public UUID incidentId() {
        return incidentId;
    }

    public SchemaStatus schemaStatus() {
        return new SchemaStatus(
                loadedSchemaVersion,
                CURRENT_SCHEMA_VERSION,
                writable(),
                writable() ? "ok" : "unsupported_or_corrupt");
    }

    /**
     * Applies one nonce-authorized mode request. An exact retry of the immediately previous
     * request is a certified no-op; the consumed nonce can never authorize a different request.
     */
    public TransitionResult transition(HeraldorSafetyMode requested, UUID offeredNonce) {
        Objects.requireNonNull(requested, "requested");
        Objects.requireNonNull(offeredNonce, "offeredNonce");
        if (!writable()) {
            return new TransitionResult(TransitionStatus.DATA_UNAVAILABLE, configuredMode(), generation);
        }
        if (offeredNonce.equals(lastNonce)
                && requested == lastRequest
                && configuredMode == requested) {
            return new TransitionResult(TransitionStatus.DUPLICATE, configuredMode, generation);
        }
        if (!offeredNonce.equals(nonce)) {
            return new TransitionResult(TransitionStatus.STALE_NONCE, configuredMode, generation);
        }
        if (generation == Long.MAX_VALUE) {
            return new TransitionResult(TransitionStatus.GENERATION_EXHAUSTED, configuredMode, generation);
        }
        HeraldorSafetyMode previous = configuredMode;
        lastNonce = nonce;
        lastRequest = requested;
        nonce = randomNonNilUuid();
        generation++;
        configuredMode = requested;
        if (requested == HeraldorSafetyMode.QUARANTINED
                && previous != HeraldorSafetyMode.QUARANTINED) {
            incidentId = randomNonNilUuid();
        }
        setDirty();
        return new TransitionResult(TransitionStatus.APPLIED, configuredMode, generation);
    }

    /**
     * Nonce-free break-glass path. The first stop from an enabled mode rotates every authorization
     * token before cleanup can begin. Repeated stops while already quarantined are idempotent and
     * retain the same generation and incident identifier.
     */
    public TransitionResult emergencyQuarantine() {
        if (!writable()) {
            return new TransitionResult(
                    TransitionStatus.DATA_UNAVAILABLE,
                    HeraldorSafetyMode.QUARANTINED,
                    generation);
        }
        if (configuredMode == HeraldorSafetyMode.QUARANTINED) {
            return new TransitionResult(TransitionStatus.DUPLICATE, configuredMode, generation);
        }
        configuredMode = HeraldorSafetyMode.QUARANTINED;
        // Exhaustion permanently prevents re-arming, but can never prevent the emergency brake.
        if (generation < Long.MAX_VALUE) {
            generation++;
        }
        nonce = randomNonNilUuid();
        incidentId = randomNonNilUuid();
        lastNonce = null;
        lastRequest = null;
        setDirty();
        return new TransitionResult(TransitionStatus.APPLIED, configuredMode, generation);
    }

    /** Startup-only ceiling reconciliation: hidden authority is destroyed, never merely clamped. */
    TransitionResult reconcileCeiling(HeraldorSafetyMode ceiling) {
        Objects.requireNonNull(ceiling, "ceiling");
        if (!writable()) {
            return new TransitionResult(
                    TransitionStatus.DATA_UNAVAILABLE,
                    HeraldorSafetyMode.QUARANTINED,
                    generation);
        }
        if (ceiling.allows(configuredMode)) {
            return new TransitionResult(TransitionStatus.DUPLICATE, configuredMode, generation);
        }
        return emergencyQuarantine();
    }

    private boolean writable() {
        return preservedUnsupportedRoot == null;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        if (preservedUnsupportedRoot != null) {
            return preservedUnsupportedRoot.copy();
        }
        root.putInt(SCHEMA, CURRENT_SCHEMA_VERSION);
        root.putString(MODE, configuredMode.serializedName());
        root.putLong(GENERATION, generation);
        root.putUUID(NONCE, nonce);
        root.putUUID(INCIDENT, incidentId);
        if (lastNonce != null && lastRequest != null) {
            root.putUUID(LAST_NONCE, lastNonce);
            root.putString(LAST_REQUEST, lastRequest.serializedName());
        }
        return root;
    }

    private static UUID randomNonNilUuid() {
        UUID generated;
        do {
            generated = UUID.randomUUID();
        } while (generated.getMostSignificantBits() == 0L
                && generated.getLeastSignificantBits() == 0L);
        return generated;
    }

    private static UUID stableFallbackUuid(CompoundTag root, String discriminator) {
        return UUID.nameUUIDFromBytes((DATA_NAME + ':' + discriminator + ':' + root)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static UUID requireNonNil(UUID value, String name) {
        if (value.getMostSignificantBits() == 0L && value.getLeastSignificantBits() == 0L) {
            throw new IllegalArgumentException(name + " cannot be nil");
        }
        return value;
    }

    public enum TransitionStatus {
        APPLIED,
        DUPLICATE,
        STALE_NONCE,
        DATA_UNAVAILABLE,
        GENERATION_EXHAUSTED
    }

    public record TransitionResult(
            TransitionStatus status, HeraldorSafetyMode mode, long generation) {

        public boolean accepted() {
            return status == TransitionStatus.APPLIED || status == TransitionStatus.DUPLICATE;
        }
    }

    public record SchemaStatus(
            int loadedVersion, int currentVersion, boolean writable, String detail) {}
}
