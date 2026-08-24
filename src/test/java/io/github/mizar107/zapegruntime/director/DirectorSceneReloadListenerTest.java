package io.github.mizar107.zapegruntime.director;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DirectorSceneReloadListenerTest {

    private static final ResourceLocation CAMPAIGN = id("heraldor");

    @AfterEach
    void resetRegistry() {
        DirectorSceneRegistry.resetForTests();
    }

    @Test
    void validCatalogParsesAsOneAtomicResource() {
        Map<ResourceLocation, DirectorSceneCatalog> parsed =
                DirectorSceneReloadListener.parseResources(Map.of(CAMPAIGN, packagedJson()));
        assertEquals(1, parsed.size());
        assertEquals(10, parsed.get(CAMPAIGN).bindings().size());
    }

    @Test
    void missingRequiredCatalogRejectsReload() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> DirectorSceneReloadListener.parseResources(Map.of()));
        assertTrue(failure.getMessage().contains("required Director catalog is missing"));
    }

    @Test
    void oneMalformedCatalogRejectsWholeCandidateSet() {
        JsonElement malformed = JsonParser.parseString("""
                {
                  "format": 1,
                  "campaign_id": "zapeg_runtime:extra",
                  "bindings": []
                }
                """);
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> DirectorSceneReloadListener.parseResources(Map.of(
                        CAMPAIGN,
                        packagedJson(),
                        id("extra"),
                        malformed)));
        assertTrue(failure.getMessage().contains("reload rejected"));
    }

    @Test
    void strictReaderRejectsDuplicateKeysAndTrailingContent() {
        assertThrows(
                com.google.gson.JsonParseException.class,
                () -> DirectorStrictJsonDocument.parse(new StringReader(
                        "{\"format\":1,\"format\":1}")));
        assertThrows(
                com.google.gson.JsonParseException.class,
                () -> DirectorStrictJsonDocument.parse(new StringReader("{} {}")));
    }

    @Test
    void registryPublicationIsImmutableAndGenerationBoundedForward() {
        DirectorSceneCatalog catalog = DirectorSceneJsonParser.parse(CAMPAIGN, packagedJson());
        DirectorSceneRegistry.publish(Map.of(CAMPAIGN, catalog));
        assertEquals(1L, DirectorSceneRegistry.current().generation());
        assertEquals(catalog, DirectorSceneRegistry.current().find(CAMPAIGN).orElseThrow());
        assertThrows(
                UnsupportedOperationException.class,
                () -> DirectorSceneRegistry.current().catalogs().clear());
    }

    private static JsonElement packagedJson() {
        try (var stream = DirectorSceneReloadListenerTest.class.getResourceAsStream(
                "/data/zapeg_runtime/heraldor_director/heraldor.json")) {
            if (stream == null) {
                throw new IllegalStateException("missing Director resource");
            }
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (java.io.IOException invalid) {
            throw new IllegalStateException(invalid);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("zapeg_runtime", path);
    }
}
