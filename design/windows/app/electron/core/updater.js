'use strict';
/**
 * 自更新：下载 → 校验 → 解压到 staging → 起 helper → 退出 → 覆盖安装目录 → 重启。
 *
 * 便携版没法在进程内自我替换：`Polaris.exe`、`*.dll`、`resources/` 在运行时被锁，
 * 覆盖只会拿到 EBUSY/EPERM。所以真正的替换交给一个**独立的 helper 进程**。
 *
 * 架构照 2dust/v2rayN 的 AmazTool 来的（`v2rayN/AmazTool/UpgradeApp.cs`），
 * 逐条对齐，不再用 cmd + robocopy：
 *
 *   1. 主进程把新包下好、解压到 `data/update/staging/`，确认里面有 `Polaris.exe`
 *   2. 起一个 helper（就是第二个 Polaris.exe 实例，靠环境变量切到 worker 分支），
 *      环境变量带上「父进程 pid + staging 根 + 安装目录 + 更新目录」
 *   3. 主进程退出（`will-quit` 里已经把内核停了，`resources/core/mihomo.exe` 不再被占）
 *   4. helper 等父进程真的退出 → **自我改名** `Polaris.exe` → `Polaris.exe.old`
 *      （Windows 允许改名运行中的 exe，但**不允许覆盖**，这一步是关键）
 *   5. 用纯 Node API 把 staging 里的文件逐个复制进安装目录，带退避重试
 *   6. 校验 → 清理包和 staging → 重新拉起 Polaris.exe
 *
 * 为什么必须是这个架构（实测踩过的坑，别再绕回去）：
 *   - 不起 cmd：cmd.exe 读 .cmd 文件用的编码**取决于进程有没有控制台**（有控制台按 UTF-8，
 *     脱离控制台按 OEM 代码页）。同一个文件在两种模式下要求互相矛盾的编码，
 *     只要安装路径里有一个非 ASCII 字符（`软件`、中文用户名），脚本第一步就废，
 *     表现为 cmd 挂死、日志一个字都不写。Node 的文件 API 一律走 Unicode 路径，没有这问题。
 *   - 不用 robocopy：errorlevel 语义、`/IS /IT` 的增量语义、以及"半新半旧"的失败模式
 *     都要人肉兜底；Node 里一个 for 循环 + 重试就够了，失败原因直接是可读的异常。
 *   - 不调 PowerShell 探测进程：无控制台的脱离进程里 `tasklist` 一个字都不输出，
 *     `Get-Process` 又会被命令行编码再坑一次。Node 的 `process.kill(pid, 0)` 最直接。
 *   - 不走命令行开关：Electron 的 SEA 单可执行里 argv 全被 Node 自己的解析器接管，
 *     未知 `--` 开关在主进程起来之前就被拒（实测 `bad option: --updtest`），
 *     所以 helper 的触发与传参**一律走环境变量**。
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

/* ---------------- 下载超时（DEVNOTES B-*） ----------------
 * 这三条以前只有一句 `req.setTimeout(60000, ...)`，结果是**建连超过 5 秒就报
 * 「下载超时（60s 无数据）」**（日志里 5.02 秒一次，连报四次）。原因见 fetchStream 注释。
 * 现在分成三层：建连看门狗 / 空闲看门狗 / 整体硬上限，任一层触发都能让
 * phase 回到 error，不会再有「下载挂死 → 之后所有下载都被『正在更新中』挡住」。
 */
const CONNECT_TIMEOUT_MS = 30 * 1000;      // TCP+TLS 建连最多等多久
const IDLE_TIMEOUT_MS = 60 * 1000;         // 建连后多久没有新数据算卡住
const TOTAL_TIMEOUT_MS = 15 * 60 * 1000;   // 一次下载的硬上限（186MB 慢速也够）

/** 实际生效的超时（都从 TIMEOUTS 读，别直接读上面的常量）。
 *  自检脚本 `scripts/updater-timeout-test.js` 会把它改成毫秒级，
 *  这样三种「卡死」都能在十几秒里验完，不用真等 15 分钟。 */
const TIMEOUTS = { connect: CONNECT_TIMEOUT_MS, idle: IDLE_TIMEOUT_MS, total: TOTAL_TIMEOUT_MS };

/** 毫秒 → 人话时长：整分钟说分钟，其余说秒（自检里是毫秒级，会出 1.5 秒这种） */
function fmtDuration(ms) {
  if (ms >= 60000 && ms % 60000 === 0) return `${ms / 60000} 分钟`;
  const s = ms / 1000;
  return `${Number.isInteger(s) ? s : s.toFixed(1)} 秒`;
}

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

/**
 * 发起 GET，把响应流 resolve 出去（重定向自己跟，最多 5 跳）。
 *
 * 超时这块是踩过坑的，别再改回 `req.setTimeout()`：
 *   - `req.setTimeout(ms)` 走的是 `_http_client.js` 的 `setSocketTimeout()`，
 *     而 socket 在**建连期间**（`sock.connecting === true`）会把这个 ms 推迟到
 *     'connect' 之后再 `sock.setTimeout(ms)`（`_http_client.js:1218-1226`）。
 *     也就是说建连阶段 socket 上带着的是 agent 自己的超时 ——
 *     `https.globalAgent.options.timeout`，本机 Node 24.19 / Electron 44.7
 *     实测都是 **5000ms**（`proxyEnv` 在时由 `NODE_USE_ENV_PROXY` 打开）。
 *   - 把 timeout 作为**请求选项**传（`{ timeout }`）才没有这个空档：
 *     `ClientRequest` 直接把它设为 `req.timeout`，`Agent.createSocket`
 *     （`_http_agent.js:436-439`）建 socket 时就用它，不再落到 agent 的 5 秒。
 *     实测：同一条请求，不传选项时 socket 上是 5000，传了就是 60000。
 *   - 诚实说明：生产日志里那三次「5.02 秒就报 60s 无数据」**没能在本机复现**。
 *     旧代码在直连、CONNECT 隧道、连接池复用三条路径上实测都是 60 秒才失败
 *     （见 DEVNOTES 2026-10-13 条目）。上面的机制是实打实的隐患，也确实被修掉了，
 *     但它是不是那三次 5.02 秒的唯一成因，没有证据下结论。
 * 另外两层保护：显式建连看门狗（CONNECT_TIMEOUT_MS，给一句人话），
 * 以及 downloadTo 里的空闲/整体计时器。
 */
function fetchStream(url, redirects = 0) {
  return new Promise((resolve, reject) => {
    let u;
    try { u = new URL(url); } catch (_) { return reject(new Error('更新地址不是合法 URL')); }
    if (u.protocol !== 'http:' && u.protocol !== 'https:') {
      return reject(new Error('只支持 http(s) 下载'));
    }
    const mod = u.protocol === 'https:' ? https : http;
    let gotResponse = false;
    let connectTimer = null;
    let req = null;
    const clearConnect = () => { if (connectTimer) { clearTimeout(connectTimer); connectTimer = null; } };
    // 建连阶段的超时：此时拿不到响应头，只可能是网络/代理连不上。
    // 拿到响应头后这个监听器就撤掉 —— 下载途中的「没数据」交给 downloadTo 的空闲
    // 计时器，那里的文案能分清「下到一半卡住」和「连不上」。
    const onTimeout = () => {
      if (gotResponse) return;
      req.destroy(new Error(`连接超时（${fmtDuration(TIMEOUTS.connect)}内没有连上 ${u.host}）`));
    };
    req = mod.get(u, {
      headers: { 'User-Agent': 'Polaris-Windows' },
      timeout: TIMEOUTS.idle,
    }, (res) => {
      gotResponse = true;
      clearConnect();
      req.removeListener('timeout', onTimeout);
      // socket 上那个 TIMEOUTS.idle 的计时器只该管「建连 + 等响应头」，
      // 拿到响应头就撤掉：下载途中若一直有数据在流，它会在第 60 秒凭空
      // 触发一次假超时（报出「60s 无数据」，可数据其实一直在下）。
      // 这一阶段的「没数据」由 downloadTo 的空闲计时器负责，它每收到一段
      // 数据就重新计时。另外复用连接池里的旧 socket 时，它可能还带着上一个
      // 请求残留的超时，这里一并清掉。
      if (req.socket) req.socket.setTimeout(0);
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
    req.on('timeout', onTimeout);
    connectTimer = setTimeout(() => {
      if (gotResponse) return;
      req.destroy(new Error(`连接超时（${fmtDuration(TIMEOUTS.connect)}内没有连上 ${u.host}）`));
    }, TIMEOUTS.connect);
    req.on('error', (e) => {
      clearConnect();
      reject(new Error('下载失败：' + (e && e.message)));
    });
  });
}

function downloadTo(url, dest, onProgress) {
  // 进度回调是可选的：自检（scripts/updater-timeout-test.js）只关心超时行为
  const progress = typeof onProgress === 'function' ? onProgress : () => {};
  return fetchStream(url).then((res) => new Promise((resolve, reject) => {
    const total = Number(res.headers['content-length'] || 0);
    const out = fs.createWriteStream(dest);
    let received = 0;
    let last = 0;
    let done = false;
    let idleTimer = null;
    let hardTimer = null;
    const clearTimers = () => {
      if (idleTimer) { clearTimeout(idleTimer); idleTimer = null; }
      if (hardTimer) { clearTimeout(hardTimer); hardTimer = null; }
    };
    // 所有失败都从这里出去：先拆计时器，再销毁两条流，最后只 reject 一次。
    // done 保证「超时」「下载中断」「写入失败」不会互相覆盖成半截状态。
    const fail = (e) => {
      if (done) return;
      done = true;
      clearTimers();
      try { res.destroy(); } catch (_) {}
      try { out.destroy(); } catch (_) {}
      reject(e);
    };
    const armIdle = () => {
      if (idleTimer) clearTimeout(idleTimer);
      idleTimer = setTimeout(
        () => fail(new Error(`下载超时（${fmtDuration(TIMEOUTS.idle)}没有收到数据）`)),
        TIMEOUTS.idle,
      );
    };
    const tick = (force) => {
      const now = Date.now();
      if (!force && now - last < 200) return;
      last = now;
      progress(received, total);
    };
    armIdle();
    // 硬上限：服务端每隔几十秒挤一点数据就能一直把空闲计时器喂饱，
    // 没有这一条就可能「看起来在下、其实永远下不完」。
    hardTimer = setTimeout(
      () => fail(new Error(`下载超时（超过 ${fmtDuration(TIMEOUTS.total)}还没下完，已放弃）`)),
      TIMEOUTS.total,
    );
    res.on('data', (c) => { received += c.length; armIdle(); tick(false); });
    res.on('error', (e) => fail(new Error('下载中断：' + (e && e.message))));
    out.on('error', (e) => fail(new Error('写入失败：' + (e && e.message))));
    out.on('finish', () => {
      if (done) return;
      done = true;
      clearTimers();
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

/* ---------------- 自更新 helper（v2rayN 式自我替换） ---------------- */

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** 进程是否还活着。`kill(pid, 0)` 不发信号，只探活；权限不足也算活着（宁可多等） */
function pidAlive(pid) {
  if (!pid) return false;
  try { process.kill(pid, 0); return true; } catch (e) { return e.code !== 'ESRCH'; }
}

/** 带退避重试：杀软/索引器会短暂占住文件，立刻重试基本都能过 */
async function withRetry(fn, say, what) {
  let last;
  for (let i = 0; i < 5; i++) {
    try { return fn(); } catch (e) {
      last = e;
      say(`重试 ${what}（第 ${i + 1}/5 次）：${e && e.message}`);
      await sleep(1000 * (i + 1));
    }
  }
  throw last;
}

/**
 * 把 src 整棵树复制进 dest，同名文件覆盖。
 * 只复制不删除：旧版本里有、新版本里没有的文件留着（v2rayN 也是这个策略），
 * 这样万一新包少带一个文件，用户至少还能启动。
 */
function copyTree(src, dest, say) {
  fs.mkdirSync(dest, { recursive: true });
  for (const ent of fs.readdirSync(src, { withFileTypes: true })) {
    const s = path.join(src, ent.name);
    const d = path.join(dest, ent.name);
    if (ent.isDirectory()) { copyTree(s, d, say); continue; }
    if (!ent.isFile()) continue;   // 符号链接、设备文件等一律不碰
    withRetry(() => fs.copyFileSync(s, d), say, path.relative(src, s));
  }
}

/**
 * 自更新 worker 的主流程，由「第二个 Polaris.exe 实例」执行。
 * 所有参数都从环境变量读（原因见文件头注释：SEA 下 argv 不可靠）。
 *
 * 返回退出码：0 成功 / 1 失败 / 2 放弃（父进程没退或 staging 不对）。
 * 任何失败都**只记日志、不动安装目录** —— 装一半比不装糟得多。
 */
async function applyWorker() {
  const stageRoot = process.env.POLARIS_APPLY_ROOT;
  const appDir = process.env.POLARIS_APPLY_TARGET;
  const updDir = process.env.POLARIS_APPLY_UPDATEDIR;
  const pid = Number(process.env.POLARIS_APPLY_PID || 0);
  const logFile = path.join(updDir || updateDir(), 'apply.log');
  const say = (s) => {
    // 用 logger 的本地时间戳（以前是 toISOString 的 UTC，跟主日志差 8 小时）
    try { fs.appendFileSync(logFile, `[${log.stamp()}] ${s}\r\n`); } catch (_) { /* 写不了日志也不能中断 */ }
  };

  try {
    if (!stageRoot || !appDir || !updDir) throw new Error('helper 参数不全，放弃');
    say(`helper 启动：父进程 pid=${pid}，staging=${stageRoot}，目标=${appDir}`);

    // 0) 先验 staging。主进程退出到 helper 真正干活之间有空档，用户可能在这期间
    //    点了「丢弃更新包」、或者清理软件把目录删了。**这种情况下没验就动手是最危险的**：
    //    会先把唯一的 Polaris.exe 改名，再发现新包里没有，最后安装目录里什么都不剩。
    //    所以这一条必须在任何写操作之前，且失败时只 return、绝不动安装目录。
    if (!fs.existsSync(path.join(stageRoot, 'Polaris.exe'))) {
      say('staging 里没有 Polaris.exe，放弃（安装目录未改动）');
      return 2;
    }

    // 1) 等主进程真的退出。它退出前会先停内核（main.js 的 will-quit → shutdown），
    //    所以走到这里 resources/core/mihomo.exe 已经不被占了
    const deadline = Date.now() + 120000;
    while (pidAlive(pid)) {
      if (Date.now() > deadline) { say('父进程 120 秒还没退出，放弃（安装目录未改动）'); return 2; }
      await sleep(1000);
    }
    say('父进程已退出');

    // 2) 自我改名。Windows 不允许覆盖运行中的 exe，但允许改名 ——
    //    改名之后安装目录里就没有 Polaris.exe 了，新包才能落进去。
    //    旧 exe 此刻还被本进程占着，删不掉，交给新进程启动时删（见 cleanStaleRename）。
    const self = process.execPath;
    const oldName = self + '.old';
    await withRetry(() => {
      fs.rmSync(oldName, { force: true });
      fs.renameSync(self, oldName);
    }, say, '改名自身 exe');
    say(`已把自身改名成 ${oldName}`);

    // 3) 覆盖安装目录（只复制不删除，data/ 是用户数据，staging 里本来就没有它）
    copyTree(stageRoot, appDir, say);
    say('复制完成');

    // 4) 校验：新 exe 必须在，否则后面拉起的就是空气
    if (!fs.existsSync(path.join(appDir, 'Polaris.exe'))) {
      throw new Error('复制完成但安装目录里没有 Polaris.exe');
    }

    // 5) 清理。**只删环境变量指过来的那个目录**，不用 stateFile()/zipPath()/stagingDir()
    //    重新推导 —— helper 算出的 root 可能和主进程不一样（安装目录中途变得不可写就会变），
    //    重新推导会删错地方，或者干脆漏删。漏删的后果是下次启动 restoreStaged() 把 staging
    //    认成「已就绪」，用户再点一次「重启并安装」就等于拿旧包把自己降级回去。
    try { fs.rmSync(path.join(updDir, 'staged.json'), { force: true }); } catch (_) {}
    try {
      for (const f of fs.readdirSync(updDir)) {
        if (/^Polaris-.*\.zip$/i.test(f)) fs.rmSync(path.join(updDir, f), { force: true });
      }
    } catch (_) {}
    try { fs.rmSync(stageRoot, { recursive: true, force: true }); } catch (_) {}
    say('清理完成');

    // 6) 重新拉起
    require('child_process').spawn(path.join(appDir, 'Polaris.exe'), [], {
      detached: true, stdio: 'ignore', windowsHide: true, cwd: appDir,
    }).unref();
    say('已重新拉起');
    return 0;
  } catch (e) {
    say('失败：' + (e && e.message));
    return 1;
  }
}

/**
 * 删掉上一次更新留下的 `Polaris.exe.old`。
 * 改名那一刻它还被 helper 进程占着，只能由新进程启动时删。
 */
function cleanStaleRename() {
  try {
    const old = process.execPath + '.old';
    if (fs.existsSync(old)) { fs.rmSync(old, { force: true }); log.info('清理了上次更新残留的 ' + old); }
  } catch (e) { log.warn('清理残留 exe 失败：' + (e && e.message)); }
}

/**
 * 退出并安装。起一个「自己」当 helper，然后让调用方退出应用。
 * 返回 {helper_pid, stage_root, install_dir}。
 */
function apply() {
  const blocked = applyBlockedReason();
  if (blocked) throw new Error(blocked);

  const stageRoot = resolveStageRoot(stagingDir());
  if (!stageRoot) throw new Error('staging 目录里没有 Polaris.exe，已放弃（安装目录未改动）');

  const appDir = installDir();
  // helper 就是同一个 exe 的第二个实例，靠环境变量 POLARIS_APPLY_STAGE 切到 worker 分支。
  // 参数全走环境变量：SEA 单可执行里 argv 被 Node 解析器接管，命令行开关进不去（见文件头注释）。
  const child = spawn(process.execPath, [], {
    detached: true,
    stdio: 'ignore',
    windowsHide: true,
    env: {
      ...process.env,
      POLARIS_APPLY_STAGE: '1',
      POLARIS_APPLY_PID: String(process.pid),
      POLARIS_APPLY_ROOT: String(stageRoot),
      POLARIS_APPLY_TARGET: String(appDir),
      POLARIS_APPLY_UPDATEDIR: updateDir(),
    },
  });
  child.unref();
  log.info(`起了自更新 helper（pid=${child.pid}），本进程即将退出`);

  set({ phase: 'applying' });
  return { helper_pid: child.pid, stage_root: stageRoot, install_dir: appDir };
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
  S, TIMEOUTS, info, onChange, download, downloadTo, fetchStream, apply, applyWorker,
  cleanStaleRename, quit, reset, restoreStaged,
  canApply, applyBlockedReason, installDir, updateDir, stagingDir,
  safeName, zipPath, isZipFile, resolveStageRoot, extract, pidAlive,
};
