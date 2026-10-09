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

  // 开发期把渲染层异常也收进日志，否则只在 DevTools 里一闪而过
  const consoleErrors = [];
  win.webContents.on('console-message', (_e, level, message, line, sourceId) => {
    const where = `${path.basename(sourceId || '')}:${line}`;
    if (level >= 2) { consoleErrors.push(`${where}: ${message}`); log.error(`renderer ${where}:`, message); }
    else log.info(`renderer ${where}:`, message);
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
        // 空页面也算失败：渲染出来但内容是空的，比报错更难发现
        if (!domInfo || domInfo.textLength < 60) out.ok = false;
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
  log.error('uncaughtException:', e && e.stack);
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
  log.error('unhandledRejection:', e && (e.stack || e));
});

module.exports = { showMain, getWindow: () => mainWindow, setTray: (t) => { tray = t; }, getTray: () => tray, updateTray, quit: () => { quitting = true; app.quit(); } };
