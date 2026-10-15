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
const release = require('./core/release');
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
 * 版本号比较（只认点分数字，够用了）。a > b 返回 1，相等 0，a < b 返回 -1。
 * 用来把「上次查到的线上最新版」与当前版本比出"该不该亮红点"——
 * 存的是版本号而不是布尔值，用户装完新版本红点会自己消失。
 * 实现放在 util/format.js，core/release.js 与这里共用同一份。
 */
const cmpVersion = fmt.cmpVersion;

/**
 * 更新信息择优（远程配置 vs GitHub Releases）。
 * 优先"既说有新版、又给得出下载地址"的那一个；远程配置排在前面，所以它可以覆盖
 * GitHub 的地址（指向镜像 / 自建 CDN）。两边都没新版就返回一个"没更新"的空壳。
 * 返回值带上 source —— 注意这里**一定**是新对象，所以调用方不能再用 `u === cfg` 判断来源
 * （2026-10-11 实测踩到：source 永远算成 github）。
 */
function pickUpdateInfo(cfg, gh) {
  const cands = [{ info: cfg, source: 'remote' }, { info: gh, source: 'github' }].filter((c) => c.info);
  const withUrl = cands.find((c) => c.info.has_update && c.info.url);
  const newer = cands.find((c) => c.info.has_update);
  const chosen = withUrl || newer || cands[0]
    || { info: { has_update: false, version: '', size: '', notes: '', url: '' }, source: 'remote' };
  const anyUrl = cands.find((c) => c.info.url);
  return {
    info: Object.assign({}, chosen.info, {
      has_update: !!chosen.info.has_update,
      url: chosen.info.url || (anyUrl ? anyUrl.info.url : ''),
    }),
    source: chosen.source,
  };
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

/* ------------------------------------------------------------------ */
/* 站点流量明细的长缓存                                                */
/* ------------------------------------------------------------------ */

/**
 * 面板的站点流量明细（`/user/stat/getTrafficLog`）是一次**聚合查询**，实测单个
 * 请求 7~30 秒（面板侧按天 group by，数据量越大越慢）。旧代码只在 client 层做了
 * 15 秒缓存，于是流量页点「今日 / 本周 / 本月」每一下都要重新等这十几秒 ——
 * 用户第 12 轮第 5 条「完全不能随着点击切换」。
 *
 * 这里的口径：
 *  · 5 分钟内的缓存直接用，切区间只做本地切片（pickTrafficDays），点一下就变；
 *  · 过期了但手里还有旧数据 → **立刻给旧数据**，同时在后台刷一次
 *    （stale-while-revalidate）：宁可让用户看到 5 分钟前的数字，也不要让他等 20 秒；
 *  · 从没成功过 → 只能等第一次（登录后主进程会预热，通常用户还没点进流量页就有了）；
 *  · 刷到过的明细落一份 `data/site-log.json`：下次启动**立刻**就有东西可显示，
 *    不用再等那十几秒（用户第 12 轮第 5 条"需要等很多才能刷新"）；
 *  · 刷新失败保留旧数据，不清空（面板抖一下不该让整页变空）。
 */
const SITE_TTL_MS = 5 * 60 * 1000;
let siteCache = { at: 0, days: [] };
let siteInflight = null;
// 最近一次刷新失败的原因（刷新成功即清空）。渲染层拿它显示
// 「面板流量明细拉取失败：…」，而不是像以前那样悄悄换成「本机统计」——
// 本机转发量和面板账号用量根本不是一回事，混着显示就是驴唇不对马嘴。
let siteLastError = null;

function loadSiteLog() {
  try {
    const raw = JSON.parse(fs.readFileSync(paths.file('site-log.json'), 'utf8'));
    if (raw && Array.isArray(raw.days) && raw.days.length) {
      siteCache = { at: Number(raw.at) || 0, days: raw.days };
    }
  } catch (_) { /* 没有 / 坏了都当没有 */ }
}

function saveSiteLog() {
  try {
    const f = paths.file('site-log.json');
    fs.mkdirSync(path.dirname(f), { recursive: true });
    fs.writeFileSync(f, JSON.stringify({ at: siteCache.at, days: siteCache.days }), 'utf8');
  } catch (e) { log.warn('site log save failed:', e && e.message); }
}

function refreshSiteLog() {
  if (siteInflight) return siteInflight;
  siteInflight = Promise.resolve()
    .then(() => panel.trafficLog())
    .then((days) => {
      siteCache = { at: Date.now(), days: Array.isArray(days) ? days : [] };
      siteLastError = null;          // 刷新成功，上一次的错误不再作数
      if (siteCache.days.length) saveSiteLog();
      return siteCache.days;
    })
    .catch((e) => {
      siteLastError = (e && e.message) || '面板请求失败';
      log.warn('site traffic log failed:', siteLastError);
      return siteCache.days;   // 失败保留旧值
    })
    .then((days) => { siteInflight = null; return days; });
  return siteInflight;
}

/** 预热（登录 / 连接后调用）：不关心结果，只是把缓存填上 */
function warmSiteLog() { if (panel.isAuthed()) refreshSiteLog().catch(() => {}); }

function siteDays() {
  // 没登录时磁盘上那份不算数：那是上一个会话（可能还是别的账号）的数字
  if (!panel.isAuthed()) return Promise.resolve([]);
  const fresh = siteCache.at && Date.now() - siteCache.at < SITE_TTL_MS;
  if (fresh) return Promise.resolve(siteCache.days);
  const p = refreshSiteLog();              // 过期就顺手在后台刷
  return siteCache.at ? Promise.resolve(siteCache.days) : p;   // 有旧值立刻给旧的
}

function clearSiteLog() {
  siteCache = { at: 0, days: [] };
  siteInflight = null;
  siteLastError = null;
  // 换账号了：磁盘上那份是上一个账号的用量，必须一起删掉，否则下次启动会先显示别人的数字
  try { fs.rmSync(paths.file('site-log.json'), { force: true }); } catch (_) {}
}

loadSiteLog();

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
      // 「点连接 = 开代理」：这里**不再**顺手拉订阅（用户第 11 轮第 2/4 条）。
      // 旧实现在这里补拉订阅，于是用户点了连接却看到"订阅下载超时"、代理没起来 ——
      // 拉数据是登录那一步的事（见 ensure_subscription），连接只负责把内核拉起来。
      if (!hasSubscribeFile()) {
        throw new Error('还没有节点数据，请先在「节点」页点「刷新订阅」');
      }
      const st = await core.connect();
      return ok({ connected: st.connected, node: st.node });
    }),

  /**
   * 登录后 / 启动时把「节点数据」准备好（用户第 11 轮第 1、4 条）。
   *
   * 旧行为：订阅只在三个时机拉 —— 点连接、设置页手动刷新、12 小时过期。
   * 于是刚登录时节点页是空的（本地连 config.yaml 都还没生成，previewNodes 只能返回空数组），
   * 用户以为"登录没拉到节点"，而点连接又变成"在拉订阅"。
   *
   * 现在：本地没订阅、订阅过期、或配置还没生成时补一次；三者都不需要就秒回。
   */
  ensure_subscription: async ({ force } = {}) => {
    needAuth();
    // 启动（已登录）走的就是这里：顺手把流量页要用的站点明细在后台预热，
    // 用户点进流量页时不用干等面板那次十几秒的聚合查询。
    warmSiteLog();
    const have = hasSubscribeFile();
    const updatedAt = Number(store.get('subscription_updated_at')) || 0;
    const stale = !updatedAt || Date.now() - updatedAt > 12 * 3600 * 1000;
    const needFetch = !!force || !have || stale;
    let fetched = false;
    // 面板拉取留在内核锁外（跟 refresh_subscription 同理）：用户在这个十几秒里
    // 点连接应该立刻能连上，而不是等订阅下载完。
    if (needFetch) {
      await pullSubscription();
      fetched = true;
      core.setDirectDomains(remote.directDomains());
    }
    return serialKernel(async () => {
      try {
        // 订阅在但配置没生成（首次登录、配置被清掉、上次组装失败）→ 就地补一份，
        // 否则节点页在未连接时永远拿不到东西（previewNodes 读的就是 config.yaml）。
        if (fetched || !fs.existsSync(paths.file('config.yaml'))) {
          // 连接中必须沿用旧端口/密钥，否则 runtime.json 会指向内核没在听的端口
          core.prepareConfig({ reuse: core.status().connected });
        }
      } catch (e) {
        // 订阅文件在但读不出来（典型：放进 OneDrive/同步盘的占位符没水合）——
        // 说清楚是哪一步坏了，别让界面只显示"空节点"。
        throw new Error(`本地订阅读取失败：${e.message}`);
      }
      if (fetched && core.status().connected) {
        // 拉到了新订阅而内核正在跑：重载一次，否则界面上的节点和内核实际用的不是同一份
        await core.disconnect();
        await core.connect();
      }
      const nodes = await core.loadNodes(true);
      return ok({ fetched, count: nodes.length });
    });
  },

  disconnect: async () =>
    serialKernel(async () => {
      await core.disconnect();
      return ok();
    }),

  get_nodes: async () => core.loadNodes(),

  select_node: async ({ name, group }) => {
    if (!name) throw new Error('缺少节点名');
    const r = await core.selectNode(name, group);
    // main 告诉渲染层"这次改的是不是主组"：只有主组的出口才等于"当前节点"，
    // 在分流组里换节点不该把首页那张当前节点条改掉（用户第 12 轮第 1 条）。
    return ok({ node: r.node, group: r.group, main: r.group === core.MAIN_GROUP });
  },

  // 「延迟测试」：节点 + 每个策略组都测一遍（用户要求分组那行也显示延迟）
  speed_test: async () => {
    await core.speedTest();
    return ok();
  },

  test_group_delays: async () => ok({ groups: await core.testGroupDelays() }),

  refresh_subscription: async () => {
    needAuth();
    // 用户主动刷新 = 明确要看最新的，站点明细缓存一并作废并在后台重拉
    clearSiteLog();
    warmSiteLog();
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
        days = await siteDays();
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
      // 面板明细的新鲜度与失败原因：渲染层据此显示「数据截至 HH:MM」或
      // 「面板流量明细拉取失败：…」，不再把本机统计冒充成面板用量。
      site_at: siteCache.at,
      site_stale: !!siteCache.at && Date.now() - siteCache.at >= SITE_TTL_MS,
      site_error: siteLastError,
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
      const days = await siteDays();
      return range ? pickTrafficDays(range, days) : days;
    } catch (e) { log.warn('trafficLog failed:', e.message); return []; }
  },

  /* ================= 账号 ================= */
  login: async ({ email, password, panel: panelInput }) => {
    try {
      const r = await panel.login(email, password, panelInput);
      clearSiteLog();          // 换账号了，别把上一个账号的流量明细端出来
      warmSiteLog();           // 登录后立刻预热站点明细：用户点进流量页时它已经在了
      // 远程配置**不挡登录**（用户第 14 轮第 6 条）：直连域名与更新源在后台补上。
      // 它自带 8 秒超时，而这台机器上 raw.githubusercontent.com 是被墙的（D-12 / D-16）。
      remote.load(true).then(() => core.setDirectDomains(remote.directDomains())).catch(() => {});
      return ok({ email: r.email });
    } catch (e) {
      return fail(friendly(e));
    }
  },

  logout: async () => {
    await core.disconnect().catch(() => {});
    panel.logout();
    clearSiteLog();
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
      // 应用内更新的红点口径（用户第 13 轮第 2 条）：有更新才亮，没更新不亮。
      // 存的是"上次查到的线上最新版"，与当前版本现算，所以装完新版本自己会灭。
      update_available: cmpVersion(s.update_latest, s.version) > 0,
      update_version: s.update_latest || '',
      update_checked_at: Number(s.update_checked_at) || 0,
      // 本地有没有节点数据（订阅原文 + 组装好的 config.yaml）。界面用它区分
      // 「还没登录」和「登录了但订阅没拉下来」——旧代码这两种情况都显示"未登录"，
      // 用户看到的是"账号没登录"，其实账号是好的（用户第 11 轮第 3 条）。
      has_subscription: hasSubscribeFile() && fs.existsSync(paths.file('config.yaml')),
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
    // 认不出是谁时 name 是空的 —— 这条日志就是当时把「空名字 + forced=true」摆到用户眼前的证据，
    // 现在多了 reason，一眼能看出是「没认出来」还是「权限不够」。
    log.info(`close port owner: pid=${id} name=${r.name || '(未识别)'} closed=${r.closed} forced=${r.forced}${r.reason ? ' reason=' + r.reason : ''}`);
    return ok(r);
  },

  /* ================= 更新 / 诊断 ================= */
  check_update: async () => {
    // 两个来源**并发**：远程配置（可选，可以指向镜像）与 GitHub Releases（与安卓端同一套口径）。
    // 以前只有远程配置，而它的 update_version 一直是空的 —— 红点永远不会亮（第 13 轮发现）。
    // 并发是为了不让"远程配置那台机器连不上"拖慢"检查更新"（它自己带 8 秒超时）。
    const cfgP = remote.load(true).catch(() => {}).then(() => remote.updateInfo());
    const ghP = release.checkLatest(String(store.get('version') || '')).catch((e) => {
      // GitHub 连不上不该把"检查更新"整个弄失败：留着远程配置的结果，最差就是"没更新"
      log.warn('release check failed: ' + (e && e.message));
      return null;
    });
    const [cfg, gh] = await Promise.all([cfgP, ghP]);
    const picked = pickUpdateInfo(cfg, gh);
    const u = picked.info;
    const st = updater.info();
    // 把"线上最新版"落盘：设置页那个红点要在**重启之后**还在（用户第 13 轮第 2 条），
    // 不能只活在这一次会话的内存里。没更新时写空串，红点随之消失。
    store.patch({
      update_latest: u.has_update ? String(u.version || '') : '',
      // 地址也要落盘：红点跨重启还在，重启后点「前往下载」时 check_update 的返回值已经没了
      update_url: u.has_update ? String(u.url || '') : '',
      update_checked_at: Date.now(),
    });
    return {
      has_update: u.has_update, version: u.version, size: u.size, notes: u.notes, url: u.url,
      source: picked.source,
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
    const target = String(url || u.url || store.get('update_url') || store.get('download_url') || '').trim();
    // 只放行 http/https：这里和 open_external 一个口径。地址可能是面板回的、也可能是
    // 落盘的 update_url，file:// 或自定义协议会被下载层当成别的协议用。
    // 空地址交给 updater.download 报「未配置更新地址」，别把两种错混成一句。
    if (target && !updater.isAllowedDownloadUrl(target)) {
      throw new Error('更新地址不是 http(s) 链接，已拒绝下载：' + target.slice(0, 120));
    }
    return updater.download(target, version || u.version || store.get('update_latest') || '');
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
    const url = u.url || store.get('update_url') || store.get('download_url') || '';
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
      // 凭据过期（面板 401/403）：把内存里的会话置空并把界面送回登录页。
      // 不做的话用户会停在"已登录"的界面上，之后每个请求各报一次错。
      // login 命令本身除外 —— 密码错也可能返回 401，那时说"登录已失效"没人看得懂。
      //
      // 注意这里用的是 invalidate() 而不是 logout()：logout() 会
      // credentials.clear() 把 credentials.dat 删掉，于是面板抽风一次
      // （CDN 回 403 面板 JSON、面板临时拒绝）用户就**永久**变成未登录 ——
      // 真机事故：用户报"退出软件再打开账号显示未登录"，就是这个链路。
      // 现在磁盘上的凭据留着，下次启动 main.js 会 verify() 复核：
      // 面板还是明确拒绝，才真清（用户第 13 轮第 1 条）。
      if (e && e.kind === 'auth' && cmd !== 'login') {
        try { panel.invalidate(); } catch (_) {}
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
