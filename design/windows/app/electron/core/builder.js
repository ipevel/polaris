'use strict';
/**
 * 组装最终 mihomo 配置：清洗后的订阅 + App 独占的运行参数。
 *
 * 原则：订阅只提供"节点"和"规则意图"，端口、控制面、DNS、TUN、日志
 * 全部由本文件决定，订阅无法覆盖（sanitizer 已先剥掉同名键）。
 */

const crypto = require('crypto');
const rulesets = require('./rulesets');

const DIRECT_GROUP = '节点选择';

function randomPort() {
  // 20000-60000，避开常见冲突
  return 20000 + Math.floor(Math.random() * 40000);
}

function randomSecret() {
  return crypto.randomBytes(16).toString('hex');
}

/** 内置分流组的成员：首位即默认出口，其余为可选项，最后并入全部节点供单独指定 */
function groupMembers(defaultOut, nodeNames) {
  const head = defaultOut === 'block'
    ? ['REJECT', DIRECT_GROUP, 'DIRECT']
    : defaultOut === 'direct'
      ? ['DIRECT', DIRECT_GROUP, 'REJECT']
      : [DIRECT_GROUP, 'DIRECT', 'REJECT'];
  const seen = new Set(head);
  for (const n of nodeNames) {
    if (typeof n === 'string' && n && !seen.has(n)) { seen.add(n); head.push(n); }
  }
  return head;
}

/**
 * @param {object} doc   sanitizer.sanitize 的产物
 * @param {object} opts  { mixedPort, controllerPort, secret, mode, tun, allowLan, ipv6,
 *                         tunStack, directDomains,
 *                         routing: { enabled, order, custom: [{name,out,enabled,rules}] } }
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

  // —— 策略组：确保有一个主选择组 ——
  const groups = Array.isArray(out['proxy-groups']) ? out['proxy-groups'].slice() : [];
  const names = new Set(groups.map((g) => g && g.name).filter(Boolean));
  const nodeNames = (Array.isArray(out.proxies) ? out.proxies : []).map((p) => p.name);
  if (!names.has(DIRECT_GROUP)) {
    if (nodeNames.length > 0) {
      groups.unshift({
        name: DIRECT_GROUP,
        type: 'select',
        proxies: [...nodeNames],
      });
    } else {
      // 没有内联节点时退化为只给 DIRECT，至少配置能加载
      groups.unshift({ name: DIRECT_GROUP, type: 'select', proxies: ['DIRECT'] });
    }
    names.add(DIRECT_GROUP);
  }

  // —— 内置分流（离线 rule-provider）：本地规则优先于订阅规则 ——
  // 每组一个策略组，首位成员表达默认出站（对齐 Karing 预设的 outbound 语义），
  // 用户可在分流页给单个分类指定具体节点。订阅里已有同名组时直接复用，不重复建组
  // —— mihomo 对重名策略组是硬失败（整个配置加载失败）。
  const routing = opts.routing || null;
  const setRules = [];
  const ruleProviders = Object.assign({}, out['rule-providers'] || {});
  const mine = new Set();
  if (routing) {
    // 用户自定义分流组排在**最前面**：自己写的规则要能覆盖内置分类。
    // 内置分类里有 gs_geolocation_ncn 这种"整个非中国"的大网，排后面就永远轮不到自定义规则。
    for (const g of rulesets.normalizeCustom(routing.custom)) {
      if (!g.enabled) continue;
      if (!names.has(g.name)) {
        groups.push({ name: g.name, type: 'select', proxies: groupMembers(g.out, nodeNames) });
        names.add(g.name);
      }
      for (const r of g.rules) setRules.push(rulesets.ruleLine(r, g.name));
    }
    const enabled = new Set(rulesets.normalizeEnabled(routing.enabled));
    for (const g of rulesets.orderedTable(routing.order)) {
      if (!enabled.has(g.name)) continue;
      if (!names.has(g.name)) {
        groups.push({ name: g.name, type: 'select', proxies: groupMembers(g.defaultOut, nodeNames) });
        names.add(g.name);
      }
      for (const key of rulesets.providerKeys(g)) {
        const p = rulesets.PROVIDERS[key];
        if (!p) continue;
        if (!ruleProviders[key]) {
          ruleProviders[key] = rulesets.providerConfig(key);
          mine.add(key);
        } else if (!mine.has(key)) {
          // 订阅自带同名规则集：不覆盖（内容不可控），但规则照样指向本组
          warnings.push(`规则集 ${key} 与订阅同名，沿用订阅的定义`);
        }
        setRules.push(`RULE-SET,${key},${g.name}${p.noResolve ? ',no-resolve' : ''}`);
      }
      for (const r of g.inlineRules || []) setRules.push(r.replace(/\{t\}/g, g.name));
    }
  }
  out['proxy-groups'] = groups;
  if (Object.keys(ruleProviders).length) out['rule-providers'] = ruleProviders;

  // —— 规则：面板域名直连置顶，内置分流次之，末尾兜底到主选择组 ——
  if (!Array.isArray(out.rules)) out.rules = [];
  const leading = directDomains.map((d) => `DOMAIN-SUFFIX,${d},DIRECT`);
  const tail = `MATCH,${DIRECT_GROUP}`;
  const rules = out.rules.filter((r) => typeof r === 'string');
  out.rules = [
    ...leading,
    ...rulesets.LAN_DIRECT_RULES,   // 内网/私有地址永远直连，不能被订阅规则拐进代理
    ...setRules,
    ...rules.filter((r) => !r.startsWith('MATCH,')),
    tail,
  ];

  return { config: out, mixedPort, controllerPort, secret, warnings };
}

module.exports = { build, DIRECT_GROUP, randomPort, randomSecret, groupMembers };
