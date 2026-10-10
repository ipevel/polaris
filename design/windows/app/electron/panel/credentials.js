'use strict';
/**
 * 凭据存储：Electron safeStorage（Windows 走 DPAPI）加密后落 data/credentials.dat。
 * 文件内容对同用户以外不可解密；便携目录下换机即失效，需重新登录（符合"无自建服务器、
 * 不采集"的隐私承诺）。
 */

const fs = require('fs');
const paths = require('../paths');
const log = require('../logger');

let safeStorage = null;
try {
  const mod = require('electron');
  safeStorage = mod && mod.safeStorage ? mod.safeStorage : null;
} catch (_) {
  safeStorage = null;
}

let cache = null;

function file() { return paths.file('credentials.dat'); }

function available() {
  try { return !!(safeStorage && safeStorage.isEncryptionAvailable()); } catch (_) { return false; }
}

function load() {
  if (cache) return cache;
  try {
    const raw = fs.readFileSync(file());
    let text = null;
    if (available()) {
      try {
        text = safeStorage.decryptString(raw);
      } catch (e) {
        // 解不开（换机/换用户/换 userData 目录/文件是明文）→ 默认当作没有凭据。
        // 只有显式打开自检开关时才退回明文，与 save() 的明文口子对称。
        // 这里必须留一条日志：safeStorage 的密钥存在 userData 的 Local State 里，
        // 只搬 credentials.dat 而不搬 data/electron/ 就会落到这条分支，静默失败
        // 会让"登录态又没了"变成无法排查的玄学（真机踩过）。
        text = process.env.POLARIS_ALLOW_PLAINTEXT_CREDENTIALS === '1' ? raw.toString('utf8') : null;
        if (text === null) {
          log.warn('credentials.dat 无法解密（换机或 data/electron 缓存丢失），需要重新登录：' + (e && e.message));
        }
      }
    } else {
      text = raw.toString('utf8');   // 纯 Node（没有 DPAPI）下本来就只有明文可读
    }
    if (text === null) { cache = null; return cache; }
    cache = JSON.parse(text);
  } catch (_) {
    cache = null;
  }
  return cache;
}

function save(obj) {
  cache = obj;
  try {
    const text = JSON.stringify(obj);
    if (available()) {
      fs.writeFileSync(file(), safeStorage.encryptString(text));
    } else if (process.env.POLARIS_ALLOW_PLAINTEXT_CREDENTIALS === '1') {
      // 仅自检用：Electron 之外没有 DPAPI，允许明文落盘以便跑端到端测试
      fs.writeFileSync(file(), Buffer.from(text, 'utf8'));
      log.warn('credentials stored WITHOUT encryption (self-test mode)');
    } else {
      log.error('safeStorage 不可用，拒绝明文保存凭据');
    }
  } catch (e) {
    log.error('credentials save failed:', e && e.message);
  }
}

function clear() {
  cache = null;
  try { fs.rmSync(file(), { force: true }); } catch (_) {}
}

module.exports = { load, save, clear, available };
