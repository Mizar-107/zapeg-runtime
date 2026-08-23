package io.github.mizar107.zapegruntime.server;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.rcon.RconConsoleSource;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class HeraldorCommandsTest {

    @Test
    void bothDiagnosticBranchesUseExactlyOneSingularPlayerArgument()
            throws ReflectiveOperationException {
        CommandNode<CommandSourceStack> root = HeraldorCommands.createRoot().build();

        for (String branchName : java.util.List.of("status", "diagnose")) {
            CommandNode<CommandSourceStack> branch = root.getChild(branchName);
            assertNotNull(branch);
            assertEquals(
                    Set.of("target"),
                    branch.getChildren().stream()
                            .map(CommandNode::getName)
                            .collect(Collectors.toSet()));
            EntityArgument target = playerArgument(branch);

            StringReader username = new StringReader("Mizar__107");
            assertDoesNotThrow(() -> target.parse(username));
            assertEquals(0, username.getRemainingLength());
            assertTrue(booleanFlag(target, "single"));
            assertTrue(booleanFlag(target, "playersOnly"));
        }
        // EntityArgument.players() is the explicit multi-target shape; the
        // branches above must not accidentally expose this variant.
        assertFalse(booleanFlag(EntityArgument.players(), "single"));
    }

    @Test
    void rootAcceptsFutureEncounterChildren() {
        var rootBuilder = HeraldorCommands.createRoot();
        rootBuilder.then(Commands.literal("servant"));
        assertNotNull(rootBuilder.build().getChild("servant"));
    }

    @Test
    void rootUsesDirectPlayerOrAuthenticatedRconPolicy() {
        var requirement = HeraldorCommands.createRoot().getRequirement();

        assertFalse(requirement.test(source(CommandSource.NULL, 4)));
        assertFalse(requirement.test(source(new RconConsoleSource(null), 1)));
        assertTrue(requirement.test(source(new RconConsoleSource(null), 2)));

        assertTrue(CommandSourcePolicy.allowsTrustedShape(
                true, true, true, false, false));
        assertFalse(CommandSourcePolicy.allowsTrustedShape(
                true, true, false, false, false));
        assertFalse(CommandSourcePolicy.allowsTrustedShape(
                false, true, true, false, false));
        assertFalse(CommandSourcePolicy.allowsTrustedShape(
                true, false, false, true, false));
        assertFalse(CommandSourcePolicy.allowsTrustedShape(
                true, false, false, false, true));
        assertTrue(CommandSourcePolicy.allowsTrustedShape(
                true, false, false, true, true));
    }

    private static EntityArgument playerArgument(CommandNode<CommandSourceStack> branch) {
        var target = assertInstanceOf(
                ArgumentCommandNode.class,
                branch.getChild("target"));
        ArgumentType<?> type = target.getType();
        return assertInstanceOf(EntityArgument.class, type);
    }

    private static boolean booleanFlag(EntityArgument argument, String name)
            throws ReflectiveOperationException {
        Field field = EntityArgument.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(argument);
    }

    private static CommandSourceStack source(CommandSource rawSource, int permissionLevel) {
        return new CommandSourceStack(
                rawSource,
                Vec3.ZERO,
                Vec2.ZERO,
                null,
                permissionLevel,
                "test",
                Component.literal("test"),
                null,
                null);
    }
}
