package io.github.mizar107.zapegruntime.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JournalGrantPolicyTest {

    @Test
    void automaticIssuanceNeverDuplicatesOrDropsForSpace() {
        assertEquals(
                JournalGrantPolicy.Decision.NO_STORY,
                decide(false, true, false, false, true, JournalGrantPolicy.Mode.AUTOMATIC));
        assertEquals(
                JournalGrantPolicy.Decision.PRESENT,
                decide(true, true, true, true, true, JournalGrantPolicy.Mode.AUTOMATIC));
        assertEquals(
                JournalGrantPolicy.Decision.FIRST_ISSUE,
                decide(true, true, false, false, true, JournalGrantPolicy.Mode.AUTOMATIC));
        assertEquals(
                JournalGrantPolicy.Decision.WAITING_FOR_SPACE,
                decide(true, true, false, false, false, JournalGrantPolicy.Mode.AUTOMATIC));
        assertEquals(
                JournalGrantPolicy.Decision.LOST_REQUIRES_RESTORE,
                decide(true, true, true, false, true, JournalGrantPolicy.Mode.AUTOMATIC));
    }

    @Test
    void explicitRestoreRequiresSpaceAndRotatesOnlyWhenMissing() {
        assertEquals(
                JournalGrantPolicy.Decision.PRESENT,
                decide(true, true, true, true, true, JournalGrantPolicy.Mode.RESTORE));
        assertEquals(
                JournalGrantPolicy.Decision.RESTORE,
                decide(true, true, true, false, true, JournalGrantPolicy.Mode.RESTORE));
        assertEquals(
                JournalGrantPolicy.Decision.RESTORE_WAITING_FOR_SPACE,
                decide(true, true, true, false, false, JournalGrantPolicy.Mode.RESTORE));
        assertEquals(
                JournalGrantPolicy.Decision.FIRST_ISSUE,
                decide(true, true, false, false, true, JournalGrantPolicy.Mode.RESTORE));
        assertEquals(
                JournalGrantPolicy.Decision.DATA_UNAVAILABLE,
                decide(true, false, false, false, true, JournalGrantPolicy.Mode.RESTORE));
    }

    private static JournalGrantPolicy.Decision decide(
            boolean story,
            boolean writable,
            boolean binding,
            boolean present,
            boolean space,
            JournalGrantPolicy.Mode mode) {
        return JournalGrantPolicy.decide(
                story, writable, binding, present, space, mode);
    }
}
