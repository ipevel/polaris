#!/usr/bin/env python3
"""生成全部设计稿 HTML。骨架/导航/顶栏只定义一次，各页只填内容。"""
import os

BASE = os.path.dirname(os.path.abspath(__file__))
PAGES = os.path.join(BASE, "pages")
os.makedirs(PAGES, exist_ok=True)

ICONS = {
    "home": '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 11l9-8 9 8"/><path d="M5 10v10h5v-6h4v6h5V10"/></svg>',
    "nodes": '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l9 5-9 5-9-5 9-5z"/><path d="M3 13l9 5 9-5"/></svg>',
    "traffic": '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M5 20v-8"/><path d="M11 20V6"/><path d="M17 20v-5"/><path d="M3 20h18"/></svg>',
    "me": '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 3.5-6.5 8-6.5s8 2.5 8 6.5"/></svg>',
    "settings": '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3.2"/><path d="M19 12a7 7 0 0 0-.14-1.4l2-1.55-2-3.46-2.36.95A7 7 0 0 0 14.1 5.1L13.75 2.6h-3.5L9.9 5.1a7 7 0 0 0-2.4 1.44l-2.36-.95-2 3.46 2 1.55a7 7 0 0 0 0 2.8l-2 1.55 2 3.46 2.36-.95a7 7 0 0 0 2.4 1.44l.35 2.5h3.5l.35-2.5a7 7 0 0 0 2.4-1.44l2.36.95 2-3.46-2-1.55c.1-.46.14-.93.14-1.4z"/></svg>',
}
NAV = [("home", "首页"), ("nodes", "节点"), ("traffic", "流量"), ("me", "我的"), ("settings", "设置")]

SHELL = """<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8">
<link rel="stylesheet" href="../css/design.css"></head><body>
<div class="window">
  <div class="titlebar">
    <img class="app-icon" src="../assets/p-icon.png">
    <div class="drag"></div>
    <div class="win-btns">
      <button class="win-btn">—</button><button class="win-btn">▢</button><button class="win-btn close">✕</button>
    </div>
  </div>
  <div class="main">
    <div class="sidebar">
      {nav}
      <div class="ver">v1.7.4</div>
    </div>
    <div class="content">{content}</div>
  </div>
  {overlay}
</div>
</body></html>"""

def nav(active):
    return "\n".join(
        f'<div class="nav-item{" active" if k == active else ""}">{ICONS[k]}<span>{label}</span></div>'
        for k, label in NAV
    )

def page(name, active, content, overlay=""):
    html = SHELL.format(nav=nav(active), content=content, overlay=overlay)
    open(os.path.join(PAGES, name + ".html"), "w", encoding="utf-8").write(html)

def head(title, sub="", actions=""):
    return f'<div class="page-head"><div><div class="page-title">{title}</div>' + \
        (f'<div class="page-sub">{sub}</div>' if sub else '') + \
        f'</div><div class="head-actions">{actions}</div></div>'

def row(k, v, chev=True, icon=None, vcls=""):
    ic = f'<span class="mini-icon" style="background:{icon[1]}">{icon[0]}</span>' if icon else ''
    return f'<div class="row"><div class="k">{ic}<span>{k}</span></div><div class="v {vcls}"><span>{v}</span>' + \
        ('<span class="chev">›</span>' if chev else '') + '</div></div>'

# ============ 1. 首页 ============
HOME_CONTENT = """
<div class="col-narrow">
  <div class="hero">
    <button class="power-btn"><svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.4" stroke-linecap="round"><path d="M12 3v9"/><path d="M6.3 6.5a8 8 0 1 0 11.4 0"/></svg></button>
    <div class="status-line"><span class="dot"></span><span class="t">已连接</span></div>
    <div class="node-line">香港 01 · 45ms</div>
  </div>
  <div class="speed-row">
    <div class="speed-tile up"><span class="ic"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#1a73e8" stroke-width="2.4" stroke-linecap="round"><path d="M12 4v14"/><path d="M6 12l6 6 6-6"/></svg></span><span><div class="lb">下载</div><div class="vl">12.4 MB/s</div></span></div>
    <div class="speed-tile dn"><span class="ic"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#e07b00" stroke-width="2.4" stroke-linecap="round"><path d="M12 20V6"/><path d="M6 12l6-6 6 6"/></svg></span><span><div class="lb">上传</div><div class="vl">2.1 MB/s</div></span></div>
  </div>
  <div class="section-label">会话</div>
  <div class="card">
    <div class="row"><div class="k"><span>代理模式</span></div><div class="v"><span>规则模式</span><span class="chev">›</span></div></div>
    <div class="row"><div class="k"><span>本次上传</span></div><div class="v strong"><span>1.2 GB</span></div></div>
    <div class="row"><div class="k"><span>本次下载</span></div><div class="v strong"><span>8.6 GB</span></div></div>
    <div class="row"><div class="k"><span>运行时间</span></div><div class="v strong"><span>02:34:18</span></div></div>
  </div>
  <div class="section-label">当前套餐</div>
  <div class="card">
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px">
      <div style="font-size:14px">已使用 <b>86 GB</b> / 200 GB</div>
      <button class="btn btn-primary btn-sm">续费</button>
    </div>
    <div class="progress"><div style="width:43%"></div></div>
    <div style="font-size:13px;color:var(--text3);margin-top:10px">到期时间 2026-11-05</div>
  </div>
</div>"""

# ============ 2. 节点 ============
def node_row(nm, region, ms, sel=False, offline=False):
    if offline:
        tail = '<span style="color:var(--red);font-weight:700;font-size:13px">离线</span>'
    else:
        badge = {"45ms": "b-green", "62ms": "b-green", "92ms": "b-green", "188ms": "b-orange", "未测": "b-gray"}[ms]
        tail = f'<span class="badge {badge}">{ms}</span>'
    return f'<div class="node-row"><span class="nm">{nm}</span><span class="badge b-blue">{region}</span>{tail}<span class="radio{" sel" if sel else ""}"></span></div>'

NODES_CONTENT = head("节点", "32 个节点 · 5 个分组",
    '<span class="search-wrap"><input class="search" placeholder="搜索节点"></span><button class="btn btn-outline btn-sm" style="height:40px">测速</button><button class="btn btn-primary btn-sm" style="height:40px">刷新订阅</button>') + """
<div class="card" style="margin-bottom:14px">
  <div class="acc-head"><div><div class="acc-title">节点选择</div><div class="acc-sub">当前：香港 01</div></div><span class="chev" style="font-size:20px">⌃</span></div>
""" + node_row("香港 01", "香港", "45ms", True) + node_row("香港 02", "香港", "62ms") + node_row("新加坡 01", "新加坡", "188ms") + node_row("日本 01", "日本", "92ms") + node_row("美国 01", "美国", "未测", offline=True) + """
</div>
<div class="card" style="margin-bottom:14px"><div class="acc-head" style="padding:0"><div class="acc-title" style="font-size:15px">国外网站分流</div><span class="chev" style="font-size:20px">⌄</span></div></div>
<div class="card"><div class="acc-head" style="padding:0"><div class="acc-title" style="font-size:15px">流媒体分流</div><span class="chev" style="font-size:20px">⌄</span></div></div>"""

# ============ 3. 流量 ============
TRAFFIC_CONTENT = head("流量", "",
    '<button class="btn btn-primary btn-sm" style="height:40px">今日</button><button class="btn btn-ghost btn-sm" style="height:40px">本周</button><button class="btn btn-ghost btn-sm" style="height:40px">本月</button>') + """
<div class="card" style="margin-bottom:14px">
  <div style="display:flex;justify-content:space-between;margin-bottom:8px"><b style="font-size:15px">网络速度 · 24小时</b><span style="font-size:13px;color:var(--text2)"><span style="color:var(--blue)">●</span> 下载　<span style="color:var(--orange)">●</span> 上传</span></div>
  <svg viewBox="0 0 640 220" width="100%" height="220">
    <defs><linearGradient id="g1" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1a73e8" stop-opacity=".35"/><stop offset="1" stop-color="#1a73e8" stop-opacity=".04"/></linearGradient></defs>
    <g stroke="#ececf0"><line x1="40" y1="20" x2="40" y2="180"/><line x1="40" y1="180" x2="630" y2="180"/></g>
    <path d="M40,150 C120,140 170,60 260,55 C350,50 400,110 480,120 C550,128 590,90 630,95 L630,180 L40,180 Z" fill="url(#g1)"/>
    <path d="M40,150 C120,140 170,60 260,55 C350,50 400,110 480,120 C550,128 590,90 630,95" fill="none" stroke="#1a73e8" stroke-width="2.5"/>
    <path d="M40,165 C140,160 220,120 320,115 C420,110 500,140 630,110" fill="none" stroke="#ff9500" stroke-width="2.5"/>
    <g font-size="11" fill="#8e8e93"><text x="40" y="198">00:00</text><text x="150" y="198">04:00</text><text x="265" y="198">08:00</text><text x="380" y="198">12:00</text><text x="490" y="198">16:00</text><text x="590" y="198">20:00</text></g>
  </svg>
  <div style="font-size:13px;color:var(--text2);margin-top:4px">当前下载 12.4 MB/s · 当前上传 2.1 MB/s</div>
</div>
<div class="speed-row" style="margin:0 0 4px">
  <div class="speed-tile up"><span><div class="lb">今日下载</div><div class="vl">8.6 GB</div></span></div>
  <div class="speed-tile dn"><span><div class="lb">今日上传</div><div class="vl">1.2 GB</div></span></div>
</div>
<div class="section-label">明细</div>
<div class="card">
  <div class="row"><div class="k"><span>总下载</span></div><div class="v strong"><span>128.4 GB</span></div></div>
  <div class="row"><div class="k"><span>总上传</span></div><div class="v strong"><span>36.2 GB</span></div></div>
  <div class="row"><div class="k"><span>峰值速率</span></div><div class="v strong"><span>24.8 MB/s</span></div></div>
  <div class="row"><div class="k"><span>在线节点</span></div><div class="v strong"><span>28</span></div></div>
</div>"""

# ============ 4. 设置 ============
SETTINGS_CONTENT = head("设置") + '<div class="col-narrow">' + """
<div class="section-label">通用</div><div class="card">
""" + row("外观", "跟随系统", icon=("◐", "#7aa5f8")) + row("语言", "简体中文", icon=("🌐", "#34c759")) + row("TUN 堆栈", "System", icon=("≋", "#af8cf8")) + row("修改密码", "", icon=("🔑", "#ffb340")) + """
</div><div class="section-label">提醒</div><div class="card">
<div class="row"><div class="k"><span>到期提醒</span></div><div class="switch on"></div></div>
<div class="row"><div class="k"><span>流量提醒</span></div><div class="switch on"></div></div>
<div class="row"><div class="k"><span>开机自启动</span></div><div class="switch"></div></div>
</div><div class="section-label">关于</div><div class="card">
""" + row("当前版本", "1.7.4", chev=False) + row("检查更新", "", chev=False, vcls="") + row("开源许可", "") + row("隐私政策", "") + """
</div></div>"""

# ============ 5. 我的 ============
ME_CONTENT = head("我的") + '<div class="col-narrow">' + """
<div class="card" style="margin-bottom:14px"><div style="display:flex;align-items:center;gap:14px">
<div style="width:52px;height:52px;border-radius:50%;background:#e8e8ed;display:flex;align-items:center;justify-content:center;font-size:22px;color:#8e8e93">👤</div>
<div><div style="font-size:16px;font-weight:700">user@example.com</div><span class="badge b-blue" style="margin-top:6px;display:inline-block">旗舰套餐</span></div>
</div></div>
<div class="card" style="margin-bottom:14px">
<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px"><div style="font-size:14px;font-weight:700">当前套餐</div><button class="btn btn-primary btn-sm">续费</button></div>
<div style="font-size:14px;margin-bottom:10px">已使用 <b>86 GB</b> / 200 GB</div>
<div class="progress"><div style="width:43%"></div></div>
<div style="font-size:13px;color:var(--text3);margin-top:10px">到期时间 2026-11-05</div>
</div>
<div class="card">
""" + row("我的订单", "", icon=("🧾", "#ffb340")) + row("我的工单", "", icon=("🎫", "#7aa5f8")) + row("邀请好友", "", icon=("🎁", "#34c759")) + row("礼品卡兑换", "", icon=("💳", "#af8cf8")) + row("公告", "", icon=("📢", "#ff9f43")) + row("关于", "", icon=("ℹ️", "#8e8e93")) + """
<div class="row" style="justify-content:center"><span style="color:var(--red);font-weight:600">退出登录</span></div>
</div></div>"""

# ============ 6. 登录 ============
LOGIN_HTML = """<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8">
<link rel="stylesheet" href="../css/design.css"></head><body>
<div class="login-wrap">
  <div class="titlebar"><img class="app-icon" src="../assets/p-icon.png"><div class="drag"></div>
  <div class="win-btns"><button class="win-btn">—</button><button class="win-btn">▢</button><button class="win-btn close">✕</button></div></div>
  <div class="login-body"><div class="login-card">
    <img class="app-icon" src="../assets/p-icon.png">
    <h1>欢迎回来</h1><div class="sub">登录以同步您的套餐与节点</div>
    <input class="field" placeholder="面板地址 https://">
    <input class="field" placeholder="邮箱">
    <input class="field" type="password" placeholder="密码">
    <button class="btn btn-primary" style="width:100%;height:48px;font-size:15px">登录</button>
    <div style="font-size:13px;color:var(--text3);margin-top:16px">还没有账号？<span style="color:var(--blue);font-weight:600">去注册</span></div>
  </div></div>
</div></body></html>"""

# ============ 7. 购买套餐 ============
def plan_card(name, price, unit, feats, hot=False):
    return f'<div class="plan-card{" hot" if hot else ""}">' + \
        ('<div class="ribbon">最受欢迎</div>' if hot else '') + \
        f'<h3>{name}</h3><div class="price">¥{price}<small>/{unit}</small></div><ul>' + \
        "".join(f"<li>{f}</li>" for f in feats) + \
        f'</ul><button class="btn {"btn-primary" if hot else "btn-outline"}">立即购买</button></div>'

PLANS_CONTENT = head("购买套餐", "", '<button class="btn btn-outline btn-sm" style="height:40px">🎁 礼品卡</button>') + '<div class="plan-grid">' + \
    plan_card("月度套餐", "19.9", "月", ["100GB 流量", "2 台设备"]) + \
    plan_card("年度套餐", "169", "年", ["500GB 流量", "5 台设备", "优先线路"], hot=True) + \
    plan_card("季度套餐", "49", "季", ["200GB 流量", "3 台设备"]) + \
    '</div><div style="text-align:center;font-size:13px;color:var(--text3);margin-top:18px">支持支付宝 / 微信支付 · 购买后即时生效</div>'

# ============ 8. 订单 ============
def order_row(name, no, date, amount, badge, pay=False):
    b = f'<span class="badge {badge[1]}">{badge[0]}</span>'
    btn = '<button class="btn btn-primary btn-sm" style="margin-left:8px">去支付</button>' if pay else ''
    return f'<div class="row"><div class="k"><div><div style="font-weight:700;font-size:15px">{name}</div><div style="font-size:12.5px;color:var(--text3);margin-top:4px">{no} · {date}</div></div></div><div class="v strong"><span style="font-size:19px">¥{amount}</span>{b}{btn}</div></div>'

ORDERS_CONTENT = head("我的订单") + '<div class="card">' + \
    order_row("旗舰年付套餐", "No.202610091201", "2026-10-09", "169", ("已完成", "b-green")) + \
    order_row("旗舰年付套餐", "No.202610071103", "2026-10-07", "169", ("待支付", "b-orange"), pay=True) + \
    order_row("月度套餐", "No.20260912088", "2026-09-12", "19.9", ("已退款", "b-gray")) + '</div>'

# ============ 9. 工单 ============
def ticket_row(subj, no, date, badge):
    return f'<div class="row"><div class="k"><div><div style="font-weight:700;font-size:15px">{subj}</div><div style="font-size:12.5px;color:var(--text3);margin-top:4px">{no} · {date}</div></div></div><div class="v"><span class="badge {badge[1]}">{badge[0]}</span><span class="chev">›</span></div></div>'

TICKETS_CONTENT = head("我的工单", actions='<button class="btn btn-primary">+ 新建工单</button>') + '<div class="card">' + \
    ticket_row("节点连接超时", "No.2026100801", "2026-10-08", ("已回复", "b-green")) + \
    ticket_row("支付结算异常", "No.2026100703", "2026-10-07", ("处理中", "b-blue")) + \
    ticket_row("账户登录失败", "No.2026100509", "2026-10-05", ("已关闭", "b-gray")) + '</div>'

# ============ 10. 邀请 ============
INVITE_CONTENT = head("邀请好友") + '<div class="col-narrow"><div class="card" style="text-align:center;padding:34px 30px">' + \
    '<div style="font-size:40px;margin-bottom:10px">🎁</div>' + \
    '<div style="font-size:19px;font-weight:800;margin-bottom:8px">邀请好友得流量</div>' + \
    '<div style="font-size:13.5px;color:var(--text2);margin-bottom:18px">每邀请 1 位好友注册并购买，您和好友各得 10 GB 流量</div>' + \
    '<div style="display:flex;gap:10px"><div class="field" style="margin:0;text-align:left;color:var(--text2);display:flex;align-items:center">https://polaris.app/i/AB12CD</div><button class="btn btn-primary" style="flex:none">复制</button></div>' + \
    '</div><div class="speed-row" style="margin-top:14px">' + \
    '<div class="speed-tile"><span><div class="lb">已邀请</div><div class="vl">3 人</div></span></div>' + \
    '<div class="speed-tile"><span><div class="lb">累计获得</div><div class="vl">30 GB</div></span></div></div>' + \
    '<div style="text-align:center;margin-top:14px"><span style="color:var(--blue);font-size:14px;font-weight:600">邀请记录 ›</span></div></div>'

# ============ 11. 礼品卡 ============
GIFTCARD_CONTENT = head("礼品卡兑换") + '<div class="col-narrow"><div class="card" style="text-align:center;padding:34px 30px">' + \
    '<div style="font-size:40px;margin-bottom:10px">💳</div>' + \
    '<div style="font-size:19px;font-weight:800;margin-bottom:8px">兑换礼品卡</div>' + \
    '<div style="font-size:13.5px;color:var(--text2);margin-bottom:18px">输入卡密，流量或时长即时到账</div>' + \
    '<input class="field" placeholder="请输入 16 位卡密" style="text-align:center">' + \
    '<button class="btn btn-primary" style="width:100%;height:48px;font-size:15px">兑换</button></div>' + \
    '<div class="section-label">兑换记录</div><div class="card">' + \
    '<div class="row"><div class="k"><span style="font-family:monospace">XXXX-XXXX-XXXX-1234</span></div><div class="v"><div style="text-align:right"><span class="badge b-green">已到账 50 GB</span><div style="font-size:12px;color:var(--text3);margin-top:4px">2024-06-12 14:23</div></div></div></div>' + \
    '<div class="row"><div class="k"><span style="font-family:monospace">XXXX-XXXX-XXXX-5678</span></div><div class="v"><div style="text-align:right"><span class="badge b-green">已到账 30 天</span><div style="font-size:12px;color:var(--text3);margin-top:4px">2024-06-10 09:05</div></div></div></div>' + \
    '</div></div>'

# ============ 12. 公告 ============
def notice_row(title, date, unread=False):
    return f'<div class="row" style="{"background:var(--blue-soft);margin:0 -24px;padding-left:24px;padding-right:24px;border-radius:8px" if unread else ""}"><div class="k"><span style="color:var(--blue);font-size:10px">●</span><div><div style="font-weight:700;font-size:15px">{title}</div><div style="font-size:12.5px;color:var(--text3);margin-top:4px">{date}</div></div></div><div class="v"><span class="chev">›</span></div></div>'

NOTICES_CONTENT = head("公告") + '<div class="card">' + \
    notice_row("v1.7.4 版本更新说明", "2026-10-09", unread=True) + \
    notice_row("香港线路维护通知", "2026-10-07") + \
    notice_row("国庆活动：年付 8 折", "2026-10-01") + '</div>'

# ============ 13. 代理模式（弹窗） ============
PROXY_OVERLAY = """<div class="overlay"><div class="dialog">
<h2>代理模式</h2><div class="dsub">选择流量的代理方式，切换即时生效</div>
<div class="opt sel"><span class="radio sel"></span><div><div style="font-weight:700">规则模式</div><div style="font-size:12.5px;color:var(--text3)">按分流规则决定直连或代理（推荐）</div></div></div>
<div class="opt"><span class="radio"></span><div><div style="font-weight:700">全局模式</div><div style="font-size:12.5px;color:var(--text3)">全部流量走代理</div></div></div>
<div class="opt"><span class="radio"></span><div><div style="font-weight:700">直连模式</div><div style="font-size:12.5px;color:var(--text3)">全部流量直连，不走代理</div></div></div>
</div></div>"""

# ============ 14. 分流规则 ============
ROUTING_CONTENT = head("分流规则", "本地规则优先于订阅规则",
    '<button class="btn btn-outline btn-sm" style="height:40px">恢复默认</button>') + """
<div class="card" style="margin-bottom:14px">
<div class="acc-head"><div class="acc-title" style="font-size:15px">国外网站分流</div><span class="badge b-blue">香港 01</span></div>
<div style="font-size:13px;color:var(--text3)">google.com · youtube.com · github.com 等 128 条</div>
</div>
<div class="card" style="margin-bottom:14px">
<div class="acc-head"><div class="acc-title" style="font-size:15px">流媒体分流</div><span class="badge b-blue">新加坡 01</span></div>
<div style="font-size:13px;color:var(--text3)">netflix.com · disneyplus.com 等 46 条</div>
</div>
<div class="card">
<div class="acc-head"><div class="acc-title" style="font-size:15px">本地直连</div><span class="badge b-gray">DIRECT</span></div>
<div style="font-size:13px;color:var(--text3)">内网 IP · 国内常用域名</div>
</div>"""

# ============ 15. 检查更新（弹窗） ============
UPDATE_OVERLAY = """<div class="overlay"><div class="dialog">
<div style="text-align:center;margin-bottom:12px"><img src="../assets/p-icon.png" style="width:56px;height:56px;border-radius:14px"></div>
<h2 style="text-align:center">发现新版本 1.7.5</h2><div class="dsub" style="text-align:center">当前版本 1.7.4 · 安装包 34.6 MB</div>
<div style="background:var(--bg);border-radius:12px;padding:14px 16px;font-size:13.5px;color:var(--text2);margin-bottom:18px;max-height:150px;overflow:hidden">更新内容：<br>1. 修复后台运行时长清零问题<br>2. 速率显示更加平滑<br>3. 优化节点测速逻辑</div>
<button class="btn btn-primary" style="width:100%;height:46px;margin-bottom:10px">立即更新</button>
<button class="btn" style="width:100%;height:44px;color:var(--text2);background:transparent">稍后再说</button>
</div></div>"""

LOGOUT_OVERLAY = """<div class="overlay"><div class="dialog">
<h2>退出登录</h2><div class="dsub">确定要退出当前账号吗？</div>
<button class="btn btn-danger" style="width:100%;height:46px;margin-bottom:10px">退出登录</button>
<button class="btn" style="width:100%;height:44px;color:var(--text2);background:transparent">取消</button>
</div></div>"""

# ============ 16. 托盘菜单 ============
TRAY_HTML = """<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8">
<link rel="stylesheet" href="../css/design.css"></head><body style="display:block">
<div class="tray-wrap">
  <div style="position:absolute;top:60px;left:80px;color:#fff"><div style="font-size:22px;font-weight:800">Polaris Windows 设计稿 · 托盘菜单</div><div style="font-size:14px;opacity:.8">右下角任务栏托盘图标右键菜单</div></div>
  <div class="tray-menu">
    <div class="tray-head"><img src="../assets/p-icon.png"><div><div style="font-size:14px;font-weight:700">Polaris</div><div style="font-size:12px;color:var(--green)">● 已连接 · 香港 01</div></div></div>
    <div class="tray-sep"></div>
    <div class="tray-item">🔌 断开连接</div>
    <div class="tray-item">🚀 节点选择 <span style="margin-left:auto;color:var(--text3)">香港 01 ›</span></div>
    <div class="tray-item">📊 打开主界面</div>
    <div class="tray-sep"></div>
    <div class="tray-item">⚙️ 设置</div>
    <div class="tray-item">🔄 检查更新</div>
    <div class="tray-sep"></div>
    <div class="tray-item">❌ 退出</div>
  </div>
  <div class="taskbar"><span style="font-size:13px;color:#6e6e73">任务栏示意 · 托盘区 ▲ 🔊 📶 🔋</span></div>
</div></body></html>"""

# ---- 生成 ----
page("home", "home", HOME_CONTENT)
page("nodes", "nodes", NODES_CONTENT)
page("traffic", "traffic", TRAFFIC_CONTENT)
page("settings", "settings", SETTINGS_CONTENT)
page("me", "me", ME_CONTENT, overlay=LOGOUT_OVERLAY)
open(os.path.join(PAGES, "login.html"), "w", encoding="utf-8").write(LOGIN_HTML)
page("plans", "me", PLANS_CONTENT)
page("orders", "me", ORDERS_CONTENT)
page("tickets", "me", TICKETS_CONTENT)
page("invite", "me", INVITE_CONTENT)
page("giftcard", "me", GIFTCARD_CONTENT)
page("notices", "me", NOTICES_CONTENT)
page("proxy", "home", HOME_CONTENT, overlay=PROXY_OVERLAY)
page("routing", "nodes", ROUTING_CONTENT)
page("update", "settings", SETTINGS_CONTENT, overlay=UPDATE_OVERLAY)
open(os.path.join(PAGES, "tray.html"), "w", encoding="utf-8").write(TRAY_HTML)
print("pages:", sorted(os.listdir(PAGES)))
