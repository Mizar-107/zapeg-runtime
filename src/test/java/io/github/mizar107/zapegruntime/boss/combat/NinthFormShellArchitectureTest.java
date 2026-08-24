package io.github.mizar107.zapegruntime.boss.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NinthFormShellArchitectureTest {

    private static final Path MAIN = Path.of(
            "src", "main", "java", "io", "github", "mizar107", "zapegruntime");

    @Test
    void shellUsesForgeNativeMultipartAndReservesStablePartIds() throws IOException {
        String boss = combat("NinthFormBoss.java");
        String part = combat("NinthFormPart.java");
        assertTrue(boss.contains("extends LivingEntity"));
        assertTrue(part.contains("extends PartEntity<NinthFormBoss>"));
        assertTrue(boss.contains("public boolean isMultipartEntity()"));
        assertTrue(boss.contains("public PartEntity<?>[] getParts()"));
        assertTrue(boss.contains("ENTITY_COUNTER.getAndAdd(parts.length + 1)"));
        assertTrue(boss.contains("parts[index].setId(id + index + 1)"));
    }

    @Test
    void shellHasStrictIdentityAndSyncedCombatPresentationFields() throws IOException {
        String boss = combat("NinthFormBoss.java");
        assertTrue(boss.contains("requireFields(authority, AUTHORITY_FIELDS"));
        assertTrue(boss.contains("requireUuid(authority, ENCOUNTER"));
        assertTrue(boss.contains("requireUuid(authority, TARGET"));
        assertTrue(boss.contains("authorityRejected = true"));
        assertTrue(boss.contains("EntityDataSerializers.STRING"));
        assertTrue(boss.contains("SYNCED_ATTACK_TICK"));
        assertTrue(boss.contains("SYNCED_PROW_HEALTH"));
        assertTrue(boss.contains("ServerBossEvent"));
        assertTrue(boss.contains("getBoundingBoxForCulling"));
    }

    @Test
    void gatewayIsLoadedOnlyAndRegistrationIsNotGloballyWired() throws IOException {
        String gateway = combat("ForgeNinthFormEntityGateway.java");
        String loaded = combat("NinthFormLoadedFootprint.java");
        String global = Files.readString(MAIN.resolve("ZapeGRuntime.java"));
        assertTrue(gateway.contains("implements NinthFormEntityGateway"));
        assertTrue(loaded.contains("getChunkSource().hasChunk"));
        assertFalse(gateway.contains("getChunk("));
        assertFalse(gateway.contains("setChunkForced"));
        assertFalse(gateway.contains("teleportToWithTicket"));
        assertFalse(global.contains("NinthFormEntities"));
    }

    @Test
    void combatSliceHasNoThirdPartyBossDependencyOrCopiedNamespace() throws IOException {
        String all = "";
        Path directory = MAIN.resolve("boss").resolve("combat");
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                all += Files.readString(file);
            }
        }
        assertFalse(all.toLowerCase().contains("cataclysm"));
        assertFalse(all.toLowerCase().contains("aquamirae"));
        assertFalse(all.contains("com.github.L_Ender"));
    }

    private static String combat(String filename) throws IOException {
        return Files.readString(MAIN.resolve("boss").resolve("combat").resolve(filename));
    }
}
