# Heraldor story foundation

This slice owns only the server-authoritative hidden-journal campaign state. It
does not implement the journal screen, servant archetypes, a Director, or the
boss. Those systems submit evidence through `StoryService`; they do not mutate
story NBT directly. Durable barrier reconcilers should use
`StoryService.submitIfExpected`, which leaves out-of-order barriers unconsumed,
recognizes already processed fact UUIDs, and constructs the epoch-bound fact on
the server thread. Its top-level result distinguishes applied, already applied,
not expected, state unavailable, capacity exhausted, and fact-ID conflict, so a
reconciler cannot accidentally treat a refused transition as success.

## Campaign contract

The required datapack resource is
`data/zapeg_runtime/heraldor_story/heraldor.json`. Schema 1 requires exactly 30
ordered nodes. Ordinals are contiguous, only ordinal 29 is terminal, every
other node has exactly one typed predicate and points to the immediately next
ordinal, and transition predicates are globally unique. Unknown fields,
duplicate JSON keys, malformed syntax, missing campaigns, fractional integers,
and invalid resource identifiers reject the entire reload while retaining the
previous registry.

Each definition is canonicalized and SHA-256 fingerprinted. Player state binds
to the campaign resource ID, revision, and fingerprint. A changed datapack
therefore pauses that player's progress until an operator explicitly rebinds
it; it can never silently reinterpret completed nodes.

## Fact and replay contract

A story fact contains a fact UUID, player UUID, campaign ID and revision,
progress epoch, expected node ID, closed `StoryFactType`, and resource-location
subject. The engine records every relevant submission before evaluating it. A
stale-node fact is also recorded, so replaying it after progress changes cannot
make it valid. Each receipt stores a canonical SHA-256 identity over the fact
UUID, target UUID, definition, epoch, expected node, type, and subject; UUID
reuse with another target or payload is a conflict, not a certified replay.
Applied state and its receipt live in one `SavedData` mutation.

Persistence is bounded to 2,048 player entries, 256 fact receipts per player,
64 recovery receipts per player, 30 completed-node slots, and eight campaign
resources. Capacity exhaustion fails closed and is visible in operator status;
it requires a backed-up offline repair rather than unsafe receipt eviction. The
story package performs no block, entity, level-position, or chunk query.

## Save schema and recovery

Current saved-data schema is 2. An empty unversioned root and strict schema 1
data migrate to schema 2. Unknown versions and malformed current data are kept
losslessly and made read-only; an in-game command never overwrites that root.
Structurally valid but stale or inconsistent player entries can be repaired by
an explicit, idempotent operation UUID:

```text
/heraldor story status <target_uuid>
/heraldor story recover <target_uuid> <operation_id> reset
/heraldor story recover <target_uuid> <operation_id> node <node_id>
```

Recovery increments the persisted progress epoch, rebuilds the completed
prefix, and retains both ordinary fact receipts and the bounded recovery receipt
set. Already processed durable barriers therefore remain duplicates, while
queued facts carrying the prior epoch become permanently stale. Replaying a
recovery operation after later progress is likewise a no-op instead of an
accidental second reset.
