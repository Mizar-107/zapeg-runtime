package io.github.mizar107.zapegruntime.director;

import io.github.mizar107.zapegruntime.story.StoryTrigger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Immutable complete scene vocabulary for one campaign. */
public final class DirectorSceneCatalog {

    public static final int FORMAT_VERSION = 1;
    public static final int HERALDOR_BINDING_COUNT = 10;

    private final ResourceLocation campaignId;
    private final Map<StoryTrigger, DirectorSceneBinding> bindings;

    public DirectorSceneCatalog(
            ResourceLocation campaignId, List<DirectorSceneBinding> bindings) {
        this.campaignId = Objects.requireNonNull(campaignId, "campaignId");
        Objects.requireNonNull(bindings, "bindings");
        if (bindings.size() != HERALDOR_BINDING_COUNT) {
            throw new IllegalArgumentException(
                    "Heraldor Director catalog must contain exactly "
                            + HERALDOR_BINDING_COUNT + " bindings");
        }
        LinkedHashMap<StoryTrigger, DirectorSceneBinding> indexed = new LinkedHashMap<>();
        Set<String> presentationSignatures = new HashSet<>();
        for (DirectorSceneBinding binding : bindings) {
            Objects.requireNonNull(binding, "binding");
            if (indexed.put(binding.trigger(), binding) != null) {
                throw new IllegalArgumentException(
                        "duplicate Director scene trigger: " + binding.trigger());
            }
            if (!presentationSignatures.add(binding.presentationSignature())) {
                throw new IllegalArgumentException(
                        "duplicate Director presentation signature: "
                                + binding.presentationSignature());
            }
        }
        this.bindings = Collections.unmodifiableMap(indexed);
    }

    public ResourceLocation campaignId() {
        return campaignId;
    }

    public Map<StoryTrigger, DirectorSceneBinding> bindings() {
        return bindings;
    }

    public Optional<DirectorSceneBinding> find(StoryTrigger trigger) {
        return Optional.ofNullable(bindings.get(trigger));
    }
}
