package io.github.mizar107.zapegruntime.timeline;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
            UUID sessionId, TimelineDefinition definition, TimelineAction action) {
        return UUID.nameUUIDFromBytes((DOMAIN + ":event:" + requireUuid(sessionId)
                + ':' + definition.id() + ':' + definition.fingerprint()
                + ':' + action.id()).getBytes(StandardCharsets.UTF_8));
    }

    public static long actionSeed(
            long sessionSeed, TimelineDefinition definition, TimelineAction action) {
        return firstLong(DOMAIN + ":action:" + sessionSeed + ':'
                + definition.id() + ':' + definition.fingerprint()
                + ':' + action.id());
    }

    private static UUID requireUuid(UUID value) {
        return Objects.requireNonNull(value, "uuid");
    }

    private static long firstLong(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getLong();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
