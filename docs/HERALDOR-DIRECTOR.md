# Heraldor Director

The native Director advances only the scene and retained-Servant barriers in
the 30-node Heraldor campaign. It is a server-thread coordinator inside
`zapeg_runtime`; it does not require KubeJS, Python, RCON, a sidecar, a player
name, a forced chunk, or a command string.

## Scene catalog

`data/zapeg_runtime/heraldor_director/heraldor.json` is a strict, atomic
datapack catalog. Duplicate keys, unknown fields, missing bindings, duplicate
typed triggers, duplicate presentation signatures, invalid stages, and values
outside the TTL/cooldown/backoff bounds reject the candidate reload while the
previous catalog remains active.

The catalog covers every scene predicate in the campaign:

| Story subject | Required proof | Reused native profile | TTL | Stage | Variant |
| --- | --- | --- | ---: | ---: | ---: |
| `voice_01` | completed | `breach_01` | 140 | 0 | 1 |
| `visitation_01` | completed | `visitation_01` | 170 | 0 | 2 |
| `stalker_glimpse_01` | presented | `peripheral_01` | 140 | 0 | 3 |
| `breach_01` | completed | `breach_01` | 180 | 0 | 4 |
| `tide_omen_01` | presented | `sky_mark_01` | 240 | 0 | 5 |
| `voice_02` | completed | `breach_01` | 220 | 0 | 6 |
| `knock_sequence_01` | completed | `breach_01` | 120 | 0 | 7 |
| `colossus_01` | presented | `colossus_01` | 320 | 3 | 8 |
| `procession_01` | completed | `near_miss_01` | 110 | 0 | 9 |
| `shipwreck_vision` | completed | `rift_01` | 200 | 1 | 10 |

The four facts that reuse `breach_01` have different TTLs and presentation
variants. TTL changes the normalized timing between the owned knock, footstep,
whisper, and manifestation cues. The variant is encoded into the stable visual
seed used by the existing owned-audio selection and target-relative placement,
so these story beats do not collapse into four identical scenes. No wire ID or
packet changed; network protocol remains exactly `9`.

## Evidence boundary

Only `SceneServerManager.dispatchDirector` installs a `DirectorSceneIdentity`
in the JVM-local active scene. Rehearsal, manual external trigger, and timeline
dispatch paths always install `null` provenance. Supplying the same event UUID
through an operator command therefore cannot create a story fact.

Packet decoding is not evidence. The server first verifies the sender UUID,
active event UUID, target UUID, and profile acknowledgement allowlist. Only the
validated active scene can call the Director bridge.

- `SCENE_COMPLETED` accepts only client terminal `GAZE` or `TIMEOUT`.
  `TIMEOUT` is the established client acknowledgement after the complete body
  TTL and any encore; the server's expiry timer calls cancellation and can
  never advance story. `BUSY`, `REJECTED`, and `ABORTED` retry instead.
- `SCENE_PRESENTED` accepts `VISIBLE` only from profiles whose existing client
  contract emits it after an actual render/sound presentation. The campaign
  uses `peripheral_01`, `sky_mark_01`, and `colossus_01` for these facts.
  `rift_01` is explicitly excluded because its current acknowledgement occurs
  on a body tick. A visitation presentation may instead use `fallback=applied`
  only after the accepted OS-status report has been committed to the bounded
  effect ledger. Requested, elapsed, decoded, or scheduled work is not proof.

## Restart and replay behavior

Director SavedData schema 1 stores at most one record for each of 2,048 target
UUIDs and never evicts a record. The exact fields bind target, campaign
revision/fingerprint, story epoch/node, typed fact subject, binding
fingerprint, presentation variant, attempt number, stable event UUID, state,
proof, retry time, and bounded outcome. Unknown schemas and structurally
invalid roots are preserved byte-for-byte and become read-only.

The state machine is:

```text
PREPARED -> AWAITING -> PROVEN -> COOLDOWN
     |          |          |
     +--retry---+          +--StoryService receipt/APPLIED
                +--cancel/non-proof terminal -> next stable attempt
```

An event UUID is deterministic for one target/campaign/epoch/node/binding and
attempt. A failed placement retains the unconsumed UUID under backoff. A crash
or failure after event-ledger consumption rotates to the next deterministic
attempt. Evidence is saved as `PROVEN` before story submission, closing the
ack-to-story crash window. On restart, an exact story receipt becomes
`story_receipt_recovered`; absent evidence is never inferred from a consumed
scene event.

A `PROVEN` record with an absent or fingerprint-changed binding becomes
`BLOCKED binding_definition_mismatch`. It cannot be reinterpreted by the new
datapack. Restore the original binding or use an explicit backed-up story
recovery command to rotate the story epoch before a fresh attempt. A proof from
an older recovery epoch is marked superseded and is never submitted into the
new envelope.

## Pacing and loaded-state policy

Every drive pass considers only the server's online player list and the
player's current story node. It delegates placement to the existing loaded-only
scene queries; no level or chunk is created or forced. Each binding has bounded
retry and post-proof cooldowns. At most one active scene and one persisted
Director record exist per target. Receipt preflight prevents dispatch of an
already receipted event.

## Retained Servant barriers

`StoryService` publishes a neutral post-commit `StoryAdvancedEvent`. The
Director subscriber only queues the target UUID; it never submits another fact
inside the synchronous callback. On the next drive pass, the reconciler reads
the player's current campaign node first, then considers only the matching
`SERVANT_DEFEATED` subject/archetype. At most four chained transitions may be
attempted in one pass.

Startup, login, and post-transition work scans the complete bounded victory
ledger (at most 4,096 entries), so old receipts cannot hide a newly appended
barrier. The periodic fallback examines 64 entries through a circular
server-local cursor; `SCAN_LIMIT` is retained in Director status and the next
pass continues from the following entry. The cursor cannot starve a finite
ledger and is reset safely on server stop. Startup performs a complete scan
again after a restart.

## Voice compatibility rehearsal

The old `/zapeg-lore voice rehearse` operator command queued an optional
external Discord test-channel clip. That sidecar delivery path is retired; it
is not a hidden dependency of the native campaign. The supported replacement
rehearses the exact in-game, target-private BREACH binding already used by the
automatic Director:

```text
/heraldor voice rehearse <online_player>
/heraldor voice rehearse <online_player> voice_01
/heraldor voice rehearse <online_player> voice_02
/heraldor voice status <online_player>
```

The short form selects `voice_01`. The two literals are a closed allowlist and
resolve the current published Heraldor catalog, including its exact TTL, stage,
and presentation variant. A missing, changed-to-non-BREACH, or otherwise
invalid binding fails closed. The generated event is always marked rehearsal
and carries neither Director proof identity nor timeline replay identity, so
`VISIBLE`, `TIMEOUT`, expiry, and operator cancellation cannot submit a story
fact or consume live campaign proof.

Status retains one bounded JVM-local outcome per target. `RECEIVED` means only
that the client decoded the packet; `visible=1` is reported only after its
accepted `VISIBLE` acknowledgement, and terminal timeout/rejection/cancellation
remain distinguishable. Logout, death, dimension change, and server stop clear
the target's retained result. A repeated command preserves the active
rehearsal, and any unrelated active scene is reported busy rather than replaced.

Privacy smoke: join with the target and a nearby observer, run both variants,
then query status during and after each sequence. Only the named target may hear
or render it. The observer must receive no Voice scene, and rehearsals must
leave the target's Director/story status unchanged.

## Operator diagnostics

The commands are read-only, trusted-root children and accept UUIDs only:

```text
/heraldor director status <target_uuid>
/heraldor director diagnose <target_uuid>
```

They report Director schema health, catalog generation, state, node, typed
fact, event UUID, attempt, proof, retry time, last outcome, latest Servant
reconciliation status (including `scan_limit`), current story node/epoch, and
exact JVM-local scene liveness. They never resolve or dispatch a username.

For a definition mismatch or intentional campaign reset, use the existing
idempotent UUID recovery surface after taking a world backup:

```text
/heraldor story recover <target_uuid> <operation_id> reset
/heraldor story recover <target_uuid> <operation_id> node <node_id>
```
