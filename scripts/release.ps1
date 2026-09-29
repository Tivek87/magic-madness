# ============================================================================
# MAGIC MADNESS - RELEASE & JAR VERSION MANAGER (0.0.1-alpha .. 50.50.50-alpha)
# ============================================================================

# #region 1. PARAMETERS & PATHS
# Usage: release.ps1 prepare (before the commit) | publish (after the push)
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('prepare', 'publish')]
    [string]$Step
)

$ErrorActionPreference = 'Stop'
$Keep = 5
$MaxPart = 50
$Base = $MaxPart + 1
$Root = Split-Path -Parent $PSScriptRoot
$ModDir = Join-Path $Root 'Magic Madness'
$Props = Join-Path $ModDir 'gradle.properties'
$Changelog = Join-Path $ModDir 'docs\CHANGELOG.md'
$Releases = Join-Path $Root 'releases'
$Manifest = Join-Path $Releases 'LATEST_5_JARS.txt'
$Utf8 = New-Object System.Text.UTF8Encoding($false)
# #endregion

# #region 2. VERSION PARSING & STEPPING (X.Y.Z WHERE EACH PART IS 0..50)
function Get-Prop([string]$name) {
    $line = [IO.File]::ReadAllLines($Props, $Utf8) | Where-Object { $_ -match "^$name=" } | Select-Object -First 1
    if (-not $line) { throw "$name missing in gradle.properties" }
    return $line.Substring($name.Length + 1).Trim()
}

function Split-Version([string]$version) {
    if ($version -notmatch '^(\d{1,2})\.(\d{1,2})\.(\d{1,2})(-[0-9A-Za-z.]+)?$') {
        throw "Version '$version' is not X.Y.Z (0..50 per part, optional -suffix)"
    }
    $x = [int]$Matches[1]
    $y = [int]$Matches[2]
    $z = [int]$Matches[3]
    $suffix = if ($Matches[4]) { [string]$Matches[4] } else { '-alpha' }
    if ($x -gt $MaxPart -or $y -gt $MaxPart -or $z -gt $MaxPart) {
        throw "Version '$version' exceeds max $MaxPart in one of X.Y.Z"
    }
    return @{
        X = $x
        Y = $y
        Z = $z
        Number = ($x * $Base * $Base) + ($y * $Base) + $z
        Suffix = $suffix
    }
}

function Join-Version([int]$number, [string]$suffix) {
    $maxNumber = ($MaxPart * $Base * $Base) + ($MaxPart * $Base) + $MaxPart
    if ($number -gt $maxNumber) { throw "Past ${MaxPart}.${MaxPart}.${MaxPart}: pick a new scheme first" }
    $x = [math]::Floor($number / ($Base * $Base))
    $y = [math]::Floor($number / $Base) % $Base
    $z = $number % $Base
    return '{0}.{1}.{2}{3}' -f $x, $y, $z, $suffix
}

function Test-RemoteTag([string]$tag) {
    $out = git -C $Root ls-remote --tags origin "refs/tags/$tag" 2>$null
    return [bool]$out
}

function Get-Notes([string]$version) {
    $lines = [IO.File]::ReadAllLines($Changelog, $Utf8)
    $start = -1
    for ($i = 0; $i -lt $lines.Length; $i++) {
        if ($lines[$i].StartsWith("## [$version]")) { $start = $i; break }
    }
    if ($start -lt 0) { throw "No '## [$version] - <date>' section in Magic Madness/docs/CHANGELOG.md" }
    $end = $lines.Length
    for ($i = $start + 1; $i -lt $lines.Length; $i++) {
        if ($lines[$i].StartsWith('## ') -or $lines[$i].StartsWith('<!-- #endregion')) { $end = $i; break }
    }
    $body = ($lines[($start + 1)..($end - 1)] -join "`n").Trim()
    return $body
}

function Update-Manifest([string]$fileName) {
    New-Item -ItemType Directory -Force $Releases | Out-Null
    $local = Get-ChildItem $Releases -Filter "$fileName-*.jar" -ErrorAction SilentlyContinue | ForEach-Object {
        $v = $_.BaseName.Substring($fileName.Length + 1)
        try {
            $parsed = Split-Version $v
            [pscustomobject]@{
                File = $_
                Version = $v
                Number = $parsed.Number
            }
        } catch { $null }
    } | Where-Object { $_ } | Sort-Object Number -Descending

    $local | Select-Object -Skip $Keep | ForEach-Object {
        Remove-Item $_.File.FullName -Force
        Write-Host "Deleted old releases\$($_.File.Name)"
    }

    $kept = @($local | Select-Object -First $Keep)
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.AppendLine("# ============================================================================")
    [void]$sb.AppendLine("# MAGIC MADNESS - LATEST 5 SAVED RELEASE JARS (MAX 5 KEPT IN /releases)")
    [void]$sb.AppendLine("# ============================================================================")
    [void]$sb.AppendLine("# Version Scheme: vX.Y.Z-alpha (each segment 0..50, e.g. v0.0.1-alpha .. v42.12.37-alpha)")
    [void]$sb.AppendLine("")
    [void]$sb.AppendLine("# #region 1. SAVED JAR VERSIONS (NEWEST FIRST)")
    $idx = 1
    foreach ($item in $kept) {
        $hash = (Get-FileHash $item.File.FullName -Algorithm SHA256).Hash.Substring(0, 16).ToLowerInvariant()
        $sizeKb = [math]::Round($item.File.Length / 1KB, 1)
        [void]$sb.AppendLine("$idx. v$($item.Version) | $($item.File.Name) | ${sizeKb} KB | sha256:$hash")
        $idx++
    }
    [void]$sb.AppendLine("# #endregion")
    [IO.File]::WriteAllText($Manifest, $sb.ToString(), $Utf8)
    Write-Host "Updated releases\LATEST_5_JARS.txt ($($kept.Count) jar(s) tracked)"
}
# #endregion

# #region 3. PREPARE & PUBLISH WORKFLOW
$version = Get-Prop 'mod_version'
$fileName = Get-Prop 'mod_file_name'

if ($Step -eq 'prepare') {
    if (Test-RemoteTag "v$version") {
        $v = Split-Version $version
        $next = Join-Version ($v.Number + 1) $v.Suffix
        $text = [IO.File]::ReadAllText($Props, $Utf8)
        $text = $text -replace "(?m)^mod_version=.*$", "mod_version=$next"
        [IO.File]::WriteAllText($Props, $text, $Utf8)
        Write-Host "mod_version $version -> $next"
        $version = $next
    } else {
        Write-Host "mod_version $version has no release yet: kept"
    }
    Write-Host "CHANGELOG heading needed: ## [$version] - $(Get-Date -Format yyyy-MM-dd)"
    exit 0
}

if (Test-RemoteTag "v$version") { throw "v$version is already released: run 'prepare' before the commit" }
if (git -C $Root status --porcelain --untracked-files=no) { throw 'Uncommitted tracked changes: commit and push first' }
git -C $Root fetch -q origin
$branch = git -C $Root rev-parse --abbrev-ref HEAD
$ahead = git -C $Root rev-list --count "origin/$branch..HEAD"
if ([int]$ahead -gt 0) { throw "$ahead commit(s) not pushed yet: push first" }
$notes = Get-Notes $version

Push-Location $ModDir
try {
    & .\gradlew.bat build --console=plain -q
    if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed' }
} finally {
    Pop-Location
}

$jar = Join-Path $ModDir "build\libs\$fileName-$version.jar"
if (-not (Test-Path $jar)) { throw "Jar not found: $jar" }

New-Item -ItemType Directory -Force $Releases | Out-Null
Copy-Item $jar $Releases -Force
Write-Host "Saved releases\$fileName-$version.jar"
Update-Manifest $fileName

$notesFile = Join-Path ([IO.Path]::GetTempPath()) "release-notes-$version.md"
$body = $notes + "`n`n---`nMinecraft 1.21.1, NeoForge 21.1+. Put the jar in your ``mods`` folder."
[IO.File]::WriteAllText($notesFile, $body, $Utf8)
$sha = git -C $Root rev-parse HEAD
gh release create "v$version" $jar --target $sha --title "v$version" --notes-file $notesFile --latest
if ($LASTEXITCODE -ne 0) { throw 'gh release create failed' }
Remove-Item $notesFile -ErrorAction SilentlyContinue
Write-Host "Released v$version"
# #endregion
