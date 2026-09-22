# 关于 src/foss

**`foss` 不是 Gradle flavor（构建变体），与分发渠道、付费/免费版本无关。**

本目录是随本仓库 vendored 的上游自由内核（mihomo，GPL-3.0）**完整源码树**，位于 `src/foss/golang/clash/`：

- 依据 GPL-3.0 §6「完整对应源码」义务，与预编译产物 `kernel-core/src/main/jniLibs/arm64-v8a/libclash.so`（本地 fork 构建，版本说明见同目录 `VERSION.md`）一并分发；
- 上游 `LICENSE` 全文随本目录保留，第三方声明见仓库根目录 [THIRD-PARTY-NOTICES.md](../../THIRD-PARTY-NOTICES.md)；
- 本仓库对上游的修改以补丁链形式维护在 `kernel-core/src/main/golang/native/`（如 `config/` 下的直连域名与流程定制），请勿直接改动本目录内的 vendored 代码。

目录名沿用自由软件社区惯例：foss = free and open-source software，意指「完整源码树」，与 `.gitignore`、Gradle 配置、构建类型均无关联。
