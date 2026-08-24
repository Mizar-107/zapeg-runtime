package io.github.mizar107.zapegruntime.journal;

/** Pure, executable issuance policy shared by automatic grant and explicit restore. */
public final class JournalGrantPolicy {

    private JournalGrantPolicy() {}

    public static Decision decide(
            boolean storyExists,
            boolean bindingDataWritable,
            boolean hasBinding,
            boolean hasActiveJournal,
            boolean hasFreeSlot,
            Mode mode) {
        if (!storyExists) {
            return Decision.NO_STORY;
        }
        if (!bindingDataWritable) {
            return Decision.DATA_UNAVAILABLE;
        }
        if (hasActiveJournal) {
            return Decision.PRESENT;
        }
        if (!hasFreeSlot) {
            return mode == Mode.RESTORE
                    ? Decision.RESTORE_WAITING_FOR_SPACE
                    : Decision.WAITING_FOR_SPACE;
        }
        if (!hasBinding) {
            return Decision.FIRST_ISSUE;
        }
        return mode == Mode.RESTORE
                ? Decision.RESTORE
                : Decision.LOST_REQUIRES_RESTORE;
    }

    public enum Mode {
        AUTOMATIC,
        RESTORE
    }

    public enum Decision {
        NO_STORY,
        DATA_UNAVAILABLE,
        PRESENT,
        FIRST_ISSUE,
        WAITING_FOR_SPACE,
        LOST_REQUIRES_RESTORE,
        RESTORE_WAITING_FOR_SPACE,
        RESTORE
    }
}
