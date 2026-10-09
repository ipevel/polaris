# Polaris Windows 客户端

基于 `design/windows` 设计系统构建的 **Windows 桌面应用**。目标是**便携版**：解压到任意目录（含 U 盘）双击即用，不装任何运行时、不写注册表。

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
│   ├── main.js             # 窗口、托盘、单实例、生命周期、--smoke 自检
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
│   │   └── remote.js       # 远程配置（多源择优、Base64 混用）
│   ├── panel/
│   │   ├── client.js       # Xboard / XiaoV2b（V2Board 家族，路径同构）
│   │   └── credentials.js  # safeStorage（DPAPI）加密凭据
│   └── net/
│       ├── sysproxy.js     # HKCU 系统代理 + InternetSetOption 广播
│       └── autostart.js    # HKCU\...\Run 开机自启
├── scripts/
│   ├── selftest-core.js    # 核心层自检（纯 Node，含真实拉起 mihomo）
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
# 开发（带内置演示数据，不连内核）
npm run mock

# 开发（真实模式，需要已登录面板）
npm start

# 核心层自检（纯 Node，不启动 Electron）
node scripts/selftest-core.js

# 打包便携版
npm run dist      # 产物：dist/Polaris-portable-<version>.zip
```

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

- **TUN 模式需要管理员权限**，默认走系统代理（免管理员）。首次开启 TUN 会 UAC 提权
- **系统代理只写 HKCU**（WinINET），不碰 WinHTTP 全局设置——那会影响整机，不适合便携应用
- 关闭主窗口 = 收进托盘（`退出` 才真正退出）。退出时必定还原系统代理
- 凭据用 DPAPI 加密，换机器即失效需重新登录（符合"无自建服务器、不采集"的隐私承诺）
