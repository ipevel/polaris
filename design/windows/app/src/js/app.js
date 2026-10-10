/* 应用主逻辑：路由、全局状态、事件绑定、自绘标题栏、主进程状态订阅。 */
(function () {
  const api = window.PolarisAPI;
  const Views = window.PolarisViews;
  const Dialogs = window.PolarisDialogs;
  const fmt = window.PolarisFormat;
  const $ = (sel, el) => (el || document).querySelector(sel);
  const $$ = (sel, el) => Array.from((el || document).querySelectorAll(sel));

  const MAIN_ROUTES = ["home", "nodes", "traffic", "routing", "settings", "me"];
  const AUTH_ROUTES = ["login", "register", "forgot"];
  /** 有实时速率/连接态可刷新的页面（paintLive 只改这两个元素的文本） */
  const LIVE_ROUTES = ["home", "nodes", "traffic", "me"];

  const state = {
    route: "home",
    booted: false,
    loggedIn: false,
    busy: false,
    // 内核状态机相位（idle/starting/connected/stopping）——主进程一直在推，
    // 但旧代码只比它、从不存它，state.phase 恒为 undefined。
    phase: "idle",
    connected: false,
    node: "",
    latency: 0,
    up_speed: 0,
    down_speed: 0,
    up_total: "0 B",
    down_total: "0 B",
    uptime: "00:00:00",
    mode: "规则模式",
    plan: { name: "—", used: 0, total: 0, expire: "" },
    nodes: [],
    groups: [],
    rulesets: { groups: [], total: 0 },
    nodeFilter: "",
    openGroups: {},        // 节点页分组手风琴的展开状态（未记录时第一个分组默认展开）
    testing: false,
    refreshing: false,
    // 拉订阅失败的原因（节点页/流量页要拿它说清楚"为什么是空的"）。
    // 旧代码只 console.warn，用户看到的是"登录了但节点是空的"（用户第 11 轮第 1 条）。
    subError: "",
    redeeming: false,
    authError: "",
    giftError: "",
    traffic: {},
    trafficSeries: { points: [], unit: "MB" },
    trafficLog: [],
    trafficRange: "today",
    plans: [],
    orders: [],
    tickets: [],
    invite: { code: "", link: "", invited: 0, earned: "0", rate: 0 },
    giftHistory: [],
    notices: [],
    unreadNotices: 0,
    settings: {},
    appInfo: {},
    tunStatus: null,
    siteInfo: {},
    registerConfig: { email_verify: 0, invite_force: 0 },
    email: "",
    updateInfo: null,      // 最近一次 check_update 的结果（设置页那行要显示「有新版本 x.y.z」）
    checkingUpdate: false,
  };

  // 自检用：把渲染层的 state 整个暴露出去。只断言 DOM 存在性证明不了
  // "这页显示的是不是真数据"（套餐徽标、分组延迟、流量区间都在 state 里）。
  window.__polarisState = state;

  const content = $("#content");
  const overlayRoot = $("#overlay-root");
  const sidebar = $("#sidebar");

  /* ---------------- 渲染 ---------------- */
  let renderCount = 0;
  let lastRenderKey = null;      // 上一次真正写进 DOM 的内容（route + html）
  let dragClickGuard = false;    // 拖动后的"补一次 click"只装一次捕获监听
  let suppressClickUntil = 0;    // 拖动松手后到这个时间点为止的 click 一律吃掉
  // 指针按着的时候不换 DOM。一次 click = pointerdown → pointerup → click，中间要是
  // 来了一次真数据重绘（比如"延迟测试"跑完、状态刷新），鼠标松开时落点已经在**新**
  // 元素上，浏览器补发的 click 只能派给共同祖先（#content），元素上的处理器一次都
  // 不会被调用 —— 用户看到的就是"点了没反应"（实测：进入分流页后第一次点开关被吞，
  // 于是"关掉"没生效、"再打开"反而把它关了，整屏断言跟着连锁变红）。
  // 按下超过 5 秒视为卡住（指针移出窗口收不到 pointerup），自动放行，免得界面冻住。
  let pointerDownAt = 0;
  let pendingSwap = null;
  document.addEventListener("pointerdown", () => { pointerDownAt = Date.now(); }, true);
  const releasePointer = () => {
    pointerDownAt = 0;
    if (!pendingSwap) return;
    const p = pendingSwap;
    pendingSwap = null;
    // 排到 click 之后：浏览器在 pointerup 之后**同步**补发 click，setTimeout(0) 在它后面。
    setTimeout(() => {
      if (pointerDownAt) return;                 // 又按下了，等下一次松手
      lastRenderKey = p.key;
      content.innerHTML = p.html;
      p.bind();
    }, 0);
  };
  document.addEventListener("pointerup", releasePointer, true);
  document.addEventListener("pointercancel", releasePointer, true);
  function render() {
    // 自检用：设置页曾经因为 loadTunStatus→render 互相调用而无限重绘，
    // 只有数渲染次数才测得到（DOM 断言在同一个 JS 帧里看不出问题）。
    renderCount += 1;
    window.__polarisRenderCount = renderCount;
    // 自检/排查用：界面到底停在哪一页。历史上出现过"点了设置页其实还在首页"，
    // 只断言 DOM 存在性根本看不出来（首页也有 .row / .section-label）。
    window.__polarisRoute = state.route;
    overlayRoot.innerHTML = "";
    // 内容没变就不动 DOM。整块 innerHTML 重建会把用户正在点的元素换掉 ——
    // mousedown 与 click 之间被换掉，这一下点击就被吞了（实测：连上内核后
    // refreshAll 回来的那次重绘，正好吃掉"展开分组/拖动排序"的点击）。
    // 也让滚动位置和展开状态不会看起来"自己跳"。
    const swap = (html, bind) => {
      const key = state.route + "\u0000" + html;
      if (key === lastRenderKey) return;
      if (pointerDownAt && Date.now() - pointerDownAt < 5000) {   // 按着不放：等松手再换
        pendingSwap = { key, html, bind };
        return;
      }
      lastRenderKey = key;
      content.innerHTML = html;
      bind();
    };
    if (AUTH_ROUTES.includes(state.route)) {
      state.loggedIn = false;
      sidebar.style.display = "none";
      swap((Views[state.route] || Views.login)(state), bindAuth);
      return;
    }
    sidebar.style.display = "";
    swap((Views[state.route] || Views.home)(state), () => {
      $$(".nav-item").forEach((el) => {
        const r = el.dataset.route;
        const on = r === state.route || (state.route === "routing" && r === "nodes");
        el.classList.toggle("active", on);
      });
      bindCommon();
      ({
        nodes: bindNodes, settings: bindSettings, me: bindMe, plans: bindPlans,
        orders: bindOrders, tickets: bindTickets, invite: bindInvite,
        giftcard: bindGiftcard, notices: bindNotices, routing: bindRouting,
        traffic: bindTraffic, home: bindHome,
      }[state.route] || (() => {}))();
    });
  }

  // 返回栈：二级页面（套餐/订单/工单/邀请/礼品卡/公告/分流规则）的返回键回到
  // 进来的那一页。只记"主页面"，避免 我的→订单→购买套餐→礼品卡 之后要按四次。
  const PRIMARY_ROUTES = ["home", "nodes", "traffic", "me", "settings"];
  let navStack = [];

  /**
   * 记一条路由流水到 window.__navLog（最近 30 条）。
   * 用途只有一个：界面"自己跳到别的页"时，自检失败信息里能直接看到是谁调的 ——
   * 只报最终状态（"我在首页"）是查不出调用点的。生产路径零影响（try/catch 吞掉一切）。
   */
  function noteNav(from, to, by) {
    try {
      if (!window.__navLog) window.__navLog = [];
      window.__navLog.push({ at: Date.now(), from: from, to: to,
        by: String(by || "").trim().slice(0, 120) });
      if (window.__navLog.length > 30) window.__navLog.shift();
    } catch (_) {}
  }

  function nav(route) {
    if (route === "register" || route === "forgot" || route === "login") state.authError = "";
    const prev = state.route;
    // 路由流水（自检失败时用）：界面"自己跳到别的页"这种问题，只有把每次跳转的
    // 调用点记下来才查得动 —— 光看最终状态是"我在首页"，看不出是谁把页面挪走的。
    noteNav(prev, route, (new Error().stack || "").split("\n")[2] || "");
    if (route !== prev) {
      if (PRIMARY_ROUTES.includes(prev)) navStack = [prev];
      else if (navStack[navStack.length - 1] !== prev) navStack.push(prev);
      // 回主页面时栈清掉，免得下次进二级页还带着上上上页
      if (PRIMARY_ROUTES.includes(route)) navStack = [];
    }
    state.route = route;
    render();
    // 虚拟网卡状态要起 PowerShell（冷启动实测 ~2.7s），只在进设置页时查一次。
    // 绝不能放在 bindSettings 里 —— 那会变成 render→loadTunStatus→render 死循环。
    if (route === "settings") loadTunStatus();
  }
  window.PolarisNav = nav;

  /** 二级页面返回：没有历史就回"我的" */
  function goBack() {
    const target = navStack.length ? navStack.pop() : "me";
    noteNav(state.route, target, "goBack()");
    state.route = target;
    render();
    if (target === "settings") loadTunStatus();
  }

  function openDialog(name, arg) {
    const tpl = Dialogs[name];
    if (!tpl) return;
    overlayRoot.innerHTML = typeof tpl === "function" ? tpl(arg === undefined ? state : arg) : tpl;
    $$("[data-overlay]", overlayRoot).forEach((o) => {
      o.addEventListener("mousedown", (e) => { if (e.target === o) closeDialog(); });
    });
    $$("[data-overlay-close]", overlayRoot).forEach((b) => b.addEventListener("click", closeDialog));
    bindDialog(name, arg);
  }
  function closeDialog() { overlayRoot.innerHTML = ""; }
  window.PolarisDialog = { open: openDialog, close: closeDialog };

  function toast(msg) {
    const d = document.createElement("div");
    d.textContent = msg;
    d.className = "toast";
    document.body.appendChild(d);
    setTimeout(() => d.remove(), 2400);
  }
  window.PolarisToast = toast;

  /** 统一的失败处理：真异常弹 toast，不要让整页白掉 */
  async function guard(label, fn) {
    try {
      return await fn();
    } catch (e) {
      const msg = (e && e.message) ? e.message : String(e);
      // 这是「已处理」的失败（下面会弹 toast），用 warn 而不是 error：
      // 否则断网/面板挂掉时满屏 error，真正的未捕获异常被淹掉，自检也没法拿
      // 「有没有 console error」当异常信号。
      console.warn(label, e);
      toast(label + "失败：" + msg);
      return null;
    }
  }

  /* ---------------- 通用事件 ---------------- */
  function bindCommon() {
    $$("[data-nav]").forEach((el) => el.addEventListener("click", (e) => { e.stopPropagation(); nav(el.dataset.nav); }));
    $$("[data-click]").forEach((el) => el.addEventListener("click", () => handleClick(el.dataset.click, el)));
    $$("[data-range]").forEach((el) => el.addEventListener("click", async () => {
      state.trafficRange = el.dataset.range;
      // 先立刻重绘：按钮高亮要马上跟着手指走，不能等面板那十几秒的明细回来
      // （用户点了"本周"没反应，看起来像按钮坏了）。
      render();
      await loadTraffic();
      render();
    }));
    $$("[data-group]").forEach((el) => el.addEventListener("click", () => {
      const g = state.groups.find((x) => x.name === el.dataset.group);
      if (g) openDialog("groupPick", g);
    }));
    // 二级页面的返回键（用户报「基本上所有二级页面都没有返回按钮」）
    $$("[data-nav-back]").forEach((el) => el.addEventListener("click", (e) => { e.stopPropagation(); goBack(); }));
    // 节点页的分组手风琴：点标题栏展开/收起
    $$("[data-acc]").forEach((el) => el.addEventListener("click", () => {
      const name = el.dataset.acc;
      const open = state.openGroups || (state.openGroups = {});
      // 没记录过时第一个分组是展开的，点它第一次应该是"收起"
      const cur = open[name] === undefined ? state.groups.filter((g) => !g.builtin && !g.structural)[0] : null;
      const isOpen = open[name] === undefined ? (cur && cur.name === name) : !!open[name];
      open[name] = !isOpen;
      render();
    }));
    bindMarkdownLinks(document);
  }

  /**
   * Markdown 正文里的链接（公告、套餐说明）。渲染层不自己开浏览器 ——
   * 把 URL 交给主进程的 open_external（只放行 http/https），与「前往下载」同一条路。
   * 页面与弹窗都要绑，所以抽出来。
   */
  function bindMarkdownLinks(scope) {
    $$(".md-link", scope).forEach((a) => a.addEventListener("click", async (e) => {
      e.preventDefault();
      e.stopPropagation();
      const url = a.dataset.mdlink;
      if (!url) return;
      await guard("打开链接", () => api.openExternal(url));
    }));
  }

  async function handleClick(action) {
    if (action === "proxy-mode") openDialog("proxy", state);
    else if (action === "check-update") {
      const u = await guard("检查更新", () => api.checkUpdate());
      if (!u) return;
      if (u.has_update) {
        lastUpdate = Object.assign({ current: state.settings.version }, u);
        state.updateInfo = u;
        openDialog("update", lastUpdate);
      } else if (u.staged) {
        // 上次下好了没装：版本没变也要让用户能装
        lastUpdate = Object.assign({ current: state.settings.version, has_update: true, version: u.version }, u);
        openDialog("update", lastUpdate);
      } else toast("已是最新版本 " + state.settings.version);
    } else if (action === "logout") openDialog("logoutConfirm", state);
    else if (action === "change-password") openDialog("changePassword", state);
    else if (action === "set-theme") openDialog("appearance", state);
    else if (action === "set-lang") openDialog("language", state);
    else if (action === "set-tun-stack") openDialog("tunStack", state);
    else if (action === "set-port") openDialog("port", state.settings);
    else if (action === "export-logs") {
      const r = await guard("导出日志", () => api.exportLogs());
      if (r) toast(r.ok ? "日志已导出到 " + r.path : "已取消");
    } else if (action === "license") openDialog("about", state.appInfo);
    else if (action === "cleanup-tun") {
      const r = await guard("清理虚拟网卡", () => api.invoke("cleanup_tun"));
      if (r) { toast(r.msg || "已处理"); loadTunStatus(true); }
    }
    else if (action === "open-telegram") {
      // 链接由主进程现取现校验（域名白名单），渲染层不传 URL
      const r = await guard("打开 Telegram", () => api.invoke("open_telegram"));
      if (r && r.ok === false) toast(r.msg || "面板没有配置 Telegram 群组");
    } else if (action === "show-subscribe-url") {
      const info = await guard("获取订阅链接", () => api.invoke("get_subscribe_url"));
      if (info) openDialog("subscribeUrl", info);
    }
    // 所有 data-click="nav-xxx" 都是"去某一页"。旧代码在这里写死了一份白名单，
    // 加了「我的套餐」入口却忘了把 nav-plans 补进去 —— 于是那一行点了完全没反应，
    // 而且不报错、不弹窗，看起来像"面板没数据"。改成前缀判断，不再漏。
    else if (action.startsWith("nav-")) nav(action.slice(4));
  }

  function errBox(sel) {
    return $(sel || "#auth-err", overlayRoot) || $("#auth-err");
  }

  function bindDialog(name, arg) {
    if (name === "proxy") {
      $$("[data-mode]", overlayRoot).forEach((el) => el.addEventListener("click", async () => {
        const r = await guard("切换模式", () => api.setProxyMode(el.dataset.mode));
        if (r) { state.mode = r.mode; closeDialog(); toast("已切换为" + r.mode); }
        render();
      }));
    }
    if (name === "update") {
      let btn = $("#btn-open-download");
      if (btn) btn.addEventListener("click", async () => {
        // 旧代码写的是 api.openExternal(arg && arg.version ? "" : "") —— 恒传空串，
        // open_external 必然抛「只允许打开 http(s) 链接」，用户每次都先吃一条红 toast。
        // 现在：拿到具体地址就直接开，拿不到才退回主进程按配置解析。
        const direct = String((arg && arg.url) || "").trim();
        const r = direct
          ? await guard("打开下载页", () => api.openExternal(direct))
          : await guard("打开下载页", () => api.invoke("open_download"));
        if (r && r.ok === false) { toast(r.msg || "未配置更新地址"); return; }
        if (r) closeDialog();
      });

      btn = $("#btn-download-update");
      if (btn) btn.addEventListener("click", async () => {
        const direct = String((arg && arg.url) || "").trim();
        paintUpdate({ phase: "downloading", percent: 0, received: 0, total: 0 });
        const r = await guard("下载更新", () => api.downloadUpdate(direct, arg && arg.version));
        // 成功时主进程已经推过 staged 事件、弹窗被重画成「重启并安装」，
        // 这里只处理失败：把进度条收起来（错误提示由 guard 统一弹，不重复弹一条）。
        if (!r || r.phase !== "staged") {
          paintUpdate(null);
          if (r && r.error) toast(r.error);
        }
      });

      btn = $("#btn-apply-update");
      if (btn) btn.addEventListener("click", async () => {
        const r = await guard("安装更新", () => api.applyUpdate());
        if (!r) return;
        paintUpdate({ phase: "applying", percent: 100, received: 0, total: 0 });
        toast("正在退出并安装，稍后会自动重启");
      });
    }
    if (name === "port") {
      const box = errBox("#dialog-err");
      $("#btn-save-port").addEventListener("click", async () => {
        if (box) box.textContent = "";
        const el = $("#port-value");
        const raw = (el && el.value || "").trim();
        const next = raw === "" ? 0 : Number(raw);
        const was = state.settings.mixed_port || 0;
        const r = await guard("保存端口", () => api.setSetting("mixed_port", next));
        if (!r) return;
        state.settings.mixed_port = r.mixed_port || 0;
        closeDialog();
        render();
        toast(r.mixed_port ? `本机代理端口已设为 ${r.mixed_port}` : "本机代理端口已改为自动");
        // 用户第 9 条：端口改了要跟「连接」同步，别让用户自己记得断开重连
        if (state.connected && r.mixed_port !== was) {
          openDialog("confirm", {
            title: "端口已保存",
            body: `本机代理端口已经改成 ${r.mixed_port || "自动"}，现在内核还在用 ${state.settings.running_port || "旧端口"}。断开重连一次让它生效吗？`,
            yes: "断开并重连",
            onYes: async () => {
              try {
                await api.disconnect();
                state.connected = false;
              } catch (e) {
                toast("断开失败：" + (e.message || e));
                render();
                return;
              }
              await connectAndRefresh();
            },
          });
        }
      });
    }
    if (name === "customRuleset") {
      const a = arg || {};
      const box = errBox("#dialog-err");
      const outOf = () => {
        const sel = $("[data-cr-out].sel", overlayRoot);
        return sel ? sel.dataset.crOut : "proxy";
      };
      $$("[data-cr-out]", overlayRoot).forEach((el) => el.addEventListener("click", () => {
        $$("[data-cr-out]", overlayRoot).forEach((o) => {
          o.classList.toggle("sel", o === el);
          const r = o.querySelector(".radio");
          if (r) r.classList.toggle("sel", o === el);
        });
      }));
      $("#btn-save-custom").addEventListener("click", async () => {
        if (box) box.textContent = "";
        const nameEl = $("#cr-name");
        const name = nameEl ? nameEl.value.trim() : "";
        const rules = ($("#cr-rules").value || "").split("\n").map((x) => x.trim()).filter(Boolean);
        if (!name) { if (box) box.textContent = "请填写分组名字"; return; }
        if (!rules.length) { if (box) box.textContent = "至少写一条规则"; return; }
        const r = await guard("保存分流组", () => api.saveCustomRuleset({
          name, out: outOf(), rules, original: a.name || "",
        }));
        if (!r) return;
        await refreshRoutes();
        closeDialog();
        render();
        toast(r.renamed ? `已保存为「${r.name}」` : `分流组「${r.name}」已保存`);
      });
      const del = $("#btn-delete-custom");
      if (del) del.addEventListener("click", () => {
        const nm = a.name;
        openDialog("confirm", {
          title: "删除分流组", body: `「${nm}」的规则会被移除，连接中的内核会热重载配置。`, yes: "删除",
          onYes: async () => {
            const r = await guard("删除分流组", () => api.deleteCustomRuleset(nm));
            if (!r) return;
            await refreshRoutes();
            render();
            toast(`已删除「${nm}」`);
          },
        });
      });
    }
    if (name === "logoutConfirm") {
      $("#btn-logout-confirm").addEventListener("click", async () => {
        await guard("退出登录", () => api.logout());
        closeDialog();
        Object.assign(state, { loggedIn: false, connected: false, node: "", nodes: [], groups: [] });
        state.settings = Object.assign({}, state.settings, { authed: false });
        nav("login");
      });
    }
    if (name === "newTicket") {
      const box = $("#tk-level");
      if (box) box.addEventListener("click", (e) => {
        const opt = e.target.closest("[data-tk-level]");
        if (!opt) return;
        box.querySelectorAll("[data-tk-level]").forEach((el) => {
          el.classList.toggle("sel", el === opt);
          const radio = el.querySelector(".radio");
          if (radio) radio.classList.toggle("sel", el === opt);
        });
      });
      $("#btn-submit-ticket").addEventListener("click", async () => {
        const subject = $("#ticket-subject").value.trim();
        const body = $("#ticket-content").value.trim();
        if (!subject) { toast("请填写标题"); return; }
        const sel = $('[data-tk-level].sel');
        const level = sel ? Number(sel.dataset.tkLevel) : 1;
        const r = await guard("提交工单", () => api.createTicket(subject, body, level));
        if (r) {
          state.tickets = await api.getTickets().catch(() => state.tickets);
          closeDialog(); render(); toast("工单已提交");
        }
      });
    }
    if (name === "changePassword") {
      $("#btn-change-pwd").addEventListener("click", async () => {
        const oldPwd = $("#cp-old").value;
        const p1 = $("#cp-new").value;
        const p2 = $("#cp-new2").value;
        const box = errBox("#dialog-err");
        if (p1.length < 8) { if (box) box.textContent = "新密码至少 8 位"; return; }
        if (p1 !== p2) { if (box) box.textContent = "两次输入的新密码不一致"; return; }
        const r = await guard("修改密码", () => api.changePassword(oldPwd, p1));
        if (r && r.ok) {
          closeDialog(); toast("密码已修改，请重新登录");
          await guard("退出登录", () => api.logout());
          Object.assign(state, { loggedIn: false, settings: Object.assign({}, state.settings, { authed: false }) });
          nav("login");
        } else if (box && r) box.textContent = r.msg || "修改失败";
      });
    }
    if (["appearance", "language", "tunStack"].includes(name)) {
      $$("[data-pick-value]", overlayRoot).forEach((el) => el.addEventListener("click", async () => {
        const key = el.dataset.pickKey;
        const value = el.dataset.pickValue;
        if (key === "tun") state.settings.tun = value;
        else state.settings[key] = value;
        await guard("保存设置", () => api.setSetting(key, value));
        if (key === "theme") applyTheme(value);
        closeDialog(); render();
      }));
    }
    if (name === "groupPick") {
      $$("[data-pick]", overlayRoot).forEach((el) => el.addEventListener("click", async () => {
        const r = await guard("切换出口", () => api.setRoutingGroup(arg.name, el.dataset.pick));
        if (r) {
          const g = state.groups.find((x) => x.name === arg.name);
          if (g) g.now = r.now || el.dataset.pick;
          closeDialog(); render(); toast("已切换为 " + (r.now || el.dataset.pick));
        }
      }));
    }
    if (name === "payment") {
      $$("[data-pay-method]", overlayRoot).forEach((el) => el.addEventListener("click", async () => {
        const r = await guard("发起支付", () => api.payOrder(arg.order_no, el.dataset.payMethod));
        closeDialog();
        if (r && r.url) toast("已在浏览器打开支付页");
        render();
      }));
    }
    if (name === "subscribeUrl") {
      const b = $("#btn-copy-sub");
      if (b) b.addEventListener("click", async () => {
        await copyText((arg && arg.url) || "");
        toast("订阅链接已复制");
      });
    }
    if (name === "confirm") {
      const b = $("#btn-confirm-yes");
      if (b && arg && typeof arg.onYes === "function") b.addEventListener("click", () => { closeDialog(); arg.onYes(); });
    }
    // 公告正文是 Markdown，里面的链接同样走主进程校验后再开浏览器
    bindMarkdownLinks(overlayRoot);
  }

  async function copyText(text) {
    if (!text) return;
    try {
      if (window.polaris && window.polaris.clipboard) await window.polaris.clipboard.writeText(text);
      else await navigator.clipboard.writeText(text);
    } catch (e) {
      toast("复制失败，请手动选择");
    }
  }

  /* ---------------- 各页事件 ---------------- */
  function bindHome() {
    const pw = $("#power");
    if (!pw) return;
    pw.addEventListener("click", async () => {
      if (state.busy) return;
      state.busy = true; render();
      let justConnected = false;
      try {
        if (state.connected) {
          await api.disconnect();
          state.connected = false;
          toast("已断开");
        } else {
          if (!state.settings.authed) { state.busy = false; nav("login"); return; }
          const st = await api.connect();
          state.connected = true;
          if (st && st.node) state.node = st.node;
          if (st && st.latency) state.latency = st.latency;
          justConnected = true;
          toast("已连接");
        }
        state.busy = false;
        // 先把已知的连接状态画出来（状态线 / 当前节点立刻更新），再去拉面板数据 ——
        // 面板那一轮要几秒，等它回来才重绘的话，用户会看到"已连接但首页还是未连接的样子"。
        render();
        await refreshAll();
      } catch (e) {
        state.busy = false;
        const msg = e.message || String(e);
        // 端口被别的软件占着 → 问一句要不要替用户请它走（用户第 10 条）
        if (!state.connected && /已被其它程序占用/.test(msg)) {
          await handlePortConflict();
          return;
        }
        toast((state.connected ? "断开" : "连接") + "失败：" + msg);
        await refreshStatus();
      }
      render();
      // 用户第 7 条：连上就自动测一次延迟，不用再进节点页手动点。
      // 连接期间不拉订阅（会和连接抢内核，见 autoRefreshSubscription 的注释与 DEVNOTES A-41），
      // 那次检查到这里没做，就交给 autoRefreshSubscription 自己的"忙完再看"补上。
      if (justConnected) runDelayTest(true);
    });
  }

  /**
   * 端口被别的程序占用（用户第 10 条）。
   * 先把「谁占着」摆出来（进程名 + PID），用户点了确认才请它退出；
   * 关掉之后顺手替用户重连一次，省得再点一下电源。
   */
  async function handlePortConflict() {
    // 端口可能是在别处改的（设置弹窗 / 主进程兜底），先问一次最新值再查占用者，
    // 不然会拿旧的 mixed_port 去查，查出来是"没人占用"，用户看到一句莫名其妙的话。
    state.settings = await api.getSettings().catch(() => state.settings);
    const port = (state.settings && state.settings.mixed_port) || 0;
    const owner = await guard("查看端口占用", () => api.portOwner(port));
    if (!owner || !owner.busy) {
      toast("端口还是被占着，但没查出是谁，请换个端口或手动关掉占用它的软件");
      await refreshStatus();
      render();
      return;
    }
    render();
    openDialog("confirm", {
      title: "本机代理端口被占用",
      body: `${port} 端口正被「${owner.name}」(PID ${owner.pid}) 占用，所以内核起不来。要关掉它并重连吗？`,
      yes: `关掉 ${owner.name}`,
      onYes: async () => {
        const r = await guard("关闭占用端口的程序", () => api.closePortOwner(owner.pid));
        if (!r || !r.closed) {
          toast(`没能关掉 ${owner.name}（可能需要管理员权限），请手动退出它或换个端口`);
          return;
        }
        toast(r.forced ? `${r.name} 没响应，已强制结束` : `已关闭 ${r.name}`);
        await connectAndRefresh();
      },
    });
  }

  /** 断开 → 连接 → 刷新，失败给一句人话（重连复用时少写一遍） */
  async function connectAndRefresh() {
    state.busy = true; render();
    try {
      const st = await api.connect();
      state.connected = true;
      if (st && st.node) state.node = st.node;
      if (st && st.latency) state.latency = st.latency;
      state.busy = false;
      render();                       // 先按已知状态立起界面，再去拉面板数据
      // 端口可能刚换过，重连后把运行端口也刷新一下（设置页要显示真实值）
      state.settings = await api.getSettings().catch(() => state.settings);
      await refreshAll();
      render();
      toast("已连接");
      runDelayTest(true);
    } catch (e) {
      state.busy = false;
      toast("连接失败：" + (e.message || e));
      await refreshStatus();
      render();
    }
  }

  function bindAuth() {
    const send = $("#btn-send-code");
    if (send) send.addEventListener("click", async () => {
      const email = ($("#reg-email") || $("#fg-email")).value.trim();
      if (!email) { toast("请先填写邮箱"); return; }
      const purpose = $("#fg-email") ? "forget" : "register";
      const r = await guard("发送验证码", () => api.sendEmailCode(email, purpose));
      if (r && r.ok) toast("验证码已发送");
      else if (r) { state.authError = r.msg; render(); }
      else toast("发送失败");
    });

    const login = $("#btn-login");
    if (login) {
      const submit = async () => {
        const panelUrl = $("#login-panel").value.trim();
        const email = $("#login-email").value.trim();
        const pass = $("#login-pass").value;
        state.authError = "";
        if (!/^https?:\/\//i.test(panelUrl) && !/^[a-z0-9.-]+\.[a-z]{2,}/i.test(panelUrl)) {
          state.authError = "请填写正确的面板地址"; render(); return;
        }
        if (!email || !pass) { state.authError = "请填写邮箱和密码"; render(); return; }
        login.disabled = true; login.textContent = "登录中…";
        const r = await guard("登录", () => api.login(email, pass, panelUrl));
        if (r && r.ok) {
          state.email = r.email || email;
          state.loggedIn = true;
          await refreshAll(true);
          nav("home");
          toast("登录成功");
          // 登录这一步就把节点数据准备好（用户第 11 轮第 1、4 条）。
          // 旧代码把这件事留给"点连接"，于是登录后节点页是空的、点连接变成"在拉订阅"。
          await ensureSubscription("login");
          render();
        } else {
          state.authError = (r && r.msg) || "登录失败";
          render();
        }
      };
      login.addEventListener("click", submit);
      $$("#login-email, #login-pass, #login-panel").forEach((el) =>
        el.addEventListener("keydown", (e) => { if (e.key === "Enter") submit(); }));
    }

    const reg = $("#btn-register");
    if (reg) reg.addEventListener("click", async () => {
      const email = $("#reg-email").value.trim();
      const code = $("#reg-code") ? $("#reg-code").value.trim() : "";
      const p1 = $("#reg-pass").value;
      const p2 = $("#reg-pass2").value;
      const invite = $("#reg-invite").value.trim();
      state.authError = "";
      if (!email) { state.authError = "请填写邮箱"; render(); return; }
      if (p1.length < 8) { state.authError = "密码至少 8 位"; render(); return; }
      if (p1 !== p2) { state.authError = "两次输入的密码不一致"; render(); return; }
      if (state.registerConfig.invite_force && !invite) { state.authError = "本站要求填写邀请码"; render(); return; }
      const r = await guard("注册", () => api.register(email, p1, code, invite));
      if (r && r.ok) {
        state.email = r.email || email;
        await refreshAll(true);
        nav("home"); toast("注册成功");
      } else { state.authError = (r && r.msg) || "注册失败"; render(); }
    });

    const fg = $("#btn-forgot");
    if (fg) fg.addEventListener("click", async () => {
      const email = $("#fg-email").value.trim();
      const code = $("#fg-code").value.trim();
      const pass = $("#fg-pass").value;
      state.authError = "";
      if (!email || !code) { state.authError = "请填写邮箱和验证码"; render(); return; }
      if (pass.length < 8) { state.authError = "新密码至少 8 位"; render(); return; }
      const r = await guard("重置密码", () => api.forgotPassword(email, code, pass));
      if (r && r.ok) { nav("login"); toast("密码已重置，请登录"); }
      else { state.authError = (r && r.msg) || "重置失败"; render(); }
    });
  }

  function bindNodes() {
    const input = $("#node-search");
    if (input) {
      input.addEventListener("input", () => {
        state.nodeFilter = input.value;
        render();
        const n = $("#node-search");
        if (n) { n.focus(); n.setSelectionRange(n.value.length, n.value.length); }
      });
    }
    // 分组手风琴里的节点行带 data-node-group：切的是**那个分组**的出口
    $$(".node-row").forEach((el) => el.addEventListener("click", async () => {
      const group = el.dataset.nodeGroup || undefined;
      const r = await guard("切换节点", () => api.selectNode(el.dataset.node, group));
      if (r && r.ok) {
        state.node = r.node;
        const n = state.nodes.find((x) => x.name === r.node);
        if (n && n.latency > 0) state.latency = n.latency;
        // 分组的"当前出口"也要跟着变
        state.groups = await api.getRoutingGroups().catch(() => state.groups);
        render(); toast("已切换到 " + r.node);
      }
    }));
    const st = $("#btn-speedtest");
    if (st) st.addEventListener("click", () => runDelayTest(false));
    const rf = $("#btn-refresh-sub");
    if (rf) rf.addEventListener("click", async () => {
      state.refreshing = true;
      const r = await guard("刷新订阅", () => api.refreshSubscription());
      state.refreshing = false;
      if (r) { await refreshAll(); toast("订阅已刷新，共 " + r.count + " 个节点"); }
      render();
    });
  }

  /**
   * 延迟测试（节点 + 每个分组）。按钮与「连接成功后自动跑一次」共用。
   * 用户第 7 条：连上就自动测一次，不要让人再进节点页手动点。
   */
  async function runDelayTest(auto) {
    if (state.testing) return;
    state.testing = true; render();
    // 拉订阅是"断开 → 重连"内核（节点列表要重新载入）。它和自动延迟测试撞在一起时
    // 内核正好是断的，speed_test 报「尚未连接」，界面上就变成"连上了但节点全是未测"。
    // 等它忙完再测，失败再等一会儿重试一次。
    if (subRefreshPromise) {
      await Promise.race([subRefreshPromise, new Promise((r) => setTimeout(r, 30000))]);
      await new Promise((r) => setTimeout(r, 800));
    }
    let r = null;
    let lastMsg = "";
    for (let i = 0; i < 2 && !r; i += 1) {
      try {
        r = await api.speedTest();
      } catch (e) {
        lastMsg = String((e && e.message) || e);
        // 只有"内核正好在重连"这一类值得再等一次；别的错误直接报给用户，别重试也别报两遍
        if (!(i === 0 && /尚未连接|正在/.test(lastMsg))) break;
        await new Promise((res) => setTimeout(res, 2500));
      }
    }
    state.testing = false;
    if (r) {
      state.nodes = await api.getNodes().catch(() => state.nodes);
      // 每个分组的延迟也一起回来了，刷新分组列表才能看到
      state.groups = await api.getRoutingGroups().catch(() => state.groups);
    }
    render();
    if (r) toast(auto ? "已自动完成延迟测试" : "延迟测试完成");
    else if (lastMsg) toast("延迟测试失败：" + lastMsg);
  }

  /**
   * 流量图悬停：鼠标划过就报出那个时间点的下载/上传用量。
   * 不用 SVG 坐标换算 —— views.js 已经把每个点切成一块透明热区 rect[data-chart-i]，
   * 命中哪块就是第几个点，指哪画哪，不会因为缩放/边距算出错的点。
   */
  function bindTraffic() {
    const svg = $("#traffic-chart");
    const tip = $("#traffic-tip");
    const hover = $("#chart-hover");
    const pts = (state.trafficSeries && state.trafficSeries.points) || [];
    if (!svg || !tip || !hover || !pts.length) return;
    const W = 620, PAD = Number(svg.dataset.pad) || 34;
    const H = Number(svg.dataset.h) || 170;
    const max = Number(svg.dataset.max) || 1;
    const unit = svg.dataset.unit || "MB";
    const px = (i) => PAD + (i / Math.max(1, pts.length - 1)) * (W - PAD - 10);
    const py = (v) => H - 20 - (v / max) * (H - 44);
    const mib = (mb) => fmt.bytes(Math.round(Number(mb || 0) * 1048576));
    const hide = () => { tip.hidden = true; hover.textContent = ""; };
    svg.addEventListener("mouseleave", hide);
    svg.addEventListener("mousemove", (ev) => {
      const raw = ev.target && ev.target.getAttribute ? ev.target.getAttribute("data-chart-i") : null;
      if (raw === null || raw === undefined) return hide();
      const i = Number(raw);
      const p = pts[i];
      if (!p) return hide();
      const cx = px(i).toFixed(1);
      hover.innerHTML =
        `<line x1="${cx}" y1="18" x2="${cx}" y2="${H - 20}" stroke="#c7c7cc" stroke-width="1"/>` +
        `<circle cx="${cx}" cy="${py(p.down).toFixed(1)}" r="4" fill="#1a73e8" stroke="#fff" stroke-width="1.5"/>` +
        `<circle cx="${cx}" cy="${py(p.up).toFixed(1)}" r="4" fill="#ff9500" stroke="#fff" stroke-width="1.5"/>`;
      tip.textContent = `${p.label}　↓ ${mib(p.down)}　↑ ${mib(p.up)}（${unit}）`;
      tip.hidden = false;
      // 贴着指针，但别飘出卡片
      const box = svg.getBoundingClientRect();
      const tipBox = tip.getBoundingClientRect();
      const left = Math.max(4, Math.min(box.width - tipBox.width - 4, ev.clientX - box.left - tipBox.width / 2));
      tip.style.left = left + "px";
      tip.style.top = Math.max(0, ev.clientY - box.top - tipBox.height - 10) + "px";
    });
  }

  function bindRouting() {
    const b = $("#btn-routing-reset");
    if (b) b.addEventListener("click", () => openDialog("confirm", {
      title: "恢复默认出口", body: "把所有策略组切回它们的第一个出口（主选择组回到订阅默认）。", yes: "恢复默认",
      onYes: async () => {
        const r = await guard("恢复默认出口", () => api.resetRoutingGroups());
        if (r) { state.groups = await api.getRoutingGroups().catch(() => state.groups); render(); toast("已恢复默认出口"); }
      },
    }));
    const rb = $("#btn-ruleset-reset");
    if (rb) rb.addEventListener("click", () => openDialog("confirm", {
      title: "恢复内置分流默认", body: "按产品预设恢复各分类的开关与顺序（苹果服务、Google、哔哩哔哩、国内直连、国外穿墙默认开启）。不影响你自己建的分流组。已连接时会热重载配置。", yes: "恢复默认",
      onYes: async () => {
        const r = await guard("恢复默认", () => api.resetRulesets());
        if (r) { await refreshRoutes(); render(); toast("已恢复内置分流默认"); }
      },
    }));
    // 本地分流总开关：关掉后面板下发的分流方案原样生效（应急用）
    const lr = $(".switch[data-click='local-routing']");
    if (lr) lr.addEventListener("click", async (e) => {
      e.stopPropagation();
      const next = !lr.classList.contains("on");
      lr.classList.toggle("on", next);
      const r = await guard("切换分流方案", () => api.setLocalRouting(next));
      if (!r) { lr.classList.toggle("on", !next); return; }
      await refreshRoutes();
      render();
      toast(next ? "已切回本地分流方案" : "已改用面板自带的分流方案");
    });
    // 顺序：按住 ⠿ 拖到目标位置（越靠前越先匹配）
    bindRulesetDrag();
    // 自定义分流组：新建 / 编辑 / 删除
    const cn = $("#btn-custom-new");
    if (cn) cn.addEventListener("click", () => openDialog("customRuleset", { out: "proxy", rules: [] }));
    $$("[data-ruleset-edit]").forEach((el) => el.addEventListener("click", (e) => {
      e.stopPropagation();
      const g = findRuleset(el.dataset.rulesetEdit);
      if (g) openDialog("customRuleset", g);
    }));
    // 开关：切换某一分类是否参与分流
    $$(".switch[data-ruleset]").forEach((sw) => sw.addEventListener("click", async (e) => {
      e.stopPropagation();
      const name = sw.dataset.ruleset;
      const next = !sw.classList.contains("on");
      sw.classList.toggle("on", next);
      const r = await guard("切换分流", () => api.setRuleset(name, next));
      if (!r) { sw.classList.toggle("on", !next); return; }
      await refreshRoutes();
      render();
      toast(next ? `${name}已开启` : `${name}已关闭`);
    }));
    // 点行：内置分类 → 单独指定出口；自定义组 → 打开编辑器
    $$("[data-ruleset-pick]").forEach((el) => el.addEventListener("click", async (e) => {
      if (e.target.closest(".switch") || e.target.closest(".ruleset-mv") || e.target.closest(".ruleset-grip")) return;
      const name = el.dataset.rulesetPick;
      const rs = findRuleset(name);
      if (rs && rs.custom) { openDialog("customRuleset", rs); return; }
      if (rs && !rs.enabled) { toast("先开启该分类，才能单独指定出口"); return; }
      if (!state.connected) { toast("连接后才能单独指定出口"); return; }
      // 本地方案下组名与内核组名一一对应（rs.group 由主进程给出）
      const g = (state.groups || []).find((x) => x.name === ((rs && rs.group) || name));      if (!g) { toast("该分组尚未在内核中生效"); return; }
      openDialog("groupPick", g);
    }));
  }

  /** 在内置分类与自定义组里找同名项 */
  function findRuleset(name) {
    const rs = state.rulesets || {};
    const all = (rs.groups || []).concat(rs.custom || []);
    return all.find((x) => x.name === name);
  }

  /**
   * 分流顺序 = 拖动手柄（不是 ↑↓ 按钮）。
   * 自己用 pointer 事件实现而不是 HTML5 drag&drop：后者在 Electron 里跟
   * 滚动容器配合不稳，而且真实鼠标事件（sendInputEvent）不一定能合成 dragstart。
   * 拖动只在**同一张卡**（自定义组之间、或内置分类之间）生效，跨卡不换位。
   *
   * 两个刻意的写法：
   *  1) move/up 挂在 document 上而不是手柄上 —— 不依赖 setPointerCapture，
   *     指针移出手柄（甚至移出窗口）也能收到，松手一定能收尾。
   *  2) 落点每次移动都**重新查 DOM**（不缓存节点数组）—— 拖动期间如果来了一次
   *     重绘（状态事件、refreshAll 回来），缓存的节点已经脱离文档，
   *     getBoundingClientRect 全是 0，落点会算错、顺序静默不变。
   */
  function bindRulesetDrag() {
    const grips = $$("[data-ruleset-grip]");
    if (!grips.length) return;
    const clear = () => $$(".ruleset-row").forEach((r) => r.classList.remove("dragging", "drop-before", "drop-after"));
    // 松手后浏览器还会补一个 click（落点在**目标行**上）——那一行有「指定出口」的点击处理，
    // 于是"拖完顺序"会顺带弹出一个出口选择框，把后面的操作全挡住（实测：拖动之后
    // 侧边栏与返回键都点不动，因为遮罩还在）。这里在捕获阶段吃掉紧跟在拖动后的那一次 click。
    if (!dragClickGuard) {
      dragClickGuard = true;
      document.addEventListener("click", (e) => {
        if (Date.now() > suppressClickUntil) return;
        suppressClickUntil = 0;
        e.preventDefault();
        e.stopPropagation();
      }, true);
    }
    for (const grip of grips) {
      grip.addEventListener("pointerdown", (ev) => {
        if (ev.button !== 0) return;
        ev.preventDefault();
        ev.stopPropagation();          // 拖手柄绝不能顺带点开"指定出口"
        const row = grip.closest(".ruleset-row");
        if (!row) return;
        const card = row.parentElement;              // 同一张卡 = 同一段
        const from = $$(".ruleset-row", card).indexOf(row);
        if (from < 0) return;
        const name = row.dataset.rulesetRow;
        let to = from;
        let moved = false;
        row.classList.add("dragging");
        const onMove = (e) => {
          const rows = $$(".ruleset-row", card);     // 每次都重查
          if (rows.length < 2) return;
          const y = e.clientY;
          let idx = rows.length - 1;
          for (let i = 0; i < rows.length; i++) {
            const r = rows[i].getBoundingClientRect();
            if (y < r.top + r.height / 2) { idx = Math.max(0, i - 1); break; }
          }
          if (y > rows[rows.length - 1].getBoundingClientRect().bottom) idx = rows.length - 1;
          if (idx !== to) { moved = true; to = idx; }
          rows.forEach((r, i) => {
            r.classList.toggle("drop-before", moved && i === to && to < from);
            r.classList.toggle("drop-after", moved && i === to && to >= from);
          });
        };
        const onUp = async () => {
          document.removeEventListener("pointermove", onMove, true);
          document.removeEventListener("pointerup", onUp, true);
          document.removeEventListener("pointercancel", onUp, true);
          clear();
          // 只要在**手柄**上按过一下，松手后浏览器补发的那次 click 就必须吃掉 ——
          // 哪怕这一下没拖动、或者拖回原位（moved=false / to===from）。
          // 第一版只在"顺序真的变了"时才装抑制，结果"原地拖一下"漏掉：
          // 此时 mousedown 在手柄（属于这一行）、mouseup 还在这一行，
          // click 的落点就是这一行本身 → 行上的「指定出口」处理被触发 → 弹窗遮罩常驻。
          // 实测就是这么挂的（ui-test 第 5 段 3 条红）。
          suppressClickUntil = Date.now() + 400;
          if (!moved || to === from) return;
          const r = await guard("调整顺序", () => api.reorderRuleset(name, to));
          if (!r) return;
          if (r.moved === false) { toast("位置没变"); return; }
          await refreshRoutes();
          render();
          toast(`「${name}」移到第 ${to + 1} 位`);
        };
        document.addEventListener("pointermove", onMove, true);
        document.addEventListener("pointerup", onUp, true);
        document.addEventListener("pointercancel", onUp, true);
      });
    }
  }

  /** 只刷新分流相关数据（比 refreshAll 便宜） */
  async function refreshRoutes() {
    const [groups, rulesets] = await Promise.all([
      api.getRoutingGroups().catch(() => state.groups),
      api.getRulesets().catch(() => state.rulesets),
    ]);
    state.groups = groups;
    state.rulesets = rulesets;
  }

  /**
   * 设置项开关（.switch[data-setting]）的点击处理。
   * 设置页与「我的」页都有这类开关（到期提醒/流量提醒搬到了「我的」页），
   * 所以必须由两边共同调用 —— 只挂在 bindSettings 上会让「我的」页的开关点不动。
   */
  function bindSettingSwitches() {
    $$(".switch[data-setting]").forEach((sw) => sw.addEventListener("click", async () => {
      const k = sw.dataset.setting;
      const next = !state.settings[k];
      // TUN 需要管理员：先拦一道，避免开了没效果
      if (k === "tun_mode" && next) {
        const admin = await api.isAdmin().catch(() => false);
        if (!admin) {
          openDialog("confirm", {
            title: "需要管理员权限",
            body: "TUN 模式要创建虚拟网卡并改路由表，必须以管理员身份重启应用。现在重启吗？",
            yes: "以管理员身份重启",
            onYes: async () => {
              const r = await guard("提权重启", () => api.restartAsAdmin());
              if (r && !r.ok) toast(r.msg || "提权被取消");
            },
          });
          return;
        }
        if (state.connected) { toast("请先断开连接再切换 TUN 模式"); return; }
      }
      if (k === "sys_proxy" && !next && state.connected) { toast("断开连接后再关闭系统代理"); return; }
      state.settings[k] = next;
      sw.classList.toggle("on", next);
      const r = await guard("保存设置", () => api.setSetting(k, next));
      if (!r) { state.settings[k] = !next; sw.classList.toggle("on", !next); }
    }));
  }

  function bindSettings() {
    bindSettingSwitches();
  }

  function bindMe() { bindSettingSwitches(); }

  function bindPlans() {
    $$("[data-buy]").forEach((b) => b.addEventListener("click", async () => {
      const p = state.plans.find((x) => x.id === b.dataset.buy);
      if (!p) return;
      b.disabled = true; b.textContent = "创建订单…";
      const r = await guard("创建订单", () => api.createOrder(p.id));
      if (!r) { b.disabled = false; render(); return; }
      state.orders = await api.getOrders().catch(() => state.orders);
      nav("orders");
      toast("订单已创建，请完成支付");
      const methods = await api.getPaymentMethods().catch(() => []);
      openDialog("payment", { order_no: r.order_no, amount: p.price, methods });
    }));
  }

  function bindOrders() {
    $$("[data-pay]").forEach((b) => b.addEventListener("click", async () => {
      const no = b.dataset.pay;
      const amount = (state.orders.find((x) => x.no === no) || {}).amount || "";
      const methods = await guard("获取支付方式", () => api.getPaymentMethods());
      if (!methods) return;
      openDialog("payment", { order_no: no, amount, methods });
    }));
  }

  function bindTickets() {
    const b = $("#btn-new-ticket");
    if (b) b.addEventListener("click", () => openDialog("newTicket", state));
    $$("[data-ticket]").forEach((el) => el.addEventListener("click", () => {
      const t = state.tickets.find((x) => x.no === el.dataset.ticket);
      if (t) openDialog("ticketDetail", t);
    }));
  }

  function bindInvite() {
    const b = $("#btn-copy-invite");
    if (b) b.addEventListener("click", async () => { await copyText(state.invite.link); toast("邀请链接已复制"); });
  }

  function bindGiftcard() {
    const b = $("#btn-redeem");
    if (!b) return;
    b.addEventListener("click", async () => {
      const code = $("#gift-code").value.trim();
      state.giftError = "";
      if (!code) { state.giftError = "请输入卡密"; render(); return; }
      state.redeeming = true; render();
      const r = await guard("兑换", () => api.redeemGift(code));
      state.redeeming = false;
      if (!r) { render(); return; }
      if (r.ok) {
        state.giftHistory = await api.getGiftHistory().catch(() => state.giftHistory);
        state.plan = await api.getPlan().catch(() => state.plan);
        render(); toast("兑换成功，" + (r.reward || "已到账"));
      } else {
        state.giftError = r.msg || "兑换失败";
        render();
      }
    });
  }

  function bindNotices() {
    $$("[data-notice]").forEach((el) => el.addEventListener("click", () => {
      const n = state.notices[+el.dataset.notice];
      if (!n) return;
      if (n.unread) {
        n.unread = false;
        state.unreadNotices = state.notices.filter((x) => x.unread).length;
      }
      openDialog("notice", n);
    }));
  }

  /* ---------------- 主题 ---------------- */
  function applyTheme(theme) {
    const dark = theme === "dark" ||
      (theme === "system" && window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches);
    document.documentElement.dataset.theme = dark ? "dark" : "light";
  }
  if (window.matchMedia) {
    window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", () => {
      if ((state.settings.theme || "system") === "system") applyTheme("system");
    });
  }

  /* ---------------- 标题栏 ---------------- */
  function bindTitlebar() {
    $$(".nav-item").forEach((el) => el.addEventListener("click", () => nav(el.dataset.route)));
    const w = () => (window.polaris ? window.polaris.win : null);
    const on = (id, fn) => { const el = $(id); if (el) el.addEventListener("click", fn); };
    on("#btn-min", () => { const a = w(); if (a) a.minimize(); });
    on("#btn-max", () => { const a = w(); if (a) a.toggleMaximize(); });
    on("#btn-close", () => { const a = w(); if (a) a.close(); });
  }

  /* ---------------- 主进程状态推送 ---------------- */
  let liveBound = false;
  let lastUpdate = null;          // 记住这次「发现新版本」的参数，进度事件要拿它重画弹窗
  function bindLiveStatus() {
    if (liveBound || !window.polaris || !window.polaris.on) return;
    liveBound = true;
    window.polaris.on("status", (st) => {
      if (!st) return;
      const wasConnected = state.connected;
      const wasPhase = state.phase;
      const wasNode = state.node;
      state.connected = !!st.connected;
      state.node = st.node || state.node;
      state.latency = st.latency || 0;
      state.up_speed = st.up_speed;
      state.down_speed = st.down_speed;
      state.up_total = st.up_total;
      state.down_total = st.down_total;
      state.uptime = st.uptime;
      if (st.mode) state.mode = st.mode;
      state.phase = st.phase || state.phase;
      // 旧代码：phase 不落 state，且只在 home 路由重绘 —— 在节点页/我的页里
      // 连接状态、当前节点、连接中动画全都不会变。
      state.busy = state.phase === "starting" || state.phase === "stopping";
      if (LIVE_ROUTES.includes(state.route)) paintLive();
      if (overlayRoot.children.length) return;   // 有弹窗时重绘会把弹窗抹掉
      const meaningful = wasConnected !== state.connected || wasPhase !== state.phase;
      const nodeChanged = state.route === "nodes" && wasNode !== state.node;
      if (meaningful || nodeChanged) render();
    });
    window.polaris.on("toast", (t) => { if (t && t.message) toast(t.message); });
    window.polaris.on("navigate", (t) => { if (t && t.route) nav(t.route); });
    window.polaris.on("update", (st) => paintUpdate(st));
  }

  /**
   * 更新进度：只改弹窗里的进度条，不整页重绘（重绘会把弹窗抹掉）。
   * 下完（staged）时把弹窗换成「重启并安装」—— 那一步是重画 overlay，安全。
   */
  function paintUpdate(st) {
    if (!st) { const box = $("#upd-progress"); if (box) box.style.display = "none"; return; }
    const box = $("#upd-progress");
    if (box) {
      box.style.display = "";
      const bar = $("#upd-bar"), text = $("#upd-text");
      if (bar) bar.style.width = Math.max(0, Math.min(100, Number(st.percent) || 0)) + "%";
      if (text) text.textContent = updateLabel(st);
    }
    if (st.phase === "staged" && lastUpdate) {
      lastUpdate = Object.assign({}, lastUpdate, { staged: true, can_apply: true });
      openDialog("update", lastUpdate);
      toast("更新包已下载完成");
    }
  }

  function updateLabel(st) {
    const mb = (n) => (Number(n) / 1048576).toFixed(1) + " MB";
    if (st.phase === "downloading") {
      return st.total ? `正在下载 ${mb(st.received)} / ${mb(st.total)}` : `正在下载 ${mb(st.received)}`;
    }
    if (st.phase === "extracting") return "正在解压更新包…";
    if (st.phase === "staged") return "已就绪，重启后完成安装";
    if (st.phase === "applying") return "正在退出并安装…";
    if (st.phase === "error") return st.error || "更新失败";
    return "";
  }

  function paintLive() {
    const d = $("#down-speed"), u = $("#up-speed");
    if (d) d.textContent = fmt.speed(state.down_speed);
    if (u) u.textContent = fmt.speed(state.up_speed);
    // 会话三个数字（本次上传/下载、运行时间）原来只有整页 render() 才刷新，
    // 而 render() 只在「连接态或阶段变化」时触发 —— 连上之后它们会一直停在
    // 最后一次重绘的值（收进托盘再恢复也一样）。状态每秒都推，这里逐个补上。
    const set = (id, v) => {
      if (v === undefined || v === null) return;
      const el = document.querySelector("#" + id + " .v span");
      if (el) el.textContent = String(v);
    };
    set("live-up-total", state.up_total);
    set("live-down-total", state.down_total);
    set("live-uptime", state.uptime);
  }

  /* ---------------- 数据装载 ---------------- */
  async function refreshStatus() {
    const st = await api.getStatus().catch(() => null);
    if (st) Object.assign(state, {
      connected: !!st.connected, node: st.node || state.node, latency: st.latency || 0,
      up_speed: st.up_speed, down_speed: st.down_speed,
      up_total: st.up_total, down_total: st.down_total,
      uptime: st.uptime, mode: st.mode || state.mode,
    });
    return st;
  }

  async function loadTraffic() {
    // 明细直接用 get_traffic 一起回来的 picked，不再单独发一次 get_traffic_log
    // —— 那是同一个面板查询（实测整页最慢的一项），发两次等于白等一倍。
    const [t, series] = await Promise.all([
      api.getTraffic(state.trafficRange).catch(() => ({})) ,
      api.getTrafficSeries(state.trafficRange).catch(() => ({ points: [], unit: "MB" })),
    ]);
    state.traffic = t || {};
    state.trafficSeries = series || { points: [], unit: "MB" };
    state.trafficLog = Array.isArray(state.traffic.picked) ? state.traffic.picked : [];
  }

  /** 登录后 / 连接后统一刷新 */
  async function refreshAll(includeAuth) {
    if (includeAuth) {
      state.settings = await api.getSettings().catch(() => state.settings);
      state.appInfo = await api.getAppInfo().catch(() => state.appInfo);
      applyTheme(state.settings.theme || "system");
    }
    await refreshStatus();
    // 面板抖动一次（超时/被 CDN 重置）不能把已经拿到的数据抹成空 —— 旧代码一律
    // `.catch(() => [])`，于是"网络一抖，套餐变未订阅、订单变空、公告变空"，
    // 用户看到的就是"没读取到网站信息 / 像没登录"（用户第 11 轮第 3、4 条）。
    // 现在失败就保留上一次的值，只把这一项标成"没刷新成功"。
    let planFailed = false;
    const [nodes, groups, rulesets, plan, plans, orders, tickets, invite, gh, notices, site, rcfg] = await Promise.all([
      api.getNodes().catch(() => state.nodes),
      api.getRoutingGroups().catch(() => state.groups),
      api.getRulesets().catch(() => state.rulesets),
      api.getPlan().catch(() => { planFailed = true; return state.plan; }),
      api.getPlans().catch(() => state.plans),
      api.getOrders().catch(() => state.orders),
      api.getTickets().catch(() => state.tickets),
      api.getInvite().catch(() => state.invite),
      api.getGiftHistory().catch(() => state.giftHistory),
      api.getNotices().catch(() => state.notices),
      api.getSiteInfo().catch(() => state.siteInfo),
      api.invoke("get_register_config").catch(() => state.registerConfig),
    ]);
    // 套餐这一项决定「我的」页那个徽章写什么。读失败且本地也没有旧值时，
    // 不能写"未订阅"（那是在说"你没买套餐"，事实是"没读到"）。
    state.panelError = planFailed && !(state.plan && state.plan.name) ? "面板暂时读不到，稍后自动重试" : "";
    state.nodes = nodes || [];
    state.groups = groups || [];
    if (rulesets) state.rulesets = rulesets;
    if (plan) state.plan = plan;
    state.plans = (plans || []).length ? plans : state.plans;
    state.orders = orders || [];
    state.tickets = tickets || [];
    if (invite) state.invite = invite;
    state.giftHistory = gh || [];
    state.notices = notices || [];
    state.unreadNotices = state.notices.filter((n) => n.unread).length;
    // 面板可能没有站点名字段（实测某个真面板就没有）——旧代码在这里
    // 要求 site.appName 非空才收，导致整份 siteInfo 被丢掉，登录页副标题永远是空的。
    if (site && (site.appName || site.appDescription || site.appUrl)) state.siteInfo = site;
    if (rcfg) state.registerConfig = Object.assign({ email_verify: 0, invite_force: 0 }, rcfg);
    state.email = state.settings.email || state.settings.last_email || "";
    // 流量明细是整条链路最慢的一项（面板侧聚合查询，实测单个请求能到 10-30 秒）。
    // 以前这里是 await loadTraffic()，于是"连上之后节点页出内容"被它拖住 ——
    // 用户看到的是：点了连接，节点页/首页几十秒都是空的。改成后台补齐，
    // 谁在看流量页就等它回来后自己重绘一次。
    loadTraffic()
      .then(() => { if (state.route === "traffic" || state.route === "home") render(); })
      .catch(() => {});
  }

  let subRefreshPromise = null;   // 正在进行的"准备节点数据"（连接/延迟测试要等它）
  /**
   * 把节点数据准备好（用户第 11 轮第 1、4 条）。
   *
   * 旧行为：订阅只在"点连接 / 设置页手动刷新 / 12 小时过期"时才拉，所以刚登录时
   * 节点页是空的（连 config.yaml 都还没生成），而点连接反倒变成"在拉订阅"。
   * 现在登录成功、启动时已登录、以及自动过期检查都走这里；失败原因会显示在
   * 节点页上，不再只 console.warn 一下（那等于没有反馈）。
   *
   * 真拉还是秒回由主进程的 ensure_subscription 决定：本地没订阅、订阅过期、
   * 配置没生成才真拉。
   */
  function ensureSubscription(reason) {
    if (!state.settings.authed) return Promise.resolve(false);
    if (subRefreshPromise) return subRefreshPromise;        // 同一时刻只准备一次
    // 用户正在点连接/断开时别动内核（订阅刷新会重建配置，见 DEVNOTES A-41）
    if (state.busy && reason !== "login") {
      return new Promise((res) => setTimeout(() => res(ensureSubscription(reason)), 15000));
    }
    const loud = reason !== "auto";
    state.subError = "";
    state.refreshing = true;
    if (state.route === "nodes") render();
    subRefreshPromise = (async () => {
      try {
        const r = await api.ensureSubscription({ reason });
        if (r && r.ok) {
          state.nodes = await api.getNodes().catch(() => state.nodes);
          state.settings = await api.getSettings().catch(() => state.settings);
          state.groups = await api.getRoutingGroups().catch(() => state.groups);
          return true;
        }
        return false;
      } catch (e) {
        state.subError = e.message || String(e);
        console.warn(`准备节点数据失败（${reason}）`, e);
        if (loud) toast(`节点数据没准备好：${state.subError}`);
        return false;
      } finally {
        state.refreshing = false;
        subRefreshPromise = null;
        if (state.route === "nodes" || state.route === "home") render();
      }
    })();
    return subRefreshPromise;
  }

  /** 定时/自动检查：只在订阅过期或本地没有节点时才真拉（判断在主进程里） */
  function autoRefreshSubscription() {
    if (!state.settings.authed) return Promise.resolve(false);
    const stale = !state.settings.subscription_updated_at ||
      Date.now() - state.settings.subscription_updated_at > 12 * 3600 * 1000;
    if (!stale && state.nodes.length > 0) return Promise.resolve(false);
    return ensureSubscription("auto");
  }

  async function boot() {
    state.settings = await api.getSettings().catch(() => ({}));
    state.appInfo = await api.getAppInfo().catch(() => ({}));
    applyTheme(state.settings.theme || "system");

    if (!state.settings.authed) {
      state.siteInfo = await api.getSiteInfo().catch(() => ({}));
      state.route = "login";
      render();
      state.booted = true;
      bindLiveStatus();
      return;
    }
    // 先把界面立起来（本地设置已经在手上了），再去拉面板数据。
    // 旧代码是 await refreshAll() 之后无条件 route="home" + render()：
    // 面板慢的时候（实测流量查询能到十几秒）用户在启动期间点进「我的」，
    // 会被这一下拽回首页；自检里也因此丢过点击（点退出登录时元素刚被换掉）。
    state.route = "home";
    noteNav("boot", "home", "boot()");
    render();
    state.booted = true;
    bindLiveStatus();
    await refreshAll();
    if (state.route === "home") render();
    // 启动时已登录（登录态由凭据恢复）：同样要把节点数据准备好 ——
    // 用户第 11 轮第 3 条"账号登录着但各处提示没登录"，一半原因是订阅/配置
    // 还没就绪、面板请求又失败，界面退化成空态。
    ensureSubscription("boot");
    // 虚拟网卡状态要起 PowerShell，放到界面出来之后再查，不占启动路径
    loadTunStatus();
    // 「自动检查更新」这个开关以前是死的（只有 UI，没有任何代码读它）。
    // 现在真的会查：延迟 6s 起，别和启动路径抢带宽，也别在启动瞬间弹窗。
    if (state.settings.auto_update) setTimeout(autoCheckUpdate, 6000);
  }

  /**
   * 静默检查更新：只弹一条 toast、并把设置页那一行改成「有新版本 x.y.z」，
   * 不自动弹窗打断用户（弹窗交给用户点「检查更新」）。
   */
  async function autoCheckUpdate() {
    if (state.checkingUpdate) return;
    state.checkingUpdate = true;
    try {
      const u = await api.checkUpdate();
      if (u && (u.has_update || u.staged)) {
        state.updateInfo = u;
        lastUpdate = Object.assign({ current: state.settings.version }, u);
        if (u.has_update) toast("发现新版本 " + u.version + "，可在「设置 → 检查更新」安装");
        else toast("有已下载好的更新 " + u.version + "，可在「设置 → 检查更新」安装");
        if (state.route === "settings") render();
      }
    } catch (_) { /* 检查更新失败不打扰用户 */ }
    finally { state.checkingUpdate = false; }
  }

  let tunInflight = null;
  /**
   * 查虚拟网卡状态。主进程侧还有 60s 缓存，这里再加一道在途去重，
   * 并且**只在结果真的变了**的时候重绘 —— 否则就是 render 死循环。
   */
  function loadTunStatus(force) {
    if (tunInflight) return tunInflight;
    const before = JSON.stringify(state.tunStatus || null);
    tunInflight = (async () => {
      try {
        state.tunStatus = await api.invoke("get_tun_status", force ? { force: true } : undefined)
          .catch(() => state.tunStatus);
        if (state.route === "settings" && JSON.stringify(state.tunStatus || null) !== before) render();
      } finally {
        tunInflight = null;
      }
    })();
    return tunInflight;
  }

  document.addEventListener("DOMContentLoaded", () => {
    bindTitlebar();
    boot();
  });
})();
