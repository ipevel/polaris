/* 数据层：优先走 Tauri 后端命令，非 Tauri 环境（浏览器预览）用本地 mock。
 * 后端实现见 src-tauri/src/main.rs，所有命令名与之一一对应。 */
(function () {
  const isTauri = !!(window.__TAURI__ && window.__TAURI__.core);

  async function invoke(cmd, args) {
    if (isTauri) return window.__TAURI__.core.invoke(cmd, args || {});
    return Mock[cmd] ? Mock[cmd](args || {}) : null;
  }

  /* ---------- 本地 mock（仅浏览器预览用，打包后走 Rust 实现） ---------- */
  const Mock = {
    get_status: () => Promise.resolve({
      connected: true, node: "香港 01", latency: 45,
      up_speed: 2.1, down_speed: 12.4,
      up_total: "1.2 GB", down_total: "8.6 GB", uptime: "02:34:18",
      mode: "规则模式",
    }),
    connect: () => Promise.resolve({ ok: true }),
    disconnect: () => Promise.resolve({ ok: true }),
    get_nodes: () => Promise.resolve([
      { name: "香港 01", region: "香港", latency: 45, group: "节点选择" },
      { name: "香港 02", region: "香港", latency: 62, group: "节点选择" },
      { name: "新加坡 01", region: "新加坡", latency: 188, group: "节点选择" },
      { name: "日本 01", region: "日本", latency: 92, group: "节点选择" },
      { name: "美国 01", region: "美国", latency: -1, group: "节点选择" },
    ]),
    select_node: (a) => Promise.resolve({ ok: true, node: a.name }),
    speed_test: () => new Promise((r) => setTimeout(() => r({ ok: true }), 1200)),
    refresh_subscription: () => new Promise((r) => setTimeout(() => r({ ok: true, count: 32 }), 1500)),
    get_traffic: (a) => Promise.resolve({
      range: a.range || "today",
      down_today: "8.6 GB", up_today: "1.2 GB",
      total_down: "128.4 GB", total_up: "36.2 GB",
      peak: "24.8 MB/s", online_nodes: 28,
    }),
    get_plan: () => Promise.resolve({ name: "旗舰套餐", used: 86, total: 200, unit: "GB", expire: "2026-11-05" }),
    get_plans: () => Promise.resolve([
      { id: "m", name: "月度套餐", price: "19.9", unit: "月", feats: ["100GB 流量", "2 台设备"] },
      { id: "y", name: "年度套餐", price: "169", unit: "年", feats: ["500GB 流量", "5 台设备", "优先线路"], hot: true },
      { id: "q", name: "季度套餐", price: "49", unit: "季", feats: ["200GB 流量", "3 台设备"] },
    ]),
    create_order: (a) => Promise.resolve({ ok: true, order_no: "No.202610091205" }),
    get_orders: () => Promise.resolve([
      { name: "旗舰年付套餐", no: "No.202610091201", date: "2026-10-09", amount: "169", status: "done" },
      { name: "旗舰年付套餐", no: "No.202610071103", date: "2026-10-07", amount: "169", status: "pending" },
      { name: "月度套餐", no: "No.20260912088", date: "2026-09-12", amount: "19.9", status: "refunded" },
    ]),
    pay_order: () => new Promise((r) => setTimeout(() => r({ ok: true }), 800)),
    get_tickets: () => Promise.resolve([
      { subject: "节点连接超时", no: "No.2026100801", date: "2026-10-08", status: "replied" },
      { subject: "支付结算异常", no: "No.2026100703", date: "2026-10-07", status: "pending" },
      { subject: "账户登录失败", no: "No.2026100509", date: "2026-10-05", status: "closed" },
    ]),
    create_ticket: (a) => Promise.resolve({ ok: true, no: "No.202610091301" }),
    get_invite: () => Promise.resolve({ code: "AB12CD", link: "https://polaris.app/i/AB12CD", invited: 3, earned: "30 GB" }),
    get_gift_history: () => Promise.resolve([
      { code: "XXXX-XXXX-XXXX-1234", reward: "已到账 50 GB", date: "2024-06-12 14:23" },
      { code: "XXXX-XXXX-XXXX-5678", reward: "已到账 30 天", date: "2024-06-10 09:05" },
    ]),
    redeem_gift: (a) => (/^[A-Za-z0-9-]{16,}$/.test((a.code || "").replace(/-/g, "")) && a.code.replace(/-/g, "").length === 16)
      ? Promise.resolve({ ok: true, reward: "50 GB" })
      : Promise.resolve({ ok: false, msg: "卡密格式不正确" }),
    get_notices: () => Promise.resolve([
      { title: "v1.7.4 版本更新说明", date: "2026-10-09", unread: true, body: "1. 修复后台运行时长清零问题\n2. 速率显示采用非对称 EMA 平滑\n3. 优化节点测速逻辑" },
      { title: "香港线路维护通知", date: "2026-10-07", body: "香港 03 节点将于 10-10 02:00-04:00 维护，届时自动切换。" },
      { title: "国庆活动：年付 8 折", date: "2026-10-01", body: "活动期间年度套餐 8 折优惠，自动生效。" },
    ]),
    set_proxy_mode: (a) => Promise.resolve({ ok: true, mode: a.mode }),
    get_settings: () => Promise.resolve({ theme: "system", lang: "zh-CN", tun: "System", expire_notify: true, traffic_notify: true, autostart: false, version: "1.7.4" }),
    set_setting: () => Promise.resolve({ ok: true }),
    check_update: () => new Promise((r) => setTimeout(() => r({ has_update: true, version: "1.7.5", size: "34.6 MB", notes: "1. 修复后台运行时长清零问题\n2. 速率显示更加平滑\n3. 优化节点测速逻辑" }), 900)),
    login: (a) => (a.email && a.password)
      ? Promise.resolve({ ok: true, email: a.email })
      : Promise.resolve({ ok: false, msg: "请输入邮箱和密码" }),
    logout: () => Promise.resolve({ ok: true }),
  };

  window.PolarisAPI = {
    isTauri,
    getStatus: () => invoke("get_status"),
    connect: () => invoke("connect"),
    disconnect: () => invoke("disconnect"),
    getNodes: () => invoke("get_nodes"),
    selectNode: (name) => invoke("select_node", { name }),
    speedTest: () => invoke("speed_test"),
    refreshSubscription: () => invoke("refresh_subscription"),
    getTraffic: (range) => invoke("get_traffic", { range }),
    getPlan: () => invoke("get_plan"),
    getPlans: () => invoke("get_plans"),
    createOrder: (plan_id) => invoke("create_order", { plan_id }),
    getOrders: () => invoke("get_orders"),
    payOrder: (no) => invoke("pay_order", { order_no: no }),
    getTickets: () => invoke("get_tickets"),
    createTicket: (subject, content) => invoke("create_ticket", { subject, content }),
    getInvite: () => invoke("get_invite"),
    getGiftHistory: () => invoke("get_gift_history"),
    redeemGift: (code) => invoke("redeem_gift", { code }),
    getNotices: () => invoke("get_notices"),
    setProxyMode: (mode) => invoke("set_proxy_mode", { mode }),
    getSettings: () => invoke("get_settings"),
    setSetting: (k, v) => invoke("set_setting", { key: k, value: v }),
    checkUpdate: () => invoke("check_update"),
    login: (email, password, panel) => invoke("login", { email, password, panel }),
    logout: () => invoke("logout"),
  };
})();
