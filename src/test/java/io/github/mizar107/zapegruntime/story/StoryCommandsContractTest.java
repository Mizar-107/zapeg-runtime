package io.github.mizar107.zapegruntime.story;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import org.junit.jupiter.api.Test;

class StoryCommandsContractTest {

    @Test
    void statusAndRecoveryTargetsAreTypedUuids() {
        CommandNode<CommandSourceStack> root =
                StoryCommands.attach(Commands.literal("heraldor")).build();
        CommandNode<CommandSourceStack> story = root.getChild("story");
        assertNotNull(story);

        CommandNode<CommandSourceStack> statusTarget =
                story.getChild("status").getChild("target_uuid");
        ArgumentCommandNode<?, ?> statusArgument =
                assertInstanceOf(ArgumentCommandNode.class, statusTarget);
        assertInstanceOf(UuidArgument.class, statusArgument.getType());
        assertNull(story.getChild("status").getChild("target"));

        CommandNode<CommandSourceStack> recoveryTarget =
                story.getChild("recover").getChild("target_uuid");
        ArgumentCommandNode<?, ?> recoveryArgument =
                assertInstanceOf(ArgumentCommandNode.class, recoveryTarget);
        assertInstanceOf(UuidArgument.class, recoveryArgument.getType());
        ArgumentCommandNode<?, ?> operationArgument = assertInstanceOf(
                ArgumentCommandNode.class,
                recoveryTarget.getChild("operation_id"));
        assertInstanceOf(UuidArgument.class, operationArgument.getType());
    }
}
