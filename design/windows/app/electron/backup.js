'use strict';
/**
 * 便携目录的"救命备份"。
 *
 * 为什么需要：便携版把配置和登录凭据都放在 `<exeDir>/data/` 里，于是"升级"最自然的
 * 动作 —— 把新包解压到同一个目录、或先删旧目录再解压 —— 会把 data/ 一起带走。
 * 真机事故：用户报"退出软件再打开账号显示未登录"，翻日志发现那次启动时
 * `data/settings.json` 与 `data/credentials.dat` 都不存在（panel_url 是空的、日志里
 * 没有 `session restored`），而 `locales/`、`resources/` 的 mtime 比 data/ 早 30 秒
 * —— 就是他 08:11:57 把新包覆盖解压进这个目录的那一刻。用户什么错都看不到，
 * 只会觉得"这软件又把我登录丢了"。
 *
 * 备份放在 `%LOCALAPPDATA%\Polaris\backup\<安装目录指纹>\`，只在**便携目录里文件
 * 真的不见了**时回填，正常情况下完全不参与读写。凭据本身仍是 safeStorage(DPAPI)
 * 加密的，换机照样解不开 —— 与原来的隐私承诺一致。
 *
 * 明确注销（logout）时会把备份里的凭据一起删掉，否则下次回填会把已注销的登录态复活。
 */
const fs = require('fs');
const os = require('os');
const path = require('path');
const crypto = require('crypto');

const log = require('./logger');
const paths = require('./paths');

// 只备份这两样：一个是"我连的是哪个面板/有哪些设置"，一个是"我是谁"。
// 其余（订阅、config.yaml、内核缓存）都能重新拉，没必要复制。
const FILES = ['settings.json', 'credentials.dat'];

function fingerprint() {
  const r = paths.root();
  const h = crypto.createHash('sha1').update(String(r)).digest('hex').slice(0, 8);
  const leaf = path.basename(String(r)).replace(/[^\w.-]/g, '') || 'root';
  return `${leaf}-${h}`;
}

function baseDir() {
  if (process.env.LOCALAPPDATA) return path.join(process.env.LOCALAPPDATA, 'Polaris', 'backup');
  if (process.platform === 'win32') return path.join(os.homedir(), 'AppData', 'Local', 'Polaris', 'backup');
  return path.join(os.homedir(), '.local', 'share', 'Polaris', 'backup');
}

function root() { return path.join(baseDir(), fingerprint()); }
function fileOf(name) { return path.join(root(), name); }
function primaryOf(name) { return paths.file(name); }

/** 把便携目录里的 settings.json / credentials.dat 复制一份到备份目录。 */
function mirrorNow() {
  const copied = [];
  try {
    for (const name of FILES) {
      const src = primaryOf(name);
      if (!fs.existsSync(src)) continue;
      fs.mkdirSync(root(), { recursive: true });
      const tmp = `${fileOf(name)}.tmp`;
      fs.copyFileSync(src, tmp);
      fs.renameSync(tmp, fileOf(name));   // 原子替换：避免别处读到半截文件
      copied.push(name);
    }
  } catch (e) {
    log.warn('backup mirror failed:', e && e.message);
  }
  return copied;
}

/** 删掉某个文件的备份（明确注销凭据时用）。 */
function forget(name) {
  try { fs.rmSync(fileOf(name), { force: true }); } catch (_) { /* 删不掉就算了 */ }
}

/**
 * 便携目录里缺文件时从备份回填。返回被回填的文件名数组。
 * 只在**文件真的不存在**时动手 —— 存在就一律以便携目录里的为准（用户手动改过就听他的）。
 */
function restoreIfMissing() {
  const back = [];
  for (const name of FILES) {
    const dst = primaryOf(name);
    const src = fileOf(name);
    try {
      if (fs.existsSync(dst) || !fs.existsSync(src)) continue;
      fs.mkdirSync(path.dirname(dst), { recursive: true });
      fs.copyFileSync(src, dst);
      back.push(name);
    } catch (e) {
      log.warn(`backup restore ${name} failed:`, e && e.message);
    }
  }
  if (back.length) log.info(`restored ${back.join(', ')} from backup (${root()})`);
  return back;
}

/** 给自检/诊断看的状态。 */
function info() {
  const has = {};
  for (const name of FILES) {
    has[name] = { primary: fs.existsSync(primaryOf(name)), backup: fs.existsSync(fileOf(name)) };
  }
  return { dir: root(), has };
}

module.exports = { root, fileOf, mirrorNow, forget, restoreIfMissing, info, FILES };
