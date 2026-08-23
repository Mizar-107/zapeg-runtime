package io.github.mizar107.zapegruntime.server;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.network.SceneNetwork;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModList;

/** Native, typed command surface for Heraldor state and encounters. */
public final class HeraldorCommands {

    private HeraldorCommands() {}

    public static void register(RegisterCommandsEvent event) {
        register(event, ignored -> {});
    }

    /**
     * Registers the core tree and gives encounter modules a stable point at
     * which to attach children such as {@code /heraldor servant ...}.
     */
    public static void register(
            RegisterCommandsEvent event,
            Consumer<LiteralArgumentBuilder<CommandSourceStack>> childAttacher) {
        LiteralArgumentBuilder<CommandSourceStack> root = createRoot();
        childAttacher.accept(root);
        event.getDispatcher().register(root);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> createRoot() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("heraldor")
                .requires(CommandSourcePolicy::operatorOrDirector);
        root.then(targetedDiagnostic("status"));
        root.then(targetedDiagnostic("diagnose"));
        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> targetedDiagnostic(String name) {
        return Commands.literal(name)
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(HeraldorCommands::diagnose));
    }

    private static int diagnose(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        HeraldorWorldData worldData = HeraldorWorldData.get(context.getSource().getServer());
        String diagnostic = HeraldorDiagnostics.format(
                new HeraldorDiagnostics.PlayerDiagnostic(
                        target.getGameProfile().getName(),
                        target.getUUID(),
                        runtimeVersion(),
                        SceneNetwork.PROTOCOL,
                        target.level().dimension().location().toString(),
                        SceneServerManager.statusFor(target.getUUID()),
                        worldData.schemaStatus(),
                        worldData.snapshotForDiagnostics(target.getUUID())));
        context.getSource().sendSuccess(() -> Component.literal(diagnostic), false);
        ZapeGRuntime.LOGGER.info(
                "Heraldor diagnostic source={} target={} uuid={}",
                context.getSource().getTextName(),
                target.getGameProfile().getName(),
                target.getUUID());
        return 1;
    }

    private static String runtimeVersion() {
        return ModList.get()
                .getModContainerById(ZapeGRuntime.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }
}
