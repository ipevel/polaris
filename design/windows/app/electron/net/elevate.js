'use strict';
/**
 * 管理员权限检测与提权重启。
 *
 * TUN 模式要创建 wintun 虚拟网卡并改路由表，必须提权。这里不常驻提权，
 * 只在用户明确开启 TUN 时以管理员身份重启整个应用 —— 进程重启后
 * 单实例锁会让旧进程退出，状态从 data/ 恢复。
 */

const { execFile, execFileSync, spawn } = require('child_process');
const { app, dialog } = require('electron');
const path = require('path');
const log = require('../logger');

let cachedAdmin = null;

/** 当前进程是否已是管理员 */
function isAdmin() {
  if (cachedAdmin !== null) return Promise.resolve(cachedAdmin);
  return new Promise((resolve) => {
    if (process.platform !== 'win32') { cachedAdmin = process.getuid && process.getuid() === 0; resolve(cachedAdmin); return; }
    execFile('net', ['session'], { windowsHide: true, timeout: 8000 }, (err, stdout, stderr) => {
      const out = `${stdout || ''}${stderr || ''}`;
      // 非管理员时 net session 会报「拒绝访问 / Access is denied」
      cachedAdmin = !err && !/拒绝访问|Access is denied/i.test(out);
      resolve(cachedAdmin);
    });
  });
}

function isAdminSync() {
  if (process.platform !== 'win32') return false;
  try {
    execFileSync('net', ['session'], { windowsHide: true, timeout: 8000, stdio: 'ignore' });
    return true;
  } catch (_) {
    return false;
  }
}

/**
 * 以管理员身份重启。返回 {ok, msg}。
 * 用户在 UAC 弹窗点「否」时 ok=false。
 */
function restartAsAdmin(extraArgs = []) {
  return new Promise((resolve) => {
    if (process.platform !== 'win32') { resolve({ ok: false, msg: '当前平台不支持提权重启' }); return; }
    const exe = app.getPath('exe');
    const args = process.argv.slice(1).filter((a) => !a.startsWith('--squirrel'));
    // 用 PowerShell 的 Start-Process -Verb RunAs 触发 UAC；返回码 1 表示用户拒绝
    const argList = args.concat(extraArgs).map((a) => `'${String(a).replace(/'/g, "''")}'`).join(',');
    const script = argList
      ? `Start-Process -FilePath '${exe.replace(/'/g, "''")}' -ArgumentList @(${argList}) -Verb RunAs`
      : `Start-Process -FilePath '${exe.replace(/'/g, "''")}' -Verb RunAs`;

    const ps = spawn('powershell', ['-NoProfile', '-NonInteractive', '-Command', script], {
      windowsHide: true,
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    let err = '';
    ps.stderr.on('data', (b) => { err += b.toString(); });
    ps.on('error', (e) => resolve({ ok: false, msg: e.message }));
    ps.on('exit', (code) => {
      if (code === 0) {
        log.info('relaunching as administrator');
        resolve({ ok: true });
        // 让新进程先拿到单实例锁再退出，否则新进程会直接退掉
        setTimeout(() => app.quit(), 600);
      } else {
        log.warn(`elevation declined or failed (code=${code}) ${err.slice(0, 200)}`);
        resolve({ ok: false, msg: '未获得管理员权限（UAC 被取消）' });
      }
    });
  });
}

module.exports = { isAdmin, isAdminSync, restartAsAdmin };
