# ZapeG Runtime roadmap

The runtime is a neutral scene engine. Campaign names, story prose, timing and
Discord delivery stay server-side so shipped client strings do not explain the
mystery.

## v0.1 — private apparition

- Target-only S2C scene packet; observers receive nothing.
- `echo_01`: elongated black figure, low-alpha red/cyan temporal copies,
  sub-pixel jitter and sparse HUD-edge faults.
- Actual client camera, frustum and block ray determine visibility.
- About 175 ms of direct gaze removes the figure; ten-second hard TTL is the
  fallback.
- OP rehearsal/live trigger/status/cancel controls.
- One global active scene, loaded-chunk-only placement and bounded replay
  ledger.
- No entity, item, block, registry entry, AI, collision, combat, loot or client
  persistence.

Promotion requires a two-client test proving that only the selected player
receives/renders the scene, plus first/third-person and
Embeddium/Oculus/Entity-Culling checks with shaders on and off.

## v0.2 — reality distortion

Add allowlisted profiles rather than arbitrary shaders or asset paths:

- `peripheral_01`: advances only while outside direct gaze.
- `threshold_01`: peeks from doorway, tree or wall cover and withdraws when
  exposed.
- `motion_echo_01`: repeats the selected player's own pose and movement
  0.5–1.5 seconds late.
- `light_fault_01`: local desaturation, delayed-looking light and a restrained
  black halo; an overlay fallback remains available when post-processing is
  disabled.
- `sky_mark_01`: an impossible moon, distant eyes or a symbol rendered only in
  the selected client's sky.
- `false_passage_01`: a render-only doorway or corridor collapses as the player
  approaches.
- `chroma_break_01`: bounded half-resolution RGB split and scanline
  displacement.

Also add per-target scenes with a small global cap, authored multi-scene
sequences, intensity/photosensitivity-safe client settings, and a no-post-FX
fallback. Sequences carry only profile IDs, bounded durations and seeds.

## v0.3 — manifestation and combat

Anything that damages, collides or can be attacked becomes server-authoritative:

- A manifested guardian or servant uses a registered Forge entity,
  `SynchedEntityData`, vanilla attributes and deterministic phase state.
- Loaded-chunk-only dark-anchor teleportation with strict caps and cleanup.
- Looking at the real guardian can break a shield, but the server validates
  gaze/line of sight before changing combat state.
- Private shadow clones and distortions may mislead selected players, while
  hitboxes, damage and attack telegraphs remain shared by every participant.
- Restart either restores an explicit encounter checkpoint or aborts and cleans
  up; it never leaves an immortal or duplicated boss.
- Discord audio remains atmosphere. Hearing it is never required for a combat
  mechanic.

The first fight should be a guardian/servant encounter, not a repeatable,
farmable Heraldor boss. Heraldor's own physical manifestation remains a later
one-time narrative gate.

## Director boundary

`/zapegscene` is the low-level scene runtime. A later high-level campaign bridge
will expose OP commands such as:

```text
/heraldor status
/heraldor pause
/heraldor resume
/heraldor phase start <phase>
/heraldor phase advance
/heraldor event trigger <event-key> [target]
/heraldor encounter start|stop <encounter-key>
```

Those commands enqueue a request; the persistent Director validates the phase,
pacing and event UUID, then calls the low-level runtime. Rehearsals never mutate
campaign state, backward phase transitions fail closed, and reset remains a
separate strongly audited host operation.
