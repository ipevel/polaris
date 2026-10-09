'use strict';
/**
 * 本地假 V2Board 面板，用于端到端自检（不需要真实面板账号）。
 *
 *   node scripts/mock-panel.js [port]
 *
 * 实现的是 Polaris 实际调用的那批接口，路径与 Xboard / XiaoV2b 一致。
 * 订阅 YAML 里故意混入脏数据（重名节点、信息伪节点、被订阅劫持的控制面），
 * 用来验证清洗链路在真实网络路径下也生效。
 */

const http = require('http');

const TOKEN = 'mock-token-0123456789abcdef';
const SUBSCRIPTION = `
# 假面板下发的订阅（含脏数据）
mixed-port: 7899
external-controller: 0.0.0.0:9999
secret: attacker
geox-url:
  geoip: https://evil.example/geoip.dat
dns:
  enable: true
proxies:
  - {name: "🇭🇰 香港 01", type: http, server: 127.0.0.1, port: 7890, username: "", password: ""}
  - {name: "🇭🇰 香港 01", type: http, server: 127.0.0.1, port: 7890}
  - {name: "🇯🇵 日本 01", type: http, server: 127.0.0.1, port: 7890}
  - {name: "剩余流量：86.5 GB", type: http, server: 127.0.0.1, port: 7890}
  - {name: "🇸🇬 新加坡 01", type: http, server: 127.0.0.1, port: 7890}
proxy-groups:
  - name: 节点选择
    type: select
    proxies: ["🇭🇰 香港 01", "🇯🇵 日本 01", "🇸🇬 新加坡 01", "剩余流量：86.5 GB"]
  - name: 流媒体分流
    type: select
    proxies: ["🇸🇬 新加坡 01", "🇭🇰 香港 01"]
rules:
  - GEOIP,CN,DIRECT
  - MATCH,节点选择
`;

const now = () => Math.floor(Date.now() / 1000);

const ROUTES = {
  'POST /passport/auth/login': (body) => {
    if (body.password === 'wrong-password') throw httpErr(401, '密码错误');
    return { auth_data: TOKEN, token: TOKEN, email: body.email, is_admin: 0 };
  },
  'POST /passport/auth/register': (body) => ({ auth_data: TOKEN, token: TOKEN, email: body.email }),
  'POST /passport/comm/sendEmailVerify': () => true,
  'POST /passport/auth/forget': () => true,
  'GET /guest/comm/config': () => ({
    app_name: 'Mock Polar Panel',
    app_description: '本地自检用假面板',
    is_email_verify: 0,
    is_invite_force: 0,
  }),
  'GET /user/info': () => ({
    email: 'tester@example.com',
    balance: 12.5,
    plan: '旗舰套餐',
    transfer_enable: 200 * 1024 ** 3,
    u: 1.24 * 1024 ** 3,
    d: 8.62 * 1024 ** 3,
    expired_at: now() + 30 * 86400,
  }),
  'GET /user/getSubscribe': () => ({
    subscribe_url: null,          // 走 token 拼装路径，覆盖 client/subscribe
    token: TOKEN,
    expired_at: now() + 30 * 86400,
  }),
  'GET /user/plan/fetch': () => ([
    { id: 1, name: '轻量套餐', price: 19, transfer_enable: 100 * 1024 ** 3, period: '月', content: '<p>100 GB 流量</p>' },
    { id: 2, name: '旗舰套餐', price: 39, transfer_enable: 200 * 1024 ** 3, period: '月', sort: 999 },
  ]),
  'GET /user/order/fetch': () => ([
    { trade_no: 'PL20260901001', plan_name: '旗舰套餐', amount: 39, status: 2, create_at: now() - 86400 * 30 },
    { trade_no: 'PL20261001009', plan_name: '旗舰套餐', amount: 39, status: 0, create_at: now() - 3600 },
  ]),
  'POST /user/order/save': () => 'PL' + Date.now(),
  'GET /user/order/getPaymentMethod': () => ([
    { id: 1, name: '支付宝', payment: 'alipay' },
    { id: 2, name: '微信支付', payment: 'wechat' },
  ]),
  'POST /user/order/checkout': () => ({ type: 'url', data: 'https://example.com/pay' }),
  'GET /user/ticket/fetch': (b, q) => (q.id
    ? { id: Number(q.id), subject: '节点连接超时', status: 0, level: 'low', created_at: now() - 7200, content: '香港节点连不上' }
    : [
      { id: 1024, subject: '节点连接超时', status: 0, reply_status: 1, level: 'low', created_at: now() - 7200 },
      { id: 1011, subject: '支付后套餐未生效', status: 3, level: 'low', created_at: now() - 86400 * 21 },
    ]),
  'POST /user/ticket/save': () => true,
  'GET /user/notice/fetch': () => ([
    { id: 1, title: '国庆假期节点维护通知', content: '10 月 1 日至 3 日期间部分节点维护。', created_at: now() - 86400, read_at: null },
    { id: 2, title: '新增 3 条 IEPL 专线', content: '新增香港/日本/新加坡专线。', created_at: now() - 86400 * 17, read_at: now() },
  ]),
  'GET /user/invite/fetch': () => ({ invite_code: 'MOCK1234', commission_rate: 15, commission_balance: 45.5, invite_num: 3 }),
  'POST /user/changePassword': () => true,
  'POST /user/gift-card/redeem': (body) => {
    if (body.card_code !== 'MOCKGIFTCARD1234') throw httpErr(400, '卡密无效');
    return { message: '30 天时长' };
  },
  'GET /user/stat/getTrafficLog': () => ([
    { date: '2026-10-09', u: 1.24 * 1024 ** 3, d: 8.62 * 1024 ** 3, total: 9.86 * 1024 ** 3 },
    { date: '2026-10-08', u: 0.8 * 1024 ** 3, d: 5.1 * 1024 ** 3, total: 5.9 * 1024 ** 3 },
  ]),
};

function httpErr(status, message) {
  const e = new Error(message);
  e.status = status;
  return e;
}

function send(res, status, payload, type) {
  const body = typeof payload === 'string' ? payload : JSON.stringify(payload);
  res.writeHead(status, {
    'Content-Type': type || 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body),
  });
  res.end(body);
}

function start(port = 0) {
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://127.0.0.1');
    const pathname = url.pathname.replace(/^\/api\/v1/, '');
    const chunks = [];
    req.on('data', (c) => chunks.push(c));
    req.on('end', () => {
      let body = {};
      try { body = JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}'); } catch (_) {}
      const q = Object.fromEntries(url.searchParams.entries());

      // 订阅下载
      if (pathname === '/client/subscribe') {
        if (q.token !== TOKEN) { send(res, 403, 'invalid token', 'text/plain'); return; }
        send(res, 200, SUBSCRIPTION, 'text/yaml; charset=utf-8');
        return;
      }

      // 鉴权
      const route = `${req.method} ${pathname}`;
      const open = ['POST /passport/auth/login', 'POST /passport/auth/register', 'GET /guest/comm/config',
        'POST /passport/comm/sendEmailVerify', 'POST /passport/auth/forget'];
      if (!open.includes(route) && req.headers.authorization !== TOKEN) {
        send(res, 401, { status: 'fail', message: '未授权' });
        return;
      }

      const handler = ROUTES[route];
      if (!handler) {
        send(res, 404, { status: 'fail', message: `mock 未实现 ${route}` });
        return;
      }
      try {
        const data = handler(body, q);
        // 真实 Xboard 的这个接口返回**裸 JSON 数组**，不裹 {status,data}
        if (route === 'GET /user/stat/getTrafficLog') {
          send(res, 200, data);
          return;
        }
        send(res, 200, { status: 'success', data, message: '' });
      } catch (e) {
        send(res, e.status || 500, { status: 'fail', message: e.message });
      }
    });
  });

  return new Promise((resolve) => {
    server.listen(port, '127.0.0.1', () => {
      resolve({ server, port: server.address().port, token: TOKEN });
    });
  });
}

module.exports = { start, TOKEN, SUBSCRIPTION, ROUTES };

if (require.main === module) {
  const p = Number(process.argv[2]) || 18790;
  start(p).then(({ port }) => console.log(`mock panel on http://127.0.0.1:${port}`));
}
