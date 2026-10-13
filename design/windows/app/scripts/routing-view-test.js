/**
 * 分流规则页视图的纯 Node 自测：不开窗口，直接把 views.js 里的 Views.routing 跑一遍，
 * 断言渲染出来的 HTML（组行数、出口徽标、顺序、说明文字、note）。
 *
 * 为什么要它：
 *   ① 「规则库每 12 小时自动更新」这行字以前写着 24 小时 —— A-61 把节拍改成 12 小时时
 *      漏掉了这处用户可见文案，靠肉眼才发现。钉住它，别再回退。
 *   ② 「Ⓜ️ 微软」组带的 note（云盘 API 直连会被重置）必须真的渲染出来，
 *      否则 note 字段加了也没人看得见（见 DEVNOTES A-64）。
 *
 * 用法：node scripts/routing-view-test.js
 */
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const SRC = path.join(__dirname, '..', 'src', 'js');

function loadViews() {
  const win = {};
  const ctx = vm.createContext({ window: win, console, Date, Math, JSON, Map, Set, Array, Number, String, RegExp, Object });
  for (const f of ['format.js', 'views.js']) {
    vm.runInContext(fs.readFileSync(path.join(SRC, f), 'utf8'), ctx, { filename: f });
  }
  if (!win.PolarisViews || !win.PolarisViews.routing) throw new Error('views.js 没有导出 Views.routing');
  return win;
}

let pass = 0;
let fail = 0;
function check(name, ok, detail) {
  if (ok) { pass += 1; console.log(`  [ OK ] ${name}${detail ? '  ' + detail : ''}`); }
  else { fail += 1; console.log(`  [FAIL] ${name}  ${detail || ''}`); }
}

const Views = loadViews().PolarisViews;

const NOTE = 'onedrive.live.com 直连会被重置；云盘打不开就把本组切到「节点选择」';
/** 第二行说明（sub）与 note 用的是同一个内联样式，所以数它的出现次数即可区分「有没有第三行」 */
const SUB_STYLE = 'font-size:11px;color:var(--text3);margin-top:2px';

/** 从整页 HTML 里切出某一行的片段（到下一个 data-ruleset-row 为止） */
function rowOf(html, name) {
  const key = `data-ruleset-row="${name}"`;
  const i = html.indexOf(key);
  if (i < 0) return '';
  const j = html.indexOf('data-ruleset-row="', i + key.length);
  return html.slice(i, j < 0 ? html.length : j);
}

const groups = [
  { name: '📲 电报消息', out: 'proxy', enabled: true, count: 1, inline: 2, default_on: true, live: true, now: '🇭🇰 香港 01' },
  { name: 'Ⓜ️ 微软', out: 'direct', enabled: true, count: 1, inline: 7, default_on: true, note: NOTE, live: true },
  { name: '🛑 广告拦截', out: 'block', enabled: false, count: 1, inline: 0, default_on: true, live: true },
];

const html = Views.routing({
  rulesets: {
    on: true,
    total: groups.length,
    groups,
    custom: [{ name: '我的组', out: 'proxy', enabled: true, inline: 3, custom: true, live: true }],
  },
});

check('页头写「12 小时」而不是 24 小时', /内置分流 · 规则库每 12 小时自动更新/.test(html), html.match(/内置分流[^<]*/)?.[0]);
check('页头没有残留的「24 小时」', !/24 小时/.test(html));
check('已启用计数按 enabled 统计（2/3）', /已启用 2\/3/.test(html));

const rowCount = (html.match(/data-ruleset-row="/g) || []).length;
check('行数 = 3 个内置组 + 1 个自定义组', rowCount === 4, `实测 ${rowCount}`);
const switchCount = (html.match(/data-ruleset="/g) || []).length;
check('每行一个开关', switchCount === 4, `实测 ${switchCount}`);

const msRow = rowOf(html, 'Ⓜ️ 微软');
const tgRow = rowOf(html, '📲 电报消息');
const adRow = rowOf(html, '🛑 广告拦截');

check('note 渲染在「Ⓜ️ 微软」那一行里', msRow.includes(NOTE));
check('note 全页只出现一次', (html.split(NOTE).length - 1) === 1);
check('带 note 的行有两段小字（说明 + note）', msRow.split(SUB_STYLE).length - 1 === 2, `实测 ${msRow.split(SUB_STYLE).length - 1}`);
check('没 note 的行只有一段小字', tgRow.split(SUB_STYLE).length - 1 === 1 && adRow.split(SUB_STYLE).length - 1 === 1);

check('出口徽标：proxy → 节点选择，并显示当前节点', /class="badge[^"]*">[^<]*<\/span>/.test(tgRow) && tgRow.includes('🇭🇰 香港 01'));
check('出口徽标：direct → 直连', /class="badge[^"]*">直连<\/span>/.test(msRow), (msRow.match(/class="badge[^"]*">[^<]*<\/span>/) || [''])[0]);
check('出口徽标：block → 拦截', /class="badge[^"]*">拦截<\/span>/.test(adRow), (adRow.match(/class="badge[^"]*">[^<]*<\/span>/) || [''])[0]);

const i1 = html.indexOf('data-ruleset-row="📲 电报消息"');
const i2 = html.indexOf('data-ruleset-row="Ⓜ️ 微软"');
const i3 = html.indexOf('data-ruleset-row="🛑 广告拦截"');
check('组按数组顺序渲染（顺序 = 匹配优先级）', i1 > 0 && i1 < i2 && i2 < i3, `${i1} < ${i2} < ${i3}`);

check('自定义组：带编辑入口与「N 条自定义规则」', /data-ruleset-edit="我的组"/.test(html) && /3 条自定义规则/.test(html));
check('拖动顺序的抓手在（顺序可调）', (html.match(/data-ruleset-grip=/g) || []).length === 4);

const off = Views.routing({ rulesets: { on: false, total: 1, groups: [groups[0]], custom: [] } });
check('关闭本地分流时给出提示，且内置组仍可配置（开关照样画）', /面板自带的分流规则正在生效/.test(off) && /data-ruleset-row="📲 电报消息"/.test(off));

const none = Views.routing({ rulesets: { on: true, groups: [], custom: [] } });
check('没有内置规则集时给出空状态文案', /缺少 resources\/rules 规则集/.test(none));

console.log(`\n分流规则页视图自测：通过 ${pass} 项，失败 ${fail} 项`);
process.exit(fail ? 1 : 0);
