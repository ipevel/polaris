# 首次连接业务流 — 证据清单

配套图源：
- 业务视图 `first-connect-flow.business.dot`
- 技术下钻 `first-connect-flow.technical.dot`

仓库：`E:/AI/Github/slte`（Polaris，远端 `ipevel/polaris`）
采集时的 HEAD：`09fac69 chore: 自动更新 remote.json → 1.4.14`
证据类型：**code** / **config** / **data** / **document** / **runtime**（本次全部为静态代码与配置证据，无运行时遥测）

置信度含义：
- `高` = 代码或配置可直接确认
- `中` = 多方部分信号吻合，但无直接来源确认该关系
- `低` = 由命名、目录结构或约定推断
- `未知` = 明确未知，需要运行时或外部信息补足

---

## 1. 面板地址输入、校验与持久化

| 步骤 | 证据类型 | sourceRefs | 置信度 |
| --- | --- | --- | --- |
| 登录页存在面板地址输入项 | code | `app/src/main/java/com/slte/app/ui/screen/login/LoginScreen.kt:147` | 高 |
| 地址必填 + 必须可归一化为合法 https 地址 | code | `app/src/main/java/com/slte/app/ui/screen/login/LoginViewModel.kt:181-185` | 高 |
| 提交时写入持久化（地址 + 后端类型） | code | `app/src/main/java/com/slte/app/ui/screen/login/LoginViewModel.kt:187-191` | 高 |
| 地址与已保存值不同时弹二次确认 | code | `app/src/main/java/com/slte/app/ui/screen/login/LoginViewModel.kt:116-126`、`:244-247` | 高 |
| 无内置默认面板地址，未配置时为空串快速失败 | code + document | `app/src/main/java/com/slte/app/data/local/ApiUrlStore.kt:26-33`、`:47-49`；`README.md`（"面板地址完全由用户掌控"） | 高 |
| 地址存于加密 SharedPreferences | code | `app/src/main/java/com/slte/app/data/local/ApiUrlStore.kt:37`（`SecurePreferences.create`） | 高 |

## 2. 后端类型探测（XiaoV2b / Xboard）

| 步骤 | 证据类型 | sourceRefs | 置信度 |
| --- | --- | --- | --- |
| 类型未知时同步探测 | code | `app/src/main/java/com/slte/app/ui/screen/login/LoginViewModel.kt:389-397` | 高 |
| 探测请求：`GET {panel}/api/v1/guest/comm/config`，5s 连接/读取超时 | code | `app/src/main/java/com/slte/app/ui/screen/login/LoginViewModel.kt:400-419` | 高 |
| 判定信号：`is_captcha` / `captcha_type` / `turnstile_site_key` → xboard | code | `app/src/main/java/com/slte/app/ui/screen/login/LoginViewModel.kt:417-419` | 高 |
| SSRF 防护：私有/保留地址不自动探测 | code | `app/src/main/java/com/slte/app/ui/screen/login/LoginViewModel.kt:393-395` | 高 |
| 探测失败则中止登录并报错 | code | `app/src/main/java/com/slte/app/ui/screen/login/LoginViewModel.kt:259-268` | 高 |

## 3. 登录与凭据

| 步骤 | 证据类型 | sourceRefs | 置信度 |
| --- | --- | --- | --- |
| 登录调用链 | code | `AuthRepository.kt:56-67` → `DualBackendAuthApi.kt:36-49` → `BackendAdapterFactory.kt:79-108` | 高 |
| 双后端适配器同时实例化，按 `ApiUrlStore.backendType` 每次请求路由 | code | `app/src/main/java/com/slte/app/data/remote/DualBackendAuthApi.kt:29-44` | 高 |
| 登录成功后置会话态并清订阅缓存 | code | `app/src/main/java/com/slte/app/data/repository/AuthRepository.kt:60-66` | 高 |
| 凭据字段映射（`token` → `authData` / `subscribeToken`） | code | `app/src/main/java/com/slte/app/data/repository/AuthRepository.kt:189-195` | 高 |
| **凭据注入白名单闸门**：仅当 host 命中 BuildConfig 白名单或当前面板主机（含子域）才加 `Authorization` | code | `app/src/main/java/com/slte/app/data/remote/AuthInterceptor.kt:88-114`；`AllowedHosts.kt:9-27`；`ApiUrlStore.kt:85-90` | 高 |
| 拦截器顺序：先 `ApiFailoverInterceptor` 改写主机，再 `AuthInterceptor` 判白名单 | code | `app/src/main/java/com/slte/app/data/remote/BackendAdapterFactory.kt:129-130` | 高 |
| 面板返回的账号/计费/结算状态属于外部系统行为 | — | 仅客户端可见契约（请求与解析），**服务端不在此仓库内** | 未知 |

## 4. 套餐与额度

| 步骤 | 证据类型 | sourceRefs | 置信度 |
| --- | --- | --- | --- |
| 订阅信息拉取（TTL 缓存 + Mutex 去重 + 会话一致性校验） | code | `app/src/main/java/com/slte/app/data/repository/SubscribeRepository.kt:64-106` | 高 |
| 请求与解析：`fetchSubscribeInfo()` → 领域模型 | code | 同上 `:78-97`；`DualBackendAuthApi.kt:77`；`SubscribeRepository.kt:182-190` | 高 |
| 领域换算：`usedTraffic = upload + download` | code | `app/src/main/java/com/slte/app/data/repository/SubscribeRepository.kt:186` | 高 |
| 无套餐时阻止连接并引导续费 | code | `app/src/main/java/com/slte/app/ui/screen/main/MainScreen.kt:120-127`；`MainViewModel.kt:306` | 高 |
| 面板侧额度扣减/重置规则 | — | 外部系统，本地无证据 | 未知 |

## 5. 订阅配置获取

| 步骤 | 证据类型 | sourceRefs | 置信度 |
| --- | --- | --- | --- |
| 候选订阅地址：账号下发地址（须 https）→ 兜底 `apiBaseUrl + SUBSCRIBE_PATH + ?token=` | code | `app/src/main/java/com/slte/app/data/remote/SubscribeSourceImpl.kt:95-114`；`BackendAdapterFactory.kt:28-38`；`KernelConfig.kt:282` | 高 |
| 逐候选回退，全失败才返回 null | code | `app/src/main/java/com/slte/app/data/remote/SubscribeSourceImpl.kt:31-57` | 高 |
| 账号下发地址非 https 则拒绝（记日志） | code | `app/src/main/java/com/slte/app/data/remote/SubscribeSourceImpl.kt:107-114` | 高 |
| 从响应头 `profile-title` / `profile-web-page-url` 提取站点品牌 | code | `app/src/main/java/com/slte/app/data/remote/SubscribeSourceImpl.kt:59-85` | 高 |
| **订阅主机可能不是面板主机**（内容来自第三方域名） | code | 同上 `:107-114`（仅校验 scheme，未校验 host 白名单） | 高 |
| 订阅更新入口：仪表盘加载时的静默更新 | code | `MainViewModel.kt:84`；`SubscriptionUpdater.kt:102-129` | 高 |
| 购买后刷新 | code | `SubscriptionUpdater.kt:131-199`；`MainViewModel.kt:286-290` | 高 |

## 6. 大小上限与有效性校验

| 步骤 | 证据类型 | sourceRefs | 置信度 |
| --- | --- | --- | --- |
| 响应体强制 20MB 上限，超限拒绝 | code | `app/src/main/java/com/slte/app/kernel/KernelConfig.kt:156-172`、`:174-190`、`:277` | 高 |
| 有效性判定：非空、无控制字符、非 HTML/JSON、存在订阅入口键 | code | `app/src/main/java/com/slte/app/kernel/SubscriptionSanitizer.kt:11-18`；`SanitizerRules`（`SUBSCRIBE_ENTRY_KEY`） | 高 |
| 边界用例有测试覆盖（HTML/JSON/BOM/控制字符等） | code | `app/src/test/java/com/slte/app/kernel/SubscriptionSanitizerBoundaryTest.kt:132-165` | 高 |

## 7. 配置净化（安全边界核心）

| 步骤 | 证据类型 | sourceRefs | 置信度 |
| --- | --- | --- | --- |
| 净化步骤：BOM 剥离 → 顶层键去缩进 → 引号键归一 → 清零顶层端口 → 中和控制面 → 清理 subtitle 模式 → 注入健康检查 → 注入白名单直连规则 + FakeIP 过滤 | code | `app/src/main/java/com/slte/app/kernel/SubscriptionSanitizer.kt:20-38` | 高 |
| **失败关闭**：关键步骤失败或清洗后仍有危险顶层键 → 返回空串，放弃输出 | code | `app/src/main/java/com/slte/app/kernel/SubscriptionSanitizer.kt:40-47` | 高 |
| 净化失败即不写文件（`takeIf { isNotBlank }`） | code | `app/src/main/java/com/slte/app/kernel/KernelConfig.kt:231-234`、`:103-112` | 高 |
| 直连域名 = 当前面板域名 + 远程配置直连域 | code | `app/src/main/java/com/slte/app/kernel/KernelConfig.kt:222-240` | 高 |
| 直连域为空时降级：跳过直连规则注入，其余流程照常 | code | `app/src/main/java/com/slte/app/kernel/KernelConfig.kt:105-107`、`:135-137` | 高 |
| 幂等性（重复净化不再变化）有测试 | code | `app/src/test/java/com/slte/app/SubscriptionSanitizerTest.kt:603-604` | 高 |
| `tun.enable` 被强制置 false（由客户端掌控隧道开关） | code | `app/src/test/java/com/slte/app/SubscriptionSanitizerTest.kt:532-533` | 高 |

## 8. 配置写入与 profile 身份

| 步骤 | 证据类型 | sourceRefs | 置信度 |
| --- | --- | --- | --- |
| 首次导入写 `pending/<uuid>/config.yaml` 后 `commit` | code | `app/src/main/java/com/slte/app/kernel/KernelConfig.kt:81-86`、`:103-112` | 高 |
| 更新写 `imported/<uuid>/config.yaml` | code | `app/src/main/java/com/slte/app/kernel/KernelConfig.kt:131-151` | 高 |
| 内容未变化则跳过内核重载 | code | `app/src/main/java/com/slte/app/kernel/KernelConfig.kt:139-143` | 高 |
| 原子写：临时文件 + `fd.sync()` + rename（含 Windows 覆盖退避） | code | `app/src/main/java/com/slte/app/kernel/KernelConfig.kt:242-262` | 高 |
| profile 身份：`Polaris-<sha256(email)>`，`Type.Url`，`source = apiBaseUrl + SUBSCRIBE_PATH` | code | `app/src/main/java/com/slte/app/kernel/KernelConfig.kt:25-29`、`:81`、`:282` | 高 |
| 同账号旧 profile 清理 + 24h 过期 pending 目录清理 | code | `app/src/main/java/com/slte/app/kernel/KernelConfig.kt:73-79`、`:264-273` | 高 |
| 处理全程切 IO 线程并持 `profileMutex` | code | `app/src/main/java/com/slte/app/kernel/KernelConfig.kt:58-66` | 高 |

## 9. 跨进程通知与内核装载

| 步骤 | 证据类型 | sourceRefs | 置信度 |
| --- | --- | --- | --- |
| 变更后广播 `ACTION_PROFILE_CHANGED`（含 uuid） | code | `app/src/main/java/com/slte/app/kernel/KernelConfig.kt:93-98`、`:146-149` | 高 |
| 内核侧监听并 `Clash.load(importedDir/<uuid>)` | code | `kernel-service/src/main/java/com/github/kr328/clash/service/clash/module/ConfigurationModule.kt:30-99`（load 在 `:81`） | 高 |
| 装载后回写选择器、`StatusProvider.currentProfile`，并发 `PROFILE_LOADED` | code | 同上 `:83-91` | 高 |
| 激活的 profile 记录缺失时**不抛异常**（避免内核服务退出导致 VPN 静默断开） | code | 同上 `:68-77` | 高 |
| profile 记录持有 `ageSecretKey`（配置加密键） | code | `kernel-service/.../ProfileManager.kt:41-56`、`:96-134`；`ConfigurationModule.kt:79` | 高 |
| **首次导入不会走内核侧直抓**：`force && processingDir/config.yaml 存在` → 跳过 `fetchProfile` | code | `kernel-service/src/main/java/com/github/kr328/clash/service/ProfileProcessor.kt:57-64` | 高 |
| 内核侧 `ProfileProcessor.update()` 无条件 `fetchProfile`（无脱敏） | code | `kernel-service/src/main/java/com/github/kr328/clash/service/ProfileProcessor.kt:105-148`（fetch 在 `:123`、`:159`） | 高 |
| `ProfileManager.update()` 已被改为空实现（注释说明订阅更新统一由 App 端负责） | code | `kernel-service/src/main/java/com/github/kr328/clash/service/ProfileManager.kt:135-137` | 高 |

### 9.1 绕过净化的理论路径 — 可达性核验

图上标为"当前不可达"的依据（逐项静态核验，非仅依赖代码注释）：

| 核验项 | 方法 | 结果 |
| --- | --- | --- |
| 是否存在 AlarmManager 排程 | 全仓 grep `setExact` / `setRepeating` / `setAndAllowWhileIdle` | **无匹配** |
| 是否存在 JobScheduler / WorkManager 定时任务 | 全仓 grep `JobScheduler` / `WorkManager` / `PeriodicWorkRequest` | **无匹配** |
| `ProfileReceiver.pendingIntentOf` 是否有排程调用者 | grep `pendingIntentOf` 全部调用点 | 仅被 `cancelNext` 使用（`ProfileReceiver.kt:47-64`） |
| `ACTION_PROFILE_REQUEST_UPDATE` 是否有生产者 | grep 全部引用 | 仓库内无生产者；仅 `ProfileReceiver` 自身构造 intent（`ProfileReceiver.kt:54`） |
| `ProfileWorker.run()` 是否有调用者 | grep `ProfileWorker` | 仅 manifest 声明；`onStartCommand` 无外部触发源 |
| 外部可否触发该 receiver | manifest 声明 | `exported=true`，但受 `android:permission="${applicationId}.permission.RECEIVE_BROADCASTS"` 保护（`kernel-service/src/main/AndroidManifest.xml:59-72`） |

结论：在**当前仓库范围内**，`ProfileProcessor.update()` 这条无脱敏直抓路径没有生产者，图标为"不可达/保留"。残余风险与需要持续守护的点见 `first-connect-flow.risks-and-gaps.md`。

## 10. 启动隧道

| 步骤 | 证据类型 | sourceRefs | 置信度 |
| --- | --- | --- | --- |
| UI 三重前置闸门：有套餐 → 申请通知权限 → `VpnService.prepare` 授权 | code | `app/src/main/java/com/slte/app/ui/screen/main/MainScreen.kt:119-136`、`:148-158` | 高 |
| `toggleConnection`：先 `ensureProfile()`，成功才 `startVpn()` | code | `app/src/main/java/com/slte/app/ui/screen/main/MainViewModel.kt:312-344` | 高 |
| 连接中再次点击 = 取消（避免"连不上也关不掉"） | code | `app/src/main/java/com/slte/app/ui/screen/main/MainViewModel.kt:294-304` | 高 |
| 启动即启动前台服务 `TunService`；停止走 `ACTION_CLASH_REQUEST_STOP` 广播 | code | `app/src/main/java/com/slte/app/kernel/KernelManager.kt:240-250` | 高 |
| `TunService : VpnService`，独立运行时循环（close/config/network/restart 模块） | code | `kernel-service/src/main/java/com/github/kr328/clash/service/TunService.kt:30-105` | 高 |
| TUN 设备参数：网关/子网、IPv6、bypass 私网路由、DNS 32/128 路由、应用黑白名单 | code | `kernel-service/src/main/java/com/github/kr328/clash/service/TunService.kt:147-189` | 高 |
| 重复启动保护：`serviceRunning` 已置位则 `stopSelf` | code | `kernel-service/src/main/java/com/github/kr328/clash/service/TunService.kt:107-119` | 高 |
| 多进程拓扑：`RemoteService` 与 `ProfileWorker` 跑在 `:background` 进程 | code | `kernel-service/src/main/AndroidManifest.xml:34-45` | 高 |
| 绑定丢失退避重绑：1s→30s，最多 5 次 | code | `app/src/main/java/com/slte/app/kernel/KernelManager.kt:188-210`、`:262-268` | 高 |
| 连接状态以 `StatusProvider.METHOD_CURRENT_PROFILE` 的 ContentProvider call 为准 | code | `app/src/main/java/com/slte/app/kernel/KernelManager.kt:216-238` | 高 |
| 内核日志经 `ILogObserver` 回流并按脱敏规则落日志 | code | `app/src/main/java/com/slte/app/kernel/KernelManager.kt:73-78`、`:95-96` | 高 |

## 11. 隧道就绪探测

| 步骤 | 证据类型 | sourceRefs | 置信度 |
| --- | --- | --- | --- |
| `awaitTunnelReady` 轮询 `queryTunnelState` / `queryProxyGroupNames` | code | `app/src/main/java/com/slte/app/kernel/KernelReadiness.kt:20-38` | 高 |
| 看门狗：内核保持连接期间持续等待（生产约 5 分钟上限），就绪即可点亮 | code | `app/src/main/java/com/slte/app/ui/screen/main/MainViewModel.kt:194-214` | 高 |
| 未就绪期间 UI 保持"连接中"且可取消 | code | 同上 `:200-219`；`MainViewModel.kt:294-304` | 高 |
| 大订阅场景下"仅 ACTION_CLASH_STARTED"不足以代表可用 | code + document | `MainViewModel.kt:194-199`（注释记录了此前的卡死缺陷） | 高 |

## 12. 流量回流（两条独立链路）

### 12.1 内核实时秒级流量（本机，不经面板）

| 步骤 | 证据类型 | sourceRefs | 置信度 |
| --- | --- | --- | --- |
| App 侧轮询（ioDispatcher 上执行 AIDL 同步调用） | code | `app/src/main/java/com/slte/app/ui/screen/main/MainViewModel.kt:116-156` | 高 |
| 取累计与秒级 blip 两个快照 | code | 同上 `:126`、`:130` | 高 |
| 会话流量 = 累计差值；网速直接读内核 blip（避免差分低估） | code | 同上 `:120-142`、`:127-130` | 高 |
| 解码：高 32 位上传 / 低 32 位下载，2bit 指数 + 30bit 数据，÷100 精度 | code | `app/src/main/java/com/slte/app/kernel/KernelProxyTraffic.kt:12-42` | 高 |
| IPC 契约 | code | `kernel-service/.../remote/IClashManager.kt:17-20`；`ClashManager.kt:41-46` | 高 |
| JNI 入口 | code | `kernel-core/src/main/cpp/main.c:66-86` | 高 |
| 精度因子 | code | `kernel-core/src/main/cpp/bridge_helper.c:11` | 高 |
| 内核侧 1 秒 ticker + `Swap(0)` 维护 blip | code | `kernel-core/src/foss/golang/clash/tunnel/statistic/manager.go:115-122` | 高 |
| 常驻通知同样消费该数据 | code | `kernel-service/.../clash/module/DynamicNotificationModule.kt:69` | 高 |
| 隧道断开即清零会话字段与历史缓冲 | code | `app/src/main/java/com/slte/app/ui/screen/main/MainViewModel.kt:170-192` | 高 |

### 12.2 面板侧按日用量（外部系统）

| 步骤 | 证据类型 | sourceRefs | 置信度 |
| --- | --- | --- | --- |
| 独立链路：`TrafficRepository.fetchTrafficLog()` → 面板 API | code | `app/src/main/java/com/slte/app/data/repository/TrafficRepository.kt:17-19`；`DualBackendAuthApi.kt:162` | 高 |
| 面板侧统计口径与结算规则 | — | 外部系统，本地无证据 | 未知 |

## 13. 会话失效与停 VPN

| 步骤 | 证据类型 | sourceRefs | 置信度 |
| --- | --- | --- | --- |
| 401 一律视为会话失效；403 仅当为面板 JSON 且含失效关键词才算 | code | `app/src/main/java/com/slte/app/data/remote/AuthInterceptor.kt:44-48`、`:132-148` | 高 |
| 边缘拦截页（Cloudflare/WAF HTML）与空响应体**不算**失效 → 不清会话、不停 VPN | code | `app/src/main/java/com/slte/app/data/remote/AuthInterceptor.kt:139-147` | 高 |
| 失效后清会话并发 `authErrorEvents`；登出会触发 `stopVpn()` | code | `AuthInterceptor.kt:124-127`；`app/src/main/java/com/slte/app/data/local/SessionManager.kt:102` | 高 |
| 该分支源于 2026-09-20 线上事故（注释记录了事故经过） | code + document | `app/src/main/java/com/slte/app/data/remote/AuthInterceptor.kt:37-43` | 高 |

---

## 未纳入本流程的相关能力（避免读者误认为遗漏）

以下属同一产品但不在"首次连接"主路径内，本流程不展开：礼品卡兑换、邀请返利、工单、公告、订单与支付、应用内更新（`remote.json` 多源容灾 + SHA-256）。如需要，可另起一条业务流（分别对应 `flow-visualizer` 的"购买→生效"与 `deployment-topology-analyzer` 的发布链）。