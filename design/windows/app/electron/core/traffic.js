'use strict';
/**
 * 流量历史：分钟级（今日曲线）+ 天级（本周/本月）。
 *
 * 只存本地、只存数字，不落任何域名或连接信息。分钟桶保留 2 天，天桶保留 400 天。
 * 写盘去抖 30 秒，避免每秒写文件。
 */

const fs = require('fs');
const path = require('path');
const paths = require('../paths');
const log = require('../logger');

const MINUTE_KEEP_DAYS = 2;
const DAY_KEEP = 400;
const FLUSH_DEBOUNCE_MS = 30000;

let data = null;
let dirty = false;
let timer = null;

function file() { return paths.file('traffic.json'); }

function todayKey(d = new Date()) {
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

function minuteKey(d = new Date()) {
  const p = (n) => String(n).padStart(2, '0');
  return `${todayKey(d)} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

function load() {
  if (data) return data;
  try {
    const raw = JSON.parse(fs.readFileSync(file(), 'utf8'));
    data = { days: raw.days || {}, minutes: raw.minutes || {}, total: raw.total || { up: 0, down: 0 } };
  } catch (_) {
    data = { days: {}, minutes: {}, total: { up: 0, down: 0 } };
  }
  return data;
}

function scheduleFlush() {
  dirty = true;
  if (timer) return;
  timer = setTimeout(() => { timer = null; flush(); }, FLUSH_DEBOUNCE_MS);
  if (timer.unref) timer.unref();
}

function flush() {
  if (!dirty || !data) return;
  dirty = false;
  try {
    fs.mkdirSync(paths.data(), { recursive: true });
    const tmp = `${file()}.tmp`;
    fs.writeFileSync(tmp, JSON.stringify(data), 'utf8');
    fs.renameSync(tmp, file());
  } catch (e) {
    log.warn('traffic flush failed:', e && e.message);
  }
}

/** 记一笔增量（字节） */
function add(up, down) {
  const d = load();
  const u = Number(up) || 0;
  const dn = Number(down) || 0;
  if (u === 0 && dn === 0) return;
  const mk = minuteKey();
  const dk = todayKey();
  d.minutes[mk] = d.minutes[mk] || { up: 0, down: 0 };
  d.minutes[mk].up += u;
  d.minutes[mk].down += dn;
  d.days[dk] = d.days[dk] || { up: 0, down: 0 };
  d.days[dk].up += u;
  d.days[dk].down += dn;
  d.total.up += u;
  d.total.down += dn;
  scheduleFlush();
}

function prune() {
  const d = load();
  const cutoff = new Date(Date.now() - MINUTE_KEEP_DAYS * 86400e3);
  const cutStr = `${todayKey(cutoff)} 00:00`;
  for (const k of Object.keys(d.minutes)) if (k < cutStr) delete d.minutes[k];
  const dayKeys = Object.keys(d.days).sort();
  while (dayKeys.length > DAY_KEEP) delete d.days[dayKeys.shift()];
}

/**
 * 曲线数据。
 * @param {'today'|'week'|'month'} range
 * @returns {{points: {label: string, up: number, down: number}[], unit: string}}
 */
function series(range) {
  prune();
  const d = load();
  const MB = 1024 * 1024;
  if (range === 'week' || range === 'month') {
    const n = range === 'week' ? 7 : 30;
    const points = [];
    for (let i = n - 1; i >= 0; i -= 1) {
      const day = new Date(Date.now() - i * 86400e3);
      const k = todayKey(day);
      const v = d.days[k] || { up: 0, down: 0 };
      points.push({
        label: `${day.getMonth() + 1}/${day.getDate()}`,
        up: Math.round(v.up / MB * 100) / 100,
        down: Math.round(v.down / MB * 100) / 100,
      });
    }
    return { points, unit: 'MB' };
  }
  // 今日：按小时聚合，24 个点
  const dk = todayKey();
  const points = [];
  for (let hh = 0; hh < 24; hh += 1) {
    const hhStr = String(hh).padStart(2, '0');
    let up = 0;
    let down = 0;
    for (const [k, v] of Object.entries(d.minutes)) {
      if (k.startsWith(`${dk} ${hhStr}:`)) { up += v.up; down += v.down; }
    }
    points.push({
      label: `${hhStr}:00`,
      up: Math.round(up / MB * 100) / 100,
      down: Math.round(down / MB * 100) / 100,
    });
  }
  const lastHour = new Date().getHours();
  return { points: points.slice(0, lastHour + 1), unit: 'MB' };
}

function totals() {
  return Object.assign({}, load().total);
}

function reset() {
  data = { days: {}, minutes: {}, total: { up: 0, down: 0 } };
  dirty = true;
  flush();
}

module.exports = { add, series, totals, reset, flush, prune };
