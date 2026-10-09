'use strict';
/**
 * TUN 模式的 Windows 侧辅助。
 *
 * mihomo 用 wintun 建一张名为 `Polaris` 的虚拟网卡并改路由表，正常退出时会自己
 * 收尾。但 Windows 上 Node 的 child.kill() 是 TerminateProcess，内核拿不到清理
 * 时机（SIGTERM 在 Windows 上没有语义）—— 异常退出后可能留下：
 *   · 一张处于「已断开」状态的 Polaris 虚拟网卡
 *   · 指向它的残留路由（接口断开会使其失效，通常不影响上网，但会脏）
 *
 * 这里只做两件事：查状态、按用户明确要求清理。**不自动删网卡** ——
 * 动网络适配器是重操作，宁可让用户点一下。
 */

const { execFile } = require('child_process');
const log = require('../logger');

const ADAPTER = 'Polaris';

function ps(script, timeoutMs = 20000) {
  return new Promise((resolve) => {
    execFile('powershell', ['-NoProfile', '-NonInteractive', '-Command', script],
      { windowsHide: true, timeout: timeoutMs }, (err, stdout) => {
        resolve({ err, out: String(stdout || '').trim() });
      });
  });
}

/** 查询 Polaris 虚拟网卡状态 */
async function status() {
  if (process.platform !== 'win32') return { supported: false, exists: false };
  const { err, out } = await ps(
    `$a = Get-NetAdapter -Name '${ADAPTER}' -ErrorAction SilentlyContinue; ` +
    `if ($a) { "$($a.Status)|$($a.InterfaceDescription)|$($a.ifIndex)" } else { 'NONE' }`,
  );
  if (err) return { supported: true, exists: false, error: err.message };
  if (out === 'NONE' || !out) return { supported: true, exists: false };
  const [state, desc, index] = out.split('|');
  return { supported: true, exists: true, state, description: desc, ifIndex: Number(index) || 0 };
}

/** 删除残留的 Polaris 虚拟网卡（需要管理员） */
async function cleanup() {
  if (process.platform !== 'win32') return { ok: false, msg: '仅支持 Windows' };
  const st = await status();
  if (!st.exists) return { ok: true, msg: '没有残留的虚拟网卡' };
  if (st.state === 'Up') return { ok: false, msg: '虚拟网卡正在使用中，请先断开连接' };
  const { err } = await ps(`Remove-NetAdapter -Name '${ADAPTER}' -Confirm:$false -ErrorAction Stop`);
  if (err) {
    log.warn('清理虚拟网卡失败:', err.message);
    return { ok: false, msg: '清理失败（需要管理员权限）' };
  }
  log.info('已清理残留虚拟网卡');
  return { ok: true, msg: '已清理残留虚拟网卡' };
}

/**
 * 停内核：先给一点时间让它自己收尾，再强杀。
 * 对 TUN 模式尤其重要 —— 争取让 mihomo 走到 tun.Close()。
 */
function stopProcess(proc, graceMs = 1200) {
  if (!proc) return Promise.resolve();
  return new Promise((resolve) => {
    let done = false;
    const finish = () => { if (!done) { done = true; resolve(); } };
    proc.once('exit', finish);
    try { proc.kill(); } catch (_) {}
    setTimeout(() => {
      try { proc.kill('SIGKILL'); } catch (_) {}
      finish();
    }, graceMs);
  });
}

module.exports = { status, cleanup, stopProcess, ADAPTER };
