'use strict';
/** 滚动日志：写 data/logs/polaris.log，单文件 4MB，保留 3 份。 */

const fs = require('fs');
const path = require('path');
const paths = require('./paths');

const MAX_BYTES = 4 * 1024 * 1024;
const KEEP = 3;

const SECRET_KEYS = /^(authorization|token|password|auth_data|subscribe_url|secret|jwt)$/i;

function redact(value, depth = 0) {
  if (depth > 4) return '[deep]';
  if (value === null || value === undefined) return value;
  if (Array.isArray(value)) return value.slice(0, 20).map((v) => redact(v, depth + 1));
  if (typeof value === 'object') {
    const out = {};
    for (const [k, v] of Object.entries(value)) {
      out[k] = SECRET_KEYS.test(k) ? '***' : redact(v, depth + 1);
    }
    return out;
  }
  return value;
}

let stream = null;
let disabled = false;
let stdoutBroken = false;

/**
 * 往 stdout 写日志。
 * 必须容错：父进程/控制台关闭后管道会断，process.stdout.write 抛 EPIPE，
 * 而 uncaughtException 处理器又会调 log.error → 再写 stdout → 无限递归刷屏，
 * 应用卡在报错循环里不再干活（DEVNOTES A-14）。断一次就永久闭嘴，文件日志照常。
 */
function toStdout(line) {
  if (stdoutBroken) return;
  if (process.env.POLARIS_LOG_STDOUT === '0' || process.env.NODE_ENV === 'production') return;
  try {
    process.stdout.write(line);
  } catch (_) {
    stdoutBroken = true;
  }
}

// 管道异步断开时会在流上抛 error 事件；没有监听器就会变成未捕获异常
try {
  process.stdout.on('error', () => { stdoutBroken = true; });
} catch (_) {}

function open() {
  if (stream || disabled) return stream;
  try {
    fs.mkdirSync(paths.logs(), { recursive: true });
    const file = path.join(paths.logs(), 'polaris.log');
    rotateIfNeeded(file);
    stream = fs.createWriteStream(file, { flags: 'a' });
    stream.on('error', () => { stream = null; });
    return stream;
  } catch (_) {
    disabled = true;
    return null;
  }
}

function rotateIfNeeded(file) {
  try {
    const st = fs.statSync(file);
    if (st.size < MAX_BYTES) return;
  } catch (_) {
    return;
  }
  for (let i = KEEP - 1; i >= 1; i -= 1) {
    const from = `${file}.${i}`;
    const to = `${file}.${i + 1}`;
    try { fs.renameSync(from, to); } catch (_) {}
  }
  try { fs.renameSync(file, `${file}.1`); } catch (_) {}
}

/**
 * 日志时间戳：**本地时间** `YYYY-MM-DD HH:mm:ss.SSS±HH:MM`。
 *
 * 以前是 `toISOString()`（UTC），在东八区比用户看到的时间早 8 小时，
 * 同一份导出日志里 mihomo 的行是 `2026-10-13T07:59:18.772630000+08:00`、
 * 应用的行却是 `2026-10-12 23:59:18.772`，两条时间线对不上，排查时得手算，
 * 还容易把「更新失败发生在 23:59」看成别的时间（DEVNOTES B-*）。
 * 带上偏移量，换时区或把日志发给别人也一眼能换算。
 */
function stamp(d = new Date()) {
  const p = (n, w = 2) => String(n).padStart(w, '0');
  const off = -d.getTimezoneOffset();
  const abs = Math.abs(off);
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} `
    + `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}.${p(d.getMilliseconds(), 3)}`
    + `${off < 0 ? '-' : '+'}${p(Math.floor(abs / 60))}:${p(abs % 60)}`;
}

function fmt(level, args) {
  return `${stamp()} ${level.padEnd(5)} ${interpolate(args)}\n`;
}

function safeJson(v) {
  try { return JSON.stringify(redact(v)); } catch (_) { return String(v); }
}

function write(level, args) {
  const line = fmt(level, args);
  toStdout(line);
  const s = open();
  if (s) s.write(line);
}

function fmtOne(a) {
  return typeof a === 'string' ? a : safeJson(a);
}

/** printf 风格占位替换：%s / %d / %j / %%。
 *  第一个字符串参数是格式串；被消费掉的参数不再重复输出（同 console.log）。 */
function interpolate(args) {
  if (args.length === 0) return '';
  const first = args[0];
  if (typeof first !== 'string' || first.indexOf('%') < 0) {
    return args.map(fmtOne).join(' ');
  }
  let i = 1;
  const head = first.replace(/%([sdj%])/g, (m, k) => {
    if (k === '%') return '%';
    if (i >= args.length) return m;
    return fmtOne(args[i++]);
  });
  const rest = args.slice(i).map(fmtOne);
  return [head, ...rest].join(' ');
}

module.exports = {
  info: (...a) => write('INFO', a),
  warn: (...a) => write('WARN', a),
  error: (...a) => write('ERROR', a),
  debug: (...a) => { if (process.env.POLARIS_DEBUG === '1') write('DEBUG', a); },
  redact,
  stamp,
  /** 导出最近 N 行日志，供"导出日志"功能使用 */
  tail(lines = 2000) {
    try {
      const file = path.join(paths.logs(), 'polaris.log');
      const raw = fs.readFileSync(file, 'utf8');
      return raw.split('\n').filter(Boolean).slice(-lines).join('\n');
    } catch (_) {
      return '';
    }
  },
};
