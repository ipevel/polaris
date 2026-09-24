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

Polaris 是一款基于 [mihomo](https://github.com/MetaCubeX/mihomo/tree/Alpha) 内核构建的代理客户端。

### Android

- 双面板后端支持：XiaoV2b / Xboard，两套适配器同时内置，登录时按面板 `guest/comm/config` 响应自动识别类型，无需换包
- 面板地址完全由用户掌控：登录页填写自己的面板网址（自动补全 `https://`，只接受 HTTPS），App 不内置任何面板地址
- 首页仪表盘：代理模式切换（规则 / 全局 / 直连）、连接开关（点击起即计时长）与当前节点、实时上下行与速率曲线、会话信息（当前 IP / 内网 IP / 本次用量 / 内存）、套餐用量与到期
- 四栏底部导航：首页 / 节点 / 流量 / 我的，切换与页面预加载更流畅
- 节点页：策略组卡片（组内选节点，收起态直接显示当前出口与延迟），右上角「测速」一次测完全部策略组、「更新订阅」一键刷新
- 流量页：已用 / 上行 / 下行 / 统计天数概览、流量趋势图与按日明细
- 我的：公告、邮箱与余额、套餐续费、订单、礼品卡兑换、邀请返利、工单、Telegram 讨论组（面板配置优先，编译期链接兜底）
- 其他设置：外观（跟随系统 / 浅色 / 深色）、语言（简体中文 / 繁體中文 / English）、TUN 堆栈（System / Gvisor / Mixed）、修改密码、到期与流量提醒开关
- 关于软件：应用与内核版本、检查更新、日志导出与分享
- 应用内更新：远程配置多源容灾 + 下载进度条 + SHA-256 校验（缺少校验和时拒绝安装）
- 安全白名单：凭据只发往白名单内域名，防配置投毒
- 日志脱敏：路径型 token（`/s/xxx` 等）统一打码，凭据与敏感值不入日志

## 下载与安装

1. 从 [Releases](https://github.com/ipevel/polaris/releases) 下载最新版 APK（`Polaris-<版本号>.apk`，arm64-v8a）
2. **校验完整性**：下载页附有 `SHA256SUMS.txt` 与签名证书指纹，安装前比对 SHA-256（Windows：`certutil -hashfile <APK> SHA256`；Linux/macOS：`sha256sum <APK>`）；不一致请勿安装
3. 按系统提示允许「安装未知应用」后完成安装（需 Android 8.0 / API 28 及以上）
4. 首次启动在登录页填写**你自己的面板网址**（App 不内置任何面板地址）
5. 升级走 App 内更新（下载后自动校验 SHA-256），也可手动下载新版覆盖安装（数据保留）

> 本项目仅通过 GitHub Releases 分发；其他渠道的安装包无法保证来源与完整性。

## 上游仓库

本仓库的基础代码来自上游项目 **[shgnx/slte](https://github.com/shgnx/slte)**，在此致谢上游作者的开源贡献。本仓库在上游基础上进行定制化开发与独立发布（品牌、面板适配、功能增强等），如需上游原版请移步上游仓库。

## 当前版本

| 平台 | 版本 | Version Code |
|------|------|-------------|
| Android | 1.4.17 | 31 |

> 内核版本：metacubex/mihomo v1.19.30（含 anytls / masque / openvpn / tailscale / zerotier 等本地 outbound 补丁）

## 开发环境

| 依赖 | 版本 |
|------|------|
| JDK | 17（`sourceCompatibility` / `jvmTarget` 均为 17，CI 同样使用 17） |
| Android SDK | compileSdk 36 / targetSdk 36 / minSdk 28 |
| NDK | 28.2.13676358 |
| CMake | 3.22.1+（`kernel-core/src/main/cpp/CMakeLists.txt` 最低要求） |
| Gradle | 8.13（wrapper 内置） |
| Android Gradle Plugin | 8.9.1 |
| Kotlin | 2.0.21 |
| Compose BOM | 2025.04.01 |

> 内核已预编译为 `libclash.so` 随仓库提供，普通编译无需 Go 环境；仅修改 Go 补丁链或升级内核版本时才需要。

## 编译

```bash
# 调试包（含单元测试）——不触达 release 变体，无需签名变量
./gradlew :app:testDebugUnitTest :app:assembleDebug

# 任何会分析 release 变体的任务（ktlintCheck / lintDebug / R8 等）都必须先提供
# 签名四件套，否则 Gradle 直接报错拒绝——防「用 debug 签名发版」
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

## 配置

### 面板地址（运行时）

**App 不内置任何面板地址。** 用户首次使用时在登录页填写自己的面板网址（自动补全 `https://`，仅接受可解析的 HTTPS 地址），登录后所有面板 API 请求都基于该地址；未填写时网络层直接快速失败。更换面板 = 退出登录后填写新地址。

### 编译期变量

通过环境变量或 `app/gradle.properties` 注入（默认值为占位符，纯编译与单元测试无需真实值）：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `POLARIS_APP_NAME` | `Polaris` | 应用显示名 |
| `POLARIS_APPLICATION_ID` | `com.polaris.app` | 应用包名 |
| `POLARIS_VERSION_NAME` | `1.4.17` | 版本名（Release 由 build.yml 传入） |
| `POLARIS_VERSION_CODE` | `31` | versionCode |
| `POLARIS_API_BASE_URL` | `https://api.example.com` | 仅作 Retrofit 构造引导占位，运行时不使用；同时并入域名白名单 |
| `POLARIS_API_TYPE` | `xiaov2b` | 遗留变量：仍写入 `BuildConfig.API_TYPE`，但运行时已不再读取（后端类型改为登录时自动探测） |
| `POLARIS_SUBSCRIBE_PATH` | `/api/v1/client/subscribe` | 订阅链接路径，面板地址 + 该路径 + token 组成订阅源 |
| `POLARIS_REMOTE_CONFIG_URLS` | 见 [config/remote.json](config/remote.json) 默认源 | 远程配置 URL，逗号分隔多源 |
| `POLARIS_ALLOWED_DOMAINS` | （空） | 追加域名白名单，逗号分隔；与 API 地址、远程配置源域名合并去重 |
| `POLARIS_TELEGRAM_GROUP_URL` | （空） | Telegram 讨论组兜底链接，面板未下发且此处留空时隐藏该入口 |

> 发版签名走 `POLARIS_RELEASE_STORE_FILE` / `POLARIS_RELEASE_STORE_PASSWORD` / `POLARIS_RELEASE_KEY_ALIAS` / `POLARIS_RELEASE_KEY_PASSWORD`（见[编译](#编译)）。

> `POLARIS_REMOTE_CONFIG_URLS` 的默认值为 `https://raw.githubusercontent.com/ipevel/polaris/main/config/remote.json`，详见 [CONFIG.md](CONFIG.md)。

> **安全白名单**：为防配置投毒导致凭据外泄，API 地址与远程配置中的直连域名只允许在域名白名单内切换。白名单 = `POLARIS_ALLOWED_DOMAINS` 追加项 + API 地址域名 + 远程配置源域名，构建期自动并入（详见 [CONFIG.md](CONFIG.md)），**无需修改代码**。仓库内置占位符 `example.com`，部署前请通过环境变量或 `app/gradle.properties` 注入你的域名。
>
> **内核直连兜底**：内核侧补丁链（`kernel-core/src/main/golang/native/config/process.go`）含独立的直连域名占位（与构建注入互不影响），自持域名需在此同步，并在修改后重新交叉编译 `libclash.so`（`GOOS=linux GOARCH=arm64 go build -tags "android cmfa with_gvisor" ./native/config/`，无需 NDK）。

## 相关项目

- [shgnx/slte](https://github.com/shgnx/slte) - 上游项目（本仓库基础代码来源）
- [mihomo](https://github.com/MetaCubeX/mihomo) - 内核引擎（核心依赖，vendored 于 `kernel-core/src/foss/golang/clash/`）
- [ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid) - 内核栈（`kernel-*` 模块）派生自该项目，第三方声明见 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)
- [V2Board (xiaov2b)](https://github.com/wyx2685/v2board/tree/master) - 兼容的机场面板后端（`xiaov2b` API，master 分支）
- [Xboard](https://github.com/cedar2025/Xboard/tree/master) - 兼容的机场面板后端（`xboard` API，master 分支）

## 社区

- [安全政策](SECURITY.md) —— 漏洞请走私有渠道报告，勿开公开 Issue
- [贡献指南](CONTRIBUTING.md) ｜ [行为准则](CODE_OF_CONDUCT.md)
- [支持与求助](SUPPORT.md)

## 许可证

本项目以 [GPL-3.0](LICENSE) 协议开源，基于 [mihomo](https://github.com/MetaCubeX/mihomo/tree/Alpha) 内核构建，第三方组件声明见 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。

---

<div align="center">

**© 2026 Polaris**

</div>
