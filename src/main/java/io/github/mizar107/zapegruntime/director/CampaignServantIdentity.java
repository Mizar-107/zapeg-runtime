package io.github.mizar107.zapegruntime.director;

import io.github.mizar107.zapegruntime.servant.ServantArchetype;
import io.github.mizar107.zapegruntime.story.StoryCampaignDefinition;
import io.github.mizar107.zapegruntime.story.StoryFactType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;

/** Deterministic restart identity for one automatic campaign Servant. */
public final class CampaignServantIdentity {

    private static final String VERSION = "zapeg-runtime:campaign-servant:v1";
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern NODE_ID = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private CampaignServantIdentity() {}

    public static UUID derive(
            UUID playerId,
            ResourceLocation campaignId,
            int campaignRevision,
            String campaignFingerprint,
            long recoveryEpoch,
            String nodeId,
            StoryFactType factType,
            ResourceLocation subject,
            ServantArchetype archetype) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(campaignId, "campaignId");
        Objects.requireNonNull(campaignFingerprint, "campaignFingerprint");
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(factType, "factType");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(archetype, "archetype");
        if (NIL_UUID.equals(playerId)) {
            throw new IllegalArgumentException("campaign Servant player UUID must not be nil");
        }
        if (campaignRevision < 1
                || campaignRevision > StoryCampaignDefinition.MAX_CAMPAIGN_REVISION
                || !SHA_256.matcher(campaignFingerprint).matches()
                || recoveryEpoch < 0L
                || factType != StoryFactType.SERVANT_DEFEATED
                || !NODE_ID.matcher(nodeId).matches()) {
            throw new IllegalArgumentException("campaign Servant envelope is invalid");
        }

        UUID result = hash(
                playerId,
                campaignId,
                campaignRevision,
                campaignFingerprint,
                recoveryEpoch,
                nodeId,
                factType,
                subject,
                archetype,
                "primary");
        if (NIL_UUID.equals(result) || playerId.equals(result)) {
            result = hash(
                    playerId,
                    campaignId,
                    campaignRevision,
                    campaignFingerprint,
                    recoveryEpoch,
                    nodeId,
                    factType,
                    subject,
                    archetype,
                    "identity_guard");
        }
        if (NIL_UUID.equals(result) || playerId.equals(result)) {
            throw new IllegalStateException("campaign Servant UUID identity guard failed");
        }
        return result;
    }

    private static UUID hash(
            UUID playerId,
            ResourceLocation campaignId,
            int campaignRevision,
            String campaignFingerprint,
            long recoveryEpoch,
            String nodeId,
            StoryFactType factType,
            ResourceLocation subject,
            ServantArchetype archetype,
            String guard) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            append(digest, VERSION);
            append(digest, guard);
            append(digest, playerId.toString());
            append(digest, campaignId.toString());
            append(digest, Integer.toString(campaignRevision));
            append(digest, campaignFingerprint);
            append(digest, Long.toString(recoveryEpoch));
            append(digest, nodeId);
            append(digest, factType.serializedName());
            append(digest, subject.toString());
            append(digest, archetype.id());
            byte[] value = digest.digest();
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
