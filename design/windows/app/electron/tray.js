'use strict';
/** 托盘：连接状态 + 快速节点切换 + 显示/退出。常驻，不随窗口关闭。 */

const path = require('path');
const { Menu, Tray, nativeImage, app } = require('electron');
const log = require('./logger');
const store = require('./store');
const core = require('./core/manager');

const ICON = path.join(__dirname, '..', 'src', 'assets', 'p-icon.png');

let tray = null;

function icon() {
  const img = nativeImage.createFromPath(ICON);
  return img.isEmpty() ? nativeImage.createEmpty() : img.resize({ width: 16, height: 16 });
}

async function buildMenu() {
  const st = await core.status();
  const nodes = (() => {
    try { return core.cachedNodes(); } catch (_) { return []; }
  })();

  const nodeItems = nodes.slice(0, 15).map((n) => ({
    label: `${n.name === st.node ? '● ' : '　'}${n.name}`,
    type: 'radio',
    checked: n.name === st.node,
    click: () => core.selectNode(n.name).catch((e) => log.warn('tray node switch failed:', e.message)),
  }));

  const template = [
    { label: st.connected ? `已连接 · ${st.node}` : '未连接', enabled: false },
    { type: 'separator' },
    {
      label: st.connected ? '断开连接' : '连接',
      click: () => (st.connected ? core.disconnect() : core.connect())
        .catch((e) => log.warn('tray toggle failed:', e.message)),
    },
    ...(nodeItems.length ? [{ label: '快速切换', enabled: false }, ...nodeItems] : []),
    { type: 'separator' },
    { label: '显示主界面', click: () => require('./main').showMain() },
    { label: '设置', click: () => { require('./main').showMain(); require('./ui').navigate('settings'); } },
    { label: '检查更新', click: () => { require('./main').showMain(); require('./ui').navigate('settings'); } },
    { type: 'separator' },
    { label: '退出', click: () => require('./main').quit() },
  ];

  return Menu.buildFromTemplate(template);
}

function buildTray() {
  tray = new Tray(icon());
  tray.setToolTip('Polaris');
  tray.setContextMenu(Menu.buildFromTemplate([{ label: 'Polaris', enabled: false }]));
  tray.on('click', () => require('./main').showMain());
  tray.on('right-click', () => tray.popUpContextMenu());
  refreshTray();
  return tray;
}

async function refreshTray() {
  if (!tray) return;
  try {
    tray.setContextMenu(await buildMenu());
    const st = await core.status();
    tray.setToolTip(st.connected ? `Polaris · ${st.node}` : 'Polaris · 未连接');
  } catch (e) {
    log.warn('tray refresh failed:', e && e.message);
  }
}

module.exports = { buildTray, refreshTray, updateTray: refreshTray };
