[CmdletBinding()]
param(
    [string] $Ffmpeg = 'ffmpeg'
)

$ErrorActionPreference = 'Stop'
$assetDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\src\main\resources\assets\zapeg_runtime\sounds\heraldor'))
[IO.Directory]::CreateDirectory($assetDirectory) | Out-Null

# Every source below is deterministic procedural synthesis. The generator
# never reads Minecraft, third-party or recorded audio.
$assets = @(
    @{
        Name = 'whisper_01.ogg'
        Input = 'anoisesrc=color=pink:seed=1107:duration=1.80:amplitude=0.32:sample_rate=44100'
        Filter = 'highpass=f=480,lowpass=f=3400,tremolo=f=5.2:d=0.58,afade=t=in:st=0:d=0.24,afade=t=out:st=1.20:d=0.60,volume=0.56,alimiter=limit=0.78'
    }
    @{
        Name = 'whisper_02.ogg'
        Input = 'anoisesrc=color=brown:seed=1907:duration=2.05:amplitude=0.36:sample_rate=44100'
        Filter = 'highpass=f=620,lowpass=f=3900,tremolo=f=4.3:d=0.64,afade=t=in:st=0:d=0.28,afade=t=out:st=1.32:d=0.73,volume=0.64,alimiter=limit=0.78'
    }
    @{
        Name = 'knock_01.ogg'
        Input = 'aevalsrc=0.55*sin(2*PI*74*t)*exp(-13*t)+0.28*sin(2*PI*121*t)*exp(-19*t)+0.12*sin(2*PI*253*t)*exp(-26*t):s=44100:d=0.55'
        Filter = 'highpass=f=38,lowpass=f=1800,afade=t=out:st=0.34:d=0.21,volume=0.82,alimiter=limit=0.82'
    }
    @{
        Name = 'knock_02.ogg'
        Input = 'aevalsrc=0.52*sin(2*PI*63*t)*exp(-11*t)+0.25*sin(2*PI*109*t)*exp(-17*t)+0.10*sin(2*PI*219*t)*exp(-24*t):s=44100:d=0.62'
        Filter = 'highpass=f=34,lowpass=f=1600,afade=t=out:st=0.39:d=0.23,volume=0.84,alimiter=limit=0.82'
    }
    @{
        Name = 'footstep_01.ogg'
        Input = 'aevalsrc=0.48*sin(2*PI*(51*t+18*t*t))*exp(-10*t)+0.20*sin(2*PI*97*t)*exp(-18*t)+0.08*sin(2*PI*337*t)*exp(-29*t):s=44100:d=0.48'
        Filter = 'highpass=f=30,lowpass=f=1500,afade=t=out:st=0.30:d=0.18,volume=0.78,alimiter=limit=0.80'
    }
    @{
        Name = 'footstep_02.ogg'
        Input = 'aevalsrc=0.46*sin(2*PI*(46*t+15*t*t))*exp(-9*t)+0.18*sin(2*PI*83*t)*exp(-16*t)+0.09*sin(2*PI*281*t)*exp(-27*t):s=44100:d=0.54'
        Filter = 'highpass=f=28,lowpass=f=1400,afade=t=out:st=0.34:d=0.20,volume=0.80,alimiter=limit=0.80'
    }
    @{
        Name = 'manifestation.ogg'
        Input = 'aevalsrc=0.16*sin(2*PI*(38*t+8*t*t))+0.12*sin(2*PI*(57*t+5*t*t))+0.07*sin(2*PI*91*t)*sin(2*PI*0.7*t):s=44100:d=2.80'
        Filter = 'highpass=f=30,lowpass=f=1200,afade=t=in:st=0:d=0.38,afade=t=out:st=2.16:d=0.64,volume=0.86,alimiter=limit=0.82'
    }
    @{
        Name = 'servant_ambient.ogg'
        Input = 'aevalsrc=0.14*sin(2*PI*(52*t+3*t*t))+0.08*sin(2*PI*97*t)*sin(2*PI*1.1*t):s=44100:d=1.35'
        Filter = 'highpass=f=40,lowpass=f=1600,afade=t=in:st=0:d=0.12,afade=t=out:st=0.95:d=0.40,volume=0.68,alimiter=limit=0.70'
    }
    @{
        Name = 'servant_hurt.ogg'
        Input = 'aevalsrc=0.22*sin(2*PI*167*t)*exp(-11*t)+0.10*sin(2*PI*311*t)*exp(-18*t):s=44100:d=0.48'
        Filter = 'highpass=f=80,lowpass=f=2600,afade=t=out:st=0.26:d=0.22,volume=0.72,alimiter=limit=0.72'
    }
    @{
        Name = 'servant_death.ogg'
        Input = 'aevalsrc=0.18*sin(2*PI*(63*t-8*t*t))+0.10*sin(2*PI*101*t)*exp(-3*t):s=44100:d=1.10'
        Filter = 'highpass=f=32,lowpass=f=1800,afade=t=out:st=0.70:d=0.40,volume=0.74,alimiter=limit=0.72'
    }
    @{
        Name = 'servant_step.ogg'
        Input = 'aevalsrc=0.40*sin(2*PI*(48*t+16*t*t))*exp(-12*t)+0.12*sin(2*PI*221*t)*exp(-24*t):s=44100:d=0.42'
        Filter = 'highpass=f=30,lowpass=f=1400,afade=t=out:st=0.24:d=0.18,volume=0.64,alimiter=limit=0.70'
    }
    @{
        Name = 'servant_telegraph.ogg'
        Input = 'aevalsrc=0.16*sin(2*PI*(81*t+36*t*t))+0.08*sin(2*PI*173*t)*sin(2*PI*6*t):s=44100:d=0.82'
        Filter = 'highpass=f=50,lowpass=f=2100,afade=t=in:st=0:d=0.08,afade=t=out:st=0.55:d=0.27,volume=0.70,alimiter=limit=0.70'
    }
    @{
        Name = 'servant_strike.ogg'
        Input = 'aevalsrc=0.24*sin(2*PI*(97*t))*exp(-7*t)+0.12*sin(2*PI*241*t)*exp(-15*t):s=44100:d=0.58'
        Filter = 'highpass=f=45,lowpass=f=2300,afade=t=out:st=0.32:d=0.26,volume=0.76,alimiter=limit=0.74'
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
