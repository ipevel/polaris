# Polaris Windows 客户端 · 现状与踩坑记录

> 最后更新：2026-10-09（内置分流已实装，见 §二.4 / §三 C-6 C-7）
> 分支：`feat/windows-portable`（基线 `origin/ui/windows-design` @ `5328b55`）
> 应用根目录：`design/windows/app/`

---

## 一、现状速览

### 产物

```
design/windows/app/dist/Polaris-portable-1.8.0.zip     185,899,380 B（~186 MB）
```

解压即用：不装运行时、不写注册表、不写 `%APPDATA%`。已验证。

> 验证方式：把 zip 解压到一个干净目录直接跑，`--uitest` 56/0、`--doctor` 72/0、`--smoke` `ok:true`，
> `data/rules` 自动铺出 48 个种子。

### 规模

| | |
| --- | --- |
| 提交 | `3d8eb6a` `ce71ad7` `8e578fd` `8f04c18` `77650f8` `621186d` `ba97f42` |
| 相对基线 | +115,989 / −902 行，103 文件 |
| 应用代码 | 主进程 ~3,460 行 JS，渲染层 ~1,423 行 JS，样式 ~380 行 |
| 内核 | mihomo v1.19.32（windows-amd64-**compatible**）+ wintun 0.14.1 |
| 规则库 | geoip.metadb + geosite.dat + ASN.mmdb，23.8 MB |
| 内置分流 | 48 个本地规则集 + 27 个分流组，1.42 MB（`resources/rules/`） |

### 三条自检线（成品包自带，目标机器无需源码）

| 命令 | 规模 | 覆盖 |
| --- | --- | --- |
| `npm test` | 110 项 | 订阅清洗、配置组装、内置分流规则、直连域名与更新地址、地区识别、真实拉起 mihomo |
| `npm run test:e2e` | 72 项 | 假面板登录、订阅清洗、面板字段映射、真实流量、内置分流热重载、系统代理还原 |
| `Polaris.exe --uitest` | 56 项 | **界面驱动**：拖拽区、最大化、滚动、填表点登录、逐页切换、分流页开关、流量曲线、弹窗、设置页不自我重绘 |
| `Polaris.exe --doctor` | 72 项 | 端到端（同 e2e），结果写 `data/doctor-report.txt` |
| `Polaris.exe --smoke` | — | 窗口能起 + DOM 渲染断言 |

当前全绿：**110 / 72 / 56**（开发态与成品包各跑一遍）。

### 已实测通过

- 登录 → 拉订阅 → 清洗 → 起内核 → 挂系统代理 → 经代理访问外网返回 204
- 真实系统流量被正确分流（日志可见 `my.1password.com` 走节点、`*.dingtalk.com` 走 GeoIP(cn) 直连）
- 断开后系统代理 4 个注册表值逐项还原到进入前的状态
- 最大化/还原布局无横向溢出；设置页可滚到底
- 便携性：删掉 `%APPDATA%\Polaris` 后跑成品，不再重建
- 内置分流：16 个默认规则集被内核**真正加载**（`/providers/rules` 逐个 `ruleCount > 0`，合计 > 1 万条），
  运行中开关分流组能热重载并让内核加载新规则集

### 未验证 / 未做

| 项 | 状态 | 说明 |
| --- | --- | --- |
| 真实面板联调 | ❌ | 后端按 Android 端 Kotlin 契约写完，假面板验过；但各面板分支字段名不统一，需要真实账号 |
| TUN 模式实机 | ❌ | 代码路径完整（含 UAC 提权、网卡收尾、残留清理），但会临时接管网络栈，未在真机跑 |
| 面板活跃会话管理（踢设备） | ❌ | 需要新功能：**两端都没有这个接口**（面板侧也没有），不是「UI 未做」 |
| 自动更新（下载并替换自身） | ⚠️ | 只做到「检测到新版本 → 打开下载页」；Windows 更新地址走 `update_windows_url`，不回落安卓 APK |
| 自定义规则集（用户自己写规则） | ❌ | 内置分流已做（27 组 / 48 规则集可开关）；用户自定义规则组、分组排序未做 |
| 分流组排序 | ❌ | `routing_order` 已在配置层支持（`orderedTable`），但 UI 没有排序入口 |

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

---

### F. 本机环境 / 工具链

| 坑 | 处理 |
| --- | --- |
| `execute` 里用 `start` 拉起 GUI 程序会一直等进程退出，被下一条消息打断判定为"未完成" | 用 `powershell Start-Process -Wait`，或让程序自己退出（`--smoke` / `--doctor`） |
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
npm run mock                  # 演示数据，不连内核
npm start                     # 真实模式

# 自检
npm test                      # 核心层 110 项（纯 Node）
npm run test:e2e              # 端到端 72 项（起真 mihomo + 假面板 + 真流量）
npm run test:e2e -- --sysproxy  # 连系统代理一起验（写 HKCU 并精确还原）

# 打包
powershell -ExecutionPolicy Bypass -File scripts/build-portable.ps1
```

成品包在别人机器上排查：

```
Polaris.exe --doctor            端到端自检，报告 → data/doctor-report.txt
Polaris.exe --doctor --sysproxy 连系统代理一起验
Polaris.exe --uitest            界面驱动自检，报告 → data/uitest-report.txt
Polaris.exe --mock              演示数据启动，看界面
```

> 本机注意：`npx` / `npm.ps1` 被执行策略禁止 → 用 `node node_modules\electron-builder\cli.js`、
> `node scripts\selftest-core.js` 直接跑；跑 Electron 前必须 `Remove-Item Env:ELECTRON_RUN_AS_NODE`。

---

## 五、下次继续的入口

按优先级：

1. **真实面板联调**（需要面板地址 + 测试账号）
   拿一份真实面板，跑 `npm run test:e2e` 的等价流程，重点核对 `panel/client.js` 里的字段兜底有没有兜到。有偏差就直接改映射表。

2. **TUN 模式实机验证**（需要用户同意，会临时接管网络栈）
   开 TUN → 验证流量 → 关 TUN → 确认路由/DNS 还原、虚拟网卡状态。先跟用户确认再动。

3. **自动更新做成自替换**
   现在是「检测到新版本 → 打开下载页」（Windows 地址取 `update_windows_url`，不回落安卓包）。
   要做成下载 zip → 校验 → 替换自身 → 重启。便携版替换自身时文件被占用，必须让一个
   脱离进程（`cmd /c ping` 等窗口期或独立 .bat）等主进程退出后再解压覆盖。

4. **内置分流的剩余部分**
   - 分流组排序：配置层已支持（`routing_order` + `orderedTable`），缺 UI 入口
   - 用户自定义规则集：往 `data/rules/` 放 yaml + 加一条 `rule-providers` 即可，
     但要注意 `gs_*` 是 domain/`gp_*` 是 ipcidr/`acl_*` 是 classical，behavior 不能写错

5. **补 UI 层面的更多断言**
   目前 56 项。还可以补：暗色主题下的对比度、窄窗口（<1120px）断点下的布局、长列表性能。
