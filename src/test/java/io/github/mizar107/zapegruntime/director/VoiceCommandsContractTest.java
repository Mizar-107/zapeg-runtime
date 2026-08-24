package io.github.mizar107.zapegruntime.director;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import io.github.mizar107.zapegruntime.server.HeraldorCommands;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import org.junit.jupiter.api.Test;

class VoiceCommandsContractTest {

    @Test
    void treeHasOnlyTypedTargetAndTheTwoClosedVoiceVariants()
            throws ReflectiveOperationException {
        var rootBuilder = HeraldorCommands.createRoot();
        var inheritedRequirement = rootBuilder.getRequirement();
        CommandNode<CommandSourceStack> root = VoiceCommands.attach(rootBuilder).build();
        assertSame(inheritedRequirement, rootBuilder.getRequirement());

        CommandNode<CommandSourceStack> voice = root.getChild("voice");
        assertNotNull(voice);
        assertEquals(Set.of("rehearse", "status"), childNames(voice));

        CommandNode<CommandSourceStack> rehearsalTarget =
                voice.getChild("rehearse").getChild("target");
        EntityArgument rehearsalArgument = playerArgument(rehearsalTarget);
        assertEquals(Set.of("voice_01", "voice_02"), childNames(rehearsalTarget));
        assertNotNull(rehearsalTarget.getCommand(), "target alone must default to voice_01");
        assertNotNull(rehearsalTarget.getChild("voice_01").getCommand());
        assertNotNull(rehearsalTarget.getChild("voice_02").getCommand());
        assertEquals(true, booleanFlag(rehearsalArgument, "single"));
        assertEquals(true, booleanFlag(rehearsalArgument, "playersOnly"));

        CommandNode<CommandSourceStack> statusTarget =
                voice.getChild("status").getChild("target");
        EntityArgument statusArgument = playerArgument(statusTarget);
        assertEquals(Set.of(), childNames(statusTarget));
        assertNotNull(statusTarget.getCommand());
        assertEquals(true, booleanFlag(statusArgument, "single"));
        assertEquals(true, booleanFlag(statusArgument, "playersOnly"));
    }

    private static Set<String> childNames(CommandNode<CommandSourceStack> node) {
        return node.getChildren().stream()
                .map(CommandNode::getName)
                .collect(Collectors.toSet());
    }

    private static EntityArgument playerArgument(CommandNode<CommandSourceStack> node) {
        ArgumentCommandNode<?, ?> argument =
                assertInstanceOf(ArgumentCommandNode.class, node);
        ArgumentType<?> type = argument.getType();
        return assertInstanceOf(EntityArgument.class, type);
    }

    private static boolean booleanFlag(EntityArgument argument, String name)
            throws ReflectiveOperationException {
        Field field = EntityArgument.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(argument);
    }
}
