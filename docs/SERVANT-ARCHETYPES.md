# Native Servant archetypes

The runtime has exactly three server-authoritative Servant archetypes. All
three use the same registered entity type so existing worlds, renderers, and
the original Stalker-compatible commands remain valid.

| Archetype | Readable special | Telegraph | Range | Special damage | Bounded follow-up |
|---|---|---:|---:|---:|---|
| Stalker | close rend | 14 ticks | 5.25 blocks | 5 | small knockback |
| Herald | omen toll | 28 ticks | 12 blocks | 4 | Weakness I, 4 seconds |
| Binder | closing chain | 24 ticks | 9 blocks | 3 | Slowness II, 3 seconds |

Every special begins with a visible glowing entity, hostile sound, and
server-broadcast particles. The target also receives an action-bar warning.
At resolution the server rechecks owner UUID, life, dimension, loaded corridor,
line of sight, and range. Failure of any check is a miss. Effects use normal
synced Minecraft entity state; there is no private client-only attack.

Cooldown jitter is a pure function of the encounter UUID, archetype, and
persisted completed-special sequence. It is reproducible after reload and
cannot exceed the archetype's fixed bound. Normal melee and incoming damage are
also restricted to the designated target UUID. The entity yields no loot or XP.

## Operator controls

All commands require permission level 2 and use Brigadier's typed online-player
argument; names are never interpolated into another command.

```text
/heraldor servant rehearse <target> stalker
/heraldor servant rehearse <target> herald
/heraldor servant rehearse <target> binder
/heraldor servant awaken <target> <stalker|herald|binder> event <uuid>
/heraldor servant status <target>
/heraldor servant dismiss <target>
/heraldor servant victories
```

`rehearse` uses a fresh event UUID but never awards progression. `status`
reports archetype, rehearsal flag, entity/load state, telegraph timing, total
campaign victories, and durable per-archetype victory counts. The legacy
`awaken <target> ...` forms remain aliases for Stalker.

`victories` takes no player input and emits exactly one bounded, machine-readable
line from the durable Servant ledger:

```text
servant_victories schema=2/2 writable=1 live_victories=3 stalker_victories=1 herald_victories=1 binder_victories=1
```

The values are global live-victory counts; rehearsals are never included. The
command exposes no UUIDs or per-player data. If the saved-data schema is newer
or otherwise unsupported, it fails instead of reporting a misleading zero:

```text
servant_victories schema=unsupported writable=0
```

The separate KubeJS `/zapeg-lore servant ...` subtree is retired. The runtime
cancels that already-parsed subtree and prints the native replacements; it does
not forward the old command or interpolate its target. Use `/heraldor servant
rehearse <online_player> stalker` for a safe smoke test, then `/heraldor servant
status <online_player>` or `/heraldor servant dismiss <online_player>`. Other
`/zapeg-lore` children are not changed by this migration guard.

## Loaded-chunk and lifecycle contract

Spawn block reads occur only after the block column is resident, and collision
queries occur only after every chunk intersecting the entity box is resident.
Melee navigation and special line-of-sight checks require a bounded, fully
resident corridor between Servant and target. No global entity scan, random
wander, teleport, or chunk request is used.

Forge 47.4.10 / vanilla 1.20.1 routes `MeleeAttackGoal.canUse()` through
`PathNavigation.createPath(Entity, 0)`. That overload constructs its
`PathNavigationRegion` from `FOLLOW_RANGE + 16` blocks around the mob. Before
entering the vanilla goal, the Servant verifies every chunk in that complete
region with `ServerChunkCache.hasChunk`; it also verifies every retained path
node. The wrapper opts into every-tick goal updates, which run before
`PathNavigation.tick`, so the same fail-closed check precedes delayed path
recomputation. A missing chunk stops navigation rather than asking vanilla to
build the region.

Logout, target death, dimension change, expiry, and dismissal remove the
active record and its exact entity without credit. A normal server stop keeps
the record and entity for restart. Reconciliation accepts only an exact match
of encounter UUID, target UUID, entity UUID, dimension, mode, deadline, and
archetype; a missing entity gets one replacement attempt and a stale twin is
rejected when it later loads.

## Durable victory integration

`ServantEncounterData.get(server).liveVictories()` is the authoritative,
non-evicting, UUID-sorted snapshot. Each immutable barrier exposes:

- `encounterId()` — global durable fact/idempotency UUID;
- `targetId()` — owning player UUID;
- `archetype()` — `STALKER`, `HERALD`, or `BINDER` (`id()` is lowercase).

Use `encounterId()` as the downstream receipt key and replay this list on
startup. `ServantVictoryEvent` is advisory only: it may be absent after a crash
or repeated during reconciliation, so campaign gameplay must not depend on
receiving it.
