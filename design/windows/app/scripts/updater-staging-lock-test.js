'use strict';
/**
 * 自检：残留/被占用的 staging 目录不该再挡住更新（2026-10-17 实机事故）。
 *
 * 走**真实调用链**：download() → clearDirForReuse() → extract() → resolveStageRoot()
 * → verifyManifest() → saveStaged()。只替换两处**操作系统层面**的东西，因为它们在
 * Linux 容器里无法真实发生：
 *   ① extract() 里的 `tar -xf`（GNU tar 不读 zip、容器无 powershell）→ 替换 execFile，
 *      由桩在 dest 写入一份合法的 staged 树（其余解包后的 resolveStageRoot /
 *      verifyManifest / saveStaged 都是真跑）。
 *   ② Windows 上「文件被占 → rmSync EBUSY / rename 也失败」→ 用 fs 补丁模拟。
 * 被测产品代码（download / clearDirForReuse / extract / resolveStageRoot /
 * verifyManifest / saveStaged）一行没改地真跑。
 *
 * 用例：
 *   正例 locked-staged：预置一个「删不掉」的 staging（rmTolerant 删不动）→ 改名让路成功
 *        → 下载 → 解压 → resolveStageRoot → verifyManifest → saveStaged 落盘 staged.json，
 *        phase=staged；旧的 staging-<ts> 让路目录存在。
 *   反例 rename-fails：staging 既删不掉、改名也失败 → download() 停在 clear-staging，
 *        给一句人话、phase=error、落 download-error.json(step=clear-staging)、无 staged.json。
 *
 * 跑法：node scripts\updater-staging-lock-test.js
 */

const fs = require('fs');
const os = require('os');
const path = require('path');
const http = require('http');
const crypto = require('crypto');
const { execFileSync, spawnSync } = require('child_process');

const SCENARIO = process.argv[2] || ''; // ''=跑全部（每例独立子进程）

// ---- 每个场景独立子进程，避免 updater 模块级 S 状态跨用例污染 ----
if (!SCENARIO) {
  let failed = 0;
  for (const s of ['locked-staged', 'rename-fails']) {
    const r = spawnSync(process.execPath, [__filename, s], { encoding: 'utf8' });
    process.stdout.write(r.stdout || '');
    process.stderr.write(r.stderr || '');
    if (r.status !== 0) failed++;
  }
  console.log(failed ? `\n✗ 有 ${failed} 个场景失败` : '\n✓ 全部场景通过');
  process.exit(failed ? 1 : 0);
}

// ---- 隔离的数据根：放 homedir 下（os.tmpdir() 会被守卫 1 拒，但本用例不跑 apply） ----
const TMP = path.join(os.homedir(), '.polaris-staging-lock-test');
const DATA = path.join(TMP, 'data');
const UPD = path.join(DATA, 'update');
const STAGE = path.join(UPD, 'staging');
// 根目录给 POLARIS_DATA_DIR；paths.updateDir() 会再拼一层 data/update，
// 于是 updateDir = TMP/data/update、staging = TMP/data/update/staging（与下面的常量对齐）。
process.env.POLARIS_DATA_DIR = TMP;
fs.rmSync(TMP, { recursive: true, force: true });
fs.mkdirSync(STAGE, { recursive: true });

let failed = 0; let total = 0;
function ok(label, cond) { total++; if (!cond) failed++; console.log((cond ? '  ✓ ' : '  ✗ ') + label); }

// 预置一个「上一次失败留下的」staging 内容（用于模拟残留）
fs.writeFileSync(path.join(STAGE, 'Polaris.exe'), 'OLD-RESIDUE');
fs.mkdirSync(path.join(STAGE, 'resources'), { recursive: true });
fs.writeFileSync(path.join(STAGE, 'resources', 'app.asar'), 'OLD-ASAR');

// ---- 需要的 staged 树（tar 桩会写进 dest） ----
function stagedTree() {
  return {
    'Polaris.exe': 'NEW-EXE',
    'resources/app.asar': 'NEW-ASAR',
    'resources/core/mihomo.exe': 'CORE-NEW',
  };
}

// ---- fs 补丁：只让「对 staging 及其子项」的删除/改名按场景表现 ----
const origRmSync = fs.rmSync, origRmdirSync = fs.rmdirSync, origRenameSync = fs.renameSync;
const underStage = (p) => String(p).startsWith(STAGE);
// 模拟 Windows「文件被占」：rmSync/rmdirSync 对 staging 一律失败
fs.rmSync = function (p, o) { if (underStage(p)) { const e = new Error('EBUSY: resource busy or locked'); e.code = 'EBUSY'; throw e; } return origRmSync.call(fs, p, o); };
fs.rmdirSync = function (p) { if (underStage(p)) { const e = new Error('EBUSY: resource busy or locked'); e.code = 'EBUSY'; throw e; } return origRmdirSync.call(fs, p); };
if (SCENARIO === 'rename-fails') {
  fs.renameSync = function (from) { if (underStage(from)) { const e = new Error('EPERM: operation not permitted'); e.code = 'EPERM'; throw e; } return origRenameSync.call(fs, ...arguments); };
}

// ---- tar 桩：模拟 `tar -xf` 成功后 dest 里的内容 ----
const origExecFile = require('child_process').execFile;
require('child_process').execFile = function (cmd, args, opts, cb) {
  if (typeof opts === 'function') { cb = opts; opts = {}; }
  if (cmd === 'tar') {
    const dest = args[3];
    fs.mkdirSync(path.join(dest, 'resources', 'core'), { recursive: true });
    const tree = stagedTree();
    const mf = [];
    for (const [rel, content] of Object.entries(tree)) {
      fs.mkdirSync(path.join(dest, path.dirname(rel)), { recursive: true });
      fs.writeFileSync(path.join(dest, rel), content);
      mf.push(crypto.createHash('sha256').update(content).digest('hex') + '  ' + rel);
    }
    fs.writeFileSync(path.join(dest, 'resources', 'polaris-files.sha256'), mf.join('\n') + '\n');
    return process.nextTick(() => cb(null, '', ''));
  }
  return origExecFile.call(require('child_process'), cmd, args, opts, cb);
};

const updater = require('../electron/core/updater');

// ---- 本地 http 源站：serve 一个「zip」（只需 PK 头过 isZipFile） ----
function startServer() {
  return new Promise((resolve) => {
    const body = Buffer.concat([Buffer.from([0x50, 0x4b, 0x03, 0x04]), Buffer.alloc(64, 7)]);
    const srv = http.createServer((_req, res) => { res.writeHead(200, { 'Content-Length': body.length }); res.end(body); });
    srv.listen(0, '127.0.0.1', () => resolve({ srv, url: 'http://127.0.0.1:' + srv.address().port + '/Polaris-portable-1.12.5.zip' }));
  });
}

function readJson(p) { try { return JSON.parse(fs.readFileSync(p, 'utf8')); } catch (_) { return null; } }

async function runLockedStaged() {
  console.log('\n正例 locked-staged：staging 删不掉 → 改名让路 → 解压成功 → staged.json 落盘');
  const { srv, url } = await startServer();
  let threw = null; let res = null;
  try { res = await updater.download(url, '1.12.5', ''); } catch (e) { threw = e; }
  srv.close();

  ok('download() 没抛错（rename 让路后一路走通）', !threw && !!res);
  ok('phase = staged', !!res && res.phase === 'staged');
  ok('staged.json 已落盘（saveStaged 真跑）', fs.existsSync(path.join(UPD, 'staged.json')));
  ok('新 staging 里有 Polaris.exe（真 extract + resolveStageRoot）', fs.existsSync(path.join(STAGE, 'Polaris.exe')));
  // 旧的「删不掉的」残留被改名让路：存在一个 staging-<ts>
  const asides = fs.readdirSync(UPD).filter((n) => /^staging-\d+$/.test(n));
  ok('旧残留已改名让路（staging-<ts> 存在）：' + (asides.join(',') || '(无)'), asides.length >= 1);
  ok('成功路径不写 download-error.json（无假绿痕迹）', !fs.existsSync(path.join(UPD, 'download-error.json')));
  ok('包内清单校验通过（新包完整落到 staging）', fs.existsSync(path.join(STAGE, 'resources', 'polaris-files.sha256')));
}

async function runRenameFails() {
  console.log('\n反例 rename-fails：staging 删不掉且改名也失败 → 人话报错 + 留痕');
  const { srv, url } = await startServer();
  let threw = null;
  try { await updater.download(url, '1.12.5', ''); } catch (e) { threw = e; }
  srv.close();

  ok('download() 抛错（没硬删用户目录）', !!threw);
  const msg = String((threw && threw.message) || '');
  ok('报错是人话（含「暂存目录清不掉」）：' + msg, msg.includes('暂存目录清不掉'));
  ok('phase = error', updater.info().phase === 'error');
  const de = readJson(path.join(UPD, 'download-error.json'));
  ok('留痕 download-error.json 存在', !!de);
  ok('诊断 step = clear-staging', !!de && de.step === 'clear-staging');
  ok('诊断里带人话原因', !!de && String(de.reason || '').includes('暂存目录清不掉'));
  ok('没有 staged.json（没假装成功）', !fs.existsSync(path.join(UPD, 'staged.json')));
  ok('旧 staging 原样保留（没硬删用户目录）', fs.existsSync(path.join(STAGE, 'Polaris.exe')));
}

(async () => {
  if (SCENARIO === 'locked-staged') await runLockedStaged();
  else if (SCENARIO === 'rename-fails') await runRenameFails();
  else { console.error('未知场景：' + SCENARIO); process.exit(2); }
  console.log((failed ? `\n✗ ${SCENARIO}: ${failed} 项失败 / 共 ${total}` : `\n✓ ${SCENARIO}: ${total} 项全通过`));
  process.exit(failed ? 1 : 0);
})();
