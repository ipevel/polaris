<div align="center">

# 北辰 Polaris

[![许可证](https://img.shields.io/badge/许可证-GPL--3.0-blue?style=flat-square)](LICENSE)
[![平台](https://img.shields.io/badge/平台-Android-green?style=flat-square)](README.md)
[![内核](https://img.shields.io/badge/内核-mihomo-9cf?style=flat-square)](https://github.com/MetaCubeX/mihomo)

**基于 mihomo 内核的轻量级 Android 代理客户端，支持 XiaoV2b / Xboard 面板**

</div>

---

## 简介

北辰（Polaris）是一款基于 [mihomo](https://github.com/MetaCubeX/mihomo/tree/Alpha) 内核构建的 Android 代理客户端。如北极星般稳定可靠，为你指引网络航向。

- 双面板后端支持：XiaoV2b / Xboard
- 面板地址完全由用户掌控：登录页填写自己的面板网址，App 不内置任何面板地址
- 仪表盘：用量、套餐、代理模式、当前 IP（国旗地区码）、连接开关
- 策略组选择与延迟测试、流量明细（按日汇总）
- 礼品卡兑换、邀请返利、工单、公告
- 应用内更新（远程配置多源容灾 + 下载进度条）

## 上游仓库

本仓库的基础代码来自上游项目 **[shgnx/slte](https://github.com/shgnx/slte)**，在此致谢上游作者的开源贡献。本仓库在上游基础上进行定制化开发与独立发布（品牌、面板适配、功能增强等），如需上游原版请移步上游仓库。

## Contributors

- [shgnx/slte](https://github.com/shgnx/slte) - 上游项目作者，本仓库基础代码来源
- **DeepSeek** - AI 结对编程（架构设计、功能实现与代码审查）
- **智谱 GLM** - AI 结对编程（功能移植、全局重构与发布工程）

## 开发环境

| 依赖 | 版本 |
|------|------|
| JDK | 17+（推荐 21） |
| Android SDK | compileSdk 36 |
| NDK | 28.2 |
| CMake | 3.22+ |
| Gradle | 8.13（wrapper 内置） |

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
| `POLARIS_APP_NAME` | `北辰` | 应用显示名 |
| `POLARIS_APPLICATION_ID` | `com.polaris.app` | 应用包名 |
| `POLARIS_API_TYPE` | `xiaov2b` | 后端类型（`xiaov2b` / `xboard`） |
| `POLARIS_API_BASE_URL` | `https://api.example.com` | 仅作 Retrofit 构造引导占位，运行时不使用 |
| `POLARIS_REMOTE_CONFIG_URLS` | `https://raw.githubusercontent.com/ipevel/polaris/main/config/remote.json`（见 [config/remote.json](config/remote.json)） | 远程配置 URL，逗号分隔多源 |

> **安全白名单**：为防配置投毒导致凭据外泄，API 地址与远程配置中的直连域名只允许在域名白名单内切换。白名单 = `POLARIS_ALLOWED_DOMAINS` 追加项 + API 地址域名 + 远程配置源域名，构建期自动并入（详见 [CONFIG.md](CONFIG.md)），**无需修改代码**。仓库内置占位符 `example.com`，部署前请通过环境变量或 `app/gradle.properties` 注入你的域名。
>
> **内核直连兜底**：内核侧补丁链（`kernel-core/src/main/golang/native/config/process.go`）含独立的直连域名占位（与构建注入互不影响），自持域名需在此同步，并在修改后重新交叉编译 `libclash.so`（`GOOS=linux GOARCH=arm64 go build -tags "android cmfa with_gvisor" ./native/config/`，无需 NDK）。

## 相关项目

- [shgnx/slte](https://github.com/shgnx/slte) - 上游项目（本仓库基础代码来源）
- [mihomo](https://github.com/MetaCubeX/mihomo) - 内核引擎（核心依赖，vendored 于 `kernel-core/src/foss/golang/clash/`）
- [ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid) - 内核栈（`kernel-*` 模块）派生自该项目，第三方声明见 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)
- [V2Board (xiaov2b)](https://github.com/wyx2685/v2board/tree/master) - 兼容的机场面板后端（`xiaov2b` API，master 分支）
- [Xboard](https://github.com/cedar2025/Xboard/tree/master) - 兼容的机场面板后端（`xboard` API，master 分支）

## 许可证

本项目以 [GPL-3.0](LICENSE) 协议开源，基于 [mihomo](https://github.com/MetaCubeX/mihomo/tree/Alpha) 内核构建，第三方组件声明见 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。

---

<div align="center">

**© 2026 北辰 Polaris**

</div>
