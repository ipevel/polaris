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

  /**
   * 二级页面的页头：带返回键。用户报「基本上所有二级页面都没有返回按钮」，
   * 所以凡是从"我的/设置/节点"点进去的页面（套餐、订单、工单、邀请、礼品卡、
   * 公告、分流规则）一律用这个。返回目标由 app.js 的返回栈决定。
   */
  function backHead(title, sub, actions) {
    return head(title, sub,
      `<button class="btn btn-ghost btn-sm" style="height:40px" data-nav-back="1">‹ 返回</button>` + (actions || ""));
  }

  /* 套餐用量：优先用字节换算（面板按 GB 两位小数取整，946 KB 会变成 0.00 GB），
     没有 _text 时退回 GB 数字（老数据里只有 used/total）。 */
  function planUsed(s) {
    const p = (s && s.plan) || {};
    return p.used_text || `${p.used == null ? "—" : p.used} GB`;
  }
  function planTotal(s) {
    const p = (s && s.plan) || {};
    return p.total_text || `${p.total == null ? "—" : p.total} GB`;
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

  // 分流组（内置本地方案）里有一批是「直连/拦截」型：它们的当前出口就是 DIRECT/REJECT，
  // 内核给不出延迟（不是没测出来，是压根不该测）。这类组显示「直连/拦截」比显示「未测」诚实。
  const EXIT_LABEL = { DIRECT: "直连", COMPATIBLE: "直连", PASS: "直连", REJECT: "拦截", "REJECT-DROP": "拦截" };
  function groupBadge(g) {
    const label = EXIT_LABEL[g.now];
    if (label) return badge(label, "b-blue");
    return latBadge(typeof g.latency === "number" ? g.latency : -1, false);
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
      ${row("本次上传", h(s.up_total), { chev: false, vcls: "strong", id: "live-up-total" })}
      ${row("本次下载", h(s.down_total), { chev: false, vcls: "strong", id: "live-down-total" })}
      ${row("运行时间", h(s.uptime), { chev: false, vcls: "strong", id: "live-uptime" })}
    </div>
    <div class="section-label">当前套餐</div>
    <div class="card">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px">
        <div style="font-size:14px">已使用 <b>${h(planUsed(s))}</b> / ${h(planTotal(s))}</div>
        <button class="btn btn-primary btn-sm" data-nav="plans">续费</button>
      </div>
      <div class="progress"><div style="width:${fmt.percent(s.plan.used, s.plan.total)}%"></div></div>
      <div style="font-size:13px;color:var(--text3);margin-top:10px">到期时间 ${h(s.plan.expire || "—")}</div>
    </div>
  </div>`;

  /* ---------------- 节点 ---------------- */
  // 节点页 = 策略组手风琴：分组在**最上面**、可折叠、分组内直接点节点就切那个分组的出口、
  // 每个分组显示自己的延迟。主组就是面板自带的「🚀 节点选择」（与安卓端同名，见
  // electron/core/builder.js 的 SELECTOR_GROUP）—— 旧版自己另建一个叫「节点选择」的组，
  // 于是顶部多出一张重复的分类卡（用户实测报的 bug）。
  Views.nodes = (s) => {
    const q = (s.nodeFilter || "").toLowerCase();
    const hit = (v) => !q || String(v).toLowerCase().includes(q);
    // 结构组（兜底组 🐟 漏网之鱼、自动选择、故障转移、GLOBAL）只是配置骨架，不是给用户选的分类
    const groups = (Array.isArray(s.groups) ? s.groups : []).filter((g) => !g.builtin && !g.structural);
    const open = s.openGroups || {};
    // 没点过就默认展开第一个（内核里排最前的就是"节点选择"）
    const isOpen = (name, i) => (open[name] === undefined ? i === 0 : !!open[name]);

    const nodeRow = (n, groupName) => `
      <div class="node-row" data-node="${h(n.name)}"${groupName ? ` data-node-group="${h(groupName)}"` : ""} style="cursor:pointer">
        <span class="nm">${h(n.name)}</span>${badge(n.region, "b-blue")}${latBadge(n.latency, n.offline)}
        <span class="radio${n.name === s.node ? " sel" : ""}"></span>
      </div>`;

    const flat = s.nodes.filter((n) => hit(n.name) || hit(n.region));
    const cards = groups.length
      ? groups.map((g, i) => {
        const opened = isOpen(g.name, i);
        const members = (g.options || []).map((name) => s.nodes.find((n) => n.name === name)
          || { name, region: "", latency: -1, offline: false }).filter((n) => hit(n.name) || hit(n.region));
        return `<div class="card" style="margin-bottom:14px">
          <div class="acc-head" data-acc="${h(g.name)}" style="cursor:pointer">
            <div><div class="acc-title" style="font-size:15px">${h(g.name)}</div>
            <div class="acc-sub">${g.count ? g.count + " 个可选出口 · " : ""}当前：${h(g.now || "—")}</div></div>
            <div style="display:flex;align-items:center;gap:10px">${groupBadge(g)}
            <span class="chev${opened ? " open" : ""}" style="font-size:20px">›</span></div>
          </div>
          ${opened ? `<div class="acc-body">${members.length ? members.map((n) => nodeRow(n, g.name)).join("") : empty("没有匹配的节点", "换个关键词试试")}</div>` : ""}
        </div>`;
      }).join("")
      // 未连接时内核还没起，拿不到策略组 —— 退回本地配置预览的扁平列表
      : `<div class="card" style="margin-bottom:14px">
          <div class="acc-head"><div><div class="acc-title">节点选择</div><div class="acc-sub">当前：${h(s.node || "未选择")}</div></div></div>
          ${flat.length ? flat.map((n) => nodeRow(n, "")).join("") : empty("没有匹配的节点", s.nodes.length ? "换个关键词试试" : "点右上角刷新订阅")}
        </div>`;

    return head("节点", `${s.nodes.length} 个节点${groups.length ? " · " + groups.length + " 个分组" : ""}`,
      `<span class="search-wrap"><input class="search" id="node-search" placeholder="搜索节点" value="${h(s.nodeFilter)}"></span>` +
      `<button class="btn btn-outline btn-sm" style="height:40px" data-click="nav-routing">分流规则</button>` +
      `<button class="btn btn-outline btn-sm" style="height:40px" id="btn-speedtest"${s.testing ? " disabled" : ""}>${s.testing ? "测试中…" : "延迟测试"}</button>` +
      `<button class="btn btn-primary btn-sm" style="height:40px" id="btn-refresh-sub"${s.refreshing ? " disabled" : ""}>${s.refreshing ? "刷新中…" : "刷新订阅"}</button>`) + cards;
  };

  /* ---------------- 流量 ---------------- */
  Views.traffic = (s) => {
    const t = s.traffic || {};
    const r = s.trafficRange;
    const seg = (k, label) => `<button class="btn ${r === k ? "btn-primary" : "btn-ghost"} btn-sm" style="height:40px" data-range="${k}">${label}</button>`;
    // 字段名必须是 state.trafficSeries —— 旧代码读 s.series（不存在），
    // 导致曲线区永远走 empty()，用户永远看不到任何流量图。
    const series = s.trafficSeries || {};
    const pts = series.points || [];
    const max = Math.max(1, ...pts.map((p) => Math.max(p.down, p.up)));
    const W = 620, H = 170, PAD = 34;
    const x = (i) => PAD + (i / Math.max(1, pts.length - 1)) * (W - PAD - 10);
    const y = (v) => H - 20 - (v / max) * (H - 44);
    const line = (key) => pts.map((p, i) => (i ? "L" : "M") + x(i).toFixed(1) + "," + y(p[key]).toFixed(1)).join(" ");
    const area = pts.length ? `${line("down")} L${x(pts.length - 1).toFixed(1)},${H - 20} L${x(0).toFixed(1)},${H - 20} Z` : "";
    const ticks = pts.filter((_, i) => pts.length <= 8 || i % Math.ceil(pts.length / 6) === 0);

    const site = t.site || null;
    const acct = t.account || null;
    const sess = t.session || {};
    const rangeLabel = r === "today" ? "今日" : r === "week" ? "本周" : "本月";
    return head("流量", "", seg("today", "今日") + seg("week", "本周") + seg("month", "本月")) + `
    <div class="section-label">站点用量 · ${rangeLabel}${site && site.days ? ` · ${site.days} 天有记录` : ""}</div>
    <div class="card" style="margin-bottom:14px">
      ${site ? `<div class="speed-row" style="margin:0 0 4px">
        <div class="speed-tile dn"><span><div class="lb">下载</div><div class="vl">${h(site.down_text)}</div></span></div>
        <div class="speed-tile up"><span><div class="lb">上传</div><div class="vl">${h(site.up_text)}</div></span></div>
      </div>
      ${row("区间合计", h(site.total_text), { chev: false, vcls: "strong" })}
      ${site.from ? row("区间", `${h(site.from)} ~ ${h(site.to)}`, { chev: false }) : ""}` 
      : empty("未登录面板，读不到站点用量", "登录后这里显示面板记录的流量明细")}
    </div>
    <div class="card" style="margin-bottom:14px">
      <div style="display:flex;justify-content:space-between;margin-bottom:8px"><b style="font-size:15px">本机实时速度 · ${r === "today" ? "24 小时" : r === "week" ? "7 天" : "30 天"}</b>
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
        <g font-size="11" fill="#8e8e93"><text x="4" y="26">${h(series.unit || "MB")}</text><text x="4" y="${H - 22}">0</text></g>
      </svg>` : empty("暂无流量记录", "连接后开始统计")}
      <div style="font-size:13px;color:var(--text2);margin-top:4px">当前下载 <span id="down-speed">${fmt.speed(s.down_speed)}</span> · 当前上传 <span id="up-speed">${fmt.speed(s.up_speed)}</span></div>
    </div>
    <div class="speed-row" style="margin:0 0 4px">
      <div class="speed-tile dn"><span><div class="lb">本次下载</div><div class="vl">${h(sess.down_text || "0 B")}</div></span></div>
      <div class="speed-tile up"><span><div class="lb">本次上传</div><div class="vl">${h(sess.up_text || "0 B")}</div></span></div>
    </div>
    <div class="section-label">面板累计</div>
    <div class="card">
      ${acct ? row("已用流量", `${h(acct.used_text)} / ${h(acct.quota_text)}`, { chev: false, vcls: "strong" })
        : row("总下载", h(t.total_down || "—"), { chev: false, vcls: "strong" })}
      ${acct ? row("套餐", h(acct.plan_name || "—"), { chev: false }) : row("总上传", h(t.total_up || "—"), { chev: false, vcls: "strong" })}
      ${acct && acct.expire ? row("到期", h(acct.expire), { chev: false }) : ""}
      ${row("本次峰值", h(t.peak || "—"), { chev: false, vcls: "strong" })}
      ${row("在线节点", String(t.online_nodes || 0), { chev: false, vcls: "strong" })}
    </div>
    ${s.trafficLog && s.trafficLog.length ? `<div class="section-label">站点流量明细</div><div class="card">` +
      s.trafficLog.map((r2) => row(h(r2.date),
        `${fmt.bytes(r2.download || 0)} ↓ / ${fmt.bytes(r2.upload || 0)} ↑`, { chev: false })).join("") + `</div>` : ""}`;
  };

  /* ---------------- 分流规则 ---------------- */
  const OUT_LABEL = { direct: "直连", block: "拦截", proxy: "节点选择" };
  const OUT_LABEL_LONG = { proxy: "节点选择", direct: "直连", block: "拦截" };

  /**
   * 一行分流组。drag=是否显示拖动手柄（顺序 = 匹配优先级，越靠前越先匹配，
   * 按住手柄拖到目标位置即可，用户要的就是拖动而不是点上下按钮）；
   * edit=自定义组才有的"编辑"入口。
   */
  function rulesetRow(g, opts) {
    const o = opts || {};
    const out = OUT_LABEL[g.out] || "节点选择";
    const cur = g.out === "proxy" ? (g.now || out) : out;
    const sub = g.custom
      ? `${g.inline} 条自定义规则${g.live ? "" : " · 未生效"}`
      : `${g.count} 个规则集${g.inline ? ` · ${g.inline} 条内联` : ""}${g.live ? "" : " · 未生效"}`;
    const grip = o.drag
      ? `<span class="ruleset-grip" data-ruleset-grip="${h(g.name)}" title="按住拖动，调整匹配顺序">⠿</span>`
      : "";
    return `<div class="row ruleset-row" data-ruleset-pick="${h(g.name)}" data-ruleset-row="${h(g.name)}">
      <div class="k"><span style="display:flex;flex-direction:column"><span>${h(g.name)}</span>
      <span style="font-size:11px;color:var(--text3);margin-top:2px">${h(sub)}</span></span></div>
      <div class="v" style="display:flex;align-items:center;gap:8px">${badge(cur, g.out === "direct" ? "b-gray" : g.out === "block" ? "b-red" : "b-blue")}
      ${grip}
      ${o.edit ? `<span class="ruleset-mv" data-ruleset-edit="${h(g.name)}" title="编辑规则">✎</span>` : ""}
      <div class="switch${g.enabled ? " on" : ""}" data-ruleset="${h(g.name)}"></div>
      <span class="chev">›</span></div></div>`;
  }

  Views.routing = (s) => {
    const rs = s.rulesets || {};
    const builtin = Array.isArray(rs.groups) ? rs.groups : [];
    const custom = Array.isArray(rs.custom) ? rs.custom : [];
    const onCount = builtin.filter((g) => g.enabled).length;
    const customOn = custom.filter((g) => g.enabled).length;
    const localOn = rs.on !== false;
    return backHead("分流规则", "与手机端同一套本地方案，自定义规则优先于内置分类",
      `<button class="btn btn-outline btn-sm" style="height:40px" id="btn-routing-reset">恢复出口</button>`) +
      `<div class="section-label">分流方案</div>` +
      `<div class="card">` +
      `<div class="row"><div class="k"><span class="mini-icon" style="background:#0A84FF">🧭</span>` +
      `<span style="display:flex;flex-direction:column"><span>使用本地分流方案</span>` +
      `<span style="font-size:11px;color:var(--text3);margin-top:2px">屏蔽面板下发的分流规则，只用下面这套内置分类</span></span></div>` +
      `<div class="v"><div class="switch${localOn ? " on" : ""}" data-click="local-routing"></div></div></div>` +
      (localOn ? "" : `<div class="row"><div class="k"><span style="color:var(--text3);font-size:12px">已关闭：面板自带的分流规则正在生效</span></div></div>`) +
      `</div>` +
      (rs.degraded ? err(`本地方案已降级：${rs.degraded}`) : "") +
      `<div class="section-label">自定义分流组 · 已启用 ${customOn}/${custom.length}</div>` +
      `<div class="card">` +
      (custom.length
        ? custom.map((g) => rulesetRow(g, { drag: true, edit: true })).join("")
        : `<div class="row"><div class="k"><span style="color:var(--text3);font-size:13px">还没有自定义分流组</span></div>` +
          `<div class="v"><span style="color:var(--text3);font-size:12px">自己写规则，永远最先匹配</span></div></div>`) +
      `<div class="row"><div class="k"><span>新建分流组</span></div>` +
      `<div class="v"><button class="btn btn-outline btn-sm" id="btn-custom-new">新建</button></div></div>` +
      `</div>` +
      `<div class="section-label">内置分流 · 规则库每 24 小时自动更新 · 已启用 ${onCount}/${rs.total || builtin.length}</div>` +
      `<div class="card">` +
      (builtin.length
        ? builtin.map((g) => rulesetRow(g, { drag: true })).join("") +
          `<div class="row"><div class="k"><span>恢复默认开关与顺序</span></div>` +
          `<div class="v"><button class="btn btn-outline btn-sm" id="btn-ruleset-reset">恢复默认</button></div></div>`
        : empty("没有内置分流规则", "缺少 resources/rules 规则集，请重新解压完整目录")) +
      `</div>`;
  };

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
      ${row("本机代理端口", s.settings.mixed_port
        ? `127.0.0.1:${h(s.settings.mixed_port)}`
        : `自动（当前 ${s.settings.running_port ? h(s.settings.running_port) : "未运行"}）`,
      { click: "set-port", icon: ["⇲", "#5ac8fa"] })}
      ${rowSwitch("TUN 模式", "tun_mode", s.settings.tun_mode, ["≋", "#af8cf8"])}
      ${row("TUN 堆栈", h((TUN_STACKS[s.settings.tun] || s.settings.tun).split("（")[0]), { click: "set-tun-stack" })}
      ${s.tunStatus && s.tunStatus.supported ? row("虚拟网卡",
        s.tunStatus.exists ? `${h(s.tunStatus.state)} · ${h(s.tunStatus.description || "")}` : "未创建",
        { chev: s.tunStatus.exists && s.tunStatus.state !== "Up", click: s.tunStatus.exists && s.tunStatus.state !== "Up" ? "cleanup-tun" : undefined, vcls: "" }) : ""}
      ${rowSwitch("允许局域网连接", "allow_lan", s.settings.allow_lan)}
      ${rowSwitch("IPv6", "ipv6", s.settings.ipv6)}
    </div>
    <div class="section-label">分流与订阅</div><div class="card">
      ${row("订阅链接", "查看", { icon: ["🔗", "#8e8e93"], click: "show-subscribe-url" })}
      ${row("重新拉取订阅", "现在拉取", { click: "refresh-sub" })}
    </div>
    <div class="section-label">提醒</div><div class="card">
      ${rowSwitch("到期提醒", "expire_notify", s.settings.expire_notify)}
      ${rowSwitch("流量提醒", "traffic_notify", s.settings.traffic_notify)}
      ${rowSwitch("开机自启动", "autostart", s.settings.autostart)}
      ${rowSwitch("自动检查更新", "auto_update", s.settings.auto_update)}
    </div>
    <div class="section-label">关于</div><div class="card">
      ${row("当前版本", h(s.settings.version), { chev: false })}
      ${row("检查更新", s.updateInfo && s.updateInfo.has_update
        ? '<span style="color:var(--blue)">有新版本 ' + h(s.updateInfo.version) + "</span>"
        : '<span style="color:var(--blue)">检查更新</span>', { chev: false, click: "check-update" })}
      ${row("导出日志", "", { chev: false, click: "export-logs" })}
      ${row("开源许可", "GPL-3.0", { click: "license" })}
      ${row("运行模式", s.appInfo.portable ? "便携版（数据在程序目录）" : "标准版", { chev: false })}
      ${row("数据目录", h(s.appInfo.data_dir || ""), { chev: false })}
      ${row("管理员权限", s.appInfo.is_admin ? "已获得" : "未获得（TUN 模式需要）", { chev: false, vcls: s.appInfo.is_admin ? "strong" : "" })}
    </div>
    <div style="text-align:center;font-size:12px;color:var(--text3);margin:18px 0 6px">Polaris ${h(s.settings.version)} · 不采集任何用户数据</div>
  </div>`;

  /* ---------------- 我的 ---------------- */
  // 与安卓端"我的"逐项对齐（名称与顺序都照手机 app 的「我的服务」列表）：
  // 订阅套餐 / 礼品卡兑换 / 我的订单 / 邀请返利 / 我的工单 / 公告通知 / Telegram。
  // Telegram 入口只在面板真下发了链接时才出现（安卓端同样取不到就隐藏）。
  // 分流规则与订阅链接**不在这里**（安卓端也没有），它们在设置页的「分流与订阅」里。
  Views.me = (s) => head("我的") + `<div class="col-narrow">
    <div class="card" style="margin-bottom:14px"><div style="display:flex;align-items:center;gap:14px">
      <div style="width:52px;height:52px;border-radius:50%;background:var(--blue-soft);display:flex;align-items:center;justify-content:center;font-size:20px;color:var(--blue);font-weight:700">${h((s.email || "?").slice(0, 1).toUpperCase())}</div>
      <div style="min-width:0"><div style="font-size:16px;font-weight:700;word-break:break-all">${h(s.email || "未登录")}</div>
      <span class="badge b-blue" style="margin-top:6px;display:inline-block">${h(s.plan.name || "未订阅")}</span></div>
    </div></div>
    <div class="card" style="margin-bottom:14px">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px"><div style="font-size:14px;font-weight:700">当前套餐</div><button class="btn btn-primary btn-sm" data-nav="plans">续费</button></div>
      <div style="font-size:14px;margin-bottom:10px">已使用 <b>${h(planUsed(s))}</b> / ${h(planTotal(s))}</div>
      <div class="progress"><div style="width:${fmt.percent(s.plan.used, s.plan.total)}%"></div></div>
      <div style="font-size:13px;color:var(--text3);margin-top:10px">到期时间 ${h(s.plan.expire || "—")}</div>
    </div>
    <div class="card">
      ${row("订阅套餐", "", { icon: ["◈", "#1a73e8"], click: "nav-plans" })}
      ${row("礼品卡兑换", "", { icon: ["💳", "#af8cf8"], click: "nav-giftcard" })}
      ${row("我的订单", "", { icon: ["🧾", "#ffb340"], click: "nav-orders" })}
      ${row("邀请返利", "", { icon: ["🎁", "#34c759"], click: "nav-invite" })}
      ${row("我的工单", "", { icon: ["🎫", "#7aa5f8"], click: "nav-tickets" })}
      ${row("公告通知", s.unreadNotices ? badge(s.unreadNotices + " 条未读", "b-red") : "", { icon: ["📢", "#ff9f43"], click: "nav-notices" })}
      ${s.siteInfo && s.siteInfo.telegramUrl ? row("Telegram", "", { icon: ["✈", "#29b6f6"], click: "open-telegram" }) : ""}
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
  Views.plans = (s) => backHead("购买套餐", "",
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
  Views.orders = (s) => backHead("我的订单", "", `<button class="btn btn-outline btn-sm" style="height:40px" data-nav="plans">购买套餐</button>`) +
    (s.orders.length ? `<div class="card">` + s.orders.map((o) => {
      const st = ORDER_STATUS[o.status] || ["未知", "b-gray"];
      return `<div class="row"><div class="k"><div><div style="font-weight:700;font-size:15px">${h(o.name)}</div>
        <div style="font-size:12.5px;color:var(--text3);margin-top:4px">${h(o.no)} · ${h(o.date)}</div></div></div>
        <div class="v strong"><span style="font-size:19px">¥${h(o.amount)}</span>${badge(st[0], st[1])}
        ${o.status === "pending" ? `<button class="btn btn-primary btn-sm" style="margin-left:8px" data-pay="${h(o.no)}">去支付</button>` : ""}</div></div>`;
    }).join("") + `</div>` : empty("还没有订单"));

  /* ---------------- 工单 ---------------- */
  const TICKET_STATUS = { replied: ["已回复", "b-green"], pending: ["处理中", "b-blue"], closed: ["已关闭", "b-gray"] };
  Views.tickets = (s) => backHead("我的工单", "", `<button class="btn btn-primary" id="btn-new-ticket">+ 新建工单</button>`) +
    (s.tickets.length ? `<div class="card">` + s.tickets.map((t) => {
      const st = TICKET_STATUS[t.status] || ["未知", "b-gray"];
      return `<div class="row" data-ticket="${h(t.no)}" style="cursor:pointer"><div class="k"><div><div style="font-weight:700;font-size:15px">${h(t.subject)}</div>
        <div style="font-size:12.5px;color:var(--text3);margin-top:4px">#${h(t.no)} · ${h(t.date)}</div></div></div>
        <div class="v">${badge(st[0], st[1])}<span class="chev">›</span></div></div>`;
    }).join("") + `</div>` : empty("还没有工单", "遇到问题可以提交工单联系客服"));

  /* ---------------- 邀请 ---------------- */
  Views.invite = (s) => backHead("邀请好友") + `<div class="col-narrow"><div class="card" style="text-align:center;padding:34px 30px">
    <div style="font-size:40px;margin-bottom:10px">🎁</div>
    <div style="font-size:19px;font-weight:800;margin-bottom:8px">邀请好友得奖励</div>
    <div style="font-size:13.5px;color:var(--text2);margin-bottom:18px">${s.invite.rate ? "好友消费返佣 " + h(s.invite.rate) + "%" : "把链接分享给好友，双方都有奖励"}</div>
    ${s.invite.link ? `<div style="display:flex;gap:10px"><div class="field" style="margin:0;text-align:left;color:var(--text2);overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${h(s.invite.link)}</div><button class="btn btn-primary" style="flex:none" id="btn-copy-invite">复制</button></div>`
      : `<div style="color:var(--text3);font-size:13.5px">面板未开启邀请功能</div>`}
  </div>
  <div class="speed-row" style="margin-top:14px">
    <div class="speed-tile"><span><div class="lb">已注册</div><div class="vl">${h(s.invite.registered)} 人</div></span></div>
    <div class="speed-tile"><span><div class="lb">累计佣金</div><div class="vl">￥${h((s.invite.commission || 0).toFixed(2))}</div></span></div>
  </div>
  <div class="speed-row" style="margin-top:10px">
    <div class="speed-tile"><span><div class="lb">可提现余额</div><div class="vl">￥${h((s.invite.balance || 0).toFixed(2))}</div></span></div>
    <div class="speed-tile"><span><div class="lb">待确认</div><div class="vl">￥${h((s.invite.pending || 0).toFixed(2))}</div></span></div>
  </div>
  ${(s.invite.codes || []).length ? `<div class="card" style="margin-top:14px"><div class="section-label">我的邀请码</div>
    ${s.invite.codes.map((c) => row(c.code, `${c.pv} 次访问`, { id: `inv-code-${c.code}` })).join("")}</div>` : ""}
  </div>`;

  /* ---------------- 礼品卡 ---------------- */
  Views.giftcard = (s) => backHead("礼品卡兑换") + `<div class="col-narrow"><div class="card" style="text-align:center;padding:34px 30px">
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
  Views.notices = (s) => backHead("公告") + (s.notices.length ? `<div class="card">` +
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

    update: (u) => {
      // 便携版能自己装（下载→解压→退出→脚本覆盖→重启），装不了才退回「前往下载」
      const ready = !!u.staged;
      const canApply = !!u.can_apply;
      const primary = ready
        ? `<button class="btn btn-primary" id="btn-apply-update" style="width:100%;height:46px;margin-bottom:10px">重启并安装</button>`
        : canApply
          ? `<button class="btn btn-primary" id="btn-download-update" style="width:100%;height:46px;margin-bottom:10px">下载并安装</button>`
          : `<button class="btn btn-primary" id="btn-open-download" style="width:100%;height:46px;margin-bottom:10px">前往下载</button>`;
      const blocked = !canApply && u.apply_blocked
        ? `<div class="dsub" style="text-align:center;margin-bottom:10px">${h(u.apply_blocked)}</div>`
        : "";
      return `<div class="overlay" data-overlay><div class="dialog">
      <div style="text-align:center;margin-bottom:12px"><img src="assets/p-icon.png" style="width:56px;height:56px;border-radius:14px"></div>
      <h2 style="text-align:center">发现新版本 ${h(u.version)}</h2>
      <div class="dsub" style="text-align:center">当前版本 ${h(u.current)}${u.size ? " · " + h(u.size) : ""}</div>
      <div style="background:var(--bg);border-radius:12px;padding:14px 16px;font-size:13.5px;color:var(--text2);margin-bottom:18px;white-space:pre-line;max-height:220px;overflow:auto">${h(u.notes || "本次更新没有提供说明")}</div>
      <div id="upd-progress" style="display:none;margin-bottom:14px">
        <div style="height:6px;border-radius:3px;background:var(--bg);overflow:hidden">
          <div id="upd-bar" style="height:100%;width:0%;background:var(--blue);transition:width .2s"></div>
        </div>
        <div id="upd-text" class="dsub" style="margin-top:8px;text-align:center"></div>
      </div>
      ${blocked}
      ${primary}
      <button class="btn" style="width:100%;height:44px;color:var(--text2);background:transparent" data-overlay-close>稍后再说</button>
    </div></div>`;
    },

    newTicket: () => `<div class="overlay" data-overlay><div class="dialog">
      <h2>新建工单</h2><div class="dsub">描述您遇到的问题，客服将尽快回复</div>
      <input class="field" id="ticket-subject" placeholder="标题（例如：节点连接超时）">
      <textarea class="field" id="ticket-content" placeholder="问题描述…" style="height:110px;padding-top:14px;resize:none"></textarea>
      <div style="font-size:12.5px;color:var(--text3);margin:14px 0 8px">优先级</div>
      <div id="tk-level" style="display:flex;gap:8px">
        ${[['0', '低'], ['1', '中'], ['2', '高']].map(([v, label], i) =>
          `<div class="opt${i === 1 ? " sel" : ""}" data-tk-level="${v}" style="flex:1;padding:10px 12px">
            <span class="radio${i === 1 ? " sel" : ""}"></span>
            <div style="font-weight:600;font-size:13.5px">${label}</div>
          </div>`).join("")}
      </div>
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

    customRuleset: (a) => {
      const g = a || {};
      const editing = !!g.name;
      const cur = g.out || "proxy";
      return `<div class="overlay" data-overlay><div class="dialog">
      <h2>${editing ? "编辑分流组" : "新建分流组"}</h2>
      <div class="dsub">自己写的规则永远排在内置分类之前</div>
      <input class="field" id="cr-name" placeholder="分组名字（例如：公司内网）" value="${h(g.name || "")}">
      <div class="section-label" style="margin:6px 0 6px">出口</div>
      <div style="display:flex;gap:8px;margin-bottom:14px">
        ${["proxy", "direct", "block"].map((v) => `
        <div class="opt${cur === v ? " sel" : ""}" data-cr-out="${v}" style="flex:1;padding:10px 8px">
          <span class="radio${cur === v ? " sel" : ""}"></span>
          <div><div style="font-weight:700;font-size:13.5px">${OUT_LABEL_LONG[v]}</div></div>
        </div>`).join("")}
      </div>
      <textarea class="field" id="cr-rules" spellcheck="false" placeholder="DOMAIN-SUFFIX,example.com&#10;DOMAIN-KEYWORD,github&#10;IP-CIDR,10.0.0.0/8" style="height:132px;padding-top:14px;resize:none;font-family:ui-monospace,Consolas,monospace;font-size:12.5px">${h((g.rules || []).join("\n"))}</textarea>
      <div class="dsub" style="text-align:left;margin:8px 0 0;line-height:1.6">一行一条，写「类型,内容」就行，出口会自动补上（IP 类规则自动加 no-resolve）。支持 DOMAIN / DOMAIN-SUFFIX / DOMAIN-KEYWORD / GEOSITE / IP-CIDR / IP-CIDR6 / GEOIP / PROCESS-NAME 等。</div>
      ${err("", "dialog-err")}
      <button class="btn btn-primary" id="btn-save-custom" style="width:100%;height:46px;margin-top:14px">${editing ? "保存修改" : "创建"}</button>
      ${editing ? `<button class="btn btn-danger" id="btn-delete-custom" style="width:100%;height:44px;margin-top:10px">删除这个分流组</button>` : ""}
      <button class="btn" style="width:100%;height:44px;color:var(--text2);background:transparent" data-overlay-close>取消</button>
    </div></div>`;
    },

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

    port: (s) => `<div class="overlay" data-overlay><div class="dialog">
      <h2>本机代理端口</h2>
      <div class="dsub">其它软件要用本机的 127.0.0.1 代理时，在这里指定端口。留空 = 自动挑一个空闲端口。</div>
      <input class="input" id="port-value" inputmode="numeric" placeholder="例如 7890，留空自动" value="${s && s.mixed_port ? h(s.mixed_port) : ""}">
      <div class="err" id="dialog-err"></div>
      <div class="dsub" style="margin-top:6px">当前运行端口：${s && s.running_port ? h(s.running_port) : "未启动"}${s && s.mixed_port ? "" : "（自动）"}</div>
      <button class="btn btn-primary" id="btn-save-port" style="width:100%;height:44px;margin-top:14px">保存</button>
      <button class="btn" style="width:100%;height:44px;color:var(--text2);background:transparent;margin-top:8px" data-overlay-close>取消</button>
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
