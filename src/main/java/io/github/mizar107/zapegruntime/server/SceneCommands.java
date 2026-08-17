package io.github.mizar107.zapegruntime.server;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.scene.CancelReason;
import io.github.mizar107.zapegruntime.scene.SceneProfile;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.rcon.RconConsoleSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

public final class SceneCommands {

    private SceneCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("zapegscene")
                .requires(SceneCommands::operatorOrDirector)
                .then(Commands.literal("rehearse")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(context -> rehearse(context, SceneProfile.ECHO_01))
                                .then(Commands.argument("profile", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                java.util.Arrays.stream(SceneProfile.values())
                                                        .map(SceneProfile::serializedName),
                                                builder))
                                        .executes(SceneCommands::rehearseWithProfile))))
                .then(Commands.literal("trigger")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("event_id", UuidArgument.uuid())
                                        .then(Commands.argument("profile", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        java.util.Arrays.stream(SceneProfile.values())
                                                                .map(SceneProfile::serializedName),
                                                        builder))
                                                .executes(SceneCommands::trigger)))))
                .then(Commands.literal("cancel-all")
                        .executes(SceneCommands::cancelAll))
                .then(Commands.literal("status")
                        .executes(SceneCommands::status)));
    }

    private static int rehearseWithProfile(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        SceneProfile profile = parseProfile(
                context.getSource(),
                StringArgumentType.getString(context, "profile"));
        return profile == null ? 0 : rehearse(context, profile);
    }

    private static int rehearse(
            CommandContext<CommandSourceStack> context,
            SceneProfile profile)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        SceneServerManager.DispatchResult result = SceneServerManager.rehearse(target, profile);
        audit(context.getSource(), "rehearse", target, result);
        return reply(context.getSource(), result);
    }

    private static int trigger(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        UUID eventId = UuidArgument.getUuid(context, "event_id");
        SceneProfile profile = parseProfile(
                source,
                StringArgumentType.getString(context, "profile"));
        if (profile == null) {
            return 0;
        }
        SceneServerManager.DispatchResult result = SceneServerManager.dispatch(
                target,
                eventId,
                profile,
                false);
        audit(source, "trigger", target, result);
        return reply(source, result);
    }

    private static SceneProfile parseProfile(CommandSourceStack source, String raw) {
        try {
            return SceneProfile.parse(raw);
        } catch (IllegalArgumentException invalid) {
            source.sendFailure(Component.literal(invalid.getMessage()));
            return null;
        }
    }

    private static boolean operatorOrDirector(CommandSourceStack source) {
        if (!source.hasPermission(2)) {
            return false;
        }
        if (source.getEntity() instanceof ServerPlayer player) {
            // `execute as <op>` changes the effective entity but retains the
            // command block/function as the underlying source. Only a command
            // typed by this exact player is admitted.
            return source.source == player;
        }
        // MinecraftServer is also the raw source used by server-derived
        // function stacks, so local console cannot be admitted without also
        // admitting that redirect path. Host automation uses authenticated
        // RCON instead.
        return source.getEntity() == null
                && source.source instanceof RconConsoleSource;
    }

    private static int cancelAll(CommandContext<CommandSourceStack> context) {
        boolean cancelled = SceneServerManager.cancel(CancelReason.OPERATOR);
        context.getSource().sendSuccess(
                () -> Component.literal(cancelled ? "scene cancelled" : "active=0"),
                false);
        return cancelled ? 1 : 0;
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(
                () -> Component.literal(SceneServerManager.status()),
                false);
        return 1;
    }

    private static int reply(
            CommandSourceStack source,
            SceneServerManager.DispatchResult result) {
        Component text = Component.literal(
                result.message() + " event=" + result.eventId());
        if (result.success()) {
            source.sendSuccess(() -> text, false);
            return 1;
        }
        source.sendFailure(text);
        return 0;
    }

    private static void audit(
            CommandSourceStack source,
            String action,
            ServerPlayer target,
            SceneServerManager.DispatchResult result) {
        ZapeGRuntime.LOGGER.info(
                "Scene operator action={} source={} target={} success={} event={}",
                action,
                source.getTextName(),
                target.getGameProfile().getName(),
                result.success(),
                result.eventId());
    }
}
