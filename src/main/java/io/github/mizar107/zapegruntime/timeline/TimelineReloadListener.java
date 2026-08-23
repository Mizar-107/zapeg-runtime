package io.github.mizar107.zapegruntime.timeline;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.mizar107.zapegruntime.ZapeGRuntime;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/** Server datapack listener with all-or-nothing validation and publication. */
public final class TimelineReloadListener extends SimpleJsonResourceReloadListener {

    public static final String DIRECTORY = "heraldor_timelines";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public TimelineReloadListener() {
        super(GSON, DIRECTORY);
    }

    /**
     * Vanilla's JSON helper logs and skips malformed syntax. Timelines are
     * authority data, so override preparation and reject malformed resources
     * instead of silently publishing an incomplete registry.
     */
    @Override
    protected Map<ResourceLocation, JsonElement> prepare(
            ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonElement> prepared = new HashMap<>();
        List<String> errors = new ArrayList<>();
        String prefix = DIRECTORY + '/';
        resourceManager.listResources(
                        DIRECTORY,
                        location -> location.getPath().endsWith(".json"))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    ResourceLocation resourceId = entry.getKey();
                    String path = resourceId.getPath();
                    if (!path.startsWith(prefix)
                            || path.length() <= prefix.length() + ".json".length()) {
                        errors.add(resourceId + ": invalid timeline resource path");
                        return;
                    }
                    String definitionPath = path.substring(
                            prefix.length(), path.length() - ".json".length());
                    ResourceLocation definitionId = ResourceLocation.tryBuild(
                            resourceId.getNamespace(), definitionPath);
                    if (definitionId == null) {
                        errors.add(resourceId + ": invalid timeline id");
                        return;
                    }
                    try (Reader reader = entry.getValue().openAsReader()) {
                        JsonElement json = JsonParser.parseReader(reader);
                        if (json == null || json.isJsonNull()) {
                            throw new JsonParseException("empty JSON document");
                        }
                        if (prepared.put(definitionId, json) != null) {
                            errors.add(definitionId + ": duplicate timeline id");
                        }
                    } catch (IOException | JsonParseException invalid) {
                        errors.add(resourceId + ": " + invalid.getMessage());
                    }
                });
        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "Heraldor timeline JSON preparation rejected: "
                            + String.join("; ", errors));
        }
        return Map.copyOf(prepared);
    }

    @Override
    protected void apply(
            Map<ResourceLocation, JsonElement> resources,
            ResourceManager resourceManager,
            ProfilerFiller profiler) {
        Map<ResourceLocation, TimelineDefinition> parsed = parseResources(resources);
        TimelineRegistry.publish(parsed);
        ZapeGRuntime.LOGGER.info(
                "Loaded {} Heraldor timelines at generation {}",
                parsed.size(),
                TimelineRegistry.current().generation());
    }

    static Map<ResourceLocation, TimelineDefinition> parseResources(
            Map<ResourceLocation, JsonElement> resources) {
        Map<ResourceLocation, TimelineDefinition> parsed = new HashMap<>();
        List<String> errors = new ArrayList<>();
        resources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    try {
                        TimelineDefinition definition =
                                TimelineJsonParser.parse(entry.getKey(), entry.getValue());
                        if (parsed.put(entry.getKey(), definition) != null) {
                            errors.add(entry.getKey() + ": duplicate id");
                        }
                    } catch (JsonParseException invalid) {
                        errors.add(invalid.getMessage());
                    }
                });
        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "Heraldor timeline reload rejected; previous registry retained: "
                            + String.join("; ", errors));
        }
        return Map.copyOf(parsed);
    }
}
