# Polaris Windows 客户端 · 现状与踩坑记录

> 最后更新：2026-10-10（**补上界面层的真线**：新写 `scripts/ui-test.js`（`--uitest`）——
> 开真窗口、用真鼠标事件、走真面板真账号，从登录页自己登进去再逐页真点，
> **73 项全绿**；这一轮它抓出并修掉 3 个只有"真点"才暴露的问题（A-23 登录后立刻连接必失败、
> A-24 我的页「我的套餐」点了没反应、A-25 首页/节点页被重复的慢查询拖住 30 秒）。
> 见 §一「测试方式」与 §三 A-23~A-25、E-11~E-13）
>
> 同日早些时候：**把测试收成一条真线** —— 删掉假面板、三条离线自检线、核心层 `npm test`、
> `--mock` 演示数据与 `--smoke` / `--restore-probe` 自检开关（见 §一「测试方式」与 §三 E-10）。
>
> 再往前：**用真实账号打通了真实面板联调** —— 套餐/订单/流量明细/每个分组延迟全部
> 对着真面板验过，并按用户报的 10 条界面/功能问题逐条修掉：
> 节点页重复选择器、二级页返回键、分流页混入策略组、延迟测试、流量页改读面板明细、我的页与安卓端对齐、
> 单实例防多开、本机代理端口设置与端口占用检测，见 §三 A-21 A-22
>
> 分支：`feat/windows-portable`（基线 `origin/ui/windows-design` @ `5328b55`）
> 应用根目录：`design/windows/app/`

---

## 一、现状速览

### 测试方式（2026-10-10 起）

**三条路，都在这台机器上、都用真东西（真账号、真面板、真节点）：**

| 方式 | 命令 | 说明 |
| --- | --- | --- |
| 真面板联调（数据层） | `electron.exe scripts\real-test.js --panel=<面板> --email=<测试账号> --password=<口令>` | 真账号 → 真面板 → 真订阅 → 真节点 → 真流量。不开窗口，验的是主进程/数据链路。密码只在命令行给一次，不落任何文件 |
| 真面板界面自检（界面层） | `electron.exe . --uitest --panel=<面板> --email=<测试账号> --password=<口令> --uitest-data=<临时目录>` | **开真窗口、真点**：用 `sendInputEvent` 发真鼠标事件（不是 `element.click()`），从登录页真登进去，逐页断言"点下去之后界面变成了什么样"。报告写 `data/uitest-report.txt` |
| 人工手测 | `npm start`（或成品包里的 `Polaris.exe`） | 由人点。自检能覆盖的只是"点得到、读得回"，手感/观感/取舍必须人看 |

> `--uitest-data=<临时目录>` 会把 `POLARIS_DATA_DIR` 指过去，用一份独立的 data
> （不动你日常登录的那份）。不给就用默认数据目录。

**已经删掉的东西（不再存在，别再去找）：**
`scripts/mock-panel.js`（本地假面板 + 假出口代理）、`scripts/selftest-ui.js`（旧的 `--uitest`）、
`scripts/selftest-e2e.js`（`--doctor` / `npm run test:e2e`）、`scripts/rt-test.js`（`--rttest` / `--soak`）、
`scripts/selftest-core.js`（`npm test`），以及 `main.js` / `package.json` 里对应的入口：
`--mock`（演示数据）、`--smoke`（DOM 断言）、`--restore-probe`（登录态探针）、
`src/js/api.js` 里的内置演示数据（浏览器直接打开 `src/index.html` 现在会明确报"没有连接到客户端主进程"，
**不再伪造数据**）。

> 注意区分：现在还有一个 `--uitest`，但它是**新写的** `scripts/ui-test.js`（2026-10-10），
> 走真面板真账号，跟被删掉的旧 `selftest-ui.js`（配假面板 + `--mock`）没有关系。

**为什么删**：这些线要离线跑就必须自带一个假面板和假账号（`ui-tester@example.com` / `rt-tester@example.com`），
于是"测过了"这件事本身变得不可信 —— 假面板的形状是照我们的理解写的，只能证明自洽：
A-22（建工单发 `content` 而面板只认 `message`）在假面板上永远绿，真面板一建单就建出空工单。
**宁可少一条自动线，也不要一条会给出错误安全感的线。**

连 `npm test`（核心层 253 项，纯 Node、不连面板、无账号）也一并删了：它本身不造假，
但它同样是"自动绿灯"，留着就会被当成"测过了"。核心层怎么验？**在真面板联调里验** ——
`real-test.js` 走的每一条路径都会经过配置组装与清洗，组装错了在那里必然红。

### 产物

```
（2026-10-10：dist/ 已按用户要求整个删除，等测试全部通过、用户明确同意后再重新打包）
```

解压即用：不装运行时、不写注册表、不写 `%APPDATA%`。已验证（历史产物：185,943,312 B ≈ 186 MB）。

> 上一版产物的验证方式（`--uitest` / `--doctor` / `--smoke`）已随自检线一起删除，此处仅作历史记录。

### 规模

| | |
| --- | --- |
| 提交 | `3d8eb6a` `ce71ad7` `8e578fd` `8f04c18` `77650f8` `621186d` `ba97f42` `b6105f1` `3c0d1ad` `00846cc` `265e057` `8648c8d` `6c72762` `6c0896a` |
| 相对基线 | +119,127 / −906 行，105 文件 |
| 应用代码 | 主进程 ~4,190 行 JS（含 core/ 3,110 行），渲染层 ~1,660 行 JS，样式 ~385 行 |
| 内核 | mihomo v1.19.32（windows-amd64-**compatible**）+ wintun 0.14.1 |
| 规则库 | geoip.metadb + geosite.dat + ASN.mmdb，23.8 MB |
| 内置分流 | 48 个本地规则集 + 27 个分流组，1.42 MB（`resources/rules/`） |
| 自定义分流 | 用户自己写规则的分流组（最多 20 组 / 每组 200 条），↑↓ 排序，存 `settings.json` 的 `custom_rulesets` |

### 测试入口（2026-10-10 起）

| 方式 | 规模 | 覆盖 |
| --- | --- | --- |
| `scripts/real-test.js` | 39-40 项 | **真面板联调（数据层）**：真登录/复用已有会话 → 套餐/订单/工单/邀请 → 今天/本周/本月流量明细与账号合计交叉验算 → 拉真订阅 → 连真节点 → 逐组延迟 → 经活节点 204 → 跑真流量看面板明细闭环 |
| `scripts/ui-test.js`（`--uitest`） | 73 项 | **真面板界面自检（界面层）**：外壳/标题栏/无横向溢出 → 界面上退出旧会话再用真账号从登录页登入 → 首页套餐与进度条 → 点 `#power` 真连内核 → 节点页分组手风琴 + 「延迟测试」逐组延迟 → 分流页返回键与无杂质 → 流量页三区间都来自面板 → 我的页徽标与 6 个入口 → 6 个二级页标题/返回键/数据 → 设置页「分流与订阅」 → 渲染层 0 error → 断开 |
| 人工手测 | — | `npm start` / 成品包 `Polaris.exe`，由人点 |
| `Polaris.exe --updtest <zip>` | — | 真演练自我替换（下载 → 解压 → 覆盖安装目录 → 重启），**会覆盖当前目录，先拷一份** |

> 已删除（2026-10-10，见上「测试方式」）：`npm test` 253 项、旧的 `--uitest` 110 项（配假面板）、
> `--doctor` / `npm run test:e2e` 128 项、`--rttest` 63 项（含 `--soak=20` 71 项）、
> `--smoke` 的 DOM 断言入口、`--mock` 演示数据、`--restore-probe` 登录态探针。
> 它们的实现与踩坑记录仍留在 §三（E-1~E-10、A-6~A-19），因为**教训是有效的**，
> 只是这些"自动化绿灯"不再被当作验收依据。

当前状态：真面板数据层 **39-40 / 0**（项数随账号里有没有流量数据浮动）、
真面板界面层 **73 / 0**（2026-10-10 11:45，账号 `cm@mbe.cc`）。

### 已实测通过

- 登录 → 拉订阅 → 清洗 → 起内核 → 挂系统代理 → 经代理访问外网返回 204
- 真实系统流量被正确分流（日志可见 `my.1password.com` 走节点、`*.dingtalk.com` 走 GeoIP(cn) 直连）
- 断开后系统代理 4 个注册表值逐项还原到进入前的状态
- 最大化/还原布局无横向溢出；设置页可滚到底
- 便携性：删掉 `%APPDATA%\Polaris` 后跑成品，不再重建
- 内置分流：16 个默认规则集被内核**真正加载**（`/providers/rules` 逐个 `ruleCount > 0`，合计 > 1 万条），
  运行中开关分流组能热重载并让内核加载新规则集
- 自动更新：下载（含 302）→ 校验 zip → 解压 → 写替换脚本 → 覆盖安装目录 → 重启，全程在成品包的**拷贝**上真跑过一遍；
  点「丢弃更新包」后下载的 zip、解压目录、`staged.json` 一起清掉（不留 186 MB 在磁盘上）
- （历史，该自检线已删）运行时功能测试（`--rttest`，真界面驱动 + 内核回读）：点界面连接 → `/configs` 的 `mixed-port` 与
  `core.mixedPort()` 一致、控制面只绑 `127.0.0.1`、`/traffic` WebSocket 真推数据、经代理 204；
  国外域名命中 `RuleSet(gs_geolocation_ncn)`（链 `香港 01 → 节点选择 → 🌏 国外穿墙`）、国内域名走 DIRECT；
  三种代理模式切换后 `/configs.mode` 跟着变；界面开关分流组后内核 `/providers/rules` 数量随之增减
  （合计 56,283 条规则）；内核被 `taskkill /F` 后应用 20s 内不再谎报已连接、再点一次能自愈成新 pid；
  面板进程被杀后界面不崩、代理仍 204；断开后系统代理 4 个值逐项回到进入测试前的状态
- 真实面板（测试账号 `cm@mbe.cc`，`scripts/real-test.js`）：**39 通过 / 0 失败**。
  套餐「TEST 已用 946 KB / 500 GB，到期 2029-04-20」、订单「美国-年-不重置 ￥23 done」、公告 1 条、
  32 个真节点、24 个策略组、25/32 个节点有延迟、**23/23 个分组都有延迟**；
  流量页三个区间与账号合计对得上（面板明细 ↑60.3 KB ↓886 KB → 账号 946 KB）；
  经活节点访问 `gstatic.com/generate_204` 返回 204；跑完 3 MB 真流量后面板明细从
  886 KB 涨到 921 KB（**站点统计闭环**）。
- 真实面板界面层（`scripts/ui-test.js --uitest`，真窗口真鼠标事件）：**73 通过 / 0 失败**。
  界面自检自己先从登录页用真账号登进去，再逐页真点：节点页 23 个分组手风琴（第一个默认展开、
  点一下能收起、32 个节点）、「延迟测试」点下去 **23/23 个分组都测出真延迟**、
  流量页 today/week/month 三个区间都来自面板明细（不再是本机算的）、
  我的页徽标显示真套餐名（不是「未订阅」）且 6 个入口齐全、6 个二级页都有返回键且返回有效、
  设置页有「分流与订阅」段、**全程渲染层 0 个 error**。
  这一轮界面自检抓出并修掉 3 个只有"真点"才会暴露的问题（见 A-23、A-24、A-25）。
- （历史，该自检线已删）长后台浸泡（`--rttest --soak=20`，20 分钟真连接 + 周期性收进托盘）：**71 通过 / 0 失败**。
  主进程内存漂移 **−3.1 MB**、内核 **+2.1 MB**、内核句柄 **+39.8 个**、线程 **+1.2 个**（无泄漏）；
  节点延迟首段 169ms → 末段 181ms（未劣化）；每 5 分钟一次 10MB 吞吐采样
  **6.59 → 20.04 → 25.19 → 25.25 → 26.85 Mbps**（首采样是冷启动，之后稳定）；内核 pid 全程未变。
  逐分钟样本在 `data/rt-samples.jsonl`

### 未验证 / 未做

| 项 | 状态 | 说明 |
| --- | --- | --- |
| 真实面板联调 | ✅ | `scripts/real-test.js` 拿真账号真面板真节点跑通 **39-40 项全绿**：登录/复用会话 → 套餐 → 订单 → 工单 → 邀请 → 公告 → 今天/本周/本月流量明细（与账号合计交叉验算）→ 拉真订阅（32 节点 / 24-26 策略组）→ 连真节点 → 逐组延迟（23/23 组有延迟）→ 经活节点 204 → 跑 3 MB 真流量后面板明细真的涨了。**过程中修掉 3 个只有真面板才暴露的问题**（见 A-22、D-6、E-9） |
| TUN 模式实机 | ❌ | 代码路径完整（含 UAC 提权、网卡收尾、残留清理），但会临时接管网络栈，未在真机跑 |
| 面板活跃会话管理（踢设备） | ❌ | 需要新功能：**两端都没有这个接口**（面板侧也没有），不是「UI 未做」 |
| 自动更新（下载并替换自身） | ✅ | 便携版可自装：`update_windows_url` 指向 zip → 下载 → 解压到 `data/update/staging` → 退出前写 `apply-update.cmd` 覆盖安装目录并重启。装在 `Program Files`/只读盘时自动退回「前往下载」；不做增量、不校验签名、失败无回滚（只覆盖不删除，用户数据与旧文件都还在，可手动重下） |
| 自定义规则集（用户自己写规则） | ✅ | 分流页「自定义分流组」：一行一条 `类型,内容`，出口由程序补；白名单校验（含拒绝 `MATCH`）、IP 类自动补 `no-resolve`；最多 20 组 / 每组 200 条；**自定义规则排在内置分类与订阅规则之前**（否则永远轮不到）；与订阅策略组同名时复用、与内置同名时拒绝 |
| 分流组排序 | ✅ | 分流页每个组（内置 + 自定义）都有 ↑↓，落 `routing_order` / `custom_rulesets` 顺序；内置与自定义不互相跨越；「恢复默认」只还原内置开关与顺序，不动自定义组 |

---

## 二、架构决策与代价

### 1. 为什么是 Electron 而不是 Tauri

原始骨架是 Tauri 2。需求是「**任何 Win10+ 机器解压即用、不装任何依赖**」。

Tauri 的 exe 只有 ~15 MB，但依赖系统 WebView2 运行时。要满足零依赖，就得捆 WebView2 固定版（约 180 MB）——体积与 Electron 持平，却多一道 Rust 工具链门槛（本机连 rustc/MSVC 都没有）。**在"零依赖"这个约束下 Tauri 的唯一优势（体积）直接消失**，所以换 Electron。

`src-tauri/` 已删除，需要时用
`git show 5328b55:design/windows/app/src-tauri/src/main.rs` 取回。

**代价**：产物 186 MB。这是"零依赖"的直接成本，不是浪费。

### 2. 前端整文件接管，不用正则打补丁

最初用正则（`re.sub`）往原型文件里打补丁，很快失控：

- 补丁的"标记"被新文件自身满足 → 补丁静默变成 no-op，看起来成功了其实什么都没做
- 一处 `\g<i>` 反向引用没展开，字面写进 HTML，CSP 被浏览器忽略，**而日志里只有一行 warning**

现在改成：暂存区存完整文件，`generated/sync_app.py` 单向同步，并用 `expect()` 断言标记存在——**同步失效会报错，不会静默通过**。

### 3. 便携三件套

```
<exe 同级>/
├─ Polaris.exe
├─ core/            mihomo.exe + wintun.dll            (extraResources)
├─ resources/geo/   规则库（geoip/geosite/ASN）          (extraResources)
├─ resources/rules/ 内置分流规则集 48 个 / 1.42 MB       (extraResources → 运行时铺到 data/rules)
└─ data/            ← 全部运行时数据
   ├─ config.yaml      内核配置（每次连接重新生成）
   ├─ profiles/        订阅原文
   ├─ rules/           内置规则集副本（内核只允许读 -d 目录内的文件）
   ├─ settings.json
   ├─ credentials.dat  DPAPI 加密
   ├─ traffic.json     流量历史
   ├─ electron/        ← Electron 自己的缓存也在这里（见坑 A-4）
   ├─ update/          下载的更新包 + staging + apply-update.cmd/log
   └─ logs/
```

数据目录解析：`<exe 同级>/data` → 不可写时回退 `%LOCALAPPDATA%/Polaris` → 开发态 `<app>/.devdata`。

### 4. 内置分流规则集：`type: file` + 相对路径，不用 http

Android 端的分流表（`kernel-core/.../native/config/routing/routing_table.go`，27 组 / 48 个规则集）
用的是 `type: http` + jsdelivr —— 因为它的 assets 只当"预播种缓存"，内核照样联网拉。

桌面端的前提是「解压即用、离线可用」，所以：

- 48 个 yaml 直接进 `extraResources`（`resources/rules/`，1.42 MB），启动时铺到 `data/rules/`
- provider 写 `type: file` + **相对路径** `rules/<key>.yaml`

第二个决定不是风格问题，是硬约束：mihomo 的 `type: file` 走
`C.Path.IsSafePath(C.Path.Resolve(schema.Path))`，而 `IsSafePath` 只放行 `-d` 目录（homeDir）下的子路径。
写绝对路径或读 `resources/rules/` 一律被拒。详见坑 C-7。

其余沿用 Android 的语义：本地规则插在订阅规则**之前**、内网地址始终直连、
每个启用组一个同名 select 组（首位成员表达默认出口）、`orderedTable` 保证排序不丢组。

### 5. 自动更新：为什么把「替换自己」交给一个外部批处理

进程不能覆盖自己正在运行的可执行文件（Windows 会锁住 `Polaris.exe` 和 asar），
所以 `apply()` 只是写一个 `apply-update.cmd` 然后 `detached + unref` 地起它，自己退出：

```
等主进程 PID 消失（tasklist 轮询，最多 120 秒）
  → robocopy staging 安装目录 /E /IS /IT        （只覆盖不删除）
  → start "" 安装目录\Polaris.exe               （重启）
```

三个刻意的选择：

- **`robocopy` 而不是 `xcopy`/自写拷贝**：`/E` 只补齐、不删除，所以 `data/`（配置、凭据、流量记录）
  和上一版遗留的文件都会留着 —— 失败也没有「更新到一半打不开」的窗口，最多是版本混杂。
- **不加 `/MIR` 或 `/PURGE`**：那会把用户数据一起清掉。自检里专门断言脚本**不含**这两个开关。
- **脚本文件不自我删除**：运行中的 `.cmd` 删自己会报错；留在 `data/update/` 当审计日志。

脚本必须**全 ASCII + CRLF**：`cmd.exe` 对 UTF-8 无 BOM 的中文路径/注释会按 ANSI 解，中文注释足以让
`goto` 标签解析错位。脚本里所有路径都用 `%SRC%`/`%APP%` 变量加引号，日志头写清版本与时间。

装不了的三种情况（开发态 / 安装目录不可写 / 还没下好）由 `applyBlockedReason()` 统一给出人话原因，
渲染层据此在「下载并安装」「重启并安装」「前往下载」三种弹窗形态之间切换。

### 6. 自定义分流：规则必须排在最前，校验必须在保存时就拦

用户自己写的分流组（`settings.json` 的 `custom_rulesets`）在配置里长这样：

```
rules:
  - IP-CIDR,192.168.0.0/16,DIRECT          ← 内网直连，永远第一
  - DOMAIN-SUFFIX,corp.com,公司内网         ← 用户自定义，排在内置分类之前
  - IP-CIDR,10.9.0.0/16,公司内网,no-resolve
  - RULE-SET,gs_apple,🍎 苹果服务           ← 内置分类
  - RULE-SET,gs_geolocation_ncn,🌏 国外穿墙
  - ...订阅自带的规则...
  - MATCH,🚀 节点选择                       ← 兜底，永远最后
```

- **自定义必须排在内置之前**：内置的「🌏 国外穿墙」里是 `gs_geolocation_ncn`（"非中国"整张大网），
  排在它后面的任何用户规则都永远匹配不到。分流规则是**首次匹配即生效**，顺序就是语义。
- **保存时就严格校验（`validateCustom`）**：一条拼错的规则会让 mihomo **拒绝整个配置**，
  内核直接起不来 —— 用户看到的现象是"点连接没反应"，排查成本极高。所以类型走白名单（`RULE_TYPES`，
  且**拒绝 `MATCH`**：它会把后面的规则全吃掉）、内容不许带空格、错误信息带「第 N 行：」前缀直接弹给用户。
- **IP 类自动补 `no-resolve`**（`IP-CIDR/IP-CIDR6/IP-SUFFIX/GEOIP`）：不补的话 mihomo 会为了匹配
  IP 规则去解析每一个域名。
- **出口由程序补**：用户只写「类型,内容」，`ruleLine()` 把目标组名插在 `no-resolve` 之前。
- **同名规则**：与**内置组**同名 → 保存时拒绝、组装时也丢弃（内置语义不能被顶掉）；
  与**订阅策略组**同名 → 允许并**复用**那个组（mihomo 里重名策略组是硬失败）。
- **排序**：分流页 ↑↓ 落 `routing_order`（内置）/ `custom_rulesets` 数组顺序（自定义），
  两者互不跨越 —— 自定义永远在内置之前，这是上面的语义决定的，不是 UI 限制。
  「恢复默认开关与顺序」只还原内置，不动用户自己写的组。

---

## 三、踩坑记录

按"下一轮最可能再踩"的顺序排。

### A. 换壳留下的隐性地雷（最贵的一类）

> 共性：**代码能跑、测试全绿、日志无错，但用户一碰就废**。因为没有一条测试在真正操作界面。

**A-1 窗口拖不动**

`design/windows/css/design.css` 的拖动是靠 Tauri 的 `data-tauri-drag-region` 属性实现的（`index.html` 里还留着这些属性）。换成 Electron 后没人接手——无边框窗口必须显式声明 `-webkit-app-region: drag`。

修：新增 `src/css/shell.css` 做外壳层覆盖，`.titlebar { -webkit-app-region: drag }`，`.win-btn` 加 `no-drag`。

**A-2 最大化后布局错位**

```css
.window { width: 1400px; height: 880px; border-radius: 16px; box-shadow: ...; }
body    { display: flex; align-items: center; justify-content: center; background: linear-gradient(...); }
```

这是**在浏览器页面里画一个假窗口**的写法（16 张设计稿就是这么渲染的）。放进真窗口，最大化后内容永远 1400×880 居中，四周全是空的。

修：`.window` 铺满视口，去掉圆角/边框/投影；`body` 去掉居中和渐变。

**A-3 内容被裁掉**

`.content { overflow: hidden }` —— 设置页有十几行，超出部分直接消失，没有滚动条。

修：`overflow-y: auto; overflow-x: hidden`。

**A-4 Electron 自己的缓存写在 `%APPDATA%`**

即使我们把业务数据放在 `data/`，Electron 的 `Cache` / `GPUCache` / `Local Storage` 照样写 `%APPDATA%\Polaris`。这：
- 违反"不写 AppData"的便携要求（整目录拷走只搬走了我们的数据）
- 多实例会抢同一个缓存目录，报 `Unable to move the cache: 拒绝访问 (0x5)`

修：`app ready` 之前重定向（`electron/main.js` 顶部）

```js
const electronData = path.join(paths.root(), 'data', 'electron');
fs.mkdirSync(electronData, { recursive: true });
app.setPath('userData', electronData);
app.setPath('sessionData', electronData);
```

**验证方式**：删掉 `%APPDATA%\Polaris` → 跑成品 → 确认不再重建。

**A-5 登录前的节点页是空的**

节点列表读自内核的 `/proxies`，内核没起来就什么都没有。但订阅生成的 `config.yaml` 明明就在本地。

修：`core/manager.js` 的 `previewNodes()` —— 内核不在时直接从本地配置读出节点与分组，登录后立刻能看到列表。`routingGroups()` 同样回退。

**教训**：`design.css` 是从**浏览器里的设计稿**来的。凡是"窗口外壳"性质的样式（尺寸、边框、滚动、拖拽）都必须在外壳层重新声明，不能指望设计稿的写法在真窗口里成立。

**A-6 设置页无界自我重绘（最贵的一个，38 项自检全绿也没抓到）**

`bindSettings()` 第一行无条件调 `loadTunStatus()`，而 `loadTunStatus()` 结尾有
`if (state.route === "settings") render()` —— render 会重新绑事件，于是：
**render → bindSettings → loadTunStatus → render → …** 停不下来。

用户看到的是：设置页一直在闪、滚不动、**从这里打开的每个弹窗（外观/语言/修改密码/导出日志）都是一闪即逝**
（`render()` 把 `overlayRoot.innerHTML` 清空）。而自检全绿，因为 `executeJavaScript` 在同一个 JS 帧里
同步读 DOM，重绘循环插不进去。

修：`nav()` 里显式`if (route === "settings") loadTunStatus()`，`bindSettings()` 里一个字都不留
（`app.js` 里那行注释就是防止后人再挪回去的）。

**教训**：绑定函数必须是纯的（只绑事件、不发请求）；请求放 `nav()` / `refreshAll()`。
反过来，"自检读得到 ≠ 用户用得到"，同步读 DOM 的断言天然看不见重绘循环。

**A-7 一个字段名写错，静默变成空态**

`Views.traffic` 读 `s.series.points`，而 `state` 里的字段叫 `trafficSeries`。不报错、不警告，
流量曲线页永远显示"暂无流量记录"——**看起来像"还没产生流量"，不像 bug**。

同类：`bindLiveStatus` 里比较 `state.phase`，而 `state` 根本没有 `phase` 字段（恒 `undefined`，
恰好在某条分支上"能用"）；`if (state.route === "home") render()` 让非首页路由的速率/连接态永不刷新。

修：视图字段名与 state 对齐；连接态变化在任何路由都重绘（首页只做轻量 `paintLive()`）。

**教训**：渲染层没有类型检查，`undefined.x` 只会变成空字符串/空数组。视图里凡是用到"可能不存在"的
字段，要么兜底要么断言——补一条真跑页面的自检（`--uitest` 里现在会断言流量曲线真画出 `path >= 3`）。

**A-8 深色主题的三级文字只差一点点不达标**

深色配色是这个 Windows 端自己补的（`views-extra.css` 的 `:root[data-theme="dark"]`）。
原来的 `--text3: #7c7c85` 在 `#16171a` 上是 **4.33:1**，正好卡在 WCAG AA 的 4.5 下面一点点 ——
不量就永远发现不了（眼睛看着"就是有点淡"）。

修：`--text3` 改成 `#8b8b94` → **5.31:1**（二级文字 `#a9a9b0` 是 7.67:1，层级还在）。

**浅色主题的 `--text3`（#8e8e93）实测只有 2.99:1**，但那是设计稿的调色板，且要压到 4.5:1
就得让它等于 `--text2`（#6e6e73），文字层级会塌 —— **留给设计方定，本端不动**（见 §五）。

自检现在每次跑都打印两套主题的实测值：

```
对比度（浅色，背景 rgb(245, 245, 247)）：{"页面标题":15.46,"分组标题":2.99,"设置项名称":16.83,"设置项取值":5.07,"导航项":5.07}
对比度（暗色，背景 rgb(22, 23, 26)）：  {"页面标题":16.03,"分组标题":5.31,"设置项名称":14.56,"设置项取值":6.97,"导航项":7.37}
```

**A-9 `auto_update` 开关一直是死的**

设置页有「自动检查更新」，`store.js` 里有默认值 `auto_update: true`，`ipc.js` 的白名单里有它 ——
但 `grep auto_update` 只命中这三处，**没有任何代码真的读它去检查更新**。开关能拨、能存、能回显，
就是不做事。这类"接线接了一半"的开关比缺功能更糟：用户以为开了。

修：`boot()` 末尾按开关延迟 6 秒静默检查一次，有更新（或已下好）才提示；
`--uitest` 断言不了它（要等 6 秒），改由 `selftest-e2e.js` 直接调命令层验证。

**教训**：新增一个设置项时，`grep <key>` 必须能同时找到"写它的地方"和"读它的地方"；
只有前者就是死开关。

**A-10 在主进程里 `return` 之前动嘴，等于玩 TDZ 地雷**

`--doctor` / `--updtest` 这类自检开关都是在 `main.js` 顶层 `return` 掉的（跳过窗口/托盘/单实例锁）。
我加 `--updtest` 时顺手在回调里写了 `quitting = true; app.quit()` —— 看着无害，
实际 `let quitting = false` 声明在那个 `return` **后面**，模块求值根本没走到，
于是 500ms 后赋值直接 `ReferenceError: Cannot access 'quitting' before initialization`，
**主进程弹错误框、永不退出**，而替换脚本正等着它退出 → 整个更新流程卡死。

修：`--updtest` 分支里只碰 `app`/`log`/`paths`，退出就用 `app.quit()`。
（顺带说明：因为模块提前 `return`，`window-all-closed` 的 `preventDefault` 也没注册，
`app.quit()` 才能真的退出——这是这套自检开关"能用"的前提条件之一。）

**后来改成结构性修法**：把 `let mainWindow / tray / quitting` 三个模块级声明**提到所有
`return` 分支之前**，这样"早退分支里碰模块级状态"就不再是地雷，只是没意义而已；
真正的地雷变成"在早退分支**之后**新增模块级 `let`"，所以分支注释里写了警告。
再加一条回归测试（`selftest-core.js` 的「启动模式」段）：真拉一次
`--updtest http://127.0.0.1:9/nope.zip`（9 号端口必拒），**断言它能在超时内自己退出且退出码是 2** ——
TDZ 崩的表现是弹模态框挂住不退出，所以"超时"就是最直接的探针。
已用"把地雷放回去"验证过这条测试真的会红（3 项 FAIL）。

**A-11 替换脚本的两个"看起来对"的坑**

1. **等不到进程退出也必须放弃**：原来写的是"等 120 秒 → `goto copy`"，也就是主进程还活着照样覆盖。
   文件被占着，robocopy 只能抄进去一半 —— 比不更新糟得多（半新半旧、可能打不开）。
   改成 `goto stuck` → 记日志 → `exit /b 2`，安装目录一个字节都不动。
2. **`timeout /t 1` 在脱离进程里是坏的**：它需要交互式控制台，而脚本是
   `spawn(cmd, {detached:true, stdio:'ignore'})` 起的 —— 没有控制台，`timeout` 立刻报错返回，
   等待循环变成空转、120 次上限瞬间耗完。换成 `ping -n 2 127.0.0.1 >nul`（老牌 sleep，不依赖控制台）。
3. **装完必须清包**：不清 `staged.json` + `staging`，下次启动 `restoreStaged()` 又把 staging 认成"已就绪"，
   用户再点一次「重启并安装」= 拿旧包把自己降级回去。所以脚本在成功分支里
   `del staged.json` / `del Polaris-*.zip` / `rd /s /q staging`（**不碰安装目录、不删脚本自身**）。

**A-12 无控制台的脱离进程里，`tasklist` 是哑的（这条差点让更新永远卡住）**

等主进程退出原来用的是 `tasklist /FI "PID eq N" /NH | find "N"`。在**有控制台**的终端里手跑完全正常，
但在真实场景（GUI 应用 spawn 的脱离 cmd，stdio 全忽略）里：

- `tasklist` **一个字都不输出**（连 `> file` 重定向出来都是空文件）；
- 管道版因此**永远等不到 EOF，卡死在 `find.exe` 上** —— 表现就是"点了安装，应用退出了，然后再也不回来"；
- 改成"重定向到文件再 `find 文件`"不卡了，但每次都是空文件 → 每次都判定"进程已退出" →
  **应用还活着就开始 robocopy**，又回到 A-11 第 1 条的坏结果。

实测结论：**PowerShell 的 `Get-Process -Id N` 在无控制台环境里工作正常**（活着的进程 exit 0，不存在的 exit 1）。
现在脚本用 `powershell -NoProfile -NonInteractive -Command "if (Get-Process -Id N ...)"`，
并把"探测本身失败"（errorlevel ≥ 2，比如机器上没有 powershell）当成**"还没退，继续等"**，
等满上限就走 `:stuck` 放弃 —— 宁可不动，也不覆盖一个正在运行的安装目录。

这三条都不是想出来的，是拿成品包副本真跑一遍 `--updtest` 压出来的 —— 见 §四。

**A-13 Electron 的"位置参数之后再跟开关"会静默退出**

演练命令原来写成 `Polaris.exe --updtest <URL> --updtest-version 9.9.9`，结果：
**主进程 exit code -1、日志一个字都不写、`data/` 都不建**，看上去完全像"应用崩了"。
把顺序换成 `--updtest-version 9.9.9 --updtest <URL>`，或者把第二个开关写成
`--updtest-version=9.9.9`（等号形式），就一切正常。

结论：**开关放位置参数前面，或者一律用 `--x=y`**。演练脚本已按等号形式写。
（这只影响带命令行开关的自检/演练路径，正常用户是点界面触发的，不受影响。）

**A-14 管道断了以后，日志会把应用卡死在报错循环里（11.63 GB 的日志）**

用 `pwsh -Command "... electron.exe ... *> out.txt"` 起 Electron 时，PowerShell **不等 GUI 子进程**
就退出了，管道随之关闭。之后应用每一次 `process.stdout.write` 都抛
`EPIPE: broken pipe, write`（`logger.js` → 被 `main.js` 的 `uncaughtException` 接住），
而那个处理器自己又调 `log.error` 写 stdout —— **同毫秒递归刷屏**，应用不再前进。

实测证据：`.devdata/data/logs/polaris.log.1` 长到 **11.63 GB**，前 3000 行里 270 行是同一条 EPIPE。

修法：`logger.js` 里给 stdout 加"断了就永久闭嘴"的开关（`stdoutBroken` + try/catch，
并监听 `process.stdout.on('error')`）；`main.js` 的 `uncaughtException` 对
`/EPIPE|EBADF|ERR_STREAM_DESTROYED/` 直接 return（这不是程序缺陷，且必须避免递归），
`unhandledRejection` 同样包 try/catch。

顺带一条：起 GUI 程序做诊断要用 Node `spawn(exe, args, { stdio: 'ignore' })`
（本机 `E:\AI\_run.js` 就是干这个的），不要用 PowerShell —— 它不等 GUI 进程，
而且本机 `Get-ChildItem Env:` / `Start-Process` 还会因为 `NO_PROXY` 与 `no_proxy` 重复键直接抛错。

**A-15 系统代理快照一丢，就把 Polaris 自己的端口当成"用户原值"写回注册表（最危险的一个）**

旧代码 `disable(before)` = `restore(before || snapshot())`。看着像"兜底"，实际是：
**快照丢了的时候，它把当前注册表值（也就是 Polaris 刚写进去的 `127.0.0.1:<内核端口>` 和
Polaris 自己的 bypass 列表）当成用户的原始设置写回去**。后果是用户的系统代理永久指向一个死端口，
而且这个被污染的值会继续当快照用，再也还原不回来。

快照丢失的现实路径：`manager.js` 里 mihomo 的 `exit` 处理器会 `disable(S.proxySnapshot)` 并把
`S.proxySnapshot = null`，之后 `disconnect()`/`shutdown()` 再调一次 `disable(null)` ——
第二次就把污染值写回去了。

修法（护栏，宁可不动也不乱写）：
- `disable(before)` / `restore(snap)` 拿到非对象一律只 `log.warn` 后 return，**绝不写注册表**；
- `snapshot()` 三个值全读不到时返回 `null`（读失败 ≠ 用户本来没设代理，否则"还原"会把用户配置清掉）；
- `enable()` 拿不到快照就 warn 并放弃（改了却还原不回去 = 永久改掉用户设置）。

自检：`selftest-core.js` 的 `testSysproxyGuard()` 断言 `disable(null)`/`disable(undefined)`/`restore(null)`
前后注册表 JSON **一字不差**；运行时测试在收尾断言"断开后自动还原到进入前的值"。

**A-16 首页会话数字只有整页重绘才刷新（连上之后就冻住了）**

首页「本次上传 / 本次下载 / 运行时间」是 `Views.home` 渲染出来的，而 `render()` 只在
**连接态或 phase 变化**时触发；`paintLive()` 只更新下载/上传两个速率。
结果：连上以后这三个数字一直停在最后一次重绘的值，收进托盘再恢复也一样
（运行时测试的 5.2 就是这么抓到的：界面 `00:00:31` vs 内核 `00:00:48`）。

修法：给三行加 id（`live-up-total` / `live-down-total` / `live-uptime`），
`paintLive()` 里跟着状态推送逐个补。自检补了一条"id 齐全"的防回归断言
（`selftest-ui.js`）—— 删掉 id 就会静默退回"数字永远不动"。

**A-17 连接态下打开「系统代理」开关，注册表原样不动（开关显示已打开）**

`set_setting` 只处理了**关**的那半边（连接态点关会被拦住），**开**的那半边只写 store：
`sys_proxy=true` 存下了，但注册表还是用户原来的代理，要等下次重连（`manager.js` 的
connect 流程里才 `sysproxy.enable`）才生效。用户看到的是"开关已经打开了、系统流量却还走原代理"。

修法：连接态下打开时立刻 `sysproxy.enable('127.0.0.1', core.mixedPort(), core.S.directDomains)`
并把返回的快照记下来；**只在没有快照时才拍快照**（已有快照说明注册表本来就是我们改的，
再拍一次就把自己的值当成"用户原值"了 —— 和 A-15 是同一个陷阱）。

自检：运行时测试 5.1 现在断言"连接态打开后注册表指向内核端口"，关掉后逐项还原。

**A-18 点了「丢弃更新包」，186 MB 的包还留在磁盘上**

`updater.reset()` 只删了 `staging/` 和 `staged.json`，**没删下载下来的 zip**
（`data/update/Polaris-<版本>.zip`，真包 ~186 MB）。用户下载完又改主意点了「丢弃」，
这 186 MB 就一直占着 —— 而且下次点了别的版本还会再多一个。

修法：`reset()` 顺手清掉更新目录里所有 `Polaris-*.zip`（只匹配我们自己命名的文件，
不动用户放进该目录的东西）。core 自检加两条：下载后包**确实落在** `data/update`、
丢弃后**确实没了**；e2e 的 `discard_update` 之后也补了同一条。

教训：这类"清理"函数要把**下载产物、解压产物、状态文件**三类一起想一遍，
写的时候只盯着自己手边那个目录（staging）很容易漏掉最大的那个文件。

---

**A-19 自定义分流组保存"成功"了，配置里却一条都没有（`serializeCustom` 吃不下自己的输出）**

`normalizeCustom()` 只认**文本行**（`DOMAIN-SUFFIX,x.com`），而 `validateCustom()` 返回的是
**已解析的规则对象**（`{type,value,noResolve}`）。`saveCustomRuleset()` 把 validate 的结果直接
push 进列表再 `serializeCustom()`，后者内部又调 `normalizeCustom()` → 每个"规则"都被当成文本行
解析 → 全部失败 → 整组被丢弃 → 存进 `settings.json` 的是 `[]`。

危害在于**完全静默**：IPC 返回 `ok:true`、UI 弹「已保存」、`applied` 甚至为 true（热重载确实跑了，
只是配置里没这条），用户只会觉得"我加的规则没用"。

修法：`normalizeCustom()` 同时接受两种形状（对象走 `parseRuleLine(ruleText(obj))` 往返）。
core 自检补两条回归：`validateCustom → serializeCustom` 往返不丢组、`normalizeCustom` 能吃对象。

教训（比这条 bug 本身更重要）：**core 自检里每个函数单独测过，不代表它们的组合是对的。**
我的 `serializeCustom` 断言当时用的是手写字符串规则 —— 恰好是"另一个形状"，所以全绿。
真正抓到它的是 e2e：从 IPC 存一组、再从 `config.yaml` 里回读。**接口层的"存进去 → 从产物里读出来"
这条链路，必须有一条测试，不能只测函数。**

---

**A-20 主进程启动时根本没调 `restore()`，登录态"跨重启失效"**

（`main.js` 里的 `session.panelUrl` 一直是空的，启动时 `siteInfo` 必然报"尚未配置面板地址"。）
修法：`whenReady` 里显式 `panel.restore()`，凭据解不开就安静退回登录页。
core 自检补了两段：函数级（凭据落盘 → restore 读回）+ **链路级**（真启动一次应用，
看它到底有没有把凭据读回来）——后者才是"那行代码还在不在"的证据。

**A-21 端口被占着，界面却显示"已连接"**

用户把「本机代理端口」固定成 7890（他另一款代理软件 BettboxCore 正占着这个端口）。
mihomo 的 mixed 端口 bind 失败只在**内核日志**里留一行
`Start Mixed(http+socks) server error: listen tcp 127.0.0.1:7890: bind: address already in use`，
而我们的 `waitReady()` 只看**控制面**端口（另一个随机端口，bind 成功）——
于是控制面起来了、`phase=connected`、界面绿了，**但所有流量都没经过代理**。
这是"看起来连上了其实没连"里最坏的一种：用户会以为节点全挂了。

修法两层：
1. `manager.assertPortFree(port)` —— 起内核前自己先 `net.bind('127.0.0.1', port)` 试一下，
   `EADDRINUSE` 直接抛出人话：`本机代理端口 7890 已被其它程序占用，请在「设置 → 本机代理端口」换一个（或改成自动）`；
2. `waitReady()` 成功后**扫一遍内核启动日志**，命中同一条错误就 kill 内核 + 还原系统代理 + 抛错。

教训：**"控制面就绪"不等于"数据面就绪"**。判活要看数据面（或至少看内核有没有报 bind 失败），
只看自己那个端口的健康检查会把"完全没生效"读成"一切正常"。

**A-22 新建工单发的是 `content`，真面板只认 `message`（假面板验不出来）**

按 Android 端契约，XBoard 的建单请求是 `{subject, message, level: Int}`，
而我们发的是 `{subject, content, level: 'low'}`。假面板按我们的形状写，所以 e2e 全绿；
真面板收到 `content` 会**建出一张空工单**（标题在、正文没了），且 `level` 字符串被丢掉。
修法：`client.ticketContent(t)` 从 `message` 数组（新形状）或 `content`（旧形状）取正文，
`createTicket(subject, content, level = 1)` 统一发 `{subject, message, level}`，
UI 加三级优先级（低/中/高，默认中）。e2e 的假面板也改成真形状（`level` 数字 + `message` 数组）。

教训：**假面板的形状是照我们的理解写的，它只能证明"自洽"。**凡是"字段名/类型"这类契约，
只要能从 Android 端源码抄，就不要靠猜（`XboardCreateTicketRequest.kt` 一眼就能看到）。

**A-23 登录后立刻点「连接」必然失败：订阅还没落盘**

真面板上刚登录，界面已经进首页、连接按钮已经能点，但 `refreshAll()` 拉订阅是异步的：
用户手快一点，`manager.js` 的 `readSubscribe()` 读不到 `data/profiles/subscribe.yaml`，
直接抛「尚未拉取订阅，请先登录面板」——**明明刚登录完**。假面板时代测不出来（本地 mock 秒回）。

修法（`electron/ipc.js`）：把拉订阅收成 `pullSubscription()`（模块级 `subscribing` 变量保证同一时刻
只有一个在飞），`connect` 命令在连之前 `if (!hasSubscribeFile())` 就先自动补拉一次
（再 `setDirectDomains` + `prepareConfig`）。用户不用知道"先刷新订阅再连接"这个顺序。

教训：**"界面已经允许点"和"底层数据已就绪"是两件事。**凡是按钮的可用状态由异步数据决定，
要么禁用按钮，要么让点击路径自己补数据 —— 不能假设用户会等。

**A-24 我的页「我的套餐」点了完全没反应（导航白名单漏了一项）**

`app.js` 的 `handleClick` 末尾原来写死了一份白名单：
`["nav-orders","nav-tickets","nav-invite","nav-giftcard","nav-notices","nav-settings","nav-routing"].includes(action)`。
加「我的套餐」入口时忘了补 `nav-plans` —— 于是那一行**点了完全没反应：不报错、不弹窗、不跳转**，
看起来像"面板没数据"。改成前缀判断 `action.startsWith("nav-")` → `nav(action.slice(4))`，不再可能漏。

教训：**枚举式的分支表是漏项制造机**，能用前缀/规则判断就别列清单；界面自检必须"点一下看落点"，
只断言元素存在的话，这种"点了没反应"永远绿。

**A-25 首页/节点页被一条慢查询拖住 30 秒，而慢是我们自己造成的**

现象：真面板上点了连接，节点页/首页几十秒没内容；日志里 `slow command get_traffic 32469ms`。
查下去是两件事叠在一起：

1. `refreshAll()` 的结尾是 `await loadTraffic()`，而 `get_traffic` 走的是面板的流量明细聚合查询。
   于是"连上之后重绘"被它拖住 —— 用户在节点页等 20 秒也等不到分组卡。
2. 同一个面板查询被发了 **2~3 遍**：`loadTraffic()` 同时发 `get_traffic` + `get_traffic_log`
   （两者在 ipc 层各自去面板查一次明细），而 `loadTraffic()` 又被 `refreshAll()`、
   `nav('traffic')`、区间按钮各调一次 → 日志里能看到 **6 条并发 `get_traffic` 各 19 秒**。
   面板侧是聚合查询，重复并发把它自己压慢了（同一批查询串行跑只要 ~360ms/次，见 E-12）。

修法：`get_traffic` 顺手把过滤后的明细 `picked` 一起返回（明细不再单独发一次请求）；
`refreshAll()` 结尾改成后台补齐（`.then(() => 只在流量页/首页 render())`，不再 await）；
区间按钮改成"先 `render()`（高亮立刻跟手）→ 拉数据 → 再 `render()`"。

修完实测：同一套界面自检，`get_traffic` 再无一条超过 2 秒（`panel slow` 一条都没有）。

教训：**"慢"要先量再猜**，而且要先怀疑自己发了几遍 —— 重复的聚合查询能把对方压到超时，
然后看起来像"面板烂"。另外：**让界面等数据的地方，默认都该是"先画出来、数据回来再补一次"**，
除非那一步没数据就没法画。

---

### B. 打包

**B-1 打包后启动即崩 `Cannot find module 'js-yaml'`**

两个原因叠加：

1. `electron` / `electron-builder` 被 `npm install` 装进了 `dependencies`（它们该在 `devDependencies`）
2. `build.files` 白名单**覆盖**了 electron-builder 的默认 glob，而默认 glob 才会带上生产依赖

修：构建期依赖归 `devDependencies`；`files` 显式列出运行时依赖。

```json
"files": ["electron/**/*", "src/**/*", "package.json",
          "node_modules/js-yaml/**/*", "node_modules/ws/**/*"]
```

**B-2 `.gitignore` 把 `build/` 吃掉了**

仓库根 `.gitignore` 是 Android 项目的，忽略 `build/`。应用图标 `design/windows/app/build/icon.png` 因此进不了库。

修：`git add -f`。

**B-3 Windows 上拉不动依赖**

Electron 二进制与 electron-builder 的 bin 直连 GitHub 基本超时。必须：

```
HTTP_PROXY / HTTPS_PROXY = http://127.0.0.1:7890
ELECTRON_MIRROR = https://npmmirror.com/mirrors/electron/
ELECTRON_BUILDER_BINARIES_MIRROR = https://npmmirror.com/mirrors/electron-builder-binaries/
```

`scripts/build-portable.ps1` 里已经内置。

**B-4 `dist/win-unpacked` 被占住 → `EBUSY: resource busy or locked`**

上一轮跑起来的 `Polaris.exe` 没退，electron-builder 删不掉输出目录。打包前先 `taskkill`。

**B-5 应用自己的 `.gitignore` 把整个核心层吃掉了（最隐蔽的一个）**

`design/windows/app/.gitignore` 里写的是 `core/`——这个模式**匹配任意深度**的 `core` 目录，
本意是忽略内核二进制 `design/windows/app/core/`（61 MB，不能进库），
结果把 `design/windows/app/electron/core/`（8 个核心文件，manager/builder/rulesets/…）也一起忽略了。

后果：`git status` 永远干净，`git add -A` 永远加不上，**提交了 6 个 commit 的客户端缺整个核心层**，
clone 下来直接起不来。全程没有任何提示——`git status` 不显示被忽略的已跟踪目录之外的东西。

修：改成锚定的 `/core/`，只忽略应用根下的内核目录。

```bash
git check-ignore -v design/windows/app/core/mihomo.exe        # 应命中 /core/
git check-ignore -v design/windows/app/electron/core/manager.js  # 应无输出
git status --ignored --short design/windows/app | grep '!!'   # 审计还有没有别的源码被误伤
```

**教训**：`.gitignore` 里的目录模式一律加前导 `/`；新增源码目录后跑一次
`git status --ignored` 审计，别只看 `git status`。

---

### C. 内核 / mihomo

**C-1 首次连接卡 19 秒**

没带规则库时，mihomo 启动会自己下载：

```
msg="Can't find GeoIP.dat, start download"
msg="Download GeoIP.dat finish"        ← 19,449 ms
```

没网就直接起不来。而且它要的是 `GeoIP.dat`，因为我们设了 `geodata-mode: true`。

修：
- 去掉 `geodata-mode`，用 Meta 格式（`geoip.metadb` + `geosite.dat`）
- 规则库随包分发到 `resources/geo/`，`ensureGeodata()` 首跑拷进 `data/`

结果：**19,449 ms → 2 ms**。

**C-2 `/proxies` 的响应形状在不同版本不一样**

mihomo v1.19 返回 `{proxies: {...}}`，我们按裸 map 解析 → 节点列表读空、切换节点报"不在分组中"。

修：`unwrapProxies()`，两种形状都吃。**这类"契约随版本漂移"要靠真实拉起内核才测得出**。

**C-3 v1.19 移除了 `global-client-fingerprint`**

内核启动时报 error（不致命但会污染日志）：

```
level=error msg="The `global-client-fingerprint` configuration is removed,
                 please set `client-fingerprint` directly on the proxy instead"
```

修：删掉该键。

**C-4 Windows 上没法优雅停内核**

Node 的 `child.kill()` 在 Windows 上是 `TerminateProcess` —— 没有 SIGTERM 语义。TUN 模式下内核来不及调用 `tun.Close()`，会留下虚拟网卡和残留路由。

修：`net/tun.js` 的 `stopProcess(proc, graceMs)` —— 先给自己一个宽限期（普通 400ms / TUN 1500ms），超时才强杀。这是能做的最好情况，不是完整的解决。**残留清理做成设置页里的手动入口**（动网络适配器太重，不自动删）。

**C-5 `--compatible` 变体**

Windows 用 `mihomo-windows-amd64-compatible-*.zip`（不带 AVX 指令）。面向"任何 Win10+ 机器"，兼容性优先于那点性能差异。

**C-6 provider 是异步初始化的，读太早会读到 0 条**

调试内置分流时踩了这个：`GET /providers/rules` 立刻返回 16 个 provider，其中 `gp_cn`、`gp_google`、
`gs_geolocation_ncn` 恒 `ruleCount: 0`，日志里**一条 error/warning 都没有**，看着像内核解析 bug。

实际是**竞态**：大文件（200 KB ~ 600 KB，上万条规则）解析慢，查询先到了。
按文件体积做二分（2000 条→0 条、截断到 1000 条就正常）会得出"体积阈值"的错误结论——
因为截断同时也缩短了解析时间。

**凡是验证 provider 是否加载，必须轮询到全部 `ruleCount > 0`**，不能查一次就下结论：

```js
for (let i = 0; i < 30; i++) {
  const p = await controller.get('/providers/rules');
  if (Object.values(p.providers).every(v => v.ruleCount > 0)) break;
  await sleep(300);
}
```

顺带排除的几条"看起来像元凶"的内核代码（都不是）：`common/utils/hash.go` 的 `MakeHash`（md5，
不可能撞）、`resource/vehicle.go` 的 `FileVehicle.Read`（每次都重读并重算 hash，不看 oldHash）、
`resource/fetcher.go` 的 `f.hash.Equal(hash)` 早退（真发生会静默 0 条，但触发不了）。

**C-7 规则集文件必须放在 `-d` 目录内**

mihomo 的 `type: file` provider 走 `C.Path.IsSafePath(C.Path.Resolve(schema.Path))`，
而 `IsSafePath` 只放行 `-d`（homeDir）下的子路径。所以内置规则集不能直接从
`resources/rules/` 读，必须先铺到 `data/rules/`，配置里写相对路径 `rules/<key>.yaml`。

另一个岔路：`acl_*` 是 classical/text 格式，里面有 mihomo 不支持的 `URL-REGEX` 规则，
会逐条 skipped（`unsupported rule type: URL-REGEX`），**无害**，属上游 ACL 表的冗余。

---

### D. 面板契约

**D-1 登录直接失败：`session.giftPath is not a function`**

`detectBackend()` 里调了 `session.giftPath()`，而 `giftPath` 是模块级函数，不在 `session` 上。

**这个 bug 数据层测试抓不到**（e2e 里写死了登录成功路径），是真实点击登录才暴露的。

**D-2 一次兑换失败把后端标记翻掉**

礼品卡兑换失败后 `session.backend` 被改成 `xiaov2b`，导致**后续所有请求**走错分支 —— 表现是流量明细页永远空。符号化的后端标记不该被一次失败改写。

修：两条路径互为兜底，但不改会话标记。

**D-3 流量明细返回裸数组**

`user/stat/getTrafficLog` 返回**裸 JSON 数组**，不裹 `{status, data}`。我们的 mock 一开始裹了，客户端也就只认裹着的形状，双向都错。

修：客户端两种形状都接受；mock 改成返回裸数组。

**D-4 订单状态映射**

数字状态直接查表错了：`2 → 'completed'`，而 UI 认的是 `'done'`。改成数字与字符串两套映射。

**D-5 面板字段名各分支不统一**

所有字段映射都写成多候选兜底（`pick(obj, ['plan_name', 'name', 'description'], '')`）。这层兜底是从 Android 端 `adapter/xboard/*` 与 `adapter/xiaov2b/*` 抄的契约，**但没有真实面板就可能兜不到**。

**D-6 流量页的"今天/本周/本月"原来是自己在本机算的（用户第 5 条）**

原实现是读本地 `traffic.json`（内核每 5 秒推的速率累加）自己分桶 —— 换机器、重装、清数据之后
数字就归零，和面板上的账号用量完全对不上。用户的原话是"应该使用站点流量明细的数据"。

改成：`get_traffic` 走面板 `user/stat/getTrafficLog`（`range=today|week|month|all`），
把逐日明细按区间求和，和 `user/getSubscribe` 的账号总量一起返回；本机 `traffic.json` 只留
"本次会话"的实时速率与上下行总量。

两个坑：
1. **不要拿账号的 `u+d` 去判断"明细有没有数据"**。面板按 GB 两位小数取整，
   跑了 29 KB 会显示成 `0.00`；于是"用 0 判断空"会把有明细的账号判成空态。
   断言要写成不变式：*站点合计 === 明细逐行求和*，明细为空时合计必须是 0。
2. **不要乘 `server_rate`**（倍率）。面板明细里给的是**已计费**字节数，再乘一遍会翻倍。
   验算方式：把 `month` 区间求和，和账号的"已用"比 —— 真面板上 128.0 GB vs 128.5 GB，对得上。

---

### E. 自检方法论（最该记住的一节）

**E-1 只查 DOM 存在性不算验证 UI**

我写了 `--smoke` 检查 `navItems`、`views` 数量、`textLength` —— 它报告"渲染正常"。然后用户打开就发现**拖不动、最大化错位、点登录没反应**。

空壳页面和能用的界面在 DOM 断言下长得一模一样。

修：`scripts/selftest-ui.js` + `Polaris.exe --uitest`，真的去操作：填表单、点按钮、开弹窗、滚到底、最大化、量溢出。**38 项**。

**规则：用户报出来的 bug，都先写成一条会失败的测试，再修。**

**E-2 app.quit() 会丢掉未刷盘的异步日志**

自检结果只出现在日志里，`app.quit()` 时 `createWriteStream` 的缓冲直接丢。表现是"跑了但什么都没输出"。

修：结果用 `fs.writeFileSync` 同步落盘。

**E-3 崩溃的自检进程会占住单实例锁**

上一轮 `--uitest` 因 `ReferenceError: fs is not defined`（uitest 回调里没 require fs）没退出，一直占着锁 → 之后每次运行都立刻 `second instance detected, exit`，看起来像"自检甩手不干"。

修：`main.js` 顶部统一 `require('fs')`；跑测试前 `taskkill`。

**E-4 mock 面板要故意喂脏数据**

`scripts/mock-panel.js` 的订阅里混了：重名节点、信息伪节点（"剩余流量：86.5 GB"）、被订阅劫持的 `external-controller` / `secret` / `geox-url`。

这样跑 e2e 才能同时验证"清洗链路在网络路径下也生效"，而不只是单元测试里生效。

**E-5 断言失败先怀疑断言**

补内置分流的 37 条 core 断言，首跑 3 条红。3 条全是**断言写错**，不是产品缺陷：

- 断言 no-resolve 只有 2 条，实际 3 条（漏了 `acl_chinacompanyip`）
- 用 `key.includes('apple')` 筛"内联组的规则集"，误命中 `gs_apple`
- 断言"不传 routing 就不生成内置组"失败——**这条恰好抓出一个真 bug**：
  `builder.build()` 的策略组数组直接引用了入参 `doc`，`push/unshift` 把调用方的配置改了

**教训**：写断言时先把期望值打印出来核对，再固化；三条红里能出一条真 bug 就够本。

**E-6 `--uitest` 不能带 `--mock`**

`Polaris.exe --uitest --mock` → 5 通过 / 2 失败，报"登录页没出来"。原因是 `--mock` 让渲染层走
`api.js` 的内置 Mock，其 `get_settings` 返回 `authed: true`，界面直接进首页——**是 mock 把被测前提改掉了**，
不是界面坏了。界面自检必须走真实 IPC + 自带的假面板（`selftest-ui.js` 自己 `mockPanel.start(0)`）。

**E-7 运行时测试（照安卓那套六步法）：先断言"点击命中了元素"，再怀疑产品**

`rt-test.js` 第一版白追了两轮假 bug，教训都在这三条上：

- **点击要断言命中**。`el.click()` 找不到元素时是**静默 no-op**，表现和"点了没生效"一模一样。
  第一版用 `.nav-item[data-route="routing"]` 进分流页 —— 侧边栏压根没这一项（设计稿就只有
  首页/节点/流量/我的/设置五项），于是整节分流页的点击全部落空，报出 4 条"假 bug"。
  现在每次点击未命中都记进 `missedClicks`，结尾统一断言"没有静默 no-op"。
- **挑测试对象要挑对**。开分流组时挑了"第一个关闭的组"，结果挑到**内联组**（只有内联规则、
  不带 rule-provider 文件），打开它内核 provider 数量当然不变 —— 又一条假 bug。
  现在挑 `!enabled && !inline && count > 0`。
- **设计上的拦截不是 bug**。连接态下点「关系统代理」会被拦住并提示"断开连接后再关闭系统代理"
  （否则流量绕过内核）。测试该断言"拦住了 + 注册表没动"，而不是"关掉了 + 已还原"。

另外：**操作之后一律从内核回读**（`/configs`、`/proxies`、`/connections`、`/providers/rules`），
不要相信界面的乐观更新 —— 这正是运行时测试和 UI 自检的分工：UI 自检管"点得到、画得出"，
运行时测试管"点下去之后内核真的变了"。

**E-8 自检绝不能真的执行"自我替换"（差点把成品包换成测试假文件）**

e2e 里那两条更新断言原来是按**开发态**写的：`can_apply === false`、
`expectReject(apply_update)`。它们只在开发态成立。成品包 + 便携目录下
`can_apply` 会是真的，于是 `--doctor` 在 `dist/win-unpacked` 上跑的时候：
真的下载了测试用的假 zip（`Polaris-9.9.9.zip`，366 字节，里面是 `MZxxxx…` 的假
`Polaris.exe`）、真的写了 `apply-update.cmd`、脚本真的走到了 `copying` 那一步 ——
**只因当时另一个自检正占着 `Polaris.exe`，robocopy 才 `failed` 没覆盖成功**。
（`data/update/apply-update.log` 里留着 `waiting for pid → copying → robocopy failed`。）

修法：`updater.canApply()` 为真时**只断言"允许自装"**，绝不调用 `apply_update`；
开发态才断言被拦住。另外那两条 `packaged === false` 的断言也改成按 `packaged` 分支。

教训：**自检脚本里任何"会改安装目录/写注册表/杀进程"的调用，都要先问一句
"在成品包里跑会怎样"** —— 开发态被拦住不等于成品包也被拦住。

**E-9 真面板联调脚本自己的三个坑（都长得像"产品坏了"）**

`scripts/real-test.js` 第一次跑，4 条红里有 3 条是脚本自己的问题：

1. **没带 `--panel` 去登录** → 捡起 `settings.json` 里上一轮留下的地址，
   连到了早就关掉的 mock 面板（`http://127.0.0.1:8436`），报"登录失败"。
   修：`commands.login({email, password, panel})` 显式带上地址。
2. **`--restore` 之前先问站点信息** → `session.panelUrl` 还是空的，
   `siteInfo()` 必然抛"尚未配置面板地址"。**顺序错了**，不是接口坏了。
3. **`--data=<目录>` 时没把 Electron 的 userData 一起指过去** → `safeStorage`（DPAPI）
   的密钥在 userData 的 `Local State` 里，指错了就解不开 `credentials.dat`，
   日志报"凭据无法解密（换机或 data/electron 缓存丢失）"—— 看起来像**登录态跨重启失效**
   （A-20 那个 bug 复发了），其实只是自检没站在同一个 userData 上。
   修：`real-test.js` 和 `main.js` 一样 `app.setPath('userData'/'sessionData', <root>/data/electron)`。

教训：**"复现了老 bug"和"新 bug"要用同一把尺子区分** —— 先在怀疑产品之前，
确认测试自己站在正确的目录/会话/顺序上。

顺带：`--restore` 让自检可以**直接用用户已经登录好的会话**跑（凭据是 DPAPI 加密的，
拷一份 data 到临时目录就能用），这样验证"真实使用现场"不需要再要一次密码。
`POLARIS_DATA_DIR` 就是为这个加的逃生口（不设它时行为与以前完全一致）。

**E-10 把假面板、四条自检线和演示数据全删了（2026-10-10）**

删掉：`scripts/mock-panel.js`、`selftest-ui.js`（`--uitest`）、`selftest-e2e.js`（`--doctor`）、
`rt-test.js`（`--rttest`/`--soak`）、`selftest-core.js`（`npm test`），以及 `main.js` / `package.json` /
`src/js/api.js` 里对应的入口：`--mock`（演示数据）、`--smoke`（DOM 断言）、`--restore-probe`（登录态探针）、
内置演示数据（浏览器直接打开 `src/index.html` 现在明确报错，不再伪造数据）。
连核心层 253 项 `npm test` 也删了 —— 它本身不造假，但同样是"自动绿灯"，留着就会被当成"测过了"。

**为什么删**：这几条线要离线跑就必须自带一个**假面板**和**假账号**（`ui-tester@example.com` /
`rt-tester@example.com`）。假面板的响应形状是照我们自己的理解写的，于是它只能证明"自洽"，
给不出"能用"的结论 —— 真实代价是 A-22：建工单我们发 `content`，假面板收得下（绿），
真面板按 Android 契约只认 `message`，一建单就是空工单（红）。
**一个会给错安全感的绿灯，比没有绿灯更贵。**

**教训（比"删测试"这件事本身重要）**：
- 判断一条自动线值不值钱，看它**是否会因为"我方理解错了"而天然绿**。纯函数、配置组装、
  "把真内核拉起来读回一次"这类不依赖外部约定的断言才是安全的；凡是"我们自己造一个假对面来对话"的，
  都要在断言旁边标注它是自洽检查，不能进验收。
- 删测试线时**要连入口一起删干净**（`main.js` 的早退分支、`package.json` 的 scripts 与 `build.files`、
  `build-portable.ps1` 里的自检步骤、`DEVNOTES` / `README` 的命令表），
  否则会留下"文档说能跑、其实文件没了"的坑。
- **删完必须立刻验证应用还能起来**：这次删掉 `main.js` 三个分支与 `api.js` 的数据层兜底之后，
  真启动一次（`npm start`）确认日志只有一条 `WARN siteInfo failed: 尚未配置面板地址`（干净数据目录的正常提示）、
  `ERROR` 计数为 0 —— 删测试代码也是改产品代码，同样要验。

**E-11 自检自己"点空了"：坐标和命中判定分两次跨进程取，中间被 `render()` 换掉**

现象：界面自检里点「我的页 → 退出登录」，诊断显示"坐标命中就是它 = true"，
但点完 `route` 变成了 `home`、确认框没出来 —— 看着像产品的点击绑定坏了。

真因在自检自己身上：`realClick()` 先一次 `executeJavaScript` 取中心坐标，再另一次取
`elementFromPoint` 做命中判定，然后才 `sendInputEvent`。**这中间隔着跨进程往返**，
只要页面在这期间 `render()` 过一次（本应用很多地方异步重绘），坐标就已经过期，
点下去落在别的元素上。诊断信息之所以"看起来正常"，是因为它也是**在点击之前**量的。

修法：把「`scrollIntoView` + 取坐标 + `elementFromPoint` 命中判定」放进**同一次** `executeJavaScript`，
最多重试 3 次；并且装一个捕获阶段的 `mousedown`/`click` 记录器（`window.__clickLog`），
失败时把"到底是谁收到了这次点击"打出来。修完同一处一次通过（`down SPAN | 退出登录`）。

教训：**自检里任何"测量 → 动作"的两步操作，都要问一句"中间页面会不会变"。**
能合成一次执行的，就不要分两次。

**E-12 定位"面板慢"：先给请求分段计时，别猜**

真面板上出现 `get_traffic 15~32 秒`。要判断"是面板慢、是 DNS/TLS 慢、还是主进程被同步调用堵住"，
靠猜没用。给 `panel/client.js` 的 `request()` 挂上 `req.on('socket')` 的分段计时
（`lookup`/`connect`/`secureConnect`/`reused` + 响应到达的 `ttfb`），超过 2000ms 打一行
`panel slow GET /user/xxx 19051ms dns=.. conn=.. tls=.. ttfb=..`。

结果：面板本身**一点都不慢**（`/user/stat/getTrafficLog` 串行 3 次 344/358/369ms，
10 个接口串行合计 3.6 秒、并发 10 路 1.1 秒），而 `panel slow` 一条都没有 ——
说明那 15~32 秒是我们**自己并发重复查询**把面板压出来的（见 A-25）。

教训：**"慢"这类问题，第一件事是把时间拆开。**拆不开就只能靠猜，猜出来的结论往往是错的
（我一度怀疑是 mihomo 吃满 CPU 饿死 Node，实测本机 16 逻辑核，直接排除）。

**E-13 界面自检必须自己登录、自己跑，不能让用户代劳**

`scripts/ui-test.js` 的第 1 段是"先用界面把旧会话退掉，再用真账号从登录页登进去"。
这样每次跑都从"干净未登录"开始，测的是用户真会走的那条路（登录 → 拉订阅 → 连接），
而不是"我本地已经登录好了，点两下看看"。
用户为此明确纠正过一次：**测试账号是给我测试用的，不是让用户自己登录的**；
界面层的 bug 必须我自己测完修完，再交给他做体验调整。

---

### F. 本机环境 / 工具链

| 坑 | 处理 |
| --- | --- |
| `execute` 里用 `start` 拉起 GUI 程序会一直等进程退出，被下一条消息打断判定为"未完成" | 用 `powershell Start-Process -Wait`，或让程序自己退出 |
| GUI 子系统的 exe 不挂控制台，直接跑拿不到输出 | 输出重定向到文件再读 |
| 控制台是 GBK，Node/Python 打印中文会 `UnicodeEncodeError` | 结果写 UTF-8 文件，再用 `read_file` 读 |
| `cmd` 用 `&&` 串多条命令有时整条返回空 | 关键命令分开执行 |
| `git commit -F -` 在 cmd 里拿不到 stdin | 先把消息写成文件，再 `-F <路径>` |
| `git rm -r --cached` 之后再 `git rm -r` 会报 pathspec 不匹配 | 索引与工作区两步走，或直接删目录再 `git add -A` |
| 仓库根 `.gitignore` 忽略 `build/` | `git add -f` |

---

## 四、验证体系怎么用

```bash
cd design/windows/app

# 首次准备
npm install
npm run core                  # 拉 mihomo + wintun 到 core/
npm run geo                   # 规则库到 resources/geo/（优先用同仓库 Android assets）

# 开发
npm start                     # 真实模式（唯一的运行方式）

# 真面板联调（要真账号；密码只在命令行给一次，不写进任何文件）
node_modules\electron\dist\electron.exe scripts\real-test.js `
    --panel=https://app.pinxiaoche.top --email=<测试账号> --password=<口令>
# 或者直接用已经登录好的那份 data（凭据是 DPAPI 加密的，拷一份到临时目录就能用）
copy <成品包目录>\data <临时目录>\data
node_modules\electron\dist\electron.exe scripts\real-test.js --restore --data=<临时目录>

# 真面板界面自检（开真窗口真点；用独立 data，不动你日常登录的那份）
Remove-Item Env:ELECTRON_RUN_AS_NODE
Start-Process -FilePath .\node_modules\electron\dist\electron.exe -ArgumentList `
  '.','--uitest','--panel=https://app.pinxiaoche.top','--email=<测试账号>','--password=<口令>','--uitest-data=E:\AI\_uitest_data'
# 报告：E:\AI\_uitest_data\data\uitest-report.txt（读时加 -Encoding UTF8）

# 打包（**必须先得到用户明确同意**）
powershell -ExecutionPolicy Bypass -File scripts/build-portable.ps1
```

> 本机注意：`npx` / `npm.ps1` 被执行策略禁止，打包要直接调
> `node node_modules\electron-builder\cli.js --win --x64`，并设
> `ELECTRON_MIRROR` / `ELECTRON_BUILDER_BINARIES_MIRROR` 指向 npmmirror；
> 跑 Electron 前记得 `Remove-Item Env:ELECTRON_RUN_AS_NODE`（本 harness 会带这个变量）。

别人机器上出了问题怎么查（**自检开关都已删除，只剩日志**）：

> 排查顺序：先看 `data/logs/polaris.log`（每次启动、内核起停、面板请求、
> 系统代理改写都记在这里），再看 `data/config.yaml`（内核真正吃进去的配置），
> 最后看 `data/profiles/`（面板下发的订阅原文）。


演练自我替换（**会覆盖当前目录，务必先整个拷一份再跑**）：

```
copy 一份 win-unpacked 到临时目录 → 在副本里删掉 resources\rules\gs_apple.yaml（当"被替换"标记）
→ 放一个 data\USERDATA.txt（当"必须保留"标记）
→ 用任意 http 服务把新 zip 发出来
→ 副本里的 Polaris.exe --updtest-version=9.9.9 --updtest http://127.0.0.1:8124/Polaris-portable-1.8.0.zip

验收：gs_apple.yaml 回来了、USERDATA.txt 还在、data\update\apply-update.log 里有 done, restarting、
     重启后的进程在跑、data\update 里只剩 apply-update.cmd 与 apply-update.log（包和 staging 都清掉了）。
```

> **开关要写在 URL 前面**，或者写成 `--updtest-version=9.9.9` 的等号形式 ——
> 位置参数之后再跟 `--switch value`，Electron 会在主进程起来之前就退出（exit -1、无日志），
> 见踩坑 A-13。这一条真的花了十几分钟才从"应用崩了"里认出来。
>
> 本机注意：`npx` / `npm.ps1` 被执行策略禁止 → 用 `node node_modules\electron-builder\cli.js`、
> `node scripts\selftest-core.js` 直接跑；跑 Electron 前必须 `Remove-Item Env:ELECTRON_RUN_AS_NODE`
> （**而且这个变量会传染给子进程**，用 Node spawn 起成品包时要把 env 过滤掉，否则 Electron 退化成纯 Node，
> 报 `<exe>: bad option: --smoke` 并秒退）。

---

## 五、下次继续的入口

按优先级：

1. ~~真实面板联调~~ ✅ 已打通（数据层 `scripts/real-test.js` 39-40 项、界面层 `scripts/ui-test.js` 73 项，都全绿）。
   还没覆盖到的真面板路径：**注册/找回密码/改密码**（会真改账号，没敢跑）、**下单与支付**
   （`/user/order/getPaymentMethod` 在真面板返回空数组 → 购买套餐页会没有支付方式可选，
   这是面板侧配置问题，不是客户端 bug；真面板还提示走 Telegram 下单）、
   **工单回复**（建单验过了，回复没验）、**礼品卡兑换**（要真卡密）。
   界面层还没覆盖的：**设置页的每一项都点一遍**（现在只验了「分流与订阅」段存在）、
   **弹窗的键盘操作**（Esc 关闭、Tab 顺序）、**托盘菜单**（收进托盘后右键各条目）、
   **长列表性能**（要一份几百节点的订阅）、**多屏 DPI 缩放**、**TUN 下的同一套走查**。

2. **TUN 模式实机验证**（需要用户同意，会临时接管网络栈）
   开 TUN → 验证流量 → 关 TUN → 确认路由/DNS 还原、虚拟网卡状态。先跟用户确认再动。

3. **自动更新的收尾**（自替换已实装并真跑过，见 §二.5）
   - 没有回滚：覆盖失败只会留下版本混杂（`robocopy` 不删文件），可以再加"上一版备份 + 失败回滚"
   - 没有校验：只判了 zip 头和 `Polaris.exe` 是否存在，没有签名/哈希校验
   - 后端还缺 `update_windows_url`：现在 `remote.updateInfo()` 已经优先取它，
     但真实面板得先把这个字段补上，否则 Windows 端只能退回「前往下载」

4. **内置分流还能再往前一步的地方**（排序与自定义规则组已在 `6c72762` 做完，见 §二.6）
   - 自定义规则**按组整块编辑**，没有"单条规则拖动排序"：组内规则按书写顺序匹配，
     要调整靠编辑弹窗里挪行。组之间可以用 ↑↓ 排。
   - 自定义规则**只能内联写**，不能引用 `data/rules/` 里的 yaml 规则集文件。
     要支持的话：UI 加一个"从文件导入"，把文件塞进 `data/rules/` 再在 `rule-providers` 里加一条；
     注意 `gs_*` 是 domain/`gp_*` 是 ipcidr/`acl_*` 是 classical，behavior 不能写错。
   - 自定义组只能选**节点选择 / 直连 / 拦截**三个出口，不能指向另一个策略组（避免用户写出环）。

5. **订阅里带的 `global-client-fingerprint` 会打一行 error 日志**（已发现，等用户定）
   mihomo v1.19 已移除这个顶层键，面板下发的订阅里还带着它，于是每次启动内核都打
   `level=error msg="The \`global-client-fingerprint\` configuration is removed, please set \`client-fingerprint\` directly on the proxy instead"`。
   **功能不受影响**（只是噪音）。修法是一行：在 `electron/core/sanitizer.js` 的 `dropKeys` 里加上这个键
   （和已经丢掉的 `geox-url`/`external-controller`/`secret` 一样）。**已问用户，等答复再动**。

6. **浅色主题的三级文字对比度（设计决策，非代码问题）**
   `design.css` 的 `--text3: #8e8e93` 在 `--bg: #f5f5f7` 上实测 **2.99:1**，低于 WCAG AA 的 4.5:1。
   它用在 `.page-sub` / `.section-label` / `.node-line` / `.acc-sub` / `.login-card .sub` 等提示性文字上。
   压到 4.5:1 需要 ≈`#6e6e73`，也就是和 `--text2` 同色、文字层级消失。
   **本端不擅自改设计稿的调色板**，请设计方定：要么接受 3:1，要么把 `--text2`/`--text3` 重新拉开。
