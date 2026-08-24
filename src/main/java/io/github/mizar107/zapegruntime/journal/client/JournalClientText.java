package io.github.mizar107.zapegruntime.journal.client;

import io.github.mizar107.zapegruntime.journal.JournalView;

/** Local presentation keys indexed only by the server-authorized ordinal. */
public final class JournalClientText {

    private static final String PREFIX = "journal.zapeg_runtime.heraldor.";
    private static final String[] ENTRY_IDS = {
        "first_scratch",
        "voice_without_air",
        "tracks_against_rain",
        "empty_bed",
        "ink_beneath_ink",
        "name_refused",
        "far_figure",
        "servant_of_distance",
        "ninth_bell",
        "threshold_breach",
        "drowned_road",
        "saltless_tide",
        "herald_at_low_water",
        "borrowed_voice",
        "three_knocks",
        "house_that_leans",
        "binder_knot",
        "servant_of_names",
        "ledger_of_absence",
        "door_below_door",
        "first_seal",
        "second_seal",
        "third_seal",
        "face_in_sky",
        "broken_procession",
        "ninth_witness",
        "hull_beneath_stone",
        "first_shape",
        "last_shape",
        "after_ninth"
    };

    private JournalClientText() {}

    public static int entryCount() {
        return ENTRY_IDS.length;
    }

    public static String titleKey(int ordinal) {
        return entryKey(ordinal) + ".title";
    }

    public static String bodyKey(int ordinal) {
        return entryKey(ordinal) + ".body";
    }

    public static String clueKey(int ordinal) {
        return entryKey(ordinal) + ".clue";
    }

    private static String entryKey(int ordinal) {
        if (ordinal < 0 || ordinal >= JournalView.ENTRY_COUNT) {
            throw new IllegalArgumentException("journal text ordinal is out of bounds");
        }
        return PREFIX + ENTRY_IDS[ordinal];
    }
}
