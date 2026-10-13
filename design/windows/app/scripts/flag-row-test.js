// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

/**
 * 节点行国旗的渲染自测：不开窗口、不联网，直接把 `Views.nodes` 跑出 HTML 再断言。
 *
 *   node scripts/flag-row-test.js
 *
 * 为什么要有这一条（flag-codes-test.js 已经验了表和图）：
 * 前面那条只证明「表和图一致、取图函数安全」，**不证明节点行真的把旗子画出来了**。
 * 行模板里少一个 `${flagImg(...)}`、或者调用点在 `<span class="nm">` 之后，
 * 前一条测试全绿而用户什么也看不到。这条测试盯的就是那个接缝：
 *
 *   1. 有 region_code 的节点 → 行首出现对应旗子 `<img class="flag" ...>`
 *   2. 只有 region 文本（旧快照）→ 靠文本反查也要出旗
 *   3. 认不出地区的节点 → 不出旗，也**不能出裂图 / 空 src**
 *   4. 旗子必须在节点名**之前**（视觉上旗子跟着名字走，不是飘在右边）
 *   5. 策略组行 / 直连行不带旗（它们本来就没有地区）
 */

'use strict';

const fs = require('fs');
const path = require('path');
const vm = require('vm');

const SRC = path.join(__dirname, '..', 'src', 'js');

let pass = 0;
let fail = 0;
function check(name, ok, detail) {
  if (ok) { pass += 1; console.log(`  [ OK ] ${name}${detail ? '  ' + detail : ''}`); }
  else { fail += 1; console.log(`  [FAIL] ${name}  ${detail || ''}`); }
}

/** 与 traffic-view-test.js 同一套装载方式：把渲染层脚本塞进 vm，拿 window 上的导出。 */
function loadRenderer() {
  const win = {};
  const ctx = vm.createContext({ window: win, console, Date, Math, JSON, Map, Set, Array, Number, String, RegExp, Object });
  for (const f of ['format.js', 'flags.js', 'views.js']) {
    vm.runInContext(fs.readFileSync(path.join(SRC, f), 'utf8'), ctx, { filename: f });
  }
  if (!win.PolarisViews || !win.PolarisViews.nodes) throw new Error('views.js 没有导出 Views.nodes');
  if (!win.PolarisFlags) throw new Error('flags.js 没有导出 PolarisFlags');
  return win;
}

const win = loadRenderer();
const Views = win.PolarisViews;

/**
 * 一份够 Views.nodes 跑起来的最小状态。
 *
 * 注意 `structural`/`builtin` 的语义：Views.nodes 会把这类组从**卡片列表**里滤掉，
 * 但它们仍然要留在 `s.groups` 里，否则成员行会把「🚀 节点选择」当成一个真实节点名。
 */
function state(nodes, groups, extra) {
  // openGroups 的键就是**分组名本身**（views.js:196 `isOpen(name)`，同一份键又写进
  // data-acc），不是内部 id —— 这里传错键会导致整张卡片折叠、成员行一行都渲染不出来。
  const openGroups = {};
  for (const g of groups) openGroups[g.name] = true;
  return Object.assign({
    nodeFilter: '',
    openGroups,
    node: '🇭🇰 香港01',
    connected: true,
    groups,
    nodes,
  }, extra || {});
}

const GROUPS = [
  { name: '🚀 节点选择', primary: true, builtin: false, structural: false, count: 4, now: '🇭🇰 香港01', options: ['🇭🇰 香港01', '🇺🇸 US-LA-02', '🇨🇭 苏黎世 01', '神秘节点 xyz'] },
  { name: '🐟 漏网之鱼', builtin: false, structural: true, count: 0, now: 'DIRECT', options: ['DIRECT'] },
];

const NODES = [
  { name: '🇭🇰 香港01', region: '香港', region_code: 'HK', latency: 42, offline: false },
  { name: '🇺🇸 US-LA-02', region: '美国', region_code: 'US', latency: 180, offline: false },
  { name: '🇨🇭 苏黎世 01', region: '瑞士', region_code: 'CH', latency: 210, offline: false },
  { name: '神秘节点 xyz', region: '其他', region_code: '', latency: -1, offline: false },
];

function render(nodes, groups, extra) {
  return Views.nodes(state(nodes, groups, extra));
}

/**
 * 从渲染结果里切出一行。
 *
 * 为什么不用 `<div…>[\s\S]*?</div>`：那是**最短匹配**，行内只要还有嵌套 div
 * （成员行里的徽章就是 `<div>`）就会提前收尾，切出一个缺尾巴的碎片，
 * 断言于是时对时错。这里改成按 div 嵌套计数找配对的那个 `</div>`。
 *
 * 也不按 `data-node="<节点名>"` 定位：模板输出的是 `h(name)` 转义后的值，
 * 名字里带 emoji 时字面量对不上。用行内名字文案来认。
 */
const stripComments = (html) => html.replace(/<!--[\s\S]*?-->/g, '');

function allRows(html) {
  const src = stripComments(html);
  const open = /<div class="node-row"/g;
  const out = [];
  let m;
  while ((m = open.exec(src)) !== null) {
    let i = m.index + 4;          // 跳过开头的 `<div`
    let depth = 1;
    while (i < src.length && depth > 0) {
      const nextOpen = src.indexOf('<div', i);
      const nextClose = src.indexOf('</div>', i);
      if (nextClose === -1) break;
      if (nextOpen !== -1 && nextOpen < nextClose) { depth += 1; i = nextOpen + 4; }
      else { depth -= 1; i = nextClose + 6; if (depth === 0) out.push(src.slice(m.index, i)); }
    }
  }
  return out;
}

function findRow(html, name) {
  return allRows(html).find((r) => r.includes(`<span class="nm">${name}</span>`)) || null;
}

/* ---------- 1. 有 region_code：出旗 ---------- */

const html = render(NODES, GROUPS);

for (const [code, lower] of [['HK', 'hk'], ['US', 'us'], ['CH', 'ch']]) {
  const want = `<img class="flag" src="assets/flags/${lower}.svg"`;
  check(`${code} 节点行首出现旗子`, html.includes(want), want);
}

/* ---------- 2. 旗子在节点名之前 ---------- */

{
  const row = findRow(html, '🇨🇭 苏黎世 01');
  if (!row) {
    check('旗子渲染在节点名之前', false, '找不到 🇨🇭 苏黎世 01 的行');
  } else {
    const atFlag = row.indexOf('<img class="flag"');
    const atName = row.indexOf('<span class="nm">');
    check('旗子渲染在节点名之前', atFlag > -1 && atName > -1 && atFlag < atName,
      `flag@${atFlag} · name@${atName}`);
  }
}

/* ---------- 3. 只有 region 文本（旧快照）也要出旗 ---------- */

const legacy = render(
  [
    { name: '🇯🇵 东京 IEPL', region: '日本', latency: 88, offline: false },   // 没有 region_code
    { name: '🇩🇪 法兰克福', region: '德国', latency: 190, offline: false },
  ],
  [{ name: '🚀 节点选择', primary: true, count: 2, now: '🇯🇵 东京 IEPL', options: ['🇯🇵 东京 IEPL', '🇩🇪 法兰克福'] }],
);
check('只有 region 文本时靠反查出旗（JP）', legacy.includes('assets/flags/jp.svg'));
check('只有 region 文本时靠反查出旗（DE）', legacy.includes('assets/flags/de.svg'));

/* ---------- 4. 认不出地区：不出旗，也不能有裂图 ---------- */

const unknownRow = findRow(html, '神秘节点 xyz');
check('认不出地区的节点行存在', !!unknownRow);
if (unknownRow) {
  check('认不出地区时行内没有旗子', !unknownRow.includes('<img class="flag"'));
}
check('整个渲染结果里没有空 src 的 img', !/<img[^>]*src=""/.test(html));
check('整个渲染结果里没有 src="undefined"', !/src="undefined"/.test(html));
check('整个渲染结果里没有 src="null"', !/src="null"/.test(html));

/* ---------- 5. 策略组行 / 直连行不带旗 ---------- */

// 两条真实约束（都是 views.js 的现行语义，照抄过来别自己想当然）：
//   · `memberNames` 会把**自己组名**从选项里滤掉 → 主组不会作为自己的出口出现；
//     要让"策略组作为出口"这一行出现，得让主组指向另一个组（🌍 国外）。
//   · 出口行的名字是**标签**不是节点名：DIRECT → 「直连」、主组 → 「跟随「🚀 节点选择」」。
const withExit = render(
  [{ name: 'DIRECT', region: '', region_code: '', latency: -1, offline: false }],
  [
    { name: '🚀 节点选择', primary: true, count: 2, now: 'DIRECT', options: ['DIRECT', '🌍 国外'] },
    { name: '🌍 国外', count: 0, now: '—', options: [] },
  ],
);
const exitRow = findRow(withExit, '直连');
check('DIRECT 行存在且不带旗', !!exitRow && !exitRow.includes('<img class="flag"'));

const groupRow = findRow(withExit, '🌍 国外');
check('策略组行存在且不带旗', !!groupRow && !groupRow.includes('<img class="flag"'));

check('整组没有任何真实节点时，一行旗子都不该有',
  allRows(withExit).filter((r) => r.includes('<img class="flag"')).length === 0);

/* ---------- 6. 一批真实地区的整体覆盖 ---------- */

const real = [
  ['🇭🇰 香港 01', '香港', 'HK'], ['🇲🇴 澳门 01', '澳门', 'MO'], ['🇹🇼 台湾 01', '台湾', 'TW'],
  ['🇯🇵 东京 01', '日本', 'JP'], ['🇰🇷 首尔 01', '韩国', 'KR'], ['🇸🇬 新加坡 01', '新加坡', 'SG'],
  ['🇺🇸 洛杉矶 01', '美国', 'US'], ['🇬🇧 伦敦 01', '英国', 'GB'], ['🇩🇪 法兰克福 01', '德国', 'DE'],
  ['🇻🇳 越南 01', '越南', 'VN'], ['🇦🇺 悉尼 01', '澳大利亚', 'AU'], ['🇨🇦 多伦多 01', '加拿大', 'CA'],
];
const realHtml = render(
  real.map(([name, region, code]) => ({ name, region, region_code: code, latency: 60, offline: false })),
  [{ name: '🚀 节点选择', primary: true, count: real.length, now: real[0][0], options: real.map((r) => r[0]) }],
);
const missing = real.filter(([, , code]) => !realHtml.includes(`assets/flags/${code.toLowerCase()}.svg`));
check('12 个真实面板地区的旗子全部渲染出来', missing.length === 0,
  missing.length ? '缺：' + missing.map((r) => r[2]).join(' ') : `共 ${real.length} 面`);

/* ---------- 收尾 ---------- */

console.log('');
if (fail) {
  console.log(`节点行国旗渲染自测失败：${fail} 项没过（通过 ${pass} 项）`);
  process.exit(1);
}
console.log(`节点行国旗渲染自测全部通过（${pass} 项）`);
