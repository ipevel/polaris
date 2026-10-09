'use strict';
/**
 * 命令注册表 —— 渲染层唯一的后端入口。
 *
 * 命令名与 src/js/api.js 逐条对应，前端不因换壳改任何调用形状。
 * 约定：业务失败用 {ok:false,msg}；真正的异常（网络/内核/文件）直接 reject，
 * 由渲染层统一弹错。
 */

const fs = require('fs');
const path = require('path');
const { ipcMain, clipboard, BrowserWindow, shell, app, dialog } = require('electron');

const log = require('./logger');
const store = require('./store');
const core = require('./core/manager');
const remote = require('./core/remote');
const panel = require('./panel/client');
const autostart = require('./net/autostart');
const fmt = require('./util/format');
const ui = require('./ui');

const CH = 'polaris:invoke';
let peakDown = 0;
let peakUp = 0;
let lastTrafficStamp = null;

function needAuth() {
  if (!panel.isAuthed()) throw new Error('尚未登录面板');
}

const commands = {
  /* ---------------- 内核 ---------------- */
  get_status: async () => core.status(),

  connect: async () => {
    needAuth();
    const st = await core.connect();
    ui.toast(st.connected ? '已连接' : '已断开');
    return { ok: true, connected: st.connected, node: st.node };
  },

  disconnect: async () => {
    await core.disconnect();
    ui.toast('已断开连接');
    return { ok: true };
  },

  get_nodes: async () => core.loadNodes(),

  select_node: async ({ name }) => ({ ok: true, node: await core.selectNode(name) }),

  speed_test: async () => {
    await core.speedTest();
    return { ok: true };
  },

  refresh_subscription: async () => {
    needAuth();
    await panel.refreshSubscription();
    const { count } = core.prepareConfig();
    if (core.status().connected) {
      // 已在连接：重载配置而不是断线重来
      await core.disconnect();
      await core.connect();
    }
    ui.toast(`订阅已刷新，共 ${count} 个节点`);
    return { ok: true, count };
  },

  set_proxy_mode: async ({ mode }) => {
    const key = fmt.modeKey(mode);
    return { ok: true, mode: fmt.modeLabel(await core.setMode(key)) };
  },

  /* ---------------- 流量 ---------------- */
  get_traffic: async ({ range }) => {
    const st = core.status();
    let online = 0;
    for (const n of core.cachedNodes()) if (!n.offline) online += 1;

    let totalDown = 0;
    let totalUp = 0;
    let planTotal = 0;
    if (panel.isAuthed()) {
      try {
        const u = await panel.userInfo();
        totalDown = u.d || 0;
        totalUp = u.u || 0;
        planTotal = u.total || 0;
      } catch (e) { log.warn('userInfo for traffic failed:', e.message); }
    }
    void planTotal;
    return {
      range: range || 'today',
      down_today: st.down_total,
      up_today: st.up_total,
      total_down: fmt.bytes(totalDown),
      total_up: fmt.bytes(totalUp),
      peak: `${fmt.speed(peakDown)} / ${fmt.speed(peakUp)} MB/s`,
      online_nodes: online || core.cachedNodes().length,
    };
  },

  /* ---------------- 面板：账号 ---------------- */
  login: async ({ email, password, panel: panelInput }) => {
    try {
      const r = await panel.login(email, password, panelInput);
      await remote.load();
      core.setDirectDomains(remote.directDomains());
      return { ok: true, email: r.email, msg: '' };
    } catch (e) {
      return { ok: false, email: '', msg: friendly(e) };
    }
  },

  logout: async () => {
    await core.disconnect().catch(() => {});
    panel.logout();
    store.set('last_node', '');
    return { ok: true };
  },

  register: async ({ email, password, code, invite }) => {
    try {
      const r = await panel.register(email, password, code, invite);
      return { ok: true, email: r.email, msg: '' };
    } catch (e) { return { ok: false, msg: friendly(e) }; }
  },

  forgot_password: async ({ email, code, password }) => {
    try { await panel.forgotPassword(email, code, password); return { ok: true, msg: '' }; }
    catch (e) { return { ok: false, msg: friendly(e) }; }
  },

  send_email_code: async ({ email, purpose }) => {
    try { await panel.sendEmailCode(email, purpose); return { ok: true, msg: '' }; }
    catch (e) { return { ok: false, msg: friendly(e) }; }
  },

  change_password: async ({ old_password: oldPwd, new_password: newPwd }) => {
    try { await panel.changePassword(oldPwd, newPwd); return { ok: true, msg: '' }; }
    catch (e) { return { ok: false, msg: friendly(e) }; }
  },

  get_site_info: async () => panel.siteInfo(),

  /* ---------------- 面板：业务 ---------------- */
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
      feats: p.feats.length ? p.feats : [`${Math.round(p.transfer_enable / (1024 ** 3))} GB 流量`],
      hot: p.hot,
    }));
  },

  create_order: async ({ plan_id: planId, coupon }) => {
    needAuth();
    const r = await panel.createOrder(planId, { couponCode: coupon });
    return { ok: true, order_no: r.order_no };
  },

  get_orders: async () => {
    needAuth();
    return panel.orders();
  },

  pay_order: async ({ order_no: orderNo }) => {
    needAuth();
    const methods = await panel.paymentMethods();
    const method = methods.length ? methods[0].id : '';
    const url = await panel.checkoutUrl(orderNo, method);
    await shell.openExternal(url);
    return { ok: true, url };
  },

  get_payment_methods: async () => { needAuth(); return panel.paymentMethods(); },

  get_tickets: async () => { needAuth(); return panel.tickets(); },
  create_ticket: async ({ subject, content }) => {
    needAuth();
    const r = await panel.createTicket(subject, content);
    return { ok: true, no: r.no };
  },

  get_invite: async () => {
    needAuth();
    const i = await panel.invite();
    return {
      code: i.code,
      link: i.link || (i.code ? `${panel.session.panelUrl}/register?code=${i.code}` : ''),
      invited: i.invited,
      earned: i.balance ? `￥${i.balance}` : '0',
    };
  },

  get_gift_history: async () => { needAuth(); return panel.giftHistory(); },
  redeem_gift: async ({ code }) => { needAuth(); return panel.redeemGift(code); },
  get_notices: async () => { needAuth(); return panel.notices(); },
  get_traffic_log: async () => { needAuth(); return panel.trafficLog(); },

  /* ---------------- 设置 ---------------- */
  get_settings: async () => {
    const s = store.all();
    return {
      theme: s.theme,
      lang: s.lang,
      tun: s.tun,
      expire_notify: s.expire_notify,
      traffic_notify: s.traffic_notify,
      autostart: s.autostart,
      version: s.version,
      // 下面几项前端目前不展示，但设置页要能读到真值
      panel_url: s.panel_url,
      last_email: s.last_email,
      sys_proxy: s.sys_proxy,
      tun_mode: s.tun_mode,
      allow_lan: s.allow_lan,
      ipv6: s.ipv6,
      auto_update: s.auto_update,
      email: s.last_email ? fmt.maskEmail(s.last_email) : '',
      authed: panel.isAuthed(),
    };
  },

  set_setting: async ({ key, value }) => {
    const s = store.all();
    switch (key) {
      case 'autostart':
        store.set('autostart', !!value);
        autostart.apply(!!value);
        break;
      case 'theme':
      case 'lang':
      case 'tun':
      case 'expire_notify':
      case 'traffic_notify':
      case 'sys_proxy':
      case 'tun_mode':
      case 'allow_lan':
      case 'ipv6':
      case 'auto_update':
      case 'panel_url':
      case 'last_email':
        store.set(key, value);
        break;
      default:
        throw new Error('未知设置项');
    }
    if (key === 'tun_mode' && value && s.connected) {
      throw new Error('请先断开连接再切换 TUN 模式');
    }
    if (key === 'sys_proxy' && value === false && core.status().connected) {
      require('./net/sysproxy').disable(null);
    }
    return { ok: true };
  },

  check_update: async () => {
    await remote.load(true).catch(() => {});
    const u = remote.updateInfo();
    return { has_update: u.has_update, version: u.version, size: u.size, notes: u.notes };
  },

  open_download: async () => {
    const u = remote.updateInfo();
    if (u.url) await shell.openExternal(u.url);
    else ui.toast('未配置更新地址');
    return { ok: true };
  },

  export_logs: async () => {
    const dir = dialog.showOpenDialog({ properties: ['openDirectory', 'createDirectory'] });
    const res = await dir;
    if (res.canceled || !res.filePaths.length) return { ok: false };
    const target = path.join(res.filePaths[0], `polaris-logs-${Date.now()}.txt`);
    fs.writeFileSync(target, log.tail(5000), 'utf8');
    return { ok: true, path: target };
  },

  get_app_info: async () => ({
    version: store.get('version'),
    portable: require('./paths').isPortable(),
    data_dir: require('./paths').data(),
    core_dir: require('./paths').core(),
    root_dir: require('./paths').root(),
    electron: process.versions.electron,
    chrome: process.versions.chrome,
    node: process.versions.node,
    is_admin: await isAdmin(),
  }),

  is_admin: async () => isAdmin(),
};

function friendly(e) {
  const m = String(e && e.message ? e.message : e);
  if (e && e.kind === 'net') return `网络错误：${m}`;
  if (e && e.kind === 'auth') return m;
  if (e && e.kind === 'input') return m;
  return m;
}

function isAdmin() {
  return new Promise((resolve) => {
    require('child_process').execFile('net', ['session'], { windowsHide: true, timeout: 8000 }, (err, out) => {
      resolve(!err && /BUILTIN\\Administrators/.test(String(out)));
    });
  });
}

function register({ mock = false } = {}) {
  ipcMain.handle(CH, async (_evt, cmd, args) => {
    const fn = commands[cmd];
    if (!fn) throw new Error(`未知命令：${cmd}`);
    const t0 = Date.now();
    try {
      const out = await fn(args || {});
      const dt = Date.now() - t0;
      if (dt > 1500) log.warn(`slow command ${cmd} ${dt}ms`);
      return out;
    } catch (e) {
      log.error(`command ${cmd} failed:`, e && e.message);
      throw new Error(friendly(e));
    }
  });

  // 窗口控制
  ipcMain.handle('polaris:win', (_e, action) => {
    const win = BrowserWindow.getFocusedWindow() || BrowserWindow.getAllWindows()[0];
    if (!win) return false;
    if (action === 'minimize') win.minimize();
    else if (action === 'toggleMaximize') { win.isMaximized() ? win.unmaximize() : win.maximize(); }
    else if (action === 'close') win.hide();
    return win.isMaximized();
  });

  ipcMain.handle('polaris:clipboard', (_e, text) => {
    clipboard.writeText(String(text || ''));
    return true;
  });

  // 状态推送：内核有流量变化时推给首页
  core.onStatus((st) => {
    if (st.connected) {
      const now = Date.now();
      if (!lastTrafficStamp) lastTrafficStamp = now;
      peakDown = Math.max(peakDown, st.down_speed);
      peakUp = Math.max(peakUp, st.up_speed);
      if (now - lastTrafficStamp >= 1000) lastTrafficStamp = now;
    }
    ui.emit('status', st);
  });

  if (mock) log.warn('mock flag set: 前端将使用内置演示数据');
}

module.exports = { register, commands, isAdmin };
