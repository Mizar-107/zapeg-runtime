package io.github.mizar107.zapegruntime.journal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class JournalArchitectureContractTest {

    private static final Path JAVA_ROOT = Path.of(
            "src", "main", "java", "io", "github", "mizar107", "zapegruntime");

    @Test
    void packetsCarryNoTargetProgressTextOrFutureKeys() throws IOException {
        String open = Files.readString(JAVA_ROOT.resolve("network/JournalOpenS2C.java"));
        String action = Files.readString(JAVA_ROOT.resolve("network/JournalActionC2S.java"));
        assertTrue(open.contains("writeInt"));
        assertTrue(open.contains("writeByte"));
        assertFalse(open.contains("writeUtf"));
        assertFalse(open.contains("writeUUID"));
        assertFalse(open.contains("ResourceLocation"));
        assertFalse(open.contains("journal_key"));
        assertFalse(open.contains("currentNode"));
        assertFalse(action.contains("UUID"));
        assertFalse(action.contains("target"));
        assertFalse(action.contains("writeUtf"));
        assertFalse(action.contains("ResourceLocation"));
    }

    @Test
    void channelUsesMainThreadConsumersAndOneExplicitProtocolTen() throws IOException {
        String network = Files.readString(JAVA_ROOT.resolve("network/SceneNetwork.java"));
        assertTrue(network.contains("public static final String PROTOCOL = \"11\""));
        assertTrue(network.contains("consumerMainThread(JournalOpenS2C::handle)"));
        assertTrue(network.contains("consumerMainThread(JournalActionC2S::handle)"));
        assertTrue(network.contains("NetworkDirection.PLAY_TO_CLIENT"));
        assertTrue(network.contains("NetworkDirection.PLAY_TO_SERVER"));
    }

    @Test
    void itemNbtIsBindingOnlyAndServerNeverUsesNames() throws IOException {
        String token = Files.readString(JAVA_ROOT.resolve("journal/JournalTokenCodec.java"));
        String service = Files.readString(JAVA_ROOT.resolve("journal/JournalService.java"));
        String commands = Files.readString(JAVA_ROOT.resolve("journal/JournalCommands.java"));
        assertTrue(token.contains("OWNER_KEY"));
        assertTrue(token.contains("TOKEN_KEY"));
        assertFalse(token.contains("CurrentNode"));
        assertFalse(token.contains("Progress"));
        assertFalse(token.contains("Completed"));
        assertFalse(token.contains("StoryFact"));
        assertFalse(service.contains("getName()"));
        assertFalse(service.contains("getGameProfile"));
        assertFalse(commands.contains("EntityArgument"));
        assertFalse(commands.contains("getPlayerByName"));
        assertTrue(commands.contains("UuidArgument.uuid()"));
        assertTrue(commands.contains("getPlayer(playerId)"));
    }

    @Test
    void automaticIssuanceIsBoundedAndNeverDropsToTheWorld() throws IOException {
        String events = Files.readString(JAVA_ROOT.resolve("journal/JournalServerEvents.java"));
        String service = Files.readString(JAVA_ROOT.resolve("journal/JournalService.java"));
        assertTrue(events.contains("RECONCILE_INTERVAL_TICKS = 100"));
        assertTrue(service.contains("getFreeSlot()"));
        assertFalse(service.contains("drop("));
        assertFalse(service.contains("spawnAtLocation"));
        assertFalse(service.contains("addFreshEntity"));
    }

    @Test
    void successfulActionRetriesUseTheExactDurableReceipt() throws IOException {
        String service = Files.readString(JAVA_ROOT.resolve("journal/JournalService.java"));
        assertTrue(service.contains("storyData.receiptStatus("));
        assertTrue(service.contains("ReceiptStatus.EXACT"));
        assertTrue(service.indexOf("ReceiptStatus.EXACT")
                < service.indexOf("JournalAuthorization.actionFor("));
        assertTrue(service.contains("receipt != StoryWorldData.ReceiptStatus.ABSENT"));
    }
}
