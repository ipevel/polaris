# Polaris Windows 版界面设计稿

16 张桌面端设计稿，全部由同一套 CSS 设计系统渲染，保证字号 / 间距 / 组件零漂移。

## 规范

见 [SPEC.md](SPEC.md)。核心要点：

- 应用自绘无边框窗口（无系统标题栏），48px 浅色自绘顶栏
- 顶栏只放 P 应用图标（项目 `mipmap-xxxhdpi/ic_launcher.png`），无字样
- 白色 230px 侧边栏，浅蓝胶囊选中态 `#E8F0FE`
- 内容区 `#F5F5F7`，白色 16px 圆角卡片，主色 `#1A73E8`

## 目录

| 目录 | 说明 |
| ---- | ---- |
| `css/design.css` | 设计系统唯一真相来源，所有页面共用 |
| `pages/` | 16 个页面 HTML（home/nodes/traffic/settings/me/login/plans/orders/tickets/invite/giftcard/notices/proxy/routing/update/tray） |
| `assets/p-icon.png` | 应用图标（取自项目 mipmap） |
| `shots/` | 1600×1000 渲染截图 |
| `build.py` | 页面生成器（骨架只定义一次，各页只填内容） |

## 重新生成

```bash
python3 build.py          # 生成 pages/
python3 shot_all.py       # 需 Chromium CDP，输出 shots/
```

## 页面清单

- home 首页 / nodes 节点 / traffic 流量 / settings 设置
- me 我的 / login 登录 / plans 购买套餐 / orders 我的订单
- tickets 我的工单 / invite 邀请好友 / giftcard 礼品卡兑换 / notices 公告
- proxy 代理模式弹窗 / routing 分流规则 / update 检查更新弹窗 / tray 托盘菜单
