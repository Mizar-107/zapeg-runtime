$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

$resourceRoot = Join-Path $PSScriptRoot '..\src\main\resources\assets\zapeg_runtime\textures\item'
$outputPath = Join-Path $resourceRoot 'heraldor_journal.png'
New-Item -ItemType Directory -Path $resourceRoot -Force | Out-Null

$bitmap = [System.Drawing.Bitmap]::new(16, 16, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
try {
    $transparent = [System.Drawing.Color]::FromArgb(0, 0, 0, 0)
    $shadow = [System.Drawing.Color]::FromArgb(255, 37, 18, 31)
    $cover = [System.Drawing.Color]::FromArgb(255, 64, 31, 49)
    $edge = [System.Drawing.Color]::FromArgb(255, 111, 55, 63)
    $paper = [System.Drawing.Color]::FromArgb(255, 202, 174, 126)
    $gold = [System.Drawing.Color]::FromArgb(255, 190, 143, 65)
    $ink = [System.Drawing.Color]::FromArgb(255, 32, 16, 21)

    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $bitmap.SetPixel($x, $y, $transparent)
        }
    }
    for ($y = 2; $y -le 14; $y++) {
        for ($x = 3; $x -le 13; $x++) {
            $bitmap.SetPixel($x, $y, $shadow)
        }
    }
    for ($y = 1; $y -le 13; $y++) {
        for ($x = 2; $x -le 12; $x++) {
            $bitmap.SetPixel($x, $y, $cover)
        }
    }
    for ($y = 1; $y -le 13; $y++) {
        $bitmap.SetPixel(2, $y, $edge)
        $bitmap.SetPixel(4, $y, $edge)
    }
    for ($x = 5; $x -le 11; $x++) {
        $bitmap.SetPixel($x, 2, $paper)
        $bitmap.SetPixel($x, 13, $paper)
    }
    $eyePixels = @(
        @(6, 6), @(7, 5), @(8, 5), @(9, 5), @(10, 6),
        @(7, 7), @(8, 7), @(9, 7), @(8, 8)
    )
    foreach ($pixel in $eyePixels) {
        $bitmap.SetPixel($pixel[0], $pixel[1], $gold)
    }
    $bitmap.SetPixel(8, 6, $ink)
    $bitmap.SetPixel(5, 10, $gold)
    $bitmap.SetPixel(7, 10, $gold)
    $bitmap.SetPixel(9, 10, $gold)
    $bitmap.SetPixel(11, 10, $gold)
    $bitmap.SetPixel(8, 11, $gold)

    $bitmap.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $bitmap.Dispose()
}

Write-Output "Generated $outputPath"
