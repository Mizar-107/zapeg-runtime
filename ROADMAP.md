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

## v0.2 — reality distortion slice

Implemented as exact-match protocol `2`, using allowlisted profiles rather
than arbitrary shaders or asset paths:

- `threshold_01`: renders an asymmetric, threshold-like partial body and
  withdraws when exposed; environment-aware cover sampling is not claimed.
- `motion_echo_01`: renders a distorted copy wearing the selected player's own
  skin at their past position and heading, about 0.6 seconds late, from a
  fixed-size transient local history; it bypasses distant safe-ground
  placement.
- `light_fault_01`: cool dimming, displaced light bands and a restrained black
  halo using a bounded overlay and short loaded camera-focus anchor, with no
  blocks or world state changed. Hidden or absent GUI frames neither acknowledge
  presentation nor accumulate its 1.5-second gaze requirement.

All four current profiles, including retained `echo_01`, use the actual camera
for figure/light presentation, block LOS, direct-gaze progress and lifecycle
cleanup. Figure-profile HUD residue may continue while an already-witnessed
anchor is briefly offscreen or occluded, but gaze cannot advance there.
`motion_echo_01` is the only profile that records movement, and its 32-sample
ring is discarded at scene end. Figure profiles disable all detached
player-skin outer layers.

Promotion of 0.2.0 still requires a two-client rehearsal of every profile,
proof that the observer receives/renders nothing, and first/third-person plus
Embeddium/Oculus/Entity-Culling checks. Resize, pause-screen, logout, death and
dimension-change cleanup should be exercised during that rehearsal.

Candidates for a later v0.2.x slice remain:

- `peripheral_01`: advances only while outside direct gaze.
- `sky_mark_01`: an impossible moon, distant eyes or a symbol rendered only in
  the selected client's sky.
- `false_passage_01`: a render-only doorway or corridor collapses as the player
  approaches.
- `chroma_break_01`: bounded half-resolution RGB split and scanline
  displacement.

Per-target concurrency with a small global cap, authored multi-scene sequences,
intensity/photosensitivity-safe client settings and optional post-processing
also remain v0.2.x work. Sequences will carry only profile IDs, bounded
durations and seeds.

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

`/zapegscene` remains the effects-only low-level scene runtime. The server-side
Heraldor Director now exposes a persistent, phase-gated OP/RCON bridge:

```text
/zapeg-lore director status
/zapeg-lore director pause
/zapeg-lore director resume
/zapeg-lore director phase start <presence|servants|manifestation>
/zapeg-lore director phase advance
/zapeg-lore director event rehearse apparition <echo|threshold|motion-echo|light-fault> <player>
/zapeg-lore director event trigger apparition <echo|threshold|motion-echo|light-fault> <player>
/zapeg-lore director cancel
```

Those commands enqueue one short-lived world-bound request; the persistent
Director validates the phase, pacing and event UUID, then calls the low-level
runtime. Rehearsals never mutate campaign state, backward phase transitions
fail closed, and reset remains a separate strongly audited host operation.
