/* 16 个页面视图。纯函数：(state) -> HTML 字符串，事件在 app.js 里绑定。 */
(function () {
  const h = (s) => String(s == null ? "" : s).replace(/&/g, "&amp;").replace(/</g, "&lt;");

  function head(title, sub, actions) {
    return `<div class="page-head"><div><div class="page-title">${title}</div>` +
      (sub ? `<div class="page-sub">${sub}</div>` : "") +
      `</div><div class="head-actions">${actions || ""}</div></div>`;
  }
  function row(k, v, opts) {
    opts = opts || {};
    const ic = opts.icon ? `<span class="mini-icon" style="background:${opts.icon[1]}">${opts.icon[0]}</span>` : "";
    const chev = opts.chev === false ? "" : '<span class="chev">›</span>';
    return `<div class="row"${opts.id ? ` id="${opts.id}"` : ""}${opts.click ? ` data-click="${opts.click}"` : ""} style="${opts.click ? "cursor:pointer" : ""}"><div class="k">${ic}<span>${k}</span></div><div class="v ${opts.vcls || ""}"><span>${v}</span>${chev}</div></div>`;
  }
  function badge(text, cls) { return `<span class="badge ${cls}">${text}</span>`; }
  function latBadge(ms) {
    if (ms < 0) return badge("未测", "b-gray");
    return badge(ms + "ms", ms <= 100 ? "b-green" : "b-orange");
  }

  const Views = {};

  /* ---------- 首页 ---------- */
  Views.home = (s) => `
  <div class="col-narrow">
    <div class="hero">
      <button class="power-btn" id="power" title="${s.connected ? "断开" : "连接"}">
        <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.4" stroke-linecap="round"><path d="M12 3v9"/><path d="M6.3 6.5a8 8 0 1 0 11.4 0"/></svg>
      </button>
      <div class="status-line">${s.connected ? '<span class="dot"></span>' : ""}<span class="t">${s.connected ? "已连接" : "未连接"}</span></div>
      <div class="node-line">${s.connected ? h(s.node) + " · " + s.latency + "ms" : "点击上方按钮开始连接"}</div>
    </div>
    <div class="speed-row">
      <div class="speed-tile up"><span class="ic"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#1a73e8" stroke-width="2.4" stroke-linecap="round"><path d="M12 4v14"/><path d="M6 12l6 6 6-6"/></svg></span><span><div class="lb">下载</div><div class="vl" id="down-speed">${s.down_speed} MB/s</div></span></div>
      <div class="speed-tile dn"><span class="ic"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#e07b00" stroke-width="2.4" stroke-linecap="round"><path d="M12 20V6"/><path d="M6 12l6-6 6 6"/></svg></span><span><div class="lb">上传</div><div class="vl" id="up-speed">${s.up_speed} MB/s</div></span></div>
    </div>
    <div class="section-label">会话</div>
    <div class="card">
      ${row("代理模式", h(s.mode), { click: "proxy-mode" })}
      ${row("本次上传", h(s.up_total), { chev: false, vcls: "strong" })}
      ${row("本次下载", h(s.down_total), { chev: false, vcls: "strong" })}
      ${row("运行时间", h(s.uptime), { chev: false, vcls: "strong" })}
    </div>
    <div class="section-label">当前套餐</div>
    <div class="card">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px">
        <div style="font-size:14px">已使用 <b>${s.plan.used} GB</b> / ${s.plan.total} GB</div>
        <button class="btn btn-primary btn-sm" data-nav="plans">续费</button>
      </div>
      <div class="progress"><div style="width:${Math.round(s.plan.used / s.plan.total * 100)}%"></div></div>
      <div style="font-size:13px;color:var(--text3);margin-top:10px">到期时间 ${s.plan.expire}</div>
    </div>
  </div>`;

  /* ---------- 节点 ---------- */
  Views.nodes = (s) => {
    const q = (s.nodeFilter || "").toLowerCase();
    const list = s.nodes.filter((n) => !q || n.name.toLowerCase().includes(q) || n.region.includes(s.nodeFilter || ""));
    const rows = list.map((n) => `
      <div class="node-row" data-node="${h(n.name)}" style="cursor:pointer">
        <span class="nm">${h(n.name)}</span>${badge(h(n.region), "b-blue")}${latBadge(n.latency)}
        <span class="radio${n.name === s.node ? " sel" : ""}"></span>
      </div>`).join("");
    return head("节点", `${s.nodes.length} 个节点 · 5 个分组`,
      `<span class="search-wrap"><input class="search" id="node-search" placeholder="搜索节点" value="${h(s.nodeFilter || "")}"></span>` +
      `<button class="btn btn-outline btn-sm" style="height:40px" id="btn-speedtest">${s.testing ? "测速中…" : "测速"}</button>` +
      `<button class="btn btn-primary btn-sm" style="height:40px" id="btn-refresh-sub">${s.refreshing ? "刷新中…" : "刷新订阅"}</button>`) + `
    <div class="card" style="margin-bottom:14px">
      <div class="acc-head"><div><div class="acc-title">节点选择</div><div class="acc-sub">当前：${h(s.node)}</div></div><span class="chev" style="font-size:20px">⌃</span></div>
      ${rows || '<div style="padding:20px;text-align:center;color:var(--text3);font-size:14px">没有匹配的节点</div>'}
    </div>
    <div class="card" style="margin-bottom:14px"><div class="acc-head" style="padding:0"><div class="acc-title" style="font-size:15px">国外网站分流</div><span class="chev" style="font-size:20px">⌄</span></div></div>
    <div class="card" data-nav="routing" style="cursor:pointer"><div class="acc-head" style="padding:0"><div class="acc-title" style="font-size:15px">流媒体分流</div><span class="chev" style="font-size:20px">›</span></div></div>`;
  };

  /* ---------- 流量 ---------- */
  Views.traffic = (s) => {
    const t = s.traffic, r = s.trafficRange;
    const seg = (k, label) => `<button class="btn ${r === k ? "btn-primary" : "btn-ghost"} btn-sm" style="height:40px" data-range="${k}">${label}</button>`;
    return head("流量", "", seg("today", "今日") + seg("week", "本周") + seg("month", "本月")) + `
    <div class="card" style="margin-bottom:14px">
      <div style="display:flex;justify-content:space-between;margin-bottom:8px"><b style="font-size:15px">网络速度 · ${r === "today" ? "24小时" : r === "week" ? "7天" : "30天"}</b><span style="font-size:13px;color:var(--text2)"><span style="color:var(--blue)">●</span> 下载　<span style="color:var(--orange)">●</span> 上传</span></div>
      <svg viewBox="0 0 640 220" width="100%" height="220">
        <defs><linearGradient id="g1" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1a73e8" stop-opacity=".35"/><stop offset="1" stop-color="#1a73e8" stop-opacity=".04"/></linearGradient></defs>
        <g stroke="#ececf0"><line x1="40" y1="20" x2="40" y2="180"/><line x1="40" y1="180" x2="630" y2="180"/></g>
        <path d="M40,150 C120,140 170,60 260,55 C350,50 400,110 480,120 C550,128 590,90 630,95 L630,180 L40,180 Z" fill="url(#g1)"/>
        <path d="M40,150 C120,140 170,60 260,55 C350,50 400,110 480,120 C550,128 590,90 630,95" fill="none" stroke="#1a73e8" stroke-width="2.5"/>
        <path d="M40,165 C140,160 220,120 320,115 C420,110 500,140 630,110" fill="none" stroke="#ff9500" stroke-width="2.5"/>
        <g font-size="11" fill="#8e8e93"><text x="40" y="198">00:00</text><text x="150" y="198">04:00</text><text x="265" y="198">08:00</text><text x="380" y="198">12:00</text><text x="490" y="198">16:00</text><text x="590" y="198">20:00</text></g>
      </svg>
      <div style="font-size:13px;color:var(--text2);margin-top:4px">当前下载 ${s.down_speed} MB/s · 当前上传 ${s.up_speed} MB/s</div>
    </div>
    <div class="speed-row" style="margin:0 0 4px">
      <div class="speed-tile up"><span><div class="lb">今日下载</div><div class="vl">${h(t.down_today)}</div></span></div>
      <div class="speed-tile dn"><span><div class="lb">今日上传</div><div class="vl">${h(t.up_today)}</div></span></div>
    </div>
    <div class="section-label">明细</div>
    <div class="card">
      ${row("总下载", h(t.total_down), { chev: false, vcls: "strong" })}
      ${row("总上传", h(t.total_up), { chev: false, vcls: "strong" })}
      ${row("峰值速率", h(t.peak), { chev: false, vcls: "strong" })}
      ${row("在线节点", h(t.online_nodes), { chev: false, vcls: "strong" })}
    </div>`;
  };

  /* ---------- 设置 ---------- */
  Views.settings = (s) => head("设置") + `<div class="col-narrow">
    <div class="section-label">通用</div><div class="card">
      ${row("外观", "跟随系统", { icon: ["◐", "#7aa5f8"] })}
      ${row("语言", "简体中文", { icon: ["文", "#34c759"] })}
      ${row("TUN 堆栈", h(s.settings.tun), { icon: ["≋", "#af8cf8"] })}
      ${row("修改密码", "", { icon: ["⚿", "#ffb340"] })}
    </div>
    <div class="section-label">提醒</div><div class="card">
      <div class="row"><div class="k"><span>到期提醒</span></div><div class="switch${s.settings.expire_notify ? " on" : ""}" data-setting="expire_notify"></div></div>
      <div class="row"><div class="k"><span>流量提醒</span></div><div class="switch${s.settings.traffic_notify ? " on" : ""}" data-setting="traffic_notify"></div></div>
      <div class="row"><div class="k"><span>开机自启动</span></div><div class="switch${s.settings.autostart ? " on" : ""}" data-setting="autostart"></div></div>
    </div>
    <div class="section-label">关于</div><div class="card">
      ${row("当前版本", h(s.settings.version), { chev: false })}
      ${row("检查更新", '<span style="color:var(--blue)">检查更新</span>', { chev: false, click: "check-update" })}
      ${row("开源许可", "")}
      ${row("隐私政策", "")}
    </div></div>`;

  /* ---------- 我的 ---------- */
  Views.me = (s) => head("我的") + `<div class="col-narrow">
    <div class="card" style="margin-bottom:14px"><div style="display:flex;align-items:center;gap:14px">
      <div style="width:52px;height:52px;border-radius:50%;background:#e8e8ed;display:flex;align-items:center;justify-content:center;font-size:22px;color:#8e8e93">👤</div>
      <div><div style="font-size:16px;font-weight:700">${h(s.email)}</div><span class="badge b-blue" style="margin-top:6px;display:inline-block">${h(s.plan.name)}</span></div>
    </div></div>
    <div class="card" style="margin-bottom:14px">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px"><div style="font-size:14px;font-weight:700">当前套餐</div><button class="btn btn-primary btn-sm" data-nav="plans">续费</button></div>
      <div style="font-size:14px;margin-bottom:10px">已使用 <b>${s.plan.used} GB</b> / ${s.plan.total} GB</div>
      <div class="progress"><div style="width:${Math.round(s.plan.used / s.plan.total * 100)}%"></div></div>
      <div style="font-size:13px;color:var(--text3);margin-top:10px">到期时间 ${s.plan.expire}</div>
    </div>
    <div class="card">
      ${row("我的订单", "", { icon: ["🧾", "#ffb340"], click: "nav-orders" })}
      ${row("我的工单", "", { icon: ["🎫", "#7aa5f8"], click: "nav-tickets" })}
      ${row("邀请好友", "", { icon: ["🎁", "#34c759"], click: "nav-invite" })}
      ${row("礼品卡兑换", "", { icon: ["💳", "#af8cf8"], click: "nav-giftcard" })}
      ${row("公告", s.unreadNotices ? badge(s.unreadNotices + " 条未读", "b-red") : "", { icon: ["📢", "#ff9f43"], click: "nav-notices" })}
      ${row("关于", "", { icon: ["ℹ️", "#8e8e93"], click: "nav-settings" })}
      <div class="row" style="justify-content:center;cursor:pointer" data-click="logout"><span style="color:var(--red);font-weight:600">退出登录</span></div>
    </div></div>`;

  /* ---------- 登录 ---------- */
  Views.login = () => `
  <div class="login-wrap" style="width:100%;height:100%;border:0;border-radius:0;box-shadow:none">
    <div class="login-body"><div class="login-card">
      <img class="app-icon" src="assets/p-icon.png" alt="">
      <h1>欢迎回来</h1><div class="sub">登录以同步您的套餐与节点</div>
      <input class="field" id="login-panel" placeholder="面板地址 https://">
      <input class="field" id="login-email" placeholder="邮箱">
      <input class="field" id="login-pass" type="password" placeholder="密码">
      <div id="login-err" style="color:var(--red);font-size:13px;min-height:20px;margin-bottom:8px"></div>
      <button class="btn btn-primary" id="btn-login" style="width:100%;height:48px;font-size:15px">登录</button>
      <div style="font-size:13px;color:var(--text3);margin-top:16px">还没有账号？<span style="color:var(--blue);font-weight:600">去注册</span></div>
    </div></div>
  </div>`;

  /* ---------- 购买套餐 ---------- */
  Views.plans = (s) => head("购买套餐") + `<div class="plan-grid">` +
    s.plans.map((p) => `
    <div class="plan-card${p.hot ? " hot" : ""}">
      ${p.hot ? '<div class="ribbon">最受欢迎</div>' : ""}
      <h3>${h(p.name)}</h3><div class="price">¥${h(p.price)}<small>/${h(p.unit)}</small></div>
      <ul>${p.feats.map((f) => `<li>${h(f)}</li>`).join("")}</ul>
      <button class="btn ${p.hot ? "btn-primary" : "btn-outline"}" data-buy="${p.id}">立即购买</button>
    </div>`).join("") +
    `</div><div style="text-align:center;font-size:13px;color:var(--text3);margin-top:18px">支持支付宝 / 微信支付 · 购买后即时生效</div>`;

  /* ---------- 订单 ---------- */
  const ORDER_STATUS = { done: ["已完成", "b-green"], pending: ["待支付", "b-orange"], refunded: ["已退款", "b-gray"] };
  Views.orders = (s) => head("我的订单") + `<div class="card">` +
    s.orders.map((o) => {
      const st = ORDER_STATUS[o.status];
      return `<div class="row"><div class="k"><div><div style="font-weight:700;font-size:15px">${h(o.name)}</div><div style="font-size:12.5px;color:var(--text3);margin-top:4px">${h(o.no)} · ${h(o.date)}</div></div></div><div class="v strong"><span style="font-size:19px">¥${h(o.amount)}</span>${badge(st[0], st[1])}${o.status === "pending" ? `<button class="btn btn-primary btn-sm" style="margin-left:8px" data-pay="${h(o.no)}">去支付</button>` : ""}</div></div>`;
    }).join("") + `</div>`;

  /* ---------- 工单 ---------- */
  const TICKET_STATUS = { replied: ["已回复", "b-green"], pending: ["处理中", "b-blue"], closed: ["已关闭", "b-gray"] };
  Views.tickets = (s) => head("我的工单", "", `<button class="btn btn-primary" id="btn-new-ticket">+ 新建工单</button>`) + `<div class="card">` +
    s.tickets.map((t) => {
      const st = TICKET_STATUS[t.status];
      return `<div class="row"><div class="k"><div><div style="font-weight:700;font-size:15px">${h(t.subject)}</div><div style="font-size:12.5px;color:var(--text3);margin-top:4px">${h(t.no)} · ${h(t.date)}</div></div></div><div class="v">${badge(st[0], st[1])}<span class="chev">›</span></div></div>`;
    }).join("") + `</div>`;

  /* ---------- 邀请 ---------- */
  Views.invite = (s) => head("邀请好友") + `<div class="col-narrow"><div class="card" style="text-align:center;padding:34px 30px">
    <div style="font-size:40px;margin-bottom:10px">🎁</div>
    <div style="font-size:19px;font-weight:800;margin-bottom:8px">邀请好友得流量</div>
    <div style="font-size:13.5px;color:var(--text2);margin-bottom:18px">每邀请 1 位好友注册并购买，您和好友各得 10 GB 流量</div>
    <div style="display:flex;gap:10px"><div class="field" style="margin:0;text-align:left;color:var(--text2);display:flex;align-items:center">${h(s.invite.link)}</div><button class="btn btn-primary" style="flex:none" id="btn-copy-invite">复制</button></div>
  </div>
  <div class="speed-row" style="margin-top:14px">
    <div class="speed-tile"><span><div class="lb">已邀请</div><div class="vl">${s.invite.invited} 人</div></span></div>
    <div class="speed-tile"><span><div class="lb">累计获得</div><div class="vl">${h(s.invite.earned)}</div></span></div>
  </div>
  <div style="text-align:center;margin-top:14px"><span style="color:var(--blue);font-size:14px;font-weight:600">邀请记录 ›</span></div></div>`;

  /* ---------- 礼品卡 ---------- */
  Views.giftcard = (s) => head("礼品卡兑换") + `<div class="col-narrow"><div class="card" style="text-align:center;padding:34px 30px">
    <div style="font-size:40px;margin-bottom:10px">💳</div>
    <div style="font-size:19px;font-weight:800;margin-bottom:8px">兑换礼品卡</div>
    <div style="font-size:13.5px;color:var(--text2);margin-bottom:18px">输入卡密，流量或时长即时到账</div>
    <input class="field" id="gift-code" placeholder="请输入 16 位卡密" style="text-align:center">
    <div id="gift-err" style="color:var(--red);font-size:13px;min-height:20px;margin-bottom:8px"></div>
    <button class="btn btn-primary" id="btn-redeem" style="width:100%;height:48px;font-size:15px">兑换</button>
  </div>
  <div class="section-label">兑换记录</div><div class="card">` +
    s.giftHistory.map((g) => `
    <div class="row"><div class="k"><span style="font-family:monospace">${h(g.code)}</span></div><div class="v"><div style="text-align:right">${badge(h(g.reward), "b-green")}<div style="font-size:12px;color:var(--text3);margin-top:4px">${h(g.date)}</div></div></div></div>`).join("") +
  `</div></div>`;

  /* ---------- 公告 ---------- */
  Views.notices = (s) => head("公告") + `<div class="card">` +
    s.notices.map((n, i) => `
    <div class="row" data-notice="${i}" style="cursor:pointer;${n.unread ? "background:var(--blue-soft);margin:0 -24px;padding-left:24px;padding-right:24px" : ""}">
      <div class="k"><span style="color:var(--blue);font-size:10px">●</span><div><div style="font-weight:700;font-size:15px">${h(n.title)}</div><div style="font-size:12.5px;color:var(--text3);margin-top:4px">${h(n.date)}</div></div></div>
      <div class="v"><span class="chev">›</span></div>
    </div>`).join("") + `</div>`;

  /* ---------- 分流规则 ---------- */
  Views.routing = (s) => head("分流规则", "本地规则优先于订阅规则",
    `<button class="btn btn-outline btn-sm" style="height:40px" id="btn-routing-reset">恢复默认</button>`) + `
    <div class="card" style="margin-bottom:14px">
      <div class="acc-head"><div class="acc-title" style="font-size:15px">国外网站分流</div>${badge("香港 01", "b-blue")}</div>
      <div style="font-size:13px;color:var(--text3)">google.com · youtube.com · github.com 等 128 条</div>
    </div>
    <div class="card" style="margin-bottom:14px">
      <div class="acc-head"><div class="acc-title" style="font-size:15px">流媒体分流</div>${badge("新加坡 01", "b-blue")}</div>
      <div style="font-size:13px;color:var(--text3)">netflix.com · disneyplus.com 等 46 条</div>
    </div>
    <div class="card">
      <div class="acc-head"><div class="acc-title" style="font-size:15px">本地直连</div>${badge("DIRECT", "b-gray")}</div>
      <div style="font-size:13px;color:var(--text3)">内网 IP · 国内常用域名</div>
    </div>`;

  /* ---------- 弹窗 ---------- */
  const Dialogs = {
    proxy: (s) => `
      <div class="overlay" data-overlay><div class="dialog">
        <h2>代理模式</h2><div class="dsub">选择流量的代理方式，切换即时生效</div>
        ${[["rule", "规则模式", "按分流规则决定直连或代理（推荐）"], ["global", "全局模式", "全部流量走代理"], ["direct", "直连模式", "全部流量直连，不走代理"]].map(([k, t, d]) => `
        <div class="opt${(s.mode === t || (k === "rule" && s.mode === "规则模式")) ? " sel" : ""}" data-mode="${t}">
          <span class="radio${(s.mode === t || (k === "rule" && s.mode === "规则模式")) ? " sel" : ""}"></span>
          <div><div style="font-weight:700">${t}</div><div style="font-size:12.5px;color:var(--text3)">${d}</div></div>
        </div>`).join("")}
      </div></div>`,
    update: (u) => `
      <div class="overlay" data-overlay><div class="dialog">
        <div style="text-align:center;margin-bottom:12px"><img src="assets/p-icon.png" style="width:56px;height:56px;border-radius:14px"></div>
        <h2 style="text-align:center">发现新版本 ${h(u.version)}</h2>
        <div class="dsub" style="text-align:center">当前版本 ${h(u.current)} · 安装包 ${h(u.size)}</div>
        <div style="background:var(--bg);border-radius:12px;padding:14px 16px;font-size:13.5px;color:var(--text2);margin-bottom:18px;white-space:pre-line">更新内容：\n${h(u.notes)}</div>
        <div id="update-body"><button class="btn btn-primary" id="btn-do-update" style="width:100%;height:46px;margin-bottom:10px">立即更新</button>
        <button class="btn" style="width:100%;height:44px;color:var(--text2);background:transparent" data-overlay-close>稍后再说</button></div>
      </div></div>`,
    newTicket: () => `
      <div class="overlay" data-overlay><div class="dialog">
        <h2>新建工单</h2><div class="dsub">描述您遇到的问题，客服将尽快回复</div>
        <input class="field" id="ticket-subject" placeholder="标题（例如：节点连接超时）">
        <textarea class="field" id="ticket-content" placeholder="问题描述…" style="height:110px;padding-top:14px;resize:none"></textarea>
        <button class="btn btn-primary" id="btn-submit-ticket" style="width:100%;height:46px">提交</button>
      </div></div>`,
    notice: (n) => `
      <div class="overlay" data-overlay><div class="dialog">
        <h2>${h(n.title)}</h2><div class="dsub">${h(n.date)}</div>
        <div style="font-size:14px;color:var(--text2);white-space:pre-line;line-height:1.7">${h(n.body || "")}</div>
        <button class="btn btn-primary" data-overlay-close style="width:100%;height:44px;margin-top:20px">知道了</button>
      </div></div>`,
  };

  window.PolarisViews = Views;
  window.PolarisDialogs = Dialogs;
})();
