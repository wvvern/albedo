<#
.SYNOPSIS
Builds the release jar of each target and copies the jars to build/libs in the repository root.

.DESCRIPTION
The modern nodes come from the three lists in modern/settings.gradle.
Each node has its own Gradle run with --configure-on-demand, because Loom mixes the mappings of the nodes when one run configures all of them.
The script stops at the first build that fails.

.EXAMPLE
./tools/build-all.ps1
#>
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$modern = Join-Path $root "modern"
$legacy = Join-Path $root "legacy"

function Get-Property([string]$file, [string]$key) {
    $line = Get-Content $file | Where-Object { $_.StartsWith("$key=") } | Select-Object -First 1
    if (-not $line) { throw "no $key in $file" }
    $line.Substring($key.Length + 1).Trim()
}

function Get-Nodes([string]$settings, [string]$list) {
    $match = [regex]::Match($settings, "(?m)^def $list = \[(.*)\]")
    if (-not $match.Success) { throw "no list $list in modern/settings.gradle" }
    @([regex]::Matches($match.Groups[1].Value, "'([^']+)'") | ForEach-Object { $_.Groups[1].Value })
}

function Invoke-Gradle([string]$dir, [string[]]$arguments) {
    Push-Location $dir
    try { & (Join-Path $dir "gradlew.bat") @arguments } finally { Pop-Location }
    if ($LASTEXITCODE -ne 0) { throw "the build failed: gradlew $arguments in $dir" }
}

$libs = Join-Path $root "build\libs"
New-Item -ItemType Directory -Force $libs | Out-Null
Remove-Item (Join-Path $libs "albedo-*.jar") -ErrorAction SilentlyContinue

$settings = Get-Content -Raw (Join-Path $modern "settings.gradle")
$loaderNodes = [ordered]@{ fabric = Get-Nodes $settings "nodes"; forge = Get-Nodes $settings "forgeNodes"; neoforge = Get-Nodes $settings "neoforgeNodes" }
$modVersion = Get-Property (Join-Path $modern "gradle.properties") "mod_version"
foreach ($node in $loaderNodes.fabric) {
    $loaders = @($loaderNodes.Keys | Where-Object { $loaderNodes[$_] -contains $node })
    Write-Host "[$node] $($loaders -join ', ')"
    Invoke-Gradle $modern (@("--configure-on-demand") + ($loaders | ForEach-Object { ":${_}:${node}:releaseJar" }))
    $mc = Get-Property (Join-Path $modern "versions\$node\gradle.properties") "minecraft_version"
    foreach ($loader in $loaders) {
        Copy-Item (Join-Path $modern "$loader\versions\$node\build\libs\albedo-$loader-$modVersion+$mc.jar") $libs
    }
}

Write-Host "[1.8.9] forge"
Invoke-Gradle $legacy @("build")
Copy-Item (Join-Path $legacy "versions\1.8.9\build\libs\albedo-forge-$modVersion+1.8.9.jar") $libs

Get-ChildItem $libs -Filter albedo-*.jar | ForEach-Object { Write-Host $_.Name }
