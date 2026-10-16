'use strict';
/**
 * PR-B 自检：残留内核自检 / 清理。
 *   跑法：node scripts/leftover-kernel-test.js
 *
 * 钉住的是**安全边界**（用户 2026-10-14 的铁律「认不出是谁就不许动」的延续）：
 *   ① 只认命令行里带我们自己数据目录的 mihomo.exe —— 别的代理软件的 mihomo 不报；
 *   ② 目录前缀不误伤（Polaris-portable-1.12.1 不能命中 1.12.10）；
 *   ③ 拿不到进程列表 / 解析失败 → 按「没有」处理，绝不猜；
 *   ④ 动手前再核进程名：认不出 / 名字不是 mihomo.exe → 一步都不走；
 *   ⑤ 接线断言：ipc 两条命令、启动自检、设置页那行与动作都真的在。
 */
const fs = require('fs');
const os = require('os');
const path = require('path');

const L = require('../electron/net/leftover');

let total = 0; let failed = 0;
function check(name, cond, detail) {
  total += 1; if (!cond) failed += 1;
  console.log((cond ? '  [ OK ] ' : '  [FAIL] ') + name + (!cond && detail ? '  ' + detail : ''));
}

console.log('-- 1. 解析 Get-CimInstance 的输出 --');
check('单个对象（没有逗号那圈）', L.parseProcs('{"ProcessId":11,"CommandLine":"x"}').length === 1);
check('数组', L.parseProcs('[{"ProcessId":11,"CommandLine":"a"},{"ProcessId":12,"CommandLine":"b"}]').length === 2);
check('空 / null → 空表（不是失败）', L.parseProcs('').length === 0 && L.parseProcs('null').length === 0 && L.parseProcs('[]').length === 0);
check('坏 JSON → null（与「没有」区分开，绝不猜）', L.parseProcs('<html>oops') === null);
check('缺 pid 的条目被丢掉', L.parseProcs('[{"CommandLine":"a"}]').length === 0);

console.log('-- 2. 目录匹配：只认我们自己的 data 目录 --');
const D = 'E:\\AI\\Polaris-portable-1.12.1\\data';
check('命令行带 -d <我们的 data> → 命中',
  L.usesDataDir('"E:\\AI\\Polaris-portable-1.12.1\\mihomo.exe" -d "E:\\AI\\Polaris-portable-1.12.1\\data" -f "E:\\AI\\Polaris-portable-1.12.1\\data\\config.yaml"', D) === true);
check('大小写不同也命中', L.usesDataDir('mihomo.exe -d e:\\ai\\polaris-portable-1.12.1\\DATA', D) === true);
check('别的目录（另一个 Clash GUI）→ 不命中', L.usesDataDir('mihomo.exe -d "C:\\Users\\x\\.config\\clash"', D) === false);
check('目录前缀不误伤（…1.12.10 不能命中 …1.12.1）',
  L.usesDataDir('mihomo.exe -d "E:\\AI\\Polaris-portable-1.12.10\\data"', D) === false);
check('空命令行 / 命令行里没有目录 → 不命中',
  L.usesDataDir('', D) === false && L.usesDataDir('mihomo.exe', D) === false);

console.log('-- 3. 平台/权限兜底 --');
(async () => {
  const r = await L.findLeftovers(os.tmpdir(), 0);
  check('非 Windows → supported:false 且不报任何东西', r.supported === false && r.list.length === 0);

  console.log('-- 4. 动手前的两道守卫 --');
  let k = await L.killLeftover(0);
  check('pid 非法 → 不动手', k.closed === false && k.reason === 'invalid');
  k = await L.killLeftover(999999, { nameForTest: '' });
  check('认不出进程名 → 不动手（unidentified）', k.closed === false && k.reason === 'unidentified');
  k = await L.killLeftover(999999, { nameForTest: 'chrome.exe' });
  check('名字不是 mihomo.exe → 不动手（not-ours）', k.closed === false && k.reason === 'not-ours');

  console.log('-- 5. 接线断言（源码级） --');
  const APP = path.join(__dirname, '..');
  const ipcSrc = fs.readFileSync(path.join(APP, 'electron', 'ipc.js'), 'utf8');
  const appSrc = fs.readFileSync(path.join(APP, 'src', 'js', 'app.js'), 'utf8');
  const viewsSrc = fs.readFileSync(path.join(APP, 'src', 'js', 'views.js'), 'utf8');
  check('ipc 有 check_leftover_kernel / kill_leftover_kernel',
    /check_leftover_kernel:/.test(ipcSrc) && /kill_leftover_kernel:/.test(ipcSrc));
  check('自检传的是我们自己的数据目录 + 当前内核 pid',
    /findLeftovers\(paths\.data\(\), \(S\.proc && S\.proc\.pid\)/.test(ipcSrc));
  check('启动时不阻塞地跑一次自检', /api\.invoke\("check_leftover_kernel"\)/.test(appSrc) && /leftoverKernel = list/.test(appSrc));
  check('设置页有那一行与动作', /残留内核/.test(viewsSrc) && /cleanup-leftover/.test(appSrc));

  console.log('\n' + (failed ? 'FAIL' : 'OK') + '：' + (total - failed) + '/' + total + ' 通过' + (failed ? '，' + failed + ' 项失败' : ''));
  process.exit(failed ? 1 : 0);
})();
