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
UUID, definition fingerprint, and action id derive the action event UUID.
Visual and placement seeds use separate deterministic domains. Player names,
player RNG, and server RNG never affect timeline replay or placement. One
active timeline is allowed per target; active work and terminal results are
bounded, and terminal replay barriers never evict.

Every timeline scene claim is also stored with its session, target,
definition fingerprint, action id, and payload hash. Direct scene claims use
the same bounded, non-evicting event-id namespace, so an old direct UUID still
cannot impersonate a timeline action after the legacy 256-entry UUID cache
turns over. Exact reserved claims resume after restart; a reserved claim whose
legacy UUID was already consumed is promoted to applied without dispatching a
second scene. Identity or payload collisions fail closed.

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
- unknown saved-data schemas and structurally corrupt schema-1 roots are
  distinguished, quarantined, and preserved without mutation;
- definition-relative seed, duration, cursor, and retry corruption becomes a
  durable `FAILED/STATE_CORRUPTION` result before lifecycle or dispatch;
- no timeline performs entity scans or forces chunk loads; every ray and
  collision footprint is bounded and preflighted against loaded chunks.

Operator commands are typed and inherit the native `/heraldor` trust policy:

```text
/heraldor timeline start <player> <session_uuid> <timeline_id>
/heraldor timeline status <player>
/heraldor timeline result <session_uuid>
/heraldor timeline cancel <player>
```

The bundled `breach_01` definition dispatches the registered screen-space
profile as one deterministic scene action; its authored sound choreography is
part of that client profile, not a second timeline side effect. Additional
non-scene action kinds must add a strictly validated model and a server adapter
outcome; they must not smuggle arbitrary commands through JSON.
