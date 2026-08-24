package io.github.mizar107.zapegruntime.boss.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class NinthFormFactIdsTest {

    @Test
    void proofIdsAreStableTargetBoundAndKindSeparated() {
        UUID encounter = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID target = UUID.fromString("20000000-0000-0000-0000-000000000002");
        NinthFormStoryGate.Envelope envelope = new NinthFormStoryGate.Envelope(
                ResourceLocation.tryBuild("zapeg_runtime", "heraldor"),
                1,
                "a".repeat(64),
                7L);
        UUID first = NinthFormFactIds.forProof(
                encounter, target, envelope, NinthFormBarrier.Kind.PHASE_ONE_COMPLETED);
        assertEquals(first, NinthFormFactIds.forProof(
                encounter, target, envelope, NinthFormBarrier.Kind.PHASE_ONE_COMPLETED));
        assertNotEquals(first, NinthFormFactIds.forProof(
                encounter, target, envelope, NinthFormBarrier.Kind.DEFEATED));
        assertNotEquals(first, NinthFormFactIds.forProof(
                encounter, UUID.randomUUID(), envelope, NinthFormBarrier.Kind.PHASE_ONE_COMPLETED));
        NinthFormStoryGate.Envelope recovered = new NinthFormStoryGate.Envelope(
                envelope.campaignId(),
                envelope.campaignRevision(),
                envelope.campaignFingerprint(),
                envelope.progressEpoch() + 1L);
        assertNotEquals(first, NinthFormFactIds.forProof(
                encounter, target, recovered, NinthFormBarrier.Kind.PHASE_ONE_COMPLETED));
    }
}
