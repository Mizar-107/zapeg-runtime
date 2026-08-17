# ZapeG Runtime

Owned Forge 1.20.1 client/server runtime for private, bounded story scenes in
the ZapeG pack. The first release renders a target-private apparition without
registering or saving a Minecraft entity.

## v0.1 boundaries

- exact client/server packet protocol;
- one allowlisted visual profile (`echo_01`);
- OP-triggered rehearsal and live scene commands;
- client-camera visibility and gaze-based disappearance;
- hard expiry and cleanup on logout, death, dimension change and restart;
- no blocks, items, AI, collision, combat, loot, chat, URLs or arbitrary assets.

The public mod name and IDs stay generic. Campaign names, prose, timing and
Discord behavior remain server-side in the Heraldor Director.

## Operator commands

Permission-level-2 in-game operators and the host console/RCON may use:

```text
/zapegscene status
/zapegscene rehearse <online-player> [echo_01]
/zapegscene trigger <online-player> <event-uuid> echo_01
/zapegscene cancel-all
```

`rehearse` is a manual, non-consuming scene. `trigger` accepts a stable UUID for
Director idempotency. The runtime allows one global scene in v0.1 and logs every
operator dispatch. Command blocks and functions cannot invoke the command tree.

See [ROADMAP.md](ROADMAP.md) for the reality-distortion and later combat plan.

## Development

```powershell
.\gradlew.bat test build
```

The release jar is `build/libs/zapeg-runtime-forge-1.20.1-0.1.0.jar`.
