package io.github.mizar107.zapegruntime.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.mizar107.zapegruntime.server.LegacyCommandDeprecationGuard.LegacySubtree;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyCommandDeprecationGuardTest {

    @Test
    void exactServantAndVoiceSubtreesAreBlockedAtAnyDepth() {
        assertEquals(
                LegacySubtree.SERVANT,
                LegacyCommandDeprecationGuard.classifyNodeNames(
                        List.of("zapeg-lore", "servant")));
        assertEquals(
                LegacySubtree.SERVANT,
                LegacyCommandDeprecationGuard.classifyNodeNames(
                        List.of("zapeg-lore", "servant", "awaken", "target")));
        assertEquals(
                LegacySubtree.VOICE,
                LegacyCommandDeprecationGuard.classifyNodeNames(
                        List.of("zapeg-lore", "voice", "rehearse", "target")));
    }

    @Test
    void everyOtherLegacyChildAndNearMatchPassesThrough() {
        List<List<String>> allowedPaths = List.of(
                List.of(),
                List.of("zapeg-lore"),
                List.of("zapeg-lore", "story"),
                List.of("zapeg-lore", "rehearse"),
                List.of("zapeg-lore", "servantish"),
                List.of("zapeg-lore", "Voice"),
                List.of("heraldor", "servant"),
                List.of("execute", "run", "zapeg-lore", "servant"));

        for (List<String> path : allowedPaths) {
            assertEquals(
                    LegacySubtree.NONE,
                    LegacyCommandDeprecationGuard.classifyNodeNames(path),
                    () -> "unexpectedly blocked parsed path " + path);
        }
    }

    @Test
    void redirectedCommandIsJudgedByItsChildContextNotRawInput() {
        assertEquals(
                LegacySubtree.SERVANT,
                LegacyCommandDeprecationGuard.classifyParsedPaths(List.of(
                        List.of("execute", "as", "targets", "run"),
                        List.of("zapeg-lore", "servant", "awaken", "target"))));
        assertEquals(
                LegacySubtree.NONE,
                LegacyCommandDeprecationGuard.classifyParsedPaths(List.of(
                        List.of("execute", "as", "targets", "run"),
                        List.of("zapeg-lore", "story", "status"))));
    }

    @Test
    void messagesNameOnlyNativeTypedReplacementCommands() {
        assertEquals(
                "/zapeg-lore servant is retired. Use /heraldor servant rehearse "
                        + "<online_player> stalker, /heraldor servant status <online_player>, "
                        + "or /heraldor servant dismiss <online_player>.",
                LegacyCommandDeprecationGuard.migrationMessage(LegacySubtree.SERVANT));
        assertEquals(
                "/zapeg-lore voice is retired. Use /heraldor voice rehearse <online_player>, "
                        + "/heraldor voice rehearse <online_player> voice_02, "
                        + "or /heraldor voice status <online_player>.",
                LegacyCommandDeprecationGuard.migrationMessage(LegacySubtree.VOICE));
        assertThrows(
                IllegalArgumentException.class,
                () -> LegacyCommandDeprecationGuard.migrationMessage(LegacySubtree.NONE));
    }
}
