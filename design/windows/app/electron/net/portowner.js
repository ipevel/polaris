'use strict';
/**
 * 谁占着这个本机端口 —— 用户第 10 条「遇到端口冲突，比如有另外的代理软件，
 * 应该优雅的关闭对应软件的运行」。
 *
 * 只用 Windows 自带工具，不引第三方依赖（便携包不带任何额外 dll）：
 *   netstat -ano -p TCP   → LISTENING 行的最后一列是 PID
 *   tasklist /FI PID eq N → 换成进程名给用户看
 *
 * 关的时候先 **不带 /F** 的 taskkill（发 WM_CLOSE，程序有机会保存配置、
 * 退到托盘或正常退出），过一会还不退再 /F 强杀 —— 用户要的是"优雅"，
 * 但也不能因为对方不响应就把连接卡死。
 */

const { execFileSync } = require('child_process');

/** 这个端口上所有正在 LISTEN 的 PID（可能不止一个：v4/v6 双栈） */
function listeners(port) {
  const want = Number(port) || 0;
  if (want <= 0) return [];
  let out = '';
  try {
    out = execFileSync('netstat.exe', ['-ano', '-p', 'TCP'], { encoding: 'utf8', windowsHide: true });
  } catch (_) {
    return [];
  }
  const pids = [];
  for (const line of out.split(/\r?\n/)) {
    // 例：  TCP    127.0.0.1:7890         0.0.0.0:0              LISTENING       12068
    const m = line.match(/^\s*TCP\s+\S+:(\d+)\s+\S+\s+LISTENING\s+(\d+)\s*$/i);
    if (!m) continue;
    if (Number(m[1]) !== want) continue;
    const pid = Number(m[2]);
    if (pid > 0 && pids.indexOf(pid) < 0) pids.push(pid);
  }
  return pids;
}

function nameOf(pid) {
  try {
    const out = execFileSync('tasklist.exe', ['/FI', `PID eq ${pid}`, '/FO', 'CSV', '/NH'], { encoding: 'utf8', windowsHide: true });
    const m = out.match(/^"([^"]+)"/);
    if (m) return m[1];
  } catch (_) { /* 进程可能刚好退出了 */ }
  return '';
}

/** 进程还在不在（Windows 上 process.kill(pid,0) 走 OpenProcess，EPERM = 还在但没权限） */
function alive(pid) {
  try {
    process.kill(pid, 0);
    return true;
  } catch (e) {
    return !!(e && e.code === 'EPERM');
  }
}

/**
 * @returns {{busy:boolean, port:number, pid?:number, name?:string}}
 *   busy=true 时 name 是进程名（拿不到就退化成 "PID 1234"）。
 */
function owner(port) {
  const p = Number(port) || 0;
  if (p <= 0) return { busy: false, port: p };
  for (const pid of listeners(p)) {
    if (pid === process.pid) continue;      // 自己（主进程）不算冲突
    const name = nameOf(pid);
    return { busy: true, port: p, pid, name: name || `PID ${pid}` };
  }
  return { busy: false, port: p };
}

/**
 * 优雅地关掉它。@returns {{closed:boolean, forced:boolean, name:string}}
 */
async function close(pid, opts = {}) {
  const id = Number(pid) || 0;
  const graceMs = Number(opts.graceMs) > 0 ? Number(opts.graceMs) : 2500;
  const name = nameOf(id);
  if (!id || id === process.pid) return { closed: false, forced: false, name };
  const kill = (force) => {
    try {
      execFileSync('taskkill.exe', force ? ['/F', '/PID', String(id)] : ['/PID', String(id)],
        { stdio: 'ignore', windowsHide: true });
      return true;
    } catch (_) {
      return false;
    }
  };
  kill(false);                                  // 先请它自己退
  const deadline = Date.now() + graceMs;
  while (Date.now() < deadline) {
    await new Promise((r) => setTimeout(r, 250));
    if (!alive(id)) return { closed: true, forced: false, name };
  }
  kill(true);                                   // 不响应才强杀
  await new Promise((r) => setTimeout(r, 400));
  return { closed: !alive(id), forced: true, name };
}

module.exports = { owner, close, listeners, alive };
