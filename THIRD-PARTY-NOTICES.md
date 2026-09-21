# 第三方声明（Third-Party Notices）

本项目（北辰 Polaris）基于并使用了下列开源项目与资源。各组件的许可证与来源如下；本项目的许可证见根目录 `LICENSE`（GPL-3.0）。

## 1. 派生项目（GPL-3.0）

- **mihomo（Clash.Meta 内核）** — https://github.com/MetaCubeX/mihomo
  - 内核完整源码位于 `kernel-core/src/foss/golang/clash/`，随本仓库一并提供（GPL-3.0 §6 完整对应源码义务）；其上游 `LICENSE` 全文随该目录保留。
  - 预编译产物 `libclash.so`（arm64-v8a）为上述源码的本地 fork 构建（见 `kernel-core/src/main/jniLibs/arm64-v8a/VERSION.md`），按 GPL-3.0 分发。
- **Clash Meta for Android（CMA）** — https://github.com/MetaCubeX/ClashMetaForAndroid
  - `kernel-common`、`kernel-service` 模块及 `kernel-core` 的部分 Android 层代码派生自 CMA（对应上游包名 `com.github.kr328.*`）。这些文件保留其上游 GPL-3.0 授权；上游版权声明由本声明统一保留。
- **shgnx/slte** — https://github.com/shgnx/slte
  - 本项目的基础代码派生自上游 SLTE 项目，特此致谢并保留其 GPL-3.0 授权。

## 2. 随包分发的数据与美术资源

- **GeoIP / ASN 数据**（`app/src/main/assets/geoip.metadb`、`ASN.mmdb`、`geosite.dat`，合计约 25MB）
  - 由 mihomo 生态常用的社区构建数据（MetaCubeX/meta-rules-dat 系）提供，遵循其上游分发条款；其中 MaxMind GeoLite2 派生数据的使用遵循 [MaxMind GeoLite2 EULA](https://www.maxmind.com/en/geolite2/eula)（含归属要求）。确切构建版本随内核资源清单管理；再分发本项目时请一并保留本声明。
- **国旗图标**（`app/src/main/assets/flags/`，257 个 SVG）
  - 来自 [lipis/flag-icons](https://github.com/lipis/flag-icons)，MIT License。© 2011+ lipis。
- **Lottie 动画**（`app/src/main/res/raw/loading.json`）与 **TGS 贴纸**（`app/src/main/assets/stickers/`）
  - 项目随包美术资源，Lottie 渲染引擎来自 [airbnb/lottie-android](https://github.com/airbnb/lottie-android)（Apache-2.0）。

## 3. 主要依赖（Gradle，运行期）

以下为主分支构建的运行期直接依赖及其许可证（仅列关键项；传递依赖请以 POM 为准）：

| 组件 | 许可证 |
| --- | --- |
| AndroidX（core-ktx / lifecycle / activity-compose / compose BOM / material3 / navigation / hilt-navigation-compose / webkit / room） | Apache-2.0 |
| Kotlin 标准库 / kotlinx-coroutines / kotlinx-serialization | Apache-2.0 |
| Dagger/Hilt | Apache-2.0 |
| Retrofit 2 / OkHttp 4 | Apache-2.0 |
| Coil 3 | Apache-2.0 |
| MikePenz multiplatform-markdown-renderer | Apache-2.0 |
| airbnb/lottie-android | Apache-2.0 |
| com.maxmind.db:maxmind-db | Apache-2.0 |
| com.github.kr328:kaidl / rikkax-multiprocess | Apache-2.0 |
| androidx.security:security-crypto | Apache-2.0 |

仅构建/测试期（不进入分发物）：JUnit 4（EPL-1.0）、MockK（Apache-2.0）、Robolectric（MIT）、SnakeYAML（Apache-2.0）、ktlint Gradle 插件（MIT）。

## 4. Go 内核传递依赖

`libclash.so` 内嵌 mihomo 的 Go 传递依赖（metacubex fork 系、sing-* 系、quic-go、tailscale、zerotier 等）。逐库许可清单与再分发兼容性核验见仓库审计记录；GPL-3.0 §6 要求的完整对应源码已随 vendored 源码树提供。重点 fork（metacubex/quic-go、metacubex/tailscale、metacubex/zerotier-go）相对上游的许可变更请以各自仓库声明为准。

## 5. 桌面分发运行时

桌面安装器捆绑 Eclipse Temurin JRE（GPLv2 with Classpath Exception，`runtime/legal/` 随包保留）与 [wintun.dll](https://www.wintun.net/)（随包分发条款见 wintun.net）。

---

GPL-3.0 要求：任何基于本项目及上述 GPL 组件构建的分发物必须开源、保留版权声明，并按 GPL-3.0 提供完整源代码。
