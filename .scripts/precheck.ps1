# =============================================================================
# Polaris local pre-check script (mirrors GitHub Actions build.yml)
#
# Usage (from repo root):  pwsh -File .scripts/precheck.ps1
#
# Purpose: run every check that CI runs remotely, BEFORE pushing, so we never
# ship "passes locally but fails CI" again. Add one check here for every new
# class of CI failure that ever happens.
# =============================================================================

$ErrorActionPreference = "Stop"

# 0. Environment (same as CI)
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$env:ANDROID_HOME = "C:\Android\Sdk"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

$kp = Join-Path $env:TEMP 'polaris-throwaway.keystore'
$env:POLARIS_RELEASE_STORE_FILE = $kp
$env:POLARIS_RELEASE_STORE_PASSWORD = 'build-throwaway'
$env:POLARIS_RELEASE_KEY_ALIAS = 'build-throwaway'
$env:POLARIS_RELEASE_KEY_PASSWORD = 'build-throwaway'
$env:POLARIS_USE_MIRROR = 'false'

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$failures = New-Object System.Collections.Generic.List[string]

Write-Host "=== Polaris pre-check @ $(Get-Date -Format 'yyyy-MM-dd HH:mm') ===" -ForegroundColor Cyan

# 0.5 throwaway keystore (CI static-check step generates it too)
Write-Host "`n[0/7] generate throwaway keystore ..." -ForegroundColor Cyan
Remove-Item $kp -ErrorAction SilentlyContinue
& keytool -genkeypair -keystore $kp -alias build-throwaway -keyalg RSA -keysize 2048 -validity 1 -storepass build-throwaway -keypass build-throwaway -dname "CN=CI-Throwaway, OU=CI, O=CI, C=CN" 2>&1 | Out-Null
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
