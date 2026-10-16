'use strict';
/**
 * 包内清单自检（b 笔）：生成 → 校验 → 各种坏情况必须红。
 *   跑法：node scripts/package-manifest-test.js
 */
const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawnSync } = require('child_process');

const M = require('./package-manifest');
const BASE = fs.mkdtempSync(path.join(os.homedir(), '.polaris-manifest-'));
const PKG = path.join(BASE, 'pkg');

let total = 0; let failed = 0;
function check(name, cond, detail) {
  total += 1; if (!cond) failed += 1;
  console.log((cond ? '  [ OK ] ' : '  [FAIL] ') + name + (!cond && detail ? '  ' + detail : ''));
}
function reset() {
  fs.rmSync(PKG, { recursive: true, force: true });
  fs.mkdirSync(path.join(PKG, 'resources'), { recursive: true });
  fs.writeFileSync(path.join(PKG, 'Polaris.exe'), 'EXE');
  fs.writeFileSync(path.join(PKG, 'resources', 'app.asar'), 'ASAR');
  fs.writeFileSync(path.join(PKG, 'icudtl.dat'), 'ICU');
}
const mfPath = () => path.join(PKG, M.MANIFEST_REL);

console.log('包内清单自检');

/* 1. 生成 */
reset();
const w = M.write(PKG);
check('生成：清单文件写出来了', fs.existsSync(mfPath()));
check('生成：条目数 = 产物文件数（3）', w.count === 3, String(w.count));
const txt = fs.readFileSync(mfPath(), 'utf8');
check('生成：格式是 <64hex>␣␣<相对路径>', /^[0-9a-f]{64}  \S+/m.test(txt), txt.split('\n')[0]);
check('生成：含 Polaris.exe 与 resources/app.asar', M.parse(txt).has('Polaris.exe') && M.parse(txt).has('resources/app.asar'));
check('生成：清单自身不列入（不自指）', !M.parse(txt).has(M.MANIFEST_REL));

/* 2. 校验：正常 */
const ok1 = M.verify(PKG);
check('校验：正常包通过', ok1.ok === true && ok1.count === 3, JSON.stringify(ok1));

/* 3. 各种坏情况必须红 */
fs.writeFileSync(path.join(PKG, 'icudtl.dat'), 'TAMPERED');
const bad1 = M.verify(PKG);
check('校验：产物被改一个字节 → 哈希不符', bad1.ok === false && /哈希不符/.test(bad1.reason), JSON.stringify(bad1));
reset(); M.write(PKG);
fs.rmSync(mfPath());
const bad2 = M.verify(PKG);
check('校验：缺清单 → 拒绝', bad2.ok === false && /缺少清单/.test(bad2.reason), JSON.stringify(bad2));
reset();
fs.writeFileSync(mfPath(), '0'.repeat(64) + '  icudtl.dat\n');
const bad3 = M.verify(PKG);
check('校验：清单里没有 Polaris.exe → 拒绝', bad3.ok === false && /没有 Polaris\.exe/.test(bad3.reason), JSON.stringify(bad3));
reset(); M.write(PKG);
fs.writeFileSync(path.join(PKG, 'extra.dll'), 'EXTRA');
const bad4 = M.verify(PKG);
check('校验：产物多了清单里没有的文件 → 拒绝', bad4.ok === false && /清单缺少条目/.test(bad4.reason), JSON.stringify(bad4));
reset(); M.write(PKG);
fs.writeFileSync(mfPath(), fs.readFileSync(mfPath(), 'utf8') + 'a'.repeat(64) + '  ghost.dll\n');
const bad5 = M.verify(PKG);
check('校验：清单多出一条产物里没有的 → 条目数不一致', bad5.ok === false, JSON.stringify(bad5));

/* 4. CLI 两个模式（发版门禁就是这么调的） */
reset();
const c1 = spawnSync(process.execPath, [path.join(__dirname, 'package-manifest.js'), 'write', PKG], { encoding: 'utf8' });
check('CLI write 退出码 0 并打印条目数', c1.status === 0 && /个文件/.test(c1.stdout), c1.stdout + c1.stderr);
const c2 = spawnSync(process.execPath, [path.join(__dirname, 'package-manifest.js'), 'verify', PKG], { encoding: 'utf8' });
check('CLI verify 退出码 0', c2.status === 0 && /通过/.test(c2.stdout), c2.stdout + c2.stderr);
fs.writeFileSync(path.join(PKG, 'icudtl.dat'), 'CHANGED');
const c3 = spawnSync(process.execPath, [path.join(__dirname, 'package-manifest.js'), 'verify', PKG], { encoding: 'utf8' });
check('CLI verify 在不一致时退出码 1 并给出原因', c3.status === 1 && /哈希不符/.test(c3.stderr), c3.stdout + c3.stderr);
const c4 = spawnSync(process.execPath, [path.join(__dirname, 'package-manifest.js'), 'bogus', PKG], { encoding: 'utf8' });
check('CLI 参数不对退出码 2', c4.status === 2);

fs.rmSync(BASE, { recursive: true, force: true });
console.log('\npackage-manifest：' + (total - failed) + ' passed / ' + failed + ' failed（共 ' + total + ' 项）');
process.exit(failed === 0 ? 0 : 1);