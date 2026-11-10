<#
.SYNOPSIS
Compares the built jars with the jars of a release, entry by entry, with no game.

.DESCRIPTION
For each jar in -New, the script finds the jar with the same name in -Old and compares the entry list and the SHA-256 of each entry.
tools/jar-changes.txt lists the entries that can be different, each with its reason.
The exit code is 1 when an entry that is not in that list is different, added or removed, or when a jar has no partner.

.EXAMPLE
gh release download v0.5.0 --repo wvvern/albedo --dir build\v0.5.0
./tools/build-all.ps1
./tools/compare-jars.ps1 -Old build\v0.5.0 -New build\libs
#>
param(
    [Parameter(Mandatory = $true)][string]$Old,
    [Parameter(Mandatory = $true)][string]$New,
    # Lines of "<entry wildcard> <reason>"; empty lines and lines that start with # are ignored.
    [string]$Explained = (Join-Path $PSScriptRoot "jar-changes.txt")
)
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Get-EntryHashes([string]$jar) {
    $hashes = @{}
    $sha = [Security.Cryptography.SHA256]::Create()
    $zip = [IO.Compression.ZipFile]::OpenRead($jar)
    try {
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName.EndsWith("/")) { continue }
            $stream = $entry.Open()
            try { $hashes[$entry.FullName] = [BitConverter]::ToString($sha.ComputeHash($stream)) } finally { $stream.Dispose() }
        }
    } finally { $zip.Dispose() }
    $hashes
}

$patterns = @(Get-Content $Explained | Where-Object { $_.Trim() -and -not $_.StartsWith("#") } | ForEach-Object { ($_.Trim() -split "\s+", 2)[0] })
$failed = $false
$jars = @(Get-ChildItem $New -Filter *.jar | Sort-Object Name)
if ($jars.Count -eq 0) { throw "no jars in $New" }
foreach ($jar in $jars) {
    $partner = Join-Path $Old $jar.Name
    if (-not (Test-Path $partner)) { Write-Host "$($jar.Name): FAIL, no jar with this name in $Old"; $failed = $true; continue }
    $oldHashes = Get-EntryHashes (Resolve-Path $partner).Path
    $newHashes = Get-EntryHashes $jar.FullName
    $lines = @()
    foreach ($name in (@($oldHashes.Keys) + @($newHashes.Keys) | Sort-Object -Unique)) {
        $state = if (-not $newHashes.ContainsKey($name)) { "removed" } elseif (-not $oldHashes.ContainsKey($name)) { "added" }
                 elseif ($oldHashes[$name] -ne $newHashes[$name]) { "changed" } else { continue }
        $known = @($patterns | Where-Object { $name -like $_ }).Count -gt 0
        if (-not $known) { $failed = $true }
        $lines += "  $(if ($known) { 'explained' } else { 'NOT EXPLAINED' }) $state $name"
    }
    $bad = @($lines | Where-Object { $_ -match "NOT EXPLAINED" }).Count
    Write-Host "$($jar.Name): $($newHashes.Count) entries, $($lines.Count) different, $bad not explained"
    $lines | ForEach-Object { Write-Host $_ }
}
if ($failed) { Write-Host "FAIL"; exit 1 }
Write-Host "PASS"
