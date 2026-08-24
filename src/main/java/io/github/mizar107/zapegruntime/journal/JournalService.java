package io.github.mizar107.zapegruntime.journal;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.network.SceneNetwork;
import io.github.mizar107.zapegruntime.server.HeraldorSafetyController;
import io.github.mizar107.zapegruntime.server.HeraldorSafetyMode;
import io.github.mizar107.zapegruntime.story.StoryCampaignDefinition;
import io.github.mizar107.zapegruntime.story.StoryCampaignRegistry;
import io.github.mizar107.zapegruntime.story.StoryFactType;
import io.github.mizar107.zapegruntime.story.StoryService;
import io.github.mizar107.zapegruntime.story.StoryWorldData;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative journal acquisition, views, and closed discovery actions. */
public final class JournalService {

    private static final Set<UUID> NOTIFIED_MISSING = new HashSet<>();
    private static final Set<UUID> NOTIFIED_FULL = new HashSet<>();
    private static final Set<UUID> NOTIFIED_UNAVAILABLE = new HashSet<>();

    private JournalService() {}

    public static GrantResult reconcile(ServerPlayer player) {
        return reconcile(player, JournalGrantPolicy.Mode.AUTOMATIC);
    }

    public static GrantResult restore(ServerPlayer player) {
        return reconcile(player, JournalGrantPolicy.Mode.RESTORE);
    }

    private static GrantResult reconcile(
            ServerPlayer player, JournalGrantPolicy.Mode mode) {
        Objects.requireNonNull(player, "player");
        MinecraftServer server = requireServerThread(player);
        HeraldorSafetyMode required = mode == JournalGrantPolicy.Mode.AUTOMATIC
                ? HeraldorSafetyMode.AUTO
                : HeraldorSafetyMode.LIVE;
        if (!HeraldorSafetyController.allows(server, required)) {
            return GrantResult.DATA_UNAVAILABLE;
        }
        boolean campaignAvailable = StoryCampaignRegistry.current()
                .find(StoryCampaignRegistry.HERALDOR_CAMPAIGN)
                .filter(campaign -> campaign.ordinalOf(campaign.entryNodeId()) == 0)
                .isPresent();
        if (!campaignAvailable) {
            return GrantResult.NO_STORY;
        }

        StoryWorldData storyData = StoryWorldData.get(server);
        JournalBindingData bindings = JournalBindingData.get(server);
        Optional<UUID> activeToken = bindings.activeToken(player.getUUID());
        boolean hasActiveJournal = activeToken
                .map(token -> inventoryContains(player, token))
                .orElse(false);
        int freeSlot = player.getInventory().getFreeSlot();
        JournalGrantPolicy.Decision decision = JournalGrantPolicy.decide(
                true,
                bindings.writable() && storyData.schemaStatus().writable(),
                activeToken.isPresent(),
                hasActiveJournal,
                freeSlot != Inventory.NOT_FOUND_INDEX,
                mode);

        return switch (decision) {
            case PRESENT -> GrantResult.PRESENT;
            case FIRST_ISSUE -> issue(player, bindings, freeSlot, false);
            case RESTORE -> issue(player, bindings, freeSlot, true);
            case WAITING_FOR_SPACE, RESTORE_WAITING_FOR_SPACE -> {
                notifyOnce(
                        player,
                        NOTIFIED_FULL,
                        "message.zapeg_runtime.journal.inventory_full");
                yield GrantResult.INVENTORY_FULL;
            }
            case LOST_REQUIRES_RESTORE -> {
                notifyOnce(
                        player,
                        NOTIFIED_MISSING,
                        "message.zapeg_runtime.journal.missing");
                yield GrantResult.MISSING_REQUIRES_RESTORE;
            }
            case DATA_UNAVAILABLE -> {
                notifyOnce(
                        player,
                        NOTIFIED_UNAVAILABLE,
                        "message.zapeg_runtime.journal.data_unavailable");
                yield GrantResult.DATA_UNAVAILABLE;
            }
            case NO_STORY -> GrantResult.NO_STORY;
        };
    }

    private static GrantResult issue(
            ServerPlayer player,
            JournalBindingData bindings,
            int freeSlot,
            boolean rotate) {
        if (freeSlot == Inventory.NOT_FOUND_INDEX) {
            return GrantResult.INVENTORY_FULL;
        }
        UUID token = UUID.randomUUID();
        ItemStack journal = new ItemStack(JournalItems.HERALDOR_JOURNAL.get());
        JournalTokenCodec.stamp(journal, player.getUUID(), token);

        boolean persisted = rotate
                ? bindings.rotate(player.getUUID(), token)
                : bindings.bindInitial(player.getUUID(), token);
        if (!persisted) {
            notifyOnce(
                    player,
                    NOTIFIED_UNAVAILABLE,
                    "message.zapeg_runtime.journal.data_unavailable");
            return GrantResult.DATA_UNAVAILABLE;
        }
        player.getInventory().setItem(freeSlot, journal);
        player.getInventory().setChanged();
        NOTIFIED_FULL.remove(player.getUUID());
        NOTIFIED_MISSING.remove(player.getUUID());
        player.displayClientMessage(
                Component.translatable(rotate
                        ? "message.zapeg_runtime.journal.restored"
                        : "message.zapeg_runtime.journal.granted"),
                false);
        return rotate ? GrantResult.RESTORED : GrantResult.ISSUED;
    }

    public static boolean openFromHeldItem(ServerPlayer player, ItemStack held) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(held, "held");
        MinecraftServer server = requireServerThread(player);
        JournalBindingData bindings = JournalBindingData.get(server);
        if (!bindings.writable()) {
            player.displayClientMessage(
                    Component.translatable("message.zapeg_runtime.journal.data_unavailable"),
                    true);
            return false;
        }
        Optional<UUID> active = bindings.activeToken(player.getUUID());
        if (active.isEmpty()
                || !JournalTokenCodec.matches(held, player.getUUID(), active.get())) {
            player.displayClientMessage(
                    Component.translatable("message.zapeg_runtime.journal.inert"), true);
            return false;
        }
        return sendAuthorizedView(player);
    }

    public static void handleAction(ServerPlayer player, JournalAction action) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(action, "action");
        MinecraftServer server = requireServerThread(player);
        JournalBindingData bindings = JournalBindingData.get(server);
        Optional<UUID> token = bindings.activeToken(player.getUUID());
        boolean hasActiveJournal = token
                .map(value -> inventoryContains(player, value))
                .orElse(false);

        Optional<StoryCampaignDefinition> loaded = StoryCampaignRegistry.current()
                .find(StoryCampaignRegistry.HERALDOR_CAMPAIGN);
        StoryWorldData storyData = StoryWorldData.get(server);
        Optional<StoryWorldData.PlayerSnapshot> state = storyData.snapshot(player.getUUID());
        if (!bindings.writable()) {
            reject(player, "message.zapeg_runtime.journal.action.unavailable");
            return;
        }
        if (!hasActiveJournal) {
            reject(player, "message.zapeg_runtime.journal.action.no_possession");
            return;
        }
        if (loaded.isEmpty()
                || state.isEmpty()
                || !storyData.schemaStatus().writable()) {
            reject(player, "message.zapeg_runtime.journal.action.unavailable");
            return;
        }
        StoryCampaignDefinition campaign = loaded.get();
        StoryWorldData.PlayerSnapshot snapshot = state.get();
        if (JournalAuthorization.viewFor(
                        player.getUUID(), campaign, true, state)
                .isEmpty()) {
            reject(player, "message.zapeg_runtime.journal.action.unavailable");
            return;
        }
        UUID factId = JournalFactIdentity.derive(
                player.getUUID(),
                campaign.id(),
                snapshot.progressEpoch(),
                action.subject());
        StoryWorldData.ReceiptStatus receipt = storyData.receiptStatus(
                player.getUUID(),
                factId,
                campaign.id(),
                campaign.revision(),
                StoryFactType.JOURNAL_DISCOVERY,
                action.subject());
        if (receipt == StoryWorldData.ReceiptStatus.EXACT) {
            // A retry delivered after the first action advanced the node is a
            // certified no-op, not a second progression mutation.
            sendAuthorizedView(player);
            return;
        }
        if (receipt != StoryWorldData.ReceiptStatus.ABSENT) {
            reject(player, "message.zapeg_runtime.journal.action.unavailable");
            return;
        }
        JournalAuthorization.ActionDecision authorization = JournalAuthorization.actionFor(
                player.getUUID(), campaign, state, action, true);
        if (authorization != JournalAuthorization.ActionDecision.ALLOW) {
            reject(player, "message.zapeg_runtime.journal.action.not_expected");
            return;
        }

        StoryService.SubmissionResult result = StoryService.submitIfExpected(
                server,
                factId,
                player.getUUID(),
                campaign.id(),
                StoryFactType.JOURNAL_DISCOVERY,
                action.subject());
        if (result.status() == StoryService.SubmissionStatus.APPLIED
                || result.status() == StoryService.SubmissionStatus.ALREADY_PROCESSED) {
            player.displayClientMessage(
                    Component.translatable("message.zapeg_runtime.journal.action.revealed"),
                    true);
            sendAuthorizedView(player);
            return;
        }
        ZapeGRuntime.LOGGER.warn(
                "Journal discovery rejected target_uuid={} action={} status={}",
                player.getUUID(),
                action.name(),
                result.status());
        reject(player, "message.zapeg_runtime.journal.action.unavailable");
    }

    private static boolean sendAuthorizedView(ServerPlayer player) {
        MinecraftServer server = requireServerThread(player);
        Optional<StoryCampaignDefinition> loaded = StoryCampaignRegistry.current()
                .find(StoryCampaignRegistry.HERALDOR_CAMPAIGN);
        StoryWorldData storyData = StoryWorldData.get(server);
        Optional<StoryWorldData.PlayerSnapshot> state = storyData.snapshot(player.getUUID());
        if (loaded.isEmpty()) {
            reject(player, "message.zapeg_runtime.journal.action.unavailable");
            return false;
        }
        StoryCampaignDefinition campaign = loaded.get();
        Optional<JournalView> view = JournalAuthorization.viewFor(
                player.getUUID(), campaign, storyData.schemaStatus().writable(), state);
        if (view.isEmpty()) {
            reject(player, "message.zapeg_runtime.journal.action.unavailable");
            return false;
        }
        SceneNetwork.openJournal(player, view.get());
        return true;
    }

    private static boolean inventoryContains(ServerPlayer player, UUID token) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (JournalTokenCodec.matches(
                    inventory.getItem(slot), player.getUUID(), token)) {
                return true;
            }
        }
        return false;
    }

    private static void reject(ServerPlayer player, String translationKey) {
        player.displayClientMessage(Component.translatable(translationKey), true);
    }

    private static void notifyOnce(
            ServerPlayer player, Set<UUID> ledger, String translationKey) {
        if (ledger.add(player.getUUID())) {
            player.displayClientMessage(Component.translatable(translationKey), false);
        }
    }

    private static MinecraftServer requireServerThread(ServerPlayer player) {
        MinecraftServer server = Objects.requireNonNull(player.getServer(), "player server");
        if (!server.isSameThread()) {
            throw new IllegalStateException("journal access requires the server thread");
        }
        return server;
    }

    public static void clearSessionNotices(UUID playerId) {
        NOTIFIED_MISSING.remove(playerId);
        NOTIFIED_FULL.remove(playerId);
        NOTIFIED_UNAVAILABLE.remove(playerId);
    }

    public static void clearAllSessionNotices() {
        NOTIFIED_MISSING.clear();
        NOTIFIED_FULL.clear();
        NOTIFIED_UNAVAILABLE.clear();
    }

    public enum GrantResult {
        NO_STORY,
        DATA_UNAVAILABLE,
        PRESENT,
        ISSUED,
        INVENTORY_FULL,
        MISSING_REQUIRES_RESTORE,
        RESTORED
    }
}
