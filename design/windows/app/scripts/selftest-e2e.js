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
const path = require('path');
const yaml = require('js-yaml');

process.env.POLARIS_ALLOW_PLAINTEXT_CREDENTIALS = '1';

const mockPanel = require('./mock-panel');

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
  console.log('data =', paths.data());

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
  app.exit(fail ? 1 : 0);
});
