package io.github.mizar107.zapegruntime.journal;

/** The complete client-visible authorization envelope: one prefix mask and one ordinal. */
public record JournalView(int unlockedMask, int currentOrdinal) {

    public static final int ENTRY_COUNT = 30;
    public static final int CHAPTER_COUNT = 5;
    public static final int ENTRIES_PER_CHAPTER = 6;
    public static final int ALL_ENTRY_BITS = (1 << ENTRY_COUNT) - 1;

    public JournalView {
        if (currentOrdinal < 0 || currentOrdinal >= ENTRY_COUNT) {
            throw new IllegalArgumentException("current journal ordinal is out of bounds");
        }
        if ((unlockedMask & ~ALL_ENTRY_BITS) != 0) {
            throw new IllegalArgumentException("journal mask contains unsupported bits");
        }
        if (unlockedMask != prefixMask(currentOrdinal)) {
            throw new IllegalArgumentException("journal authorization must be an exact prefix");
        }
    }

    public static JournalView through(int currentOrdinal) {
        return new JournalView(prefixMask(currentOrdinal), currentOrdinal);
    }

    public boolean unlocked(int ordinal) {
        return ordinal >= 0
                && ordinal < ENTRY_COUNT
                && (unlockedMask & (1 << ordinal)) != 0;
    }

    public int chapterFor(int ordinal) {
        if (ordinal < 0 || ordinal >= ENTRY_COUNT) {
            throw new IllegalArgumentException("journal ordinal is out of bounds");
        }
        return ordinal / ENTRIES_PER_CHAPTER + 1;
    }

    public boolean chapterUnlocked(int chapter) {
        return chapter >= 1 && chapter <= chapterFor(currentOrdinal);
    }

    public int latestInChapter(int chapter) {
        if (chapter < 1 || chapter > CHAPTER_COUNT || !chapterUnlocked(chapter)) {
            throw new IllegalArgumentException("journal chapter is locked or invalid");
        }
        int finalOrdinal = chapter * ENTRIES_PER_CHAPTER - 1;
        return Math.min(finalOrdinal, currentOrdinal);
    }

    private static int prefixMask(int ordinal) {
        if (ordinal < 0 || ordinal >= ENTRY_COUNT) {
            throw new IllegalArgumentException("current journal ordinal is out of bounds");
        }
        return ordinal == ENTRY_COUNT - 1 ? ALL_ENTRY_BITS : (1 << (ordinal + 1)) - 1;
    }
}
