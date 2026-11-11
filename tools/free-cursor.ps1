<#
.SYNOPSIS
Safety net of tools/run-hidden.ps1: waits until a hidden game ends, then frees the cursor clip that it can leave behind.

.DESCRIPTION
A game that grabs the mouse clips the cursor of the whole window station, so a game that is killed can leave the cursor of the
player in a small region. After the process -WaitPid ends, this script calls ClipCursor(null) only when the clip is smaller than
the virtual screen, no other harness game runs and no game of the player runs. It never frees a clip while a game runs.
#>
param(
    [Parameter(Mandatory = $true)][int]$WaitPid,
    # The harness folder of the game, to wait for its java process, which can outlive the cmd.exe that started it.
    [string]$Harness = "",
    # A file for the line that tells when the script freed the clip.
    [string]$Log = ""
)
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class ClipFree {
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")] public static extern bool GetClipCursor(out RECT rect);
    [DllImport("user32.dll")] public static extern bool ClipCursor(IntPtr rect);
    [DllImport("user32.dll")] public static extern int GetSystemMetrics(int index);
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
}
"@
[void][ClipFree]::SetProcessDPIAware()

function Get-JavaLines {
    @(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | ForEach-Object { $_.CommandLine })
}

# The process of the run ends first, and the game can still be shutting down for a few seconds.
Wait-Process -Id $WaitPid -ErrorAction SilentlyContinue
if ($Harness) {
    $own = "-Dalbedo.harness=" + $Harness
    for ($i = 0; $i -lt 30 -and @(Get-JavaLines | Where-Object { $_ -and $_.Contains($own) }).Count -gt 0; $i++) { Start-Sleep -Seconds 1 }
}
Start-Sleep -Seconds 2

$lines = Get-JavaLines
# Another harness game can clip on purpose of its own test, and a game of the player needs its own grab.
if (@($lines | Where-Object { $_ -match "-Dalbedo\.harness=" }).Count -gt 0) { exit 0 }
if (@($lines | Where-Object { $_ -match "--gameDir|KnotClient|net\.minecraft\.client\.main\.Main|launchwrapper\.Launch|BootstrapLauncher" }).Count -gt 0) { exit 0 }

$rect = New-Object ClipFree+RECT
if (-not [ClipFree]::GetClipCursor([ref]$rect)) { exit 0 }
$width = [ClipFree]::GetSystemMetrics(78)
$height = [ClipFree]::GetSystemMetrics(79)
if (($rect.Right - $rect.Left) -ge $width -and ($rect.Bottom - $rect.Top) -ge $height) { exit 0 }
[void][ClipFree]::ClipCursor([IntPtr]::Zero)
if ($Log) {
    "{0} freed the cursor clip ({1},{2},{3},{4}) after the process {5} ended" -f (Get-Date -Format "HH:mm:ss"), $rect.Left, $rect.Top, $rect.Right, $rect.Bottom, $WaitPid |
        Add-Content -Encoding ascii $Log
}
