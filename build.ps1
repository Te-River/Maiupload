# Build script (PowerShell)
# Usage:
#   powershell -ExecutionPolicy Bypass -File D:\Github\MaiproberPlus-New\build.ps1           # 默认 snapshot（CI/in-app updater）
#   powershell -ExecutionPolicy Bypass -File D:\Github\MaiproberPlus-New\build.ps1 -rel      # 开发者显式构建 Release
#   powershell -ExecutionPolicy Bypass -File D:\Github\MaiproberPlus-New\build.ps1 debug
#   powershell -ExecutionPolicy Bypass -File D:\Github\MaiproberPlus-New\build.ps1 snapshot
#   or in PowerShell:
#     & D:\Github\MaiproberPlus-New\build.ps1 -rel
#
# Override via env vars: JAVA_HOME / ANDROID_HOME
#
# LXNS OAuth (PKCE, public client — no client_secret):
#   client_id is hardcoded in android/app/build.gradle.kts -> BuildConfig.
#
# Signing: uses android/maiupload-release.keystore (shared release cert).
#   Keystore + passwords are committed to the repo so all builders produce
#   identically-signed APKs. Override via env vars if needed.

# --- 参数声明（必须在脚本身最前面，PowerShell 规定）---
# 用法：
#   build.ps1            → 默认 snapshot
#   build.ps1 -rel       → 显式构建 Release
#   build.ps1 debug      → debug
#   build.ps1 snapshot   → snapshot
param(
    [switch]$rel,
    [Parameter(Position = 0)][string]$Target
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$AndroidDir = Join-Path $ScriptDir "android"
$ReleaseDir = Join-Path $ScriptDir "release"

# --- Signing material (shared, committed) ---
$KeyStore  = Join-Path $AndroidDir "maiupload-release.keystore"
$KeyAlias  = "maiupload-release"
$StorePass = "Maiupload2026!Release"
$KeyPass   = "Maiupload2026!Release"

# Allow env-var override for advanced users
$ksPath = if ($env:LOCAL_KEYSTORE_PATH) { $env:LOCAL_KEYSTORE_PATH } else { $KeyStore }
$ksAlias = if ($env:LOCAL_KEYSTORE_ALIAS) { $env:LOCAL_KEYSTORE_ALIAS } else { $KeyAlias }
$sp = if ($env:LOCAL_STORE_PASSWORD) { $env:LOCAL_STORE_PASSWORD } else { $StorePass }
$kp = if ($env:LOCAL_KEY_PASSWORD) { $env:LOCAL_KEY_PASSWORD } else { $KeyPass }

# --- Build target ---
# -rel 开关（param 声明）：显式构建 Release（开发者正式发版用）
# 不带 -rel 时：位置参数指定 debug/snapshot；无参数默认 snapshot（CI/in-app updater）
if ($rel) {
    $Target = "release"
} elseif ([string]::IsNullOrEmpty($Target)) {
    $Target = "snapshot"
}

switch ($Target) {
    "debug"   { $GradleTask = "assembleDebug" }
    "release" { $GradleTask = "assembleRelease" }
    "snapshot"{ $GradleTask = "assembleSnapshot" }
    default {
        Write-Host "[ERROR] Unknown build target: $Target" -ForegroundColor Red
        Write-Host "  Usage: build.ps1 [debug|snapshot] [-rel]" -ForegroundColor Red
        Write-Host "         build.ps1 -rel               (显式构建 Release)" -ForegroundColor Red
        exit 1
    }
}
$ApkSubdir = switch ($Target) {
    "debug"   { "debug" }
    "release" { "release" }
    "snapshot"{ "snapshot" }
}

# --- Env checks: JAVA_HOME ---
# Force jdk-21 if present locally, regardless of inherited $env:JAVA_HOME (which may point to jdk-25).
if (Test-Path "D:\Java\jdk-21\bin\java.exe") {
    $jh = "D:\Java\jdk-21"
} elseif ($env:JAVA_HOME) {
    $jh = $env:JAVA_HOME
} else {
    $jh = ""
}
if (-not $jh -or -not (Test-Path (Join-Path $jh "bin\java.exe"))) {
    Write-Host "[ERROR] JAVA_HOME not set or invalid. Install JDK 21 (https://adoptium.net/) and set `$env:JAVA_HOME, or install to D:\Java\jdk-21." -ForegroundColor Red
    exit 1
}
$javaExe = Join-Path $jh "bin\java.exe"
# Java writes -version to stderr by convention; temporarily relax ErrorActionPreference so it isn't treated as fatal.
$prevEAP = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$jhOut = (& $javaExe -version 2>&1 | ForEach-Object { $_ }) -join "`n"
$ErrorActionPreference = $prevEAP
if ($jhOut -notmatch 'version "21') {
    Write-Host "[ERROR] JDK at JAVA_HOME is not version 21: $jh" -ForegroundColor Red
    Write-Host $jhOut
    exit 1
}
$env:JAVA_HOME = $jh
$env:PATH = "$jh\bin;" + $env:PATH

# --- Env checks: ANDROID_HOME ---
$ah = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif (Test-Path "$env:LOCALAPPDATA\Android\Sdk") { "$env:LOCALAPPDATA\Android\Sdk" } else { "" }
if (-not $ah -or -not (Test-Path (Join-Path $ah "platforms"))) {
    Write-Host "[ERROR] ANDROID_HOME not set or invalid (missing platforms/). Set `$env:ANDROID_HOME to your Android SDK path." -ForegroundColor Red
    exit 1
}
$env:ANDROID_HOME = $ah

# --- Verify keystore exists ---
if (-not (Test-Path $ksPath)) {
    Write-Host "[ERROR] Release keystore not found: $ksPath" -ForegroundColor Red
    Write-Host "  Expected android/maiupload-release.keystore (committed to repo)." -ForegroundColor Red
    exit 1
}

$env:LOCAL_KEYSTORE_PATH    = $ksPath
$env:LOCAL_KEYSTORE_ALIAS   = $ksAlias
$env:LOCAL_STORE_PASSWORD   = $sp
$env:LOCAL_KEY_PASSWORD     = $kp

Write-Host ("[INFO] Keystore: {0}" -f $ksPath) -ForegroundColor Cyan
Write-Host ("[INFO] Alias:    {0}" -f $ksAlias) -ForegroundColor Cyan

# --- Build ---
Write-Host "[BUILD] Starting $GradleTask ..." -ForegroundColor Green
Push-Location $AndroidDir
try {
    & .\gradlew.bat $GradleTask --console=plain
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] gradlew $GradleTask failed (exit $LASTEXITCODE)" -ForegroundColor Red
        exit 1
    }
} finally {
    Pop-Location
}

# --- Collect artifacts ---
$ApkDir = Join-Path $AndroidDir "app\build\outputs\apk\$ApkSubdir"
if (-not (Test-Path $ApkDir)) {
    Write-Host "[ERROR] Build succeeded but APK output dir not found: $ApkDir" -ForegroundColor Red
    exit 1
}
if (-not (Test-Path $ReleaseDir)) { New-Item -ItemType Directory -Path $ReleaseDir | Out-Null }
Get-ChildItem -Path $ReleaseDir -Filter *.apk -ErrorAction SilentlyContinue | Remove-Item -Force
Remove-Item -Path (Join-Path $ReleaseDir "output-metadata.json") -Force -ErrorAction SilentlyContinue

$apks = @(Get-ChildItem -Path $ApkDir -Filter *.apk)
if ($apks.Count -eq 0) {
    Write-Host "[ERROR] No .apk files under: $ApkDir" -ForegroundColor Red
    exit 1
}
foreach ($apk in $apks) {
    Copy-Item -Path $apk.FullName -Destination $ReleaseDir -Force
    Write-Host "[OK] Copied to release\: $($apk.Name)" -ForegroundColor Green
}
$meta = Join-Path $ApkDir "output-metadata.json"
if (Test-Path $meta) { Copy-Item -Path $meta -Destination $ReleaseDir -Force }

Write-Host "[DONE] Build complete. Artifacts in: $ReleaseDir" -ForegroundColor Green
Get-ChildItem -Path $ReleaseDir -Filter *.apk | Format-Table Name, Length
