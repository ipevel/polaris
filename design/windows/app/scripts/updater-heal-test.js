'use strict';
/**
 * 自更新「启动自愈」自检（纯 Node，不需要 Electron、不碰真实安装目录）。
 *   跑法：node scripts/updater-heal-test.js
 *
 * W9：applyWorker 第 2 步把 Polaris.exe 改名为 Polaris.exe.old 之后、第 4 步把新文件
 * 复制到位之前，进程被强杀 / 掉电 —— 安装目录里就只剩 .old，没有 Polaris.exe，
 * 用户双击什么都起不来。这里钉死两条：
 *   A. 缺失 + .old 存在 → 启动时改回 Polaris.exe（恢复）
 *   B. 正常情况（exe 在）→ 一个字都不动，.old 仍由 cleanStaleRename 清理
 * 外加保护条：exe 缺失时 cleanStaleRename 不许把唯一的 .old 删掉。
 */
const fs = require('fs');
const os = require('os');
const path = require('path');

const TMP = path.join(os.tmpdir(), 'polaris-updater-heal');
const APP = path.join(TMP, '中文路径测试', 'Polaris-portable-1.11.3');
const DATA = path.join(TMP, 'data');
const EXE = path.join(APP, 'Polaris.exe');
const OLD = EXE + '.old';

process.env.POLARIS_DATA_DIR = DATA;
// P0：installDir() 现在优先读 POLARIS_APPLY_TARGET（helper 从临时目录运行后这是唯一权威）
process.env.POLARIS_APPLY_TARGET = APP;

let total = 0; let failed = 0;
function check(name, cond, detail) {
  total += 1; if (!cond) failed += 1;
  console.log((cond ? '  [ OK ] ' : '  [FAIL] ') + name + (!cond && detail ? '  ' + detail : ''));
}

// updater 的 cleanStaleRename 现在按 installDir()（= POLARIS_APPLY_TARGET）定位；
// 这里仍把 process.execPath 也指到假 exe，防它回退到 exe 目录时碰到真 node.exe
Object.defineProperty(process, 'execPath', { value: EXE, configurable: true });

const updater = require('../electron/core/updater');

function reset() {
  fs.rmSync(TMP, { recursive: true, force: true });
  fs.mkdirSync(APP, { recursive: true });
  fs.mkdirSync(DATA, { recursive: true });
}

const hasHeal = typeof updater.healMissingExe === 'function';
check('W9 updater 导出 healMissingExe（旧代码没有它，只能手工改文件名）', hasHeal);

if (hasHeal) {
  // A. 缺失 + .old 存在 → 恢复
  reset();
  fs.writeFileSync(OLD, 'OLD-1.11.2');
  const a = updater.healMissingExe(APP);
  check('W9 A 缺失 exe + 有 .old → 自愈成功', a.healed === true, JSON.stringify(a));
  check('W9 A 恢复后 Polaris.exe 存在，内容就是旧 exe 的字节',
    fs.existsSync(EXE) && fs.readFileSync(EXE, 'utf8') === 'OLD-1.11.2',
    fs.existsSync(EXE) ? fs.readFileSync(EXE, 'utf8') : '(exe 不存在)');
  check('W9 A .old 已改走，不再残留', !fs.existsSync(OLD));

  // B. 正常情况 → 不动
  reset();
  fs.writeFileSync(EXE, 'NEW-1.11.3');
  fs.writeFileSync(OLD, 'OLD-1.11.2');
  const b = updater.healMissingExe(APP);
  check('W9 B 正常情况（exe 在）→ 自愈不动手', b.healed === false, JSON.stringify(b));
  check('W9 B 正常情况 exe 内容没被改写', fs.readFileSync(EXE, 'utf8') === 'NEW-1.11.3');
  check('W9 B 正常情况 .old 仍留着（交给 cleanStaleRename）', fs.existsSync(OLD));
  updater.cleanStaleRename();
  check('W9 B 正常情况 cleanStaleRename 照旧删掉 .old（升级路径不变）',
    !fs.existsSync(OLD) && fs.existsSync(EXE) && fs.readFileSync(EXE, 'utf8') === 'NEW-1.11.3');

  // C. 没有 .old → 什么都不做
  reset();
  const c = updater.healMissingExe(APP);
  check('W9 C 没有 .old → 无操作、不报错', c.healed === false && !fs.existsSync(EXE), JSON.stringify(c));

  // D. 保护：exe 缺失时清理不许删唯一的 .old
  reset();
  fs.writeFileSync(OLD, 'OLD-1.11.2');
  updater.cleanStaleRename();
  check('W9 D exe 缺失时 cleanStaleRename 保留 .old（不能扔掉最后的救生圈）', fs.existsSync(OLD));
  const d = updater.healMissingExe(APP);
  check('W9 D 保留下来的 .old 随后仍能自愈', d.healed === true && fs.readFileSync(EXE, 'utf8') === 'OLD-1.11.2');

  // E. 用户双击 .old 启动（process.execPath 就是 .old）→ 也能救回来
  reset();
  fs.writeFileSync(OLD, 'OLD-1.11.2');
  Object.defineProperty(process, 'execPath', { value: OLD, configurable: true });
  const e = updater.healMissingExe(APP);
  check('W9 E 双击 .old 启动 → 自愈把 .old 改回 Polaris.exe',
    e.healed === true && fs.existsSync(EXE), JSON.stringify(e));
  updater.cleanStaleRename();
  check('W9 E 该场景下清理不会误删刚恢复的 exe',
    fs.existsSync(EXE) && fs.readFileSync(EXE, 'utf8') === 'OLD-1.11.2');
  Object.defineProperty(process, 'execPath', { value: EXE, configurable: true });
}

// F. 接线：main.js 启动路径必须先自愈、再清理（顺序反了就是先删掉唯一的 .old）
const mainSrc = fs.readFileSync(path.join(__dirname, '..', 'electron', 'main.js'), 'utf8');
const iHeal = mainSrc.indexOf('healMissingExe');
const iClean = mainSrc.indexOf('cleanStaleRename');
check('W9 F main.js 启动时调用了 healMissingExe', iHeal > 0);
check('W9 F main.js 先自愈后清理', iHeal > 0 && iClean > iHeal, 'heal@' + iHeal + ' clean@' + iClean);

console.log('');
console.log('通过 ' + (total - failed) + ' 项，失败 ' + failed + ' 项');
process.exit(failed ? 1 : 0);
