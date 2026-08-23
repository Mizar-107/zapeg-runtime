package io.github.mizar107.zapegruntime.servant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import com.mojang.brigadier.tree.CommandNode;
import org.junit.jupiter.api.Test;

class ServantCommandContractTest {

    @Test
    void vanillaPlayerSelectorAcceptsTheRealUnderscoredUsernameAsOneToken()
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String username = "Mizar__107";
        StringReader reader = new StringReader(username);

        assertNotNull(EntityArgument.player().parse(reader));
        assertEquals(username.length(), reader.getCursor());
    }

    @Test
    void commandSubtreeAttachesBelowCallerOwnedRoot() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("heraldor");
        ServantCommands.attach(root);

        assertEquals("servant", root.build().getChild("servant").getName());
    }

    @Test
    void operatorTreeExposesAllThreeTypedAwakenAndRehearsalControls() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("heraldor");
        ServantCommands.attach(root);
        CommandNode<CommandSourceStack> servant = root.build().getChild("servant");
        CommandNode<CommandSourceStack> awakenTarget = servant
                .getChild("awaken")
                .getChild("target");
        CommandNode<CommandSourceStack> rehearseTarget = servant
                .getChild("rehearse")
                .getChild("target");

        for (ServantArchetype archetype : ServantArchetype.values()) {
            assertNotNull(awakenTarget.getChild(archetype.id()));
            assertNotNull(rehearseTarget.getChild(archetype.id()));
            assertNotNull(awakenTarget.getChild(archetype.id()).getChild("event"));
            assertNotNull(rehearseTarget.getChild(archetype.id()).getChild("event"));
        }
        assertNotNull(servant.getChild("status"));
        assertNotNull(servant.getChild("dismiss"));
        assertNotNull(awakenTarget.getChild("rehearsal"),
                "legacy Stalker rehearsal syntax remains available");
    }
}
