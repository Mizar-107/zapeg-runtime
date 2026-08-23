package io.github.mizar107.zapegruntime.timeline;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** Stable IDs and seeds derived without server RNG or player display names. */
public final class TimelineDeterminism {

    private static final String DOMAIN = "heraldor:timeline:v1";

    private TimelineDeterminism() {}

    public static long sessionSeed(
            UUID sessionId, UUID targetId, TimelineDefinition definition) {
        return firstLong(DOMAIN + ":session:" + requireUuid(sessionId)
                + ':' + requireUuid(targetId) + ':' + definition.id()
                + ':' + definition.fingerprint());
    }

    public static UUID actionEventId(
            UUID sessionId,
            UUID targetId,
            TimelineDefinition definition,
            TimelineAction action) {
        return UUID.nameUUIDFromBytes((DOMAIN + ":event:" + requireUuid(sessionId)
                + ':' + requireUuid(targetId)
                + ':' + definition.id() + ':' + definition.fingerprint()
                + ':' + action.id()).getBytes(StandardCharsets.UTF_8));
    }

    public static long actionSeed(
            long sessionSeed, TimelineDefinition definition, TimelineAction action) {
        return firstLong(DOMAIN + ":action:" + sessionSeed + ':'
                + definition.id() + ':' + definition.fingerprint()
                + ':' + action.id());
    }

    public static long placementSeed(
            long sessionSeed, TimelineDefinition definition, TimelineAction action) {
        return firstLong(DOMAIN + ":placement:" + sessionSeed + ':'
                + definition.id() + ':' + definition.fingerprint()
                + ':' + action.id());
    }

    public static String actionPayloadHash(
            TimelineDefinition definition, TimelineAction action) {
        String payload = DOMAIN + ":payload:" + definition.id() + ':'
                + definition.fingerprint() + ':' + action.id() + ':'
                + action.atTick() + ':' + action.deadlineTick() + ':'
                + action.retryIntervalTicks() + ':' + action.required() + ':'
                + action.profile().serializedName() + ':' + action.ttlTicks()
                + ':' + action.stage();
        return HexFormat.of().formatHex(digest(payload));
    }

    public static String directScenePayloadHash(
            UUID targetId, String profile, int ttlTicks, int stage) {
        Objects.requireNonNull(profile, "profile");
        String payload = DOMAIN + ":direct-scene:" + requireUuid(targetId)
                + ':' + profile + ':' + ttlTicks + ':' + stage;
        return HexFormat.of().formatHex(digest(payload));
    }

    private static UUID requireUuid(UUID value) {
        return Objects.requireNonNull(value, "uuid");
    }

    private static long firstLong(String input) {
        return ByteBuffer.wrap(digest(input)).getLong();
    }

    private static byte[] digest(String input) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
