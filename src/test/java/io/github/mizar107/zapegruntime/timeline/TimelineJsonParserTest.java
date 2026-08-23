package io.github.mizar107.zapegruntime.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.mizar107.zapegruntime.scene.SceneProfile;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class TimelineJsonParserTest {

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            "zapeg_runtime", "parser_test");

    @Test
    void parsesStrictAuthoredTimelineAndCanonicalisesActions() {
        TimelineDefinition definition = parse(validJson());

        assertEquals(ID, definition.id());
        assertEquals(200, definition.durationTicks());
        assertEquals(TimelinePolicies.Disconnect.PAUSE, definition.policies().disconnect());
        assertEquals("early", definition.actions().get(0).id());
        assertEquals(SceneProfile.THRESHOLD_01, definition.actions().get(0).profile());
        assertEquals(160, definition.actions().get(0).ttlTicks());
        assertEquals("late", definition.actions().get(1).id());
    }

    @Test
    void rejectsUnknownFieldsTypesPoliciesAndLossyNumbers() {
        assertRejected(validJson().replace(
                "\"duration_ticks\": 200",
                "\"duration_ticks\": 200, \"duraton_ticks\": 4"));
        assertRejected(validJson().replace("\"type\": \"scene\"", "\"type\": \"shell\""));
        assertRejected(validJson().replace("\"disconnect\": \"pause\"", "\"disconnect\": \"wait\""));
        assertRejected(validJson().replace("\"at_tick\": 10", "\"at_tick\": 10.5"));
        assertRejected(validJson().replace("\"format\": 1", "\"format\": 2"));
    }

    @Test
    void rejectsDuplicateIdsInvalidStagesAndDeadlineOverflow() {
        assertRejected(validJson().replace("\"id\": \"late\"", "\"id\": \"early\""));
        assertRejected(validJson().replace(
                "\"profile\": \"threshold_01\"",
                "\"profile\": \"threshold_01\", \"stage\": 1"));
        assertRejected(validJson().replace("\"deadline_tick\": 190", "\"deadline_tick\": 201"));
    }

    private static TimelineDefinition parse(String json) {
        return TimelineJsonParser.parse(ID, JsonParser.parseString(json));
    }

    private static void assertRejected(String json) {
        assertThrows(JsonParseException.class, () -> parse(json));
    }

    private static String validJson() {
        return """
                {
                  "format": 1,
                  "duration_ticks": 200,
                  "policies": {
                    "disconnect": "pause",
                    "restart": "pause",
                    "dimension_change": "fail",
                    "death": "fail"
                  },
                  "actions": [
                    {
                      "id": "late",
                      "at_tick": 100,
                      "deadline_tick": 190,
                      "type": "scene",
                      "profile": "echo_01"
                    },
                    {
                      "id": "early",
                      "at_tick": 10,
                      "deadline_tick": 80,
                      "retry_interval_ticks": 4,
                      "required": false,
                      "type": "scene",
                      "profile": "threshold_01",
                      "ttl_ticks": 160
                    }
                  ]
                }
                """;
    }
}
