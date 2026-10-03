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

Polaris 是一款基于 [mihomo](https://github.com/MetaCubeX/mihomo/tree/Alpha) 内核构建的 Android 代理客户端。

- **面板**：内置 XiaoV2b / Xboard 双适配器，登录时按 `guest/comm/config` 自动识别，无需换包；面板地址完全由用户在登录页填写（自动补全 `https://`，仅接受 HTTPS），App 不内置任何地址。
- **首页**：代理模式切换（规则 / 全局 / 直连）、连接开关（起连即计时）、当前节点、实时速率、会话信息（当前 IP / 内网 IP / 本次用量 / 内存）、套餐用量与到期。
- **导航**：首页 / 节点 / 流量 / 我的 四栏，页面预加载、切换流畅。
- **节点页**：两层结构 ——「🚀 节点选择」卡直接列出主组全部成员（自动选择 / 故障转移 / 直连 / 具体节点），可折叠且收起后仍显示当前出口；下方「分流规则组」每条独立指定出口（跟随 / 自动 / 故障转移 / 直连 / 拦截 / 具体节点）。右上角「测速」「更新订阅」。
- **流量页**：已用 / 上行 / 下行 / 统计天数概览、趋势图与按日明细。
- **我的**：公告、邮箱与余额、套餐续费、订单、礼品卡兑换、邀请返利、工单、Telegram 讨论组。
- **设置**：外观（跟随系统 / 浅色 / 深色）、语言（简中 / 繁中 / English）、TUN 堆栈、修改密码、到期与流量提醒、关于（版本 / 日志导出）。
- **安全**：凭据只发往白名单域名防配置投毒；日志路径型 token 统一打码。

## 下载与安装

1. 从 [Releases](https://github.com/ipevel/polaris/releases) 下载 `Polaris-<版本号>.apk`（arm64-v8a）。
2. **校验完整性**：比对下载页的 `SHA256SUMS.txt`（Windows `certutil -hashfile <APK> SHA256`，Linux/macOS `sha256sum <APK>`），不一致请勿安装。
3. 允许「安装未知应用」后安装（需 Android 8.0 / API 28+）。
4. 首次启动在登录页填写**你自己的面板网址**。

> 仅通过 GitHub Releases 分发。本应用不提供应用内更新，升级请从 Releases 下载新版本覆盖安装（数据保留）；其他渠道的安装包不保证来源与完整性。

## 上游仓库

基础代码来自 **[shgnx/slte](https://github.com/shgnx/slte)**，在此致谢。本仓库在其基础上定制（品牌、面板适配、功能增强）并独立发布。

## 当前版本

| 平台 | 版本 | Version Code |
|------|------|-------------|
| Android | 1.5.17 | 50 |

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
> **内核直连兜底**：内核侧补丁链（`kernel-core/src/main/golang/native/config/process.go`）含独立直连域名占位，自持域名需同步，并在修改后重新交叉编译 `libclash.so`（`GOOS=linux GOARCH=arm64 go build -tags "android cmfa with_gvisor" ./native/config/`，无需 NDK）。

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
