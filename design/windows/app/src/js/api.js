/* 数据层：宿主（Electron 主进程）→ 真实命令；浏览器 → 内置演示数据。
 *
 * 命令名与后端 electron/ipc.js 逐条对应。所有方法返回 Promise；
 * 业务失败返回 {ok:false,msg}，真异常直接 reject（由调用方 toast）。 */
(function () {
  const isMock = /(\?|&)mock=1/.test(location.search);
  const host = !isMock && window.polaris && window.polaris.invoke ? window.polaris : null;
  const isHost = !!host;

  function invoke(cmd, args) {
    if (isHost) return host.invoke(cmd, args || {});
    const fn = Mock[cmd];
    if (!fn) return Promise.resolve(null);
    try { return Promise.resolve(fn(args || {})); }
    catch (e) { return Promise.reject(e); }
  }

  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

  /* ---------------- 演示数据（仅浏览器预览 / --mock） ---------------- */
  const NODES = [
    { name: "🇭🇰 香港 01", region: "香港", latency: 45, offline: false },
    { name: "🇭🇰 香港 02", region: "香港", latency: 62, offline: false },
    { name: "🇯🇵 日本 东京 01", region: "日本", latency: 78, offline: false },
    { name: "🇸🇬 新加坡 01", region: "新加坡", latency: 92, offline: false },
    { name: "🇺🇸 美国 洛杉矶 01", region: "美国", latency: 168, offline: false },
    { name: "🇺🇸 美国 圣何塞 02", region: "美国", latency: -1, offline: true },
    { name: "🇩🇪 德国 法兰克福", region: "德国", latency: 210, offline: false },
  ];

  const Mock = {
    get_status: () => ({
      connected: false, node: "", latency: 0,
      up_speed: 0, down_speed: 0, up_total: "0 B", down_total: "0 B",
      uptime: "00:00:00", mode: "规则模式", phase: "idle", core_alive: false,
    }),
    connect: () => ({ ok: true, connected: true }),
    disconnect: () => ({ ok: true }),
    get_nodes: () => NODES.map((n) => Object.assign({}, n)),
    select_node: (a) => ({ ok: true, node: a.name }),
    speed_test: async () => { await sleep(900); return { ok: true }; },
    refresh_subscription: async () => { await sleep(700); return { ok: true, count: NODES.length }; },
    set_proxy_mode: (a) => ({ ok: true, mode: a.mode }),
    get_traffic: () => ({
      range: "today", down_today: "8.62 GB", up_today: "1.24 GB",
      total_down: "186.4 GB", total_up: "22.7 GB",
      peak: "42.1 / 6.8 MB/s", online_nodes: 6,
    }),
    get_traffic_series: () => ({
      points: Array.from({ length: 24 }, (_, i) => ({
        label: String(i).padStart(2, "0") + ":00",
        down: Math.round(40 + Math.random() * 90),
        up: Math.round(8 + Math.random() * 30),
      })),
      unit: "MB",
    }),
    get_traffic_log: () => [],
    get_routing_groups: () => ([
      { name: "国外网站分流", type: "select", now: "香港 01", count: 128, options: NODES.map((n) => n.name), builtin: false },
      { name: "流媒体分流", type: "select", now: "新加坡 01", count: 46, options: NODES.map((n) => n.name), builtin: false },
      { name: "本地直连", type: "select", now: "DIRECT", count: 0, options: ["DIRECT"], builtin: true },
    ]),
    set_routing_group: (a) => ({ ok: true, now: a.node }),
    reset_routing_groups: () => ({ ok: true }),
    get_rulesets: () => ({
      total: 26,
      enabled: ["苹果服务", "Google Play", "Google", "哔哩哔哩", "国内直连", "国外穿墙"],
      groups: [
        { name: "广告拦截", out: "block", enabled: false, count: 1, inline: 0, default_on: false, now: "", live: false },
        { name: "苹果服务", out: "direct", enabled: true, count: 2, inline: 1, default_on: true, now: "DIRECT", live: true },
        { name: "油管视频", out: "proxy", enabled: false, count: 2, inline: 0, default_on: false, now: "", live: false },
        { name: "Google", out: "proxy", enabled: true, count: 2, inline: 1, default_on: true, now: "香港 01", live: true },
        { name: "哔哩哔哩", out: "direct", enabled: true, count: 1, inline: 0, default_on: true, now: "DIRECT", live: true },
        { name: "国内直连", out: "direct", enabled: true, count: 2, inline: 0, default_on: true, now: "DIRECT", live: true },
        { name: "国外穿墙", out: "proxy", enabled: true, count: 1, inline: 0, default_on: true, now: "香港 01", live: true },
      ],
    }),
    set_ruleset: (a) => ({ ok: true, enabled: !!a.on, applied: false }),
    move_ruleset: () => ({ ok: true, moved: false, applied: false }),
    save_custom_ruleset: (a) => ({ ok: true, name: (a && a.name) || "", applied: false }),
    delete_custom_ruleset: () => ({ ok: true, applied: false }),
    reset_rulesets: () => ({ ok: true, applied: false }),
    get_plan: () => ({ name: "旗舰套餐", used: 86, total: 200, expire: "2026-11-05" }),
    get_plans: () => ([
      { id: "1", name: "轻量套餐", price: "19", unit: "月", feats: ["100 GB 流量", "3 台设备"], hot: false },
      { id: "2", name: "旗舰套餐", price: "39", unit: "月", feats: ["200 GB 流量", "5 台设备", "全节点解锁"], hot: true },
      { id: "3", name: "无限套餐", price: "79", unit: "月", feats: ["不限流量", "10 台设备", "专属线路"], hot: false },
    ]),
    create_order: () => ({ ok: true, order_no: "PL" + Date.now() }),
    get_orders: () => ([
      { no: "PL20260901001", name: "旗舰套餐", amount: "39.00", date: "2026-09-01 12:04", status: "done" },
      { no: "PL20260801007", name: "旗舰套餐", amount: "39.00", date: "2026-08-01 09:31", status: "done" },
    ]),
    pay_order: () => ({ ok: true, url: "" }),
    get_payment_methods: () => ([{ id: "1", name: "支付宝" }, { id: "2", name: "微信支付" }]),
    get_tickets: () => ([
      { no: "1024", subject: "节点连接超时", date: "2026-10-06 14:22", status: "replied" },
      { no: "1011", subject: "支付后套餐未生效", date: "2026-09-18 08:05", status: "closed" },
    ]),
    create_ticket: () => ({ ok: true, no: "1025" }),
    get_invite: () => ({ code: "ABC123", link: "https://panel.example.com/register?code=ABC123", invited: 3, earned: "￥45" }),
    get_gift_history: () => [],
    redeem_gift: (a) => (String(a.code || "").length >= 16 ? { ok: true, reward: "30 天时长", msg: "" } : { ok: false, reward: "", msg: "卡密格式不正确" }),
    get_notices: () => ([
      { id: "1", title: "国庆假期节点维护通知", date: "2026-09-30", unread: true, body: "10 月 1 日至 3 日期间，部分香港节点将进行线路维护。" },
      { id: "2", title: "新增 3 条 IEPL 专线", date: "2026-09-22", unread: false, body: "新增香港、日本、新加坡各一条 IEPL 专线，已加入默认分组。" },
    ]),
    login: (a) => ({ ok: true, email: a.email, msg: "" }),
    logout: () => ({ ok: true }),
    register: () => ({ ok: true, email: "", msg: "" }),
    forgot_password: () => ({ ok: true, msg: "" }),
    send_email_code: () => ({ ok: true, msg: "" }),
    change_password: () => ({ ok: true, msg: "" }),
    get_site_info: () => ({ appName: "Polaris 演示面板", appDescription: "演示数据", appUrl: "", telegramUrl: "" }),
    get_settings: () => ({
      theme: "system", lang: "zh-CN", tun: "gvisor",
      expire_notify: true, traffic_notify: true, autostart: false,
      version: "1.8.0", panel_url: "", last_email: "", email: "u***@example.com",
      sys_proxy: true, tun_mode: false, allow_lan: false, ipv6: false,
      auto_update: true, authed: true,
    }),
    set_setting: () => ({ ok: true }),
    get_app_info: () => ({ version: "1.8.0", portable: false, data_dir: "(浏览器预览)", is_admin: false }),
    check_update: () => ({ has_update: false, version: "1.8.0", size: "", notes: "" }),
    get_update_state: () => ({ phase: "idle", version: "", percent: 0, can_apply: false, install_dir: "", portable: false, packaged: false }),
    download_update: () => ({ phase: "idle", version: "", percent: 0 }),
    apply_update: () => ({ ok: false, msg: "演示模式不支持安装更新" }),
    discard_update: () => ({ ok: true }),
    open_external: () => ({ ok: true }),
    export_logs: () => ({ ok: false }),
    is_admin: () => false,
    restart_as_admin: () => ({ ok: false, msg: "浏览器预览不支持" }),
  };

  async function listMethod(cmd) {
    const r = await invoke(cmd);
    return Array.isArray(r) ? r : [];
  }

  window.PolarisAPI = {
    isHost,
    isTauri: isHost,          // 兼容旧字段
    invoke,

    /* 内核 */
    getStatus: () => invoke("get_status"),
    connect: () => invoke("connect"),
    disconnect: () => invoke("disconnect"),
    getNodes: () => listMethod("get_nodes"),
    selectNode: (name) => invoke("select_node", { name }),
    speedTest: () => invoke("speed_test"),
    refreshSubscription: () => invoke("refresh_subscription"),
    setProxyMode: (mode) => invoke("set_proxy_mode", { mode }),

    /* 流量 */
    getTraffic: (range) => invoke("get_traffic", { range }),
    getTrafficSeries: (range) => invoke("get_traffic_series", { range }),
    getTrafficLog: () => listMethod("get_traffic_log"),

    /* 分流 */
    getRoutingGroups: () => listMethod("get_routing_groups"),
    setRoutingGroup: (name, node) => invoke("set_routing_group", { name, node }),
    resetRoutingGroups: () => invoke("reset_routing_groups"),
    getRulesets: () => invoke("get_rulesets"),
    setRuleset: (name, on) => invoke("set_ruleset", { name, on }),
    moveRuleset: (name, dir) => invoke("move_ruleset", { name, dir }),
    saveCustomRuleset: (arg) => invoke("save_custom_ruleset", arg || {}),
    deleteCustomRuleset: (name) => invoke("delete_custom_ruleset", { name }),
    resetRulesets: () => invoke("reset_rulesets"),

    /* 套餐 / 订单 */
    getPlan: () => invoke("get_plan"),
    getPlans: () => listMethod("get_plans"),
    createOrder: (planId, coupon) => invoke("create_order", { plan_id: planId, coupon }),
    getOrders: () => listMethod("get_orders"),
    payOrder: (orderNo, method) => invoke("pay_order", { order_no: orderNo, method }),
    getPaymentMethods: () => listMethod("get_payment_methods"),

    /* 工单 */
    getTickets: () => listMethod("get_tickets"),
    createTicket: (subject, content) => invoke("create_ticket", { subject, content }),

    /* 邀请 / 礼品卡 / 公告 */
    getInvite: () => invoke("get_invite"),
    getGiftHistory: () => listMethod("get_gift_history"),
    redeemGift: (code) => invoke("redeem_gift", { code }),
    getNotices: () => listMethod("get_notices"),

    /* 账号 */
    login: (email, password, panel) => invoke("login", { email, password, panel }),
    logout: () => invoke("logout"),
    register: (email, password, code, invite) => invoke("register", { email, password, code, invite }),
    forgotPassword: (email, code, password) => invoke("forgot_password", { email, code, password }),
    sendEmailCode: (email, purpose) => invoke("send_email_code", { email, purpose }),
    changePassword: (oldPwd, newPwd) => invoke("change_password", { old_password: oldPwd, new_password: newPwd }),
    getSiteInfo: () => invoke("get_site_info"),

    /* 设置 / 系统 */
    getSettings: () => invoke("get_settings"),
    setSetting: (key, value) => invoke("set_setting", { key, value }),
    getAppInfo: () => invoke("get_app_info"),
    checkUpdate: () => invoke("check_update"),
    getUpdateState: () => invoke("get_update_state"),
    downloadUpdate: (url, version) => invoke("download_update", { url, version }),
    applyUpdate: () => invoke("apply_update"),
    discardUpdate: () => invoke("discard_update"),
    openExternal: (url) => invoke("open_external", { url }),
    exportLogs: () => invoke("export_logs"),
    isAdmin: () => invoke("is_admin"),
    restartAsAdmin: () => invoke("restart_as_admin"),
    getTunStatus: () => invoke("get_tun_status"),
    cleanupTun: () => invoke("cleanup_tun"),
  };
})();
