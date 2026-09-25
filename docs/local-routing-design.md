# Polaris 本地分流方案 + 节点选择方式 设计文档

> 移植自 Karing（KaringX/karing）的「订阅只出节点、App 出方案」设计，结合同团队 mihomo 内核客户端 clashmi 的公开模板模型实现。
> 关联调研：工作区 `karing-repo` / `karing-ruleset` / `clashmi-repo` 三个参考克隆（2026-09-25 调研）。

## 1. 目标

1. 屏蔽面板（Xboard/V2board 订阅 YAML）下发的 `rules` / `proxy-groups`，只使用 App 本地内置分流方案。
2. Karing 式节点选择：全局手动选择 + 自动选择（url-test）+ 故障转移（fallback）+ 每条分流规则组独立出口（select 组成员切换）。
3. 面板节点（`proxies`）原样保留，作为本地策略组的成员来源。
4. 面板 YAML 缺节点（仅 proxy-provider）或本地分流关闭时，回退到现有行为（面板配置原样生效）。

## 2. 架构决策

| 决策 | 选择 | 理由 |
|---|---|---|
| 生成层位置 | Go 侧 `patchLocalRouting` 处理器 | Polaris 清洗是行级的、刻意不解析 YAML；Go 侧拿到已解析的 `RawConfig` 结构体直接改字段，零解析风险，且与现有安全 patch 链一致 |
| 状态通道 | `filesDir/clash/routing.json`（Go home 目录，与 override.json 同级） | 复用 override.json 的既有读写模式；内核未运行时文件也持久存在；无需改 AIDL |
| 策略组成员 | `include-all: true` 自动纳入全部节点 | 订阅更新后无需重新生成；节点增减自动跟随 |
| 规则数据 | 内置 APK assets 预播种 + http rule-provider 自动更新 | 冷启动断网时使用内置文件可立即生效；联网后内核按 interval 从 jsDelivr 刷新 |
| 选择持久化 | 本地分流启用时 `StoreSelected=true`（mihomo cache.db） | 每条分流组的出口选择必须跨重连存活；本地分流关闭时维持现状 false |
| 规则数据源 | karing-ruleset 数据谱系：`MetaCubeX/meta-rules-dat@meta`（geosite/geoip）+ `ACL4SSR/ACL4SSR@master`（Clash 清单），经 fastly.jsdelivr.net | 与 karing-ruleset 同一上游、每日自动更新；48 个种子全量内置（1.5MB） |

## 3. 状态文件契约（routing.json）

```json
{
  "version": 1,
  "enabled": true,
  "groups": { "广告拦截": false, "电报消息": true },
  "custom": [
    { "name": "我的规则", "url": "https://.../rules.yaml", "behavior": "classical", "interval": 86400 }
  ]
}
```

- `enabled`：本地分流总开关。`false` 或文件缺失/损坏 → 不改动面板配置（回退现状）。
- `groups`：内置分流组开关，缺省 = 内置默认值（表格 defaultOn）。`false` → 该组规则全部不生成。
- `custom`：M3 自定义规则组（名称 + 规则集 URL + behavior + 刷新间隔）。
- 解析失败一律静默回退（与 override.json 同策略），不阻断连接。

## 4. 生成的策略组结构（Go 侧）

按序生成（顺序是契约：`KernelProxyGroup.selectorGroup()` 取第一个 Selector 组）：

| # | 组名 | 类型 | 成员 | 说明 |
|---|---|---|---|---|
| 1 | 🚀 节点选择 | select | [自动选择, 故障转移, DIRECT] + include-all | 全局手动选择，默认=自动选择 |
| 2 | 自动选择 | url-test | include-all | url=gstatic generate_204, interval=300, tolerance=50, lazy |
| 3 | 故障转移 | fallback | include-all | 同上 URL |
| 4… | 各分流组 | select | [🚀 节点选择, REJECT, DIRECT]（block 型组 REJECT 首位） | 默认出口由首位成员表达 |
| n-1 | 🎯 全球直连 | select | [DIRECT, 🚀 节点选择] | |
| n | 🐟 漏网之鱼 | select | [🚀 节点选择, DIRECT] | MATCH 兜底组 |

- 与现有 App 启发式对齐：`selectAuto()` 找 URLTest 组命中「自动选择」；`selectFallback()` 命中「故障转移」；`ensureGlobalSelection()` 全局模式兜底自动挂载。
- 面板 `cfg.ProxyGroup` / `cfg.Rule` / `cfg.SubRules` / `cfg.RuleProvider` 整体丢弃（= 屏蔽网站下发分流）。
- 面板 `cfg.Proxy` 保留；`ProxyProvider` 保留（include-all 会纳入其节点）。

## 5. 生成的规则序列

1. 自家后端域名直连（Go 侧 `directDomains`，与 patchRules 双防线一致）
2. 本地网络/私有地址直连（内联，含 no-resolve）
3. 苹果推送内联规则（域名字段 + IP-CIDR no-resolve，Karing 同款）
4. 依内置分流表顺序：每启用组的 `RULE-SET,<key>,<组名>`（ipcidr 类加 `,no-resolve`）+ 组内联规则
5. ProxyLite 的裸关键字转内联 `DOMAIN-KEYWORD,<kw>,<组名>`（classical 行为不收裸关键字，必须内联）
6. `MATCH,🐟 漏网之鱼`

不使用 GEOIP 规则（避免 geoip.db 冷启动下载依赖，国内 IP 由 ChinaIp/ChinaIpV6/ChinaCompanyIp provider 覆盖）。

## 6. 内置分流表（provider → behavior）

分组、顺序、默认开关与出站语义逐条对齐 **Karing 预设 cn.json**（KaringX/karing
assets/datas/preset/cn.json）：广告拦截(REJECT 默认,关)、应用净化(关)、苹果推送(关)、
苹果服务(DIRECT,开)、油管(关)、Gemini(关)、Google Play(开)、Google FCM(关)、
Google(开)、Facebook(关)、X(关)、TikTok(关)、Instagram(关)、奈飞(关)、WhatsApp(关)、
电报(关)、Claude(关)、OpenAI(关)、GitHub(关)、微软Bing(关)、微软云盘(关)、
微软服务(关)、游戏平台(关)、哔哩哔哩(DIRECT,开)、网易音乐(关)、国内直连(DIRECT,开)、
国外穿墙(开)。

规则数据源沿用 karing-ruleset 的**数据谱系**（karing-ruleset 本体仅发布 sing-box
.srs 格式，mihomo 无法读取）：
- geosite/geoip 类别 → `MetaCubeX/meta-rules-dat@meta`（karing-ruleset 的 geo
  数据同源上游；KaringX/meta-rules-dat README 指定的 mihomo 格式获取渠道），
  behavior: geosite=domain、geoip=ipcidr，payload YAML
- ACL4SSR 清单 → `ACL4SSR/ACL4SSR@master` 的 `Clash/` 目录（karing-ruleset
  workflow 每日转换 srs 的同一来源），behavior=classical、format=text
- 下载均走 `fastly.jsdelivr.net`，provider interval=86400s（24h），与
  karing-ruleset 每日构建节奏一致
- 「恶意软件」组缺失：其引用的 malware/phishing 类别仅存在于 karing-ruleset 的
  Iran 专用源，meta-rules-dat 无对应类别

## 7. 处理器链与持久化

```
patchExternalController → patchOverride → [NEW] patchLocalRouting → patchGeneral
  → patchProfile(StoreSelected=分流启用) → patchDns → patchRules(兜底保留) → …
```

- 直连兜底优先级：App 构建期注入清单（随 routing.json `direct_domains` 下发的真实面板域名）> 内核编译期 `directDomains`（占位）；`patchRules`（自家域名直连插队）保留为面板模式兜底，其查重逻辑保证与生成规则不重复。
- 冷启动安全性：provider 初始下载失败仅记日志（executor `loadProvider` 非致命），规则不匹配时流量落入 MATCH 兜底，不会连接失败。
- `IsSafePath` 在 cmfa 构建下恒放行（上游 CFA 同款），patchProviders 的 profileDir 路径重写沿用。

## 8. Kotlin 侧

| 组件 | 职责 |
|---|---|
| `RoutingStateStore` | routing.json 原子读写（temp+rename，防截断坑 #13）+ UI 状态镜像（SharedPreferences） |
| `KernelConfig` | 导入/更新订阅后预播种 provider 文件到 `imported/<uuid>/providers/polaris-rules/`（不覆盖内核已更新的文件）并确保 routing.json 存在 |
| 设置页 | 「分流方案」入口：本地分流总开关（M1）；分流组开关列表（M2）；自定义规则组管理（M3） |
| 状态变更 | 写 routing.json → `ACTION_PROFILE_CHANGED` 广播触发内核重载（内核运行时） |

## 9. 阶段与验证

| 阶段 | 内容 | 验证 |
|---|---|---|
| M1 | Go 生成器 + 屏蔽面板 rules/groups + 内置 provider assets + 总开关 + StoreSelected 条件化 | go vet/test（GOOS=linux 语法门禁）、跨编译 so、`:app:verifyKernelBinary`、Kotlin 单测、九项门禁 |
| M2 | 分流组开关持久化 + 分流规则管理 UI | RoutingStateStore 单测、UI 冒烟、九项门禁 |
| M3 | 自定义规则组（URL/behavior 校验）+ 生成器支持 | Go 单测（custom 追加/重名防冲突）、Kotlin 单测、九项门禁 |

## 10. 已核对的风险清单

- [x] `IsSafePath`：cmfa 构建恒 true（constant/path.go:89）
- [x] provider 初始失败非致命（hub/executor/executor.go loadProvider 仅记日志）
- [x] classical 非法条目跳过不报错（rules/provider/classical_strategy.go Insert）
- [x] `ParseRuleFormat("")` = yaml；`include-all` 字段受支持（adapter/outboundgroup/parser.go:41）
- [x] provider 判定：ipcidr 4 个（ChinaIp/ChinaIpV6/ChinaCompanyIp/TelegramCIDR），其余 classical，ProxyLite 内联
- [x] 预编译 so 必须重编（project-records 坑 7），构建 tags：`android cmfa with_gvisor`
- [x] `selectorGroup()` 首组契约 → 🚀 节点选择必须排第一
- [x] 全局模式：GLOBAL 兜底逻辑不受影响（autoGroupName 命中「自动选择」）
- [x] 原子写入（坑 #13）：routing.json 用 temp+rename
