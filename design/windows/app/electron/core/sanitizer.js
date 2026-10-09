'use strict';
/**
 * 订阅清洗（对应 Android 端 kernel/Sanitizer* 系列）。
 *
 * 这是安全边界，不只是"让内核能加载"：
 *   - 订阅可以覆盖 external-controller / secret / 端口 → 我们必须全量接管
 *   - geox-url / ntp / script / web / listeners 属于供应链投毒面 → 整块丢弃
 *   - 重名节点、缺 name 的节点 → 内核会拒绝加载**整份**配置，写盘前必须挡住
 *   - 面板把"剩余流量/到期时间"伪装成真节点 → 内核 url-test 会选中它们，连上却不通
 *
 * 与 Kotlin 版的差异：这里用 js-yaml 做**结构化**改写，而不是逐行正则手术。
 * 锚点、合并键、引号键、flow 写法都由解析器统一处理，语义更稳；
 * 规则集合与守卫条件与 Kotlin 版逐条对齐。
 */

const yaml = require('js-yaml');

const RESERVED_NAMES = new Set([
  'DIRECT', 'REJECT', 'REJECT-DROP', 'COMPATIBLE', 'PASS', 'PASS-RULE', 'GLOBAL',
]);

const DROPPED_TOP_LEVEL_KEYS = [
  'hosts', 'script', 'scripting', 'web', 'listeners', 'geox-url', 'ntp',
];

const NEUTRALIZED_KEYS = [
  'external-controller', 'external-controller-tls', 'external-controller-unix',
  'external-controller-pipe', 'external-ui', 'external-ui-name', 'external-ui-url',
  'secret',
];

const ZEROED_PORT_KEYS = ['port', 'socks-port', 'mixed-port', 'redir-port', 'tproxy-port'];
const ZEROED_SWITCH_KEYS = ['allow-lan', 'bind-address'];

const HEALTH_CHECK_GROUP_TYPES = new Set(['url-test', 'fallback', 'load-balance']);
const HEALTH_CHECK_URL = 'https://www.gstatic.com/generate_204';
const HEALTH_CHECK_INTERVAL = 300;

const FULL_WIDTH_COLON = '：';
const INFO_NAME = /剩余流量|已用流量|总流量|套餐流量|套餐到期|到期时间|过期时间|距离下次重置|重置剩余|流量重置|剩余天数|剩余时间|官网|官方网站|续费|购买|客服|邀请|订阅地址|订阅链接|机场|公告|群组|频道|Telegram|电报群/;

class SanitizeError extends Error {}

function isInfoLikeName(name) {
  return String(name || '').includes(FULL_WIDTH_COLON) || INFO_NAME.test(String(name || ''));
}

function parse(text) {
  const body = String(text || '').replace(/^\uFEFF/, '');
  if (!body.trim()) throw new SanitizeError('订阅内容为空');
  if (/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/.test(body)) {
    throw new SanitizeError('订阅含非法控制字符');
  }
  const head = body.trimStart();
  if (head.startsWith('<') || head.startsWith('{')) {
    throw new SanitizeError('返回的不是 Clash YAML（疑似 HTML/JSON）');
  }
  let doc;
  try {
    doc = yaml.load(body, { json: true });
  } catch (e) {
    throw new SanitizeError(`订阅 YAML 解析失败：${e.message}`);
  }
  if (!doc || typeof doc !== 'object' || Array.isArray(doc)) {
    throw new SanitizeError('订阅 YAML 顶层不是映射');
  }
  return doc;
}

/** 是否是一份"看起来像订阅"的 YAML */
function isValidSubscribeYaml(text) {
  try {
    const doc = parse(text);
    return Array.isArray(doc.proxies) || !!(doc['proxy-providers'] && typeof doc['proxy-providers'] === 'object');
  } catch (_) {
    return false;
  }
}

function endpointKey(p) {
  if (!p || typeof p !== 'object') return '|';
  const cred = p.uuid != null && p.uuid !== '' ? p.uuid : (p.password || '');
  return [p.type || '', p.server || '', p.port == null ? '' : p.port, cred].join('|');
}

function groupNames(groups) {
  const out = new Set();
  for (const g of groups || []) if (g && typeof g.name === 'string') out.add(g.name);
  return out;
}

/** 剔除信息伪节点；会清空某个分组成员时整步放弃 */
function dropInfoProxies(doc) {
  const proxies = Array.isArray(doc.proxies) ? doc.proxies : null;
  if (!proxies || proxies.length < 2) return 0;

  const byEndpoint = new Map();
  proxies.forEach((p) => {
    const k = endpointKey(p);
    if (!byEndpoint.has(k)) byEndpoint.set(k, []);
    byEndpoint.get(k).push(p);
  });

  const doomed = new Set();
  for (const same of byEndpoint.values()) {
    if (same.length < 2) continue;
    const info = same.filter((p) => isInfoLikeName(p.name));
    if (info.length === 0 || info.length === same.length) continue;
    for (const p of info) doomed.add(p);
  }
  if (doomed.size === 0) return 0;

  // 守卫：任一分组会被清空 → 放弃，宁可留伪节点也不产出内核拒绝加载的配置
  const groups = Array.isArray(doc['proxy-groups']) ? doc['proxy-groups'] : [];
  for (const g of groups) {
    const members = (g && Array.isArray(g.proxies)) ? g.proxies : [];
    if (members.length > 0 && members.every((m) => doomed.has(m))) return 0;
  }

  doc.proxies = proxies.filter((p) => !doomed.has(p));
  const doomedNames = new Set([...doomed].map((p) => p.name));
  for (const g of groups) {
    if (Array.isArray(g.proxies)) g.proxies = g.proxies.filter((m) => !doomedNames.has(m));
  }
  return doomed.size;
}

/** 重名节点改名（内核遇重名直接拒绝加载整份配置） */
function dedupeProxyNames(doc) {
  const proxies = Array.isArray(doc.proxies) ? doc.proxies : null;
  if (!proxies || proxies.length === 0) return 0;

  const taken = new Set(RESERVED_NAMES);
  for (const n of groupNames(doc['proxy-groups'])) taken.add(n);

  const seen = new Set();
  let renamed = 0;
  for (const p of proxies) {
    const name = p && typeof p.name === 'string' ? p.name : null;
    if (name == null) continue;
    if (seen.has(name)) {
      let i = 2;
      let candidate = `${name} (${i})`;
      while (taken.has(candidate)) { i += 1; candidate = `${name} (${i})`; }
      taken.add(candidate);
      p.name = candidate;
      seen.add(candidate);
      renamed += 1;
    } else {
      seen.add(name);
      taken.add(name);
    }
  }

  // 额外加固：订阅里出现内核内置名（DIRECT/GLOBAL…）时也改名，Kotlin 版未覆盖这一条
  for (const p of proxies) {
    if (p && typeof p.name === 'string' && RESERVED_NAMES.has(p.name)) {
      const base = p.name;
      let i = 2;
      let candidate = `${base} (${i})`;
      while (taken.has(candidate)) { i += 1; candidate = `${base} (${i})`; }
      taken.add(candidate);
      p.name = candidate;
      renamed += 1;
    }
  }
  return renamed;
}

function injectHealthCheck(doc) {
  const groups = Array.isArray(doc['proxy-groups']) ? doc['proxy-groups'] : [];
  for (const g of groups) {
    if (!g || !HEALTH_CHECK_GROUP_TYPES.has(g.type)) continue;
    g.url = g.url || HEALTH_CHECK_URL;
    g.interval = g.interval || HEALTH_CHECK_INTERVAL;
    g.tolerance = g.tolerance || 50;
    if (typeof g.lazy !== 'boolean') g.lazy = true;
  }
}

function injectDirectRules(doc, domains) {
  if (!Array.isArray(domains) || domains.length === 0) return 0;
  if (!Array.isArray(doc.rules)) doc.rules = [];
  const existing = new Set(doc.rules);
  let added = 0;
  for (const d of domains) {
    const rule = `DOMAIN-SUFFIX,${d},DIRECT`;
    if (existing.has(rule)) continue;
    doc.rules.unshift(rule);
    existing.add(rule);
    added += 1;
  }
  if (!doc.dns || typeof doc.dns !== 'object') doc.dns = {};
  if (!Array.isArray(doc.dns['fake-ip-filter'])) doc.dns['fake-ip-filter'] = ['*.lan', '*.local'];
  const filter = doc.dns['fake-ip-filter'];
  for (const d of domains) {
    if (!filter.includes(`+.${d}`)) filter.push(`+.${d}`);
  }
  return added;
}

/**
 * 清洗整份订阅。
 * @param {string} text 订阅原文
 * @param {{directDomains?: string[]}} opts
 */
function sanitize(text, opts = {}) {
  const doc = parse(text);
  const report = { droppedKeys: [], renamed: 0, droppedInfo: 0, directRules: 0 };

  // 1) 整块丢弃的顶层键
  for (const k of DROPPED_TOP_LEVEL_KEYS) {
    if (k in doc) { delete doc[k]; report.droppedKeys.push(k); }
  }

  // 2) 控制面接管：订阅不得影响我们的端口 / secret / 外部界面
  for (const k of NEUTRALIZED_KEYS) {
    if (k in doc) { delete doc[k]; report.droppedKeys.push(k); }
  }

  // 3) 端口与监听面全部由 App 决定
  for (const k of ZEROED_PORT_KEYS) doc[k] = 0;
  for (const k of ZEROED_SWITCH_KEYS) {
    doc[k] = k === 'allow-lan' ? false : '';
  }

  // 4) TUN 由 App 单独控制（mihomo 侧 tun 段也一并清掉后重建）
  delete doc.tun;

  if ('ui-subtitle-pattern' in doc) {
    doc['ui-subtitle-pattern'] = '';
    report.droppedKeys.push('ui-subtitle-pattern');
  }

  // 5) 重名改名 + 伪节点剔除（顺序与 Kotlin 版一致：先改名，避免伪节点判定被重名干扰）
  report.renamed = dedupeProxyNames(doc);
  report.droppedInfo = dropInfoProxies(doc);
  injectHealthCheck(doc);
  report.directRules = injectDirectRules(doc, opts.directDomains || []);

  if (!isKernelLoadable(doc)) {
    throw new SanitizeError('清洗后仍不可加载（重名或缺 name 的节点未被解决），已放弃写盘');
  }
  return { doc, report };
}

/** 内核能否加载：无节点时允许走 proxy-providers；否则不允许重名 / 缺名 */
function isKernelLoadable(doc) {
  const proxies = Array.isArray(doc.proxies) ? doc.proxies : [];
  const hasProviders = !!(doc['proxy-providers'] && Object.keys(doc['proxy-providers']).length > 0);
  if (proxies.length === 0) return hasProviders;
  const names = new Set();
  for (const p of proxies) {
    if (!p || typeof p.name !== 'string' || !p.name) return false;
    if (names.has(p.name)) return false;
    names.add(p.name);
  }
  return true;
}

module.exports = {
  parse, sanitize, isValidSubscribeYaml, isKernelLoadable,
  isInfoLikeName, endpointKey,
  SanitizeError,
  HEALTH_CHECK_URL,
};
