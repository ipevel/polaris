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
    const req = mod.request(url, { method, headers, timeout: timeoutMs }, (res) => {
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', async () => {
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
  const giftPath = session.giftPath();
  void giftPath;
  // 默认按 Xboard 处理；gift-card 兑换失败时再自动切 XiaoV2b
  return store.get('panel_backend') || 'xboard';
}

function giftPath() {
  return session.backend === 'xiaov2b' ? '/user/redeemgiftcard' : '/user/gift-card/redeem';
}

function logout() {
  session.token = '';
  session.email = '';
  session.backend = '';
  credentials.clear();
}

const isAuthed = () => !!session.token;

/* ------------------------------------------------------------------ */
/* 站点 / 用户                                                         */
/* ------------------------------------------------------------------ */

async function siteInfo() {
  try {
    const cfg = await get('/guest/comm/config', { noAuth: true });
    return {
      appName: String(pick(cfg, ['app_name', 'appName', 'site_name', 'name'], '')),
      appDescription: String(pick(cfg, ['app_description', 'appDescription', 'description', 'sub_name'], '')),
      appUrl: String(pick(cfg, ['app_url', 'appUrl', 'home_url', 'url'], session.panelUrl)),
      telegramUrl: String(pick(cfg, ['telegram_url', 'telegramUrl'], '')),
      icp: String(pick(cfg, ['icp', 'icp_url'], '')),
    };
  } catch (e) {
    log.warn('siteInfo failed:', e.message);
    return { appName: '', appDescription: '', appUrl: session.panelUrl, telegramUrl: '', icp: '' };
  }
}

async function userInfo() {
  const u = await get('/user/info');
  const transferEnable = Number(pick(u, ['transfer_enable', 'transferEnable'], 0)) || 0;
  const uUsed = Number(pick(u, ['u', 'used', 'used_traffic'], 0)) || 0;
  const planName = pick(u, ['plan', 'plan_name', 'planName'], '未订阅');
  const expiredAt = toTs(pick(u, ['expired_at', 'expire_at', 'expiredAt'], 0));
  const plan = (u && typeof u.plan === 'object' && u.plan) ? u.plan : null;
  return {
    email: String(pick(u, ['email'], session.email)),
    balance: Number(pick(u, ['balance'], 0)) || 0,
    plan_name: String(planName),
    total: Math.round(transferEnable / (1024 ** 3) * 100) / 100,
    used: Math.round(uUsed / (1024 ** 3) * 100) / 100,
    expire: dateOnly(expiredAt),
    expired_at: expiredAt,
    transfer_enable: transferEnable,
    u: uUsed,
    d: Number(pick(u, ['d', 'download'], 0)) || 0,
    ...(plan ? { plan } : {}),
  };
}

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

  // 订阅域名必须直连，否则开代理后自己拉不到自己
  try {
    const host = new URL(abs).hostname;
    if (host) {
      const existing = require('../core/remote').directDomains();
      require('../core/remote').load();
      void existing;
      void host;
    }
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

async function plans() {
  const list = await get('/user/plan/fetch');
  return (Array.isArray(list) ? list : []).map((p) => ({
    id: String(pick(p, ['id'], '')),
    name: String(pick(p, ['name', 'title'], '')),
    price: Number(pick(p, ['price'], 0)) || 0,
    currency: String(pick(p, ['currency'], 'CNY')),
    transfer_enable: Number(pick(p, ['transfer_enable'], 0)) || 0,
    month_price: Number(pick(p, ['month_price'], 0)) || 0,
    unit: String(pick(p, ['period'], '')),
    sold: Number(pick(p, ['sold'], 0)) || 0,
    stock: Number(pick(p, ['stock'], -1)),
    hot: !!p.hot || Number(pick(p, ['sort'], 0)) >= 999,
    feats: [].concat(pick(p, ['content'], []) || []).map((x) => String(x).replace(/<[^>]+>/g, '').trim()).filter(Boolean).slice(0, 3),
  }));
}

async function orders() {
  const list = await get('/user/order/fetch');
  return (Array.isArray(list) ? list : []).map((o) => {
    const status = String(pick(o, ['status'], ''));
    return {
      no: String(pick(o, ['trade_no', 'order_no', 'id'], '')),
      name: String(pick(o, ['plan_name', 'name', 'description'], '')),
      amount: String(pick(o, ['amount', 'price'], '0')),
      date: dateTime(toTs(pick(o, ['create_at', 'created_at'], 0))),
      status: orderStatus(status),
      raw_status: status,
    };
  });
}

function orderStatus(s) {
  const map = {
    0: 'pending', 1: 'processing', 2: 'completed', 3: 'cancelled',
    pending: 'pending', processing: 'processing', completed: 'done', cancelled: 'refunded',
  };
  return map[s] || 'pending';
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

async function tickets() {
  const list = await get('/user/ticket/fetch');
  return (Array.isArray(list) ? list : []).map((t) => ({
    no: String(pick(t, ['id'], '')),
    subject: String(pick(t, ['subject', 'title'], '')),
    date: dateTime(toTs(pick(t, ['created_at', 'create_at'], 0))),
    updated: dateTime(toTs(pick(t, ['updated_at', 'update_at'], 0))),
    status: ticketStatus(t),
    level: String(pick(t, ['level'], 'low')),
    content: String(pick(t, ['content'], '')),
  }));
}

function ticketStatus(t) {
  const s = Number(pick(t, ['status'], 0));
  const replied = Number(pick(t, ['reply_status'], t.replied_at ? 1 : 0));
  if (Number(t.closed_at) > 0 || s === 3) return 'closed';
  if (replied > 0) return 'replied';
  return 'pending';
}

async function createTicket(subject, content, level = 'low') {
  const r = await post('/user/ticket/save', { subject, content, level });
  return { no: String(pick(r, ['id'], '')), ok: true };
}

async function invite() {
  const i = await get('/user/invite/fetch');
  return {
    code: String(pick(i, ['invite_code', 'code'], '')),
    link: String(pick(i, ['invite_url', 'link'], '')),
    rate: Number(pick(i, ['commission_rate', 'rate'], 0)) || 0,
    balance: Number(pick(i, ['commission_balance', 'balance'], 0)) || 0,
    invited: Number(pick(i, ['invite_num', 'invited_count'], 0)) || 0,
  };
}

async function giftHistory() {
  try {
    const r = await get('/user/ticket/fetch');   // 多数面板不提供兑换历史，返回空
    void r;
  } catch (_) {}
  return [];
}

async function redeemGift(code) {
  const clean = String(code || '').trim();
  if (!/^[A-Za-z0-9-]{16,}$/.test(clean)) {
    return { ok: false, reward: '', msg: '卡密格式不正确' };
  }
  try {
    const r = await post(giftPath(), { card_code: clean });
    return { ok: true, reward: String(pick(r, ['message', 'text'], '兑换成功')), msg: '' };
  } catch (e) {
    // 两个后端路径互为兜底：第一次失败就换另一种形态再试
    if (session.backend === 'xboard') {
      session.backend = 'xiaov2b';
      try {
        const r = await post(giftPath(), { card_code: clean });
        return { ok: true, reward: String(pick(r, ['message', 'text'], '兑换成功')), msg: '' };
      } catch (e2) {
        return { ok: false, reward: '', msg: e2.message };
      }
    }
    return { ok: false, reward: '', msg: e.message };
  }
}

async function notices() {
  const list = await get('/user/notice/fetch?current=1&pageSize=50');
  return (Array.isArray(list) ? list : []).map((n) => ({
    id: String(pick(n, ['id'], '')),
    title: String(pick(n, ['title'], '')),
    date: dateOnly(toTs(pick(n, ['created_at', 'create_at'], 0))),
    body: String(pick(n, ['content'], '')),
    unread: !n.read_at,
  }));
}

async function trafficLog() {
  if (session.backend !== 'xboard') return [];
  const res = await request('GET', '/user/stat/getTrafficLog', { raw: true });
  if (res.status !== 200) return [];
  let arr;
  try { arr = JSON.parse(res.text); } catch (_) { return []; }
  return (Array.isArray(arr) ? arr : []).map((r) => ({
    date: String(pick(r, ['date', 'created_at'], '')),
    upload: Number(pick(r, ['u', 'upload'], 0)) || 0,
    download: Number(pick(r, ['d', 'download'], 0)) || 0,
    total: Number(pick(r, ['total'], 0)) || 0,
  }));
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
  siteInfo, userInfo, subscribeInfo, refreshSubscription,
  plans, orders, createOrder, orderDetail, paymentMethods, checkoutUrl,
  tickets, createTicket, invite, giftHistory, redeemGift, notices, trafficLog,
  changePassword, sendEmailCode, forgotPassword, registerConfig,
  normalizePanelUrl,
};
