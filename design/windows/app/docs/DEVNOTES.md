# Polaris Windows 客户端 · 现状与踩坑记录

> 最后更新：2026-10-10（**第 9 轮：面板「网络错误」根因 = 本机 DNS 回黑洞地址 + 连接与拉订阅撞车**）
> 用户第 9 轮提的 3 条：①TUN 堆栈对话框里「（默认）」还标在 `gvisor` 上、顺序也没把默认项放最前
> —— 改成 `system / gvisor / mixed`、「（默认）」标到 `system`（`store.js` 的默认值早就是 `system`，
> 但**存储值优先于默认值**，老 data 目录里存的还是 `gvisor`，A-33）；②登录页那句「仅仅是一个拼车网站」
> 是**面板给的**（`/guest/comm/config` 的 `app_description`，游客接口不需要登录，也没落盘缓存，
> 每次启动现拉），不是内置文案；③**「网络错误：面板请求超时」的真根因**：本机路由器会把面板域名
> 间歇性解析成黑洞地址（实测两个地址裸 TCP 443 都 8s 无应答），
> 而**同一个域名**用 `dns.resolve4`（c-ares 直接问 DNS 服务器）拿到的是真地址
> （Cloudflare）——谁先连黑洞谁就卡满 20s 超时。
> 现在 `panel/client.js` 做三层兜底：**①两条解析路合并**（getaddrinfo + c-ares，去重）
> → **②按地址短超时 + 换地址重试 + 坏地址记忆**（5 分钟，只降级不排除，`connectionAttemptTimeout`
> 也会记账）→ **③公共 DNS**（`223.5.5.5` / `119.29.29.29` / `180.76.76.76`，只在前面都连不上时用；
> `1.1.1.1` 实测恰好回那两个黑洞，**故意不采用**）。顺带修两处：每条解析路加 2.5s 上限
> （DNS 被挂住时请求不再干等，日志也不会只写「试过 未知地址」）、面板答了 4xx **不再**当连接失败重试。
> 界面线同时抓出一个真 bug **A-41**：开机自动拉订阅（内部是"断开→重连"）与用户点的连接撞车，
> 把刚拉起来的内核拆掉、主进程报 `read ECONNRESET`，最后**内核是死的**（延迟测试全空）——
> 修法是给内核操作加一把串行锁 + 渲染层 `state.busy` 期间不拉订阅（忙完 15s 后再看一次）。
> 数据层 **182 / 0**、界面层 **162 / 0**（真账号真面板真节点）。见 §三 A-41、D-12、E-19。
>
> 同一日更早：**第 8 轮：删两个会打架的分流组 + 24 小时更新与落点实测 + 后端自动识别 + 全功能覆盖**
> 用户第 8 轮提的 5 条：①**删掉「📢 Google FCM」与「📢 苹果推送通知」两个分组**，Google 正常走
> 「🌏 Google」、苹果正常走「🍎 苹果服务」（APNs 的 9 段 IP 内联进苹果服务组）——
> 这两个组的规则本来就被前面的组抢先命中（FCM 44 条全被 `+.google.com` 覆盖，配置写着直连实际走代理，
> 见 OpenViking `polaris分流规则失效问题`）；顺带**全面排查 24 小时更新会不会真的更新、更新哪些规则、
> 分流能不能真的分对**，方法是让内核真拉一次、把请求真发出去再看内核日志里的匹配行（新增两整段自检）；
> ②**网站信息必须真从面板读**（审计结论：站点名/描述/URL/公告/套餐/Telegram 全部来自面板接口，
> 唯一的内置文案是登录页在面板没给描述时的兜底句子）；③**后端要自动识别**（照安卓端
> `LoginViewModel.detectBackendType`：`/guest/comm/config` 里出现验证码相关字段就是 xboard，
> 否则 xiaov2b；两端只有礼品卡路径与"流量明细接口只有 xboard 有"两处差异），识别结果落 store 并回显；
> ④**先自己把功能全测一遍**（real-test 从 108 项扩到 **168 项**，新增"其余 32 个命令逐条真调用"，
> 破坏性的真下单/真支付/真自我替换/提权重启明确跳过并打印说明）；⑤未连接时首页那行
> 「请先登录面板」**删掉**（能进这个页面的人都登录了，写这句话没有意义）。
> 界面层顺带补 7 条断言（未连接不显示小字、后端识别结果、分流表 25 组、苹果服务 12 条内联、
> 流量页后端名与明细接口提示）。数据层 **168 / 0**、界面层 **159 / 0**（真账号真面板真节点）。
> 见 §三 A-39、A-40、D-10、D-11、E-18。
>
> 同一日更早：**第 7 轮：用户第 7~10 条 + 两个"点了没反应"**
> 用户第 7 轮提的 4 条：⑦节点页连上后**自动跑一次延迟测试**（不用再进节点页手动点）；
> ⑧节点顺序**按网站下发的顺序**，不再自己排（根因：内核 `slices.Sort(AllProxies)` 会把
> `include-all` 并入的节点按名字重排 → 改成显式列成员，A-36）；⑨本机代理端口**保存后长久生效**、
> 与连接同步（`mixed_port` 落 store + `prepareConfig` 用它 + 保存后问"要不要断开重连"）；
> ⑩端口被别的代理软件占着时**优雅关闭它**（新增 `electron/net/portowner.js`：先问、再优雅
> `taskkill /PID`、250ms 轮询、最后才 `/F`；既不假装连上，也不偷偷杀进程）。
> 另外两个"点了没反应"都是真 bug：**拖动分流行后浏览器补发的 click 点开了「指定出口」弹窗**，
> 遮罩常驻把后面所有操作挡死（A-35）；**「我的」页的到期提醒/流量提醒开关点不动** ——
> `.switch[data-setting]` 的点击处理只挂在 `bindSettings()` 上，开关搬到「我的」页后就没人绑了（A-38）。
> 顺带把"重绘吞点击"这一类问题从根上收口：指针还按着的时候 `render()` 不换 DOM（A-27 延伸）。
> 数据层 **108 / 0**、界面层 **152 / 0**（真账号真面板真节点）。
> 见 §三 A-35 A-36 A-37 A-38、E-17。
>
> 同一日更早：**第 6 轮：入口各归各位 + 流量页数字调对**
> 用户第 6 轮提的 6 条：①设置页 TUN 堆栈默认改 `system`；②设置页「分流与订阅」整段删掉
> （订阅链接搬去我的页、重新拉取订阅本来就在节点页）；③到期提醒/流量提醒从设置页搬到我的页；
> ④流量曲线标题「本机实时速度」→「**本机实时用量**」（画的是用量不是网速）；
> ⑤鼠标划过曲线显示那个时间点的下载/上传读数；⑥流量页「面板累计」的
> **已用流量 0 B / 套餐 未订阅**（用户标注"没正确调用"）——根因是流量页自己又拼了一套口径
> （`u.u + u.d` / `transfer_enable`），与「我的」页（`get_plan`）**不同源**，面板一抖动就退化成
> "看起来没订阅"；现在统一走 `ipc.js planSnapshot()` 的字节口径，`panel/client.js userInfo()`
> 加 45 秒短缓存（只缓存取到套餐的结果，抖动不会把"未订阅"缓存住），并删掉「本次峰值」「在线节点」
> 两行（连带 `peak` 记账与 `online_nodes` 计数一起删干净）。
> 见 §三 A-34、D-9、E-17。
>
> 同一日更早：**第 5 轮：默认分流表按用户口径重排 + 三处界面诚实化**
> 用户第 5 轮提的 5 条：①**默认开启的分组与顺序按他的截图来**（20 组开 / 7 组关，
> 顺序 = AI 与流媒体 → 社交 → Google → Apple → 微软 → 游戏 → 国内直连/穿墙 → 拦截），
> 他明确说「安卓我也在调了」，所以这一版默认值与安卓端当前默认**故意不一致**；
> ②节点页里「自动选择 / 故障转移 / DIRECT」不再显示「其他 / 未测」（看起来像坏了），
> 改成「自动 / 备用 / 直连」+ 当前出口小字，并在 `real-test.js` 里**让内核真的从这三个出口各出去一次**；
> ③「分组调好了位置，外面的节点不跟着变」——根因是 `mihomo /proxies` 是 Go map 序列化、键顺序随机，
> 界面按名字排序就把拖动顺序丢了，改成按生成配置时的真实顺序重建索引（`manager.js groupOrderIndex()`）；
> ④入口与页头文案逐字统一（订阅套餐/礼品卡兑换/邀请返利/公告通知）；⑤套餐说明与公告正文
> 支持 Markdown 渲染（新增 `src/js/md.js`，先转义再解析，链接统一交主进程 `open_external`）。
> 数据层 **89 / 0**、界面层 **122 / 0**（真账号真面板真节点）。
> 见 §三 A-31 A-32 A-33、D-8、E-16。
>
> 同一日更早：**第 4 轮：分流模型改成与安卓端同一套**
> 用户要求「跟 polaris 安卓 app 一样，屏蔽网址下发的，只用本地的策略，策略更新也照安卓端」，
> 于是把原来的**合并模型**（面板规则 + 我的规则插队、面板组复用）整体换成**替换模型**：
> 面板下发的 `proxy-groups` / `rules` / `rule-providers` 全部丢弃，改用与安卓端**逐字一致**的
> 25 组本地表 + 4 个结构组（`🚀 节点选择` / `自动选择` / `故障转移` / `🐟 漏网之鱼`，全部 `include-all`）
> + 14 条内网直连 + 12 条苹果推送内联；`rule-provider` 从 `type: file` 改成
> **`type: http` + `interval: 86400` + 随包种子预播种（只补缺失、不覆盖内核下载的新版）**；
> 节点名撞上保留名时整体降级回面板配置（与安卓端 `checkNameCollisions` 同款）。
> 分流页加了「本地分流」总开关。数据层 **77 / 0**、界面层 **100 / 0**（真账号真面板真节点）。
> 这一轮抓出 2 个真 bug 与 2 类断言写错：A-29（`include-all` 的组里没有节点名 → 连接前节点页全空）、
> A-30（直连/拦截型分组显示「未测」）、E-15（`/providers/rules` 响应被 `{providers:{}}` 包裹；
> 等待条件里混了两个来源的子条件）。见 §二.4、§三 A-29 A-30、E-15）
>
> 同一日更早：**按用户第三轮反馈改完并跑绿两条线**：① 节点页两张「直连」分类卡合成一张
> （当时用 alias，第 4 轮换模型后 alias 已删）；② 分流顺序改成**拖动**（↑↓ 按钮删掉）；
> ③ 我的页与手机端「我的服务」逐项对齐（订阅套餐/礼品卡兑换/我的订单/邀请返利/我的工单/公告通知/Telegram）；
> ④ 设置页删掉与节点页重复的「分流规则」入口与整个「面板」段；⑤ 邮箱不再打码。
> 这一轮抓出 3 个只有"真点/真拖"才暴露的产品问题：A-26（重复直连组）、
> A-27（启动时先 render 再拉数据 + 整块重建 DOM 会吞点击）、A-28（拖动依赖缓存的节点数组，
> 中途重绘就失效），以及 D-7（Telegram 链接在登录后的 `/user/comm/config` 里）。
>
> 同日早些时候：**补上界面层的真线** —— 新写 `scripts/ui-test.js`（`--uitest`），
> 开真窗口、用真鼠标事件、走真面板真账号，从登录页自己登进去再逐页真点；
> 它抓出并修掉 3 个只有"真点"才暴露的问题（A-23 登录后立刻连接必失败、
> A-24 我的页「我的套餐」点了没反应、A-25 首页/节点页被重复的慢查询拖住 30 秒）。
>
> 再早：**把测试收成一条真线** —— 删掉假面板、三条离线自检线、核心层 `npm test`、
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
design/windows/app/dist/Polaris-portable-1.9.0.zip   185,970,496 B（≈ 177 MiB）
sha256 = cb8a1899e622deec12c2c635e54806155968531f9321a4dd22c7e4773e46d44f
```

解压即用：不装运行时、不写注册表、不写 `%APPDATA%`。**成品包实测**（把 zip 解到干净目录
`E:\AI\_pkg190` 直接跑）：`Polaris.exe --uitest` → **162 通过 / 0 失败**，日志首行
`Polaris 1.9.0 start | packaged=true portable=true`，11 段全 PASS（真面板登录、真内核连接、
节点/分流/流量/我的/二级页/设置、端口被占用时优雅关掉占用程序）。

包内审计（用户要求「测试账号/密码/网址/测试数据一律不进包」）：132 个 zip 条目里没有 `data/`，
没有 `settings.json` / `credentials.dat` / `*.log` / 自检报告；`app.asar` 里搜 `pinxiaoche`、
`mbe.cc`、`Wang16766912`、`lark_n`、`fe80::1` 全部无命中；`resources/{core,geo,rules}` = 4 / 3 / 47 个文件。

> 成品包只剩 `--uitest` 一条验证线（`--doctor` / `--smoke` / `--mock` / `--rttest` 已随旧自检线删除；
> 数据层的 `real-test.js` 是纯 Node 入口，不在成品包里跑）。

### 规模

| | |
| --- | --- |
| 提交 | `3d8eb6a` `ce71ad7` `8e578fd` `8f04c18` `77650f8` `621186d` `ba97f42` `b6105f1` `3c0d1ad` `00846cc` `265e057` `8648c8d` `6c72762` `6c0896a` `4c1e1db` `7b8e7bd` `5ce7010` `14f0816` `33d1fc0` … `9867731`（`chore(windows): 同步 1.9.0 版本号、清理仓库内凭据与过期说明`，9 文件 +58/−49）+ 本条文档提交（哈希见 `git log -1`） |
| 相对基线 | +121,154 / −940 行，103 文件 |
| 应用代码 | 主进程 6,026 行 JS（含 core/ 2,986 与 net/），渲染层 2,406 行 JS，样式 492 行 CSS；自检脚本 2,159 行（`real-test.js` 1,238 + `ui-test.js` 905） |
| 内核 | mihomo v1.19.32（windows-amd64-**compatible**）+ wintun 0.14.1 |
| 规则库 | geoip.metadb + geosite.dat + ASN.mmdb，23.8 MB |
| 内置分流 | 47 个本地规则集 + 25 个分流组，1.42 MB（`resources/rules/`，随包预播种；运行时由内核按 `type: http` + 24h 在线更新） |
| 自定义分流 | 用户自己写规则的分流组（最多 20 组 / 每组 200 条），**拖动排序**，存 `settings.json` 的 `custom_rulesets` |

### 测试入口（2026-10-10 起）

| 方式 | 规模 | 覆盖 |
| --- | --- | --- |
| `scripts/real-test.js` | 182 项 | **真面板联调（数据层）**：真登录/复用已有会话 → 套餐/订单/工单/邀请/Telegram → 今天/本周/本月流量明细与账号合计交叉验算 → 拉真订阅 → 连真节点 → 逐组延迟 → 经活节点 204 → 跑真流量看面板明细闭环 → 分流顺序拖动排序（含还原）→ **本地分流方案：屏蔽面板下发的组与规则、只用本地内置方案、`rule-provider` 全是 `type: http` + 24h、随包种子只补缺失、内核真加载了 5.6 万条规则** → **三个特殊出口（`自动选择` / `故障转移` / `DIRECT`）真的生效**：内核里类型是 URLTest / Fallback / Direct，切过去都认，经「自动选择」「故障转移」各出去一次拿到 204，经 `DIRECT` 拿到百度 200 → **节点顺序 = 网站下发的顺序**（分组里不再按名字重排）→ **端口占用与端口保存**：查得出占用者进程名/PID、请它退出后端口真空出来、`mixed_port` 保存后长久生效且连接后内核真跑在这个端口上 → **规则库 24 小时更新**：把缓存文件 mtime 拨到 25 小时前再重载，断言内核立刻强制刷新（日志为证）、CDN 可达、`updatedAt` 前进、mtime 变现在、规则仍有效、在线版不被打回种子 → **分流落点逐组实测**：19 个启用组各挑真域名发一次请求，从内核日志里核对落在它自己那个组（同出口的遮蔽单独列出，出口不同才算错）→ **全功能覆盖**：其余 32 个命令逐条真调用（代理模式三切、策略组出口改写与还原、自定义分流组增删与保留名校验、本地分流开关两态、本机采样曲线、注册/找回/发码/改密码的错误路径、支付方式/礼品卡/应用信息/管理员、TUN 状态与残留清理、更新查询与丢弃、`open_external` 的 URL 白名单），破坏性的真下单/真支付/真自我替换/提权重启明确跳过并打印原因 → **面板拨号健壮性**：两条解析路都拿掉（`dns.lookup` / `resolve4` / `resolve6` 全换成不回调的 `hang`）时 `resolveAll` 必须在 6s 内抛「DNS 没有解析出地址」而不是把请求挂死 → **内核操作串行化**：同时发起 `refresh_subscription` 与 `connect`，两个都不许 reject、结束后 `connected` 为真、控制面 `/version` 能读、日志增量里没有 `read ECONNRESET` |
| `scripts/ui-test.js`（`--uitest`） | 162 项 | **真面板界面自检（界面层）**：外壳/标题栏/无横向溢出 → 界面上退出旧会话再用真账号从登录页登入 → 首页套餐与进度条 + **未连接时不再显示「请先登录面板」那行小字** + **后端是自动识别出来的（`panel_backend`）** → 点 `#power` 真连内核（连上后**自动跑一次延迟测试**、**首页显示当前节点**）→ 节点页分组手风琴 + 唯一「直连」分类 + 「延迟测试」逐组延迟（直连/拦截型分组显示「直连」而不是「未测」）+ **主组前三行是「自动 / 备用 / 直连」而不是「其他 / 未测」且带当前出口** + **节点页卡片顺序 = 分流页顺序，拖动后跟着变** → 分流页返回键、无杂质、**内置分流 25 组且没有 Google FCM / 苹果推送通知、苹果推送 12 条内联并进「🍎 苹果服务」**、**「本地分流」总开关真开关一次（以主进程 `routing_on` 为准）**、**真拖动手柄改顺序 + 原地拖不弹「指定出口」+ 拖回原位不留遮罩** → 流量页三区间都来自面板 + 曲线标题是「本机实时用量」+ **鼠标悬停曲线显示该点读数** + **后端名与"没有明细接口"的提示** → 我的页徽标/邮箱不打码/7 个入口文案与手机端一致 + **到期提醒/流量提醒开关真能拨（以主进程 store 为准）** → 6 个二级页标题/返回键/数据 + **入口文案逐字断言** → **订阅套餐与公告正文的 Markdown 真渲染成块级标签、不留 `**`/`|`/`](` 残渣** → 设置页无「分流与订阅」段、无重复入口、无「面板」段、TUN 堆栈默认 `system`（且对话框里的顺序是 `system` / `gvisor` / `mixed`，「（默认）」标在 `system` 上） → **端口被别的软件占用时界面优雅提示并可关掉它** → 渲染层 0 error → 断开 |
| 人工手测 | — | `npm start` / 成品包 `Polaris.exe`，由人点 |
| `Polaris.exe --updtest <zip>` | — | 真演练自我替换（下载 → 解压 → 覆盖安装目录 → 重启），**会覆盖当前目录，先拷一份** |

> 已删除（2026-10-10，见上「测试方式」）：`npm test` 253 项、旧的 `--uitest` 110 项（配假面板）、
> `--doctor` / `npm run test:e2e` 128 项、`--rttest` 63 项（含 `--soak=20` 71 项）、
> `--smoke` 的 DOM 断言入口、`--mock` 演示数据、`--restore-probe` 登录态探针。
> 它们的实现与踩坑记录仍留在 §三（E-1~E-10、A-6~A-19），因为**教训是有效的**，
> 只是这些"自动化绿灯"不再被当作验收依据。

当前状态：真面板数据层 **182 / 0**、真面板界面层 **162 / 0**、**成品包（解压后）界面层 162 / 0**（2026-10-10 22:36；测试账号与面板地址属私有凭据，不入库）。

### 已实测通过

- 登录 → 拉订阅 → 清洗 → 起内核 → 挂系统代理 → 经代理访问外网返回 204
- 真实系统流量被正确分流（日志可见 `my.1password.com` 走节点、`*.dingtalk.com` 走 GeoIP(cn) 直连）
- 断开后系统代理 4 个注册表值逐项还原到进入前的状态
- 最大化/还原布局无横向溢出；设置页可滚到底
- 便携性：删掉 `%APPDATA%\Polaris` 后跑成品，不再重建
- 内置分流：19 个默认规则集被内核**真正加载**（`/providers/rules` 逐个 `ruleCount > 0`，合计 **56,077 条**），
  运行中开关分流组能热重载并让内核加载新规则集；面板下发的 `proxy-groups` / `rules` / `rule-providers`
  在开启本地分流后**一个都不剩**（实测配置里只剩 4 个结构组 + 19 个启用组 + 47 个 `type: http` 的 provider）
- 自动更新：下载（含 302）→ 校验 zip → 解压 → 写替换脚本 → 覆盖安装目录 → 重启，全程在成品包的**拷贝**上真跑过一遍；
  点「丢弃更新包」后下载的 zip、解压目录、`staged.json` 一起清掉（不留 186 MB 在磁盘上）
- （历史，该自检线已删）运行时功能测试（`--rttest`，真界面驱动 + 内核回读）：点界面连接 → `/configs` 的 `mixed-port` 与
  `core.mixedPort()` 一致、控制面只绑 `127.0.0.1`、`/traffic` WebSocket 真推数据、经代理 204；
  国外域名命中 `RuleSet(gs_geolocation_ncn)`（链 `香港 01 → 节点选择 → 🌏 国外穿墙`）、国内域名走 DIRECT；
  三种代理模式切换后 `/configs.mode` 跟着变；界面开关分流组后内核 `/providers/rules` 数量随之增减
  （合计 56,283 条规则）；内核被 `taskkill /F` 后应用 20s 内不再谎报已连接、再点一次能自愈成新 pid；
  面板进程被杀后界面不崩、代理仍 204；断开后系统代理 4 个值逐项回到进入测试前的状态
- 真实面板（测试账号，`scripts/real-test.js`）：**182 通过 / 0 失败**。
  套餐「全区域-年-不重置 已用 0.04 / 500 GB，到期 2027-10-10」、订单、公告、Telegram 入口
  （登录后 `/user/comm/config` 的 `telegram_discuss_link`）、32 个真节点、
  流量页三个区间与账号合计对得上；经活节点访问 `gstatic.com/generate_204` 返回 204；
  跑完真流量后面板明细真的涨了（**站点统计闭环**）；拖动排序真的改了匹配顺序并还原；
  **本地方案**：配置里面板的组/规则/rule-provider 全被替换掉、只剩 4 个结构组 + 6 个启用组 +
  16 个 `type: http`（`interval: 86400`、`path` 在 `data/polaris-rules/`、url 指向 jsDelivr）、
  末条 `MATCH,🐟 漏网之鱼`、`sub-rules` 已删、内核真加载 **56,077 条**规则，
  且**内核下载到的新版规则不会被随包种子打回旧版**（种子只补缺失）。
- **三个特殊出口真的生效**（`real-test.js` 新增段，内核回读 + 真出网）：`自动选择` 在内核里是
  `URLTest`、`故障转移` 是 `Fallback`、`DIRECT` 是 `Direct`；把它们分别切到主组当前出口，内核回读都认；
  经「自动选择」和「故障转移」各访问一次 `generate_204` 都返回 204（`自动选择` 当时挑的是
  `🇭🇰 ＨＫ · Global〔4837/CMI/163 2x〕`、`故障转移` 是 `🇩🇪 ＤＥ · BGP〔9929 2x〕`）；
  经 `DIRECT` 访问百度拿到 200，证明走的是本机网络。测完自动切回 `自动选择`。
- 真实面板界面层（`scripts/ui-test.js --uitest`，真窗口真鼠标事件）：**152 通过 / 0 失败**。
  界面自检自己先从登录页用真账号登进去，再逐页真点：节点页分组手风琴（第一个默认展开、
  点一下能收起、32 个节点）、**唯一一张「直连」分类卡**、「延迟测试」点下去每个"出口是节点"的分组
  都测出真延迟（直连/拦截型分组显示「直连」）、**真按住手柄拖动分流组并断言匹配顺序变了、不丢行**、
  **「本地分流」总开关真关一次再开一次（关掉后开关回退、页面提示面板方案生效）**、
  流量页 today/week/month 三个区间都来自面板明细（不再是本机算的）、
  我的页徽标显示真套餐名（不是「未订阅」）、**邮箱不打码**、7 个入口文案与手机端逐字一致、
  6 个二级页都有返回键且返回有效、设置页有「分流与订阅」且**没有**重复的「分流规则」入口与「面板」段、
  **全程渲染层 0 个 error**。
  第 5 轮又补上：主组前三行显示「自动 / 备用 / 直连」+ 当前出口（不是「其他 / 未测」）、
  节点页卡片顺序 = 分流页顺序且**拖动后跟着变**、二级页入口文案逐字断言、
  套餐与公告的 Markdown 真渲染成块级标签且**不留 `**` / `|` / `](` 残渣**。
  这一轮界面自检抓出并修掉的问题见 A-26、A-27、A-28、A-29、A-31、A-32。
  第 6~7 轮再补：流量页曲线标题「本机实时用量」+ **悬停显示该点读数**、面板累计与「我的」页
  **同源**（不再是 0 B / 未订阅）、**连上内核自动跑一次延迟测试**、**原地拖不弹「指定出口」弹窗、
  拖回原位不留遮罩**、**到期提醒/流量提醒开关真能拨**（以主进程 store 为准）、
  设置页 TUN 堆栈默认 `system`、**端口被别的软件占用时能优雅关掉它并重连**。
  这两轮抓出并修掉的真 bug 见 A-34、A-35、A-36、A-37、A-38。
- **规则库 24 小时更新是真的会更新**（`real-test.js` 新增段，2026-10-10）：把 3 个缓存文件的 mtime
  拨到 25 小时前 → `reloadConfig()` → 8 秒内皮尔日志出现
  `[Provider] gs_telegram not updated for a long time, force refresh`、没有 `pull error`、
  `/providers/rules` 的 `updatedAt` 前进到当时、缓存文件 mtime 变成现在（下一次 24 小时从这里起算）、
  拉回来之后 `ruleCount > 0`，且**在线版不会被随包种子打回旧版**。
  机制：mihomo 的 `resource.Fetcher.Initial()` 把本地文件 mtime 当作"上次更新时间"锚点，
  重启不重置；超过 `interval` 就 `forceUpdate` 一次；内容相同只 `Chtimes`；失败按 2 倍退避重试。
- **分流落点逐组实测**（同一段）：19 个启用的分流组各挑规则集里的真域名发一次请求，
  从内核日志逐条核对落点 —— `github-api.arkoselabs.com → RuleSet(gs_github) → 🐱 GitHub`、
  `ai.google.dev → RuleSet(acl_gemini) → ♊️ Google Gemini`、`periscope.tv → RuleSet(gs_x) → 📲 X`、
  `steambroadcast.akamaized.net → RuleSet(acl_steam) → 🎮 游戏平台`、
  `api.iplay.163.com → RuleSet(acl_neteasemusic) → 🎶 网易音乐`、
  `www.baidu.com → RuleSet(acl_chinadomain) → 🎯 国内直连` …… 18 个落自己组，
  1 个（`redirector.c.play.google.com`）被同出口的「🌏 Google」先命中（两边都是代理，只是归类不同）；
  另外「没命中任何规则的域名落到 `🐟 漏网之鱼`」也是 PASS。**没有任何"该直连却走了代理"的组**。
- **全功能覆盖**（同一段新增）：IPC 面上其余 32 个命令逐条真调用一遍（只读或自还原 + 错误路径），
  破坏性的 `create_order` / `pay_order` / `download_update` / `apply_update` / `restart_as_admin` /
  TUN 实机 / 真开浏览器 / 导出日志（会弹目录框）明确跳过并在报告里打印原因。
  期间修掉 6 条**断言形状写错**（E-18），不是产品缺陷。
- （历史，该自检线已删）长后台浸泡（`--rttest --soak=20`，20 分钟真连接 + 周期性收进托盘）：**71 通过 / 0 失败**。
  主进程内存漂移 **−3.1 MB**、内核 **+2.1 MB**、内核句柄 **+39.8 个**、线程 **+1.2 个**（无泄漏）；
  节点延迟首段 169ms → 末段 181ms（未劣化）；每 5 分钟一次 10MB 吞吐采样
  **6.59 → 20.04 → 25.19 → 25.25 → 26.85 Mbps**（首采样是冷启动，之后稳定）；内核 pid 全程未变。
  逐分钟样本在 `data/rt-samples.jsonl`

### 未验证 / 未做

| 项 | 状态 | 说明 |
| --- | --- | --- |
| 真实面板联调 | ✅ | `scripts/real-test.js` 拿真账号真面板真节点跑通 **182 项全绿**：登录/复用会话 → 套餐 → 订单 → 工单 → 邀请 → 公告 → Telegram → 今天/本周/本月流量明细（与账号合计交叉验算）→ 拉真订阅（32 节点）→ 连真节点 → 逐组延迟 → 经活节点 204 → 跑真流量后面板明细真的涨了 → 拖动排序生效并还原 → **本地方案接管**（面板组/规则全丢、`type: http` + 24h、种子只补缺失、内核加载 5.6 万条）→ **三个特殊出口各自真出网一次** → **节点顺序按网站下发** → **端口被占/端口保存** → **规则库 24h 到点真的强制刷新**（拨 mtime 实锤）→ **19 个分流组逐组发真请求核对落点** → **其余 32 个命令逐条真调用**（破坏性操作明确跳过）。**过程中修掉 10 个只有真面板/真界面才暴露的问题**（见 A-22、A-29、A-31、A-32、A-34~A-38、D-6、E-9） |
| 与安卓端同一套分流模型 | ✅ | 面板下发的 `rules` / `proxy-groups` / `rule-providers` 整体丢弃，改用与安卓端**逐字一致**的 25 组本地表（默认开 19 组；第 8 轮按用户要求删掉「📢 Google FCM」与「📢 苹果推送通知」两个会被前置组遮蔽的组，见 A-39/D-11）+ 4 个结构组（`🚀 节点选择` / `自动选择` / `故障转移` / `🐟 漏网之鱼`）+ 14 条内网直连 + 12 条苹果推送内联（并进「🍎 苹果服务」）；`rule-provider` 改成 `type: http` + `interval: 86400` + 随包种子预播种（冷启动断网也能立刻生效）；节点名与保留名冲突时整体降级回面板配置（与安卓端同款兜底） |
| 面板后端自动识别 | ✅ | 照安卓端 `LoginViewModel.detectBackendType`：`/guest/comm/config` 里出现验证码相关字段 → xboard，否则 xiaov2b；结果落 `store.panel_backend` 并回显。两端差异只有礼品卡路径与"流量明细接口只有 xboard 有"两处（见 D-10） |
| 规则库 24 小时在线更新 | ✅ | 47 个 `rule-provider` 全是 `type: http` + `interval: 86400` + jsDelivr 规则 CDN，缓存落在 `data/polaris-rules/`；把缓存 mtime 拨到 25 小时前再重载，内核**立刻**打 `[Provider] X not updated for a long time, force refresh` 并真的拉回新内容（`updatedAt` 前进、mtime 变现在、规则仍有效、在线版不被打回种子）。计时锚点是文件 mtime，**重启不重置**（见 §二.4） |
| 分流落点正确性 | ✅ | 19 个启用组各挑规则集里的真域名真发一次请求，从内核日志核对落点：18 个落自己组，1 个被**同出口**的组先命中（只是归类不同）；**没有任何"该直连却走了代理"的组**。写断言的判据是**出口语义**而不是组名（见 D-11） |
| TUN 模式实机 | ❌ | 代码路径完整（含 UAC 提权、网卡收尾、残留清理），但会临时接管网络栈，未在真机跑 |
| 面板活跃会话管理（踢设备） | ❌ | 需要新功能：**两端都没有这个接口**（面板侧也没有），不是「UI 未做」 |
| 自动更新（下载并替换自身） | ✅ | 便携版可自装：`update_windows_url` 指向 zip → 下载 → 解压到 `data/update/staging` → 退出前写 `apply-update.cmd` 覆盖安装目录并重启。装在 `Program Files`/只读盘时自动退回「前往下载」；不做增量、不校验签名、失败无回滚（只覆盖不删除，用户数据与旧文件都还在，可手动重下） |
| 自定义规则集（用户自己写规则） | ✅ | 分流页「自定义分流组」：一行一条 `类型,内容`，出口由程序补；白名单校验（含拒绝 `MATCH`）、IP 类自动补 `no-resolve`；最多 20 组 / 每组 200 条；**自定义规则排在内置分类与订阅规则之前**（否则永远轮不到）；与订阅策略组同名时复用、与内置同名时拒绝 |
| 分流组排序 | ✅ | 分流页每个组（内置 + 自定义）都能**按住手柄拖动**排序，落 `routing_order` / `custom_rulesets` 顺序；内置与自定义不互相跨越；「恢复默认」只还原内置开关与顺序，不动自定义组（2026-10-10 按用户要求把 ↑↓ 按钮换成拖动，见 A-28） |

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
├─ resources/rules/ 内置分流规则集 47 个 / 1.42 MB       (extraResources → 启动时铺到 data/polaris-rules)
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

### 4. 分流方案与安卓端同一套模型：屏蔽面板下发，`type: http` + 24h + 随包种子

2026-10-10 用户要求「跟 polaris 安卓 app 一样，屏蔽网址下发的，只用本地的策略，策略更新也照安卓端」，
于是把早期那套「面板规则 + 我的规则插队」的**合并模型**整体换成安卓端
`kernel-core/.../native/config/routing/` 的**替换模型**：

- **整体替换**：面板给的 `proxy-groups` / `rules` / `rule-providers` 全部丢弃（`sub-rules` 也删掉），
  用与安卓端**逐字一致**的 25 组本地表（默认开 19 组，第 5 轮按用户截图口径排的顺序：电报消息 / GitHub / 油管视频 /
  国内直连 / 国外穿墙）+ 4 个结构组 + 14 条内网直连 + 12 条苹果推送内联，末条 `MATCH,🐟 漏网之鱼`。
  节点本身照旧用面板下发的 `proxies`（只换分流，不换节点）。
- **结构组**（`🚀 节点选择` select `[自动选择,故障转移,DIRECT]` / `自动选择` url-test /
  `故障转移` fallback / `🐟 漏网之鱼` select `[节点选择,DIRECT]`）都是 `include-all` 语义 ——
  组成员靠内核按 include-all 并入，**不再由我们枚举 32 个节点名**（旧模型枚举节点名，
  订阅一换就得重写一遍；安卓端也是这个写法）。`🐟 漏网之鱼` 刻意**不** include-all（安卓端同款）。
- **重名兜底**：面板节点名或 proxy-provider 键撞上保留名（27 表组 + 4 结构组 + 8 个 mihomo 预注册名）
  时，整体**降级回面板配置**并在分流页顶部说明原因 —— 与安卓端 `checkNameCollisions` 同款行为。
  硬来会让 mihomo 直接拒绝加载整份配置（组名重复），用户看到的是"连不上"。
- **规则数据**：`type: http` + `interval: 86400` + `path: polaris-rules/<key>.yaml`（相对 `-d`），
  47 个 yaml 进 `extraResources`（`resources/rules/`，1.42 MB）当**预播种缓存**：
  启动时铺到 `data/polaris-rules/`，但**只补缺失、不覆盖** —— 内核下载过的新版规则不会被随包种子打回旧版。
  mihomo 的 `Fetcher.Initial()` 先 `os.Stat(vehicle.Path())` 读本地文件、`time.Since(modTime) > interval`
  才起后台拉取，所以冷启动断网也立刻有规则，联网后按 24 小时自动更新。
- `path` 必须是相对 `-d` 的路径：mihomo 走 `C.Path.IsSafePath(C.Path.Resolve(schema.Path))`，
  只放行 homeDir 下的子路径，写绝对路径或直接指向 `resources/rules/` 一律被拒（见坑 C-7）。
- 目录名 `polaris-rules` 与安卓端 `providerSubPath` 一致；旧的 `data/rules/`（`type: file` 时代）
  在启动时顺手清掉，不留 1.5 MB 死文件。

其余语义沿用安卓端：本地规则插在订阅规则**之前**、内网地址始终直连、
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
- **排序**：分流页**按住手柄拖动**（`⠿`），落 `routing_order`（内置）/ `custom_rulesets` 数组顺序（自定义），
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

**A-26 节点页两张几乎一样的「直连」分类卡（用户第三轮第 1 条）**

现象：节点页顶部并排两张卡 ——「🎯 国内直连 35 个可选出口·当前 DIRECT 75ms」与
「🎯 全球直连 34 个可选出口·当前 DIRECT 78ms」，用户直接截图说"这两个节点重复了"。

查证：`data/config.yaml` 里引用 `🎯 国内直连` 的**全是我们自己**的内置规则集行
（`RULE-SET,gp_cn` / `acl_chinadomain` / `acl_chinacompanyip` / `acl_unban` / `acl_steamcn` /
`acl_download` / `acl_chinamedia`），引用 `🎯 全球直连` 的是**面板订阅自带的**规则
（`DOMAIN-SUFFIX,local` / 各类内网 IP-CIDR / `RULE-SET,chinadomain,chinaip` / `DOMAIN-SUFFIX,cn` / `GEOIP,CN`）。
两者语义完全一样（国内/私有地址直连），只是名字不同 —— 面板的策略组列表里本来就有 `🎯 全球直连`，
我们的内置分类又自己建了一个同义的组。

修法（当时）：给内置分类加 **alias**（`rulesets.js` 的 TABLE 里 `🎯 国内直连` 带 `alias: '🎯 全球直连'`），
builder 建组与写 `RULE-SET` 行时统一用 `groupFor(g) = g.alias || g.name`：面板已经有同义组就复用它。
alias 的目标必须在同一份配置里真的存在，否则 `RULE-SET` 会指向不存在的策略组（mihomo 拒绝加载整份配置）。

**后续（2026-10-10 用户第 4 轮）：alias 这套连同整个合并模型一起删了** ——
本地方案下不再保留任何面板组，`🎯 国内直连` 用回自己的名字、面板那个 `🎯 全球直连` 整组丢弃，
所以"A-26 的两张卡"在新模型下从根上不存在（实测界面自检里"唯一一张直连分类卡"仍然绿）。
教训仍然成立：**分类重复往往不是界面 bug，而是配置层建了两个语义相同的组**；
判断"该保留哪个"要看**这套分流规则归谁管** —— 规则来源换成"只用本地"之后，答案就只剩本地那个。

**A-27 启动时先 `render()` 再拉数据 + 整块重建 DOM，会吞掉用户的点击**

现象（界面自检抓到的）：① 从「我的」页点「退出登录」，`realClick` 三次都报
"点不到元素 `[data-click="logout"]`（没找到（可能刚被重绘掉））"，可现场诊断里那个按钮
明明存在、坐标也对、`elementFromPoint` 命中的就是它；② 节点页手风琴点一下能收起、再点不展开；
③ 连接内核之后紧接着读到的分组列表还是**连接前**的本地预览（于是又误报 A-26 那张重复卡）。

根因（三件事同一个机理）：
- `boot()` 原来是 `await refreshAll(); state.route = "home"; render();` —— 面板查询要几秒到几十秒，
  这期间用户（或自检）已经点进别的页，数据回来后被**无条件拽回首页**，整块 DOM 换掉，点击落空。
- `render()` 每次都 `content.innerHTML = html` 全量重建。任何一次异步数据回来触发的 `render()`
  都会把"刚刚量好坐标、正要按下"的那个元素换成新节点，这一下点击就被吞。
- 连接后的 `refreshAll()` 会晚几秒回来，自检没等它就断言了连接后的状态。

修法：
1. `boot()` 先立界面再拉数据：`state.route="home"; render(); state.booted=true; bindLiveStatus();`
   然后 `await refreshAll(); if (state.route === "home") render();` —— **用户已经离开这一页就不抢着重绘**。
2. `render()` 里加内容指纹：`swap(html, bind)` 用 `state.route + "\0" + html` 做 key，
   与上次相同就**完全不动 DOM**（滚动位置、展开状态、正在点的元素都保住）。
3. 自检侧加 `settle(win)`（等渲染计数连续安静 1.5s）与 `clickUntil()`（点一下没效果就再点一次）。
4. **第 7 轮补的第二刀**：指纹短路只能挡住"内容没变"的重绘，**真有数据更新时照样会吞**。
   实测漏网场景：进入分流页后第一次点「本地分流」开关，正好撞上后台那次重绘 ——
   `pointerdown` 与 `pointerup` 之间 DOM 被换掉，浏览器补发的 click 只能派给共同祖先（`#content`），
   开关的处理器一次都没被调用（界面看起来"点了没反应"；自检里表现为"关掉"没生效、
   "再打开"反而把它关掉，后面一整串断言跟着连锁变红）。
   现在 `render()` 在**指针还按着**的时候不换 DOM：把 `{key, html, bind}` 存进 `pendingSwap`，
   `pointerup`/`pointercancel` 之后用 `setTimeout(0)` 落盘（排在浏览器补发的 click 之后）；
   `pointerDownAt` 超过 5 秒视为卡住（指针移出窗口收不到 pointerup）自动放行，免得界面冻住。
   自检侧同时加 `flipUntil(win, sel, readExpr, want)`：**用主进程读回真实值**来确认开关拨过去了，
   而不是只看 DOM 上的乐观样式 —— 只看 DOM 的话，被吞掉的那次点击看起来"已经生效"。

教训：**"数据回来就整块重绘"是 Electron 里最容易被忽视的点击杀手**，
它只在"用户刚好在那一刻操作"时发生，所以只查 DOM 的自检永远绿。
渲染函数要么做增量、要么至少做"内容没变就不碰 DOM"的短路；
再加上"按着不换、松手再换"，才算把这一类收口。

**A-28 拖动排序依赖"按下时缓存的节点数组"，中途一次重绘就静默失效**

现象：新写的拖动排序在自检里"拖了但顺序没变"，界面无报错、无 toast，看起来像没拖到。

根因：第一版 `bindRulesetDrag()` 在 `pointerdown` 时把 `$$(".ruleset-row", card)` 的结果
**存进闭包**，`pointermove` 里用它算落点；move/up 事件还挂在那根手柄上、并依赖
`setPointerCapture`。拖动过程中只要来一次 `render()`（状态事件、`refreshAll` 回来都会），
被缓存的那些节点就**脱离了文档**，`getBoundingClientRect()` 全返回 0 → 落点算成最后一格或者不动，
而手柄自己也随旧 DOM 一起消失，`pointerup` 根本收不到。

修法：① move/up 挂 `document` 并用捕获阶段（`addEventListener(..., true)`），不依赖
`setPointerCapture`，指针移出手柄/移出窗口也能收尾；② 落点每次移动都**重新查一遍 DOM**，
不缓存节点数组；③ 松手时先解绑再清理高亮。

教训：**拖动/长按这类"跨多个事件周期"的交互，任何跨事件缓存下来的 DOM 引用都是定时炸弹**；
每次事件里现查现用，代价可以忽略。

**A-29 `include-all` 的组里没有节点名，于是"连接前的节点页"整个是空的**

现象（换成安卓那套替换模型之后第一次跑真面板测试）：`real-test.js` 的「节点列表非空」直接是 `0`；
界面上则是没点「连接」之前，节点页一张卡、一行节点都没有。

根因：本地方案下 `proxy-groups` 的结构组写成 `proxies: [自动选择, 故障转移, DIRECT]` +
`include-all: true` —— 节点是**内核**按 include-all 并进去的，配置里根本没有节点名。
而 `manager.js` 的 `previewNodes()`（连接前读本地 `config.yaml` 兜底预览）原来写的是
`main.proxies.filter(n => byName.has(n))`，过滤完一个都不剩 → `S.nodes = []`。
旧模型是"我们自己枚举 32 个节点名"，所以这段一直是对的，换模型时它就成了地雷。

修法：`previewNodes()` 里加 `membersOf(g)` —— `include-all === true` 就返回全部 `proxies` 的名字，
否则取 `proxies` 里真实存在的节点、为空再退回全部；`S.groups` 的 `count/options` 与 `S.nodes` 都走它。
顺带修掉一个显示问题：连接前每个分类卡都写着「3 个可选出口」（那是结构成员数）。

教训：**"配置里写了什么"和"内核实际认什么"不是一回事**；凡是 include-all / provider / 动态注入
这类"内核帮我补全"的字段，读配置兜底的代码都必须按内核语义再算一遍。

**A-30 直连/拦截型分组显示「未测」，看起来像没测出来**

现象：节点页上 `🍎 苹果服务` / `🎯 国内直连` / `📺 哔哩哔哩` 三个分组的徽标都是「未测」，
界面自检里"每个分组都测出了延迟"这条直接红（4/7）。

查证：这三个分组的当前出口就是 `DIRECT`（本地方案里它们是 direct 型，
`block` 型则是 `REJECT`）。`/proxies/<组名>/delay` 对"出口是 DIRECT/REJECT"的组给不出延迟 ——
**不是没测出来，是压根不该测**。自检那条断言把所有分组一视同仁，是断言写错了。

修法：两处一起改 —— ① `views.js` 加 `groupBadge(g)`：出口是 `DIRECT/COMPATIBLE/PASS` 显示「直连」、
`REJECT/REJECT-DROP` 显示「拦截」，其余才显示延迟徽标；② 自检断言改成
「每个**出口是节点**的分组都测出了延迟」，直连/拦截型分组单独列出、不参与判定，
并补一条"直连/拦截型分组显示的是「直连/拦截」而不是「未测」"。

教训：**"每个 X 都要有 Y"这种全量断言，先问一遍"有没有哪类 X 本来就不该有 Y"**；
把不该有的那类显式排除并打印出来，断言才有诊断价值。

**A-31 「分组调好了位置，为什么外面的节点不跟着变？」——按名字排序把拖动顺序丢了**

现象（用户第 5 轮第 3 条）：在分流页把内置分组拖成他想要的顺序，回到节点页，
分组卡的顺序还是老的。

查证：`mihomo /proxies` 的响应是 Go `map` 序列化出来的，**键顺序随机**（每次请求都可能不同）。
所以 `manager.routingGroups()` 原来那句 `out.sort((a,b)=>a.name.localeCompare(b.name))`
并不是"稳定兜底"，而是把用户在分流页拖出来的顺序**按名字重排**了 —— 两个界面看着像同源，
其实一个用顺序表、一个用字母序。

修法：新增 `manager.js groupOrderIndex()` —— 按**生成配置时的真实顺序**重建 name→序号索引
（主组 → 自定义组 → `rulesets.orderedTable(store.get('routing_order'))` → 兜底组），
`routingGroups()` 用它排序（表里没有的组按名字兜底排在最后）。
这样节点页、分流页、`config.yaml` 里的 `RULE-SET` 行三处顺序天然一致。

教训：**凡是"顺序"这种信息，绝不能依赖一个无序容器的遍历结果，也不能用名字排序当兜底** ——
必须有一个显式的顺序源（这里是 `routing_order` + 表序），所有展示都从它派生。
自检也要盯住这一点：`ui-test.js` 在"拖到第一位"的状态下直接问节点页的数据源
（`PolarisAPI.getRoutingGroups()`），断言第一张卡就是被拖的那组。

**A-32 三个特殊出口显示「其他 / 未测」，用户以为它们不生效**

现象（用户第 5 轮第 2 条）：节点页主组里「自动选择 / 故障转移 / DIRECT」三行显示
`其他` + `未测`，用户直接问"这三个实际生效吗？"。

查证：它们确实生效，但**延迟对它们没有意义** —— `自动选择`(URLTest) 与 `故障转移`(Fallback)
是内核自己在探测的组，`DIRECT` 是直连；`/proxies/<名>/delay` 给不出（或给出的是它们当前出口的延迟）。
界面把它们当普通节点渲染，于是"没有延迟"被显示成"未测"，看起来就是坏的。

修法：① `views.js` 里 `STRUCT_LABEL = {自动选择:'自动', 故障转移:'备用'}`，这三个出口显示中文标签，
并把它们的**当前出口**用 `.nm-sub` 小字写出来（`🇭🇰 ＨＫ · C · 54ms`）；
② 数据层真的验一遍 —— `real-test.js` 新增一段：读 `/proxies/<名>` 确认内核里的类型是
`URLTest` / `Fallback` / `Direct`，然后**把主组依次切到这三个出口各出一次网**
（自动选择/故障转移要求 204，DIRECT 用百度验"走的是本机网络"），最后还原成「自动选择」；
③ 界面层断言这三行的徽标是「自动/备用/直连」而不是「未测」，且「自动选择」那行写出了它挑中的节点。

顺带修掉一个同源问题：`manager.previewNodes()` 里 `include-all` 的组只算节点名，
于是**连接前**主组卡片里根本没有这三行（连上之后有）—— 两态不一致。
按内核语义改成"显式成员在前 + 其余节点在后"。

教训：**界面上的"未知/未测"必须区分"没测到"和"本来就不适用"**；
用户看到「未测」只会得出一个结论：这功能坏了。

**A-33 用户说"默认按我的截图来"，但默认值只对新装生效**

现象：把 `rulesets.js` 的 `defaultOn` 从 6 组改成 20 组之后，用户机器上看到的还是他之前存的那份。

查证：`routing_rules`（启用集合）与 `routing_order`（顺序）都是**持久化**在 `settings.json` 里的，
`defaultEnabled()` 只在没有这个键时兜底。用户机器上恰好已经有这份键（他自己在界面上调过），
所以改默认值对他"没反应"——这不是 bug，是设计。

处理：默认值按用户截图改（20 开 / 7 关 + 截图顺序），并且**不去改用户已经存下的选择**；
测试用的数据目录（`.devdata` / `--uitest-data`）里把这份残留清掉，让自检跑在新默认上。

教训：改"默认值"时先问一句"这个键是不是已经落盘了"；是的话，改代码只影响新装用户，
要么接受，要么在文档/汇报里说清楚。

---

**A-34 同一屏里"已用流量 0 B / 套餐 未订阅"和「我的」页的真实套餐名自相矛盾**

现象：用户截图标注流量页「面板累计」——已用流量 `0 B / 500 GB`、套餐 `未订阅`，
而「我的」页同一时刻写着 `全区域-年-不重置`、已用 269 MB。

查证：两处**读的不是同一份数据**。`get_plan`（我的页）用 `panel.userInfo()` 的
`used_bytes`/`total_bytes`；`get_traffic`（流量页）自己又拼了一套：`u.u + u.d` 与
`u.transfer_enable`。而 `userInfo()` 内部是两个接口并行 + 各自 catch：
`/user/info`（有 transfer_enable / expired_at，**没有 u/d、没有 plan 对象**）与
`/user/getSubscribe`（有 u/d 与 plan）。只要后者抖一下（限流/超时），
流量页就退化成 `/user/info` 的形状 —— 正好是"0 B + 未订阅 + 500 GB + 到期正常"。
探针实测（`userInfo()` 直调）：`plan_name=全区域-年-不重置`、`used_bytes=282462413`、
`u=106016244`、`d=176446169` —— 数据层是对的，是"两次不同源 + 一次抖动"造成的显示矛盾。

处理：①`ipc.js` 新增 `planSnapshot()` 作为套餐/用量的**唯一**口径，`get_plan` 与
`get_traffic` 都走它（字节口径，946 KB 不会显示成 0）；②`panel/client.js` 的 `userInfo()`
加 45 秒短缓存，**只缓存取到套餐的结果**；这次没取到套餐但上次取到过 → 沿用上次的套餐名
（是请求失败，不是用户退订了），换账号/退出登录时 `clearUserInfoCache()`；
③流量页删掉「本次峰值」「在线节点」两行（用户明确不要），连带把 `ipc.js` 里模块级的
`peak` 记账、`resetPeak()`、`online_nodes` 计数与 `core.onStatus` 里的 max 累加一起删掉
（没人显示的数据不要继续算）。

教训：同一份业务数据只允许有一个口径函数；"两个接口并行 + 各自 catch"的兜底写法，
失败时会拼出一个**看起来合理但错误**的组合（有额度和到期、却没有套餐和用量），
比直接报错更难查。缓存也要区分"没取到"与"确实没有"。

---

**A-35 拖完分流行，遮罩莫名其妙一直挂着（一屏 3 条红全指向它）**

现象：界面自检第 5 段（分流页拖动排序）之后，流量页与我的页的每一次点击都失败，
报「该坐标最上面是 `<div class="overlay">`，目标是 `<button>` 文本「‹ 返回」」——
3 条红（返回键、侧边栏流量、侧边栏我的）其实是**同一个遮罩**挡住整屏。

查证：拖动本身是好的（顺序真的变了、也真的没丢行、断言都过）。问题在**松手之后
浏览器补发的那次 click**：指针从手柄按下、移过几行、在目标行上松开，浏览器会把 click
派发到 mousedown 与 mouseup 的共同祖先，于是 `[data-ruleset-pick]`
的点击处理被触发、弹出「指定出口」弹窗，而这次弹窗没有人去关它。

处理：`app.js` 里加一个"刚拖过"的抑制窗口 —— 模块作用域 `suppressClickUntil`，
拖动**真的改变了顺序**时置 `Date.now() + 400`，并用**捕获阶段**的 document click 监听
（只装一次，`dragClickGuard`）把窗口内的 click `preventDefault + stopPropagation`。

**同一个坑还有第二层，第一版补丁没堵住（第二次实测仍然 overlay=1）**：
上面那句"指针在目标行上松开"是**拖到别的行**的情况。而"**原地拖一下**"
（手柄上按下、松手还在同一行 —— 自检里"拖回原位"那一步其实正好退化成这个：
被拖的行拖到第一位之后，`order0[1]` 就是它自己）时，mousedown 与 mouseup
**都落在这一行**，共同祖先就是**这一行本身**，`[data-ruleset-pick]` 必然被点开；
而第一版只在 `moved && to !== from`（顺序真的变了）时才装抑制，
"原地拖"走的是 `if (!moved || to === from) return;` 这条早退路径 —— 抑制根本没装上。
修法：抑制窗口的赋值提到早退**之前**，只要在**手柄**上按过一下、松手就吃掉紧随的 click。
定位手法：写了个一次性探针（`electron.exe <probe.js>`，`require` 真 `main.js` 后挂
`browser-window-created`，在页面里装捕获阶段的 click 记录 + `#overlay-root` 的
MutationObserver），复现后直接看到 click 的落点是 `DIV|row ruleset-row`、
`pick="📲 电报消息"` —— 一次就锁定了落点，比反复跑 7 分钟的自检猜要快得多。

教训：自定义拖拽不能假设"松手后没有 click"。pointer 事件与 click 是两套派发，
凡是"按下与松开落在同一元素上"的拖拽，都要显式吃掉那次补发的 click ——
**包括原地拖（没产生位移）的那种**，"没改顺序"不等于"没发生拖拽"；
另外，一屏里多条红如果是"同一个坐标被同一个东西挡住"，先怀疑一个共同根因。

---

**A-36 节点顺序"自己会变"：`include-all` 的成员会被内核按名字重排**

用户第 8 条：「节点排序要按照网站下发的排序，不应该自己再设置节点排序规则」。
先按常规思路查渲染层与主进程：全仓 grep `sort`，两边都没有对节点排序 —— 顺序真来自内核。
再看内核源码：`kernel-core/src/foss/golang/clash/config/config.go:943`
`slices.Sort(AllProxies)`，而 `outboundgroup/parser.go:110` 在 `include-all` 时
`groupOption.Proxies = append(groupOption.Proxies, AllProxies...)` ——
**内核先把"所有节点"排序，再并进组**，所以 `include-all` 组的成员顺序是名字字节序
（实测第一个是 `🇩🇪 ＤＥ · BGP〔9929 2x〕`），不是网站下发顺序。

处理：本地方案不再用 `include-all`，改成把节点**显式写进**每个组的 `proxies`
（`builder.js` 的 `members(head)`：有订阅节点时 `head.concat(nodeNames)`），
顺序 = 生成配置时的顺序 = 订阅顺序。顺带修好两件事：
①`previewNodes()` 的 `membersOf` 与内核语义对齐（显式成员就是全部成员），
连接前节点页不再少三行、也不再和连上后不一致；
②`real-test.js` 加了逐项比对（订阅原文 vs `/proxies/<主组>.all`）与
「第一项不是字节序最小的那个」两条断言，防止以后又悄悄退回 `include-all`。

教训：`include-all` 看着"省事、订阅更新不用重拼"，代价是把顺序的控制权交给内核；
只要产品要求"顺序照订阅"，就必须自己列成员。查"顺序不对"这类问题时，
**先 grep 自己有没有排序，再去内核源码里找有没有别人在排**。

---

**A-37 端口被别的代理软件占着：不假装连上，也不偷偷杀进程**

用户第 10 条：「如果遇到端口冲突，比如有另外的代理软件，应该优雅的关闭对应软件的运行」，
第 9 条：「设置好本机代理端口后应该可以长久保存并与连接点开同步运行」。

处理：新增 `electron/net/portowner.js`（只用系统自带的 `netstat -ano -p TCP` 与
`tasklist`，不引第三方依赖）：`owner(port)` 给出 `{busy, pid, name}`（跳过自己），
`close(pid)` **先不带 `/F`** 的 `taskkill`（发 WM_CLOSE，程序有机会保存配置再退），
每 250ms 探一次、给 2.5 秒宽限，还不退才 `/F` 强杀。渲染层：连接失败且错误信息命中
「已被其它程序占用」时弹确认框（进程名 + PID 原样摆出来）→ 用户确认后关掉它 →
自动重连并跑一次延迟测试；端口在**连接状态**下被改，保存后弹「断开重连让它生效吗？」，
而不是只留一句 toast 让用户自己记得。

教训：这类"替用户动别的进程"的功能要**先问、再优雅、最后才强杀**，并且把"是谁占着"
原样显示给用户。`taskkill` 不带 `/F` 对无窗口的控制台进程无效，会走满宽限期 ——
这是刻意的，别为了快就默认 `/F`。

**A-38 「我的」页的到期提醒/流量提醒开关点不动（搬了家，没人给它绑处理器）**

现象：界面自检报 `点「到期提醒」开关真的生效 :: 点了 3 次都没关掉`。
第 6 轮把这两个开关从设置页搬到「我的」页（用户要求），`Views.me` 里渲染出来了、
样式也对，**就是点了没反应**。

根因：`.switch[data-setting]` 的点击处理只写在 `bindSettings()` 里，而它只在设置路由
被调用（`render()` 的路由表 `settings: bindSettings`）。开关搬到「我的」页后，
`bindMe()` 原来是个空壳（`/* data-click 已在 bindCommon 处理 */`），于是没有任何
处理器 —— 页面长得对、点了没反应。

修法：把开关绑定抽成 `bindSettingSwitches()`，`bindSettings()` 与 `bindMe()` 都调它；
顺手把"先 await 再改样式"改成"先乐观改、失败回滚"（原来是 `await guard(...)` 之后
无条件 `classList.toggle("on", next)`，保存失败时样式仍显示成功）。

教训：**界面元素搬家时，要连"谁绑它"一起搬**。这类"渲染对了但没人绑"的缺陷，
只断言"元素存在/文案对"的自检永远绿 —— 必须真的点一下，并且**用主进程读回真实值**
（`flipUntil`）而不是看 DOM 上的乐观样式。同类的下一层风险见 A-27 第 4 条。

---

**A-39 删掉一个内置分流组，要连"它的规则集"一起想清楚**

第 8 轮用户要求删掉「📢 Google FCM」与「📢 苹果推送通知」两个组（原因见 D-11/记忆条目：
它们的规则全被前置组抢先命中，写着直连其实走代理）。删组时容易只动 `TABLE`：

- `ACL_GOOGLE_FCM` 的 provider 定义（`PROVIDERS.acl_googlefcm`）随即**没人引用**。
  留着是"与安卓端 provider 表一致、将来一行就能恢复"的便利，代价是 `resources/rules/`
  里那份 `acl_googlefcm.yaml` 种子**永远不会被拷贝**（`ensureRuleSeeds()` 只按
  `allProviderKeys()`＝被 TABLE 引用的键拷贝）→ 随包多一个没人用的文件。
  这一轮的处理：**删种子文件、保留 provider 定义并写注释说明**，让"随包规则集 47 个"
  和"被引用的 47 个"严格对上。
- 「苹果推送通知」的 12 条内联规则不是删掉而是**并入「🍎 苹果服务」组**（APNs 靠 IP 段，
  `geosite:apple` 只有域名，缺了就真的连不上推送）。
- TABLE 少两组会连带影响：默认开启集合（20 → 19）、`total`（27 → 25）、
  ui-test 里所有按数字写死的断言。**数字断言要么改成从 `rulesets` 动态取，要么一次全改到位。**

教训：删内置功能时，把"表 / provider 定义 / 随包种子 / 默认值 / 自检里的数字 / 文档"
当成一个整体过一遍；只删看起来最显眼的那一处，会留下永远不被使用的文件和永远对不上的断言。

---

**A-40 网站信息与后端类型：能读的全读，读不到就"直说读不到"**

第 8 轮用户问"该读网站信息的地方是不是真的读了，而不是内置进去了"。逐项核对后的结论是
**没有内置假数据**：站点名/描述/URL/ICP 来自 `/guest/comm/config`（登录后 `/user/comm/config`）、
套餐与订单来自 `/user/plan/fetch`、`/user/order/fetch`、公告来自 `/user/notice/fetch`、
Telegram 链接只在面板下发时才渲染；唯一的固定文案是登录页在面板没给描述时的兜底句子
（真面板有 `app_description`，会被覆盖）。后端类型也不再写死：`detectBackend` 照安卓端
`detectBackendType` 探测一次并落 store（见 D-10）。

教训：这类"是不是内置的"问题，只能靠**逐个字段追到接口**来回答；回答时把"哪个字段来自哪个接口"
列出来，比说一句"都是真的"有用得多。用户能一眼看出哪里还是假的。

---

**A-41 连接与"自动拉订阅"撞车：内核被拆了，用户那次连接报 `read ECONNRESET`**

第 9 轮自检里出现一屏 6 条红，日志时间线是这样的（同一秒级窗口）：

```
11:49:41.381 kernel ready: mixed=35282 controller=57605      ← 用户点的 connect 把内核拉起来了
11:49:43.251 system proxy on -> 127.0.0.1:35282              ← 还是那次 connect
11:49:43.261 subscription refreshed (42273 bytes)            ← 开机自动拉订阅拿到结果
11:49:43.282 config prepared: ... localRouting=on            ← 自动拉订阅的"改配置"
11:49:44.843 system proxy restored                           ← 自动拉订阅内部做了 disconnect
11:49:44.866 ERROR command connect failed: read ECONNRESET   ← 用户那次 connect 撞上死掉的控制面
11:49:45.105 kernel ready: mixed=34513                       ← 自动拉订阅又连了回来（内核其实活着）
```

`refresh_subscription` 的语义就是"拉订阅 → 重新生成配置 → **如果已连接就断开重连**"。
它和用户点的那次 `connect` 同时进来时，两者对内核的操作交错：用户刚起来的进程被
订阅刷新拆掉，用户那次连接去访问已经关掉的控制面 → `read ECONNRESET`。
**渲染层只看到"连接失败"**，于是首页不显示当前节点、节点页退回本地预览（`now` 永远空）、
`state.connected` 一直是 false —— 一屏红全由这一处引起，内核本身反而是活的。

修法（`electron/ipc.js`）：给"会改内核状态"的代码加一把串行锁。

```js
let kernelChain = Promise.resolve();
function serialKernel(fn) {
  const next = kernelChain.then(fn, fn);
  kernelChain = next.then(() => {}, () => {});   // 一次失败不能把链子打断
  return next;
}
```

`connect` / `disconnect` 整体进锁；`refresh_subscription` **只把"改配置 + 重载"那半段**进锁，
面板网络拉取刻意留在锁外（否则用户点连接要干等十几秒）。渲染层同时补一条：
`autoRefreshSubscription()` 在 `state.busy`（正在连接/断开）时直接返回，连接成功、
延迟测试跑完再补一次拉订阅。

教训：**凡是"内部会断开重连"的操作，都必须和用户的连接操作互斥**。这类竞态在单线程 JS 里
一样会发生 —— 因为它们是 `await` 之间交错的异步序列，不是线程问题。自检里值得直接写一条
"两个并发请求都不报错 + 之后控制面真的能应答"，否则下次改回来不会有任何测试拦住。

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

mihomo 的 provider（`type: file` 与 `type: http` 的 `path`）都走
`C.Path.IsSafePath(C.Path.Resolve(schema.Path))`，而 `IsSafePath` 只放行 `-d`（homeDir）下的子路径。
所以内置规则集不能直接从 `resources/rules/` 读，必须先铺到 `data/` 里再写相对路径 ——
第 4 轮改成 `type: http` 之后路径是 `polaris-rules/<key>.yaml`（`data/polaris-rules/`），
约束完全一样：**绝对路径、`..`、指向 `resources/` 一律被拒**。
顺带一个好处：`type: http` 的 `path` 同时是内核的下载缓存位置，预播种和在线更新共用同一个文件。

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

**D-7 Telegram 链接在**登录后**的 `/user/comm/config` 里，游客配置里没有**

「我的」页要跟手机端一样有 Telegram 入口。先在 `siteInfo()` 里读游客配置
`/guest/comm/config` 的 `telegram_url` —— **真面板上没有这个字段**，于是入口永远不出现。
安卓端的取法是登录后 `GET /user/comm/config` 取 `telegram_discuss_link`
（`XboardAuthApi.kt:357` → `XboardApi.kt:107`，字段见 `XboardDto.kt:61`）。
对齐之后真面板给的是一个 `https://t.me/…` 讨论组链接。

顺带一条安全口径：这个链接是要丢给系统浏览器打开的，所以照安卓端
`ExternalLinks.kt` 的白名单校验域名（只认 `t.me` / `telegram.me` / `telegram.dog`，
且必须 http(s)）；取不到就**隐藏入口**，而不是留一个点了没反应的死按钮。
校验放在主进程（`panel/client.js` 的 `safeTelegram`），渲染层只发"打开 Telegram"这个意图、
不传 URL。

**D-8 套餐说明与公告正文是 Markdown 原文，不是纯文本**

用户第 5 轮第 5 条：「公共和订阅套餐的文字描述要支持 md 代码渲染」。
真面板给的是 Markdown 原文（探针实测 `E:\AI\_probe_md.js`）：

* 套餐 `/user/plan/fetch` 的 `content`：段落 + `[文字](https://t.me/…)` + `---` + `📢[Telegram频道](...)`；
* 公告 `/user/notice/fetch` 的 `content`：`# 线路说明` 标题、`* 亚太·香港 CM（1000mbps）` 列表、
  `**粗体里套链接**`、`---`、以及 **Markdown 表格** `|Question|Answer|` + `|---|---|`，
  而且**一段里的换行是有意义的**（一行一个节点，不是软换行）。

原来的实现是 `h()` 转义后塞进 `white-space: pre-line` 的容器 → 用户看到的是满屏 `#`、`**`、`|`。

修法：新增 `src/js/md.js`（`window.PolarisMarkdown`），**先转义再解析**的安全子集渲染器：
标题 / 粗体 / 斜体 / 行内代码 / 代码块 / 有序无序列表 / 引用 / `---` / 链接 / 表格，
段落内的单个换行按 `<br>` 渲染（面板就是这么写的）；输出只包含自造的标签类名。
链接不放行 `javascript:` 之类，只认 `^https?://`，并且渲染层**不自己开浏览器** ——
点链接走 `data-mdlink` → 主进程 `open_external`（那里已经有 http(s) 白名单）。

`plans()` 的映射也要跟着改：`content` 是 Markdown 原文（`pick(p, ['content','description'])`），
`feats` 只是我们自己拼的摘要，只有 `content` 为空时才退回它。

---

**D-9 套餐与用量散在两个接口里，各 catch 各的，就拼出"半个真相"**

面板侧（xboard 系）这两件事**不在同一个接口**：

| 接口 | 有 | 没有 |
| --- | --- | --- |
| `/user/info` | `transfer_enable`（额度）、`expired_at`（到期） | `u`/`d`（已用上下行）、`plan` 对象 |
| `/user/getSubscribe` | `u`/`d`、`plan`（含名字） | 额度、到期 |

`userInfo()` 原来把两个接口**并行 + 各自 catch** 再合并，于是 `/user/getSubscribe`
一抖（限流/超时），返回的就是"有额度有到期、没有套餐名和已用"的半成品 ——
界面上看正好是「已用 0 B / 套餐 未订阅 / 到期正常」这种**看起来合理**的错误组合。

约定（现在写死在 `ipc.js planSnapshot()` 里）：套餐名、已用、额度**只认套餐口径**，
`/user/getSubscribe` 拿不到就沿用上一次成功的结果（45 秒短缓存，且只缓存成功结果），
而不是把 `transfer_enable` 单独拿出来当额度用。已用流量按**字节**传（`used_bytes`），
由界面 `fmt.bytes` 格式化 —— 面板按 GB 保留两位，29 KB 会被它自己显示成 0.00。

---

**D-10 后端类型要像安卓端那样"自己认"，不能写死 xboard**

第 8 轮用户点名要求："安卓 app 上已经弄好了，什么自动识别后端、流量信息接口都有，去看看；
注意 Windows 也要自动识别后端。"照安卓端 `LoginViewModel.detectBackendType` 抄了一版：

- 探测 `GET <panel>/api/v1/guest/comm/config`（5 秒超时），`data` 里出现
  `is_captcha` / `captcha_type` / `turnstile_site_key` / `recaptcha_v3_site_key` 任一 → **xboard**，
  否则 → **xiaov2b**。探不通只 `log.warn`，不阻断登录（返回上一次记住的值，最后兜底 `xboard`）。
- 结果落 `store.panel_backend`，`get_settings` 回显 `panel_backend`，`restore()` 时也带回来。
- 两端**真实差异只有两处**（这是整件事存在的理由）：礼品卡
  `user/gift-card/redeem`（xboard）vs `user/redeemgiftcard`（xiaov2b）；
  以及**流量明细 `/user/stat/getTrafficLog` 只有 xboard 有**
  （安卓 `TrafficViewModel` 里就是 `backendSupportsLog=false`）。其余路径两端逐字相同。
- 所以流量页在 `site_log === false` 时**不装作有数据**，而是显示
  「这个面板没有站点流量明细接口 / 识别到的后端是 xiaov2b，只提供账号累计用量」。

教训：把"平台差异"收敛到探测出来的一两个标志位上，比在每个调用点写 `if (xboard)` 好维护；
探测不出来时要**保持上一次的判断并说出来**，不要静默退回默认值装作没事。

---

**D-11 规则遮蔽要按"出口语义"判严重程度，不能一律当错**

第 8 轮排查分流落点时，逐组发真请求 + 读内核日志，发现两类"没落在自己组"：

- `redirector.c.play.google.com` 属于「🌏 Google Play」，却先命中了「🌏 Google」的
  `RuleSet(gs_google)`。**两边出口都是 proxy** → 只是归类不同，最终走向一样，无害。
- 用户报的「📢 Google FCM」是另一类：它配置写的是 `direct`，44 条规则**全**被
  `+.google.com` 抢走 → 用户以为在直连，实际全走代理（`🍎 苹果服务` 的
  `push.apple.com` 被 `+.apple.com` 抢走也是同一类，只是那 11 条仍生效）。
  这类必须当 bug（本轮的处置就是删组，见 A-39）。

自检的写法：把每组的 `out` 拉出来比对 —— **命中自己组 → PASS**；
**被同出口的组抢走 → PASS 但打印"规则遮蔽（同出口，无害但值得知道）"**；
**被不同出口的组抢走 → FAIL**（"本该是 direct/block"）。这样报告既不会天天假红，
又能在真出问题时指出来。

教训：判断"分流对不对"要落到**出口**上，而不是"命中了哪个组名"。组名只是分类。

---

**D-12 面板域名会被本机 DNS 解析成黑洞地址：用户看到的是「网络错误：面板请求超时」**

第 9 轮用户报"为什么提示网络错误"，日志里是 `slow command login 20022ms`（两次）+
`panel 请求超时`，而同一台机器上 `curl` 有时通有时不通。逐层量下来的事实：

- **同一时刻，两条解析路径给出的地址完全不同**：
  - `dns.lookup`（getaddrinfo，Node 默认走这条）→ 两个连不上的地址（一 IPv6 一 IPv4）；
  - `dns.resolve4/resolve6`（c-ares，直接问 DNS 服务器）→ Cloudflare 真地址
    （`curl` 拿到的也是这些）。
- 对 getaddrinfo 给的那两个地址做**裸 TCP 443**：两个都 **8s 超时**（黑洞）。强制 https 逐地址：
  IPv4 → `Client network socket disconnected before secure TLS connection was established`（5.1s）、
  IPv6 → 同样错误（264ms）；而**不指定地址、走域名**的请求 1042ms 成功。
- 原因：Node 24 默认开了 Happy Eyeballs（`net.getDefaultAutoSelectFamily() === true`，
  `autoSelectFamilyAttemptTimeout` 250ms），会并行试多个地址、自动挑活的；**只拿到黑洞地址时
  它无处可挑，只能死等到 20s 超时**。Electron 内置 node 24.21 也是同样默认值。
- 这不是"面板挂了"：公共 DNS 问一次就能拿到真地址（`223.5.5.5` 82ms、
  `119.29.29.29` 646ms、`180.76.76.76` 266ms；**`1.1.1.1` 恰好也回那两个黑洞地址，故不采用**）。

修法（`electron/panel/client.js`，三层兜底，全部只影响面板请求）：

1. **自己解析**：`resolveAll(host)` 并行取 `dns.lookup{all:true}` + `resolve4` + `resolve6` 合并去重
   （getaddrinfo 和 c-ares 的答案不一样，合并后命中真地址的概率就上来了），
   请求时用 `lookup` 选项喂给 `https.request`，并显式带 `autoSelectFamily:true,
   autoSelectFamilyAttemptTimeout:250`。
2. **换地址重试**：连接阶段单独计时（`connectTimeout = min(5000, max(2000, timeoutMs/4))`），
   连不上就换下一个地址重试（最多 4 次）；`connectionAttemptTimeout`/`connectionAttemptFailed`
   事件里把连不上的 IP 记进 `badAddrs`（5 分钟有效，`orderAddresses` 只把它们**降级**到后面、
   不排除）。重试的判据是**"面板一个字节都没回过"**（`!ph.ttfb`）——请求没到面板，重试才安全。
3. **公共 DNS**：第二次尝试起叠加 `PUBLIC_DNS = ['223.5.5.5','119.29.29.29','180.76.76.76']`
   （每个服务器一个 `new dns.Resolver()` + `setServers`），答案排在最前面。

顺带修掉一个自作聪明的重试：响应路径原来的 4 处 `reject(new PanelError(...))` 没盖
"阶段"标记，重试循环读到空标记以为"没收到任何字节"，**把 HTTP 400/422/404 也换地址重试了 3 次**。
现在统一走 `fail(err)`（盖上 `err.phase`），4xx 一律不重试。

还有一处是看日志才发现的：本机路由器偶尔把 DNS 请求**整个挂住**（一批面板请求全停在
"地址还没解析出来"），连接计时器先到点，日志只能写 `面板连接超时（试过 未知地址）` ——
既没信息量，请求也白等。现在每条解析路都有 `DNS_TIMEOUT = 2500` 上限，连接计时器按
`connectTimeout + DNS_TIMEOUT` 计，到点还没地址就明说「DNS 在 2.5s 内没有给出地址」。

自检怎么写：`scripts/real-test.js` 的「面板拨号健壮性」段 —— 纯函数（坏地址记忆、排序、
有效期）+ 把 `dns.lookup` 替换成只回 `203.0.113.1`（RFC 5737 保留段）验证"合并结果里还有别的
地址"+ 真发一次请求验证"带黑洞地址时照样成功"+ 日志里不该出现"换地址重试"（4xx 那条）
+ 把 `dns.lookup/resolve4/resolve6` 全换成不回调，验证"解析会自己收尾"。

教训：用户报"网络错误"时，别只盯着应用代码 —— **先量解析**。同一台机器上 `dns.lookup` 和
`dns.resolve4` 给出不同答案这件事，只有把两条路径的结果并排打印出来才会看见。

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

**E-14 异步界面里的自检要"等安静"再动手，点一下不生效就再点一次**

这一轮 4 条红里有 3 条不是产品坏，而是**自检在界面还没稳的时候就去量/去点了**：
连接内核后的那轮 `refreshAll()` 要几秒才回来，回来时会 `render()` 一次；
自检在那之前读到的分组列表还是连接前的本地预览（误报 A-26 重复卡），
或者量好坐标正要按下去，元素被换掉了（误报"点不到元素"）。

两个原语解决：
- `settle(win, {quiet = 1500, timeout = 40000})`：轮询 `window.__polarisRenderCount`，
  连续 `quiet` 毫秒不增长才算安静 —— 比 `sleep(固定值)` 可靠，也不浪费（安静就立刻返回）。
- `clickUntil(win, sel, cond, {tries = 3})`：点一下 → 等条件成立；没成立就再点一次。
  界面"第一次点击被吞"是真实存在的用户场景（见 A-27），所以这里点两次不算放水，
  但如果三次都不成立就该红。

教训：**自检的每一步都要问"我现在读到的是不是最新的"**。
`window.__polarisRenderCount` / `__polarisRoute` / `__polarisState` 这几个钩子就是为这个留的。

**E-15 两条"看起来是产品坏、其实是断言写错"的红（换模型那轮的典型）**

① **`/providers/rules` 的响应形状变了**：自检读 `raw[key].ruleCount`，全 `undefined` →
报"内核一个规则集都没加载"、"规则总数 0"。真相是 mihomo v1.19 把它包在
`{"providers": {<key>: {ruleCount, vehicleType, behavior, format, updatedAt, ...}}}` 里
（旧版本直接铺在顶层）。加一个 `unwrapProv(raw)`（有 `.providers` 就取它）两种都吃，
并顺手断言 `vehicleType === 'HTTP'`（确认是内核在线拉取的，不是本地 file 兜底）。
用一次性探针脚本直接 `console.log(JSON.stringify(raw).slice(0,300))` 看一眼原始响应，
比继续猜快得多。

② **"点按钮"和"数据到位"混在一个等待条件里**：流量页区间按钮的等待条件原来是
「按钮高亮 **且** `state.traffic.range === 'month'`」，超时 90 秒。红的时候完全看不出是哪半没满足；
事后查日志那 90 秒里**根本没有发出 `get_traffic`** —— 是那一下点击被重绘吞了（A-27 同源）。
改成两步：先 `clickUntil` 只等「按钮高亮」（4 秒，吞了就再点，并把"第几次才点中"打出来），
再单独 `waitFor` 「数据到位」（60 秒）。红了立刻能分清"没点中"还是"面板慢"。

教训：**等待条件里每 `&&` 一个不同来源的子条件，诊断力就减一半**；
把它们拆成有先后顺序的两步，失败信息自己会说话。

**E-16 一屏 16 条红，根因是"我自己运行环境里的 `HTTP_PROXY`"**

现象：`--uitest` 从「准备登录态：登录后进首页」开始连锁 16 条红，套餐显示未订阅、
面板数据全 0，看起来像登录/面板整层塌了。日志里的真话只有一句：

```
ERROR command get_plans failed: connect ECONNREFUSED 127.0.0.1:7890
```

`get_orders / get_tickets / get_invite / get_notices / siteInfo / telegramLink / trafficLog`
全是同一句 `ECONNREFUSED 127.0.0.1:7890`。7890 是本机**另一款代理软件**（BettboxCore）
的端口，而它当时没在运行。

根因不在产品：**这个开发 harness 会给它启动的每个子进程注入
`HTTP_PROXY=HTTPS_PROXY=http://127.0.0.1:7890`**，而 Electron 主进程里的 Node `https`
（面板请求走的就是它）会照 `HTTPS_PROXY` 走 → 代理不在 → 面板全线不可达。
用户从资源管理器双击启动**没有这些环境变量**，所以这纯粹是自检环境的产物。

修法：写一个干净的启动器（`_launch_ui.ps1` / `_run_clean.ps1`），用
`System.Diagnostics.ProcessStartInfo` + `UseShellExecute = $false`，启动前

```powershell
foreach ($k in @('HTTP_PROXY','HTTPS_PROXY','http_proxy','https_proxy','ELECTRON_RUN_AS_NODE')) {
  try { $psi.EnvironmentVariables.Remove($k) } catch { }
}
```

两个必须记住的点：

* `.ps1` 里**只能有 ASCII**。PowerShell 5.1 按 ANSI 读无 BOM 的 UTF-8 文件，注释里一个中文
  就能报出一串莫名其妙的语法错误。
* `ELECTRON_RUN_AS_NODE` 只能 `Remove`，**赋空串不算删掉** —— Electron 看到这个变量存在
  就按纯 Node 跑，`require('electron')` 拿不到 `app`，main.js 第一行就崩。

教训：**一屏断言同时因为网络错误变红时，先查运行环境，再查产品**；并且要能一句话回答
"用户从桌面启动会不会遇到同样的事" —— 会，才是产品缺陷，不会，就是我自己的测试脚手架在
骗我。（相关但未定：面板 API 请求是否应该尊重 `HTTP_PROXY`？用户环境里若有指向已关闭代理的
全局代理变量，产品同样会全屏失败。这一条已列为待确认项，不擅自改。）

---

**E-17 自检断言会"过期"：改了设计不改断言，红的是断言不是产品**

第 7 轮把 `include-all` 换成显式列成员（A-36）之后，数据层自检首跑 **105 通过 / 2 失败**，
两条红都是：

```
FAIL  主组 proxies 恰好是 [自动选择, 故障转移, DIRECT]（不许 include-all）
FAIL  所有组都不再 include-all
```

看着像"改坏了"，其实是**断言写死了上一版的实现**（老断言要求 `include-all === true`、
要求主组 `proxies` 只有三项）。产品行为完全正确：主组前三项仍是那三个结构出口，
只是后面显式跟上了 32 个节点。

处理：把断言从"实现形状"改成"**用户能感知的契约**"—— 主组**前三个成员**是
`[自动选择, 故障转移, DIRECT]`、节点显式写进来（`mainProxies.length > 3` 且不带 `include-all`）、
兜底组只有 `[主组, DIRECT]`；另外补两条**防回归**断言：「节点顺序 = 网站下发的顺序」
（逐项与 `subscribe.yaml` 比对）与「分组里不是按名字排序的」（正是 A-36 的回归点）。

教训：断言要钉**契约**（顺序、可见行为、外部可见的数据形状），不要钉**内部实现形状**
（某个字段等不等于 true、数组长度恰好是几）—— 后者在重构时必然假红，而假红最贵的代价是
"让人不再相信自检"。相应地，每次有意改设计，必须同一批改动里把受影响断言一起改，
并把**当初引出这个设计的那个现象**补成一条防回归断言。

---

**E-18 `ipc.js` 的失败是"返回值"不是"异常"，断言形状要先看真实返回**

第 8 轮新写的"全功能覆盖"段首跑 **162 通过 / 6 失败**，六条全是**断言写法错**，产品没坏：

| 断言原本假设 | 真实情况 |
| --- | --- |
| `register` / `forgot_password` / `change_password` 会 reject | `ipc.js` 里是 `catch (e) { return fail(friendly(e)); }` —— 返回 `{ok:false,msg}`，**不抛错**；`.then(()=>'').catch()` 拿到的是空串 |
| `send_email_code` 会给"邮箱不存在"的细文案 | 面板只回 `{"ok":false,"msg":"面板返回 HTTP 400"}` |
| `get_app_info().version` 有值 | `version` 来自 `store.get('version')`，由 `main.js` 启动时写；这个自检入口是 `scripts/real-test.js`（不走 main.js）→ 空串。改断言 `data_dir` 非空 + `portable` 布尔 |
| `get_update_state()` 有 `state` / `can_apply` | `updater.info()` 真实形状是 `{phase, version, url, file, received, total, percent, error, staged_at, extractor, install_dir}` —— **`phase` 不是 `state`，也没有 `can_apply`** |

修法：统一用一个 `asFail(r)` 把 `{ok:false,msg}` 折成 `FAIL:msg` 再断言；
形状类断言改成"拿真实返回里的字段名"（`phase`），并顺手核对了 `updater.info()` 的实现。

教训：写断言前**先打印一次真实返回**（`console.log(JSON.stringify(x))`），
比对着自己脑子里的形状写要快得多。六条红一条产品问题都没有，但每条都花了我一轮复跑 ——
自检的第一个成本不是"跑多久"，是"假红的次数"。下次的改进方向：把"真实返回"先跑成探针看一眼。

**E-19 界面"自己跳到别的页"这类问题，先把跳转流水记下来**

第 9 轮界面线出现过一次 **145 通过 / 1 失败**：点流量页的「本周」按钮（按钮确实高亮了），
60 秒后页面却停在**首页**（`{"route":"home","segs":0,"tr":"today"}`），之后再跑两次都是 162/0。
只报"最终状态是首页"是查不出调用点的，于是给渲染层加了一条**路由流水**：
`app.js` 的 `nav()` / `goBack()` / `boot()` 各记一条 `{at, from, to, by:<调用栈第 3 帧>}` 到
`window.__navLog`（最近 30 条，`try/catch` 包住，生产路径零影响），自检失败信息里直接打印最后 6 条。

它到现在没再复现（同一版本连跑两次全绿），所以**结论是"重绘吞点击"那一族的老问题**
（A-27：慢数据回来时整块 `innerHTML` 重画，这一下点击连"按钮高亮"都没留下），
而不是某条隐藏的 `nav("home")` —— 全仓 `nav("home")` 只有 4 处（登录成功、注册成功、boot、点侧边栏），
且已排除页面重载（`ui-test.js` / `main.js` / `app.js` 里都没有 `reload(` / `location.`）。

教训：**"偶发"的问题不要靠再跑一次来确认它好了**，要先把"能证明是谁干的"的证据挂上去
（这里是调用栈），下一次复现时自检报告自己会说。流水本身留着不删 —— 它是这类问题的探针，
成本是一个数组和一次 `new Error().stack`。

---

### F. 本机环境 / 工具链

| 坑 | 处理 |
| --- | --- |
| `execute` 里用 `start` 拉起 GUI 程序会一直等进程退出，被下一条消息打断判定为"未完成" | 用 `powershell Start-Process -Wait`，或让程序自己退出 |
| GUI 子系统的 exe 不挂控制台，直接跑拿不到输出 | 输出重定向到文件再读 |
| PowerShell 的 `& electron.exe ... \| Out-File` **不等 GUI 进程**（日志会是 0 字节、`$LASTEXITCODE` 为空） | 用 `cmd /c "set ELECTRON_RUN_AS_NODE=&& electron.exe ... > 文件 2>&1"`（cmd 会等子进程）；界面自检仍用 `Start-Process` + 轮询报告文件 |
| 控制台是 GBK，Node/Python 打印中文会 `UnicodeEncodeError` | 结果写 UTF-8 文件，再用 `read_file` 读 |
| `cmd` 用 `&&` 串多条命令有时整条返回空 | 关键命令分开执行 |
| `git commit -F -` 在 cmd 里拿不到 stdin | 先把消息写成文件，再 `-F <路径>` |
| `git rm -r --cached` 之后再 `git rm -r` 会报 pathspec 不匹配 | 索引与工作区两步走，或直接删目录再 `git add -A` |
| 仓库根 `.gitignore` 忽略 `build/` | `git add -f` |
| 开发 harness 给每个子进程注入 `HTTP_PROXY=HTTPS_PROXY=http://127.0.0.1:7890`，Electron 主进程的 Node `https` 会照走，代理没开就整层 `ECONNREFUSED` | 用 `_run_clean.ps1` / `_launch_ui.ps1` 启动（`ProcessStartInfo` 里 `EnvironmentVariables.Remove`），详见 E-16 |
| `.ps1` 里写中文会报奇怪的语法错 | PS 5.1 按 ANSI 读无 BOM 文件，临时脚本保持纯 ASCII |
| `$psi.EnvironmentVariables['ELECTRON_RUN_AS_NODE'] = ''` 不生效 | 只能 `Remove`，见 E-16 |

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
# 注意：PowerShell 的 `& exe | Out-File` 不等 GUI 进程（日志会是 0 字节），要走 cmd：
cmd /c "set ELECTRON_RUN_AS_NODE=&& node_modules\electron\dist\electron.exe scripts\real-test.js --panel=<面板地址> --email=<测试账号> --password=<口令> > E:\AI\_real.txt 2>&1"
# 或者直接用已经登录好的那份 data（凭据是 DPAPI 加密的，拷一份到临时目录就能用）
copy <成品包目录>\data <临时目录>\data
node_modules\electron\dist\electron.exe scripts\real-test.js --restore --data=<临时目录>

# 真面板界面自检（开真窗口真点；用独立 data，不动你日常登录的那份）
Remove-Item Env:ELECTRON_RUN_AS_NODE
Start-Process -FilePath .\node_modules\electron\dist\electron.exe -ArgumentList `
  '.','--uitest','--panel=<面板地址>','--email=<测试账号>','--password=<口令>','--uitest-data=E:\AI\_uitest_data'
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
→ 副本里的 Polaris.exe --updtest-version=9.9.9 --updtest http://127.0.0.1:8124/Polaris-portable-1.9.0.zip

验收：gs_apple.yaml 回来了、USERDATA.txt 还在、data\update\apply-update.log 里有 done, restarting、
     重启后的进程在跑、data\update 里只剩 apply-update.cmd 与 apply-update.log（包和 staging 都清掉了）。
```

> **开关要写在 URL 前面**，或者写成 `--updtest-version=9.9.9` 的等号形式 ——
> 位置参数之后再跟 `--switch value`，Electron 会在主进程起来之前就退出（exit -1、无日志），
> 见踩坑 A-13。这一条真的花了十几分钟才从"应用崩了"里认出来。
>
> 本机注意：`npx` / `npm.ps1` 被执行策略禁止 → 打包用 `node node_modules\electron-builder\cli.js`、
> 数据层自检用 `node scripts\real-test.js`（旧 `selftest-*.js` 已删）；跑 Electron 前必须 `Remove-Item Env:ELECTRON_RUN_AS_NODE`
> （**而且这个变量会传染给子进程**，用 Node spawn 起成品包时要把 env 过滤掉，否则 Electron 退化成纯 Node，
> 报 `<exe>: bad option: --smoke` 并秒退）。
>
> **两条自检线都要在"干净环境"里跑**：这个 harness 会给子进程注入
> `HTTP_PROXY=HTTPS_PROXY=http://127.0.0.1:7890`，Electron 主进程的 Node `https`（面板请求走它）
> 会照走，代理没开就整层 `ECONNREFUSED`（见 E-16）。现成脚本：
> `powershell -File E:\AI\_run_clean.ps1 -Exe <electron.exe> -ArgLine '<脚本与参数>' -Out <日志> -WorkDir <app 目录>`
> （`real-test.js` 用这个），界面自检用 `E:\AI\_launch_ui.ps1`（自带停旧实例、删旧报告、跑完打印 FAIL 行）。

---

## 五、下次继续的入口

按优先级：

1. ~~真实面板联调~~ ✅ 已打通（数据层 `scripts/real-test.js` **182 项**、界面层 `scripts/ui-test.js` **162 项**，都全绿）。
   还没覆盖到的真面板路径：**注册/找回密码/改密码**（会真改账号，没敢跑）、**下单与支付**
   （`/user/order/getPaymentMethod` 在真面板返回空数组 → 购买套餐页会没有支付方式可选，
   这是面板侧配置问题，不是客户端 bug；真面板还提示走 Telegram 下单）、
   **工单回复**（建单验过了，回复没验）、**礼品卡兑换**（要真卡密）。
   界面层还没覆盖的：**设置页的每一项都点一遍**（现在只验了「分流与订阅」段存在）、
   **弹窗的键盘操作**（Esc 关闭、Tab 顺序）、**托盘菜单**（收进托盘后右键各条目）、
   **长列表性能**（要一份几百节点的订阅）、**多屏 DPI 缩放**、**TUN 下的同一套走查**、
   **自定义分流组的拖动**（真面板订阅里没有自定义组，界面层只拖了内置组）。
   另：**面板 API 请求是否该尊重 `HTTP_PROXY`**（E-16 的延伸）—— 用户机器上若有指向已关闭代理的
   全局代理变量，产品会整层 `ECONNREFUSED`。要不要强制面板请求不走代理，**待用户定**，没擅自改。

1.2 **"重绘吞点击"这一类还剩最后一层**（A-27 第 4 条已按"按着不换、松手再换"收口）
   现在的做法是"指针按着期间不换 DOM"，不是增量渲染：一次真的数据更新仍会整块重建
   `#content`，只是排在 `pointerup` 之后。真正干净的做法是列表/卡片做**键控增量更新**
   （只改变化的行），那是渲染层的一次重构，没做。若以后还遇到"点了没反应"，
   优先怀疑这里，并用 `flipUntil`（主进程读回真实值）确认，别只看 DOM 上的乐观样式。

1.5 **本地方案（第 4 轮）还没验到的边角**
   - 「本地分流」**关掉之后**的走查：界面自检只验了开关回退与提示，没验"关掉后面板规则真的生效"
     （要连上内核看 `/rules` 里出现面板的 `GEOIP,CN`）。
   - **降级路径**：面板订阅里塞一个叫「自动选择」的节点名，走一遍"整体降级回面板配置 + 分流页提示原因"。
     代码路径写好了，但只能靠构造订阅才触发（真面板里没有这种节点名）。
   - **在线更新真的发生**：现在验的是"种子只补缺失"，没验"24 小时后内核真的去 jsDelivr 拉了新版"
     （要把 `interval` 临时改小、或把种子文件 mtime 改老，再等内核后台拉取）。
   - 自定义规则组的 `url` 形态（`type: http` 的 `behavior/format` 校验）只有单元级覆盖。

1.6 **面板域名被本机 DNS 解析成黑洞地址（第 9 轮，已做三层兜底，但根因在路由器）**
   实测本机路由器会间歇性只回两个连不上的地址（一 IPv6 一 IPv4），
   同一个域名用 c-ares 问却是真地址。现在 `panel/client.js` 会**合并两条解析路 + 换地址重试
   + 最后问公共 DNS**，所以应用不会再卡满 20s。但这是"绕过"：真正的修法是路由器/DNS 侧
   别再下发那两个地址。若用户再报"面板请求超时"，先看日志里 `换地址重试 N/4` 后面的
   `已知连不上的地址` 列表 —— 它会把当时的坏地址原样列出来。

1.7 **界面"自己跳到首页"只出现过一次（第 9 轮，已挂探针但未复现）**
   一次界面自检里，点流量页「本周」后 60 秒页面停在首页（同一版本随后连跑三次全绿）。
   现在 `app.js` 有一条路由流水 `window.__navLog`（`nav`/`goBack`/`boot` 各记一条调用栈），
   自检失败信息里会打印最后 6 条 —— 下次复现时先看它，别再从"最终状态"倒推。见 E-19。

2. **TUN 模式实机验证**（需要用户同意，会临时接管网络栈）
   开 TUN → 验证流量 → 关 TUN → 确认路由/DNS 还原、虚拟网卡状态。先跟用户确认再动。

3. **自动更新的收尾**（自替换已实装并真跑过，见 §二.5）
   - 没有回滚：覆盖失败只会留下版本混杂（`robocopy` 不删文件），可以再加"上一版备份 + 失败回滚"
   - 没有校验：只判了 zip 头和 `Polaris.exe` 是否存在，没有签名/哈希校验
   - 后端还缺 `update_windows_url`：现在 `remote.updateInfo()` 已经优先取它，
     但真实面板得先把这个字段补上，否则 Windows 端只能退回「前往下载」

4. **内置分流还能再往前一步的地方**（排序与自定义规则组已在 `6c72762` 做完，见 §二.6）
   - 自定义规则**按组整块编辑**，没有"单条规则拖动排序"：组内规则按书写顺序匹配，
     要调整靠编辑弹窗里挪行。组之间可以**拖动**排（2026-10-10 起，见 A-28）。
   - 自定义规则**只能内联写**，不能引用 `data/polaris-rules/` 里的 yaml 规则集文件
     （第 4 轮换成 `type: http` 之后，自定义组引用的文件也得让内核去下，不能再"塞本地文件"）。
     要支持的话：UI 加一个"从 URL 导入"，写进 `rule-providers` 时给 `behavior`（`gs_*` 是 domain /
     `gp_*` 是 ipcidr / `acl_*` 是 classical）。
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
