# 隐私政策 / Privacy Policy

**生效日期**：2026-09-28
**适用产品**：北辰 Polaris（Android 客户端，以下简称"本应用"）

> 一句话概括：**本应用没有自建服务器，不做任何数据采集与上报。**你输入的面板地址、账号、订阅与流量数据只保存在你自己的手机里；代理流量只在你与你自己选择的面板/节点之间流转。

---

## 1. 我们不收集什么

本应用**不包含**任何统计、埋点、广告、崩溃上报 SDK（无 Firebase、无 Sentry、无 Bugly、无友盟等），因此：

- 不采集设备标识（IMEI / Android ID / OAID / 广告 ID 均不读取）；
- 不采集通讯录、短信、通话记录、相册、位置、麦克风、摄像头；
- 不采集你的浏览记录、访问域名或代理流量的内容；
- 不向开发者或任何第三方上传上述信息——因为本应用根本没有可供上传的服务器端点。

## 2. 只保存在你设备本地的数据

以下数据写在应用私有目录（其他应用无法读取），卸载即删除：

| 数据 | 存储位置 | 说明 |
| --- | --- | --- |
| 面板地址（你手动输入） | 本地私有存储 | 用于下次启动直连你的面板 |
| 登录邮箱 / 账号标识 | 本地私有存储 | 展示"我的"页面用 |
| 登录密码、访问令牌 | **加密存储**（AndroidKeyStore + AES-GCM） | 绝不落明文；密钥由系统密钥库托管 |
| 订阅节点、分组、用量、订单、工单、公告 | 本地私有存储（缓存） | 来自你的面板，用于离线查看 |
| 远程配置缓存 | 加密存储 | 用于拉取面板下发的应用配置（如应用名、描述） |
| 主题、语言、TUN 堆栈、提醒开关等偏好 | 本地私有存储 | 纯客户端设置 |
| 运行日志 | 内存 + 应用私有目录 | 仅在你点「日志导出」时才会写出并交给你分享 |

本应用已关闭系统自动备份（`allowBackup=false`），上述数据不会被同步到云端。

## 3. 会发生的网络通信

1. **你的面板**（你自行输入并确认的地址，HTTPS）：登录、拉取订阅与用量、下单、工单等。这些请求直接发往**你所选择的面板运营者**，与开发者无关；其数据处理规则由该运营者负责，请查阅其隐私说明。
2. **GitHub**（`raw.githubusercontent.com` / `github.com`）：拉取面板下发的远程配置（应用名、描述等展示信息）；以及在你点击「开源许可 / 用户协议 / 隐私政策」时用浏览器打开对应页面。
3. **公共 IP 查询**（`api.ipify.org`）：仅当你使用需要显示出口 IP 的功能时请求，用于向你展示当前公网 IP；请求不携带任何账号信息。

本应用**不接收**来自上述任何一方的推送或指令式配置下发，远程配置只影响应用名与描述等展示信息的呈现。

## 4. VPN 权限与流量

本应用的核心功能是在本机建立一个**本地 VPN（TUN）接口**，把流量按你选择的节点与分流规则转发。这意味着：

- 系统会显示常驻通知与 VPN 状态图标，这是 Android 对本地 VPN 的强制要求；
- **流量内容不会被本应用读取、记录或上传**：转发由开源内核（mihomo）在本机完成，日志中不包含请求正文；
- 你可以随时断开连接或卸载应用，本地 VPN 随之中止。

## 5. 日志

运行日志用于排查"连不上"这类问题，内容为连接状态、事件与错误信息，并已对令牌、密码、邮箱、面板主机名做**脱敏打码**。日志只在你主动点击「日志导出」时生成文件，之后如何分享完全由你决定。

## 6. 未成年人

本应用不面向 14 周岁以下儿童，也不会收集其任何信息（因为本应用不收集任何人的信息）。

## 7. 你的权利

由于数据只在你自己的设备上，你可以随时通过「退出登录」「清除数据」或在系统设置中卸载应用来删除全部数据，无需向开发者提出请求。

## 8. 政策变更

若本政策发生变更，会随新版本一起发布并在仓库中留档；继续使用即视为接受更新后的内容。

## 9. 联系方式

问题、疑问或安全相关报告，请通过本应用仓库的 Issues 提交。

---

## English Summary

Polaris ships **no analytics, no telemetry, and no developer-operated server**. Your panel URL, account, subscription and usage data stay in the app's private storage on your device; credentials and the remote-config cache are encrypted via AndroidKeyStore. Network traffic goes only to (1) the panel you configured, (2) GitHub for the remote display config and legal pages, and (3) `api.ipify.org` when showing your public IP. The VPN runs entirely on-device via an open-source mihomo kernel; traffic content is never inspected, logged, or uploaded. Logs are sanitized and only exported when you explicitly tap "Export logs". Automatic backup is disabled.
