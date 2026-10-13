'use strict';
/**
 * 真面板自检的「缺参就用一句话退出」前置检查。
 *
 * 为什么单独一个文件：`npm run test:real` / `npm run test:ui` 要连**真面板真账号**，
 * 缺参数时必须**立刻**打印用法并以退出码 2 结束。不能让人等一整个 Electron 启动
 * 之后才发现少了 `--password` —— 那既是浪费时间，也容易被当成「跑过了」。
 *
 * 两种入口的形状不一样，所以参数要求也不同：
 *   - real-test.js 是 `electron scripts/real-test.js`（普通主进程脚本，会自己 print 用法）
 *   - ui-test.js 是 `require` 进来的模块，真正的入口是 `electron . --uitest`
 *     （main.js 里读 `--panel/--email/--password`，缺了就 log.error + 退出码 2）
 * 这里统一在**启动 Electron 之前**挡一道，两个入口给同一份用法说明。
 *
 * 用法：node scripts/preflight-creds.js <real|ui> [--restore] [其它参数…]
 *   real 模式多认一个 `--restore`（复用已登录会话，不需要面板账号密码）
 *
 * 退出码：0 = 参数齐了，可以启动；2 = 缺参数（已打印用法）
 */

const mode = process.argv[2] || '';
const args = process.argv.slice(3);

const hasFlag = (k) => args.includes(`--${k}`);
/** 取 `--k=v` 的值；`--k=` 这种空值也算「没给」 */
const valOf = (k) => {
  const hit = args.find((x) => x.startsWith(`--${k}=`));
  return hit ? hit.slice(k.length + 3).trim() : '';
};

const RESTORE = hasFlag('restore');
const PANEL = valOf('panel');
const EMAIL = valOf('email');
const PASSWORD = valOf('password');

if (mode === 'real') {
  // real-test.js 的 --restore 走「已登录会话」，不需要面板账号密码
  if (RESTORE) {
    if (!valOf('data')) {
      console.error('缺少 --data=<数据根目录>：--restore 要用那个目录里已经登录好的会话。');
      console.error('');
      console.error('用法:');
      console.error('  npm run test:real -- --panel=<面板地址> --email=<邮箱> --password=<密码> [--port=7890]');
      console.error('  npm run test:real -- --restore --data=<数据根目录> [--keep-session]');
      process.exit(2);
    }
    process.exit(0);
  }
  if (!PANEL || !EMAIL || !PASSWORD) {
    console.error('缺少 --panel / --email / --password，无法连真面板。');
    console.error('');
    console.error('用法:');
    console.error('  npm run test:real -- --panel=<面板地址> --email=<邮箱> --password=<密码> [--port=7890]');
    console.error('  npm run test:real -- --restore --data=<数据根目录> [--keep-session]');
    console.error('');
    console.error('说明：跑在开发态，数据目录默认 scripts/.devdata，不碰你正在用的那个实例；');
    console.error('      强制 sys_proxy=false，不改系统代理。密码只用于本次运行，不写进任何文件。');
    process.exit(2);
  }
  process.exit(0);
}

if (mode === 'ui') {
  if (!PANEL || !EMAIL || !PASSWORD) {
    console.error('缺少 --panel / --email / --password，无法连真面板。');
    console.error('');
    console.error('用法:');
    console.error('  npm run test:ui -- --panel=<面板地址> --email=<邮箱> --password=<密码> [--uitest-data=<数据根目录>]');
    console.error('');
    console.error('说明：会真的开一个窗口、连真面板、用真账号登录、用真鼠标事件点界面；');
    console.error('      报告写到 <数据根目录>/uitest-report.txt。不给 --uitest-data 就用开发态 .devdata。');
    process.exit(2);
  }
  process.exit(0);
}

console.error(`用法: node scripts/preflight-creds.js <real|ui> [参数…]（收到的是 "${mode}"）`);
process.exit(2);
