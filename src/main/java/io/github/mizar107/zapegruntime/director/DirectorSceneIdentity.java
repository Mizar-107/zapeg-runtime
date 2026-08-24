package io.github.mizar107.zapegruntime.director;

import io.github.mizar107.zapegruntime.story.StoryCampaignDefinition;
import io.github.mizar107.zapegruntime.story.StoryFactType;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;

/** Exact server-owned identity carried only by a live Director scene. */
public record DirectorSceneIdentity(
        UUID eventId,
        UUID targetId,
        ResourceLocation campaignId,
        int campaignRevision,
        String campaignFingerprint,
        long progressEpoch,
        String nodeId,
        StoryFactType factType,
        ResourceLocation subject,
        String bindingFingerprint,
        int presentationVariant) {

    private static final UUID NIL_UUID = new UUID(0L, 0L);
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern NODE_ID = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");

    public DirectorSceneIdentity {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(campaignId, "campaignId");
        Objects.requireNonNull(campaignFingerprint, "campaignFingerprint");
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(factType, "factType");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(bindingFingerprint, "bindingFingerprint");
        if (NIL_UUID.equals(eventId)
                || NIL_UUID.equals(targetId)
                || eventId.equals(targetId)) {
            throw new IllegalArgumentException("invalid Director UUID identity");
        }
        if (campaignRevision < 1
                || campaignRevision > StoryCampaignDefinition.MAX_CAMPAIGN_REVISION) {
            throw new IllegalArgumentException("invalid Director campaign revision");
        }
        if (!SHA_256.matcher(campaignFingerprint).matches()
                || !SHA_256.matcher(bindingFingerprint).matches()) {
            throw new IllegalArgumentException("invalid Director fingerprint");
        }
        if (progressEpoch < 0L) {
            throw new IllegalArgumentException("Director progress epoch cannot be negative");
        }
        if (!NODE_ID.matcher(nodeId).matches()) {
            throw new IllegalArgumentException("invalid Director node id");
        }
        if (factType != StoryFactType.SCENE_COMPLETED
                && factType != StoryFactType.SCENE_PRESENTED) {
            throw new IllegalArgumentException("Director identity requires a scene fact type");
        }
        if (presentationVariant < 0 || presentationVariant > 15) {
            throw new IllegalArgumentException("invalid Director presentation variant");
        }
    }

    /** Stable scene seed with the binding variant encoded into the owned audio choices. */
    public long visualSeed() {
        return authoredVisualSeed(eventId, presentationVariant);
    }

    /**
     * Reuses the exact Director presentation-variant encoding without creating
     * or carrying a Director proof identity.
     */
    public static long authoredVisualSeed(UUID eventId, int presentationVariant) {
        Objects.requireNonNull(eventId, "eventId");
        if (presentationVariant < 0 || presentationVariant > 15) {
            throw new IllegalArgumentException("invalid Director presentation variant");
        }
        long mixed = mix64(eventId.getMostSignificantBits()
                ^ Long.rotateLeft(eventId.getLeastSignificantBits(), 23));
        return (mixed & ~0xffL) | presentationVariantByte(presentationVariant);
    }

    public long placementSeed() {
        return mix64(eventId.getLeastSignificantBits()
                ^ Long.rotateRight(eventId.getMostSignificantBits(), 17)
                ^ 0x6a09e667f3bcc909L);
    }

    static int presentationVariantByte(int variant) {
        // Low bits are already consumed by BREACH_01 to select the two owned
        // knock/footstep/whisper files and their target-relative placement.
        int spread = (variant * 0x35 + 0x12) & 0xff;
        return spread ^ (variant << 4);
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
