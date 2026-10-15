# Issue label 四维集与用法

本文件是 Polaris 仓库 Issue label 的**单一真源**说明。四维集在 2026-10-15 建立，与
`.github/ISSUE_TEMPLATE/` 里的两个表单、以及 PR 六项 body 配套使用。

## 一、四个维度（每个 Issue 四个维度各取一个）

| 维度 | 取值 | 说明 |
| --- | --- | --- |
| **type** | `bug` / `feat` / `chore` / `ci` / `docs` | 这一单是什么性质 |
| **platform** | `android` / `win` / `repo` / `rules` | 影响面在哪一端；跨端就选最主要的一端并在正文写明另一端 |
| **priority** | `P0` / `P1` / `P2` / `P3` | `P0` 立即修（线上 / 数据 / 安全）；`P1` 本版必修；`P2` 排下版；`P3` 有空再做 |
| **status** | `triage` / `in-progress` / `blocked` / `waiting-user` | 当前卡在哪；`triage` = 待分诊，`waiting-user` = 等用户 / 等外部 |

**label 名一律不加前缀**（不要写 `status:triage`、`type:bug`）——表单里的引用、机器人的读写、
以及本文档用的都是裸名；加前缀会出现第二套、并让表单引用静默失效（GitHub 对不存在的 label 是**静默忽略**）。

## 二、标题格式

`[Bug|Feat|Chore|CI|Docs] <android|win|repo|rules> 一句话`

例：`[Bug] android 节点页分流组卡显示旧组名`。

## 三、Issue 与 PR 的分工

- **Issue 是登记与追踪层**；**PR 是实现与门禁层**。
- PR body **首行**写 `Closes #N`（合并自动关）或 `Refs #N`（只引用）。
  - **不强制**：这是软要求，由评审人检查；**没有 required check**。
- **P0 允许先修后补登记**（24 小时内补 Issue）。

## 四、triage（谁做、做什么）

triage 由 **Github运维主管**负责，**每周一次**：

1. 清 backlog：给新 Issue 补齐四维 label；
2. 重复的关掉（`duplicate`）并写一句理由；不做的一律 `wontfix` / `invalid` 并写理由；
3. 定不了级、或涉及产品判断的 → 报决策主管 → 需要用户拍板的升到主对话/用户；
4. backlog 里的条目按 `priority` 排序推进；被阻塞的换 `blocked`、等用户的换 `waiting-user`。

## 五、body 五项（表单已内置，手写 Issue 也要满足）

1. **现象 / 期望**
2. **影响面（平台 + 路径）**
3. **复现或证据**
4. **验收标准**
5. **来源**

## 六、保留的默认 label

`duplicate` / `invalid` / `wontfix` / `question` / `good first issue` / `help wanted` / `accessibility`
保留原样（它们不参与四维分类）。原先与 `feat` / `docs` 重复的 `enhancement` / `documentation`
已于 2026-10-15 删除（当时使用数均为 0）。
