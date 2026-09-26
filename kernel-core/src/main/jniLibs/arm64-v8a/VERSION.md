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

## 注意

- 本 so 为**自定义构建**，包含上游 mihomo 没有的本地 outbound 补丁——**不能**直接用上游 ClashMetaForAndroid APK 里的 so 替换，会丢失这些协议。
- 升级内核时需要：更新 Go 源（保留本地补丁）→ 本地重建 so → 更新本文件的版本记录。
- 目前仅 arm64-v8a；如需覆盖 32 位真机，需补 armeabi-v7a 构建。
