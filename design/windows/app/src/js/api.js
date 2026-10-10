/* 数据层：渲染层 → 宿主（Electron 主进程）。
 *
 * 命令名与后端 electron/ipc.js 逐条对应。所有方法返回 Promise；
 * 业务失败返回 {ok:false,msg}，真异常直接 reject（由调用方 toast）。
 * 没有宿主时（例如用浏览器直接打开 src/index.html）**不伪造数据**，直接失败。 */
(function () {
  const host = window.polaris && window.polaris.invoke ? window.polaris : null;
  const isHost = !!host;

  function invoke(cmd, args) {
    if (!isHost) return Promise.reject(new Error('没有连接到客户端主进程（请用 Polaris.exe 或 npm start 启动）'));
    return host.invoke(cmd, args || {});
  }

  async function listMethod(cmd, args) {
    const r = await invoke(cmd, args);
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
    selectNode: (name, group) => invoke("select_node", { name, group }),
    speedTest: () => invoke("speed_test"),
    testGroupDelays: () => invoke("test_group_delays"),
    refreshSubscription: () => invoke("refresh_subscription"),
    setProxyMode: (mode) => invoke("set_proxy_mode", { mode }),

    /* 流量 */
    getTraffic: (range) => invoke("get_traffic", { range }),
    getTrafficSeries: (range) => invoke("get_traffic_series", { range }),
    getTrafficLog: (range) => listMethod("get_traffic_log", { range }),

    /* 分流 */
    getRoutingGroups: () => listMethod("get_routing_groups"),
    setRoutingGroup: (name, node) => invoke("set_routing_group", { name, node }),
    resetRoutingGroups: () => invoke("reset_routing_groups"),
    getRulesets: () => invoke("get_rulesets"),
    setRuleset: (name, on) => invoke("set_ruleset", { name, on }),
    reorderRuleset: (name, to) => invoke("reorder_ruleset", { name, to }),
    saveCustomRuleset: (arg) => invoke("save_custom_ruleset", arg || {}),
    deleteCustomRuleset: (name) => invoke("delete_custom_ruleset", { name }),
    resetRulesets: () => invoke("reset_rulesets"),
    setLocalRouting: (on) => invoke("set_local_routing", { on }),

    /* 套餐 / 订单 */
    getPlan: () => invoke("get_plan"),
    getPlans: () => listMethod("get_plans"),
    createOrder: (planId, coupon) => invoke("create_order", { plan_id: planId, coupon }),
    getOrders: () => listMethod("get_orders"),
    payOrder: (orderNo, method) => invoke("pay_order", { order_no: orderNo, method }),
    getPaymentMethods: () => listMethod("get_payment_methods"),

    /* 工单 */
    getTickets: () => listMethod("get_tickets"),
    createTicket: (subject, content, level = 1) => invoke("create_ticket", { subject, content, level }),

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
