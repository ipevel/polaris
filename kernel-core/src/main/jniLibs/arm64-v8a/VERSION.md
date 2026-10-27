# libclash.so 构建记录

| 项 | 值 |
|---|---|
| 文件 | `libclash.so`（arm64-v8a） |
| Go 内核 | metacubex/mihomo v1.19.33（fork 目录：`kernel-core/src/foss/golang`，与上游同源） |
| GIT_VERSION | `mihomo_custom` |
| 本地补丁 | 增补 anytls/masque/openvpn/tailscale/zerotier 等 outbound（见 `kernel-core/src/foss/golang/clash`） |
| 本地桥接 | `native/tunnel/urltest.go`：按节点 urlTest + 失败分类（offline/timeout）；`proxies.go` 组查询带 `measured` 标记（区分"没测过"与"测了失败"），供离线标识与测速流提前收尾使用 |
| 配置补丁 | `process.go` 开启 `Profile.StoreSelected`（组选择持久化到 cache.db）；清空 `GeoXUrl`/`NTP`（订阅不得指定 geo 库下载源与 NTP 服务器，回退内置默认） |
| 泄漏修复 | `native/tunnel.go` `healthCheck` 补 `C.release_object`（原实现每次组健康检查泄漏一个 JNI 全局引用，常驻进程无界累积） |
| 分类修复 | `native/tunnel/urltest.go` 错误分类改大小写不敏感（第三方库 "Connection refused" 此前误判为超时） |
| 构建方式 | Go NDK 交叉编译，产物手工放回本目录 |

## 重建命令（2026-10-25，升级 v1.19.30 → v1.19.33）

```bash
cd kernel-core/src/main/golang
CGO_ENABLED=1 GOOS=android GOARCH=arm64 \
  CC=$NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android28-clang \
  go build -buildmode=c-shared -tags "android cmfa with_gvisor" -trimpath \
  -ldflags "-s -w -X github.com/metacubex/mihomo/constant.Version=v1.19.33" \
  -o libclash.so ./native
```

生成产物同时覆盖 `kernel-core/src/main/cpp/libclash.h` 与本目录的 `libclash.h`，
并把新摘要写回同目录 `SHA256SUMS`。

## 注意

- 本 so 为**自定义构建**，包含上游 mihomo 没有的本地 outbound 补丁——**不能**直接用上游 ClashMetaForAndroid APK 里的 so 替换，会丢失这些协议。
- 升级内核时需要：更新 Go 源（保留本地补丁）→ 本地重建 so → 更新本文件的版本记录。
- 目前仅 arm64-v8a；如需覆盖 32 位真机，需补 armeabi-v7a 构建。
