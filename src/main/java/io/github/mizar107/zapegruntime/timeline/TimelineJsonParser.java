package io.github.mizar107.zapegruntime.timeline;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import io.github.mizar107.zapegruntime.scene.SceneProfile;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Strict parser: unknown fields and lossy numbers reject the entire reload. */
public final class TimelineJsonParser {

    private static final Set<String> ROOT_FIELDS =
            Set.of("format", "duration_ticks", "policies", "actions");
    private static final Set<String> POLICY_FIELDS =
            Set.of("disconnect", "restart", "dimension_change", "death");
    private static final Set<String> ACTION_FIELDS = Set.of(
            "id",
            "at_tick",
            "deadline_tick",
            "retry_interval_ticks",
            "required",
            "type",
            "profile",
            "ttl_ticks",
            "stage");

    private TimelineJsonParser() {}

    public static TimelineDefinition parse(ResourceLocation id, JsonElement json) {
        try {
            JsonObject root = requireObject(json, "timeline");
            rejectUnknown(root, ROOT_FIELDS, "timeline");
            int format = requireInt(root, "format");
            if (format != TimelineDefinition.FORMAT_VERSION) {
                throw new IllegalArgumentException(
                        "unsupported timeline format " + format);
            }
            int duration = requireInt(root, "duration_ticks");
            TimelinePolicies policies = parsePolicies(
                    requireObject(require(root, "policies"), "policies"));
            JsonArray encodedActions = requireArray(require(root, "actions"), "actions");
            List<TimelineAction> actions = new ArrayList<>(encodedActions.size());
            for (int index = 0; index < encodedActions.size(); index++) {
                actions.add(parseAction(
                        requireObject(encodedActions.get(index), "actions[" + index + "]")));
            }
            return new TimelineDefinition(id, duration, policies, actions);
        } catch (IllegalArgumentException invalid) {
            throw new JsonParseException("invalid timeline " + id + ": "
                    + invalid.getMessage(), invalid);
        }
    }

    private static TimelinePolicies parsePolicies(JsonObject policies) {
        rejectUnknown(policies, POLICY_FIELDS, "policies");
        return new TimelinePolicies(
                TimelinePolicies.ParsedPolicy.parse(
                        TimelinePolicies.Disconnect.class,
                        requireString(policies, "disconnect"),
                        "disconnect"),
                TimelinePolicies.ParsedPolicy.parse(
                        TimelinePolicies.Restart.class,
                        requireString(policies, "restart"),
                        "restart"),
                TimelinePolicies.ParsedPolicy.parse(
                        TimelinePolicies.DimensionChange.class,
                        requireString(policies, "dimension_change"),
                        "dimension_change"),
                TimelinePolicies.ParsedPolicy.parse(
                        TimelinePolicies.Death.class,
                        requireString(policies, "death"),
                        "death"));
    }

    private static TimelineAction parseAction(JsonObject action) {
        rejectUnknown(action, ACTION_FIELDS, "action");
        String type = requireString(action, "type");
        if (!"scene".equals(type)) {
            throw new IllegalArgumentException("unknown timeline action type: " + type);
        }
        SceneProfile profile = SceneProfile.parse(requireString(action, "profile"));
        int ttlTicks = optionalInt(action, "ttl_ticks", profile.defaultTtlTicks());
        return new TimelineAction(
                requireString(action, "id"),
                requireInt(action, "at_tick"),
                requireInt(action, "deadline_tick"),
                optionalInt(action, "retry_interval_ticks", 20),
                optionalBoolean(action, "required", true),
                profile,
                ttlTicks,
                optionalInt(action, "stage", 0));
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
        return value.getAsString();
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

    private static int optionalInt(JsonObject object, String field, int fallback) {
        return object.has(field) ? requireInt(object, field) : fallback;
    }

    private static boolean optionalBoolean(
            JsonObject object, String field, boolean fallback) {
        if (!object.has(field)) {
            return fallback;
        }
        JsonElement value = require(object, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(field + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static void rejectUnknown(
            JsonObject object, Set<String> allowed, String context) {
        for (String field : object.keySet()) {
            if (!allowed.contains(field)) {
                throw new IllegalArgumentException(
                        "unknown " + context + " field: " + field);
            }
        }
    }
}
