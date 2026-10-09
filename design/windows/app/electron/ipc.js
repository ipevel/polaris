'use strict';
/**
 * 命令注册表 —— 渲染层唯一的后端入口。
 *
 * 命令名与 src/js/api.js 逐条对应，前端不因换壳改任何调用形状。
 * 约定：业务失败用 {ok:false,msg}；真正的异常（网络/内核/文件）直接 reject，
 * 由渲染层的 guard() 统一弹错。
 */

const fs = require('fs');
const path = require('path');
const { ipcMain, clipboard, BrowserWindow, shell, app, dialog } = require('electron');

const log = require('./logger');
const store = require('./store');
const paths = require('./paths');
const core = require('./core/manager');
const remote = require('./core/remote');
const traffic = require('./core/traffic');
const panel = require('./panel/client');
const autostart = require('./net/autostart');
const elevate = require('./net/elevate');
const sysproxy = require('./net/sysproxy');
const fmt = require('./util/format');
const ui = require('./ui');

const CH = 'polaris:invoke';
// 本次会话的峰值速率（MB/s）。旧代码从不重置 —— 断开重连后「峰值」还是
// 几小时前那一次的最高值，用户以为刚才跑出了那个速度。
const peak = { down: 0, up: 0 };
function resetPeak() { peak.down = 0; peak.up = 0; }

function needAuth() {
  if (!panel.isAuthed()) throw new Error('尚未登录面板');
}

function ok(extra) { return Object.assign({ ok: true }, extra || {}); }
function fail(msg) { return { ok: false, msg: msg || '操作失败' }; }

const commands = {
  /* ================= 内核 ================= */
  get_status: async () => core.status(),

  connect: async () => {
    needAuth();
    resetPeak();                     // 新会话从 0 起算
    const st = await core.connect();
    return ok({ connected: st.connected, node: st.node });
  },

  disconnect: async () => {
    await core.disconnect();
    return ok();
  },

  get_nodes: async () => core.loadNodes(),

  select_node: async ({ name }) => {
    if (!name) throw new Error('缺少节点名');
    return ok({ node: await core.selectNode(name) });
  },

  speed_test: async () => {
    await core.speedTest();
    return ok();
  },

  refresh_subscription: async () => {
    needAuth();
    await panel.refreshSubscription();
    // 订阅域名可能是第一次见到，立刻并入直连域名再生成配置
    core.setDirectDomains(remote.directDomains());
    const { count } = core.prepareConfig();
    if (core.status().connected) {
      // 已在连接：重载配置而不是让用户手动断开重连
      resetPeak();
      await core.disconnect();
      await core.connect();
    }
    return ok({ count });
  },

  set_proxy_mode: async ({ mode }) => {
    if (!mode) throw new Error('缺少模式');
    return ok({ mode: fmt.modeLabel(await core.setMode(fmt.modeKey(mode))) });
  },

  /* ================= 分流 ================= */
  get_routing_groups: async () => core.routingGroups(),
  set_routing_group: async ({ name, node }) => core.setRoutingGroup(name, node),
  reset_routing_groups: async () => core.resetRoutingGroups(),

  /* ================= 流量 ================= */
  get_traffic: async ({ range }) => {
    const st = core.status();
    const session = traffic.totals();
    let online = 0;
    for (const n of core.cachedNodes()) if (!n.offline) online += 1;

    let totalDown = session.down;
    let totalUp = session.up;
    if (panel.isAuthed()) {
      try {
        const u = await panel.userInfo();
        totalDown = u.d || totalDown;
        totalUp = u.u || totalUp;
      } catch (e) {
        log.warn('userInfo for traffic failed:', e && e.message);
      }
    }
    return {
      range: range || 'today',
      down_today: fmt.bytes(st.connected ? traffic.series('today').points.reduce((a, p) => a + p.down, 0) * 1048576 : 0),
      up_today: fmt.bytes(st.connected ? traffic.series('today').points.reduce((a, p) => a + p.up, 0) * 1048576 : 0),
      total_down: fmt.bytes(totalDown),
      total_up: fmt.bytes(totalUp),
      peak: `${fmt.speed(peak.down)} / ${fmt.speed(peak.up)} MB/s`,
      online_nodes: online,
    };
  },

  get_traffic_series: async ({ range }) => traffic.series(range || 'today'),

  get_traffic_log: async () => {
    if (!panel.isAuthed()) return [];
    try { return await panel.trafficLog(); } catch (e) { log.warn('trafficLog failed:', e.message); return []; }
  },

  /* ================= 账号 ================= */
  login: async ({ email, password, panel: panelInput }) => {
    try {
      const r = await panel.login(email, password, panelInput);
      await remote.load(true).catch(() => {});
      core.setDirectDomains(remote.directDomains());
      return ok({ email: r.email });
    } catch (e) {
      return fail(friendly(e));
    }
  },

  logout: async () => {
    await core.disconnect().catch(() => {});
    panel.logout();
    store.patch({ last_node: '', last_email: store.get('last_email') });
    return ok();
  },

  register: async ({ email, password, code, invite }) => {
    try {
      const r = await panel.register(email, password, code, invite);
      await remote.load(true).catch(() => {});
      core.setDirectDomains(remote.directDomains());
      return ok({ email: r.email });
    } catch (e) { return fail(friendly(e)); }
  },

  forgot_password: async ({ email, code, password }) => {
    try { await panel.forgotPassword(email, code, password); return ok(); }
    catch (e) { return fail(friendly(e)); }
  },

  send_email_code: async ({ email, purpose }) => {
    try { await panel.sendEmailCode(email, purpose); return ok(); }
    catch (e) { return fail(friendly(e)); }
  },

  change_password: async ({ old_password: oldPwd, new_password: newPwd }) => {
    try { await panel.changePassword(oldPwd, newPwd); return ok(); }
    catch (e) { return fail(friendly(e)); }
  },

  get_register_config: async () => {
    try { return await panel.registerConfig(); } catch (e) { return { email_verify: 0, invite_force: 0 }; }
  },

  get_site_info: async () => panel.siteInfo(),

  /* ================= 面板业务 ================= */
  get_plan: async () => {
    needAuth();
    const u = await panel.userInfo();
    return { name: u.plan_name, used: u.used, total: u.total, expire: u.expire };
  },

  get_plans: async () => {
    needAuth();
    const list = await panel.plans();
    return list.map((p) => ({
      id: p.id,
      name: p.name,
      price: String(p.price),
      unit: p.unit || '月',
      feats: p.feats.length ? p.feats : [`${Math.round((p.transfer_enable || 0) / (1024 ** 3))} GB 流量`],
      hot: p.hot,
    }));
  },

  create_order: async ({ plan_id: planId, coupon }) => {
    needAuth();
    if (!planId) throw new Error('缺少套餐 ID');
    const r = await panel.createOrder(planId, { couponCode: coupon });
    return ok({ order_no: r.order_no });
  },

  get_orders: async () => { needAuth(); return panel.orders(); },

  get_payment_methods: async () => { needAuth(); return panel.paymentMethods(); },

  pay_order: async ({ order_no: orderNo, method }) => {
    needAuth();
    if (!orderNo) throw new Error('缺少订单号');
    let methodId = method;
    if (!methodId) {
      const methods = await panel.paymentMethods();
      methodId = methods.length ? methods[0].id : '';
    }
    const url = await panel.checkoutUrl(orderNo, methodId);
    if (!url) throw new Error('面板未返回支付地址');
    await shell.openExternal(url);
    return ok({ url });
  },

  get_tickets: async () => { needAuth(); return panel.tickets(); },

  create_ticket: async ({ subject, content }) => {
    needAuth();
    if (!subject) throw new Error('请填写工单标题');
    const r = await panel.createTicket(subject, content);
    return ok({ no: r.no });
  },

  get_invite: async () => {
    needAuth();
    const i = await panel.invite();
    return {
      code: i.code,
      link: i.link || (i.code ? `${panel.session.panelUrl}/#/register?code=${i.code}` : ''),
      invited: i.invited,
      earned: i.balance ? `￥${i.balance}` : '0',
      rate: i.rate,
    };
  },

  get_gift_history: async () => {
    if (!panel.isAuthed()) return [];
    return panel.giftHistory();
  },

  redeem_gift: async ({ code }) => {
    needAuth();
    return panel.redeemGift(code);
  },

  get_notices: async () => {
    if (!panel.isAuthed()) return [];
    return panel.notices();
  },

  get_subscribe_url: async () => {
    needAuth();
    const s = await panel.subscribeInfo();
    return { url: s.subscribe_url || '' };
  },

  /* ================= 设置 ================= */
  get_settings: async () => {
    const s = store.all();
    return {
      theme: s.theme,
      lang: s.lang,
      tun: s.tun,
      expire_notify: s.expire_notify,
      traffic_notify: s.traffic_notify,
      autostart: s.autostart,
      auto_update: s.auto_update,
      version: s.version,
      panel_url: s.panel_url,
      last_email: s.last_email,
      sys_proxy: s.sys_proxy,
      tun_mode: s.tun_mode,
      allow_lan: s.allow_lan,
      ipv6: s.ipv6,
      subscription_updated_at: s.subscription_updated_at,
      email: s.last_email ? fmt.maskEmail(s.last_email) : '',
      authed: panel.isAuthed(),
    };
  },

  set_setting: async ({ key, value }) => {
    const BOOL_KEYS = ['autostart', 'expire_notify', 'traffic_notify', 'sys_proxy', 'auto_update', 'tun_mode', 'allow_lan', 'ipv6'];
    const STR_KEYS = ['theme', 'lang', 'tun', 'panel_url', 'last_email'];
    if (!BOOL_KEYS.includes(key) && !STR_KEYS.includes(key)) throw new Error('未知设置项：' + key);

    if (key === 'autostart') {
      const okk = autostart.apply(!!value);
      if (!okk && value) throw new Error('设置开机自启动失败');
    }
    if (key === 'tun_mode') {
      if (value && !(await elevate.isAdmin())) throw new Error('TUN 模式需要管理员权限');
      if (core.status().connected) throw new Error('请先断开连接再修改 TUN 模式');
    }
    if (key === 'sys_proxy' && !value && core.status().connected) {
      sysproxy.disable(core.S.proxySnapshot);
      core.S.proxySnapshot = null;
    }
    store.set(key, BOOL_KEYS.includes(key) ? !!value : value);
    return ok();
  },

  get_app_info: async () => ({
    version: store.get('version'),
    portable: paths.isPortable(),
    data_dir: paths.data(),
    core_dir: paths.core(),
    root_dir: paths.root(),
    electron: process.versions.electron,
    chrome: process.versions.chrome,
    node: process.versions.node,
    is_admin: await elevate.isAdmin(),
  }),

  is_admin: async () => elevate.isAdmin(),

  restart_as_admin: async () => elevate.restartAsAdmin(),

  get_tun_status: async ({ force } = {}) => require('./net/tun').status(!!force),

  cleanup_tun: async () => require('./net/tun').cleanup(),

  /* ================= 更新 / 诊断 ================= */
  check_update: async () => {
    await remote.load(true).catch(() => {});
    const u = remote.updateInfo();
    return { has_update: u.has_update, version: u.version, size: u.size, notes: u.notes, url: u.url };
  },

  open_download: async () => {
    const u = remote.updateInfo();
    const url = u.url || store.get('download_url') || '';
    if (!url) return fail('未配置更新地址');
    await shell.openExternal(url);
    return ok({ url });
  },

  open_external: async ({ url }) => {
    const u = String(url || '');
    if (!/^https?:\/\//i.test(u)) throw new Error('只允许打开 http(s) 链接');
    await shell.openExternal(u);
    return ok();
  },

  export_logs: async () => {
    const res = await dialog.showOpenDialog({
      title: '选择导出目录',
      properties: ['openDirectory', 'createDirectory'],
    });
    if (res.canceled || !res.filePaths.length) return { ok: false, msg: '已取消' };
    const target = path.join(res.filePaths[0], `polaris-logs-${Date.now()}.txt`);
    const body = [
      `Polaris ${store.get('version')} | portable=${paths.isPortable()} | admin=${await elevate.isAdmin()}`,
      `platform=${process.platform} ${process.arch} | electron=${process.versions.electron} node=${process.versions.node}`,
      `data=${paths.data()}`,
      `authed=${panel.isAuthed()} panel=${store.get('panel_url') || '-'}`,
      ''.padEnd(60, '='),
      log.tail(5000),
    ].join('\n');
    fs.writeFileSync(target, body, 'utf8');
    return ok({ path: target });
  },
};

function friendly(e) {
  const m = String(e && e.message ? e.message : e);
  if (e && e.kind === 'net') return `网络错误：${m}`;
  return m;
}

function register({ mock = false } = {}) {
  ipcMain.handle(CH, async (_evt, cmd, args) => {
    const fn = commands[cmd];
    if (!fn) throw new Error(`未知命令：${cmd}`);
    const t0 = Date.now();
    try {
      const out = await fn(args || {});
      const dt = Date.now() - t0;
      if (dt > 2000) log.warn(`slow command ${cmd} ${dt}ms`);
      return out;
    } catch (e) {
      log.error(`command ${cmd} failed:`, e && e.message);
      throw new Error(friendly(e));
    }
  });

  ipcMain.handle('polaris:win', (_e, action) => {
    const win = BrowserWindow.getFocusedWindow() || BrowserWindow.getAllWindows()[0];
    if (!win) return false;
    if (action === 'minimize') win.minimize();
    else if (action === 'toggleMaximize') { if (win.isMaximized()) win.unmaximize(); else win.maximize(); }
    else if (action === 'close') win.hide();
    return win.isMaximized();
  });

  ipcMain.handle('polaris:clipboard', (_e, text) => {
    clipboard.writeText(String(text || ''));
    return true;
  });

  // 状态推送：速率、连接状态、运行时长
  core.onStatus((st) => {
    if (st.connected) {
      peak.down = Math.max(peak.down, st.down_speed || 0);
      peak.up = Math.max(peak.up, st.up_speed || 0);
    }
    ui.emit('status', st);
  });

  if (mock) log.warn('mock 模式：前端将使用内置演示数据');
}

module.exports = { register, commands, isAdmin: elevate.isAdmin };
