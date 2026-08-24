# Heraldor Hidden Journal

Batch 3C adds a native journal item and screen to the `zapeg_runtime` Forge
mod. It does not depend on a sidecar, Citizen, username commands, or item NBT
for progression.

## Player experience

- The server binds one unstackable Hidden Journal to the player's UUID when
  the Heraldor campaign is available. A player without story state receives a
  read-only pristine view of entry zero, so the first clue is never deadlocked
  behind its own discovery.
- The screen uses two responsive pages, five chapter tabs, mouse controls,
  narrated widgets, and Left/Right, Page Up/Page Down, Home/End, and number
  keys 1–5. It remains readable at ordinary ATM9 GUI scales.
- Thirty original entries ship in both `en_us` and `tr_tr`. Completed entries
  remain readable; only the current entry shows an actionable clue. Locked
  entries cannot be selected.
- Entry 4 reveals the palimpsest by holding its page to the light. Entry 18
  resolves the absence ledger by counting its missing lines. Both are visible
  page interactions rather than operator commands.

## Server authority and privacy

The server sends a fixed five-byte view: a 30-bit authorized prefix and the
current ordinal. It does not serialize player UUIDs, node IDs, localization
keys, fact receipts, recovery state, future-node keys, or arbitrary text. The
client-to-server action is one byte from a two-value closed enum. Both packet
handlers are registered on the main thread. Protocol `10` rejects mixed jars
at login.

For an interaction, the server derives the sender UUID from the connection and
checks all of the following before submitting a fact:

1. the active journal token is in that UUID's inventory;
2. the story SavedData root is writable and matches the active campaign;
3. the current node, ordinal, fact type, and subject match the closed action;
4. the deterministic fact UUID is derived from player UUID, campaign, recovery
   epoch, and subject.

Retries therefore address the same durable receipt. A packet arriving after
the first action advanced the node is accepted only when the receipt matches
the same sender and payload exactly, and is then a certified no-op. No
client-provided target, progress value, node name, subject, or text is trusted.

## Issuance, loss, and recovery

The binding ledger is bounded to 2,048 UUIDs and stores one globally unique,
non-NIL active token per player. Unknown schemas, malformed entries, duplicate
players, shared tokens, and NIL UUIDs are preserved read-only and fail closed.
The item stores only binding schema, owner UUID, and token; it stores no story
progress.

Automatic issuance checks at login and once every 100 player ticks. If the
main inventory is full, the journal stays pending and nothing is dropped into
the world. Once an active journal has been issued, its absence does not cause
automatic duplication. An operator can restore a missing copy for an online
player with the UUID-only command:

```text
/heraldor journal restore <target_uuid>
```

Restore refuses a full inventory. A successful restore rotates the active
token, making a lost or copied older journal inert. If the active copy is
already present, restore is an idempotent no-op.

## Current quest clues

The journal clue text reflects the native quest mechanics: sunset Brush use on
an extinguished Campfire; backward rain travel; nine Bell rings; submerged
Gravel travel; the closed-door vigil; dry crawling below Y=0; the eight Armor
Stand Spyglass witness; and the five crouched main-hand ritual offerings. Item
consumption remains the quest implementation's responsibility and occurs only
after an applied story transition.
