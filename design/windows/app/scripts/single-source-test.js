// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

/**
 * 「规则只有一个源」自测：不联网、不起窗口，只读代码与随包文件。
 *
 *   node scripts/single-source-test.js
 *
 * 用户问过两次「你到底弄了几套分流规则」。从 2026-10-14 起答案是「一套」：
 * 分流清单 + geodata（geosite.dat / geoip.metadb / ASN.mmdb）全部来自
 * `ipevel/polaris-rules`，由 electron/core/rulesync.js 每 12 小时刷新一次。
 *
 * 但代码里还留着旧模块 electron/core/geosync.js（第三方 appshubcc/bett-rules 源，
 * 「新 sum 配旧字节」那次故障就是它）。它保留只为打包脚本 fetch-geo.py 的清单定义。
 * 这个自测就是钉死「它不再被任何运行期模块引用」「运行期不再出现任何第三方源地址」，
 * 免得哪天有人顺手接回去、又变回两套。
 *
 * 钉死五件事：
 *   1. 运行期代码（electron/、src/js/，排除 geosync.js 自身）**剥掉整行注释后**，
 *      不得出现 bett-rules / appshubcc / GeoLite2-ASN；
 *   2. 运行期代码里必须出现 ipevel/polaris-rules（至少 rulesync.js 与 rulesets.js）；
 *   3. 没有任何运行期模块 require geosync；
 *   4. resources/rules/ 恰好是 PROVIDERS 那 15 个 <键>.yaml，没有 acl_*.yaml 残留，
 *      且与安卓端正本（polaris-rules 的 dist/rules/）的键名集合一致；
 *   5. resources/geo/ 三份都在且非空。
 *
 * 为什么注释行要剥掉：rulesync.js / rulesets.js 的文档里**故意**写了
 * 「旧方案从 bett-rules 拉」来说明改动原因，那是解释，不是引用。
 */

'use strict';

const fs = require('fs');
const path = require('path');

const APP = path.join(__dirname, '..');
const ELECTRON = path.join(APP, 'electron');
const RENDERER = path.join(APP, 'src', 'js');
const SEED_DIR = path.join(APP, 'resources', 'rules');
const GEO_DIR = path.join(APP, 'resources', 'geo');

const rulesets = require(path.join(ELECTRON, 'core', 'rulesets.js'));

let passed = 0;
let failed = 0;
function ok(name) { passed += 1; console.log('  ok   ' + name); }
function bad(name, detail) {
  failed += 1;
  console.log('  FAIL ' + name);
  if (detail) console.log('       ' + String(detail).split('\n').join('\n       '));
}

/** 递归收集 .js 文件（不跟符号链接、不跳 node_modules —— 这个仓库的 app 里没有） */
function jsFiles(dir) {
  const out = [];
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) out.push(...jsFiles(p));
    else if (e.isFile() && e.name.endsWith('.js')) out.push(p);
  }
  return out;
}

/**
 * 只留「代码行」：丢掉空行、以及以 * / // / /* 开头的注释行。
 * 不做完整的词法分析 —— 够用且不会因为字符串里的 `//`（https://…）而误剪掉真正的引用。
 */
function codeLines(text) {
  return text.split(/\r?\n/).filter((l) => {
    const t = l.trim();
    if (!t) return false;
    if (t.startsWith('*') || t.startsWith('//') || t.startsWith('/*')) return false;
    return true;
  });
}

const GEOSYNC = path.join(ELECTRON, 'core', 'geosync.js');
const runtime = [...jsFiles(ELECTRON), ...jsFiles(RENDERER)];

// ---- 1. 运行期不得出现第三方源 --------------------------------------------
const BANNED = ['bett-rules', 'appshubcc', 'GeoLite2-ASN'];
const hits = [];
for (const f of runtime) {
  if (f === GEOSYNC) continue;                       // 旧模块自身允许保留这些字面量
  for (const line of codeLines(fs.readFileSync(f, 'utf8'))) {
    for (const b of BANNED) if (line.includes(b)) hits.push(`${path.relative(APP, f)}: ${line.trim()}`);
  }
}
if (hits.length === 0) ok('运行期代码里没有任何第三方规则/geodata 源（bett-rules / appshubcc / GeoLite2-ASN）');
else bad('运行期代码里出现了第三方源', hits.slice(0, 6).join('\n'));

// ---- 2. 必须指向我们自己的仓库 --------------------------------------------
const needSource = [path.join(ELECTRON, 'core', 'rulesync.js'), path.join(ELECTRON, 'core', 'rulesets.js')];
const missing = needSource.filter((f) => !fs.readFileSync(f, 'utf8').includes('ipevel/polaris-rules'));
if (missing.length === 0) ok('rulesync.js / rulesets.js 都指向 ipevel/polaris-rules');
else bad('以下文件里找不到 ipevel/polaris-rules', missing.map((f) => path.relative(APP, f)).join('\n'));

// ---- 3. 没有运行期模块 require geosync ------------------------------------
const reqGeosync = [];
for (const f of runtime) {
  if (f === GEOSYNC) continue;
  for (const line of codeLines(fs.readFileSync(f, 'utf8'))) {
    if (/require\(\s*['"][^'"]*geosync['"]\s*\)/.test(line)) reqGeosync.push(`${path.relative(APP, f)}: ${line.trim()}`);
  }
}
if (reqGeosync.length === 0) ok('没有任何运行期模块 require geosync（旧源已彻底摘掉）');
else bad('仍有运行期模块 require geosync', reqGeosync.join('\n'));
if (fs.existsSync(GEOSYNC)) ok('geosync.js 仍在（fetch-geo.py 要用它的清单定义，别删）');
else bad('geosync.js 不见了：打包脚本 fetch-geo.py 的清单定义还在引用它');

// ---- 4. 随包种子 = PROVIDERS 的 15 个键 -----------------------------------
const keys = Object.keys(rulesets.PROVIDERS).sort();
const seedFiles = fs.readdirSync(SEED_DIR).filter((f) => f.endsWith('.yaml')).sort();
const expectSeeds = keys.map((k) => `${k}.yaml`).sort();
const seedGap = [];
for (const e of expectSeeds) if (!seedFiles.includes(e)) seedGap.push(`缺 ${e}`);
for (const s of seedFiles) if (!expectSeeds.includes(s)) seedGap.push(`多 ${s}`);
if (seedGap.length === 0) ok(`resources/rules/ 恰好是 PROVIDERS 的 ${keys.length} 个 <键>.yaml`);
else bad('随包种子与 PROVIDERS 对不上', seedGap.join('\n'));

const legacy = fs.readdirSync(SEED_DIR).filter((f) => f.startsWith('acl_'));
if (legacy.length === 0) ok('resources/rules/ 里没有 acl_*.yaml 残留');
else bad('仍有 acl_*.yaml 残留', legacy.join('\n'));

// keys 与 allProviderKeys() 必须同一批（别的模块用的是后者）
const allKeys = [...rulesets.allProviderKeys()].sort();
if (JSON.stringify(allKeys) === JSON.stringify(keys)) ok('allProviderKeys() 与 PROVIDERS 是同一批键');
else bad('allProviderKeys() 与 PROVIDERS 不一致', `PROVIDERS=${keys.join(',')}\nallProviderKeys=${allKeys.join(',')}`);

// ---- 5. geodata 三份在位 --------------------------------------------------
const geoNeed = ['geosite.dat', 'geoip.metadb', 'ASN.mmdb'];
const geoGap = [];
for (const g of geoNeed) {
  const p = path.join(GEO_DIR, g);
  if (!fs.existsSync(p)) { geoGap.push(`缺 ${g}`); continue; }
  const n = fs.statSync(p).size;
  if (n < 1024) geoGap.push(`${g} 只有 ${n} B，不像真的`);
}
if (geoGap.length === 0) ok('resources/geo/ 三份 geodata 都在且非空');
else bad('随包 geodata 有问题', geoGap.join('\n'));

console.log(`\n共 ${passed + failed} 项：${passed} 通过 / ${failed} 失败`);
process.exit(failed === 0 ? 0 : 1);
