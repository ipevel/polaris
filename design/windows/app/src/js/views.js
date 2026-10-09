/* 页面视图与弹窗。纯函数：(state) -> HTML 字符串；事件在 app.js 里绑定。
 * 所有插值都过 h() 转义 —— 节点名、公告正文、工单标题都来自面板，属不可信输入。 */
(function () {
  const h = window.PolarisFormat.escape;
  const fmt = window.PolarisFormat;

  function head(title, sub, actions) {
    return `<div class="page-head"><div><div class="page-title">${h(title)}</div>` +
      (sub ? `<div class="page-sub">${h(sub)}</div>` : "") +
      `</div><div class="head-actions">${actions || ""}</div></div>`;
  }

  function row(k, v, opts) {
    opts = opts || {};
    const ic = opts.icon ? `<span class="mini-icon" style="background:${opts.icon[1]}">${opts.icon[0]}</span>` : "";
    const chev = opts.chev === false ? "" : '<span class="chev">›</span>';
    return `<div class="row"${opts.id ? ` id="${opts.id}"` : ""}${opts.click ? ` data-click="${opts.click}"` : ""} style="${opts.click ? "cursor:pointer" : ""}">` +
      `<div class="k">${ic}<span>${k}</span></div>` +
      `<div class="v ${opts.vcls || ""}"><span>${v}</span>${chev}</div></div>`;
  }

  function rowSwitch(label, key, on, icon) {
    const ic = icon ? `<span class="mini-icon" style="background:${icon[1]}">${icon[0]}</span>` : "";
    return `<div class="row"><div class="k">${ic}<span>${h(label)}</span></div>` +
      `<div class="switch${on ? " on" : ""}" data-setting="${key}"></div></div>`;
  }

  function badge(text, cls) { return `<span class="badge ${cls}">${h(text)}</span>`; }

  function latBadge(ms, offline) {
    if (offline) return badge("离线", "b-red");
    if (ms < 0) return badge("未测", "b-gray");
    return badge(ms + "ms", ms <= 100 ? "b-green" : ms <= 250 ? "b-orange" : "b-red");
  }

  function empty(text, hint) {
    return `<div class="empty"><div class="empty-t">${h(text)}</div>${hint ? `<div class="empty-s">${h(hint)}</div>` : ""}</div>`;
  }

  function err(text, id) {
    return `<div class="inline-err"${id ? ` id="${id}"` : ""}>${h(text || "")}</div>`;
  }

  const Views = {};

  /* ---------------- 首页 ---------------- */
  Views.home = (s) => `
  <div class="col-narrow">
    <div class="hero">
      <button class="power-btn${s.busy ? " busy" : ""}" id="power" title="${s.connected ? "断开" : "连接"}"${s.busy ? " disabled" : ""}>
        <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.4" stroke-linecap="round"><path d="M12 3v9"/><path d="M6.3 6.5a8 8 0 1 0 11.4 0"/></svg>
      </button>
      <div class="status-line">${s.connected ? '<span class="dot"></span>' : ""}<span class="t">${s.busy ? "连接中…" : s.connected ? "已连接" : "未连接"}</span></div>
      <div class="node-line">${s.connected ? h(s.node || "未选择节点") + (s.latency ? " · " + s.latency + "ms" : "") : (s.loggedIn ? "点击上方按钮开始连接" : "请先登录面板")}</div>
    </div>
    <div class="speed-row">
      <div class="speed-tile up"><span class="ic"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#1a73e8" stroke-width="2.4" stroke-linecap="round"><path d="M12 4v14"/><path d="M6 12l6 6 6-6"/></svg></span><span><div class="lb">下载</div><div class="vl" id="down-speed">${fmt.speed(s.down_speed)}</div></span></div>
      <div class="speed-tile dn"><span class="ic"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#e07b00" stroke-width="2.4" stroke-linecap="round"><path d="M12 20V6"/><path d="M6 12l6-6 6 6"/></svg></span><span><div class="lb">上传</div><div class="vl" id="up-speed">${fmt.speed(s.up_speed)}</div></span></div>
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
        <div style="font-size:14px">已使用 <b>${h(s.plan.used)} GB</b> / ${h(s.plan.total)} GB</div>
        <button class="btn btn-primary btn-sm" data-nav="plans">续费</button>
      </div>
      <div class="progress"><div style="width:${fmt.percent(s.plan.used, s.plan.total)}%"></div></div>
      <div style="font-size:13px;color:var(--text3);margin-top:10px">到期时间 ${h(s.plan.expire || "—")}</div>
    </div>
  </div>`;

  /* ---------------- 节点 ---------------- */
  Views.nodes = (s) => {
    const q = (s.nodeFilter || "").toLowerCase();
    const list = s.nodes.filter((n) => !q || n.name.toLowerCase().includes(q) || String(n.region).toLowerCase().includes(q));
    const rows = list.map((n) => `
      <div class="node-row" data-node="${h(n.name)}" style="cursor:pointer">
        <span class="nm">${h(n.name)}</span>${badge(n.region, "b-blue")}${latBadge(n.latency, n.offline)}
        <span class="radio${n.name === s.node ? " sel" : ""}"></span>
      </div>`).join("");
    return head("节点", `${s.nodes.length} 个节点${s.groups.length > 1 ? " · " + s.groups.length + " 个分组" : ""}`,
      `<span class="search-wrap"><input class="search" id="node-search" placeholder="搜索节点" value="${h(s.nodeFilter)}"></span>` +
      `<button class="btn btn-outline btn-sm" style="height:40px" id="btn-speedtest"${s.testing ? " disabled" : ""}>${s.testing ? "测速中…" : "测速"}</button>` +
      `<button class="btn btn-primary btn-sm" style="height:40px" id="btn-refresh-sub"${s.refreshing ? " disabled" : ""}>${s.refreshing ? "刷新中…" : "刷新订阅"}</button>`) + `
    <div class="card" style="margin-bottom:14px">
      <div class="acc-head"><div><div class="acc-title">节点选择</div><div class="acc-sub">当前：${h(s.node || "未选择")}</div></div></div>
      ${rows || empty("没有匹配的节点", s.nodes.length ? "换个关键词试试" : "点右上角刷新订阅")}
    </div>
    ${s.groups.filter((g) => !g.builtin).map((g) => `
    <div class="card" style="margin-bottom:14px;cursor:pointer" data-group="${h(g.name)}">
      <div class="acc-head" style="padding:0"><div><div class="acc-title" style="font-size:15px">${h(g.name)}</div>
      <div class="acc-sub">${g.count ? g.count + " 个可选出口 · " : ""}当前：${h(g.now || "—")}</div></div><span class="chev" style="font-size:20px">›</span></div>
    </div>`).join("")}`;
  };

  /* ---------------- 流量 ---------------- */
  Views.traffic = (s) => {
    const t = s.traffic || {};
    const r = s.trafficRange;
    const seg = (k, label) => `<button class="btn ${r === k ? "btn-primary" : "btn-ghost"} btn-sm" style="height:40px" data-range="${k}">${label}</button>`;
    const pts = (s.series && s.series.points) || [];
    const max = Math.max(1, ...pts.map((p) => Math.max(p.down, p.up)));
    const W = 620, H = 170, PAD = 34;
    const x = (i) => PAD + (i / Math.max(1, pts.length - 1)) * (W - PAD - 10);
    const y = (v) => H - 20 - (v / max) * (H - 44);
    const line = (key) => pts.map((p, i) => (i ? "L" : "M") + x(i).toFixed(1) + "," + y(p[key]).toFixed(1)).join(" ");
    const area = pts.length ? `${line("down")} L${x(pts.length - 1).toFixed(1)},${H - 20} L${x(0).toFixed(1)},${H - 20} Z` : "";
    const ticks = pts.filter((_, i) => pts.length <= 8 || i % Math.ceil(pts.length / 6) === 0);

    return head("流量", "", seg("today", "今日") + seg("week", "本周") + seg("month", "本月")) + `
    <div class="card" style="margin-bottom:14px">
      <div style="display:flex;justify-content:space-between;margin-bottom:8px"><b style="font-size:15px">网络速度 · ${r === "today" ? "24 小时" : r === "week" ? "7 天" : "30 天"}</b>
      <span style="font-size:13px;color:var(--text2)"><span style="color:var(--blue)">●</span> 下载　<span style="color:var(--orange)">●</span> 上传</span></div>
      ${pts.length ? `<svg viewBox="0 0 ${W} ${H}" width="100%" height="${H}">
        <defs><linearGradient id="g1" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1a73e8" stop-opacity=".32"/><stop offset="1" stop-color="#1a73e8" stop-opacity=".03"/></linearGradient></defs>
        <g stroke="#ececf0"><line x1="${PAD}" y1="20" x2="${PAD}" y2="${H - 20}"/><line x1="${PAD}" y1="${H - 20}" x2="${W - 6}" y2="${H - 20}"/></g>
        <path d="${area}" fill="url(#g1)"/>
        <path d="${line("down")}" fill="none" stroke="#1a73e8" stroke-width="2.5" stroke-linejoin="round"/>
        <path d="${line("up")}" fill="none" stroke="#ff9500" stroke-width="2.2" stroke-linejoin="round"/>
        <g font-size="11" fill="#8e8e93">${ticks.map((p) => {
          const i = pts.indexOf(p);
          return `<text x="${x(i).toFixed(1)}" y="${H - 5}" text-anchor="middle">${h(p.label)}</text>`;
        }).join("")}</g>
        <g font-size="11" fill="#8e8e93"><text x="4" y="26">${h(s.series.unit)}</text><text x="4" y="${H - 22}">0</text></g>
      </svg>` : empty("暂无流量记录", "连接后开始统计")}
      <div style="font-size:13px;color:var(--text2);margin-top:4px">当前下载 ${fmt.speed(s.down_speed)} · 当前上传 ${fmt.speed(s.up_speed)}</div>
    </div>
    <div class="speed-row" style="margin:0 0 4px">
      <div class="speed-tile up"><span><div class="lb">本次下载</div><div class="vl">${h(t.down_today || "0 B")}</div></span></div>
      <div class="speed-tile dn"><span><div class="lb">本次上传</div><div class="vl">${h(t.up_today || "0 B")}</div></span></div>
    </div>
    <div class="section-label">面板累计</div>
    <div class="card">
      ${row("总下载", h(t.total_down || "—"), { chev: false, vcls: "strong" })}
      ${row("总上传", h(t.total_up || "—"), { chev: false, vcls: "strong" })}
      ${row("峰值速率", h(t.peak || "—"), { chev: false, vcls: "strong" })}
      ${row("在线节点", String(t.online_nodes || 0), { chev: false, vcls: "strong" })}
    </div>
    ${s.trafficLog && s.trafficLog.length ? `<div class="section-label">面板流量明细</div><div class="card">` +
      s.trafficLog.map((r2) => row(h(r2.date), fmt.bytes((r2.upload || 0) + (r2.download || 0)), { chev: false })).join("") + `</div>` : ""}`;
  };

  /* ---------------- 分流规则 ---------------- */
  Views.routing = (s) => head("分流规则", "本地规则优先于订阅规则",
    `<button class="btn btn-outline btn-sm" style="height:40px" id="btn-routing-reset">恢复默认</button>`) +
    (s.groups.length
      ? s.groups.map((g) => `
      <div class="card" style="margin-bottom:14px;${g.builtin ? "" : "cursor:pointer"}"${g.builtin ? "" : ` data-group="${h(g.name)}"`}>
        <div class="acc-head" style="padding:0">
          <div><div class="acc-title" style="font-size:15px">${h(g.name)}</div>
          <div class="acc-sub">${g.count ? g.count + " 个可选出口 · " : ""}${h(g.type)}</div></div>
          <div style="display:flex;align-items:center;gap:8px">${badge(g.now || "—", g.now === "DIRECT" || g.now === "REJECT" ? "b-gray" : "b-blue")}${g.builtin ? "" : '<span class="chev" style="font-size:20px">›</span>'}</div>
        </div>
      </div>`).join("")
      : empty("暂无分流分组", "订阅里没有 proxy-groups，或尚未连接"));

  /* ---------------- 设置 ---------------- */
  const LANGS = { "zh-CN": "简体中文", "zh-TW": "繁體中文", "en-US": "English" };
  const THEMES = { system: "跟随系统", light: "浅色", dark: "深色" };
  const TUN_STACKS = { gvisor: "gvisor（兼容性最好）", system: "system（性能更好）", mixed: "mixed" };

  Views.settings = (s) => head("设置") + `<div class="col-narrow">
    <div class="section-label">通用</div><div class="card">
      ${row("外观", h(THEMES[s.settings.theme] || s.settings.theme), { icon: ["◐", "#7aa5f8"], click: "set-theme" })}
      ${row("语言", h(LANGS[s.settings.lang] || s.settings.lang), { icon: ["文", "#34c759"], click: "set-lang" })}
      ${row("修改密码", "", { icon: ["⚿", "#ffb340"], click: "change-password" })}
    </div>
    <div class="section-label">连接</div><div class="card">
      ${rowSwitch("系统代理", "sys_proxy", s.settings.sys_proxy, ["⇄", "#7aa5f8"])}
      ${rowSwitch("TUN 模式", "tun_mode", s.settings.tun_mode, ["≋", "#af8cf8"])}
      ${row("TUN 堆栈", h((TUN_STACKS[s.settings.tun] || s.settings.tun).split("（")[0]), { click: "set-tun-stack" })}
      ${s.tunStatus && s.tunStatus.supported ? row("虚拟网卡",
        s.tunStatus.exists ? `${h(s.tunStatus.state)} · ${h(s.tunStatus.description || "")}` : "未创建",
        { chev: s.tunStatus.exists && s.tunStatus.state !== "Up", click: s.tunStatus.exists && s.tunStatus.state !== "Up" ? "cleanup-tun" : undefined, vcls: "" }) : ""}
      ${rowSwitch("允许局域网连接", "allow_lan", s.settings.allow_lan)}
      ${rowSwitch("IPv6", "ipv6", s.settings.ipv6)}
    </div>
    <div class="section-label">提醒</div><div class="card">
      ${rowSwitch("到期提醒", "expire_notify", s.settings.expire_notify)}
      ${rowSwitch("流量提醒", "traffic_notify", s.settings.traffic_notify)}
      ${rowSwitch("开机自启动", "autostart", s.settings.autostart)}
      ${rowSwitch("自动检查更新", "auto_update", s.settings.auto_update)}
    </div>
    <div class="section-label">面板</div><div class="card">
      ${row("面板地址", h(s.settings.panel_url || "未设置"), { click: "set-panel", icon: ["⬡", "#1a73e8"] })}
      ${row("当前账号", h(s.settings.email || "未登录"), { chev: false })}
      ${row("重新拉取订阅", "现在拉取", { click: "refresh-sub" })}
    </div>
    <div class="section-label">关于</div><div class="card">
      ${row("当前版本", h(s.settings.version), { chev: false })}
      ${row("检查更新", '<span style="color:var(--blue)">检查更新</span>', { chev: false, click: "check-update" })}
      ${row("导出日志", "", { chev: false, click: "export-logs" })}
      ${row("开源许可", "GPL-3.0", { click: "license" })}
      ${row("运行模式", s.appInfo.portable ? "便携版（数据在程序目录）" : "标准版", { chev: false })}
      ${row("数据目录", h(s.appInfo.data_dir || ""), { chev: false })}
      ${row("管理员权限", s.appInfo.is_admin ? "已获得" : "未获得（TUN 模式需要）", { chev: false, vcls: s.appInfo.is_admin ? "strong" : "" })}
    </div>
    <div style="text-align:center;font-size:12px;color:var(--text3);margin:18px 0 6px">Polaris ${h(s.settings.version)} · 不采集任何用户数据</div>
  </div>`;

  /* ---------------- 我的 ---------------- */
  Views.me = (s) => head("我的") + `<div class="col-narrow">
    <div class="card" style="margin-bottom:14px"><div style="display:flex;align-items:center;gap:14px">
      <div style="width:52px;height:52px;border-radius:50%;background:var(--blue-soft);display:flex;align-items:center;justify-content:center;font-size:20px;color:var(--blue);font-weight:700">${h((s.email || "?").slice(0, 1).toUpperCase())}</div>
      <div style="min-width:0"><div style="font-size:16px;font-weight:700;word-break:break-all">${h(s.email || "未登录")}</div>
      <span class="badge b-blue" style="margin-top:6px;display:inline-block">${h(s.plan.name || "未订阅")}</span></div>
    </div></div>
    <div class="card" style="margin-bottom:14px">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px"><div style="font-size:14px;font-weight:700">当前套餐</div><button class="btn btn-primary btn-sm" data-nav="plans">续费</button></div>
      <div style="font-size:14px;margin-bottom:10px">已使用 <b>${h(s.plan.used)} GB</b> / ${h(s.plan.total)} GB</div>
      <div class="progress"><div style="width:${fmt.percent(s.plan.used, s.plan.total)}%"></div></div>
      <div style="font-size:13px;color:var(--text3);margin-top:10px">到期时间 ${h(s.plan.expire || "—")}</div>
    </div>
    <div class="card">
      ${row("我的订单", "", { icon: ["🧾", "#ffb340"], click: "nav-orders" })}
      ${row("我的工单", "", { icon: ["🎫", "#7aa5f8"], click: "nav-tickets" })}
      ${row("邀请好友", "", { icon: ["🎁", "#34c759"], click: "nav-invite" })}
      ${row("礼品卡兑换", "", { icon: ["💳", "#af8cf8"], click: "nav-giftcard" })}
      ${row("公告", s.unreadNotices ? badge(s.unreadNotices + " 条未读", "b-red") : "", { icon: ["📢", "#ff9f43"], click: "nav-notices" })}
      ${row("订阅链接", "查看", { icon: ["🔗", "#8e8e93"], click: "show-subscribe-url" })}
      ${row("关于", "", { icon: ["ℹ️", "#8e8e93"], click: "nav-settings" })}
      <div class="row" style="justify-content:center;cursor:pointer" data-click="logout"><span style="color:var(--red);font-weight:600">退出登录</span></div>
    </div></div>`;

  /* ---------------- 登录 / 注册 / 找回 ---------------- */
  function authShell(inner) {
    return `<div class="login-wrap" style="width:100%;height:100%;border:0;border-radius:0;box-shadow:none">
      <div class="login-body"><div class="login-card">${inner}</div></div></div>`;
  }

  Views.login = (s) => authShell(`
    <img class="app-icon" src="assets/p-icon.png" alt="">
    <h1>欢迎回来</h1><div class="sub">${h(s.siteInfo.appDescription || "登录以同步您的套餐与节点")}</div>
    <input class="field" id="login-panel" placeholder="面板地址 https://" value="${h(s.settings.panel_url || "")}">
    <input class="field" id="login-email" placeholder="邮箱" value="${h(s.settings.last_email || "")}">
    <input class="field" id="login-pass" type="password" placeholder="密码">
    ${err(s.authError, "auth-err")}
    <button class="btn btn-primary" id="btn-login" style="width:100%;height:48px;font-size:15px">登录</button>
    <div class="auth-links"><span data-nav="register">注册账号</span><span data-nav="forgot">忘记密码</span></div>
    ${s.settings.version ? `<div class="auth-ver">Polaris ${h(s.settings.version)}</div>` : ""}`);

  Views.register = (s) => authShell(`
    <img class="app-icon" src="assets/p-icon.png" alt="">
    <h1>注册账号</h1><div class="sub">${s.registerConfig.email_verify ? "需要邮箱验证码" : "创建后即可登录"}</div>
    <input class="field" id="reg-email" placeholder="邮箱">
    ${s.registerConfig.email_verify ? `<div class="field-row"><input class="field" id="reg-code" placeholder="邮箱验证码"><button class="btn btn-outline" id="btn-send-code">获取</button></div>` : ""}
    <input class="field" id="reg-pass" type="password" placeholder="密码（至少 8 位）">
    <input class="field" id="reg-pass2" type="password" placeholder="确认密码">
    ${s.registerConfig.invite_force ? `<input class="field" id="reg-invite" placeholder="邀请码（必填）">` : `<input class="field" id="reg-invite" placeholder="邀请码（选填）">`}
    ${err(s.authError, "auth-err")}
    <button class="btn btn-primary" id="btn-register" style="width:100%;height:48px;font-size:15px">注册</button>
    <div class="auth-links"><span data-nav="login">已有账号，去登录</span></div>`);

  Views.forgot = (s) => authShell(`
    <img class="app-icon" src="assets/p-icon.png" alt="">
    <h1>找回密码</h1><div class="sub">验证码将发送到你的邮箱</div>
    <input class="field" id="fg-email" placeholder="邮箱">
    <div class="field-row"><input class="field" id="fg-code" placeholder="邮箱验证码"><button class="btn btn-outline" id="btn-send-code">获取</button></div>
    <input class="field" id="fg-pass" type="password" placeholder="新密码（至少 8 位）">
    ${err(s.authError, "auth-err")}
    <button class="btn btn-primary" id="btn-forgot" style="width:100%;height:48px;font-size:15px">重置密码</button>
    <div class="auth-links"><span data-nav="login">返回登录</span></div>`);

  /* ---------------- 购买套餐 ---------------- */
  Views.plans = (s) => head("购买套餐", "",
    `<button class="btn btn-outline btn-sm" style="height:40px" data-nav="giftcard">🎁 礼品卡</button>`) +
    (s.plans.length ? `<div class="plan-grid">` + s.plans.map((p) => `
    <div class="plan-card${p.hot ? " hot" : ""}">
      ${p.hot ? '<div class="ribbon">最受欢迎</div>' : ""}
      <h3>${h(p.name)}</h3><div class="price">¥${h(p.price)}<small>/${h(p.unit)}</small></div>
      <ul>${p.feats.map((f) => `<li>${h(f)}</li>`).join("")}</ul>
      <button class="btn ${p.hot ? "btn-primary" : "btn-outline"}" data-buy="${h(p.id)}">立即购买</button>
    </div>`).join("") + `</div>
    <div style="text-align:center;font-size:13px;color:var(--text3);margin-top:18px">支付在浏览器中完成，回到应用后点「我的订单」刷新状态</div>`
    : empty("暂无可购买的套餐", "面板未配置套餐"));

  /* ---------------- 订单 ---------------- */
  const ORDER_STATUS = { done: ["已完成", "b-green"], pending: ["待支付", "b-orange"], processing: ["处理中", "b-blue"], refunded: ["已退款", "b-gray"] };
  Views.orders = (s) => head("我的订单", "", `<button class="btn btn-outline btn-sm" style="height:40px" data-nav="plans">购买套餐</button>`) +
    (s.orders.length ? `<div class="card">` + s.orders.map((o) => {
      const st = ORDER_STATUS[o.status] || ["未知", "b-gray"];
      return `<div class="row"><div class="k"><div><div style="font-weight:700;font-size:15px">${h(o.name)}</div>
        <div style="font-size:12.5px;color:var(--text3);margin-top:4px">${h(o.no)} · ${h(o.date)}</div></div></div>
        <div class="v strong"><span style="font-size:19px">¥${h(o.amount)}</span>${badge(st[0], st[1])}
        ${o.status === "pending" ? `<button class="btn btn-primary btn-sm" style="margin-left:8px" data-pay="${h(o.no)}">去支付</button>` : ""}</div></div>`;
    }).join("") + `</div>` : empty("还没有订单"));

  /* ---------------- 工单 ---------------- */
  const TICKET_STATUS = { replied: ["已回复", "b-green"], pending: ["处理中", "b-blue"], closed: ["已关闭", "b-gray"] };
  Views.tickets = (s) => head("我的工单", "", `<button class="btn btn-primary" id="btn-new-ticket">+ 新建工单</button>`) +
    (s.tickets.length ? `<div class="card">` + s.tickets.map((t) => {
      const st = TICKET_STATUS[t.status] || ["未知", "b-gray"];
      return `<div class="row" data-ticket="${h(t.no)}" style="cursor:pointer"><div class="k"><div><div style="font-weight:700;font-size:15px">${h(t.subject)}</div>
        <div style="font-size:12.5px;color:var(--text3);margin-top:4px">#${h(t.no)} · ${h(t.date)}</div></div></div>
        <div class="v">${badge(st[0], st[1])}<span class="chev">›</span></div></div>`;
    }).join("") + `</div>` : empty("还没有工单", "遇到问题可以提交工单联系客服"));

  /* ---------------- 邀请 ---------------- */
  Views.invite = (s) => head("邀请好友") + `<div class="col-narrow"><div class="card" style="text-align:center;padding:34px 30px">
    <div style="font-size:40px;margin-bottom:10px">🎁</div>
    <div style="font-size:19px;font-weight:800;margin-bottom:8px">邀请好友得奖励</div>
    <div style="font-size:13.5px;color:var(--text2);margin-bottom:18px">${s.invite.rate ? "好友消费返佣 " + h(s.invite.rate) + "%" : "把链接分享给好友，双方都有奖励"}</div>
    ${s.invite.link ? `<div style="display:flex;gap:10px"><div class="field" style="margin:0;text-align:left;color:var(--text2);overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${h(s.invite.link)}</div><button class="btn btn-primary" style="flex:none" id="btn-copy-invite">复制</button></div>`
      : `<div style="color:var(--text3);font-size:13.5px">面板未开启邀请功能</div>`}
  </div>
  <div class="speed-row" style="margin-top:14px">
    <div class="speed-tile"><span><div class="lb">已邀请</div><div class="vl">${h(s.invite.invited)} 人</div></span></div>
    <div class="speed-tile"><span><div class="lb">累计获得</div><div class="vl">${h(s.invite.earned)}</div></span></div>
  </div></div>`;

  /* ---------------- 礼品卡 ---------------- */
  Views.giftcard = (s) => head("礼品卡兑换") + `<div class="col-narrow"><div class="card" style="text-align:center;padding:34px 30px">
    <div style="font-size:40px;margin-bottom:10px">💳</div>
    <div style="font-size:19px;font-weight:800;margin-bottom:8px">兑换礼品卡</div>
    <div style="font-size:13.5px;color:var(--text2);margin-bottom:18px">输入卡密，流量或时长即时到账</div>
    <input class="field" id="gift-code" placeholder="请输入卡密" style="text-align:center" autocomplete="off">
    ${err(s.giftError, "gift-err")}
    <button class="btn btn-primary" id="btn-redeem" style="width:100%;height:48px;font-size:15px"${s.redeeming ? " disabled" : ""}>${s.redeeming ? "兑换中…" : "兑换"}</button>
  </div>
  ${s.giftHistory.length ? `<div class="section-label">兑换记录</div><div class="card">` +
    s.giftHistory.map((g) => `<div class="row"><div class="k"><span style="font-family:monospace">${h(g.code)}</span></div>
    <div class="v"><div style="text-align:right">${badge(g.reward, "b-green")}<div style="font-size:12px;color:var(--text3);margin-top:4px">${h(g.date)}</div></div></div></div>`).join("") + `</div>` : ""}</div>`;

  /* ---------------- 公告 ---------------- */
  Views.notices = (s) => head("公告") + (s.notices.length ? `<div class="card">` +
    s.notices.map((n, i) => `
    <div class="row" data-notice="${i}" style="cursor:pointer;${n.unread ? "background:var(--blue-soft);margin:0 -24px;padding-left:24px;padding-right:24px" : ""}">
      <div class="k">${n.unread ? '<span style="color:var(--blue);font-size:10px">●</span>' : ""}<div><div style="font-weight:700;font-size:15px">${h(n.title)}</div>
      <div style="font-size:12.5px;color:var(--text3);margin-top:4px">${h(n.date)}</div></div></div>
      <div class="v"><span class="chev">›</span></div>
    </div>`).join("") + `</div>` : empty("暂无公告"));

  /* ---------------- 弹窗 ---------------- */
  const Dialogs = {
    proxy: (s) => {
      const cur = s.mode;
      return `<div class="overlay" data-overlay><div class="dialog">
        <h2>代理模式</h2><div class="dsub">选择流量的代理方式，切换即时生效</div>
        ${[["rule", "规则模式", "按分流规则决定直连或代理（推荐）"], ["global", "全局模式", "全部流量走代理"], ["direct", "直连模式", "全部流量直连，不走代理"]].map(([k, t, d]) => `
        <div class="opt${cur === t ? " sel" : ""}" data-mode="${t}">
          <span class="radio${cur === t ? " sel" : ""}"></span>
          <div><div style="font-weight:700">${t}</div><div style="font-size:12.5px;color:var(--text3)">${d}</div></div>
        </div>`).join("")}
      </div></div>`;
    },

    update: (u) => `<div class="overlay" data-overlay><div class="dialog">
      <div style="text-align:center;margin-bottom:12px"><img src="assets/p-icon.png" style="width:56px;height:56px;border-radius:14px"></div>
      <h2 style="text-align:center">发现新版本 ${h(u.version)}</h2>
      <div class="dsub" style="text-align:center">当前版本 ${h(u.current)}${u.size ? " · " + h(u.size) : ""}</div>
      <div style="background:var(--bg);border-radius:12px;padding:14px 16px;font-size:13.5px;color:var(--text2);margin-bottom:18px;white-space:pre-line;max-height:220px;overflow:auto">${h(u.notes || "本次更新没有提供说明")}</div>
      <button class="btn btn-primary" id="btn-open-download" style="width:100%;height:46px;margin-bottom:10px">前往下载</button>
      <button class="btn" style="width:100%;height:44px;color:var(--text2);background:transparent" data-overlay-close>稍后再说</button>
    </div></div>`,

    newTicket: () => `<div class="overlay" data-overlay><div class="dialog">
      <h2>新建工单</h2><div class="dsub">描述您遇到的问题，客服将尽快回复</div>
      <input class="field" id="ticket-subject" placeholder="标题（例如：节点连接超时）">
      <textarea class="field" id="ticket-content" placeholder="问题描述…" style="height:110px;padding-top:14px;resize:none"></textarea>
      ${err("", "dialog-err")}
      <button class="btn btn-primary" id="btn-submit-ticket" style="width:100%;height:46px">提交</button>
    </div></div>`,

    logoutConfirm: () => `<div class="overlay" data-overlay><div class="dialog">
      <h2>退出登录</h2><div class="dsub">退出后当前连接会断开，本地凭据会被清除</div>
      <button class="btn btn-danger" id="btn-logout-confirm" style="width:100%;height:46px;margin-bottom:10px">退出登录</button>
      <button class="btn" style="width:100%;height:44px;color:var(--text2);background:transparent" data-overlay-close>取消</button>
    </div></div>`,

    notice: (n) => `<div class="overlay" data-overlay><div class="dialog">
      <h2>${h(n.title)}</h2><div class="dsub">${h(n.date)}</div>
      <div style="font-size:14px;color:var(--text2);white-space:pre-line;line-height:1.7;max-height:320px;overflow:auto">${h(n.body || "")}</div>
      <button class="btn btn-primary" data-overlay-close style="width:100%;height:44px;margin-top:20px">知道了</button>
    </div></div>`,

    ticketDetail: (t) => `<div class="overlay" data-overlay><div class="dialog">
      <h2>${h(t.subject)}</h2><div class="dsub">#${h(t.no)} · ${h(t.date)}</div>
      <div style="font-size:14px;color:var(--text2);white-space:pre-line;line-height:1.7;max-height:320px;overflow:auto">${h(t.content || "（无内容）")}</div>
      <button class="btn btn-primary" data-overlay-close style="width:100%;height:44px;margin-top:20px">关闭</button>
    </div></div>`,

    changePassword: () => `<div class="overlay" data-overlay><div class="dialog">
      <h2>修改密码</h2><div class="dsub">修改后需要重新登录</div>
      <input class="field" id="cp-old" type="password" placeholder="当前密码">
      <input class="field" id="cp-new" type="password" placeholder="新密码（至少 8 位）">
      <input class="field" id="cp-new2" type="password" placeholder="确认新密码">
      ${err("", "dialog-err")}
      <button class="btn btn-primary" id="btn-change-pwd" style="width:100%;height:46px">确认修改</button>
    </div></div>`,

    appearance: (s) => pickDialog("外观", "theme", [
      ["system", "跟随系统", "与 Windows 主题保持一致"],
      ["light", "浅色", ""],
      ["dark", "深色", ""],
    ], ({ system: "跟随系统", light: "浅色", dark: "深色" })[s.settings.theme]),

    language: (s) => pickDialog("语言", "lang", [
      ["zh-CN", "简体中文", ""],
      ["zh-TW", "繁體中文", ""],
      ["en-US", "English", "部分文案尚未翻译"],
    ], ({ "zh-CN": "简体中文", "zh-TW": "繁體中文", "en-US": "English" })[s.settings.lang]),

    tunStack: (s) => pickDialog("TUN 堆栈", "tun", [
      ["gvisor", "gvisor", "纯用户态，兼容性最好，性能略低（默认）"],
      ["system", "system", "走系统协议栈，性能更好，少数环境有兼容问题"],
      ["mixed", "mixed", "两者混用"],
    ], ({ gvisor: "gvisor", system: "system", mixed: "mixed" })[s.settings.tun]),

    groupPick: (g) => `<div class="overlay" data-overlay><div class="dialog">
      <h2>${h(g.name)}</h2><div class="dsub">选择该分组的出口节点</div>
      <div class="pick-list">${(g.options || []).map((n) => `
        <div class="opt${n === g.now ? " sel" : ""}" data-pick="${h(n)}">
          <span class="radio${n === g.now ? " sel" : ""}"></span><div>${h(n)}</div>
        </div>`).join("") || empty("该分组没有可选项")}</div>
    </div></div>`,

    payment: (m) => `<div class="overlay" data-overlay><div class="dialog">
      <h2>选择支付方式</h2><div class="dsub">订单 ${h(m.order_no)} · ¥${h(m.amount)}</div>
      ${m.methods.length ? m.methods.map((p) => `
        <div class="opt" data-pay-method="${h(p.id)}">
          <span class="radio"></span><div><div style="font-weight:700">${h(p.name)}</div></div>
        </div>`).join("") : empty("面板未配置支付方式", "请联系客服")}
      <button class="btn" style="width:100%;height:44px;color:var(--text2);background:transparent;margin-top:12px" data-overlay-close>取消</button>
    </div></div>`,

    subscribeUrl: (u) => `<div class="overlay" data-overlay><div class="dialog">
      <h2>订阅链接</h2><div class="dsub">含你的身份令牌，请勿分享给他人</div>
      <div class="code-box">${h(u.url || "未获取到订阅链接")}</div>
      <button class="btn btn-primary" id="btn-copy-sub" style="width:100%;height:44px;margin-top:14px">复制</button>
      <button class="btn" style="width:100%;height:44px;color:var(--text2);background:transparent;margin-top:8px" data-overlay-close>关闭</button>
    </div></div>`,

    confirm: (c) => `<div class="overlay" data-overlay><div class="dialog">
      <h2>${h(c.title)}</h2><div class="dsub">${h(c.body)}</div>
      <button class="btn btn-danger" id="btn-confirm-yes" style="width:100%;height:46px;margin-bottom:10px">${h(c.yes || "确定")}</button>
      <button class="btn" style="width:100%;height:44px;color:var(--text2);background:transparent" data-overlay-close>取消</button>
    </div></div>`,

    about: (i) => `<div class="overlay" data-overlay><div class="dialog">
      <div style="text-align:center;margin-bottom:12px"><img src="assets/p-icon.png" style="width:56px;height:56px;border-radius:14px"></div>
      <h2 style="text-align:center">Polaris ${h(i.version)}</h2>
      <div class="dsub" style="text-align:center">${i.portable ? "便携版" : "标准版"} · Electron ${h(i.electron || "")}</div>
      <div class="code-box" style="text-align:left;font-size:12px">GPL-3.0. 代理内核 mihomo（GPL-3.0）以独立进程随包分发。
本项目不采集任何用户数据，不上报任何统计。
凭据以 Windows DPAPI 加密存储在本机，换机即失效。</div>
      <button class="btn btn-primary" data-overlay-close style="width:100%;height:44px;margin-top:16px">关闭</button>
    </div></div>`,
  };

  function pickDialog(title, key, options, current) {
    return `<div class="overlay" data-overlay><div class="dialog">
      <h2>${h(title)}</h2><div class="dsub">选择后立即生效</div>
      ${options.map(([v, label, desc]) => `
        <div class="opt${label === current ? " sel" : ""}" data-pick-value="${v}" data-pick-key="${key}">
          <span class="radio${label === current ? " sel" : ""}"></span>
          <div><div style="font-weight:700">${h(label)}</div>${desc ? `<div style="font-size:12.5px;color:var(--text3)">${h(desc)}</div>` : ""}</div>
        </div>`).join("")}
    </div></div>`;
  }

  window.PolarisViews = Views;
  window.PolarisDialogs = Dialogs;
})();
