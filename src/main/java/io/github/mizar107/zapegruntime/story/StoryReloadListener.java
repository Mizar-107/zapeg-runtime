package io.github.mizar107.zapegruntime.story;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import io.github.mizar107.zapegruntime.ZapeGRuntime;
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

/** All-or-nothing, duplicate-aware datapack loader for story campaigns. */
public final class StoryReloadListener extends SimpleJsonResourceReloadListener {

    public static final String DIRECTORY = "heraldor_story";
    public static final int MAX_CAMPAIGNS = 8;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public StoryReloadListener() {
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
                        errors.add(resourceId + ": invalid story resource path");
                        return;
                    }
                    String definitionPath = path.substring(
                            prefix.length(), path.length() - ".json".length());
                    ResourceLocation definitionId = ResourceLocation.tryBuild(
                            resourceId.getNamespace(), definitionPath);
                    if (definitionId == null) {
                        errors.add(resourceId + ": invalid campaign id");
                        return;
                    }
                    try (Reader reader = entry.getValue().openAsReader()) {
                        JsonElement json = StoryStrictJsonDocument.parse(reader);
                        if (prepared.put(definitionId, json) != null) {
                            errors.add(definitionId + ": duplicate campaign id");
                        }
                    } catch (IOException | JsonParseException invalid) {
                        errors.add(resourceId + ": " + invalid.getMessage());
                    }
                });
        if (prepared.size() > MAX_CAMPAIGNS) {
            errors.add("campaign count exceeds " + MAX_CAMPAIGNS);
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "Heraldor story JSON preparation rejected: " + String.join("; ", errors));
        }
        return Map.copyOf(prepared);
    }

    @Override
    protected void apply(
            Map<ResourceLocation, JsonElement> resources,
            ResourceManager resourceManager,
            ProfilerFiller profiler) {
        Map<ResourceLocation, StoryCampaignDefinition> parsed = parseResources(resources);
        StoryCampaignRegistry.publish(parsed);
        ZapeGRuntime.LOGGER.info(
                "Loaded {} Heraldor story campaigns at generation {}",
                parsed.size(),
                StoryCampaignRegistry.current().generation());
    }

    static Map<ResourceLocation, StoryCampaignDefinition> parseResources(
            Map<ResourceLocation, JsonElement> resources) {
        if (resources.size() > MAX_CAMPAIGNS) {
            throw new IllegalStateException("Heraldor story campaign count exceeds " + MAX_CAMPAIGNS);
        }
        Map<ResourceLocation, StoryCampaignDefinition> parsed = new HashMap<>();
        List<String> errors = new ArrayList<>();
        resources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    try {
                        StoryCampaignDefinition definition =
                                StoryCampaignJsonParser.parse(entry.getKey(), entry.getValue());
                        if (parsed.put(entry.getKey(), definition) != null) {
                            errors.add(entry.getKey() + ": duplicate campaign id");
                        }
                    } catch (JsonParseException invalid) {
                        errors.add(invalid.getMessage());
                    }
                });
        if (!parsed.containsKey(StoryCampaignRegistry.HERALDOR_CAMPAIGN)) {
            errors.add("required campaign is missing: " + StoryCampaignRegistry.HERALDOR_CAMPAIGN);
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "Heraldor story reload rejected; previous registry retained: "
                            + String.join("; ", errors));
        }
        return Map.copyOf(parsed);
    }
}
