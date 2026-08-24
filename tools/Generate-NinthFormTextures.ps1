$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

$textureDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\src\main\resources\assets\zapeg_runtime\textures\entity'))
[IO.Directory]::CreateDirectory($textureDirectory) | Out-Null

# These rectangles are the exact vanilla cuboid unwraps declared by
# NinthFormUvLayout. No source image, game texture, or third-party asset is read.
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
    $grain = ($x * 37 + $y * 71 + $region.Seed * 101) % 19
    $seam = (($x - $region.U) % 16 -eq 0) -or (($y - $region.V) % 24 -eq 0)
    switch ($region.Material) {
        'timber' {
            if ($seam) { return Color 255 18 28 31 }
            return Color 255 (31 + ($grain % 8)) (45 + ($grain % 11)) (48 + ($grain % 9))
        }
        'armor' {
            if ($seam) { return Color 255 14 25 29 }
            return Color 255 (27 + ($grain % 9)) (52 + ($grain % 13)) (57 + ($grain % 11))
        }
        'lamp' {
            if ($seam) { return Color 255 34 47 43 }
            return Color 255 (55 + ($grain % 12)) (94 + ($grain % 17)) (82 + ($grain % 15))
        }
        'mooring' {
            if ($seam) { return Color 255 25 36 38 }
            return Color 255 (47 + ($grain % 11)) (73 + ($grain % 13)) (68 + ($grain % 9))
        }
        'heart' {
            if ($seam) { return Color 255 30 22 40 }
            return Color 255 (61 + ($grain % 12)) (43 + ($grain % 10)) (75 + ($grain % 14))
        }
        'crown' {
            if ($seam) { return Color 255 28 35 31 }
            return Color 255 (63 + ($grain % 13)) (78 + ($grain % 16)) (59 + ($grain % 12))
        }
        'bone' {
            if ($seam) { return Color 255 41 50 48 }
            return Color 255 (83 + ($grain % 14)) (96 + ($grain % 13)) (88 + ($grain % 12))
        }
        'fin' {
            if ($seam) { return Color 255 17 29 34 }
            return Color 255 (29 + ($grain % 9)) (51 + ($grain % 15)) (58 + ($grain % 17))
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
