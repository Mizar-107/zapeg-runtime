$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

$textureDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\src\main\resources\assets\zapeg_runtime\textures\entity'))
[IO.Directory]::CreateDirectory($textureDirectory) | Out-Null

# Canonical authoring is tools/Generate-HeraldorPresenceAssets.py.
# This Windows painter fills the same NinthFormUvLayout rectangles as
# plates, lantern discs, rope wraps, and rune strokes. It is not a
# noise-grain sampler and reads no source image, game texture, or
# third-party asset. Re-running it will not match committed hashes;
# the committed PNG bytes are the release inputs.
$regions = @(
    @{ Name = 'parent_hull'; U = 0; V = 0; Width = 480; Height = 180; Seed = 11; Material = 'timber' }
    @{ Name = 'armored_hull_aft'; U = 0; V = 180; Width = 448; Height = 152; Seed = 23; Material = 'armor' }
    @{ Name = 'prow_lantern'; U = 0; V = 332; Width = 96; Height = 52; Seed = 37; Material = 'lamp' }
    @{ Name = 'port_mooring'; U = 96; V = 332; Width = 128; Height = 64; Seed = 41; Material = 'mooring' }
    @{ Name = 'starboard_mooring'; U = 224; V = 332; Width = 128; Height = 64; Seed = 43; Material = 'mooring' }
    @{ Name = 'keel_heart'; U = 352; V = 332; Width = 144; Height = 72; Seed = 53; Material = 'heart' }
    @{ Name = 'crown'; U = 0; V = 400; Width = 128; Height = 112; Seed = 61; Material = 'crown' }
    @{ Name = 'mast_rib'; U = 128; V = 400; Width = 64; Height = 80; Seed = 67; Material = 'bone' }
    @{ Name = 'port_fin'; U = 192; V = 404; Width = 144; Height = 108; Seed = 71; Material = 'fin' }
    @{ Name = 'starboard_fin'; U = 336; V = 404; Width = 144; Height = 108; Seed = 73; Material = 'fin' }
)

function Color([int] $alpha, [int] $red, [int] $green, [int] $blue) {
    return [System.Drawing.Color]::FromArgb($alpha, $red, $green, $blue)
}

function Base-Color([hashtable] $region, [int] $x, [int] $y) {
    $band = [Math]::Abs((($x - $region.U) / 12) % 2 - 1)
    $plate = [Math]::Abs((($y - $region.V) / 18) % 2 - 1)
    $seam = (($x - $region.U) % 32 -eq 0) -or (($y - $region.V) % 28 -eq 0)
    $stroke = (($x + 2 * $y + $region.Seed) % 47 -eq 0)
    switch ($region.Material) {
        'timber' {
            if ($seam) { return Color 255 18 28 31 }
            return Color 255 (31 + ([int](6 * $band))) (45 + ([int](8 * $plate))) (48 + ([int](7 * $band)))
        }
        'armor' {
            if ($seam) { return Color 255 14 25 29 }
            return Color 255 (27 + ([int](7 * $band))) (52 + ([int](9 * $plate))) (57 + ([int](8 * $plate)))
        }
        'lamp' {
            if ($seam) { return Color 255 34 47 43 }
            return Color 255 (55 + ([int](10 * $band))) (94 + ([int](12 * $plate))) (82 + ([int](11 * $band)))
        }
        'mooring' {
            if ($seam) { return Color 255 25 36 38 }
            return Color 255 (47 + ([int](8 * $plate))) (73 + ([int](9 * $plate))) (68 + ([int](7 * $band)))
        }
        'heart' {
            if ($seam) { return Color 255 30 22 40 }
            return Color 255 (61 + ([int](10 * $band))) (43 + ([int](6 * $plate))) (75 + ([int](8 * $band)))
        }
        'crown' {
            if ($seam) { return Color 255 28 35 31 }
            return Color 255 (63 + ([int](9 * $plate))) (78 + ([int](9 * $plate))) (59 + ([int](10 * $band)))
        }
        'bone' {
            if ($seam) { return Color 255 41 50 48 }
            return Color 255 (83 + ([int](8 * $band))) (96 + ([int](9 * $plate))) (88 + ([int](10 * $band)))
        }
        'fin' {
            if ($seam) { return Color 255 17 29 34 }
            return Color 255 (29 + ([int](7 * $band))) (51 + ([int](11 * $band))) (58 + ([int](12 * $plate)))
        }
        default { throw "Unknown Ninth Form material $($region.Material)" }
    }
}

function Emissive-Color([hashtable] $region, [int] $x, [int] $y) {
    if ($region.Material -eq 'armor' -or $region.Material -eq 'bone') {
        return Color 0 0 0 0
    }
    $localX = $x - $region.U
    $localY = $y - $region.V
    $rune = (($localX + 2 * $localY + $region.Seed) % 29 -eq 0) -or
        (($localX * 3 - $localY + $region.Seed) % 37 -eq 0)
    $core = $region.Material -in @('lamp', 'mooring', 'heart') -and
        (([Math]::Abs($localX - [Math]::Floor($region.Width / 2)) -le 1) -or
        ([Math]::Abs($localY - [Math]::Floor($region.Height / 2)) -le 1))
    if (-not $rune -and -not $core) {
        return Color 0 0 0 0
    }
    $flicker = ($x * 13 + $y * 17 + $region.Seed) % 36
    if ($region.Material -eq 'heart') {
        return Color (168 + $flicker) (118 + ($flicker % 22)) 77 (174 + ($flicker % 31))
    }
    return Color (154 + $flicker) (69 + ($flicker % 24)) (184 + ($flicker % 38)) (171 + ($flicker % 44))
}

function New-Atlas([bool] $emissive) {
    $bitmap = [System.Drawing.Bitmap]::new(512, 512, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $transparent = Color 0 0 0 0
    for ($y = 0; $y -lt 512; $y++) {
        for ($x = 0; $x -lt 512; $x++) {
            $bitmap.SetPixel($x, $y, $transparent)
        }
    }
    foreach ($region in $regions) {
        for ($y = $region.V; $y -lt $region.V + $region.Height; $y++) {
            for ($x = $region.U; $x -lt $region.U + $region.Width; $x++) {
                $pixel = if ($emissive) {
                    Emissive-Color $region $x $y
                } else {
                    Base-Color $region $x $y
                }
                $bitmap.SetPixel($x, $y, $pixel)
            }
        }
    }
    return $bitmap
}

$outputs = @(
    @{ Name = 'ninth_form.png'; Emissive = $false }
    @{ Name = 'ninth_form_emissive.png'; Emissive = $true }
)

foreach ($output in $outputs) {
    $bitmap = New-Atlas $output.Emissive
    try {
        $path = Join-Path $textureDirectory $output.Name
        $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $bitmap.Dispose()
    }
}

Get-ChildItem -LiteralPath $textureDirectory -Filter 'ninth_form*.png' |
    Sort-Object Name |
    Select-Object Name, Length, @{Name='SHA256'; Expression={(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}}
