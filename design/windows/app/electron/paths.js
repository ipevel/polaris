'use strict';
/**
 * 路径解析 —— 便携模式的单点真相。
 *
 * 优先级：
 *   1. 打包后：<exe 所在目录>/data            （绿色、可整目录拷走、U 盘可跑）
 *   2. 打包后但目录只读：%LOCALAPPDATA%/Polaris （U 盘只读等降级场景）
 *   3. 开发态：<app>/.devdata
 *
 * 本模块刻意不强依赖 electron：core/ 与 util/ 下的逻辑要能在纯 Node 下跑自检，
 * 所以 require 失败时退到一个基于 __dirname 的开发态根目录。
 */

const fs = require('fs');
const path = require('path');

let electronApp = null;
try {
  const mod = require('electron');
  electronApp = mod && mod.app ? mod.app : null;
} catch (_) {
  electronApp = null;
}

const IS_ELECTRON = !!electronApp;
const IS_PACKAGED = IS_ELECTRON ? electronApp.isPackaged : false;

function canWrite(dir) {
  try {
    fs.mkdirSync(dir, { recursive: true });
    fs.accessSync(dir, fs.constants.W_OK);
    return true;
  } catch (_) {
    return false;
  }
}

let cachedRoot = null;
let portable = false;

function root() {
  if (cachedRoot) return cachedRoot;

  if (IS_PACKAGED) {
    const exeDir = path.dirname(electronApp.getPath('exe'));
    const probe = path.join(exeDir, '.polaris-write-probe');
    if (canWrite(probe)) {
      try { fs.rmSync(probe, { recursive: true, force: true }); } catch (_) {}
      cachedRoot = exeDir;
      portable = true;
    } else {
      cachedRoot = path.join(electronApp.getPath('appData'), 'Polaris');
      portable = false;
    }
  } else {
    // 开发态 / 纯 Node 自检：都落在 app 目录下的 .devdata
    const appDir = IS_ELECTRON ? electronApp.getAppPath() : path.resolve(__dirname, '..');
    cachedRoot = path.join(appDir, '.devdata');
    portable = false;
  }
  return cachedRoot;
}

const paths = {
  root,
  isElectron: IS_ELECTRON,
  isPackaged: IS_PACKAGED,
  isPortable: () => portable,
  data: () => path.join(root(), 'data'),
  logs: () => path.join(root(), 'data', 'logs'),
  profiles: () => path.join(root(), 'data', 'profiles'),
  providers: () => path.join(root(), 'data', 'providers'),
  cache: () => path.join(root(), 'data', 'cache'),
  file: (...rel) => path.join(root(), 'data', ...rel),
  core: () => (IS_PACKAGED
    ? path.join(process.resourcesPath, 'core')
    : path.resolve(__dirname, '..', 'core')),
  ensureAll() {
    for (const d of [paths.data(), paths.logs(), paths.profiles(), paths.providers(), paths.cache()]) {
      fs.mkdirSync(d, { recursive: true });
    }
    return paths;
  },
};

module.exports = paths;
