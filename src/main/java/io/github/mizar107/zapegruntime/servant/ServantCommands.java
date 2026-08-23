package io.github.mizar107.zapegruntime.servant;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.mizar107.zapegruntime.ZapeGRuntime;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
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
        RequiredArgumentBuilder<CommandSourceStack, EntitySelector> awakenTarget =
                Commands.argument("target", EntityArgument.player())
                        .executes(context -> awaken(
                                context,
                                UUID.randomUUID(),
                                ServantArchetype.STALKER,
                                false))
                        .then(Commands.literal("rehearsal")
                                .executes(context -> awaken(
                                        context,
                                        UUID.randomUUID(),
                                        ServantArchetype.STALKER,
                                        true)))
                        .then(Commands.literal("event")
                                .then(Commands.argument("encounter_id", UuidArgument.uuid())
                                        .executes(context -> awaken(
                                                context,
                                                UuidArgument.getUuid(context, "encounter_id"),
                                                ServantArchetype.STALKER,
                                                false))
                                        .then(Commands.literal("rehearsal")
                                                .executes(context -> awaken(
                                                        context,
                                                        UuidArgument.getUuid(
                                                                context, "encounter_id"),
                                                        ServantArchetype.STALKER,
                                                        true)))));
        RequiredArgumentBuilder<CommandSourceStack, EntitySelector> rehearseTarget =
                Commands.argument("target", EntityArgument.player())
                        .executes(context -> awaken(
                                context,
                                UUID.randomUUID(),
                                ServantArchetype.STALKER,
                                true));
        for (ServantArchetype archetype : ServantArchetype.values()) {
            awakenTarget.then(archetypeBranch(archetype, false));
            rehearseTarget.then(archetypeBranch(archetype, true));
        }

        return root.then(Commands.literal("servant")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("awaken")
                        .then(awakenTarget))
                .then(Commands.literal("rehearse")
                        .then(rehearseTarget))
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
            ServantArchetype archetype,
            boolean rehearsal)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        ServantEncounterManager.StartResult result = ServantEncounterManager.awaken(
                target, encounterId, archetype, rehearsal);
        Component reply = Component.literal(
                result.message()
                        + " archetype=" + archetype.id()
                        + " encounter=" + result.encounterId()
                        + (result.servantId() == null ? "" : " entity=" + result.servantId()));
        if (result.success()) {
            context.getSource().sendSuccess(() -> reply, false);
        } else {
            context.getSource().sendFailure(reply);
        }
        audit(
                context.getSource(),
                rehearsal ? "rehearse" : "awaken",
                target,
                encounterId,
                archetype,
                result.status().name());
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
        audit(context.getSource(), "dismiss", target, null, null, Boolean.toString(removed));
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
                        + " archetype=" + encounter.archetype().id()
                        + " rehearsal=" + encounter.rehearsal()
                        + " deadline=" + encounter.deadlineGameTime()
                        + ServantEncounterManager.combatSnapshot(
                                        context.getSource().getServer(), encounter)
                                .map(snapshot -> " telegraph=" + snapshot.telegraphing()
                                        + " next_special=" + snapshot.nextSpecialGameTime()
                                        + " resolves=" + snapshot.specialResolveGameTime()
                                        + " specials=" + snapshot.completedSpecials())
                                .orElse(" entity_loaded=0"))
                .orElse("active=0");
        StringBuilder typedVictories = new StringBuilder();
        for (ServantArchetype archetype : ServantArchetype.values()) {
            int count = ServantEncounterManager.victoryCount(
                    context.getSource().getServer(), target.getUUID(), archetype);
            typedVictories.append(' ')
                    .append(archetype.id())
                    .append("_victories=")
                    .append(count < 0 ? "unavailable" : count);
        }
        context.getSource().sendSuccess(
                () -> Component.literal(detail + " live_victories="
                        + (victories < 0 ? "unavailable" : victories)
                        + typedVictories),
                false);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> archetypeBranch(
            ServantArchetype archetype,
            boolean rehearsal) {
        return Commands.literal(archetype.id())
                .executes(context -> awaken(
                        context, UUID.randomUUID(), archetype, rehearsal))
                .then(Commands.literal("event")
                        .then(Commands.argument("encounter_id", UuidArgument.uuid())
                                .executes(context -> awaken(
                                        context,
                                        UuidArgument.getUuid(context, "encounter_id"),
                                        archetype,
                                        rehearsal))));
    }

    private static void audit(
            CommandSourceStack source,
            String action,
            ServerPlayer target,
            UUID encounterId,
            ServantArchetype archetype,
            String result) {
        ZapeGRuntime.LOGGER.info(
                "Servant operator action={} source={} target={} target_uuid={} encounter={} archetype={} result={}",
                action,
                source.getTextName(),
                target.getGameProfile().getName(),
                target.getUUID(),
                encounterId,
                archetype == null ? "none" : archetype.id(),
                result);
    }
}
