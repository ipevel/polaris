'use strict';
/**
 * DoH 污染活体探测。**默认不跑**（CI 里不跑），只在需要复核时手动执行：
 *
 *     node scripts/dns-poison-check.js --live            # 只报告
 *     node scripts/dns-poison-check.js --live --strict    # 家目录配置里若含污染解析器则 exit 1
 *
 * 它做两件事：
 *  1. 对一组微软/OneDrive 域，逐个 DoH 端点问一遍，标出返回**污染段**地址的端点；
 *  2. 从 electron/core/builder.js 现算一份 config，看配置里用的解析器里有没有被实测污染的那个。
 *
 * 为什么要有这个脚本：2026-10-13 修的那条 bug（Ⓜ️ 微软云盘 112 次 i/o timeout）根因是
 * 阿里 DoH 对 onedrive.live.com / skyapi.onedrive.live.com 返回了 Facebook/Dropbox/Yahoo 段的
 * 地址（128.242.240.155 / 108.160.169.171 / 173.252.108.21），而配置把阿里 DoH 排在第一位。
 * 离线门禁 scripts/dns-config-test.js 只能防"有人又把阿里加回去"，防不了"换了一家新解析器
 * 结果它也在投毒"，所以留这个活体复核入口。
 *
 * 需要能直连这些 DoH（本机 HTTPS_PROXY 会干扰，脚本内刻意不走代理）。
 */
const path = require('path');

const LIVE = process.argv.includes('--live');
const STRICT = process.argv.includes('--strict');

// 活体探测出来的、会返回污染地址的端点标签（--strict 的判定依据）
const poisonedEndpoints = new Set();

if (!LIVE) {
  console.log('dns-poison-check: 未加 --live，跳过（活体探测不进 CI，见文件头注释）');
  process.exit(0);
}

const appRoot = path.join(__dirname, '..');

// —— 被 GFW 传统投毒使用的地址段（按 1.11.4 真机日志里出现的段归纳）——
const POISON_RANGES = [
  [/^157\.240\./, 'facebook/16'],
  [/^31\.13\./, 'facebook/16'],
  [/^66\.220\./, 'facebook/16'],
  [/^173\.252\./, 'facebook/16'],
  [/^179\.60\./, 'facebook/16'],
  [/^162\.125\./, 'dropbox/16'],
  [/^108\.160\./, 'bitgravity'],
  [/^128\.242\./, 'yahoo/verizon'],
  [/^8\.7\./, 'gfw-poison'],
  [/^46\.82\./, 'gfw-poison'],
  [/^78\.16\./, 'gfw-poison'],
  [/^59\.24\./, 'gfw-poison'],
  [/^243\.185\./, 'gfw-poison'],
  [/^203\.98\./, 'gfw-poison'],
];

const ENDPOINTS = [
  ['alidns 223.5.5.5', 'https://223.5.5.5/dns-query'],
  ['alidns 223.6.6.6', 'https://223.6.6.6/dns-query'],
  ['alidns dns.alidns.com', 'https://dns.alidns.com/dns-query'],
  ['dnspub 1.12.12.12', 'https://1.12.12.12/dns-query'],
  ['dnspub 120.53.53.53', 'https://120.53.53.53/dns-query'],
  ['dnspub doh.pub', 'https://doh.pub/dns-query'],
  ['cloudflare 1.1.1.1', 'https://1.1.1.1/dns-query'],
  ['google 8.8.8.8', 'https://8.8.8.8/dns-query'],
];

// 这些域必须解析到微软段（13.107/150.171/40.99/20.190/52.97…），任何 POISON_RANGES 命中都算污染
const MS_DOMAINS = [
  'onedrive.live.com',
  'skyapi.onedrive.live.com',
  'api.onedrive.com',
  'storage.live.com',
  'login.live.com',
  'outlook.live.com',
  'graph.microsoft.com',
  'sharepoint.com',
];

function classify(ip) {
  for (const [re, tag] of POISON_RANGES) if (re.test(ip)) return tag;
  return '';
}

async function ask(endpoint, name) {
  // `/dns-query` 端点用 GET+application/dns-json（Google/Cloudflare/阿里/腾讯都认这个扩展）
  const url = `${endpoint}?name=${encodeURIComponent(name)}&type=A`;
  const ac = new AbortController();
  const t = setTimeout(() => ac.abort(), 8000);
  try {
    const r = await fetch(url, { headers: { accept: 'application/dns-json' }, signal: ac.signal });
    if (!r.ok) return { err: `HTTP ${r.status}` };
    const j = await r.json();
    return { ips: (j.Answer || []).filter((a) => a.type === 1).map((a) => a.data) };
  } catch (e) {
    return { err: e.name === 'AbortError' ? 'timeout' : 'fetch failed' };
  } finally {
    clearTimeout(t);
  }
}

(async () => {
  const results = [];

  for (const [label, endpoint] of ENDPOINTS) {
    let hadPoison = false;
    const lines = [];
    for (const n of MS_DOMAINS) {
      const r = await ask(endpoint, n);
      let verdict = '';
      if (r.err) verdict = `ERR ${r.err}`;
      else if (!r.ips.length) verdict = '(no A)';
      else {
        const tags = [...new Set(r.ips.map(classify).filter(Boolean))];
        if (tags.length) { verdict = `POISON[${tags.join(',')}]`; hadPoison = true; }
        else verdict = 'ok';
      }
      lines.push(`    ${verdict.padEnd(22)} ${n.padEnd(30)} ${(r.ips || []).slice(0, 3).join(' ')}`);
      results.push({ label, name: n, verdict, ips: r.ips || [] });
    }
    if (hadPoison) pollutedEndpoints.add(label);
    console.log(`\n=== ${label} ===`);
    for (const l of lines) console.log(l);
  }

  console.log('\n=== 结论 ===');
  const proven = [...new Set(results.map((r) => r.label))].filter((l) => !results.some((r) => r.label === l && r.verdict.startsWith('ERR')));
  const unreachable = [...new Set(results.filter((r) => r.verdict.startsWith('ERR')).map((r) => r.label))];
  if (!poisonedEndpoints.size) {
    console.log(`  实测可达的端点（${proven.length} 个）都没有返回污染地址；不可达的端点（${unreachable.length} 个）无法判定。`);
  } else {
    console.log(`  实测返回污染地址的端点：${[...poisonedEndpoints].join(' / ')}`);
  }
  if (unreachable.length) console.log(`  不可达、无法判定：${unreachable.join(' / ')}`);
  // 具体地址也打出来，方便和真机日志里的 IP 对号
  const badIps = [...new Set(results.filter((r) => r.verdict.startsWith('POISON')).flatMap((r) => r.ips.map(classify).filter(Boolean)))];
  if (badIps.length) console.log(`  命中的污染段：${badIps.join(', ')}`);

  if (!STRICT) {
    console.log('\n（未加 --strict：只报告，不判定）');
    process.exit(0);
  }

  // strict：配置里用到的解析器不能出现在"实测污染"名单里
  const builder = require(path.join(appRoot, 'electron', 'core', 'builder.js'));
  const built = builder.build({ proxies: [] }, { mixedPort: 7890, controllerPort: 9090, secret: 'x', directDomains: [] });
  const dns = built.config.dns || {};
  const used = new Set();
  for (const key of ['nameserver', 'proxy-server-nameserver', 'direct-nameserver']) {
    for (const v of dns[key] || []) used.add(String(v));
  }
  for (const v of Object.values(dns['nameserver-policy'] || {})) {
    for (const x of Array.isArray(v) ? v : [v]) used.add(String(x));
  }
  // 把配置里的 URL 映射回端点标签：按主机名匹配
  const hostOf = (u) => { try { return new URL(u).host; } catch { return ''; } };
  const badUsed = [];
  for (const [label] of poisonedEndpoints) {
    const host = hostOf(ENDPOINTS.find(([l]) => l === label)[1]);
    for (const u of used) if (hostOf(u) === host) badUsed.push(`${label} (${u})`);
  }
  if (badUsed.length) {
    console.log(`\n[FAIL] 生成的 DNS 配置里用到了实测会返回污染地址的解析器：\n  ${badUsed.join('\n  ')}`);
    process.exit(1);
  }
  console.log('\n[ OK ] 生成的 DNS 配置没有用到任何实测返回污染地址的解析器。');
  process.exit(0);
})();
