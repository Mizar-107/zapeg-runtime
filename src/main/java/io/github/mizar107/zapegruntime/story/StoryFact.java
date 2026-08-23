package io.github.mizar107.zapegruntime.story;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * Stable, server-authored evidence submitted to the story engine.
 *
 * <p>The recovery epoch and expected node make delayed delivery fail closed.
 * The fact UUID is a replay key; player identity is always a UUID and never a
 * display name.
 */
public record StoryFact(
        UUID factId,
        UUID playerId,
        ResourceLocation campaignId,
        int campaignRevision,
        long progressEpoch,
        String expectedNodeId,
        StoryFactType type,
        ResourceLocation subject) {

    public StoryFact {
        Objects.requireNonNull(factId, "factId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(campaignId, "campaignId");
        Objects.requireNonNull(expectedNodeId, "expectedNodeId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(subject, "subject");
        if (campaignRevision < 1) {
            throw new IllegalArgumentException("campaign revision must be positive");
        }
        if (progressEpoch < 0L) {
            throw new IllegalArgumentException("progress epoch cannot be negative");
        }
        StoryCampaignDefinition.validateNodeId(expectedNodeId);
    }

    public StoryTrigger trigger() {
        return new StoryTrigger(type, subject);
    }

    /** Canonical payload identity used to distinguish replay from UUID conflict. */
    public String identityFingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            append(digest, factId.toString());
            append(digest, playerId.toString());
            append(digest, campaignId.toString());
            append(digest, Integer.toString(campaignRevision));
            append(digest, Long.toString(progressEpoch));
            append(digest, expectedNodeId);
            append(digest, type.serializedName());
            append(digest, subject.toString());
            return toHex(digest.digest());
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

    private static String toHex(byte[] value) {
        StringBuilder encoded = new StringBuilder(value.length * 2);
        for (byte item : value) {
            encoded.append(Character.forDigit((item >>> 4) & 0xf, 16));
            encoded.append(Character.forDigit(item & 0xf, 16));
        }
        return encoded.toString();
    }
}
