/**
 * 流量页视图的纯 Node 自测：不开窗口，直接把 views.js 里的 Views.traffic 跑一遍，
 * 断言渲染出来的 HTML（柱子数量、逐日明细行数、合计、口径文案）。
 *   用法：node scripts/traffic-view-test.js
 */
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const SRC = path.join(__dirname, '..', 'src', 'js');

function loadViews() {
  const win = {};
  const ctx = vm.createContext({ window: win, console, Date, Math, JSON, Map, Set, Array, Number, String });
  for (const f of ['format.js', 'views.js']) {
    vm.runInContext(fs.readFileSync(path.join(SRC, f), 'utf8'), ctx, { filename: f });
  }
  if (!win.PolarisViews || !win.PolarisViews.traffic) throw new Error('views.js 没有导出 Views.traffic');
  return win;
}

let pass = 0;
let fail = 0;
function check(name, ok, detail) {
  if (ok) { pass += 1; console.log(`  [ OK ] ${name}${detail ? '  ' + detail : ''}`); }
  else { fail += 1; console.log(`  [FAIL] ${name}  ${detail || ''}`); }
}

const win = loadViews();
const Views = win.PolarisViews;
const dom = new Date().getDate();
const ym = new Date().toISOString().slice(0, 7);

/** 造一份面板逐日明细：date 用 YYYY-MM-DD，到今天为止，缺的那天面板就是不给 */
function siteLog(fromDay) {
  const out = [];
  for (let i = fromDay; i <= dom; i += 1) {
    const key = `${ym}-${String(i).padStart(2, '0')}`;
    if (i === 4) continue;                                  // 面板这天没记录
    out.push({ date: key, download: i * 1_000_000_000, upload: i * 200_000_000, ts: 0 });
  }
  return out;
}

function base(extra) {
  return Object.assign({
    settings: { authed: true },
    traffic: { range: 'month', backend: 'xboard', site_log: true, account: null, total_down: '—', total_up: '—' },
    trafficLog: [],
    trafficSeries: { points: [], unit: 'MB' },
  }, extra || {});
}

const stat = (html) => ({
  bars: (html.match(/<rect /g) || []).length,
  lines: (html.match(/<line /g) || []).length,
  segs: (html.match(/data-range=/g) || []).length,
  tip: /traffic-tip/.test(html),
  labels: (html.match(/class="section-label">([^<]*)</g) || []).map((s) => s.replace(/.*>/, '')),
  rows: (html.match(/<div class="row"[\s\S]*?<\/div>/g) || []).length,
  text: html,
});

console.log('\n[1] 面板有逐日明细（主路径）');
{
  const s = base({ trafficLog: siteLog(1) });
  const html = Views.traffic(s);
  const q = stat(html);
  check('渲染不抛错且有内容', html.length > 500, `${html.length} 字节`);
  check('每天一根柱（1 号~今天）', q.bars === dom, `${q.bars} 根 / ${dom} 天`);
  check('只有一条基线，没有虚线网格', q.lines === 1, `${q.lines} 条 line`);
  check('区间切换按钮已经没了', q.segs === 0, `${q.segs} 个`);
  check('折线悬停读数已经没了', q.tip === false);
  check('第一段是本月口径', /月 1 日起/.test(q.labels[0] || ''), q.labels[0]);
  check('标了来源是面板明细', /面板明细/.test(q.labels[0] || ''), q.labels[0]);
  check('有「本月合计」这一行', /本月合计/.test(html));
  check('区间从本月 1 号起', new RegExp(`${ym}-01 ~ ${ym}-`).test(html), (html.match(/-01 ~ /) || [''])[0]);
  // 合计必须等于「面板给了的那几天之和」——第 4 天面板没记录、补的是 0，不能算进去
  const exp = s.trafficLog.reduce((a, r) => ({ d: a.d + r.download, u: a.u + r.upload }), { d: 0, u: 0 });
  const fb = win.PolarisFormat.bytes;
  check('本月合计 = 面板逐日之和', html.includes(fb(exp.d)) && html.includes(fb(exp.u)),
    `期望 下 ${fb(exp.d)} / 上 ${fb(exp.u)}`);
}

console.log('\n[2] 面板没有明细（退回本机统计）');
{
  const pts = [];
  for (let i = 1; i <= dom; i += 1) pts.push({ label: `${new Date().getMonth() + 1}/${i}`, down: i, up: i / 2 });
  const s = base({ trafficLog: [], trafficSeries: { points: pts, unit: 'MB' } });
  const html = Views.traffic(s);
  const q = stat(html);
  check('仍然每天一根柱', q.bars === dom, `${q.bars} 根`);
  check('标了来源是本机统计', /本机统计/.test(q.labels[0] || ''), q.labels[0]);
}

console.log('\n[3] 什么都没数据（空态）');
{
  const html = Views.traffic(base({}));
  check('给的是本月空态', /本月还没有用量记录/.test(html));
  check('空态下不画柱状图', !/<svg/.test(html));
  check('空态下不列每日明细', !/每日明细/.test(html));
}

console.log('\n[4] 只有 1 号有数据（月初第一天，柱宽不能撑爆）');
{
  const key = `${ym}-01`;
  const s = base({ trafficLog: [{ date: key, download: 5e9, upload: 1e9, ts: 0 }] });
  const html = Views.traffic(s);
  const w = (html.match(/<rect [^>]*width="([\d.]+)"/) || [])[1];
  check('柱宽被限住（≤44）', Number(w) <= 44.001, `width=${w}`);
  check('零用量日留了短桩', (html.match(/<rect /g) || []).length === new Date().getDate(), `${(html.match(/<rect /g) || []).length} 根`);
}

console.log(`\n通过 ${pass} 项，失败 ${fail} 项`);
process.exit(fail ? 1 : 0);