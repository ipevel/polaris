'use strict';
/** 主进程入口：窗口、托盘、单实例、生命周期。 */

const path = require('path');
const { app, BrowserWindow, Menu, Tray, nativeImage, shell, dialog, ipcMain } = require('electron');

const paths = require('./paths');
const log = require('./logger');
const store = require('./store');
const { register: registerCommands } = require('./ipc');
const { buildTray, updateTray } = require('./tray');

const pkg = require('../package.json');

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
  win.webContents.on('console-message', (_e, level, message, line, sourceId) => {
    const where = `${path.basename(sourceId || '')}:${line}`;
    if (level >= 2) log.error(`renderer ${where}:`, message);
    else log.info(`renderer ${where}:`, message);
  });
  win.webContents.on('did-fail-load', (_e, code, desc, url) => {
    log.error('window load failed:', code, desc, url);
  });
  win.webContents.on('preload-error', (_e, file, err) => {
    log.error('preload failed:', file, err && err.message);
  });

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
});

// 自检模式：起窗口、跑一会儿、留日志、自己退出（供 CI / 无头验证用）
// 放在 whenReady 外面，避免被 .then 回调里的异常吞掉
if (process.argv.includes('--smoke')) {
  log.info('smoke mode armed');
  setTimeout(async () => {
    const fs = require('fs');
    const out = { ok: true, checks: {}, errors: [] };
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
  }, 2500);
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
