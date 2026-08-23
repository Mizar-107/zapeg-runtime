package io.github.mizar107.zapegruntime.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TimelineRegistryTest {

    @AfterEach
    void resetRegistry() {
        TimelineRegistry.resetForTests();
    }

    @Test
    void bundledTimelineIsValidDataAndLoadsThroughProductionParser() throws IOException {
        String path = "/data/zapeg_runtime/heraldor_timelines/dread_approach_01.json";
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream);
            JsonElement json = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            TimelineDefinition definition = TimelineJsonParser.parse(
                    ResourceLocation.fromNamespaceAndPath(
                            "zapeg_runtime", "dread_approach_01"),
                    json);
            assertEquals(3, definition.actions().size());
            assertEquals("peripheral_witness", definition.actions().get(0).id());
            assertEquals("threshold", definition.actions().get(2).id());
        }
    }

    @Test
    void publicationIsImmutableOrderedAndGenerationBounded() {
        TimelineDefinition definition = TimelineDefinitionTest.definition(
                java.util.List.of(TimelineDefinitionTest.action("cue", 1, 20)));
        TimelineRegistry.publish(Map.of(definition.id(), definition));

        TimelineRegistry.Snapshot snapshot = TimelineRegistry.current();
        assertEquals(1L, snapshot.generation());
        assertEquals(definition, snapshot.find(definition.id()).orElseThrow());
        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.definitions().clear());
        assertThrows(IllegalArgumentException.class, () -> TimelineRegistry.publish(Map.of()));
        assertEquals(snapshot, TimelineRegistry.current());
    }

    @Test
    void oneInvalidResourceRejectsPreparedBatchBeforePublication() {
        ResourceLocation goodId = TimelineDefinitionTest.id();
        ResourceLocation badId = ResourceLocation.fromNamespaceAndPath(
                "zapeg_runtime", "bad");
        JsonElement good = JsonParser.parseString(validJson());
        JsonElement bad = JsonParser.parseString(validJson().replace(
                "\"format\": 1", "\"format\": 99"));

        assertThrows(
                IllegalStateException.class,
                () -> TimelineReloadListener.parseResources(Map.of(
                        goodId, good,
                        badId, bad)));
        assertEquals(0L, TimelineRegistry.current().generation());
        assertTrue(TimelineRegistry.current().definitions().isEmpty());
    }

    @Test
    void malformedJsonSyntaxFailsPreparationInsteadOfBeingSilentlySkipped() {
        ResourceLocation path = ResourceLocation.fromNamespaceAndPath(
                "zapeg_runtime", "heraldor_timelines/broken.json");
        TimelineReloadListener listener = new TimelineReloadListener();

        assertThrows(
                IllegalStateException.class,
                () -> listener.prepare(resources(path, "{ broken"), null));
        Map<ResourceLocation, JsonElement> prepared = listener.prepare(
                resources(path, validJson()), null);
        assertEquals(
                Set.of(ResourceLocation.fromNamespaceAndPath(
                        "zapeg_runtime", "broken")),
                prepared.keySet());
    }

    private static String validJson() {
        return """
                {
                  "format": 1,
                  "duration_ticks": 100,
                  "policies": {
                    "disconnect": "pause",
                    "restart": "pause",
                    "dimension_change": "fail",
                    "death": "fail"
                  },
                  "actions": [{
                    "id": "cue",
                    "at_tick": 1,
                    "deadline_tick": 20,
                    "type": "scene",
                    "profile": "echo_01"
                  }]
                }
                """;
    }

    private static ResourceManager resources(ResourceLocation id, String content) {
        Resource resource = new Resource(
                null,
                () -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        Map<ResourceLocation, Resource> resources = Map.of(id, resource);
        return new ResourceManager() {
            @Override
            public Set<String> getNamespaces() {
                return Set.of(id.getNamespace());
            }

            @Override
            public Optional<Resource> getResource(ResourceLocation location) {
                return Optional.ofNullable(resources.get(location));
            }

            @Override
            public List<Resource> getResourceStack(ResourceLocation location) {
                return getResource(location).map(List::of).orElseGet(List::of);
            }

            @Override
            public Map<ResourceLocation, Resource> listResources(
                    String directory, Predicate<ResourceLocation> predicate) {
                return resources.entrySet().stream()
                        .filter(entry -> entry.getKey().getPath().startsWith(directory + '/'))
                        .filter(entry -> predicate.test(entry.getKey()))
                        .collect(java.util.stream.Collectors.toUnmodifiableMap(
                                Map.Entry::getKey, Map.Entry::getValue));
            }

            @Override
            public Map<ResourceLocation, List<Resource>> listResourceStacks(
                    String directory, Predicate<ResourceLocation> predicate) {
                return Map.of();
            }

            @Override
            public Stream<PackResources> listPacks() {
                return Stream.empty();
            }
        };
    }
}
