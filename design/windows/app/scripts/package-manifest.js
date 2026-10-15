'use strict';
/**
 * 包内清单：生成 / 校验（b 笔，发布侧）。
 *
 *   node scripts/package-manifest.js write  <包目录>   # 写 <包目录>/resources/polaris-files.sha256
 *   node scripts/package-manifest.js verify <包目录>   # 校验清单存在且与产物一致
 *
 * 为什么要有它：更新器（a 笔）把「缺清单 / 哈希不符」当**拒绝安装**的硬闸门，所以发版产物里
 * 必须有这份清单，而且必须与最终产物逐字节一致。发版前置门禁在重打包 zip 之后跑 verify，
 * 不一致就红，不许把不一致的包发出去。
 *
 * 清单格式与 SHA256SUMS.txt 同形：<64位小写hex>␣␣<相对路径>（相对包根，POSIX 分隔符）。
 * 清单自身不列入（否则自指）。必须含 Polaris.exe 与 resources/app.asar。
 */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const MANIFEST_REL = 'resources/polaris-files.sha256';
const REQUIRED = ['Polaris.exe', 'resources/app.asar'];

function listFiles(root, prefix, out) {
  const pre = prefix || '';
  const acc = out || [];
  for (const e of fs.readdirSync(path.join(root, pre), { withFileTypes: true })) {
    const rel = pre ? pre + '/' + e.name : e.name;
    if (e.isDirectory()) listFiles(root, rel, acc);
    else if (e.isFile()) acc.push(rel);
  }
  return acc;
}
const sha256 = (p) => crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');

function write(root) {
  const rels = listFiles(root).filter((r) => r !== MANIFEST_REL);
  const lines = rels.map((r) => sha256(path.join(root, r)) + '  ' + r);
  const target = path.join(root, MANIFEST_REL);
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.writeFileSync(target, lines.join('\n') + '\n', 'utf8');
  return { count: lines.length, file: target };
}

function parse(text) {
  const map = new Map();
  for (const line of String(text || '').split(/\r?\n/)) {
    const m = /^([0-9a-f]{64})\s+\*?(.+)$/.exec(line.trim());
    if (m) map.set(m[2].replace(/\\/g, '/'), m[1].toLowerCase());
  }
  return map;
}

function verify(root) {
  const mf = path.join(root, MANIFEST_REL);
  if (!fs.existsSync(mf)) return { ok: false, reason: '包内缺少清单 ' + MANIFEST_REL };
  const want = parse(fs.readFileSync(mf, 'utf8'));
  if (!want.size) return { ok: false, reason: '清单是空的' };
  for (const req of REQUIRED) if (!want.has(req)) return { ok: false, reason: '清单里没有 ' + req };
  const rels = listFiles(root).filter((r) => r !== MANIFEST_REL);
  for (const rel of rels) {
    if (!want.has(rel)) return { ok: false, reason: '清单缺少条目：' + rel };
    if (sha256(path.join(root, rel)) !== want.get(rel)) return { ok: false, reason: '哈希不符：' + rel };
  }
  if (want.size !== rels.length) return { ok: false, reason: '清单条目数 ' + want.size + ' 与产物文件数 ' + rels.length + ' 不一致' };
  return { ok: true, count: want.size };
}

module.exports = { MANIFEST_REL, REQUIRED, listFiles, parse, write, verify };

if (require.main === module) {
  const mode = process.argv[2];
  const root = process.argv[3];
  if (!mode || !root || ['write', 'verify'].indexOf(mode) < 0) {
    console.log('用法：node scripts/package-manifest.js write|verify <包目录>');
    process.exit(2);
  }
  if (mode === 'write') {
    const r = write(root);
    console.log('已写清单 ' + r.file + '（' + r.count + ' 个文件）');
    process.exit(0);
  }
  const v = verify(root);
  if (v.ok) { console.log('清单校验通过（' + v.count + ' 个文件）'); process.exit(0); }
  console.error('清单校验失败：' + v.reason);
  process.exit(1);
}