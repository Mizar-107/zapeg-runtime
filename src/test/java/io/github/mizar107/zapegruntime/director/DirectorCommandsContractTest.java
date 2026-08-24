package io.github.mizar107.zapegruntime.director;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import org.junit.jupiter.api.Test;

class DirectorCommandsContractTest {

    @Test
    void statusAndDiagnoseAcceptOnlyTypedTargetUuid() {
        CommandNode<CommandSourceStack> root =
                DirectorCommands.attach(Commands.literal("heraldor")).build();
        CommandNode<CommandSourceStack> director = root.getChild("director");
        assertNotNull(director);
        for (String branch : java.util.List.of("status", "diagnose")) {
            CommandNode<CommandSourceStack> target =
                    director.getChild(branch).getChild("target_uuid");
            ArgumentCommandNode<?, ?> argument =
                    assertInstanceOf(ArgumentCommandNode.class, target);
            assertInstanceOf(UuidArgument.class, argument.getType());
            assertNull(director.getChild(branch).getChild("target"));
        }
    }
}
