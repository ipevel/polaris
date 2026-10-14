# libclash.so 构建记录

| 项 | 值 |
|---|---|
| 文件 | `libclash.so`（arm64-v8a） |
| Go 内核 | metacubex/mihomo v1.19.30（本地 fork：`kernel-core/src/foss/golang`） |
| GIT_VERSION | `mihomo_custom` |
| 本地补丁 | 增补 anytls/masque/openvpn/tailscale/zerotier 等 outbound（见 `kernel-core/src/foss/golang/clash`） |
| 构建方式 | gomobile/Go NDK 交叉编译，产物手工放回本目录 |

## 2026-09-25 重建记录（本地分流方案）

- 变更：`native/config` 新增 `patchLocalRouting`（处理器链 patchOverride 之后）与子包
  `native/config/routing`（Karing 式本地分流生成，见 `docs/local-routing-design.md`）；
  `patchProfile` 的 StoreSelected 改为随本地分流开关；修复 `override.go` 存量编译破损
  （stdlib log 无 Warnln，改用 mihomo log）。
- 二次重建（同日缺陷修复）：routing.json 新增 `direct_domains`（App 侧注入真实面板
  域名清单，优先于编译期占位——否则本地生成规则会整体替换掉清洗注入的直连规则）；
  provider 预播种扩展名统一为 `.yaml`（mihomo 按声明 format 解析，与扩展名无关），
  与 App 侧 48 个种子文件名对齐。
- 构建命令：`GOOS=android GOARCH=arm64 CGO_ENABLED=1
  CC=<NDK>/toolchains/llvm/prebuilt/windows-x86_64/bin/aarch64-linux-android28-clang.cmd
  go build -tags "android cmfa with_gvisor" -buildmode=c-shared -o libclash.so ./native`
- 三次重建（2026-09-25，1.5.0 反馈修复）：分流规则组与自定义组改为 `include-all: true`
  （全部节点并入每条分流组），成员表补齐 自动选择/故障转移，使用户可为单个分类指定
  任意出口（跟随节点选择 / 自动 / 故障转移 / 直连 / 拦截 / 具体节点）。
- 头文件：`jniLibs/arm64-v8a/libclash.h` 与 `cpp/libclash.h` 导出符号与本次构建逐一比对
  一致（仅 cgo 行号注释差异），故未替换；`.so` 摘要见同目录 SHA256SUMS。

## 2026-09-25 重建记录（测速与健壮性修复）

- 变更：
  - `native/config/routing/routing_table.go`：`proxyGroupURL` 由明文
    `http://www.gstatic.com/generate_204` 改为与 mihomo `constant.DefaultTestURL`
    逐字一致的 `https://…`，并新增 `proxyGroupExpectedStatus = "204"`。
    原来组 URL 与 provider 默认 URL 不同，会让每个 proxy-provider 被登记一条
    额外 health-check（`healthcheck.go:url != hc.url` 才登记）→ 同一节点被两个
    URL 各测一次，而 UI 只读「最近有结果」的那条 → 延迟在两条历史之间抖动。
    另：留空 expected-status 时 mihomo 默认 `*`，任意响应（含拦截页 200）都算通。
  - `native/config/routing/routing_build.go`：结构组补 `expected-status`；
    自定义组名与直连域名接入 `validGroupName` / `validRuleDomain`（非法项跳过
    并告警，避免逗号导致 `RULE-SET` / `DOMAIN-SUFFIX` 字段错位进而整份配置加载失败）。
  - `native/config/routing/reserved.go`（新增）：生成前扫描面板节点名 / provider 名
    是否命中保留名（结构组 + 内置分流组 + `DIRECT/REJECT/COMPLETE/PASS/GLOBAL/default`），
    命中即 `NameCollisionError` → 复用既有「降级回面板配置」回退（mihomo 对重名是
    硬失败，会被订阅内容直接触发）。
  - `native/config/routing.go`：降级时写 `routing-degraded.json` 标记（成功应用时删除），
    App 侧读取后给可见提示。
  - `native/tunnel/proxies.go`：`Proxy` 新增 `tested` 字段（`delayTested`），把
    「从未测过」与「测过但不存活」区分开——两者此前都返回 `0xffff`，UI 只能把
    没测完显示成「超时」。
- 构建命令：`GOOS=android GOARCH=arm64 CGO_ENABLED=1
  CC=<NDK>/toolchains/llvm/prebuilt/windows-x86_64/bin/aarch64-linux-android28-clang.cmd
  go build -tags "android cmfa with_gvisor" -buildmode=c-shared -o libclash.so ./native`
- 头文件：`libclash.h` 已随本次构建刷新，导出符号（37 个）与 `cpp/libclash.h` 比对一致。
- 验证：`GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build -tags "android cmfa with_gvisor" ./native/...`
  通过；`go test ./native/config/routing/...` 通过（含新增的测速 URL / 命名冲突 /
  字符集校验 / 预算默认值断言）；`:app:verifyKernelBinary` 与 `:app:testDebugUnitTest` 见 CI 记录。

## 2026-09-26 重建记录（节点选择持久化修复）

- 变更：`native/tunnel/proxies.go` 的 `PatchSelector` 在 `s.Set(name)` 成功后再调用
  `cachefile.Cache().SetSelected(selector, name)`。此前该函数只改内存选中态，
  从不写 cachefile（写盘逻辑仅存在于 mihomo REST 路由层 `hub/route/proxies.go`），
  而 App 切节点完全走 JNI，导致 `SelectedMap()` 恒为空、重启/重登后选中态丢失。
  `SetSelected` 内部自带 `profile.StoreSelected` 门控，与内核启动时的恢复路径
  （`executor.patchSelectGroup` → `ForceSet`）条件一致；仅 `Set` 成功时落盘。
- 构建命令：`GOOS=android GOARCH=arm64 CGO_ENABLED=1
  CC=<NDK>/toolchains/llvm/prebuilt/windows-x86_64/bin/aarch64-linux-android28-clang.cmd
  go build -tags "android cmfa with_gvisor" -buildmode=c-shared -o libclash.so ./native`
- 头文件：新构建的 `libclash.h` 与本目录既有 `libclash.h` 逐字节一致，未替换；
  `.so` 摘要见同目录 SHA256SUMS。

## 2026-09-28 重建记录（分流组拖动排序 order 支持）

- 变更：`routing.json` 新增 `order`（内置分流组名的用户自定义顺序，App 侧拖动排序写入）。
  `native/config/routing/routing.go` 的 `State` 增加 `Order []string`（`json:"order"`）；
  `routing_table.go` 新增纯函数 `OrderedTable(order)`：order 中出现的组名按给定次序排在前，
  未出现的按 `Table` 默认顺序追加在后，未知/重复/空名一律忽略且绝不丢组，空 order 直接返回 `Table`；
  `routing_build.go` 的 `Build` 改为 `for _, item := range OrderedTable(state.Order)`。
  顺序同时决定「规则匹配优先级」与 App 侧（节点页 / 分流规则页）的组展示顺序，不影响各组开关。
- 构建命令：`GOOS=android GOARCH=arm64 CGO_ENABLED=1
  CC=<NDK>/toolchains/llvm/prebuilt/windows-x86_64/bin/aarch64-linux-android28-clang.cmd
  go build -tags "android cmfa with_gvisor" -buildmode=c-shared -o libclash.so ./native`
- 头文件：新构建的 `libclash.h` 与本目录既有 `libclash.h` 逐字节一致，未替换；
  `.so` 摘要见同目录 SHA256SUMS。
- 验证：`GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build -tags "android cmfa with_gvisor" ./native/...`
  通过；`go test ./native/config/routing/...` 通过（含新增的 `TestOrderedTable` /
  `TestBuildAppliesGroupOrder`）。

## 2026-10-09 重建记录（上游 shgnx/slte 内核健壮性跟进 + 节点离线标识）

- 变更（全部为上游 `shgnx/slte` 已修、Polaris 未跟的崩溃/泄漏/投毒面，逐 hunk 移植）：
  - `native/tunnel/urltest.go`（新增）＋ `native/tunnel.go` 新增 `//export urlTest`：
    对**单个节点**跑一次真实 URLTest 并按错误文本分类返回 `{"delay":N,"kind":K}`，
    `kind` 取 `""`（存活）/ `"timeout"`（抖动、需重试）/ `"offline"`（NXDOMAIN、连接被拒、
    无路由——域名层面的确定性失败）。应用层据此把「超时」与「离线」分开显示：
    延迟超时只说明这一次没通，不能据此判定节点已下线。
  - `kernel-core/src/main/cpp/libclash.h` 增 `extern char* urlTest(c_string name, int timeoutMs);`，
    `cpp/main.c` 增 `Java_..._Bridge_nativeUrlTest`（新增导出，其余函数体改动不涉及符号）。
  - `native/utils.go` `marshalJson`：序列化失败由 `panic(err.Error())` 改为回退
    `[]byte("{}")`。cgo 导出函数内的 panic 无法被 Kotlin 侧捕获，会直接终止常驻
    VPN 进程（用户表现为「内核突然没了」）。
  - `native/app.go`：`C.malloc(1024)` → `C.calloc(1, 1024)`，错误缓冲清零后再交给
    C 侧 `strncpy`（超长时 strncpy 不写 NUL）。
  - `native/tunnel.go` `healthCheck`：`C.complete(completable, nil)` 之后补
    `C.release_object(completable)`。原实现每次组健康检查泄漏一个 JNI 全局引用，
    常驻进程内无界累积；与 `updateProvider` 的成对释放范式对齐。
  - `native/config/process.go` `patchGeneral`：补 `cfg.NTP = config.DefaultRawConfig().NTP`。
    订阅不得指定 NTP 服务器（时间源劫持），回退内置默认。**注意** geox-url 不在此处
    复位——`patchGeoXUrl` 用 `officialGeoXUrls` 单独兜底，比无脑重置更明确。
- 未变更既有导出符号（仅在末尾新增 `urlTest`），因此 `libclash.h` 仍沿用 Polaris 的
  手工维护版（与 `cpp/libclash.h` 只差 cgo 样板：Go 1.23.4 起不再生成
  `_GoStringLen`/`_GoStringPtr` 声明，C 侧也无任何调用方）；本次只把新增的 `urlTest`
  声明按同一风格补进两个头文件。`cpp/libclash.h` 才是编译期实际包含的头，
  `jniLibs/arm64-v8a/libclash.h` 是随产物存档的镜像。
- 构建命令：`GOOS=android GOARCH=arm64 CGO_ENABLED=1
  CC=C:\Android\Sdk\ndk\28.2.13676358\toolchains\llvm\prebuilt\windows-x86_64\bin\aarch64-linux-android28-clang.cmd
  go build -tags "android cmfa with_gvisor" -buildmode=c-shared -o libclash.so ./native`
  （Go 1.23.4 windows/amd64；产物 75,181,976 字节）
- 验证：`GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build -tags "android cmfa with_gvisor" ./native/...`
  通过（EXIT=0）；`go vet -tags "android cmfa with_gvisor" ./native/...` 通过（EXIT=0，
  与 CI 同款）；`go test -count=1 ./native/config/routing/...` 通过
  （`ok cfa/native/config/routing 0.872s`）。
- 产物摘要：见同目录 `SHA256SUMS`（`2739cf53…5091aee`）。

## 2026-10-10 重建记录（移除两个被遮蔽的分流组）

- 变更：`native/config/routing/routing_table.go` 删除「📢 Google FCM」与
  「📢 苹果推送通知」两个分流组，以及只被它们引用的 `acl_googlefcm` provider 项与
  `applePushInlineRules`（12 条内联规则）；`app/src/main/assets/routing/providers/acl_googlefcm.yaml`
  同步删除，内置种子 48 → 47，分组 27 → 25（见 `docs/local-routing-design.md`）。
  原因：这两组排在「🌏 Google」与「🍎 苹果服务」之后，域名规则 100%、IP 规则 1 条被
  前面的组提前命中，分组开关设成什么出口都不生效——配置与实际行为不符。
  Google 统一走「🌏 Google」，苹果统一走「🍎 苹果服务」，不再单列。
- 构建命令：同上（Go 1.23.4 windows/amd64，`GOPROXY=off` 走本地模块缓存；
  产物 75,181,304 字节）。
- 验证：产物内**已不含** `📢 Google FCM` / `📢 苹果推送通知` / `GoogleFCM.list` /
  `push.apple.com` 四串（旧产物四串全在），字节级确认改动真的进了 so；
  `llvm-nm -D --defined-only` 比对旧/新产物**导出符号 156 = 156 完全一致**，
  因此 `libclash.h` 不替换（沿用 Polaris 手工维护版：Go 1.23.4 起不再生成
  `_GoStringLen`/`_GoStringPtr`，C 侧无任何调用方）。
- 产物摘要：见同目录 `SHA256SUMS`（`a87939f8…d220c0`）。

## 2026-10-13 重建记录（AI 分流组三合一 + 补上 10-11/12 未进包的规则改动）

- 变更：
  - `native/config/routing/routing_table.go`：「♊️ Google Gemini」「💬 Claude」「💬 OpenAI」
    三个组合并为一个「🤖 AI」（位置不变：📲 WhatsApp 之后、🐱 GitHub 之前；默认开、走代理、
    无 provider）。规则 = 新增 `GEOSITE,category-ai-!cn` + openai / anthropic / google-gemini
    三个分类 + 147 条内联域名兜底，共 151 条；并去掉旧 Gemini 组里的 `GEOSITE,google` 与
    `GEOSITE,google-play`——它们排在「🌏 Google」「🌏 Google Play」之前，把这两个组整体吃掉
    （开关设成什么都不生效），与 2026-10-10 移除 Google FCM / 苹果推送同因。合并后
    `www.google.com` / `accounts.google.com` / `drive.google.com` 重新落回「🌏 Google」。
  - 新增 `AIGroupName` / `legacyGroupAliases` / `canonicalGroupName`：`State.Groups` 与
    `State.Order` 里的旧组名在读取侧归一（任一旧名为开 → 新组为开；顺序取三者中最靠前的
    位置），避免一次改名把用户调过的开关与拖动顺序静默丢掉；显式的新组名优先于残留旧键。
  - `app/src/main/java/com/slte/app/kernel/RoutingGroups.kt`：Kotlin 镜像同步合并；顺带修
    一处漂移——`📲 Facebook` / `📲 WhatsApp` 原写成 `defaultOn = false`，内核表与 Windows 表
    都是 `true`（全新安装的安卓端这两个组会显示为关）。迁移落在 `RoutingStateStore.load()`
    与 `orderedGroups()`。
- **本产物同时补上了 2026-10-11/12 那 4 次 `routing_table.go` 改动**：上一个随包 .so
  （`a87939f8…d220c0`）是 2026-10-10 构建的，其后表改过 4 次都没有同步重建 .so。逐字节核对
  旧产物：里面**没有** `GEOSITE,cn` / `GEOIP,CN,{t},no-resolve` / `GEOSITE,geolocation-!cn` /
  `google-gemini` / `DOMAIN-SUFFIX,douyin.com,{t}`，三个 AI 组当时走的还是 `acl_gemini` /
  `acl_claude` / `gs_openai` provider——即 10-12 那批「分流规则改读本地 geosite/geoip + 国内
  直连补 GEOIP 兜底」此前从未进过随包内核。本次重建后这些串全部存在。
- 构建命令：`GOOS=android GOARCH=arm64 CGO_ENABLED=1
  CC=C:\Android\Sdk\ndk\28.2.13676358\toolchains\llvm\prebuilt\windows-x86_64\bin\aarch64-linux-android28-clang.cmd
  go build -tags "android cmfa with_gvisor" -buildmode=c-shared -o libclash.so ./native`
  （Go 1.23.4 windows/amd64，`GOPROXY=off` 走本地模块缓存；产物 75,190,528 字节）
- 验证：
  - `GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go vet -tags "android cmfa with_gvisor" ./native/...`
    通过（EXIT=0）；`go build`（同款环境变量）通过（EXIT=0）——与 CI 的 Go 门禁同款命令。
  - `go test -c` 编译通过（新增 `TestAIGroupMerge` / `TestLegacyAIGroupAliases`，以及
    `TestRoutingDecisionAccuracy` 里 Google 域名重新落回「🌏 Google」的断言）。
    **本机无法执行 Go 测试二进制**：Windows 已启用 Smart App Control，含 mihomo 的测试 exe
    被 code integrity 拦下（Microsoft-Windows-CodeIntegrity 事件 3077/3113，报
    "did not meet the Enterprise signing level requirements"）。Go 单测的运行时结论以 CI 的
    `go test -v -count=1 ./native/config/routing/...` 为准。
  - 产物内**已含** `🤖 AI` / `GEOSITE,category-ai-!cn` / `GEOSITE,google-gemini` /
    `DOMAIN-SUFFIX,chatgpt.com,{t}` … `DOMAIN-SUFFIX,tome.app,{t}`，且 AI 组不再带
    `GEOSITE,google`。
  - `llvm-nm -D --defined-only` 比对旧/新产物**导出符号 156 = 156 完全一致**，因此
    `libclash.h` 不替换（沿用 Polaris 手工维护版）。
- 产物摘要：见同目录 `SHA256SUMS`（`af293366…c76b8a`）。

## 2026-10-13 重建记录（「🌏 Google Play」挪到「🌏 Google」之前）

- 变更：`routing_table.go` / `RoutingGroups.kt` 把「🌏 Google Play」挪到「🌏 Google」之前
  （顺序即优先级）。原因：geosite 的 `google-play` 只有 10 条，其中 8 条
  （play.google.com、play.googleapis.com、play-lh.googleusercontent.com、
  redirector.c.play.google.com …）同时也在 `google` 分类里，排在后面就全被
  `GEOSITE,google` 先命中——真实域名覆盖为 0，那个开关设成什么都不生效，与
  2026-10-10 移除 Google FCM / 苹果推送、2026-10-13 合并 AI 组同因。挪序只会从
  `google` 分类抢走 4 条 Play 自己的域名（googleplay.com、xn--ngstr-lra8j.com、
  redirector.xn--ngstr-lra8j.com、redirector.c.play.google.com），两组 DefaultOut
  都是 proxy，连通性零影响，只改归类归属。
- 构建命令：同上一节（Go 1.23.4 windows/amd64，产物 75,190,528 字节）。
- 验证：
  - `go test -count=1 ./native/config/routing/...` **本次在本机真跑起来了**：24 个用例
    全 PASS，含 `TestRoutingDecisionAccuracy` 用真实 geosite 断言 `play.google.com` /
    `play.googleapis.com` / `play-lh.googleusercontent.com` 落「🌏 Google Play」，
    `www.google.com` / `accounts.google.com` / `drive.google.com` 落「🌏 Google」。
    **更正上一节的说法**：上一节记录写「本机无法执行 Go 测试二进制」——Smart App
    Control 的拦截时有时无（同一台机器、同一份工具链，后续构建的测试 exe 又能执行），
    上一节那两个用例（`TestAIGroupMerge` / `TestLegacyAIGroupAliases`）本次也一并真跑过，
    PASS。
  - `go vet` / `go build`（CI 同款环境变量）EXIT=0。
  - `llvm-nm -D --defined-only` 导出符号 156 = 156 与上一版完全一致，`libclash.h` 不替换。
- 产物摘要：见同目录 `SHA256SUMS`（`b15ad722…ab41c7`）。

## 2026-10-14 重建记录（分流表 23 组精简为 12 组、本地规则集 10 → 15 份）

- 变更：
  - `native/config/routing/routing_table.go`：`Table` 由 23 组精简为 **12 组**（顺序即优先级）：
    `📲 电报消息` / `🎧 TikTok` / `📲 社交媒体`（X+Instagram+Facebook+WhatsApp 四合一）/
    `📺 流媒体`（油管+奈飞二合一）/ `🤖 AI` / `🎮 游戏平台` / `Ⓜ️ 微软`（云盘+微软服务+Bing 三合一，
    默认 direct）/ `🍎 苹果服务`（direct）/ `🎯 国内直连`（直连组并入 B 站与网易音乐，direct）/
    `🌏 国外穿墙` / `🍃 应用净化`（默认关、block）/ `🛑 广告拦截`（block）。
    随表删除的组：`🐱 GitHub`、`🌏 Google Play`、`🌏 Google`、`Ⓜ️ 微软Bing`、`Ⓜ️ 微软服务`、
    `📺 哔哩哔哩`、`🎶 网易音乐` 等 11 个。
  - `Providers` 由 10 个 `acl_*` 改为 **15 个**本地 file provider，键名即规则仓库的产出名：
    `social` `streaming` `ai` `games` `telegram` `tiktok` `china-domain` `china-ip` `china-media`
    `onedrive` `apple` `proxy-gfwlist` `proxy-media` `banad` `banprogramad`；全部 `URL: ""`
    （本地 file provider，不再从远端拉）、`Behavior: "classical"`、`Format: "text"`，
    仅 `china-ip` 带 `NoResolve`。
  - `legacyGroupAliases` 扩到 **12 条**（三个 AI 旧名 + X/Instagram/Facebook/WhatsApp → 社交媒体 +
    油管/奈飞 → 流媒体 + 微软云盘 → 微软 + 哔哩哔哩/网易音乐 → 国内直连）。**刻意不映射**已取消的
    `🐱 GitHub` / `🌏 Google Play` / `🌏 Google` / `Ⓜ️ 微软Bing` / `Ⓜ️ 微软服务`——旧开关状态
    无处安放，硬映射会把「我关掉 Google」变成「我关掉整个穿墙」。
  - `app/src/main/assets/routing/providers/`：删掉 10 份 `acl_*.yaml`，换入 15 份 `<键>.yaml`
    （内容为 `ipevel/polaris-rules` 的 `dist/rules/<键>.list` 字节）。路径契约两端一致：
    Go 侧 `FileExt() == ".yaml"` + `providerSubPath == "polaris-rules"`，Windows 侧
    `seedPath == polaris-rules/<键>.yaml`。
  - `app/src/main/java/com/slte/app/kernel/RoutingGroups.kt`：Kotlin 镜像同步为 12 组、别名表 12 条；
    `RoutingGroupsMigrationTest.kt` / `RoutingTableParityTest.kt` 跟着改写（后者新增「别名表逐字一致」
    与「随包种子文件名 == 内核表 provider 键」两条契约）。
- 构建命令：`GOOS=android GOARCH=arm64 CGO_ENABLED=1
  CC=C:\Android\Sdk\ndk\28.2.13676358\toolchains\llvm\prebuilt\windows-x86_64\bin\aarch64-linux-android28-clang.cmd
  go build -tags "android cmfa with_gvisor" -buildmode=c-shared -o libclash.so ./native`
  （Go 1.23.4 windows/amd64，`GOPROXY=off` 走本地模块缓存；产物 75,190,376 字节）
- 验证：
  - `GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go vet -tags "android cmfa with_gvisor" ./native/...`
    EXIT=0；同环境 `go build` EXIT=0 —— 与 CI 的 Go 门禁同款命令。
  - `go test -v -count=1 ./native/config/routing/...` **24 个用例全 PASS**（1.685s），
    其中 `TestRoutingDecisionAccuracy` 用真实 geosite 跑，以
    `POLARIS_GEO_DIR=E:\AI\Github\polaris-rules\geo` +
    `POLARIS_PROVIDERS_DIR=app/src/main/assets/routing/providers` 打开真跑；
    含新写的 `TestMicrosoftGroupKeepsDirectOutbound`、`TestNoRemoteRuleSource`。
  - 逐字节核对产物：`📲 社交媒体` / `📺 流媒体` / `GEOSITE,category-ai-!cn` 存在；
    `🌏 Google Play` / `🐱 GitHub` / `Ⓜ️ 微软Bing` / `Ⓜ️ 微软服务` **均已消失**，
    `Ⓜ️ 微软云盘` 仍在（它是别名表里的旧名）。
  - `llvm-nm -D --defined-only` 比对旧/新产物**导出符号 156 = 156 完全一致**，因此
    `libclash.h` 不替换（生成版只是少了 cgo 的 C++ 守卫块、`(void)` 写法不同，
    `_GoStringLen` / `_GoStringPtr` 自 Go 1.23.4 起不再生成，C 侧无调用方）。
- 产物摘要：见同目录 `SHA256SUMS`（`4dcd946d…4a8fd7`）。

## 2026-10-14 重建记录（「Ⓜ️ 微软」组收窄）

- 变更：`native/config/routing/routing_table.go` 的「Ⓜ️ 微软」组内联规则由 7 条改为 13 条，
  去掉宽口径的 `GEOSITE,microsoft`（752 条）与 `GEOSITE,bing`（40 条），换成
  「云盘 + 登录 + 系统更新 + Azure」四类：`GEOSITE,onedrive`、
  `DOMAIN-SUFFIX,{docs.live.net,content.live.net,odc.officeapps.live.com,officeapps.live.com}`、
  `DOMAIN-SUFFIX,{login.live.com,login.microsoftonline.com,login.microsoft.com}`、
  `DOMAIN-SUFFIX,{update.microsoft.com,windowsupdate.microsoft.com,windowsupdate.com}`、
  `GEOSITE,azure`。组名、顺序、`DefaultOn`/`DefaultOut`（direct）、`Providers`（onedrive）均未动，
  表仍是 12 组 / 15 provider。
- 原因：2026-10-14 用户裁定「微软组只留云盘直连，其余走兜底」。宽口径的 `GEOSITE,microsoft`
  含 `github.com` / `githubusercontent.com` / `github.io` / `bing.com` / `office.com` /
  `outlook.com` / `teams.microsoft.com`，会把已取消的 GitHub 组重新吞成**直连**（国内等于不可用）。
  收窄后 GitHub 系列落回「🌏 国外穿墙」（`proxy-gfwlist` 本就含 10 条 github 规则，未新增规则），
  Bing / Office / Outlook / Teams / MSN 同样落回穿墙。
- 构建命令：`GOOS=android GOARCH=arm64 CGO_ENABLED=1
  CC=C:\Android\Sdk\ndk\28.2.13676358\toolchains\llvm\prebuilt\windows-x86_64\bin\aarch64-linux-android28-clang.cmd
  go build -tags "android cmfa with_gvisor" -buildmode=c-shared -o libclash.so ./native`
  （Go 1.23.4 windows/amd64，`GOPROXY=off` 走本地模块缓存；产物 75,191,160 字节）
- 验证：
  - `go test -count=1 ./native/config/routing/...` 全绿，其中 `TestRoutingDecisionAccuracy`
    用真实 geosite 跑（`POLARIS_GEO_DIR=app/src/main/assets` +
    `POLARIS_PROVIDERS_DIR=app/src/main/assets/routing/providers`），逐域落点与用户裁定表逐格相符；
    内核日志侧证 `onedrive => Ⓜ️ 微软, records: 19`、`azure => Ⓜ️ 微软, records: 151`、
    `geolocation-!cn => 🌏 国外穿墙, records: 27340`，`microsoft` / `bing` 两条 GeoSite 规则
    已不再挂在「Ⓜ️ 微软」组下。
  - `GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go vet -tags "android cmfa with_gvisor" ./native/...`
    EXIT=0；同环境 `go build` EXIT=0 —— 与 CI 的 Go 门禁同款命令。
  - 逐字节核对产物（反向对照，这是「收窄真进了二进制」的证据）：
    新规则串 `GEOSITE,azure,{t}` / `DOMAIN-SUFFIX,login.live.com,{t}` /
    `DOMAIN-SUFFIX,login.microsoftonline.com,{t}` / `DOMAIN-SUFFIX,login.microsoft.com,{t}` /
    `DOMAIN-SUFFIX,update.microsoft.com,{t}` / `DOMAIN-SUFFIX,windowsupdate.microsoft.com,{t}` /
    `DOMAIN-SUFFIX,windowsupdate.com,{t}` **各命中 1 次**；
    旧规则串 `GEOSITE,microsoft,{t}` / `GEOSITE,bing,{t}` **各 0 次**
    （替换前的产物正好相反：前者 1 / 1，后者 0）。
  - `llvm-nm -D --defined-only` 比对旧/新产物**导出符号 156 = 156 完全一致**，因此
    `libclash.h` 不替换。
- 产物摘要：见同目录 `SHA256SUMS`（`881529da…6ea804`）。

## 注意

- 本 so 为**自定义构建**，包含上游 mihomo 没有的本地 outbound 补丁——**不能**直接用上游 ClashMetaForAndroid APK 里的 so 替换，会丢失这些协议。
- 升级内核时需要：更新 Go 源（保留本地补丁）→ 本地重建 so → 更新本文件的版本记录。
- 目前仅 arm64-v8a；如需覆盖 32 位真机，需补 armeabi-v7a 构建。
