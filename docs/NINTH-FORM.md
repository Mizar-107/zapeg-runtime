# Ninth Form encounter contract

The Ninth Form is Heraldor campaign nodes `first_shape` (27) and `last_shape`
(28). Rehearsal uses the same entity and combat code but creates no story
barrier, reward, or campaign transition.

## Arena and ownership

- The player's current position becomes the center of a 48-block virtual
  arena. Every intersecting chunk must already be resident.
- Arenas are at least 128 blocks apart and at most four bosses may be loaded.
- No chunk ticket, forced chunk, pathfinding, block mutation, explosion, or
  physical loot is used.
- The target UUID owns the attempt and story credit. Up to eight nearby live,
  non-spectating players are counted when the attempt starts; scaling caps at
  the five-player tier.
- Leaving the arena, dying, logging out, or changing dimension suspends the
  exact entity. It can resume only after the target returns within 48 blocks
  and the complete arena is resident.

| Players | Health | Damage |
| ---: | ---: | ---: |
| 1 | 1.00× | 1.00× |
| 2 | 1.45× | 1.08× |
| 3 | 1.85× | 1.15× |
| 4 | 2.20× | 1.21× |
| 5+ | 2.50× | 1.27× |

## Damage surfaces

Base parent health is 900. During phase one, ordinary hull hits deal 0.35×
damage with a 2% max-health cap and cannot lower the parent below 55%. The
prow lantern and port/starboard moorings each take 1.0× damage with a 4% cap.
All three must break before the interlude proof is issued.

During the final phase, the keel-heart takes 1.6× damage with a 5% cap. The
aft surface and direct parent remain deliberately inefficient. Player melee
and projectiles owned by a live player inside the arena are accepted; display
names and command text are never damage authority.

## Attacks

Every attack has a windup of at least 24 ticks, a bounded active window, and a
recovery window. Selection is derived from encounter UUID, phase, and persisted
cycle with no mutable random source and no immediate repeat. Anchorfall locks
its impact point at windup start. Directional attacks track only during windup
and keep the locked yaw throughout active and recovery windows. Reloading an
active attack preserves its cycle but restarts the complete windup.

## Persistence and proof

`zapeg_runtime_ninth_form` schema 1 permits 32 active attempts and 4,096
immutable barriers. Unknown, future, or corrupt roots are copied back without
mutation and become read-only. Entity UUID, encounter UUID, target UUID, and
generation are distinct. A stale generation cannot join or complete a current
attempt.

Phase-one completion is canonicalized to all three weak points broken before
save. Combat signals enter a bounded, deduplicating inbox and are consumed at
server end-tick, outside the entity tick. Story facts use deterministic IDs
bound to encounter, target, campaign revision/fingerprint, recovery epoch,
fact type, and subject. Unresolved starts and proofs retry with bounded
20–1,200 tick backoff; they never load a chunk.

## Operator playtest

1. Run `/heraldor ninth_form rehearse <target>` as a direct level-2 player or
   authenticated console/RCON source.
2. Confirm the awakening cue, boss bar, base/emissive wreck, three distinct
   breakable weak points, and six readable telegraphs.
3. Walk beyond 48 blocks, return, and confirm the old entity is discarded and
   one exact-generation replacement resumes with preserved vitality.
4. Break all three weak points and confirm the same encounter enters its final
   phase; destroy the keel-heart and confirm the banish cue and entity cleanup.
5. Run `/heraldor ninth_form status <target>` before and after. Rehearsal must
   not advance the journal, grant the toast, or create story barriers.
6. Repeat a live node-27 attempt across one server restart, then verify the
   node-27 phase proof and node-28 defeat proof each advance exactly once.
