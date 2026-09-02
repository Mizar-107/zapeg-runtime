[CmdletBinding()]
param(
    [string] $Ffmpeg = 'ffmpeg'
)

$ErrorActionPreference = 'Stop'
$assetDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\src\main\resources\assets\zapeg_runtime\sounds\ninth_form'))
[IO.Directory]::CreateDirectory($assetDirectory) | Out-Null

# Original deterministic additive synthesis. The generator reads no recorded,
# Minecraft, mod, or third-party audio.
$assets = @(
    @{
        Name = 'awakening.ogg'
        Input = 'aevalsrc=0.20*sin(2*PI*(31*t+5*t*t))+0.12*sin(2*PI*(47*t+2*t*t))+0.05*sin(2*PI*113*t)*sin(2*PI*0.8*t):s=44100:d=1.85'
        Filter = 'highpass=f=24,lowpass=f=1300,afade=t=in:st=0:d=0.20,afade=t=out:st=1.24:d=0.61,volume=0.78,alimiter=limit=0.76'
    }
    @{
        Name = 'telegraph.ogg'
        Input = 'aevalsrc=0.18*sin(2*PI*(72*t+48*t*t))+0.10*sin(2*PI*(119*t+23*t*t))+0.04*sin(2*PI*311*t)*sin(2*PI*7*t):s=44100:d=0.95'
        Filter = 'highpass=f=48,lowpass=f=2200,tremolo=f=5.5:d=0.34,afade=t=in:st=0:d=0.08,afade=t=out:st=0.68:d=0.27,volume=0.72,alimiter=limit=0.72'
    }
    @{
        Name = 'weakpoint_break.ogg'
        Input = 'aevalsrc=0.24*sin(2*PI*(173*t-91*t*t))*exp(-5*t)+0.16*sin(2*PI*347*t)*exp(-12*t)+0.08*sin(2*PI*719*t)*exp(-19*t):s=44100:d=0.72'
        Filter = 'highpass=f=60,lowpass=f=3100,afade=t=out:st=0.38:d=0.34,volume=0.77,alimiter=limit=0.74'
    }
    @{
        Name = 'banish.ogg'
        Input = 'aevalsrc=0.15*sin(2*PI*(54*t-7*t*t))+0.10*sin(2*PI*(83*t-4*t*t))+0.06*sin(2*PI*137*t)*sin(2*PI*0.55*t):s=44100:d=2.20'
        Filter = 'highpass=f=28,lowpass=f=1500,aecho=0.8:0.72:74:0.18,afade=t=in:st=0:d=0.12,afade=t=out:st=1.35:d=0.85,volume=0.74,alimiter=limit=0.72'
    }
    @{
        Name = 'impact.ogg'
        Input = 'aevalsrc=0.22*sin(2*PI*(88*t-40*t*t))*exp(-4.2*t)+0.12*sin(2*PI*211*t)*exp(-9*t):s=44100:d=0.88'
        Filter = 'highpass=f=40,lowpass=f=2400,afade=t=out:st=0.52:d=0.36,volume=0.74,alimiter=limit=0.72'
    }
    @{
        Name = 'hurt.ogg'
        Input = 'aevalsrc=0.20*sin(2*PI*(143*t))*exp(-8*t)+0.10*sin(2*PI*287*t)*exp(-14*t):s=44100:d=0.62'
        Filter = 'highpass=f=70,lowpass=f=2800,afade=t=out:st=0.34:d=0.28,volume=0.70,alimiter=limit=0.70'
    }
    @{
        Name = 'death.ogg'
        Input = 'aevalsrc=0.18*sin(2*PI*(41*t-6*t*t))+0.10*sin(2*PI*(67*t-3*t*t))+0.05*sin(2*PI*109*t)*sin(2*PI*0.6*t):s=44100:d=1.65'
        Filter = 'highpass=f=26,lowpass=f=1400,afade=t=in:st=0:d=0.10,afade=t=out:st=1.05:d=0.60,volume=0.76,alimiter=limit=0.74'
    }
    @{
        Name = 'bed.ogg'
        Input = 'aevalsrc=0.10*sin(2*PI*(29*t))+0.07*sin(2*PI*(43.5*t))+0.04*sin(2*PI*61*t)*sin(2*PI*0.25*t):s=44100:d=6.40'
        Filter = 'highpass=f=20,lowpass=f=900,afade=t=in:st=0:d=0.80,afade=t=out:st=5.40:d=1.00,volume=0.55,alimiter=limit=0.60'
    }
)

foreach ($asset in $assets) {
    $target = Join-Path $assetDirectory $asset.Name
    & $Ffmpeg -nostdin -hide_banner -loglevel error -y `
        -f lavfi -i $asset.Input -af $asset.Filter `
        -ar 44100 -ac 1 -c:a libvorbis -q:a 4 `
        -map_metadata -1 -fflags +bitexact -flags:a +bitexact $target
    if ($LASTEXITCODE -ne 0 -or -not [IO.File]::Exists($target)) {
        throw "Failed to synthesize $($asset.Name)"
    }
}

Get-ChildItem -LiteralPath $assetDirectory -Filter '*.ogg' |
    Sort-Object Name |
    Select-Object Name, Length, @{Name='SHA256'; Expression={(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}}
