'use strict';
/** 展示格式化：与 Android 端 utils/FormatUtils 的口径对齐。 */

function bytes(n) {
  const v = Number(n) || 0;
  if (v < 1024) return `${Math.round(v)} B`;
  const units = ['KB', 'MB', 'GB', 'TB', 'PB'];
  let x = v / 1024;
  let i = 0;
  while (x >= 1024 && i < units.length - 1) { x /= 1024; i += 1; }
  return `${x < 10 ? x.toFixed(2) : x < 100 ? x.toFixed(1) : Math.round(x)} ${units[i]}`;
}

/** 速率：返回数值（MB/s），保留一位小数；UI 侧拼单位 */
function speed(bytesPerSec) {
  const v = (Number(bytesPerSec) || 0) / (1024 * 1024);
  if (v >= 100) return v.toFixed(0);
  return v.toFixed(1);
}

function duration(sec) {
  const s = Math.max(0, Math.floor(Number(sec) || 0));
  const h = String(Math.floor(s / 3600)).padStart(2, '0');
  const m = String(Math.floor((s % 3600) / 60)).padStart(2, '0');
  const ss = String(s % 60).padStart(2, '0');
  return `${h}:${m}:${ss}`;
}

const MODE_LABELS = { rule: '规则模式', global: '全局模式', direct: '直连模式' };
function modeLabel(m) { return MODE_LABELS[m] || String(m || ''); }
function modeKey(label) {
  const found = Object.keys(MODE_LABELS).find((k) => MODE_LABELS[k] === label);
  return found || label;
}

/**
 * 版本号比较。a > b 返回 1，相等 0，a < b 返回 -1。
 * 用来把「线上最新版」与当前版本比出"该不该亮红点" —— 存版本号而不是布尔值，
 * 用户装完新版本红点会自己消失。
 *
 * 口径（F3，按 SemVer 的判定顺序，只实现更新红点用得上的部分）：
 *   1. 数字段按**数值**比：1.10.0 > 1.9.9（字符串比会反过来）；段数不等时缺段补 0，
 *      所以 1.12 == 1.12.0 == 1.12.0.0。
 *   2. 预发布（-beta 这类）**小于同号正式版**：1.12.0-beta < 1.12.0。
 *      旧实现用 parseInt 逐段比，"-beta" 被吞成 0，两者判等 → 1.12.0-beta 的用户
 *      永远"已经是最新"，收不到正式版。
 *      预发布之间按 SemVer：标识逐段比，纯数字标识按数值、字母标识按 ASCII，
 *      前缀相同的短的更小（1.12.0-beta < 1.12.0-beta.2），数字标识 < 字母标识。
 *   3. build 元数据（+xxx）一律**忽略**：1.12.0+build == 1.12.0。
 *   4. 允许可选的前导 v/V（tag 里常见）：v1.12.0 == 1.12.0；前后空白先 trim。
 *   5. 非法输入（空串 / 空段 / 空标识 / 杂字符 / null）**不抛异常，返回 0**，
 *      即"没法比较，不声称有更新"。为什么不按 0.0.0 处理：cmpVersion(任意, '')
 *      曾经恒为 1，"有更新"永远成立（DEVNOTES E-23）—— 拿不准时宁可不亮红点，
 *      也不要让用户掉进更新循环。要区分"真的相等"和"没法比"用 isValidVersion()。
 */
const VERSION_RE = /^v?(\d+(?:\.\d+)*)(?:-([0-9A-Za-z.-]+))?(?:\+([0-9A-Za-z.-]+))?$/;

/** 解析出版本；非法返回 null。@returns {null|{nums:number[],pre:string[]}} */
function parseVersion(v) {
  const s = String(v == null ? '' : v).trim();
  const m = VERSION_RE.exec(s);
  if (!m) return null;
  const nums = m[1].split('.').map((x) => parseInt(x, 10));
  if (nums.some((n) => !Number.isFinite(n))) return null;
  // 正则允许 "a..b" / 结尾的 "."，这里把空标识挡掉
  const pre = m[2] ? m[2].split('.') : [];
  if (pre.some((p) => p === '')) return null;
  return { nums, pre };
}

/** 能不能当一个版本号用（cmpVersion 对非法输入返回 0，用这个区分"相等"与"没法比"） */
function isValidVersion(v) { return parseVersion(v) !== null; }

function cmpVersion(a, b) {
  const pa = parseVersion(a);
  const pb = parseVersion(b);
  if (!pa || !pb) return 0;
  for (let i = 0; i < Math.max(pa.nums.length, pb.nums.length); i += 1) {
    const x = pa.nums[i] || 0;
    const y = pb.nums[i] || 0;
    if (x !== y) return x > y ? 1 : -1;
  }
  // 主版本号相同 → 有预发布的那个更小
  if (pa.pre.length && !pb.pre.length) return -1;
  if (!pa.pre.length && pb.pre.length) return 1;
  for (let i = 0; i < Math.max(pa.pre.length, pb.pre.length); i += 1) {
    const x = pa.pre[i];
    const y = pb.pre[i];
    if (x === undefined) return -1;   // 前缀相同，短的更小
    if (y === undefined) return 1;
    if (x === y) continue;
    const nx = /^\d+$/.test(x);
    const ny = /^\d+$/.test(y);
    if (nx && ny) return Number(x) > Number(y) ? 1 : -1;
    if (nx) return -1;                // 数字标识 < 字母标识
    if (ny) return 1;
    return x > y ? 1 : -1;            // 字母标识按 ASCII 序
  }
  return 0;
}

module.exports = { bytes, speed, duration, modeLabel, modeKey, cmpVersion, isValidVersion };
