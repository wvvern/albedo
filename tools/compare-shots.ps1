<#
.SYNOPSIS
Compares the screenshots of two folders. Prints the difference of each pair of PNG files with the same relative path.

.DESCRIPTION
Each line has four values and the path:
  mean     the mean difference of the red, green and blue values of all pixels (0 to 255)
  percent  the percent of the pixels where a color value differs by more than -Step
  max      the largest difference of a color value
A file that is in only one folder, or a pair with different sizes, gets a line that starts with "!".

.EXAMPLE
./tools/compare-shots.ps1 -Before build\baseline\r1 -After build\baseline\r2
#>
param(
    [Parameter(Mandatory = $true)][string]$Before,
    [Parameter(Mandatory = $true)][string]$After,
    # A pixel counts in "percent" when a color value differs by more than this.
    [int]$Step = 16
)
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing
# The loop over the pixels is C#, because it is too slow in PowerShell.
Add-Type @"
using System;
public static class ShotCompare {
    // Returns the mean, the percent and the max of two BGRA pixel arrays of the same size.
    public static double[] Difference(byte[] a, byte[] b, int step) {
        long sum = 0; long over = 0; int max = 0;
        for (int i = 0; i < a.Length; i += 4) {
            int pixelMax = 0;
            for (int c = 0; c < 3; c++) {
                int d = Math.Abs(a[i + c] - b[i + c]);
                sum += d;
                if (d > pixelMax) pixelMax = d;
            }
            if (pixelMax > step) over++;
            if (pixelMax > max) max = pixelMax;
        }
        double pixels = a.Length / 4.0;
        return new double[] { sum / (pixels * 3), 100.0 * over / pixels, max };
    }
}
"@

function Get-Pixels([string]$file) {
    $image = New-Object System.Drawing.Bitmap($file)
    try {
        $area = New-Object System.Drawing.Rectangle(0, 0, $image.Width, $image.Height)
        $data = $image.LockBits($area, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $bytes = New-Object byte[] ($data.Stride * $image.Height)
        [Runtime.InteropServices.Marshal]::Copy($data.Scan0, $bytes, 0, $bytes.Length)
        $image.UnlockBits($data)
        return @{ Size = "$($image.Width)x$($image.Height)"; Bytes = $bytes }
    } finally { $image.Dispose() }
}

$Before =(Resolve-Path $Before).Path
$After = (Resolve-Path $After).Path
$names = @(Get-ChildItem $Before, $After -Recurse -Filter *.png | ForEach-Object {
    $base = if ($_.FullName.StartsWith($Before + "\")) { $Before } else { $After }
    $_.FullName.Substring($base.Length + 1)
} | Sort-Object -Unique)
"mean`tpercent`tmax`tfile"
foreach ($name in $names) {
    $a = Join-Path $Before $name
    $b = Join-Path $After $name
    if (-not (Test-Path -LiteralPath $a)) { "! only in After: $name"; continue }
    if (-not (Test-Path -LiteralPath $b)) { "! only in Before: $name"; continue }
    $pa = Get-Pixels $a
    $pb = Get-Pixels $b
    if ($pa.Size -ne $pb.Size) { "! different sizes: $name"; continue }
    $d = [ShotCompare]::Difference($pa.Bytes, $pb.Bytes, $Step)
    "{0:F3}`t{1:F3}`t{2:F0}`t{3}" -f $d[0], $d[1], $d[2], $name
}
