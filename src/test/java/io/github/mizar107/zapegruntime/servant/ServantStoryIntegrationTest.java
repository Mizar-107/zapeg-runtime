package io.github.mizar107.zapegruntime.servant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ServantStoryIntegrationTest {

    @Test
    void everyTypedArchetypeMapsToItsClosedStorySubject() {
        assertEquals(
                "zapeg_runtime:stalker_01",
                ServantProgressionSync.storySubject(ServantArchetype.STALKER).toString());
        assertEquals(
                "zapeg_runtime:herald_01",
                ServantProgressionSync.storySubject(ServantArchetype.HERALD).toString());
        assertEquals(
                "zapeg_runtime:binder_01",
                ServantProgressionSync.storySubject(ServantArchetype.BINDER).toString());
    }
}
