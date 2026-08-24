# Heraldor

Server-authoritative Forge 1.20.1 story, horror, and encounter mod for the
ZapeG pack. The stable `zapeg_runtime` mod ID and archive stem are retained for
world and deployment migration.

## Batch 2 (`0.6.0-b2`) deterministic-horror slice

- exact-match protocol `9`; wire IDs 0–13 remain stable and `breach_01` is
  added at ID 14, so protocol-8 clients are refused during the handshake;
- `breach_01` is target-private screen-space choreography with target-local
  sound. It does not inspect a ground anchor, load a chunk, raycast, spawn an
  entity, require a shader or send a sound to observers;
- `visitation_01` always runs the same complete in-game sequence. Its optional
  title/motion/popup/taskbar effects may augment it, but disabling them or
  running on an unsupported platform cannot suppress the fallback;
- fallback status is `requested` when scheduled and becomes `applied` only
  after the GUI hook emits a non-zero frame. Receipt and elapsed ticks remain
  insufficient evidence;
- whispers, knocks, footsteps and manifestation use seven deterministic,
  from-scratch OGG assets registered by the mod. Playback and metadata gain
  are bounded, subtitles ship in English and Turkish, and no recorded or
  remote media enters the generator.

See [docs/BREACH-01.md](docs/BREACH-01.md) for the timing, resource hashes and
integration contract.

## Heraldor `1.1.0`

The five delivery batches now form one native Forge runtime: UUID-authorized
scenes and diagnostics, deterministic horror timelines, a 30-node hidden
journal campaign, automatic Servant encounters, and the original multipart
Ninth Form finale. Gameplay authority does not require Python, RCON, KubeJS,
Discord, voice delivery, Cataclysm, Aquamirae, or GeckoLib. Exact-match
protocol `11` is retained, while Forge `MATCH_VERSION` requires the server and
every joining client to use this exact release.

Release 1.1 adds a fail-closed operational boundary around that content. Fresh
world safety state is quarantined. `manual`, `live`, and `auto` must be armed
explicitly with a rotating nonce, cannot exceed `HERALDOR_MAX_MODE`, and are
admitted only when a second atomic world authority record exactly matches the
forced/read-back SavedData plus a zero-active cleanup certificate. Missing,
corrupt, stale, unwritable, or mismatched authority stays quarantined.
Every Minecraft process starts with a closed boot latch: a previously armed
mode is demoted to quarantine and must be armed again after each restart.

`/heraldor safety stop` is nonce-free and cancels scenes, timelines, Servants,
Ninth Form projections, Director queues, and quest sessions while preserving
story evidence. Direct OP players may arm; authenticated RCON may inspect and
stop but may not re-arm. The physical server console uses the dedicated
`/heraldorsafety` alias. See [docs/RELEASE-1.1.md](docs/RELEASE-1.1.md).

## Batch 4 (`0.8.0-b4`) Ninth Form

- exact-match protocol `11` registers the original multipart `Ninth Form`
  boss; protocol-10 clients are refused before entity state can desynchronize;
- campaign nodes 27 and 28 start one continuous encounter automatically. A
  bounded retry scheduler waits for a fully resident 48-block virtual arena
  without loading chunks, placing tickets, editing blocks, or exploding
  terrain;
- the 900-health spectral wreck has three phase-one weak points, an exposed
  phase-two keel-heart, an armored aft surface, and a parent hull. Per-hit
  multipliers, caps, and the 55% phase-one floor are server-authoritative;
- health and damage scale through fixed 1–5 player tables and remain capped
  for larger groups. Any nearby participant may help, but campaign credit is
  permanently bound to the encounter target UUID;
- keel sweep, anchorfall, undertow, drowned broadside, wake charge, and
  ninefold gaze use deterministic cycles with at least 24 ticks of telegraph,
  locked dodgeable geometry, active windows, and recovery windows;
- exact encounter identity, generation, phase, weak-point vitality and attack
  cursor survive restarts. Phase and defeat proofs are immutable, idempotent,
  target/envelope-bound story barriers; future or corrupt data is preserved
  read-only;
- the parent and five Forge-native parts use an original code-built model,
  exact procedural 512-pixel texture pair, bounded emissive states, four
  original procedural sounds, English/Turkish text, and a toast-only reward;
- no Cataclysm or Aquamirae code, asset, namespace, or runtime dependency is
  used.

Level-2 operators can test without changing campaign progress:

```text
/heraldor ninth_form rehearse <target>
/heraldor ninth_form status <target>
/heraldor ninth_form reconcile <target>
```

See [docs/NINTH-FORM.md](docs/NINTH-FORM.md) for combat values, recovery
semantics, and the playtest checklist. Asset hashes and regeneration commands
are in [docs/NINTH-FORM-ASSETS.md](docs/NINTH-FORM-ASSETS.md).

## Batch 3 (`0.7.0-b3`) story and native Servants

- a strict datapack defines one linear 30-node hidden-journal campaign. Atomic
  reload rejects malformed or incomplete graphs instead of publishing a
  partial story;
- UUID-scoped progress, definition fingerprints, recovery epochs and
  target/payload-bound fact receipts are server-authoritative, restart-safe and
  fail closed on corruption or future schemas;
- durable encounter barriers feed typed story facts directly. Advisory Forge
  events and sidecars never advance the campaign;
- a restart-safe native Director dispatches the ten campaign scene predicates
  from a strict datapack catalog. Only validated active-scene presentation or
  completion evidence advances story; rehearsals and manual triggers never do;
- retained Servant victories are reconciled in current campaign order after
  each applied transition, on login/startup, and through a bounded periodic
  cursor. Out-of-order barriers remain unconsumed;
- `stalker`, `herald`, and `binder` are the complete archetype set. Each uses
  one visible server entity, UUID-owned targeting, shared sound/particle/glow
  telegraphs, and server-resolved bounded damage/effects;
- special timing is derived from encounter UUID plus a persisted sequence—no
  player RNG—and a miss never damages a hidden or out-of-range target;
- spawn collision checks and pursuit corridors are rejected unless every
  touched chunk is already loaded. The Servant never teleports, wanders, or
  asks for a chunk to be generated;
- durable victory barriers now retain the archetype. Schema-1 encounters and
  victories migrate to `stalker`; unknown future schemas remain read-only;
- logout, target death, dimension change, operator dismissal, expiry, and the
  existing one-shot restart recovery close or reconcile the encounter without
  awarding a victory.

See [docs/SERVANT-ARCHETYPES.md](docs/SERVANT-ARCHETYPES.md) for mechanics,
operator commands, and the durable integration contract. See
[docs/HERALDOR-STORY-FOUNDATION.md](docs/HERALDOR-STORY-FOUNDATION.md) for the
campaign schema, replay identity and recovery contract.
See [docs/HERALDOR-DIRECTOR.md](docs/HERALDOR-DIRECTOR.md) for scene mappings,
proof semantics, restart windows, pacing, and UUID diagnostics.

## Batch 1 (`0.5.0-b1`) compatibility history

- exact-match protocol `8`; mixed protocol 7 clients fail the handshake. v8
  adds a fixed four-effect visitation status packet. `RECEIVED` means only
  that the scene arrived; visitation rejects generic `VISIBLE` and `GAZE`
  acknowledgements. Title, motion, popup and taskbar each report four fixed,
  independent dimensions: capability, primary delivery, in-game fallback and
  cleanup, all with bounded reason codes.

## Batch 1 operator smoke

All target arguments below are Brigadier `EntityArgument.player()` values. The
name is resolved to a connected player before execution; it is never pasted
into another command. Run these as a direct level-2+ player or authenticated
RCON source (not through `/execute` or a command block):

```text
/heraldor status Mizar__107
/heraldor diagnose Mizar__107
/heraldor voice rehearse Mizar__107
/heraldor voice rehearse Mizar__107 voice_02
/heraldor voice status Mizar__107
/heraldor servant status Mizar__107
/heraldor servant awaken Mizar__107 rehearsal
/heraldor servant rehearse Mizar__107 stalker
/heraldor servant rehearse Mizar__107 herald
/heraldor servant rehearse Mizar__107 binder
/heraldor servant dismiss Mizar__107
/heraldor servant victories
/zapegscene rehearse Mizar__107 visitation_01
/zapegscene diagnose Mizar__107
```

A rehearsal Servant may be fought normally but its death must leave
`live_victories` unchanged. For the live exact-once gate, generate a fresh UUID
and run `/heraldor servant awaken Mizar__107 event <uuid>`; killing it once must
increment `live_victories` by one, and reusing that UUID must not increment it
again. `dismiss` removes either kind without awarding a victory. The visitation
diagnostic reports each popup/title/motion/taskbar capability, primary result,
in-game fallback and cleanup independently; packet receipt alone is not an
`applied` result.

`/heraldor servant victories` is a no-argument, RCON-compatible aggregate over
the bounded durable victory ledger. Its fixed single-line response includes
schema/writability plus global live and per-archetype counts; it exposes no
player identifiers and fails instead of returning zero for an unsupported
schema.

The old `/zapeg-lore voice rehearse` command tested an optional external
Discord voice channel and is retired. Its native replacement above rehearses
the exact target-private `voice_01` or `voice_02` BREACH presentation; omitting
the final literal selects `voice_01`. It never advances the campaign. For the
privacy smoke, keep a second client beside the target: only the named target
may receive the sound/render sequence, while `voice status` must expose the
event, exact variant, acknowledgement, and whether `VISIBLE` was observed.

The runtime intercepts the retired `/zapeg-lore servant ...` and
`/zapeg-lore voice ...` subtrees and returns a migration message only. It never
forwards or rebuilds the command, so a player name cannot become command text.
Use `/heraldor servant rehearse <online_player> stalker`, `/heraldor servant
status <online_player>`, or `/heraldor servant dismiss <online_player>` for
Servant testing. Use `/heraldor voice rehearse <online_player>`, `/heraldor
voice rehearse <online_player> voice_02`, or `/heraldor voice status
<online_player>` for Voice testing. Every other `/zapeg-lore` child is left
unchanged.

## Batch 2 timeline foundation

Strict datapack definitions drive UUID-scoped, restart-safe sessions and
seeded target-private scene dispatch. Durable terminal barriers make start
requests idempotent without claiming that packet delivery proves client
presentation. See [`docs/HERALDOR-TIMELINES.md`](docs/HERALDOR-TIMELINES.md).

The legacy `awaken <target>`, `awaken <target> rehearsal`, and
`awaken <target> event <uuid>` forms select `stalker`. A typed live run is
`/heraldor servant awaken <target> <stalker|herald|binder> event <uuid>`.

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
- `visitation_01`: a complete target-private `breach_01`-style in-game scare,
  plus optional, default-off OS effects. A borderless always-on-top
  window shows a bundled image (shipped at a deliberately boring asset path)
  for a faded blink of well under two seconds; the game window title
  momentarily reads as glitched block glyphs — never letters, words or a
  name; the window position shivers through a small decaying pulse; and an
  optional taskbar attention flash rides the blink (flashing the game
  window's own taskbar button, so it works even with the face popup opted
  out). Cleanup is explicit and bounded: successful position readback clears
  the captured origin, while a failed restore retains it for retry and is
  reported instead of silently discarded. Early cancel or logout requests
  popup disposal, the popup never steals keyboard or mouse focus, and no
  state is persisted. When opted in, the layer preflights its bundled PNG at
  client setup and reports every effect through fixed non-sensitive status/reason enums.
  The popup is `applied` only after the Swing EDT confirms it is showing with
  nonzero opacity (or showing in the opaque mode); window motion is `applied`
  only after position readback. GLFW's void title and taskbar calls remain
  `requested:unverified_api`, never `applied`; title cleanup likewise remains
  `pending:unverified_api` as a terminal observation because GLFW exposes no
  title readback. Its in-game fallback is requested for every visitation and
  becomes `applied` only after the selected render hook proves a nonempty,
  nonzero-alpha clipped draw. The face
  popup and taskbar flash are Windows-only (reported unsupported elsewhere —
  a macOS AWT init under GLFW can hang the JVM); the title and window-pulse
  beats are plain GLFW and run everywhere. The scene never resolves by gaze
  and suppresses the generic world/fog prelude; its dedicated bounded breach
  choreography and original target-local audio remain active.
  Physical async cleanup owns one bounded diagnostic session across a client
  level unload; another visitation is answered `BUSY` until that terminal
  cleanup report is sent. A true network logout requests local cleanup and
  drops the now-unsendable session immediately. Non-visitation scenes remain
  free to run while cleanup settles.

Figure presentation, direct-gaze progress and the light fault's spatial
activation use the real target camera, frustum and block line of sight. The
figure profiles' restrained HUD-edge residue is intentionally ambient once a
scene has been witnessed, even while its anchor is briefly offscreen or
occluded; gaze cannot advance then. Packets are sent only to the selected
player and client history is discarded when the scene ends.

World-rendered scenes with a configured prelude open with a short client-local
prelude — a cave-sound swell with a subtle fog and brightness dip — before the
body begins. The prelude is drawn through the fog viewport event with
conservative caps so it yields to shader packs rather than fighting them. The
camera-unease layer adds at most a
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
are always the last thing visible. Existing scene audio uses a small allowlist
of vanilla events, while breach uses registered mod-owned synthesis for its
whispers, knocks, footsteps and manifestation. Both paths play client-locally
on the target only — never remotely or server-broadcast — on the ambient sound
channel, which players do not mute the way grinders mute hostiles. Each scene plays an arrival
beat, one faint seeded mid-scene beat, and a resolve beat; sound volume is
range-compensated so distant anchors arrive faint instead of silent.

The public mod name and IDs stay generic. Campaign names, prose, timing and
Discord behavior remain server-side in the Heraldor Director.

## Client configuration

The external OS-effect layer (`visitation_01`) is governed by a per-client
config at `config/zapeg_runtime-client.toml`. Its versioned consent switch
defaults to `false`: each player must explicitly opt in locally. Sub-toggles
default to `true`, but have no effect while versioned consent is off. Missing,
unloaded or unreadable config also means off.

Migration is deliberately non-inheriting. A 0.4.x client may already have
`enabled = true`; that legacy value is now deprecated and ignored. It does not
opt the player in. Only setting `externalEffectsOptInV2 = true` after updating
the mod enables external effects.

```toml
[osScares]
# Deprecated 0.4.x key. Ignored; never grants consent.
enabled = false
# The only external-effect consent authority. Defaults off; the in-game
# visitation fallback always remains available.
externalEffectsOptInV2 = false
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
/zapegscene diagnose <online-player>
/zapegscene rehearse <online-player> [profile] [stage]
/zapegscene trigger <online-player> <event-uuid> <profile> [ttl-ticks] [hint-x hint-z]
/zapegscene trigger <online-player> <event-uuid> colossus_01 stage <0-4> [ttl-ticks]
/zapegscene cancel-all
```

`profile` is one of `echo_01`, `threshold_01`, `motion_echo_01`,
`light_fault_01`, `peripheral_01`, `footsteps_01`, `sky_mark_01`,
`false_passage_01`, `chroma_break_01`, `near_miss_01`, `whisper_steps_01`,
`colossus_01`, `visitation_01`, `rift_01`, or `breach_01`. Arbitrary shader names, asset paths and
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

`diagnose` reports protocol, active/last visitation event and the four latest
client outcomes. Each compact outcome uses `c` (capability), `p` (primary),
`f` (fallback), and `x` (cleanup), for example
`window_title{c=ready,p=requested:unverified_api,f=applied,x=pending:unverified_api}`.
It exposes no paths, exception text, native handles or monitor names. `status`
includes the same bounded report while a visitation is active. A normal scene
acknowledgement is never presented as proof of an OS effect.

See [ROADMAP.md](ROADMAP.md) for the reality-distortion and later combat plan.

## Development

```powershell
.\gradlew.bat test build
```

The final release artifact is
`build/libs/zapeg-runtime-forge-1.20.1-1.1.0.jar`. Protocol remains 11, but
Forge exact-version matching means the server, tracked
`overrides/mods` jar and every client artifact must be replaced atomically
with the same build.

Batch 3 also includes the native, UUID-bound Hidden Journal. It exposes only
the server-authorized completed prefix and current clue, supports five chapter
tabs plus keyboard navigation, and owns two closed journal discoveries. A
pristine player receives only entry zero; corrupt or future story data exposes
nothing. See [docs/HERALDOR-JOURNAL.md](docs/HERALDOR-JOURNAL.md).
