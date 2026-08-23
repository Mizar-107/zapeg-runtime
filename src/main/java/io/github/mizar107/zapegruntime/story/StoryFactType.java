package io.github.mizar107.zapegruntime.story;

import java.util.Arrays;

/** Closed vocabulary for server-authored campaign evidence. */
public enum StoryFactType {
    WORLD_DISCOVERY("world_discovery"),
    SCENE_PRESENTED("scene_presented"),
    SCENE_COMPLETED("scene_completed"),
    JOURNAL_DISCOVERY("journal_discovery"),
    RITUAL_COMPLETED("ritual_completed"),
    SERVANT_DEFEATED("servant_defeated"),
    BOSS_PHASE_COMPLETED("boss_phase_completed"),
    BOSS_DEFEATED("boss_defeated");

    private final String serializedName;

    StoryFactType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static StoryFactType parse(String value) {
        return Arrays.stream(values())
                .filter(type -> type.serializedName.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown story fact type: " + value));
    }
}
