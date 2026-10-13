# Polaris Windows 客户端

基于 `design/windows` 设计系统构建的 **Windows 桌面应用**。目标是**便携版**：解压到任意目录（含 U 盘）双击即用，不装任何运行时、不写注册表。

> **接手前先读 [`docs/DEVNOTES.md`](docs/DEVNOTES.md)** —— 现状、架构决策的理由、以及踩过的坑（尤其「换壳留下的隐性地雷」那一节，那类问题代码能跑、测试全绿，但用户一碰就废）。

## 为什么是 Electron 而不是 Tauri

原始骨架是 Tauri 2，但 Tauri 的 exe 依赖系统 WebView2 运行时。要满足"任何 Win10+ 机器解压即用、不装额外依赖"，就得把 WebView2 固定版（约 180 MB）一起打包，体积和 Electron 持平，却多一道 Rust 工具链门槛。Electron 自带 Chromium，真正零依赖，所以改用 Electron。

前端（`src/`）**一行没重写**：`src/js/api.js` 的命令名与原 Tauri 版完全一致，只是 `invoke` 的落地从 `window.__TAURI__.core` 换成 preload 暴露的 `window.polaris`。原 `src-tauri/` 骨架已删除，需要时用 `git show 5328b55:design/windows/app/src-tauri/src/main.rs` 取回。

## 架构

```
app/
├── src/                    # 前端：HTML/CSS/JS（= 设计稿，可直接在浏览器预览）
│   ├── index.html          # 应用骨架：自绘标题栏 + 侧边栏 + 路由容器
│   ├── css/design.css      # 设计系统（与 design/windows/css 同源）
│   ├── js/api.js           # 数据层：window.polaris.invoke（只走真实主进程，没有 mock 降级）
│   ├── js/views.js         # 16 个页面视图（纯函数）
│   └── js/app.js           # 路由、状态、事件、自绘标题栏、实时状态订阅
├── electron/               # 主进程（后端）
│   ├── main.js             # 窗口、托盘、单实例、生命周期
│   ├── preload.js          # contextBridge：window.polaris
│   ├── ipc.js              # 命令注册表（与 api.js 逐条对应）
│   ├── paths.js            # 便携数据目录解析
│   ├── store.js            # settings.json 原子读写
│   ├── logger.js           # 滚动日志 + 敏感字段脱敏
│   ├── tray.js             # 托盘菜单（连接/节点/退出）
│   ├── core/
│   │   ├── manager.js      # mihomo sidecar 生命周期、状态机、速率 EMA
│   │   ├── controller.js   # external-controller HTTP/WS 客户端
│   │   ├── sanitizer.js    # 订阅清洗（安全边界，见下）
│   │   ├── builder.js      # 最终 config.yaml 组装
│   │   ├── region.js       # 节点名 → 地区
│   │   ├── traffic.js      # 流量 WebSocket 与速率聚合
│   │   ├── rulesets.js     # 内置分流规则集（12 组 / 15 个本地规则集）
│   │   ├── updater.js      # 更新下载、解压、替换脚本（自替换）
│   │   └── remote.js       # 远程配置（多源择优、Base64 混用）
│   ├── panel/
│   │   ├── client.js       # Xboard / XiaoV2b（V2Board 家族，路径同构）
│   │   └── credentials.js  # safeStorage（DPAPI）加密凭据
│   └── net/
│       ├── sysproxy.js     # HKCU 系统代理 + InternetSetOption 广播
│       └── autostart.js    # HKCU\...\Run 开机自启
├── scripts/
│   ├── real-test.js        # 真面板联调（要真账号；真订阅/真节点/真流量）
│   ├── ui-test.js          # 界面自检（`--uitest`，真窗口真鼠标事件）
│   ├── fetch-core.py       # 拉内核二进制到 core/
│   ├── fetch-geo.py        # 拉规则库到 resources/geo/
│   └── clean.js
├── core/                   # 内核二进制（不入库，见 core.lock.json）
│   ├── mihomo.exe
│   └── wintun.dll
└── build/icon.png          # 打包图标源
```

### 订阅清洗是安全边界，不只是"让内核能加载"

面板下发的 YAML 被当作不可信输入处理：

- **夺回控制面**：`external-controller*` / `secret` / 全部端口 / `allow-lan` / `bind-address` 一律剥离，防止订阅劫持本机代理或让内网可访问
- **供应链投毒面整块丢弃**：`geox-url`（规则库下载源）、`ntp`（时间源劫持）、`script` / `scripting` / `web` / `listeners` / `hosts`
- **防止整份配置被内核拒绝**：重名节点改名（含 `DIRECT`/`GLOBAL` 等内核内置名）、缺 `name` 的条目写盘前闸门拦下
- **剔除信息伪节点**：面板把「剩余流量：86.5 GB」这类说明文本伪装成真节点塞进分组，内核 url-test 会真的选中它们（连上却不通）。判定条件是"同端点 + 名字像说明文本"，且剔除会导致分组清空时整步放弃
- **直连兜底**：面板域名与远程配置 `direct_domains` 自动注入 DIRECT 规则和 fake-ip 豁免，避免开了代理自己联系不上自己

## 运行

```bash
# 依赖与内核
npm install
npm run core     # 拉 mihomo.exe + wintun.dll 到 core/
npm run geo      # 规则库到 resources/geo/（同仓库有 Android assets 时直接拷，否则从 MetaCubeX 发布页下载）

# 运行（唯一方式：真实模式，需要已登录面板）
npm start

# 真面板联调（要真账号；密码只在命令行给一次，不写进任何文件）
npm run test:real -- --panel=<面板地址> --email=<测试账号> --password=<口令>

# 界面自检（会真的开窗口、真鼠标点，要真账号）
npm run test:ui -- --panel=<面板地址> --email=<测试账号> --password=<口令>

# 离线自检 13 条（不需要凭据、不下载外网、不碰真安装目录）
npm run test:offline
# 或单跑：test:updater / test:updater-timeout / test:traffic / test:geosync / test:rulesync /
#         test:routing-sync / test:flags / test:flag-row / test:dns / test:portowner /
#         test:config / test:single-source / test:routing-view

# 打包便携版（动手前先问用户）
powershell -ExecutionPolicy Bypass -File scripts/build-portable.ps1
# 或： npm run dist   → dist/Polaris-portable-<version>.zip
```

> `test:real` / `test:ui` 缺 `--panel/--email/--password` 会**立刻打印用法并以退出码 2 退出**
> （由 `scripts/preflight-creds.js` 在启动 Electron 之前挡一道），不会让你等一整个启动才发现少参数。
> 参数是**追加**在 `--` 之后的；`test:real` 还支持 `--restore --data=<数据根目录>` 复用已登录会话。
>
> ⚠️ **跑 `test:real` 会改动工作树**：规则 + geodata 的 12 小时刷新会写 `resources/geo/`
> （dev 下 `paths.geo()` 就指这个仓库跟踪目录）。跑完请还原：
> `git checkout -- design/windows/app/resources/geo/`
> 刷新标记 `rules_last_sync` 落在 `data/polaris-rules/`（未跟踪，不用管）。
> 详见 `docs/DEVNOTES.md` §三 A-58 与 A-62。

### CI 门禁

`.github/workflows/ci-windows.yml`（**push / PR 到 `windows-portable` + 手动 `workflow_dispatch`**，
跑在 **`windows-latest`** 上）跑：

> 文件名带 `-windows` 是故意的：主仓库已有 `.github/workflows/ci.yml`（安卓 + Go 的
> version-gate / go / quality）。同名会在同步进 main 时把它整个覆盖掉，所以分开命名。
>
> 跑 Windows runner 而不是 ubuntu：`updater` 的 `extract()` 是 Windows 实现
> （bsdtar + `powershell Expand-Archive` 回退），在 ubuntu 上必然 `spawn powershell ENOENT` ——
> 产品本身也只有 Windows 版，拿 ubuntu 验是错平台。代价是计费更高，所以只跑离线自检，
> 不装 Electron、不打包。

1. `npm run test:offline` —— **13 条离线自检**（清单见下表）
2. `node --check` 全量 js 语法检查
3. **分流表跨语言一致性 + 跨仓库输入真取到**：从 `main` 取 `kernel-core/` 与安卓
   `app/src/main/assets/flags`，用 `POLARIS_GO_TABLE` / `POLARIS_ANDROID_DIR` 指过去真跑
   `routing-sync-test.js`，并设两个 `_REQUIRED=1` **禁止跳过**（找不到就是红，不是绿）

> 第 3 条为什么不许跳过：`windows-portable` 分支上没有 `kernel-core/`，本地跑会打印「跳过」并
> `exit 0` —— 在 CI 里那等于一个永远绿的假通过。而这条测试要防的正是「main 改了分流表、
> Windows 镜像没跟上」这种静默分叉（2026-10-12 就这么分叉过一次）。
>
> 现状（2026-10-14 07:2x）：`main` 的 Go 表**已经追平**（`main` tip `47c25fc`，安卓交付
> `ce6d656`），两端实测都是 `12 组 / 15 个 provider / 241 条内联`，所以这道门禁在
> `windows-portable` 上**是真绿**：`5b3ca8b` 的 run `38473927116` 全部 success。
> 它红过几次（最近一次是 run `38454540083`），红的原因正是当时 `main` 还停在旧表 ——
> 那几次红是它该有的表现，不是缺陷；`main` 追平后自己就绿了。
>
> ⚠️ **发版前提**：`build.yml` 的 `windows` job 打的是 **`main` 上的** `design/windows/app`，
> 所以必须先同步 `main` 再发版，否则放出去的便携版会是旧分流表（`v1.11.5` 就是同步之后
> 打的：解包实测包内 asar 是 12 组 / 15 个 provider）。同步清单见 `docs/DEVNOTES.md` §五 的 1.9
> （**绝不做整分支合并**，一律路径限定取件）。

### 出问题怎么查

出问题的第一现场是 `data/logs/polaris.log`（启动、内核起停、面板请求、系统代理改写都记在里面），
其次是 `data/config.yaml`（内核真正吃进去的配置）与 `data/profiles/`（面板下发的订阅原文）。

### 测试覆盖什么

| 方式 | 覆盖 | 不覆盖 |
| --- | --- | --- |
| `npm run test:real`（`scripts/real-test.js`，真账号真面板，221 项） | 真登录 → 套餐/订单/工单/邀请/公告 → 今天/本周/本月流量明细与账号合计交叉验算 → 拉真订阅 → 连真节点 → 逐组延迟 → 经活节点出网 204 → 跑真流量看面板明细闭环 → 拖动排序 → 本地方案接管 → 三个特殊出口各自真出网 | 界面手感与观感（由人点）、TUN 模式、下单支付 |
| `npm run test:ui`（`scripts/ui-test.js`，真窗口真鼠标，174 项） | 自己从登录页用真账号登进去，再逐页真点：外壳/登录/首页/连接/节点页/分流页/流量页/我的页/6 个二级页/设置页/断开 | 托盘菜单、多屏 DPI、TUN 下的走查、自定义分流组拖动 |
| `npm run test:offline`（13 条离线自检，CI 里也跑） | `test:updater` 自我替换 helper（25 项）、`test:updater-timeout` 下载超时三层（27 项）、`test:traffic` 流量页视图（36 项）、`test:geosync` 旧 geodata 模块单测（11 项）、`test:rulesync` 规则+geo 12 小时懒更新 + 钉 commit SHA（42 项）、`test:routing-sync` 两端分流表逐条一致、`test:flags` 国旗码表↔图（11 项）、`test:flag-row` 节点行画旗（15 项）、`test:dns` DNS 解析器无污染源（10 项）、`test:portowner` 端口占用者铁律（14 项）、`test:config` 默认值与设置迁移（12 项）、`test:single-source` 规则只有一个源（8 项）、`test:routing-view` 分流规则页视图（17 项） | 需要 Electron 运行时或真面板的路径（那两条在上面） |
| 人工手测 | 界面交互、手感、观感、布局与主题 | — |

> 2026-10-10 起**不再有任何离线自检线与假数据**：原 `scripts/mock-panel.js`（假面板）、
> 旧的 `scripts/selftest-ui.js`（配假面板的 `--uitest`）、`--doctor` / `--rttest` 两条离线自检线、
> `npm test`（核心层）、`--mock`（演示数据）
> 全部删除，浏览器直接打开 `src/index.html` 也不再伪造数据。理由：假面板的响应形状是照我们自己的
> 理解写的，只能证明自洽 —— 建工单我们发 `content`、假面板收得下（绿），真面板只认 `message`（红），
> 一个会给错安全感的绿灯比没有绿灯更贵。详见 `docs/DEVNOTES.md` §三 E-10。
>
> 注意：现在的 `--uitest` 是**新写的** `scripts/ui-test.js`，走真面板真账号真鼠标事件，
> 与被删掉的旧 `selftest-ui.js`（假面板 + `--mock`）没有关系。



### 性能注意

规则库（`resources/geo`，20.7 MB）随包分发。不这么做的话，mihomo 首次连接会自己去 GitHub 下 `GeoIP.dat`——实测 **19 秒**，而且没网就直接起不来。带上之后是 **2–13 ms**。

## 便携目录布局

打包产物解压后：

```
Polaris/
├─ Polaris.exe          # 主程序
├─ core/                # mihomo.exe + wintun.dll + 许可
├─ data/                # 全部运行时数据，删掉即恢复出厂
│  ├─ config.yaml
│  ├─ profiles/         # 订阅原文
│  ├─ providers/
│  ├─ settings.json
│  ├─ credentials.dat   # DPAPI 加密
│  └─ logs/
└─ resources/
```

数据目录解析顺序：`<exe 同级>/data` → 目录只读时回退 `%APPDATA%/Polaris` → 开发态 `<app>/.devdata`。

## 前提与已知限制

- **TUN 模式需要管理员权限**，默认走系统代理（免管理员）。在设置里开启 TUN 时会弹 UAC 重启应用
- **TUN 残留**：Windows 上 Node 的 `child.kill()` 是强杀，内核来不及摘路由和虚拟网卡。
  接口断开后不会影响上网，但会留下一张「已断开」的 `Polaris` 网卡；
  设置页里有「虚拟网卡」状态和手动清理入口（需要管理员）
- **系统代理只写 HKCU**（WinINET），不碰 WinHTTP 全局设置——那会影响整机，不适合便携应用
- 关闭主窗口 = 收进托盘（`退出` 才真正退出）。退出时必定还原系统代理
- 凭据用 DPAPI 加密，换机器即失效需重新登录（符合"无自建服务器、不采集"的隐私承诺）
- **未接入的能力**：面板的活跃会话管理（踢设备）—— 面板接口本身没有，需要面板侧先加
- **TUN 模式尚未实机验证**（会接管网络栈，需用户在场确认）
