<#
.SYNOPSIS
Runs a command file of the test harness for each target on a hidden desktop and checks the result.
The default command file builds a test scene in a superflat world and takes screenshots of vanilla, each built-in profile,
night with and without fullbright, and the settings screen.

.DESCRIPTION
The game runs with the test harness (core/src/main/java/dev/albedo/core/dev/Harness.java).
Sound and narrator are off. The game does not pause when it loses focus.
Screenshots go to <OutDir>/<target>/ (default build/screenshots), with result.txt, which starts with PASS or FAIL.
The exit code is 1 when a target fails. A target is PASS when:
- the game got to quit,
- the game log has "Albedo ready" and "Changed N textures", and with no -Compat, no reason for a resource reload,
- the harness log has no line that starts with ! and no DIFFERENT,
- each skycompare logged "same" and each framechanged logged its result,
- each screenshot is at the window size (1600 x 900),
- the hooks line shows that each hook of the game ran (the script adds the command hooks before quit),
- the run folder has config/albedo/config.json.

.EXAMPLE
./tools/screenshots.ps1                       # every target
./tools/screenshots.ps1 -Targets fabric-26.3  # one target
# From a different shell, give a list of targets through -Command, because -File makes one string of it:
pwsh -Command "& ./tools/screenshots.ps1 -Targets fabric-26.3,neoforge-26.3"
# The gallery images:
./tools/screenshots.ps1 -Targets fabric-26.3 -Shots gallery.txt -GuiScale 2 -OutDir build\gallery
# With VBOs off (the default of 1.12.2 is on):
pwsh -Command "& ./tools/screenshots.ps1 -Targets forge-1.12.2 -OptionLines useVbo:false -OutDir build\vbo-off"
# The Vulkan backend (26.2 and 26.3; the game log has "Using graphics backend Vulkan"):
pwsh -Command "& ./tools/screenshots.ps1 -Targets fabric-26.3 -Backend vulkan -OutDir build\vulkan"
# With Sodium in the dev run, and more blocks for the renderer mods (lily pad, swamp grass):
./tools/screenshots.ps1 -Targets fabric-1.21.11 -Compat sodium -Extra scene-compat.txt -OutDir build\compat-sodium
# With Iris (it adds Sodium) and a shader pack zip that you downloaded; without -ShaderPack, Iris has shaders off:
./tools/screenshots.ps1 -Targets fabric-26.3 -Compat iris -ShaderPack C:\temp\pack.zip -Extra scene-compat.txt -OutDir build\compat-iris
#>
param(
    [string[]]$Targets = @("fabric-26.3", "neoforge-26.3", "fabric-1.21.11", "neoforge-1.21.11", "fabric-1.21.1", "neoforge-1.21.1", "forge-1.8.9"),
    [int]$TimeoutSeconds = 600,
    # The folder for the screenshots, relative to the repository root.
    [string]$OutDir = "build\screenshots",
    # The command file in tools/harness/, run after the scene.
    [string]$Shots = "shots.txt",
    # Optional dev-only renderer mod for modern targets, for example sodium or embeddium.
    [string]$Compat = "",
    # Optional shader pack zip for -Compat iris. The script copies it into the run folder, which git ignores.
    [string]$ShaderPack = "",
    # More command files in tools/harness/, added after the scene.
    [string[]]$Extra = @(),
    # The GUI scale, where 0 is auto; 2 gives the settings screen room for the sky preview in the harness window.
    [int]$GuiScale = 0,
    # Optional config.json that the game starts with in place of the default config, for example a copy of the config of a player.
    [string]$Config = "",
    # More options.txt lines, for example useVbo:false. Not $Options: PowerShell names are not case-sensitive.
    [string[]]$OptionLines = @(),
    # The graphics backend of 26.2 and later, forced with the launch argument --graphicsBackend; 26.1 and older have only OpenGL.
    [ValidateSet("", "opengl", "vulkan")][string]$Backend = "",
    # The Vulkan validation layers of the game (--vulkanValidation), for a run with -Backend vulkan.
    [switch]$VulkanValidation,
    # The mod loader of a fabric target: fabric; quilt, which runs the release jar of the node as a mod on Quilt Loader (the game log has its version line);
    # or quilt-dev, which runs the dev classes on Quilt Loader.
    [ValidateSet("fabric", "quilt", "quilt-dev")][string]$Loader = "fabric"
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$iris = ($Compat -split "," | ForEach-Object { $_.Trim() }) -contains "iris"
if ($ShaderPack) {
    if (-not $iris) { throw "-ShaderPack needs -Compat iris" }
    $ShaderPack = (Resolve-Path $ShaderPack).Path
}
if ($Config) { $Config = (Resolve-Path $Config).Path }

function Set-Options([string]$runDir) {
    # A new file each run, so that no option of an earlier run changes the screenshots.
    # It turns off sound and the first-start onboarding (it can start the narrator) before the game reads the options.
    New-Item -ItemType Directory -Force $runDir | Out-Null
    $options = [ordered]@{ onboardAccessibility = "false"; narrator = "0"; soundCategory_master = "0.0"; pauseOnLostFocus = "false";
                           tutorialStep = "none"; skipMultiplayerWarning = "true"; joinedFirstServer = "true"; fullscreen = "false"; guiScale = "$GuiScale" }
    foreach ($line in $OptionLines) { $key, $value = $line -split ":", 2; $options[$key] = $value }
    ($options.GetEnumerator() | ForEach-Object { "$($_.Key):$($_.Value)" }) | Set-Content -Encoding ascii (Join-Path $runDir "options.txt")
}

function Set-TomlKey([string]$runDir, [string]$name, [string]$key, [string]$value) {
    # Puts "key = value" at the top of config/<name>, where it is outside each table, in place of the old line of the key.
    $config = Join-Path $runDir "config"
    New-Item -ItemType Directory -Force $config | Out-Null
    $file = Join-Path $config $name
    $lines = @(if (Test-Path $file) { Get-Content $file | Where-Object { $_ -notmatch "^\s*$key\s*=" } })
    @("$key = $value") + $lines | Set-Content -Encoding ascii $file
}

function Get-PngSize([string]$file) {
    # The width and the height are the first two big-endian numbers of the IHDR chunk, at byte 16.
    $stream = [IO.File]::OpenRead($file)
    try {
        $head = New-Object byte[] 24
        if ($stream.Read($head, 0, 24) -lt 24) { return "not a PNG" }
    } finally { $stream.Dispose() }
    "$([Net.IPAddress]::NetworkToHostOrder([BitConverter]::ToInt32($head, 16))) x $([Net.IPAddress]::NetworkToHostOrder([BitConverter]::ToInt32($head, 20)))"
}

function Get-Failures([string]$harness, [string]$gradleLog, [string]$runDir, [string[]]$hooks, [string]$forced) {
    # The reasons why a run is not PASS, from the harness folder, the game log and the run folder.
    if (-not (Test-Path (Join-Path $runDir "config\albedo\config.json"))) { "the run folder does not have config/albedo/config.json" }
    $harnessLog = Join-Path $harness "log.txt"
    $harnessText = @(if (Test-Path $harnessLog) { Get-Content $harnessLog })
    $gameText = if (Test-Path $gradleLog) { Get-Content -Raw $gradleLog } else { "" }
    if (-not ($harnessText -match "^> quit")) { "the game did not get to quit" }
    # The game falls back to the other backend when the forced one does not start, which would hide the test of the wrong backend.
    if ($forced -and $gameText -notmatch "(?i)Using graphics backend $forced\b") { "the game log does not have 'Using graphics backend $forced'" }
    if ($Loader -ne "fabric" -and $gameText -notmatch "with Quilt Loader \d") { "the game log does not have the Quilt Loader version line" }
    # The mod table of Quilt Loader names the file of each mod; the release jar of the node is in the mods folder of the run.
    if ($Loader -eq "quilt" -and $gameText -notmatch "<mods>\\albedo-fabric-\S+\.jar") { "the mod table of the game log does not have the release jar of Albedo" }
    if ($gameText -notmatch "Albedo ready") { "the game log does not have 'Albedo ready'" }
    if ($gameText -notmatch "Changed \d+ textures") { "the game log does not have 'Changed N textures'" }
    # With no other mod, each texture repaints in place; a failed upload reloads all resources, which hides the failure in the images.
    if (-not $Compat) {
        [regex]::Matches($gameText, "textures cannot be repainted|more textures than the mod keeps|cannot be sent to the GPU") |
            ForEach-Object { "the game log has a reload reason: $($_.Value)" } | Select-Object -Unique
    }
    $harnessText -match "^!" | ForEach-Object { "harness error: $_" }
    $harnessText -cmatch "DIFFERENT" | ForEach-Object { "harness compare: $_" }
    # The command "hooks" logs how many times each optional hook ran, and an optional hook with a wrong target name never runs.
    $hookLines = @($harnessText -match "^hooks:")
    if ($hookLines.Count -eq 0) { "the harness log does not have a hooks line" }
    foreach ($line in $hookLines) {
        $hooks | Where-Object { $line -notmatch "\b$_=[1-9]" } | ForEach-Object { "the hook $_ did not run: $line" }
    }
    # The harness renames commands.txt to commands.taken when it reads it.
    $compares = 0
    $frameChecks = 0
    $cursorChecks = 0
    # The game starts at the size of the run task.
    $size = "1600 x 900"
    foreach ($line in Get-Content (Join-Path $harness "commands.taken") -ErrorAction SilentlyContinue) {
        $words = @($line.Trim() -split "\s+")
        if ($words[0] -eq "skycompare") { $compares++ }
        if ($words[0] -eq "framechanged") { $frameChecks++ }
        if ($words[0] -eq "cursorfree") { $cursorChecks++ }
        $images = @()
        if ($words[0] -eq "screenshot") {
            $images = @(Get-Item (Join-Path $harness "$($words[1] -replace '\.png$', '').png") -ErrorAction SilentlyContinue)
            if ($images.Count -eq 0) { "no image for: $line" }
        } elseif ($words[0] -eq "frames") {
            $images = @(Get-ChildItem $harness -Filter "$($words[2])-*.png")
            if ($images.Count -lt [int]$words[1]) { "not all images for: $line" }
        }
        # A game window of the wrong size gives screenshots that do not compare with the reference.
        $images | Where-Object { (Get-PngSize $_.FullName) -ne $size } | ForEach-Object { "$($_.Name) is $(Get-PngSize $_.FullName), expected $size" }
    }
    # A skycompare logs its result in a later frame, so a run can get to quit with no result.
    $same = @($harnessText -match "^skycompare .*: same$").Count
    if ($same -lt $compares) { "$compares skycompare commands gave only $same results with 'same'" }
    # A framechanged logs its result when the next frame is read back, which can come after quit.
    $changed = @($harnessText -match "^framechanged ").Count
    if ($changed -lt $frameChecks) { "$frameChecks framechanged commands gave only $changed results" }
    # A cursorfree logs its result at once, with a "!" when the game clips the cursor, so a missing line means that it did not run.
    $cursorFree = @($harnessText -match "^cursorfree: (ok|skipped)").Count
    if ($cursorFree -lt $cursorChecks) { "$cursorChecks cursorfree commands gave only $cursorFree results with 'ok'" }
}

# The ground of a superflat world is at y=-61 from 1.18 and at y=3 before it, so a scene line moves up by 64 blocks on the old versions.
function Move-SceneUp([string]$line) {
    $words = $line -split " "
    $yIndexes = switch ($words[1]) { "fill" { @(3, 6) } "setblock" { @(3) } "tp" { @(4) } "summon" { @(4) } default { @() } }
    if ($words[0] -ne "run" -or $words.Count -lt 5) { return $line }
    foreach ($i in $yIndexes) {
        $y = 0.0
        if ($i -lt $words.Count -and [double]::TryParse($words[$i], [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture, [ref]$y) -and $y -ge -64 -and $y -lt 0) {
            $words[$i] = ($y + 64).ToString([Globalization.CultureInfo]::InvariantCulture)
        }
    }
    return $words -join " "
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

$failed = @()
foreach ($target in $Targets) {
    $irisFailed = $false
    $platform, $version = $target -split "-", 2
    if ($Loader -ne "fabric" -and $platform -ne "fabric") { throw "-Loader $Loader runs only fabric targets, not $target" }
    # The legacy build has the Forge targets with MCP names; all other Forge targets are in the modern build.
    $legacy = $target -in @("forge-1.8.9", "forge-1.12.2")
    if ($legacy) {
        $dir = Join-Path $root "legacy"; $task = ":${version}:runClientDev"; $runDir = Join-Path $dir "versions\$version\run"; $scene = "scene-legacy.txt"
    } else {
        $dir = Join-Path $root "modern"; $task = ":${platform}:${version}:runClient"
        $runDir = Join-Path $dir "$platform\versions\$version\run"; $scene = "scene-modern.txt"
    }
    $harness = Join-Path $env:TEMP "albedo-harness\$target"
    Remove-Item -Recurse -Force $harness -ErrorAction SilentlyContinue
    # A process of an earlier run can hold the folder, and its old log and images would then count for this run.
    if (Test-Path $harness) { throw "cannot delete $harness; stop the process that uses it" }
    New-Item -ItemType Directory -Force $harness | Out-Null
    # The files that the commands use, for example clip.ps1 of cursorfree.
    Copy-Item -Recurse (Join-Path $PSScriptRoot "harness\files") $harness
    Set-Options $runDir
    # A release jar that an earlier -Loader quilt run left here would load next to the classes of the dev run.
    Remove-Item (Join-Path $runDir "mods\albedo-*.jar") -ErrorAction SilentlyContinue
    # The early loading window of Forge and NeoForge does not open on the hidden desktop, so the game makes its window itself.
    if (-not $legacy -and $platform -ne "fabric") { Set-TomlKey $runDir "fml.toml" "earlyWindowControl" "false" }
    # The NeoForge dev run checks each Blaze3D call, but Sodium 0.8.14 casts a texture that this check wraps, so -Compat stops it.
    if (-not $legacy -and $platform -eq "neoforge") {
        Set-TomlKey $runDir "neoforge-client.toml" "enableB3DValidationLayer" $(if ($Compat) { "false" } else { "true" })
    }
    $irisRun = $iris -and -not $legacy
    if ($irisRun) { Set-Iris $runDir }
    # Use the default config (or the -Config file) and a new world each time, so the screenshots are the same each run.
    $configFile = Join-Path $runDir "config\albedo\config.json"
    Remove-Item -Force $configFile -ErrorAction SilentlyContinue
    if ($Config) {
        New-Item -ItemType Directory -Force (Split-Path $configFile) | Out-Null
        Copy-Item -Force $Config $configFile
    }
    Remove-Item -Recurse -Force (Join-Path $runDir "saves\AlbedoTest") -ErrorAction SilentlyContinue
    # First the scene of this version, then the command file, where a legacy target runs the *-legacy.txt copy of a *-modern.txt file.
    $shotList = if ($legacy) { $Shots -replace "-modern\.txt$", "-legacy.txt" } else { $Shots }
    $files = @("harness\$scene") + ($Extra | ForEach-Object { "harness\$_" }) + @("harness\$shotList")
    $lines = Get-Content ($files | ForEach-Object { Join-Path $PSScriptRoot $_ })
    # The legacy files use the commands of 1.8.9; 1.12.2 names an entity type by its id and has no achievement command.
    if ($target -eq "forge-1.12.2") {
        $lines = $lines | Where-Object { $_ -notmatch "^run achievement " } | ForEach-Object {
            [regex]::Replace(($_ -creplace "type=!Player\b", "type=!player"), "^(run summon )([A-Z]\w*)",
                { param($m) $m.Groups[1].Value + "minecraft:" + $m.Groups[2].Value.ToLowerInvariant() })
        }
    }
    # Each run logs the hook counters before it stops, so that the result can show a hook that did not run.
    # The block short_grass has the name grass before 1.20.3.
    $oldGrass = -not $legacy -and [version]$version -lt [version]"1.20.3"
    $oldGround = -not $legacy -and [version]$version -lt [version]"1.18"
    $lines | ForEach-Object { if ($_ -match "^quit\s*$") { "hooks" }; $_ } |
        ForEach-Object { if ($oldGrass) { $_ -replace "\bshort_grass\b", "grass" } else { $_ } } |
        ForEach-Object { if ($oldGround) { Move-SceneUp $_ } else { $_ } } |
        # The server stands at the world spawn, which a flat world before 1.18 puts away from 0 0, so a relative tp starts from the player there.
        ForEach-Object { if ($oldGround) { $_ -replace "^run tp @p ~ ~ ~ ", "run execute as @p at @p run tp @p ~ ~ ~ " } else { $_ } } |
        Set-Content -Encoding ascii (Join-Path $harness "commands.txt")

    Write-Host "[$target] launching hidden"
    $log = Join-Path $harness "gradle.log"
    # Only 26.2 and later have the launch argument; the OpenGL game of an older version has no backend line to check.
    $forcedBackend = if ($legacy -or [version]$version -lt [version]"26.2") { "" } else { $Backend }
    $gradlePid = & (Join-Path $PSScriptRoot "run-hidden.ps1") -Dir $dir -Task $task -Harness $harness -Log $log -Compat $Compat -Backend $forcedBackend -VulkanValidation:$VulkanValidation -Quilt:($Loader -ne "fabric") -QuiltJar:($Loader -eq "quilt")
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $harnessLog = Join-Path $harness "log.txt"
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 3
        if (-not (Get-Process -Id $gradlePid -ErrorAction SilentlyContinue)) { break }
        # A loader that fails to start leaves the Gradle process running.
        if ((Test-Path $log) -and (Select-String -Path $log -Pattern 'Uncaught exception in thread "main"' -Quiet)) { Write-Host "[$target] the loader did not start"; break }
        if ((Test-Path $harnessLog) -and (Select-String -Path $harnessLog -Pattern "^> quit" -Quiet)) {
            # The game saves the world before it exits; 26.x can take 10 s or more.
            $quitBy = (Get-Date).AddSeconds(30)
            while ((Get-Date) -lt $quitBy -and (Get-Process -Id $gradlePid -ErrorAction SilentlyContinue)) { Start-Sleep -Seconds 1 }
            break
        }
    }
    if (Get-Process -Id $gradlePid -ErrorAction SilentlyContinue) {
        Write-Host "[$target] still running; stopping it"
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
    # Each line that starts with ! fails the target, also "! unknown command" for an error in the command file.
    if (Test-Path $harnessLog) { Select-String -Path $harnessLog -Pattern "^!" | ForEach-Object { Write-Host "[$target] $($_.Line)" } }
    # Iris turns shaders off when the pack does not load, and the run continues.
    $gradleText = Get-Content -Raw $log
    if ($ShaderPack -and $irisRun -and ($gradleText -notmatch "Using shaderpack" -or $gradleText -match "disabling shaders")) {
        Write-Host "[$target] Iris did not load the shader pack; see $log"
        $irisFailed = $true
    }
    # A renderer mod replaces the terrain draw, so its own tint hook must run in place of the game's tint and afterSolid hooks.
    # The hook mouseGrab counts the grab calls that the harness skips, so that a game which grabs the mouse (and clips the cursor) fails.
    $hooks = if ($legacy) { @("afterSolid", "beforeClouds", "tint", "shade", "reloadStep", "textureUpload", "mouseGrab") }
             elseif ($Compat) { @("fullbright", "shadow", "skyPass", "sodiumTint", "mouseGrab") }
             else { @("afterSolid", "tint", "fullbright", "shadow", "skyPass", "mouseGrab") }
    $failures = @(Get-Failures $harness $log $runDir $hooks $forcedBackend)
    if ($irisFailed) { $failures += "Iris did not load the shader pack" }
    $result = if ($failures.Count -eq 0) { "PASS" } else { "FAIL" }
    @($result, "target: $target", "commands: $(@($files | Split-Path -Leaf) -join ', ')", "screenshots: $($pngs.Count)") + $failures |
        Set-Content -Encoding ascii (Join-Path $out "result.txt")
    Write-Host "[$target] $result"
    if ($failures.Count -gt 0) { $failed += $target }
}
if ($failed.Count -gt 0) { Write-Host "FAIL: $($failed -join ', ')"; exit 1 }
