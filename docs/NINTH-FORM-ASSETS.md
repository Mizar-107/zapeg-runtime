# Ninth Form asset provenance

All Ninth Form visual and audio assets are original procedural work generated
inside this repository. No Minecraft texture, Cataclysm/Aquamirae asset,
recording, stock library, web download, or model export is an input.

## Exact UV atlases

`tools/Generate-HeraldorPresenceAssets.py` authors both 512 x 512 RGBA atlases
as painted plates, lantern discs, rope wraps, and rune strokes. It fills only
the ten non-overlapping cuboid unwraps in `NinthFormUvLayout`. The older
`tools/Generate-NinthFormTextures.ps1` remains as a Windows-auditable painter
for the same rectangles; it is not a noise-grain sampler and reads no source
image.

The emissive atlas is transparent outside sparse authored strokes. Runtime
alpha is separately capped at 0.68 and the armored aft hull is never included
in the emissive pass.

| Asset | Bytes | SHA-256 |
| --- | ---: | --- |
| `ninth_form.png` | 18,563 | `23ef98a86ef8b0fe6c9efa560506e14b66c66e1e48327288bf3b46f37c31dfa2` |
| `ninth_form_emissive.png` | 5,964 | `6d9eee04073040cb903bde1712b2e17fd0ae699e9d8d5e1635e980412f549c8b` |

## Procedural audio

`tools/Generate-NinthFormAudio.ps1` (and the presence generator) supplies
deterministic additive equations to FFmpeg's `aevalsrc`, then applies bounded
filters and encodes mono 44.1 kHz Vorbis with metadata removed and bit-exact
flags. No sampled or recorded source enters the pipeline.

| Asset | Duration | Bytes | SHA-256 |
| --- | ---: | ---: | --- |
| `awakening.ogg` | 1.85 s | 6,290 | `a12f1687e6d9b5998c109223f2ca5151d31704c4d637b9180ac303a9a28192a6` |
| `telegraph.ogg` | 0.95 s | 5,092 | `37904e6a7ae092afee17349885a3efae8c4d150055f924f012ec2786285a5481` |
| `weakpoint_break.ogg` | 0.72 s | 4,995 | `075d1d1ccec6b614cc8046ad70f75db4f9dafc93389670dd076ed7c653e4d20c` |
| `banish.ogg` | 2.274 s | 6,766 | `873bed1b9bfd6195f1fbb68478c4cfc32b9ddd80c79f4aef93a446084595ec87` |
| `impact.ogg` | 0.88 s | 5,002 | `1d901f385d89b22bc28f7010cc22f9cebab90d3effd04d84863f30ea945164df` |
| `hurt.ogg` | 0.62 s | 4,678 | `3add0ae3e9c60475af2e482c6f8a453c7f8801b1eba674b52bd7686e948022b5` |
| `death.ogg` | 1.65 s | 6,048 | `aa93420b0ba0a561366c3f6f27ece930ac2114ab2b14027516f9ac3d913ce57a` |
| `bed.ogg` | 6.40 s | 12,723 | `c1501e818337c7527bba3f8c70ce9d55ed8650d9061c4745fe66e2fe2397c12d` |

The committed binary assets are the release inputs. Regeneration is an
auditable authoring step; resource-contract tests pin their byte lengths,
hashes, dimensions, Ogg framing, sample rate, duration, and uniqueness.
