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
 *
 * ---- 2026-10-14 真机日志改的一条铁律（用户拍板）----
 * 日志里出现过：`close port owner: pid=16256 name= closed=false forced=true`
 * —— 认不出占用者是谁，却把它强杀了。用户明确：**探测不出是谁，就不许动它**。
 * 所以 close() 现在是「认不出 → 直接拒绝，一步都不走」，宁可让用户手动处理，
 * 也不能误杀他机器上某个重要进程。
 */

const { execFileSync, spawnSync } = require('child_process');

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

function tidy(s) {
  return String(s == null ? '' : s).replace(/[\r\n]+/g, ' ').trim();
}

/**
 * PID → 进程名。三条退路，都失败就返回空串（**空串是有意义的**：调用方据此拒绝动手）。
 *
 * 1. tasklist CSV —— 常规路径，最快；
 * 2. tasklist LIST —— 有些系统上 CSV 的首列被本地化表头/引号规则搞乱时兜底；
 * 3. wmic —— 前两条都被拒（受保护进程）时最后的退路；Win11 24H2 起 wmic 可能已移除，
 *    拿不到就算了，不影响结论。
 */
function nameOf(pid) {
  try {
    const out = execFileSync('tasklist.exe', ['/FI', `PID eq ${pid}`, '/FO', 'CSV', '/NH'],
      { encoding: 'utf8', windowsHide: true });
    const m = out.match(/^"([^"]+)"/);
    if (m && tidy(m[1])) return tidy(m[1]);
  } catch (_) { /* 进程可能刚好退出了 */ }
  try {
    const out = execFileSync('tasklist.exe', ['/FI', `PID eq ${pid}`, '/FO', 'LIST', '/NH'],
      { encoding: 'utf8', windowsHide: true });
    const m = out.match(/^\s*(?:映像名称|Image Name)\s*:\s*(\S+)\s*$/im);
    if (m && tidy(m[1])) return tidy(m[1]);
  } catch (_) { /* 同上 */ }
  try {
    const out = execFileSync('wmic.exe', ['process', 'where', `processid=${pid}`, 'get', 'Name', '/value'],
      { encoding: 'utf8', windowsHide: true });
    const m = out.match(/^\s*Name\s*=\s*(\S+)\s*$/im);
    if (m && tidy(m[1])) return tidy(m[1]);
  } catch (_) { /* wmic 可能已不存在 */ }
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
 * 该不该动手 —— 纯函数，好单测。
 * @returns {{allow:boolean, reason:string}} reason ∈ '' | 'invalid' | 'self' | 'unidentified'
 */
function closeDecision(pid, selfPid, name) {
  const id = Number(pid) || 0;
  if (!id) return { allow: false, reason: 'invalid' };
  if (id === Number(selfPid)) return { allow: false, reason: 'self' };
  if (!tidy(name)) return { allow: false, reason: 'unidentified' };
  return { allow: true, reason: '' };
}

/** taskkill 说的话 → 机器可读的原因（中英都要认） */
function killReason(text, code) {
  const t = String(text || '');
  if (/拒绝访问|Access is denied|Access denied/i.test(t)) return 'access-denied';
  if (/没有找到|not found|no running instance|没有运行/i.test(t)) return 'not-found';
  // 控制台进程没有窗口可收 WM_CLOSE，Windows 直接说"只能强制终止" —— 这不是失败，
  // 是"优雅那条路对它不适用"，下面立刻走强杀。
  if (/只能强制终止|only be terminated forcefully/i.test(t)) return 'needs-force';
  return code === 0 ? '' : 'failed';
}

/**
 * @returns {{busy:boolean, port:number, pid?:number, name?:string, identified?:boolean, reason?:string}}
 *   busy=true 时 name 是进程名（拿不到就退化成 "PID 1234" 好显示）；
 *   identified=false 表示**没认出是谁**，界面据此不给"关掉它"的按钮。
 */
function owner(port) {
  const p = Number(port) || 0;
  if (p <= 0) return { busy: false, port: p };
  for (const pid of listeners(p)) {
    if (pid === process.pid) continue;      // 自己（主进程）不算冲突
    const name = nameOf(pid);
    return {
      busy: true,
      port: p,
      pid,
      name: name || `PID ${pid}`,
      identified: !!name,
      reason: name ? '' : 'unidentified',
    };
  }
  return { busy: false, port: p };
}

/**
 * 优雅地关掉它。**认不出是谁就一步都不走。**
 * @param {object} [opts] { graceMs, nameForTest }
 *   nameForTest 只给离线自测用：真机上没法构造"进程在、但 tasklist 认不出它"的情形
 *   （那需要一个受保护进程），所以留一个显式注入口来覆盖「拒绝动手」那条路。
 * @returns {{closed:boolean, forced:boolean, name:string, reason:string}}
 *   reason ∈ '' | 'invalid' | 'self' | 'unidentified' | 'access-denied' | 'not-found' | 'still-running' | 'failed'
 */
async function close(pid, opts = {}) {
  const id = Number(pid) || 0;
  const graceMs = Number(opts.graceMs) > 0 ? Number(opts.graceMs) : 2500;
  const name = opts.nameForTest !== undefined ? tidy(opts.nameForTest) : nameOf(id);
  const d = closeDecision(id, process.pid, name);
  // 铁律：invalid / self / 认不出是谁 —— 一步都不走，连"请它自己退"都不发。
  if (!d.allow) return { closed: false, forced: false, name, reason: d.reason };

  const kill = (force) => {
    try {
      const r = spawnSync('taskkill.exe', force ? ['/F', '/PID', String(id)] : ['/PID', String(id)],
        { encoding: 'utf8', windowsHide: true });
      const code = r.status === null ? -1 : r.status;
      return { ok: code === 0, code, text: tidy(`${r.stdout || ''} ${r.stderr || ''}`) };
    } catch (e) {
      return { ok: false, code: -1, text: tidy(e && e.message) };
    }
  };

  const first = kill(false);                    // 先请它自己退
  const fr = killReason(first.text, first.code);
  // 连"请它自己退"都吃了访问拒绝，说明权限不够 —— 2.5 秒后的强杀只会再吃一次，
  // 没必要让用户白等，直接把真实原因还给界面。
  if (fr === 'access-denied') return { closed: false, forced: false, name, reason: 'access-denied' };
  if (fr !== 'needs-force') {                   // 「只能强制终止」就别白等 graceful 窗口了
    const deadline = Date.now() + graceMs;
    while (Date.now() < deadline) {
      await new Promise((r) => setTimeout(r, 250));
      if (!alive(id)) return { closed: true, forced: false, name, reason: '' };
    }
  }
  const hard = kill(true);                      // 不响应才强杀
  await new Promise((r) => setTimeout(r, 400));
  const gone = !alive(id);
  return {
    closed: gone,
    forced: true,
    name,
    reason: gone ? '' : (killReason(hard.text, hard.code) || 'still-running'),
  };
}

module.exports = { owner, close, listeners, alive, nameOf, closeDecision, killReason };
