# Heraldor 1.0 delivery contract

Heraldor 1.0 turns the existing target-private scene runtime into one
server-authoritative Forge mod. Gameplay-critical state, targeting, combat,
story advancement and diagnostics must not depend on KubeJS, RCON, Python,
SQLite, Discord or voice delivery. Those systems may consume optional events,
but their absence may never stop the campaign.

The existing `zapeg_runtime` mod id is retained for world and client migration.
The public product name becomes Heraldor. No Heraldor batch may edit, import or
depend on `zapeg-citizens`.

## Batch 1 — native foundation

- UUID-keyed, versioned `SavedData` with idempotent milestones.
- Typed `/heraldor` diagnostics; usernames are display text only.
- Native Servant entity and per-player encounter manager.
- Rehearsal/live separation, exact-once victory, no loot or XP, bounded expiry.
- Truthful visitation capability/effect results; packet receipt is not visual
  proof.
- Matching client/server development jar and a recorded test result.

Gate: `Mizar__107` resolves through `EntityArgument.player()`, a rehearsal
Servant can be spawned, fought and cleaned up, a live kill advances exactly
once, and visitation status distinguishes popup/title/motion/taskbar outcomes.

## Batch 2 — deterministic horror

- Data-authored, seeded timelines and per-player sessions.
- `breach_01`, a placement-proof in-game visitation fallback.
- Original registered whispers, knocks, footsteps and manifestation audio.
- Rebuilt optional Windows popup with correct-monitor placement, explicit
  capability reporting and guaranteed cleanup.

Gate: every scene reports requested/applied/fallback/failed effects, private
content remains target-only, and unsupported/disabled OS effects still deliver
the complete in-game scare.

## Batch 3 — story and servants

- Thirty-node hidden-journal questline across prologue, five acts and epilogue.
- Mod-owned advancements, discoveries, artifacts and reset-safe structures.
- Stalker, Herald and Binder Servant archetypes.
- Action-gated progression; long silences affect atmosphere, not completion.

Gate: the campaign completes with all sidecars stopped and FTB Quests acts only
as presentation, never as the authority.

## Batch 4 — Dokuzuncu Suret

- Original GeckoLib manifestation with server-authoritative multipart hit
  regions and deterministic attack timelines.
- Dedicated resettable arena, 1–8 player scaling and restart checkpoints.
- Shared readable damage telegraphs; target-private illusions never conceal
  real damage.
- One-time banishment and non-farmable narrative reward.

Gate: solo/multiplayer, wipe/rejoin, restart, abort, duplicate prevention and
arena cleanup all pass.

## Batch 5 — release

- Forge unit/GameTests and exact ATM9 dedicated-server smoke tests.
- Two-client privacy/shared-combat matrix.
- Embeddium, Oculus, shader, Entity Culling and flight-gear checks.
- Windowed, borderless, fullscreen and mixed-monitor visitation checks.
- Reproducible jar, identical hashes in server/client/update artifacts, no stale
  duplicate jar, and an explicit zero-change audit for `zapeg-citizens`.

Heraldor is not called finished until every gate has evidence.
