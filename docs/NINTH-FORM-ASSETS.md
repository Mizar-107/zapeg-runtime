# Ninth Form asset provenance

All Ninth Form visual and audio assets are original procedural work generated
inside this repository. No Minecraft texture, Cataclysm/Aquamirae asset,
recording, stock library, web download, or model export is an input.

## Exact UV atlases

`tools/Generate-NinthFormTextures.ps1` creates both 512 x 512 RGBA atlases
directly with `System.Drawing`. Its ten rectangles exactly mirror the vanilla
cuboid unwraps in `NinthFormUvLayout`: the parent hull, five native hit parts,
and four parent ornaments. The generator starts with a transparent bitmap,
fills only those non-overlapping regions, and derives every grain, seam, rune,
and emissive crack from integer coordinates plus a fixed per-region seed.

The emissive atlas is transparent outside sparse procedural cracks. Runtime
alpha is separately capped at 0.68 and the armored aft hull is never included
in the emissive pass.

| Asset | Bytes | SHA-256 |
| --- | ---: | --- |
| `ninth_form.png` | 60,198 | `b0aa099c94fc8284a59123ae435411bca72087f770ddbb07aeb1858f3e02d1ab` |
| `ninth_form_emissive.png` | 30,700 | `5a33cf674168b7e5591ba005739c3403b4336dfb5284707f76fd731125e00cf3` |

## Procedural audio

`tools/Generate-NinthFormAudio.ps1` supplies deterministic additive equations
to FFmpeg's `aevalsrc`, then applies bounded filters and encodes mono 44.1 kHz
Vorbis with metadata removed and bit-exact flags. No sampled or recorded source
enters the pipeline.

| Asset | Duration | Bytes | SHA-256 |
| --- | ---: | ---: | --- |
| `awakening.ogg` | 1.85 s | 6,290 | `a12f1687e6d9b5998c109223f2ca5151d31704c4d637b9180ac303a9a28192a6` |
| `telegraph.ogg` | 0.95 s | 5,092 | `37904e6a7ae092afee17349885a3efae8c4d150055f924f012ec2786285a5481` |
| `weakpoint_break.ogg` | 0.72 s | 4,995 | `075d1d1ccec6b614cc8046ad70f75db4f9dafc93389670dd076ed7c653e4d20c` |
| `banish.ogg` | 2.274 s | 6,766 | `873bed1b9bfd6195f1fbb68478c4cfc32b9ddd80c79f4aef93a446084595ec87` |

The committed binary assets are the release inputs. Regeneration is an
auditable authoring step; resource-contract tests pin their byte lengths,
hashes, dimensions, Ogg framing, sample rate, duration, and uniqueness.
