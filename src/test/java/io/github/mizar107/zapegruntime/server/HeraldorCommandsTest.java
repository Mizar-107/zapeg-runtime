package io.github.mizar107.zapegruntime.server;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import org.junit.jupiter.api.Test;

class HeraldorCommandsTest {

    @Test
    void diagnosticBranchesRequireAPlayerArgumentAndRootAcceptsEncounterChildren() {
        var rootBuilder = HeraldorCommands.createRoot();
        rootBuilder.then(Commands.literal("servant"));
        CommandNode<CommandSourceStack> root = rootBuilder.build();

        CommandNode<CommandSourceStack> status = root.getChild("status");
        assertNotNull(status);
        var target = assertInstanceOf(
                ArgumentCommandNode.class,
                status.getChild("target"));
        assertInstanceOf(EntityArgument.class, target.getType());
        assertNotNull(root.getChild("diagnose"));
        assertNotNull(root.getChild("servant"));
    }
}
