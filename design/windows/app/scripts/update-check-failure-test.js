'use strict';
/**
 * 「检查更新失败」不许显示成「已是最新版本」（假绿）自检。
 *   跑法：node scripts/update-check-failure-test.js
 *
 * 钉四件：
 *   ① 真实网络失败被分成 net / http / parse 三类（用本地假 GitHub 伺服真跑，不是看代码推断）；
 *   ② 分档文案：403 要单说「限流」、net 要给出「换个网络/断开代理」的可操作建议；
 *   ③ 接线断言：ipc 不再把失败吞成 null，且把 check_failed / update_check_failed 交出去；
 *   ④ 设置页与点「检查更新」都会走「失败」那条分支，而不是「已是最新版本」。
 */
const fs = require('fs');
const os = require('os');
const path = require('path');
const http = require('http');
const vm = require('vm');

const APP = path.join(__dirname, '..');
const BASE = fs.mkdtempSync(path.join(os.tmpdir(), 'polaris-chkfail-'));
process.env.POLARIS_DATA_DIR = BASE;

const release = require(path.join(APP, 'electron', 'core', 'release'));
const store = require(path.join(APP, 'electron', 'store'));

let total = 0; let failed = 0;
function check(name, cond, detail) {
  total += 1; if (!cond) failed += 1;
  console.log((cond ? '  [ OK ] ' : '  [FAIL] ') + name + (!cond && detail ? '  ' + detail : ''));
}
function loadViews() {
  const win = {};
  const ctx = vm.createContext({ window: win, console, Date, Math, JSON, Map, Set, Array, Number, String, RegExp, Object });
  for (const f of ['format.js', 'views.js']) {
    vm.runInContext(fs.readFileSync(path.join(APP, 'src', 'js', f), 'utf8'), ctx, { filename: f });
  }
  return win.PolarisViews;
}
async function fakeServer(handler) {
  const srv = http.createServer(handler);
  await new Promise((r) => srv.listen(0, '127.0.0.1', r));
  const base = 'http://127.0.0.1:' + srv.address().port;
  store.set('release_api_base', base);
  return srv;
}
async function tryCheck() {
  try { const i = await release.checkLatest('1.12.2'); return { ok: true, info: i }; }
  catch (e) { return { ok: false, kind: String((e && e.kind) || ''), status: Number((e && e.status) || 0), msg: String((e && e.message) || '') }; }
}

(async () => {
  console.log('-- 1. 真网络失败 → 分档（本地假 GitHub，不打外网） --');
  let srv = await fakeServer((req, res) => { res.writeHead(403); res.end('rate limited'); });
  let r = await tryCheck();
  check('403 → kind=http status=403（限流）', r.ok === false && r.kind === 'http' && r.status === 403, JSON.stringify(r));
  srv.close();

  srv = await fakeServer((req, res) => { res.writeHead(200); res.end('<html>proxy oops</html>'); });
  r = await tryCheck();
  check('回 HTML → kind=parse', r.ok === false && r.kind === 'parse', JSON.stringify(r));
  srv.close();

  srv = await fakeServer((req, res) => { res.writeHead(500); res.end('boom'); });
  r = await tryCheck();
  check('500 → kind=http status=500', r.ok === false && r.kind === 'http' && r.status === 500, JSON.stringify(r));
  srv.close();

  store.set('release_api_base', 'http://127.0.0.1:9');   // 没有监听 → ECONNREFUSED
  r = await tryCheck();
  check('连不上 → kind=net', r.ok === false && r.kind === 'net', JSON.stringify(r));

  srv = await fakeServer((req, res) => { res.writeHead(200, { 'Content-Type': 'application/json' }); res.end(JSON.stringify({ tag_name: 'v1.12.3', assets: [] })); });
  r = await tryCheck();
  check('有 release 但没 zip 资产 → 不失败，但 asset_missing 标出来', r.ok === true && r.info.asset_missing === true, JSON.stringify(r && r.info && { has: r.info.has_update, am: r.info.asset_missing }));
  srv.close();
  store.set('release_api_base', '');

  console.log('-- 2. 分档文案 --');
  const V = loadViews();
  check('views.js 导出了 checkFailText', typeof V.checkFailText === 'function');
  const c403 = V.checkFailText('http:403');
  check('403 单说限流', /限流/.test(c403) && /403/.test(c403), c403);
  const cNet = V.checkFailText('net');
  check('net 给可操作建议（换网络/断开代理）', /换个网络/.test(cNet) && /代理/.test(cNet), cNet);
  check('parse 说清不是合法发布信息', /不是合法发布信息/.test(V.checkFailText('parse')));
  check('对象输入也能吃', /限流/.test(V.checkFailText({ kind: 'http', status: 403 })));
  check('空值 → 空串（没有失败）', V.checkFailText('') === '' && V.checkFailText(null) === '');

  console.log('-- 3. 接线断言（源码级） --');
  const ipcSrc = fs.readFileSync(path.join(APP, 'electron', 'ipc.js'), 'utf8');
  const appSrc = fs.readFileSync(path.join(APP, 'src', 'js', 'app.js'), 'utf8');
  const viewsSrc = fs.readFileSync(path.join(APP, 'src', 'js', 'views.js'), 'utf8');
  check('ipc 不再把 GitHub 失败吞成 null（改成 ok/err 两态）', /ok: false, kind: kind, status: status/.test(ipcSrc));
  check('ipc 日志带分档与状态码', /kind=' \+ kind \+ \(status \? ' status='/.test(ipcSrc));
  check('ipc 返回 check_failed', /check_failed: checkFailed,/.test(ipcSrc));
  check('ipc 落盘 update_check_failed（跨重启可见）', /update_check_failed: checkFailed \?/.test(ipcSrc));
  check('点击「检查更新」有失败分支，不再一律「已是最新版本」',
    /else if \(u\.check_failed\)/.test(appSrc) && /已是最新版本/.test(appSrc));
  check('设置页渲染失败态', /s\.settings\.update_check_failed/.test(viewsSrc) && /checkFailText\(s\.settings\.update_check_failed\)/.test(viewsSrc));

  console.log('\n' + (failed ? 'FAIL' : 'OK') + '：' + (total - failed) + '/' + total + ' 通过' + (failed ? '，' + failed + ' 项失败' : ''));
  process.exit(failed ? 1 : 0);
})().catch((e) => { console.log('  [FAIL] 自检自身异常：' + (e && e.stack)); process.exit(1); });
