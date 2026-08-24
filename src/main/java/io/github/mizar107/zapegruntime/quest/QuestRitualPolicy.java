package io.github.mizar107.zapegruntime.quest;

import io.github.mizar107.zapegruntime.story.StoryService;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;

/** The sole offering-consumption gate: campaign commit always happens first. */
public final class QuestRitualPolicy {

    private QuestRitualPolicy() {}

    public static boolean consumeAfterApplied(
            ItemStack offering, int requiredCount, StoryService.SubmissionResult submission) {
        Objects.requireNonNull(offering, "offering");
        Objects.requireNonNull(submission, "submission");
        if (requiredCount < 1) {
            throw new IllegalArgumentException("required offering count must be positive");
        }
        int consumption = committedConsumption(
                offering.getCount(), requiredCount, submission.status());
        if (consumption == 0) {
            return false;
        }
        offering.shrink(consumption);
        return true;
    }

    static int committedConsumption(
            int availableCount,
            int requiredCount,
            StoryService.SubmissionStatus status) {
        Objects.requireNonNull(status, "status");
        if (availableCount < 0 || requiredCount < 1) {
            throw new IllegalArgumentException("offering counts are invalid");
        }
        if (status != StoryService.SubmissionStatus.APPLIED) {
            return 0;
        }
        if (availableCount < requiredCount) {
            throw new IllegalStateException("committed ritual offering changed before consumption");
        }
        return requiredCount;
    }
}
