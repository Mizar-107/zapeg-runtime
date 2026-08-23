# Heraldor timeline contract

Server timelines live at
`data/<namespace>/heraldor_timelines/<path>.json`. The resource path becomes
the timeline id. Reload is atomic: malformed JSON, an unknown field, an
unknown profile, an unsupported format, or any failed bound rejects the whole
prepared registry and leaves the last published generation intact.

Every definition declares:

- `format`: currently `1`.
- `duration_ticks`: hard session bound, 20–12,000 ticks.
- `policies`: explicit `disconnect`, `restart`, `dimension_change`, and
  `death` handling.
- `actions`: 1–64 scene requests. Each has a stable lowercase `id`,
  `at_tick`, `deadline_tick`, `type: "scene"`, and `profile`. Optional fields
  are `ttl_ticks`, `stage`, `retry_interval_ticks`, and `required`.

Actions are canonically ordered by `(at_tick, id)`. A session UUID, target
UUID, definition fingerprint, and action id derive the action event UUID and
visual seed. Player names and server RNG never affect replay. One active
timeline is allowed per target; active work and terminal results are bounded,
and terminal replay barriers never evict.

`APPLIED` in the timeline engine means the server validated, consumed, and
privately dispatched that scene request. It is deliberately not proof that a
client displayed an effect. Client presentation, fallback, failure, and OS
cleanup truth remain in the scene acknowledgement/effect-status contracts.
Timeline `SUCCEEDED` therefore means every required request was dispatched or
recognized as an idempotent replay, not that every visual effect was observed.

Lifecycle behavior is durable:

- paused sessions do not advance while disconnected or across restart;
- dimension changes and death follow the authored policy;
- a changed or missing definition fails the pinned session closed;
- retryable placement/busy failures obey per-action intervals and deadlines;
- unknown saved-data schemas are preserved without mutation;
- no timeline performs entity scans or forces chunk loads.

Operator commands are typed and inherit the native `/heraldor` trust policy:

```text
/heraldor timeline start <player> <session_uuid> <timeline_id>
/heraldor timeline status <player>
/heraldor timeline result <session_uuid>
/heraldor timeline cancel <player>
```

To add `breach_01`, register its `SceneProfile` and client presentation, then
author one or more timeline actions that reference that profile. No timeline
engine or persistence change is required. Additional non-scene action kinds
must add a strictly validated model and a server adapter outcome; they must
not smuggle arbitrary commands through JSON.
