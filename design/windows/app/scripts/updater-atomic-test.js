'use strict';
/**
 * P0 更新链自检（V1a：原顺序 + 全量备份回滚 + 两个 rename 提交 + 两条 fail-closed 守卫）。
 *   跑法：node scripts/updater-atomic-test.js
 *
 * 覆盖：RED 前置（旧代码会留下「新 exe + 旧 asar」）、5 条失败出口的回滚断言、
 * 守卫 1（env fail-closed）、守卫 2（安装目录路径断言）、包内清单、暂存包版本门、
 * 路径收口（helper 从临时目录运行时 installDir/updateDir 仍指向安装目录）。
 *
 * 不碰真实安装目录：安装目录/数据目录都在 homedir 下的临时基目录里（不能放 os.tmpdir()，
 * 守卫 1 会拒绝临时目录），helper 的临时副本目录才是 os.tmpdir() 下的。
 */
const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawnSync } = require('child_process');

const REPO_UPDATER = path.join(__dirname, '..', 'electron', 'core', 'updater.js');
const UPDATER = process.env.POLARIS_TEST_UPDATER || REPO_UPDATER;
const BASE = fs.mkdtempSync(path.join(os.homedir(), '.polaris-p0-'));
const APP = path.join(BASE, 'app');
const DATA = APP;                       // 生产里 POLARIS_DATA_DIR = 安装目录
const UPD = path.join(APP, 'data', 'update');
const STAGE = path.join(UPD, 'staging');
const HELPER_TMP = path.join(BASE, 'helper-temp');
const SPAWNS = path.join(BASE, 'spawns.json');
const RUNNER = path.join(BASE, 'runner.js');

process.env.POLARIS_DATA_DIR = DATA;
process.env.POLARIS_APPLY_TARGET = APP;

let total = 0; let failed = 0; let skipped = 0;
function check(name, cond, detail) {
  total += 1; if (!cond) failed += 1;
  console.log((cond ? '  [ OK ] ' : '  [FAIL] ') + name + (!cond && detail ? '  ' + detail : ''));
}
function skip(name, why) { skipped += 1; console.log('  [SKIP] ' + name + '：' + why); }
const read = (p) => (fs.existsSync(p) ? fs.readFileSync(p, 'utf8') : '(无)');

const RUNNER_SRC = [
  '\'use strict\';',
  "const fs = require('fs');",
  "const path = require('path');",
  "if (process.env.P0_DROP_DATA_DIR === '1') { delete process.env.POLARIS_DATA_DIR; } else { process.env.POLARIS_DATA_DIR = process.env.P0_DATA; }",
  "process.env.POLARIS_APPLY_STAGE = '1';",
  'process.env.POLARIS_APPLY_ROOT = process.env.P0_STAGE;',
  'process.env.POLARIS_APPLY_TARGET = process.env.P0_APP;',
  'process.env.POLARIS_APPLY_UPDATEDIR = process.env.P0_UPD;',
  "process.env.POLARIS_APPLY_PID = process.env.P0_PID || '999999';",
  "if (process.env.P0_REEXEC === '1') { process.env.POLARIS_APPLY_REEXEC = '1'; process.env.POLARIS_APPLY_TEMPDIR = process.env.P0_TEMPDIR || ''; }",
  'const spawns = [];',
  "const cp = require('child_process');",
  'cp.spawn = (file, args, opts) => { spawns.push({ file: file, env: (opts && opts.env) || {} }); return { unref() {} }; };',
  "Object.defineProperty(process, 'execPath', { value: path.join(process.env.P0_APP, 'Polaris.exe'), configurable: true });",
  'const updater = require(process.env.P0_UPDATER);',
  "if (process.env.POLARIS_TEST_RETRY_FAST === '1' && updater.APPLY) { if (Array.isArray(updater.APPLY.retryDelays)) updater.APPLY.retryDelays = [20, 20]; updater.APPLY.overwriteWaitMs = 300; updater.APPLY.overwritePollMs = 50; }",
  'updater.applyWorker().then((code) => { fs.writeFileSync(process.env.P0_SPAWNS, JSON.stringify(spawns)); process.exit(code); })',
  "  .catch((e) => { fs.writeFileSync(process.env.P0_SPAWNS, JSON.stringify(spawns)); console.error('worker 抛错：' + (e && e.message)); process.exit(9); });",
].join('\n');

function sha256(p) { return require('crypto').createHash('sha256').update(fs.readFileSync(p)).digest('hex'); }
function writeManifest() {
  const rels = [];
  const walk = (dir, prefix) => {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
      const rel = prefix ? prefix + '/' + e.name : e.name;
      if (e.isDirectory()) walk(path.join(dir, e.name), rel); else rels.push(rel);
    }
  };
  walk(STAGE, '');
  const out = rels.filter((r) => r !== 'resources/polaris-files.sha256').map((r) => sha256(path.join(STAGE, r)) + '  ' + r);
  fs.mkdirSync(path.join(STAGE, 'resources'), { recursive: true });
  fs.writeFileSync(path.join(STAGE, 'resources', 'polaris-files.sha256'), out.join('\n') + '\n');
}
function reset(opts) {
  const o = opts || {};
  fs.rmSync(BASE, { recursive: true, force: true });
  fs.mkdirSync(path.join(APP, 'resources'), { recursive: true });
  fs.mkdirSync(path.join(STAGE, 'resources'), { recursive: true });
  fs.mkdirSync(UPD, { recursive: true });
  fs.mkdirSync(HELPER_TMP, { recursive: true });
  fs.writeFileSync(path.join(APP, 'Polaris.exe'), 'OLD-EXE');
  fs.writeFileSync(path.join(APP, 'resources', 'app.asar'), 'OLD-ASAR');
  fs.writeFileSync(path.join(APP, 'icudtl.dat'), 'OLD-ICU');
  fs.writeFileSync(path.join(APP, 'chrome_100_percent.pak'), 'OLD-PAK');
  fs.writeFileSync(path.join(APP, 'resources', 'keep.txt'), 'OLD-KEEP');
  fs.writeFileSync(path.join(STAGE, 'Polaris.exe'), 'NEW-EXE');
  fs.writeFileSync(path.join(STAGE, 'resources', 'app.asar'), 'NEW-ASAR');
  fs.writeFileSync(path.join(STAGE, 'icudtl.dat'), 'NEW-ICU');
  fs.writeFileSync(path.join(STAGE, 'chrome_100_percent.pak'), 'NEW-PAK');
  fs.writeFileSync(path.join(STAGE, 'resources', 'keep.txt'), 'NEW-KEEP');
  if (o.badFile === 'icudtl.dat') { fs.rmSync(path.join(APP, 'icudtl.dat'), { force: true }); fs.mkdirSync(path.join(APP, 'icudtl.dat')); }   // 目标同名是目录 → copyFileSync EISDIR
  // 只读文件：备份读得到，P2 复制与回滚写回都必然失败（目录只读挡不住改写已有文件）
  if (o.readonlyFile) fs.chmodSync(path.join(APP, 'resources', 'keep.txt'), 0o444);
  if (o.manyFiles) {
    for (let i = 0; i < 3000; i += 1) fs.writeFileSync(path.join(STAGE, 'f' + i + '.dll'), 'x');
    fs.writeFileSync(path.join(STAGE, 'zzz-big.bin'), Buffer.alloc(120 * 1024 * 1024, 7));   // 拖慢 P2，好让 SIGKILL 落在复制阶段
  }
  if (o.noManifest !== true) writeManifest();
  if (o.tamper) fs.writeFileSync(path.join(STAGE, 'resources', 'app.asar'), 'TAMPERED');
  fs.writeFileSync(RUNNER, RUNNER_SRC);
}
function runWorker(o) {
  const opt = o || {};
  const env = Object.assign({}, process.env, {
    P0_DATA: DATA, P0_APP: APP, P0_STAGE: STAGE, P0_UPD: UPD, P0_UPDATER: UPDATER, P0_SPAWNS: SPAWNS,
    P0_PID: opt.pid || '999999',
  });
  if (opt.phase2) { env.P0_REEXEC = '1'; env.P0_TEMPDIR = HELPER_TMP; }
  env.POLARIS_TEST_RETRY_FAST = '1';                  // 别真等 57 秒
  if (opt.dropDataDir) env.P0_DROP_DATA_DIR = '1';     // 守卫 1 的注入（runner 里也不会再设它）
  const r = spawnSync(process.execPath, [RUNNER], { env: env, encoding: 'utf8', timeout: 60000 });
  let spawns = [];
  try { spawns = JSON.parse(read(SPAWNS)); } catch (_) { spawns = []; }
  return { code: r.status, out: String(r.stdout || '') + String(r.stderr || ''), spawns: spawns };
}
const result = () => { try { return JSON.parse(read(path.join(UPD, 'apply-result.json'))); } catch (_) { return null; } };
const backupDirs = () => { try { return fs.readdirSync(UPD).filter((n) => n.indexOf('backup-') === 0); } catch (_) { return []; } };
function pairOk() { return read(path.join(APP, 'Polaris.exe')) === 'OLD-EXE' && read(path.join(APP, 'resources', 'app.asar')) === 'OLD-ASAR'; }

console.log('P0 更新链自检（基目录 ' + BASE + '）');

/* ---------- 1. 出口 1：P2 复制失败（exe 还没落位）→ 全量回滚 ---------- */
reset({ badFile: 'icudtl.dat' });
let r1 = runWorker({ phase2: true });
check('出口 1 退出码 1（失败）', r1.code === 1, 'code=' + r1.code + ' ' + r1.out.slice(0, 200));
check('出口 1 exe 仍是旧字节（没提交）', read(path.join(APP, 'Polaris.exe')) === 'OLD-EXE', read(path.join(APP, 'Polaris.exe')));
check('出口 1 asar 仍是旧字节（不存在「新 exe + 旧 asar」）', read(path.join(APP, 'resources', 'app.asar')) === 'OLD-ASAR');
check('出口 1 其它被覆盖文件已还原（pak）', read(path.join(APP, 'chrome_100_percent.pak')) === 'OLD-PAK');
check('出口 1 新包独有的文件没被留下（keep.txt 还原为旧内容）', read(path.join(APP, 'resources', 'keep.txt')) === 'OLD-KEEP');
check('出口 1 没有 .old / .new 残留', !fs.existsSync(path.join(APP, 'Polaris.exe.old')) && !fs.existsSync(path.join(APP, 'Polaris.exe.new')));
check('出口 1 apply-result.json 记了失败且 rolled_back=true', !!result() && result().ok === false && result().rolled_back === true, JSON.stringify(result()));
check('出口 1 回滚成功后备份目录已清', backupDirs().length === 0, JSON.stringify(backupDirs()));
check('出口 1 staging 保留（留给下次重试）', fs.existsSync(STAGE));
check('出口 1 apply.log 有逐步日志', /复制完成|失败：/.test(read(path.join(UPD, 'apply.log'))));

/* ---------- 2. 出口 2：提交点之后失败也不许出现「新 exe + 旧 asar」 ---------- */
reset({ badFile: 'icudtl.dat' });
let r2 = runWorker({ phase2: true });
check('出口 2 失败后 exe/asar 成对（要么都新、要么都旧）', (read(path.join(APP, 'Polaris.exe')) === 'OLD-EXE') === (read(path.join(APP, 'resources', 'app.asar')) === 'OLD-ASAR'), 'exe=' + read(path.join(APP, 'Polaris.exe')) + ' asar=' + read(path.join(APP, 'resources', 'app.asar')));
const srcText = fs.readFileSync(REPO_UPDATER, 'utf8');
check('出口 2 源码里有提交后的 asar 校验（新 exe 配旧 asar 会抛）', srcText.indexOf('app.asar 校验不符') > 0);
check('出口 2 源码里提交是「两个 rename」而不是复制', /renameSync\(curExe, oldExe\)/.test(srcText) && /renameSync\(newExe, curExe\)/.test(srcText));
check('P5 里有包内清单复核（防提交前后 staged 树被换）', /提交后清单复核失败/.test(srcText));
check('备份失败是硬失败（不进入 P2）', /备份失败，未开始复制/.test(srcText));

/* ---------- 3. 出口 3：复制中途被杀 → 安装目录永远有可启动 exe ---------- */
reset({ manyFiles: true });
const child = require('child_process').spawn(process.execPath, [RUNNER], { env: Object.assign({}, process.env, { P0_DATA: DATA, P0_APP: APP, P0_STAGE: STAGE, P0_UPD: UPD, P0_UPDATER: UPDATER, P0_SPAWNS: SPAWNS, P0_PID: '999999', P0_REEXEC: '1', P0_TEMPDIR: HELPER_TMP }), stdio: 'ignore' });
const t0 = Date.now();
while (Date.now() - t0 < 120) { /* 让 P2 复制跑起来 */ }
child.kill('SIGKILL');
require('child_process').spawnSync(process.execPath, ['-e', 'setTimeout(()=>{},50)']);
check('出口 3 被杀后安装目录仍有可启动的 Polaris.exe（旧或新都算，不能没有）', fs.existsSync(path.join(APP, 'Polaris.exe')), read(path.join(APP, 'Polaris.exe')));
check('出口 3 不存在「只剩 .old / 只剩 .new」的失联态', !(!fs.existsSync(path.join(APP, 'Polaris.exe')) && (fs.existsSync(path.join(APP, 'Polaris.exe.old')) || fs.existsSync(path.join(APP, 'Polaris.exe.new')))));

/* ---------- 4. 出口 5：回滚失败必须可见 ---------- */
if (process.platform === 'win32') {
  skip('出口 5 回滚失败可见', 'Windows 上 chmod 000 不生效，留真机注入');
} else {
  reset({ readonlyFile: true });
  const r5 = runWorker({ phase2: true });
  fs.chmodSync(path.join(APP, 'resources', 'keep.txt'), 0o644);
  check('出口 5 退出码 1', r5.code === 1, 'code=' + r5.code);
  check('出口 5 回滚失败被记录（rolled_back=false）', !!result() && result().ok === false && result().rolled_back === false, JSON.stringify(result()));
  check('出口 5 回滚失败时保留备份目录供人工恢复', backupDirs().length > 0, JSON.stringify(backupDirs()));
  check('出口 5 apply.log 里有「回滚失败」', /回滚失败/.test(read(path.join(UPD, 'apply.log'))));
}

/* ---------- 5. 守卫 1：env fail-closed ---------- */
const updater = require(UPDATER);
const hasGuard1 = typeof updater.guardHelperEnv === 'function';
const hasGuard2 = typeof updater.guardAppPath === 'function';
check('守卫 1 缺 POLARIS_DATA_DIR → 拒绝', hasGuard1 && updater.guardHelperEnv({ POLARIS_APPLY_TARGET: APP }).ok === false);
check('守卫 1 缺 POLARIS_APPLY_TARGET → 拒绝', hasGuard1 && updater.guardHelperEnv({ POLARIS_DATA_DIR: APP }).ok === false);
check('守卫 1 目标落在 os.tmpdir() → 拒绝', hasGuard1 && updater.guardHelperEnv({ POLARIS_APPLY_TARGET: path.join(os.tmpdir(), 'x'), POLARIS_DATA_DIR: APP }).ok === false);
check('守卫 1 数据目录落在 os.tmpdir() → 拒绝', hasGuard1 && updater.guardHelperEnv({ POLARIS_APPLY_TARGET: APP, POLARIS_DATA_DIR: path.join(os.tmpdir(), 'x') }).ok === false);
check('守卫 1 目标不存在 → 拒绝', hasGuard1 && updater.guardHelperEnv({ POLARIS_APPLY_TARGET: path.join(BASE, 'nope'), POLARIS_DATA_DIR: APP }).ok === false);
check('守卫 1 合法 env → 通过', hasGuard1 && updater.guardHelperEnv({ POLARIS_APPLY_TARGET: APP, POLARIS_DATA_DIR: APP }).ok === true);
reset();
const g1run = runWorker({ phase2: true, dropDataDir: true });
check('守卫 1 helper 拒绝运行时退出码 3', g1run.code === 3, 'code=' + g1run.code);
check('守卫 1 拒绝时不碰安装目录（exe/asar 原样、无 .old/.new）', pairOk() && !fs.existsSync(path.join(APP, 'Polaris.exe.old')) && !fs.existsSync(path.join(APP, 'Polaris.exe.new')));

/* ---------- 6. 守卫 2：安装目录路径断言 ---------- */
reset();
check('守卫 2 安装目录内的路径 → 放行', hasGuard2 && updater.guardAppPath(path.join(APP, 'Polaris.exe'), '写') === path.join(APP, 'Polaris.exe'));
let threw = false;
try { if (hasGuard2) updater.guardAppPath(path.join(BASE, 'elsewhere', 'Polaris.exe'), '写'); } catch (_) { threw = true; }
check('守卫 2 安装目录外的路径 → 抛错中止', threw);
check('守卫 2 自我改名/清理前都调了守卫（源码断言）',
  /guardAppPath\(path\.join\(appDir, 'Polaris\.exe'\), '写'\)/.test(srcText) && /guardAppPath\(old, '清理残留 exe'\)/.test(srcText));

/* ---------- 7. 包内清单 ---------- */
reset({ noManifest: true });
const r7 = runWorker({});
check('清单缺失 → 拒绝（phase 1 退出码 1）', r7.code === 1, 'code=' + r7.code);
check('清单缺失 → 安装目录未动', pairOk());
check('清单缺失 → apply-result.json 记了原因', !!result() && /清单/.test(String(result().reason)), JSON.stringify(result()));
reset({ tamper: true });
const r7b = runWorker({});
check('清单哈希不符 → 拒绝', r7b.code === 1 && /哈希不符/.test(String((result() || {}).reason)), JSON.stringify(result()));

/* ---------- 8. 暂存包版本门（硬验收③） ---------- */
reset();
require(path.join(__dirname, '..', 'electron', 'store')).set('version', '1.12.0');   // 版本门要有当前版本可比
fs.writeFileSync(path.join(UPD, 'staged.json'), JSON.stringify({ version: '0.9.0', file: '' }));
updater.restoreStaged();
check('旧暂存包 → 不进 staged 态', updater.info().phase !== 'staged', updater.info().phase);
check('旧暂存包 → staged.json 已清（不再反复挂出）', !fs.existsSync(path.join(UPD, 'staged.json')));
check('旧暂存包 → 有可见提示字段', !!updater.info().staged_ignored && updater.info().staged_ignored.version === '0.9.0');
reset();
fs.writeFileSync(path.join(UPD, 'staged.json'), JSON.stringify({ version: '99.0.0', file: '' }));
updater.restoreStaged();
check('更新版本 → 正常进 staged 态', updater.info().phase === 'staged', updater.info().phase);

/* ---------- 9. 路径收口（helper 从临时目录运行时） ---------- */
check('路径收口 installDir() == POLARIS_APPLY_TARGET', updater.installDir() === path.resolve(APP), updater.installDir());
check('路径收口 updateDir() 落在安装目录下', updater.updateDir() === path.join(path.resolve(APP), 'data', 'update'), updater.updateDir());

/* ---------- 10. 日志双写（硬验收①） ---------- */
reset();
fs.mkdirSync(path.join(UPD, 'apply.log'));      // 让 appendFileSync 必然失败
const r10 = runWorker({ phase2: true });
fs.rmSync(path.join(UPD, 'apply.log'), { recursive: true, force: true });
check('apply.log 写不进去时仍然有 apply-result.json', !!result(), JSON.stringify(result()));
check('apply.log 写不进去时 helper 仍跑完（exit 0）', r10.code === 0, 'code=' + r10.code + ' ' + r10.out.slice(0, 200));
check('日志写失败会显式记到主日志（源码断言）', /apply\.log 写不进去/.test(srcText));

fs.rmSync(BASE, { recursive: true, force: true });
console.log('\nupdater-atomic：' + (total - failed) + ' passed / ' + failed + ' failed（共 ' + total + ' 项，跳过 ' + skipped + '）');
process.exit(failed === 0 ? 0 : 1);