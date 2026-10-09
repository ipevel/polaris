'use strict';
/** 主进程入口：窗口、托盘、单实例、生命周期。 */

const fs = require('fs');
const path = require('path');
const { app, BrowserWindow, Menu, Tray, nativeImage, shell, dialog, ipcMain } = require('electron');

const paths = require('./paths');
const log = require('./logger');
const store = require('./store');

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
const { register: registerCommands } = require('./ipc');
const { buildTray, updateTray } = require('./tray');

const pkg = require('../package.json');

// 诊断模式：跑端到端自检（本地假面板 + 真实内核 + 真实系统代理），不受单实例锁影响。
// 放在最前面 —— 正常启动的窗口/托盘/锁全部跳过。
if (process.argv.includes('--doctor')) {
  require('../scripts/selftest-e2e');
  return;
}

const IS_DEV = !app.isPackaged || process.argv.includes('--dev');
const MOCK = process.argv.includes('--mock');

// 自我替换演练：`Polaris.exe --updtest <zip 地址>`。
// 下载 → 解压 → 写替换脚本 → 退出，由脚本覆盖安装目录并重启。
// 这条路径平时只能在真实发版时走一次，坏了就是把用户的安装目录搞坏，
// 所以留一个能在成品包上直接演练的开关（跑之前请先把整个目录拷一份）。
// 注意 1：这个分支在模块顶层 return，后面的 `quitting` 等声明都不会执行，
//         所以这里不能碰它们（曾经在这里赋 `quitting = true`，直接 TDZ 崩在主进程里）。
// 注意 2：**开关要写在 URL 前面，或者用 `--updtest-version=9.9.9` 这种等号形式**。
//         位置参数（那个 URL）之后再跟 `--switch value`，Electron 会在主进程起来之前就退出，
//         实测 exit code -1、日志一个字都不写，排查起来极像"应用崩了"。
if (process.argv.includes('--updtest')) {
  const argOf = (name) => {
    const eq = process.argv.find((a) => a.startsWith(name + '='));
    if (eq) return eq.slice(name.length + 1);
    const i = process.argv.indexOf(name);
    return i >= 0 ? (process.argv[i + 1] || '') : '';
  };
  const url = argOf('--updtest');
  const version = argOf('--updtest-version') || 'updtest';
  app.whenReady().then(async () => {
    const updater = require('./core/updater');
    try {
      log.info(`更新演练：${url} → ${paths.root()}`);
      const st = await updater.download(url, version);
      log.info(`更新演练：解压完成 phase=${st.phase}`);
      const r = updater.apply();
      log.info(`更新演练：替换脚本 ${r.script}，安装目录 ${r.install_dir}，退出中`);
      setTimeout(() => app.quit(), 500);
    } catch (e) {
      log.error('更新演练失败：' + (e && e.message));
      setTimeout(() => app.exit(2), 200);
    }
  });
  return;
}

let mainWindow = null;
let tray = null;
let quitting = false;

/* ---------- 单实例 ---------- */
if (!app.requestSingleInstanceLock()) {
  log.info('second instance detected, exit');
  app.quit();
} else {
  app.on('second-instance', () => {
    showMain();
  });
}

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

  const query = MOCK ? '?mock=1' : '';
  win.loadFile(path.join(__dirname, '..', 'src', 'index.html'), { query: query ? { mock: '1' } : {} });

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
  log.info(`root=${paths.root()} core=${paths.core()} mock=${MOCK}`);

  registerCommands({ mock: MOCK });
  tray = buildTray();
  mainWindow = createWindow();

  app.on('activate', () => showMain());

  // 界面驱动自检：真的去点界面（拖不动/最大化错位/点了没反应这类只有驱动才测得到）
  if (process.argv.includes('--uitest')) {
    const { run } = require('../scripts/selftest-ui');
    setTimeout(async () => {
      let result = { pass: 0, fail: 1, failures: ['界面自检未运行'], steps: [] };
      const lines = [];
      const log = (m) => { lines.push(m); console.log(m); };
      try {
        const win = mainWindow;
        result = await run(win, { log });
      } catch (e) {
        result.failures.push(String((e && e.stack) || e));
      }
      try {
        fs.mkdirSync(paths.data(), { recursive: true });
        fs.writeFileSync(paths.file('uitest-report.txt'),
          [`Polaris 界面自检 ${new Date().toISOString()}`,
            `结果：${result.pass} 通过 / ${result.fail} 失败`,
            result.failures.length ? '失败项：\n' + result.failures.map((f) => '  - ' + f).join('\n') : '失败项：无',
            '', ...lines].join('\n'), 'utf8');
      } catch (_) {}
      quitting = true;
      app.exit(result.fail ? 1 : 0);
    }, 1200);
  }

  // 运行时功能测试：真界面操作 → 真 IPC → 真内核，每步都从内核回读校验。
  // 开关要写成 --soak=N（位置参数放在开关后面会让 Electron 在主进程起来前静默退出，见 DEVNOTES A-13）
  if (process.argv.includes('--rttest')) {
    const { run } = require('../scripts/rt-test');
    const soakArg = process.argv.find((a) => a.startsWith('--soak='));
    const si = process.argv.indexOf('--soak');
    const soakMinutes = Number(soakArg ? soakArg.split('=')[1] : (si >= 0 ? process.argv[si + 1] : 0)) || 0;
    const keep = process.argv.includes('--keep');
    setTimeout(async () => {
      let result = { pass: 0, fail: 1, failures: ['运行时测试未运行'], steps: [], notes: [] };
      const lines = [];
      const reportPath = paths.file('rt-report.txt');
      // 边跑边落盘：跑到一半卡住/被杀也能看到进度（stdout 在 GUI 进程里本来就看不见）
      const log = (m) => {
        lines.push(m);
        try { console.log(m); } catch (_) {}
        try { fs.appendFileSync(reportPath, m + '\n', 'utf8'); } catch (_) {}
      };
      try {
        result = await run(mainWindow, { log, keep, soakMinutes });
      } catch (e) {
        result.failures.push(String((e && e.stack) || e));
      }
      try {
        fs.mkdirSync(paths.data(), { recursive: true });
        fs.writeFileSync(reportPath,
          [`Polaris 运行时功能测试 ${new Date().toISOString()}`,
            `结果：${result.pass} 通过 / ${result.fail} 失败`,
            result.failures.length ? '失败项：\n' + result.failures.map((f) => '  - ' + f).join('\n') : '失败项：无',
            '', ...lines].join('\n'), 'utf8');
      } catch (_) {}
      quitting = true;
      app.exit(result.fail ? 1 : 0);
    }, 1500);
  }
});

// 自检模式：起窗口、跑一会儿、留日志、自己退出（供 CI / 无头验证用）
// 放在 whenReady 外面，避免被 .then 回调里的异常吞掉
if (process.argv.includes('--smoke')) {
  log.info('smoke mode armed');
  setTimeout(async () => {
    const out = { ok: true, checks: {}, dom: null, errors: [], console_errors: [] };

    // 收集渲染层的报错，别只依赖 main 侧的日志
    try {
      const win = BrowserWindow.getAllWindows()[0];
      if (win) {
        const domInfo = await win.webContents.executeJavaScript(`(() => ({
          title: document.title,
          readyState: document.readyState,
          pageTitle: (document.querySelector('.page-title') || {}).textContent || null,
          navItems: document.querySelectorAll('.nav-item').length,
          cards: document.querySelectorAll('.card').length,
          sidebarVisible: (document.querySelector('#sidebar') || {}).style
            ? document.querySelector('#sidebar').style.display !== 'none' : null,
          textLength: (document.body.innerText || '').length,
          // 留一小段正文：textLength 不达标时，光看数字查不出「页面渲染成了什么」
          textPreview: (document.body.innerText || '').replace(/\\s+/g, ' ').trim().slice(0, 160),
          route: document.querySelector('#btn-login') ? 'login'
            : (document.querySelector('.nav-item.active') || {}).dataset
              ? document.querySelector('.nav-item.active').dataset.route : null,
          hasLogin: !!document.querySelector('#btn-login'),
          theme: document.documentElement.dataset.theme || null,
          apiHost: !!(window.PolarisAPI && window.PolarisAPI.isHost),
          views: Object.keys(window.PolarisViews || {}).length,
          dialogs: Object.keys(window.PolarisDialogs || {}).length,
          hasFormat: !!window.PolarisFormat,
        }))()`);
        out.dom = domInfo;
        out.console_errors = win.__consoleErrors || [];
        if (out.console_errors.length) out.ok = false;
        if (!domInfo || domInfo.readyState !== 'complete') out.ok = false;
        if (!domInfo || domInfo.navItems !== 5) out.ok = false;
        if (!domInfo || domInfo.views < 12 || domInfo.dialogs < 10) out.ok = false;
        // 空页面也算失败：渲染出来但内容是空的，比报错更难发现。
        // 登录页要单独判：它的文案天生就短（表单靠 placeholder 表达，不算 innerText），
        // 旧的「一律 >= 60」会把「登录页渲染正确」误判成空页面。
        const loginOk = domInfo && domInfo.hasLogin === true && domInfo.textLength >= 30;
        const innerOk = domInfo && domInfo.route !== 'login' && domInfo.textLength >= 60;
        if (!domInfo || (!loginOk && !innerOk)) out.ok = false;
      } else {
        out.ok = false;
        out.errors.push('没有窗口');
      }
    } catch (e) {
      out.ok = false;
      out.errors.push('DOM 检查失败: ' + (e && e.message));
    }

    try {
      const { commands } = require('./ipc');
      out.checks.status = await commands.get_status();
      out.checks.app_info = await commands.get_app_info();
      out.checks.settings = await commands.get_settings();
      out.checks.update = await commands.check_update();
    } catch (e) {
      out.ok = false;
      out.errors.push(String(e && e.message ? e.message : e));
    }
    try {
      fs.mkdirSync(paths.data(), { recursive: true });
      fs.writeFileSync(paths.file('smoke-result.json'), JSON.stringify(out, null, 2), 'utf8');
    } catch (e) {
      log.error('smoke write failed:', e && e.message);
    }
    log.info('smoke done ok=%s', out.ok);
    quitting = true;
    app.quit();
  }, 3000);
}

app.on('before-quit', () => { quitting = true; });

app.on('window-all-closed', (e) => {
  // 托盘常驻：不退出
  e?.preventDefault?.();
});

// 退出前的最后清理（还原系统代理 / 停内核）
app.on('will-quit', async () => {
  try {
    const { shutdown } = require('./core/manager');
    await shutdown('app-quit');
  } catch (e) {
    log.error('shutdown failed:', e && e.message);
  }
});

process.on('uncaughtException', (e) => {
  const msg = String((e && e.message) || e);
  // stdout 管道断了（父进程/控制台退出）不是程序缺陷；而 log.error 自己还要写一次
  // stdout，不特判就会无限递归刷屏、应用卡在报错循环里（DEVNOTES A-14）
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
