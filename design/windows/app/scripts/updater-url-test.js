'use strict';
/**
 * 更新下载地址「协议收口」自检（纯 Node，不需要 Electron、不联网、不碰真实安装目录）。
 *   跑法：node scripts/updater-url-test.js
 *
 * B6：download_update 的地址有三个来源（界面传的、面板 check_update 回的、落盘的
 * update_url/download_url）。落盘那份是可写的，file:// 或自定义协议不该被当成下载目标。
 * 这里钉死：
 *   A. isAllowedDownloadUrl / assertDownloadUrl 的判据（http、https 大小写、空白、各种脏串）
 *   B. download() 自己也要收口，且必须在动 updateDir 之前就拒绝
 *   C. ipc.download_update 在进 updater 之前就拒绝，且空地址仍报「未配置更新地址」
 * 注意（诚实记录）：下载层 fetchStream 早就有 u.protocol !== http(s) 的判断，所以旧代码
 * 不会真的拿 ftp 去下；B6 修的是「校验点太深、文案不明确、download 已经先建目录/清目录」。
 */
const fs = require('fs');
const os = require('os');
const path = require('path');

const TMP = path.join(os.tmpdir(), 'polaris-updater-url');
const DATA = path.join(TMP, 'data');
process.env.POLARIS_DATA_DIR = DATA;
fs.rmSync(TMP, { recursive: true, force: true });
fs.mkdirSync(DATA, { recursive: true });

let total = 0; let failed = 0;
function check(name, cond, detail) {
  total += 1; if (!cond) failed += 1;
  console.log((cond ? '  [ OK ] ' : '  [FAIL] ') + name + (!cond && detail ? '  ' + detail : ''));
}

const updater = require('../electron/core/updater');

const hasHelper = typeof updater.isAllowedDownloadUrl === 'function'
  && typeof updater.assertDownloadUrl === 'function';
check('B6 updater 导出 isAllowedDownloadUrl / assertDownloadUrl（旧代码没有）', hasHelper);

if (hasHelper) {
  /* ---- A. 判据 ---- */
  const yes = [
    ['http://example.com/Polaris.zip', 'http 普通地址'],
    ['https://example.com/Polaris.zip', 'https 普通地址'],
    ['HTTPS://EXAMPLE.COM/Polaris.zip', '大写 HTTPS 也要放行'],
    ['  https://example.com/a.zip\n', '前后空白/换行先 trim'],
    ['https://example.com/a.zip?v=1#x', '带 query 和 fragment'],
  ];
  const no = [
    ['', '空串'],
    ['   ', '全空白'],
    [null, 'null'],
    [undefined, 'undefined'],
    ['ftp://example.com/a.zip', 'ftp'],
    ['file:///C:/Users/x/Polaris.zip', 'file:// 本地路径'],
    ['javascript:alert(1)', 'javascript:'],
    ['data:text/plain,hi', 'data:'],
    ['\\\\server\\share\\Polaris.zip', 'UNC 路径'],
    ['C:\\Users\\x\\Polaris.zip', 'Windows 绝对路径'],
    ['//evil.com/a.zip', '协议相对地址（别当 https 放行）'],
    ['http:/example.com/a.zip', '缺一条斜杠'],
    ['httpx://example.com/a.zip', 'httpx 假协议'],
  ];
  for (const [u, why] of yes) {
    check('B6 A 放行 ' + why + '：' + JSON.stringify(u), updater.isAllowedDownloadUrl(u) === true);
  }
  for (const [u, why] of no) {
    check('B6 A 拒绝 ' + why + '：' + JSON.stringify(u), updater.isAllowedDownloadUrl(u) === false);
  }

  /* ---- A2. assertDownloadUrl ---- */
  check('B6 A2 合法地址返回 trim 后的串',
    updater.assertDownloadUrl('  https://example.com/a.zip ') === 'https://example.com/a.zip');
  const msgOf = (fn) => { try { fn(); return null; } catch (e) { return String((e && e.message) || e); } };
  const mFtp = msgOf(() => updater.assertDownloadUrl('ftp://example.com/a.zip'));
  check('B6 A2 ftp 抛错且文案点明 http(s)', !!mFtp && /http\(s\)/.test(mFtp), String(mFtp));
  check('B6 A2 ftp 抛错时把被拒的地址带出来（便于日志定位）',
    !!mFtp && mFtp.includes('ftp://example.com/a.zip'), String(mFtp));
  const mEmpty = msgOf(() => updater.assertDownloadUrl(''));
  check('B6 A2 空地址也抛错，且不吐出 undefined',
    !!mEmpty && mEmpty.includes('(空)') && !mEmpty.includes('undefined'), String(mEmpty));
  const mLong = msgOf(() => updater.assertDownloadUrl('ftp://example.com/' + 'a'.repeat(500)));
  check('B6 A2 超长地址被截断到 120 字符，不会把日志刷爆',
    !!mLong && mLong.length < 220, String(mLong && mLong.length));
}

/* ---- B. download() 自己收口，且不碰 updateDir ---- */
async function main() {
async function rejectOf(p) {
  try { await p; return { threw: false, msg: '' }; } catch (e) { return { threw: true, msg: String((e && e.message) || e) }; }
}
const zipCount = () => {
  try { return fs.readdirSync(updater.updateDir()).filter((n) => /^Polaris-9\.9/.test(n)).length; }
  catch (_) { return 0; }
};
const before = zipCount();

const rFile = await rejectOf(updater.download('file:///C:/nope/Polaris.zip', '9.9'));
check('B6 B download(file://…) 被拒', rFile.threw, JSON.stringify(rFile));
check('B6 B 拒绝文案是「不是 http(s) 链接」而不是下载层的模糊报错（旧代码报「只支持 http(s) 下载」）',
  /不是 http\(s\) 链接/.test(rFile.msg), rFile.msg);
check('B6 B 拒绝时没在更新目录留下任何 Polaris-9.9* 文件（校验在动目录之前）',
  zipCount() === before, 'before=' + before + ' after=' + zipCount());

const rEmpty = await rejectOf(updater.download('', '9.9'));
check('B6 B 空地址仍是「未配置更新地址」（行为不变，没被协议校验顶掉）',
  rEmpty.threw && rEmpty.msg.includes('未配置更新地址'), rEmpty.msg);

const rApk = await rejectOf(updater.download('https://example.invalid/Polaris.apk', '9.9'));
check('B6 B 顺序：https 的 apk 地址先过协议校验、再被 apk 判断拦下（说明校验没把合法协议误杀）',
  rApk.threw && rApk.msg.includes('安卓'), rApk.msg);

/* ---- C. ipc.download_update 接线 ---- */
const ipcSrc = fs.readFileSync(path.join(__dirname, '..', 'electron', 'ipc.js'), 'utf8');
const start = ipcSrc.indexOf('download_update:');
const end = ipcSrc.indexOf('apply_update:', start);
const seg = start >= 0 && end > start ? ipcSrc.slice(start, end) : '';
check('B6 C 取到 download_update 处理段', seg.length > 0 && seg.length < 1200, 'len=' + seg.length);
check('B6 C 段内调用 updater.isAllowedDownloadUrl', /updater\.isAllowedDownloadUrl\(/.test(seg));
check('B6 C 非法地址是抛错（界面 guard 会弹红 toast），不是静默返回',
  /throw new Error\('更新地址不是 http\(s\) 链接/.test(seg));
check('B6 C 校验发生在 updater.download 之前',
  seg.indexOf('isAllowedDownloadUrl') >= 0 && seg.indexOf('isAllowedDownloadUrl') < seg.indexOf('updater.download('),
  'check@' + seg.indexOf('isAllowedDownloadUrl') + ' call@' + seg.indexOf('updater.download('));
check('B6 C 空地址不受协议校验影响（守卫带 target &&，空串仍走 updater 的「未配置更新地址」）',
  /if \(target && !updater\.isAllowedDownloadUrl\(target\)\)/.test(seg));
check('B6 C 地址先 trim 再用（面板回带空白的地址不会被误判）', /\|\| ''\)\.trim\(\)/.test(seg));
check('B6 C open_external 的同类口径仍在（两处一个标准）',
  /只允许打开 http\(s\) 链接/.test(ipcSrc));

console.log('\nupdater-url：' + (total - failed) + ' passed / ' + failed + ' failed（共 ' + total + ' 项）');
try { fs.rmSync(TMP, { recursive: true, force: true }); } catch (_) {}
process.exit(failed ? 1 : 0);
}

main().catch((e) => { console.log('  [FAIL] 自检自身异常：' + ((e && e.stack) || e)); process.exit(1); });
