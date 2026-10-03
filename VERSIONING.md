# Polaris 版本策略

本文件定义 Polaris 的版本号规则、发布流程与自动门禁，供发布者与协作者查阅。
规则遵循[语义化版本 2.0.0](https://semver.org/lang/zh-CN/)；因为 Polaris 是直接分发给
最终用户的应用，这里先明确"本项目的公共 API 是什么"，再给出递增判定与门禁用法。

---

## 一、什么是本项目的公共 API

SemVer 规定"版本号随公共 API 变化"，所以必须先说清公共 API。对 Polaris 而言，
公共 API **不是** Kotlin 类或函数签名（内部实现随时可重构），而是用户、部署者与
下游系统实际依赖的契约：

| 契约 | 具体内容 | 出处 |
|---|---|---|
| 构建期配置键 | `POLARIS_*` 变量名与语义 | [CONFIG.md](CONFIG.md) 第一节 |
| 远程配置 schema | 远程 JSON 的字段名、类型与语义 | [CONFIG.md](CONFIG.md) 第二节 |
| 面板 API 契约 | 订阅接口路径、鉴权方式、返回结构 | `POLARIS_SUBSCRIBE_PATH` |
| 用户数据格式 | 应用内配置与登录态的持久化结构（升级后必须仍可读） | `app/src/main/java/com/slte/app/data/local` |
| 用户可见交互 | 页面入口位置、设置项名称与语义、通知栏行为 | `app/src/main/java/com/slte/app/ui` |
| 系统集成点 | VPN 服务声明、开机自启、快捷方式与 Intent 入口 | `app/src/main/AndroidManifest.xml` |
| 发布资产 | APK 文件名 `Polaris-<版本>.apk`、`SHA256SUMS.txt`、随附文档 | 发布流程（见第四节） |

不在上表内的一切——内部类与包结构、日志文案、资源 id、Compose 组件、私有函数——
都属于**私有实现**，重构它们不需要升 MAJOR。

---

## 二、递增判定

版本号形如 `X.Y.Z`（`X` = MAJOR，`Y` = MINOR，`Z` = PATCH），三段都是非负整数、无前导零。

| 变化 | 递增位 | 处理其余位 |
|---|---|---|
| 破坏上表任一契约（改 `POLARIS_*` 键名、改远程配置字段语义、用户数据不再向后兼容、移除用户可见入口、改 APK 命名规则） | MAJOR | MINOR / PATCH 归零 |
| 新增或废弃上表契约，但旧用法仍然有效 | MINOR | PATCH 归零 |
| 仅修复缺陷、无契约变化 | PATCH | — |

对本仓库常见的具体判定：

| 改动 | 递增位 |
|---|---|
| 新增设置项、新增页面入口、新增远程配置字段 | MINOR |
| 移除页面上的品牌标识区、下线某个功能入口 | MAJOR（用户可见入口消失）；若只是位置调整仍属 MINOR |
| 界面视觉重构、图标替换、深色模式适配 | MINOR（用户可见交互契约变化） |
| 复用既有组件实现新入口（如把更新入口并入订阅卡） | MINOR（新增用户可见入口） |
| 崩溃修复、逻辑错误修复、文案纠错、依赖安全升级 | PATCH |
| 仅内部重构、仅测试与 CI、仅注释 | 不改变公共 API 时用 PATCH；**注意仍必须递增版本号**，见第三节 |

预发布版本用连字符（`1.6.0-rc.1`），构建元数据用加号（`1.6.0+build.7`）；
构建元数据不参与优先级比较。

---

## 三、versionName、versionCode 与 tag

三者必须指向同一个版本，任一不一致都会被门禁拦截。

| 项 | 规则 | 定义位置 |
|---|---|---|
| `versionName` | 合法的 `X.Y.Z`（可带预发布/构建元数据），必须**严格大于**上一已发布版本 | 发布时由 workflow 输入注入；本地回退值在 `app/build.gradle.kts` |
| `versionCode` | 正整数，必须**严格大于**上一版本；留空时由 CI 用 `date +%s / 60` 生成 | 同上 |
| git tag | 必须恰好是 `v` + `versionName`（如 `v1.6.0`），由发布流程自动创建 | `gh release create` |

两条硬规则：

- **已发布的版本号不可修改、不可复用。** 修复已发布版本的问题必须发新版本；
  也禁止用更低的版本号重新发布（回填预发布号同样被拒）。
- **不得只消耗版本号而不发布。** 把 `versionName` 改进代码却不打 tag，
  会让版本序列出现空洞、破坏可追溯性；门禁的历史审计会报出这类欠账。

---

## 四、发布流程

发布通过 Actions → **Polaris Build** → Run workflow 手动触发，`build` job 在装 JDK /
Android SDK **之前**先跑版本门禁，不通过直接中止（省掉无意义的构建耗时）。

1. 把 `versionName` 填成本次发布的版本号（无默认值，必须手填），按第二节判定递增位。
2. `versionCode` 留空即由 CI 生成；**仅在需要与既有渠道对齐时手填**，且必须大于上一版本。
3. `releaseTag` 留空（自动取 `v<版本号>`）；填了就必须与 `v<版本号>` 完全一致。
4. `gateMode` 保持 `strict`。`warn` 只告警不阻断，`off` 完全跳过——
   两者都只用于紧急回滚，用完立即改回 `strict`。
5. 发布后同步 `app/build.gradle.kts` 里的 `slteVersionName` / `slteVersionCode` 回退值，
   以及 [README.md](README.md) 的"当前版本"表，避免本地构建报出旧版本号。

门禁检查项：

| 级别 | 检查 |
|---|---|
| 阻断 | `versionName` 是合法 SemVer（无前导零） |
| 阻断 | tag 等于 `v` + `versionName` |
| 阻断 | `versionName` 严格大于所有已发布 tag 的版本 |
| 阻断 | `versionCode` 严格大于上一已发布版本的 `versionCode` |
| 阻断 | 上一 tag 到 HEAD 区间内出现 `feat` 却未升 MINOR、出现破坏性变更却未升 MAJOR |
| 告警 | tag 已存在且是 HEAD 的祖先（合法重跑，会更新既有 Release 资产） |
| 告警 | `app/build.gradle.kts` 的回退版本号与本次发布版本不一致 |

---

## 五、本地运行门禁

提交或发布前可在仓库根目录直接跑，与 CI 用的是同一份脚本：

```bash
# 模拟发布 1.6.0（versionCode 51）
python3 .scripts/version_gate.py \
  --tag v1.6.0 \
  --android-gradle app/build.gradle.kts \
  --version-name 1.6.0 --version-code 51 \
  --check-commits
```

```bash
# 只做历史审计：找出"写进代码却从未发布"的版本号欠账
python3 .scripts/version_gate.py --audit-history app/build.gradle.kts:POLARIS_VERSION_NAME
```

审计发现的是**既成事实**（历史欠账无法靠补 tag 消除，补 tag 反而会给旧提交挂上错误的
版本）。确认某笔欠账不再需要处理时，用 `--audit-allowlist <文件>` 逐行列出豁免，
让门禁只对新出现的问题失败；豁免文件需写明每条的来由。

需要 git 历史与 tag：浅克隆时先 `git fetch --tags --deepen=500`。
脚本无第三方依赖，只用标准库；退出码非 0 表示门禁未通过。

---

## 六、相关文档

| 文档 | 视角 |
|---|---|
| [README.md](README.md) | 用户：介绍、下载、编译、配置入口 |
| [CONFIG.md](CONFIG.md) | 部署者：构建配置与远程配置字段（即公共 API 的配置面） |
| [CONTRIBUTING.md](CONTRIBUTING.md) | 贡献者：门禁、代码规范、提交规则 |
| [docs/writing-guide.md](docs/writing-guide.md) | 提交信息与文档写作规范 |
| 本文件 | 发布者：版本号规则、发布流程与版本门禁 |
