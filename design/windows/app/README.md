# Polaris Windows 客户端

基于 `design/windows` 设计系统构建的 Windows 桌面应用（Tauri 2 + WebView）。

## 架构

```
app/
├── src/                    # 前端：HTML/CSS/JS（即设计稿本身，可直接在浏览器预览）
│   ├── index.html          # 应用骨架：自绘标题栏 + 侧边栏 + 路由容器
│   ├── css/design.css      # 设计系统（与 design/windows/css 同源）
│   ├── js/api.js           # 数据层：Tauri invoke，浏览器下自动降级为 mock
│   ├── js/views.js         # 16 个页面视图（纯函数）
│   └── js/app.js           # 路由、状态、事件、自绘标题栏
└── src-tauri/              # Rust 后端
    ├── src/main.rs         # tauri::command（与 api.js 一一对应）+ 托盘
    └── src/core.rs         # 内核抽象 Core trait；当前 MockCore，真实实现替换 WindowsCore
```

- 自绘无边框窗口：`tauri.conf.json` 中 `decorations: false`，标题栏/窗口按钮由前端绘制（`data-tauri-drag-region`）。
- 前端在浏览器里打开 `src/index.html` 即可全功能预览（mock 数据）；打包后走 Rust commands。
- 所有面板业务接口（登录/套餐/订单/工单/礼品卡/公告/邀请）在 `main.rs` 中已按 Xboard 面板 API 形状留好 TODO。

## 内核接入（下一步）

实现 `core.rs` 中的 `Core` trait，推荐 sidecar 方案：把 mihomo Windows 二进制作为 Tauri sidecar 打包，
经其 HTTP API 驱动（`/configs`、`PUT /proxies/{name}`），TUN 走 mihomo 自带 `tun`（需管理员权限）。
`main.rs` 的 commands 层不需要任何改动。

## 在 Windows 上构建

```powershell
# 安装 Rust + Node.js + WebView2（Win10/11 自带）
npm install -g @tauri-apps/cli
cd app
npm run build        # 输出安装包在 src-tauri/target/release/bundle/
```

图标：`src-tauri/icons/` 目前只有 `p-icon.png` 占位，构建前运行 `tauri icon src/assets/p-icon.png` 生成全套。

## 功能状态

- [x] 16 个页面全部可交互（导航/搜索/测速/弹窗/开关/表单）
- [x] 托盘菜单（原生）
- [x] 自绘标题栏（最小化/最大化/关闭）
- [ ] 真实代理内核（待接入，见 core.rs）
- [ ] 面板 API 联调（各 command 内 TODO）
- [ ] 自动更新（tauri updater plugin）
