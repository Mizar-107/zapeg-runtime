# Heraldor quest actions

Batch 3D gives the campaign's seven `world_discovery` and five
`ritual_completed` predicates playable vanilla mechanics. The story registry
remains the only authority: an action is observed only while its exact typed
trigger is the acting player's current node, and completion is submitted through
`StoryService.submitIfExpected`.

## Discovery clues and mechanics

| Subject | Journal clue contract | Exact server mechanic |
| --- | --- | --- |
| `ashen_scratch` | “Brush the cold ash after dusk.” | After tick 13000 and before 23000, sneak-use a Brush in the main hand on an extinguished regular Campfire. The brush and campfire are unchanged. An absent writable player record is intentionally treated as campaign entry, epoch 0. |
| `backward_tracks` | “Let rain write six blocks of steps you refuse to face.” | During active rain, continuously move backward relative to the view vector for at least 40 ticks and 6 horizontal blocks while standing on mud, coarse dirt, or dirt path. A forward/sideways step, dry sky, invalid footing, teleport-sized step, node change, dimension change, death, or logout resets the streak. |
| `ninth_bell` | “After dark, give one bell nine deliberate voices before half a minute closes.” | At night, successfully ring the same Bell nine times inside 600 ticks. Only an accepted server `BellBlock.onHit` with a real loaded `BellBlockEntity` counts. Main-hand rings must be at least 6 ticks apart. A different bell, dimension, expired window, node change, death, or logout resets the sequence. |
| `drowned_road` | “Keep your eyes beneath the water and follow eight blocks of gravel.” | Keep the eyes submerged for at least 60 ticks while travelling at least 8 horizontal blocks over a continuous gravel bed. Surfacing, leaving gravel, a node/dimension change, death, or logout resets the streak. |
| `leaning_house` | “At night, lend your weight to a shut wooden door and hold three seconds without wandering.” | At night, sneak in actual physical contact with a closed wooden door for 60 ticks. A narrow 0.04-block horizontal probe must intersect that loaded door's collision shape; collision with a wall while a door is merely nearby does not count. Each step must be at most 0.035 blocks, cumulative drift at most 0.50 blocks, and net drift at most 0.25 blocks. Opening/leaving the door, day, movement, node/dimension change, death, or logout resets it. |
| `underdoor` | “Below the zero line, let a little door press you flat; pass three blocks beneath it without water.” | Below Y=0, begin in the vanilla dry crawl (`Pose.SWIMMING`) beneath a closed Trapdoor, then remain dry-crawling until net horizontal displacement reaches 3 blocks. Water, standing up, teleport-sized movement, node/dimension change, death, or logout resets it. |
| `ninth_witness` | “Set eight empty witnesses around you. At night, take the ninth place behind an eye of glass.” | At night, continuously use a Spyglass for 40 ticks while exactly eight living Armor Stands are within a 5-block radius. The query is a small loaded-entity AABB filtered to the true radius. Releasing the spyglass, changing the count, day, node/dimension change, death, or logout resets it. |

## Ritual clues, altars, and offerings

Every ritual requires sneaking and the offering in the server main hand. The
altar interaction is checked first, the deterministic typed fact is committed
second, and the exact offering count is shrunk only when the result is
`APPLIED`. `NOT_EXPECTED`, replay, conflict, unavailable data, and every other
failure leave the stack untouched.

| Subject | Journal clue contract | Altar and offering |
| --- | --- | --- |
| `name_refusal` | “Give the blue fire a name that has never been written.” | Sneak-use 1 **unrenamed Name Tag** on a **lit Soul Campfire**. |
| `binder_knot` | “Tie what leads to the line only after the line pulls taut.” | Sneak-use 1 **Lead** on an **attached Tripwire Hook** whose string line is taut. |
| `seal_01` | “Crown carved deepstone with red flame; feed it three embers.” | A **Chiseled Deepslate** block crowned directly by a **lit red Candle**; offer 3 **Blaze Powder**. |
| `seal_02` | “Crown the same cut stone with blue flame; feed it three drowned lights.” | Chiseled Deepslate crowned directly by a **lit blue Candle**; offer 3 **Prismarine Crystals**. |
| `seal_03` | “Crown the cut stone with black flame; give it one echo.” | Chiseled Deepslate crowned directly by a **lit black Candle**; offer 1 **Echo Shard**. |

## Identity, lifecycle, and safety

- Fact UUIDs are deterministic name UUIDs over a version tag, campaign ID,
  acting player UUID, recovery epoch, closed fact type, and exact subject. A
  retry in one epoch is idempotent; operator recovery deliberately produces a
  new replay key.
- No display name, username, command string, sidecar, item entity, global
  scoreboard, or operator action participates in credit.
- At most 2,048 online player sessions exist in memory. Sessions begin only
  after resolving an owned trigger at the exact current story node. They are
  pruned against the online UUID set each server tick and explicitly removed on
  logout, dimension change, clone/death, node change, and server stop. Nothing
  is persisted outside the bounded story ledger.
- Block reads are limited to the acting player's footing/head/adjacent door or
  the already-interacted block. Every derived block position passes
  `ServerLevel.hasChunkAt` before `getBlockState`. The armor-stand query reads
  only loaded entity sections in a 5-block AABB. There is no chunk fetch,
  ticket, structure search, or cross-dimension scan.
- Completion prose and chime use `ServerPlayer.sendSystemMessage` and
  `playNotifySound`, so quest feedback targets only the acting UUID. The bell's
  normal world sound is the player's accepted vanilla bell interaction, not a
  completion broadcast.
- Discoveries do not edit the world or consume items. Ritual stacks are
  consumed only after the story transition commits; the event is then claimed
  to prevent a second block-use path.
