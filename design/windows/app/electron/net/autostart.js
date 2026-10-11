'use strict';
/**
 * 开机自启。
 *
 * 便携应用不写安装目录，写 HKCU\...\Run 的 exe 绝对路径 —— 移动目录后自启会指向
 * 旧位置，因此这里同时把"目录是否还是原来那个"作为健康检查交给 UI 提示。
 */

const { execFileSync } = require('child_process');
const path = require('path');
const { app } = require('electron');
const log = require('../logger');

const KEY = 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run';
const NAME = 'Polaris';

function value() {
  return process.platform === 'linux' ? path.join(process.env.HOME || '', '.config', 'autostart', `${NAME}.desktop`) : KEY;
}

function command() {
  return `"${app.getPath('exe')}" --autostart`;
}

function isEnabled() {
  if (process.platform !== 'win32') return false;
  try {
    const out = execFileSync('reg', ['query', KEY, '/v', NAME], { encoding: 'utf8', windowsHide: true, timeout: 5000 });
    return out.includes(NAME);
  } catch (_) {
    return false;
  }
}

function apply(enabled) {
  if (process.platform !== 'win32') return false;
  try {
    if (enabled) {
      execFileSync('reg', ['add', KEY, '/v', NAME, '/t', 'REG_SZ', '/d', command(), '/f'],
        { windowsHide: true, timeout: 5000 });
    } else {
      try {
        execFileSync('reg', ['delete', KEY, '/v', NAME, '/f'], { windowsHide: true, timeout: 5000 });
      } catch (_) {}
    }
    log.info(`autostart ${enabled ? 'enabled' : 'disabled'}: ${command()}`);
    return true;
  } catch (e) {
    log.error('autostart toggle failed:', e && e.message);
    return false;
  }
}

module.exports = { apply, isEnabled, command };
