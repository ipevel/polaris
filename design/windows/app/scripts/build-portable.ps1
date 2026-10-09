# 打包 Windows 便携版。
#
#   powershell -ExecutionPolicy Bypass -File scripts/build-portable.ps1
#
# 产物：dist\Polaris-portable-<version>.zip
# 解压后目录即为成品，可直接拷到任意 Win10+ 机器运行（无需安装任何运行时）。

$ErrorActionPreference = 'Stop'
$app = Split-Path -Parent $PSScriptRoot
Set-Location $app

# 构建期需要的镜像/代理。国内网络下 Electron 与 electron-builder 的二进制
# 直连 GitHub 基本会超时，这里统一走 npmmirror；有本地代理的话填在下面。
if (-not $env:HTTP_PROXY)  { $env:HTTP_PROXY  = 'http://127.0.0.1:7890' }
if (-not $env:HTTPS_PROXY) { $env:HTTPS_PROXY = 'http://127.0.0.1:7890' }
$env:ELECTRON_MIRROR = 'https://npmmirror.com/mirrors/electron/'
$env:ELECTRON_BUILDER_BINARIES_MIRROR = 'https://npmmirror.com/mirrors/electron-builder-binaries/'

if (-not (Test-Path 'core\mihomo.exe')) {
  throw "缺少 core\mihomo.exe。先跑 scripts\fetch-core.py 拉内核（见 README）。"
}

Write-Host '== 核心层自检 ==' -ForegroundColor Cyan
node scripts\selftest-core.js
if ($LASTEXITCODE -ne 0) { throw '核心层自检失败，已中止打包' }

Write-Host '== 打包 ==' -ForegroundColor Cyan
npx electron-builder --win --x64
if ($LASTEXITCODE -ne 0) { throw '打包失败' }

$ver = (Get-Content package.json -Raw | ConvertFrom-Json).version
$zip = "dist\Polaris-portable-$ver.zip"
Write-Host ''
Write-Host "产物：$zip" -ForegroundColor Green
Write-Host ("大小：{0:N1} MB" -f ((Get-Item $zip).Length / 1MB))