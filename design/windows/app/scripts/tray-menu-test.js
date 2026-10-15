'use strict';
/**
 * 托盘自检（纯 Node，不需要 Electron、不需要真托盘、不联网）。
 *   跑法：node scripts/tray-menu-test.js
 *
 * W7：core 每秒都会 emit 一次流量 tick（manager.js 的 TRAFFIC_TICK_MS），
 *     旧 tray.js 每次 onStatus 都 Menu.buildFromTemplate + setContextMenu 一遍 ——
 *     一秒重建一次托盘菜单。这里用假 electron + 假 manager 钉死
 *     「内容没变就不重建、变了才重建」，并覆盖首次创建与销毁后重建。
 * B8：window-all-closed 的托盘兜底 —— ensureTray() 没有托盘就补建、有则复用。
 *
 * 假 electron 通过临时替换 Module._load 注入，真 electron 模块一个字节都没碰。
 */
const os = require('os');
const path = require('path');
const Module = require('module');

process.env.POLARIS_DATA_DIR = path.join(os.tmpdir(), 'polaris-tray-selftest');

let total = 0; let failed = 0;
function check(name, cond, detail) {
  total += 1; if (!cond) failed += 1;
  console.log((cond ? '  [ OK ] ' : '  [FAIL] ') + name + (!cond && detail ? '  ' + detail : ''));
}
function tryRequire(p) { try { return require(p); } catch (e) { return { __err: e }; } }

/* ---------------- 一、纯逻辑（tray-menu.js） ---------------- */
const menuMod = tryRequire('../electron/tray-menu');
check('W7 electron/tray-menu.js 存在（旧代码把菜单逻辑写死在 tray.js 里，没有这个模块）',
  !menuMod.__err, menuMod.__err && menuMod.__err.message);

if (!menuMod.__err) {
  const menu = menuMod;
  const H = {
    selectNode: () => Promise.resolve(), connect: () => Promise.resolve(), disconnect: () => Promise.resolve(),
    showMain: () => {}, openSettings: () => {}, openUpdate: () => {}, quit: () => {}, warn: () => {},
  };
  const NODES = [{ name: '香港01' }, { name: '日本02' }];
  const off = menu.buildTemplate({ connected: false, node: '' }, [], H);
  const on = menu.buildTemplate({ connected: true, node: '香港01' }, NODES, H);

  check('W7 未连接：首行「未连接」且不可点，动作是「连接」',
    off[0].label === '未连接' && off[0].enabled === false && off[2].label === '连接',
    off[0].label + ' / ' + off[2].label);
  check('W7 已连接：首行「已连接 · 节点」，动作是「断开连接」',
    on[0].label === '已连接 · 香港01' && on[2].label === '断开连接', on[0].label + ' / ' + on[2].label);

  const radios = on.filter((i) => i.type === 'radio');
  check('W7 节点是 radio 项：当前节点打勾并带 ● 前缀，其余带全角空格对齐',
    radios.length === 2 && radios[0].checked === true && radios[0].label === '● 香港01' &&
    radios[1].checked === false && radios[1].label === '　日本02',
    JSON.stringify(radios.map((r) => r.label + (r.checked ? '*' : ''))));

  const many = menu.buildTemplate({ connected: false, node: '' },
    Array.from({ length: 30 }, (_, i) => ({ name: 'N' + i })), H);
  check('W7 节点最多列 ' + menu.MAX_NODE_ITEMS + ' 个（菜单不能被节点列表撑爆）',
    many.filter((i) => i.type === 'radio').length === menu.MAX_NODE_ITEMS,
    String(many.filter((i) => i.type === 'radio').length));
  check('W7 没有节点时不出现「快速切换」这一组',
    !menu.buildTemplate({ connected: false, node: '' }, [], H).some((i) => i.label === '快速切换'));
  check('W7 菜单尾部固定项齐全（显示主界面/设置/检查更新/退出）',
    ['显示主界面', '设置', '检查更新', '退出'].every((l) => on.some((i) => i.label === l)));

  const sigOn1 = menu.signature(on);
  const sigOn2 = menu.signature(menu.buildTemplate({ connected: true, node: '香港01' }, NODES, H));
  check('W7 内容一致（只有回调闭包是新的）→ 签名一致', sigOn1 === sigOn2);
  check('W7 签名只取可见字段：换一批新闭包不触发重建', menu.shouldRebuild(sigOn1, sigOn2) === false);
  check('W7 连接状态变化 → 签名变', menu.shouldRebuild(sigOn1, menu.signature(off)) === true);
  check('W7 切换当前节点 → 签名变',
    menu.shouldRebuild(sigOn1, menu.signature(menu.buildTemplate({ connected: true, node: '日本02' }, NODES, H))) === true);
  check('W7 节点列表变化 → 签名变',
    menu.shouldRebuild(sigOn1, menu.signature(menu.buildTemplate({ connected: true, node: '香港01' }, NODES.concat([{ name: '美国03' }]), H))) === true);
  check('W7 文字变化 → 签名变（将来接多语言不会被漏掉）',
    menu.signature([{ label: 'A' }]) !== menu.signature([{ label: 'B' }]));
  check('W7 enabled 变化 → 签名变', menu.signature([{ label: 'A' }]) !== menu.signature([{ label: 'A', enabled: false }]));
  check('W7 tooltip 文案：未连接 / 已连接 · 节点',
    menu.tooltipOf({ connected: false }) === 'Polaris · 未连接' &&
    menu.tooltipOf({ connected: true, node: '香港01' }) === 'Polaris · 香港01');
}

// 接线部分要等异步的 tick；顶层 await 会让 node 把本文件当 ESM 解析（ERR_AMBIGUOUS_MODULE_SYNTAX）
(async () => {
  /* ---------------- 二、tray.js 的真实接线（假 electron + 假 manager） ---------------- */
  const state = { connected: false, node: '', nodes: [] };
  let statusListener = null;
  const fakeManager = {
    status: async () => ({ connected: state.connected, node: state.node }),
    cachedNodes: () => state.nodes,
    onStatus: (fn) => { statusListener = fn; },
    selectNode: async () => {}, connect: async () => {}, disconnect: async () => {},
  };

  const trays = [];
  const fakeElectron = {
    Menu: { buildFromTemplate: (t) => ({ __tpl: t }) },
    Tray: class {
      constructor() { this.menuCalls = 0; this.tipCalls = 0; this.destroyed = false; trays.push(this); }
      setToolTip(s) { this.tip = s; this.tipCalls += 1; }
      setContextMenu(m) { this.menu = m; this.menuCalls += 1; }
      on() {} popUpContextMenu() {} destroy() { this.destroyed = true; }
    },
    nativeImage: { createFromPath: () => ({ isEmpty: () => true }), createEmpty: () => ({}) },
    app: { isPackaged: false, getPath: () => '', on() {} },
  };

  const origLoad = Module._load;
  Module._load = function (request, parent) {
    if (request === 'electron') return fakeElectron;
    if (parent && /tray\.js$/.test(parent.filename)) {
      if (request === './core/manager') return fakeManager;
      if (request === './store') return { get: () => undefined, set: () => {}, patch: () => {} };
      if (request === './logger') return { info() {}, warn() {}, error() {}, stamp: () => '' };
    }
    return origLoad.apply(this, arguments);
  };
  const trayMod = tryRequire('../electron/tray.js');
  Module._load = origLoad;

  check('W7 tray.js 能加载', !trayMod.__err, trayMod.__err && trayMod.__err.message);

// 模拟 manager 的每秒状态回调：真 app 里 manager.js 的流量 tick 每秒 emit() 一次
const tick = async () => { if (statusListener) statusListener(); await new Promise((r) => setTimeout(r, 25)); };

  if (!trayMod.__err) {
    check('B8 tray.js 导出 ensureTray（window-all-closed 的托盘兜底）', typeof trayMod.ensureTray === 'function');
    check('B8 tray.js 导出 destroyTray（退出时释放图标）', typeof trayMod.destroyTray === 'function');

const t1 = trayMod.buildTray();
  await tick();
  // 建托盘时先垫一份占位菜单（避免右击时闪空菜单），随后第一次刷新设真实菜单
  check('W7 首次创建就设上了真实菜单（占位 → 未连接）',
    t1.menuCalls === 2 && !!t1.menu && t1.menu.__tpl[0].label === '未连接',
    '设菜单 ' + t1.menuCalls + ' 次 / ' + (t1.menu && t1.menu.__tpl[0].label));
  check('W7 首次创建写了一次 tooltip（占位 Polaris → Polaris · 未连接）',
    t1.tipCalls === 2 && t1.tip === 'Polaris · 未连接', t1.tipCalls + ' 次 / ' + t1.tip);
  check('W7 状态回调挂在 core.onStatus 上（否则托盘不跟状态走）', typeof statusListener === 'function');

  const baseMenu = t1.menuCalls;
  const baseTip = t1.tipCalls;

  // 每秒一次的流量 tick：状态与菜单内容一模一样
  await tick(); await tick();
  check('W7 状态没变时不再重建菜单（旧代码一秒一次）', t1.menuCalls === baseMenu, '实际 ' + t1.menuCalls + ' 次');
  check('W7 状态没变时不再写 tooltip（旧代码一秒一次）', t1.tipCalls === baseTip, '实际 ' + t1.tipCalls + ' 次');

  state.connected = true; state.node = '香港01'; state.nodes = [{ name: '香港01' }];
  await tick();
  check('W7 连上之后重建一次', t1.menuCalls === baseMenu + 1, '实际 ' + t1.menuCalls + ' 次');
  check('W7 重建后的菜单确实是「已连接 · 香港01」',
    !!t1.menu && t1.menu.__tpl[0].label === '已连接 · 香港01', t1.menu && t1.menu.__tpl[0].label);
  check('W7 tooltip 跟着变成「Polaris · 香港01」', t1.tip === 'Polaris · 香港01', t1.tip);

  await tick(); await tick();
  check('W7 连上之后再来的 tick 不再重建', t1.menuCalls === baseMenu + 1, '实际 ' + t1.menuCalls + ' 次');

  state.node = '日本02'; state.nodes = [{ name: '香港01' }, { name: '日本02' }];
  await tick();
  check('W7 快速切换节点 → 重建一次', t1.menuCalls === baseMenu + 2, '实际 ' + t1.menuCalls + ' 次');
  await tick();
  check('W7 节点列表稳定后不再重建', t1.menuCalls === baseMenu + 2, '实际 ' + t1.menuCalls + ' 次');

  if (typeof trayMod.destroyTray === 'function') {
    trayMod.destroyTray();
    check('W7 destroyTray 释放了图标且内部置空',
      trays[trays.length - 1].destroyed === true &&
      (typeof trayMod.getTray !== 'function' || trayMod.getTray() === null));
  }
  if (typeof trayMod.ensureTray === 'function') {
    const t2 = trayMod.ensureTray();
    await tick();
    check('B8 销毁后 ensureTray 重建了新托盘并设上了菜单',
      t2 !== t1 && t2.menuCalls === 2, '实际 ' + (t2 && t2.menuCalls) + ' 次');
    const t3 = trayMod.ensureTray();
    check('B8 已有托盘时 ensureTray 复用、不会建出第二个图标', t3 === t2 && trays.length === 2, '托盘数 ' + trays.length);
    await tick();
    check('W7 重建后的托盘同样按签名去重（同状态不再重复设）', t2.menuCalls === 2, '实际 ' + t2.menuCalls + ' 次');
  }
}

console.log('');
console.log('通过 ' + (total - failed) + ' 项，失败 ' + failed + ' 项');
process.exit(failed ? 1 : 0);
})();
