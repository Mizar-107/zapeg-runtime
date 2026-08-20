package io.github.mizar107.zapegruntime.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SceneBindingTest {

    @Test
    void duplicatePublicNamesCollapseIntoFamilyStages() {
        assertEquals(
                new SceneBinding(SceneProfile.RIFT_01, RiftChoreography.STAGE_ECLIPSE),
                SceneBinding.parse("light_fault_01"));
        assertEquals(
                new SceneBinding(SceneProfile.RIFT_01, RiftChoreography.STAGE_ECLIPSE),
                SceneBinding.parse("eclipse_01"));
        assertEquals(
                new SceneBinding(SceneProfile.RIFT_01, RiftChoreography.STAGE_TEAR),
                SceneBinding.parse("chroma_break_01"));
        assertEquals(
                new SceneBinding(SceneProfile.RIFT_01, RiftChoreography.STAGE_UNMOOR),
                SceneBinding.parse("UNMOOR_01"));
        assertEquals(
                new SceneBinding(SceneProfile.RIFT_01, RiftChoreography.STAGE_WITNESS),
                SceneBinding.parse("witness_01"));
        assertEquals(
                new SceneBinding(SceneProfile.FOOTSTEPS_01, HauntChoreography.STAGE_WHISPER),
                SceneBinding.parse("whisper_steps_01"));
        assertEquals(
                new SceneBinding(SceneProfile.FOOTSTEPS_01, HauntChoreography.STAGE_CLOSING),
                SceneBinding.parse("closing_steps_01"));
        assertEquals(SceneProfile.ECHO_01, SceneBinding.parse("echo_01").profile());
        assertEquals(0, SceneBinding.parse("echo_01").stage());
    }

    @Test
    void explicitStageCannotExceedTheFamily() {
        SceneBinding rift = SceneBinding.parse("rift_01");
        assertEquals(RiftChoreography.STAGE_UNMOOR, rift.withStage(2).stage());
        assertThrows(IllegalArgumentException.class, () -> rift.withStage(4));
        assertThrows(IllegalArgumentException.class, () -> SceneBinding.parse("unknown"));
    }
}
