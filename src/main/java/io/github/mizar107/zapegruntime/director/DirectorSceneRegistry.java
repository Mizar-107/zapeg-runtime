package io.github.mizar107.zapegruntime.director;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceLocation;

/** Atomically published, immutable Director scene catalogs. */
public final class DirectorSceneRegistry {

    private static final AtomicReference<Snapshot> CURRENT =
            new AtomicReference<>(new Snapshot(0L, Map.of()));

    private DirectorSceneRegistry() {}

    public static Snapshot current() {
        return CURRENT.get();
    }

    static void publish(Map<ResourceLocation, DirectorSceneCatalog> catalogs) {
        Objects.requireNonNull(catalogs, "catalogs");
        if (catalogs.isEmpty()) {
            throw new IllegalArgumentException("Director reload cannot publish an empty registry");
        }
        CURRENT.updateAndGet(previous -> new Snapshot(previous.generation() + 1L, catalogs));
    }

    static void resetForTests() {
        CURRENT.set(new Snapshot(0L, Map.of()));
    }

    public record Snapshot(
            long generation, Map<ResourceLocation, DirectorSceneCatalog> catalogs) {

        public Snapshot {
            if (generation < 0L) {
                throw new IllegalArgumentException("registry generation cannot be negative");
            }
            Objects.requireNonNull(catalogs, "catalogs");
            TreeMap<ResourceLocation, DirectorSceneCatalog> ordered = new TreeMap<>();
            catalogs.forEach((id, catalog) -> {
                if (!id.equals(catalog.campaignId())) {
                    throw new IllegalArgumentException(
                            "Director registry key does not match campaign id: " + id);
                }
                if (ordered.put(id, catalog) != null) {
                    throw new IllegalArgumentException("duplicate Director campaign id: " + id);
                }
            });
            catalogs = Collections.unmodifiableMap(ordered);
        }

        public Optional<DirectorSceneCatalog> find(ResourceLocation campaignId) {
            return Optional.ofNullable(catalogs.get(campaignId));
        }
    }
}
