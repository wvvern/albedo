<#
.SYNOPSIS
Takes the README screenshots for each target on a hidden desktop: builds a test scene in a superflat world, then
captures vanilla, every built-in profile, night with and without fullbright, and the settings screen.

.DESCRIPTION
For each target the game runs with the test harness (see core/src/main/java/dev/singlecolor/core/dev/Harness.java),
sound muted, narrator off, no pausing on focus loss. Screenshots land in docs/screenshots/<target>/.

.EXAMPLE
./tools/screenshots.ps1                       # every target
./tools/screenshots.ps1 -Targets fabric-26.3  # one target
#>
param(
    [string[]]$Targets = @("fabric-26.3", "neoforge-26.3", "fabric-1.21.11", "neoforge-1.21.11", "fabric-1.21.1", "neoforge-1.21.1", "forge-1.8.9"),
    [int]$TimeoutSeconds = 600
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

function Set-Options([string]$runDir) {
    # Silence first-launch onboarding (which can speak through the narrator) and sound before the game reads them.
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
    $harness = Join-Path $env:TEMP "singlecolor-harness\$target"
    Remove-Item -Recurse -Force $harness -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force $harness | Out-Null
    Set-Options $runDir
    # Start from the default config and a fresh world every time, so screenshots are reproducible.
    Remove-Item -Force (Join-Path $runDir "config\singlecolor\config.json") -ErrorAction SilentlyContinue
    Remove-Item -Recurse -Force (Join-Path $runDir "saves\SingleColorTest") -ErrorAction SilentlyContinue
    Copy-Item (Join-Path $PSScriptRoot "harness\$scene") (Join-Path $harness "commands.txt")
    (Get-Item (Join-Path $harness "commands.txt")).LastWriteTime = (Get-Date).AddSeconds(-5)

    Write-Host "[$target] launching hidden"
    $log = Join-Path $harness "gradle.log"
    $gradlePid = & (Join-Path $PSScriptRoot "run-hidden.ps1") -Dir $dir -Task $task -Harness $harness -Log $log
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

    $out = Join-Path $root "docs\screenshots\$target"
    Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force $out | Out-Null
    $shots = Get-ChildItem $harness -Filter *.png
    $shots | Copy-Item -Destination $out -Force
    Write-Host "[$target] $($shots.Count) screenshots -> $out"
    if (Test-Path $harnessLog) { Select-String -Path $harnessLog -Pattern "^!" | ForEach-Object { Write-Host "[$target] $($_.Line)" } }
}
