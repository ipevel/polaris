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
- 构建命令：`GOOS=android GOARCH=arm64 CGO_ENABLED=1
  CC=<NDK>/toolchains/llvm/prebuilt/windows-x86_64/bin/aarch64-linux-android28-clang.cmd
  go build -tags "android cmfa with_gvisor" -buildmode=c-shared -o libclash.so ./native`
- 头文件：`jniLibs/arm64-v8a/libclash.h` 与 `cpp/libclash.h` 导出符号与本次构建逐一比对
  一致（仅 cgo 行号注释差异），故未替换；`.so` 摘要见同目录 SHA256SUMS。

## 注意

- 本 so 为**自定义构建**，包含上游 mihomo 没有的本地 outbound 补丁——**不能**直接用上游 ClashMetaForAndroid APK 里的 so 替换，会丢失这些协议。
- 升级内核时需要：更新 Go 源（保留本地补丁）→ 本地重建 so → 更新本文件的版本记录。
- 目前仅 arm64-v8a；如需覆盖 32 位真机，需补 armeabi-v7a 构建。
