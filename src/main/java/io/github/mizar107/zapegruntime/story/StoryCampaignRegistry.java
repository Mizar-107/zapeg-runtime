package io.github.mizar107.zapegruntime.story;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceLocation;

/** Atomically published immutable campaign registry. */
public final class StoryCampaignRegistry {

    public static final ResourceLocation HERALDOR_CAMPAIGN =
            Objects.requireNonNull(ResourceLocation.tryBuild("zapeg_runtime", "heraldor"));
    private static final AtomicReference<Snapshot> CURRENT =
            new AtomicReference<>(new Snapshot(0L, Map.of()));

    private StoryCampaignRegistry() {}

    public static Snapshot current() {
        return CURRENT.get();
    }

    static void publish(Map<ResourceLocation, StoryCampaignDefinition> definitions) {
        Map<ResourceLocation, StoryCampaignDefinition> immutable = Map.copyOf(definitions);
        CURRENT.updateAndGet(previous -> new Snapshot(previous.generation() + 1L, immutable));
    }

    public record Snapshot(
            long generation,
            Map<ResourceLocation, StoryCampaignDefinition> definitions) {

        public Snapshot {
            definitions = Map.copyOf(definitions);
        }

        public Optional<StoryCampaignDefinition> find(ResourceLocation id) {
            return Optional.ofNullable(definitions.get(id));
        }
    }
}
