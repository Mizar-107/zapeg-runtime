package io.github.mizar107.zapegruntime.servant;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.mizar107.zapegruntime.ZapeGRuntime;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Typed Brigadier subtree; no command ever interpolates or reparses a username. */
public final class ServantCommands {

    private ServantCommands() {}

    /**
     * Adds {@code servant} beneath a caller-owned root, for example
     * {@code ServantCommands.attach(Commands.literal("heraldor"))}.
     */
    public static LiteralArgumentBuilder<CommandSourceStack> attach(
            LiteralArgumentBuilder<CommandSourceStack> root) {
        return root.then(Commands.literal("servant")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("awaken")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(context -> awaken(context, UUID.randomUUID(), false))
                                .then(Commands.literal("rehearsal")
                                        .executes(context -> awaken(
                                                context, UUID.randomUUID(), true)))
                                .then(Commands.literal("event")
                                        .then(Commands.argument("encounter_id", UuidArgument.uuid())
                                                .executes(context -> awaken(
                                                        context,
                                                        UuidArgument.getUuid(
                                                                context, "encounter_id"),
                                                        false))
                                                .then(Commands.literal("rehearsal")
                                                        .executes(context -> awaken(
                                                                context,
                                                                UuidArgument.getUuid(
                                                                        context, "encounter_id"),
                                                                true)))))))
                .then(Commands.literal("dismiss")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ServantCommands::dismiss)))
                .then(Commands.literal("status")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ServantCommands::status))));
    }

    private static int awaken(
            CommandContext<CommandSourceStack> context,
            UUID encounterId,
            boolean rehearsal)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        ServantEncounterManager.StartResult result = ServantEncounterManager.awaken(
                target, encounterId, rehearsal);
        Component reply = Component.literal(
                result.message()
                        + " encounter=" + result.encounterId()
                        + (result.servantId() == null ? "" : " entity=" + result.servantId()));
        if (result.success()) {
            context.getSource().sendSuccess(() -> reply, false);
        } else {
            context.getSource().sendFailure(reply);
        }
        audit(context.getSource(), "awaken", target, encounterId, result.status().name());
        return result.success() ? 1 : 0;
    }

    private static int dismiss(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        boolean removed = ServantEncounterManager.cancelForTarget(
                context.getSource().getServer(),
                target.getUUID(),
                ServantEncounterManager.CloseReason.OPERATOR);
        context.getSource().sendSuccess(
                () -> Component.literal(removed ? "Servant dismissed" : "active=0"),
                false);
        audit(context.getSource(), "dismiss", target, null, Boolean.toString(removed));
        return removed ? 1 : 0;
    }

    private static int status(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        Optional<ServantEncounter> active = ServantEncounterManager.activeFor(
                context.getSource().getServer(), target.getUUID());
        int victories = ServantEncounterManager.victoryCount(
                context.getSource().getServer(), target.getUUID());
        String detail = active
                .map(encounter -> "active=1 encounter=" + encounter.encounterId()
                        + " entity=" + encounter.servantId()
                        + " rehearsal=" + encounter.rehearsal()
                        + " deadline=" + encounter.deadlineGameTime())
                .orElse("active=0");
        context.getSource().sendSuccess(
                () -> Component.literal(detail + " live_victories="
                        + (victories < 0 ? "unavailable" : victories)),
                false);
        return 1;
    }

    private static void audit(
            CommandSourceStack source,
            String action,
            ServerPlayer target,
            UUID encounterId,
            String result) {
        ZapeGRuntime.LOGGER.info(
                "Servant operator action={} source={} target={} target_uuid={} encounter={} result={}",
                action,
                source.getTextName(),
                target.getGameProfile().getName(),
                target.getUUID(),
                encounterId,
                result);
    }
}
