// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

/**
 * 国旗一致性自测：不联网、不起窗口，只比对文件与表。
 *
 *   node scripts/flag-codes-test.js
 *
 * 钉死四件事：
 *   1. `src/js/flags.js` 的 FLAG_CODES 与 `src/assets/flags/*.svg` **完全一致**
 *      —— 这是「表和图谁缺了谁」的唯一防线，两边任何一边动了都会红；
 *   2. `region.js` 的 detect() 在这批图上**必然画得出旗**
 *      —— 也就是 detect 出口的每个码都能在 FLAG_CODES 里找到（SW/UK 必须已归一）；
 *   3. 归一化行为：SW → CH、UK → GB，且只碰这两个；
 *   4. 与安卓端 `app/src/main/assets/flags/` 的**图名集合相同**
 *      —— 安卓仓库在同一个检出里时顺手验；不在就跳过（不失败），
 *         因为 Windows 侧不该因为安卓仓库没克隆就过不了门禁。
 *
 * 为什么要有第 4 条：两端共用同一批旗是刻意的（用户 2026-10-13 要求
 * 「Windows 端补旗帜，对齐安卓」）。哪天有人在 Windows 单独加了一张图，
 * 这里会立刻发现，而不是等用户看到一边有一边没有。
 */

'use strict';

const assert = require('assert');
const fs = require('fs');
const path = require('path');

const APP = path.join(__dirname, '..');
const JS_DIR = path.join(APP, 'src', 'js');
const FLAG_DIR = path.join(APP, 'src', 'assets', 'flags');

const { detect, normalizeCode, FLAG_LABEL } = require(path.join(APP, 'electron', 'core', 'region'));

let passed = 0;
let failed = 0;
function ok(name) { passed += 1; console.log('  ok   ' + name); }
function bad(name, detail) {
  failed += 1;
  console.log('  FAIL ' + name + (detail ? '\n         ' + detail : ''));
}

/* ---------- 1. 表 vs 图 ---------- */

/** 从 flags.js 文本里抠出 FLAG_CODES 的字面量（IIFE 无法 require，只能解析文本）。 */
function codesFromJs() {
  const src = fs.readFileSync(path.join(JS_DIR, 'flags.js'), 'utf8');
  const m = src.match(/const FLAG_CODES = new Set\(\[([\s\S]*?)\]\);/);
  assert.ok(m, 'flags.js 里找不到 FLAG_CODES 声明');
  return m[1].match(/"([a-z]{2})"/g).map((s) => s.slice(1, -1));
}

function svgCodes() {
  return fs.readdirSync(FLAG_DIR)
    .filter((f) => f.endsWith('.svg'))
    .map((f) => f.slice(0, -4).toLowerCase());
}

const table = codesFromJs();
const onDisk = svgCodes();
const tableSet = new Set(table);
const diskSet = new Set(onDisk);

try {
  assert.strictEqual(tableSet.size, table.length, 'FLAG_CODES 有重复项');
  ok(`FLAG_CODES 无重复（${table.length} 个）`);
} catch (e) { bad('FLAG_CODES 无重复', e.message); }

try {
  assert.deepStrictEqual([...tableSet].sort(), [...diskSet].sort(), '表与图不一致');
  ok(`FLAG_CODES 与 src/assets/flags/*.svg 完全一致（${onDisk.length} 张）`);
} catch (e) {
  const inTableNotDisk = [...tableSet].filter((c) => !diskSet.has(c));
  const onDiskNotTable = [...diskSet].filter((c) => !tableSet.has(c));
  bad('FLAG_CODES 与 src/assets/flags/*.svg 完全一致',
    `表里多（图缺）：${inTableNotDisk.join(' ') || '无'} ｜ 图里多（表缺）：${onDiskNotTable.join(' ') || '无'}`);
}

try {
  const sorted = [...table].join(',') === [...table].sort().join(',');
  assert.ok(sorted, 'FLAG_CODES 未按字母序排列');
  ok('FLAG_CODES 按字母序排列（便于人工比对）');
} catch (e) { bad('FLAG_CODES 按字母序排列', e.message); }

/* ---------- 2. detect 出口的码必须都画得出旗 ---------- */

// 把 region.js 认识的全部码逼出来：FLAG_LABEL 的键 + 每个中文名各自当节点名试一遍。
const candidates = new Set(Object.keys(FLAG_LABEL));
for (const label of Object.values(FLAG_LABEL)) candidates.add(label);
// 再补一批真实面板节点名的形态
for (const probe of [
  '🇭🇰 香港 01', '🇺🇸 US-LA-02', '🇯🇵 东京 IEPL', '🇰🇷 首尔', '🇲🇴 澳门',
  '🇩🇪 法兰克福', '🇬🇧 London', '🇨🇭 苏黎世', '🇸🇪 Stockholm', 'HK-Premium',
  'Switzerland', 'Zurich', '奥地利', 'Austria', 'Vietnam', '越南', '新加坡 SG',
]) candidates.add(probe);

const missing = [];
for (const name of candidates) {
  const r = detect(name);
  if (!r) continue;
  if (!tableSet.has(r.code.toLowerCase())) missing.push(`${name} → ${r.code}`);
  if (!r.label) missing.push(`${name} → code=${r.code} 但 label 为空`);
}
try {
  assert.deepStrictEqual(missing, [], 'detect 能输出画不出旗的码');
  ok(`detect 出口的码全部画得出旗（试了 ${candidates.size} 个名字）`);
} catch (e) { bad('detect 出口的码全部画得出旗', missing.join(' ｜ ')); }

/* ---------- 3. 归一化 ---------- */

try {
  assert.strictEqual(normalizeCode('SW'), 'CH');
  assert.strictEqual(normalizeCode('sw'), 'CH');
  assert.strictEqual(normalizeCode('UK'), 'GB');
  assert.strictEqual(normalizeCode('uk'), 'GB');
  ok('归一化：SW → CH、UK → GB（含小写）');
} catch (e) { bad('归一化 SW/UK', e.message); }

try {
  assert.strictEqual(normalizeCode('HK'), 'HK');
  assert.strictEqual(normalizeCode('us'), 'US');
  assert.strictEqual(normalizeCode(''), '');
  assert.strictEqual(normalizeCode(null), null);
  ok('归一化不误伤其他码与空值');
} catch (e) { bad('归一化不误伤其他码', e.message); }

// detect('瑞士') 必须落到 CH（这一条正是当初为什么要有别名表）
try {
  const r = detect('瑞士 苏黎世');
  assert.ok(r, 'detect 认不出「瑞士」');
  assert.strictEqual(r.code, 'CH');
  assert.strictEqual(r.label, '瑞士');
  ok('detect("瑞士 苏黎世") → { code: CH, label: 瑞士 }');
} catch (e) { bad('detect 瑞士 → CH', e.message); }

/* ---------- 4. 与安卓端图名集合相同 ---------- */

// 安卓仓库是**同级目录**（E:\AI\Github\slte），不是本仓库的子目录，所以 CI 上必然找不到。
// 路径可用 POLARIS_ANDROID_DIR 指过来（和 routing-sync-test 用 POLARIS_GO_TABLE 一个路子）；
// 本机找不到就在 CI 上真红（POLARIS_ANDROID_REQUIRED=1），不静默变绿。
const ANDROID_DIR = process.env.POLARIS_ANDROID_DIR || path.join('E:', 'AI', 'Github', 'slte');
const ANDROID_FLAG_DIR = path.join(ANDROID_DIR, 'app', 'src', 'main', 'assets', 'flags');

if (fs.existsSync(ANDROID_FLAG_DIR)) {
  // 两端都归一成「去掉 .svg 的小写名」再比 —— 目录里带扩展名，FLAG_CODES 里不带。
  const androidCodes = fs.readdirSync(ANDROID_FLAG_DIR)
    .filter((f) => f.endsWith('.svg'))
    .map((f) => f.slice(0, -4).toLowerCase())
    .sort();
  const androidSet = new Set(androidCodes);
  try {
    assert.deepStrictEqual([...diskSet].sort(), androidCodes, '与安卓端旗子集合不同');
    ok(`与安卓端 app/src/main/assets/flags/ 集合相同（各 ${androidCodes.length} 张）`);
  } catch (e) {
    const winOnly = [...diskSet].sort().filter((c) => !androidSet.has(c));
    const andOnly = androidCodes.filter((c) => !diskSet.has(c));
    bad('与安卓端旗子集合相同',
      `仅 Windows 有：${winOnly.join(' ') || '无'} ｜ 仅安卓有：${andOnly.join(' ') || '无'}`);
  }
} else if (process.env.POLARIS_ANDROID_REQUIRED === '1') {
  bad('与安卓端旗子集合相同', `找不到安卓仓库：${ANDROID_FLAG_DIR}（POLARIS_ANDROID_REQUIRED=1 时不许跳过）`);
} else {
  console.log(`  skip 与安卓端比对（未找到 ${ANDROID_FLAG_DIR}，可用 POLARIS_ANDROID_DIR 指定）`);
}

/* ---------- 5. 渲染层安全：src 只能由白名单拼出 ---------- */

// flags.js 是给浏览器用的 IIFE，在 node 里用最小 window 桩跑一遍，验证它真的按白名单办事。
const flagsSrc = fs.readFileSync(path.join(JS_DIR, 'flags.js'), 'utf8');
const win = {};
// eslint-disable-next-line no-new-func
new Function('window', flagsSrc)(win);
const F = win.PolarisFlags;

try {
  assert.ok(F && typeof F.flagImg === 'function', 'flags.js 没有挂上 window.PolarisFlags');
  ok('flags.js 能装载并导出 flagImg');
} catch (e) { bad('flags.js 能装载', e.message); }

if (F) {
  try {
    assert.strictEqual(F.count, onDisk.length);
    assert.strictEqual(F.flagImg('hk'), '<img class="flag" src="assets/flags/hk.svg" alt="" loading="lazy">');
    assert.strictEqual(F.flagImg('HK'), '<img class="flag" src="assets/flags/hk.svg" alt="" loading="lazy">');
    ok('flagImg 正常路径输出预期标签（大小写都吃）');
  } catch (e) { bad('flagImg 正常路径', e.message); }

  // 不存在的码 / 注入尝试 / 空值 —— 必须一个字都不输出
  for (const evil of ['sw', 'uk2', '', null, undefined, '../../etc/passwd', 'hk.svg', 'H K', 'zz', '12']) {
    try {
      assert.strictEqual(F.flagImg(evil), '', `flagImg(${JSON.stringify(evil)}) 应返回空串`);
    } catch (e) { bad(`flagImg(${JSON.stringify(evil)}) 返回空串`, e.message); }
  }
  ok('flagImg 对未登记码/路径穿越/空值一律返回空串（不给裂图、不拼外部路径）');
}

/* ---------- 收尾 ---------- */

console.log('');
if (failed) {
  console.log(`国旗一致性自测失败：${failed} 项没过（通过 ${passed} 项）`);
  process.exit(1);
}
console.log(`国旗一致性自测全部通过（${passed} 项）`);
