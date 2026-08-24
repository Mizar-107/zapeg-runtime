package io.github.mizar107.zapegruntime.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JournalViewTest {

    @Test
    void authorizedViewsAreExactPrefixesAcrossAllThirtyEntries() {
        for (int ordinal = 0; ordinal < JournalView.ENTRY_COUNT; ordinal++) {
            JournalView view = JournalView.through(ordinal);
            assertEquals(ordinal, view.currentOrdinal());
            for (int candidate = 0; candidate < JournalView.ENTRY_COUNT; candidate++) {
                assertEquals(candidate <= ordinal, view.unlocked(candidate));
            }
        }
        assertEquals(JournalView.ALL_ENTRY_BITS, JournalView.through(29).unlockedMask());
    }

    @Test
    void chaptersExposeOnlyTheirLatestAuthorizedEntry() {
        JournalView view = JournalView.through(18);
        assertTrue(view.chapterUnlocked(4));
        assertFalse(view.chapterUnlocked(5));
        assertEquals(5, view.latestInChapter(1));
        assertEquals(17, view.latestInChapter(3));
        assertEquals(18, view.latestInChapter(4));
        assertThrows(IllegalArgumentException.class, () -> view.latestInChapter(5));
    }

    @Test
    void sparseMasksFutureBitsAndBadOrdinalsFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> new JournalView(0b101, 2));
        assertThrows(IllegalArgumentException.class, () -> new JournalView(1 << 30, 0));
        assertThrows(IllegalArgumentException.class, () -> JournalView.through(-1));
        assertThrows(IllegalArgumentException.class, () -> JournalView.through(30));
    }
}
