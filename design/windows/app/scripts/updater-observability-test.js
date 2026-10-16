'use strict';
/**
 * PR-D 自检：apply 阶段「失败可观测」。
 *   跑法：node scripts/updater-observability-test.js
 *
 * 背景：1.11.6→1.12.0 那次更新没生效，helper 两分钟内**一行日志都没有**，
 * 事后分不清「helper 没起来」还是「起来了但早死」。这里钉住四件：
 *   ① 主进程起 helper 之前先写 apply-intent.json（意图）
 *   ② helper 一起手就写 apply-alive.json，且必须在任何守卫之前
 *   ③ helper 的 stdout/stderr 不再 'ignore'，重定向到 data/update/helper.out.log
 *   ④ 下次启动：意图在、结果不在（或结果更旧）→ apply_incomplete 报出来；结果不旧于意图 → 清掉意图
 * 再加两条接线断言：光有函数不算数，要被真的接上（前端那两处也要渲染/弹出来）。
 */
const fs = require('fs');
const os = require('os');
const path = require('path');

const BASE = fs.mkdtempSync(path.join(os.tmpdir(), 'polaris-apply-obs-'));
process.env.POLARIS_DATA_DIR = BASE;
process.env.POLARIS_APPLY_UPDATEDIR = BASE;

const U = require('../electron/core/updater');

let total = 0; let failed = 0;
function check(name, cond, detail) {
  total += 1; if (!cond) failed += 1;
  console.log((cond ? '  [ OK ] ' : '  [FAIL] ') + name + (!cond && detail ? '  ' + detail : ''));
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const exists = (f) => fs.existsSync(f);
const APP = path.join(__dirname, '..');

(async () => {
  console.log('-- 1. 从没发起过更新 → 不该报「没跑完」 --');
  check('无意图 → applyIncomplete() = null', U.applyIncomplete() === null);

  console.log('-- 2. 意图在、没写 alive、没有结果 → 「helper 根本没起来」 --');
  U.writeApplyIntent({ version: '1.12.2', stage_root: '/x/staging', install_dir: '/x' });
  let inc = U.applyIncomplete();
  check('报出「没跑完」', !!inc, JSON.stringify(inc));
  check('alive=false（helper 没起来）', !!(inc && inc.alive === false));
  check('带上目标版本与 helper pid 字段', !!(inc && inc.version === '1.12.2' && 'helper_pid' in inc));
  check('意图真的落了盘', exists(U.applyIntentFile()));

  console.log('-- 3. helper 写了 alive → 能区分「起来了但早死」 --');
  U.writeApplyAlive({ target: '/x' });
  inc = U.applyIncomplete();
  check('alive=true', !!(inc && inc.alive === true));

  console.log('-- 4. 结果不旧于意图 → 视为完成，并清掉意图 --');
  await sleep(5);
  U.writeApplyResult({ ok: false, step: 'worker', reason: '演示', rolled_back: true });
  check('applyIncomplete() = null', U.applyIncomplete() === null);
  check('意图文件已清', !exists(U.applyIntentFile()));
  check('alive 文件已清', !exists(U.applyAliveFile()));
  check('结果本身保留（「上次更新失败」那条提示还要用）', !!U.readApplyResult());

  console.log('-- 5. 旧结果不能给新的这一次交差 --');
  U.writeApplyIntent({ version: '1.12.3' });
  const res = U.readApplyResult();
  fs.writeFileSync(path.join(BASE, 'apply-result.json'), JSON.stringify(Object.assign({}, res, { ts: 1 })), 'utf8');
  inc = U.applyIncomplete();
  check('结果是更旧的 → 仍报「没跑完」', !!inc, JSON.stringify(inc));
  U.clearApplyIntent();

  console.log('-- 6. 接线断言（源码级） --');
  const updSrc = fs.readFileSync(path.join(APP, 'electron', 'core', 'updater.js'), 'utf8');
  const viewsSrc = fs.readFileSync(path.join(APP, 'src', 'js', 'views.js'), 'utf8');
  const appSrc = fs.readFileSync(path.join(APP, 'src', 'js', 'app.js'), 'utf8');
  check('apply() 起 helper 前写意图', /writeApplyIntent\(\{ version: String\(S\.version/.test(updSrc));
  check('helper stdio 不再 ignore（重定向到文件）', /stdio: outFd == null \? 'ignore' : \['ignore', outFd, outFd\]/.test(updSrc));
  const aliveCall = updSrc.indexOf('writeApplyAlive({ stage_root');
  const guardCall = updSrc.indexOf('const g1 = guardHelperEnv()');
  check('alive 写在守卫之前', aliveCall > 0 && guardCall > 0 && aliveCall < guardCall, 'alive@' + aliveCall + ' guard@' + guardCall);
  check('info() 暴露 apply_incomplete', /apply_incomplete: applyIncomplete\(\)/.test(updSrc));
  check('设置页渲染「上次更新没跑完」', /上次更新没跑完/.test(viewsSrc) && /apply_incomplete/.test(viewsSrc));
  check('状态回调里弹一次同样的提示', /上次更新没跑完/.test(appSrc) && /apply_incomplete/.test(appSrc));

  console.log('\n' + (failed ? 'FAIL' : 'OK') + '：' + (total - failed) + '/' + total + ' 通过' + (failed ? '，' + failed + ' 项失败' : ''));
  process.exit(failed ? 1 : 0);
})().catch((e) => { console.log('  [FAIL] 自检自身异常：' + (e && e.stack)); process.exit(1); });
