<!--
Polaris 流程（2026-10-15 起）：单 main + 短命分支 + PR。
- 任何改动一律走短命分支 + PR，禁止直推 main。
- 下面六项 + 两节（Changelog / 栈·依赖）全部必填，缺一项不予合并。
- 合并由「决策对话」批准，且 CI required status checks 必须全绿。
-->

## 1. 动机 / Motivation（必填）

<!-- 为什么做这个改动：要解决的现象、用户可见的影响、关联 Issue（如有）。 -->

- 关联 Issue / Closes #
- 变更类型 / Type of change:
  - [ ] Bug 修复 / Bug fix
  - [ ] 新功能 / New feature
  - [ ] UI/UX 改进 / UI/UX improvement
  - [ ] 性能优化 / Performance optimization
  - [ ] 代码重构 / Code refactoring
  - [ ] 文档更新 / Documentation update
  - [ ] CI / 流程 / Other

## 2. 改动清单 / Changes（必填）

<!-- 逐文件或逐模块列出改了什么，带路径。 -->

1. 
2. 
3. 

## 3. 影响面 / Impact（必填）

<!-- 影响哪个端、哪些用户路径、是否需要发版、有没有碰版本行。 -->

- 端 / Target: 安卓 / Windows / 内核 Go / CI 流程
- 用户可见变化: 
- 是否需要发版: 
- 版本行（`app/build.gradle.kts:48-49`、`design/windows/app/package.json` 与 `package-lock.json`）: 未动 / 动了（仅发版 PR 允许动）

## 4. 验证证据 / Verification（必填）

<!-- 必须给可复核的证据：命令 + 结果数字 + 日志路径。只写「已测试」不算。 -->

- RED（改动前确实失败的证据）: 
- GREEN（改动后通过的证据，含用例数与耗时）: 
- CI: 

## 5. 回退方案 / Rollback（必填）

<!-- 合并后怎么撤：squash commit SHA / revert PR / 是否涉及数据或配置迁移。 -->

## 6. 风险与未覆盖项 / Risks & Gaps（必填）

<!-- 已知风险、无设备未取证的真机项、有意不做的事。不许把未覆盖写成通过。 -->

## Changelog / 用户可见变化（必填）

<!-- 一句话写清「用户能感知到什么变化」；确实没有就写「无」。
     ⚠️ 不要手改 docs/release-notes/<版本>.md 与 docs/release-records/**：
     发版说明由「上一个 tag → 本次 main」的已合 PR 的这一行汇总生成，手写会与汇总结果打架
     （1.12.2 那轮 #44→#45 就是手写说明稿的代价）。 -->

- 

## 栈 / 依赖（必填）

<!-- 本 PR 基于哪个 PR、必须先合哪个 PR；没有就写「无」。
     用于「PR 叠 PR」时让评审人一眼看出合并顺序；没有就不写数字。 -->

- 基于 / Based on: 无
- 必须先合 / Depends on: 无

## 提交前自查 / Pre-submit checklist

- [ ] 改动都在短命分支上，没有直推 main
- [ ] 分支名符合规范：`feat|fix|chore|docs|style|ci/<端>-<简述>`
- [ ] 版本行只在发版 PR 里动
- [ ] 上面六项 + Changelog + 栈/依赖 都填了，没有留空
- [ ] 本地跑过对应门禁（安卓 `:app:ktlintCheck` / Windows `npm run test:offline` / Go `go vet`）

## 截图 / Screenshots（涉及 UI 时）

| 变更前 / Before | 变更后 / After |
| --- | --- |
|  |  |
