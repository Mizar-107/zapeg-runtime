package io.github.mizar107.zapegruntime.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LegacyCommandDeprecationArchitectureTest {

    private static final Path GUARD = Path.of(
            "src", "main", "java", "io", "github", "mizar107", "zapegruntime",
            "server", "LegacyCommandDeprecationGuard.java");

    @Test
    void forgeGuardCancelsAtHighestPriorityAndOnlySendsAMessage() throws IOException {
        String source = Files.readString(GUARD);

        assertTrue(source.contains("@SubscribeEvent(priority = EventPriority.HIGHEST)"));
        assertTrue(source.contains("event.setCanceled(true)"));
        assertTrue(source.contains("sendFailure(Component.literal(migrationMessage(subtree)))"));
        assertTrue(
                source.indexOf("event.setCanceled(true)")
                        < source.indexOf("sendFailure(Component.literal(migrationMessage(subtree)))"),
                "the retired command must be canceled before reporting its replacement");
        assertFalse(source.contains("performCommand"));
        assertFalse(source.contains("runCommand"));
        assertFalse(source.contains("getDispatcher"));
        assertFalse(source.contains(".register("));
    }

    @Test
    void guardUsesParsedNodeNamesAndNeverReadsOrReparsesCommandText() throws IOException {
        String source = Files.readString(GUARD);

        assertTrue(source.contains("context.getNodes().stream()"));
        assertTrue(source.contains("parsed.getNode().getName()"));
        assertTrue(source.contains("context = context.getChild()"));
        assertFalse(source.contains("getReader()"));
        assertFalse(source.contains("getInput()"));
        assertFalse(source.contains("StringReader"));
        assertFalse(source.contains("EntityArgument"));
        assertFalse(source.contains("target.get"));
    }
}
