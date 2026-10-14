'use strict';
/**
 * 自我替换 helper 的自检（纯 Node，不需要 Electron，也不需要网络）。
 *
 * 为什么单独一条线：`applyWorker()` 是唯一会**改写用户安装目录**的代码，
 * 而且它跑在 GUI 应用退出之后的另一个进程里 —— 出问题现场早就没了（E-8）。
 * 所以这里用假安装目录 + 假 exe + 拦截 spawn，把三个最危险的场景钉死。
 *
 * 跑法：`node scripts\updater-self-test.js`
 *
 * 绝不做的事：不碰真安装目录、不下载、不起 cmd/powershell、不改系统任何东西。
 * （`Object.defineProperty(process, 'execPath')` 把"自我改名"的目标指到临时目录里
 *   的假 exe 上，所以真正被改名的永远不是 node.exe 也不是用户的 Polaris.exe。）
 */

const fs = require('fs');
const path = require('path');
const os = require('os');

const TMP = path.join(os.tmpdir(), 'polaris-updater-selftest');
const APP = path.join(TMP, '中文路径测试', 'Polaris-portable-1.10.0'); // 故意含非 ASCII
const DATA = path.join(TMP, 'data');
const UPD = path.join(DATA, 'update');
const STAGE = path.join(UPD, 'staging');

// 让 updater.js 里的 paths.* 全部落到临时目录
process.env.POLARIS_DATA_DIR = DATA;

// 把"自我改名"和"重新拉起"的目标指到假 exe：真 node.exe 和真 Polaris.exe 都不会被碰
const FAKE_EXE = path.join(APP, 'Polaris.exe');
Object.defineProperty(process, 'execPath', { value: FAKE_EXE, configurable: true });

const updater = require('../electron/core/updater');

// 拦住"重新拉起"：只记参数，不去执行一个文本文件
let launched = null;
require('child_process').spawn = (file, args, opts) => { launched = { file, args, opts }; return { unref() {} }; };

let failed = 0; let total = 0;
function ok(label, cond) {
  total++; if (!cond) failed++;
  console.log((cond ? '  \u2713 ' : '  \u2717 ') + label);
}
const read = (p) => fs.readFileSync(p, 'utf8');

function reset() {
  fs.rmSync(TMP, { recursive: true, force: true });
  // 旧安装目录：程序本体 + 用户数据
  fs.mkdirSync(path.join(APP, 'resources', 'core'), { recursive: true });
  fs.mkdirSync(path.join(APP, 'resources', 'rules'), { recursive: true });
  fs.mkdirSync(path.join(APP, 'data'), { recursive: true });
  fs.writeFileSync(path.join(APP, 'Polaris.exe'), 'OLD-1.10.0');
  fs.writeFileSync(path.join(APP, 'resources', 'core', 'mihomo.exe'), 'core-OLD');
  fs.writeFileSync(path.join(APP, 'resources', 'rules', 'gs_apple.yaml'), 'apple-OLD');
  fs.writeFileSync(path.join(APP, 'data', 'credentials.dat'), 'USER-CREDENTIALS');
  // staging：download() 解压出来的新包（zip 顶层就是文件，没有外层目录）
  for (const d of ['resources/core', 'resources/rules']) fs.mkdirSync(path.join(STAGE, d), { recursive: true });
  fs.writeFileSync(path.join(STAGE, 'Polaris.exe'), 'NEW-1.11.0');
  fs.writeFileSync(path.join(STAGE, 'resources', 'core', 'mihomo.exe'), 'core-NEW');
  fs.writeFileSync(path.join(STAGE, 'resources', 'rules', 'gs_apple.yaml'), 'apple-NEW');
  fs.writeFileSync(path.join(STAGE, 'resources', 'rules', 'gs_cn.yaml'), 'cn-rules');
  // 待清理的残留
  fs.writeFileSync(path.join(UPD, 'staged.json'), '{"version":"1.11.0"}');
  fs.writeFileSync(path.join(UPD, 'Polaris-1.11.0.zip'), 'PK-zip');
}

function setEnv(pid) {
  process.env.POLARIS_APPLY_STAGE = '1';
  process.env.POLARIS_APPLY_ROOT = STAGE;
  process.env.POLARIS_APPLY_TARGET = APP;
  process.env.POLARIS_APPLY_UPDATEDIR = UPD;
  process.env.POLARIS_APPLY_PID = String(pid);
}

async function scenarioNormal() {
  console.log('\n场景 1 · 父进程已退出（正常路径）');
  reset();
  launched = null;
  setEnv(999999); // 不存在的 pid
  const code = await updater.applyWorker();
  ok('退出码 0', code === 0);
  ok('Polaris.exe 换成新包', read(FAKE_EXE) === 'NEW-1.11.0');
  ok('resources/core 被覆盖', read(path.join(APP, 'resources', 'core', 'mihomo.exe')) === 'core-NEW');
  ok('同名旧文件被覆盖', read(path.join(APP, 'resources', 'rules', 'gs_apple.yaml')) === 'apple-NEW');
  ok('新包新增的文件落位', fs.existsSync(path.join(APP, 'resources', 'rules', 'gs_cn.yaml')));
  ok('用户数据 data/ 原样保留', read(path.join(APP, 'data', 'credentials.dat')) === 'USER-CREDENTIALS');
  ok('旧 exe 改名成 .old（Windows 不允许覆盖运行中的 exe）', read(FAKE_EXE + '.old') === 'OLD-1.10.0');
  ok('staged.json 已删', !fs.existsSync(path.join(UPD, 'staged.json')));
  ok('zip 已删', !fs.existsSync(path.join(UPD, 'Polaris-1.11.0.zip')));
  ok('staging 已删（否则下次启动 restoreStaged() 会拿旧包把用户降级回去）', !fs.existsSync(STAGE));
  ok('重新拉起的是新 Polaris.exe', !!launched && launched.file === FAKE_EXE);
}

async function scenarioParentAlive() {
  console.log('\n场景 2 · 父进程一直不退出（必须放弃，绝不动安装目录）');
  reset();
  launched = null;
  setEnv(process.pid); // 活着的 pid
  const t0 = Date.now();
  const code = await updater.applyWorker();
  ok('退出码 2（放弃）', code === 2);
  ok('等满了 120 秒上限', Date.now() - t0 > 110000);
  ok('安装目录一个字节都没动', read(FAKE_EXE) === 'OLD-1.10.0');
  ok('旧 exe 没被改名', !fs.existsSync(FAKE_EXE + '.old'));
  ok('staging 还在（留给用户重试）', fs.existsSync(STAGE));
  ok('没有拉起任何东西', launched === null);
}

async function scenarioStagingGone() {
  console.log('\n场景 3 · staging 里没有 Polaris.exe（不许先改名再发现）');
  reset();
  launched = null;
  fs.rmSync(path.join(STAGE, 'Polaris.exe'));
  setEnv(999999);
  const code = await updater.applyWorker();
  ok('退出码 2', code === 2);
  ok('安装目录未被改动', read(FAKE_EXE) === 'OLD-1.10.0');
  ok('旧 exe 没被改名', !fs.existsSync(FAKE_EXE + '.old'));
  ok('没有拉起任何东西', launched === null);
}

async function scenarioStaleRename() {
  console.log('\n场景 4 · 启动时清理上次残留的 .old');
  reset();
  setEnv(999999);
  await updater.applyWorker();
  ok('替换后 .old 确实在', fs.existsSync(FAKE_EXE + '.old'));
  updater.cleanStaleRename();
  ok('cleanStaleRename 删掉了 .old', !fs.existsSync(FAKE_EXE + '.old'));
  ok('cleanStaleRename 没碰新 exe', read(FAKE_EXE) === 'NEW-1.11.0');
  updater.cleanStaleRename();
  ok('没有残留时再调一次也不报错', true);
}

async function scenarioCopyFails() {
  console.log('\n场景 5 · 复制失败（目标侧同名的是个目录，copyFileSync 必失败）');
  reset();
  launched = null;
  // 让「复制 resources/rules/gs_cn.yaml」这一步必然失败：目标侧同名路径是个目录
  fs.mkdirSync(path.join(APP, 'resources', 'rules', 'gs_cn.yaml'), { recursive: true });
  const fast = updater.APPLY.retryDelays;
  updater.APPLY.retryDelays = [1, 1];   // 别真等 57 秒
  let code;
  try {
    setEnv(999999);
    code = await updater.applyWorker();
  } finally {
    updater.APPLY.retryDelays = fast;
  }
  const alog = read(path.join(UPD, 'apply.log'));
  ok('退出码 1（失败）', code === 1);
  ok('失败文件记了第 1 次重试', /重试 .*gs_cn\.yaml（第 1\/3 次）/.test(alog));
  ok('失败文件记了第 2 次重试（旧代码不 await，只会记第 1 次就往下走）', /重试 .*gs_cn\.yaml（第 2\/3 次）/.test(alog));
  ok('日志里有「复制失败，已放弃本次安装」', alog.includes('复制失败，已放弃本次安装'));
  ok('没有「清理完成」（清理不得早于复制完成）', !alog.includes('清理完成'));
  ok('没有「已重新拉起」（失败不许拉起）', !alog.includes('已重新拉起'));
  ok('staging 还在（留给下次重试）', fs.existsSync(STAGE));
  ok('zip 还在（没被清掉）', fs.existsSync(path.join(UPD, 'Polaris-1.11.0.zip')));
  ok('staged.json 还在（下次启动还能 restoreStaged）', fs.existsSync(path.join(UPD, 'staged.json')));
  ok('没有拉起任何东西', launched === null);
  ok('安装目录里仍有可启动的 Polaris.exe', fs.existsSync(FAKE_EXE));
  ok('用户数据 data/ 原样保留', read(path.join(APP, 'data', 'credentials.dat')) === 'USER-CREDENTIALS');
}

async function scenarioCleanupOrder() {
  console.log('\n场景 6 · 成功路径的顺序：复制完成 → 清理完成 → 重新拉起');
  reset();
  launched = null;
  setEnv(999999);
  const code = await updater.applyWorker();
  const alog = read(path.join(UPD, 'apply.log'));
  const iCopy = alog.indexOf('复制完成');
  const iClean = alog.indexOf('清理完成');
  const iLaunch = alog.indexOf('已重新拉起');
  ok('退出码 0', code === 0);
  ok('三行日志都在', iCopy >= 0 && iClean >= 0 && iLaunch >= 0);
  ok('「复制完成」早于「清理完成」', iCopy < iClean);
  ok('「清理完成」早于「已重新拉起」', iClean < iLaunch);
  ok('拉起时环境里没有 POLARIS_APPLY_*（否则新实例会再跑一遍 helper）',
    !!launched && launched.opts && !Object.keys(launched.opts.env || {}).some((k) => k.startsWith('POLARIS_APPLY_')));
  ok('拉起时仍带着 PATH（只剔自己那几个变量）',
    !!launched && Object.keys(launched.opts.env || {}).some((k) => k.toLowerCase() === 'path'));
}

async function main() {
  console.log('自我替换 helper 自检（临时目录：' + TMP + '）');
  try {
    await scenarioNormal();
    await scenarioParentAlive();
    await scenarioStagingGone();
    await scenarioStaleRename();
    await scenarioCopyFails();
    await scenarioCleanupOrder();
  } finally {
    fs.rmSync(TMP, { recursive: true, force: true });
  }
  console.log(`\n通过 ${total - failed} 项，失败 ${failed} 项`);
  process.exit(failed === 0 ? 0 : 1);
}

main().catch((e) => { console.error('自检本身崩了：', e); process.exit(1); });