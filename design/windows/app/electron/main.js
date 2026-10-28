'use strict';
/** 主进程入口：窗口、托盘、单实例、生命周期。 */

const fs = require('fs');
const path = require('path');
const { app, BrowserWindow, Menu, Tray, nativeImage, shell, dialog, ipcMain } = require('electron');

const paths = require('./paths');
const log = require('./logger');
const store = require('./store');
const ui = require('./ui');

/** 读 `--name=value` 或 `--name value` 两种写法 */
function argOf(name) {
  const eq = process.argv.find((a) => a.startsWith(name + '='));
  if (eq) return eq.slice(name.length + 1);
  const i = process.argv.indexOf(name);
  return i >= 0 ? (process.argv[i + 1] || '') : '';
}

// 界面自检要"用真账号登录，但别弄脏用户自己的登录态"，所以允许把数据根目录指到别处。
// **必须在这里设**：下面第一件事就是 paths.root()，它会把结果缓存住，之后再改就晚了。
if (process.argv.includes('--uitest')) {
  const dir = argOf('--uitest-data');
  if (dir) process.env.POLARIS_DATA_DIR = path.resolve(dir);
}

// Electron 自己的 userData / sessionData / cache 默认落在 %APPDATA%\Polaris ——
// 便携应用不能这样。必须在 app ready 之前改到 data/ 下，否则"整目录拷走"只搬走了
// 我们的数据，缓存还留在别人机器上（而且多个实例会抢同一个缓存目录报 EBUSY）。
try {
  const electronData = path.join(paths.root(), 'data', 'electron');
  fs.mkdirSync(electronData, { recursive: true });
  app.setPath('userData', electronData);
  app.setPath('sessionData', electronData);
} catch (e) {
  // 只读介质等情况：退回默认路径，不因为这个起不来
  console.warn('无法重定向 Electron 数据目录，将使用默认位置:', e && e.message);
}

// 便携目录被整包替换/云同步清掉时，settings.json 与 credentials.dat 会一起没掉，
// 用户看到的就是"打开软件又要重新登录、连面板地址都是空的"。这两个文件在
// %LOCALAPPDATA% 有备份，缺了就从备份回填（只在文件真的不存在时动手）。
// 必须在这里做：store.load() / panel.restore() 都在后面，晚了就来不及。
try {
  const restored = require('./backup').restoreIfMissing();
  if (restored.length) log.info(`data/ 里的 ${restored.join('、')} 从备份回填（安装目录可能被整包替换过）`);
} catch (e) {
  log.warn('backup restore skipped:', e && e.message);
}

// 上一次自我替换留下的 Polaris.exe.old：改名那一刻它还被 helper 进程占着，
// 只能由新进程启动时删（见 updater.js 的 applyWorker 第 2 步）。
// 但如果在「已改名、新文件还没落位」的瞬间被强杀/掉电，安装目录里就只剩 .old、
// 没有 Polaris.exe，应用再也起不来（W9）——所以**先自愈，再清理**：
// healMissingExe() 把 .old 改回 Polaris.exe；cleanStaleRename() 只在 exe 真的在时才删 .old。
try {
  const updater = require('./core/updater');
  const healed = updater.healMissingExe();
  if (healed && healed.healed) log.info('安装目录缺少 Polaris.exe，已从 .old 恢复（上次更新被中断）');
  updater.cleanStaleRename();
} catch (e) { /* 恢复/清理失败都不影响启动 */ }

// 分流规则 + 地理库统一刷新（P1）：来源只有 ipevel/cboard-rulesets 一处，
// 规则清单 14 + geo 3 份，每 12 小时对一次上游；有变化会在内核在线时重启内核生效。
// 两份都随包内置，所以这一步只是「跟上上游」，失败也不影响启动与连接。
// 不 await：启动路径不能等网络。
try {
  const mgr = require('./core/manager');
  // 自更新下载走本机代理：核心在跑（connected）→ 注入运行中的 mixed-port（来源①）；
  // 核心不在 → updater 读 Windows 系统代理（来源②，见 updater.resolveProxy）。
  try {
    const updater = require('./core/updater');
    updater.setCoreProxyProvider(() => (mgr.S && mgr.S.phase === 'connected' ? mgr.mixedPort() : 0));
  } catch (_) {}
  mgr.refreshRules();
  mgr.scheduleRules();
} catch (_) { /* 离线照常启动 */ }

/* ---------- 自更新 helper ---------- */
// 第二个 Polaris.exe 实例：负责覆盖安装目录然后重新拉起（架构见 updater.js 文件头，
// 照 2dust/v2rayN 的 AmazTool）。**这个分支必须在单实例锁之前**：
// 主进程还在跑，它持有锁，helper 会被 `requestSingleInstanceLock()` 判成「第二实例」直接退出。
// 也不碰任何窗口/托盘/IPC —— 它只做替换这一件事，做完 process.exit。
if (process.env.POLARIS_APPLY_STAGE) {
  const updater = require('./core/updater');
  updater.applyWorker().then(
    (code) => process.exit(code),
    (e) => { try { log.error('apply worker 崩溃：' + (e && e.message)); } catch (_) {} process.exit(1); },
  );
  return;
}

const { register: registerCommands } = require('./ipc');
const { buildTray, updateTray } = require('./tray');

const pkg = require('../package.json');

// 模块级可变状态。**必须声明在所有 `return` 分支之前**：
// 下面 `--updtest` 分支会在模块顶层 return，它之后的
// `let` 声明永远不会执行；而定时器/回调是在模块求值完才跑的，一旦那时去碰
// 这些绑定就是 TDZ 崩（`Cannot access 'quitting' before initialization`），
// 表现是主进程弹一个模态错误框卡死。曾经就是这么崩的。
let mainWindow = null;
let tray = null;
let quitting = false;

const IS_DEV = !app.isPackaged || process.argv.includes('--dev');

// 自我替换演练：`set POLARIS_UPDTEST_URL=<zip 地址> && Polaris.exe`。
// 下载 → 解压 → 起一个自己的副本当 helper → 退出，由 helper 覆盖安装目录并重启。
// 这条路径平时只能在真实发版时走一次，坏了就是把用户的安装目录搞坏，
// 所以留一个能在成品包上直接演练的开关（跑之前请先把整个目录拷一份）。
// 注意 1：这个分支在模块顶层 return，后面还有函数声明与事件注册不会执行。
//         模块级状态（`quitting` 等）已提到本分支之前声明，所以这里碰它们不会 TDZ；
//         但**不要再往后加新的模块级 `let`**，否则同样的崩会回来。
// 注意 2：**必须走环境变量，不能写成 `--updtest <url>`**。Electron 的 SEA 单可执行里
//         argv 全被 Node 自己的解析器接管（`node::InitializeNodeWithArgsInternal`），
//         未知 `--` 开关在主进程起来之前就被拒掉 —— 实测三种传法分别报
//         `bad option: --updtest=...`、`bad option: --updtest`、
//         `Cannot find module '--updtest=...'`，日志一个字都不写，
//         排查起来极像"应用崩了"。环境变量不经过 argv 解析，可以可靠到达。
const updtestUrl = process.env.POLARIS_UPDTEST_URL;
if (updtestUrl) {
  const version = process.env.POLARIS_UPDTEST_VERSION || 'updtest';
  app.whenReady().then(async () => {
    const updater = require('./core/updater');
    try {
      log.info(`更新演练：${updtestUrl} → ${paths.root()}`);
      const st = await updater.download(updtestUrl, version);
      log.info(`更新演练：解压完成 phase=${st.phase}`);
      const r = updater.apply();
      log.info(`更新演练：helper pid=${r.helper_pid}，安装目录 ${r.install_dir}，退出中`);
      setTimeout(() => app.quit(), 500);
    } catch (e) {
      log.error('更新演练失败：' + (e && e.message));
      setTimeout(() => app.exit(2), 200);
    }
  });
  return;
}

/* ---------- 单实例 ---------- */
// 拿不到锁说明已经有一个 Polaris 在跑（同一个数据目录）。这里必须**彻底停住**：
// 旧代码只调 app.quit() 就继续往下走，whenReady 一到照样 createWindow ——
// 于是双击多少次就开多少个窗口，托盘里挤一排图标（用户实测报的 bug）。
// 进程要退干净：quit() 是异步的，所以再用 return 把本模块剩下的注册全部跳过。
if (!app.requestSingleInstanceLock()) {
  log.info('second instance detected, exit');
  app.quit();
  return;
}
app.on('second-instance', () => {
  showMain();
});

/* ---------- 窗口 ---------- */
function createWindow() {
  const win = new BrowserWindow({
    width: 1400,
    height: 880,
    minWidth: 1100,
    minHeight: 700,
    frame: false,            // 自绘标题栏
    show: false,
    backgroundColor: '#F5F5F7',
    icon: path.join(__dirname, '..', 'src', 'assets', 'p-icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
      spellcheck: false,
    },
  });

  win.loadFile(path.join(__dirname, '..', 'src', 'index.html'));

  win.once('ready-to-show', () => win.show());

  // 开发期把渲染层异常也收进日志，否则只在 DevTools 里一闪而过。
  // 只把 level>=3（error）当异常收：警告混进来会让「有没有渲染层错误」这个自检信号失效。
  const consoleErrors = [];
  const consoleWarnings = [];
  win.webContents.on('console-message', (...a) => {
    // 老签名 (event, level:number, message, line, sourceId)；
    // 新 Electron 只给一个事件对象、level 是 'error'|'warning'|'info' 字符串。两种都吃。
    let level, message, line, sourceId;
    if (a.length === 1 && a[0] && typeof a[0] === 'object' && 'level' in a[0]) {
      const e = a[0];
      level = e.level === 'error' ? 3 : (e.level === 'warning' ? 2 : 1);
      message = e.message; line = e.lineNumber; sourceId = e.sourceId;
    } else {
      [, level, message, line, sourceId] = a;
    }
    const where = `${path.basename(sourceId || '')}:${line}`;
    if (level >= 3) {
      consoleErrors.push(`${where}: ${message}`);
      log.error(`renderer ${where}:`, message);
    } else if (level === 2) {
      consoleWarnings.push(`${where}: ${message}`);
      log.warn(`renderer ${where}:`, message);
    } else {
      log.info(`renderer ${where}:`, message);
    }
  });
  win.webContents.on('did-fail-load', (_e, code, desc, url) => {
    consoleErrors.push(`load ${code} ${desc} ${url}`);
    log.error('window load failed:', code, desc, url);
  });
  win.webContents.on('preload-error', (_e, file, err) => {
    consoleErrors.push(`preload ${file}: ${err && err.message}`);
    log.error('preload failed:', file, err && err.message);
  });
  win.__consoleErrors = consoleErrors;
  win.__consoleWarnings = consoleWarnings;

  win.on('close', (e) => {
    if (quitting) return;
    // 关闭 = 收进托盘，不退出
    e.preventDefault();
    win.hide();
    if (process.platform === 'darwin') app.dock?.hide();
    log.info('window hidden to tray');
  });

  win.on('closed', () => { mainWindow = null; });

  // 外链一律走系统浏览器
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:/i.test(url)) shell.openExternal(url);
    return { action: 'deny' };
  });
  win.webContents.on('will-navigate', (e, url) => {
    if (!url.startsWith('file://')) { e.preventDefault(); if (/^https?:/i.test(url)) shell.openExternal(url); }
  });

  return win;
}

function showMain() {
  if (!mainWindow) mainWindow = createWindow();
  if (mainWindow.isMinimized()) mainWindow.restore();
  mainWindow.show();
  mainWindow.focus();
}

/* ---------- 生命周期 ---------- */
app.whenReady().then(async () => {
  paths.ensureAll();
  store.set('version', pkg.version);
  log.info('='.repeat(60));
  log.info(`Polaris ${pkg.version} start | packaged=${app.isPackaged} portable=${paths.isPortable()}`);
  log.info(`root=${paths.root()} core=${paths.core()}`);

  // 登录态跨重启：凭据落在 data/credentials.dat（DPAPI 加密，只有本用户能解），
  // 启动时读回来 —— 顺带让登录页能拿到站点名/公告。
  // 旧实现里 restore() 定义了却**从来没有人调用**：文件只写不读，重启就得重新登录，
  // 而且 session.panelUrl 一直是空的，启动时 siteInfo 必然报"尚未配置面板地址"。
  if (require('./panel/client').restore()) log.info('panel session restored at boot');

  registerCommands();
  tray = buildTray();
  mainWindow = createWindow();

  // 复核磁盘上那份凭据（用户第 13 轮第 1 条："退出软件再打开账号显示未登录"）。
  // 只有面板**明确拒绝**（401/403 且回的是面板自己的 JSON）才清凭据并回登录页；
  // 超时/连不上/被 CDN 拦一律保留 —— 面板抽风一次不该让用户重新登录。
  // 放在窗口之后异步做，不占启动路径。
  verifyStoredSession();

  // 界面自检：开真窗口、连真面板、用真账号登录、用真鼠标事件点。
  // 和 real-test.js 的分工：那个只调 IPC（验数据层），这个专门验界面层
  // （手风琴、返回键、按钮文案、页面显示的是不是面板真数据）。
  if (process.argv.includes('--uitest')) runUiTest();

  app.on('activate', () => showMain());

});

/**
 * 启动时复核登录态。`--uitest` 下由自检自己决定要不要登录，这里不插手
 * （否则会在自检"故意删掉凭据逼一次真登录"的用例里插一条导航）。
 */
async function verifyStoredSession() {
  const panel = require('./panel/client');
  if (!panel.isAuthed()) return;
  if (process.argv.includes('--uitest')) return;
  try {
    const r = await panel.verify();
    if (r === 'auth') {
      log.warn('存储的登录态已被面板拒绝（401/403），清除凭据并回到登录页');
      panel.logout();
      ui.toast('登录已失效，请重新登录');
      ui.navigate('login');
    } else if (r === 'net') {
      log.warn('复核登录态时面板不可达，保留登录态（不当成未登录）');
    } else {
      log.info('存储的登录态复核通过');
    }
  } catch (e) {
    log.warn('复核登录态出错，保留登录态：' + (e && e.message));
  }
}

/** `--uitest --panel=… --email=… --password=… [--uitest-data=<根目录>]` */
function runUiTest() {
  const panel = argOf('--panel');
  const email = argOf('--email');
  const password = argOf('--password');
  const done = (code, text) => {
    try { fs.writeFileSync(paths.file('uitest-report.txt'), text, 'utf8'); } catch (_) {}
    setTimeout(() => app.exit(code), 200);
  };
  if (!panel || !email || !password) {
    log.error('uitest: 缺少 --panel / --email / --password');
    return done(2, '缺少 --panel / --email / --password，无法连真面板');
  }
  log.info(`uitest: 真面板界面自检开始 panel=${panel} email=${email} root=${paths.root()}`);
  // 等窗口 ready-to-show + 渲染层 boot() 走完（boot 里有一次异步 get_settings）
  setTimeout(async () => {
    try {
      const r = await require('../scripts/ui-test').run(mainWindow, { panel, email, password });
      log.info(`uitest: ${r.pass} 通过 / ${r.fail} 失败`);
      done(r.fail ? 1 : 0, r.lines.join('\n') + '\n');
    } catch (e) {
      log.error('uitest 崩了：' + (e && e.stack));
      done(2, '界面自检自身异常：' + (e && e.stack));
    }
  }, 1800);
}

app.on('before-quit', () => { quitting = true; });

app.on('window-all-closed', () => {
  // 托盘常驻：不退出。**这个监听器本身就是「不退出」的手段** —— Electron 只有在
  // 没有任何 window-all-closed 监听器时才按默认行为 quit；该事件的 event 不可取消，
  // 所以旧代码那句 e?.preventDefault?.() 是纯空操作（B8）。
  // 窗口全关之后托盘是唯一入口，于是这里做真正有意义的事：确保托盘还在。
  // （托盘创建失败、或被系统/用户销毁过而窗口又关掉，用户就再也叫不出界面了。）
  try {
    require('./tray').ensureTray();
  } catch (e) {
    log.warn('window-all-closed 补建托盘失败：' + (e && e.message));
  }
});

// 退出前的最后清理（还原系统代理 / 停内核）
app.on('will-quit', async () => {
  try {
    // 退出前再同步一次救命备份：这一轮里改过的设置/新登录的凭据都要落进备份
    require('./backup').mirrorNow();
  } catch (_) { /* 备份失败不影响退出 */ }
  try {
    const { shutdown } = require('./core/manager');
    await shutdown('app-quit');
  } catch (e) {
    log.error('shutdown failed:', e && e.message);
  }
  // 释放托盘图标：Windows 上不释放，通知区会留一个点不动的幽灵图标
  try { require('./tray').destroyTray(); } catch (_) { /* 释放失败不影响退出 */ }
});

process.on('uncaughtException', (e) => {
  const msg = String((e && e.message) || e);
  // stdout 管道断了（父进程/控制台退出）不是程序缺陷；而 log.error 自己还要写一次
  // stdout，不特判就会无限递归刷屏、应用卡在报错循环里
  if (/EPIPE|EBADF|ERR_STREAM_DESTROYED/.test(msg)) return;
  try { log.error('uncaughtException:', e && e.stack); } catch (_) {}
  try {
    if (mainWindow && !IS_DEV) {
      dialog.showMessageBox(mainWindow, {
        type: 'error',
        title: 'Polaris',
        message: '程序遇到未处理的错误',
        detail: String(e && e.message ? e.message : e),
        buttons: ['忽略', '退出'],
        defaultId: 1,
      }).then((r) => { if (r.response === 1) app.quit(); });
    }
  } catch (_) {}
});

process.on('unhandledRejection', (e) => {
  try { log.error('unhandledRejection:', e && (e.stack || e)); } catch (_) {}
});

module.exports = { showMain, getWindow: () => mainWindow, setTray: (t) => { tray = t; }, getTray: () => tray, updateTray, quit: () => { quitting = true; app.quit(); } };
