# 安全政策（Security Policy）

Polaris 是基于 mihomo 内核的开源 Android 代理客户端。我们重视安全问题，欢迎负责任的漏洞披露。

## 支持范围

| 版本 | 安全支持 |
|------|---------|
| 最新 Release（见 [Releases](https://github.com/ipevel/polaris/releases)） | ✅ 接收安全更新 |
| 旧版本与已下线的桌面端历史 | ❌ 请升级至最新版 |

- **支持对象**：本仓库代码——Android 应用层（`app/`）、内核栈集成（`kernel-*`）与 GitHub Actions 工作流。
- **mihomo 内核漏洞**请直接上报 [MetaCubeX/mihomo](https://github.com/MetaCubeX/mihomo/security/advisories)（vendored 源码位于 `kernel-core/src/foss/golang/clash/`）；若影响本仓库的集成方式，可在汇报上游的同时通知我们。
- **上游 Clash Meta for Android（CMA）**的问题请上报 [MetaCubeX/ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid)。

## 如何报告漏洞

**请勿通过公开 Issue 报告安全漏洞。** 请使用 GitHub 私有渠道：

1. **GitHub 安全公告（推荐）**：[Security → Report a vulnerability](https://github.com/ipevel/polaris/security/advisories/new)
2. 如不可用，可在任意 Issue 中仅留联系方式，由维护者私下跟进（请勿在正文中写出漏洞细节）。

报告时请尽量包含：

- 受影响版本（App「我的 → 关于」页版本号，或 Release 标签）
- 复现步骤 / PoC
- 影响评估（如凭据泄露、远程执行、越权等）

## 响应预期

| 阶段 | 预期 |
|------|------|
| 确认收到 | 3 天内 |
| 初步评估 | 7 天内 |
| 修复发布 | 视严重程度尽快处理 |

修复发布后将在 Release 说明中对报告者致谢（除非报告者要求匿名）。

## 报告范围之外

- 自建面板（XiaoV2b / Xboard）或机场基础设施本身的漏洞（请报告给相应面板项目或服务商）
- 缺乏复现依据、无法验证的报告
- 理论上的资源耗尽类攻击（无绕过或提权效果的 DoS）
- 已在最新版修复的历史问题
