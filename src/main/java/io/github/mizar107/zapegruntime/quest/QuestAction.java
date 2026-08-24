package io.github.mizar107.zapegruntime.quest;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.story.StoryFactType;
import io.github.mizar107.zapegruntime.story.StoryTrigger;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Closed set of campaign predicates owned by the vanilla quest-action bridge. */
public enum QuestAction {
    ASHEN_SCRATCH(StoryFactType.WORLD_DISCOVERY, "ashen_scratch", Mode.INTERACTION),
    BACKWARD_TRACKS(StoryFactType.WORLD_DISCOVERY, "backward_tracks", Mode.TRACKED),
    NINTH_BELL(StoryFactType.WORLD_DISCOVERY, "ninth_bell", Mode.BELL),
    DROWNED_ROAD(StoryFactType.WORLD_DISCOVERY, "drowned_road", Mode.TRACKED),
    LEANING_HOUSE(StoryFactType.WORLD_DISCOVERY, "leaning_house", Mode.TRACKED),
    UNDERDOOR(StoryFactType.WORLD_DISCOVERY, "underdoor", Mode.TRACKED),
    NINTH_WITNESS(StoryFactType.WORLD_DISCOVERY, "ninth_witness", Mode.TRACKED),
    NAME_REFUSAL(StoryFactType.RITUAL_COMPLETED, "name_refusal", Mode.RITUAL),
    BINDER_KNOT(StoryFactType.RITUAL_COMPLETED, "binder_knot", Mode.RITUAL),
    SEAL_01(StoryFactType.RITUAL_COMPLETED, "seal_01", Mode.RITUAL),
    SEAL_02(StoryFactType.RITUAL_COMPLETED, "seal_02", Mode.RITUAL),
    SEAL_03(StoryFactType.RITUAL_COMPLETED, "seal_03", Mode.RITUAL);

    private final StoryFactType factType;
    private final ResourceLocation subject;
    private final Mode mode;

    QuestAction(StoryFactType factType, String subjectPath, Mode mode) {
        this.factType = Objects.requireNonNull(factType, "factType");
        this.subject = ResourceLocation.fromNamespaceAndPath(ZapeGRuntime.MOD_ID, subjectPath);
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    public StoryFactType factType() {
        return factType;
    }

    public ResourceLocation subject() {
        return subject;
    }

    public StoryTrigger trigger() {
        return new StoryTrigger(factType, subject);
    }

    public Mode mode() {
        return mode;
    }

    public static Optional<QuestAction> forTrigger(StoryTrigger trigger) {
        Objects.requireNonNull(trigger, "trigger");
        return Arrays.stream(values()).filter(action -> action.trigger().equals(trigger)).findFirst();
    }

    public enum Mode {
        INTERACTION,
        TRACKED,
        BELL,
        RITUAL
    }
}
