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

module.exports = { bytes, speed, duration, modeLabel, modeKey };
