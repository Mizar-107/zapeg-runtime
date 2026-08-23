package io.github.mizar107.zapegruntime.story;

import com.google.gson.JsonElement;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

final class StoryCampaignTestFixtures {

    private static final String RESOURCE =
            "/data/zapeg_runtime/heraldor_story/heraldor.json";

    private StoryCampaignTestFixtures() {}

    static JsonElement document() {
        InputStream stream = StoryCampaignTestFixtures.class.getResourceAsStream(RESOURCE);
        if (stream == null) {
            throw new IllegalStateException("missing test campaign resource " + RESOURCE);
        }
        try (InputStreamReader reader =
                new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return StoryStrictJsonDocument.parse(reader);
        } catch (IOException impossible) {
            throw new IllegalStateException("failed to close campaign resource", impossible);
        }
    }

    static StoryCampaignDefinition campaign() {
        return StoryCampaignJsonParser.parse(
                StoryCampaignRegistry.HERALDOR_CAMPAIGN, document());
    }

    static ResourceLocation id(String path) {
        return id("zapeg_runtime", path);
    }

    static ResourceLocation id(String namespace, String path) {
        return Objects.requireNonNull(ResourceLocation.tryBuild(namespace, path));
    }
}
