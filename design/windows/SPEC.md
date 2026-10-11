# Polaris Windows 版设计规范（锁定，2026-10-09 用户确认）

所有设计图必须严格遵守以下规范，不得自行发挥。

## 窗口框架（应用自绘，无系统边框）
- 无边框窗口，16px 圆角，细描边 + 柔和投影，悬浮于浅色桌面壁纸之上
- 顶部自绘标题栏：高 48px，浅色（白/极浅灰）
  - 左侧：仅放 P 应用图标（项目真实图标，见下），无任何文字
  - 中部：可拖拽区
  - 右侧：三个极简自绘按钮 — 最小化（—）、最大化（▢）、关闭（✕），关闭悬停变红

## 应用图标
- 使用项目真实图标：`/home/hatch/workspace/polaris/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png`
- 几何字母 P，蓝色 #2F6BF6，圆环碗部，浅色圆角方底
- 生成图时作为 image reference 传入，prompt 中声明"icon alone, no text, no wordmark"

## 侧边栏
- 白色，宽 230px，顶部直接开始导航（不放 logo）
- 导航项：首页 / 节点 / 流量 / 我的 / 设置，图标 + 文字
- 选中态：浅蓝胶囊背景 #E8F0FE + 蓝色图标文字；未选中灰色
- 底部：小字灰色版本号 "v1.7.4"

## 内容区
- 背景 #F5F5F7
- 白色卡片，16px 圆角，柔和阴影
- 主色 #1A73E8；下载蓝 / 上传橙
- 页面大标题：28px 加粗居左（首页无大标题，用连接舞台）
- iOS 极简风，中文排版干净，行距宽松

## 通用 prompt 模板（每次逐字套用框架部分，只改页面内容）
> CUSTOM APP FRAME — no OS title bar: borderless window, 16px rounded corners, thin border, soft drop shadow on subtle wallpaper. App-drawn slim 44px LIGHT top bar: LEFT side shows ONLY the app icon from the attached reference image — a rounded-square icon with light background and a blue geometric letter 'P' whose bowl is a ring/donut shape (icon alone, absolutely no text, no wordmark anywhere); rest draggable; right side three minimal app-styled window buttons (minimize dash, maximize square, close X; close turns red on hover). STRICT UNIFIED THEME: white left sidebar 230px starting directly with nav list (home icon 首页, layers icon 节点, chart icon 流量, person icon 我的, gear icon 设置) — NO logo in sidebar; selected = light-blue pill (#E8F0FE) blue icon+text, others gray; sidebar bottom small gray "v1.7.4". Content #F5F5F7. White cards 16px radius, soft shadow. Primary blue #1A73E8. Clean Chinese typography, iOS-minimal.
