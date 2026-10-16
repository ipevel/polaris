'use strict';
/**
 * 残留内核自检（PR-B）：找出「不是本进程拉起的、但属于我们自己的 mihomo.exe」。
 *
 * 为什么需要：Windows 上父进程死了子进程不会跟着死（Node 的 child.kill 就是
 * TerminateProcess）。主进程崩溃 / 被强杀之后，mihomo 会继续占着本机代理端口、占着
 * TUN 虚拟网卡，用户看到的是「连不上」或「端口被占」。portowner 那条路只在「端口被占」
 * 时才触发 —— 内核不占那个端口时，用户手上没有任何提示。
 *
 * 安全边界（延续用户 2026-10-14 拍板的铁律「认不出是谁就不许动」）：
 *   · 只认**命令行里带我们自己的数据目录**的 mihomo.exe —— 别的代理软件的 mihomo
 *     （Clash 系 GUI 一大把）一律不碰、连提示都不给，免得用户误杀；
 *   · 拿不到进程列表 / 解析失败（权限不足、非 Windows、PowerShell 不可用）→ 一条都不报；
 *   · 动手前再核一次进程名必须是 mihomo.exe，它不是就不再动。
 */
const { execFile } = require('child_process');
const log = require('../logger');

const IMAGE = 'mihomo.exe';

function ps(script, timeoutMs = 8000) {
  return new Promise((resolve) => {
    execFile('powershell', ['-NoProfile', '-NonInteractive', '-Command', script],
      { windowsHide: true, timeout: timeoutMs }, (err, stdout) => {
        resolve({ err, out: String(stdout || '').trim() });
      });
  });
}

/** 解析 Get-CimInstance 的 JSON（单个对象或数组都吃）。解析失败返回 null（与「没有」区分开） */
function parseProcs(text) {
  const raw = String(text == null ? '' : text).trim();
  if (!raw || raw === 'null' || raw === '[]') return [];
  let json;
  try { json = JSON.parse(raw); } catch (_) { return null; }
  const arr = Array.isArray(json) ? json : [json];
  return arr.map((p) => ({
    pid: Number((p && (p.ProcessId != null ? p.ProcessId : p.processId)) || 0),
    cmd: String((p && (p.CommandLine != null ? p.CommandLine : p.commandLine)) || ''),
  })).filter((p) => p.pid > 0);
}

/**
 * 命令行里是否真的用了我们的数据目录。
 * 后面的字符必须是空白或结尾 —— 否则 Polaris-portable-1.12.1 会被 Polaris-portable-1.12.10 误命中。
 */
function usesDataDir(cmd, dataDir) {
  const c = String(cmd || '').replace(/\\/g, '/').replace(/"/g, '').toLowerCase();
  const d = String(dataDir || '').replace(/\\/g, '/').replace(/\/+$/, '').toLowerCase();
  if (!c || !d) return false;
  const esc = d.replace(/[.*+?^$\(\)\[\]{}|]/g, '\\$&');
  return new RegExp(esc + '(?=[\\s]|$)', 'i').test(c);
}

/**
 * 找出属于我们自己的残留内核。
 * @param {string} dataDir 我们自己的数据目录（内核的 -d 参数指向它）
 * @param {number} [exceptPid] 当前正在跑的内核 pid（它不是残留）
 * @returns {Promise<{supported:boolean, list:Array<{pid:number,cmd:string}>, error?:string}>}
 */
async function findLeftovers(dataDir, exceptPid) {
  if (process.platform !== 'win32') return { supported: false, list: [] };
  const { err, out } = await ps(
    "Get-CimInstance Win32_Process -Filter \"Name='" + IMAGE + "'\" | " +
    'Select-Object ProcessId,CommandLine | ConvertTo-Json -Compress -Depth 3',
  );
  if (err) {
    log.warn('残留内核自检：拿不到进程列表（' + (err && err.message) + '）');
    return { supported: true, list: [], error: String((err && err.message) || 'error') };
  }
  const procs = parseProcs(out);
  if (procs === null) {
    log.warn('残留内核自检：进程列表解析失败，按「没有」处理，不猜');
    return { supported: true, list: [], error: 'parse' };
  }
  const skip = Number(exceptPid) || 0;
  return { supported: true, list: procs.filter((p) => p.pid !== skip && usesDataDir(p.cmd, dataDir)) };
}

/**
 * 结束一个残留内核。**动手前三道守卫**：
 *   ① 认不出进程名 → 不动；② 名字不是 mihomo.exe → 不动；
 *   ③ **再核一次判据**：这个 pid 必须出现在 findLeftovers() 的名单里（= 命令行里带我们自己的
 *      data 目录）。名单外的一律不碰 —— 别的 Clash GUI 的 mihomo 即使名字一样也不杀；
 *      非 Windows（supported:false，一条都不报）同样落到这条，什么都不动。
 * 每一次拒绝都留 log.warn（可见记录），绝不静默杀进程。
 * @param {object} [opts] dataDir（默认 paths.data()）/ listForTest（自检注入名单）/ nameForTest / graceMs
 * @returns {Promise<{closed:boolean, forced?:boolean, name?:string, reason:string}>}
 */
async function killLeftover(pid, opts = {}) {
  const id = Number(pid) || 0;
  const portowner = require('./portowner');
  const name = opts.nameForTest !== undefined ? String(opts.nameForTest || '') : portowner.nameOf(id);
  if (!id) return { closed: false, name: '', reason: 'invalid' };
  if (!name) return { closed: false, name: '', reason: 'unidentified' };
  if (name.toLowerCase() !== IMAGE) {
    log.warn('残留内核：PID ' + id + ' 的进程名不是 ' + IMAGE + '（' + name + '），不动手');
    return { closed: false, name: name, reason: 'not-ours' };
  }
  let list;
  if (Array.isArray(opts.listForTest)) {
    list = opts.listForTest;
  } else {
    const dataDir = opts.dataDir || require('../paths').data();
    let found;
    try { found = await findLeftovers(dataDir, 0); } catch (e) {
      log.warn('残留内核：动手前复核名单失败（' + (e && e.message) + '），不动手');
      return { closed: false, name: name, reason: 'recheck-failed' };
    }
    list = (found && found.list) || [];
  }
  if (!list.some((x) => Number(x && x.pid) === id)) {
    log.warn('残留内核：PID ' + id + ' 不在自检名单里（不是我们 data 目录启动的 ' + IMAGE + '），不动手');
    return { closed: false, name: name, reason: 'not-in-list' };
  }
  const r = await portowner.close(id, { nameForTest: name, graceMs: opts.graceMs });
  log.info('残留内核：已请求结束 PID ' + id + '（' + (r && r.closed ? '成功' : '未结束') + (r && r.forced ? '，强制' : '') + '）');
  return Object.assign({ name: name, pid: id }, r);
}

module.exports = { findLeftovers, parseProcs, usesDataDir, killLeftover, IMAGE };
