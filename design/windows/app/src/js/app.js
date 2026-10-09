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
    testing: false,
    refreshing: false,
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

  const content = $("#content");
  const overlayRoot = $("#overlay-root");
  const sidebar = $("#sidebar");

  /* ---------------- 渲染 ---------------- */
  let renderCount = 0;
  function render() {
    // 自检用：设置页曾经因为 loadTunStatus→render 互相调用而无限重绘，
    // 只有数渲染次数才测得到（DOM 断言在同一个 JS 帧里看不出问题）。
    renderCount += 1;
    window.__polarisRenderCount = renderCount;
    overlayRoot.innerHTML = "";
    if (AUTH_ROUTES.includes(state.route)) {
      state.loggedIn = false;
      sidebar.style.display = "none";
      content.innerHTML = (Views[state.route] || Views.login)(state);
      bindAuth();
      return;
    }
    sidebar.style.display = "";
    content.innerHTML = (Views[state.route] || Views.home)(state);
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
  }

  function nav(route) {
    if (route === "register" || route === "forgot" || route === "login") state.authError = "";
    state.route = route;
    render();
    // 虚拟网卡状态要起 PowerShell（冷启动实测 ~2.7s），只在进设置页时查一次。
    // 绝不能放在 bindSettings 里 —— 那会变成 render→loadTunStatus→render 死循环。
    if (route === "settings") loadTunStatus();
  }
  window.PolarisNav = nav;

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
      console.error(label, e);
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
      await loadTraffic();
      render();
    }));
    $$("[data-group]").forEach((el) => el.addEventListener("click", () => {
      const g = state.groups.find((x) => x.name === el.dataset.group);
      if (g) openDialog("groupPick", g);
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
    else if (action === "export-logs") {
      const r = await guard("导出日志", () => api.exportLogs());
      if (r) toast(r.ok ? "日志已导出到 " + r.path : "已取消");
    } else if (action === "license") openDialog("about", state.appInfo);
    else if (action === "cleanup-tun") {
      const r = await guard("清理虚拟网卡", () => api.invoke("cleanup_tun"));
      if (r) { toast(r.msg || "已处理"); loadTunStatus(true); }
    }
    else if (action === "set-panel") {
      await guard("退出登录", () => api.logout());
      nav("login");
    } else if (action === "refresh-sub") {
      state.refreshing = true;
      const r = await guard("刷新订阅", () => api.refreshSubscription());
      state.refreshing = false;
      if (r) { await refreshAll(); toast("订阅已刷新，共 " + r.count + " 个节点"); }
      render();
    } else if (action === "show-subscribe-url") {
      const info = await guard("获取订阅链接", () => api.invoke("get_subscribe_url"));
      if (info) openDialog("subscribeUrl", info);
    }
    else if (["nav-orders", "nav-tickets", "nav-invite", "nav-giftcard", "nav-notices", "nav-settings", "nav-routing"].includes(action)) {
      nav(action.replace("nav-", ""));
    }
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
      $("#btn-submit-ticket").addEventListener("click", async () => {
        const subject = $("#ticket-subject").value.trim();
        const body = $("#ticket-content").value.trim();
        if (!subject) { toast("请填写标题"); return; }
        const r = await guard("提交工单", () => api.createTicket(subject, body));
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
      try {
        if (state.connected) {
          await api.disconnect();
          state.connected = false;
          toast("已断开");
        } else {
          if (!state.settings.authed) { state.busy = false; nav("login"); return; }
          await api.connect();
          state.connected = true;
          toast("已连接");
        }
        state.busy = false;
        await refreshAll();
      } catch (e) {
        state.busy = false;
        toast((state.connected ? "断开" : "连接") + "失败：" + (e.message || e));
        await refreshStatus();
      }
      render();
    });
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
          autoRefreshSubscription();
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
    $$(".node-row").forEach((el) => el.addEventListener("click", async () => {
      const r = await guard("切换节点", () => api.selectNode(el.dataset.node));
      if (r && r.ok) {
        state.node = r.node;
        const n = state.nodes.find((x) => x.name === r.node);
        if (n && n.latency > 0) state.latency = n.latency;
        render(); toast("已切换到 " + r.node);
      }
    }));
    const st = $("#btn-speedtest");
    if (st) st.addEventListener("click", async () => {
      state.testing = true; render();
      const r = await guard("测速", () => api.speedTest());
      state.testing = false;
      if (r) state.nodes = await api.getNodes().catch(() => state.nodes);
      render();
      if (r) toast("测速完成");
    });
    const rf = $("#btn-refresh-sub");
    if (rf) rf.addEventListener("click", async () => {
      state.refreshing = true;
      const r = await guard("刷新订阅", () => api.refreshSubscription());
      state.refreshing = false;
      if (r) { await refreshAll(); toast("订阅已刷新，共 " + r.count + " 个节点"); }
      render();
    });
  }

  function bindTraffic() { /* 分段切换在 bindCommon 处理 */ }

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
      title: "恢复内置分流默认", body: "按产品预设恢复各分类的开关（苹果服务、Google、哔哩哔哩、国内直连、国外穿墙默认开启）。已连接时会热重载配置。", yes: "恢复默认",
      onYes: async () => {
        const r = await guard("恢复默认", () => api.resetRulesets());
        if (r) { await refreshRoutes(); render(); toast("已恢复内置分流默认"); }
      },
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
    // 点行：给这个分类单独指定出口
    $$("[data-ruleset-pick]").forEach((el) => el.addEventListener("click", async (e) => {
      if (e.target.closest(".switch")) return;
      const name = el.dataset.rulesetPick;
      const rs = ((state.rulesets || {}).groups || []).find((x) => x.name === name);
      if (rs && !rs.enabled) { toast("先开启该分类，才能单独指定出口"); return; }
      if (!state.connected) { toast("连接后才能单独指定出口"); return; }
      const g = (state.groups || []).find((x) => x.name === name);
      if (!g) { toast("该分组尚未在内核中生效"); return; }
      openDialog("groupPick", g);
    }));
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

  function bindSettings() {
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
      await guard("保存设置", () => api.setSetting(k, next));
      sw.classList.toggle("on", next);
    }));
    $$("[data-click]").forEach(() => {});
  }

  function bindMe() { /* data-click 已在 bindCommon 处理 */ }

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
    const [t, series, log] = await Promise.all([
      api.getTraffic(state.trafficRange).catch(() => ({})) ,
      api.getTrafficSeries(state.trafficRange).catch(() => ({ points: [], unit: "MB" })),
      api.getTrafficLog().catch(() => []),
    ]);
    state.traffic = t || {};
    state.trafficSeries = series || { points: [], unit: "MB" };
    state.trafficLog = Array.isArray(log) ? log : [];
  }

  /** 登录后 / 连接后统一刷新 */
  async function refreshAll(includeAuth) {
    if (includeAuth) {
      state.settings = await api.getSettings().catch(() => state.settings);
      state.appInfo = await api.getAppInfo().catch(() => state.appInfo);
      applyTheme(state.settings.theme || "system");
    }
    await refreshStatus();
    const [nodes, groups, rulesets, plan, plans, orders, tickets, invite, gh, notices, site, rcfg] = await Promise.all([
      api.getNodes().catch(() => []),
      api.getRoutingGroups().catch(() => []),
      api.getRulesets().catch(() => state.rulesets),
      api.getPlan().catch(() => null),
      api.getPlans().catch(() => []),
      api.getOrders().catch(() => []),
      api.getTickets().catch(() => []),
      api.getInvite().catch(() => null),
      api.getGiftHistory().catch(() => []),
      api.getNotices().catch(() => []),
      api.getSiteInfo().catch(() => state.siteInfo),
      api.invoke("get_register_config").catch(() => state.registerConfig),
    ]);
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
    if (site && site.appName) state.siteInfo = site;
    if (rcfg) state.registerConfig = Object.assign({ email_verify: 0, invite_force: 0 }, rcfg);
    state.email = state.settings.email || state.settings.last_email || "";
    await loadTraffic();
  }

  let autoRefreshing = false;
  async function autoRefreshSubscription() {
    if (autoRefreshing || !state.settings.authed) return;
    autoRefreshing = true;
    try {
      const stale = !state.settings.subscription_updated_at ||
        Date.now() - state.settings.subscription_updated_at > 12 * 3600 * 1000;
      if (stale || state.nodes.length === 0) {
        const r = await api.refreshSubscription();
        if (r && r.ok) { state.nodes = await api.getNodes().catch(() => state.nodes); render(); }
      }
    } catch (e) {
      console.warn("自动拉取订阅失败", e);
    } finally {
      autoRefreshing = false;
    }
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
    await refreshAll();
    state.route = "home";
    render();
    state.booted = true;
    bindLiveStatus();
    autoRefreshSubscription();
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
