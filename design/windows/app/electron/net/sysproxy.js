'use strict';
/**
 * Windows 系统代理（WinINET / IE 设置），免管理员即可写。
 *
 * 只动 HKCU：ProxyEnable / ProxyServer / ProxyOverride，并清掉 PAC，
 * 写完必须广播 InternetSetOption 让已运行的浏览器立刻重读，
 * 否则 Chrome 等要等到下一轮轮询甚至重启才生效。
 *
 * 退出/断开时严格还原进入前的值，异常退出靠 boot 时读取的快照兜底。
 */

const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const paths = require('../paths');
const log = require('../logger');

const KEY = 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings';
const VALUES = ['ProxyEnable', 'ProxyServer', 'ProxyOverride', 'AutoConfigURL'];

function regQuery(name) {
  try {
    const out = execFileSync(
      'reg',
      ['query', KEY, '/v', name],
      { encoding: 'utf8', windowsHide: true, timeout: 5000, stdio: ['ignore', 'pipe', 'ignore'] },
    );
    const m = out.match(new RegExp(`${name}\\s+(\\S+)\\s+(.*)$`, 'm'));
    if (!m) return null;
    const raw = m[2].trim();
    if (/^0x/i.test(raw)) return parseInt(raw, 16);
    return raw;
  } catch (_) {
    return null;
  }
}

function regSet(name, type, value) {
  execFileSync(
    'reg',
    ['add', KEY, '/v', name, '/t', type, '/d', String(value), '/f'],
    { windowsHide: true, timeout: 5000, stdio: 'ignore' },
  );
}

/** 删一个本来就不存在的值会返回非零并往 stderr 吐内容，这里静默处理 */
function regDelete(name) {
  try {
    execFileSync('reg', ['delete', KEY, '/v', name, '/f'],
      { windowsHide: true, timeout: 5000, stdio: 'ignore' });
  } catch (_) {
    /* 本来就没有，忽略 */
  }
}

const REFRESH_PS1 = `
Add-Type -Namespace PolarisWin -Name Native -MemberDefinition @"
[DllImport("wininet.dll", SetLastError=true)]
public static extern bool InternetSetOption(IntPtr hInternet, int dwOption, IntPtr lpBuffer, int dwBufferLength);
"@
[void][PolarisWin.Native]::InternetSetOption([IntPtr]::Zero, 39, [IntPtr]::Zero, 0)
[void][PolarisWin.Native]::InternetSetOption([IntPtr]::Zero, 37, [IntPtr]::Zero, 0)
`;

/** 通知系统代理已变更（39 = SETTINGS_CHANGED，37 = REFRESH） */
function broadcast() {
  try {
    fs.mkdirSync(paths.cache(), { recursive: true });
    const script = path.join(paths.cache(), 'refresh-proxy.ps1');
    if (!fs.existsSync(script)) fs.writeFileSync(script, REFRESH_PS1, 'utf8');
    execFileSync(
      'powershell',
      ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', script],
      { windowsHide: true, timeout: 15000 },
    );
  } catch (e) {
    log.warn('sysproxy broadcast failed:', e && e.message);
  }
}

function snapshot() {
  const snap = {};
  for (const v of VALUES) snap[v] = regQuery(v);
  // 读失败不能当成「用户本来没设代理」——否则还原时会把用户的配置删掉/清零
  if (snap.ProxyEnable == null && snap.ProxyServer == null && snap.ProxyOverride == null) return null;
  return snap;
}

function restore(snap) {
  if (!snap || typeof snap !== 'object') {
    log.warn('sysproxy restore skipped: no valid snapshot');
    return;
  }
  try {
    regSet('ProxyEnable', 'REG_DWORD', snap.ProxyEnable == null ? 0 : snap.ProxyEnable);
    if (snap.ProxyServer) regSet('ProxyServer', 'REG_SZ', snap.ProxyServer);
    else regDelete('ProxyServer');
    if (snap.ProxyOverride) regSet('ProxyOverride', 'REG_SZ', snap.ProxyOverride);
    else regDelete('ProxyOverride');
    if (snap.AutoConfigURL) regSet('AutoConfigURL', 'REG_SZ', snap.AutoConfigURL);
    else regDelete('AutoConfigURL');
    broadcast();
  } catch (e) {
    log.error('sysproxy restore failed:', e && e.message);
  }
}

function bypassList(extraDomains = []) {
  const base = [
    '<local>',
    'localhost',
    '127.*',
    '10.*',
    '172.16.*', '172.17.*', '172.18.*', '172.19.*',
    '172.20.*', '172.21.*', '172.22.*', '172.23.*',
    '172.24.*', '172.25.*', '172.26.*', '172.27.*',
    '172.28.*', '172.29.*', '172.30.*', '172.31.*',
    '192.168.*',
    '*.local',
  ];
  return base.concat(extraDomains.filter(Boolean)).join(';');
}

/**
 * 开启系统代理。
 * @returns {object|null} 进入前的快照，供 disable 原样还原
 */
function enable(host, port, extraDomains = []) {
  const before = snapshot();
  // 拿不到快照就不动注册表：改了却还原不回去，等于永久改掉用户的系统代理
  if (!before) {
    log.warn('sysproxy enable skipped: cannot read current settings');
    return null;
  }
  try {
    regDelete('AutoConfigURL'); // 与 PAC 互斥
    regSet('ProxyServer', 'REG_SZ', `${host}:${port}`);
    regSet('ProxyOverride', 'REG_SZ', bypassList(extraDomains));
    regSet('ProxyEnable', 'REG_DWORD', 1);
    broadcast();
    log.info(`system proxy on -> ${host}:${port}`);
    return before;
  } catch (e) {
    log.error('sysproxy enable failed:', e && e.message);
    return before;
  }
}

/**
 * 关闭并还原进入前的值。
 *
 * 关键：拿不到快照时**绝不能写注册表**。旧实现是 `restore(before || snapshot())`，
 * 快照丢了就把「当前值」（也就是 Polaris 自己刚写进去的 127.0.0.1:<内核端口>）
 * 当成用户的原始设置还原回去 —— 结果是内核端口一换，用户的系统代理就永久指向一个
 * 死端口，而且此后再也还原不回来（快照被污染成 Polaris 自己的值）。
 */
function disable(before) {
  if (!before || typeof before !== 'object') {
    log.warn('sysproxy disable skipped: no snapshot to restore');
    return;
  }
  restore(before);
  log.info('system proxy restored');
}

module.exports = { enable, disable, snapshot, restore, broadcast, bypassList };
