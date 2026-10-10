'use strict';
/**
 * 组装最终 mihomo 配置：清洗后的订阅 + App 独占的运行参数。
 *
 * 原则：订阅只提供"节点"和"规则意图"，端口、控制面、DNS、TUN、日志
 * 全部由本文件决定，订阅无法覆盖（sanitizer 已先剥掉同名键）。
 *
 * 分流方案与安卓端 kernel-core 的 config/routing 同构（routing_build.go）：
 * **屏蔽面板下发的 proxy-groups / rules / rule-providers / sub-rules，
 * 只用本地内置方案**；面板节点（proxies）与 proxy-providers 原样保留，
 * 靠 include-all 成为本地各组的成员（订阅更新后不用重拼配置）。
 */

const crypto = require('crypto');
const rulesets = require('./rulesets');

/**
 * 策略组名与安卓端 kernel-core 的 config/routing 常量保持一致
 * （E:\AI\Github\slte\kernel-core\src\main\golang\native\config\routing\routing_table.go:23-26）：
 *   GroupNameSelector = "🚀 节点选择"  主选择组 —— 节点页那张「节点选择」卡就是它
 *   GroupNameAuto     = "自动选择"     url-test 测速组（主组首位成员 = 默认出口）
 *   GroupNameFallback = "故障转移"     fallback 组
 *   GroupNameFinal    = "🐟 漏网之鱼"  兜底组 —— 成员只有 [主组, DIRECT]，MATCH 落到这里
 */
const SELECTOR_GROUP = rulesets.GROUP_SELECTOR;
const AUTO_GROUP = rulesets.GROUP_AUTO;
const FALLBACK_GROUP = rulesets.GROUP_FALLBACK;
const FINAL_GROUP = rulesets.GROUP_FINAL;
// manager.js 的 MAIN_GROUP 直接取这个值，语义仍是「主选择组」
const DIRECT_GROUP = SELECTOR_GROUP;

function randomPort() {
  // 20000-60000，避开常见冲突
  return 20000 + Math.floor(Math.random() * 40000);
}

function randomSecret() {
  return crypto.randomBytes(16).toString('hex');
}

/**
 * 内置分流组的成员：首位即默认出口（select 组默认选首位），对齐 Karing 预设语义。
 * 节点不在这里枚举 —— include-all 会把全部节点并进每一组（安卓端同款），
 * 用户才能给单个分类指定具体节点，订阅加节点也不用重拼配置。
 */
function groupMembers(defaultOut) {
  if (defaultOut === 'block') return ['REJECT', SELECTOR_GROUP, 'DIRECT'];
  if (defaultOut === 'direct') return ['DIRECT', SELECTOR_GROUP, AUTO_GROUP, FALLBACK_GROUP, 'REJECT'];
  return [SELECTOR_GROUP, AUTO_GROUP, FALLBACK_GROUP, 'DIRECT', 'REJECT'];
}

/**
 * 本地分流方案：整体替换面板下发的分组与规则（安卓端 routing.Build 的等价实现）。
 *
 * 降级（返回 {degraded}）：面板节点/Provider 名与本地保留名冲突时**整体回退**到面板
 * 配置 —— mihomo 对重名是硬失败（整个 profile 加载不了），而这个冲突完全由订阅内容
 * 决定（安卓端 reserved.go:51 checkNameCollisions 同款）。
 */
function buildLocalRouting(out, routing, directDomains) {
  const reserved = new Set(rulesets.reservedNames());
  const proxies = Array.isArray(out.proxies) ? out.proxies : [];
  for (const p of proxies) {
    if (p && typeof p.name === 'string' && reserved.has(p.name)) {
      return { degraded: `订阅里的节点「${p.name}」与本地分流的保留名冲突` };
    }
  }
  const providerNames = Object.keys(out['proxy-providers'] || {});
  for (const k of providerNames) {
    if (reserved.has(k)) return { degraded: `订阅里的节点提供者「${k}」与本地分流的保留名冲突` };
  }
  if (proxies.length === 0 && providerNames.length === 0) {
    return { degraded: '订阅里没有任何节点' };
  }

  // 顺序即契约：主选择组必须排第一（安卓端 KernelProxyGroup.selectorGroup() 取首个 Selector 组）
  const groups = [
    { name: SELECTOR_GROUP, type: 'select', proxies: [AUTO_GROUP, FALLBACK_GROUP, 'DIRECT'], 'include-all': true },
    {
      name: AUTO_GROUP,
      type: 'url-test',
      url: rulesets.TEST_URL,
      interval: 300,
      tolerance: 50,
      // 只认 204：留空时 mihomo 默认 "*"，中间盒的拦截页（200）也会被当成"通"
      'expected-status': '204',
      'include-all': true,
    },
    {
      name: FALLBACK_GROUP,
      type: 'fallback',
      url: rulesets.TEST_URL,
      interval: 300,
      'expected-status': '204',
      'include-all': true,
    },
  ];
  const ruleProviders = {};
  const rules = [];

  // 自家后端域名直连最前置（否则面板域名会被下面的分类规则拐进代理）
  for (const d of directDomains) rules.push(`DOMAIN-SUFFIX,${d},DIRECT`);
  rules.push(...rulesets.LAN_DIRECT_RULES);

  // 用户自定义分流组排在**最前面**：自己写的规则要能覆盖内置分类。
  // 内置分类里有 gs_geolocation_ncn 这种"整个非中国"的大网，排后面就永远轮不到自定义规则。
  for (const g of rulesets.normalizeCustom(routing.custom)) {
    if (!g.enabled) continue;
    groups.push({ name: g.name, type: 'select', proxies: groupMembers(g.out), 'include-all': true });
    for (const r of g.rules) rules.push(rulesets.ruleLine(r, g.name));
  }

  const enabled = new Set(rulesets.normalizeEnabled(routing.enabled));
  for (const g of rulesets.orderedTable(routing.order)) {
    if (!enabled.has(g.name)) continue;
    groups.push({ name: g.name, type: 'select', proxies: groupMembers(g.defaultOut), 'include-all': true });
    for (const key of rulesets.providerKeys(g)) {
      const p = rulesets.PROVIDERS[key];
      if (!p) continue;
      ruleProviders[key] = rulesets.providerConfig(key);
      rules.push(`RULE-SET,${key},${g.name}${p.noResolve ? ',no-resolve' : ''}`);
    }
    for (const r of g.inlineRules || []) rules.push(r.replace(/\{t\}/g, g.name));
  }

  groups.push({ name: FINAL_GROUP, type: 'select', proxies: [SELECTOR_GROUP, 'DIRECT'] });
  rules.push(`MATCH,${FINAL_GROUP}`);

  out['proxy-groups'] = groups;
  out['rule-providers'] = ruleProviders;
  out.rules = rules;
  delete out['sub-rules'];   // 面板 sub-rules 只被面板 rules 引用，规则已整体替换
  return { groups: groups.length, ruleSets: Object.keys(ruleProviders).length };
}

/**
 * 面板模式（本地分流关闭，或本地方案降级）：面板配置原样生效。
 * 只补两件产品必需的事：主选择组/兜底组一定存在，自家域名与内网地址一定直连。
 */
function ensurePanelConfig(out, directDomains) {
  const groups = Array.isArray(out['proxy-groups']) ? out['proxy-groups'].slice() : [];
  const names = new Set(groups.map((g) => g && g.name).filter(Boolean));
  const nodeNames = (Array.isArray(out.proxies) ? out.proxies : []).map((p) => p.name);
  // 主选择组：面板自带就复用（mihomo 对重名组是硬失败，整个配置加载不了），没有才自建
  if (!names.has(SELECTOR_GROUP)) {
    if (nodeNames.length > 0) {
      groups.unshift({ name: SELECTOR_GROUP, type: 'select', 'include-all': true });
    } else {
      // 没有内联节点时退化为只给 DIRECT，至少配置能加载
      groups.unshift({ name: SELECTOR_GROUP, type: 'select', proxies: ['DIRECT'] });
    }
    names.add(SELECTOR_GROUP);
  }
  // 兜底组：MATCH 的落点。成员首位是主组，所以兜底流量天然跟着「节点选择」走
  if (!names.has(FINAL_GROUP)) {
    groups.push({ name: FINAL_GROUP, type: 'select', proxies: [SELECTOR_GROUP, 'DIRECT'] });
    names.add(FINAL_GROUP);
  }
  out['proxy-groups'] = groups;

  if (!Array.isArray(out.rules)) out.rules = [];
  const leading = directDomains.map((d) => `DOMAIN-SUFFIX,${d},DIRECT`);
  out.rules = [
    ...leading,
    ...rulesets.LAN_DIRECT_RULES,   // 内网/私有地址永远直连，不能被订阅规则拐进代理
    ...out.rules.filter((r) => typeof r === 'string' && !r.startsWith('MATCH,')),
    `MATCH,${FINAL_GROUP}`,
  ];
}

/**
 * @param {object} doc   sanitizer.sanitize 的产物
 * @param {object} opts  { mixedPort, controllerPort, secret, mode, tun, allowLan, ipv6,
 *                         tunStack, directDomains,
 *                         routing: { on, order, enabled: [组名], custom: [{name,out,enabled,rules}] } }
 */
function build(doc, opts = {}) {
  const mixedPort = opts.mixedPort || randomPort();
  const controllerPort = opts.controllerPort || randomPort();
  const secret = opts.secret || randomSecret();
  const directDomains = opts.directDomains || [];
  const warnings = [];

  const out = Object.assign({}, doc);

  // —— 运行参数（App 独占）——
  out['mixed-port'] = mixedPort;
  out['allow-lan'] = !!opts.allowLan;
  out['bind-address'] = opts.allowLan ? '*' : '';
  out.mode = opts.mode || 'rule';
  out['log-level'] = opts.logLevel || 'info';
  out.ipv6 = !!opts.ipv6;
  out['external-controller'] = `127.0.0.1:${controllerPort}`;
  out.secret = secret;
  out['unified-delay'] = true;
  out['tcp-concurrent'] = true;
  out['find-process-mode'] = 'off';
  out['keep-alive-interval'] = 30;
  // store-selected：本地分流下每条分流组的出口选择必须跨重连存活（安卓端同款）
  out.profile = { 'store-selected': true, 'store-fake-ip': true };
  // 用 Meta 格式规则库（geoip.metadb / geosite.dat），随包分发，不联网下载
  out['geodata-loader'] = 'standard';
  out['geo-auto-update'] = false;
  out['geo-update-interval'] = 24;
  out['geox-url'] = {};              // 清空，防订阅残留

  // —— DNS：fake-ip + 本地解析，面板域名直连 ——
  out.dns = {
    enable: true,
    listen: '127.0.0.1:0',
    'enhanced-mode': 'fake-ip',
    'fake-ip-range': '198.18.0.1/16',
    'fake-ip-filter': ['*.lan', '*.local', 'localhost.ptlogin2.qq.com', ...directDomains.map((d) => `+.${d}`)],
    'default-nameserver': ['223.5.5.5', '119.29.29.29', '1.1.1.1'],
    nameserver: ['https://223.5.5.5/dns-query', 'https://doh.pub/dns-query', 'https://1.1.1.1/dns-query'],
    'proxy-server-nameserver': ['https://223.5.5.5/dns-query', 'https://doh.pub/dns-query'],
    'direct-nameserver': ['https://223.5.5.5/dns-query', 'https://doh.pub/dns-query'],
  };

  // —— TUN（可选，需管理员）——
  if (opts.tun) {
    out.tun = {
      enable: true,
      stack: opts.tunStack || 'gvisor',
      device: 'Polaris',
      'auto-route': true,
      'auto-detect-interface': true,
      'strict-route': false,
      'dns-hijack': ['any:53'],
      mtu: 1500,
    };
    // TUN 接管后系统代理会二次劫持，显式关掉端口入站避免环路
    out['mixed-port'] = 0;
    out['tun']['auto-redirect'] = false;
  }

  // —— 分流方案：本地内置方案（默认）或面板方案 ——
  const routing = opts.routing || null;
  let local = null;
  let degraded = '';
  if (routing && routing.on) {
    const r = buildLocalRouting(out, routing, directDomains);
    if (r && r.degraded) {
      degraded = r.degraded;
      warnings.push(`${r.degraded}，已回退到面板自带的分流方案`);
    } else {
      local = r;
    }
  }
  if (!local) ensurePanelConfig(out, directDomains);

  return {
    config: out,
    mixedPort,
    controllerPort,
    secret,
    warnings,
    local: !!local,
    degraded,
    groups: local ? local.groups : (Array.isArray(out['proxy-groups']) ? out['proxy-groups'].length : 0),
    ruleSets: local ? local.ruleSets : Object.keys(out['rule-providers'] || {}).length,
  };
}

module.exports = {
  build, DIRECT_GROUP, SELECTOR_GROUP, AUTO_GROUP, FALLBACK_GROUP, FINAL_GROUP,
  randomPort, randomSecret, groupMembers,
};
