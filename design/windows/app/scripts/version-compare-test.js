'use strict';
/**
 * 版本号比较（cmpVersion）自检（纯 Node，不需要 Electron、不联网）。
 *   跑法：node scripts/version-compare-test.js
 *
 * F3：cmpVersion 原来用 parseInt 逐段比，"-beta" 被吞成 0 →
 *   cmpVersion('1.12.0-beta', '1.12.0') === 0，预发布用户永远"已经是最新"。
 * 这里钉死新口径：
 *   A. 数字段按数值、段数不等补 0（1.10.0 > 1.9.9，1.12 == 1.12.0）
 *   B. 预发布 < 同号正式版；预发布之间按 SemVer（-beta < -beta.2、数字 < 字母）
 *   C. build 元数据忽略、可选前导 v、空白先 trim
 *   D. 非法输入不抛异常、返回 0，且 isValidVersion() 能区分"没法比"与"相等"
 *   E. 自更新方向没被比反：当前 1.12.0-beta + 远端 1.12.0 正式 = 需要更新
 *      （并核对 release.js / remote.js / ipc.js 三个调用点的实参顺序）
 */
const fs = require('fs');
const path = require('path');

const fmt = require('../electron/util/format');

let total = 0; let failed = 0;
function check(name, cond, detail) {
  total += 1; if (!cond) failed += 1;
  console.log((cond ? '  [ OK ] ' : '  [FAIL] ') + name + (!cond && detail ? '  ' + detail : ''));
}
/** 断言 cmpVersion(a,b) === want */
function eq(a, b, want, name) {
  const got = fmt.cmpVersion(a, b);
  check(name || ('cmpVersion(' + JSON.stringify(a) + ', ' + JSON.stringify(b) + ') === ' + want),
    got === want, 'got=' + got);
}

console.log('== A. 数字段按数值 / 段数不等 ==');
eq('1.10.0', '1.9.9', 1, 'A 1.10.0 > 1.9.9（按字符串比会反过来）');
eq('1.9.9', '1.10.0', -1, 'A 1.9.9 < 1.10.0');
eq('1.10.0', '1.11.3', -1, 'A 1.10.0 < 1.11.3');
eq('1.11.3', '1.12.0', -1, 'A 1.11.3 < 1.12.0');
eq('1.12.0', '1.12.0', 0, 'A 同版本相等');
eq('1.12', '1.12.0', 0, 'A 段数不等时缺段补 0：1.12 == 1.12.0');
eq('1.12.0', '1.12.0.0', 0, 'A 多出来的 0 段也算相等：1.12.0 == 1.12.0.0');
eq('2.0.0', '1.99.99', 1, 'A 主版本优先：2.0.0 > 1.99.99');

console.log('== B. 预发布（F3 的核心） ==');
eq('1.12.0-beta', '1.12.0', -1, 'B 1.12.0-beta < 1.12.0（旧代码判等）');
eq('1.12.0', '1.12.0-beta', 1, 'B 1.12.0 > 1.12.0-beta');
eq('1.12.0-beta.2', '1.12.0-beta', 1, 'B 1.12.0-beta.2 > 1.12.0-beta');
eq('1.12.0-beta', '1.12.0-beta.2', -1, 'B 1.12.0-beta < 1.12.0-beta.2');
eq('1.12.0-alpha', '1.12.0-beta', -1, 'B -alpha < -beta（ASCII 序）');
eq('1.12.0-Beta', '1.12.0-beta', -1, 'B 字母标识大小写敏感：-Beta < -beta');
eq('1.12.0-2', '1.12.0-alpha', -1, 'B 纯数字标识 < 字母标识');
eq('1.12.0-10', '1.12.0-9', 1, 'B 数字标识按数值比：-10 > -9');
eq('1.12.0-rc.1', '1.12.0-beta.9', 1, 'B rc > beta（前缀先比）');
eq('1.13.0-beta', '1.12.0', 1, 'B 主版本更高的预发布仍然更大：1.13.0-beta > 1.12.0');
eq('1.12.0-beta', '1.11.6', 1, 'B 预发布比上一个正式版新：1.12.0-beta > 1.11.6');

console.log('== C. build 元数据 / 前导 v / 空白 ==');
eq('1.12.0+build', '1.12.0', 0, 'C build 元数据忽略：1.12.0+build == 1.12.0');
eq('1.12.0+build.5', '1.12.0+build.7', 0, 'C 两边 build 不同也相等');
eq('1.12.0-beta+sha.abc', '1.12.0-beta', 0, 'C 预发布上的 build 同样忽略');
eq('1.12.0-beta+sha.abc', '1.12.0', -1, 'C 忽略 build 之后预发布语义不变');
eq('v1.12.0', '1.12.0', 0, 'C 前导 v 允许：v1.12.0 == 1.12.0');
eq('V1.12.0', 'v1.12.0', 0, 'C 大小写 v 都允许');
eq('v1.12.0', 'v1.11.6', 1, 'C 带 v 也按数值比');
eq('  1.12.0  ', '1.12.0', 0, 'C 前后空白先 trim');
eq('1.12.0\n', 'v1.12.0', 0, 'C 换行也 trim');
eq('1.12.0+meta', 'v1.12.0', 0, 'C v + build 一起出现仍相等');

console.log('== D. 非法输入：不抛异常、返回 0 ==');
const bad = ['', '   ', 'abc', '1..2', '1.12.0-', '1.12.0-?', '1.12.0 beta', 'x1.2.3', '1.12.0+', '1.12.0-a..b', '-1.2.3', '1.-2.3', null, undefined, {}, NaN];
for (const v of bad) {
  let threw = null; let got = null;
  try { got = fmt.cmpVersion(v, '1.12.0'); } catch (e) { threw = e; }
  check('D 非法输入 ' + JSON.stringify(String(v)) + '：不抛异常且返回 0',
    !threw && got === 0, threw ? 'threw=' + threw.message : 'got=' + got);
}
check('D 两个非法输入之间也返回 0（不抛）', fmt.cmpVersion('abc', '') === 0);
check('D 空串不再恒判「有更新」：cmpVersion(任意, "") === 0（旧代码恒为 1）',
  fmt.cmpVersion('1.12.0', '') === 0 && fmt.cmpVersion('9.9.9', '') === 0);

console.log('== E. isValidVersion：区分「相等」与「没法比」 ==');
check('E 导出 isValidVersion（旧代码没有）', typeof fmt.isValidVersion === 'function');
if (typeof fmt.isValidVersion === 'function') {
  const yes = ['1.12.0', '1.12', 'v1.12.0', '1.12.0-beta', '1.12.0+build', '1.12.0-beta.2+x'];
  const no = ['', 'abc', '1..2', '1.12.0-', null, undefined];
  for (const v of yes) check('E isValidVersion(' + JSON.stringify(v) + ') === true', fmt.isValidVersion(v) === true);
  for (const v of no) check('E isValidVersion(' + JSON.stringify(String(v)) + ') === false', fmt.isValidVersion(v) === false);
  check('E 非法输入下 cmpVersion 为 0 但 isValidVersion 为 false（两者合起来能区分）',
    fmt.cmpVersion('abc', '1.12.0') === 0 && fmt.isValidVersion('abc') === false);
}

console.log('== F. 自更新方向没被比反（当前 1.12.0-beta + 远端 1.12.0 正式 → 需要更新） ==');
/** 复刻三个调用点的判据：只有「远端比当前新」才算有更新 */
const hasUpdate = (latest, current) => fmt.cmpVersion(latest, current) > 0;
check('F 当前 1.12.0-beta、远端 1.12.0 正式 → 需要更新（旧代码 false，用户收不到正式版）',
  hasUpdate('1.12.0', '1.12.0-beta') === true);
check('F 当前 1.12.0、远端 1.12.0-beta → 不更新（不能给用户降级）',
  hasUpdate('1.12.0-beta', '1.12.0') === false);
check('F 当前 1.12.0、远端 1.12.0 → 不更新', hasUpdate('1.12.0', '1.12.0') === false);
check('F 当前 1.11.6、远端 1.12.0 → 需要更新', hasUpdate('1.12.0', '1.11.6') === true);
check('F 当前 1.12.0-beta、远端 1.12.0-beta.2 → 需要更新', hasUpdate('1.12.0-beta.2', '1.12.0-beta') === true);
check('F 当前版本读不出来（""）→ 不声称有更新', hasUpdate('1.12.0', '') === false);

/* ---- 源码接线：实参顺序（远端在前、当前在后）不能反 ---- */
const src = (p) => fs.readFileSync(path.join(__dirname, '..', 'electron', p), 'utf8');
const relSrc = src('core/release.js');
const remSrc = src('core/remote.js');
const ipcSrc = src('ipc.js');
check('F release.js 用 cmpVersion(latest, currentVersion) > 0 决定 has_update',
  /cmpVersion\(latest, currentVersion\) > 0/.test(relSrc));
check('F remote.js 用 cmpVersion(远端, store.get(\'version\')) > 0 决定 has_update',
  /cmpVersion\(version, store\.get\('version'\)\) > 0/.test(remSrc));
check('F ipc.js 用 cmpVersion(s.update_latest, s.version) > 0 决定红点',
  /cmpVersion\(s\.update_latest, s\.version\) > 0/.test(ipcSrc));
check('F 三个调用点都是「> 0 才算更新」（没有 >= 0 这种恒亮的写法）',
  (relSrc + remSrc + ipcSrc).split('cmpVersion(').length - 1 === 3
  && !/cmpVersion\([^)]*\) >= 0/.test(relSrc + remSrc + ipcSrc));

console.log('\nversion-compare：' + (total - failed) + ' passed / ' + failed + ' failed（共 ' + total + ' 项）');
process.exit(failed ? 1 : 0);
