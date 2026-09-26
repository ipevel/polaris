# 贡献指南（Contributing Guide）

感谢关注 Polaris！欢迎通过 Issue 与 Pull Request 参与贡献。

## 行为准则

参与本项目即表示你同意遵守[行为准则](CODE_OF_CONDUCT.md)。求助与使用问题请走[支持渠道](SUPPORT.md)；安全问题请先看[安全政策](SECURITY.md)（勿开公开 Issue）。

## 许可证

本项目以 [GPL-3.0](LICENSE) 分发。提交 PR 即表示你的贡献将按 GPL-3.0 授权（并入同一作品整体许可），无需签署 CLA；基于本项目的衍生作品须同样以 GPL-3.0 开源并提供完整对应源码。

## 开发环境

JDK 17+、Android SDK（compileSdk 36）、NDK 28.2、CMake 3.22+，详见 [README](README.md#开发环境)。内核已预编译为 `libclash.so` 随仓库提供，常规改动无需本地 Go 环境。

## PR 前请跑完本地门禁

远程 CI 与以下门禁完全一致，先在本地全绿再提 PR，避免来回返工：

```bash
# 一次性生成 CI 调试签名（lint 会分析 release 变体，必须存在）
keytool -genkeypair -v -keystore ci-throwaway.keystore -alias ci-throwaway \
  -keyalg RSA -keysize 2048 -validity 1 \
  -storepass ci-throwaway -keypass ci-throwaway \
  -dname "CN=CI-Throwaway, OU=CI, O=CI, C=CN"

export POLARIS_RELEASE_STORE_FILE="$(pwd)/ci-throwaway.keystore"
export POLARIS_RELEASE_STORE_PASSWORD=ci-throwaway
export POLARIS_RELEASE_KEY_ALIAS=ci-throwaway
export POLARIS_RELEASE_KEY_PASSWORD=ci-throwaway

./gradlew :app:verifyKernelBinary :app:assembleDebug :app:testDebugUnitTest \
  :app:assembleDebugAndroidTest :app:minifyReleaseWithR8 \
  :app:verifyReleaseApiSurvivors :app:ktlintCheck :app:lintDebug
```

## 代码规范

- **静态检查**：ktlint（android_studio 风格，配置见 [.editorconfig](.editorconfig)）与 Android Lint 必须通过。
- **版权头**：新增源码文件须携带 `SPDX-License-Identifier: GPL-3.0-only` 版权头（从本仓库同类文件复制即可）。
- **依赖**：新增第三方依赖的许可证须与 GPL-3.0 兼容，并同步维护 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)；禁止引入网络明文传输。
- **日志**：敏感信息必须脱敏（走 `AppLog` 体系；`LogSanitizationGuardTest` 守卫会拦截未脱敏的 `.message` 日志）。
- **图标**：UI 图标统一经 `SlteIcons` 引用，禁止页面裸用 `Icons.*`。
- **when 穷尽**：sealed class 新增分支后须补齐所有 `when` 表达式。
- **签名与凭据**：任何密钥、密码、内网地址不得入库（`.gitignore` 已覆盖 `*.keystore`、`local.properties`，新增敏感文件请一并忽略）。

## 提交信息与文档规范

- 提交信息与所有文档统一遵循[**说明规范**](docs/writing-guide.md)。
- 摘要格式为 `type(scope): 摘要`，type 取 `feat` / `fix` / `docs` / `style` / `refactor` / `perf` / `test` / `build` / `ci` / `chore` / `revert`。
- 标题 ≤ 50 字符，不以 BOM 等不可见字符开头；一次提交只做一件事。
- 可选启用模板：`git config commit.template .gitmessage`。
- 改动功能或流程时，同步更新对应文档（`README.md` / `CONFIG.md` / `docs/` 等）。

## AI 辅助开发

允许使用 AI 辅助编码，但 PR 内容须经过你的理解与验证：门禁全绿、diff 自查通过后再提交。
