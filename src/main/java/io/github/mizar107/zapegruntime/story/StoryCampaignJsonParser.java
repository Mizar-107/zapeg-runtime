package io.github.mizar107.zapegruntime.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Exact-schema parser for hidden-journal campaign resources. */
public final class StoryCampaignJsonParser {

    private static final Set<String> ROOT_FIELDS =
            Set.of("schema", "campaign_id", "revision", "entry", "nodes");
    private static final Set<String> COMMON_NODE_FIELDS =
            Set.of("id", "ordinal", "chapter", "journal_key", "terminal");
    private static final Set<String> NON_TERMINAL_NODE_FIELDS =
            Set.of(
                    "id",
                    "ordinal",
                    "chapter",
                    "journal_key",
                    "terminal",
                    "advance_on",
                    "next");
    private static final Set<String> TRIGGER_FIELDS = Set.of("type", "subject");

    private StoryCampaignJsonParser() {}

    public static StoryCampaignDefinition parse(
            ResourceLocation definitionId, JsonElement document) {
        try {
            JsonObject root = requireObject(document, "campaign root");
            requireExactFields(root, ROOT_FIELDS, "campaign root");
            int schema = requireInt(root, "schema");
            if (schema != StoryCampaignDefinition.SCHEMA_VERSION) {
                throw new IllegalArgumentException("unsupported campaign schema: " + schema);
            }
            ResourceLocation declaredId = requireResourceLocation(root, "campaign_id");
            if (!definitionId.equals(declaredId)) {
                throw new IllegalArgumentException(
                        "campaign_id must match datapack resource id " + definitionId);
            }
            int revision = requireInt(root, "revision");
            String entry = requireString(root, "entry");
            JsonArray encodedNodes = requireArray(root, "nodes");
            if (encodedNodes.size() != StoryCampaignDefinition.REQUIRED_NODE_COUNT) {
                throw new IllegalArgumentException(
                        "nodes must contain exactly "
                                + StoryCampaignDefinition.REQUIRED_NODE_COUNT
                                + " entries");
            }
            List<StoryNode> nodes = new ArrayList<>(encodedNodes.size());
            for (int index = 0; index < encodedNodes.size(); index++) {
                nodes.add(parseNode(encodedNodes.get(index), index));
            }
            return new StoryCampaignDefinition(declaredId, revision, entry, nodes);
        } catch (IllegalArgumentException invalid) {
            throw new JsonParseException(definitionId + ": " + invalid.getMessage(), invalid);
        }
    }

    private static StoryNode parseNode(JsonElement encoded, int index) {
        JsonObject node = requireObject(encoded, "node " + index);
        boolean terminal = requireBoolean(node, "terminal");
        requireExactFields(
                node,
                terminal ? COMMON_NODE_FIELDS : NON_TERMINAL_NODE_FIELDS,
                "node " + index);
        String id = requireString(node, "id");
        int ordinal = requireInt(node, "ordinal");
        int chapter = requireInt(node, "chapter");
        String journalKey = requireString(node, "journal_key");
        if (terminal) {
            return new StoryNode(id, ordinal, chapter, journalKey, true, null, null);
        }
        JsonObject encodedTrigger = requireObject(node.get("advance_on"), "advance_on");
        requireExactFields(encodedTrigger, TRIGGER_FIELDS, "advance_on");
        StoryFactType type = StoryFactType.parse(requireString(encodedTrigger, "type"));
        ResourceLocation subject = requireResourceLocation(encodedTrigger, "subject");
        return new StoryNode(
                id,
                ordinal,
                chapter,
                journalKey,
                false,
                new StoryTrigger(type, subject),
                requireString(node, "next"));
    }

    private static JsonObject requireObject(JsonElement value, String description) {
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(description + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return value.getAsJsonArray();
    }

    private static String requireString(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        String result = value.getAsString();
        if (result.isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be empty");
        }
        return result;
    }

    private static int requireInt(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        try {
            BigDecimal decimal = value.getAsBigDecimal();
            return decimal.intValueExact();
        } catch (ArithmeticException invalid) {
            throw new IllegalArgumentException(field + " must be an exact 32-bit integer");
        }
    }

    private static boolean requireBoolean(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(field + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static ResourceLocation requireResourceLocation(JsonObject object, String field) {
        String encoded = requireString(object, field);
        if (encoded.length() > 128) {
            throw new IllegalArgumentException(field + " exceeds maximum resource id length");
        }
        ResourceLocation parsed = ResourceLocation.tryParse(encoded);
        if (parsed == null || !parsed.toString().equals(encoded)) {
            throw new IllegalArgumentException(field + " must be a canonical resource id");
        }
        return parsed;
    }

    private static void requireExactFields(
            JsonObject object, Set<String> expected, String description) {
        Set<String> actual = new HashSet<>(object.keySet());
        if (!actual.equals(expected)) {
            Set<String> missing = new HashSet<>(expected);
            missing.removeAll(actual);
            Set<String> unknown = new HashSet<>(actual);
            unknown.removeAll(expected);
            throw new IllegalArgumentException(
                    description + " fields mismatch; missing=" + missing + " unknown=" + unknown);
        }
    }
}
