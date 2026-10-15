'use strict';
/**
 * 应用内更新「下载超时」自检（纯 Node，不需要 Electron、不需要外网、不碰真安装目录）。
 *
 * 为什么单独一条线：`updater-self-test.js` 覆盖的是**自我替换 helper**，完全没碰
 * `download()/downloadTo()/fetchStream()`。而线上日志里翻车最狠的恰好是这一段：
 *
 *   23:59:40.941 开始下载更新
 *   23:59:43.772 command download_update failed: 正在更新中，请稍候
 *   23:59:45.964 更新下载失败：下载失败：下载超时（60s 无数据）   ← 距开始只有 5.02 秒
 *
 * 旧代码只写了一句 `req.setTimeout(60000, ...)`，而 `req.setTimeout()` 在建连期间
 * （`sock.connecting === true`）会把超时**推迟到 'connect' 之后**才设到 socket 上，
 * 于是建连阶段 socket 上带着的是 agent 自己的超时 —— 本机 Node 24.19 / Electron 44.7
 * 实测 `https.globalAgent.options.timeout` 都是 **5000ms**。把 timeout 作为请求选项
 * （`{ timeout }`）传才没有这个空档。这是实打实的隐患，新代码修的就是它。
 *
 * 诚实交代（红/绿对照的结论）：**那三次 5.02 秒没能复现**。把旧代码单独拿出来，在
 * 直连、CONNECT 隧道、连接池复用三条路径上跑同一个「发完响应头就不吭声」的服务器，
 * 旧代码都是 60 秒才失败（`下载中断：aborted`），不是 5 秒。所以本脚本**不声称**
 * 「旧代码第 5 秒必炸」——它只验证新代码的行为是对的、文案是准的、失败后能重试。
 *
 * 这个脚本在 127.0.0.1 上起真 TCP/HTTP 服务器来构造各种卡住的情形（不改 dns/agent
 * 之类内部状态），再用 `updater.TIMEOUTS` 把三种超时压到毫秒级，半分钟跑完全部情形。
 *
 * 跑法：`node scripts\updater-timeout-test.js`（或 `npm run test:updater-timeout`）
 *       `node scripts\updater-timeout-test.js --cases=1` 只跑第 1 例
 *
 * 绝不做的事：不碰真安装目录、不下载外网、不起 cmd/powershell、不改系统任何东西。
 * 只监听 127.0.0.1 的临时端口，只往 %TEMP% 下的临时目录写。
 */

const fs = require('fs');
const path = require('path');
const os = require('os');
const http = require('http');
const net = require('net');

/* ---------------- 临时目录：先设环境变量，再 require updater ---------------- */
const TMP = path.join(os.tmpdir(), 'polaris-updater-timeout-test');
const DATA = path.join(TMP, 'data');
process.env.POLARIS_DATA_DIR = DATA; // paths.root() 会缓存它，必须在 require 之前设好

const updater = require('../electron/core/updater');

const UPD = path.join(DATA, 'data', 'update');
const STAGE = path.join(UPD, 'staging');

/* ---------------- 结果统计 ---------------- */
let failed = 0;
let passed = 0;
function ok(label, cond, extra) {
  if (cond) passed++; else failed++;
  console.log((cond ? '  \u2713 ' : '  \u2717 ') + label + (cond || !extra ? '' : `\n      → ${extra}`));
}

/* ---------------- 最小 zip 打包（stored，不压缩） ---------------- */
const CRC_TABLE = (() => {
  const t = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c;
  }
  return t;
})();
function crc32(buf) {
  let c = -1;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ -1) >>> 0;
}

/** 只写「本地文件头 + 数据 + 中央目录 + EOCD」，够 tar/Expand-Archive 认就行 */
function buildZip(entries) {
  const locals = [];
  const centrals = [];
  let offset = 0;
  for (const e of entries) {
    const name = Buffer.from(e.name, 'utf8');
    const data = Buffer.isBuffer(e.data) ? e.data : Buffer.from(String(e.data), 'utf8');
    const crc = crc32(data);
    const lh = Buffer.alloc(30);
    lh.writeUInt32LE(0x04034b50, 0);
    lh.writeUInt16LE(20, 4);      // version needed
    lh.writeUInt16LE(0x0800, 6);  // flags: UTF-8 名字
    lh.writeUInt16LE(0, 8);       // stored
    lh.writeUInt16LE(0, 10); lh.writeUInt16LE(0, 12); // time/date
    lh.writeUInt32LE(crc, 14);
    lh.writeUInt32LE(data.length, 18);
    lh.writeUInt32LE(data.length, 22);
    lh.writeUInt16LE(name.length, 26);
    lh.writeUInt16LE(0, 28);
    locals.push(lh, name, data);

    const ch = Buffer.alloc(46);
    ch.writeUInt32LE(0x02014b50, 0);
    ch.writeUInt16LE(20, 4); ch.writeUInt16LE(20, 6);
    ch.writeUInt16LE(0x0800, 8);
    ch.writeUInt16LE(0, 10);
    ch.writeUInt16LE(0, 12); ch.writeUInt16LE(0, 14);
    ch.writeUInt32LE(crc, 16);
    ch.writeUInt32LE(data.length, 20);
    ch.writeUInt32LE(data.length, 24);
    ch.writeUInt16LE(name.length, 28);
    ch.writeUInt16LE(0, 30); ch.writeUInt16LE(0, 32);
    ch.writeUInt16LE(0, 34); ch.writeUInt16LE(0, 36);
    ch.writeUInt32LE(0, 38);
    ch.writeUInt32LE(offset, 42);
    centrals.push(ch, name);

    offset += 30 + name.length + data.length;
  }
  const centralBuf = Buffer.concat(centrals);
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0);
  eocd.writeUInt16LE(0, 4); eocd.writeUInt16LE(0, 6);
  eocd.writeUInt16LE(entries.length, 8);
  eocd.writeUInt16LE(entries.length, 10);
  eocd.writeUInt32LE(centralBuf.length, 12);
  eocd.writeUInt32LE(offset, 16);
  eocd.writeUInt16LE(0, 20);
  return Buffer.concat([...locals, centralBuf, eocd]);
}

// a 笔的包内清单闸门：夹具包必须带**合法清单**（缺清单 = 拒绝安装，闸门不放宽）。
// 清单格式与 scripts/package-manifest.js 一致：<64hex>␣␣<相对路径>，清单自身不列入。
const crypto = require('crypto');
const sha256Of = (s) => crypto.createHash('sha256').update(Buffer.from(String(s), 'utf8')).digest('hex');
const FIXTURE_FILES = [
  { name: 'Polaris.exe', data: 'NEW-1.11.3' },
  { name: 'resources/app.asar', data: 'NEW-ASAR' },
  { name: 'resources/core/mihomo.exe', data: 'core-NEW' },
];
const FIXTURE_MANIFEST = FIXTURE_FILES.map((f) => sha256Of(f.data) + '  ' + f.name).join('\n') + '\n';
const GOOD_ZIP = buildZip(FIXTURE_FILES.concat([
  { name: 'resources/polaris-files.sha256', data: FIXTURE_MANIFEST },
]));

/* ---------------- 临时 HTTP 服务器（只监听 127.0.0.1） ---------------- */
let server = null;
let port = 0;
let handler = null;

function startServer() {
  return new Promise((resolve) => {
    server = http.createServer((req, res) => {
      try { handler(req, res); } catch (_) { try { res.destroy(); } catch (__) {} }
    });
    server.on('clientError', () => {});
    server.listen(0, '127.0.0.1', () => {
      port = server.address().port;
      resolve();
    });
  });
}
const url = (p) => `http://127.0.0.1:${port}${p}`;

/** 正常返回一个 zip */
function serveZip(res, body) {
  const buf = body || GOOD_ZIP;
  res.writeHead(200, { 'Content-Type': 'application/zip', 'Content-Length': String(buf.length) });
  res.end(buf);
}
/** 收下请求但先不吭声，等 delayMs 之后再发完整 zip（模拟「建连很慢」） */
function serveStallThenZip(res, delayMs) {
  const t = setTimeout(() => { try { serveZip(res); } catch (_) {} }, delayMs);
  res.on('close', () => clearTimeout(t));
}
/** 只发响应头，body 一直不发（模拟「连上了但没数据」） */
function serveStall(res) {
  res.writeHead(200, { 'Content-Type': 'application/zip', 'Content-Length': '1048576' });
  res.flushHeaders();
}
/** 每 intervalMs 挤 1 字节（模拟「看起来在下、其实永远下不完」） */
function serveDrip(res, intervalMs) {
  res.writeHead(200, { 'Content-Type': 'application/zip', 'Content-Length': '1048576' });
  res.flushHeaders();
  const t = setInterval(() => { try { res.write('x'); } catch (_) { clearInterval(t); } }, intervalMs);
  res.on('close', () => clearInterval(t));
}

/* ---------------- 哑巴 TCP 服务器（真正的「连得上但不回应」） ----------------
 * 为什么不用「拖慢 DNS」：本机实测字面 IP（127.0.0.1）不等 lookup 回调，
 * 打了 dns.lookup 补丁请求照样 14ms 就完成，复现不了建连窗口。
 * 这里用最朴素的办法 —— 收下 TCP 连接后什么都不做：
 *   - 旧代码：socket 上带着 agent 自己的 5000ms（`req.setTimeout` 在建连期间不生效）
 *   - 新代码：请求选项里的 timeout 才是 60000ms，且 connect 看门狗先按自己的时长报错
 */
let silent = null;
let silentPort = 0;
function startSilentServer() {
  return new Promise((resolve) => {
    silent = net.createServer((sock) => {
      sock.on('error', () => {});
      // 收下连接，一个字节都不回
    });
    silent.on('error', () => {});
    silent.listen(0, '127.0.0.1', () => {
      silentPort = silent.address().port;
      resolve();
    });
  });
}
const silentUrl = () => `http://127.0.0.1:${silentPort}/silent`;

/* ---------------- 用例 ---------------- */
const ALL = [1, 2, 3, 4, 5, 6];

async function case1() {
  console.log('\n用例 1 · 服务端拖 6 秒才应答 → 应当正常下完（旧的 req.setTimeout 在建连窗口里不生效）');
  const dest = path.join(TMP, 'case1.zip');
  handler = (req, res) => serveStallThenZip(res, 6000); // 6 秒 > globalAgent 的 5000ms
  const t0 = Date.now();
  try {
    // 第三个参数在旧代码里是必填的，所以显式传一个空回调 —— 新旧代码都能跑，
    // 这样红/绿对照比的是超时行为本身，不是「谁把 onProgress 做成可选」。
    const r = await updater.downloadTo(url('/slow'), dest, () => {});
    const ms = Date.now() - t0;
    ok(`建连等了 ${ms}ms 仍下载成功（超时选项从建连起就生效）`, true);
    ok(`字节数正确（${r.size}）`, r.size === GOOD_ZIP.length, `期望 ${GOOD_ZIP.length}`);
    ok('落盘内容与源一致', fs.readFileSync(dest).equals(GOOD_ZIP));
    ok('耗时确实超过了 5 秒（证明踩到了建连窗口）', ms > 5000, `实际 ${ms}ms`);
  } catch (e) {
    ok('建连等了 6 秒仍下载成功', false, `实际报错：${e && e.message}`);
  }
}

async function case2() {
  console.log('\n用例 2 · 服务端收下连接后彻底不回应 → 文案要写清「连接超时」和真实时长');
  const dest = path.join(TMP, 'case2.zip');
  const oldConnect = updater.TIMEOUTS.connect;
  const oldIdle = updater.TIMEOUTS.idle;
  updater.TIMEOUTS.connect = 1200;
  updater.TIMEOUTS.idle = 60000; // 让建连看门狗先响，文案才是「连接超时」
  const t0 = Date.now();
  try {
    await updater.downloadTo(silentUrl(), dest);
    ok('应当抛错', false, '居然成功了');
  } catch (e) {
    const m = String((e && e.message) || '');
    const ms = Date.now() - t0;
    ok(`报的是连接超时：${m}`, m.includes('连接超时'), m);
    ok('时长写的是 connect 上限（1.2 秒），不是 60 秒', m.includes('1.2 秒'), m);
    ok('带上了主机名', m.includes('127.0.0.1'), m);
    ok(`在 1.2 秒左右就报，没拖到 60 秒（${ms}ms）`, ms < 2600, `实际 ${ms}ms`);
    ok('dest 没有被当成下完（不存在或 0 字节）', !fs.existsSync(dest) || fs.statSync(dest).size === 0);
  } finally {
    updater.TIMEOUTS.connect = oldConnect;
    updater.TIMEOUTS.idle = oldIdle;
  }
}

async function case3() {
  console.log('\n用例 3 · 连上了但一直没数据 → 文案要写「没有收到数据」');
  const dest = path.join(TMP, 'case3.zip');
  const oldIdle = updater.TIMEOUTS.idle;
  handler = (req, res) => serveStall(res);
  updater.TIMEOUTS.idle = 800;
  try {
    await updater.downloadTo(url('/stall'), dest);
    ok('应当抛错', false, '居然成功了');
  } catch (e) {
    const m = String((e && e.message) || '');
    ok(`报的是空闲超时：${m}`, m.includes('下载超时'), m);
    ok('时长是 0.8 秒', m.includes('0.8 秒'), m);
    ok('说明是「没收到数据」而不是「连不上」', m.includes('没有收到数据'), m);
    ok('dest 没有变成半截文件', !fs.existsSync(dest) || fs.statSync(dest).size === 0);
  } finally {
    updater.TIMEOUTS.idle = oldIdle;
  }
}

async function case4() {
  console.log('\n用例 4 · 每 250ms 挤 1 字节（饿不死空闲计时器）→ 整体硬上限要拦住');
  const dest = path.join(TMP, 'case4.zip');
  const oldIdle = updater.TIMEOUTS.idle;
  const oldTotal = updater.TIMEOUTS.total;
  handler = (req, res) => serveDrip(res, 250);
  updater.TIMEOUTS.idle = 3000;   // 每 250ms 都有数据，空闲计时器永远不响
  updater.TIMEOUTS.total = 1500;  // 只有整体硬上限能救
  try {
    await updater.downloadTo(url('/drip'), dest);
    ok('应当抛错', false, '居然成功了');
  } catch (e) {
    const m = String((e && e.message) || '');
    ok(`报的是整体超时：${m}`, m.includes('还没下完'), m);
    ok('时长是 1.5 秒', m.includes('1.5 秒'), m);
  } finally {
    updater.TIMEOUTS.idle = oldIdle;
    updater.TIMEOUTS.total = oldTotal;
  }
}

async function case5() {
  console.log('\n用例 5 · 下载超时后状态必须回 error，且下一次能立刻重试（旧代码会被「正在更新中」挡住）');
  const oldIdle = updater.TIMEOUTS.idle;
  const oldTotal = updater.TIMEOUTS.total;
  updater.TIMEOUTS.idle = 700;
  updater.TIMEOUTS.total = 60000;
  try {
    updater.reset();
    handler = (req, res) => serveStall(res);
    let firstErr = null;
    try {
      await updater.download(url('/stall'), '1.11.3');
    } catch (e) { firstErr = e; }
    ok('第一次下载确实失败了', !!firstErr, firstErr && firstErr.message);
    ok(`失败后 phase 回到 error（实际 ${updater.S.phase}）`, updater.S.phase === 'error', updater.S.phase);
    ok('error 字段里有给用户看的文案', !!updater.S.error, updater.S.error);

    // 换成正常服务器，再下第二次 —— 这一步就是线上被挡住的地方
    handler = (req, res) => serveZip(res);
    let secondErr = null;
    let secondInfo = null;
    try {
      secondInfo = await updater.download(url('/ok'), '1.11.3');
    } catch (e) { secondErr = e; }
    ok('第二次不再报「正在更新中，请稍候」', !(secondErr && /正在更新中/.test(secondErr.message)),
      secondErr && secondErr.message);
    ok('第二次下载 + 解压走通，phase=staged', updater.S.phase === 'staged',
      `${updater.S.phase}${secondErr ? ' / ' + secondErr.message : ''}`);
    ok('staged 里拿到了 Polaris.exe', fs.existsSync(path.join(STAGE, 'Polaris.exe')));
    ok('info() 里能看出可安装', !!secondInfo && secondInfo.phase === 'staged');
  } finally {
    updater.TIMEOUTS.idle = oldIdle;
    updater.TIMEOUTS.total = oldTotal;
  }
}

async function case6() {
  console.log('\n用例 6 · 正常路径：下载 → PK 校验 → 解压 → staged（确保改动没把好路走坏）');
  try {
    updater.reset();
    handler = (req, res) => serveZip(res);
    const info = await updater.download(url('/ok'), '1.11.3');
    ok('phase=staged', updater.S.phase === 'staged', updater.S.phase);
    ok('percent=100', info.percent === 100, String(info.percent));
    ok('Polaris.exe 已解压到位', fs.existsSync(path.join(STAGE, 'Polaris.exe')));
    ok('resources/core/mihomo.exe 也在', fs.existsSync(path.join(STAGE, 'resources', 'core', 'mihomo.exe')));
    ok('zip 头校验通过（下载物是 zip）', updater.isZipFile(path.join(UPD, 'Polaris-1.11.3.zip')));
  } catch (e) {
    ok('正常路径走通', false, e && e.message);
  }
}

/* ---------------- 时间戳用例（logger.js） ---------------- */
function caseStamp() {
  console.log('\n用例 7 · 日志时间戳是本地时间且带偏移量（旧代码是 UTC，差 8 小时）');
  const log = require('../electron/logger');
  const d = new Date(2026, 9, 13, 23, 59, 18, 772); // 本地 2026-10-13 23:59:18.772
  const s = log.stamp(d);
  const offMin = -d.getTimezoneOffset();
  const sign = offMin < 0 ? '-' : '+';
  const hh = String(Math.floor(Math.abs(offMin) / 60)).padStart(2, '0');
  const mm = String(Math.abs(offMin) % 60).padStart(2, '0');
  ok(`格式是本地时间 + 偏移量：${s}`, s === `2026-10-13 23:59:18.772${sign}${hh}:${mm}`, s);
  ok('不再出现 UTC 的 15:59（旧代码的 toISOString 会写成 15:59:18.772）', !s.includes('15:59'), s);
  const now = log.stamp();
  ok('无参调用也能用', /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}[+-]\d{2}:\d{2}$/.test(now), now);
}

/* ---------------- 入口 ---------------- */
async function main() {
  const arg = process.argv.find((a) => a.startsWith('--cases='));
  const wanted = arg ? arg.split('=')[1].split(',').map(Number) : ALL;
  console.log('应用内更新「下载超时」自检');
  console.log('临时目录：' + TMP);
  const T = updater.TIMEOUTS || {};
  console.log('默认超时：connect=' + T.connect
    + 'ms idle=' + T.idle + 'ms total=' + T.total + 'ms'
    + (updater.TIMEOUTS ? '' : '（旧代码没有 TIMEOUTS，说明这是红/绿对照的红跑）'));

  fs.rmSync(TMP, { recursive: true, force: true });
  fs.mkdirSync(DATA, { recursive: true });
  await startServer();
  await startSilentServer();

  try {
    for (const n of wanted) {
      if (n === 1) await case1();
      else if (n === 2) await case2();
      else if (n === 3) await case3();
      else if (n === 4) await case4();
      else if (n === 5) await case5();
      else if (n === 6) await case6();
      else if (n === 7) caseStamp();
    }
  } finally {
    try { server.close(); } catch (_) {}
    try { server.closeAllConnections && server.closeAllConnections(); } catch (_) {}
    try { silent.close(); } catch (_) {}
    try { silent.closeAllConnections && silent.closeAllConnections(); } catch (_) {}
    if (!process.env.POLARIS_KEEP_TESTDATA) {
      try { fs.rmSync(TMP, { recursive: true, force: true }); } catch (_) {}
    }
  }

  console.log(`\n通过 ${passed} 项，失败 ${failed} 项`);
  console.log(failed === 0 ? '全部通过' : `失败 ${failed} 项`);
  process.exit(failed === 0 ? 0 : 1);
}

main().catch((e) => { console.error('自检本身崩了：', e); process.exit(1); });
