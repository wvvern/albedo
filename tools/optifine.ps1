<#
.SYNOPSIS
Runs the release jar of the 1.8.9 build in a production Forge client, with or without OptiFine, on a hidden desktop.
Takes the screenshots of the test scene for each OptiFine setting.

.DESCRIPTION
OptiFine patches the obfuscated game classes, so it does not run in the dev client (MCP names). This script starts
the game as the launcher does: launchwrapper, the Forge universal jar and the obfuscated 1.8.9 jar.

Before you start:
1. Install Minecraft 1.8.9 and Forge 1.8.9 one time with the official launcher (the script reads
   %APPDATA%\.minecraft\versions\1.8.9 and %APPDATA%\.minecraft\libraries\net\minecraftforge\forge).
   The script also finds libraries in the Gradle cache of legacy/.
2. Put an OptiFine 1.8.9 jar (for example HD U M5) in legacy\build\optifine\. Get it from optifine.net.
3. Run "./gradlew build" in legacy/.

The game runs offline (no login). The game folder is legacy\build\optifine\game.
Screenshots go to <OutDir>\<variant>\. The harness log is in %TEMP%\worldpainter-harness\optifine-1.8.9-<variant>\.

Variants (OptiFine settings, see Set-Variant):
  none        no OptiFine
  default     OptiFine with its default settings
  quake       Fast Render, smooth lighting off, Custom Colors, fancy Connected and Natural Textures, Fast graphics,
              Clear Water, fancy Better Grass, Dynamic Lights off
  quake-ao    quake with smooth lighting on
  quake-fancy quake with Fancy graphics

.EXAMPLE
./tools/optifine.ps1                                   # all variants
./tools/optifine.ps1 -Variants none,quake -OutDir build\compat-optifine
./tools/optifine.ps1 -Variants quake -DumpClasses   # the transformed classes go to legacy\build\optifine\game\CLASSLOADER_TEMP
#>
param(
    [string[]]$Variants = @("none", "default", "quake", "quake-ao", "quake-fancy"),
    [string]$Shots = "shots.txt",
    [string]$OutDir = "build\compat-optifine",
    [string]$Java = "",
    [switch]$DumpClasses,
    [int]$TimeoutSeconds = 600
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$legacy = Join-Path $root "legacy"
$work = Join-Path $legacy "build\optifine"
$game = Join-Path $work "game"
$mc = Join-Path $env:APPDATA ".minecraft"
$gradleCache = Join-Path $env:USERPROFILE ".gradle\caches"

function Find-Java {
    if ($Java) { return $Java }
    $candidates = @(Get-ChildItem (Join-Path $env:USERPROFILE ".jdks") -Directory -Filter "*1.8*" -ErrorAction SilentlyContinue) +
        @(Get-ChildItem "C:\Program Files\Eclipse Adoptium", "C:\Program Files\Java" -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match "^(jdk|jre)-?(8|1\.8)" })
    foreach ($c in $candidates) { $exe = Join-Path $c.FullName "bin\java.exe"; if (Test-Path $exe) { return $exe } }
    throw "No Java 8 found. Use -Java <path to java.exe>."
}

function Find-Library([string]$coordinate) {
    $group, $artifact, $version = $coordinate -split ":"
    $file = "$artifact-$version.jar"
    $path = Join-Path $mc ("libraries\" + ($group -replace "\.", "\") + "\$artifact\$version\$file")
    if (Test-Path $path) { return $path }
    $hit = Get-ChildItem (Join-Path $gradleCache "modules-2\files-2.1\$group\$artifact\$version") -Recurse -Filter $file -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($hit) { return $hit.FullName }
    throw "Library not found: $coordinate"
}

function Get-ClassPath {
    $forge = Get-ChildItem (Join-Path $mc "libraries\net\minecraftforge\forge") -Recurse -Filter "forge-1.8.9-*.jar" |
        Sort-Object Name -Descending | Select-Object -First 1
    if (-not $forge) { throw "Forge 1.8.9 is not installed in $mc" }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead($forge.FullName)
    try { $reader = New-Object IO.StreamReader($zip.GetEntry("version.json").Open()); $forgeJson = $reader.ReadToEnd() | ConvertFrom-Json }
    finally { $zip.Dispose() }
    $vanillaJson = Get-Content (Join-Path $mc "versions\1.8.9\1.8.9.json") -Raw | ConvertFrom-Json
    $seen = @{}; $cp = @()
    foreach ($lib in @($forgeJson.libraries) + @($vanillaJson.libraries)) {
        $name = $lib.name
        $key = ($name -split ":")[0..1] -join ":"
        if ($seen.ContainsKey($key) -or $lib.natives -or $name -like "net.minecraftforge:forge:*") { continue }
        if ($lib.rules -and -not ($lib.rules | Where-Object { $_.action -eq "allow" -and -not $_.os })) { continue }
        $seen[$key] = $true
        $cp += Find-Library $name
    }
    $cp += $forge.FullName
    $cp += Join-Path $mc "versions\1.8.9\1.8.9.jar"
    return ($cp -join ";")
}

function Set-Lines([string]$file, [hashtable]$wanted) {
    $options = [ordered]@{}
    if (Test-Path $file) {
        foreach ($line in Get-Content $file) { $i = $line.IndexOf(':'); if ($i -gt 0) { $options[$line.Substring(0, $i)] = $line.Substring($i + 1) } }
    }
    foreach ($k in $wanted.Keys) { $options[$k] = $wanted[$k] }
    ($options.GetEnumerator() | ForEach-Object { "$($_.Key):$($_.Value)" }) | Set-Content -Encoding ascii $file
}

function Set-Variant([string]$variant) {
    New-Item -ItemType Directory -Force (Join-Path $game "mods") | Out-Null
    Get-ChildItem (Join-Path $game "mods") -Filter *.jar | Remove-Item -Force
    $jar = Get-ChildItem (Join-Path $legacy "build\libs") -Filter "world-painter-forge-*.jar" | Select-Object -First 1
    if (-not $jar) { throw "Run ./gradlew build in legacy/ first." }
    Copy-Item $jar.FullName (Join-Path $game "mods")
    if ($variant -ne "none") {
        $of = Get-ChildItem $work -Filter "*OptiFine*1.8.9*.jar" | Select-Object -First 1
        if (-not $of) { throw "Put an OptiFine 1.8.9 jar in $work" }
        Copy-Item $of.FullName (Join-Path $game "mods")
    }
    $options = @{ narrator = "0"; soundCategory_master = "0.0"; pauseOnLostFocus = "false"; fullscreen = "false"; guiScale = "0";
                  fancyGraphics = "true"; ao = "2" }
    $of = @{}
    Remove-Item -Force (Join-Path $game "optionsof.txt") -ErrorAction SilentlyContinue
    if ($variant -like "quake*") {
        $options.fancyGraphics = "false"; $options.ao = "0"
        $of = @{ ofFastRender = "true"; ofCustomColors = "true"; ofConnectedTextures = "2"; ofNaturalTextures = "true";
                 ofClearWater = "true"; ofBetterGrass = "2"; ofDynamicLights = "3" }
        if ($variant -eq "quake-ao") { $options.ao = "2" }
        if ($variant -eq "quake-fancy") { $options.fancyGraphics = "true" }
    }
    Set-Lines (Join-Path $game "options.txt") $options
    if ($of.Count -gt 0) { Set-Lines (Join-Path $game "optionsof.txt") $of }
    Remove-Item -Force (Join-Path $game "config\worldpainter\config.json") -ErrorAction SilentlyContinue
    Remove-Item -Recurse -Force (Join-Path $game "saves\WorldPainterTest") -ErrorAction SilentlyContinue
}

$javaExe = Find-Java
$classPath = Get-ClassPath
$natives = Join-Path $gradleCache "essential-loom\1.8.9\natives"
$assets = Join-Path $gradleCache "essential-loom\assets"
$assetIndex = "1.8.9-1.8"
if (-not (Test-Path (Join-Path $assets "indexes\$assetIndex.json"))) { $assets = Join-Path $mc "assets"; $assetIndex = "1.8" }

foreach ($variant in $Variants) {
    $harness = Join-Path $env:TEMP "worldpainter-harness\optifine-1.8.9-$variant"
    if (Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -match [regex]::Escape($harness) }) {
        throw "A game with the harness $harness is already running."
    }
    Remove-Item -Recurse -Force $harness -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force $harness | Out-Null
    Set-Variant $variant
    Get-Content (Join-Path $PSScriptRoot "harness\scene-legacy.txt"), (Join-Path $PSScriptRoot "harness\$Shots") |
        Set-Content -Encoding ascii (Join-Path $harness "commands.txt")
    (Get-Item (Join-Path $harness "commands.txt")).LastWriteTime = (Get-Date).AddSeconds(-5)

    # The class path is too long for cmd.exe, so it goes in the CLASSPATH variable that the game process inherits.
    $env:CLASSPATH = $classPath
    $debug = if ($DumpClasses) { "-Dlegacy.debugClassLoading=true -Dlegacy.debugClassLoadingSave=true -Dmixin.debug.verbose=true" } else { "" }
    $arguments = @("-Xmx2G", "-Djava.library.path=`"$natives`"", "-Dworldpainter.harness=`"$harness`"", $debug, "net.minecraft.launchwrapper.Launch",
        "--username", "Dev", "--version", "1.8.9", "--gameDir", "`"$game`"", "--assetsDir", "`"$assets`"", "--assetIndex", $assetIndex,
        "--uuid", "0", "--accessToken", "0", "--userProperties", "{}", "--userType", "legacy",
        "--tweakClass", "net.minecraftforge.fml.common.launcher.FMLTweaker", "--width", "1600", "--height", "900")
    $commandLine = "`"$javaExe`" " + ($arguments -join " ")
    Write-Host "[$variant] launching hidden"
    $log = Join-Path $harness "game.log"
    $cmdPid = & (Join-Path $PSScriptRoot "run-hidden.ps1") -Dir $game -CommandLine $commandLine -Log $log
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $harnessLog = Join-Path $harness "log.txt"
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 3
        if (-not (Get-Process -Id $cmdPid -ErrorAction SilentlyContinue)) { break }
        if ((Test-Path $harnessLog) -and (Select-String -Path $harnessLog -Pattern "^> quit" -Quiet)) { Start-Sleep -Seconds 5; break }
    }
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -match [regex]::Escape($harness) } |
        ForEach-Object { Write-Host "[$variant] still running; stopping it"; Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

    $out = Join-Path $root "$OutDir\$variant"
    Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force $out | Out-Null
    $pngs = Get-ChildItem $harness -Filter *.png
    $pngs | Copy-Item -Destination $out -Force
    Copy-Item $log, $harnessLog $out -ErrorAction SilentlyContinue
    Write-Host "[$variant] $($pngs.Count) screenshots -> $out"
    if (Test-Path $harnessLog) { Select-String -Path $harnessLog -Pattern "^!" | ForEach-Object { Write-Host "[$variant] $($_.Line)" } }
}
