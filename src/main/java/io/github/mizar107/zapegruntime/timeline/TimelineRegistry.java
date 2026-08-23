package io.github.mizar107.zapegruntime.timeline;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;

/** Atomically published immutable timeline definitions. */
public final class TimelineRegistry {

    private static volatile Snapshot current = new Snapshot(0L, Map.of());

    private TimelineRegistry() {}

    public static Snapshot current() {
        return current;
    }

    static void publish(Map<ResourceLocation, TimelineDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions");
        if (definitions.isEmpty()) {
            throw new IllegalArgumentException(
                    "timeline reload cannot publish an empty registry");
        }
        current = new Snapshot(current.generation() + 1L, definitions);
    }

    static void resetForTests() {
        current = new Snapshot(0L, Map.of());
    }

    public record Snapshot(
            long generation,
            Map<ResourceLocation, TimelineDefinition> definitions) {

        public Snapshot {
            if (generation < 0L) {
                throw new IllegalArgumentException("registry generation cannot be negative");
            }
            Objects.requireNonNull(definitions, "definitions");
            TreeMap<ResourceLocation, TimelineDefinition> ordered = new TreeMap<>();
            definitions.forEach((id, definition) -> {
                if (!id.equals(definition.id())) {
                    throw new IllegalArgumentException(
                            "timeline registry key does not match definition id: " + id);
                }
                if (ordered.put(id, definition) != null) {
                    throw new IllegalArgumentException("duplicate timeline id: " + id);
                }
            });
            definitions = Collections.unmodifiableMap(ordered);
        }

        public Optional<TimelineDefinition> find(ResourceLocation id) {
            return Optional.ofNullable(definitions.get(id));
        }
    }
}
