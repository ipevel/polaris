/**
 * 分流表跨语言一致性校验：Windows 的 electron/core/rulesets.js 必须与安卓端
 * kernel-core/src/main/golang/native/config/routing/routing_table.go 逐条一致。
 *
 * 为什么需要这个测试：2026-10-12 的「分流规则本地化」只改了 Go 内核那一半，
 * Windows 的 JS 镜像还是旧的 type:http 远程 provider 方案 —— 两端分流行为当场分叉，
 * 而且是静默分叉（两边都能正常启动，只是命中结果不同）。这类漂移靠人眼 review 发现不了，
 * 所以做成测试：真的把 Go 源码解析出来跟 JS 表对一遍。
 *
 * 在 windows-portable 分支上 kernel-core/ 不存在（文件所有权不在这条分支），
 * 默认会跳过并明确说明；在 main / android-portable 上会真跑。
 *
 * ⚠️ 但「跳过」在 CI 里等于一个永远绿的假通过，所以有 **strict 模式**：
 *   设 `POLARIS_GO_TABLE_REQUIRED=1` 后找不到 Go 表就是**失败**（退出码 1），不再跳过。
 *   `.github/workflows/ci.yml` 的 windows job 会先把 main 的 kernel-core/ 取下来，
 *   再用 `POLARIS_GO_TABLE=<路径>` + `POLARIS_GO_TABLE_REQUIRED=1` 调这里 —— 这样
 *   这条门禁要么真比对通过、要么红，不存在「静默跳过还显示绿」。
 *
 * 运行：node scripts/routing-sync-test.js
 *       POLARIS_GO_TABLE=<main 里的 routing_table.go> POLARIS_GO_TABLE_REQUIRED=1 node scripts/routing-sync-test.js
 */
'use strict';

const fs = require('fs');
const path = require('path');

const here = __dirname;
const GO_REL = ['kernel-core', 'src', 'main', 'golang', 'native', 'config', 'routing', 'routing_table.go'];
const REPO_ROOT = (() => {
  let dir = here;
  for (let i = 0; i < 8; i++) {
    const cand = path.join(dir, 'kernel-core', 'src', 'main', 'golang', 'native', 'config', 'routing', 'routing_table.go');
    if (fs.existsSync(cand)) return dir;
    const parent = path.dirname(dir);
    if (parent === dir) break;
    dir = parent;
  }
  return null;
})();

// POLARIS_GO_TABLE 可显式指定 Go 源文件位置：在只有单平台分支的 worktree 里
// 想校验另一条分支的内核表时用它，不必把 kernel-core 搬过来。
// POLARIS_GO_TABLE_REQUIRED=1 时禁止跳过（CI 用），找不到就是失败。
const GO_OVERRIDE = process.env.POLARIS_GO_TABLE ? path.resolve(process.env.POLARIS_GO_TABLE) : null;
const REQUIRED = process.env.POLARIS_GO_TABLE_REQUIRED === '1';

if (!REPO_ROOT && !GO_OVERRIDE) {
  if (REQUIRED) {
    console.error('[routing-sync] 失败：POLARIS_GO_TABLE_REQUIRED=1，但既没找到 kernel-core/…/routing_table.go，');
    console.error('[routing-sync] 也没给 POLARIS_GO_TABLE。这条门禁在 CI 里不允许静默跳过。');
    console.error('[routing-sync] 修法：workflow 里先把 main 的 kernel-core/ 取下来，再指 POLARIS_GO_TABLE 过去。');
    process.exit(1);
  }
  console.log('[routing-sync] 未找到 kernel-core/…/routing_table.go（不在 main/android-portable 上），跳过。');
  console.log('[routing-sync] 这条测试的意义就在两端都在时才生效，跳过不是失败。');
  console.log('[routing-sync] （CI 请设 POLARIS_GO_TABLE_REQUIRED=1，那样就不会走到这里）');
  process.exit(0);
}

const GO = GO_OVERRIDE || path.join(REPO_ROOT, ...GO_REL);

// 显式指了路径却不存在 = 配置写错了，必须炸出来，不能当成「跳过」
if (!fs.existsSync(GO)) {
  console.error(`[routing-sync] 失败：Go 分流表不存在：${GO}`);
  if (GO_OVERRIDE) console.error('[routing-sync] POLARIS_GO_TABLE 指到了不存在的文件。');
  process.exit(1);
}

const src = fs.readFileSync(GO, 'utf8');
console.log(`[routing-sync] Go 表：${GO}`);
const rulesets = require(path.join(here, '..', 'electron', 'core', 'rulesets.js'));

const fails = [];
const eq = (label, a, b) => {
  const sa = JSON.stringify(a);
  const sb = JSON.stringify(b);
  if (sa !== sb) fails.push(`${label}\n    Go: ${sa}\n    JS: ${sb}`);
};

/* ---------- 解析 Go 的 Providers 表 ----------
 * 形如：  "acl_banad":          {"acl_banad", "", "classical", "text", false},
 * 字段：  Key, URL(空=本地), Behavior, Format, NoResolve
 */
const providers = {};
const provRe = /^\s*"([^"]+)":\s*\{"([^"]*)",\s*"([^"]*)",\s*"([^"]*)",\s*"([^"]*)",\s*(true|false)\},?/gm;
let m;
while ((m = provRe.exec(src))) {
  // 位置结构体 {Key, URL, Behavior, Format, NoResolve}：Key 重复一次，跳过它
  const [, key, , url, behavior, format, noResolve] = m;
  providers[key] = { url, behavior, format, noResolve: noResolve === 'true' };
}

/* ---------- 解析 Go 的 Table ----------
 * 逐条 {Name: ..., DefaultOn: ..., DefaultOut: ..., Providers: ..., InlineRules: ...}。
 * InlineRules 有单行与 append(...) 两种写法，直接捞「带引号且含逗号的字符串」即可，
 * 因为组名与 provider 键都不含逗号，只有内联规则含逗号 —— 顺序也天然保留。
 */
const tableSrc = src.slice(src.indexOf('var Table = []RuleGroup{'));
const blocks = tableSrc
  .split(/\n\t\{Name: /)
  .slice(1)
  .map((b) => '{Name: ' + b.split(/\n\t\}\n/)[0]);

const goTable = blocks.map((b) => {
  const name = /Name:\s*"([^"]*)"/.exec(b)[1];
  const defaultOn = /DefaultOn:\s*(true|false)/.exec(b)[1] === 'true';
  const defaultOut = /DefaultOut:\s*"([^"]*)"/.exec(b)[1];
  const pm = /Providers:\s*\[\]string\{([^}]*)\}/.exec(b);
  const providers2 = pm
    ? pm[1].split(',').map((s) => s.trim().replace(/^"|"$/g, '')).filter(Boolean)
    : [];
  const rules = [];
  const strRe = /"([^"]+)"/g;
  let sm;
  while ((sm = strRe.exec(b))) {
    if (sm[1].includes(',')) rules.push(sm[1]);
  }
  return { name, defaultOn, defaultOut, providers: providers2, inlineRules: rules };
});

/* ---------- ① 组数、顺序、名称 ---------- */
eq('组数', goTable.length, rulesets.TABLE.length);
goTable.forEach((g, i) => {
  const j = rulesets.TABLE[i];
  if (!j) { fails.push(`第 ${i} 组 Go 有「${g.name}」而 JS 表没有`); return; }
  eq(`[${i}] 组名`, g.name, j.name);
  eq(`[${g.name}] defaultOn`, g.defaultOn, !!j.defaultOn);
  eq(`[${g.name}] defaultOut`, g.defaultOut, j.defaultOut);
  eq(`[${g.name}] providers`, g.providers, j.providers || []);
  eq(`[${g.name}] inlineRules`, g.inlineRules, j.inlineRules || []);
});

/* 反向：JS 不能多出 Go 没有的组（改名/新增都会静默分叉） */
rulesets.TABLE.forEach((j) => {
  if (!goTable.some((g) => g.name === j.name)) fails.push(`JS 有组「${j.name}」而 Go 表没有`);
});

/* ---------- ② Providers 表逐条一致 ---------- */
const goKeys = Object.keys(providers).sort();
const jsKeys = Object.keys(rulesets.PROVIDERS).sort();
eq('provider 键集合', goKeys, jsKeys);
goKeys.forEach((k) => {
  const g = providers[k];
  const j = rulesets.PROVIDERS[k];
  if (!j) { fails.push(`provider「${k}」Go 表有而 JS 表没有`); return; }
  eq(`provider[${k}] url`, g.url, j.url);
  eq(`provider[${k}] behavior`, g.behavior, j.behavior);
  eq(`provider[${k}] format`, g.format, j.format);
  eq(`provider[${k}] noResolve`, g.noResolve, !!j.noResolve);
});
/* 反向：JS 的 provider 键也不能多（多出来的键内核那边没有对应文件契约） */
jsKeys.forEach((k) => {
  if (!providers[k]) fails.push(`provider「${k}」JS 表有而 Go 表没有`);
});

/* ---------- ③ 两端生成的 rule-provider 配置都必须是 type:file ---------- */
jsKeys.forEach((k) => {
  const c = rulesets.providerConfig(k);
  if (!c) { fails.push(`providerConfig(${k}) 返回 null`); return; }
  if (c.type !== 'file') fails.push(`providerConfig(${k}).type=${c.type}，必须是 file`);
  if ('url' in c) fails.push(`providerConfig(${k}) 不该带 url`);
  if ('interval' in c) fails.push(`providerConfig(${k}) 不该带 interval`);
});

/* ---------- 结果 ---------- */
const goRules = goTable.reduce((n, g) => n + g.inlineRules.length, 0);
console.log(`[routing-sync] Go 表 ${goTable.length} 组 / ${goKeys.length} 个 provider / ${goRules} 条内联规则`);
console.log(`[routing-sync] JS 表 ${rulesets.TABLE.length} 组 / ${jsKeys.length} 个 provider / ${fails.length ? '' : '已比对'}`);

if (fails.length) {
  console.error('\n[routing-sync] FAIL — 两端分流表不一致：');
  fails.forEach((f) => console.error('  · ' + f));
  if (goTable.length !== rulesets.TABLE.length || goKeys.length !== jsKeys.length) {
    console.error('\n提示：本门禁比的是「本分支的 JS 表」与「Go 表」（CI 里取 main，本地默认取当前 worktree）。');
    console.error('      刚改过分组或 providers 时常见情形是：Go 侧也改了，但还没同步进 main。');
    console.error('      要比对本地那份 Go 改动：POLARIS_GO_TABLE=<routing_table.go 的绝对路径> npm run test:routing-sync');
  }
  process.exit(1);
}
console.log('[routing-sync] PASS — 两端逐条一致（组名/顺序/默认开关/出站/内联规则/providers/本地性）');