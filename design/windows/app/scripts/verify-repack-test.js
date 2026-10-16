'use strict';
/**
 * verify-repack.js 的自检（⑤ 负例证据）：故意构造坏 zip，断言必须红。
 *   跑法：node scripts/verify-repack-test.js
 *
 * 覆盖：① 反斜杠 / .. / 绝对路径；② 集合丢文件 / 多文件；③ 非空目录被丢必须红、空目录被丢可接受且有结论；
 *       ④ 只改大小写（② 归一化后相同）也必须红。
 */
const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawnSync } = require('child_process');

const CHECKER = path.join(__dirname, 'verify-repack.js');
const BASE = fs.mkdtempSync(path.join(os.homedir(), '.polaris-repack-'));
const SRC = path.join(BASE, 'src');
const ZIPDIR = path.join(BASE, 'zip');

let total = 0; let failed = 0;
function check(name, cond, detail) {
  total += 1; if (!cond) failed += 1;
  console.log((cond ? '  [ OK ] ' : '  [FAIL] ') + name + (!cond && detail ? '  ' + detail : ''));
}

/* ---------- 最小 zip 写入（stored，支持任意条目名与目录条目） ---------- */
const CRC_TABLE = (() => { const t = new Int32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c; } return t; })();
function crc32(buf) { let c = -1; for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8); return (c ^ -1) >>> 0; }
function buildZip(entries) {
  const locals = []; const centrals = []; let offset = 0;
  for (const e of entries) {
    const name = Buffer.from(e.name, 'utf8');
    const data = Buffer.isBuffer(e.data) ? e.data : Buffer.from(String(e.data || ''), 'utf8');
    const crc = crc32(data);
    const lh = Buffer.alloc(30);
    lh.writeUInt32LE(0x04034b50, 0); lh.writeUInt16LE(20, 4); lh.writeUInt16LE(0x0800, 6); lh.writeUInt16LE(0, 8);
    lh.writeUInt16LE(0, 10); lh.writeUInt16LE(0, 12); lh.writeUInt32LE(crc, 14);
    lh.writeUInt32LE(data.length, 18); lh.writeUInt32LE(data.length, 22);
    lh.writeUInt16LE(name.length, 26); lh.writeUInt16LE(0, 28);
    locals.push(lh, name, data);
    const ch = Buffer.alloc(46);
    ch.writeUInt32LE(0x02014b50, 0); ch.writeUInt16LE(20, 4); ch.writeUInt16LE(20, 6); ch.writeUInt16LE(0x0800, 8);
    ch.writeUInt16LE(0, 10); ch.writeUInt16LE(0, 12); ch.writeUInt16LE(0, 14);
    ch.writeUInt32LE(crc, 16); ch.writeUInt32LE(data.length, 20); ch.writeUInt32LE(data.length, 24);
    ch.writeUInt16LE(name.length, 28); ch.writeUInt16LE(0, 30); ch.writeUInt16LE(0, 32);
    ch.writeUInt16LE(0, 34); ch.writeUInt16LE(0, 36); ch.writeUInt32LE(0, 38); ch.writeUInt32LE(offset, 42);
    centrals.push(ch, name);
    offset += 30 + name.length + data.length;
  }
  const centralBuf = Buffer.concat(centrals);
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0); eocd.writeUInt16LE(0, 4); eocd.writeUInt16LE(0, 6);
  eocd.writeUInt16LE(entries.length, 8); eocd.writeUInt16LE(entries.length, 10);
  eocd.writeUInt32LE(centralBuf.length, 12); eocd.writeUInt32LE(offset, 16); eocd.writeUInt16LE(0, 20);
  return Buffer.concat([...locals, centralBuf, eocd]);
}

/* ---------- 源目录（模拟重打包前的 $tmp，已含清单） ---------- */
const SRC_FILES = [
  ['Polaris.exe', 'EXE'],
  ['resources/app.asar', 'ASAR'],
  ['resources/core/mihomo.exe', 'CORE'],
  ['resources/polaris-files.sha256', 'MANIFEST'],
  ['locales/zh-CN.pak', 'PAK'],
];
function resetSrc() {
  fs.rmSync(BASE, { recursive: true, force: true });
  fs.mkdirSync(SRC, { recursive: true });
  fs.mkdirSync(ZIPDIR, { recursive: true });
  for (const [rel, data] of SRC_FILES) {
    fs.mkdirSync(path.dirname(path.join(SRC, rel)), { recursive: true });
    fs.writeFileSync(path.join(SRC, rel), data);
  }
  fs.mkdirSync(path.join(SRC, 'empty-dir'), { recursive: true });   // 空目录：允许被丢
}
const goodEntries = () => SRC_FILES.map(([rel, data]) => ({ name: rel, data })).concat([
  { name: 'resources/', data: '' }, { name: 'resources/core/', data: '' },
  { name: 'locales/', data: '' },
  // 故意不写 empty-dir/：模拟 Compress-Archive 丢空目录
]);
function runCase(name, entries) {
  const zip = path.join(ZIPDIR, name + '.zip');
  fs.writeFileSync(zip, buildZip(entries));
  const r = spawnSync(process.execPath, [CHECKER, '--before', SRC, '--zip', zip], { encoding: 'utf8' });
  return { code: r.status, out: String(r.stdout || '') + String(r.stderr || '') };
}

console.log('重打包后断言 · 负例自检');

/* 正向：应该通过，并且空目录被丢要有结论 */
resetSrc();
const okCase = runCase('good', goodEntries());
check('正向：合法 zip 退出码 0', okCase.code === 0, okCase.out.trim().split('\n').slice(-3).join(' | '));
check('正向：① 条目名合规', /① 条目名合规/.test(okCase.out));
check('正向：② 集合逐一相同', /② 文件条目集合逐一相同/.test(okCase.out));
check('正向：④ 大小写逐字一致', /④ 条目名大小写与源逐字一致/.test(okCase.out));
check('正向：③ 空目录被丢有结论（可接受 + 为什么 + 将来怎么办）',
  /③ 空目录被丢 1 个/.test(okCase.out) && /结论：可接受/.test(okCase.out) && /运行期 paths\.ensureAll 创建/.test(okCase.out) && /\.keep 占位/.test(okCase.out), okCase.out.trim().split('\n').slice(-6).join(' | '));

/* ① 三类坏条目名 */
resetSrc();
const bs = runCase('backslash', goodEntries().map((e) => (e.name === 'resources/app.asar' ? { name: 'resources\\app.asar', data: e.data } : e)));
check('① 反斜杠条目名 → 红', bs.code === 1 && /① 条目名不合规/.test(bs.out), bs.out.trim().split('\n')[0]);
resetSrc();
const dotdot = runCase('dotdot', goodEntries().concat([{ name: '../evil.txt', data: 'x' }]));
check('① .. 段条目名 → 红', dotdot.code === 1 && /① 条目名不合规/.test(dotdot.out));
resetSrc();
const abs = runCase('abs', goodEntries().concat([{ name: '/abs.txt', data: 'x' }]));
check('① 绝对路径条目名 → 红', abs.code === 1 && /① 条目名不合规/.test(abs.out));

/* ② 丢/多文件 */
resetSrc();
const miss = runCase('missing', goodEntries().filter((e) => e.name !== 'resources/core/mihomo.exe'));
check('② 丢一个文件 → 红并列出丢了谁', miss.code === 1 && /② 文件条目集合与源目录不一致/.test(miss.out) && /resources\/core\/mihomo\.exe/.test(miss.out), miss.out.trim().split('\n').slice(0, 3).join(' | '));
resetSrc();
const extra = runCase('extra', goodEntries().concat([{ name: 'ghost.dll', data: 'x' }]));
check('② 多一个文件 → 红并列出多了谁', extra.code === 1 && /② 文件条目集合与源目录不一致/.test(extra.out) && /ghost\.dll/.test(extra.out));

/* ④ 只改大小写：② 归一化后相同，④ 必须红 */
resetSrc();
const caseZip = runCase('case', goodEntries().map((e) => (e.name === 'Polaris.exe' ? { name: 'POLARIS.EXE', data: e.data } : e)));
check('④ 只改大小写 → ④ 红（② 归一化后不算差异）', caseZip.code === 1 && /④ 条目名大小写与源不一致/.test(caseZip.out) && !/② 文件条目集合与源目录不一致/.test(caseZip.out), caseZip.out.trim().split('\n').slice(0, 3).join(' | '));

/* ③ 非空目录被丢 → 红（并要求单独报主管） */
resetSrc();
const dirDrop = runCase('dir-drop', goodEntries().filter((e) => e.name !== 'locales/zh-CN.pak' && e.name !== 'locales/'));
check('③ 非空目录被丢 → 红并提示报主管', dirDrop.code === 1 && /③ 有\*\*非空\*\*目录被丢/.test(dirDrop.out) && /必须报主管/.test(dirDrop.out), dirDrop.out.trim().split('\n').slice(0, 3).join(' | '));

/* ③（2026-10-15 修）打包器不写目录条目但文件都在 → 必须绿（CI 真实现场） */
resetSrc();
const noDir = runCase('no-dir-entries', SRC_FILES.map(([rel, data]) => ({ name: rel, data })));
check('③ 目录条目缺失但文件都在 → 绿（不再误报）', noDir.code === 0, noDir.out.trim().split('\n').slice(-4).join(' | '));
check('③ 该场景仍打印空目录结论（可接受 + 为什么 + 将来怎么办）',
  /③ 空目录被丢 1 个/.test(noDir.out) && /结论：可接受/.test(noDir.out) && /运行期 paths\.ensureAll 创建/.test(noDir.out) && /\.keep 占位/.test(noDir.out), noDir.out.trim().split('\n').slice(-5).join(' | '));
check('③ 该场景不报「非空目录被丢」', !/非空\*\*目录被丢/.test(noDir.out));

/* ③ 某目录的文件整批丢失 → 必须红 */
resetSrc();
const dirLost = runCase('dir-files-lost', goodEntries().filter((e) => e.name.indexOf('resources/core/') !== 0));
check('③ 某目录文件整批丢失 → 红并点名该目录', dirLost.code === 1 && /③ 有\*\*非空\*\*目录被丢/.test(dirLost.out) && /resources\/core/.test(dirLost.out), dirLost.out.trim().split('\n').slice(0, 5).join(' | '));
/* ③（issue #24）workflow 同款 fixture：只含一个空目录 + 一个根文件 → 必须绿 + 打印结论 */
const FX = path.join(BASE, 'fx');
const FXZ = path.join(BASE, 'fx.zip');
fs.rmSync(FX, { recursive: true, force: true });
fs.mkdirSync(path.join(FX, 'empty-dir'), { recursive: true });
fs.writeFileSync(path.join(FX, 'readme.txt'), 'fixture');
fs.writeFileSync(FXZ, buildZip([{ name: 'readme.txt', data: 'fixture' }]));   // 故意不写 empty-dir/（Compress-Archive 不写目录条目）
const fxRun = spawnSync(process.execPath, [CHECKER, '--before', FX, '--zip', FXZ], { encoding: 'utf8' });
const fxOut = String(fxRun.stdout || '') + String(fxRun.stderr || '');
check('#24 workflow 同款 fixture（只有空目录 + 一个根文件）→ 绿', fxRun.status === 0, fxOut.trim().split('\n').slice(-3).join(' | '));
check('#24 该 fixture 的输出必须含「空目录被丢」与那句结论',
  /空目录被丢 1 个/.test(fxOut) && /结论：可接受/.test(fxOut), fxOut.trim().split('\n').slice(-5).join(' | '));

/* 用法错 → 退出码 2 */
const usage = spawnSync(process.execPath, [CHECKER], { encoding: 'utf8' });
check('用法错 → 退出码 2', usage.status === 2);

fs.rmSync(BASE, { recursive: true, force: true });
console.log('\nverify-repack：' + (total - failed) + ' passed / ' + failed + ' failed（共 ' + total + ' 项）');
process.exit(failed === 0 ? 0 : 1);