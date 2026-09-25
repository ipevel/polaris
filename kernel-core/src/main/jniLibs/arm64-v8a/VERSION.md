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

## 注意

- 本 so 为**自定义构建**，包含上游 mihomo 没有的本地 outbound 补丁——**不能**直接用上游 ClashMetaForAndroid APK 里的 so 替换，会丢失这些协议。
- 升级内核时需要：更新 Go 源（保留本地补丁）→ 本地重建 so → 更新本文件的版本记录。
- 目前仅 arm64-v8a；如需覆盖 32 位真机，需补 armeabi-v7a 构建。
