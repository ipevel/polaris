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

function fmt(level, args) {
  const ts = new Date().toISOString().replace('T', ' ').slice(0, 23);
  return `${ts} ${level.padEnd(5)} ${interpolate(args)}\n`;
}

function safeJson(v) {
  try { return JSON.stringify(redact(v)); } catch (_) { return String(v); }
}

function write(level, args) {
  const line = fmt(level, args);
  if (process.env.POLARIS_LOG_STDOUT !== '0' && process.env.NODE_ENV !== 'production') {
    process.stdout.write(line);
  }
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
