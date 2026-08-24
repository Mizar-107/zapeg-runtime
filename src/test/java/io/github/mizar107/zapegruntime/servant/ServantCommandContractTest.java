package io.github.mizar107.zapegruntime.servant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
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
        CommandNode<CommandSourceStack> victories = servant.getChild("victories");
        assertNotNull(victories);
        assertNotNull(victories.getCommand());
        assertTrue(victories.getChildren().isEmpty(),
                "aggregate victory query accepts no user input");
        assertTrue(victories.getRequirement().test(null),
                "victories adds no child policy and inherits the trusted root");
        assertNotNull(awakenTarget.getChild("rehearsal"),
                "legacy Stalker rehearsal syntax remains available");
    }

    @Test
    void victoryQueryHasOneStrictBoundedSuccessLine() {
        ServantCommands.VictoryQueryResponse response = ServantCommands.victoryQueryResponse(
                Optional.of(new ServantEncounterData.GlobalVictoryCounts(
                        ServantEncounterData.CURRENT_SCHEMA_VERSION, 4, 2, 1, 1)));

        assertTrue(response.success());
        assertEquals(1, response.commandResult());
        assertEquals(
                "servant_victories schema=2/2 writable=1 live_victories=4 "
                        + "stalker_victories=2 herald_victories=1 binder_victories=1",
                response.line());
        assertFalse(response.line().contains("\n"));
        assertFalse(response.line().contains("\r"));
        assertFalse(response.line().contains("uuid"));
    }

    @Test
    void victoryQueryFailsClosedWhenSchemaIsUnsupported() {
        ServantCommands.VictoryQueryResponse response =
                ServantCommands.victoryQueryResponse(Optional.empty());

        assertFalse(response.success());
        assertEquals(0, response.commandResult());
        assertEquals(
                "servant_victories schema=unsupported writable=0",
                response.line());
    }
}
