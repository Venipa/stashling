<#
.SYNOPSIS
  Quick signed release build (skips tests). Wraps gradlew assembleRelease with the keystore the
  build expects (STASHLING_KEYSTORE_*/STASHLING_KEY_*).

.EXAMPLE
  ./release.ps1 -Keystore F:\keys\stashling.jks -StorePassword secret -KeyAlias upload -KeyPassword secret
  ./release.ps1 -Bundle -VersionName 1.2.3 -VersionCode 10203   # reuses keystore from env vars

  Without args it reads the four values from the environment, so you can export them once and just run ./release.ps1.
#>
[CmdletBinding()]
param(
    [string]$Keystore,
    [string]$StorePassword,
    [string]$KeyAlias,
    [string]$KeyPassword,
    [string]$VersionName,
    [string]$VersionCode,
    [string]$EnvFile = ".env",
    [string]$KeysFile = "keys/release.properties",   # same KEY=VALUE format; written next to the keystore (gitignored)
    [switch]$Bundle   # build an .aab instead of an .apk
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

# Load keys/release.properties, then .env, into the process environment so Gradle and the defaults below see it. The file overrides any already-set
# env var; explicit -Keystore etc. still win (applied after this). Lines: KEY=VALUE, '#' comments and blanks skipped;
# surrounding quotes trimmed.
foreach ($file in @($KeysFile, $EnvFile)) {
    if (-not (Test-Path -LiteralPath $file)) { continue }
    foreach ($line in Get-Content -LiteralPath $file) {
        $t = $line.Trim()
        if (-not $t -or $t.StartsWith("#")) { continue }
        $eq = $t.IndexOf("=")
        if ($eq -lt 1) { continue }
        $name = $t.Substring(0, $eq).Trim()
        $value = $t.Substring($eq + 1).Trim().Trim('"', "'")
        Set-Item -Path "Env:$name" -Value $value
    }
}

# Fall back to the environment (now including .env) when a param was not passed.
if (-not $Keystore)      { $Keystore      = $env:STASHLING_KEYSTORE_FILE }
if (-not $StorePassword) { $StorePassword = $env:STASHLING_KEYSTORE_PASSWORD }
if (-not $KeyAlias)      { $KeyAlias      = $env:STASHLING_KEY_ALIAS }
if (-not $KeyPassword)   { $KeyPassword   = $env:STASHLING_KEY_PASSWORD }

# The build stays unsigned unless all four are set; fail early instead of shipping an unsigned artifact.
if (-not $KeyPassword) { $KeyPassword = $StorePassword }
$missing = @()
if (-not $Keystore)      { $missing += "keystore file (-Keystore / STASHLING_KEYSTORE_FILE)" }
if (-not $StorePassword) { $missing += "store password (-StorePassword / STASHLING_KEYSTORE_PASSWORD)" }
if (-not $KeyAlias)      { $missing += "key alias (-KeyAlias / STASHLING_KEY_ALIAS)" }
if (-not $KeyPassword)   { $missing += "key password (-KeyPassword / STASHLING_KEY_PASSWORD)" }
if ($missing.Count -gt 0) { throw "Missing signing input(s): $($missing -join ', ')" }

$keystorePath = (Resolve-Path -LiteralPath $Keystore).Path   # gradle file() is module-relative; pass an absolute path

$env:STASHLING_KEYSTORE_FILE     = $keystorePath
$env:STASHLING_KEYSTORE_PASSWORD = $StorePassword
$env:STASHLING_KEY_ALIAS         = $KeyAlias
$env:STASHLING_KEY_PASSWORD      = $KeyPassword
if ($VersionName) { $env:STASHLING_VERSION_NAME = $VersionName }
if ($VersionCode) { $env:STASHLING_VERSION_CODE = $VersionCode }

$task = if ($Bundle) { ":app:bundleRelease" } else { ":app:assembleRelease" }
# assembleRelease does not run unit tests, but exclude them explicitly so -Bundle or future task graphs stay test-free.
& .\gradlew.bat $task -x test -x lint
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed ($LASTEXITCODE)." }

$outDir = if ($Bundle) { "app\build\outputs\bundle\release" } else { "app\build\outputs\apk\release" }
$pattern = if ($Bundle) { "*.aab" } else { "*.apk" }
Get-ChildItem -Path $outDir -Filter $pattern -ErrorAction SilentlyContinue |
    ForEach-Object { Write-Host "Built: $($_.FullName)" -ForegroundColor Green }
