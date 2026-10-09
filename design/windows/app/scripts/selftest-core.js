'use strict';
/**
 * 核心层自检（纯 Node 跑，不需要 Electron，也不需要面板账号）。
 *
 *   node scripts/selftest-core.js
 *
 * 覆盖：
 *   1. 订阅清洗规则（去重名 / 剔伪节点 / 夺控制面 / 直连规则注入）
 *   2. 配置组装（端口、secret、DNS、TUN、兜底规则）
 *   3. 内置分流规则（27 组 × 48 个离线规则集，策略组语义、订阅冲突、开关）
 *   4. 直连域名与更新地址（订阅域名必须真被记住；Windows 不得用安卓 APK 地址）
 *   5. 真实拉起 mihomo sidecar，控制面可用，节点可列出、可切换、可测速
 *
 * 第 5 步用一份本地合成的订阅（指向 127.0.0.1 的哑节点），验证的是
 * "内核能被我们生成的配置喂起来并被控制"，不是"能翻墙"。
 */

const fs = require('fs');
const os = require('os');
const path = require('path');
const yaml = require('js-yaml');
const { spawn } = require('child_process');

const sanitizer = require('../electron/core/sanitizer');
const builder = require('../electron/core/builder');
const { Controller, sleep } = require('../electron/core/controller');
const region = require('../electron/core/region');

let pass = 0;
let fail = 0;

function check(name, cond, detail) {
  if (cond) { pass += 1; console.log(`  PASS  ${name}`); }
  else { fail += 1; console.log(`  FAIL  ${name}${detail ? ` :: ${detail}` : ''}`); }
}

function section(t) { console.log(`\n== ${t}`); }

/* ---------------- 1. 清洗 ---------------- */

const DIRTY_SUB = `
# 面板下发的脏配置
mixed-port: 7899
allow-lan: true
external-controller: 0.0.0.0:9999
secret: attacker-secret
geox-url:
  geoip: https://evil.example/geoip.dat
ntp:
  server: evil.example
script:
  - test.js
dns:
  enable: true
proxies:
  - name: "🇭🇰 香港 01"
    type: ss
    server: 1.2.3.4
    port: 8388
    cipher: aes-128-gcm
    password: pw1
  - name: "🇭🇰 香港 01"
    type: ss
    server: 1.2.3.4
    port: 8388
    cipher: aes-128-gcm
    password: pw1
  - name: "剩余流量：86.5 GB"
    type: ss
    server: 1.2.3.4
    port: 8388
    cipher: aes-128-gcm
    password: pw1
  - name: "🇯🇵 日本 01"
    type: trojan
    server: 5.6.7.8
    port: 443
    password: pw2
  - name: "DIRECT"
    type: ss
    server: 9.9.9.9
    port: 1
    cipher: none
    password: x
proxy-groups:
  - name: 节点选择
    type: select
    proxies:
      - "🇭🇰 香港 01"
      - "剩余流量：86.5 GB"
      - "🇯🇵 日本 01"
rules:
  - GEOIP,CN,DIRECT
  - DOMAIN-SUFFIX,cn,DIRECT
  - MATCH,节点选择
`;

function testSanitizer() {
  section('订阅清洗');
  const { doc, report } = sanitizer.sanitize(DIRTY_SUB, { directDomains: ['panel.example.com'] });

  check('顶层端口被清零', doc['mixed-port'] === 0, String(doc['mixed-port']));
  check('allow-lan 被关', doc['allow-lan'] === false, String(doc['allow-lan']));
  check('external-controller 被夺走', doc['external-controller'] === undefined);
  check('secret 被夺走', doc.secret === undefined);
  check('geox-url 整块丢弃', doc['geox-url'] === undefined);
  check('ntp 整块丢弃', doc.ntp === undefined);
  check('script 整块丢弃', doc.script === undefined);
  check('DNS 保留（交由 builder 重建）', doc.dns === undefined || typeof doc.dns === 'object');

  const names = doc.proxies.map((p) => p.name);
  check('重名已改唯一', new Set(names).size === names.length, names.join(' | '));
  check('改名条数 > 0', report.renamed >= 1, String(report.renamed));
  check('伪节点已剔除', !names.some((n) => n.includes('剩余流量')), names.join(' | '));
  check('伪节点剔除计数 > 0', report.droppedInfo >= 1, String(report.droppedInfo));
  check('内核内置名已避让', !names.includes('DIRECT'), names.join(' | '));
  check('分组引用同步清理', !doc['proxy-groups'][0].proxies.some((p) => String(p).includes('剩余流量')));
  check('直连规则已注入', doc.rules.some((r) => r === 'DOMAIN-SUFFIX,panel.example.com,DIRECT'));
  check('fake-ip-filter 已注入', (doc.dns['fake-ip-filter'] || []).includes('+.panel.example.com'));
  check('清洗后可被内核加载', sanitizer.isKernelLoadable(doc));

  // 非法输入必须被挡住，而不是产出一份坏配置
  const rejects = [
    ['HTML 伪装', '<html>404</html>'],
    ['空内容', '   '],
    ['控制字符', 'proxies:\n  - name: \u0007bad\n'],
  ];
  for (const [label, text] of rejects) {
    let threw = false;
    try { sanitizer.sanitize(text, {}); } catch (_) { threw = true; }
    check(`拒绝非法订阅：${label}`, threw);
  }

  // 缺 name 的条目 -> 不允许写盘
  let gate = false;
  try { sanitizer.sanitize('proxies:\n  - type: ss\n    server: a\n    port: 1\n', {}); }
  catch (_) { gate = true; }
  check('缺 name 的节点被闸门拦下', gate);
}

/* ---------------- 2. 配置组装 ---------------- */

function testBuilder() {
  section('配置组装');
  const { doc } = sanitizer.sanitize(DIRTY_SUB, { directDomains: ['panel.example.com'] });
  const r = builder.build(doc, {
    mode: 'rule', tun: false, allowLan: false, ipv6: false,
    directDomains: ['panel.example.com'],
  });
  const c = r.config;

  check('mixed-port 有效', c['mixed-port'] > 0 && c['mixed-port'] < 65536, String(c['mixed-port']));
  check('控制面只绑 127.0.0.1', String(c['external-controller']).startsWith('127.0.0.1:'));
  check('secret 已随机生成', typeof c.secret === 'string' && c.secret.length >= 16);
  check('端口与 secret 落盘一致', c.secret === r.secret && String(c['mixed-port']) === String(r.mixedPort));
  check('DNS fake-ip 开启', c.dns.enable === true && c.dns['enhanced-mode'] === 'fake-ip');
  check('面板域名进 fake-ip-filter', c.dns['fake-ip-filter'].includes('+.panel.example.com'));
  check('geox-url 为空对象（不可被订阅注入）', c['geox-url'] && Object.keys(c['geox-url']).length === 0);
  check('主选择组存在', c['proxy-groups'].some((g) => g.name === builder.DIRECT_GROUP));
  check('兜底 MATCH 规则在最后', c.rules[c.rules.length - 1] === `MATCH,${builder.DIRECT_GROUP}`);
  check('面板域名直连在规则最前', c.rules[0] === 'DOMAIN-SUFFIX,panel.example.com,DIRECT');
  check('订阅自带 MATCH 被替换', c.rules.filter((r2) => r2.startsWith('MATCH,')).length === 1);
  check('订阅原有规则保留', c.rules.some((r2) => r2 === 'GEOIP,CN,DIRECT'));
  check('profile 持久化选中', c.profile['store-selected'] === true);

  const r2 = builder.build(doc, { tun: true, tunStack: 'gvisor' });
  check('TUN 打开后 mixed-port 归零避免环路', r2.config['mixed-port'] === 0);
  check('TUN 段开启', r2.config.tun.enable === true && r2.config.tun.stack === 'gvisor');

  const y = yaml.dump(c);
  const round = yaml.load(y);
  check('YAML 序列化/反序列化无损', round['external-controller'] === c['external-controller'] && round.proxies.length === c.proxies.length);
}

/* ---------------- 4. 内置分流规则 ---------------- */

function testRulesets() {
  section('内置分流规则');
  const paths = require('../electron/paths');
  const rulesets = require('../electron/core/rulesets');

  const keys = rulesets.allProviderKeys();
  check('规则集共 48 个', keys.length === 48, String(keys.length));
  check('规则集键不重复', new Set(keys).size === 48, String(new Set(keys).size));
  const missing = keys.filter((k) => {
    const f = path.join(paths.rules(), rulesets.seedFile(k));
    return !fs.existsSync(f) || fs.statSync(f).size === 0;
  });
  check('每个键都有对应种子文件', missing.length === 0, missing.join(','));
  const orphan = fs.readdirSync(paths.rules()).filter((f) => f.endsWith('.yaml') && !keys.includes(f.replace(/\.yaml$/, '')));
  check('种子目录没有多余文件', orphan.length === 0, orphan.join(','));
  check('种子路径是 data 内相对路径（mihomo IsSafePath 要求）',
    keys.every((k) => rulesets.seedPath(k) === `rules/${k}.yaml` && !path.isAbsolute(rulesets.seedPath(k))));

  check('分流表 27 组', rulesets.TABLE.length === 27, String(rulesets.TABLE.length));
  check('默认启用 6 组', rulesets.defaultEnabled().length === 6, rulesets.defaultEnabled().join(','));
  check('normalizeEnabled 去重并丢弃未知组',
    JSON.stringify(rulesets.normalizeEnabled(['🛑 广告拦截', '不存在', '🛑 广告拦截'])) === JSON.stringify(['🛑 广告拦截']),
    JSON.stringify(rulesets.normalizeEnabled(['🛑 广告拦截', '不存在', '🛑 广告拦截'])));

  const ordered = rulesets.orderedTable(['🌏 国外穿墙', '🛑 广告拦截']);
  check('orderedTable 按用户顺序置顶且不丢组',
    ordered.length === 27 && ordered[0].name === '🌏 国外穿墙' && ordered[1].name === '🛑 广告拦截' &&
    new Set(ordered.map((g) => g.name)).size === 27, ordered.slice(0, 3).map((g) => g.name).join(','));
  check('orderedTable 忽略未知组名', rulesets.orderedTable(['不存在']).length === 27);

  // 默认配置（6 组）→ 16 个规则集
  const doc = {
    proxies: [{ name: '香港 01' }, { name: '日本 01' }],
    'proxy-groups': [{ name: '流媒体分流', type: 'select', proxies: ['香港 01'] }],
    rules: ['GEOIP,CN,DIRECT', 'MATCH,节点选择'],
  };
  const r = builder.build(doc, { directDomains: ['panel.example.com'], routing: { enabled: rulesets.defaultEnabled() } });
  const c = r.config;
  const pv = c['rule-providers'] || {};
  check('默认 6 组生成 16 个 rule-provider', Object.keys(pv).length === 16, String(Object.keys(pv).length));
  check('rule-provider 是 file 型且指向 data 内相对路径',
    Object.keys(pv).every((k) => pv[k].type === 'file' && pv[k].path === rulesets.seedPath(k)));
  check('订阅自带的策略组保留', c['proxy-groups'].some((g) => g.name === '流媒体分流'));
  check('启用组各自建了同名策略组',
    ['🍎 苹果服务', '🌏 Google Play', '🌏 Google', '📺 哔哩哔哩', '🎯 国内直连', '🌏 国外穿墙']
      .every((n) => c['proxy-groups'].some((g) => g.name === n)));
  check('未启用组不建组也不加规则',
    !c['proxy-groups'].some((g) => g.name === '🐱 GitHub') && !(c.rules || []).some((x) => x.includes('gs_github')));
  check('策略组名不重复', new Set(c['proxy-groups'].map((g) => g.name)).size === c['proxy-groups'].length);

  const setRules = c.rules.filter((x) => x.startsWith('RULE-SET,'));
  check('16 条 RULE-SET', setRules.length === 16, String(setRules.length));
  check('ipcidr 规则集带 no-resolve',
    setRules.filter((x) => x.includes(',no-resolve')).length === 3 &&
    setRules.some((x) => x === 'RULE-SET,gp_cn,🎯 国内直连,no-resolve') &&
    setRules.some((x) => x === 'RULE-SET,acl_chinacompanyip,🎯 国内直连,no-resolve'),
    setRules.filter((x) => x.includes('no-resolve')).join(' | '));
  check('规则集规则排在内网直连之后、订阅规则之前',
    c.rules.indexOf('RULE-SET,gs_apple,🍎 苹果服务') > c.rules.findIndex((x) => x.startsWith('IP-CIDR,192.168.0.0/16')) &&
    c.rules.indexOf('RULE-SET,gs_apple,🍎 苹果服务') < c.rules.indexOf('GEOIP,CN,DIRECT'));
  check('MATCH 兜底仍在最后', c.rules[c.rules.length - 1] === 'MATCH,节点选择');
  check('默认配置无告警', r.warnings.length === 0, r.warnings.join(' | '));

  const g = (n) => c['proxy-groups'].find((x) => x.name === n);
  check('proxy 语义组默认出口是主选择组', g('🌏 Google').proxies[0] === '节点选择');
  check('direct 语义组默认出口是 DIRECT', g('🍎 苹果服务').proxies[0] === 'DIRECT');
  check('block 语义组默认出口是 REJECT', g('🛑 广告拦截') === undefined);
  check('组内并入全部节点供单独指定',
    g('🌏 Google').proxies.includes('香港 01') && g('🌏 Google').proxies.includes('日本 01'),
    g('🌏 Google').proxies.join(','));

  // 打开拦截组 + 苹果推送（纯内联组）
  const r2 = builder.build(doc, {
    routing: { enabled: rulesets.normalizeEnabled([...rulesets.defaultEnabled(), '🛑 广告拦截', '📢 苹果推送通知']) },
  });
  const c2 = r2.config;
  check('开启广告拦截后规则集变 18 个', Object.keys(c2['rule-providers'] || {}).length === 18,
    String(Object.keys(c2['rule-providers'] || {}).length));
  const block = c2['proxy-groups'].find((x) => x.name === '🛑 广告拦截');
  check('拦截组默认出口是 REJECT', block && block.proxies[0] === 'REJECT', block && block.proxies.slice(0, 3).join(','));
  check('拦截规则指向内置组',
    c2.rules.includes('RULE-SET,gs_category_ads_all,🛑 广告拦截') && c2.rules.includes('RULE-SET,acl_banad,🛑 广告拦截'));
  const inline = c2.rules.filter((x) => x.endsWith(',📢 苹果推送通知,no-resolve') || x.endsWith(',📢 苹果推送通知'));
  check('纯内联组生成 12 条内联规则（{t} 已替换）', inline.length === 12, String(inline.length));
  check('内联组不产生规则集文件',
    rulesets.providerKeys(rulesets.TABLE.find((x) => x.name === '📢 苹果推送通知')).length === 0 &&
    !Object.keys(c2['rule-providers'] || {}).some((k) => k === 'apple_push'),
    Object.keys(c2['rule-providers'] || {}).join(','));

  // 订阅自带同名规则集 / 同名策略组
  const r3 = builder.build({
    proxies: [{ name: '香港 01' }],
    'proxy-groups': [{ name: '🐱 GitHub', type: 'select', proxies: ['香港 01'] }],
    'rule-providers': { gs_github: { type: 'http', behavior: 'domain', format: 'yaml', url: 'https://example.com/gh.yaml' } },
    rules: ['MATCH,节点选择'],
  }, { routing: { enabled: ['🐱 GitHub'] } });
  const c3 = r3.config;
  check('订阅同名规则集不被覆盖', c3['rule-providers'].gs_github.type === 'http', c3['rule-providers'].gs_github.type);
  check('同名冲突有告警', r3.warnings.some((w) => w.includes('gs_github')), r3.warnings.join(' | '));
  check('订阅同名策略组被复用不重建',
    c3['proxy-groups'].filter((x) => x.name === '🐱 GitHub').length === 1 &&
    c3['proxy-groups'].find((x) => x.name === '🐱 GitHub').proxies.length === 1);
  check('规则照样指向该组', c3.rules.includes('RULE-SET,gs_github,🐱 GitHub'));

  // 不传 routing 时完全保持旧行为（老配置/老断言不受影响）
  const r4 = builder.build(doc, {});
  check('不传 routing 时不生成规则集', !r4.config['rule-providers'], JSON.stringify(r4.config['rule-providers']));
  check('不传 routing 时无内置组', !r4.config['proxy-groups'].some((x) => x.name === '🎯 国内直连'));
  check('内网直连规则始终注入（含未启用内置分流时）',
    r4.config.rules.some((x) => x.startsWith('IP-CIDR,192.168.0.0/16')));
}

/* ---------------- 5. 直连域名 / 更新地址 ---------------- */

async function testDirectAndUpdate() {
  section('直连域名与更新地址');
  const http = require('http');
  const store = require('../electron/store');
  const remote = require('../electron/core/remote');

  const savedHosts = store.get('subscribe_hosts');
  const savedPanel = store.get('panel_url');
  const savedDownload = store.get('download_url');
  const savedSources = store.get('remote_config_urls');

  // 本地起一个远程配置源，验证真实拉取路径而不是凭空造 cache
  const cfg = {
    config_version: 7,
    direct_domains: ['direct.example.com'],
    update_version: '9.9.9',
    update_size: '12 MB',
    update_changelog: '安卓更新日志',
    update_url: 'https://dl.example.com/Polaris-9.9.9.apk',
    update_apk_url: 'https://dl.example.com/Polaris-9.9.9.apk',
    update_windows_url: 'https://dl.example.com/Polaris-9.9.9-win.zip',
    update_windows_changelog: 'Windows 更新日志',
  };
  let hits = 0;
  const server = http.createServer((req, res) => {
    hits += 1;
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end(JSON.stringify(cfg));
  });
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const port = server.address().port;

  try {
    store.set('subscribe_hosts', []);
    store.set('panel_url', 'https://panel.example.com');
    store.set('download_url', '');
    store.set('remote_config_urls', `http://127.0.0.1:${port}/config.json`);
    await remote.load(true);

    check('远程配置已拉到', hits > 0, `hits=${hits}`);
    const dd = remote.directDomains();
    check('远程配置的直连域名生效', dd.includes('direct.example.com'), JSON.stringify(dd));
    check('面板域名进直连列表', dd.includes('panel.example.com'), JSON.stringify(dd));

    // 订阅域名必须真的被记住 —— 旧代码取了 host 就丢，等于没做
    const added = remote.rememberDirectHost('Sub.CDN-Example.NET');
    check('订阅域名被记入直连列表', added === true && remote.directDomains().includes('sub.cdn-example.net'),
      JSON.stringify(remote.directDomains()));
    check('重复记住同一域名返回 false', remote.rememberDirectHost('sub.cdn-example.net') === false);
    check('订阅域名持久化到 settings', Array.isArray(store.get('subscribe_hosts'))
      && store.get('subscribe_hosts').includes('sub.cdn-example.net'));
    check('直连域名去重', remote.directDomains().length === new Set(remote.directDomains()).size);

    // 面板直连 + 订阅直连都要出现在生成的规则最前面
    const built = builder.build({ proxies: [], rules: ['MATCH,DIRECT'] }, {
      directDomains: remote.directDomains(),
    });
    check('订阅域名生成 DOMAIN-SUFFIX,DIRECT 规则',
      built.config.rules.includes('DOMAIN-SUFFIX,sub.cdn-example.net,DIRECT'),
      JSON.stringify(built.config.rules.slice(0, 4)));
    check('面板/订阅域名进 fake-ip-filter',
      built.config.dns['fake-ip-filter'].includes('+.sub.cdn-example.net'),
      JSON.stringify(built.config.dns['fake-ip-filter'].slice(-3)));

    // 更新地址：Windows 端绝不能把安卓 APK 地址当成自己的更新包
    const u = remote.updateInfo();
    check('优先取 Windows 专用更新地址', u.url === 'https://dl.example.com/Polaris-9.9.9-win.zip', JSON.stringify(u));
    check('安卓地址单独暴露，不混进 url', u.android_url === 'https://dl.example.com/Polaris-9.9.9.apk', u.android_url);
    check('Windows 专用更新日志优先', u.notes === 'Windows 更新日志', u.notes);
    check('版本不同即提示有更新', u.has_update === true && u.version === '9.9.9', JSON.stringify(u));

    // 面板只给了安卓地址时：宁可用本机配置的下载页，也不给安卓包
    delete cfg.update_windows_url;
    delete cfg.update_windows_changelog;
    await remote.load(true);
    check('无 Windows 地址时不回落安卓 APK', remote.updateInfo().url === '',
      JSON.stringify(remote.updateInfo()));
    store.set('download_url', 'https://polaris.example.com/download');
    check('本机配置的下载页优先于安卓地址',
      remote.updateInfo().url === 'https://polaris.example.com/download',
      remote.updateInfo().url);
  } finally {
    try { server.close(); } catch (_) {}
    store.set('subscribe_hosts', savedHosts);
    store.set('panel_url', savedPanel);
    store.set('download_url', savedDownload);
    store.set('remote_config_urls', savedSources);
  }
}

/* ---------------- 6. 地区识别 ---------------- */

function testRegion() {
  section('地区识别');
  const cases = [
    ['🇭🇰 香港 01', '香港'],
    ['香港 IEPL 专线', '香港'],
    ['Japan Premium', '日本'],
    ['新加坡-狮城', '新加坡'],
    ['美国 洛杉矶 02', '美国'],
    ['US Los Angeles 02', '美国'],
    ['Russia', '俄罗斯'],
    ['Indonesia', '印度尼西亚'],
    ['India', '印度'],
    ['Tokyo JP-01', '日本'],
    ['Premium IP', '其他'],
    ['未知节点', '其他'],
  ];
  for (const [name, want] of cases) {
    const r = region.detect(name);
    const got = r ? r.label : '其他';
    check(`识别「${name}」→ ${want}`, got === want, `got ${got}`);
  }
}

/* ---------------- 7. 真实拉起 mihomo ---------------- */

const FAKE_SUB = `
proxies:
  - name: "🇭🇰 哑节点 01"
    type: ss
    server: 127.0.0.1
    port: 18388
    cipher: aes-128-gcm
    password: fake
  - name: "🇯🇵 哑节点 02"
    type: ss
    server: 127.0.0.1
    port: 18389
    cipher: aes-128-gcm
    password: fake
proxy-groups:
  - name: 节点选择
    type: select
    proxies: ["🇭🇰 哑节点 01", "🇯🇵 哑节点 02"]
rules:
  - MATCH,节点选择
`;

async function testKernel() {
  section('真实拉起 mihomo');
  const appDir = path.resolve(__dirname, '..');
  const exe = path.join(appDir, 'core', 'mihomo.exe');
  if (!fs.existsSync(exe)) {
    check('mihomo.exe 存在', false, exe);
    return;
  }
  check('mihomo.exe 存在', true);

  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'polaris-core-'));
  const { doc } = sanitizer.sanitize(FAKE_SUB, { directDomains: ['panel.example.com'] });
  const r = builder.build(doc, { mode: 'rule', directDomains: ['panel.example.com'] });
  const cfgPath = path.join(dir, 'config.yaml');
  fs.writeFileSync(cfgPath, yaml.dump(r.config, { lineWidth: -1 }), 'utf8');

  const proc = spawn(exe, ['-d', dir, '-f', cfgPath], { cwd: dir, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  let out = '';
  proc.stdout.on('data', (b) => { out += b.toString(); });
  proc.stderr.on('data', (b) => { out += b.toString(); });

  const ctl = new Controller(r.controllerPort, r.secret);
  try {
    const v = await ctl.waitReady(25000);
    check('控制面就绪', !!v.version, JSON.stringify(v));

    const rawProxies = await ctl.get('/proxies');
    const proxies = (rawProxies && rawProxies.proxies) ? rawProxies.proxies : rawProxies;
    const group = proxies[builder.DIRECT_GROUP];
    check('主选择组可读', !!group, Object.keys(proxies || {}).join(','));
    check('分组含两个节点', group && group.all.length === 2, group ? group.all.join(',') : '');

    const first = group.all[0];
    const put = await ctl.put(`/proxies/${encodeURIComponent(builder.DIRECT_GROUP)}`, { name: group.all[1] });
    check('切换节点 PUT 无错误', put === null || put === true || put === undefined, String(put));
    const after = await ctl.get(`/proxies/${encodeURIComponent(builder.DIRECT_GROUP)}`);
    check('切换被内核回读确认', after.now === group.all[1], `${after.now}`);

    // 哑节点测速应失败（超时），但不应把整个命令打挂
    let delayThrew = false;
    try {
      await ctl.get(`/proxies/${encodeURIComponent(first)}/delay?timeout=2000&url=${encodeURIComponent('https://www.gstatic.com/generate_204')}`, undefined, 6000);
    } catch (_) { delayThrew = true; }
    check('哑节点测速失败被正常捕获', delayThrew === true);

    const cfg = await ctl.get('/configs');
    check('模式读取为 rule', cfg.mode === 'rule', cfg.mode);

    // 流量流：连上后即使无流量也应保持打开
    const ws = await ctl.connectWS('/traffic');
    check('/traffic WebSocket 可连', ws && ws.readyState === 1);
    ws.close();
  } catch (e) {
    check('内核自检', false, e.message);
    console.log('--- mihomo output ---\n' + out.slice(-2500));
  } finally {
    try { proc.kill(); } catch (_) {}
    await sleep(300);
    try { fs.rmSync(dir, { recursive: true, force: true }); } catch (_) {}
  }
}

(async () => {
  console.log('Polaris 核心层自检');
  console.log('node', process.version);
  testSanitizer();
  testBuilder();
  testRulesets();
  await testDirectAndUpdate();
  testRegion();
  await testKernel();
  console.log(`\n结果：${pass} 通过 / ${fail} 失败`);
  process.exit(fail ? 1 : 0);
})();
