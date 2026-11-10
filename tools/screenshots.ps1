<#
.SYNOPSIS
Makes the README screenshots for each target on a hidden desktop.
Builds a test scene in a superflat world. Takes screenshots of vanilla, each built-in profile,
night with and without fullbright, and the settings screen.

.DESCRIPTION
The game runs with the test harness (core/src/main/java/dev/albedo/core/dev/Harness.java).
Sound and narrator are off. The game does not pause when it loses focus.
Screenshots go to docs/screenshots/<target>/, or to <OutDir>/<target>/.

.EXAMPLE
./tools/screenshots.ps1                       # every target
./tools/screenshots.ps1 -Targets fabric-26.3  # one target
# Store page images:
./tools/screenshots.ps1 -Targets fabric-26.3 -Shots gallery-041.txt -GuiScale 2 -OutDir build\gallery-041
# With Sodium in the dev run, and more blocks for the renderer mods (lily pad, swamp grass):
./tools/screenshots.ps1 -Targets fabric-1.21.11 -Compat sodium -Extra scene-compat.txt -OutDir build\compat-sodium
# With Iris (it adds Sodium) and a shader pack zip that you downloaded; without -ShaderPack, Iris has shaders off:
./tools/screenshots.ps1 -Targets fabric-26.3 -Compat iris -ShaderPack C:\temp\pack.zip -Extra scene-compat.txt -OutDir build\compat-iris
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
    # Optional shader pack zip for -Compat iris. The script copies it into the run folder, which git ignores.
    [string]$ShaderPack = "",
    # More command files in tools/harness/, added after the scene.
    [string[]]$Extra = @(),
    # The GUI scale, where 0 is auto; 2 gives the settings screen room for the sky preview in the harness window.
    [int]$GuiScale = 0
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$iris = ($Compat -split "," | ForEach-Object { $_.Trim() }) -contains "iris"
if ($ShaderPack) {
    if (-not $iris) { throw "-ShaderPack needs -Compat iris" }
    $ShaderPack = (Resolve-Path $ShaderPack).Path
}

function Set-Options([string]$runDir) {
    # Turn off sound and the first-start onboarding (it can start the narrator) before the game reads the options.
    New-Item -ItemType Directory -Force $runDir | Out-Null
    $file = Join-Path $runDir "options.txt"
    $options = [ordered]@{}
    if (Test-Path $file) {
        foreach ($line in Get-Content $file) { $i = $line.IndexOf(':'); if ($i -gt 0) { $options[$line.Substring(0, $i)] = $line.Substring($i + 1) } }
    }
    $wanted = @{ onboardAccessibility = "false"; narrator = "0"; soundCategory_master = "0.0"; pauseOnLostFocus = "false";
                 tutorialStep = "none"; skipMultiplayerWarning = "true"; joinedFirstServer = "true"; fullscreen = "false"; guiScale = "$GuiScale" }
    foreach ($k in $wanted.Keys) { $options[$k] = $wanted[$k] }
    ($options.GetEnumerator() | ForEach-Object { "$($_.Key):$($_.Value)" }) | Set-Content -Encoding ascii $file
}

function Set-Iris([string]$runDir) {
    # Iris reads config/iris.properties at start, and the file stays for later runs, so each run writes it.
    $config = Join-Path $runDir "config"
    New-Item -ItemType Directory -Force $config | Out-Null
    $lines = @("disableUpdateMessage=true", "enableShaders=$(if ($ShaderPack) { 'true' } else { 'false' })")
    if ($ShaderPack) {
        $packs = Join-Path $runDir "shaderpacks"
        New-Item -ItemType Directory -Force $packs | Out-Null
        $name = Split-Path -Leaf $ShaderPack
        # A new copy each run, so that a folder pack does not keep files from an old copy.
        Remove-Item -Recurse -Force (Join-Path $packs $name) -ErrorAction SilentlyContinue
        Copy-Item -Recurse -Force $ShaderPack $packs
        $lines += "shaderPack=$name"
    }
    # Iris reads the file as ISO-8859-1, so no byte order mark.
    $lines | Set-Content -Encoding ascii (Join-Path $config "iris.properties")
}

foreach ($target in $Targets) {
    $loader, $version = $target -split "-", 2
    if ($loader -eq "forge") {
        $dir = Join-Path $root "legacy"; $task = "runClientDev"; $runDir = Join-Path $dir "run"; $scene = "scene-legacy.txt"
    } else {
        $dir = Join-Path $root "modern"; $task = ":${loader}:${version}:runClient"
        $runDir = Join-Path $dir "$loader\versions\$version\run"; $scene = "scene-modern.txt"
    }
    $harness = Join-Path $env:TEMP "albedo-harness\$target"
    Remove-Item -Recurse -Force $harness -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force $harness | Out-Null
    # Images that the commands load, for example the test skin of skins.txt.
    Copy-Item -Recurse (Join-Path $PSScriptRoot "harness\files") $harness
    Set-Options $runDir
    $irisRun = $iris -and $loader -ne "forge"
    if ($irisRun) { Set-Iris $runDir }
    # Use the default config and a new world each time, so the screenshots are the same each run.
    Remove-Item -Force (Join-Path $runDir "config\albedo\config.json") -ErrorAction SilentlyContinue
    Remove-Item -Recurse -Force (Join-Path $runDir "saves\AlbedoTest") -ErrorAction SilentlyContinue
    # First the scene for this version, then the shots for all versions; a shot list for modern has a copy for 1.8.9.
    $shotList = if ($loader -eq "forge") { $Shots -replace "-modern\.txt$", "-legacy.txt" } else { $Shots }
    $files = @("harness\$scene") + ($Extra | ForEach-Object { "harness\$_" }) + @("harness\$shotList")
    Get-Content ($files | ForEach-Object { Join-Path $PSScriptRoot $_ }) |
        Set-Content -Encoding ascii (Join-Path $harness "commands.txt")

    Write-Host "[$target] launching hidden"
    $log = Join-Path $harness "gradle.log"
    $gradlePid = & (Join-Path $PSScriptRoot "run-hidden.ps1") -Dir $dir -Task $task -Harness $harness -Log $log -Compat $Compat
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $harnessLog = Join-Path $harness "log.txt"
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 3
        if (-not (Get-Process -Id $gradlePid -ErrorAction SilentlyContinue)) { break }
        if ((Test-Path $harnessLog) -and (Select-String -Path $harnessLog -Pattern "^> quit" -Quiet)) {
            # The game saves the world before it exits; 26.x can take 10 s or more.
            $quitBy = (Get-Date).AddSeconds(30)
            while ((Get-Date) -lt $quitBy -and (Get-Process -Id $gradlePid -ErrorAction SilentlyContinue)) { Start-Sleep -Seconds 1 }
            break
        }
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
    # Not $shots: PowerShell names are not case-sensitive, and the [string]$Shots parameter would make it one string.
    $pngs = Get-ChildItem $harness -Filter *.png
    $pngs | Copy-Item -Destination $out -Force
    Write-Host "[$target] $($pngs.Count) screenshots -> $out"
    if (Test-Path $harnessLog) { Select-String -Path $harnessLog -Pattern "^!" | ForEach-Object { Write-Host "[$target] $($_.Line)" } }
    # Iris turns shaders off when the pack does not load, and the run continues.
    $gradleText = Get-Content -Raw $log
    if ($ShaderPack -and $irisRun -and ($gradleText -notmatch "Using shaderpack" -or $gradleText -match "disabling shaders")) {
        Write-Host "[$target] Iris did not load the shader pack; see $log"
    }
}
