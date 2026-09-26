# 首次连接业务流 — 风险、缺口与待验证项

本文件是 `first-connect-flow.business.dot` / `first-connect-flow.technical.dot` 的不确定性台账。
**规则：图与 `architecture-understanding.md` 的确定性口径以本文件为准。本文件中标为未知的内容，在图上必须是虚线/灰框，不得表现为已确认路径。**

---

## A. 顶层限制（先看这里，再看细节）

### A1. 面板是外部系统，服务端行为整体不可见

本流程中所有"面板侧副作用"（额度扣减、套餐生效、订单结算、订阅签发的时效与鉴权细节）**在本仓库内没有证据**。仓库里能确认的只有客户端的**请求与解析契约**：请求哪个路径、带什么头、如何解析响应。

- 因此：`面板侧计费 / 额度扣减 / 订单结算` 在图上必须保持 `[未知]` 灰色虚线节点。
- 若后续要回答"下单后多久生效""额度是按日重置还是按周期重置"，必须先取得面板实现或接口文档，或用真机抓包。

### A2. 本报告全部为静态证据，无运行时遥测

采集手段为代码与配置阅读，**没有**真机 trace、logcat、内核日志或压测数据。因此下列内容虽在代码上成立，但"实际运行时是否如此"未被观测：

| 项 | 静态结论 | 需运行时验证 |
| --- | --- | --- |
| TUN 建立耗时与成功率（不同 ROM/厂商后台限制下） | 存在看门狗与取消路径 | 真机弱网/被杀后台场景 |
| AIDL 同步调用往返耗时（注释称常为 1.0~1.2s） | 代码注释给出经验值 | 实测耗时分布 |
| `awaitTunnelReady` 5 分钟上限是否足够（大订阅） | 上限可配 | 超大订阅实测 |
| 内核 1s ticker 的 blip 与实际网速的一致性 | 解码逻辑与 C/Go 侧一致 | 与系统流量统计交叉比对 |

---

## B. 值得盯住的设计风险

### B1.（最高优先）绕过净化的理论路径仍然存在于代码中

**事实**：`ProfileProcessor.update()` 会无条件调用 `Clash.fetchAndValid()` 直抓订阅并热重载，**不经过** `SubscriptionSanitizer`。一旦这条路径被触发，面板下发的原始配置（含顶层端口、控制面等已净化的内容）会直接进入内核。

**当前可达性**：仓库内无可达调用方（详见 `first-connect-flow.evidence.md` 第 9.1 节的逐项核验：无 AlarmManager 排程、无 JobScheduler/WorkManager、`ACTION_PROFILE_REQUEST_UPDATE` 无生产者、`ProfileManager.update()` 已改为空实现）。

**残余风险（这一段是真实存在、不该被"当前不可达"掩盖的）**：

1. `ProfileReceiver` 是 `android:exported="true"`，仅由自定义权限 `${applicationId}.permission.RECEIVE_BROADCASTS` 保护（`kernel-service/src/main/AndroidManifest.xml:59-72`）。该权限声明为 `android:protectionLevel="privileged|signature"`（`kernel-common/src/main/AndroidManifest.xml:3-7`），因此**普通第三方应用无法持有**；但**同签名应用**与**特权系统应用**具备触发能力——触发的后果是内核用原始订阅热重载，绕过脱敏。
2. 这条路径是"保留但无人使用"的状态，没有注解 `@Deprecated`，没有编译期或测试防线。未来任何一次"顺手接上定时更新"的改动（这是上游 CMA 的原始设计意图，很可能被后来的贡献者当成缺失功能补上）都会立刻把脱敏链路打穿。
3. `ProfileManager.kt:139-142` 与 `ProfileWorker.kt:99` 的"不要调度定时抓取"意图只写在注释里，属**文档性约束**，无机制强制。

**建议的验证/加固动作**（任一即可显著降低风险）：
- 在 `ProfileProcessor.update()` 入口加运行时断言/日志，或直接删除该方法与 `ProfileWorker`；
- 在 CI 里加一条守卫：若 `Clash.fetchAndValid(` 在 `ProfileProcessor` 中被调用且无脱敏前置，则失败（与本仓库既有的"日志脱敏守卫"同类思路）；
- 将 `ProfileReceiver` 的 `exported` 收紧为 `false`（该 receiver 所需的系统广播在 manifest 中另有声明，见下方 B5 需一并核验）。

### B2. 订阅内容的来源主机不受白名单约束 —— 净化器是唯一屏障

`SubscribeSourceImpl.candidateSubscribeUrls()` 对账号下发的订阅地址**只校验 https，不校验 host**（`SubscribeSourceImpl.kt:107-114`）。即：面板（或中间人，若面板被攻陷）可以让客户端去任意 https 域名取订阅内容。

- 该请求**不会**被注入凭据（`AuthInterceptor` 只对白名单主机或当前面板主机加 `Authorization`，`AuthInterceptor.kt:93`），这一点是正确且值得肯定的；
- 但内容是**第三方可控文本**，随后直接进入 `SubscriptionSanitizer`。也就是说，B2 与配置投毒之间的**唯一**屏障就是净化器的正确性。
- 净化器已具备较强的失败关闭语义（关键步骤失败或残留危险顶层键即返回空串，`SubscriptionSanitizer.kt:40-47`），且有专门的边界测试（`SubscriptionSanitizerBoundaryTest.kt`，含 HTML/JSON/BOM/控制字符/幂等性用例）。**但这是单点防线**，缺少第二道校验（例如：净化后重新解析 YAML 并断言顶层键集合为允许列表）。

**待验证**：`SanitizerRules` 的允许/禁止键清单是否与 mihomo 当前版本的全部危险配置项保持同步（尤其是新增的出站/控制类配置项）。这需要对照内核版本逐项核对，本次未做。

### B3. 订阅刷新没有时间驱动，长期运行会话可能一直用旧配置

小节流量与订阅更新走 `maybeSilentUpdate`，其唯一调用点是 `MainViewModel` 初始化（`MainViewModel.kt:84`），即**每次进入仪表盘才刷新**。加上"购买后刷新"与手动刷新。而内核侧原生的定时更新 worker 已被判定为不可达（见 B1）。

- 后果：用户长时间挂着 VPN、不进仪表盘，则节点变更、订阅失效、套餐到期都不会被自动感知，直到下一次进入仪表盘或发生网络异常。
- 这也解释了为什么 `ProfileProcessor` 里 `Imported.interval` 字段仍然存在却无人消费——**数据结构支持 ≠ 行为存在**。
- **这是产品行为层面的缺口，不是 bug**；是否要补一个前台/工作管理器驱动的周期性刷新，属于产品决策。

### B4. 净化失败的降级语义可能造成"静默不可用"

`directDomains()` 为空时会降级为"跳过直连规则注入，其余流程照常完成"（`KernelConfig.kt:105-107`、`:135-137`）。这避免了整体失败，但结果是：订阅内容被装载，而面板域名没有被加入直连白名单——在开启代理后，客户端访问面板的请求会走代理路径。是否可用取决于面板域名是否恰好落在规则内。

- 需要验证：该降级发生时是否有用户可见提示（当前只在日志里留痕）。
- 需要确认：`remoteConfig.directDomains` 的典型取值（来自 `remote.json`），是否为可空配置。

### B5. `ProfileReceiver` 的 exported 与所需系统广播之间存在待核验的耦合

`ProfileReceiver` 用 `ACTION_BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` / `TIME_SET` / `TIMEZONE_CHANGED` 触发"取消过期闹钟"逻辑（`ProfileReceiver.kt:28-37`），但这些系统广播在 manifest 中的 `intent-filter` 声明与 `exported=true` 是绑定在一起的（`kernel-service/src/main/AndroidManifest.xml:59-72`）。

- 直接把它改成 `exported=false` 可能同时屏蔽系统广播，导致 `cancelNext` 逻辑失效（虽然当前没有闹钟可取消，但会改变接收器的行为契约）。
- **建议动作**：如要收紧，应拆分为两个 receiver（一个接收系统广播、一个接收内部更新请求），而不是简单改 `exported`。本次**未做变更，仅记录**。

---

## C. 证据缺口（图上已标 unknown / 需补证据）

| # | 缺口 | 当前状态 | 补齐方式 |
| --- | --- | --- | --- |
| C1 | 面板侧计费/额度/结算规则 | 未知（外部系统） | 面板实现、接口文档或抓包 |
| C2 | `remote.json` 的真实分发与多源容灾行为 | 已知代码结构（`data/remote/config/`），本次未展开其解析与探测细节 | 单独一条流/拓扑分析 |
| C3 | 应用内更新的签名与 SHA-256 校验链路 | README 有描述，本次未逐行核验 | 需要时另起流程 |
| C4 | `ageSecretKey` 的生成、注入与配置加解密全链路 | 只确认字段存在与 `Clash.setAgeSecretKey` 调用点 | 追 `ProfileManager` 全流程 + 内核侧 |
| C5 | 端到端测试覆盖 | `androidTest` 仅 4 个文件，`sharedTest` 1 个；主流程为单测 + 手工验证 | 补一次真机 E2E 脚本 |
| C6 | 内核版本与 `SanitizerRules` 危险键清单的对应关系 | 未核对 | 对照 mihomo 版本逐项核对（见 B2） |

---

## D. Agent 指令覆盖度（与"AI 能否安全改这个仓库"相关）

仓库不再内置 agent 指令文件；此前的发版流程说明（现已移出公开仓库）的覆盖情况如下。

- **覆盖得好的部分**：提交/发版流程、合规检查（secrets、workflow 安全、依赖 CVE、权限最小化、明文 HTTP 禁止、隐私）、本地全量 CI 门禁、R8 keep 规则要求、"合规与功能冲突时找两全方案"的原则。
- **没有覆盖的部分（对本次流程最关键的）**：
  1. **没有说明净化链路是安全底线**，也没有"禁止让任何路径绕过 `SubscriptionSanitizer`"这条约束（这正是 B1 的风险来源）；
  2. 没有说明 `ProfileWorker` / `ProfileProcessor.update()` 属**保留且不可达**，后来者极易把它当缺失功能补上；
  3. 没有说明面板是外部系统、服务端行为不可假定；
  4. 没有订阅刷新时机（进入仪表盘触发）的业务约束说明。
- **建议**：把上述 4 条补进 agent 指令（或本目录下的架构说明），成本很低，直接堵住 B1 这类风险。

---

## E. 需要立即验证的行动清单（按性价比排序）

1. **确认 `ProfileProcessor.update()` 是否要删除**（或加编译期/CI 守卫）——这是唯一一条能把脱敏打穿的路径，成本最低、收益最高。（对应 B1）
2. **确认 `ProfileReceiver` 的权限模型**：自定义权限是否为 `signature` 级；同签名应用能否触发。（对应 B1、B5）
3. **核对 `SanitizerRules` 危险键清单与当前内核版本的差距**，特别注意新增控制类配置项。（对应 B2、C6）
4. **确认订阅刷新时机的产品预期**：长期挂机场景是否需要时间驱动的刷新。（对应 B3）
5. **确认降级路径是否有用户可见提示**。（对应 B4）