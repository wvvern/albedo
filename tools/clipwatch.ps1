<#
.SYNOPSIS
Logs the cursor clip rectangle and the virtual screen size of the window station, once for each interval, until you stop it.
It only reads the clip (GetClipCursor). It never moves the cursor and never calls ClipCursor.

.EXAMPLE
./tools/clipwatch.ps1 -Log build\clip\clip.log
#>
param(
    [Parameter(Mandatory = $true)][string]$Log,
    [double]$IntervalSeconds = 1,
    # Stops after this many seconds; 0 runs until the process is stopped.
    [int]$Seconds = 0
)
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class ClipProbe {
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")] public static extern bool GetClipCursor(out RECT rect);
    [DllImport("user32.dll")] public static extern int GetSystemMetrics(int index);
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
}
"@
[void][ClipProbe]::SetProcessDPIAware()
New-Item -ItemType Directory -Force (Split-Path -Parent $Log) | Out-Null
$start = Get-Date
while ($true) {
    $rect = New-Object ClipProbe+RECT
    $ok = [ClipProbe]::GetClipCursor([ref]$rect)
    $left = [ClipProbe]::GetSystemMetrics(76); $top = [ClipProbe]::GetSystemMetrics(77)
    $width = [ClipProbe]::GetSystemMetrics(78); $height = [ClipProbe]::GetSystemMetrics(79)
    $clipWidth = $rect.Right - $rect.Left; $clipHeight = $rect.Bottom - $rect.Top
    $state = if (-not $ok) { "UNREADABLE" } elseif ($clipWidth -lt $width -or $clipHeight -lt $height) { "CLIPPED" } else { "free" }
    # The harness games that run now, by the target folder of their -Dalbedo.harness, to tell whose clip it is.
    $games = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | ForEach-Object { if ($_.CommandLine -match "-Dalbedo\.harness=\S*\\([^\\ ]+)") { $Matches[1] } })
    "{0} clip=({1},{2},{3},{4}) {5}x{6} virtual=({7},{8}) {9}x{10} {11} games=[{12}]" -f (Get-Date -Format "HH:mm:ss"), $rect.Left, $rect.Top, $rect.Right,
        $rect.Bottom, $clipWidth, $clipHeight, $left, $top, $width, $height, $state, ($games -join ",") | Add-Content -Encoding ascii $Log
    if ($Seconds -gt 0 -and ((Get-Date) - $start).TotalSeconds -ge $Seconds) { break }
    Start-Sleep -Milliseconds ([int]($IntervalSeconds * 1000))
}
