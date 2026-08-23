package io.github.mizar107.zapegruntime.story;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Exact typed predicate attached to a campaign node. */
public record StoryTrigger(StoryFactType type, ResourceLocation subject) {

    public StoryTrigger {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(subject, "subject");
    }
}
