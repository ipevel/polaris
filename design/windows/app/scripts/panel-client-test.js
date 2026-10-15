'use strict';
/**
 * 面板客户端自测（纯 Node + 本地假面板，不联网、不碰真面板）。
 *   用法：node scripts/panel-client-test.js
 *
 * 覆盖两条缺陷：
 *   W1 userInfo 缺「在飞合并」—— 同一时刻的多路调用（首页/流量页/我的页同时要套餐）
 *      会各发一轮 /user/info + /user/getSubscribe。旧代码只有 45s 结果缓存，
 *      而缓存是拿到结果才写的，并发那几路一条都吃不到。这里用假面板数请求条数钉死。
 *   W3 checkoutUrl 吞错 —— 旧代码把失败 catch 成 null、再用 void 丢掉成功结果，
 *      最后无条件拼 GET 地址：面板 500 / 订单失效时照样「成功」打开一个坏链接。
 *      现在面板失败必须抛给上层（ipc 的校验与界面的 guard toast 才有机会说话）。
 *   W2 toTs 死三元 —— 旧代码三个分支全乘 1000，面板回毫秒时日期被放大成公元 5 万年。
 *      这里把秒/毫秒/字符串/0/负数边界与「到期日」端到端一起钉死。
 */
const http = require('http');
const os = require('os');
const path = require('path');

// paths.js 优先用这个环境变量当数据根目录，免得自检去写真实用户数据
process.env.POLARIS_DATA_DIR = path.join(os.tmpdir(), 'polaris-panel-client-test');

const panel = require(path.join(__dirname, '..', 'electron', 'panel', 'client'));

const API = '/api/v1';
const P = (p) => API + p;

let pass = 0;
let fail = 0;
function check(name, ok, detail) {
  if (ok) { pass += 1; console.log('  [ OK ] ' + name + (detail ? '  ' + detail : '')); }
  else { fail += 1; console.log('  [FAIL] ' + name + '  ' + (detail || '')); }
}

/* ---------------- 假面板 ---------------- */
let hits = [];
let routes = Object.create(null);   // path -> { status, json }

const server = http.createServer((req, res) => {
  const chunks = [];
  req.on('data', (c) => chunks.push(c));
  req.on('end', () => {
    const raw = Buffer.concat(chunks).toString('utf8');
    let body = null;
    try { body = raw ? JSON.parse(raw) : null; } catch (_) { body = raw; }
    const url = new URL(req.url, 'http://127.0.0.1');
    hits.push({ method: req.method, path: url.pathname, body });
    const h = routes[url.pathname];
    if (!h) {
      res.writeHead(404, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ status: 'fail', message: '假面板没有这条路由：' + url.pathname }));
      return;
    }
    res.writeHead(h.status || 200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(h.json === undefined ? {} : h.json));
  });
});

const hitsOf = (p) => hits.filter((h) => h.path === P(p)).length;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** 一份「有套餐」的面板回答 */
function havePanel(overrides) {
  routes = Object.create(null);
  Object.assign(routes, {
    [P('/user/info')]: { json: { status: 'ok', data: {
      email: 'self@test.local', balance: 12.5,
      transfer_enable: 100 * 1024 * 1024 * 1024, u: 1024 * 1024 * 1024, d: 2 * 1024 * 1024 * 1024,
      plan_id: 7, expired_at: 1893456000,
    } } },
    [P('/user/getSubscribe')]: { json: { status: 'ok', data: {
      u: 1024 * 1024 * 1024, d: 2 * 1024 * 1024 * 1024, transfer_enable: 100 * 1024 * 1024 * 1024,
      expired_at: 1893456000, next_reset_at: 1890000000,
      plan: { name: '自检套餐', transfer_enable: 100 },
    } } },
  }, overrides || {});
  hits = [];
}

(async () => {
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const port = server.address().port;
  panel.session.panelUrl = 'http://127.0.0.1:' + port;
  panel.session.token = 'SELF-TEST-TOKEN';
  panel.session.email = 'self@test.local';
  console.log('假面板 http://127.0.0.1:' + port + '（API 前缀 ' + API + '）');

  /* ---------- W1：在飞合并 ---------- */
  panel.clearUserInfoCache();
  havePanel();
  const [a, b, c] = await Promise.all([panel.userInfo(), panel.userInfo(), panel.userInfo()]);
  check('W1 并发 3 路 userInfo：/user/info 只发 1 次（旧代码 3 次）', hitsOf('/user/info') === 1,
    '实际 ' + hitsOf('/user/info') + ' 次');
  check('W1 并发 3 路 userInfo：/user/getSubscribe 只发 1 次（旧代码 3 次）',
    hitsOf('/user/getSubscribe') === 1, '实际 ' + hitsOf('/user/getSubscribe') + ' 次');
  check('W1 三路拿到同一份数据', JSON.stringify(a) === JSON.stringify(b) && JSON.stringify(b) === JSON.stringify(c));
  check('W1 数据没被合并弄坏（套餐名/总量/用量都在）',
    a.plan_name === '自检套餐' && a.total === 100 && a.used === 3 && a.used_bytes === 3 * 1024 * 1024 * 1024,
    JSON.stringify({ plan: a.plan_name, total: a.total, used: a.used }));

  await panel.userInfo();
  check('W1 45s 结果缓存仍然生效（第 4 次调用不发请求）', hitsOf('/user/info') === 1,
    '实际 ' + hitsOf('/user/info') + ' 次');

  await panel.userInfo({ force: true });
  check('W1 force 越过缓存、重发一轮（在飞状态已正确清空）', hitsOf('/user/info') === 2,
    '实际 ' + hitsOf('/user/info') + ' 次');

  // 面板挂了：失败不许污染缓存，也不许把在飞状态卡住
  havePanel({
    [P('/user/info')]: { status: 503, json: { status: 'fail', message: '面板炸了' } },
    [P('/user/getSubscribe')]: { status: 503, json: { status: 'fail', message: '面板炸了' } },
  });
  const degraded = await panel.userInfo({ force: true });
  check('W1 两个接口都 503 时不抛（userInfo 一直吞接口错误、沿用上一次套餐）',
    !!degraded && degraded.plan_name === '自检套餐', JSON.stringify(degraded && degraded.plan_name));
  check('W1 失败时仍然各发了一次', hitsOf('/user/info') === 1 && hitsOf('/user/getSubscribe') === 1);
  // 冷启动（本来就没有缓存）时失败：不许把「未订阅」缓存下来
  panel.clearUserInfoCache();
  hits = [];
  const cold = await panel.userInfo();
  check('W1 冷启动失败不写缓存（下次还会去问面板）', hitsOf('/user/info') === 1 && cold.plan_name === '未订阅',
    '实际 ' + hitsOf('/user/info') + ' 次 / plan=' + cold.plan_name);

  // 换账号/登出后，旧请求回来不许写缓存
  panel.clearUserInfoCache();
  havePanel();
  const stale = panel.userInfo();
  panel.clearUserInfoCache();          // 模拟「请求还在飞时用户退出登录」
  const staleData = await stale;
  hits = [];
  await panel.userInfo();
  check('W1 clearUserInfoCache 后旧请求没有写缓存（gen 守卫）',
    hitsOf('/user/info') === 1 && !!staleData, '实际 ' + hitsOf('/user/info') + ' 次');

  /* ---------- W3：收银台不再吞错 ---------- */
  havePanel({
    [P('/user/order/checkout')]: { status: 200, json: { status: 'fail', message: '订单已失效' } },
  });
  hits = [];
  let err1 = null;
  try { await panel.checkoutUrl('TN-1', 2); } catch (e) { err1 = e; }
  check('W3 面板明确失败（status:fail）必须抛出，不许回一个坏链接',
    !!err1 && /订单已失效/.test(err1.message), err1 ? err1.message : '没抛（旧行为）');
  check('W3 抛的是 PanelError（ipc 层按 kind 分类、按 message 显示）', !!err1 && err1.kind === 'api');

  havePanel({ [P('/user/order/checkout')]: { status: 500, json: { status: 'fail', message: 'server error' } } });
  let err2 = null;
  try { await panel.checkoutUrl('TN-2', 1); } catch (e) { err2 = e; }
  check('W3 面板 500 必须抛出', !!err2 && /HTTP 500/.test(err2.message), err2 ? err2.message : '没抛（旧行为）');

  havePanel({ [P('/user/order/checkout')]: { status: 200, json: { status: 'ok', data: 'https://pay.example.com/abc' } } });
  const u1 = await panel.checkoutUrl('TN-3', 3);
  check('W3 面板直接回 URL 字符串时要用它（旧代码 void res 丢掉了）',
    u1 === 'https://pay.example.com/abc', u1);

  havePanel({ [P('/user/order/checkout')]: { status: 200, json: { status: 'ok', data: { url: '/user/pay/xyz' } } } });
  const u2 = await panel.checkoutUrl('TN-4', 3);
  check('W3 面板回相对地址时补成绝对地址', u2 === 'http://127.0.0.1:' + port + '/user/pay/xyz', u2);

  havePanel({ [P('/user/order/checkout')]: { status: 200, json: { status: 'ok', data: true } } });
  const u3 = await panel.checkoutUrl('TN-5', 9);
  check('W3 面板只回 true（少数分支）时退回 GET 收银台地址，行为不退步',
    u3 === 'http://127.0.0.1:' + port + API + '/user/order/checkout?trade_no=TN-5&payment_id=9', u3);

  const postHit = hits.filter((h) => h.path === P('/user/order/checkout') && h.method === 'POST').pop();
  check('W3 下单仍是 POST，且 trade_no / payment_id 都在 body 里',
    !!postHit && postHit.body && postHit.body.trade_no === 'TN-5' && postHit.body.payment_id === 9,
    JSON.stringify(postHit && postHit.body));

  /* ---------- W2：秒 / 毫秒两种单位（旧代码全乘 1000） ---------- */
  const T = panel.toTs;
  check('W2 toTs 已导出（旧代码没有这个出口）', typeof T === 'function');
  if (typeof T === 'function') {
    check('W2 秒（10 位）按秒解释：×1000', T(1893456000) === 1893456000000, String(T(1893456000)));
    check('W2 毫秒（13 位）原样返回，不再被放大 1000 倍', T(1893456000123) === 1893456000123, String(T(1893456000123)));
    check('W2 数字字符串按同一套值域判断（秒 / 毫秒）',
      T('1893456000') === 1893456000000 && T('1893456000123') === 1893456000123,
      T('1893456000') + ' / ' + T('1893456000123'));
    check('W2 日期字符串仍走 Date.parse', T('2026-10-14T00:00:00Z') === Date.parse('2026-10-14T00:00:00Z'));
    check('W2 边界：1e12 当毫秒、1e12-1 当秒', T(1e12) === 1e12 && T(1e12 - 1) === (1e12 - 1) * 1000);
    check('W2 0 / 负数 / 空串 / null / undefined / NaN / 乱码 → 0（显示空日期，不是 1970 也不是 5 万年）',
      T(0) === 0 && T(-1) === 0 && T(-1893456000) === 0 && T('') === 0 && T('   ') === 0 &&
      T(null) === 0 && T(undefined) === 0 && T(NaN) === 0 && T('乱七八糟') === 0,
      [T(0), T(-1), T(''), T(null), T(NaN), T('乱七八糟')].join(','));
  }
  // 端到端：面板给毫秒时「到期日」必须还是那天（期望值就地按同一个 Date 算，避开时区差异）
  const MS_EXPIRE = 1893456000123;
  const dms = new Date(MS_EXPIRE);
  const pad2 = (n) => String(n).padStart(2, '0');
  const wantExpire = dms.getFullYear() + '-' + pad2(dms.getMonth() + 1) + '-' + pad2(dms.getDate());
  panel.clearUserInfoCache();
  havePanel({
    [P('/user/info')]: { json: { status: 'ok', data: {
      email: 'self@test.local', transfer_enable: 100 * 1024 * 1024 * 1024, u: 0, d: 0,
      plan_id: 7, expired_at: MS_EXPIRE,
    } } },
    [P('/user/getSubscribe')]: { json: { status: 'ok', data: {
      u: 0, d: 0, transfer_enable: 100 * 1024 * 1024 * 1024, expired_at: MS_EXPIRE,
      plan: { name: '自检套餐', transfer_enable: 100 },
    } } },
  });
  const msUser = await panel.userInfo({ force: true });
  check('W2 面板回毫秒时到期日仍是当天（旧代码会算成 5 万年后）',
    msUser.expire === wantExpire, msUser.expire + ' / 期望 ' + wantExpire);
  check('W2 expired_at 原样保留毫秒', msUser.expired_at === MS_EXPIRE, String(msUser.expired_at));

  server.close();
  console.log('');
  console.log(pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})().catch((e) => {
  console.error('自检自身崩了：', e);
  try { server.close(); } catch (_) {}
  process.exit(1);
});
