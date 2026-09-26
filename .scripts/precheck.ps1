# =============================================================================
# Polaris local pre-check script (mirrors GitHub Actions build.yml)
#
# Usage (from repo root):  pwsh -File .scripts/precheck.ps1
#
# Purpose: run every check that CI runs remotely, BEFORE pushing, so we never
# ship "passes locally but fails CI" again. Add one check here for every new
# class of CI failure that ever happens.
# =============================================================================

$ErrorActionPreference = "Continue"

# 0. Environment -- use JAVA_HOME / ANDROID_HOME if already set; otherwise probe
#    the usual locations. Never hard-code a personal machine path in this script
#    (see docs/writing-guide.md 2.6).
if (-not $env:JAVA_HOME) {
    $jdkRoots = @($env:ProgramFiles, ${env:ProgramFiles(x86)})
    if ($env:LOCALAPPDATA) { $jdkRoots += (Join-Path $env:LOCALAPPDATA 'Programs') }
    $jdkRoots = $jdkRoots | Where-Object { $_ -and (Test-Path $_) }
    foreach ($root in $jdkRoots) {
        $hit = Get-ChildItem -Path (Join-Path $root 'Eclipse Adoptium') -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending | Select-Object -First 1
        if ($hit) { $env:JAVA_HOME = $hit.FullName; break }
    }
}
if (-not $env:ANDROID_HOME -and $env:LOCALAPPDATA) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (-not $env:ANDROID_SDK_ROOT -and $env:ANDROID_HOME) { $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME }
if ($env:JAVA_HOME) { $env:PATH = "$env:JAVA_HOME\bin;$env:PATH" }

if (-not $env:JAVA_HOME) {
    Write-Host "WARN: JDK not found; install JDK 17 or set JAVA_HOME" -ForegroundColor Yellow
} elseif (-not (Test-Path $env:JAVA_HOME)) {
    Write-Host "WARN: JAVA_HOME points to a missing path: $env:JAVA_HOME" -ForegroundColor Yellow
}
if (-not $env:ANDROID_HOME) {
    Write-Host "WARN: Android SDK not found; set ANDROID_HOME" -ForegroundColor Yellow
} elseif (-not (Test-Path $env:ANDROID_HOME)) {
    Write-Host "WARN: ANDROID_HOME points to a missing path: $env:ANDROID_HOME" -ForegroundColor Yellow
}

$kp = Join-Path $env:TEMP 'polaris-throwaway.keystore'
$env:POLARIS_RELEASE_STORE_FILE = $kp
$env:POLARIS_RELEASE_STORE_PASSWORD = 'build-throwaway'
$env:POLARIS_RELEASE_KEY_ALIAS = 'build-throwaway'
$env:POLARIS_RELEASE_KEY_PASSWORD = 'build-throwaway'
$env:POLARIS_USE_MIRROR = 'true'

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$failures = New-Object System.Collections.Generic.List[string]

Write-Host "=== Polaris pre-check @ $(Get-Date -Format 'yyyy-MM-dd HH:mm') ===" -ForegroundColor Cyan

# 0.5 throwaway keystore (CI static-check step generates it too)
Write-Host "`n[0/7] generate throwaway keystore ..." -ForegroundColor Cyan
Remove-Item $kp -ErrorAction SilentlyContinue
$null = & keytool -genkeypair -keystore $kp -alias build-throwaway -keyalg RSA -keysize 2048 -validity 1 -storepass build-throwaway -keypass build-throwaway -dname "CN=CI-Throwaway, OU=CI, O=CI, C=CN" 2>$null
if ($LASTEXITCODE -ne 0) { $failures.Add("keystore gen failed") }

# 1. Unit tests (history: Dispatchers.IO leak / missing stub mock / ctor params)
Write-Host "`n[1/7] testDebugUnitTest ..." -ForegroundColor Cyan
$out = & .\gradlew.bat :app:testDebugUnitTest --no-daemon --warning-mode none 2>&1
if ($LASTEXITCODE -ne 0) {
    $failures.Add("testDebugUnitTest")
    $out | Select-String 'FAILED|BUILD FAILED|UncaughtExceptions|Caused by' | Select-Object -First 8 | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
} else {
    $out | Select-String 'BUILD SUCCESSFUL' | Select-Object -First 1 | ForEach-Object { Write-Host "  $_" -ForegroundColor Green }
}

# 2. ktlint (all source sets; history: import order LoginViewModel)
Write-Host "`n[2/7] ktlintCheck ..." -ForegroundColor Cyan
$out = & .\gradlew.bat :app:ktlintCheck --no-daemon --warning-mode none 2>&1
if ($LASTEXITCODE -ne 0) {
    $failures.Add("ktlintCheck")
    $out | Select-String 'FAILED|\.kt:' | Select-Object -First 8 | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
} else {
    Write-Host "  PASSED" -ForegroundColor Green
}

# 3. Android Lint (debug)
Write-Host "`n[3/7] lintDebug ..." -ForegroundColor Cyan
$out = & .\gradlew.bat :app:lintDebug --no-daemon --warning-mode none 2>&1
if ($LASTEXITCODE -ne 0) {
    $failures.Add("lintDebug")
    $out | Select-String 'FAILED|Lint found|Error:' | Select-Object -First 8 | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
} else {
    Write-Host "  PASSED" -ForegroundColor Green
}

# 4. R8 keep rules (verifyReleaseApiSurvivors)
Write-Host "`n[4/7] verifyReleaseApiSurvivors ..." -ForegroundColor Cyan
$out = & .\gradlew.bat :app:verifyReleaseApiSurvivors --no-daemon --warning-mode none 2>&1
if ($LASTEXITCODE -ne 0) {
    $failures.Add("verifyReleaseApiSurvivors")
    $out | Select-String 'FAILED' | Select-Object -First 5 | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
} else {
    Write-Host "  PASSED" -ForegroundColor Green
}

# 5. Bare Icons check (CI regex: capital Icons. ; import lines use lowercase icons. so they do NOT match)
Write-Host "`n[5/7] bare Icons.* reference check ..." -ForegroundColor Cyan
$files = Get-ChildItem -Path 'app\src\main\java' -Recurse -Include *.kt | Where-Object { $_.Name -ne 'SlteIcons.kt' }
$bare = @()
foreach ($f in $files) {
    $m = Select-String -Path $f.FullName -CaseSensitive -Pattern 'Icons\.(AutoMirrored\.)?(Outlined|Rounded|Filled|Sharp|TwoTone)\.'
    if ($m) { $bare += $m }
}
if ($bare.Count -gt 0) {
    $failures.Add("bare Icons.* x$($bare.Count)")
    $bare | ForEach-Object { Write-Host "  $($_.Path):$($_.LineNumber) -- $($_.Line.Trim())" -ForegroundColor Red }
} else {
    Write-Host "  PASSED ($($files.Count) files)" -ForegroundColor Green
}

# 6. String locale key parity (history: missed login_backend_auto_hint in zh-Hant)
Write-Host "`n[6/7] string locale key parity ..." -ForegroundColor Cyan
function Get-StringKeys($path) {
    [regex]::Matches((Get-Content $path -Raw), '<string name="([^"]+)"') |
        ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
}
$base = Get-StringKeys 'app\src\main\res\values\strings.xml'
$en   = Get-StringKeys 'app\src\main\res\values-en\strings.xml'
$hant = Get-StringKeys 'app\src\main\res\values-b+zh+Hant\strings.xml'
$missingEn = @(Compare-Object $base $en | Where-Object SideIndicator -eq '<=').Count
$missingHant = @(Compare-Object $base $hant | Where-Object SideIndicator -eq '<=').Count
$extraEn = @(Compare-Object $base $en | Where-Object SideIndicator -eq '=>').Count
$extraHant = @(Compare-Object $base $hant | Where-Object SideIndicator -eq '=>').Count
if ($missingEn -gt 0 -or $missingHant -gt 0 -or $extraEn -gt 0 -or $extraHant -gt 0) {
    $failures.Add("locale parity (en miss $missingEn / extra $extraEn, hant miss $missingHant / extra $extraHant)")
} else {
    Write-Host "  PASSED ($($base.Count) keys x 3 locales)" -ForegroundColor Green
}

# 7. Release compile (compile errors only surface in CI, e.g. LoggInApp typo)
Write-Host "`n[7/7] compileReleaseKotlin ..." -ForegroundColor Cyan
$out = & .\gradlew.bat :app:compileReleaseKotlin --no-daemon --warning-mode none 2>&1
if ($LASTEXITCODE -ne 0) {
    $failures.Add("compileReleaseKotlin")
    $out | Select-String 'e: file' | Select-Object -First 8 | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
} else {
    Write-Host "  PASSED" -ForegroundColor Green
}

# Summary
Write-Host "`n=== pre-check result ===" -ForegroundColor Cyan
if ($failures.Count -gt 0) {
    Write-Host "FAILED ($($failures.Count)):" -ForegroundColor Red
    $failures | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
    exit 1
} else {
    Write-Host "ALL 7 PASSED" -ForegroundColor Green
    exit 0
}
