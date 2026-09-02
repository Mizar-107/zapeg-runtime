package io.github.mizar107.zapegruntime.boss.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NinthFormCombatArchitectureTest {

    private static final Path COMBAT = Path.of(
            "src", "main", "java", "io", "github", "mizar107", "zapegruntime",
            "boss", "combat");

    @Test
    void combatHasNoWorldMutationPathfindingOrChunkAcquisitionSurface() throws IOException {
        String all = allCombatSource();
        assertFalse(all.contains("getChunk("));
        assertFalse(all.contains("setChunkForced"));
        assertFalse(all.contains("teleportToWithTicket"));
        assertFalse(all.contains("PathNavigation"));
        assertFalse(all.contains("Goal"));
        assertFalse(all.contains("setBlock("));
        assertFalse(all.contains("destroyBlock("));
        assertFalse(all.contains("explode("));
        assertFalse(all.contains("new Explosion"));
        assertFalse(all.contains("setSecondsOnFire"));
        assertFalse(all.contains("StoryService"));
        assertFalse(all.contains("net.minecraft.client"));
        assertTrue(all.contains("getChunkSource().hasChunk"));
    }

    @Test
    void damageRoutesThroughPartsAndProjectileOwnerAttribution() throws IOException {
        String boss = source("NinthFormBoss.java");
        String part = source("NinthFormPart.java");
        String engine = source("NinthFormCombatEngine.java");
        String attribution = source("NinthFormDamageAttribution.java");
        assertTrue(part.contains("getParent().hurtPart(kind, source, amount)"));
        assertTrue(boss.contains("NinthFormCombatEngine.hurtParent(this, source, amount)"));
        assertTrue(engine.contains("NinthFormDamagePolicy.routePart("));
        assertTrue(engine.contains("NinthFormDamagePolicy.routeParent("));
        assertTrue(attribution.contains("source.getEntity()"));
        assertTrue(attribution.contains("projectile.getOwner() instanceof ServerPlayer"));
        assertFalse(attribution.contains("getGameProfile().getName()"));
    }

    @Test
    void typedSignalsAlwaysUseImmutableEncounterTargetCredit() throws IOException {
        String boss = source("NinthFormBoss.java");
        String engine = source("NinthFormCombatEngine.java");
        assertTrue(boss.contains("identity.targetId(),"));
        assertTrue(engine.contains("NinthFormCombatSignal.Kind.PHASE_COMPLETED"));
        assertTrue(engine.contains("NinthFormCombatSignal.Kind.DEFEATED"));
        assertTrue(engine.contains("NinthFormCombatSignal.Kind.SUSPENDED"));
        assertFalse(boss.contains("StoryService"));
        assertFalse(engine.contains("StoryService"));
    }

    @Test
    void attackCursorIsSyncedAndEveryRecoveryReentersWindup() throws IOException {
        String boss = source("NinthFormBoss.java");
        String engine = source("NinthFormCombatEngine.java");
        assertTrue(boss.contains("SYNCED_ATTACK_CYCLE"));
        assertTrue(boss.contains("SYNCED_ATTACK_ID"));
        assertTrue(boss.contains("SYNCED_ATTACK_TICK"));
        assertTrue(boss.contains("@Nullable private Vec3 attackAnchor"));
        assertFalse(boss.contains("SYNCED_ATTACK_ANCHOR"));
        assertTrue(boss.contains("SYNCED_ATTACK_YAW"));
        assertTrue(boss.contains("NinthFormRecoveryPolicy.restartAtWindup(request.combatState())"));
        assertTrue(boss.contains("NinthFormRecoveryPolicy.restartAtWindup(combat)"));
        assertTrue(engine.contains("boss.lockAttackAnchor(target.position())"));
        assertTrue(engine.contains("boss.attackAnchor().orElse(origin)"));
        assertTrue(engine.contains("trackTargetDuringWindup(boss, attack, target.position())"));
        assertTrue(engine.contains("window == NinthFormAttack.AttackWindow.WINDUP"));
        assertTrue(engine.contains("boss.applyAttackYaw(lockedYaw.get())"));
        assertTrue(engine.contains("float attackYaw = boss.attackYaw().orElseThrow()"));
        assertFalse(engine.contains("faceTarget("));
    }

    @Test
    void targetRangeAndPhaseHandshakeFailClosed() throws IOException {
        String engine = source("NinthFormCombatEngine.java");
        assertTrue(engine.contains(
                "NinthFormCombatGeometry.insideConfinement(\n                        boss.position(), target.position())"));
        int transition = engine.indexOf(
                "boss.transition(NinthFormPhase.FIRST, NinthFormPhase.INTERLUDE)");
        int proof = engine.indexOf("NinthFormCombatSignal.Kind.PHASE_COMPLETED");
        assertTrue(transition >= 0);
        assertTrue(proof > transition);
        assertTrue(engine.contains("phase = NinthFormPhase.INTERLUDE"));
        int terminalProof = engine.indexOf("NinthFormCombatSignal.Kind.DEFEATED");
        int suspension = engine.indexOf("NinthFormCombatSignal.Kind.SUSPENDED");
        int targetLookup = engine.indexOf("ServerPlayer target =");
        assertTrue(terminalProof >= 0);
        assertTrue(terminalProof < targetLookup);
        assertTrue(targetLookup < suspension);
    }

    @Test
    void persistedCombatScaleUsesTheCanonicalDoubleTableNotTheSyncedFloat() throws IOException {
        String boss = source("NinthFormBoss.java");
        assertTrue(boss.contains("return NinthFormScaling.damageScale(participantCount())"));
        assertTrue(boss.contains("mirror.putDouble(DAMAGE_SCALE, damageScale())"));
        assertFalse(boss.contains("return entityData.get(SYNCED_DAMAGE_SCALE)"));
    }

    @Test
    void exactCleanupDoesNotDependOnEveryFootprintChunkRemainingLoaded() throws IOException {
        String gateway = source("ForgeNinthFormEntityGateway.java");
        int suspend = gateway.indexOf("public ControlResult suspendLoaded");
        int discard = gateway.indexOf("public ControlResult discardLoaded");
        int exactHelper = gateway.indexOf("private Checked checkedExact");
        assertTrue(suspend >= 0 && discard > suspend && exactHelper > discard);
        assertTrue(gateway.substring(suspend, discard).contains("checkedExact(identity, entityId)"));
        assertTrue(gateway.substring(discard, exactHelper).contains("checkedExact(identity, entityId)"));
    }

    private static String allCombatSource() throws IOException {
        StringBuilder result = new StringBuilder();
        try (var files = Files.list(COMBAT)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                result.append(Files.readString(file));
            }
        }
        return result.toString();
    }

    private static String source(String filename) throws IOException {
        return Files.readString(COMBAT.resolve(filename));
    }
}
