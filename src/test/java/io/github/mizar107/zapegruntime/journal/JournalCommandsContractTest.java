package io.github.mizar107.zapegruntime.journal;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import org.junit.jupiter.api.Test;

class JournalCommandsContractTest {

    @Test
    void restoreTargetsOnlyAUuidBelowTheTrustedRoot() {
        CommandNode<CommandSourceStack> root =
                JournalCommands.attach(Commands.literal("heraldor")).build();
        CommandNode<CommandSourceStack> journal = root.getChild("journal");
        assertNotNull(journal);
        CommandNode<CommandSourceStack> target =
                journal.getChild("restore").getChild("target_uuid");
        ArgumentCommandNode<?, ?> argument = assertInstanceOf(ArgumentCommandNode.class, target);
        assertInstanceOf(UuidArgument.class, argument.getType());
        assertNull(journal.getChild("restore").getChild("target"));
    }
}
