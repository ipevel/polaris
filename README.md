<div align="center">

# Polaris

[![许可证](https://img.shields.io/badge/许可证-GPL--3.0-blue?style=flat-square)](LICENSE)
[![Android](https://img.shields.io/badge/Android-28--36-green?style=flat-square)](README.md)
[![内核](https://img.shields.io/badge/内核-mihomo-9cf?style=flat-square)](https://github.com/MetaCubeX/mihomo)
[![CI](https://github.com/ipevel/polaris/actions/workflows/ci.yml/badge.svg)](https://github.com/ipevel/polaris/actions/workflows/ci.yml)

**基于 mihomo 内核的轻量级代理客户端，支持 XiaoV2b / Xboard 面板**

</div>

---

## 简介

Polaris 是一款基于 [mihomo](https://github.com/MetaCubeX/mihomo/tree/Alpha) 内核的代理客户端，支持 XiaoV2b / Xboard 面板，登录时自动识别，无需换包。Android 为主客户端；另附 **Windows 便携版**（源码在 `windows-portable` 分支的 `design/windows/app/`，免安装、解压即用、共用同一套面板账号与分流方案）。

- **界面**：iOS 极简风，深色 / 浅色 / 跟随系统，简中 / 繁中 / English
- **功能**：一键连接与实时速率、节点与分流、流量统计、套餐与订单、应用内更新
- **保活**：VPN 服务跑在独立进程，连接前引导关闭电池优化
- **安全**：面板地址不内置、仅接受 HTTPS，凭据仅发往白名单域名
- **Windows 便携版**：单个 zip 解压即用，不写注册表、不落 `%APPDATA%`；内置内核与规则库，带系统代理与 TUN 模式

## 下载与安装

### Android

1. 从 [Releases](https://github.com/ipevel/polaris/releases) 下载 `Polaris-<版本号>.apk`（arm64-v8a）。
2. **校验完整性**：比对下载页的 `SHA256SUMS.txt`（Windows `certutil -hashfile <APK> SHA256`，Linux/macOS `sha256sum <APK>`），不一致请勿安装。
3. 允许「安装未知应用」后安装（需 Android 8.0 / API 28+）。
4. 首次启动在登录页填写**你自己的面板网址**。

> 仅通过 GitHub Releases 分发。App 内「我的 → 关于软件 → 检查更新」可直接下载安装新版本（覆盖安装，数据保留）；也可以从 Releases 手动下载 APK 覆盖安装。其他渠道的安装包不保证来源与完整性。

### Windows

1. 从同一个 [Releases](https://github.com/ipevel/polaris/releases) 下载 `Polaris-portable-<版本号>.zip`（Windows 10/11 x64）。
2. **解压到任意目录**（推荐非系统盘、路径不含空格），双击 `Polaris.exe` 即可，无需安装、无需管理员权限。
3. 目录结构：`Polaris.exe` + `core/`（mihomo 内核 + wintun）+ `resources/geo/`、`resources/rules/`（内置地理库与 47 个分流规则集）+ `data/`（配置、订阅、日志，首次运行自动创建）。
4. TUN 模式需要管理员权限（设置页里点开关会引导重启为管理员）；仅用系统代理时不需要。
5. 卸载 = 直接删掉整个目录；`data/` 是唯一会产生写入的地方。

> 包内**不含任何面板地址与账号**，首次启动在登录页填自己的面板网址。源码与开发/打包细节见 `windows-portable` 分支的 `design/windows/app/README.md`、`design/windows/app/docs/DEVNOTES.md`。

## 上游仓库

基础代码来自 **[shgnx/slte](https://github.com/shgnx/slte)**，在此致谢。本仓库在其基础上定制（品牌、面板适配、功能增强）并独立发布。

## 当前版本

| 平台 | 版本 | Version Code |
|------|------|-------------|
| Android | 1.9.0 | 29861169 |
| Windows 便携版 | 1.9.0 | — |

> 两个平台共用同一版本号与同一个 [Release](https://github.com/ipevel/polaris/releases/latest)（Android 为 `Polaris-<版本>.apk`，Windows 为 `Polaris-portable-<版本>.zip`）。

> 内核：metacubex/mihomo v1.19.30（含 anytls / masque / openvpn / tailscale / zerotier 等本地 outbound 补丁）。版本以 [Releases](https://github.com/ipevel/polaris/releases) 为准；版本号规则、发布流程与自动门禁见 [VERSIONING.md](VERSIONING.md)。

## 开发环境

| 依赖 | 版本 |
|------|------|
| JDK | 17（`sourceCompatibility` / `jvmTarget` 均为 17，CI 同） |
| Android SDK | compileSdk 36 / targetSdk 36 / minSdk 28 |
| NDK | 28.2.13676358 |
| CMake | 3.22.1+（见 `kernel-core/src/main/cpp/CMakeLists.txt`） |
| Gradle | 8.13（wrapper 内置） |
| Android Gradle Plugin | 8.9.1 |
| Kotlin | 2.0.21 |
| Compose BOM | 2025.04.01 |

> 内核已预编译为 `libclash.so` 随仓库提供，普通编译无需 Go 环境；仅改 Go 补丁链或升级内核时才需要。

## 编译

```bash
# 调试包（不触达 release 变体，无需签名）
./gradlew :app:testDebugUnitTest :app:assembleDebug

# 会分析 release 变体的任务（ktlintCheck / lintDebug / R8 等）必须先提供签名四件套，
# 否则 Gradle 拒绝执行——防「用 debug 签名发版」
export POLARIS_RELEASE_STORE_FILE=<keystore>
export POLARIS_RELEASE_STORE_PASSWORD=<密码>
export POLARIS_RELEASE_KEY_ALIAS=<别名>
export POLARIS_RELEASE_KEY_PASSWORD=<密码>

# 本地全量门禁（与 CI 对齐，推送前必跑；本地可用一次性 keystore）
./gradlew :app:verifyKernelBinary :app:assembleDebug :app:testDebugUnitTest \
  :app:assembleDebugAndroidTest :app:minifyReleaseWithR8 \
  :app:verifyReleaseApiSurvivors :app:ktlintCheck :app:lintDebug

# 发布包
./gradlew :app:assembleRelease
```

> **推送即出包**：每次推送到 main，CI 会把调试包作为 artifact 上传（Actions → run → Artifacts，保留 7 天）。它是 **debug 签名**，无法覆盖已装的正式版；正式分发走 [Releases](https://github.com/ipevel/polaris/releases)（手动触发 `Polaris Build`，发布前须跑完本地门禁，见 [CONTRIBUTING.md](CONTRIBUTING.md)）。

### Windows 便携版

源码在 `windows-portable` 分支的 `design/windows/app/`（Electron 工程，与 Android 侧构建互不影响）。两条门禁都在该目录下跑，**都必须真跑**（真面板、真账号、真节点）：

```bash
cd design/windows/app

# 数据层门禁：真面板登录 → 拉订阅 → 起内核 → 经代理访问外网 → 面板业务接口
node_modules/electron/dist/electron.exe scripts/real-test.js \
  --panel=<你的面板地址> --email=<测试账号> --password=<口令>

# 界面层门禁：开真窗口真点击走完所有页面（用独立 data 目录，不动日常那份）
node_modules/electron/dist/electron.exe . --uitest \
  --panel=<面板地址> --email=<测试账号> --password=<口令> --uitest-data=<临时目录>

# 打包（出 dist/Polaris-portable-<版本>.zip；打包前两条门禁必须全绿）
node node_modules/electron-builder/cli.js --win --x64
```

> 跑 Electron 前若环境里设了 `ELECTRON_RUN_AS_NODE`（某些 IDE / agent 会设），必须先清掉，否则 Electron 退化成纯 Node。
> Windows 端的版本号唯一来源是 `design/windows/app/package.json` 的 `version`，发布时必须与 Android 的 `versionName` 一致（同一个 Release、同一个 tag）。
> 内核与地理库/规则库已随仓库与成品包分发；仅在升级内核或改内核补丁链时才需要重跑 `scripts/fetch-core.py` / `scripts/fetch-geo.py`。

## 配置

### 面板地址（运行时）

**App 不内置任何面板地址。** 用户在登录页填写自己的面板网址（自动补全 `https://`，仅接受可解析的 HTTPS 地址），此后所有 API 请求基于该地址；未填写时网络层快速失败。更换面板 = 退出登录后填新地址。

### 编译期变量

通过环境变量或 `app/gradle.properties` 注入（默认值为占位符，纯编译与单测无需真实值）：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `POLARIS_APP_NAME` | `Polaris` | 应用显示名 |
| `POLARIS_APPLICATION_ID` | `com.polaris.app` | 包名 |
| `POLARIS_VERSION_NAME` | `1.5.15` | 版本名（Release 由 build.yml 传入） |
| `POLARIS_VERSION_CODE` | `48` | versionCode |
| `POLARIS_API_BASE_URL` | `https://api.example.com` | 仅作 Retrofit 构造占位，运行时不使用；并入域名白名单 |
| `POLARIS_API_TYPE` | `xiaov2b` | 遗留变量：仍写入 `BuildConfig.API_TYPE`，运行时不再读取（后端类型登录时自动探测） |
| `POLARIS_SUBSCRIBE_PATH` | `/api/v1/client/subscribe` | 订阅路径，面板地址 + 该路径 + token 组成订阅源 |
| `POLARIS_REMOTE_CONFIG_URLS` | 见 [config/remote.json](config/remote.json) | 远程配置 URL，逗号分隔多源 |
| `POLARIS_ALLOWED_DOMAINS` | （空） | 追加域名白名单，逗号分隔 |
| `POLARIS_TELEGRAM_GROUP_URL` | （空） | Telegram 兜底链接，面板未下发且此处留空时隐藏入口 |

> 发版签名走 `POLARIS_RELEASE_STORE_*` 四件套（见[编译](#编译)）。

> **安全白名单**：API 地址与远程配置中的直连域名只允许在白名单内切换（白名单 = `POLARIS_ALLOWED_DOMAINS` + API 地址域名 + 远程配置源域名，构建期自动并入，详见 [CONFIG.md](CONFIG.md)）。仓库内置占位符 `example.com`，部署前请注入你的域名。
>
> **内核直连兜底**：内核侧补丁链（`kernel-core/src/main/golang/native/config/process.go`）含独立直连域名占位，自持域名需同步，并在修改后重建 `libclash.so`。交叉编译命令以 `kernel-core/src/main/jniLibs/arm64-v8a/VERSION.md` 的构建记录为准，形如
> `GOOS=android GOARCH=arm64 CGO_ENABLED=1 CC=<NDK>/toolchains/llvm/prebuilt/windows-x86_64/bin/aarch64-linux-android28-clang.cmd go build -tags "android cmfa with_gvisor" -buildmode=c-shared -o libclash.so ./native`
> ——`GOOS` 是 `android` 不是 `linux`，目标包是 `./native` 不是 `./native/config/`，必须带 `-buildmode=c-shared`，且**需要 NDK 里的 clang 作交叉编译器**（CGO 要开）。重建后必须把新摘要写回同目录 `SHA256SUMS`，否则构建会被 `:kernel-core:verifyNativeLibraries` 拦下。

## 相关项目

- [shgnx/slte](https://github.com/shgnx/slte) - 上游项目（本仓库基础代码来源）
- [mihomo](https://github.com/MetaCubeX/mihomo) - 内核引擎（vendored 于 `kernel-core/src/foss/golang/clash/`）
- [ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid) - 内核栈（`kernel-*` 模块）派生自该项目
- [V2Board (xiaov2b)](https://github.com/wyx2685/v2board/tree/master) - 兼容的机场面板后端
- [Xboard](https://github.com/cedar2025/Xboard/tree/master) - 兼容的机场面板后端

## 社区

- [安全政策](SECURITY.md)（漏洞请走私渠道报告，勿开公开 Issue）
- [贡献指南](CONTRIBUTING.md) ｜ [说明规范](docs/writing-guide.md) ｜ [版本策略](VERSIONING.md) ｜ [行为准则](CODE_OF_CONDUCT.md) ｜ [支持与求助](SUPPORT.md)

## 许可证

以 [GPL-3.0](LICENSE) 协议开源，基于 [mihomo](https://github.com/MetaCubeX/mihomo/tree/Alpha) 内核构建，第三方组件声明见 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。

每个 Release 的附件中都包含 `LICENSE` 与 `THIRD-PARTY-NOTICES.md`，与二进制一并分发；应用内「关于软件 → 用户协议 / 隐私政策 / 开源许可」也可直接跳转到对应文档。

- [隐私政策](PRIVACY.md)
- [用户协议](TERMS.md)

---

<div align="center">

**© 2026 Polaris**

</div>
