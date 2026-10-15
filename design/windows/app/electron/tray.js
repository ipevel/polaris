'use strict';
/** 托盘：连接状态 + 快速节点切换 + 显示/退出。常驻，不随窗口关闭。 */

const path = require('path');
const { Menu, Tray, nativeImage, app } = require('electron');
const log = require('./logger');
const store = require('./store');
const core = require('./core/manager');
const menu = require('./tray-menu');

const ICON = path.join(__dirname, '..', 'src', 'assets', 'p-icon.png');

let tray = null;
// 上一次真正设进托盘的「菜单内容签名」与 tooltip。core 每秒都会 emit 一次流量 tick
// （manager.js 的 TRAFFIC_TICK_MS），旧实现每次 onStatus 都重建菜单 + 重设 tooltip，
// 一秒一次（W7）。现在只有内容真的变了才碰托盘。
let lastSig = '';
let lastTip = '';

function icon() {
  const img = nativeImage.createFromPath(ICON);
  return img.isEmpty() ? nativeImage.createEmpty() : img.resize({ width: 16, height: 16 });
}

/** 真实现注入给纯逻辑模块的回调（tray-menu.js 不 require electron） */
function handlers() {
  return {
    warn: (...a) => log.warn(...a),
    selectNode: (name) => core.selectNode(name),
    connect: () => core.connect(),
    disconnect: () => core.disconnect(),
    showMain: () => require('./main').showMain(),
    openSettings: () => { require('./main').showMain(); require('./ui').navigate('settings'); },
    openUpdate: () => { require('./main').showMain(); require('./ui').navigate('settings'); },
    quit: () => require('./main').quit(),
  };
}

function buildTray() {
  tray = new Tray(icon());
  tray.setToolTip('Polaris');
  tray.setContextMenu(Menu.buildFromTemplate([{ label: 'Polaris', enabled: false }]));
  // 首次创建（以及销毁后重建）必须真的设一次菜单：签名状态跟着托盘一起重置，
  // 否则新托盘会以为「菜单还是旧的那份」而一个都不设（W7 的销毁路径）。
  lastSig = '';
  lastTip = '';
  tray.on('click', () => require('./main').showMain());
  tray.on('right-click', () => tray.popUpContextMenu());
  refreshTray();
  // 状态变化时刷新菜单与 tooltip。原先只有 buildTray() 调一次 refreshTray()，
  // 之后托盘永远停留在启动时的状态——启动时没连接，连上之后菜单里还是「未连接」，
  // 与主界面状态不一致（用户 2026-10-11 反馈）。
  core.onStatus(() => { refreshTray(); });
  return tray;
}

/** force=true 时无视签名，强制重设一次（留给需要强制刷新的调用方） */
async function refreshTray(force) {
  if (!tray) return;
  try {
    const st = await core.status();
    const nodes = (() => {
      try { return core.cachedNodes(); } catch (_) { return []; }
    })();

    const template = menu.buildTemplate(st, nodes, handlers());
    const sig = menu.signature(template);
    if (force || menu.shouldRebuild(lastSig, sig)) {
      tray.setContextMenu(Menu.buildFromTemplate(template));
      lastSig = sig;
    }

    const tip = menu.tooltipOf(st);
    if (force || tip !== lastTip) {
      tray.setToolTip(tip);
      lastTip = tip;
    }
  } catch (e) {
    log.warn('tray refresh failed:', e && e.message);
  }
}

/**
 * 窗口全关之后托盘是唯一入口：没有托盘就补建一个（B8 的 window-all-closed 兜底）。
 * 有托盘时直接复用，不会建出第二个图标。
 */
function ensureTray() {
  return tray || buildTray();
}

/** 退出时释放托盘图标（Windows 上不释放会在通知区留一个幽灵图标） */
function destroyTray() {
  try { if (tray) tray.destroy(); } catch (_) { /* 释放失败不影响退出 */ }
  tray = null;
  lastSig = '';
  lastTip = '';
}

module.exports = {
  buildTray, refreshTray, ensureTray, destroyTray,
  updateTray: refreshTray,
  getTray: () => tray,
};
