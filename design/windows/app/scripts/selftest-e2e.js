'use strict';
/**
 * 端到端自检：假面板 → 登录 → 拉订阅 → 清洗 → 起内核 → 真实代理 → 系统代理还原。
 *
 *   npx electron scripts/selftest-e2e.js            # 不动系统代理
 *   npx electron scripts/selftest-e2e.js --sysproxy # 连系统代理一起验（会写 HKCU 并还原）
 *
 * 这一步才是"真机验证"：内核是真的，HTTP 请求是真的，系统代理注册表是真的。
 * 唯一造假的是面板（本地 mock）和出口节点（指向本机既有代理，避免依赖外部订阅）。
 */

const { app } = require('electron');
const fs = require('fs');
const http = require('http');
const os = require('os');
const path = require('path');
const yaml = require('js-yaml');

process.env.POLARIS_ALLOW_PLAINTEXT_CREDENTIALS = '1';

const mockPanel = require('./mock-panel');

// 打包后跑 --doctor 时，app 目录是 asar，__dirname 指向 asar 内的 scripts/
const ASAR_ROOT = path.resolve(__dirname, '..');

let pass = 0;
let fail = 0;
const failures = [];

function check(name, cond, detail) {
  if (cond) { pass += 1; console.log(`  PASS  ${name}`); }
  else {
    fail += 1;
    failures.push(name + (detail ? ` :: ${detail}` : ''));
    console.log(`  FAIL  ${name}${detail ? ` :: ${detail}` : ''}`);
  }
}
function section(t) { console.log(`\n== ${t}`); }

const WITH_SYSPROXY = process.argv.includes('--sysproxy');

function httpThroughProxy(proxyPort, targetUrl, timeoutMs = 15000) {
  return new Promise((resolve, reject) => {
    const u = new URL(targetUrl);
    const req = http.request({
      host: '127.0.0.1',
      port: proxyPort,
      method: 'GET',
      path: targetUrl,
      headers: { Host: u.host, 'Proxy-Connection': 'keep-alive' },
      timeout: timeoutMs,
    }, (res) => {
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => resolve({ status: res.statusCode, bytes: Buffer.concat(chunks).length }));
    });
    req.on('timeout', () => req.destroy(new Error('经代理请求超时')));
    req.on('error', reject);
    req.end();
  });
}

async function expectReject(name, fn) {
  try { await fn(); check(name, false, '本应报错但成功了'); }
  catch (e) { check(name, true); }
}

app.whenReady().then(async () => {
  const commands = require('../electron/ipc').commands;
  const paths = require('../electron/paths');
  const store = require('../electron/store');
  const core = require('../electron/core/manager');
  const sysproxy = require('../electron/net/sysproxy');

  paths.ensureAll();
  console.log('Polaris 端到端自检');
  console.log('app  =', ASAR_ROOT);
  console.log('data =', paths.data());
  console.log('core =', paths.core());
  console.log('geo  =', paths.geo(), fs.existsSync(paths.geo()) ? '(存在)' : '(缺失)');

  const { server, port } = await mockPanel.start(0);
  const panelUrl = `http://127.0.0.1:${port}`;
  console.log('mock panel =', panelUrl);

  let proxySnapshotBefore = null;
  let connected = false;

  try {
    /* ---------- 登录 ---------- */
    section('登录');
    const bad = await commands.login({ email: 'a@b.c', password: 'wrong-password', panel: panelUrl });
    check('错误密码被拒', bad && bad.ok === false, JSON.stringify(bad));

    const good = await commands.login({ email: 'tester@example.com', password: 'correct', panel: panelUrl });
    check('正确凭据登录成功', good && good.ok === true, JSON.stringify(good));
    check('邮箱回填正确', good && good.email === 'tester@example.com', good && good.email);

    const settings0 = await commands.get_settings();
    check('登录态持久化', settings0.authed === true);
    check('账号做了脱敏展示', /^\w\*+@/.test(settings0.email || ''), settings0.email);
    check('凭据文件已落盘', fs.existsSync(paths.file('credentials.dat')));

    const site = await commands.get_site_info();
    check('站点信息可读', site.appName === 'Mock Polar Panel', site.appName);

    /* ---------- 拉订阅 + 清洗 ---------- */
    section('订阅拉取与清洗');
    const refresh = await commands.refresh_subscription();
    check('订阅刷新成功', refresh && refresh.ok === true, JSON.stringify(refresh));
    check('清洗后剩 4 个节点（去掉重名与信息伪节点）', refresh.count === 4, String(refresh.count));

    const rawCfg = fs.readFileSync(paths.file('config.yaml'), 'utf8');
    const cfg = yaml.load(rawCfg);
    check('订阅的控制面被夺走', String(cfg['external-controller']).startsWith('127.0.0.1:'), cfg['external-controller']);
    check('订阅的 secret 被替换', cfg.secret !== 'attacker', cfg.secret);
    check('订阅的端口被清零', cfg['mixed-port'] > 0 && cfg['mixed-port'] !== 7899, String(cfg['mixed-port']));
    check('geox-url 被清空', cfg['geox-url'] && Object.keys(cfg['geox-url']).length === 0);
    check('重名节点已改名', cfg.proxies.some((p) => /\(2\)$/.test(p.name)), cfg.proxies.map((p) => p.name).join('|'));
    check('信息伪节点已剔除', !cfg.proxies.some((p) => /剩余流量/.test(p.name)));
    check('规则末尾是兜底 MATCH', cfg.rules[cfg.rules.length - 1].startsWith('MATCH,'));
    check('原始订阅没被就地改写', rawCfg.includes('attacker') === false);

    /* ---------- 面板业务 ---------- */
    section('面板业务数据');
    const plan = await commands.get_plan();
    check('套餐读取', plan.name === '旗舰套餐' && plan.total === 200, JSON.stringify(plan));
    check('到期时间格式化', /^\d{4}-\d{2}-\d{2}$/.test(plan.expire), plan.expire);

    const plans = await commands.get_plans();
    check('套餐列表 2 条', plans.length === 2, String(plans.length));
    check('HTML 描述被清洗成纯文本', plans[0].feats.every((f) => !/[<>]/.test(f)), JSON.stringify(plans[0].feats));

    const orders = await commands.get_orders();
    check('订单列表 2 条', orders.length === 2, String(orders.length));
    check('订单状态映射正确', orders[0].status === 'done' && orders[1].status === 'pending', orders.map((o) => o.status).join(','));

    const tickets = await commands.get_tickets();
    check('工单列表 2 条', tickets.length === 2, String(tickets.length));
    check('工单状态映射正确', tickets[0].status === 'replied' && tickets[1].status === 'closed', tickets.map((t) => t.status).join(','));

    const notices = await commands.get_notices();
    check('公告列表 2 条', notices.length === 2, String(notices.length));
    check('未读标记正确', notices[0].unread === true && notices[1].unread === false);

    const invite = await commands.get_invite();
    check('邀请信息读取', invite.code === 'MOCK1234' && invite.invited === 3, JSON.stringify(invite));

    const giftOk = await commands.redeem_gift({ code: 'MOCKGIFTCARD1234' });
    check('礼品卡兑换成功', giftOk.ok === true, JSON.stringify(giftOk));
    const giftBad = await commands.redeem_gift({ code: 'WRONGCODE00000000' });
    check('无效卡密被拒', giftBad.ok === false, JSON.stringify(giftBad));

    const tlog = await commands.get_traffic_log();
    check('流量明细可读', Array.isArray(tlog) && tlog.length === 2, String(tlog.length));

    const emoty = await commands.send_email_code({ email: 'tester@example.com', purpose: 'forget' });
    check('发送验证码', emoty.ok === true);

    /* ---------- 内核与真实流量 ---------- */
    section('内核与真实流量');
    if (WITH_SYSPROXY) {
      store.set('sys_proxy', true);
      proxySnapshotBefore = sysproxy.snapshot();
    } else {
      store.set('sys_proxy', false);
    }

    const st = await commands.connect();
    connected = true;
    check('连接成功', st && st.ok === true && st.connected === true, JSON.stringify(st));

    const status = await commands.get_status();
    check('内核进程存活', status.core_alive === true);
    check('运行模式为规则模式', status.mode === '规则模式', status.mode);

    const nodes = await commands.get_nodes();
    // 订阅里 5 个条目：香港01、香港01(重复)、日本01、剩余流量(伪)、新加坡01
    // 清洗后 proxies 剩 4 个（重复的改名为「香港 01 (2)」，伪节点被剔除）；
    // 但主分组只引用了原始那 3 个真节点 —— 改名后的副本没有任何分组引用它，
    // 所以可见节点是 3 个。这与 Android 端行为一致（改名而不动分组引用）。
    check('可见节点 3 个', nodes.length === 3, String(nodes.length));
    check('地区识别生效', nodes.some((n) => n.region === '香港') && nodes.some((n) => n.region === '日本'),
      nodes.map((n) => n.region).join(','));
    check('默认选中主分组第一个节点', !!status.node, status.node);

    const groups = await commands.get_routing_groups();
    const main = groups.find((g) => g.name === '节点选择');
    check('读到主分组', !!main, groups.map((g) => g.name).join(','));
    check('主分组有 3 个候选', main && main.count === 3, main && String(main.count));

    const other = groups.find((g) => g.name === '流媒体分流');
    check('读到订阅里的其它分组', !!other, groups.map((g) => g.name).join(','));

    if (other) {
      const swapped = await commands.set_routing_group({ name: '流媒体分流', node: other.options[other.options.length - 1] });
      check('切换分组出口成功', swapped.ok === true, JSON.stringify(swapped));
    }
    await expectReject('切换不存在的分组会报错', () => commands.set_routing_group({ name: '不存在组', node: 'x' }));
    await expectReject('选择不存在的节点会报错', () => commands.select_node({ name: '不存在节点' }));

    const pick = nodes.find((n) => n.region === '日本') || nodes[1];
    const sel = await commands.select_node({ name: pick.name });
    check('切换节点成功', sel.ok === true && sel.node === pick.name, JSON.stringify(sel));

    await commands.speed_test();
    const after = await commands.get_nodes();
    const measured = after.filter((n) => n.latency > 0);
    check('测速有真实结果（走通了出口代理）', measured.length > 0,
      after.map((n) => `${n.name}:${n.latency}`).join(' '));

    // 真·数据面：直接经 mihomo 的 mixed 端口发一个 HTTP 请求
    const mixed = core.mixedPort();
    check('mixed 端口有效', mixed > 0 && mixed < 65536, String(mixed));
    try {
      const r = await httpThroughProxy(mixed, 'http://www.gstatic.com/generate_204');
      check('经代理访问外网返回 204', r.status === 204, `status=${r.status}`);
    } catch (e) {
      check('经代理访问外网返回 204', false, e.message);
    }

    const modes = ['全局模式', '直连模式', '规则模式'];
    for (const m of modes) {
      const r = await commands.set_proxy_mode({ mode: m });
      check(`切换到${m}`, r.ok === true && r.mode === m, JSON.stringify(r));
    }

    /* ---------- 内置分流规则（离线规则集） ---------- */
    section('内置分流规则');
    const rulesets = require('../electron/core/rulesets');
    const seedKeys = rulesets.allProviderKeys();
    const seedMiss = seedKeys.filter((k) => !fs.existsSync(path.join(paths.ruleSeeds(), rulesets.seedFile(k))));
    check('规则集种子已铺到 data/rules', seedKeys.length === 48 && seedMiss.length === 0,
      `keys=${seedKeys.length} missing=${seedMiss.join(',')}`);

    const readCfg = () => yaml.load(fs.readFileSync(paths.file('config.yaml'), 'utf8'));
    const cfg0 = readCfg();
    const providers0 = Object.keys(cfg0['rule-providers'] || {});
    check('默认启用 6 组 → 16 个 rule-provider', providers0.length === 16, String(providers0.length));
    check('rule-provider 一律 file + data 内相对路径', providers0.every((k) => {
      const p = cfg0['rule-providers'][k];
      return p && p.type === 'file' && p.path === rulesets.seedPath(k) && !path.isAbsolute(p.path);
    }), JSON.stringify(cfg0['rule-providers'][providers0[0]] || {}));
    check('生成 16 条 RULE-SET 且 MATCH 兜底在最后',
      (cfg0.rules || []).filter((r) => String(r).startsWith('RULE-SET,')).length === 16 &&
      String((cfg0.rules || []).slice(-1)[0]).startsWith('MATCH,'),
      String((cfg0.rules || []).slice(-1)[0]));
    const lanIdx = (cfg0.rules || []).findIndex((r) => String(r).startsWith('IP-CIDR,192.168.0.0/16'));
    const setIdx = (cfg0.rules || []).findIndex((r) => String(r).startsWith('RULE-SET,'));
    check('内网直连规则排在内置分流之前', lanIdx >= 0 && setIdx > lanIdx, `lan=${lanIdx} set=${setIdx}`);

    // 内核侧的 provider 初始化是异步的：必须轮询，否则会读到还没解析完的 0 条
    const providerCounts = async () => {
      const pr = await core.S.controller.get('/providers/rules');
      return pr && pr.providers ? pr.providers : pr;
    };
    let prov = null;
    for (let i = 0; i < 30; i++) {
      await new Promise((r) => setTimeout(r, 300));
      prov = await providerCounts();
      if (providers0.every((k) => prov[k] && prov[k].ruleCount > 0)) break;
    }
    const zero = providers0.filter((k) => !prov[k] || prov[k].ruleCount <= 0);
    check('内核把 16 个规则集全部解析出规则', zero.length === 0,
      zero.map((k) => `${k}:${prov[k] ? prov[k].ruleCount : 'missing'}`).join(' '));
    const ruleTotal = providers0.reduce((a, k) => a + ((prov[k] && prov[k].ruleCount) || 0), 0);
    check('规则总数 > 1000 条', ruleTotal > 1000, String(ruleTotal));

    const rs0 = await commands.get_rulesets();
    check('分流表 27 组 / 默认启用 6 组',
      rs0 && rs0.total === 27 && Array.isArray(rs0.enabled) && rs0.enabled.length === 6,
      JSON.stringify({ total: rs0 && rs0.total, enabled: rs0 && rs0.enabled && rs0.enabled.length }));
    check('每组带出口语义与规则集数量',
      Array.isArray(rs0.groups) && rs0.groups.length === 27 &&
      rs0.groups.every((g) => ['direct', 'block', 'proxy'].includes(g.out) && g.count >= 0),
      JSON.stringify(rs0.groups && rs0.groups[0]));

    // 开关 + 热重载：配置与运行中的内核都要跟着变
    const on = await commands.set_ruleset({ name: '🛑 广告拦截', on: true });
    check('开启「广告拦截」并热重载', on && on.ok === true && on.applied === true, JSON.stringify(on));
    const cfg1 = readCfg();
    check('热重载后配置多出 2 个广告规则集',
      Object.keys(cfg1['rule-providers'] || {}).length === 18,
      String(Object.keys(cfg1['rule-providers'] || {}).length));
    const adLine = String((cfg1.rules || []).find((r) => String(r).startsWith('RULE-SET,gs_category_ads_all,')) || '');
    check('广告规则集出口指向内置组', adLine === 'RULE-SET,gs_category_ads_all,🛑 广告拦截', adLine);
    let prov1 = null;
    for (let i = 0; i < 30; i++) {
      await new Promise((r) => setTimeout(r, 300));
      prov1 = await providerCounts();
      if (prov1.gs_category_ads_all && prov1.gs_category_ads_all.ruleCount > 0) break;
    }
    check('热重载后内核真的加载了新增规则集',
      !!prov1.gs_category_ads_all && prov1.gs_category_ads_all.ruleCount > 0,
      JSON.stringify(prov1.gs_category_ads_all || null));

    await expectReject('开启不存在的分流组会报错', () => commands.set_ruleset({ name: '不存在的分流组', on: true }));
    const back = await commands.reset_rulesets();
    check('恢复默认分流', back && back.ok === true && back.applied === true, JSON.stringify(back));
    const cfg2 = readCfg();
    check('恢复默认后规则集回到 16 个',
      Object.keys(cfg2['rule-providers'] || {}).length === 16,
      String(Object.keys(cfg2['rule-providers'] || {}).length));

    /* ---------- 自定义分流组 ---------- */
    section('自定义分流组');
    {
      const C1 = '端到端测试组A';
      const C2 = '端到端测试组B';
      const readCfg2 = () => yaml.load(fs.readFileSync(paths.file('config.yaml'), 'utf8'));
      const kernelProxies = async () => {
        const pr = await core.S.controller.get('/proxies');
        return (pr && pr.proxies) || {};
      };

      const s1 = await commands.save_custom_ruleset({
        name: C1, out: 'direct', rules: ['DOMAIN-SUFFIX,e2e-custom.example', 'IP-CIDR,10.9.0.0/16'],
      });
      check('保存自定义分流组并热重载', s1 && s1.ok === true && s1.applied === true && s1.name === C1, JSON.stringify(s1));
      const cfgC1 = readCfg2();
      const c1Idx = (cfgC1.rules || []).findIndex((r) => String(r) === `DOMAIN-SUFFIX,e2e-custom.example,${C1}`);
      const setIdx1 = (cfgC1.rules || []).findIndex((r) => String(r).startsWith('RULE-SET,'));
      check('自定义规则进了配置', c1Idx >= 0, String(c1Idx));
      check('自定义规则排在内置分流之前', c1Idx >= 0 && c1Idx < setIdx1, `custom=${c1Idx} set=${setIdx1}`);
      check('IP 类自定义规则带 no-resolve',
        String((cfgC1.rules || [])[c1Idx + 1]) === `IP-CIDR,10.9.0.0/16,${C1},no-resolve`, String((cfgC1.rules || [])[c1Idx + 1]));
      const cg1 = (cfgC1['proxy-groups'] || []).find((g) => g.name === C1);
      check('自定义组被建成策略组且出口是直连', !!cg1 && cg1.proxies[0] === 'DIRECT', JSON.stringify(cg1));
      let kp = null;
      for (let i = 0; i < 20; i++) {
        await new Promise((r) => setTimeout(r, 250));
        kp = await kernelProxies();
        if (kp[C1]) break;
      }
      check('运行中的内核真的多了这个策略组', !!kp[C1], Object.keys(kp).filter((k) => k.includes('端到端')).join(','));

      const s2 = await commands.save_custom_ruleset({ name: C2, out: 'block', rules: ['DOMAIN-KEYWORD,e2e-ads'] });
      check('第二个自定义组也保存成功', s2 && s2.ok === true, JSON.stringify(s2));
      const cfgC2 = readCfg2();
      const aIdx = (cfgC2.rules || []).findIndex((r) => String(r).endsWith(`,${C1}`));
      const bIdx = (cfgC2.rules || []).findIndex((r) => String(r).endsWith(`,${C2}`));
      check('自定义组之间按创建顺序排列', aIdx >= 0 && bIdx > aIdx, `A=${aIdx} B=${bIdx}`);

      const mv = await commands.move_ruleset({ name: C2, dir: -1 });
      check('↑ 把自定义组提到前面', mv && mv.ok === true && mv.moved === true, JSON.stringify(mv));
      const cfgMv = readCfg2();
      const bIdx2 = (cfgMv.rules || []).findIndex((r) => String(r).endsWith(`,${C2}`));
      const aIdx2 = (cfgMv.rules || []).findIndex((r) => String(r).endsWith(`,${C1}`));
      check('配置里的顺序真的换了', bIdx2 >= 0 && aIdx2 > bIdx2, `A=${aIdx2} B=${bIdx2}`);
      const mvTop = await commands.move_ruleset({ name: C2, dir: -1 });
      check('已经在最前时不再移动', mvTop && mvTop.ok === true && mvTop.moved === false, JSON.stringify(mvTop));

      const rsC = await commands.get_rulesets();
      check('get_rulesets 带回自定义组与规则文本',
        rsC && rsC.custom_total === 2 && rsC.custom[0].name === C2 &&
        Array.isArray(rsC.custom[0].rules) && rsC.custom[0].rules.length === 1,
        JSON.stringify(rsC && rsC.custom));
      check('自定义组标记为 custom 且不计入内置 27 组', rsC.custom.every((g) => g.custom === true) && rsC.groups.length === 27);

      // 内置组的排序：拿当前第二个内置组往上提
      const orderBefore = (await commands.get_rulesets()).groups.map((g) => g.name);
      const second = orderBefore[1];
      const mvB = await commands.move_ruleset({ name: second, dir: -1 });
      check('内置组也能上移', mvB && mvB.ok === true && mvB.moved === true && mvB.order[0] === second,
        JSON.stringify({ moved: mvB && mvB.moved, head: mvB && mvB.order && mvB.order[0] }));
      const orderAfter = (await commands.get_rulesets()).groups.map((g) => g.name);
      check('上移后内置顺序真的变了', orderAfter[0] === second && orderAfter[1] === orderBefore[0],
        `${orderBefore.slice(0, 2).join(',')} → ${orderAfter.slice(0, 2).join(',')}`);
      check('内置与自定义不会互相跨越', (await commands.get_rulesets()).custom.map((g) => g.name).join(',') === `${C2},${C1}`);

      await expectReject('同名自定义组会被拒绝',
        () => commands.save_custom_ruleset({ name: C1, out: 'proxy', rules: ['DOMAIN,x.com'] }));
      await expectReject('与内置组同名会被拒绝',
        () => commands.save_custom_ruleset({ name: '🎯 国内直连', out: 'proxy', rules: ['DOMAIN,x.com'] }));
      await expectReject('非法规则类型会被拒绝',
        () => commands.save_custom_ruleset({ name: '坏规则组', out: 'proxy', rules: ['NOSUCHTYPE,x.com'] }));
      await expectReject('空规则会被拒绝',
        () => commands.save_custom_ruleset({ name: '空规则组', out: 'proxy', rules: [] }));

      const rs2 = await commands.reset_rulesets();
      check('恢复默认不会动自定义组', rs2 && rs2.ok === true, JSON.stringify(rs2));
      const rsAfterReset = await commands.get_rulesets();
      check('恢复默认后自定义组还在', rsAfterReset.custom_total === 2, String(rsAfterReset.custom_total));

      const d1 = await commands.delete_custom_ruleset({ name: C1 });
      const d2 = await commands.delete_custom_ruleset({ name: C2 });
      check('删除自定义组', d1 && d1.ok === true && d2 && d2.ok === true, JSON.stringify([d1, d2]));
      const cfgEnd = readCfg2();
      check('删除后规则从配置里消失',
        !(cfgEnd.rules || []).some((r) => String(r).includes(C1) || String(r).includes(C2)));
      check('删除后策略组也没了', !(cfgEnd['proxy-groups'] || []).some((g) => g.name === C1 || g.name === C2));
      let kpEnd = null;
      for (let i = 0; i < 20; i++) {
        await new Promise((r) => setTimeout(r, 250));
        kpEnd = await kernelProxies();
        if (!kpEnd[C1] && !kpEnd[C2]) break;
      }
      check('内核里的策略组也消失了', !kpEnd[C1] && !kpEnd[C2], Object.keys(kpEnd).filter((k) => k.includes('端到端')).join(','));
      check('清理干净：自定义组为 0', (await commands.get_rulesets()).custom_total === 0);
    }

    /* ---------- 自动更新 ---------- */
    section('自动更新');
    {
      const updater = require('../electron/core/updater');
      const ui = require('../electron/ui');
      const { execFileSync } = require('child_process');

      // 造一个「像成品包」的 zip（根下有 Polaris.exe），用本地 http 发出去
      const src = path.join(os.tmpdir(), 'polaris-e2e-upd-src');
      const zip = path.join(os.tmpdir(), 'polaris-e2e-upd.zip');
      fs.rmSync(src, { recursive: true, force: true });
      fs.rmSync(zip, { force: true });
      fs.mkdirSync(path.join(src, 'resources'), { recursive: true });
      fs.writeFileSync(path.join(src, 'Polaris.exe'), 'MZ' + 'x'.repeat(2048));
      fs.writeFileSync(path.join(src, 'resources', 'app.asar'), 'fake asar');
      fs.writeFileSync(path.join(src, 'version.txt'), '9.9.9');
      execFileSync('powershell', ['-NoProfile', '-NonInteractive', '-Command',
        `Compress-Archive -Path '${src}\\*' -DestinationPath '${zip}' -Force`], { windowsHide: true });
      const buf = fs.readFileSync(zip);
      const srv = http.createServer((req, res) => {
        if (req.url === '/pkg.zip') {
          res.writeHead(200, { 'content-type': 'application/zip', 'content-length': buf.length });
          return res.end(buf);
        }
        res.writeHead(404); res.end('nope');
      });
      await new Promise((r) => srv.listen(0, '127.0.0.1', r));
      const updBase = `http://127.0.0.1:${srv.address().port}`;

      // register() 才会把 updater 的进度接到 ui.emit 上；e2e 平时不注册 handler，
      // 这里注册一次，然后盯住推给渲染层的事件。
      require('../electron/ipc').register({});
      const events = [];
      const origEmit = ui.emit;
      ui.emit = (ch, payload) => { if (ch === 'update') events.push(payload && payload.phase); return origEmit(ch, payload); };

      try {
        const st0 = await commands.get_update_state();
        check('初始更新状态是 idle', st0.phase === 'idle', JSON.stringify(st0.phase));
        if (st0.packaged) {
          // 成品包里 can_apply 是"可写目录 + 已下好包"的函数，不该断言它一定是 false
          check('成品包里 can_apply 与 packaged/portable/staged 自洽',
            st0.can_apply === (st0.phase === 'staged' && st0.portable === true),
            JSON.stringify({ can_apply: st0.can_apply, portable: st0.portable, phase: st0.phase }));
        } else {
          check('开发态不允许自我替换', st0.can_apply === false, JSON.stringify({ can_apply: st0.can_apply }));
        }
        check('get_update_state 带回安装目录', !!st0.install_dir, st0.install_dir);

        const cu = await commands.check_update();
        check('check_update 说明能不能自装',
          typeof cu.can_apply === 'boolean' && typeof cu.apply_blocked === 'string',
          JSON.stringify({ can_apply: cu.can_apply, apply_blocked: cu.apply_blocked }));

        const dl = await commands.download_update({ url: `${updBase}/pkg.zip`, version: '9.9.9' });
        check('经 IPC 下载并解压成功', dl && dl.phase === 'staged', dl && dl.phase);
        check('staging 里有 Polaris.exe',
          fs.existsSync(path.join(updater.stagingDir(), 'Polaris.exe')));
        check('进度事件经 ui.emit 推给渲染层',
          events.includes('downloading') && events.includes('extracting') && events.includes('staged'),
          events.join('>'));

        // 自检**绝不能真的执行自我替换**：成品包 + 便携目录下 apply_update 会写
        // apply-update.cmd、退出后 robocopy 覆盖整个安装目录 —— 用测试用的假 zip
        // 一跑，用户的安装就被换成假 Polaris.exe 了（这个坑真踩过：在
        // dist/win-unpacked 上跑 --doctor，脚本已经走到 "copying"，
        // 只因当时另一个自检正占着 exe 才 robocopy failed 没被覆盖）。
        if (updater.canApply()) {
          check('成品包已就绪时允许自我替换（自检不真的执行安装）',
            updater.applyBlockedReason() === '', JSON.stringify(updater.applyBlockedReason()));
        } else {
          await expectReject('开发态 apply_update 被拦住', () => commands.apply_update());
        }

        const disc = await commands.discard_update();
        check('丢弃更新包返回 ok', disc && disc.ok === true, JSON.stringify(disc));
        const st1 = await commands.get_update_state();
        check('丢弃后回到 idle', st1.phase === 'idle', st1.phase);
        check('丢弃后下载下来的 zip 也删掉了（真包 ~186 MB）',
          !fs.existsSync(updater.zipPath('9.9.9')), updater.zipPath('9.9.9'));
      } finally {
        ui.emit = origEmit;
        try { srv.close(); } catch (_) {}
        updater.reset();
        fs.rmSync(src, { recursive: true, force: true });
        fs.rmSync(zip, { force: true });
      }
    }

    /* ---------- 系统代理 ---------- */
    if (WITH_SYSPROXY) {
      section('系统代理');
      await new Promise((r) => setTimeout(r, 400));
      const cur = sysproxy.snapshot();
      check('系统代理已开启', Number(cur.ProxyEnable) === 1, JSON.stringify(cur));
      check('系统代理指向内核端口', String(cur.ProxyServer || '').includes(String(mixed)), cur.ProxyServer);
      check('绕行列表含内网段', String(cur.ProxyOverride || '').includes('192.168.*'), cur.ProxyOverride);
    } else {
      section('系统代理（已跳过，加 --sysproxy 可验）');
    }

    /* ---------- 断开与还原 ---------- */
    section('断开与清理');
    const dis = await commands.disconnect();
    check('断开成功', dis.ok === true);
    connected = false;
    const st2 = await commands.get_status();
    check('断开后状态归零', st2.connected === false && st2.core_alive === false, JSON.stringify(st2));

    if (WITH_SYSPROXY) {
      const after2 = sysproxy.snapshot();
      const same = ['ProxyEnable', 'ProxyServer', 'ProxyOverride', 'AutoConfigURL']
        .every((k) => String(after2[k] == null ? '' : after2[k]) === String(proxySnapshotBefore[k] == null ? '' : proxySnapshotBefore[k]));
      check('系统代理已还原到进入前的值', same, `before=${JSON.stringify(proxySnapshotBefore)} after=${JSON.stringify(after2)}`);
    }

    /* ---------- 退出登录 ---------- */
    section('退出登录');
    await commands.logout();
    const s3 = await commands.get_settings();
    check('登录态已清除', s3.authed === false);
    check('凭据文件已删除', !fs.existsSync(paths.file('credentials.dat')));
    await expectReject('未登录时拉订阅会报错', () => commands.refresh_subscription());
  } catch (e) {
    check('自检过程中未抛异常', false, (e && e.stack) || String(e));
  } finally {
    try { if (connected) await commands.disconnect(); } catch (_) {}
    try { if (WITH_SYSPROXY && proxySnapshotBefore) sysproxy.restore(proxySnapshotBefore); } catch (_) {}
    try { server.close(); } catch (_) {}
    try { require('../electron/core/traffic').flush(); } catch (_) {}
  }

  console.log(`\n结果：${pass} 通过 / ${fail} 失败`);
  if (failures.length) {
    console.log('失败项：');
    failures.forEach((f) => console.log('  - ' + f));
  }
  try { fs.writeFileSync(path.join(paths.data(), 'doctor-report.txt'), report(), 'utf8'); } catch (_) {}
  app.exit(fail ? 1 : 0);
});

function report() {
  return [
    `Polaris 诊断报告 ${new Date().toISOString()}`,
    `结果：${pass} 通过 / ${fail} 失败`,
    failures.length ? '失败项：\n' + failures.map((f) => '  - ' + f).join('\n') : '失败项：无',
  ].join('\n');
}
