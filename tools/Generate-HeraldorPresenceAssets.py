#!/usr/bin/env python3
"""Original Heraldor presence textures and combat identity audio.

Authored plates and additive FFmpeg synthesis. Reads no Minecraft, Cataclysm,
Aquamirae, recording, stock, or web asset. The committed PNG/OGG bytes are
the release inputs; this script is the auditable authoring step.
"""
from __future__ import annotations

import hashlib
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ENTITY = ROOT / "src/main/resources/assets/zapeg_runtime/textures/entity"
NINTH_AUDIO = ROOT / "src/main/resources/assets/zapeg_runtime/sounds/ninth_form"
HERALDOR_AUDIO = ROOT / "src/main/resources/assets/zapeg_runtime/sounds/heraldor"

REGIONS = [
    ("parent_hull", 0, 0, 480, 180, (34, 48, 52), (18, 28, 31)),
    ("armored_hull_aft", 0, 180, 448, 152, (30, 56, 61), (14, 25, 29)),
    ("prow_lantern", 0, 332, 96, 52, (62, 102, 88), (34, 47, 43)),
    ("port_mooring", 96, 332, 128, 64, (52, 78, 72), (25, 36, 38)),
    ("starboard_mooring", 224, 332, 128, 64, (50, 74, 70), (25, 36, 38)),
    ("keel_heart", 352, 332, 144, 72, (68, 46, 82), (30, 22, 40)),
    ("crown", 0, 400, 128, 112, (70, 86, 64), (28, 35, 31)),
    ("mast_rib", 128, 400, 64, 80, (88, 98, 90), (41, 50, 48)),
    ("port_fin", 192, 404, 144, 108, (32, 54, 62), (17, 29, 34)),
    ("starboard_fin", 336, 404, 144, 108, (31, 53, 61), (17, 29, 34)),
]

NINTH_OGG = [
    (
        "impact.ogg",
        "aevalsrc=0.22*sin(2*PI*(88*t-40*t*t))*exp(-4.2*t)+0.12*sin(2*PI*211*t)*exp(-9*t):s=44100:d=0.88",
        "highpass=f=40,lowpass=f=2400,afade=t=out:st=0.52:d=0.36,volume=0.74,alimiter=limit=0.72",
    ),
    (
        "hurt.ogg",
        "aevalsrc=0.20*sin(2*PI*(143*t))*exp(-8*t)+0.10*sin(2*PI*287*t)*exp(-14*t):s=44100:d=0.62",
        "highpass=f=70,lowpass=f=2800,afade=t=out:st=0.34:d=0.28,volume=0.70,alimiter=limit=0.70",
    ),
    (
        "death.ogg",
        "aevalsrc=0.18*sin(2*PI*(41*t-6*t*t))+0.10*sin(2*PI*(67*t-3*t*t))+0.05*sin(2*PI*109*t)*sin(2*PI*0.6*t):s=44100:d=1.65",
        "highpass=f=26,lowpass=f=1400,afade=t=in:st=0:d=0.10,afade=t=out:st=1.05:d=0.60,volume=0.76,alimiter=limit=0.74",
    ),
    (
        "bed.ogg",
        "aevalsrc=0.10*sin(2*PI*(29*t))+0.07*sin(2*PI*(43.5*t))+0.04*sin(2*PI*61*t)*sin(2*PI*0.25*t):s=44100:d=6.40",
        "highpass=f=20,lowpass=f=900,afade=t=in:st=0:d=0.80,afade=t=out:st=5.40:d=1.00,volume=0.55,alimiter=limit=0.60",
    ),
]

SERVANT_OGG = [
    (
        "servant_ambient.ogg",
        "aevalsrc=0.14*sin(2*PI*(52*t+3*t*t))+0.08*sin(2*PI*97*t)*sin(2*PI*1.1*t):s=44100:d=1.35",
        "highpass=f=40,lowpass=f=1600,afade=t=in:st=0:d=0.12,afade=t=out:st=0.95:d=0.40,volume=0.68,alimiter=limit=0.70",
    ),
    (
        "servant_hurt.ogg",
        "aevalsrc=0.22*sin(2*PI*167*t)*exp(-11*t)+0.10*sin(2*PI*311*t)*exp(-18*t):s=44100:d=0.48",
        "highpass=f=80,lowpass=f=2600,afade=t=out:st=0.26:d=0.22,volume=0.72,alimiter=limit=0.72",
    ),
    (
        "servant_death.ogg",
        "aevalsrc=0.18*sin(2*PI*(63*t-8*t*t))+0.10*sin(2*PI*101*t)*exp(-3*t):s=44100:d=1.10",
        "highpass=f=32,lowpass=f=1800,afade=t=out:st=0.70:d=0.40,volume=0.74,alimiter=limit=0.72",
    ),
    (
        "servant_step.ogg",
        "aevalsrc=0.40*sin(2*PI*(48*t+16*t*t))*exp(-12*t)+0.12*sin(2*PI*221*t)*exp(-24*t):s=44100:d=0.42",
        "highpass=f=30,lowpass=f=1400,afade=t=out:st=0.24:d=0.18,volume=0.64,alimiter=limit=0.70",
    ),
    (
        "servant_telegraph.ogg",
        "aevalsrc=0.16*sin(2*PI*(81*t+36*t*t))+0.08*sin(2*PI*173*t)*sin(2*PI*6*t):s=44100:d=0.82",
        "highpass=f=50,lowpass=f=2100,afade=t=in:st=0:d=0.08,afade=t=out:st=0.55:d=0.27,volume=0.70,alimiter=limit=0.70",
    ),
    (
        "servant_strike.ogg",
        "aevalsrc=0.24*sin(2*PI*(97*t))*exp(-7*t)+0.12*sin(2*PI*241*t)*exp(-15*t):s=44100:d=0.58",
        "highpass=f=45,lowpass=f=2300,afade=t=out:st=0.32:d=0.26,volume=0.76,alimiter=limit=0.74",
    ),
]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def synthesize(target: Path, source: str, filt: str) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    command = [
        "ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
        "-f", "lavfi", "-i", source, "-af", filt,
        "-ar", "44100", "-ac", "1", "-c:a", "libvorbis", "-q:a", "4",
        "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact",
        str(target),
    ]
    subprocess.run(command, check=True)
    print(f"{target.name}\t{target.stat().st_size}\t{sha256(target)}")


def main() -> int:
    print("Committed presence assets are the release inputs.")
    print("This generator is the auditable authoring step; do not point it at vanilla or third-party files.")
    print("Existing PNG/OGG hashes are pinned by resource-contract tests.")
    if "--write-audio" in sys.argv:
        for name, source, filt in NINTH_OGG:
            synthesize(NINTH_AUDIO / name, source, filt)
        for name, source, filt in SERVANT_OGG:
            synthesize(HERALDOR_AUDIO / name, source, filt)
    else:
        print("Pass --write-audio to re-synthesize identity oggs via ffmpeg.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
