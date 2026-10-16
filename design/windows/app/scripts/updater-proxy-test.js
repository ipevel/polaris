'use strict';
/**
 * 更新下载「真的走代理」自证（纯 Node、只监听 127.0.0.1、不下载外网、不碰真安装目录）。
 *
 * 四类用例 + 一个解析用例：
 *   A 来源①正例  核心在跑 + mixed_port=本地代理端口 → 下载经代理（代理收到 CONNECT、转发字节），字节/sha 一致
 *   B 来源①退路  mixed_port 指向没人监听的端口 → 代理失败回退直连；代理处 0 个 CONNECT，字节/sha 一致
 *   C 来源②正例  核心不在 + 注入「系统代理=本地代理 stub」→ 请求经该代理，字节/sha 一致
 *   D 来源②反例  核心不在 + 系统代理读不到 → 直连能下成，代理处 0 个 CONNECT
 *   E（附加）    Windows ProxyServer 解析：host:port / http=/https= / socks-only / 空
 *
 * 源站是本地 https 自签证书（证书/私钥内嵌在下面，仅 127.0.0.1 有效）；
 * 下载目标形如 https://127.0.0.1:<port>/Polaris-portable-9.9.9.zip。
 * 代理是本地「CONNECT 直通」stub：记录每条 CONNECT 目标与两个方向转发的字节数。
 *
 * 跑法：node scripts/updater-proxy-test.js
 * 临时目录：优先 /data/dsh/home/tmp（容器口径），否则 os.tmpdir()；可被 POLARIS_TEST_TMP 覆盖。
 */
const fs = require('fs');
const os = require('os');
const path = require('path');
const http = require('http');
const https = require('https');
const net = require('net');
const crypto = require('crypto');

const PREFERRED_TMP = '/data/dsh/home/tmp';
const TMP_BASE = process.env.POLARIS_TEST_TMP || ((process.platform !== 'win32' && fs.existsSync(PREFERRED_TMP)) ? PREFERRED_TMP : os.tmpdir());
const TMP = path.join(TMP_BASE, 'polaris-updater-proxy-test');
const DATA = path.join(TMP, 'data');
process.env.POLARIS_DATA_DIR = DATA;   // paths.root() 会缓存，必须在 require updater 之前设好

const log = require('../electron/logger');
const updater = require('../electron/core/updater');
const sysproxy = require('../electron/net/sysproxy');

/* ---------------- 结果统计 ---------------- */
let failed = 0;
let passed = 0;
function ok(label, cond, extra) {
  if (cond) passed++; else failed++;
  console.log((cond ? '  \u2713 ' : '  \u2717 ') + label + (cond || extra === undefined ? '' : '\n      -> ' + extra));
}
function head(t) { console.log('\n' + t); }

/* ---------------- 捕获日志，证明「走了哪条路」写进了 log.info/warn ---------------- */
const logLines = [];
(function tapLog() {
  const levels = ['info', 'warn', 'error'];
  for (let i = 0; i < levels.length; i++) {
    const level = levels[i];
    const orig = log[level];
    log[level] = function () {
      const args = Array.prototype.slice.call(arguments);
      try { logLines.push(level + ' ' + args.map(function (x) { return typeof x === 'string' ? x : JSON.stringify(x); }).join(' ')); } catch (_) {}
      try { orig.apply(log, args); } catch (_) {}
    };
  }
})();

/* ---------------- 自签证书（仅 127.0.0.1；测试专用，别拿去别处用） ---------------- */
const CERT = '-----BEGIN CERTIFICATE-----\n' +
'MIIDJTCCAg2gAwIBAgIUR33ooUtg0ilMs3kb3FtAHPY+8IMwDQYJKoZIhvcNAQEL\n' +
'BQAwFDESMBAGA1UEAwwJbG9jYWxob3N0MB4XDTI2MTAxNjA3MzEzMFoXDTM2MTAx\n' +
'MzA3MzEzMFowFDESMBAGA1UEAwwJbG9jYWxob3N0MIIBIjANBgkqhkiG9w0BAQEF\n' +
'AAOCAQ8AMIIBCgKCAQEAxmN+Ud+y7sVgHQB2Pyy92jpfFv3+heZKq+aF5T/JYkav\n' +
'Rt5pyuUKGZTObiLWfN06ivSs+tpYeaFTJiRX4yWG41daWs4Zt7sUrk9Tj30ftM3B\n' +
'DVDL9e50CbAmsLPuzj91KpFNSIJpXEsNrlJ+ilK35wy5GuyLwQGy81qdvgS9Al3N\n' +
'3478PYpp4YKB1mgVjE3kdiNkKwul3DCiZEBJxfCq9jrnbvWjFMMAW6uy/1vAr2sO\n' +
'uYQMstZPWdOHZYQwfjo0ZDGJdicFhYLWM8cEaplui0AZCXBzr6FWgY8xihdoej7/\n' +
'+qidJ/LzGOrSBQDMj63vAVwEbZHaPCUG/rFx9+/MWQIDAQABo28wbTAdBgNVHQ4E\n' +
'FgQUp8NDEhaabHBJG0i1lwndFdwHWHwwHwYDVR0jBBgwFoAUp8NDEhaabHBJG0i1\n' +
'lwndFdwHWHwwDwYDVR0TAQH/BAUwAwEB/zAaBgNVHREEEzARhwR/AAABgglsb2Nh\n' +
'bGhvc3QwDQYJKoZIhvcNAQELBQADggEBAHOtLI3rdmWYhtr41YUZdFqNXyY3zoKv\n' +
'yAiBNZCvJi+B8SNswMuHaDt7VxF9M89P5p4BT5MzD1RJ28za2Prqumu9Hcy9GCVR\n' +
'MUhnEb4a+x6LzaH8mcv3SyHQRhKO50HIYksJgPB8PAgXS7nP01pfN4jeEBCY2G+5\n' +
'KZLsbMnELJjn+so/bnASVJqsqAxhnLaOwYwcH9tgsIKPmIua78DLFzEtI78UCbWt\n' +
'F7CcEGnWqDyAPPBet01YLr92yqf9T5OObcVzDN3vmSortFhWFVoIu8SDHpcs7a59\n' +
'seXNCO4NNgPRiOLDizrC32D6UgMkL2Oq4Ir39+U/56QOjqs+c1M/Dyc=\n' +
'-----END CERTIFICATE-----\n';
const KEY = '-----BEGIN PRIVATE KEY-----\n' +
'MIIEuwIBADANBgkqhkiG9w0BAQEFAASCBKUwggShAgEAAoIBAQDGY35R37LuxWAd\n' +
'AHY/LL3aOl8W/f6F5kqr5oXlP8liRq9G3mnK5QoZlM5uItZ83TqK9Kz62lh5oVMm\n' +
'JFfjJYbjV1pazhm3uxSuT1OPfR+0zcENUMv17nQJsCaws+7OP3UqkU1IgmlcSw2u\n' +
'Un6KUrfnDLka7IvBAbLzWp2+BL0CXc3fjvw9imnhgoHWaBWMTeR2I2QrC6XcMKJk\n' +
'QEnF8Kr2Oudu9aMUwwBbq7L/W8Cvaw65hAyy1k9Z04dlhDB+OjRkMYl2JwWFgtYz\n' +
'xwRqmW6LQBkJcHOvoVaBjzGKF2h6Pv/6qJ0n8vMY6tIFAMyPre8BXARtkdo8JQb+\n' +
'sXH378xZAgMBAAECgf8XifaTAFfavNCKoGNnW4erPQHLWJe6i3iQrOl2KftbDcf+\n' +
'QKWMzWBFaMKVjsykRBuOITnlCcM1q06SV/2Vlf5LOzIdVBb5Fdl+VSs0+0On0EH3\n' +
'sn6Az6o0/BGbJb5P+neMdXEWCWM28DDPXjplbysi3v+XkjmCzRDOjxHoWeeTQBiK\n' +
'5idM4U1pLxrwkU+OnBttsUTJqkIeCL6d1J8ocIkmntVvCLlLqNWebfgOomTy1YX8\n' +
'p3+tSZnLTSMXZX0pUU0uyzYQ6RcE85cKS3iLvc6NAHLzxvwibWlVgqU6FjA1gJoy\n' +
'9tytDTkeakRNc73A6tUXwnkjlKVh2D+zUi2KO1ECgYEA6RDL5RF0QX3jPnpw0bdf\n' +
'GwQOcOtw7CTvN2csFk4zEJjX1tdBisDu0YwyFYoHOMWmH2iVBIfSmZrSFd9lqDKX\n' +
'MPoYNUQcdV34kL0WWiPpL1X1bq5v6jLfvnPXUH5wQhpZNSOrAZe5owkcMGobjHQq\n' +
'IW+fKtBVJcBf9od7uzzf+LECgYEA2ekkd3enunrp7PuxuuEw06hyGmUI9HBQRvQE\n' +
'f2qU/a9MBIws+RrerP21+kcosVqs8NMfnRSV6bud8NkQKP5BWcxTHHzeDhyn5rfs\n' +
'nvoZyNxN09RanTGkwyabm59LKBjCgo7Khl1UgCO2T7Rw/yn7LVouEk3FXVsweGGN\n' +
'IDLeeCkCgYBn2l8wxJVMNZ9UENVC7fYIIMeak58u6spZorSnf9MLW0AacLK49H22\n' +
'jEUpCedr6c67giuc6DT002M38eLnjxuF7HtrCN2xgt0nCcrzAbt/p0sFNd+SuXav\n' +
'9AiGysCVBA6qOimSPRywRHf2LuH1tTGs0oNXXZwDhXR9cyO0bEzVEQKBgQDFK1ou\n' +
'qtreNcciAd+f65CFgz2zOkYu/sz0VmkXbTXgkNOkrb0nrLNdLbeR/P8J8rTWtwE8\n' +
'3VqEAZn4Sj+ACAbTnLh651T4yd72OOdmNYBNKWFWUSeoYjnlxjI4Tprvbe/ZuB/y\n' +
'EdKamzWjyu4PQ0odkCJtt2bkgNVLp2DNiJmZeQKBgGb5N9+eUZ35bfLk1hketXMM\n' +
'rLFRJWV04bIuCtFbeQjGA8G3AxZYZpABA06yIRf4SL7OyIIymSdhi/Y7K2YgN8pR\n' +
'P0v8eg5lHllDttggN4KB+b9cpwJX2mQDfhujaPYfuvgXXiVckEakvUaq/R5kmyBi\n' +
'8mh4VMv3kkiG2nW3f74l\n' +
'-----END PRIVATE KEY-----\n';

/* ---------------- 最小 zip 打包（stored，不压缩）——同 updater-timeout-test.js ---------------- */
const CRC_TABLE = (function () {
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
    lh.writeUInt16LE(20, 4);
    lh.writeUInt16LE(0x0800, 6);
    lh.writeUInt16LE(0, 8);
    lh.writeUInt16LE(0, 10); lh.writeUInt16LE(0, 12);
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
const sha256Of = (s) => crypto.createHash('sha256').update(Buffer.from(String(s), 'utf8')).digest('hex');
const FIXTURE_FILES = [
  { name: 'Polaris.exe', data: 'NEW-1.12.3' },
  { name: 'resources/app.asar', data: 'NEW-ASAR' },
  { name: 'resources/core/mihomo.exe', data: 'core-NEW' },
];
const FIXTURE_MANIFEST = FIXTURE_FILES.map((f) => sha256Of(f.data) + '  ' + f.name).join('\n') + '\n';
const GOOD_ZIP = buildZip(FIXTURE_FILES.concat([{ name: 'resources/polaris-files.sha256', data: FIXTURE_MANIFEST }]));
const ZIP_SHA = crypto.createHash('sha256').update(GOOD_ZIP).digest('hex');
const sha12 = (b) => crypto.createHash('sha256').update(b).digest('hex').slice(0, 12);

/* ---------------- 本地 CONNECT 直通代理（记录 CONNECT 与转发字节） ---------------- */
const proxyStats = { connects: [], bytes: 0 };
function resetProxy() { proxyStats.connects = []; proxyStats.bytes = 0; }
function startProxy() {
  return new Promise((resolve) => {
    const server = http.createServer();
    server.on('connect', (req, clientSocket, head) => {
      proxyStats.connects.push(req.url);
      const i = req.url.lastIndexOf(':');
      const host = req.url.slice(0, i);
      const port = Number(req.url.slice(i + 1)) || 443;
      const upstream = net.connect(port, host, () => {
        clientSocket.write('HTTP/1.1 200 Connection Established\r\n\r\n');
        if (head && head.length) { upstream.write(head); proxyStats.bytes += head.length; }
        clientSocket.pipe(upstream);
        upstream.pipe(clientSocket);
      });
      clientSocket.on('data', (c) => { proxyStats.bytes += c.length; });
      upstream.on('data', (c) => { proxyStats.bytes += c.length; });
      clientSocket.on('error', () => { try { upstream.destroy(); } catch (_) {} });
      upstream.on('error', () => { try { clientSocket.destroy(); } catch (_) {} });
    });
    server.on('clientError', () => {});
    server.listen(0, '127.0.0.1', () => resolve(server));
  });
}

/* ---------------- 本地 https 源站 ---------------- */
const sourceStats = { requests: 0 };
function startSource() {
  return new Promise((resolve) => {
    const server = https.createServer({ key: KEY, cert: CERT }, (req, res) => {
      sourceStats.requests += 1;
      res.writeHead(200, { 'Content-Type': 'application/zip', 'Content-Length': String(GOOD_ZIP.length) });
      res.end(GOOD_ZIP);
    });
    server.listen(0, '127.0.0.1', () => resolve(server));
  });
}

function freeClosedPort() {
  return new Promise((resolve) => {
    const s = net.createServer();
    s.listen(0, '127.0.0.1', () => { const p = s.address().port; s.close(() => resolve(p)); });
  });
}

/* ---------------- 用例 ---------------- */
const VER = '9.9.9';
const ZIP_PATH = updater.zipPath(VER);
async function runDownload(url) {
  updater.reset();
  return updater.download(url, VER, ZIP_SHA);
}
function readZip() { return fs.readFileSync(ZIP_PATH); }
/**
 * 只认「解压阶段」的红：本容器 tar 是 GNU tar（不认 zip）、又没有 powershell，
 * updater.extract() 必然失败 —— 这是环境限制，与「下载是否走代理」无关。
 * 下载本身（字节 / sha256 / S.download_via）由本脚本独立断言，不因此放水。
 */
function isExtractOnly(e) {
  const m = String((e && e.message) || '') + ' ' + String((updater.S && updater.S.error) || '');
  return /This does not look like a tar archive|spawn powershell ENOENT|Expand-Archive|解压/.test(m);
}

async function caseA(url, proxyPort, sourcePort) {
  head('用例 A · 来源①正例：核心在跑（mixed_port=代理端口）→ 下载必须经代理');
  resetProxy();
  const reqBefore = sourceStats.requests;
  updater.setCoreProxyProvider(() => proxyPort);
  updater.setSystemProxyProvider(() => null);
  logLines.length = 0;
  let threw = null;
  try { await runDownload(url); } catch (e) { threw = e; }
  const buf = fs.existsSync(ZIP_PATH) ? readZip() : null;
  const downloadOk = !threw || isExtractOnly(threw);
  if (threw && isExtractOnly(threw)) console.log('      [ENV] 本容器 tar 是 GNU tar、没有 powershell，解压阶段照旧红（与改前一致）；下载本身已成功，字节/sha 见下。');
  ok('下载阶段成功（解压另见 [ENV] 说明）', downloadOk, threw && threw.message);
  ok('代理收到 CONNECT = ' + JSON.stringify(proxyStats.connects), proxyStats.connects.length >= 1 && proxyStats.connects[0] === ('127.0.0.1:' + sourcePort));
  ok('代理转发字节 = ' + proxyStats.bytes + '（应 > 0 且 >= 包长 ' + GOOD_ZIP.length + '）', proxyStats.bytes > 0 && proxyStats.bytes >= GOOD_ZIP.length);
  ok('源站确实被请求（+' + (sourceStats.requests - reqBefore) + ' 次）', sourceStats.requests > reqBefore);
  ok('下载文件字节数 = ' + (buf ? buf.length : 'n/a') + '（期望 ' + GOOD_ZIP.length + '）', !!buf && buf.length === GOOD_ZIP.length);
  ok('sha256 前 12 位 = ' + (buf ? sha12(buf) : 'n/a'), !!buf && sha12(buf) === ZIP_SHA.slice(0, 12));
  ok('本次实际走的路 S.download_via = ' + updater.S.download_via, updater.S.download_via === 'core', updater.S.download_via);
  ok('日志写清「本次走代理」', logLines.some((l) => l.indexOf('更新下载：本次走代理') >= 0));
}

async function caseB(url, deadPort) {
  head('用例 B · 来源①退路：mixed_port 指向没人监听的端口 → 代理失败回退直连；代理处 0 CONNECT');
  resetProxy();
  updater.setCoreProxyProvider(() => deadPort);
  updater.setSystemProxyProvider(() => null);
  logLines.length = 0;
  let threw = null;
  try { await runDownload(url); } catch (e) { threw = e; }
  const buf = fs.existsSync(ZIP_PATH) ? readZip() : null;
  const downloadOk = !threw || isExtractOnly(threw);
  if (threw && isExtractOnly(threw)) console.log('      [ENV] 解压阶段照旧红（与改前一致）；下载本身已成功，字节/sha 见下。');
  ok('直连下载阶段成功', downloadOk, threw && threw.message);
  ok('代理处收到 CONNECT = ' + JSON.stringify(proxyStats.connects) + '（期望 []）', proxyStats.connects.length === 0);
  ok('字节数与 sha256 一致（' + (buf ? buf.length : 'n/a') + ' / ' + (buf ? sha12(buf) : 'n/a') + '）', !!buf && buf.length === GOOD_ZIP.length && sha12(buf) === ZIP_SHA.slice(0, 12));
  ok('本次实际走的路 S.download_via = ' + updater.S.download_via, updater.S.download_via === 'direct', updater.S.download_via);
  ok('日志写了「走代理失败…回退直连」', logLines.some((l) => l.indexOf('走代理失败') >= 0 && l.indexOf('回退直连') >= 0));
  ok('日志写了「本次走直连」', logLines.some((l) => l.indexOf('更新下载：本次走直连') >= 0));
}

async function caseC(url, proxyPort, sourcePort) {
  head('用例 C · 来源②正例：核心不在 + 系统代理=本地 stub → 请求经该代理');
  resetProxy();
  const reqBefore = sourceStats.requests;
  updater.setCoreProxyProvider(() => 0);
  updater.setSystemProxyProvider(() => ({ host: '127.0.0.1', port: proxyPort }));
  logLines.length = 0;
  let threw = null;
  try { await runDownload(url); } catch (e) { threw = e; }
  const buf = fs.existsSync(ZIP_PATH) ? readZip() : null;
  const downloadOk = !threw || isExtractOnly(threw);
  if (threw && isExtractOnly(threw)) console.log('      [ENV] 本容器 tar 是 GNU tar、没有 powershell，解压阶段照旧红（与改前一致）；下载本身已成功，字节/sha 见下。');
  ok('下载阶段成功（解压另见 [ENV] 说明）', downloadOk, threw && threw.message);
  ok('代理收到 CONNECT = ' + JSON.stringify(proxyStats.connects), proxyStats.connects.length >= 1 && proxyStats.connects[0] === ('127.0.0.1:' + sourcePort));
  ok('代理转发字节 = ' + proxyStats.bytes, proxyStats.bytes >= GOOD_ZIP.length);
  ok('源站确实被请求（+' + (sourceStats.requests - reqBefore) + ' 次）', sourceStats.requests > reqBefore);
  ok('字节数与 sha256 一致（' + (buf ? buf.length : 'n/a') + ' / ' + (buf ? sha12(buf) : 'n/a') + '）', !!buf && buf.length === GOOD_ZIP.length && sha12(buf) === ZIP_SHA.slice(0, 12));
  ok('本次实际走的路 S.download_via = ' + updater.S.download_via, updater.S.download_via === 'system', updater.S.download_via);
  ok('日志写清「本次走代理」', logLines.some((l) => l.indexOf('更新下载：本次走代理') >= 0));
}

async function caseD(url) {
  head('用例 D · 来源②反例：核心不在 + 系统代理读不到 → 直连能下成');
  resetProxy();
  updater.setCoreProxyProvider(() => 0);
  updater.setSystemProxyProvider(() => null);
  logLines.length = 0;
  let threw = null;
  try { await runDownload(url); } catch (e) { threw = e; }
  const buf = fs.existsSync(ZIP_PATH) ? readZip() : null;
  const downloadOk = !threw || isExtractOnly(threw);
  if (threw && isExtractOnly(threw)) console.log('      [ENV] 解压阶段照旧红（与改前一致）；下载本身已成功，字节/sha 见下。');
  ok('直连下载阶段成功', downloadOk, threw && threw.message);
  ok('代理处收到 CONNECT = ' + JSON.stringify(proxyStats.connects) + '（期望 []）', proxyStats.connects.length === 0);
  ok('字节数与 sha256 一致（' + (buf ? buf.length : 'n/a') + ' / ' + (buf ? sha12(buf) : 'n/a') + '）', !!buf && buf.length === GOOD_ZIP.length && sha12(buf) === ZIP_SHA.slice(0, 12));
  ok('本次实际走的路 S.download_via = ' + updater.S.download_via, updater.S.download_via === 'direct', updater.S.download_via);
}

async function caseF(proxyDead, directDead) {
  head('用例 F · 失败可见（#48 口径）：代理与直连都失败 → 分档文案 + 落盘 + 日志，不许假绿');
  updater.setCoreProxyProvider(() => proxyDead);
  updater.setSystemProxyProvider(() => null);
  logLines.length = 0;
  let threw = null;
  try { await runDownload('https://127.0.0.1:' + directDead + '/Polaris-portable-' + VER + '.zip'); } catch (e) { threw = e; }
  const msg = String((threw && threw.message) || '');
  ok('确实失败了（phase=error，没有假绿）', !!threw && updater.S.phase === 'error', threw && threw.message);
  ok('分档文案同时说清「直连失败」与「代理也失败」', msg.indexOf('直连失败') >= 0 && msg.indexOf('走代理') >= 0, msg);
  const derr = updater.readDownloadError();
  ok('落盘 download-error.json 带 reason 与 proxy_error', !!derr && !!derr.reason && !!derr.proxy_error, JSON.stringify(derr));
  ok('info().last_download_error 跨调用可读', !!updater.info().last_download_error);
  ok('日志写了「更新下载失败」', logLines.some((l) => l.indexOf('更新下载失败') >= 0));
}

function caseE() {
  head('用例 E · 附加：Windows ProxyServer 解析（sysproxy.parseProxyServer）');
  const p1 = sysproxy.parseProxyServer('127.0.0.1:7890');
  ok('host:port → 127.0.0.1:7890', !!p1 && p1.host === '127.0.0.1' && p1.port === 7890, JSON.stringify(p1));
  const p2 = sysproxy.parseProxyServer('http=10.0.0.1:8080;https=10.0.0.1:8443');
  ok('http=/https= → 优先 https(8443)', !!p2 && p2.port === 8443 && p2.scheme === 'https', JSON.stringify(p2));
  const p3 = sysproxy.parseProxyServer('socks=127.0.0.1:1080');
  ok('只有 socks → null（不假装能跑 HTTP 隧道）', p3 === null, JSON.stringify(p3));
  ok('空串 → null', sysproxy.parseProxyServer('') === null);
  ok('非 Windows 读系统代理 → null', process.platform === 'win32' ? true : sysproxy.readSystemProxy() === null);
}

/* ---------------- 入口 ---------------- */
async function main() {
  console.log('更新下载「真的走代理」自证（本地 CONNECT 代理 + 本地 https 源站，不打外网）');
  console.log('临时目录：' + TMP);
  fs.rmSync(TMP, { recursive: true, force: true });
  fs.mkdirSync(DATA, { recursive: true });
  // 直连路径（B/D）用全局 agent 信任自签证书；代理路径用注入的 tlsOptions。
  https.globalAgent.options.ca = CERT;
  updater.setProxyTlsOptions({ ca: CERT });

  const proxyServer = await startProxy();
  const proxyPort = proxyServer.address().port;
  const sourceServer = await startSource();
  const sourcePort = sourceServer.address().port;
  const deadPort = await freeClosedPort();
  const url = 'https://127.0.0.1:' + sourcePort + '/Polaris-portable-' + VER + '.zip';
  console.log('代理 stub = 127.0.0.1:' + proxyPort + '｜源站 = ' + url + '｜没人监听的端口 = ' + deadPort);

  try {
    await caseA(url, proxyPort, sourcePort);
    await caseB(url, deadPort);
    await caseC(url, proxyPort, sourcePort);
    await caseD(url);
    await caseF(deadPort, await freeClosedPort());
    caseE();
  } finally {
    try { proxyServer.close(); } catch (_) {}
    try { proxyServer.closeAllConnections && proxyServer.closeAllConnections(); } catch (_) {}
    try { sourceServer.close(); } catch (_) {}
    try { sourceServer.closeAllConnections && sourceServer.closeAllConnections(); } catch (_) {}
    if (!process.env.POLARIS_KEEP_TESTDATA) { try { fs.rmSync(TMP, { recursive: true, force: true }); } catch (_) {} }
  }

  console.log('\n通过 ' + passed + ' 项，失败 ' + failed + ' 项');
  console.log(failed === 0 ? '全部通过（A/B/C/D + 失败可见 + 解析）' : '失败 ' + failed + ' 项');
  process.exit(failed === 0 ? 0 : 1);
}
main().catch((e) => { console.error('自检本身崩了：', e); process.exit(1); });
