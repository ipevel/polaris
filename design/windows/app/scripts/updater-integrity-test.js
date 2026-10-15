'use strict';
/**
 * 更新链完整性自检（PR-A：包完整性校验）。
 *   跑法：node scripts/updater-integrity-test.js
 *
 * 覆盖三件事，全部离线可跑：
 *   1) 发布侧 SHA256SUMS.txt 的解析与「下载后核对」——相符放行 / 不符 fail-closed 且删包 /
 *      拿不到摘要只 warn 不拦（镜像与自建 CDN 没有这份文件，不能因此装不了更新）；
 *   2) 包内清单的**单文件定点校验**（启动前核内核用）——相符绿 / 被改红 / 清单缺失或没有该条跳过；
 *   3) 接线断言（源码级）：光有函数不算数，必须真的被接上（这三处以前都没有）。
 *
 * 口径提醒：这一层防的是**损坏 / 搬运 / CDN 截断**，不是信任锚（摘要与包同源同账号）。
 */
const fs = require('fs');
const os = require('os');
const path = require('path');
const http = require('http');
const crypto = require('crypto');

const BASE = fs.mkdtempSync(path.join(os.tmpdir(), 'polaris-integrity-'));
process.env.POLARIS_DATA_DIR = BASE;

const U = require('../electron/core/updater');
const R = require('../electron/core/release');
const PM = require('./package-manifest');

let total = 0; let failed = 0;
function check(name, cond, detail) {
  total += 1; if (!cond) failed += 1;
  console.log((cond ? '  [ OK ] ' : '  [FAIL] ') + name + (!cond && detail ? '  ' + detail : ''));
}
const sha = (b) => crypto.createHash('sha256').update(b).digest('hex');

/* ---------------- 1. 发布侧摘要解析 ---------------- */
console.log('-- 1. 发布侧摘要解析（SHA256SUMS.txt） --');
const A = 'a'.repeat(64); const B = 'b'.repeat(64);
const sums = R.parseSums(A + '  Polaris-portable-1.12.1.zip\r\n' + B + ' *Polaris-portable-1.12.0.zip\r\n乱码行\r\n');
check('解析出两条（忽略乱码行）', sums.size === 2, 'size=' + sums.size);
check('zip 名对得上', sums.get('Polaris-portable-1.12.1.zip') === A);
check('二进制标记 * 也能认', sums.get('Polaris-portable-1.12.0.zip') === B);
check('空文本 = 空表', R.parseSums('').size === 0);
check('同目录摘要地址可推出',
  R.sumsUrlFor('https://github.com/ipevel/polaris/releases/download/v1.12.1/Polaris-portable-1.12.1.zip') ===
  'https://github.com/ipevel/polaris/releases/download/v1.12.1/SHA256SUMS.txt');
check('地址不合法 → 空串（不抛）', R.sumsUrlFor('not a url') === '');

/* ---------------- 2. 下载后核对 ---------------- */
console.log('-- 2. 下载后核对（fail-closed） --');
const zipFile = path.join(BASE, 'fake.zip');
fs.writeFileSync(zipFile, Buffer.from('PK\x03\x04payload'));
const good = sha(fs.readFileSync(zipFile));
check('相符 → 放行', U.verifyDownloadedFile(zipFile, good).ok === true && U.verifyDownloadedFile(zipFile, good).skipped !== true);
check('大写摘要也能对', U.verifyDownloadedFile(zipFile, good.toUpperCase()).ok === true);
const bad = U.verifyDownloadedFile(zipFile, 'c'.repeat(64));
check('不符 → 红（fail-closed）', bad.ok === false && /摘要不符/.test(bad.reason), bad.reason);
check('没有摘要 → 跳过、不拦', U.verifyDownloadedFile(zipFile, '').ok === true && U.verifyDownloadedFile(zipFile, '').skipped === true);
check('摘要格式不对 → 跳过、不拦', U.verifyDownloadedFile(zipFile, 'not-a-hash').skipped === true);

/* ---------------- 3. 包内清单定点校验 ---------------- */
console.log('-- 3. 包内清单定点校验（启动前核内核） --');
const CORE_REL = 'resources/core/mihomo.exe';
const PKG = path.join(BASE, 'pkg');
fs.mkdirSync(path.join(PKG, 'resources', 'core'), { recursive: true });
fs.writeFileSync(path.join(PKG, 'Polaris.exe'), 'exe');
fs.writeFileSync(path.join(PKG, 'resources', 'app.asar'), 'asar');
fs.writeFileSync(path.join(PKG, CORE_REL), 'core-bytes');
PM.write(PKG);
const g1 = U.verifyManifestEntry(PKG, CORE_REL);
check('内核与清单相符 → 绿', g1.ok === true && g1.skipped !== true, JSON.stringify(g1));
fs.appendFileSync(path.join(PKG, CORE_REL), 'X');
const g2 = U.verifyManifestEntry(PKG, CORE_REL);
check('内核被改 → 红（fail-closed）', g2.ok === false, JSON.stringify(g2));
fs.writeFileSync(path.join(PKG, CORE_REL), 'core-bytes');
check('改回来 → 又绿', U.verifyManifestEntry(PKG, CORE_REL).ok === true);

const PKG2 = path.join(BASE, 'pkg-nomanifest');
fs.mkdirSync(path.join(PKG2, 'resources', 'core'), { recursive: true });
fs.writeFileSync(path.join(PKG2, CORE_REL), 'x');
check('没有清单 → 跳过（开发态/老包不因此起不来）', U.verifyManifestEntry(PKG2, CORE_REL).skipped === true);
fs.writeFileSync(path.join(PKG2, 'resources', 'polaris-files.sha256'), 'd'.repeat(64) + '  resources/other.dll\n');
check('清单里没有该条 → 跳过', U.verifyManifestEntry(PKG2, CORE_REL).skipped === true);
fs.writeFileSync(path.join(PKG2, 'resources', 'polaris-files.sha256'), sha(Buffer.from('x')) + '  ' + CORE_REL + '\n');
fs.rmSync(path.join(PKG2, CORE_REL));
check('文件缺失而清单有 → 红', U.verifyManifestEntry(PKG2, CORE_REL).ok === false);

/* ---------------- 4. 端到端：坏包必须被拒 ---------------- */
async function e2e() {
  console.log('-- 4. 端到端：下载到摘要不符的包必须拒绝且不留包 --');
  const body = Buffer.concat([Buffer.from([0x50, 0x4b, 0x03, 0x04]), Buffer.alloc(4096, 0x41)]);
  const server = http.createServer((req, res) => {
    res.writeHead(200, { 'Content-Type': 'application/zip', 'Content-Length': String(body.length) });
    res.end(body);
  });
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const port = server.address().port;
  const url = 'http://127.0.0.1:' + port + '/Polaris-portable-1.12.1.zip';
  const zp = U.zipPath('1.12.1');

  let err = null;
  try { await U.download(url, '1.12.1', 'e'.repeat(64)); } catch (e) { err = e; }
  check('摘要不符 → download 抛错', !!err && /摘要不符/.test(err.message), err && err.message);
  check('坏包已删掉（不留半个包）', !fs.existsSync(zp), zp);
  check('phase = error 且写进了原因', U.S.phase === 'error' && /摘要/.test(String(U.S.error || '')), String(U.S.error || ''));

  let err2 = null;
  try { await U.download(url, '1.12.1', sha(body)); } catch (e) { err2 = e; }
  check('摘要相符 → 不被摘要闸门拦下（后面的解压失败是环境问题）',
    !err2 || !/摘要/.test(err2.message), err2 && err2.message);
  server.close();
}

/* ---------------- 5. 接线断言（源码级） ---------------- */
function wiring() {
  console.log('-- 5. 接线断言（源码级） --');
  const rd = (p) => fs.readFileSync(path.join(__dirname, '..', p), 'utf8');
  const updSrc = rd('electron/core/updater.js');
  const mgrSrc = rd('electron/core/manager.js');
  const ipcSrc = rd('electron/ipc.js');
  const stSrc = rd('electron/store.js');
  check('download 真的调了核对', /verifyDownloadedFile\(file/.test(updSrc));
  check('提交后校验带上内核', /内核 mihomo\.exe 校验不符/.test(updSrc));
  check('启动前核内核（manager.startKernel）', /verifyManifestEntry\(/.test(mgrSrc) && /mihomo\.exe/.test(mgrSrc));
  check('ipc 把落盘摘要传下去', /update_sha256/.test(ipcSrc));
  check('store 有 update_sha256 默认值', /update_sha256/.test(stSrc));

  /* 硬条件（决策主管）：摘要取不到 → 状态必须是 missing，UI 才能显示「本次未做包校验」 */
  const relSrc = rd('electron/core/release.js');
  const vSrc = rd('src/js/views.js');
  check('shaStateFor：拿到摘要 → ok',
    R.shaStateFor('Polaris-portable-1.12.1.zip', new Map([['Polaris-portable-1.12.1.zip', A]]), null).state === 'ok');
  const s1 = R.shaStateFor('Polaris-portable-1.12.1.zip', new Map(), null);
  check('shaStateFor：摘要里没有该条 → missing + 人话原因',
    s1.state === 'missing' && /没有 Polaris-portable-1\.12\.1\.zip/.test(s1.reason), JSON.stringify(s1));
  const s2 = R.shaStateFor('x.zip', null, new Error('boom'));
  check('shaStateFor：取不到（网络错）→ missing + 原因带原错',
    s2.state === 'missing' && /取发布侧摘要失败/.test(s2.reason) && /boom/.test(s2.reason), JSON.stringify(s2));
  const s3 = R.shaStateFor('', new Map(), null);
  check('shaStateFor：没有 zip 资产 → missing', s3.state === 'missing' && /没有 zip 资产/.test(s3.reason), JSON.stringify(s3));
  check('release.checkLatest 返回 sha_state / sha_reason（UI 靠它判）',
    /sha_state: sha\.state/.test(relSrc) && /sha_reason: sha\.reason/.test(relSrc));
  check('ipc 落盘并暴露 update_sha_state / sha_state',
    /update_sha_state/.test(ipcSrc) && /sha_state: u\.sha_state/.test(ipcSrc));
  check('更新弹窗在 sha_state≠ok 时显示「本次未做包校验」',
    /本次未做包校验/.test(vSrc) && /u\.sha_state !== "ok"/.test(vSrc));
  check('设置页也显示该提示', /s\.settings\.update_sha_state !== "ok"/.test(vSrc));
}

e2e().then(() => {
  wiring();
  console.log('\n' + (failed ? 'FAIL' : 'OK') + '：' + (total - failed) + '/' + total + ' 通过' + (failed ? '，' + failed + ' 项失败' : ''));
  process.exit(failed ? 1 : 0);
}, (e) => {
  console.log('  [FAIL] 自检自身异常：' + (e && e.stack));
  process.exit(1);
});
