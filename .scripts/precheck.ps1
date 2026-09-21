# =============================================================================
# Polaris 本地预检脚本（对齐 GitHub Actions CI 的 build.yml）
#
# 用法：在仓库根目录执行
#   pwsh -File .scripts\precheck.ps1
#
# 目的：把 CI 会在远端执行的每一项检查在本地先跑一遍，避免「本地通过、CI 失败」。
# 每新增一类 CI 失败，就往这里补一项检查。
# =============================================================================

$ErrorActionPreference = "Stop"

# ---------------------------------------------------------------------------
# 0. 环境准备（与 CI 一致）
# ---------------------------------------------------------------------------
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

Write-Host "=== Polaris 预检 @ $(Get-Date -Format 'yyyy-MM-dd HH:mm') ===" -ForegroundColor Cyan

# ---------------------------------------------------------------------------
# 0.5 生成 throwaway keystore（CI 静态检查步骤也会生成）
# ---------------------------------------------------------------------------
Write-Host "`n[0/7] 生成 throwaway keystore ..." -ForegroundColor Cyan
Remove-Item $kp -ErrorAction SilentlyContinue
& keytool -genkeypair -v `
    -keystore $kp `
    -alias build-throwaway -keyalg RSA -keysize 2048 -validity 1 `
    -storepass build-throwaway -keypass build-throwaway `
    -dname "CN=CI-Throwaway, OU=CI, O=CI, C=CN" 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { $failures.Add("keystore 生成失败") }

# ---------------------------------------------------------------------------
# 1. 单元测试（历史失败：Dispatchers.IO 泄漏 / stub mock 不全 / 构造函数参数缺位）
# ---------------------------------------------------------------------------
Write-Host "`n[1/7] 单元测试 testDebugUnitTest ..." -ForegroundColor Cyan
$out = & .\gradlew.bat :app:testDebugUnitTest --no-daemon --warning-mode none 2>&1
if ($LASTEXITCODE -ne 0) {
    $failures.Add("testDebugUnitTest 失败")
    $out | Select-String 'FAILED|BUILD FAILED|UncaughtExceptions|Caused by' | Select-Object -First 8 | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
} else {
    $out | Select-String 'tests completed' | ForEach-Object { Write-Host "  $_" -ForegroundColor Green }
}

# ---------------------------------------------------------------------------
# 2. ktlint（全 source set，历史失败：import 排序 LoginViewModel:3:1）
# ---------------------------------------------------------------------------
Write-Host "`n[2/7] ktlintCheck ..." -ForegroundColor Cyan
$out = & .\gradlew.bat :app:ktlintCheck --no-daemon --warning-mode none 2>&1
if ($LASTEXITCODE -ne 0) {
    $failures.Add("ktlintCheck 失败")
    $out | Select-String 'FAILED|\.kt:' | Select-Object -First 8 | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
} else {
    Write-Host "  PASSED" -ForegroundColor Green
}

# ---------------------------------------------------------------------------
# 3. Android Lint（debug variant）
# ---------------------------------------------------------------------------
Write-Host "`n[3/7] lintDebug ..." -ForegroundColor Cyan
$out = & .\gradlew.bat :app:lintDebug --no-daemon --warning-mode none 2>&1
if ($LASTEXITCODE -ne 0) {
    $failures.Add("lintDebug 失败")
    $out | Select-String 'FAILED|Lint found|Error:' | Select-Object -First 8 | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
} else {
    Write-Host "  PASSED" -ForegroundColor Green
}

# ---------------------------------------------------------------------------
# 4. R8 存活校验（verifyReleaseApiSurvivors）
# ---------------------------------------------------------------------------
Write-Host "`n[4/7] verifyReleaseApiSurvivors ..." -ForegroundColor Cyan
$out = & .\gradlew.bat :app:verifyReleaseApiSurvivors --no-daemon --warning-mode none 2>&1
if ($LASTEXITCODE -ne 0) {
    $failures.Add("verifyReleaseApiSurvivors 失败")
    $out | Select-String 'FAILED' | Select-Object -First 5 | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
} else {
    Write-Host "  PASSED" -ForegroundColor Green
}

# ---------------------------------------------------------------------------
# 5. 裸 Icons 引用检查（CI 正则：Icons. 大写，import 是小写 icons. 不命中）
# ---------------------------------------------------------------------------
Write-Host "`n[5/7] 裸 Icons.* 引用检查 ..." -ForegroundColor Cyan
$files = Get-ChildItem -Path 'app\src\main\java' -Recurse -Include *.kt | Where-Object { $_.Name -ne 'SlteIcons.kt' }
$bare = @()
foreach ($f in $files) {
    $m = Select-String -Path $f.FullName -Pattern 'Icons\.(AutoMirrored\.)?(Outlined|Rounded|Filled|Sharp|TwoTone)\.'
    if ($m) { $bare += $m }
}
if ($bare.Count -gt 0) {
    $failures.Add("裸 Icons.* 引用 $($bare.Count) 处")
    $bare | ForEach-Object { Write-Host "  $($_.Path):$($_.LineNumber) -- $($_.Line.Trim())" -ForegroundColor Red }
} else {
    Write-Host "  PASSED ($($files.Count) 文件)" -ForegroundColor Green
}

# ---------------------------------------------------------------------------
# 6. 字符串 locale 键一致性（历史失败：漏 values-b+zh+Hant 的 login_backend_auto_hint）
# ---------------------------------------------------------------------------
Write-Host "`n[6/7] 字符串 locale 键一致性 ..." -ForegroundColor Cyan
function Get-StringKeys($path) {
    [regex]::Matches((Get-Content $path -Raw), '<string name="([^"]+)"') |
        ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
}
$base = Get-StringKeys 'app\src\main\res\values\strings.xml'
$en   = Get-StringKeys 'app\src\main\res\values-en\strings.xml'
$hant = Get-StringKeys 'app\src\main\res\values-b+zh+Hant\strings.xml'
$missing = @()
$missing += (Compare-Object $base $en   | Where-Object SideIndicator -eq '<=' | ForEach-Object { "values-en 缺: $($_.InputObject)" })
$missing += (Compare-Object $base $hant | Where-Object SideIndicator -eq '<=' | ForEach-Object { "values-zh+Hant 缺: $($_.InputObject)" })
$missing += (Compare-Object $base $en   | Where-Object SideIndicator -eq '=>' | ForEach-Object { "values-en 多: $($_.InputObject)" })
$missing += (Compare-Object $base $hant | Where-Object SideIndicator -eq '=>' | ForEach-Object { "values-zh+Hant 多: $($_.InputObject)" })
if ($missing.Count -gt 0) {
    $failures.Add("locale 键不一致")
    $missing | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
} else {
    Write-Host "  PASSED（3 locale 键一致）" -ForegroundColor Green
}

# ---------------------------------------------------------------------------
# 7. release 编译（编译错误才会在 CI 暴露，如 SlteApp.kt LoggInApp 拼写错误）
# ---------------------------------------------------------------------------
Write-Host "`n[7/7] compileReleaseKotlin ..." -ForegroundColor Cyan
$out = & .\gradlew.bat :app:compileReleaseKotlin --no-daemon --warning-mode none 2>&1
if ($LASTEXITCODE -ne 0) {
    $failures.Add("compileReleaseKotlin 失败")
    $out | Select-String 'e: file' | Select-Object -First 8 | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
} else {
    Write-Host "  PASSED" -ForegroundColor Green
}

# ---------------------------------------------------------------------------
# 汇总
# ---------------------------------------------------------------------------
Write-Host "`n=== 预检结果 ===" -ForegroundColor Cyan
if ($failures.Count -gt 0) {
    Write-Host "❌ FAILED（$($failures.Count) 项）：" -ForegroundColor Red
    $failures | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
    exit 1
} else {
    Write-Host "✅ 全部通过（7 项）" -ForegroundColor Green
    exit 0
}
