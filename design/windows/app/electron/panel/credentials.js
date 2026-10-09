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
    const text = available() ? safeStorage.decryptString(raw) : raw.toString('utf8');
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
