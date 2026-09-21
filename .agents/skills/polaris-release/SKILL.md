---
name: polaris-release
description: 北辰 Polaris（E:/AI/Github/slte 仓库，远端 ipevel/polaris）的标准提交与发版流程。凡是在该仓库做 git commit、push、发 Release、升版本号、触发 GitHub Actions 构建打包，都必须先走本技能的"合规检查"与"本地全量 CI 门禁"，禁止未经合规审查和本地验证直接推送。触发词：提交、推送、发版、发布、release、升版本、打 tag、编译验证、合规。
---

# Polaris 提交与发版标准流程

背景：该项目曾因"本地没跑全量编译就推送"导致 GitHub CI 失败（R8 混淆缺依赖、ktlint 规范、日志脱敏守卫、非穷尽 when 等）。**所有 push 前必须在本地跑完全量 CI 门禁，全部通过才能提交推送。**

## 第 1 步：合规检查（不可跳过）

> **原则：合规是底线，功能是目的。** 合规检查的目的是让项目安全、合规地交付，而不是以牺牲功能为代价换取合规。遇到合规要求与功能冲突时，应寻找同时满足两者的方案（如用 EncryptedSharedPreferences 替代明文存储而非删掉持久登录），禁止以"不合规"为由直接砍掉必要功能。

推送前必须逐项过检，任何一项不通过则禁止提交。合规问题无法通过 CI 自动捕获，必须人工/脚本审查。

### 1.1 GitHub 合规

- **Secrets 零泄露**：`git add -A` 前执行 `git diff --cached -- '*.kt' '*.kts' '*.xml' '*.properties' '*.json' '*.yaml' '*.yml'`，逐行扫视确认无硬编码 API key、OAuth token、签名密钥密码、数据库凭证等敏感值；`.gitignore` 已覆盖 `*.keystore`/`*.pem`，但新增文件可能遗漏
- **Workflow 安全**：修改 `.github/workflows/*.yml` 后，确认 pull_request 触发的 job 没有写入 secrets 或使用 `pull_request_target` 暴露仓库权限；`build.yml` 的 `workflow_dispatch` 输入不直接拼入 shell 命令（防注入）
- **Branch 保护**：main 分支应保持 `required_status_check`，勿在 CI 配置中绕过

### 1.2 安全合规

- **依赖安全**：新增第三方依赖时，运行 `./gradlew dependencyInsight --configuration releaseRuntimeClasspath --dependency <group>` 确认传递链无已知高危 CVE；有疑虑时查阅 [GitHub Advisory Database](https://github.com/advisories)
- **R8/ProGuard 保留规则**：新增 `-keep` 或 `-dontwarn` 时必须注释说明对应类/库及原因，禁止无注释的 keep 规则
- **权限最小化**：修改 `AndroidManifest.xml` 权限声明时，确认新权限为功能必需且无更少权限的替代方案；`QUERY_ALL_PACKAGES` 等敏感权限必须标注使用场景
- **网络传输**：新增网络请求必须走 HTTPS；禁止明文 HTTP（`android:usesCleartextTraffic` 保持 `false`）

### 1.3 隐私合规

- **PII 零硬编码**：代码中禁止出现真实用户手机号、邮箱、IMEI、Android ID、IP 地址等个人可识别信息（测试 mock 数据除外）
- **日志脱敏**：所有 `AppLog.*` 调用中出现的 `.message` 必须经 `sanitizeLog(...)`（与第 2 步 CI 门禁 LogSanitizationGuardTest 规则一致）
- **数据采集透明**：新增任何遥测/统计/崩溃上报 SDK 或自定义埋点前，确认隐私政策文档已同步更新说明数据类型与用途
- **本地存储安全**：敏感数据（token、密码）使用 EncryptedSharedPreferences 或 Keystore 存储，禁止明文写 SharedPreferences

### 1.4 开源合规

- **License 兼容性**：新增依赖的 License 必须与本项目 License（Apache-2.0）兼容；以下 License 类型禁止引入：AGPL-3.0/SSPL/BSL/CC-BY-NC 系列
- **NOTICES 维护**：新增或升级第三方依赖后，运行 `./gradlew :app:licenseDebugReport`（需 `com.jaredsburrows.license` 插件），将输出与 `NOTICES` 文件比对，确认版权声明和 License 全文已收录
- **源文件头**：本项目核心源文件保持统一版权头（如 `SPDX-License-Identifier: Apache-2.0`）；新增文件需补齐
- **代码来源标注**：从 Stack Overflow、博客、其他开源项目复制超过 10 行的代码段时，在注释中标注出处 URL 及原始 License

合规检查通过后进入第 2 步。

## 第 2 步：本地全量 CI 门禁（不可跳过）

在 `E:/AI/Github/slte` 下执行：

```bash
# 首次需要一次性签名（lint 会分析 release 变体，必须存在）
keytool -genkeypair -v -keystore ci-throwaway.keystore -alias ci-throwaway \
  -keyalg RSA -keysize 2048 -validity 1 \
  -storepass ci-throwaway -keypass ci-throwaway \
  -dname "CN=CI-Throwaway, OU=CI, O=CI, C=CN"

# 全量门禁：与 .github/workflows/ci.yml 对齐的 8 项
export ANDROID_HOME="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
export POLARIS_RELEASE_STORE_FILE="$(pwd)/ci-throwaway.keystore"
export POLARIS_RELEASE_STORE_PASSWORD=ci-throwaway
export POLARIS_RELEASE_KEY_ALIAS=ci-throwaway
export POLARIS_RELEASE_KEY_PASSWORD=ci-throwaway

./gradlew :app:verifyKernelBinary :app:assembleDebug :app:testDebugUnitTest \
  :app:assembleDebugAndroidTest :app:minifyReleaseWithR8 \
  :app:verifyReleaseApiSurvivors :app:ktlintCheck :app:lintDebug

# 第 9 项：裸图标检查（CI 用 rg，本地 grep 对齐；只允许 SlteIcons.kt 内出现）
grep -rnE 'Icons\.(AutoMirrored\.)?(Outlined|Rounded|Filled|Sharp|TwoTone)\.' \
  app/src/main/java --include="*.kt" | grep -v "ui.theme.SlteIcons.kt" || echo "OK"
```

合规检查通过且九项全绿才允许 push。单跑 `testDebugUnitTest` 通过不等于全量通过。

### 历史踩坑（失败模式清单，改动涉及相关面时重点自查）

| 失败点 | 规则 |
| --- | --- |
| R8 缺 `com.google.errorprone.annotations.*` | proguard-rules.pro 已有 `-dontwarn`；新增依赖报 R8 Missing class 时补同类规则 |
| ktlint import 顺序 | 按字典序，`java.*` 排在 `io.*` 与 `kotlinx.*` 之间 |
| ktlint backing-property-naming | `_x` 私有属性必须有同名公开属性 `x` |
| LogSanitizationGuardTest | 任何 `AppLog.*` 里出现 `.message` 必须经 `sanitizeLog(...)` |
| 非穷尽 when | sealed class 加新分支后，所有 `when` 必须补齐 |
| mockk relaxed 的 StateFlow.collect | 契约返回 Nothing，未打桩会抛 KotlinNothingValueException；测试需 `every { repo.sessionState } returns MutableStateFlow(...)` |
| 协程测试禁用墙钟 | 超时逻辑用「有界 repeat + delay」表达，禁用 `System.currentTimeMillis()` 死循环（虚拟时钟下会热旋真实时间） |

## 第 3 步：升版本号（若本次包含要发布的功能）

1. `app/build.gradle.kts` 第 45-46 行：`POLARIS_VERSION_CODE` / `POLARIS_VERSION_NAME` 的**默认值**（versionCode 恒 +1）
2. `config/remote.json`：`update_version`、`update_changelog_title`、`update_changelog`（本版变更摘要）

## 第 4 步：提交

- 线性历史、Conventional 前缀（feat/fix/chore/ci），大改动在正文用 bullet 展开，不做巨型单行堆叠
- 提交前 `git add -A` 后先 `git status --short` 过目，确认没有把密钥/构建产物带入（.gitignore 已覆盖 *.keystore/*.pem/dist/.mimosa）

## 第 5 步：推送

```bash
git push
```

push 后关注 `gh run list --limit 1` 的 CI 结果；失败则本地复现修复后重走第 2 步。

## 第 6 步：发 Release（需用户要求发布时）

Release 由手动触发的 `北辰 Polaris Build`（build.yml，workflow_dispatch）完成，CI 全绿**不会**自动发版。注意：**每次修改 build.yml 后，触发前先 commit+push，workflow 用的是 main 上的最新文件。**

```bash
gh workflow run build.yml \
  -f appName=北辰 \
  -f applicationId=com.polaris.app \
  -f versionName=<版本> \
  -f versionCode=<code> \
  -f apiBaseUrl=https://api.example.com \
  -f apiType=xboard \
  -f buildType=release \
  -f publishRelease=true \
  -f releaseTag=v<版本>
```

参数说明：
- `apiBaseUrl`：占位符即可（面板地址由用户登录时输入，BuildConfig 值仅作回退）
- `apiType`：历史 Release 均为 `xboard`，用户未明示时用 `xboard`
- `remoteConfigUrls` / `allowedDomains`：**留空**——build.yml 已内置默认值（raw.githubusercontent.com 配置源 + github.com 更新白名单），留空才会用默认值
- `iconB64`：留空使用仓库内 `app/icon-source.png`（图标更新流程见下）

## 第 7 步：发布验证

```bash
gh run watch <run-id>          # 或轮询 gh run view <run-id>
gh release view v<版本>        # 确认 APK/SHA256SUMS.txt/SIGNING.txt 三件资产齐全
git fetch --tags               # 同步远端自动创建的 tag 到本地
```

Release 成功后 build.yml 会自动把 `update_version/update_apk_url/update_apk_sha256` 回写到 `config/remote.json` 并以 github-actions[bot] 身份推送——这就是应用内更新的数据源，无需手工维护。

## 图标更新（用户提供了新 SVG 时）

仓库没有 ImageMagick，用 Chrome headless 渲染 SVG → PNG：

```bash
"C:/Program Files/Google/Chrome/Application/chrome.exe" --headless --disable-gpu \
  --screenshot="app/icon-source.png" --window-size=1024,1024 \
  --default-background-color=00000000 "file:///<SVG 绝对路径 URL 编码>"
```

然后按 CI 管线（build.yml「应用自定义图标」步骤）用 PIL 重生成全套：adaptive 前景 432 画布居中 288 可视 → `drawable-nodpi/ic_launcher_foreground.png`；mdpi~xxxhdpi 48/72/96/144/192 → `mipmap-*/ic_launcher.png` 与 `ic_launcher_round.png`。monochrome 背景层是 vector XML，改路径需手工换算 1024→108 视口。改完必须本地渲染出来看一眼再提交。

## 顺手维护

- tag 双端一致：`git fetch --tags`；发现本地有未推送的 tag 就 `git push origin <tag>`
- 本仓库合规整改有既定路线图（审计报告 21 条），涉及 LICENSE/NOTICES/workflow 变更时先查该报告避免回退
