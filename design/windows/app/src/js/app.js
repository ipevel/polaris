/* 应用主逻辑：路由、全局状态、事件绑定、自绘标题栏。 */
(function () {
  const api = window.PolarisAPI;
  const Views = window.PolarisViews;
  const Dialogs = window.PolarisDialogs;
  const $ = (sel, el) => (el || document).querySelector(sel);
  const $$ = (sel, el) => Array.from((el || document).querySelectorAll(sel));

  const MAIN_ROUTES = ["home", "nodes", "traffic", "settings", "me"];
  const state = {
    route: "home",
    loggedIn: true,
    email: "user@example.com",
    connected: true, node: "香港 01", latency: 45,
    up_speed: 2.1, down_speed: 12.4,
    up_total: "1.2 GB", down_total: "8.6 GB", uptime: "02:34:18",
    mode: "规则模式",
    plan: { name: "旗舰套餐", used: 86, total: 200, expire: "2026-11-05" },
    nodes: [], nodeFilter: "", testing: false, refreshing: false,
    traffic: {}, trafficRange: "today",
    plans: [], orders: [], tickets: [], invite: {}, giftHistory: [],
    notices: [], unreadNotices: 0,
    settings: {},
  };

  const content = $("#content");
  const overlayRoot = $("#overlay-root");

  /* ---------- 渲染 ---------- */
  function render() {
    overlayRoot.innerHTML = "";
    if (state.route === "login") {
      content.innerHTML = Views.login();
      $("#sidebar").style.display = "none";
      bindLogin();
      return;
    }
    $("#sidebar").style.display = "";
    content.innerHTML = (Views[state.route] || Views.home)(state);
    $$(".nav-item").forEach((el) => {
      const r = el.dataset.route;
      el.classList.toggle("active", r === state.route || (state.route === "routing" && r === "nodes"));
    });
    bindCommon();
    ({ nodes: bindNodes, settings: bindSettings, me: bindMe, plans: bindPlans,
       orders: bindOrders, tickets: bindTickets, invite: bindInvite,
       giftcard: bindGiftcard, notices: bindNotices, routing: bindRouting,
       home: bindHome }[state.route] || (() => {}))();
  }

  function nav(route) { state.route = route; render(); }
  window.PolarisNav = nav;

  function openDialog(name, arg) {
    overlayRoot.innerHTML = Dialogs[name](arg === undefined ? state : arg);
    overlayRoot.querySelectorAll("[data-overlay]").forEach((o) => {
      o.addEventListener("mousedown", (e) => { if (e.target === o) closeDialog(); });
    });
    overlayRoot.querySelectorAll("[data-overlay-close]").forEach((b) =>
      b.addEventListener("click", closeDialog));
    bindDialog(name);
  }
  function closeDialog() { overlayRoot.innerHTML = ""; }
  window.PolarisDialog = { open: openDialog, close: closeDialog };

  /* ---------- 通用事件 ---------- */
  function bindCommon() {
    $$("[data-nav]").forEach((el) => el.addEventListener("click", () => nav(el.dataset.nav)));
    $$("[data-click]").forEach((el) => el.addEventListener("click", () => handleClick(el.dataset.click, el)));
    $$("[data-range]").forEach((el) => el.addEventListener("click", async () => {
      state.trafficRange = el.dataset.range;
      state.traffic = await api.getTraffic(state.trafficRange);
      render();
    }));
  }

  async function handleClick(action, el) {
    if (action === "proxy-mode") openDialog("proxy");
    else if (action === "check-update") {
      const u = await api.checkUpdate();
      if (u.has_update) openDialog("update", { ...u, current: state.settings.version });
      else toast("已是最新版本");
    }
    else if (action === "logout") openDialog("logoutConfirm");
    else if (action === "nav-orders") nav("orders");
    else if (action === "nav-tickets") nav("tickets");
    else if (action === "nav-invite") nav("invite");
    else if (action === "nav-giftcard") nav("giftcard");
    else if (action === "nav-notices") nav("notices");
    else if (action === "nav-settings") nav("settings");
  }

  function bindDialog(name) {
    if (name === "proxy") {
      $$("[data-mode]", overlayRoot).forEach((el) => el.addEventListener("click", async () => {
        const r = await api.setProxyMode(el.dataset.mode);
        state.mode = r.mode; closeDialog(); render(); toast("已切换为" + r.mode);
      }));
    }
    if (name === "update") {
      const btn = $("#btn-do-update");
      if (btn) btn.addEventListener("click", () => {
        $("#update-body").innerHTML = `<div style="font-size:13px;color:var(--text2);margin-bottom:10px" id="upd-tip">正在下载…</div><div class="progress"><div id="upd-bar" style="width:5%"></div></div>`;
        let p = 5;
        const t = setInterval(() => {
          p = Math.min(100, p + Math.random() * 14);
          $("#upd-bar").style.width = p + "%";
          if (p >= 100) {
            clearInterval(t);
            $("#upd-tip").textContent = "下载完成，准备安装…";
            setTimeout(() => { closeDialog(); toast("已更新到最新版本"); }, 900);
          }
        }, 220);
      });
    }
    if (name === "logoutConfirm") {
      $("#btn-logout-confirm").addEventListener("click", async () => {
        await api.logout(); state.loggedIn = false; closeDialog(); nav("login");
      });
    }
    if (name === "newTicket") {
      $("#btn-submit-ticket").addEventListener("click", async () => {
        const subject = $("#ticket-subject").value.trim();
        const ctn = $("#ticket-content").value.trim();
        if (!subject) { toast("请填写标题"); return; }
        const r = await api.createTicket(subject, ctn);
        state.tickets.unshift({ subject, no: r.no, date: "2026-10-09", status: "pending" });
        closeDialog(); render(); toast("工单已提交");
      });
    }
  }

  function toast(msg) {
    const d = document.createElement("div");
    d.textContent = msg;
    d.style.cssText = "position:fixed;left:50%;bottom:60px;transform:translateX(-50%);background:rgba(30,30,34,.92);color:#fff;font-size:13.5px;padding:10px 22px;border-radius:10px;z-index:999;box-shadow:0 6px 20px rgba(0,0,0,.25)";
    document.body.appendChild(d);
    setTimeout(() => d.remove(), 2200);
  }
  window.PolarisToast = toast;

  /* ---------- 各页事件 ---------- */
  function bindHome() {
    const pw = $("#power");
    if (pw) pw.addEventListener("click", async () => {
      if (state.connected) { await api.disconnect(); state.connected = false; }
      else { await api.connect(); state.connected = true; }
      render();
      toast(state.connected ? "已连接" : "已断开");
    });
  }

  function bindLogin() {
    $("#btn-login").addEventListener("click", async () => {
      const email = $("#login-email").value.trim();
      const pass = $("#login-pass").value;
      const r = await api.login(email, pass, $("#login-panel").value.trim());
      if (r.ok) { state.loggedIn = true; state.email = email; await boot(false); }
      else $("#login-err").textContent = r.msg || "登录失败";
    });
  }

  async function bindNodes() {
    const input = $("#node-search");
    if (input) {
      input.addEventListener("input", () => { state.nodeFilter = input.value; render(); const n = $("#node-search"); n.focus(); n.setSelectionRange(n.value.length, n.value.length); });
    }
    $$(".node-row").forEach((el) => el.addEventListener("click", async () => {
      const r = await api.selectNode(el.dataset.node);
      state.node = r.node;
      const n = state.nodes.find((x) => x.name === r.node);
      if (n && n.latency > 0) state.latency = n.latency;
      render(); toast("已切换到 " + r.node);
    }));
    const st = $("#btn-speedtest");
    if (st) st.addEventListener("click", async () => {
      state.testing = true; render();
      await api.speedTest();
      state.nodes.forEach((n) => { if (n.latency < 0) n.latency = 60 + Math.floor(Math.random() * 160); });
      state.testing = false; render(); toast("测速完成");
    });
    const rf = $("#btn-refresh-sub");
    if (rf) rf.addEventListener("click", async () => {
      state.refreshing = true; render();
      const r = await api.refreshSubscription();
      state.refreshing = false; render(); toast(`订阅已刷新，共 ${r.count} 个节点`);
    });
  }

  function bindSettings() {
    $$(".switch[data-setting]").forEach((sw) => sw.addEventListener("click", async () => {
      const k = sw.dataset.setting;
      state.settings[k] = !state.settings[k];
      await api.setSetting(k, state.settings[k]);
      sw.classList.toggle("on", state.settings[k]);
    }));
  }

  function bindMe() { /* data-click 已在 bindCommon 处理 */ }

  function bindPlans() {
    $$("[data-buy]").forEach((b) => b.addEventListener("click", async () => {
      const p = state.plans.find((x) => x.id === b.dataset.buy);
      const r = await api.createOrder(p.id);
      state.orders.unshift({ name: p.name, no: r.order_no, date: "2026-10-09", amount: p.price, status: "pending" });
      nav("orders"); toast("订单已创建，请完成支付");
    }));
  }

  function bindOrders() {
    $$("[data-pay]").forEach((b) => b.addEventListener("click", async () => {
      b.textContent = "支付中…";
      await api.payOrder(b.dataset.pay);
      const o = state.orders.find((x) => x.no === b.dataset.pay);
      if (o) o.status = "done";
      render(); toast("支付成功，套餐已生效");
    }));
  }

  function bindTickets() {
    const b = $("#btn-new-ticket");
    if (b) b.addEventListener("click", () => openDialog("newTicket"));
  }

  function bindInvite() {
    const b = $("#btn-copy-invite");
    if (b) b.addEventListener("click", async () => {
      try { await navigator.clipboard.writeText(state.invite.link); toast("邀请链接已复制"); }
      catch (e) { toast(state.invite.link); }
    });
  }

  function bindGiftcard() {
    $("#btn-redeem").addEventListener("click", async () => {
      const code = $("#gift-code").value.trim();
      const r = await api.redeemGift(code);
      if (r.ok) {
        state.giftHistory.unshift({ code, reward: "已到账 " + r.reward, date: "2026-10-09" });
        render(); toast("兑换成功，" + r.reward + "已到账");
      } else $("#gift-err").textContent = r.msg;
    });
  }

  function bindNotices() {
    $$("[data-notice]").forEach((el) => el.addEventListener("click", () => {
      const n = state.notices[+el.dataset.notice];
      if (n.unread) { n.unread = false; state.unreadNotices = state.notices.filter((x) => x.unread).length; }
      openDialog("notice", n);
      overlayRoot.querySelector("[data-overlay-close]").addEventListener("click", render, { once: true });
    }));
  }

  function bindRouting() {
    const b = $("#btn-routing-reset");
    if (b) b.addEventListener("click", () => toast("已恢复默认分流规则"));
  }

  /* ---------- 标题栏 ---------- */
  function bindTitlebar() {
    $$(".nav-item").forEach((el) => el.addEventListener("click", () => nav(el.dataset.route)));
      const w = () => (window.polaris ? window.polaris.win : null);
      $("#btn-min").addEventListener("click", () => { const a = w(); if (a) a.minimize(); });
      $("#btn-max").addEventListener("click", () => { const a = w(); if (a) a.toggleMaximize(); });
      $("#btn-close").addEventListener("click", () => { const a = w(); if (a) a.close(); });
  }

  /* ---------- 启动 ---------- */
  async function boot(keepRoute) {
    const [st, plan, nodes, traffic, plans, orders, tickets, invite, gh, notices, settings] = await Promise.all([
      api.getStatus(), api.getPlan(), api.getNodes(), api.getTraffic("today"),
      api.getPlans(), api.getOrders(), api.getTickets(), api.getInvite(),
      api.getGiftHistory(), api.getNotices(), api.getSettings(),
    ]);
    Object.assign(state, {
      connected: st.connected, node: st.node, latency: st.latency,
      up_speed: st.up_speed, down_speed: st.down_speed,
      up_total: st.up_total, down_total: st.down_total, uptime: st.uptime, mode: st.mode,
      plan: { name: plan.name, used: plan.used, total: plan.total, expire: plan.expire },
      nodes, traffic, plans, orders, tickets, invite, giftHistory: gh, notices, settings,
      unreadNotices: notices.filter((n) => n.unread).length,
    });
    if (!keepRoute) state.route = "home";
      render();
      bindLiveStatus();
      // 速率模拟跳动：仅浏览器/mock 预览用，真实运行时由主进程推送替换
      if (!api.isHost) setInterval(() => {
      if (!state.connected) return;
      state.down_speed = (11 + Math.random() * 3).toFixed(1);
      state.up_speed = (1.8 + Math.random() * 0.8).toFixed(1);
      const d = $("#down-speed"), u = $("#up-speed");
      if (d) d.textContent = state.down_speed + " MB/s";
      if (u) u.textContent = state.up_speed + " MB/s";
    }, 2000);
  }

    /* ---------- 主进程状态推送（真实速率 / 连接状态 / 跳转） ---------- */
    let liveBound = false;
    function bindLiveStatus() {
      if (liveBound || !window.polaris || !window.polaris.on) return;
      liveBound = true;
      window.polaris.on("status", (st) => {
        if (!st) return;
        const was = state.connected;
        state.connected = !!st.connected;
        state.node = st.node || state.node;
        state.latency = st.latency || 0;
        state.up_speed = st.up_speed;
        state.down_speed = st.down_speed;
        state.up_total = st.up_total;
        state.down_total = st.down_total;
        state.uptime = st.uptime;
        if (st.mode) state.mode = st.mode;
        if (state.route === "home") paintLiveSpeed();
        if (was !== state.connected) render();
      });
      window.polaris.on("toast", (t) => { if (t && t.message) toast(t.message); });
      window.polaris.on("navigate", (t) => { if (t && t.route) nav(t.route); });
    }
    function paintLiveSpeed() {
      const d = $("#down-speed"), u = $("#up-speed");
      if (d) d.textContent = state.down_speed + " MB/s";
      if (u) u.textContent = state.up_speed + " MB/s";
      const t = $(".hero .t"), n = $(".hero .node-line");
      if (t) t.textContent = state.connected ? "已连接" : "未连接";
      if (n) n.textContent = state.connected
        ? state.node + " · " + state.latency + "ms"
        : "点击上方按钮开始连接";
    }

    document.addEventListener("DOMContentLoaded", () => { bindTitlebar(); boot(false); });
})();
