# ZapeG Runtime

Owned Forge 1.20.1 client/server runtime for private, bounded story scenes in
the ZapeG pack. It renders target-private apparitions and bounded reality
faults without registering or saving a Minecraft entity.

## v0.2 boundaries

- exact-match protocol `2`; mixed v0.1/v0.2 clients fail the handshake;
- four allowlisted visual profiles, with stable wire ID `0` retained for
  `echo_01`;
- OP-triggered rehearsal and live scene commands;
- client-camera visibility and gaze-based disappearance;
- hard expiry and cleanup on logout, death, dimension change and restart;
- no blocks, items, AI, collision, combat, loot, chat, URLs or arbitrary assets.

Profiles are deliberately distinct and bounded:

- `echo_01`: elongated black figure, red/cyan separation and HUD-edge faults;
- `threshold_01`: an asymmetric, threshold-like partial figure that withdraws
  under gaze; it does not claim environment-aware cover placement;
- `motion_echo_01`: a distorted copy built from about 0.6 seconds of bounded
  local player-position history; dispatch does not require a distant ground
  anchor because that history owns the rendered position;
- `light_fault_01`: spatially gated cool darkness, light bands and a restrained
  halo keyed to a short loaded camera-focus anchor, with no figure or world
  mutation. It acknowledges visibility and advances gaze only from a presented,
  non-hidden GUI frame, and requires 1.5 seconds of presented gaze to resolve.

Figure presentation, direct-gaze progress and the light fault's spatial
activation use the real target camera, frustum and block line of sight. The
figure profiles' restrained HUD-edge residue is intentionally ambient once a
scene has been witnessed, even while its anchor is briefly offscreen or
occluded; gaze cannot advance then. Packets are sent only to the selected
player and client history is discarded when the scene ends.

Apparition models render only their manually posed base body parts. Player-skin
outer layers, ears and cloak are disabled so baked overlay transforms cannot
detach from the silhouette.

The public mod name and IDs stay generic. Campaign names, prose, timing and
Discord behavior remain server-side in the Heraldor Director.

## Operator commands

Permission-level-2 in-game operators and authenticated RCON may use:

```text
/zapegscene status
/zapegscene rehearse <online-player> [profile]
/zapegscene trigger <online-player> <event-uuid> <profile>
/zapegscene cancel-all
```

`profile` is one of `echo_01`, `threshold_01`, `motion_echo_01`, or
`light_fault_01`. Arbitrary shader names, asset paths and URLs are rejected.

`rehearse` is a manual, non-consuming scene. `trigger` accepts a stable UUID for
Director idempotency. The current v0.2 slice still allows one global scene and
logs every operator dispatch. Command blocks and functions cannot invoke the
command tree.

See [ROADMAP.md](ROADMAP.md) for the reality-distortion and later combat plan.

## Development

```powershell
.\gradlew.bat test build
```

The release jar is `build/libs/zapeg-runtime-forge-1.20.1-0.2.0.jar`.
