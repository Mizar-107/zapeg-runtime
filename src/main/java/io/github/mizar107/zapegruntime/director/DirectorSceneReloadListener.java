package io.github.mizar107.zapegruntime.director;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.story.StoryCampaignRegistry;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/** All-or-nothing reload boundary for server-owned scene-to-story bindings. */
public final class DirectorSceneReloadListener extends SimpleJsonResourceReloadListener {

    public static final String DIRECTORY = "heraldor_director";
    public static final int MAX_CATALOGS = 8;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public DirectorSceneReloadListener() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected Map<ResourceLocation, JsonElement> prepare(
            ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonElement> prepared = new HashMap<>();
        List<String> errors = new ArrayList<>();
        String prefix = DIRECTORY + '/';
        resourceManager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json"))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    ResourceLocation resourceId = entry.getKey();
                    String path = resourceId.getPath();
                    if (!path.startsWith(prefix)
                            || path.length() <= prefix.length() + ".json".length()) {
                        errors.add(resourceId + ": invalid Director resource path");
                        return;
                    }
                    String catalogPath = path.substring(
                            prefix.length(), path.length() - ".json".length());
                    ResourceLocation catalogId = ResourceLocation.tryBuild(
                            resourceId.getNamespace(), catalogPath);
                    if (catalogId == null) {
                        errors.add(resourceId + ": invalid Director catalog id");
                        return;
                    }
                    try (Reader reader = entry.getValue().openAsReader()) {
                        JsonElement json = DirectorStrictJsonDocument.parse(reader);
                        if (prepared.put(catalogId, json) != null) {
                            errors.add(catalogId + ": duplicate Director catalog id");
                        }
                    } catch (IOException | JsonParseException invalid) {
                        errors.add(resourceId + ": " + invalid.getMessage());
                    }
                });
        if (prepared.size() > MAX_CATALOGS) {
            errors.add("Director catalog count exceeds " + MAX_CATALOGS);
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "Heraldor Director JSON preparation rejected: " + String.join("; ", errors));
        }
        return Map.copyOf(prepared);
    }

    @Override
    protected void apply(
            Map<ResourceLocation, JsonElement> resources,
            ResourceManager resourceManager,
            ProfilerFiller profiler) {
        Map<ResourceLocation, DirectorSceneCatalog> parsed = parseResources(resources);
        DirectorSceneRegistry.publish(parsed);
        ZapeGRuntime.LOGGER.info(
                "Loaded {} Heraldor Director catalogs at generation {}",
                parsed.size(),
                DirectorSceneRegistry.current().generation());
    }

    static Map<ResourceLocation, DirectorSceneCatalog> parseResources(
            Map<ResourceLocation, JsonElement> resources) {
        if (resources.size() > MAX_CATALOGS) {
            throw new IllegalStateException("Heraldor Director catalog count exceeds " + MAX_CATALOGS);
        }
        Map<ResourceLocation, DirectorSceneCatalog> parsed = new HashMap<>();
        List<String> errors = new ArrayList<>();
        resources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    try {
                        DirectorSceneCatalog catalog =
                                DirectorSceneJsonParser.parse(entry.getKey(), entry.getValue());
                        if (parsed.put(entry.getKey(), catalog) != null) {
                            errors.add(entry.getKey() + ": duplicate catalog id");
                        }
                    } catch (JsonParseException invalid) {
                        errors.add(invalid.getMessage());
                    }
                });
        if (!parsed.containsKey(StoryCampaignRegistry.HERALDOR_CAMPAIGN)) {
            errors.add("required Director catalog is missing: "
                    + StoryCampaignRegistry.HERALDOR_CAMPAIGN);
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "Heraldor Director reload rejected; previous registry retained: "
                            + String.join("; ", errors));
        }
        return Map.copyOf(parsed);
    }
}
