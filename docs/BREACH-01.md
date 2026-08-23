# `breach_01` and visitation fallback

This Batch-2 slice changes the exact-match scene channel from protocol 8 to
protocol 9. Descriptor layout and profile wire IDs 0–13 are unchanged;
`breach_01` is the new ID 14, and fallback state wire ID 5 adds the truthful
idle state `available`. Server and clients must therefore ship the same jar.

## Presentation contract

`breach_01` is a target-private screen-space sequence. The server still sends
a validated descriptor, but the client never uses its anchor for presentation.
The default scene has a normalized 180-tick body. `visitation_01` uses a
170-tick body. Neither has a world/fog prelude: both remain complete even at
the command's minimum TTL and run independently of locally enabled OS effects.

The seeded body is deliberately slow and dark:

1. two mod-owned knocks answer one another around the target;
2. a translucent room closes around an impossible central doorway;
3. four mod-owned footsteps circle from 4.8 to 2.64 blocks away;
4. a breath whispers directly behind the target;
5. a dark, texture-free manifestation resolves inside the doorway, then fades;
6. one final step lands before the entire overlay returns to zero.

The renderer uses only bounded `GuiGraphics.fill` primitives. It has no block,
entity, texture, model, world-render, raycast, render-distance, culling, shader
or chunk dependency. Veil opacity is capped at 0.72, eye opacity at 0.68, and
all full-screen transitions are eased over multiple ticks; there is no bright
or rapid flash.

Screens open when a packet arrives retain the existing hold behavior. Cancel,
death, dimension change, level unload and logout clear the same single
transient client scene state. No breach state is persisted.

## Truthful visitation fallback

Opening a visitation diagnostic session records the in-game fallback as
`requested`. It becomes `applied` only when the GUI hook actually emits at
least one non-zero breach frame. Packet receipt, sound scheduling and elapsed
client ticks do not prove presentation. `visitation_01` still rejects generic
`VISIBLE` and `GAZE`; `breach_01` accepts `VISIBLE` from the GUI proof hook but
can never resolve by gaze.

The fallback runs regardless of the OS-effect master/subtoggles and regardless
of platform preflight results. OS capability, primary, fallback and cleanup
remain independent diagnostic axes.

## Original sound resources

`tools/Generate-HeraldorAudio.ps1` creates every source using deterministic
FFmpeg `anoisesrc` or `aevalsrc` synthesis. It reads no recorded, Minecraft or
third-party audio. Re-running the generator is byte-for-byte deterministic
with the pinned encoder invocation.

| asset | duration | SHA-256 |
| --- | ---: | --- |
| `footstep_01.ogg` | 0.48 s | `75e311233d581bacd929ca3b39df3b97bd718491cfa06dfdf4115816801e951e` |
| `footstep_02.ogg` | 0.54 s | `4aa7d1083eafd5ea00a3593984954bc2eecbb257b384bae26b511538298f4d6f` |
| `knock_01.ogg` | 0.55 s | `1d14fbadf2f4e7aa1b45a6bcf2937f2c93207861cf6ee1b2f002b6a9f6497b2d` |
| `knock_02.ogg` | 0.62 s | `dd9c056c58457289608a1ecdcc845fbfdd6117419de6cf805e9c09e3c7f1e768` |
| `manifestation.ogg` | 2.80 s | `32e5d55ec7a7550d73633799241d04fb337fbde7d933cb2edbde86d64337d966` |
| `whisper_01.ogg` | 1.80 s | `f6e27727abc8b5c1d9211b1f0cc2f2b4a388f3cfc2c9efb21efab6fdeea77010` |
| `whisper_02.ogg` | 2.05 s | `2d0c09bc58b33688e6de92c197db1f3cd4af83f78dd03d10757e4bfe5c985d07` |

Seven common-side `SoundEvent`s (two whisper, two knock, two footstep and one
manifestation variant) are registered through a `DeferredRegister`. Code picks
each variant from the scene seed; the sound engine never randomizes the authored
sequence. The OGG resources are mono 44.1 kHz Vorbis. `sounds.json`
gain never exceeds 0.82; the client playback helper independently clamps gain
to 0.85 and pitch to 0.50–1.50. English and Turkish subtitle keys cover all
four events.
