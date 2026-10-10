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
const updater = require('./core/updater');
const traffic = require('./core/traffic');
const panel = require('./panel/client');
const autostart = require('./net/autostart');
const elevate = require('./net/elevate');
const portowner = require('./net/portowner');
const sysproxy = require('./net/sysproxy');
const fmt = require('./util/format');
const ui = require('./ui');

const CH = 'polaris:invoke';

function needAuth() {
  if (!panel.isAuthed()) throw new Error('尚未登录面板');
}

/**
 * 套餐/用量的**唯一**口径。首页、「我的」、流量页三处都从这里拿数据。
 * 旧代码流量页自己又拼了一套（u.u + u.d、transfer_enable），于是同一屏里
 * 「已用流量 0 B / 500 GB + 套餐未订阅」和「我的」页的真实套餐名互相矛盾 ——
 * 只要 `/user/getSubscribe` 抖一下，流量页就退化成"看起来没订阅"。
 */
async function planSnapshot() {
  const u = await panel.userInfo();
  const usedBytes = Number(u.used_bytes || 0);
  const totalBytes = Number(u.total_bytes || 0);
  return {
    name: u.plan_name,
    used: u.used,
    total: u.total,
    expire: u.expire,
    used_bytes: usedBytes,
    total_bytes: totalBytes,
    used_text: fmt.bytes(usedBytes),
    total_text: fmt.bytes(totalBytes),
    up: u.u,
    down: u.d,
  };
}

/**
 * 拉订阅。同一时刻只允许一个在飞：刚登录那几秒里 `refreshAll` 的拉取还没回来，
 * 用户这时点「连接」会走进 connect 的补拉分支 —— 没有这个去重就会同时拉两遍。
 */
let subscribing = null;
function pullSubscription() {
  if (!subscribing) {
    subscribing = Promise.resolve(panel.refreshSubscription()).finally(() => { subscribing = null; });
  }
  return subscribing;
}
function hasSubscribeFile() {
  return fs.existsSync(path.join(paths.profiles(), 'subscribe.yaml'));
}

function ok(extra) { return Object.assign({ ok: true }, extra || {}); }
function fail(msg) { return { ok: false, msg: msg || '操作失败' }; }

/**
 * 站点流量明细的区间口径：今日 / 本周（周一起）/ 本月。
 * 明细里 `ts` 是**当地零点**的秒级时间戳（面板就是这么给的），所以直接和
 * `new Date(y,m,d).getTime()` 比即可；千万不要用 UTC 或 toISOString 去切，
 * 会整段错一天。
 */
function pickTrafficDays(range, days) {
  const list = Array.isArray(days) ? days : [];
  const now = new Date();
  const y = now.getFullYear();
  const m = now.getMonth();
  const d = now.getDate();
  let from = null;
  if (range === 'today') from = new Date(y, m, d).getTime();
  else if (range === 'week') from = new Date(y, m, d - ((now.getDay() + 6) % 7)).getTime();  // 周一=0
  else if (range === 'month') from = new Date(y, m, 1).getTime();
  if (from === null) return list;
  return list.filter((x) => Number(x.ts) * 1000 >= from);
}

/** 本机代理端口：0 或空 = 随机端口；返回 null 表示"用随机" */
function wantedPort() {
  const n = Number(store.get('mixed_port'));
  return Number.isInteger(n) && n > 0 && n < 65536 ? n : null;
}

/**
 * 内核操作串行锁。
 *
 * 起因（真事故）：开机后自动拉订阅（refresh_subscription）与用户点的那次
 * connect 会叠在一起跑。订阅刷新内部就是"断开 → 重连"，于是它把用户刚拉起来
 * 的内核拆掉，用户的 connect 再往已经死掉的控制面发请求 ——
 * 日志里就是 `ERROR command connect failed: read ECONNRESET` 紧跟
 * `mihomo exited`，最后内核是死的、延迟全空。
 *
 * 这里保证同一时刻只有一段"会改内核状态"的代码在跑：连接、断开、以及订阅刷新里
 * 的改配置 + 重载。面板网络拉取刻意留在锁外，免得用户点连接要干等十几秒。
 */
let kernelChain = Promise.resolve();
function serialKernel(fn) {
  const next = kernelChain.then(fn, fn);
  // 链子本身不能因为某一次失败就断掉（失败照常抛给调用方）
  kernelChain = next.then(() => {}, () => {});
  return next;
}

const commands = {
  /* ================= 内核 ================= */
  get_status: async () => core.status(),

  connect: async () =>
    serialKernel(async () => {
      needAuth();
      // 刚登录就点连接时，订阅往往还在拉的路上（登录后 refreshAll 是异步的）。
      // 旧实现在这里直接抛「尚未拉取订阅，请先登录面板」—— 用户明明刚登录成功，
      // 看到这句只会以为登录坏了。这里自己先把订阅补齐再连。
      if (!hasSubscribeFile()) {
        log.info('connect: 本地还没有订阅，先自动拉一次');
        await pullSubscription();
        core.setDirectDomains(remote.directDomains());
        core.prepareConfig();
      }
      const st = await core.connect();
      return ok({ connected: st.connected, node: st.node });
    }),

  disconnect: async () =>
    serialKernel(async () => {
      await core.disconnect();
      return ok();
    }),

  get_nodes: async () => core.loadNodes(),

  select_node: async ({ name, group }) => {
    if (!name) throw new Error('缺少节点名');
    return ok({ node: await core.selectNode(name, group) });
  },

  // 「延迟测试」：节点 + 每个策略组都测一遍（用户要求分组那行也显示延迟）
  speed_test: async () => {
    await core.speedTest();
    return ok();
  },

  test_group_delays: async () => ok({ groups: await core.testGroupDelays() }),

  refresh_subscription: async () => {
    needAuth();
    // 面板拉取不占内核锁：用户在这个十几秒里点连接应该立刻能连上
    await pullSubscription();
    return serialKernel(async () => {
      // 订阅域名可能是第一次见到，立刻并入直连域名再生成配置
      core.setDirectDomains(remote.directDomains());
      const { count } = core.prepareConfig();
      if (core.status().connected) {
        // 已在连接：重载配置而不是让用户手动断开重连
        await core.disconnect();
        await core.connect();
      }
      return ok({ count });
    });
  },

  set_proxy_mode: async ({ mode }) => {
    if (!mode) throw new Error('缺少模式');
    return ok({ mode: fmt.modeLabel(await core.setMode(fmt.modeKey(mode))) });
  },

  /* ================= 分流 ================= */
  get_routing_groups: async () => core.routingGroups(),
  set_routing_group: async ({ name, node }) => core.setRoutingGroup(name, node),
  reset_routing_groups: async () => core.resetRoutingGroups(),
  get_rulesets: async () => core.rulesetState(),
  set_ruleset: async ({ name, on }) => ok(await core.setRuleset(name, !!on)),
  reorder_ruleset: async ({ name, to }) => ok(await core.reorderRuleset(name, Number(to) || 0)),
  save_custom_ruleset: async (arg) => ok(await core.saveCustomRuleset(arg || {})),
  delete_custom_ruleset: async ({ name }) => ok(await core.deleteCustomRuleset(name)),
  reset_rulesets: async () => ok(await core.resetRulesets()),
  set_local_routing: async ({ on }) => ok(await core.setLocalRouting(!!on)),

  /* ================= 流量 ================= */
  get_traffic: async ({ range }) => {
    const session = traffic.totals();

    // 站点流量：面板明细按天聚合，今日/本周/本月都用**站点数据**算。
    // 旧版拿本机内核的采样曲线求和当成"今日流量"，那是本机转发量，不是账号用量
    // （用户实测报的 bug：流量页数字和面板对不上）。
    let site = null;
    let days = [];
    let picked = [];
    let account = null;
    // 该面板后端到底有没有站点流量明细接口（小 V2B 没有，只有 Xboard 有）
    const backend = panel.session.backend || store.get('panel_backend') || 'xboard';
    const siteLog = backend === 'xboard';
    if (panel.isAuthed()) {
      try {
        days = await panel.trafficLog();
        picked = pickTrafficDays(range || 'today', days);
        const up = picked.reduce((a, x) => a + x.upload, 0);
        const down = picked.reduce((a, x) => a + x.download, 0);
        site = {
          days: picked.length,
          up, down, total: up + down,
          up_text: fmt.bytes(up), down_text: fmt.bytes(down), total_text: fmt.bytes(up + down),
          latest: days.length ? days[0].date : '',
          from: picked.length ? picked[picked.length - 1].date : '',
          to: picked.length ? picked[0].date : '',
        };
      } catch (e) {
        log.warn('trafficLog for traffic failed:', e && e.message);
      }
      try {
        const p = await planSnapshot();
        account = {
          up: p.up, down: p.down, total: (p.up || 0) + (p.down || 0),
          used_bytes: p.used_bytes, total_bytes: p.total_bytes,
          used_text: p.used_text,
          quota_text: p.total_text,
          plan_name: p.name,
          expire: p.expire,
        };
      } catch (e) {
        log.warn('userInfo for traffic failed:', e && e.message);
      }
    }
    return {
      range: range || 'today',
      site,
      site_log: siteLog,
      backend,
      account,
      session: { up: session.up, down: session.down, up_text: fmt.bytes(session.up), down_text: fmt.bytes(session.down) },
      total_down: fmt.bytes(account ? account.down : session.down),
      total_up: fmt.bytes(account ? account.up : session.up),
      days,
      // 区间过滤后的明细：渲染层直接用它，省掉一次重复的面板查询
      // （面板明细是整页最慢的一项，之前 get_traffic 与 get_traffic_log 各拉一次）。
      picked,
    };
  },

  get_traffic_series: async ({ range }) => traffic.series(range || 'today'),

  get_traffic_log: async ({ range } = {}) => {
    if (!panel.isAuthed()) return [];
    try {
      const days = await panel.trafficLog();
      return range ? pickTrafficDays(range, days) : days;
    } catch (e) { log.warn('trafficLog failed:', e.message); return []; }
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

  get_site_info: async () => {
    const info = await panel.siteInfo();
    // 游客配置里没有 Telegram 字段是常态，登录后再问一次真实字段
    if (!info.telegramUrl) info.telegramUrl = await panel.telegramLink();
    return info;
  },

  /* ================= 面板业务 ================= */
  get_plan: async () => {
    needAuth();
    // used/total 是 GB（面板口径，两位小数）；_text 走字节换算，小用量不会显示成 0 GB
    return await planSnapshot();
  },

  get_plans: async () => {
    needAuth();
    const list = await panel.plans();
    return list.map((p) => ({
      id: p.id,
      name: p.name,
      price: String(p.price),
      unit: p.unit || '月',
      period: p.period || '',
      periods: p.periods || [],
      // panel.plans() 已经把 transfer_enable 换算成 GB（面板原始值是 GB，不用再除）
      transfer_enable: p.transfer_enable,
      feats: p.feats.length ? p.feats : [`${p.transfer_enable || 0} GB 流量`],
      hot: p.hot,
      sell: p.sell,
      renew: p.renew,
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

  create_ticket: async ({ subject, content, level }) => {
    needAuth();
    if (!subject) throw new Error('请填写工单标题');
    if (!content) throw new Error('请填写问题描述');
    const r = await panel.createTicket(subject, content, level);
    return ok({ no: r.no });
  },

  get_invite: async () => {
    needAuth();
    const i = await panel.invite();
    return {
      code: i.code,
      link: i.link || (i.code ? `${panel.session.panelUrl}/#/register?code=${i.code}` : ''),
      invited: i.invited,
      registered: i.registered,
      commission: i.commission,
      pending: i.pending,
      balance: i.balance,
      rate: i.rate,
      codes: i.codes,
      // 面板没给邀请码时（真机 stat 全 0）不显示"￥0"这种假数字
      earned: i.commission ? `￥${i.commission.toFixed(2)}` : '0',
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
      mixed_port: Number(s.mixed_port) || 0,          // 0 = 随机（界面上显示"自动"）
      running_port: core.S.mixedPort || 0,            // 当前内核实际在听的端口
      email: s.last_email || '',          // 用户第 5 条：自己的账号邮箱不打码
      panel_backend: panel.session.backend || s.panel_backend || '',   // 自动识别出来的后端
      authed: panel.isAuthed(),
    };
  },

  set_setting: async ({ key, value }) => {
    const BOOL_KEYS = ['autostart', 'expire_notify', 'traffic_notify', 'sys_proxy', 'auto_update', 'tun_mode', 'allow_lan', 'ipv6'];
    const STR_KEYS = ['theme', 'lang', 'tun', 'panel_url', 'last_email'];
    const NUM_KEYS = ['mixed_port'];
    if (!BOOL_KEYS.includes(key) && !STR_KEYS.includes(key) && !NUM_KEYS.includes(key)) throw new Error('未知设置项：' + key);

    if (key === 'mixed_port') {
      // 用户第 9 条：让客户端能固定监听 127.0.0.1:7890。0/空 = 随机。
      const raw = value === '' || value === null || value === undefined ? 0 : Number(value);
      if (!Number.isInteger(raw) || raw < 0 || raw > 65535) throw new Error('端口必须是 0-65535 之间的整数（0 = 自动）');
      if (raw > 0 && raw < 1024) throw new Error('1024 以下的端口需要管理员权限，请换一个');
      if (raw > 0 && raw === Number(store.get('controller_port'))) throw new Error('该端口已被控制面占用，请换一个');
      const was = Number(store.get('mixed_port')) || 0;
      store.set('mixed_port', raw);
      if (core.status().connected) {
        // 端口变了就得重建内核；这里只提示，不偷偷重连（会断掉用户正在跑的流量）
        if (raw !== was) ui.toast('端口已保存，断开重连后生效');
      }
      return ok({ mixed_port: raw });
    }

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
    if (key === 'sys_proxy' && value && core.status().connected && !core.S.proxySnapshot) {
      // 连接态下把开关打开必须**立刻挂上**：旧行为只写 store，注册表原样不动，
      // 用户看到开关已打开、系统流量却还是走原来的代理，要等下次重连才生效
      // （运行时测试的 5.1 就是这么抓到的）。
      // 只在没有快照时才拍快照 —— 已有快照说明注册表本来就是我们改的，
      // 再拍一次会把我们自己的值当成"用户原值"（见踩坑 A-15）。
      core.S.proxySnapshot = sysproxy.enable('127.0.0.1', core.mixedPort(), core.S.directDomains);
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

  /* ---- 端口占用（用户第 10 条）：先看清楚是谁，再请它自己走 ---- */

  port_owner: async ({ port } = {}) => {
    const p = Number(port) || Number(store.get('mixed_port')) || 0;
    return ok(portowner.owner(p));
  },

  close_port_owner: async ({ pid } = {}) => {
    const id = Number(pid) || 0;
    if (!id) return fail('没给出要关闭的进程');
    const r = await portowner.close(id);
    log.info(`close port owner: pid=${id} name=${r.name} closed=${r.closed} forced=${r.forced}`);
    return ok(r);
  },

  /* ================= 更新 / 诊断 ================= */
  check_update: async () => {
    await remote.load(true).catch(() => {});
    const u = remote.updateInfo();
    const st = updater.info();
    return {
      has_update: u.has_update, version: u.version, size: u.size, notes: u.notes, url: u.url,
      // 便携版能不能自己装：打包 + 安装目录可写才允许，否则退回「前往下载」
      can_apply: paths.isPackaged && paths.isPortable(),
      apply_blocked: paths.isPackaged && paths.isPortable() ? '' : updater.applyBlockedReason(),
      install_dir: st.install_dir,
      staged: st.phase === 'staged' && st.version === u.version,
    };
  },

  get_update_state: async () => updater.info(),

  download_update: async ({ url, version } = {}) => {
    const u = remote.updateInfo();
    const target = String(url || u.url || store.get('download_url') || '');
    return updater.download(target, version || u.version);
  },

  apply_update: async () => {
    const r = updater.apply();
    // 脚本已经在等我们退出了：先回包让界面把「正在重启」显示出来，再退
    setTimeout(() => updater.quit(), 600);
    return ok(r);
  },

  discard_update: async () => updater.reset(),

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

  // 「我的」页的 Telegram 入口：链接现取现校验，拿不到就明确告诉界面隐藏/提示
  open_telegram: async () => {
    const info = await panel.siteInfo();
    const url = info.telegramUrl || (await panel.telegramLink());
    if (!url) return fail('面板没有配置 Telegram 群组');
    await shell.openExternal(url);
    return ok({ url });
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

function register() {
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
      // 凭据过期（面板 401/403）：清掉登录态并把界面送回登录页。
      // 不做的话用户会停在"已登录"的界面上，之后每个请求各报一次错。
      // login 命令本身除外 —— 密码错也可能返回 401，那时说"登录已失效"没人看得懂。
      if (e && e.kind === 'auth' && cmd !== 'login') {
        try { panel.logout(); } catch (_) {}
        ui.toast('登录已失效，请重新登录');
        ui.navigate('login');
      }
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
    ui.emit('status', st);
  });

  // 更新进度推送（下载/解压/就绪），渲染层不用轮询
  updater.onChange((st) => ui.emit('update', st));
  updater.restoreStaged();
}

module.exports = { register, commands, isAdmin: elevate.isAdmin };
