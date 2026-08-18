# ZapeG Runtime

Owned Forge 1.20.1 client/server runtime for private, bounded story scenes in
the ZapeG pack. It renders target-private apparitions and bounded reality
faults without registering or saving a Minecraft entity.

## v0.3 boundaries

- exact-match protocol `4`; mixed v0.2/v0.3 clients fail the handshake (v4
  extends the profile wire-ID set with `sky_mark_01`, `false_passage_01`,
  `chroma_break_01`, `near_miss_01` and `whisper_steps_01`);
- eleven allowlisted profiles, with stable wire ID `0` retained for `echo_01`;
- OP-triggered rehearsal and live scene commands, with an optional coarse
  anchor hint the Director uses to place scenes near remembered places;
- client-camera visibility and gaze-based disappearance;
- a bounded camera-unease layer (sub-degree jitter, brief shake pulses, slow
  unnatural roll) with strict intensity caps that never fights player control;
- scene phasing: a client-local ambience-dip prelude before the body and, for
  allowlisted profiles, a single bounded encore beat after the apparent end;
- hard expiry and cleanup on logout, death, dimension change and restart;
- no blocks, items, AI, collision, combat, loot, chat, URLs or arbitrary assets.

Profiles are deliberately distinct and bounded:

- `echo_01`: elongated black figure, red/cyan separation and HUD-edge faults;
- `threshold_01`: an asymmetric, threshold-like partial figure that withdraws
  under gaze; it does not claim environment-aware cover placement;
- `motion_echo_01`: a distorted copy wearing the target's own skin, built from
  about 0.6 seconds of bounded local player-position history; dispatch does not
  require a distant ground anchor because that history owns the rendered
  position;
- `light_fault_01`: spatially gated cool darkness, light bands and a restrained
  halo keyed to a short loaded camera-focus anchor, with no figure or world
  mutation. It acknowledges visibility and advances gaze only from a presented,
  non-hidden GUI frame, and requires 1.5 seconds of presented gaze to resolve;
- `peripheral_01`: a still silhouette whose alpha collapses as the camera look
  vector nears it — it only reads at the edge of vision, and a direct look
  resolves it within a blink-long 80 ms dwell. It never tracks the camera;
- `footsteps_01`: sound-only. Eleven seeded vanilla steps circle from the
  anchor's direction toward the target, stop just over three blocks away, and
  never arrive; the screen stays clean and the scene always ends in silence
  (TIMEOUT), never by gaze;
- `sky_mark_01`: an impossible pale mark — a swollen moon, or two distant eyes
  — that only the target's client renders, fixed at a seeded sky bearing and
  clamped inside the far plane so it is always visible when looked at. It
  breathes slowly, never moves across the sky, and resolves by gaze;
- `false_passage_01`: a render-only doorway with a recessed, breathing
  interior placed on distant safe ground. As the target approaches within the
  collapse distance the passage tears and folds in on itself over a bounded
  collapse window; approach resolves it, gaze never does. After the apparent
  end it may sound one final encore beat about thirty seconds later;
- `chroma_break_01`: a screen-space "corrupted recording" fault — a bounded
  RGB-split fringe and slow scanline displacement drawn as a GUI overlay. It
  is photosensitivity-safe by construction: intensity is capped, the pulse is
  a slow sine, and there is no rapid full-screen flashing;
- `near_miss_01`: a figure that crosses just behind the target using the local
  motion history, walking from one side to the other over a bounded crossing
  window. It is placed so it never enters the crosshair; a direct look is
  impossible by construction and the scene resolves on its own;
- `whisper_steps_01`: sound-only. The target hears their own earlier footsteps
  replayed from behind, drawn from the local motion trace at roughly a
  ten-second delay, with a walking gait pitch. The screen stays clean and the
  scene always ends in silence (TIMEOUT), never by gaze.

Figure presentation, direct-gaze progress and the light fault's spatial
activation use the real target camera, frustum and block line of sight. The
figure profiles' restrained HUD-edge residue is intentionally ambient once a
scene has been witnessed, even while its anchor is briefly offscreen or
occluded; gaze cannot advance then. Packets are sent only to the selected
player and client history is discarded when the scene ends.

Every scene opens with a short client-local prelude — a cave-sound swell with
a subtle fog and brightness dip — before the body begins. The prelude is drawn
through the fog viewport event with conservative caps so it yields to shader
packs rather than fighting them. The camera-unease layer adds at most a
fraction of a degree of yaw/pitch jitter, rare brief shake pulses and a slow
micro-roll while a scene is active; all magnitudes are hard-capped and decay
to zero the moment the scene ends or the client cleans up.

Apparition models render only their manually posed base body parts. Player-skin
outer layers, ears and cloak are disabled so baked overlay transforms cannot
detach from the silhouette. The black-figure profiles bake the classic humanoid
model layer that matches their texture; `motion_echo_01` bakes the base wide or
slim player body to match the target's own model. Scene audio is a small
allowlist of vanilla sound events played client-locally on the target's client
only — no custom, remote or server-broadcast audio. Each scene plays an arrival
beat, one faint seeded mid-scene beat, and a resolve beat; sound volume is
range-compensated so distant anchors arrive faint instead of silent.

The public mod name and IDs stay generic. Campaign names, prose, timing and
Discord behavior remain server-side in the Heraldor Director.

## Operator commands

Permission-level-2 in-game operators and authenticated RCON may use:

```text
/zapegscene status
/zapegscene rehearse <online-player> [profile]
/zapegscene trigger <online-player> <event-uuid> <profile> [ttl-ticks] [hint-x hint-z]
/zapegscene cancel-all
```

`profile` is one of `echo_01`, `threshold_01`, `motion_echo_01`,
`light_fault_01`, `peripheral_01`, `footsteps_01`, `sky_mark_01`,
`false_passage_01`, `chroma_break_01`, `near_miss_01`, or `whisper_steps_01`.
Arbitrary shader names, asset paths and URLs are rejected.

`rehearse` is a manual, non-consuming scene at the profile's default length.
`trigger` accepts a stable UUID for Director idempotency plus an optional
`ttl-ticks` override (1–1200) so the Director can scale scene length with
campaign phase; the server clamps it to the same bound the wire descriptor
enforces. Ground-anchored profiles also accept an optional coarse `hint-x
hint-z` pair; placement then prefers safe ground near the hint while still
keeping its distance from the target. The current slice still allows one
global scene and logs every operator dispatch. Command blocks and functions
cannot invoke the command tree.

See [ROADMAP.md](ROADMAP.md) for the reality-distortion and later combat plan.

## Development

```powershell
.\gradlew.bat test build
```

The release jar is `build/libs/zapeg-runtime-forge-1.20.1-0.2.0.jar`.
