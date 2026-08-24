package io.github.mizar107.zapegruntime.director;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import io.github.mizar107.zapegruntime.scene.SceneProfile;
import io.github.mizar107.zapegruntime.story.StoryFactType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Strict parser for the bounded native Director catalog. */
public final class DirectorSceneJsonParser {

    private static final Set<String> ROOT_FIELDS =
            Set.of("format", "campaign_id", "bindings");
    private static final Set<String> BINDING_FIELDS = Set.of(
            "fact_type",
            "subject",
            "profile",
            "ttl_ticks",
            "stage",
            "presentation_variant",
            "cooldown_ticks",
            "retry_ticks");

    private DirectorSceneJsonParser() {}

    public static DirectorSceneCatalog parse(ResourceLocation id, JsonElement json) {
        try {
            JsonObject root = requireObject(json, "Director catalog");
            rejectUnknown(root, ROOT_FIELDS, "Director catalog");
            int format = requireInt(root, "format");
            if (format != DirectorSceneCatalog.FORMAT_VERSION) {
                throw new IllegalArgumentException("unsupported Director format " + format);
            }
            ResourceLocation campaignId = parseResource(
                    requireString(root, "campaign_id"), "campaign_id");
            if (!campaignId.equals(id)) {
                throw new IllegalArgumentException(
                        "campaign_id must match the Director catalog resource id");
            }
            JsonArray encoded = requireArray(require(root, "bindings"), "bindings");
            List<DirectorSceneBinding> bindings = new ArrayList<>(encoded.size());
            for (int index = 0; index < encoded.size(); index++) {
                bindings.add(parseBinding(
                        requireObject(encoded.get(index), "bindings[" + index + ']')));
            }
            return new DirectorSceneCatalog(campaignId, bindings);
        } catch (IllegalArgumentException invalid) {
            throw new JsonParseException(
                    "invalid Director catalog " + id + ": " + invalid.getMessage(), invalid);
        }
    }

    private static DirectorSceneBinding parseBinding(JsonObject binding) {
        rejectUnknown(binding, BINDING_FIELDS, "binding");
        StoryFactType type = StoryFactType.parse(requireString(binding, "fact_type"));
        ResourceLocation subject = parseResource(requireString(binding, "subject"), "subject");
        SceneProfile profile = SceneProfile.parse(requireString(binding, "profile"));
        return new DirectorSceneBinding(
                type,
                subject,
                profile,
                requireInt(binding, "ttl_ticks"),
                requireInt(binding, "stage"),
                requireInt(binding, "presentation_variant"),
                requireInt(binding, "cooldown_ticks"),
                requireInt(binding, "retry_ticks"));
    }

    private static JsonElement require(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException("missing field: " + field);
        }
        return value;
    }

    private static JsonObject requireObject(JsonElement value, String field) {
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonElement value, String field) {
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return value.getAsJsonArray();
    }

    private static String requireString(JsonObject object, String field) {
        JsonElement value = require(object, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        String result = value.getAsString();
        if (result.isEmpty() || result.length() > 128) {
            throw new IllegalArgumentException(field + " has an invalid length");
        }
        return result;
    }

    private static int requireInt(JsonObject object, String field) {
        JsonElement value = require(object, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        try {
            BigDecimal number = value.getAsBigDecimal().stripTrailingZeros();
            if (number.scale() > 0) {
                throw new ArithmeticException("fractional");
            }
            return number.intValueExact();
        } catch (ArithmeticException invalid) {
            throw new IllegalArgumentException(field + " must be an exact 32-bit integer");
        }
    }

    private static ResourceLocation parseResource(String raw, String field) {
        ResourceLocation parsed = ResourceLocation.tryParse(raw);
        if (parsed == null || !parsed.toString().equals(raw)) {
            throw new IllegalArgumentException(field + " must be a canonical resource location");
        }
        return parsed;
    }

    private static void rejectUnknown(
            JsonObject object, Set<String> allowed, String context) {
        for (String field : object.keySet()) {
            if (!allowed.contains(field)) {
                throw new IllegalArgumentException("unknown " + context + " field: " + field);
            }
        }
    }
}
