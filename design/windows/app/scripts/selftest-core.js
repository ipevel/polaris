'use strict';
/**
 * 核心层自检（纯 Node 跑，不需要 Electron，也不需要面板账号）。
 *
 *   node scripts/selftest-core.js
 *
 * 覆盖：
 *   1. 订阅清洗规则（去重名 / 剔伪节点 / 夺控制面 / 直连规则注入）
 *   2. 配置组装（端口、secret、DNS、TUN、兜底规则）
 *   3. 真实拉起 mihomo sidecar，控制面可用，节点可列出、可切换、可测速
 *
 * 第 3 步用一份本地合成的订阅（指向 127.0.0.1 的哑节点），验证的是
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

/* ---------------- 3. 地区识别 ---------------- */

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

/* ---------------- 4. 真实拉起 mihomo ---------------- */

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
  testRegion();
  await testKernel();
  console.log(`\n结果：${pass} 通过 / ${fail} 失败`);
  process.exit(fail ? 1 : 0);
})();
