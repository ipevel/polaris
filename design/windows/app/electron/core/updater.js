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
const fmt = require('../util/format');
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

/* ---------------- helper 侧的可调参数（A-68） ----------------
 * `applyWorker()` 覆盖安装目录时，目标文件可能还被 Electron 的 GPU/renderer 子进程
 * 映射着（`resources/app.asar`、`*.pak`、`*.dll`、`icudtl.dat`），此刻 copyFileSync
 * 会拿到 EBUSY/EPERM/UNKNOWN。**主进程退出 ≠ 子进程退出**，所以必须「先等齐、再复制」，
 * 并且按文件重试的窗口要足够长，覆盖子进程退出的那段时间。
 * 自检 `scripts/updater-self-test.js` 会把这些值缩到毫秒级。
 */
const APPLY = {
  retryDelays: [500, 1000, 2000, 3000, 5000, 5000, 10000, 10000, 10000, 10000], // 单文件重试间隔，合计约 57 秒
  overwriteWaitMs: 90 * 1000,   // 等「将被覆盖的文件」全部可写的总窗口
  overwritePollMs: 1000,        // 轮询间隔
  lockCodes: ['EBUSY', 'EPERM', 'EACCES', 'UNKNOWN', 'ENOTEMPTY'],
};

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
    last_apply_result: readApplyResult(),
    apply_incomplete: applyIncomplete(),
    staged_ignored: S.staged_ignored || null,
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
    // helper 从临时目录运行时 process.execPath 指向临时副本；安装目录的唯一权威来源
    // 是父进程传进来的 POLARIS_APPLY_TARGET（守卫 1 已验过它存在、不是临时目录）。
    const forced = String(process.env.POLARIS_APPLY_TARGET || '').trim();
    if (forced) return path.resolve(forced);
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
    const current = String(store.get('version') || '');
    const staged = String(raw.version);
    // 硬验收③：暂存包必须比当前版本新才可装；旧的直接忽略并清掉（免得反复重挂）
    if (current && fmt.cmpVersion(staged, current) <= 0) {
      log.warn('忽略旧暂存包 ' + staged + '（当前 ' + current + '），已清理');
      S.staged_ignored = { version: staged, current: current };
      try { fs.rmSync(stateFile(), { force: true }); } catch (_) {}
      try { rmTolerant(stagingDir()); } catch (_) {}
      emitState();
      return;
    }
    set({
      phase: 'staged', version: staged, file: String(raw.file || ''),
      staged_at: Number(raw.staged_at) || 0, extractor: String(raw.extractor || ''),
      received: 0, total: 0, percent: 100, error: '',
    });
    log.info('发现已下载的更新包 ' + S.version + '，可直接安装');
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
 * 更新地址只认 http/https。
 * 地址有三个来源：界面传进来的、面板 check_update 回的、落盘的 update_url/download_url。
 * 落盘那份是可写的，而 file:// 或自定义协议进了 downloadTo 会被当成别的协议/本地路径用，
 * 所以在这里统一收口；拒绝时给一句人能看懂的原因，不要静默当成路径处理。
 */
function isAllowedDownloadUrl(url) {
  return /^https?:\/\//i.test(String(url == null ? '' : url).trim());
}

function assertDownloadUrl(url) {
  const u = String(url == null ? '' : url).trim();
  if (!isAllowedDownloadUrl(u)) {
    throw new Error(`更新地址不是 http(s) 链接，已拒绝下载：${u.slice(0, 120) || '(空)'}`);
  }
  return u;
}

/**
 * 下载 + 校验 + 解压 + 校验，一步到位（UI 上就是一个进度条）。
 * 任何一步失败都会把 phase 设成 error 并抛错，绝不留下半个包当「下载好了」。
 */
async function download(url, version, expectedSha256) {
  const target = String(url || '').trim();
  if (!target) throw new Error('未配置更新地址');
  // 协议校验放在最前面：不合法时连 updateDir 都不碰，不留半个状态
  assertDownloadUrl(target);
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

  // 发布侧摘要核对（有就 fail-closed，没有只 warn）：PR-A 之前这里只看 zip 头，
  // 断在半截、或被 CDN 换成别的东西的包，要等解压甚至安装时才暴露。
  const dg = verifyDownloadedFile(file, expectedSha256);
  if (!dg.ok) {
    try { fs.rmSync(file, { force: true }); } catch (_) {}
    set({ phase: 'error', error: dg.reason });
    log.error('更新包校验失败：' + dg.reason);
    throw new Error(dg.reason);
  }
  if (dg.skipped) log.warn('没有可核对的发布摘要，跳过摘要校验（' + dg.reason + '）');
  else log.info(`更新包摘要核对通过 sha256=${dg.actual.slice(0, 12)}…`);

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

  // PR #15 补的硬条件：下载/解压完成后**先验一次**包内清单 + sha256（不通过就停在 error，用户当场看到原因）
  const mf0 = verifyManifest(root);
  if (!mf0.ok) {
    set({ phase: 'error', error: '更新包校验失败：' + mf0.reason });
    log.error('更新包清单校验失败：' + mf0.reason);
    throw new Error(S.error);
  }
  log.info('更新包清单校验通过（' + mf0.count + ' 个文件）');

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

/**
 * 带退避重试：杀软/索引器会短暂占住文件，立刻重试基本都能过。
 *
 * **调用处必须 await**（A-68）：这个函数是 async，以前 `copyTree` 里直接调、不 await，
 * 于是第一次失败之后流程就往下走了 —— 复制还在后台重试，`applyWorker` 已经报
 * 「复制完成」并开始清理、拉起。真机上的表现就是 apply.log 里每个失败文件只有
 * 「第 1/5 次」一条、紧接着「复制完成」，安装目录里留着旧的 `resources/app.asar`
 * （exe 已经是新的）→ 版本号永远读旧值 → 无限升级循环。
 *
 * `fn` 允许是异步的（改名那一步就是），`await` 一并覆盖。
 */
async function withRetry(fn, say, what, delays) {
  const schedule = Array.isArray(delays) ? delays : APPLY.retryDelays;
  const attempts = schedule.length + 1;
  let last;
  for (let i = 0; i < attempts; i++) {
    try { return await fn(); } catch (e) {
      last = e;
      if (i === attempts - 1) break;
      say(`重试 ${what}（第 ${i + 1}/${attempts} 次）：${e && e.message}`);
      await sleep(schedule[i]);
    }
  }
  throw last;
}

/** 递归列出 root 下所有普通文件的相对路径（符号链接、设备文件一律不算）。
 *  helper 用它算出「这次会覆盖哪些文件」，好在动手之前先等它们可写。 */
function listFiles(root, prefix = '', out = []) {
  let entries = [];
  try { entries = fs.readdirSync(path.join(root, prefix), { withFileTypes: true }); } catch (_) { return out; }
  for (const ent of entries) {
    const rel = prefix ? path.join(prefix, ent.name) : ent.name;
    if (ent.isDirectory()) listFiles(root, rel, out);
    else if (ent.isFile()) out.push(rel);
  }
  return out;
}

/**
 * 这个目标文件现在能不能被覆盖。
 * Windows 上被别的进程映射着的 exe/dll/pak，用**写权限**打开会拿 EBUSY/EPERM ——
 * 那正是 copyFileSync 会失败的一批，提前探一次就能「先等齐、再动手」。
 */
function canOverwrite(file) {
  try { fs.closeSync(fs.openSync(file, 'r+')); return true; }
  catch (e) {
    if (e && e.code === 'ENOENT') return true;      // 目标还不存在，直接写
    return !APPLY.lockCodes.includes(e && e.code);
  }
}

/**
 * 等安装目录里「将被覆盖的文件」全部可写，最多等 APPLY.overwriteWaitMs。
 * 主进程退出 ≠ Electron 的 GPU/renderer 子进程退出：它们还映射着 app.asar / *.pak /
 * *.dll / icudtl.dat，正好是 v1.11.4 真机上 EBUSY 的那 11 个文件。
 * 等不齐也照样往下走（由 copyTree 的按文件重试兜底），只是把「还有几个被占」写进日志。
 */
async function waitOverwritable(appDir, stageRoot, say) {
  const rels = listFiles(stageRoot);
  const started = Date.now();
  let announced = false;
  for (;;) {
    const locked = rels.filter((rel) => !canOverwrite(path.join(appDir, rel)));
    if (!locked.length) {
      if (announced) say(`目标文件已全部可覆盖（等了 ${Math.round((Date.now() - started) / 1000)} 秒）`);
      return true;
    }
    const list = locked.slice(0, 5).join('、') + (locked.length > 5 ? ' 等' : '');
    if (Date.now() - started >= APPLY.overwriteWaitMs) {
      say(`等目标文件可覆盖超时（${Math.round(APPLY.overwriteWaitMs / 1000)} 秒），仍有 ${locked.length} 个被占：${list}`);
      return false;
    }
    if (!announced) { say(`有 ${locked.length} 个目标文件还被占着（${list}），等子进程退干净`); announced = true; }
    await sleep(APPLY.overwritePollMs);
  }
}

/**
 * 尽力删除：单个文件删不掉（还被占着）**不能中断整棵树的清理** ——
 * 真机上 `rmSync(recursive)` 就是因为头几个文件 EBUSY 直接抛错，staging 里剩一堆残留。
 * 删不掉的记一条日志留给下次启动，**不抛错**。
 */
function rmTolerant(target, say) {
  let leftovers = 0;
  const walk = (p) => {
    let entries = [];
    try { entries = fs.readdirSync(p, { withFileTypes: true }); } catch (_) { return; }
    for (const ent of entries) {
      const c = path.join(p, ent.name);
      if (ent.isDirectory()) { walk(c); try { fs.rmdirSync(c); } catch (_) { leftovers++; } }
      else { try { fs.rmSync(c, { force: true }); } catch (_) { leftovers++; } }
    }
  };
  try {
    if (fs.statSync(target).isDirectory()) { walk(target); try { fs.rmdirSync(target); } catch (_) { leftovers++; } }
    else fs.rmSync(target, { force: true });
  } catch (_) { /* 本来就不存在 */ }
  if (leftovers && typeof say === 'function') say(`清理时有 ${leftovers} 项删不掉（多半还被占着），留给下次启动再清`);
  return leftovers === 0;
}

/* ------------------------------------------------------------------ */
/* P0（2026-10-15）：helper 的 fail-closed 守卫 / 清单校验 / 备份回滚     */
/* ------------------------------------------------------------------ */

/** 路径 p 是否等于 parent 或在其之下（都先 resolve；Windows 下大小写不敏感） */
function pathInside(p, parent) {
  const norm = (x) => {
    const r = path.resolve(String(x || ''));
    return process.platform === 'win32' ? r.toLowerCase() : r;
  };
  const a = norm(p); const b = norm(parent);
  return a === b || a.startsWith(b.endsWith(path.sep) ? b : b + path.sep);
}

/** 是否落在 os.tmpdir() 之下（helper 的安装目录/数据目录都不许在这里） */
function isTempPath(p) {
  const tmp = String(require('os').tmpdir() || '');
  if (!tmp) return false;
  return pathInside(p, tmp);
}

/**
 * 守卫 1（fail-closed）：POLARIS_APPLY_TARGET 与 POLARIS_DATA_DIR 必须同时存在、
 * 指向真实存在的目录、且都不在 os.tmpdir() 下。任一不满足 → 拒绝运行（不许猜）。
 * helper 从临时目录运行后，这两个变量是它唯一的「我是谁、在哪」依据；猜错的后果
 * 是把新文件写进临时目录或孤儿树，而用户那棵树一个字节都没变。
 */
function guardHelperEnv(env) {
  const e = env || process.env;
  const target = String(e.POLARIS_APPLY_TARGET || '').trim();
  const data = String(e.POLARIS_DATA_DIR || '').trim();
  if (!target) return { ok: false, reason: '缺少 POLARIS_APPLY_TARGET' };
  if (!data) return { ok: false, reason: '缺少 POLARIS_DATA_DIR' };
  if (!path.isAbsolute(target)) return { ok: false, reason: 'POLARIS_APPLY_TARGET 不是绝对路径：' + target };
  if (!path.isAbsolute(data)) return { ok: false, reason: 'POLARIS_DATA_DIR 不是绝对路径：' + data };
  try { if (!fs.statSync(target).isDirectory()) return { ok: false, reason: 'POLARIS_APPLY_TARGET 不是目录：' + target }; }
  catch (_) { return { ok: false, reason: 'POLARIS_APPLY_TARGET 不存在：' + target }; }
  try { if (!fs.statSync(data).isDirectory()) return { ok: false, reason: 'POLARIS_DATA_DIR 不是目录：' + data }; }
  catch (_) { return { ok: false, reason: 'POLARIS_DATA_DIR 不存在：' + data }; }
  if (isTempPath(target)) return { ok: false, reason: 'POLARIS_APPLY_TARGET 落在临时目录：' + target };
  if (isTempPath(data)) return { ok: false, reason: 'POLARIS_DATA_DIR 落在临时目录：' + data };
  return { ok: true, target: path.resolve(target), data: path.resolve(data) };
}

/**
 * 守卫 2：对安装目录的任何写 / 删 / rename 之前，断言目标 resolved 绝对路径就在
 * POLARIS_APPLY_TARGET 之下。不满足 → 抛错中止（宁可不装，也不改错目录）。
 */
function guardAppPath(p, op) {
  const appDir = installDir();
  if (!pathInside(p, appDir)) {
    throw new Error('拒绝' + (op || '操作') + '：目标不在安装目录内（' + path.resolve(String(p)) + ' 不在 ' + path.resolve(appDir) + ' 下）');
  }
  return path.resolve(String(p));
}

/** helper 的结构化结果：落在更新目录，下次启动由主进程读出来给 UI 看 */
function applyResultFile() {
  // helper 要写进父进程指定的那个更新目录（= 真实安装目录的 data/update），
  // 主进程下次启动再用 readApplyResult() 从同一处读出来；两者在生产里是同一个目录。
  return path.join(process.env.POLARIS_APPLY_UPDATEDIR || updateDir(), 'apply-result.json');
}
function writeApplyResult(obj) {
  try {
    fs.mkdirSync(updateDir(), { recursive: true });
    fs.writeFileSync(applyResultFile(), JSON.stringify(Object.assign({ ts: Date.now() }, obj || {})), 'utf8');
  } catch (e) { log.warn('写 apply-result.json 失败：' + (e && e.message)); }
}
function readApplyResult() {
  try {
    const raw = JSON.parse(fs.readFileSync(applyResultFile(), 'utf8'));
    return raw && typeof raw === 'object' ? raw : null;
  } catch (_) { return null; }
}
function clearApplyResult() { try { fs.rmSync(applyResultFile(), { force: true }); } catch (_) {} }
/**
 * PR-D：apply 的「意图」与「alive」记录（失败可观测）。
 *
 * 主进程在起 helper **之前**写一条意图；helper 一起手写一条 alive。下次启动读回来：
 *   · 意图在、结果在（且结果不旧） → 这次更新有结论（成功或失败都算）
 *   · 意图在、结果不在            → **上次更新没跑完**（helper 没起来 / 起手就死 / 被强杀）
 *   · 意图不在                     → 从没发起过更新
 * 1.11.6→1.12.0 那次「更新没生效、两分钟一行日志都没有」，就是第二种情况——
 * 以前它在界面上表现为「什么也没发生」，用户和我们都没有任何可查的痕迹。
 */
function applyIntentFile() {
  return path.join(process.env.POLARIS_APPLY_UPDATEDIR || updateDir(), 'apply-intent.json');
}
function applyAliveFile() {
  return path.join(process.env.POLARIS_APPLY_UPDATEDIR || updateDir(), 'apply-alive.json');
}
function writeApplyIntent(obj) {
  try {
    fs.mkdirSync(updateDir(), { recursive: true });
    fs.writeFileSync(applyIntentFile(), JSON.stringify(Object.assign({ ts: Date.now() }, obj || {})), 'utf8');
  } catch (e) { log.warn('写 apply-intent.json 失败：' + (e && e.message)); }
}
function readApplyIntent() {
  try {
    const raw = JSON.parse(fs.readFileSync(applyIntentFile(), 'utf8'));
    return raw && typeof raw === 'object' ? raw : null;
  } catch (_) { return null; }
}
function readApplyAlive() {
  try {
    const raw = JSON.parse(fs.readFileSync(applyAliveFile(), 'utf8'));
    return raw && typeof raw === 'object' ? raw : null;
  } catch (_) { return null; }
}
/** helper 一起手就写：证明「进程真的跑起来了」（apply.log 第一行之前也可能死） */
function writeApplyAlive(extra) {
  try {
    fs.mkdirSync(path.dirname(applyAliveFile()), { recursive: true });
    fs.writeFileSync(applyAliveFile(), JSON.stringify(Object.assign({ ts: Date.now(), pid: process.pid }, extra || {})), 'utf8');
  } catch (_) { /* 写不进去也不能因此不干活 */ }
}
function clearApplyIntent() {
  try { fs.rmSync(applyIntentFile(), { force: true }); } catch (_) {}
  try { fs.rmSync(applyAliveFile(), { force: true }); } catch (_) {}
}
/**
 * 上次更新是否「没跑完」。结果必须**不旧于**意图才算完成 ——
 * 上一次留下的 apply-result.json 不能拿来给这一次交差。
 */
function applyIncomplete() {
  const intent = readApplyIntent();
  if (!intent) return null;
  const res = readApplyResult();
  if (res && Number(res.ts || 0) >= Number(intent.ts || 0)) { clearApplyIntent(); return null; }
  return {
    started_at: Number(intent.ts || 0),
    version: String(intent.version || ''),
    helper_pid: Number(intent.helper_pid || 0),
    alive: !!readApplyAlive(),   // false = helper 进程根本没起来
    result_missing: true,
  };
}

/** 包内清单（发布侧生成）：逐文件 sha256；缺清单 = 拒绝安装 */
const MANIFEST_REL = 'resources/polaris-files.sha256';
function parseManifest(text) {
  const out = new Map();
  for (const line of String(text || '').split(/\r?\n/)) {
    const m = /^([0-9a-f]{64})\s+\*?(.+)$/.exec(line.trim());
    if (m) out.set(m[2].replace(/\\/g, '/'), m[1].toLowerCase());
  }
  return out;
}
function sha256File(file) {
  return require('crypto').createHash('sha256').update(fs.readFileSync(file)).digest('hex');
}
/** 校验整棵 staged 树：缺清单 / 少条目 / 哈希不符 → {ok:false, reason} */
function verifyManifest(stageRoot) {
  const mf = path.join(stageRoot, MANIFEST_REL);
  if (!fs.existsSync(mf)) return { ok: false, reason: '包内缺少清单 ' + MANIFEST_REL + '（拒绝安装）' };
  let want;
  try { want = parseManifest(fs.readFileSync(mf, 'utf8')); } catch (e) { return { ok: false, reason: '清单读不了：' + (e && e.message) }; }
  if (!want.size) return { ok: false, reason: '清单是空的（拒绝安装）' };
  if (!want.has('Polaris.exe')) return { ok: false, reason: '清单里没有 Polaris.exe' };
  if (!want.has('resources/app.asar')) return { ok: false, reason: '清单里没有 resources/app.asar' };
  for (const rel of listFiles(stageRoot)) {
    const key = rel.replace(/\\/g, '/');
    if (key === MANIFEST_REL) continue;
    if (!want.has(key)) return { ok: false, reason: '清单缺少条目：' + key };
    let got;
    try { got = sha256File(path.join(stageRoot, rel)); } catch (e) { return { ok: false, reason: '读不了 ' + key + '：' + (e && e.message) }; }
    if (got !== want.get(key)) return { ok: false, reason: '哈希不符：' + key };
  }
  return { ok: true, count: want.size };
}

/**
 * 下载完的 zip 摘要核对。expected 为空 / 格式不对 → **跳过（不拦）**：
 * 镜像与自建 CDN 没有 SHA256SUMS.txt，不能因为拿不到摘要就让用户装不了更新。
 * 有摘要而不符 → fail-closed（调用方删包并停在 error）。
 * 口径：这一层防的是**损坏 / 搬运 / CDN 截断**，不是信任锚（摘要与包同源同账号）。
 */
function verifyDownloadedFile(file, expected) {
  const want = String(expected || '').trim().toLowerCase();
  if (!/^[0-9a-f]{64}$/.test(want)) return { ok: true, skipped: true, reason: '没有可核对的发布摘要' };
  let actual = '';
  try { actual = sha256File(file); } catch (e) { return { ok: false, reason: '读不了下载到的包：' + (e && e.message) }; }
  if (actual !== want) {
    return { ok: false, expected: want, actual, reason: `更新包摘要不符（期望 ${want.slice(0, 12)}…，实际 ${actual.slice(0, 12)}…）` };
  }
  return { ok: true, expected: want, actual };
}

/**
 * 包内清单的**单文件**定点校验（启动前核内核用）。
 * 为什么不整包校验：geo / 规则有几百 MB，每次启动都算一遍不可接受；而内核是原生 exe，
 * 它坏掉的表现是「连不上」这种玄学，最值得在启动前换成一句人话。
 * 语义：清单缺失 / 清单里没有这一条 → skipped（开发态、老包不因此起不来）；
 *       条目在而哈希不符 / 文件读不了 → ok:false（fail-closed）。
 */
function verifyManifestEntry(root, rel) {
  const key = String(rel || '').replace(/\\/g, '/');
  let text = '';
  try { text = fs.readFileSync(path.join(root, MANIFEST_REL), 'utf8'); }
  catch (_) { return { ok: true, skipped: true, reason: '没有包内清单（' + MANIFEST_REL + '）' }; }
  let want;
  try { want = parseManifest(text); } catch (e) { return { ok: true, skipped: true, reason: '清单读不了：' + (e && e.message) }; }
  if (!want.has(key)) return { ok: true, skipped: true, reason: '清单里没有 ' + key };
  let actual = '';
  try { actual = sha256File(path.join(root, key)); } catch (e) { return { ok: false, reason: '读不了 ' + key + '：' + (e && e.message) }; }
  if (actual !== want.get(key)) return { ok: false, expected: want.get(key), actual, reason: key + ' 与包内清单不符' };
  return { ok: true, expected: want.get(key), actual };
}

/** 备份「将被覆盖且已存在」的文件；返回清单（含原 sha256，供回滚校验） */
function backupOverwritten(appDir, stageRoot, backupDir, say) {
  const entries = [];
  fs.mkdirSync(backupDir, { recursive: true });
  for (const rel of listFiles(stageRoot)) {
    if (rel === 'Polaris.exe') continue;            // exe 靠 .old，不进备份
    if (rel === MANIFEST_REL) continue;
    const src = path.join(appDir, rel);
    let st;
    try { st = fs.statSync(src); } catch (_) { continue; }
    if (!st.isFile()) continue;
    const dst = path.join(backupDir, rel);
    fs.mkdirSync(path.dirname(dst), { recursive: true });
    fs.copyFileSync(src, dst);
    entries.push({ rel: rel.replace(/\\/g, '/'), sha256: sha256File(src) });
  }
  if (say) say('已备份 ' + entries.length + ' 个将被覆盖的文件 → ' + backupDir);
  return entries;
}

/** 用备份把已覆盖的文件还原回去（逐个校验 sha256） */
function restoreBackup(appDir, backupDir, entries, say) {
  let restored = 0; let failed = 0;
  for (const e of entries || []) {
    const src = path.join(backupDir, e.rel);
    const dst = path.join(appDir, e.rel);
    try {
      guardAppPath(dst, '回滚');
      fs.copyFileSync(src, dst);
      if (sha256File(dst) !== e.sha256) { failed += 1; if (say) say('回滚后校验不符：' + e.rel); }
      else restored += 1;
    } catch (err) {
      failed += 1;
      if (say) say('回滚失败：' + e.rel + '（' + (err && err.message) + '）');
    }
  }
  return { restored: restored, failed: failed };
}

/** 复制整棵树，但跳过顶层指定名字（V1a：exe 单独走 .new + rename 提交） */
async function copyTreeExcept(src, dest, skipNames, say) {
  const skip = new Set(skipNames || []);
  fs.mkdirSync(dest, { recursive: true });
  for (const ent of fs.readdirSync(src, { withFileTypes: true })) {
    if (skip.has(ent.name)) continue;
    const s = path.join(src, ent.name);
    const d = path.join(dest, ent.name);
    if (ent.isDirectory()) { await copyTreeExcept(s, d, null, say); continue; }
    if (!ent.isFile()) continue;
    guardAppPath(d, '复制');
    await withRetry(() => fs.copyFileSync(s, d), say, path.relative(src, s));
  }
}
/**
 * 把 src 整棵树复制进 dest，同名文件覆盖。
 * 只复制不删除：旧版本里有、新版本里没有的文件留着（v2rayN 也是这个策略），
 * 这样万一新包少带一个文件，用户至少还能启动。
 *
 * **每个文件都要 await**（A-68）：不 await 的话失败会被吞掉，流程照常往下走。
 */
async function copyTree(src, dest, say) {
  fs.mkdirSync(dest, { recursive: true });
  for (const ent of fs.readdirSync(src, { withFileTypes: true })) {
    const s = path.join(src, ent.name);
    const d = path.join(dest, ent.name);
    if (ent.isDirectory()) { await copyTree(s, d, say); continue; }
    if (!ent.isFile()) continue;   // 符号链接、设备文件等一律不碰
    await withRetry(() => fs.copyFileSync(s, d), say, path.relative(src, s));
  }
}

/* ---- phase 1：清单校验 → 复制到临时目录 → 从临时目录拉起新 helper ---- */
async function prepareAndReexec(stageRoot, appDir, updDir, pid, say) {
  if (!fs.existsSync(path.join(stageRoot, 'Polaris.exe'))) {
    say('staging 里没有 Polaris.exe，放弃（安装目录未改动）');
    writeApplyResult({ ok: false, step: 'precheck', reason: 'staging 里没有 Polaris.exe', rolled_back: false });
    return 2;
  }
  const mf = verifyManifest(stageRoot);
  if (!mf.ok) {
    say('拒绝安装：' + mf.reason);
    writeApplyResult({ ok: false, step: 'manifest', reason: mf.reason, rolled_back: false });
    return 1;
  }
  say('包内清单校验通过（' + mf.count + ' 个文件）');

  const deadline = Date.now() + 120000;
  while (pidAlive(pid)) {
    if (Date.now() > deadline) {
      say('父进程 120 秒还没退出，放弃（安装目录未改动）');
      writeApplyResult({ ok: false, step: 'wait-parent', reason: '父进程 120 秒未退出', rolled_back: false });
      return 2;
    }
    await sleep(1000);
  }
  say('父进程已退出');

  let tempDir = '';
  try {
    tempDir = fs.mkdtempSync(path.join(require('os').tmpdir(), 'polaris-helper-'));
    await copyTree(stageRoot, tempDir, say);
  } catch (e) {
    say('复制到临时目录失败：' + (e && e.message));
    writeApplyResult({ ok: false, step: 'stage-temp', reason: (e && e.message) || 'copy failed', rolled_back: false });
    return 1;
  }
  say('已把已校验的新包复制到临时目录 ' + tempDir);
  const childEnv = Object.assign({}, process.env, { POLARIS_APPLY_REEXEC: '1', POLARIS_APPLY_TEMPDIR: tempDir });
  try {
    const child = require('child_process').spawn(path.join(tempDir, 'Polaris.exe'), [], {   // 懒 require：自检要能拦 spawn
      detached: true, stdio: 'ignore', windowsHide: true, cwd: tempDir, env: childEnv,
    });
    child.unref();
    say('已从临时目录拉起 helper（pid=' + child.pid + '）');
  } catch (e) {
    say('从临时目录拉起失败：' + (e && e.message));
    writeApplyResult({ ok: false, step: 'spawn-temp', reason: (e && e.message) || 'spawn failed', rolled_back: false });
    return 1;
  }
  return 0;
}
/* ---- phase 2：V1a 真正安装（原顺序 + 全量备份回滚 + 两个 rename 提交）---- */
async function doApply(stageRoot, appDir, updDir, tempDir, say) {
  const backupDir = path.join(updDir, 'backup-' + Date.now());
  let entries = [];
  let committed = false;
  try {
    if (!fs.existsSync(path.join(stageRoot, 'Polaris.exe'))) throw new Error('staging 里没有 Polaris.exe');
    const mf = verifyManifest(stageRoot);
    if (!mf.ok) throw new Error('清单校验失败：' + mf.reason);

    await waitOverwritable(appDir, stageRoot, say);

    // P1 备份（失败 = 硬失败，不进入 P2）
    try { entries = backupOverwritten(appDir, stageRoot, backupDir, say); }
    catch (e) { throw new Error('备份失败，未开始复制：' + (e && e.message)); }

    // P2 复制除 exe 外的全部文件
    await copyTreeExcept(stageRoot, appDir, ['Polaris.exe'], say);
    say('复制完成（除 exe）');

    // P3 staged exe → Polaris.exe.new（旧 exe 仍在，目录全程可启动）
    const curExe = guardAppPath(path.join(appDir, 'Polaris.exe'), '写');
    const oldExe = guardAppPath(curExe + '.old', '写');
    const newExe = guardAppPath(curExe + '.new', '写');
    await withRetry(() => fs.copyFileSync(path.join(stageRoot, 'Polaris.exe'), newExe), say, 'Polaris.exe.new');
    say('新 exe 已就位（Polaris.exe.new）');

    // P4 提交：两个 rename（无 exe 窗口只有毫秒级）
    await withRetry(() => { fs.rmSync(oldExe, { force: true }); fs.renameSync(curExe, oldExe); }, say, '改名自身 exe');
    say('已把 Polaris.exe 改名成 ' + oldExe);
    await withRetry(() => fs.renameSync(newExe, curExe), say, 'Polaris.exe.new → Polaris.exe');
    committed = true;
    say('已提交：新 exe 落位');

    // P5 校验
    if (!fs.existsSync(curExe)) throw new Error('提交后安装目录里没有 Polaris.exe');
    const wantAsar = sha256File(path.join(stageRoot, 'resources', 'app.asar'));
    const gotAsar = sha256File(path.join(appDir, 'resources', 'app.asar'));
    if (wantAsar !== gotAsar) throw new Error('app.asar 校验不符（新 exe 配旧 asar）');
    // 内核也是随包分发的原生 exe：以前只比 app.asar，内核那一步没落地就只能等用户
    // 点「连接」时才发现，而且报出来的是「连不上」。这里一起比。
    const coreRel = 'resources/core/mihomo.exe';
    if (fs.existsSync(path.join(stageRoot, coreRel))) {
      const wantCore = sha256File(path.join(stageRoot, coreRel));
      const gotCore = sha256File(path.join(appDir, coreRel));
      if (wantCore !== gotCore) throw new Error('内核 mihomo.exe 校验不符（复制没落地）');
    }
    say('校验通过：exe + app.asar + 内核 与暂存包一致');
    const mf2 = verifyManifest(stageRoot);
    if (!mf2.ok) throw new Error('提交后清单复核失败：' + mf2.reason);

    // P6 清理 + 拉起
    // 只删环境变量指过来的那个目录（不重新推导 root：helper 算出来的可能与主进程不同）
    try { fs.rmSync(path.join(updDir, 'staged.json'), { force: true }); } catch (_) {}
    try {
      for (const name of fs.readdirSync(updDir)) {
        if (/^Polaris-.*\.zip$/i.test(name)) rmTolerant(path.join(updDir, name), say);
      }
    } catch (_) {}
    rmTolerant(stageRoot, say);
    rmTolerant(backupDir, say);
    if (tempDir) rmTolerant(tempDir, say);
    say('清理完成');

    const childEnv = Object.assign({}, process.env);
    for (const k of Object.keys(childEnv)) if (k.indexOf('POLARIS_APPLY_') === 0) delete childEnv[k];
    require('child_process').spawn(path.join(appDir, 'Polaris.exe'), [], {
      detached: true, stdio: 'ignore', windowsHide: true, cwd: appDir, env: childEnv,
    }).unref();
    say('已重新拉起');
    writeApplyResult({ ok: true, step: 'done', reason: '', rolled_back: false });
    return 0;
  } catch (e) {
    say('失败：' + (e && e.message));
    let rolled = true;
    if (entries.length) {
      const r = restoreBackup(appDir, backupDir, entries, say);
      if (r.failed) rolled = false;
      say('回滚：还原 ' + r.restored + ' 个，失败 ' + r.failed + ' 个');
    }
    const curExe = path.join(appDir, 'Polaris.exe');
    const oldExe = curExe + '.old';
    try {
      if (committed && fs.existsSync(oldExe)) {
        const back = path.join(appDir, 'Polaris.exe.rollback');
        guardAppPath(back, '回滚改名');
        fs.renameSync(curExe, back);
        fs.renameSync(oldExe, curExe);
        fs.rmSync(back, { force: true });
        say('已把旧 exe 换回（版本回退一致）');
      } else if (!fs.existsSync(curExe) && fs.existsSync(oldExe)) {
        guardAppPath(curExe, '回滚改名');
        fs.renameSync(oldExe, curExe);
        say('已把旧 exe 改回 Polaris.exe');
      }
    } catch (e2) { rolled = false; say('回滚改名失败：' + (e2 && e2.message)); }
    if (rolled) { rmTolerant(backupDir, say); } else { say('保留备份目录供人工恢复：' + backupDir); }
    if (tempDir) rmTolerant(tempDir, say);
    writeApplyResult({ ok: false, step: 'apply', reason: (e && e.message) || 'unknown', rolled_back: rolled });
    return 1;
  } finally {
    try {
      const curExe = path.join(appDir, 'Polaris.exe');
      const oldExe = curExe + '.old';
      const newExe = curExe + '.new';
      if (!fs.existsSync(curExe)) {
        if (fs.existsSync(oldExe)) { guardAppPath(curExe, 'finally 兜底'); fs.renameSync(oldExe, curExe); say('finally：已把 .old 改回 Polaris.exe'); }
        else if (fs.existsSync(newExe)) { guardAppPath(curExe, 'finally 兜底'); fs.renameSync(newExe, curExe); say('finally：已把 .new 提升为 Polaris.exe'); }
      }
    } catch (e) { say('finally 兜底失败：' + (e && e.message)); }
  }
}
/**
 * 自更新 worker 的主流程（P0/V1a）。两个阶段：
 *   phase 1（由安装目录里的旧 exe 启动）：守卫 1 → 清单校验 → 等父进程退出
 *            → 把已校验的新包整份复制到 os.tmpdir() → 从临时目录拉起 phase 2
 *   phase 2（由临时目录里的新 exe 运行）：不加载安装目录的 dll/pak，按 V1a 安装
 * 返回退出码：0 成功 / 1 失败 / 2 放弃（父进程没退或 staging 不对）/ 3 守卫拒绝
 */
async function applyWorker() {
  const stageRoot = process.env.POLARIS_APPLY_ROOT;
  const appDir = process.env.POLARIS_APPLY_TARGET;
  const updDir = process.env.POLARIS_APPLY_UPDATEDIR;
  const pid = Number(process.env.POLARIS_APPLY_PID || 0);
  const reexec = process.env.POLARIS_APPLY_REEXEC === '1';
  const tempDir = String(process.env.POLARIS_APPLY_TEMPDIR || '');
  // PR-D②：**第一件事**就写 alive —— 放在任何守卫、任何 say 之前。
  // 守卫失败会在 say 之后留下 apply.log；而「Node 起来了、还没走到 say 就死」以前一行痕迹都没有。
  // 有了它，事后能分清「helper 根本没起来」和「起来了但早死」。
  writeApplyAlive({ stage_root: String(stageRoot || ''), target: String(appDir || ''), reexec: reexec });

  const logFile = path.join(updDir || updateDir(), 'apply.log');
  const say = (s) => {
    const line = '[' + log.stamp() + '] ' + s;
    // 硬验收①：apply.log 与主日志都写；写失败要显式报出来，不再静默吞掉
    try { fs.appendFileSync(logFile, line + '\r\n'); }
    catch (e) { try { log.error('apply.log 写不进去：' + (e && e.message)); } catch (_) {} }
    try { log.info('[helper] ' + s); } catch (_) {}
  };

  // 守卫 1（fail-closed）：两阶段都要过，缺一不可
  const g1 = guardHelperEnv();
  if (!g1.ok) {
    say('拒绝运行：' + g1.reason);
    writeApplyResult({ ok: false, step: 'guard-env', reason: g1.reason, rolled_back: false });
    return 3;
  }

  try {
    if (reexec) {
      say('helper 启动（临时目录运行）：pid=' + pid + '，staging=' + stageRoot + '，目标=' + appDir);
      return await doApply(stageRoot, appDir, updDir, tempDir, say);
    }
    say('helper 启动（阶段 1）：父进程 pid=' + pid + '，staging=' + stageRoot + '，目标=' + appDir);
    return await prepareAndReexec(stageRoot, appDir, updDir, pid, say);
  } catch (e) {
    say('失败：' + (e && e.message));
    writeApplyResult({ ok: false, step: 'worker', reason: (e && e.message) || 'unknown', rolled_back: false });
    return 1;
  }
}
/**
 * 启动自愈（W9）：安装目录里没有 `Polaris.exe`，却留着上次自我替换改名的
 * `Polaris.exe.old`。这是 applyWorker 第 2 步（改名）与第 4 步（复制）之间被强杀 /
 * 掉电留下的残局：**安装目录里唯一还能启动的文件就是那个 .old**，用户双击安装目录
 * 里的任何东西都起不来。此时把它改回 Polaris.exe 就恢复了（Windows 允许给运行中的
 * exe 改名，所以「用户双击 .old 启动」这条路上也能救回来）。
 *
 * 只做这一件事：不碰其它文件，改名失败就原样保留 .old（宁可不自愈，也不能把唯一的
 * 可执行文件弄丢）。dir 可传，方便纯 Node 自检不碰真实安装目录。
 */
function healMissingExe(dir) {
  const target = dir || installDir();
  const exe = path.join(target, 'Polaris.exe');
  const old = exe + '.old';
  try {
    if (fs.existsSync(exe)) return { healed: false, reason: 'exe 在' };
    if (!fs.existsSync(old)) return { healed: false, reason: '没有 .old' };
    fs.renameSync(old, exe);
    log.info('启动自愈：安装目录缺少 Polaris.exe，已把 ' + old + ' 改回 Polaris.exe');
    return { healed: true, exe };
  } catch (e) {
    log.warn('启动自愈失败，保留 ' + old + ' 不动：' + (e && e.message));
    return { healed: false, reason: (e && e.message) || 'unknown' };
  }
}

/**
 * 删掉上一次更新留下的 `Polaris.exe.old`。
 * 改名那一刻它还被 helper 进程占着，只能由新进程启动时删。
 *
 * W9：**只有当前这个进程正在跑的 exe 真的存在时才删**。helper 改名后被杀、或用户是
 * 双击 .old 启动的（那时 process.execPath 就是 .old 本身），.old 是安装目录里唯一能
 * 启动的文件 —— 这时删掉它，等于把用户最后的救生圈扔了，只能手工改文件名才能再用。
 */
function cleanStaleRename() {
  try {
    // P0：helper 从临时目录运行时 process.execPath 是临时副本，必须看安装目录
    const exe = path.join(installDir(), 'Polaris.exe');
    const old = exe + '.old';
    if (!fs.existsSync(old)) return;
    if (!fs.existsSync(exe)) {
      log.warn('保留 ' + old + '：当前 exe 不存在，它是安装目录里唯一能启动的文件（等自愈改名）');
      return;
    }
    guardAppPath(old, '清理残留 exe');
    fs.rmSync(old, { force: true });
    log.info('清理了上次更新残留的 ' + old);
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
  // PR-D①：起 helper **之前**留下意图。helper 若在 Node 启动阶段就死（SEA 环境变量没注入全、
  // 被杀软拦、exe 被锁），以前是一点痕迹都没有：主进程只写「起了 helper(pid=…)」就退出，
  // 两分钟后用户看到版本没变、日志空白。有了这条，下次启动能明确说「上次更新没跑完」。
  writeApplyIntent({ version: String(S.version || ''), stage_root: String(stageRoot), install_dir: String(appDir) });

  // PR-D③：helper 的 stdout/stderr 以前是 'ignore'（全丢）。重定向到文件 —— 它在 Node
  // 启动阶段就崩的那种情况，唯一能留下的就是它自己吐的错。
  let outFd = null;
  try { fs.mkdirSync(updateDir(), { recursive: true }); outFd = fs.openSync(path.join(updateDir(), 'helper.out.log'), 'a'); } catch (_) { outFd = null; }
  const child = spawn(process.execPath, [], {
    detached: true,
    stdio: outFd == null ? 'ignore' : ['ignore', outFd, outFd],
    windowsHide: true,
    env: {
      ...process.env,
      POLARIS_APPLY_STAGE: '1',
      POLARIS_APPLY_PID: String(process.pid),
      POLARIS_APPLY_ROOT: String(stageRoot),
      POLARIS_APPLY_TARGET: String(appDir),
      POLARIS_APPLY_UPDATEDIR: updateDir(),
      // helper 从临时目录运行后，paths.root() 会按 exe 位置解析；这个变量把它的
      // 数据目录钉回安装目录（守卫 1 会校验它存在且不在临时目录）
      POLARIS_DATA_DIR: appDir,
    },
  });
  if (outFd != null) { try { fs.closeSync(outFd); } catch (_) {} }
  child.unref();
  writeApplyIntent({ version: String(S.version || ''), stage_root: String(stageRoot), install_dir: String(appDir), helper_pid: child.pid });
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
  S, TIMEOUTS, APPLY, info, onChange, download, downloadTo, fetchStream, apply, applyWorker,
  cleanStaleRename, healMissingExe, quit, reset, restoreStaged,
  isAllowedDownloadUrl, assertDownloadUrl,
  canApply, applyBlockedReason, installDir, updateDir, stagingDir,
  safeName, zipPath, isZipFile, resolveStageRoot, extract, pidAlive,
  // P0 测试钩子
  guardHelperEnv, guardAppPath, pathInside, isTempPath, verifyManifest, verifyManifestEntry,
  verifyDownloadedFile, parseManifest, sha256File,
  backupOverwritten, restoreBackup, copyTreeExcept, writeApplyResult, readApplyResult, clearApplyResult,
  writeApplyIntent, readApplyIntent, writeApplyAlive, readApplyAlive, clearApplyIntent, applyIncomplete, applyIntentFile, applyAliveFile,
  copyTree, listFiles, canOverwrite, waitOverwritable, rmTolerant,
};
