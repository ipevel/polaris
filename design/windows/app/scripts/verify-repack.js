'use strict';
/**
 * 重打包后断言（ci/win-repack-assertions）：拿**源目录**（重打包前、已写清单的 $tmp）与**重打包后的 zip**比，
 * 全部 fail-closed（不符就 exit 1），并给出可复现的负例证据（见 verify-repack-test.js）。
 *
 *   node scripts/verify-repack.js --before <源目录> --zip <重打包后的 zip>
 *
 * 五条：
 *   ① 条目名：不含反斜杠、不以 / 开头、不含 .. 段（zip 规范用正斜杠，反斜杠会让部分解压器建出怪名字）
 *   ② 文件条目集合与源目录**归一化后**逐一相同（分隔符统一 /、大小写统一），不是只比数量
 *   ③ 目录差异：目录「存在」= 有目录条目 或 有文件落在它下面（打包器可不写目录条目）；
 *      只有「源里非空 + 新 zip 里既无目录条目也无文件落其下」才算真丢 → 红；空目录被丢 → 结论输出
 *   ④ 条目名大小写与源**逐字一致**（② 归一化比较抓不到大小写变化，这条单独抓）
 *   ⑤ 负例证据：本文件的判定逻辑可被纯 Node 单测直接喂坏 zip（见 test 文件）
 *
 * 为什么读 zip 中央目录而不是解压后比：Windows 文件系统大小写不敏感，解压出来的树看不出大小写变化；
 * 条目名的真身只在中央目录里。
 */
const fs = require('fs');
const path = require('path');

function fail(msg, extra) {
  console.error('[FAIL] ' + msg);
  if (extra) console.error(String(extra).split('\n').map((l) => '       ' + l).join('\n'));
  process.exitCode = 1;
  return false;
}

/** 读 zip 中央目录里的条目名（含目录条目，目录以 / 结尾） */
function readZipEntries(file) {
  const buf = fs.readFileSync(file);
  const min = Math.max(0, buf.length - 65557);
  let eocd = -1;
  for (let i = buf.length - 22; i >= min; i -= 1) {
    if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error('不是 zip（找不到 EOCD）：' + file);
  const count = buf.readUInt16LE(eocd + 10);
  let off = buf.readUInt32LE(eocd + 16);
  const names = [];
  for (let n = 0; n < count; n += 1) {
    if (buf.readUInt32LE(off) !== 0x02014b50) throw new Error('中央目录项签名不对 @' + off);
    const nameLen = buf.readUInt16LE(off + 28);
    const extraLen = buf.readUInt16LE(off + 30);
    const commentLen = buf.readUInt16LE(off + 32);
    names.push(buf.slice(off + 46, off + 46 + nameLen).toString('utf8'));
    off += 46 + nameLen + extraLen + commentLen;
  }
  return names;
}

/** 源目录里的相对路径（文件 + 目录，均以 / 分隔） */
function walkSource(root) {
  const files = [];
  const dirs = [];
  const walk = (rel) => {
    for (const e of fs.readdirSync(path.join(root, rel), { withFileTypes: true })) {
      const r = rel ? rel + '/' + e.name : e.name;
      if (e.isDirectory()) { dirs.push(r); walk(r); }
      else if (e.isFile()) files.push(r);
    }
  };
  walk('');
  return { files: files.sort(), dirs: dirs.sort() };
}
const norm = (s) => s.replace(/\\/g, '/').toLowerCase();

function main() {
  const argv = process.argv.slice(2);
  const before = argv[argv.indexOf('--before') + 1];
  const zip = argv[argv.indexOf('--zip') + 1];
  if (!before || !zip || argv.indexOf('--before') < 0 || argv.indexOf('--zip') < 0) {
    console.error('用法：node scripts/verify-repack.js --before <源目录> --zip <zip>');
    process.exit(2);
  }
  const entries = readZipEntries(zip);
  const src = walkSource(before);
  const fileEntries = entries.filter((n) => !n.endsWith('/'));
  const dirEntries = entries.filter((n) => n.endsWith('/')).map((n) => n.replace(/\/$/, ''));
  let ok = true;

  /* ① 条目名合规（fail-closed） */
  const bad = entries.filter((n) => n.indexOf('\\') >= 0 || n.startsWith('/') || n.split('/').indexOf('..') >= 0 || !n);
  if (bad.length) ok = fail('① 条目名不合规（含反斜杠 / 绝对路径 / ..）：' + bad.length + ' 个', bad.slice(0, 20).join('\n')) && ok;
  else console.log('  [ OK ] ① 条目名合规：' + entries.length + ' 个条目，全部正斜杠、无 .. 段');

  /* ② 文件条目集合：归一化后逐一相同（fail-closed） */
  const a = src.files.map(norm).sort();
  const b = fileEntries.map(norm).sort();
  const missing = a.filter((x) => b.indexOf(x) < 0);
  const extra = b.filter((x) => a.indexOf(x) < 0);
  if (missing.length || extra.length) {
    ok = fail('② 文件条目集合与源目录不一致（归一化后）', '丢：' + (missing.join('、') || '无') + '\n多：' + (extra.join('、') || '无')) && ok;
  } else console.log('  [ OK ] ② 文件条目集合逐一相同（归一化后，' + fileEntries.length + ' 个文件）');

  /* ④ 大小写逐字一致（fail-closed；② 归一化后抓不到） */
  const aCase = src.files.slice().sort();
  const bCase = fileEntries.slice().sort();
  const caseDiff = aCase.filter((x, i) => bCase[i] !== x);
  if (caseDiff.length) {
    ok = fail('④ 条目名大小写与源不一致：' + caseDiff.length + ' 个', caseDiff.slice(0, 20).join('\n')) && ok;
  } else console.log('  [ OK ] ④ 条目名大小写与源逐字一致（' + fileEntries.length + ' 个）');

  /* ③ 目录差异（2026-10-15 修：原判据误报）——
        目录「存在」= zip 里有该目录条目 **或** 有任意文件条目以 <dir>/ 为前缀。
        为什么：打包器（Compress-Archive）**只写文件条目、不写目录条目**，而目录条目对解压器本来就是冗余的
        （解压时按文件路径重建）。原来的判据只查目录条目，于是「每个非空目录都被判丢」——dryRun
        run 38767099173 的 windows job 就是被这条误报挡下的（96 个文件一个没丢、①②④ 全绿）。
        现在只有「源里该目录**非空** + 新 zip 里既无目录条目、也没有任何文件落在它下面」才算真丢 → 红。 */
  const srcDirsNorm = src.dirs.map(norm).sort();
  const zipDirsNorm = dirEntries.map(norm).sort();
  const fileNorms = fileEntries.map(norm);
  const dirCovered = (d) => {
    if (zipDirsNorm.indexOf(d) >= 0) return true;
    const prefix = d + '/';
    return fileNorms.some((n) => n.startsWith(prefix));
  };
  const droppedDirs = srcDirsNorm.filter((d) => !dirCovered(d));
  const emptyDropped = [];
  const meaningfulDropped = [];
  for (const d of droppedDirs) {
    const real = src.dirs.find((x) => norm(x) === d);
    const isEmpty = fs.readdirSync(path.join(before, real)).length === 0;
    (isEmpty ? emptyDropped : meaningfulDropped).push(real);
  }
  if (meaningfulDropped.length) {
    ok = fail('③ 有**非空**目录被丢（对解压结构有意义，必须报主管）：' + meaningfulDropped.length + ' 个', meaningfulDropped.join('\n')) && ok;
  } else if (emptyDropped.length) {
    console.log('  [ OK ] ③ 空目录被丢 ' + emptyDropped.length + ' 个（Compress-Archive 不保留空目录）');
    console.log('       结论：可接受——便携包不依赖空目录（data/ 由运行期 paths.ensureAll 创建）；');
    console.log('       若将来包内出现必须存在的空目录，改为在该目录放一个 .keep 占位文件。');
    console.log('       被丢清单：' + emptyDropped.join('、'));
  } else console.log('  [ OK ] ③ 无目录丢失（打包器可以不写目录条目；文件落点已覆盖全部源目录）');

  if (!ok) { console.error('重打包后断言未通过（fail-closed）'); process.exit(1); }
  console.log('重打包后断言全部通过');
  process.exit(0);
}

module.exports = { readZipEntries, walkSource, norm };
if (require.main === module) main();