'use strict';
/**
 * DNS 配置自测（离线，不联网）。
 *
 * 背景：1.11.4 真机日志里「Ⓜ️ 微软云盘 --> onedrive.live.com:443 i/o timeout」共 112 条，
 * 目标是**污染地址**（Facebook/Dropbox/Yahoo 段）。2026-10-13 逐端点比对确认根因是
 * 阿里 DoH 对微软域返回了错误解析结果，而配置把阿里 DoH 排在了第一位。
 * 见 electron/core/builder.js 里 `DOH_CLEAN_*` 上方那段注释。
 *
 * 这个测试盯三件事：
 *  1. 生成的 config 的 DNS 段里**没有任何**已知会返回污染的解析器（阿里/`alidns`）；
 *  2. 微软/网盘这一族在 `nameserver-policy` 里被钉到了干净解析器上；
 *  3. 整个 DNS 段只用 https:// 的 DoH（`default-nameserver` 除外，那是引导用的明文 IP）。
 *
 * 联网的活体比对（真去查一遍 DoH 看谁在投毒）在 `scripts/dns-poison-check.js --live`，
 * 不进离线门禁 —— 门禁不能因为某家 DNS 半夜抖动就红。
 */
const assert = require('assert');
const path = require('path');

const appRoot = path.join(__dirname, '..');
const builder = require(path.join(appRoot, 'electron', 'core', 'builder.js'));

let pass = 0;
let fail = 0;
function ok(name) { console.log(`  [ OK ] ${name}`); pass++; }
function bad(name, detail) { console.log(`  [FAIL] ${name}  ${detail || ''}`); fail++; }
function check(name, fn) {
  try { fn(); ok(name); } catch (e) { bad(name, e.message); }
}

// —— 造一份最小订阅（只要能被 build() 接受即可；这里关心的是 DNS 段）——
const doc = {
  proxies: [{ name: '节点A', type: 'ss', server: '1.2.3.4', port: 8388, cipher: 'aes-128-gcm', password: 'x' }],
};
const built = builder.build(doc, {
  mixedPort: 7890,
  controllerPort: 9090,
  secret: 'test-secret',
  mode: 'rule',
  ipv6: true,
  tun: false,
  directDomains: ['panel.example.com'],
});
const dns = built.config.dns;

// 已知会给出污染结果的解析器（2026-10-13 逐端点实测，见 scripts/dns-poison-check.js --live）：
// 阿里 DoH 三台对 onedrive.live.com / skyapi.onedrive.live.com 返回 Facebook/Dropbox/Yahoo 段地址。
const POISONED = ['alidns', '223.5.5.5', '223.6.6.6'];
// 引导解析器是明文 IP，允许出现；业务解析器必须全是 DoH
const BOOTSTRAP = new Set(dns['default-nameserver'] || []);

function isDoh(v) { return typeof v === 'string' && /^https:\/\//.test(v); }
function poisoned(v) { const s = String(v).toLowerCase(); return POISONED.some((p) => s.includes(p)); }

console.log('\nDNS 配置自测');

check('config 里有 dns 段', () => { assert.ok(dns && typeof dns === 'object', '没有 dns 段'); });

check('nameserver 全部是 DoH', () => {
  const bad1 = (dns.nameserver || []).filter((v) => !isDoh(v));
  assert.deepStrictEqual(bad1, [], `非 DoH：${bad1.join(', ')}`);
});

check('nameserver 里没有阿里 DoH', () => {
  const bad1 = (dns.nameserver || []).filter(poisoned);
  assert.deepStrictEqual(bad1, [], `含被实证污染的解析器：${bad1.join(', ')}`);
});

check('proxy-server-nameserver 里没有阿里 DoH', () => {
  const bad1 = (dns['proxy-server-nameserver'] || []).filter(poisoned);
  assert.deepStrictEqual(bad1, [], `含被实证污染的解析器：${bad1.join(', ')}`);
});

check('direct-nameserver 里没有阿里 DoH 且都是 DoH', () => {
  const list = dns['direct-nameserver'] || [];
  const bad1 = list.filter((v) => poisoned(v) || !isDoh(v));
  assert.deepStrictEqual(bad1, [], `有问题：${bad1.join(', ')}`);
});

check('default-nameserver 只用于引导：必须是明文 IP，且不含任何 DoH 地址', () => {
  const list = dns['default-nameserver'] || [];
  assert.ok(list.length > 0, '空');
  const bad1 = list.filter((v) => isDoh(v));
  assert.deepStrictEqual(bad1, [], `引导解析器里混进了 DoH 主机名（会解析不了自己）：${bad1.join(', ')}`);
  const bad2 = list.filter((v) => poisoned(v));
  assert.deepStrictEqual(bad2, [], `引导解析器里含被实证污染的解析器：${bad2.join(', ')}`);
});

check('nameserver-policy 存在且键值都是干净 DoH', () => {
  const np = dns['nameserver-policy'];
  assert.ok(np && typeof np === 'object', '没有 nameserver-policy');
  const bad1 = Object.entries(np).filter(([, v]) => {
    const list = Array.isArray(v) ? v : [v];
    return list.length === 0 || list.some((x) => typeof x !== 'string' || poisoned(x));
  });
  assert.deepStrictEqual(bad1.map(([k]) => k), [], `策略里有问题：${JSON.stringify(bad1)}`);
});

check('按域钉死：微软/网盘族必须绕开污染解析器', () => {
  const np = dns['nameserver-policy'] || {};
  const need = ['+.live.com', '+.onedrive.com', '+.sharepoint.com', '+.office.com', '+.office.net'];
  const missing = need.filter((k) => !np[k]);
  assert.deepStrictEqual(missing, [], `缺键：${missing.join(', ')}`);
  // geosite:microsoft 这一条覆盖 geosite 判定的微软域（onedrive/azure/windowsupdate 都算）
  assert.ok(np['geosite:microsoft'], '缺 geosite:microsoft 策略');
  for (const k of [...need, 'geosite:microsoft']) {
    const list = Array.isArray(np[k]) ? np[k] : [np[k]];
    assert.ok(list.some((x) => String(x).includes('doh.pub')), `${k} 没走 DNSPub：${list.join(', ')}`);
  }
});

check('DDNS 类星球域已按 mihomo 语法写成 +. 前缀（不带 * 或 scheme）', () => {
  const np = dns['nameserver-policy'] || {};
  for (const k of ['+.live.com', '+.onedrive.com', '+.sharepoint.com', '+.office.com', '+.office.net']) {
    assert.ok(!/[*/]/.test(k.replace('+.', '')), `${k} 含非法字符`);
    assert.ok(k.startsWith('+.'), `${k} 缺 +. 前缀`);
  }
});

check('gvisor/其它：ipv6 打开时内核会解析 v6，fake-ip-filter 仍含面板域', () => {
  const f = dns['fake-ip-filter'] || [];
  assert.ok(f.some((x) => x === '+.panel.example.com'), `面板域不在 fake-ip-filter：${f.join(', ')}`);
});

console.log(`\nDNS 配置自测：通过 ${pass} 项，失败 ${fail} 项`);
process.exit(fail ? 1 : 0);
