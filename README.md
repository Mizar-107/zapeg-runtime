# ZapeG Runtime

Owned Forge 1.20.1 client/server runtime for private, bounded story scenes in
the ZapeG pack. It renders target-private apparitions and bounded reality
faults without registering or saving a Minecraft entity.

## v0.4 boundaries

- exact-match protocol `7`; mixed v0.3.x (protocol 6) clients fail the
  handshake. v7 adds `rift_01` at wire ID 13 and lets the existing descriptor
  `stage` field select haunt/rift beats. A v6 client would reject a non-colossus
  stage (or unknown id 13) mid-session, so the mismatch is refused up front.

## v0.3 boundaries

- exact-match protocol `6`; mixed v0.3.0/v0.3.1 clients fail the handshake
  (v6 adds `visitation_01` at wire ID 12; the descriptor layout is unchanged,
  but a v5 client would fail closed on the unknown ID mid-session, so the
  mismatch is refused up front);
- thirteen allowlisted profiles, with stable wire ID `0` retained for
  `echo_01`;
- OP-triggered rehearsal and live scene commands, with an optional coarse
  anchor hint the Director uses to place scenes near remembered places;
- client-camera visibility and gaze-based disappearance;
- a bounded camera-unease layer (sub-degree jitter, brief shake pulses, slow
  unnatural roll) with strict intensity caps that never fights player control,
  plus a dedicated heavy footfall-shake mode reserved for the colossus;
- a gaze-pull layer: during an allowlisted scene's pull window the rendered
  camera is dragged toward the apparition's glowing eyes at a slow bounded
  rate. The player can fight it but the pull wins, smoothly; it eases in and
  out, decays to exactly zero on release, never touches the player's real
  rotation, and composes with the unease layer under a combined cap;
- scene phasing: a client-local ambience-dip prelude before the body and, for
  allowlisted profiles, a single bounded encore beat after the apparent end;
- GUI-hold: a scene that arrives while any screen is open (chat, inventory,
  a modpack terminal) is acknowledged as delivered but held, starting — with
  its presented TTL — only when the screen closes; a newer spawn replaces a
  held one, cancel/logout clears it, and the server-side occupancy expiry
  bounds the wait. A screen opening mid-scene never aborts the scene;
- hard expiry and cleanup on logout, death, dimension change and restart;
- no blocks, items, AI, collision, combat, loot, chat, URLs or arbitrary assets.

Profiles are deliberately distinct and bounded:

- `echo_01`: elongated black figure, red/cyan separation and HUD-edge faults;
- `threshold_01`: an asymmetric, threshold-like partial figure that withdraws
  under gaze; it does not claim environment-aware cover placement;
- `motion_echo_01`: a distorted copy wearing the target's own skin, built from
  about 0.6 seconds of bounded local player-position history; dispatch does not
  require a distant ground anchor because that history owns the rendered
  position. The newest copy keeps the target's face but its eyes glow the
  signature ember orange — the tell that the copy is wrong;
- `light_fault_01`: spatially gated cool darkness, light bands and a restrained
  halo keyed to a short loaded camera-focus anchor, with no figure or world
  mutation. It acknowledges visibility and advances gaze only from a presented,
  non-hidden GUI frame, and requires 1.5 seconds of presented gaze to resolve;
- `peripheral_01`: a still silhouette whose alpha collapses as the camera look
  vector nears it — it only reads at the edge of vision, glowing eyes
  included, over a wide angular ramp with a ~5-tick temporal ease so it
  dissolves rather than pops, and a direct look resolves it within a short
  140 ms dwell. It never tracks the camera;
- `footsteps_01`: sound-only. Eleven seeded vanilla steps circle from the
  anchor's direction toward the target, stop just over three blocks away, and
  never arrive; the screen stays clean and the scene always ends in silence
  (TIMEOUT), never by gaze;
- `sky_mark_01`: an impossible pale mark — a swollen moon, or two distant
  ember-orange eyes — that only the target's client renders, fixed at a seeded
  sky bearing and clamped inside the far plane so it is always visible when
  looked at. It breathes slowly, never moves across the sky, and resolves by
  gaze;
- `false_passage_01`: a render-only doorway with a recessed, breathing
  interior placed on distant safe ground. As the target approaches within the
  collapse distance the passage tears and folds in on itself over a bounded
  collapse window — and only then, mid-fold, two ember eyes are briefly
  visible deep inside it. Approach resolves it, gaze never does. After the
  apparent end it may sound one final encore beat about thirty seconds later;
- `chroma_break_01`: a screen-space "corrupted recording" fault — a bounded
  RGB-split fringe and slow scanline displacement drawn as a GUI overlay. It
  is photosensitivity-safe by construction: intensity is capped, the pulse is
  a slow sine, and there is no rapid full-screen flashing;
- `near_miss_01`: a figure that crosses just behind the target using the local
  motion history, walking from one side to the other over a bounded crossing
  window, eyes glowing. It is placed so it never enters the crosshair; a
  direct look is impossible by construction and the scene resolves on its own;
- `whisper_steps_01`: sound-only. The target hears their own earlier footsteps
  replayed from behind, drawn from the local motion trace at roughly a
  ten-second delay, with a walking gait pitch. The screen stays clean and the
  scene always ends in silence (TIMEOUT), never by gaze;
- `colossus_01`: a roughly hundred-block humanoid silhouette standing far
  beyond loaded chunks, rendered only on the target's client — no entity,
  hitbox or loot, and never gaze-resolved. The wire stage (0–4) picks the
  distance: a horizon smudge at 280 blocks, then 220, 160, 110, and finally a
  towering 70-block near-presence that stops, watches for a held beat while
  its eyes slowly narrow, and is simply gone. Two ember-orange eyes sit
  slightly too far apart on its face, additive and unfogged, so they read at
  every distance and are the last thing visible as it fades. Each footfall
  lands as a deep pitched-down boom at the target's position synced with a
  heavy camera pulse; the figure rocks and breathes in the fog, which is
  mixed manually because the position-color pipeline ignores shader fog. The
  anchor is a seeded horizon bearing pinned to the target's feet — nothing
  collides, so no ground scan runs at those distances. On low render
  distances the two far stages are pulled inside the client's own far plane
  (render-only, never the wire anchor) and scaled to match, so the horizon
  silhouette still reads instead of being clipped away;
- `rift_01`: staged manifestation overlay (wire stage 0–3). Eclipse is a
  bounded near-black wash plus a strong vanilla fog pull that yields to
  shader packs on the fog plane; tear is the old chroma-break recording
  fault; unmoor is a slow hue crawl, chromatic smear and a few pixels of
  warp (photosensitivity-capped, never a strobe); witness cancels vanilla
  HUD overlays and holds two oversized ember eyes on a 70-tick breathe.
  Never gaze-resolved. Public aliases (`light-fault`, `chroma-break`,
  `eclipse`, `unmoor`, `witness`) map onto these stages;
- `visitation_01`: the OS-level scare. Nothing renders in-game; instead the
  client briefly steps outside the game window. A borderless always-on-top
  window shows a bundled image (shipped at a deliberately boring asset path)
  for a faded blink of well under two seconds; the game window title
  momentarily reads as glitched block glyphs — never letters, words or a
  name; the window position shivers through a small decaying pulse; and an
  optional taskbar attention flash rides the blink (flashing the game
  window's own taskbar button, so it works even with the face popup opted
  out). Everything restores exactly: the true title and geometry always come
  back, an early cancel or logout disposes a shown popup immediately, the
  popup never steals keyboard or mouse focus, nothing persists, and the
  layer fails silent on headless or unsupported platforms. The face popup
  and taskbar flash are Windows-only (skipped silently elsewhere — a macOS
  AWT init under GLFW can hang the JVM); the title and window-pulse beats
  are plain GLFW and run everywhere. The scene never resolves by gaze and
  the game screen itself stays clean.

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
to zero the moment the scene ends or the client cleans up. The colossus uses a
separate heavy mode on the same layer: deep, slow footfall pulses (capped at
2.5 degrees of yaw, less on pitch and roll, decaying within about a second)
over a faint ground sway — the ground answering each step, never a fight for
control.

Apparition models render only their manually posed base body parts. Player-skin
outer layers, ears and cloak are disabled so baked overlay transforms cannot
detach from the silhouette. The black-figure profiles bake the classic humanoid
model layer that matches their texture; `motion_echo_01` bakes the base wide or
slim player body to match the target's own model.

Every humanoid figure carries the same signature: two ember-orange eyes riding
the animated head pose. They are drawn as additive position-color quads — the
textureless twin of vanilla's `RenderType.eyes` (the spider/enderman approach),
so no asset is shipped — unfogged, unlit and steady, with a soft oversized halo
behind each bright core. They never flash or strobe, they dim as the camera
leaves the figure's front hemisphere instead of shining through the head, and
on the colossus they hold at full strength while the body fades, so the eyes
are always the last thing visible. Scene audio is a small
allowlist of vanilla sound events played client-locally on the target's client
only — no custom, remote or server-broadcast audio — on the ambient sound
channel, which players do not mute the way grinders mute hostiles. Each scene plays an arrival
beat, one faint seeded mid-scene beat, and a resolve beat; sound volume is
range-compensated so distant anchors arrive faint instead of silent.

The public mod name and IDs stay generic. Campaign names, prose, timing and
Discord behavior remain server-side in the Heraldor Director.

## Client configuration

The OS-level scare layer (`visitation_01`) is governed by a per-client config
at `config/zapeg_runtime-client.toml`, so any player can opt out locally
without affecting anyone else. All toggles default to `true` on this
friends-only server:

```toml
[osScares]
# Master switch; when false, visitation scenes do nothing on this client.
enabled = true
# The brief borderless always-on-top face blink.
facePopup = true
# The glitched window title and the small window pulse.
windowWrongness = true
# The taskbar/dock attention flash riding the face blink.
taskbarFlash = true
```

Every other scene layer (apparitions, sounds, camera unease, gaze pull) has
no client toggle and is unaffected by these settings.

## Operator commands

Permission-level-2 in-game operators and authenticated RCON may use:

```text
/zapegscene status
/zapegscene rehearse <online-player> [profile] [stage]
/zapegscene trigger <online-player> <event-uuid> <profile> [ttl-ticks] [hint-x hint-z]
/zapegscene trigger <online-player> <event-uuid> colossus_01 stage <0-4> [ttl-ticks]
/zapegscene cancel-all
```

`profile` is one of `echo_01`, `threshold_01`, `motion_echo_01`,
`light_fault_01`, `peripheral_01`, `footsteps_01`, `sky_mark_01`,
`false_passage_01`, `chroma_break_01`, `near_miss_01`, `whisper_steps_01`,
`colossus_01`, or `visitation_01`. Arbitrary shader names, asset paths and
URLs are rejected.

`rehearse` is a manual, non-consuming scene at the profile's default length;
for `colossus_01` it accepts an optional stage (0–4) so any approach step can
be previewed without touching the Director's escalation state. `trigger`
accepts a stable UUID for Director idempotency plus an optional `ttl-ticks`
override (20–1200) so the Director can scale scene length with campaign phase;
the server clamps it into the same bounds the wire descriptor enforces, and
the descriptor is validated before the event id is consumed so a rejected
dispatch never burns a deterministic beat id. For
`colossus_01` the stage travels as an explicit bounded argument after a
`stage` literal; it is rejected for every other profile. Ground-anchored
profiles also accept an optional coarse `hint-x hint-z` pair; placement then
prefers safe ground near the hint while still keeping its distance from the
target. The current slice still allows one global scene and logs every
operator dispatch. Command blocks and functions cannot invoke the command
tree.

See [ROADMAP.md](ROADMAP.md) for the reality-distortion and later combat plan.

## Development

```powershell
.\gradlew.bat test build
```

The release jar is `build/libs/zapeg-runtime-forge-1.20.1-0.4.0.jar`
(protocol 7 — server, tracked `overrides/mods` jar and both client artifacts
must all carry this same build).
