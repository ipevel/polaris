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

Polaris 是一款基于 [mihomo](https://github.com/MetaCubeX/mihomo/tree/Alpha) 内核构建的代理客户端，稳定可靠，为你指引网络航向。

### Android

- 双面板后端支持：XiaoV2b / Xboard（`xiaov2b` API 握手，Xboard 兼容复用同一适配器）
- 面板地址完全由用户掌控：登录页填写自己的面板网址，App 不内置任何面板地址
- 首页仪表盘：连接开关（点击起即计时长）、流量统计与网速方形大卡、当前 IP（国旗地区码）、用量与套餐等高对齐信息密度布局
- 四栏底部导航：主页 / 节点 / 流量 / 我的，切换与页面预加载更流畅
- 策略组选择与延迟测试、节点名归一化显示、流量明细（按日环形总图 + 上行/下行/合计）
- 订阅卡片一键更新订阅、礼品卡兑换、邀请返利、工单、公告
- 应用内更新（远程配置多源容灾 + 下载进度条 + SHA-256 校验）
- 三种代理模式：规则 / 全局 / 直连
- TUN 堆栈可选：System / Gvisor / Mixed
- 多语言：简体中文 / 繁體中文 / English
- 深色 / 浅色 / 跟随系统主题
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
| Android | 1.4.10 | 24 |

> 内核版本：metacubex/mihomo v1.19.30（含 anytls / masque / openvpn / tailscale / zerotier 等本地 outbound 补丁）

## Contributors

- [shgnx/slte](https://github.com/shgnx/slte) - 上游项目作者，本仓库基础代码来源
- **DeepSeek** - AI 结对编程（架构设计、功能实现与代码审查）
- **智谱 GLM** - AI 结对编程（功能移植、全局重构与发布工程）
- **github-actions[bot]** - CI/CD 发布自动化（Release 构建、APK 上传与 `config/remote.json` 版本信息/checksum 自动回写）

## 开发环境

| 依赖 | 版本 |
|------|------|
| JDK | 17+（推荐 21） |
| Android SDK | compileSdk 36 |
| NDK | 28.2 |
| CMake | 3.22+ |
| Gradle | 8.13（wrapper 内置） |
| Kotlin | 2.0.21 |
| Compose BOM | 2025.04.01 |

> 内核已预编译为 `libclash.so` 随仓库提供，普通编译无需 Go 环境；仅修改 Go 补丁链或升级内核版本时才需要。

## 编译

```bash
# 调试包（含单元测试）
./gradlew :app:testDebugUnitTest :app:assembleDebug

# 发布包（必须提供签名环境变量）
POLARIS_RELEASE_STORE_FILE=<keystore> \
POLARIS_RELEASE_STORE_PASSWORD=<密码> \
POLARIS_RELEASE_KEY_ALIAS=<别名> \
POLARIS_RELEASE_KEY_PASSWORD=<密码> \
./gradlew :app:assembleRelease
```

## 配置

### 面板地址（运行时）

**App 不内置任何面板地址。** 用户首次使用时在登录页填写自己的面板网址（自动补全 `https://` 前缀），登录后所有面板 API 请求都基于该地址；未填写时网络层直接快速失败。更换面板 = 退出登录后填写新地址。

### 编译期变量

通过环境变量或 `app/gradle.properties` 注入（默认值为占位符，纯编译与单元测试无需真实值）：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `POLARIS_APP_NAME` | `Polaris` | 应用显示名 |
| `POLARIS_APPLICATION_ID` | `com.polaris.app` | 应用包名 |
| `POLARIS_API_TYPE` | `—（不再作运行时默认）` | 遗留变量：后端类型已改为登录时自动识别（`xiaov2b`/`xboard` 由面板接口探测），不再以此值兜底选型 |
| `POLARIS_API_BASE_URL` | `https://api.example.com` | 仅作 Retrofit 构造引导占位，运行时不使用 |
| `POLARIS_REMOTE_CONFIG_URLS` | 见 [config/remote.json](config/remote.json) 默认源 | 远程配置 URL，逗号分隔多源 |

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
