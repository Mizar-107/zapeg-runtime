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

## v0.2.1 — escalation slice

Implemented as exact-match protocol `3` (the profile wire-ID set grew, so old
clients must fail the handshake rather than mis-decode):

- `peripheral_01`: a still silhouette that only reads at the edge of vision —
  its alpha collapses as the camera look vector nears the anchor, and a direct
  look resolves it within a blink-long 80 ms dwell. It never tracks the camera.
- `footsteps_01`: sound-only. Eleven seeded vanilla steps circle closer from
  the anchor's direction, stop just over three blocks away, and never arrive;
  the screen stays clean and the scene always ends in silence (TIMEOUT).
- Multi-beat choreography: every scene now plays an arrival beat, one faint
  seeded mid-scene beat, and a resolve beat from the vanilla client-local
  sound allowlist; volume is range-compensated so distant anchors arrive faint
  instead of silent.
- Phase-scaled intensity: `/zapegscene trigger` accepts an optional bounded
  `ttl-ticks` override (1–1200) and the Director scales scene length by
  campaign phase. The wire format is unchanged; the descriptor bound rose to
  1200 ticks to match.

## v0.3 — horror slice

Implemented as exact-match protocol `4` (the profile wire-ID set grew again, so
old clients must fail the handshake rather than mis-decode):

- `sky_mark_01`: an impossible pale moon or a pair of distant eyes rendered
  only in the selected client's sky at a seeded bearing, clamped inside the
  far plane. It breathes slowly, never tracks the camera, and resolves by
  gaze.
- `false_passage_01`: a render-only doorway with a recessed breathing interior
  on distant safe ground. It tears and collapses as the target approaches;
  approach resolves it, gaze never does. It is the first profile with an
  encore: one final beat about thirty seconds after the apparent end.
- `chroma_break_01`: a screen-space corrupted-recording fault — bounded
  RGB-split fringe and slow scanline displacement as a GUI overlay.
  Photosensitivity-safe by construction: capped intensity, slow sine pulse, no
  rapid full-screen flashing.
- `near_miss_01`: a figure crosses just behind the target using the local
  motion history, never entering the crosshair; it resolves on its own.
- `whisper_steps_01`: sound-only. The target's own earlier footsteps replay
  from behind at roughly a ten-second delay; the screen stays clean and the
  scene always ends in silence.
- Scene phasing: every scene opens with a client-local ambience-dip prelude
  (cave-sound swell plus a capped fog/brightness dip through the fog viewport
  event, designed to yield to shader packs), and allowlisted profiles may
  close with a single bounded encore beat.
- Camera-unease layer: sub-degree yaw/pitch jitter, rare brief shake pulses
  and a slow micro-roll while a scene is active, all hard-capped and decaying
  to zero at scene end. Unease, never motion sickness.
- Anchor hints: ground-anchored profiles accept an optional coarse hint so the
  Director can place scenes near places the target actually visits.

Per-target concurrency with a small global cap, authored multi-scene sequences,
intensity/photosensitivity-safe client settings and optional post-processing
remain v0.3.x work. Sequences will carry only profile IDs, bounded durations
and seeds.

## v0.3.0 — colossus slice

Implemented as exact-match protocol `5` (the spawn descriptor gained a bounded
escalation stage, so old clients must fail the handshake rather than
mis-decode):

- `colossus_01`: a roughly hundred-block render-only silhouette on a seeded
  horizon bearing, far beyond loaded chunks. No entity, hitbox, AI, collision
  or loot — it can never be fought or farmed, and it never resolves by gaze.
- Director-tracked escalation: the wire stage (0–4) selects the distance —
  280, 210, 150, 100, then a 70-block finale that stops, holds a watching
  beat with a faint heartbeat, and is simply gone. Each delivered live
  trigger advances the stored stage once; rehearsals read it but never move
  it, and the scheduler can never pick the profile on its own.
- A dedicated heavy footfall-shake mode on the camera-unease layer: deep
  pulses synced to each step (2.5-degree yaw cap, less on pitch/roll, decay
  within about a second) over a faint ground sway. Steps land as pitched-down
  vanilla booms played at the target's own position — pressure through the
  ground, not airborne sound, so distance never makes them silent.
- Deliberate fog handling: the silhouette is drawn with the position-color
  pipeline (which ignores shader fog) and mixes toward the live fog color
  with a stage-scaled strength, so it reads as a dark shape breathing in the
  fog rather than being erased by it. Frustum culling uses an expanded box
  around the anchor; depth testing stays on so foreground terrain occludes
  it honestly.

## v0.3.1 — visitation slice

Implemented as exact-match protocol `6` (the profile wire-ID set grew to 13
with `visitation_01`; a v5 client would fail closed on the unknown id
mid-session, so the handshake refuses the mismatch up front):

- Gaze-pull layer: while an allowlisted scene is in its pull window, the
  rendered camera is dragged toward the apparition's glowing eyes at a
  bounded rate. The player can fight it — mouse input still applies — but
  the pull wins slowly and smoothly, eases in and out, and decays to exactly
  zero on release, so it never leaves residual rotation. It is a render-layer
  offset (the player's real rotation is untouched), composes with the
  camera-unease layer under a combined cap, and is photosensitivity and
  motion-sickness safe: slow maximum rate, no snapping, no flashing. On
  `echo_01` the pull releases as the forced look resolves the scene; on the
  `colossus_01` finale it holds the target's gaze while the figure watches
  back.
- `visitation_01`: the OS-level scare profile. Renders nothing in-game;
  instead the client briefly steps outside the game window — a borderless
  always-on-top face blink (the bundled `visitation_face.png`, faded in and
  out in well under two seconds), a window title that momentarily reads as
  glitched block glyphs (never letters, never words, never a name), a small
  decaying window-position pulse, and an optional taskbar attention flash.
  Everything restores exactly: the original title and geometry always come
  back, the popup never steals keyboard or mouse focus, and the layer fails
  silent on headless or unsupported platforms. Operator-only and
  manifestation-gated on the Director side; never gaze-resolved.
- Per-client opt-out: the `osScares` client config (master switch plus
  face-popup, window-wrongness and taskbar-flash sub-toggles) lets any
  player disable the OS-level beats locally without affecting anyone else.

## v0.4 — manifestation and combat

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
/zapeg-lore director event rehearse apparition <echo|threshold|motion-echo|light-fault|peripheral|footsteps|sky-mark|false-passage|chroma-break|near-miss|whisper-steps|colossus|visitation> <player>
/zapeg-lore director event trigger apparition <echo|threshold|motion-echo|light-fault|peripheral|footsteps|sky-mark|false-passage|chroma-break|near-miss|whisper-steps|colossus|visitation> <player>
/zapeg-lore director colossus reset <player>
/zapeg-lore director discord whisper
/zapeg-lore director voice rehearse
/zapeg-lore director cancel
```

Bare or malformed Director commands reply with a compact usage summary that
spells out the rehearse-vs-trigger difference. `discord whisper` posts one
seeded Turkish unease line through the configured webhook (fail-closed when
unconfigured, audited in SQLite, paced by a per-world cooldown);
`voice rehearse` enqueues a rehearsal-only voice clip through the same gates
as the host-side `admin voice-rehearse`.

Those commands enqueue one short-lived world-bound request; the persistent
Director validates the phase, pacing and event UUID, then calls the low-level
runtime. Rehearsals never mutate campaign state, backward phase transitions
fail closed, and reset remains a separate strongly audited host operation.
