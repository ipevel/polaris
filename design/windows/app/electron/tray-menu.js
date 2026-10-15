'use strict';
/**
 * 托盘菜单的**纯逻辑**：模板构造 + 内容签名 + tooltip 文案。
 *
 * 这里故意不 require electron（Tray/Menu 留在 tray.js）：纯 Node 自检才能直接钉住
 * 「菜单内容没变就不重建」这条规则（W7）。模板只装数据与回调，不碰 Electron 对象。
 */

const MAX_NODE_ITEMS = 15;

/**
 * 菜单模板。handlers 由 tray.js 注入（真实现里是 core.* / main.* / ui.*）。
 * 回调统一用 Promise.resolve(...) 包一层：自检注入的假 handler 可能不返回 Promise，
 * 直接 .catch 会 TypeError（真实 handler 一定返回 Promise）。
 */
function buildTemplate(st, nodes, handlers) {
  const s = st || {};
  const h = handlers || {};
  const warn = h.warn || (() => {});
  const list = Array.isArray(nodes) ? nodes : [];

  const nodeItems = list.slice(0, MAX_NODE_ITEMS).map((n) => ({
    label: `${n.name === s.node ? '● ' : '　'}${n.name}`,
    type: 'radio',
    checked: n.name === s.node,
    click: () => Promise.resolve(h.selectNode && h.selectNode(n.name))
      .catch((e) => warn('tray node switch failed:', e && e.message)),
  }));

  return [
    { label: s.connected ? `已连接 · ${s.node}` : '未连接', enabled: false },
    { type: 'separator' },
    {
      label: s.connected ? '断开连接' : '连接',
      click: () => Promise.resolve(s.connected ? (h.disconnect && h.disconnect()) : (h.connect && h.connect()))
        .catch((e) => warn('tray toggle failed:', e && e.message)),
    },
    ...(nodeItems.length ? [{ label: '快速切换', enabled: false }, ...nodeItems] : []),
    { type: 'separator' },
    { label: '显示主界面', click: () => h.showMain && h.showMain() },
    { label: '设置', click: () => h.openSettings && h.openSettings() },
    { label: '检查更新', click: () => h.openUpdate && h.openUpdate() },
    { type: 'separator' },
    { label: '退出', click: () => h.quit && h.quit() },
  ];
}

/**
 * 菜单内容签名：只取看得见的字段（类型/文字/选中/可用）。
 * click 闭包每次都是新函数，**不参与**比较 —— 这正是旧代码里
 * 「内容一模一样、每秒仍被 Menu.buildFromTemplate 重建一次」的根源（W7）。
 */
function signature(template) {
  return (Array.isArray(template) ? template : [])
    .map((i) => [
      i && i.type ? i.type : 'normal',
      (i && i.label) || '',
      i && i.checked ? 1 : 0,
      i && i.enabled === false ? 0 : 1,
    ].join('\u0001'))
    .join('\u0002');
}

function shouldRebuild(prevSig, nextSig) { return prevSig !== nextSig; }

function tooltipOf(st) {
  const s = st || {};
  return s.connected ? `Polaris · ${s.node}` : 'Polaris · 未连接';
}

module.exports = { MAX_NODE_ITEMS, buildTemplate, signature, shouldRebuild, tooltipOf };
