/* 页面视图与弹窗。纯函数：(state) -> HTML 字符串；事件在 app.js 里绑定。
 * 所有插值都过 h() 转义 —— 节点名、公告正文、工单标题都来自面板，属不可信输入。 */
(function () {
  const h = window.PolarisFormat.escape;
  const fmt = window.PolarisFormat;
  const md = (s) => (window.PolarisMarkdown ? window.PolarisMarkdown.render(s) : h(s));

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

  /**
   * 套餐数据到底回来了没有。
   * `state.plan` 的初始值是 `{name:'—', used:0, total:0}`，直接渲染出来就是
   * 「已使用 0 GB / 0 GB · 到期时间 —」—— 用户看到的是"我的套餐没了"。
   * 第 13 轮实测：新登录那一次首页先渲染的就是这个占位值，界面自检的四条红全出在这。
   * 没回来时宁可写"读取中…"，也不写假的 0。
   */
  function planReady(s) {
    const p = (s && s.plan) || {};
    return !!(p.name && p.name !== "—" && p.name !== "未订阅");
  }

  function row(k, v, opts) {
    opts = opts || {};
    const ic = opts.icon ? `<span class="mini-icon" style="background:${opts.icon[1]}">${opts.icon[0]}</span>` : "";
    const chev = opts.chev === false ? "" : '<span class="chev">›</span>';
    return `<div class="row"${opts.id ? ` id="${opts.id}"` : ""}${opts.click ? ` data-click="${opts.click}"` : ""} style="${opts.click ? "cursor:pointer" : ""}">` +
      `<div class="k">${ic}<span>${k}</span></div>` +
      `<div class="v ${opts.vcls || ""}"><span>${v}</span>${chev}</div></div>`;
  }

  /**
   * 开关行。hint 是可选的第二行说明（默认没有，保持其它开关的原样）。
   * 说明放在 row **内部**并让它换行，这样分隔线仍在整行下方 —— 放到 row 外面
   * 会出现「先一条线、再一行小字」的割裂感。
   */
  function rowSwitch(label, key, on, icon, hint) {
    const ic = icon ? `<span class="mini-icon" style="background:${icon[1]}">${icon[0]}</span>` : "";
    const hh = hint
      ? `<div style="flex-basis:100%;font-size:12px;color:var(--text3);margin-top:6px;line-height:1.5">${h(hint)}</div>`
      : "";
    return `<div class="row"${hint ? ' style="flex-wrap:wrap"' : ""}>` +
      `<div class="k">${ic}<span>${h(label)}</span></div>` +
      `<div class="switch${on ? " on" : ""}" data-setting="${key}"></div>${hh}</div>`;
  }

  function badge(text, cls) { return `<span class="badge ${cls}">${h(text)}</span>`; }

  /**
   * 节点行的国旗 —— 与安卓端 FlagPlaceholder 同一批图（257 个 SVG）。
   *
   * 图是随包资源（`src/assets/flags/<小写码>.svg`），相对路径解析，
   * `img-src 'self'` 里的 self 就是 file://，本地相对路径属于 self。
   *
   * 真正的取值与白名单在 `js/flags.js`（为什么不能在视图层拼 src，那里写了原因：
   * CSP 是 `script-src 'self'`，**行内 onerror 会被拒**，所以只能先判断再拼）。
   * 码不认识时这里返回空串，调用方退回原来的文字徽标 —— 宁可少一面旗，不给裂图。
   */
  function flagImg(code) {
    return window.PolarisFlags ? window.PolarisFlags.flagImg(code) : "";
  }

  /**
   * 只有地区**文本**时的兜底取码。
   *
   * 主进程 `nodeRecord()` 其实已经给了 `region_code`，正常路径用不到这里。
   * 但它有两个现实理由：
   * 1. 早前版本的 `state.nodes` 快照里可能只有 `region`，升级途中会短暂命中；
   * 2. `state.node` 是内核回读的**节点名**，节点页顶部那条「当前节点」没有 code 字段，
   *    这里由文本反查就能一并显示旗子。
   *
   * 只覆盖 `region.js` 的 FLAG_LABEL 里真实存在的中文名；查不到就返回空串
   * （`flagImg` 会对空串返回空，最终就是没有旗子 —— 不会裂图）。
   * `SW`/`UK` 已由主进程归一成 `CH`/`GB`，这里不必再列。
   */
  const LABEL_CODE = {
    "香港": "HK", "台湾": "TW", "澳门": "MO", "日本": "JP", "韩国": "KR", "新加坡": "SG",
    "美国": "US", "英国": "GB", "德国": "DE", "法国": "FR", "荷兰": "NL", "加拿大": "CA",
    "澳大利亚": "AU", "俄罗斯": "RU", "土耳其": "TR", "印度": "IN", "印度尼西亚": "ID",
    "马来西亚": "MY", "泰国": "TH", "越南": "VN", "菲律宾": "PH", "巴西": "BR", "阿根廷": "AR",
    "意大利": "IT", "西班牙": "ES", "葡萄牙": "PT", "瑞典": "SE", "挪威": "NO", "芬兰": "FI",
    "丹麦": "DK", "瑞士": "CH", "奥地利": "AT", "比利时": "BE", "爱尔兰": "IE", "波兰": "PL",
    "捷克": "CZ", "乌克兰": "UA", "阿联酋": "AE", "沙特": "SA", "以色列": "IL", "南非": "ZA",
    "新西兰": "NZ", "墨西哥": "MX", "智利": "CL", "哥伦比亚": "CO", "中国": "CN",
  };

  /** 取地区码：优先用主进程给的 `region_code`，没有才由文本反查。 */
  function codeOf(rec) {
    if (!rec) return "";
    if (rec.region_code) return rec.region_code;
    return LABEL_CODE[rec.region || ""] || "";
  }

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

  /**
   * 摘要取不到时的可见文案（硬要求②）：
   *   - 说清「取不到摘要 ≠ 包有问题，但本次没有校验」；
   *   - 连续 N（= 主进程 ipc 里的 SHA_MISSING_ESCALATE，3）次未校验 → 换更醒目的说法；
   *   - 结果落盘（settings），所以**事后/重启后仍能复查**，不是弹窗一闪而过。
   */
  function shaNotice(state, reason, streak) {
    const st = String(state || "ok");
    if (st === "ok" || !state) return "";
    const n = Number(streak) || 0;
    const why = reason ? "（" + reason + "）" : "";
    if (n >= 3) return err("连续 " + n + " 次未做包校验：取不到发布侧摘要，包本身可能没问题，但请留意下载来源" + why);
    return err("本次未做包校验：取不到发布侧摘要 ≠ 包有问题，但本次没有校验" + why);
  }

  /**
   * 「检查更新失败」怎么跟用户说（分档）。空值 → ''（没有失败）。
   * 接受两种输入：IPC 返回的 {kind,status}，或落盘的码字符串 'net' / 'http:403' / 'parse' / 'config'。
   * 为什么要分档：以前四条来源的失败都被吞掉，界面只会说「已是最新版本」——
   * 用户以为自己是最新的，其实这次根本没查成（假绿）。
   */
  function checkFailText(cf) {
    if (!cf) return '';
    let kind = ''; let status = 0;
    if (typeof cf === 'string') {
      const p = String(cf).split(':');
      kind = p[0] || ''; status = Number(p[1]) || 0;
    } else {
      kind = String(cf.kind || ''); status = Number(cf.status || 0);
    }
    if (kind === 'http' && status === 403) return '检查更新失败：GitHub 接口限流（403），等几分钟或换个网络再试';
    if (kind === 'http') return '检查更新失败：更新接口返回 HTTP ' + (status || '未知');
    if (kind === 'parse') return '检查更新失败：拿到的不是合法发布信息（可能被代理页或劫持页替换）';
    if (kind === 'net') return '检查更新失败：连不上 GitHub —— 多半是当前节点或网络导致，换个网络或断开代理再试';
    if (kind === 'config') return '检查更新失败：更新接口地址不合法（远程配置可能被写坏）';
    return '检查更新失败：原因未知，可在「关于」里导出日志';
  }
  const Views = {};
  Views.checkFailText = checkFailText;   // app.js 弹 toast 用同一份文案

  /* ---------------- 首页 ---------------- */
  Views.home = (s) => {
    // 没有节点时不能点连接：点了只会弹「还没有节点数据，请先在「节点」页点「刷新订阅」」
    // （用户日志里连着出现好几次）。直接把按钮禁掉并把原因写在状态行上，比让用户去撞错误更好。
    const noNodes = !(s.nodes && s.nodes.length);
    const stText = s.busy ? "连接中…" : noNodes ? "还没有节点" : s.connected ? "已连接" : "未连接";
    const stHint = !s.busy && noNodes
      ? '<div class="node-line">先在「节点」页点「刷新订阅」拉取节点</div>'
      : s.connected ? '<div class="node-line">' + h(s.node || "未选择节点") + (s.latency ? " · " + s.latency + "ms" : "") + "</div>" : "";
    return `
  <div class="col-narrow">
    <div class="hero">
      <button class="power-btn${s.busy ? " busy" : ""}" id="power" title="${noNodes ? "还没有节点，请先刷新订阅" : s.connected ? "断开" : "连接"}"${s.busy || noNodes ? " disabled" : ""}>
        <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.4" stroke-linecap="round"><path d="M12 3v9"/><path d="M6.3 6.5a8 8 0 1 0 11.4 0"/></svg>
      </button>
      <div class="status-line">${s.connected ? '<span class="dot"></span>' : ""}<span class="t">${stText}</span></div>
      ${stHint}
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
        <div style="font-size:14px">已使用 <b>${h(planReady(s) ? planUsed(s) : "—")}</b> / ${h(planReady(s) ? planTotal(s) : "—")}</div>
        <button class="btn btn-primary btn-sm" data-nav="plans">续费</button>
      </div>
      <div class="progress"><div style="width:${planReady(s) ? fmt.percent(s.plan.used, s.plan.total) : 0}%"></div></div>
      <div style="font-size:13px;color:var(--text3);margin-top:10px">到期时间 ${h(planReady(s) ? (s.plan.expire || "—") : "读取中…")}</div>
      ${!s.plan.name && s.panelError ? `<div style="font-size:13px;color:var(--orange);margin-top:6px">${h(s.panelError)}</div>` : ""}
    </div>
  </div>`;
  };

  /* ---------------- 节点 ---------------- */
  // 主组里的前几个成员不是节点，而是内核的特殊出口：自动选择（url-test，内核每 300s
  // 自己挑最快的）、故障转移（fallback，第一个不通就换下一个）、DIRECT（直连）、REJECT（拦截）。
  // 它们**当然生效**（见 scripts/real-test.js 的「三个特殊出口真的生效」一段），
  // 但延迟对它们没意义 —— 直连没有延迟，自动选择/故障转移的延迟得等内核的健康检查。
  // 显示成「其他 / 未测」会让人以为坏了（用户报的就是这个），所以按种类单独给标签：
  // 策略组 → 「策略组 / 自动」，直连拦截 → 「不需要」（与安卓端 MemberRow 同口径）。

  // 节点页 = 策略组手风琴：分组在**最上面**、可折叠、分组内直接点节点就切那个分组的出口、
  // 每个分组显示自己的延迟。主组就是本地生成的「🚀 节点选择」（与安卓端同名，见
  // electron/core/builder.js 的 SELECTOR_GROUP）—— 旧版自己另建一个叫「节点选择」的组，
  // 于是顶部多出一张重复的分类卡（用户实测报的 bug）。
  Views.nodes = (s) => {
    const q = (s.nodeFilter || "").toLowerCase();
    const hit = (v) => !q || String(v).toLowerCase().includes(q);
    // 结构组（兜底组 🐟 漏网之鱼、自动选择、故障转移、GLOBAL）只是配置骨架，不是给用户选的分类
    const groups = (Array.isArray(s.groups) ? s.groups : []).filter((g) => !g.builtin && !g.structural);
    const open = s.openGroups || {};
    // 进节点页时全部折叠，展开状态只认显式记过的那一个（用户第 12 轮第 4 条）。
    // 旧代码"没点过就默认展开第一个"，于是每次进页都自己弹开一张卡、切页回来也恢复不了。
    const isOpen = (name) => !!open[name];

    // 成员名的种类（安卓端 KernelProxyGroup.memberKindOf 同口径）：
    // 名字同时也是策略组 → 组出口；DIRECT/REJECT 这类 → 内置出口；其余 → 真实节点。
    const groupNames = new Set((s.groups || []).map((g) => g.name));
    const primary = (s.groups || []).find((g) => g.primary);
    const primaryName = primary ? primary.name : "";

    /**
     * 一个成员行。选中态只信内核回读的分组出口 now —— 安卓端 NodeGroupCard 是
     * `selected = member.name == selectedName`，绝不做乐观更新。
     * 旧代码用的是全局 state.node，于是"外面写着 A、点开勾的却是 B"（用户第 12 轮第 1 条）。
     */
    const memberRow = (name, now, groupName) => {
      const attr = `data-node="${h(name)}"${groupName ? ` data-node-group="${h(groupName)}"` : ""} style="cursor:pointer"`;
      const radio = `<span class="radio${name === now ? " sel" : ""}"></span>`;
      const exitLabel = EXIT_LABEL[name];
      if (exitLabel) {
        return `<div class="node-row" ${attr}><span class="nm">${h(exitLabel)}</span>${badge("不需要", "b-gray")}${radio}</div>`;
      }
      if (groupNames.has(name)) {
        // 主组作为出口时，安卓端的写法是「跟随「节点选择」」—— 比自己再画一遍组名清楚
        const label = name === primaryName ? `跟随「${name}」` : name;
        return `<div class="node-row" ${attr}><span class="nm">${h(label)}</span>${badge("策略组", "b-blue")}${badge("自动", "b-gray")}${radio}</div>`;
      }
      const n = s.nodes.find((x) => x.name === name) || { name, region: "", latency: -1, offline: false };
      return `<div class="node-row" ${attr}>${flagImg(codeOf(n))}<span class="nm">${h(n.name)}</span>${badge(n.region, "b-blue")}${latBadge(n.latency, n.offline)}${radio}</div>`;
    };

    // 组内成员：与安卓端 visibleGroupMembers 一致 —— 去掉「故障转移」这个入口
    // （每个组里它都排在自动选择后面，点它等于把整个组交给 fallback，用户第 12 轮第 2 条），
    // 除非当前出口正好就是它。顺序保持内核给的顺序（= 网站下发的订阅顺序）。
    const memberNames = (g) => (g.options || [])
      .filter((n) => n !== g.name && !(n === "故障转移" && g.now !== "故障转移"));

    const flat = s.nodes.filter((n) => hit(n.name) || hit(n.region));
    const cards = groups.length
      ? groups.map((g) => {
        const opened = isOpen(g.name);
        const members = memberNames(g)
          .filter((name) => hit(name) || hit((s.nodes.find((x) => x.name === name) || {}).region))
          .map((name) => memberRow(name, g.now, g.name));
        return `<div class="card" style="margin-bottom:14px">
          <div class="acc-head" data-acc="${h(g.name)}"${g.primary ? ' data-acc-primary="1"' : ""} style="cursor:pointer">
            <div><div class="acc-title" style="font-size:15px">${h(g.name)}</div>
            <div class="acc-sub">${g.count ? g.count + " 个可选出口 · " : ""}当前：${h(g.now || "—")}</div></div>
            <div style="display:flex;align-items:center;gap:10px">${groupBadge(g)}
            <span class="chev${opened ? " open" : ""}" style="font-size:20px">›</span></div>
          </div>
          ${opened ? `<div class="acc-body">${members.length ? members.join("") : empty("没有匹配的节点", "换个关键词试试")}</div>` : ""}
        </div>`;
      }).join("")
      // 未连接时内核还没起，拿不到策略组 —— 退回本地配置预览的扁平列表
      : `<div class="card" style="margin-bottom:14px">
          <div class="acc-head"><div><div class="acc-title">节点选择</div><div class="acc-sub">当前：${h(s.node || "未选择")}</div></div></div>
          ${flat.length ? flat.map((n) => memberRow(n.name, s.node, "")).join("")
            : (s.nodes.length ? empty("没有匹配的节点", "换个关键词试试")
              : empty("还没有节点数据", s.subError || "点右上角「刷新订阅」拉取节点"))}
        </div>`;

    return head("节点", `${s.nodes.length} 个节点${groups.length ? " · " + groups.length + " 个分组" : ""}`,
      `<span class="search-wrap"><input class="search" id="node-search" placeholder="搜索节点" value="${h(s.nodeFilter)}"></span>` +
      `<button class="btn btn-outline btn-sm" style="height:40px" data-click="nav-routing">分流规则</button>` +
      `<button class="btn btn-outline btn-sm" style="height:40px" id="btn-speedtest"${s.testing ? " disabled" : ""}>${s.testing ? "测试中…" : "延迟测试"}</button>` +
      `<button class="btn btn-primary btn-sm" style="height:40px" id="btn-refresh-sub"${s.refreshing ? " disabled" : ""}>${s.refreshing ? "刷新中…" : "刷新订阅"}</button>`) + cards;
  };

  /* ---------------- 流量 ---------------- */
    /**
     * 流量页：只保留「本月」一种口径，结构照安卓端（V5TrafficScreen）：合计 → 每日柱状图 → 每日明细。
     *
     * 为什么砍掉 今日 / 本周 / 本月 / 全部 四个切换（用户反馈）：
     *  · 站点明细本来就是按天聚合的，一天就一根柱，切「今日 / 本周」并不能多出信息；
     *  · 记录只跨一两周时四个按钮给出来的数字完全一样（用户截图里「今日」和「本周」
     *    都是 3.62 GB / 5.71 GB，「本月」和「全部」都是 121 GB / 55.3 GB），看着像按钮坏了；
     *  · 旧折线图纵轴只有「0」和一个单位、没有任何刻度，线全程贴着底走，末端一个尖峰。
     *
     * 数据来源：面板的站点流量明细优先（能看到装软件之前的历史，正是用户要的）；
     * 面板没有明细接口（xiaov2b）或没登录时退回本机每日统计，并在标题上写明用的是哪个来源。
     */
    Views.traffic = (s) => {
      const t = s.traffic || {};
      const acct = t.account || null;

      const now = new Date();
      const mo = now.getMonth();
      const dom = now.getDate();
      const key0 = (d) => `${now.getFullYear()}-${String(mo + 1).padStart(2, "0")}-${String(d).padStart(2, "0")}`;
      const recByDate = new Map((s.trafficLog || []).map((r) => [String(r.date), r]));
      const locPts = ((s.trafficSeries || {}).points) || [];
      const locByLabel = new Map(locPts.map((p) => [String(p.label), p]));
      const useSite = recByDate.size > 0;      // 面板明细优先
      // 面板明细的新鲜度 / 失败原因（ipc.js get_traffic 带回来）
      const tInfo = s.traffic || {};
      const siteAt = Number(tInfo.site_at) || 0;
      const siteErr = String(tInfo.site_error || "");
      const siteStale = !!tInfo.site_stale;
      const hhmm = (ms) => { const d = new Date(ms); return String(d.getHours()).padStart(2, "0") + ":" + String(d.getMinutes()).padStart(2, "0"); };
      const authed = !!(s.settings && s.settings.authed);
      // 面板明细接口存在（xboard）且已登录 → 本机统计**不能**顶替面板数字。
      // 旧版在这里静默退回本机统计，于是页面是「本机当天 6.03 MB」、标题却是
      // 「10 月 1 日起 · 共 12 天」，用户看到的就是驴唇不对马嘴（2026-10-12 截图）。
      const localOk = !(tInfo.site_log !== false && authed);
      const panelDead = !localOk && !useSite;
      // 1 号到今天逐天建轴，面板没给的那天补 0 —— 少了这天，整根柱子就凭空消失
      const days = [];
      for (let i = 1; i <= dom; i += 1) {
        const rec = recByDate.get(key0(i));
        const loc = locByLabel.get(`${mo + 1}/${i}`);
        const down = useSite ? Number((rec && rec.download) || 0)
          : (localOk ? Math.round(Number((loc && loc.down) || 0) * 1048576) : 0);
        const up = useSite ? Number((rec && rec.upload) || 0)
          : (localOk ? Math.round(Number((loc && loc.up) || 0) * 1048576) : 0);
        days.push({ key: key0(i), label: `${mo + 1}/${i}`, down, up, total: down + up });
      }
      const totDown = days.reduce((a, x) => a + x.down, 0);
      const totUp = days.reduce((a, x) => a + x.up, 0);
      const totAll = totDown + totUp;
      const hasAny = totAll > 0;
      // 来源标签必须带天数：本月只有两天有明细时，「面板明细」看着像加载失败，
      // 标成「面板明细（2 天）」一眼就知道是数据本身就只有两天，不是程序没取到。
      const siteFrom = [...recByDate.keys()].sort()[0] || "";
      const siteTo = [...recByDate.keys()].sort().pop() || "";
      const srcLabel = useSite ? `面板明细（${recByDate.size} 天）`
        : (localOk && locPts.length ? "本机统计" : "");
      // ① 面板失败且连缓存都没有 → 明确报错，不给数字
      const deadCard = `<div class="card" style="margin-bottom:14px;border-left:3px solid var(--orange)">`
        + `<div style="font-size:14px;color:var(--text)">面板流量明细拉取失败</div>`
        + `<div style="font-size:13px;color:var(--text3);margin-top:4px">${h(siteErr || "面板请求超时或网络不通")}</div>`
        + `<div style="font-size:13px;color:var(--text3);margin-top:4px">本机统计的口径是"本机转发量"，和面板账号用量不是一回事，所以不拿它顶替。稍后重试即可。</div>`
        + `</div>`;
      // ② 有缓存但刷新失败 / 已过期 → 照常显示旧数据，但必须标明是什么时候的
      const notice = (useSite && (siteErr || siteStale))
        ? `<div class="card" style="margin-bottom:14px;border-left:3px solid var(--orange)">`
          + `<div style="font-size:13px;color:var(--text2)">${siteErr ? "面板流量明细刷新失败：" + h(siteErr) : "面板流量明细已超过 5 分钟未刷新"}`
          + `，下面是 ${siteAt ? hhmm(siteAt) : "上次"} 缓存下来的数据。</div></div>`
        : "";

      // —— 柱状图：纯色柱 + 峰值标注 + 零值短桩，不画虚线网格（安卓端 BarsChart 同款）——
      const W = 620, CH = 190, PX0 = 8, PX1 = W - 8;
      const BASE = CH - 26, TOP = 26;
      const n = days.length;
      const peak = days.reduce((a, x) => (x.total > a ? x.total : a), 0);
      const peakIdx = peak > 0 ? days.findIndex((x) => x.total === peak) : -1;
      const GAP = 3;
      const bw = Math.min(44, (PX1 - PX0 - GAP * (n - 1)) / n);
      const bx0 = PX0 + ((PX1 - PX0) - (bw * n + GAP * (n - 1))) / 2;   // 整组居中
      const bars = days.map((x, i) => {
        const bx = bx0 + i * (bw + GAP);
        if (x.total <= 0) {
          const st = 3;
          return `<rect x="${bx.toFixed(1)}" y="${BASE - st}" width="${bw.toFixed(1)}" height="${st}" rx="1.5" fill="#aeaeb2"/>`;
        }
        const hgt = Math.max(2, (x.total / peak) * (BASE - TOP));
        return `<rect x="${bx.toFixed(1)}" y="${(BASE - hgt).toFixed(1)}" width="${bw.toFixed(1)}" height="${hgt.toFixed(1)}" rx="3" fill="#1a73e8"/>`;
      }).join("");
      const ticks = [...new Set([0, Math.floor((n - 1) / 2), n - 1])];
      const tickSvg = ticks.map((i) =>
        `<text x="${(bx0 + i * (bw + GAP) + bw / 2).toFixed(1)}" y="${CH - 8}" text-anchor="middle">${h(days[i].label)}</text>`).join("");
      const peakSvg = peak > 0
        ? `<text x="${PX1}" y="${TOP - 9}" text-anchor="end">${h(fmt.bytes(peak))}</text>` : "";

      const chart = panelDead ? deadCard : hasAny
        ? `<svg id="traffic-chart" viewBox="0 0 ${W} ${CH}" width="100%" height="${CH}" font-size="11" fill="#aeaeb2">
          ${peakSvg}
          <line x1="${PX0}" y1="${BASE}" x2="${PX1}" y2="${BASE}" stroke="#aeaeb2" stroke-width="1"/>
          ${bars}
          ${tickSvg}
        </svg>
        <div style="font-size:12.5px;color:var(--text3);margin-top:2px">柱高 = 当天下载 + 上传合计；零用量日留短桩</div>`
        : empty("本月还没有用量记录", s.settings.authed
          ? "面板还没返回这个月的明细，或本机刚装上、还没跑过流量"
          : "登录面板后这里显示面板记录的逐日明细");

      const list = days.slice().reverse().map((x) =>
        row(h(x.key), `${fmt.bytes(x.down)} ↓　${fmt.bytes(x.up)} ↑`, { chev: false })).join("");

      return head("流量", "") + `
      ${notice}
      <div class="section-label">${mo + 1} 月 1 日起 · 共 ${n} 天${srcLabel ? ` · ${h(srcLabel)}` : ""}</div>
      ${useSite ? `<div class="section-label" style="margin-top:-8px;color:var(--text3)">面板区间 ${h(siteFrom)} ~ ${h(siteTo)}${siteFrom !== key0(1) ? ` · ${h(key0(1))} 起无明细` : ""}</div>` : ""}
      <div class="card" style="margin-bottom:14px">
        <div class="speed-row" style="margin:0 0 4px">
          <div class="speed-tile dn"><span><div class="lb">下载</div><div class="vl">${h(fmt.bytes(totDown))}</div></span></div>
          <div class="speed-tile up"><span><div class="lb">上传</div><div class="vl">${h(fmt.bytes(totUp))}</div></span></div>
        </div>
        ${row("本月合计", h(fmt.bytes(totAll)), { chev: false, vcls: "strong" })}
        ${row("区间", `${key0(1)} ~ ${key0(dom)}`, { chev: false })}
      </div>
      <div class="section-label">每日用量</div>
      <div class="card" style="margin-bottom:14px">${chart}</div>
      ${hasAny ? `<div class="section-label">每日明细（整月逐天）</div><div class="card" style="margin-bottom:14px">${list}</div>` : ""}
      <div class="section-label">面板累计</div>
      <div class="card">
        ${acct ? row("已用流量", `${h(acct.used_text || "—")} / ${h(acct.quota_text || "—")}`, { chev: false, vcls: "strong" })
          : row("总下载", h(t.total_down || "—"), { chev: false, vcls: "strong" })}
        ${acct ? row("套餐", h(acct.plan_name || "—"), { chev: false }) : row("总上传", h(t.total_up || "—"), { chev: false, vcls: "strong" })}
        ${acct && acct.expire ? row("到期", h(acct.expire), { chev: false }) : ""}
      </div>`;
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
    const note = g.note
      ? `<span style="font-size:11px;color:var(--text3);margin-top:2px">${h(g.note)}</span>`
      : "";
    const grip = o.drag
      ? `<span class="ruleset-grip" data-ruleset-grip="${h(g.name)}" title="按住拖动，调整匹配顺序">⠿</span>`
      : "";
    return `<div class="row ruleset-row" data-ruleset-pick="${h(g.name)}" data-ruleset-row="${h(g.name)}">
      <div class="k"><span style="display:flex;flex-direction:column"><span>${h(g.name)}</span>
      <span style="font-size:11px;color:var(--text3);margin-top:2px">${h(sub)}</span>${note}</span></div>
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
      // 被隐藏的自定义分流组：**每次读都可见**（判据是文件里存在 hidden 条目），不删数据
      (Array.isArray(rs.hidden) && rs.hidden.length
        ? `<div class="section-label">被隐藏的自定义分流组 · ${rs.hidden.length}</div>` +
          `<div class="card">` +
          `<div class="row"><div class="k"><span style="font-size:12px;color:var(--text3)">在 App 列表中暂时隐藏，配置仍保留在本地</span></div></div>` +
          rs.hidden.map((x) => `<div class="row"><div class="k"><span>${h(x && x.name ? x.name : "(无名)")}</span></div>` +
            `<div class="v"><span style="font-size:11px;color:var(--text3)">${x && x.reason === "reserved" ? "与内置组同名" : "名字或规则不可用"}</span></div></div>`).join("") +
          `</div>`
        : "") +
      `<div class="section-label">内置分流 · 规则库每 12 小时自动更新 · 已启用 ${onCount}/${rs.total || builtin.length}</div>` +
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
      ${rowSwitch("IPv6", "ipv6", s.settings.ipv6, null, "关闭后 IPv6 站点会直接打不开（直连域名解析到 IPv6 时整条失败），部分移动/教育网络尤其明显。默认保持打开。")}
    </div>
    <div class="section-label">启动与更新</div><div class="card">
      ${rowSwitch("开机自启动", "autostart", s.settings.autostart)}
      ${rowSwitch("自动检查更新", "auto_update", s.settings.auto_update)}
    </div>
    <div class="section-label">关于</div><div class="card">
      ${row("当前版本", h(s.settings.version), { chev: false })}
      ${(s.update_state && s.update_state.last_apply_result && s.update_state.last_apply_result.ok === false)
        ? err("上次更新失败" + (s.update_state.last_apply_result.rolled_back === true ? "，已回滚到旧版本：" : "，回滚未完成：") + (s.update_state.last_apply_result.reason || "未知原因"))
        : ""}
      ${(s.update_state && s.update_state.apply_incomplete)
        ? err("上次更新没跑完（" + (s.update_state.apply_incomplete.alive ? "helper 起来了但没留下结果" : "helper 根本没起来") + "，安装目录未改动）")
        : ""}
      ${row("检查更新", s.settings.update_available
        ? '<span class="dot-new"></span><span style="color:var(--blue)">有新版本 ' + h(s.settings.update_version) + "</span>"
        : (s.updateInfo && s.updateInfo.staged
          ? '<span class="dot-new"></span><span style="color:var(--blue)">有新版本 ' + h(s.updateInfo.version) + "（已下载）</span>"
          : '<span style="color:var(--blue)">检查更新</span>'), { chev: false, click: "check-update" })}
      ${s.settings.update_check_failed
        ? err(checkFailText(s.settings.update_check_failed) + "（上次检查于 " + new Date(Number(s.settings.update_checked_at) || Date.now()).toLocaleString() + "）")
        : ""}
      ${s.settings.update_available ? shaNotice(s.settings.update_sha_state, s.settings.update_sha_reason, s.settings.update_sha_missing_streak) : ""}
      ${(s.appInfo && s.appInfo.last_kernel_verify && s.appInfo.last_kernel_verify.state && s.appInfo.last_kernel_verify.state !== "ok")
        ? err("内核启动校验未执行（不计为通过）：" + (s.appInfo.last_kernel_verify.reason || "未知原因"))
        : ""}
      ${row("导出日志", "", { chev: false, click: "export-logs" })}
      ${row("开源许可", "GPL-3.0", { click: "license" })}
      ${row("运行模式", s.appInfo.portable ? "便携版（数据在程序目录）" : "标准版", { chev: false })}
      ${row("数据目录", h(s.appInfo.data_dir || ""), { chev: false })}
      ${row("管理员权限", s.appInfo.is_admin ? "已获得" : "未获得（TUN 模式需要）", { chev: false, vcls: s.appInfo.is_admin ? "strong" : "" })}
      ${(s.leftoverKernel && s.leftoverKernel.length)
        ? row("残留内核", s.leftoverKernel.map((x) => "PID " + x.pid).join("、") + "（上次退出没清掉）", { chev: true, click: "cleanup-leftover" })
        : ""}
    </div>
    <div style="text-align:center;font-size:12px;color:var(--text3);margin:18px 0 6px">Polaris ${h(s.settings.version)} · 不采集任何用户数据</div>
  </div>`;

  /* ---------------- 我的 ---------------- */
  // 与安卓端"我的"逐项对齐（名称与顺序都照手机 app 的「我的服务」列表）：
  // 订阅套餐 / 礼品卡兑换 / 我的订单 / 邀请返利 / 我的工单 / 公告通知 / Telegram。
  // Telegram 入口只在面板真下发了链接时才出现（安卓端同样取不到就隐藏）。
  // 分流规则在节点页（顶部分流规则按钮）、重新拉取订阅在节点页（刷新订阅），
  // 所以这里不再重复给入口；到期/流量提醒从设置页搬到了这里（提醒是"我的"自己的事）。
  Views.me = (s) => head("我的") + `<div class="col-narrow">
    <div class="card" style="margin-bottom:14px"><div style="display:flex;align-items:center;gap:14px">
      <div style="width:52px;height:52px;border-radius:50%;background:var(--blue-soft);display:flex;align-items:center;justify-content:center;font-size:20px;color:var(--blue);font-weight:700">${h((s.email || "?").slice(0, 1).toUpperCase())}</div>
      <div style="min-width:0"><div style="font-size:16px;font-weight:700;word-break:break-all">${h(s.email || (s.settings.authed ? "已登录" : "未登录"))}</div>
      <span class="badge b-blue" style="margin-top:6px;display:inline-block">${h(planReady(s) ? s.plan.name : (s.settings.authed ? "读取中…" : "未订阅"))}</span></div>
    </div></div>
    <div class="card" style="margin-bottom:14px">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px"><div style="font-size:14px;font-weight:700">当前套餐</div><button class="btn btn-primary btn-sm" data-nav="plans">续费</button></div>
      <div style="font-size:14px;margin-bottom:10px">已使用 <b>${h(planReady(s) ? planUsed(s) : "—")}</b> / ${h(planReady(s) ? planTotal(s) : "—")}</div>
      <div class="progress"><div style="width:${planReady(s) ? fmt.percent(s.plan.used, s.plan.total) : 0}%"></div></div>
      <div style="font-size:13px;color:var(--text3);margin-top:10px">到期时间 ${h(planReady(s) ? (s.plan.expire || "—") : "读取中…")}</div>
    </div>
    <div class="section-label">提醒</div><div class="card" style="margin-bottom:14px">
      ${rowSwitch("到期提醒", "expire_notify", s.settings.expire_notify, ["⏰", "#ff9f43"])}
      ${rowSwitch("流量提醒", "traffic_notify", s.settings.traffic_notify, ["📶", "#34c759"])}
      ${row("订阅链接", "查看", { icon: ["🔗", "#8e8e93"], click: "show-subscribe-url" })}
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

  /* ---------------- 订阅套餐 ---------------- */
  // 二级页标题必须与「我的」页里的入口文案逐字一致 —— 用户报过
  // 「外面是订阅套餐，点进去是购买套餐」（入口叫订阅套餐、页头写购买套餐）。
  Views.plans = (s) => backHead("订阅套餐", "",
    `<button class="btn btn-outline btn-sm" style="height:40px" data-nav="giftcard">🎁 礼品卡兑换</button>`) +
    (s.plans.length ? `<div class="plan-grid">` + s.plans.map((p) => {
      // 套餐说明是面板下发的 Markdown 原文（标题/列表/表格/链接），
      // 有正文就渲染正文，没有才退回自动摘要 feats。
      const body = p.content && String(p.content).trim()
        ? `<div class="md plan-md">${md(p.content)}</div>`
        : `<ul>${(p.feats || []).map((f) => `<li>${h(f)}</li>`).join("")}</ul>`;
      return `
    <div class="plan-card${p.hot ? " hot" : ""}">
      ${p.hot ? '<div class="ribbon">最受欢迎</div>' : ""}
      <h3>${h(p.name)}</h3><div class="price">¥${h(p.price)}<small>/${h(p.unit)}</small></div>
      ${body}
      <button class="btn ${p.hot ? "btn-primary" : "btn-outline"}" data-buy="${h(p.id)}">立即购买</button>
    </div>`;
    }).join("") + `</div>
    <div style="text-align:center;font-size:13px;color:var(--text3);margin-top:18px">支付在浏览器中完成，回到应用后点「我的订单」刷新状态</div>`
    : empty("暂无可购买的套餐", "面板未配置套餐"));

  /* ---------------- 订单 ---------------- */
  const ORDER_STATUS = { done: ["已完成", "b-green"], pending: ["待支付", "b-orange"], processing: ["处理中", "b-blue"], refunded: ["已退款", "b-gray"] };
  Views.orders = (s) => backHead("我的订单", "", `<button class="btn btn-outline btn-sm" style="height:40px" data-nav="plans">订阅套餐</button>`) +
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

  /* ---------------- 邀请返利 ---------------- */
  // 标题与「我的」页入口「邀请返利」保持一致
  Views.invite = (s) => backHead("邀请返利") + `<div class="col-narrow"><div class="card" style="text-align:center;padding:34px 30px">
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

  /* ---------------- 公告通知 ---------------- */
  Views.notices = (s) => backHead("公告通知") + (s.notices.length ? `<div class="card">` +
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
      ${shaNotice(u.sha_state, u.sha_reason, u.sha_streak)}
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
      <div class="md" style="max-height:340px;overflow:auto">${md(n.body || "（无内容）")}</div>
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
      ["system", "system", "走系统协议栈，性能更好，少数环境有兼容问题（默认）"],
      ["gvisor", "gvisor", "纯用户态，兼容性最好，性能略低"],
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

    /* 纯告知：只有一个"知道了"，没有红色危险按钮 —— 给「我们不替你动手」这种场景用
       （端口被占但认不出是谁：不提供关掉它的入口，免得用户以为点了就会关）。
       键名必须与上面的 notice（公告：标题 + 日期 + Markdown 正文）区分开：
       同一个对象字面量里重名的键，后面那个会把前面那个整个覆盖掉 —— 旧代码两个都叫
       notice，于是公告弹窗静默退化成这份纯文本版，日期与 Markdown 全丢。 */
    portNotice: (c) => `<div class="overlay" data-overlay><div class="dialog">
      <h2>${h(c.title)}</h2><div class="dsub">${h(c.body)}</div>
      ${c.hint ? `<div class="dsub" style="font-size:12px">${h(c.hint)}</div>` : ""}
      <button class="btn btn-primary" style="width:100%;height:46px;margin-top:6px" data-overlay-close>${h(c.yes || "知道了")}</button>
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
