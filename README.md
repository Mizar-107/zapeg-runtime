# ZapeG Runtime

Owned Forge 1.20.1 client/server runtime for private, bounded story scenes in
the ZapeG pack. It renders target-private apparitions and bounded reality
faults without registering or saving a Minecraft entity.

## v0.2 boundaries

- exact-match protocol `3`; mixed v0.1/v0.2 clients fail the handshake (v3
  extends the profile wire-ID set with `peripheral_01` and `footsteps_01`);
- six allowlisted visual profiles, with stable wire ID `0` retained for
  `echo_01`;
- OP-triggered rehearsal and live scene commands;
- client-camera visibility and gaze-based disappearance;
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
  (TIMEOUT), never by gaze.

Figure presentation, direct-gaze progress and the light fault's spatial
activation use the real target camera, frustum and block line of sight. The
figure profiles' restrained HUD-edge residue is intentionally ambient once a
scene has been witnessed, even while its anchor is briefly offscreen or
occluded; gaze cannot advance then. Packets are sent only to the selected
player and client history is discarded when the scene ends.

Figure presentation, direct-gaze progress and the light fault's spatial
activation use the real target camera, frustum and block line of sight. The
figure profiles' restrained HUD-edge residue is intentionally ambient once a
scene has been witnessed, even while its anchor is briefly offscreen or
occluded; gaze cannot advance then. Packets are sent only to the selected
player and client history is discarded when the scene ends.

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
/zapegscene trigger <online-player> <event-uuid> <profile> [ttl-ticks]
/zapegscene cancel-all
```

`profile` is one of `echo_01`, `threshold_01`, `motion_echo_01`,
`light_fault_01`, `peripheral_01`, or `footsteps_01`. Arbitrary shader names,
asset paths and URLs are rejected.

`rehearse` is a manual, non-consuming scene at the profile's default length.
`trigger` accepts a stable UUID for Director idempotency plus an optional
`ttl-ticks` override (1–1200) so the Director can scale scene length with
campaign phase; the server clamps it to the same bound the wire descriptor
enforces. The current v0.2 slice still allows one global scene and logs every
operator dispatch. Command blocks and functions cannot invoke the command tree.

See [ROADMAP.md](ROADMAP.md) for the reality-distortion and later combat plan.

## Development

```powershell
.\gradlew.bat test build
```

The release jar is `build/libs/zapeg-runtime-forge-1.20.1-0.2.0.jar`.
