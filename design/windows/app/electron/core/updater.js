'use strict';
/**
 * 自动更新：下载 → 校验 → 解压到 staging → 生成替换脚本 → 退出并重启。
 *
 * 便携版没法在进程内自我替换：`Polaris.exe`、`*.dll`、`resources/` 在运行时被锁，
 * 覆盖只会拿到 EBUSY/EPERM。所以真正的替换交给一个脱离父进程的批处理：
 *
 *   1. 下载新包到 `data/update/Polaris-<版本>.zip`
 *   2. 解压到 `data/update/staging/`，确认里面有 `Polaris.exe`（解压不对就不敢动安装目录）
 *   3. 写 `data/update/apply-update.cmd`：等本进程退出 → robocopy 覆盖安装目录 → 重新拉起 → 记日志
 *   4. `app.quit()`，脚本接手
 *
 * 安装目录里的 `data/` 是用户数据（配置、订阅、凭据、流量记录），脚本只覆盖不删除，
 * 所以更新后账号与设置都还在。
 *
 * 本模块不 require electron（除了真正要退出那一下），这样纯 Node 的自检也能覆盖它。
 */

const fs = require('fs');
const path = require('path');
const http = require('http');
const https = require('https');
const { execFile, spawn } = require('child_process');

const paths = require('../paths');
const store = require('../store');
const log = require('../logger');

const ZIP_MAGIC = [0x50, 0x4b]; // "PK"

/** 更新状态机：idle → downloading → extracting → staged →（apply）applying / error */
const S = {
  phase: 'idle',
  version: '',
  url: '',
  file: '',
  received: 0,
  total: 0,
  percent: 0,
  error: '',
  staged_at: 0,
  extractor: '',
};

const listeners = new Set();

function onChange(fn) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

function info() {
  return {
    phase: S.phase,
    version: S.version,
    url: S.url,
    file: S.file,
    received: S.received,
    total: S.total,
    percent: S.percent,
    error: S.error,
    staged_at: S.staged_at,
    extractor: S.extractor,
    install_dir: installDir(),
    portable: paths.isPortable(),
    packaged: paths.isPackaged,
    can_apply: canApply(),
  };
}

function emitState() {
  const snapshot = info();
  for (const fn of listeners) {
    try { fn(snapshot); } catch (e) { log.warn('update listener failed:', e && e.message); }
  }
}

function set(patch) {
  Object.assign(S, patch);
  emitState();
}

/** 安装目录：打包后就是 exe 所在目录（也就是便携根目录），开发态是 app 目录 */
function installDir() {
  try {
    const mod = require('electron');
    const app = mod && mod.app;
    if (app && app.isPackaged) return path.dirname(app.getPath('exe'));
    if (app && app.getAppPath) return app.getAppPath();
  } catch (_) { /* 纯 Node 自检 */ }
  return path.resolve(__dirname, '..', '..');
}

function updateDir() { return paths.updateDir(); }
function stagingDir() { return path.join(updateDir(), 'staging'); }
function stateFile() { return path.join(updateDir(), 'staged.json'); }

/**
 * 能不能自我替换。只有「打包 + 便携（安装目录可写）」才允许：
 * 开发态覆盖的是源码目录，只读目录（Program Files / 只读 U 盘）覆盖会失败。
 */
function canApply() {
  return paths.isPackaged && paths.isPortable() && S.phase === 'staged';
}

function applyBlockedReason() {
  if (!paths.isPackaged) return '开发态不支持自我替换，请手动更新';
  if (!paths.isPortable()) return '当前安装目录不可写（可能装在 Program Files 或只读盘），请手动解压更新';
  if (S.phase !== 'staged') return '还没有下载好的更新包';
  return '';
}

/** 版本号进文件名前先洗一遍，避免 `../` 之类跑出目录 */
function safeName(version) {
  const v = String(version || '').trim().replace(/[^0-9A-Za-z._-]/g, '').replace(/\.{2,}/g, '.');
  return v || 'latest';
}

function zipPath(version) { return path.join(updateDir(), `Polaris-${safeName(version)}.zip`); }

/** zip 头校验：只认 "PK"。下载到一半的 html 错误页、被劫持的 302 落地页都过不了这关 */
function isZipFile(file) {
  try {
    const fd = fs.openSync(file, 'r');
    const buf = Buffer.alloc(4);
    const n = fs.readSync(fd, buf, 0, 4, 0);
    fs.closeSync(fd);
    if (n < 2) return false;
    if (buf[0] !== ZIP_MAGIC[0] || buf[1] !== ZIP_MAGIC[1]) return false;
    return true;
  } catch (_) {
    return false;
  }
}

/**
 * 解压出来的根目录：正常包是「根下直接有 Polaris.exe」，
 * 也容忍「包里套了一层同名目录」。两种都不是就返回 null（宁可不更新）。
 */
function resolveStageRoot(dir) {
  if (!dir) return null;
  if (fs.existsSync(path.join(dir, 'Polaris.exe'))) return dir;
  let entries = [];
  try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch (_) { return null; }
  const dirs = entries.filter((e) => e.isDirectory());
  if (dirs.length === 1 && fs.existsSync(path.join(dir, dirs[0].name, 'Polaris.exe'))) {
    return path.join(dir, dirs[0].name);
  }
  return null;
}

function fetchStream(url, redirects = 0) {
  return new Promise((resolve, reject) => {
    let u;
    try { u = new URL(url); } catch (_) { return reject(new Error('更新地址不是合法 URL')); }
    if (u.protocol !== 'http:' && u.protocol !== 'https:') {
      return reject(new Error('只支持 http(s) 下载'));
    }
    const mod = u.protocol === 'https:' ? https : http;
    const req = mod.get(u, { headers: { 'User-Agent': 'Polaris-Windows' } }, (res) => {
      const code = res.statusCode || 0;
      if (code >= 300 && code < 400 && res.headers.location) {
        res.resume();
        if (redirects >= 5) return reject(new Error('重定向次数过多'));
        let next;
        try { next = new URL(res.headers.location, u).toString(); } catch (_) { return reject(new Error('重定向地址不合法')); }
        return resolve(fetchStream(next, redirects + 1));
      }
      if (code !== 200) {
        res.resume();
        return reject(new Error(`下载失败：HTTP ${code}`));
      }
      resolve(res);
    });
    req.on('error', (e) => reject(new Error('下载失败：' + (e && e.message))));
    req.setTimeout(60000, () => req.destroy(new Error('下载超时（60s 无数据）')));
  });
}

function downloadTo(url, dest, onProgress) {
  return fetchStream(url).then((res) => new Promise((resolve, reject) => {
    const total = Number(res.headers['content-length'] || 0);
    const out = fs.createWriteStream(dest);
    let received = 0;
    let last = 0;
    let done = false;
    const tick = (force) => {
      const now = Date.now();
      if (!force && now - last < 200) return;
      last = now;
      onProgress(received, total);
    };
    res.on('data', (c) => { received += c.length; tick(false); });
    res.on('error', (e) => { if (!done) { done = true; out.destroy(); reject(new Error('下载中断：' + (e && e.message))); } });
    out.on('error', (e) => { if (!done) { done = true; reject(new Error('写入失败：' + (e && e.message))); } });
    out.on('finish', () => {
      if (done) return;
      done = true;
      tick(true);
      resolve({ size: received, total });
    });
    res.pipe(out);
  }));
}

function psQuote(s) { return `'${String(s).replace(/'/g, "''")}'`; }

function run(cmd, args, timeoutMs) {
  return new Promise((resolve, reject) => {
    execFile(cmd, args, { windowsHide: true, timeout: timeoutMs, maxBuffer: 4 * 1024 * 1024 }, (err, stdout, stderr) => {
      if (err) {
        const detail = String(stderr || stdout || err.message || '').trim().split(/\r?\n/)[0];
        return reject(new Error(detail || '解压失败'));
      }
      resolve(String(stdout || ''));
    });
  });
}

/**
 * 解压。优先用系统自带的 bsdtar（Win10 1803+ 就有，快且不怕长路径），
 * 没有 tar 再退回 PowerShell 的 Expand-Archive（慢，但几乎哪都有）。
 */
async function extract(zipFile, dest) {
  fs.rmSync(dest, { recursive: true, force: true });
  fs.mkdirSync(dest, { recursive: true });
  try {
    await run('tar', ['-xf', zipFile, '-C', dest], 600000);
    return 'tar';
  } catch (e) {
    log.warn('tar 解压失败，改用 Expand-Archive：' + (e && e.message));
    await run('powershell', ['-NoProfile', '-NonInteractive', '-Command',
      `Expand-Archive -LiteralPath ${psQuote(zipFile)} -DestinationPath ${psQuote(dest)} -Force`], 900000);
    return 'Expand-Archive';
  }
}

function saveStaged() {
  try {
    fs.mkdirSync(updateDir(), { recursive: true });
    fs.writeFileSync(stateFile(), JSON.stringify({
      version: S.version, file: S.file, staged_at: S.staged_at, extractor: S.extractor,
    }, null, 2));
  } catch (e) {
    log.warn('写 staged.json 失败：' + (e && e.message));
  }
}

/** 启动时把上一次「下好了但没装」的包捞回来，别让用户白下 186MB */
function restoreStaged() {
  try {
    const raw = JSON.parse(fs.readFileSync(stateFile(), 'utf8'));
    const root = resolveStageRoot(stagingDir());
    if (!root || !raw || !raw.version) return;
    if (raw.file && !fs.existsSync(raw.file)) return;
    set({
      phase: 'staged', version: String(raw.version), file: String(raw.file || ''),
      staged_at: Number(raw.staged_at) || 0, extractor: String(raw.extractor || ''),
      received: 0, total: 0, percent: 100, error: '',
    });
    log.info(`发现已下载的更新包 ${S.version}，可直接安装`);
  } catch (_) { /* 没有就是没有 */ }
}

function reset() {
  try { fs.rmSync(stagingDir(), { recursive: true, force: true }); } catch (_) {}
  try { fs.rmSync(stateFile(), { force: true }); } catch (_) {}
  // 下载下来的 zip 也要删掉：一个真包 ~186 MB，用户点了「丢弃更新包」
  // 却把包留在磁盘上（还可能是上一次没装成的旧版本）不合适。
  // 只删更新目录里我们自己命名的 Polaris-*.zip，别碰用户放进去的东西。
  try {
    for (const name of fs.readdirSync(updateDir())) {
      if (/^Polaris-.*\.zip$/i.test(name)) {
        try { fs.rmSync(path.join(updateDir(), name), { force: true }); } catch (_) {}
      }
    }
  } catch (_) { /* 目录不存在就算了 */ }
  set({ phase: 'idle', version: '', url: '', file: '', received: 0, total: 0, percent: 0, error: '', staged_at: 0, extractor: '' });
  return { ok: true };
}

/**
 * 下载 + 校验 + 解压 + 校验，一步到位（UI 上就是一个进度条）。
 * 任何一步失败都会把 phase 设成 error 并抛错，绝不留下半个包当「下载好了」。
 */
async function download(url, version) {
  const target = String(url || '').trim();
  if (!target) throw new Error('未配置更新地址');
  if (/\.apk(\?|#|$)/i.test(target)) {
    // 后端只有一个 update_url 时它指的是安卓包，直接下下来装会把 Windows 端毁掉
    throw new Error('更新地址指向的是安卓安装包，Windows 版请等后端补上 update_windows_url');
  }
  if (S.phase === 'downloading' || S.phase === 'extracting') throw new Error('正在更新中，请稍候');

  const ver = String(version || '').trim() || 'latest';
  const file = zipPath(ver);
  fs.mkdirSync(updateDir(), { recursive: true });
  try { fs.rmSync(stagingDir(), { recursive: true, force: true }); } catch (_) {}
  try { fs.rmSync(stateFile(), { force: true }); } catch (_) {}

  set({ phase: 'downloading', version: ver, url: target, file, received: 0, total: 0, percent: 0, error: '', staged_at: 0, extractor: '' });
  log.info(`开始下载更新 ${ver}：${target}`);

  let size = 0;
  try {
    const r = await downloadTo(target, file, (received, total) => {
      set({
        received,
        total,
        percent: total ? Math.min(99, Math.round((received / total) * 100)) : 0,
      });
    });
    size = r.size;
  } catch (e) {
    set({ phase: 'error', error: (e && e.message) || '下载失败' });
    log.error('更新下载失败：' + S.error);
    throw e;
  }

  if (!size) {
    set({ phase: 'error', error: '下载到的文件是空的' });
    throw new Error(S.error);
  }
  if (!isZipFile(file)) {
    set({ phase: 'error', error: '下载到的不是 zip 包（可能被劫持或地址指向了网页）' });
    log.error(`更新包校验失败：${file}（${size} 字节）`);
    throw new Error(S.error);
  }

  set({ phase: 'extracting', percent: 100, received: size, total: size });
  log.info(`下载完成 ${size} 字节，开始解压到 ${stagingDir()}`);

  let extractor = '';
  try {
    extractor = await extract(file, stagingDir());
  } catch (e) {
    set({ phase: 'error', error: (e && e.message) || '解压失败' });
    log.error('更新解压失败：' + S.error);
    throw e;
  }

  const root = resolveStageRoot(stagingDir());
  if (!root) {
    set({ phase: 'error', error: '更新包里没有找到 Polaris.exe，已放弃（安装目录未改动）' });
    log.error('更新包结构不对：' + stagingDir());
    throw new Error(S.error);
  }

  S.staged_at = Date.now();
  S.extractor = extractor;
  saveStaged();
  set({ phase: 'staged', percent: 100, error: '' });
  log.info(`更新 ${ver} 已就绪（${extractor}），等待用户重启安装`);
  return info();
}

/**
 * 生成替换脚本。脚本内容刻意全用 ASCII：cmd.exe 默认代码页读中文会乱码，
 * 而这个脚本是无人值守跑的，任何乱码都可能变成一条错命令。
 */
function scriptFor(stageRoot, appDir, pid) {
  const src = String(stageRoot).replace(/"/g, '');
  const app = String(appDir).replace(/"/g, '');
  const upd = updateDir().replace(/"/g, '');
  const logFile = path.join(updateDir(), 'apply-update.log').replace(/"/g, '');
  return [
    '@echo off',
    'setlocal',
    `set "SRC=${src}"`,
    `set "APP=${app}"`,
    `set "UPD=${upd}"`,
    `set "LOG=${logFile}"`,
    'echo [%date% %time%] waiting for pid ' + Number(pid) + ' > "%LOG%"',
    'set /a N=0',
    // 为什么不用 tasklist：脚本是"无控制台"的脱离进程（GUI 应用 spawn 出来的 cmd），
    // 这种环境下 tasklist 一个字都不输出 —— 管道版会卡死在 find 上（永远等不到 EOF），
    // 重定向到文件的版本则每次都判定"进程已退出"，于是应用还活着就开始覆盖。
    // PowerShell 的 Get-Process 在无控制台环境里工作正常，实测过。
    ':wait',
    `powershell -NoProfile -NonInteractive -Command "if (Get-Process -Id ${Number(pid)} -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }" >nul 2>nul`,
    'if errorlevel 2 goto tick',
    'if errorlevel 1 goto copy',
    ':tick',
    'set /a N+=1',
    'if %N% GEQ 120 goto stuck',
    'ping -n 2 127.0.0.1 >nul',
    'goto wait',
    ':copy',
    'ping -n 2 127.0.0.1 >nul',
    'echo [%date% %time%] copying >> "%LOG%"',
    'robocopy "%SRC%" "%APP%" /E /IS /IT /R:2 /W:1 /NFL /NDL /NJH /NJS /NP >> "%LOG%" 2>&1',
    'if errorlevel 8 goto fail',
    // 装完就把包清掉：不清的话下次启动 restoreStaged() 会把 staging 又认成「已就绪」，
    // 用户再点一次「重启并安装」就等于拿旧包把自己降级回去
    'del "%UPD%\\staged.json" 2>nul',
    'del "%UPD%\\Polaris-*.zip" 2>nul',
    'rd /s /q "%SRC%" 2>nul',
    'echo [%date% %time%] done, restarting >> "%LOG%"',
    'start "" "%APP%\\Polaris.exe"',
    'exit /b 0',
    // 主进程没退就绝不覆盖：文件被占着，抄到一半只会留下半新半旧的安装目录
    ':stuck',
    'echo [%date% %time%] pid ' + Number(pid) + ' still alive after 120s, aborted >> "%LOG%"',
    'exit /b 2',
    ':fail',
    'echo [%date% %time%] robocopy failed >> "%LOG%"',
    'exit /b 1',
    '',
  ].join('\r\n');
}

/**
 * 退出并安装。写完脚本就 spawn 一个脱离父进程的 cmd，然后让调用方退出应用。
 * 返回 {script, pid, stage_root, install_dir}，自检就靠这个断言脚本内容。
 */
function apply() {
  const blocked = applyBlockedReason();
  if (blocked) throw new Error(blocked);

  const stageRoot = resolveStageRoot(stagingDir());
  if (!stageRoot) throw new Error('staging 目录里没有 Polaris.exe，已放弃（安装目录未改动）');

  const appDir = installDir();
  const pid = process.pid;
  const script = path.join(updateDir(), 'apply-update.cmd');
  fs.writeFileSync(script, scriptFor(stageRoot, appDir, pid), 'utf8');
  log.info(`写好了替换脚本 ${script}，即将退出并重启`);

  const child = spawn('cmd.exe', ['/c', script], { detached: true, stdio: 'ignore', windowsHide: true });
  child.unref();

  set({ phase: 'applying' });
  return { script, pid, stage_root: stageRoot, install_dir: appDir };
}

/** apply() 之后由 ipc 层调用：让进程真的退出，把舞台交给脚本 */
function quit() {
  try {
    const { app } = require('electron');
    if (app && app.quit) { app.quit(); return true; }
  } catch (_) { /* 纯 Node */ }
  return false;
}

module.exports = {
  S, info, onChange, download, apply, quit, reset, restoreStaged,
  canApply, applyBlockedReason, installDir, updateDir, stagingDir,
  safeName, zipPath, isZipFile, resolveStageRoot, scriptFor, extract,
};
