package io.github.mizar107.zapegruntime.server;

import com.mojang.brigadier.context.CommandContextBuilder;
import io.github.mizar107.zapegruntime.ZapeGRuntime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Stops two retired KubeJS command subtrees after Brigadier has parsed them.
 *
 * <p>The guard deliberately does not register or forward an alias. Parsed node names are the
 * authority, so an argument value is never copied into a replacement command.
 */
@Mod.EventBusSubscriber(modid = ZapeGRuntime.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LegacyCommandDeprecationGuard {

    private static final String LEGACY_ROOT = "zapeg-lore";
    private static final String LEGACY_SERVANT = "servant";
    private static final String LEGACY_VOICE = "voice";

    private static final String SERVANT_MIGRATION =
            "/zapeg-lore servant is retired. Use /heraldor servant rehearse "
                    + "<online_player> stalker, /heraldor servant status <online_player>, "
                    + "or /heraldor servant dismiss <online_player>.";
    private static final String VOICE_MIGRATION =
            "/zapeg-lore voice is retired. Use /heraldor voice rehearse <online_player>, "
                    + "/heraldor voice rehearse <online_player> voice_02, "
                    + "or /heraldor voice status <online_player>.";

    private LegacyCommandDeprecationGuard() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCommand(CommandEvent event) {
        LegacySubtree subtree = classifyParsedPaths(parsedNodePaths(
                event.getParseResults().getContext()));
        if (subtree == LegacySubtree.NONE) {
            return;
        }

        event.setCanceled(true);
        event.getParseResults()
                .getContext()
                .getSource()
                .sendFailure(Component.literal(migrationMessage(subtree)));
    }

    private static List<List<String>> parsedNodePaths(
            CommandContextBuilder<CommandSourceStack> rootContext) {
        List<List<String>> paths = new ArrayList<>();
        for (CommandContextBuilder<CommandSourceStack> context = rootContext;
                context != null;
                context = context.getChild()) {
            paths.add(context.getNodes().stream()
                    .map(parsed -> parsed.getNode().getName())
                    .toList());
        }
        return List.copyOf(paths);
    }

    static LegacySubtree classifyParsedPaths(List<? extends List<String>> parsedPaths) {
        Objects.requireNonNull(parsedPaths, "parsedPaths");
        for (List<String> path : parsedPaths) {
            LegacySubtree subtree = classifyNodeNames(path);
            if (subtree != LegacySubtree.NONE) {
                return subtree;
            }
        }
        return LegacySubtree.NONE;
    }

    static LegacySubtree classifyNodeNames(List<String> nodeNames) {
        Objects.requireNonNull(nodeNames, "nodeNames");
        if (nodeNames.size() < 2 || !LEGACY_ROOT.equals(nodeNames.get(0))) {
            return LegacySubtree.NONE;
        }
        return switch (nodeNames.get(1)) {
            case LEGACY_SERVANT -> LegacySubtree.SERVANT;
            case LEGACY_VOICE -> LegacySubtree.VOICE;
            default -> LegacySubtree.NONE;
        };
    }

    static String migrationMessage(LegacySubtree subtree) {
        return switch (Objects.requireNonNull(subtree, "subtree")) {
            case SERVANT -> SERVANT_MIGRATION;
            case VOICE -> VOICE_MIGRATION;
            case NONE -> throw new IllegalArgumentException("NONE has no migration message");
        };
    }

    enum LegacySubtree {
        NONE,
        SERVANT,
        VOICE
    }
}
