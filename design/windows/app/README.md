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
│   ├── js/api.js           # 数据层：window.polaris.invoke；浏览器下自动降级为 mock
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
│   │   ├── rulesets.js     # 内置分流规则集（27 组 / 48 个规则集）
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
npm run geo      # 规则库到 resources/geo/（优先用同仓库 Android assets）

# 运行（唯一方式：真实模式，需要已登录面板）
npm start

# 真面板联调（要真账号；密码只在命令行给一次，不写进任何文件）
node_modules\electron\dist\electron.exe scripts\real-test.js `
    --panel=<面板地址> --email=<测试账号> --password=<口令>

# 打包便携版（动手前先问用户）
powershell -ExecutionPolicy Bypass -File scripts/build-portable.ps1
# 或： npm run dist   → dist/Polaris-portable-<version>.zip
```

### 出问题怎么查

出问题的第一现场是 `data/logs/polaris.log`（启动、内核起停、面板请求、系统代理改写都记在里面），
其次是 `data/config.yaml`（内核真正吃进去的配置）与 `data/profiles/`（面板下发的订阅原文）。

### 测试覆盖什么

| 方式 | 覆盖 | 不覆盖 |
| --- | --- | --- |
| `scripts/real-test.js`（真账号真面板） | 真登录 → 套餐/订单/工单/邀请/公告 → 今天/本周/本月流量明细与账号合计交叉验算 → 拉真订阅 → 连真节点 → 逐组延迟 → 经活节点出网 204 → 跑真流量看面板明细闭环 | 界面手感与观感（由人点）、TUN 模式、下单支付 |
| 人工手测 | 界面交互、手感、观感、布局与主题 | — |

> 2026-10-10 起**不再有任何离线自检线与假数据**：原 `scripts/mock-panel.js`（假面板）、
> `--uitest` / `--doctor` / `--rttest` 三条离线自检线、`npm test`（核心层）、`--mock`（演示数据）
> 全部删除，浏览器直接打开 `src/index.html` 也不再伪造数据。理由：假面板的响应形状是照我们自己的
> 理解写的，只能证明自洽 —— 建工单我们发 `content`、假面板收得下（绿），真面板只认 `message`（红），
> 一个会给错安全感的绿灯比没有绿灯更贵。详见 `docs/DEVNOTES.md` §三 E-10。



### 性能注意

规则库（`resources/geo`，23.8 MB）随包分发。不这么做的话，mihomo 首次连接会自己去 GitHub 下 `GeoIP.dat`——实测 **19 秒**，而且没网就直接起不来。带上之后是 **2–13 ms**。

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

数据目录解析顺序：`<exe 同级>/data` → 目录只读时回退 `%LOCALAPPDATA%/Polaris` → 开发态 `<app>/.devdata`。

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
