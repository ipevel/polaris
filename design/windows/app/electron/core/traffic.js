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

function dayKeyOf(d) { return todayKey(d); }

/** 本周一 00:00（周一为一周起点，与面板口径一致） */
function startOfWeek(d = new Date()) {
  const x = new Date(d.getFullYear(), d.getMonth(), d.getDate());
  const dow = (x.getDay() + 6) % 7;   // 周一 = 0
  x.setDate(x.getDate() - dow);
  return x;
}

/** 本月 1 日 00:00 */
function startOfMonth(d = new Date()) {
  return new Date(d.getFullYear(), d.getMonth(), 1);
}

function addDays(d, n) {
  const x = new Date(d.getFullYear(), d.getMonth(), d.getDate());
  x.setDate(x.getDate() + n);
  return x;
}

/** 'YYYY-MM-DD' → Date（本地时区零点） */
function parseDay(key) {
  const [y, m, dd] = String(key).split('-').map((s) => parseInt(s, 10));
  return new Date(y || 1970, (m || 1) - 1, dd || 1);
}

/** 枚举 [from, today] 之间的每一天（含两端），最多 DAY_KEEP 天 */
function dayRange(from) {
  const out = [];
  const last = new Date();
  const guard = DAY_KEEP + 2;
  for (let t = from, i = 0; dayKeyOf(t) <= dayKeyOf(last) && i < guard; t = addDays(t, 1), i += 1) {
    out.push(new Date(t.getFullYear(), t.getMonth(), t.getDate()));
  }
  return out;
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
 * @param {'today'|'week'|'month'|'all'} range
 *   today = 今日 24 小时；week = 本周一至今；month = 本月 1 日至今；
 *   all   = 最早有记录的那天至今（天桶保留 DAY_KEEP=400 天，够看一年多）
 * @returns {{points: {label: string, up: number, down: number}[], unit: string}}
 */
function series(range) {
  prune();
  const d = load();
  const MB = 1024 * 1024;
  if (range === 'week' || range === 'month' || range === 'all') {
    let from = null;
    if (range === 'week') from = startOfWeek();
    else if (range === 'month') from = startOfMonth();
    else {
      // 'all'：从最早有记录的那天开始，而不是「最近 30 天」——
      // 旧实现把「本月」做成最近 30 天，用户看不到更早的用量。
      const keys = Object.keys(d.days).sort();
      if (!keys.length) return { points: [], unit: 'MB' };
      from = parseDay(keys[0]);
    }
    const points = dayRange(from).map((day) => {
      const k = dayKeyOf(day);
      const v = d.days[k] || { up: 0, down: 0 };
      return {
        label: `${day.getMonth() + 1}/${day.getDate()}`,
        up: Math.round(v.up / MB * 100) / 100,
        down: Math.round(v.down / MB * 100) / 100,
      };
    });
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
