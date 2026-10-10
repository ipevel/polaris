'use strict';
/**
 * 面板客户端（Xboard / XiaoV2b 均为 V2Board 家族，路径同构）。
 *
 * 基址：<panel>/api/v1
 * 鉴权：Authorization: <auth_data>（裸 token，非 Bearer）
 * 形态差异只有两处，用 capability 表达，不再拆两套实现：
 *   - 礼品卡：Xboard = user/gift-card/redeem，XiaoV2b = user/redeemgiftcard
 *   - 流量明细：仅 Xboard 提供 user/stat/getTrafficLog
 *
 * 字段名在各面板分支上并不统一，所有映射都做了多候选兜底。
 */

const https = require('https');
const http = require('http');
const { URL } = require('url');
const fs = require('fs');
const path = require('path');

const paths = require('../paths');
const log = require('../logger');
const store = require('../store');
const credentials = require('./credentials');

const UA = 'ClashMetaForAndroid/2.11.32';   // 与 Android 端一致：部分面板按 UA 放行订阅
const API_PREFIX = '/api/v1';

const session = {
  panelUrl: '',
  token: '',
  email: '',
  backend: '',            // 'xboard' | 'xiaov2b'
};

class PanelError extends Error {
  constructor(message, kind) { super(message); this.kind = kind || 'api'; }
}

function normalizePanelUrl(input) {
  let s = String(input || '').trim();
  if (!s) return '';
  if (!/^https?:\/\//i.test(s)) s = `https://${s}`;
  s = s.replace(/\/+$/, '');
  if (/\/api\/v\d+$/i.test(s)) s = s.replace(/\/api\/v\d+$/i, '');
  if (!/^https?:\/\//i.test(s)) throw new PanelError('面板地址必须以 http(s) 开头', 'input');
  return s;
}

function request(method, apiPath, { body, raw, timeoutMs = 20000, noAuth = false } = {}) {
  if (!session.panelUrl) return Promise.reject(new PanelError('尚未配置面板地址', 'no-panel'));
  const url = new URL(API_PREFIX + apiPath, session.panelUrl + '/');
  const mod = url.protocol === 'https:' ? https : http;

  const payload = body === undefined ? null
    : Buffer.from(typeof body === 'string' ? body : JSON.stringify(body), 'utf8');

  const headers = {
    'User-Agent': UA,
    Accept: 'application/json, text/yaml, text/plain, */*',
  };
  if (payload) {
    headers['Content-Type'] = 'application/json; charset=utf-8';
    headers['Content-Length'] = payload.length;
  }
  if (!noAuth && session.token) headers.Authorization = session.token;

  return new Promise((resolve, reject) => {
    // 面板请求的分段计时：慢的时候必须能一眼看出慢在 DNS / TCP / TLS / 面板本身，
    // 否则只能靠猜（实测曾出现单个 360ms 的接口在应用里耗时 15-30s）。
    const t0 = Date.now();
    const ph = {};
    const req = mod.request(url, { method, headers, timeout: timeoutMs }, (res) => {
      ph.ttfb = Date.now();
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', async () => {
        const total = Date.now() - t0;
        if (total > 2000) {
          log.warn(`panel slow ${method} ${apiPath} ${total}ms `
            + `dns=${ph.dns ? ph.dns - t0 : '-'} conn=${ph.conn ? ph.conn - t0 : '-'} `
            + `tls=${ph.tls ? ph.tls - t0 : '-'} ttfb=${ph.ttfb ? ph.ttfb - t0 : '-'}`
            + `${ph.reused ? ' reused-socket' : ''}`);
        }
        const text = Buffer.concat(chunks).toString('utf8');
        if (res.statusCode === 401 || res.statusCode === 403) {
          reject(new PanelError('登录已失效，请重新登录', 'auth'));
          return;
        }
        if (raw) { resolve({ status: res.statusCode, text, headers: res.headers }); return; }
        if (res.statusCode >= 400) {
          reject(new PanelError(`面板返回 HTTP ${res.statusCode}`, 'http'));
          return;
        }
        let json;
        try { json = JSON.parse(text); } catch (_) {
          reject(new PanelError('面板返回的不是 JSON', 'shape'));
          return;
        }
        if (json && json.status === 'fail') {
          reject(new PanelError(String(json.message || '操作失败'), 'api'));
          return;
        }
        resolve(json && Object.prototype.hasOwnProperty.call(json, 'data') ? json.data : json);
      });
    });
    req.on('timeout', () => req.destroy(new PanelError('面板请求超时', 'timeout')));
    req.on('error', (e) => reject(new PanelError(e.message || '网络错误', 'net')));
    req.on('socket', (s) => {
      if (s.connecting) {
        s.once('lookup', () => { ph.dns = Date.now(); });
        s.once('connect', () => { ph.conn = Date.now(); });
        s.once('secureConnect', () => { ph.tls = Date.now(); });
      } else {
        ph.reused = Date.now();
      }
    });
    if (payload) req.write(payload);
    req.end();
  });
}

const get = (p, o) => request('GET', p, o);
const post = (p, body, o) => request('POST', p, { ...(o || {}), body });

/* ------------------------------------------------------------------ */
/* 字段兜底                                                            */
/* ------------------------------------------------------------------ */

function pick(obj, keys, fallback) {
  if (!obj) return fallback;
  for (const k of keys) {
    if (obj[k] !== undefined && obj[k] !== null && obj[k] !== '') return obj[k];
  }
  return fallback;
}

function toTs(v) {
  if (v == null) return 0;
  if (typeof v === 'number') return v > 1e12 ? v * 1000 : (v > 1e9 ? v * 1000 : v * 1000);
  const n = parseInt(String(v), 10);
  if (Number.isFinite(n)) return n > 1e12 ? n : n * 1000;
  const d = Date.parse(String(v));
  return Number.isFinite(d) ? d : 0;
}

function dateOnly(ms) {
  if (!ms) return '';
  const d = new Date(ms);
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

function dateTime(ms) {
  if (!ms) return '';
  const d = new Date(ms);
  const p = (n) => String(n).padStart(2, '0');
  return `${dateOnly(ms)} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

/* ------------------------------------------------------------------ */
/* 登录态                                                              */
/* ------------------------------------------------------------------ */

function restore() {
  const c = credentials.load();
  if (c && c.panelUrl && c.token) {
    session.panelUrl = c.panelUrl;
    session.token = c.token;
    session.email = c.email || '';
    session.backend = c.backend || '';
    store.set('panel_url', c.panelUrl);
    store.set('last_email', session.email);
    log.info(`panel session restored: ${c.panelUrl} (${c.email})`);
    return true;
  }
  return false;
}

function persist() {
  if (!session.token) { credentials.clear(); return; }
  credentials.save({
    panelUrl: session.panelUrl, token: session.token,
    email: session.email, backend: session.backend,
  });
}

async function login(email, password, panelInput) {
  const panelUrl = normalizePanelUrl(panelInput || store.get('panel_url'));
  if (!panelUrl) throw new PanelError('请填写面板地址', 'input');
  if (!email || !password) throw new PanelError('请输入邮箱和密码', 'input');

  clearUserInfoCache();   // 换账号了，别把上一个账号的套餐留给新账号
  session.panelUrl = panelUrl;
  session.token = '';

  const data = await post('/passport/auth/login', { email, password });
  const token = pick(data, ['auth_data', 'token', 'access_token']);
  if (!token) throw new PanelError('面板未返回登录凭据', 'shape');

  session.token = String(token);
  session.email = String(pick(data, ['email'], email));
  session.backend = detectBackend(panelUrl);

  store.set('panel_url', panelUrl);
  store.set('last_email', session.email);
  persist();
  log.info(`panel login ok: ${panelUrl} as ${session.email}`);
  return { email: session.email };
}

function detectBackend(url) {
  void url;
  // 两个后端路径同构，只有礼品卡一处不同；这里不再靠猜，兑换时两条路径互为兜底。
  return store.get('panel_backend') || 'xboard';
}

const GIFT_PATHS = ['/user/gift-card/redeem', '/user/redeemgiftcard'];

function logout() {
  clearUserInfoCache();
  session.token = '';
  session.email = '';
  session.backend = '';
  credentials.clear();
}

const isAuthed = () => !!session.token;

/**
 * Telegram 链接白名单：必须是 http(s) 且域名是 Telegram 的那几个。
 * 面板下发的链接我们直接丢给系统浏览器打开，所以不能来者不拒 ——
 * 与安卓端 ExternalLinks.kt 的口径一致（t.me / telegram.me / telegram.dog）。
 */
const TELEGRAM_HOSTS = ['t.me', 'telegram.me', 'telegram.dog'];
function safeTelegram(url) {
  const raw = String(url == null ? '' : url).trim();
  if (!raw) return '';
  let u;
  try { u = new URL(raw); } catch (_) { return ''; }
  if (u.protocol !== 'http:' && u.protocol !== 'https:') return '';
  const host = u.hostname.toLowerCase();
  return TELEGRAM_HOSTS.some((h) => host === h || host.endsWith('.' + h)) ? u.href : '';
}

/**
 * Telegram 讨论组链接（对齐安卓端 fetchTelegramDiscussLink）。
 * 游客配置 /guest/comm/config 里通常没有这个字段，真实字段在登录后的
 * /user/comm/config 的 telegram_discuss_link（Xboard / 小 V2B 都是这个名）。
 * 拿不到就返回空串 —— 界面据此隐藏入口，而不是显示一个点不动的死按钮。
 */
async function telegramLink() {
  if (!isAuthed()) return '';
  try {
    const cfg = await get('/user/comm/config');
    const link = safeTelegram(pick(cfg, [
      'telegram_discuss_link', 'telegramDiscussLink',
      'telegram_link', 'telegramLink', 'telegram_url', 'telegram',
    ], ''));
    if (link) return link;
  } catch (e) {
    log.warn('telegramLink failed:', e.message);
  }
  return '';
}

/* ------------------------------------------------------------------ */
/* 站点 / 用户                                                         */
/* ------------------------------------------------------------------ */

async function siteInfo() {
  try {
    const cfg = await get('/guest/comm/config', { noAuth: true });
    const appUrl = String(pick(cfg, ['app_url', 'appUrl', 'home_url', 'url'], session.panelUrl));
    // 面板通常**没有**站点名字段（真机实测 app.pinxiaoche.top 的 /guest/comm/config 里
    // 只有 app_description/app_url/logo）。空着会让调用方把整份 siteInfo 丢掉，所以退回域名。
    let appName = String(pick(cfg, ['app_name', 'appName', 'site_name', 'title', 'name'], ''));
    if (!appName) {
      try { appName = new URL(appUrl).hostname; } catch (_) { appName = session.panelUrl || ''; }
    }
    return {
      appName,
      appDescription: String(pick(cfg, ['app_description', 'appDescription', 'description', 'sub_name'], '')),
      appUrl,
      telegramUrl: safeTelegram(pick(cfg, ['telegram_url', 'telegramUrl', 'telegram_discuss_link'], '')),
      icp: String(pick(cfg, ['icp', 'icp_url'], '')),
    };
  } catch (e) {
    log.warn('siteInfo failed:', e.message);
    let host = '';
    try { host = new URL(session.panelUrl || '').hostname; } catch (_) { host = ''; }
    return { appName: host, appDescription: '', appUrl: session.panelUrl, telegramUrl: '', icp: '' };
  }
}

// userInfo 的短缓存：流量页/首页/我的页会同时要同一份数据，
// 每次都重新拉两个接口既慢又容易把面板打到限流；45 秒内的重复调用直接复用。
// 关键是**只缓存取到了套餐的结果** —— 一次网络抖动不该把"未订阅"缓存下来。
let userInfoCache = null;
const USER_INFO_TTL = 45000;

async function userInfo(options) {
  const force = !!(options && options.force);
  if (!force && userInfoCache && Date.now() - userInfoCache.at < USER_INFO_TTL) return userInfoCache.data;
  // 累计流量、套餐对象、到期时间都在 /user/getSubscribe 里：/user/info **没有 u/d、
  // 也没有 plan 对象**（只有 plan_id / transfer_enable / expired_at）。
  // 旧代码只读 /user/info，于是「我的」页永远是"未订阅 / 已使用 0 GB"——真实面板实测。
  const [u, sub] = await Promise.all([
    get('/user/info').catch(() => ({})),
    get('/user/getSubscribe').catch(() => ({})),
  ]);
  const plan = (sub && typeof sub.plan === 'object' && sub.plan)
    || (u && typeof u.plan === 'object' && u.plan) || null;
  const transferEnable = Number(pick(sub, ['transfer_enable'], 0))
    || Number(pick(u, ['transfer_enable', 'transferEnable'], 0)) || 0;
  const upUsed = Number(pick(sub, ['u'], pick(u, ['u', 'used', 'used_traffic'], 0))) || 0;
  const downUsed = Number(pick(sub, ['d'], pick(u, ['d', 'download'], 0))) || 0;
  const expiredAt = toTs(pick(sub, ['expired_at'], pick(u, ['expired_at', 'expire_at', 'expiredAt'], 0)));
  const planName = (plan && plan.name)
    || String(pick(u, ['plan_name', 'planName'], '')) || String(pick(sub, ['plan_name'], ''));
  // 这次没取到套餐、但上一次取到过 —— 是这次请求失败，不是用户真的退订了。
  // 沿用上一次的套餐名/到期时间，别让界面抖成"未订阅"。
  if (!planName && userInfoCache && userInfoCache.data.plan_name !== '未订阅') {
    return Object.assign({}, userInfoCache.data, {
      up: upUsed, down: downUsed,
      used: userInfoCache.data.used, total: userInfoCache.data.total,
    });
  }
  // 总量优先用字节数换算（getSubscribe.transfer_enable 是字节），退回套餐的 GB 值
  const totalGb = transferEnable > 0
    ? transferEnable / (1024 ** 3)
    : Number(pick(plan || {}, ['transfer_enable'], 0)) || 0;
  const round2 = (n) => Math.round(n * 100) / 100;
  const data = {
    email: String(pick(u, ['email'], pick(sub, ['email'], session.email))),
    balance: Number(pick(u, ['balance'], 0)) || 0,
    plan_name: String(planName || '未订阅'),
    total: round2(totalGb),
    // 面板按双向计费（u + d），只算上传会少一半
    used: round2((upUsed + downUsed) / (1024 ** 3)),
    // 字节原值也带上：面板按 GB 两位小数取整，946 KB 会显示成 0.00 GB，
    // 于是「我的」页会写成"已使用 0 GB"（用户报过），界面改用 fmt.bytes 渲染。
    used_bytes: upUsed + downUsed,
    total_bytes: transferEnable,
    expire: dateOnly(expiredAt),
    expired_at: expiredAt,
    transfer_enable: transferEnable,
    next_reset_at: toTs(pick(sub, ['next_reset_at'], 0)),
    u: upUsed,
    d: downUsed,
    ...(plan ? { plan } : {}),
  };
  if (planName || transferEnable > 0) userInfoCache = { at: Date.now(), data };
  return data;
}

function clearUserInfoCache() { userInfoCache = null; }

async function subscribeInfo() {
  const s = await get('/user/getSubscribe');
  return {
    subscribe_url: String(pick(s, ['subscribe_url', 'subscribeUrl', 'link'], '')),
    token: String(pick(s, ['token'], '')),
    expired_at: toTs(pick(s, ['expired_at', 'expire_at'], 0)),
    expired: !!s.expired,
  };
}

/** 下载订阅原文并落盘；成功后由 core 重新生成 config */
async function refreshSubscription() {
  const info = await subscribeInfo();
  let url = info.subscribe_url;
  if (!url && info.token) url = `${session.panelUrl}${API_PREFIX}/client/subscribe?token=${encodeURIComponent(info.token)}`;
  if (!url) throw new PanelError('面板未返回订阅地址', 'shape');

  const abs = /^https?:\/\//i.test(url) ? url : `${session.panelUrl}${url}`;
  const res = await requestAbsolute(abs);
  if (res.status !== 200) throw new PanelError(`订阅下载失败 HTTP ${res.status}`, 'http');
  if (!/proxies|proxy-providers/i.test(res.text)) {
    throw new PanelError('订阅内容不是 Clash 配置（面板可能拒绝了该客户端）', 'shape');
  }
  fs.mkdirSync(paths.profiles(), { recursive: true });
  fs.writeFileSync(path.join(paths.profiles(), 'subscribe.yaml'), res.text, 'utf8');
  store.set('subscription_updated_at', Date.now());

  // 订阅域名必须直连，否则开代理后自己拉不到自己。
  // 旧代码这里只取了 host 就 `void host` 丢掉，等于什么都没做。
  try {
    const host = new URL(abs).hostname;
    if (host) require('../core/remote').rememberDirectHost(host);
  } catch (_) {}
  log.info(`subscription refreshed (${res.text.length} bytes)`);
  return { bytes: res.text.length };
}

function requestAbsolute(url) {
  return new Promise((resolve, reject) => {
    let u;
    try { u = new URL(url); } catch (e) { reject(new PanelError('订阅地址非法', 'input')); return; }
    const mod = u.protocol === 'https:' ? https : http;
    const req = mod.get(url, { headers: { 'User-Agent': UA }, timeout: 30000 }, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        res.resume();
        requestAbsolute(new URL(res.headers.location, url).toString()).then(resolve, reject);
        return;
      }
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => resolve({ status: res.statusCode, text: Buffer.concat(chunks).toString('utf8') }));
    });
    req.on('timeout', () => req.destroy(new PanelError('订阅下载超时', 'timeout')));
    req.on('error', (e) => reject(new PanelError(e.message || '订阅下载失败', 'net')));
  });
}

/* ------------------------------------------------------------------ */
/* 业务数据                                                            */
/* ------------------------------------------------------------------ */

/** 面板的周期价字段（单位：分）。没有通用 price 字段，价格必须按周期取。 */
const PERIODS = [
  ['month_price', '月'], ['quarter_price', '季'], ['half_year_price', '半年'],
  ['year_price', '年'], ['two_year_price', '两年'], ['three_year_price', '三年'],
  ['onetime_price', '一次性'],
];

function yuan(cents) {
  const n = Number(cents);
  if (!Number.isFinite(n) || n <= 0) return 0;
  return Math.round(n) / 100;
}

function planFeats(p) {
  const out = [];
  const gb = Number(pick(p, ['transfer_enable'], 0)) || 0;
  if (gb > 0) out.push(gb >= 1024 ? Math.round(gb / 1024 * 10) / 10 + ' TB 流量' : gb + ' GB 流量');
  const dev = Number(pick(p, ['device_limit'], 0)) || 0;
  if (dev > 0) out.push(dev + ' 台设备同时在线');
  const raw = String(pick(p, ['content'], '') || '').replace(/<[^>]+>/g, '');
  for (const line of raw.split(/\r?\n/)) {
    const t = line.replace(/[#*`>~\-[\]()]/g, '').trim();
    if (t && !/^https?:/i.test(t) && out.length < 4) out.push(t.slice(0, 42));
  }
  return out.slice(0, 4);
}

async function plans() {
  const list = await get('/user/plan/fetch');
  return (Array.isArray(list) ? list : []).map((p) => {
    const periods = PERIODS
      .filter(([k]) => Number(p[k]) > 0)
      .map(([k, label]) => ({ key: k, label, price: yuan(p[k]) }));
    const first = periods[0] || { key: 'month_price', label: '月', price: 0 };
    return {
      id: String(pick(p, ['id'], '')),
      name: String(pick(p, ['name', 'title'], '')),
      price: first.price,
      period: first.key,
      unit: first.label,
      periods,
      currency: String(pick(p, ['currency'], 'CNY')),
      transfer_enable: Number(pick(p, ['transfer_enable'], 0)) || 0,
      device_limit: Number(pick(p, ['device_limit'], 0)) || 0,
      sold: Number(pick(p, ['sold'], 0)) || 0,
      stock: Number(pick(p, ['stock'], -1)),
      hot: !!p.hot || Number(pick(p, ['sort'], 0)) >= 999,
      sell: p.sell === undefined ? true : !!p.sell,
      renew: !!p.renew,
      // 面板的套餐说明是 Markdown 原文（标题/列表/表格/链接都有），
      // 原样带到界面由 src/js/md.js 渲染；feats 只是没有正文时的兜底摘要。
      content: String(pick(p, ['content', 'description'], '') || ''),
      feats: planFeats(p),
    };
  });
}

async function orders() {
  const list = await get('/user/order/fetch');
  return (Array.isArray(list) ? list : []).map((o) => {
    const status = String(pick(o, ['status'], ''));
    const plan = (o && typeof o.plan === 'object' && o.plan) ? o.plan : null;
    // 套餐名在**嵌套的** o.plan.name 里，金额字段是 total_amount（分）——
    // 旧代码读平铺的 name/amount，订单列表因此全是空白 + ¥0。
    const amount = yuan(pick(o, ['total_amount', 'amount', 'price'], 0));
    const periodLabel = (PERIODS.find(([k]) => k === pick(o, ['period'], '')) || [, ''])[1];
    return {
      no: String(pick(o, ['trade_no', 'order_no', 'id'], '')),
      name: String((plan && plan.name) || pick(o, ['plan_name', 'name', 'description'], '')),
      amount: amount ? String(amount) : '0',
      period: String(pick(o, ['period'], '')),
      period_label: periodLabel,
      date: dateTime(toTs(pick(o, ['create_at', 'created_at'], 0))),
      status: orderStatus(status),
      raw_status: status,
    };
  });
}

function orderStatus(s) {
  // 数字是面板下发的原始状态；字符串是已经映射过的 UI 状态（少数分支直接返回文案）
  const numeric = {
    0: 'pending', 1: 'processing', 2: 'done', 3: 'refunded', 4: 'refunded',
  };
  if (typeof s === 'number' || /^\d+$/.test(String(s))) {
    return numeric[Number(s)] || 'pending';
  }
  const named = {
    pending: 'pending', processing: 'processing', completed: 'done',
    complete: 'done', done: 'done', cancelled: 'refunded', canceled: 'refunded', refunded: 'refunded',
  };
  return named[String(s).toLowerCase()] || 'pending';
}

async function createOrder(planId, opts = {}) {
  const no = await post('/user/order/save', {
    plan_id: planId,
    period: opts.period || 'month_price',
    coupon_code: opts.couponCode || undefined,
  });
  return { order_no: String(pick(no, ['trade_no'], no || '')) };
}

async function orderDetail(tradeNo) {
  const o = await get(`/user/order/detail?trade_no=${encodeURIComponent(tradeNo)}`);
  return { no: String(pick(o, ['trade_no'], tradeNo)), status: orderStatus(String(pick(o, ['status'], ''))) };
}

async function paymentMethods() {
  const list = await get('/user/order/getPaymentMethod');
  return (Array.isArray(list) ? list : []).map((m) => ({
    id: String(pick(m, ['id', 'code', 'payment'], '')),
    name: String(pick(m, ['name', 'title', 'description'], '')),
    type: String(pick(m, ['type'], '')),
  }));
}

/** 收银台：返回 URL，由渲染层用系统浏览器打开（不在应用内嵌第三方支付页） */
async function checkoutUrl(tradeNo, method) {
  const res = await post('/user/order/checkout', {
    trade_no: tradeNo,
    payment_id: method,
    callback_url: `${session.panelUrl}${API_PREFIX}/user/order/checkout`,
  }).catch(() => null);
  void res;
  return `${session.panelUrl}${API_PREFIX}/user/order/checkout?trade_no=${encodeURIComponent(tradeNo)}&payment_id=${encodeURIComponent(method)}`;
}

/** 工单正文：Xboard 的 XboardTicketData.message 是"消息数组"（安卓端口径），不是 content 字符串 */
function ticketContent(t) {
  const raw = t && Object.prototype.hasOwnProperty.call(t, 'message') ? t.message : null;
  if (typeof raw === 'string' && raw) return raw;
  if (Array.isArray(raw)) {
    const texts = raw
      .map((m) => (typeof m === 'string' ? m : String(pick(m || {}, ['message', 'content'], ''))))
      .filter(Boolean);
    if (texts.length) return texts.join('\n\n');
  }
  return String(pick(t || {}, ['content'], ''));
}

async function tickets() {
  const list = await get('/user/ticket/fetch');
  return (Array.isArray(list) ? list : []).map((t) => ({
    no: String(pick(t, ['id'], '')),
    subject: String(pick(t, ['subject', 'title'], '')),
    date: dateTime(toTs(pick(t, ['created_at', 'create_at'], 0))),
    updated: dateTime(toTs(pick(t, ['updated_at', 'update_at'], 0))),
    status: ticketStatus(t),
    level: String(pick(t, ['level'], 0)),
    content: ticketContent(t),
  }));
}

function ticketStatus(t) {
  const s = Number(pick(t, ['status'], 0));
  const replied = Number(pick(t, ['reply_status'], t.replied_at ? 1 : 0));
  if (Number(t.closed_at) > 0 || s === 3) return 'closed';
  if (replied > 0) return 'replied';
  return 'pending';
}

/**
 * 新建工单。请求体字段名照安卓端 XboardCreateTicketRequest(subject, level: Int, message) ——
 * 发 content 会建出一条空工单。level：0 低 / 1 中（安卓默认）/ 2 高。
 */
async function createTicket(subject, content, level = 1) {
  const n = Number(level);
  const r = await post('/user/ticket/save', {
    subject,
    message: String(content || ''),
    level: Number.isFinite(n) && n >= 0 && n <= 2 ? Math.round(n) : 1,
  });
  return { no: String(pick(r, ['id'], '')), ok: true };
}

async function invite() {
  const i = await get('/user/invite/fetch');
  // 真机实测形状：{codes:[{id,user_id,code,status,pv,created_at}], stat:[...]} ——
  // 没有平铺的 invite_code / invite_url / invite_num，旧映射因此全是空。
  // stat 的下标含义照安卓端 XboardInviteData.toDomain()（XboardDto.kt:218）：
  //   [0] 已注册人数 [1] 累计佣金(分) [2] 待确认佣金(分) [3] 佣金比例(%) [4] 可提现余额(分)
  const codes = Array.isArray(i.codes) ? i.codes : [];
  const first = codes[0];
  const code = typeof first === 'string' ? first : String(pick(first || {}, ['code', 'invite_code'], ''));
  const stat = Array.isArray(i.stat) ? i.stat.map((n) => Number(n) || 0) : [];
  const at = (n) => stat[n] || 0;
  const fen = (v) => (Number(v) || 0) / 100; // 分 → 元
  return {
    code,
    codes: codes.map((c) => (typeof c === 'string'
      ? { code: c, pv: 0, created_at: 0 }
      : { code: String(pick(c, ['code'], '')), pv: Number(pick(c, ['pv'], 0)) || 0, created_at: toTs(pick(c, ['created_at'], 0)) })),
    link: String(pick(i, ['invite_url', 'link'], ''))
      || (code ? `${session.panelUrl}${API_PREFIX}/#/register?code=${encodeURIComponent(code)}` : ''),
    registered: at(0),
    commission: fen(at(1)),
    pending: fen(at(2)),
    rate: at(3),
    balance: fen(at(4)),
    invited: at(0),
    stat,
  };
}

async function giftHistory() {
  // 多数面板不提供兑换历史接口；先查本地流水，再退回空列表
  return [];
}

async function redeemGift(code) {
  const clean = String(code || '').trim();
  if (!/^[A-Za-z0-9-]{16,}$/.test(clean)) {
    return { ok: false, reward: '', msg: '卡密格式不正确' };
  }
  // 两条路径互为兜底，且**不改**会话里的后端标记 ——
  // 一次兑换失败不能把后续请求都带到另一条路径上去（早期版本踩过这个坑）
  let lastMsg = '';
  for (const p of GIFT_PATHS) {
    try {
      const r = await post(p, { card_code: clean });
      return { ok: true, reward: String(pick(r, ['message', 'text', 'msg'], '兑换成功')), msg: '' };
    } catch (e) {
      lastMsg = e.message;
      if (e.kind === 'auth' || e.kind === 'net' || e.kind === 'timeout') break;
    }
  }
  return { ok: false, reward: '', msg: lastMsg || '兑换失败' };
}

async function notices() {
  const list = await get('/user/notice/fetch?current=1&pageSize=50');
  return (Array.isArray(list) ? list : []).map((n) => {
    // 真实面板的公告**没有已读字段**（抓包确认：只有 id/sort/title/content/show/
    // created_at/updated_at）。旧代码写 `unread: !n.read_at` —— 字段不存在时
    // 恒为 true，于是"我的"页永远挂着"2 条未读"。安卓端的 Notice 模型里
    // 压根没有已读概念，这里也对齐：面板给了已读状态才判断。
    const readAt = n.read_at;
    return {
      id: String(pick(n, ['id'], '')),
      title: String(pick(n, ['title'], '')),
      date: dateOnly(toTs(pick(n, ['created_at', 'create_at'], 0))),
      body: String(pick(n, ['content'], '')),
      unread: readAt === undefined ? false : !readAt,
    };
  });
}

async function trafficLog() {
  // 一次刷新里流量页会问两次（区间汇总 + 明细列表），加个短缓存别把面板打两遍
  if (trafficCache.rows && Date.now() - trafficCache.at < 15000) return trafficCache.rows;
  // 只有 Xboard 提供这个接口。不再按 backend 标记短路 —— 面板分支五花八门，
  // 直接试一次，404 就当没有（返回空数组），不要让标记把人挡住。
  try {
    const res = await request('GET', '/user/stat/getTrafficLog', { raw: true });
    if (res.status !== 200) return [];
    const parsed = JSON.parse(res.text);
    // 真实 Xboard 这里返回**裸数组**；部分分支会包一层 {data:[...]}，两种都吃
    const arr = Array.isArray(parsed) ? parsed : (Array.isArray(parsed && parsed.data) ? parsed.data : null);
    if (!arr) {
      log.warn('trafficLog 返回了非数组，已忽略');
      return [];
    }
    trafficCache.rows = aggregateTrafficLog(arr);
    trafficCache.at = Date.now();
    return trafficCache.rows;
  } catch (e) {
    log.warn('trafficLog unavailable:', e && e.message);
    return [];
  }
}

/** 站点流量明细的短缓存：一次界面刷新里会被问两次（区间汇总 + 明细列表） */
const trafficCache = { at: 0, rows: null };

/**
 * 按天聚合站点流量明细。
 * 真机实测（app.pinxiaoche.top，Xboard）：一行 = 一天里的一个计费倍率，
 * 同一天有多行；字段是 `{d, u, record_at(秒，当地零点), server_rate}`，
 * **没有** date / upload / download / total。旧代码直接读那些不存在的字段，
 * 于是「面板流量明细」永远是一列空白日期 + 0 B。
 *
 * server_rate **不参与求和**：实测按天原始求和与 /user/getSubscribe 的 u/d 完全一致
 * （22.795 GB / 104.976 GB 对得上），乘倍率会翻倍，与面板自己显示的"已用流量"不符。
 */
function aggregateTrafficLog(arr) {
  // 面板可能返回非数组（裸对象/字符串/null）——不设防会 TypeError 打穿到渲染层
  if (!Array.isArray(arr)) return [];
  const byDay = new Map();
  for (const r of arr) {
    if (!r) continue;
    const rawTs = pick(r, ['record_at', 'date', 'created_at'], 0);
    let sec = 0;
    if (typeof rawTs === 'string' && !/^\d+$/.test(rawTs)) {
      const d = Date.parse(rawTs);
      sec = Number.isFinite(d) ? Math.floor(d / 1000) : 0;
    } else {
      sec = Number(rawTs) || 0;
      if (sec > 1e12) sec = Math.floor(sec / 1000);
    }
    const cur = byDay.get(sec) || { date: dateOnly(sec * 1000), ts: sec, upload: 0, download: 0, total: 0 };
    cur.upload += Number(pick(r, ['u', 'upload'], 0)) || 0;
    cur.download += Number(pick(r, ['d', 'download'], 0)) || 0;
    cur.total = cur.upload + cur.download;
    byDay.set(sec, cur);
  }
  return [...byDay.values()].sort((a, b) => b.ts - a.ts);
}

async function changePassword(oldPassword, newPassword) {
  await post('/user/changePassword', { old_password: oldPassword, new_password: newPassword });
  return true;
}

async function sendEmailCode(email, purpose = 'forget') {
  await post('/passport/comm/sendEmailVerify', { email, purpose });
  return true;
}

async function forgotPassword(email, code, password) {
  await post('/passport/auth/forget', { email, code, password });
  return true;
}

async function registerConfig() {
  const cfg = await get('/guest/comm/config', { noAuth: true });
  return {
    email_verify: Number(pick(cfg, ['is_email_verify', 'email_verify'], 0)) || 0,
    invite_force: Number(pick(cfg, ['is_invite_force', 'invite_force'], 0)) || 0,
    email_whitelist_suffix: String(pick(cfg, ['emailWhitelistSuffix', 'email_whitelist_suffix'], '')),
  };
}

async function register(email, password, emailCode, inviteCode) {
  const data = await post('/passport/auth/register', {
    email, password,
    email_code: emailCode || undefined,
    invite_code: inviteCode || undefined,
  });
  const token = pick(data, ['auth_data', 'token', 'access_token']);
  if (!token) throw new PanelError('注册成功但未返回登录凭据，请直接登录', 'shape');
  session.token = String(token);
  session.email = email;
  store.set('panel_url', session.panelUrl);
  store.set('last_email', email);
  persist();
  return { email };
}

module.exports = {
  session, PanelError,
  restore, login, logout, isAuthed, register,
  siteInfo, telegramLink, safeTelegram, userInfo, subscribeInfo, refreshSubscription,
  plans, orders, createOrder, orderDetail, paymentMethods, checkoutUrl,
  tickets, createTicket, invite, giftHistory, redeemGift, notices, trafficLog,
  changePassword, sendEmailCode, forgotPassword, registerConfig,
  normalizePanelUrl,
  // 纯函数，导出给自检用（把面板原始行按天聚合 / 工单正文兜底）
  aggregateTrafficLog,
  ticketContent,
  clearUserInfoCache,
};
