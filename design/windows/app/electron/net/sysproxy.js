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
  return snap;
}

function restore(snap) {
  if (!snap) return;
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

/** 关闭并还原进入前的值 */
function disable(before) {
  restore(before || snapshot());
  log.info('system proxy restored');
}

module.exports = { enable, disable, snapshot, restore, broadcast, bypassList };
