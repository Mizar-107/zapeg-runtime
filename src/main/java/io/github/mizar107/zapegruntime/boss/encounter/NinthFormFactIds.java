package io.github.mizar107.zapegruntime.boss.encounter;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Target-bound deterministic replay keys for both immutable combat proofs. */
public final class NinthFormFactIds {

    private static final String VERSION = "ninth-form-fact-v1";

    private NinthFormFactIds() {}

    public static UUID forProof(
            UUID encounterId,
            UUID targetId,
            NinthFormStoryGate.Envelope envelope,
            NinthFormBarrier.Kind kind) {
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(kind, "kind");
        String canonical = String.join(
                "\n",
                VERSION,
                encounterId.toString(),
                targetId.toString(),
                envelope.campaignId().toString(),
                Integer.toString(envelope.campaignRevision()),
                envelope.campaignFingerprint(),
                Long.toString(envelope.progressEpoch()),
                kind.storyType().serializedName(),
                kind.storySubject().toString());
        UUID result = UUID.nameUUIDFromBytes(canonical.getBytes(StandardCharsets.UTF_8));
        if (result.equals(encounterId) || result.equals(targetId)) {
            result = UUID.nameUUIDFromBytes(
                    (canonical + "\nidentity-guard").getBytes(StandardCharsets.UTF_8));
        }
        return result;
    }
}
