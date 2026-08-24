# Heraldor 1.1 safety release

Heraldor 1.1 retains protocol 11 and the complete 1.0 story, scene, Servant,
Voice, hidden-journal, quest, and Ninth Form content. Forge metadata uses
`MATCH_VERSION`, so every client and the server must still replace 1.0 with the
same 1.1 JAR before joining.

## Authority model

Every world starts `quarantined`. Runtime behavior is divided into four modes:

- `quarantined`: diagnostics, stop, and cleanup only;
- `manual`: explicit rehearsals;
- `live`: explicit operator live actions;
- `auto`: authored autonomous story and Director work.

The persisted mode is bounded by `HERALDOR_MAX_MODE` (default `MANUAL`). An
unknown ceiling value quarantines. Lowering the ceiling below stored authority
destroys that hidden authority, rotates the nonce, and requires a fresh arm.
Every new Minecraft process also consumes a closed boot latch: even an exact
previously armed disk pair is demoted to `quarantined` before cleanup. Operators
must explicitly arm again after every restart. This is intentional: if every
emergency-stop write fails, an old disk pair can never resurrect native work.

Permission requires three matching proofs: safety SavedData verified from the
actual compressed world file after an fsync, an independently forced and
atomically replaced world authority record, and a zero-active cleanup
certificate for the exact generation in this JVM. Missing, unreadable,
unsupported, corrupt, mismatched, or failed persistence never authorizes work.

## Operator surface

Direct in-game OP:

```text
/heraldor safety status
/heraldor safety stop
/heraldor safety cleanup
/heraldor safety arm manual <nonce>
/heraldor safety arm live <nonce>
/heraldor safety arm auto <nonce>
```

Authenticated RCON can run `status`, `stop`, and `cleanup`, but cannot arm.
This prevents the campaign Director's own credential from releasing its kill
switch. The physical server console uses the equivalent dedicated root:

```text
/heraldorsafety status
/heraldorsafety stop
/heraldorsafety cleanup
/heraldorsafety arm manual|live|auto <nonce>
```

Every arm consumes and rotates the nonce. A duplicate receipt cannot invent a
missing cleanup certificate. Never put an arm command in a function, command
block, startup script, scheduler, or healthcheck.

## Emergency behavior

`stop` installs a quarantine transition barrier before mutating SavedData or
touching active projections. Cleanup cancels all server scenes and timelines,
durable and orphaned loaded Servants, the Ninth Form encounter, Director
queues, and quest sessions. It preserves story, victory, and incident evidence.
Unresolved cleanup keeps the generation uncertified and therefore inert.
If no durable quarantine write can be proved, the command returns
`stop_failed reason=persistence_failed`; the host procedure must stop Minecraft
rather than treating that response as containment. A durable quarantine with
remaining cleanup work likewise returns `stop_failed reason=cleanup_unresolved`.

Servants also check safety at pursuit, goal-tick, and damage boundaries, reject
joins when state is unavailable, and are swept across every loaded dimension.
Compatibility victory replay and direct legacy progression writes require
`auto`; quarantine cannot silently advance the story.

The native brake cannot synchronously stop an already-claimed external Discord
audio job or a Python process. Deployment must pair it with the host emergency
procedure that stops both Heraldor sidecars first and stops Minecraft if any
containment receipt is missing or unresolved.

## Rollout gate

Deploy first to a copied world with the host ceiling `MANUAL`. Leave sidecars
stopped, run `stop`, `cleanup`, and `status`, then arm `manual` with the reported
nonce. Rehearse target privacy, Servants, Voice, every scene, and Ninth Form;
run the emergency brake mid-action; restart twice; and verify nothing resumes
while quarantined. Combat rehearsals can damage players, so use the copied world
and a deliberate test inventory. Heraldor itself does not edit or destroy
terrain.

ZapeG Citizens is a separate mod and is not changed by this release.
