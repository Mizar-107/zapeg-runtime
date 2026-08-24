package io.github.mizar107.zapegruntime.journal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Deterministic fact ids for retry-safe journal discoveries. */
public final class JournalFactIdentity {

    private JournalFactIdentity() {}

    public static UUID derive(
            UUID playerId,
            ResourceLocation campaignId,
            long recoveryEpoch,
            ResourceLocation subject) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(campaignId, "campaignId");
        Objects.requireNonNull(subject, "subject");
        if (recoveryEpoch < 0L) {
            throw new IllegalArgumentException("recovery epoch cannot be negative");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            append(digest, "zapeg-runtime:journal-fact:v1");
            append(digest, playerId.toString());
            append(digest, campaignId.toString());
            append(digest, Long.toString(recoveryEpoch));
            append(digest, subject.toString());
            byte[] value = digest.digest();
            // RFC 9562 version 8 marks this as an application-defined UUID
            // derived from the SHA-256 namespace above (not a v5/SHA-1 UUID).
            value[6] = (byte) ((value[6] & 0x0f) | 0x80);
            value[8] = (byte) ((value[8] & 0x3f) | 0x80);
            long most = 0L;
            long least = 0L;
            for (int index = 0; index < 8; index++) {
                most = (most << 8) | (value[index] & 0xffL);
                least = (least << 8) | (value[index + 8] & 0xffL);
            }
            return new UUID(most, least);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void append(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }
}
