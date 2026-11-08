<#
.SYNOPSIS
Makes the README screenshots for each target on a hidden desktop.
Builds a test scene in a superflat world. Takes screenshots of vanilla, each built-in profile,
night with and without fullbright, and the settings screen.

.DESCRIPTION
The game runs with the test harness (core/src/main/java/dev/blockpainter/core/dev/Harness.java).
Sound and narrator are off. The game does not pause when it loses focus.
Screenshots go to docs/screenshots/<target>/, or to <OutDir>/<target>/.

.EXAMPLE
./tools/screenshots.ps1                       # every target
./tools/screenshots.ps1 -Targets fabric-26.3  # one target
# Store page images:
./tools/screenshots.ps1 -Targets fabric-26.3 -Shots gallery.txt -OutDir build\gallery
# With Sodium in the dev run, and more blocks for the renderer mods (lily pad, swamp grass):
./tools/screenshots.ps1 -Targets fabric-1.21.11 -Compat sodium -Extra scene-compat.txt -OutDir build\compat-sodium
#>
param(
    [string[]]$Targets = @("fabric-26.3", "neoforge-26.3", "fabric-1.21.11", "neoforge-1.21.11", "fabric-1.21.1", "neoforge-1.21.1", "forge-1.8.9"),
    [int]$TimeoutSeconds = 600,
    # Folder for the screenshots, relative to the repository root. The default is the README folder.
    [string]$OutDir = "docs\screenshots",
    # The shot list in tools/harness/, run after the scene.
    [string]$Shots = "shots.txt",
    # Optional dev-only renderer mod for modern targets, for example sodium or embeddium.
    [string]$Compat = "",
    # More command files in tools/harness/, added after the scene.
    [string[]]$Extra = @()
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

function Set-Options([string]$runDir) {
    # Turn off sound and the first-start onboarding (it can start the narrator) before the game reads the options.
    New-Item -ItemType Directory -Force $runDir | Out-Null
    $file = Join-Path $runDir "options.txt"
    $options = [ordered]@{}
    if (Test-Path $file) {
        foreach ($line in Get-Content $file) { $i = $line.IndexOf(':'); if ($i -gt 0) { $options[$line.Substring(0, $i)] = $line.Substring($i + 1) } }
    }
    $wanted = @{ onboardAccessibility = "false"; narrator = "0"; soundCategory_master = "0.0"; pauseOnLostFocus = "false";
                 tutorialStep = "none"; skipMultiplayerWarning = "true"; joinedFirstServer = "true"; fullscreen = "false"; guiScale = "0" }
    foreach ($k in $wanted.Keys) { $options[$k] = $wanted[$k] }
    ($options.GetEnumerator() | ForEach-Object { "$($_.Key):$($_.Value)" }) | Set-Content -Encoding ascii $file
}

foreach ($target in $Targets) {
    $loader, $version = $target -split "-", 2
    if ($loader -eq "forge") {
        $dir = Join-Path $root "legacy"; $task = "runClientDev"; $runDir = Join-Path $dir "run"; $scene = "scene-legacy.txt"
    } else {
        $dir = Join-Path $root "modern"; $task = ":${loader}:${version}:runClient"
        $runDir = Join-Path $dir "$loader\versions\$version\run"; $scene = "scene-modern.txt"
    }
    $harness = Join-Path $env:TEMP "blockpainter-harness\$target"
    Remove-Item -Recurse -Force $harness -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force $harness | Out-Null
    Set-Options $runDir
    # Use the default config and a new world each time, so the screenshots are the same each run.
    Remove-Item -Force (Join-Path $runDir "config\blockpainter\config.json") -ErrorAction SilentlyContinue
    Remove-Item -Recurse -Force (Join-Path $runDir "saves\BlockPainterTest") -ErrorAction SilentlyContinue
    # First the scene for this version, then the shots for all versions.
    $files = @("harness\$scene") + ($Extra | ForEach-Object { "harness\$_" }) + @("harness\$Shots")
    Get-Content ($files | ForEach-Object { Join-Path $PSScriptRoot $_ }) |
        Set-Content -Encoding ascii (Join-Path $harness "commands.txt")
    (Get-Item (Join-Path $harness "commands.txt")).LastWriteTime = (Get-Date).AddSeconds(-5)

    Write-Host "[$target] launching hidden"
    $log = Join-Path $harness "gradle.log"
    $gradlePid = & (Join-Path $PSScriptRoot "run-hidden.ps1") -Dir $dir -Task $task -Harness $harness -Log $log -Compat $Compat
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $harnessLog = Join-Path $harness "log.txt"
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 3
        if (-not (Get-Process -Id $gradlePid -ErrorAction SilentlyContinue)) { break }
        if ((Test-Path $harnessLog) -and (Select-String -Path $harnessLog -Pattern "^> quit" -Quiet)) { Start-Sleep -Seconds 5; break }
    }
    if (Get-Process -Id $gradlePid -ErrorAction SilentlyContinue) {
        Write-Host "[$target] still running after the scene; stopping it"
        Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -match [regex]::Escape($harness) } |
            ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
        Stop-Process -Id $gradlePid -Force -ErrorAction SilentlyContinue
    }

    $out = Join-Path $root "$OutDir\$target"
    Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force $out | Out-Null
    $shots = Get-ChildItem $harness -Filter *.png
    $shots | Copy-Item -Destination $out -Force
    Write-Host "[$target] $($shots.Count) screenshots -> $out"
    if (Test-Path $harnessLog) { Select-String -Path $harnessLog -Pattern "^!" | ForEach-Object { Write-Host "[$target] $($_.Line)" } }
}
