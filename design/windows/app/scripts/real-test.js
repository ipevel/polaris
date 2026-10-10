'use strict';
/**
 * 真实面板联调 —— 拿真账号真面板真节点跑一遍每个页面要用的数据。
 *
 *   electron.exe scripts/real-test.js --panel=https://xxx --email=a@b.c --password=***
 *       [--port=7890]          把本机代理端口固定成 7890 再验一遍（对应"用户第 9 条"）
 *       [--keep-session]       结束后不断开内核（默认会断开并退出登录）
 *
 *   electron.exe scripts/real-test.js --restore --data=<目录>
 *       直接用那个目录里已经登录好的会话跑（用户说"我已经登录好了，就用我登录的这个测"）。
 *       --data 会通过 POLARIS_DATA_DIR 改写数据目录，所以通常是把它拷一份再跑，
 *       不动用户自己那份 data。
 *
 * 为什么要有这个：mock 面板再怎么对齐形状，也只能证明"映射逻辑自洽"，
 * 证明不了"真面板真节点真流量真跑得通"。DEVNOTES 里"真实面板联调 ❌"就是指这个。
 *
 * 注意事项：
 *   - 跑在开发态（electron.exe scripts/real-test.js），数据目录默认是 scripts/.devdata，
 *     不会碰打包版 dist/win-unpacked/data，也就不会顶掉用户正在用的那个实例。
 *   - 强制 sys_proxy=false：不改系统代理注册表，避免把用户正在用的代理顶掉。
 *   - 密码只在命令行里给一次，不写进任何文件（写文件的是 DPAPI 加密后的凭据）。
 */

// --data 要在 require 任何 electron/ 模块之前设好，paths.js 第一次读 root() 就锁定
const DATA_DIR = (() => {
  const a = process.argv.find((x) => x.startsWith('--data='));
  return a ? a.slice(7) : '';
})();
if (DATA_DIR) process.env.POLARIS_DATA_DIR = DATA_DIR;

const { app } = require('electron');
const http = require('http');
const nodePath = require('path');
const nodeFs = require('fs');

// 必须和 main.js 一样把 Electron 的 userData 指到 <root>/data/electron：
// safeStorage（DPAPI）的密钥存在 userData 的 Local State 里，指错了就解不开
// credentials.dat —— 现象是"凭据无法解密，需要重新登录"，
// 会让人误以为"登录态跨重启失效"，其实是自检自己没站在同一个 userData 上。
if (DATA_DIR) {
  try {
    const electronData = nodePath.join(DATA_DIR, 'data', 'electron');
    nodeFs.mkdirSync(electronData, { recursive: true });
    app.setPath('userData', electronData);
    app.setPath('sessionData', electronData);
  } catch (_) {}
}

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
const arg = (k, d = '') => {
  const a = process.argv.find((x) => x.startsWith(`--${k}=`));
  return a ? a.slice(k.length + 3) : d;
};
const flag = (k) => process.argv.includes(`--${k}`);

function httpThroughProxy(proxyPort, targetUrl, timeoutMs = 20000) {
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

const PANEL = arg('panel');
const EMAIL = arg('email');
const PASSWORD = arg('password');
const PORT = Number(arg('port', '0')) || 0;
const KEEP = flag('keep-session');
const RESTORE = flag('restore');

if (!RESTORE && (!PANEL || !EMAIL || !PASSWORD)) {
  console.log('用法: electron.exe scripts/real-test.js --panel=<url> --email=<邮箱> --password=<密码> [--port=7890]');
  console.log('   或: electron.exe scripts/real-test.js --restore [--data=<数据根目录>]   # 会用 <根目录>/data');
  process.exit(2);
}

app.whenReady().then(async () => {
  const { commands } = require('../electron/ipc');
  const paths = require('../electron/paths');
  const store = require('../electron/store');
  const core = require('../electron/core/manager');
const builder = require('../electron/core/builder');

  paths.ensureAll();
  console.log(`data = ${paths.data()}`);
  const portBefore = store.get('mixed_port');

  try {
    /* ---------------- 登录 ---------------- */
    section(RESTORE ? '登录（用数据目录里已有的会话）' : '登录（真实面板）');

    // 顺序要紧：restore() 之前 session.panelUrl 还是空的，
    // 这时候问站点信息只会拿到"尚未配置面板地址"（第一次写这个脚本就踩了）。
    const panel = require('../electron/panel/client');
    if (RESTORE) {
      // 不登录：直接把那个目录里 DPAPI 加密的凭据读回来用。
      // 这条同时验证"重启后登录态还在"（A-20）在真面板上确实成立。
      const okRestore = panel.restore(); // 返回布尔值，不是对象
      check('已有会话被认出来了', okRestore === true, JSON.stringify({ email: panel.session.email }));
      check('会话里有 token', !!panel.session.token && String(panel.session.token).length > 10,
        `len=${String(panel.session.token || '').length}`);
      check('会话里记住了面板地址', /^https?:\/\//.test(String(panel.session.panelUrl)), String(panel.session.panelUrl));
      console.log(`  复用账号: ${panel.session.email || '(取不到)'}`);
    }

    const site0 = await commands.get_site_info();
    console.log(`  站点: ${site0.appName || site0.appUrl || '(未取到)'} — ${site0.appDescription || ''}`);
    check('面板地址连通', !!(site0.appUrl || PANEL), JSON.stringify(site0));

    await commands.set_setting({ key: 'sys_proxy', value: false });
    await commands.set_setting({ key: 'tun_mode', value: false });

    let login;
    if (!RESTORE) {
      // 必须显式带上 --panel 给的地址：不带就会用 settings.json 里上一轮留下的地址
      // （曾经因此连到 127.0.0.1:8436 那个早就关掉的 mock 面板）。
      try { login = await commands.login({ email: EMAIL, password: PASSWORD, panel: PANEL }); }
      catch (e) { check('登录成功', false, e && e.message); finish(); return; }
      check('登录成功', login.ok === true, JSON.stringify(login).slice(0, 300));
    }

    const st = await commands.get_settings();
    // 用户第 5 条：自己的邮箱不打码（原来是 c*@mbe.cc）
    check('登录态认出来了且邮箱不打码', st.authed === true && /@/.test(String(st.email)) && String(st.email).indexOf('*') < 0,
      JSON.stringify({ authed: st.authed, email: st.email }));
    if (EMAIL) check('邮箱就是登录用的那个', String(st.email) === String(EMAIL), `「${st.email}」vs「${EMAIL}」`);

    const reg = await commands.get_register_config();
    console.log(`  注册配置: ${JSON.stringify(reg)}`);

    /* ---------------- 我的页面 ---------------- */
    section('我的页面（套餐 / 订单 / 工单 / 邀请 / 公告）');
    const plan = await commands.get_plan();
    console.log(`  套餐: ${plan.name} 已用 ${plan.used} / ${plan.total} GB，到期 ${plan.expire}`);
    check('套餐名不是"未订阅"', !!plan.name && plan.name !== '未订阅', String(plan.name));
    check('套餐总额 > 0', Number(plan.total) > 0, String(plan.total));
    check('已用流量是数字（0 也合法，新账号没跑过流量）', Number.isFinite(Number(plan.used)) && Number(plan.used) >= 0, String(plan.used));
    check('到期时间有值', /^\d{4}-\d{2}-\d{2}$/.test(String(plan.expire)), String(plan.expire));

    const plans = await commands.get_plans();
    check('可购买套餐列表非空', Array.isArray(plans) && plans.length > 0, String(plans && plans.length));
    if (plans && plans[0]) {
      console.log(`  可购买: ${plans.slice(0, 3).map((p) => `${p.name} ￥${p.price}/${p.unit || p.period || ''} ${p.transfer_enable}GB`).join(' | ')}`);
      check('套餐价格不是 0（周期价字段分→元）', plans.some((p) => Number(p.price) > 0), plans.map((p) => p.price).join(','));
    }

    const orders = await commands.get_orders();
    check('订单列表非空（我的订单读到了）', Array.isArray(orders) && orders.length > 0, String(orders && orders.length));
    if (orders && orders[0]) {
      console.log(`  最近订单: ${orders[0].name} ￥${orders[0].amount} ${orders[0].status} ${orders[0].date}`);
      check('订单名字不是空', !!orders[0].name, JSON.stringify(orders[0]));
    }

    const tickets = await commands.get_tickets();
    console.log(`  工单 ${Array.isArray(tickets) ? tickets.length : '?'} 条`);

    const invite = await commands.get_invite();
    console.log(`  邀请: code=${invite.code || '(无)'} 已注册=${invite.registered} 佣金=${invite.commission}`);
    check('邀请码要么有值要么明确为空串', typeof invite.code === 'string', JSON.stringify(invite).slice(0, 200));

    const notices = await commands.get_notices();
    console.log(`  公告 ${Array.isArray(notices) ? notices.length : '?'} 条${notices && notices[0] ? `，最新《${notices[0].title}》` : ''}`);

    // 用户第 2 条：我的页要有 Telegram 入口（面板下发了链接才显示）
    const site = await commands.get_site_info();
    const tg = String((site && site.telegramUrl) || '');
    console.log(`  Telegram 入口: ${tg || '(面板没配，界面隐藏该入口)'}`);
    check('Telegram 链接要么为空要么是 t.me 的合法地址',
      !tg || (/^https:\/\/(t\.me|telegram\.me|telegram\.dog)\//.test(tg) || /^https:\/\/[a-z.]*telegram[a-z.]*\//.test(tg)), tg);

    /* ---------------- 流量页面（必须来自面板） ---------------- */
    section('流量页面（今天 / 本周 / 本月 取面板明细）');
    console.log(`  账号已用 ${plan.used} GB（面板按 GB 两位小数，几 KB 会显示成 0.00，所以不能拿它判断明细有没有数据）`);
    for (const range of ['today', 'week', 'month']) {
      const t = await commands.get_traffic({ range });
      const site = t.site || {};
      const tl = await commands.get_traffic_log({ range });
      const rows = Array.isArray(tl) ? tl : [];
      console.log(`  ${range}: 面板 ${site.days} 天 ↑${site.up_text} ↓${site.down_text}（累计 ${site.total_text}） · `
        + `明细 ${rows.length} 行 · 账号 已用 ${t.account && t.account.used_text} / ${t.account && t.account.quota_text}`);
      // 不变式（与账号有没有流量无关）：
      //   有明细 → 每行日期合法、流量为正、站点合计 = 明细求和
      //   没明细 → 站点合计必须是干净的 0（不能拿本机采样编数字）
      const datesOk = rows.every((r) => /^\d{4}-\d{2}-\d{2}$/.test(String(r.date)));
      const sumUp = rows.reduce((a, r) => a + Number(r.upload || 0), 0);
      const sumDown = rows.reduce((a, r) => a + Number(r.download || 0), 0);
      check(`${range} 明细每行都有合法日期`, datesOk, JSON.stringify(rows.slice(0, 2)));
      if (rows.length) {
        check(`${range} 站点合计 = 明细求和（不乘 server_rate、不漏行）`,
          Number(site.up) === sumUp && Number(site.down) === sumDown,
          `site ↑${site.up}/↓${site.down} vs 明细 ↑${sumUp}/↓${sumDown}`);
        check(`${range} 有明细时合计 > 0`, Number(site.up) + Number(site.down) > 0, JSON.stringify(site));
      } else {
        check(`${range} 没明细时站点合计是干净的空（不编数字）`,
          Number(site.days) === 0 && Number(site.up) === 0 && Number(site.down) === 0, JSON.stringify(site));
      }
    }

    /* ---------------- 订阅与节点 ---------------- */
    section('订阅与节点（真节点）');
    const sub = await commands.refresh_subscription();
    check('订阅刷新成功', sub.ok === true, JSON.stringify(sub).slice(0, 300));
    check('订阅里有真节点（count>0）', Number(sub.count) > 0, String(sub.count));
    const subUrl = await commands.get_subscribe_url();
    check('订阅链接取到', !!(subUrl && subUrl.url), JSON.stringify(subUrl).slice(0, 200));

    const nodes = await commands.get_nodes();
    const list = Array.isArray(nodes) ? nodes : [];
    console.log(`  节点 ${list.length} 个`);
    check('节点列表非空', list.length > 0, String(list.length));
    const bad = list.filter((n) => /剩余流量|到期|官网|expire/i.test(n.name || ''));
    check('没有信息伪节点', bad.length === 0, bad.map((n) => n.name).join('|'));
    console.log(`  示例: ${list.slice(0, 5).map((n) => `${n.name}(${n.latency})`).join(' | ')}`);

    /* ---------------- 端口 + 连接 + 真流量 ---------------- */
    section(`连接（本机代理端口 ${PORT || '随机'}）`);
    if (PORT) {
      const r = await commands.set_setting({ key: 'mixed_port', value: PORT });
      check(`端口设为 ${PORT}`, r.ok === true && r.mixed_port === PORT, JSON.stringify(r));
    }
    let status = await commands.get_status();
    if (status.connected) { await commands.disconnect(); status = await commands.get_status(); }
    let conn = null;
    let connErr = null;
    try { conn = await commands.connect(); } catch (e) { connErr = e; }
    if (connErr) {
      // 端口被别的程序占着（真机上 7890 是 BettboxCore）——必须给一句人话，
      // 而不是"已连接"却没有代理可用。验完错误路径再退回自动端口跑通全流程。
      if (PORT && /已被其它程序占用/.test(String(connErr.message))) {
        check(`端口 ${PORT} 被占时给出人话提示（不是假装连上）`, true);
        console.log(`  ${connErr.message}`);
        await commands.set_setting({ key: 'mixed_port', value: 0 });
        try { conn = await commands.connect(); } catch (e2) { connErr = e2; }
      }
      if (!conn) { check('连接成功', false, String(connErr && connErr.message)); }
    }
    if (conn) check('连接成功', conn.ok === true, JSON.stringify(conn).slice(0, 400));

    status = await commands.get_status();
    console.log(`  内核: ${status.node || '(未选)'} ${status.mode} 端口 ${core.mixedPort()} 运行 ${status.uptime}`);
    check('状态显示已连接', status.connected === true, JSON.stringify(status));
    if (PORT && core.mixedPort() === PORT) check(`内核监听的是 ${PORT}`, true, String(core.mixedPort()));
    check('有选中节点', !!status.node, String(status.node));

    const groups = await commands.get_routing_groups();
    const gArr = Array.isArray(groups) ? groups : [];
    const vis = gArr.filter((g) => !g.builtin && !g.structural);
    const main = gArr.find((g) => g.name === builder.SELECTOR_GROUP) || vis[0];
    console.log(`  策略组 ${gArr.length} 个（可见 ${vis.length}）: ${vis.slice(0, 6).map((g) => `${g.name}(${g.count}节点/${g.latency}ms)`).join(' | ')}`);
    // 用户第二轮反馈：主组只能有一张卡，且必须是「🚀 节点选择」（与安卓端同名）
    check(`分组里有主组「${builder.SELECTOR_GROUP}」`, gArr.some((g) => g.name === builder.SELECTOR_GROUP), gArr.map((g) => g.name).join(','));
    check('没有第二个叫「节点选择」的主组（重复卡根因）', !gArr.some((g) => g.name === '节点选择'), gArr.map((g) => g.name).join(','));
    check('主组排在可见分组的第一位', !!vis[0] && vis[0].name === builder.SELECTOR_GROUP, vis.map((g) => g.name).join(','));
    check('兜底组不在可见分类里', !vis.some((g) => g.name === builder.FINAL_GROUP), vis.map((g) => g.name).join(','));
    check('分组带延迟字段（第 4 条）', !!main && typeof main.latency === 'number', JSON.stringify(main));
    // 用户第三轮反馈：两张「直连」分类卡重复了。内置的 🎯 国内直连 与面板的 🎯 全球直连
    // 语义相同，配置层必须只留一个（面板给了就复用面板的，见 rulesets.js 的 alias）。
    const names = gArr.map((g) => g.name);
    check('两张「直连」分类卡不再并存', !(names.indexOf('🎯 国内直连') >= 0 && names.indexOf('🎯 全球直连') >= 0),
      names.filter((x) => /直连$/.test(x)).join(',') || '(无直连组)');
    check('直连类分组至少有一个（别把功能删没了）', names.some((x) => /直连$/.test(x)), names.join(','));

    // 这条只做记录，不算断言：订阅里默认选中的节点可能是死的（实测过美国节点 TCP 连不上），
    // 那既不是产品缺陷也不该让整轮红。真正的断言在延迟测试挑出活节点之后。
    try {
      const r = await httpThroughProxy(core.mixedPort(), 'http://www.gstatic.com/generate_204');
      console.log(`  经当前选中节点访问外网: status=${r.status}${r.status === 204 ? '' : '（该节点不通，下面挑活节点再验）'}`);
    } catch (e) { console.log(`  经当前选中节点访问外网失败: ${e.message}（下面挑活节点再验）`); }

    /* ---------------- 延迟测试（用户第 4 条：每个分组都要有延迟） ---------------- */
    section('延迟测试（真节点 + 每个策略组）');
    await commands.speed_test();
    const nodesAfter = await commands.get_nodes();
    const alive = (Array.isArray(nodesAfter) ? nodesAfter : []).filter((n) => Number(n.latency) > 0);
    console.log(`  节点延迟: 可用 ${alive.length}/${(nodesAfter || []).length} —— ${alive.slice(0, 5).map((n) => `${n.name}=${n.latency}ms`).join(' | ') || '(全部超时)'}`);
    check('延迟测试有真结果（不是一片未测）', alive.length > 0, `${alive.length}/${(nodesAfter || []).length}`);

    const gd = await commands.test_group_delays();
    const gdArr = (gd && Array.isArray(gd.groups)) ? gd.groups : [];
    const gAlive = gdArr.filter((g) => Number(g.latency) > 0);
    console.log(`  分组延迟: ${gAlive.length}/${gdArr.length} 个分组有延迟 —— ${gAlive.slice(0, 5).map((g) => `${g.name}=${g.latency}ms`).join(' | ') || '(全部超时)'}`);
    check('每个分组都有延迟字段（不是 undefined）', gdArr.length > 0 && gdArr.every((g) => typeof g.latency === 'number'), JSON.stringify(gdArr.slice(0, 2)));
    check('至少一个分组测出真延迟', gAlive.length > 0, `${gAlive.length}/${gdArr.length}`);

    // 真节点可能本身是死的（第一次实测选中的美国节点 TCP 连不上）——这不是产品缺陷。
    // 用延迟测试挑一个活节点再验一次外网：活节点也不通，才说明是我们的问题。
    if (alive.length) {
      const fastest = alive.slice().sort((a, b) => a.latency - b.latency)[0];
      const picked = await commands.select_node({ name: fastest.name, group: main && main.name });
      console.log(`  改选最快的活节点: ${fastest.name} (${fastest.latency}ms) ok=${picked && picked.ok}`);
      await new Promise((r) => setTimeout(r, 800));
      try {
        const r2 = await httpThroughProxy(core.mixedPort(), 'http://www.gstatic.com/generate_204', 25000);
        check('经活节点访问外网返回 204', r2.status === 204, `status=${r2.status}`);
      } catch (e) { check('经活节点访问外网返回 204', false, e.message); }
    } else {
      check('经代理访问外网返回 204', false, '延迟测试没测出任何活节点，无法判断');
    }

    const t2 = await commands.get_traffic({ range: 'today' });
    check('连接后本机本次流量有计数', Number((t2.session || {}).up) + Number((t2.session || {}).down) > 0, JSON.stringify(t2.session));

    // 真跑几 MB 流量，然后看面板明细会不会出现 —— 这才是"流量页面读站点明细"的闭环
    // （用户第 5 条：今天/本周/本月必须是站点数据，不是本机算的）。
    try {
      await httpThroughProxy(core.mixedPort(), 'http://speed.cloudflare.com/__down?bytes=3000000', 40000);
      console.log('  已通过真节点下载约 3 MB');
    } catch (e) { console.log(`  下载测试跳过: ${e.message}`); }
    let sawPanel = null;
    for (let i = 0; i < 12 && !sawPanel; i += 1) {
      await new Promise((r) => setTimeout(r, 5000));
      const tl = await commands.get_traffic_log({ range: 'today' });
      if (Array.isArray(tl) && tl.length > 0) sawPanel = tl[0];
    }
    if (sawPanel) {
      console.log(`  面板明细已更新: ${sawPanel.date} 上行 ${sawPanel.upload} / 下行 ${sawPanel.download} 字节`);
      check('跑完真流量后面板明细出现了（站点统计闭环）', true);
    } else {
      console.log('  WARN  60 秒内面板还没统计出今天的明细（面板侧统计有延迟，不算失败）');
    }

    /* ---------------- 分流顺序（拖动排序，数据层） ---------------- */
    section('分流顺序（拖动排序）');
    {
      const rs0 = await commands.get_rulesets();
      const g0 = (rs0 && Array.isArray(rs0.groups)) ? rs0.groups.map((g) => g.name) : [];
      console.log(`  内置分流顺序（前 5）: ${g0.slice(0, 5).join(' | ')}`);
      check('内置分流有可排序的多组', g0.length >= 3, `${g0.length} 组`);
      check('内置组都带内核里的真实组名（alias 用）', (rs0.groups || []).every((g) => typeof g.group === 'string' && g.group), JSON.stringify((rs0.groups || []).slice(0, 2)));
      const moving = g0[2];
      const r1 = await commands.reorder_ruleset({ name: moving, to: 0 });
      check('拖动排序接口生效', r1 && r1.moved === true, JSON.stringify(r1).slice(0, 200));
      const rs1 = await commands.get_rulesets();
      const g1 = (rs1 && Array.isArray(rs1.groups)) ? rs1.groups.map((g) => g.name) : [];
      check('被拖的组真的排到了第一位', g1[0] === moving, `${g1.slice(0, 4).join(' | ')}`);
      check('排序没有丢组', g1.length === g0.length, `${g0.length} → ${g1.length}`);
      // 顺序 = 匹配优先级，会在配置里体现成 RULE-SET 行的先后
      const r2 = await commands.reorder_ruleset({ name: moving, to: 2 });
      check('拖回原位也生效（不把测试顺序留给你）', r2 && r2.moved === true, JSON.stringify(r2).slice(0, 120));
      const rs2 = await commands.get_rulesets();
      const g2 = (rs2 && Array.isArray(rs2.groups)) ? rs2.groups.map((g) => g.name) : [];
      check('顺序已还原', g2.join(',') === g0.join(','), g2.slice(0, 4).join(' | '));
    }

    /* ---------------- 本地分流方案（与安卓端同一模型） ---------------- */
    section('本地分流方案（屏蔽面板下发，只用本地内置方案）');
    {
      const rulesets = require('../electron/core/rulesets');
      const yaml = require('js-yaml');
      const cfgPath = paths.file('config.yaml');
      const cfg = yaml.load(nodeFs.readFileSync(cfgPath, 'utf8')) || {};

      const rsState = await commands.get_rulesets();
      check('分流方案总开关默认是「用本地」（与安卓端一致）', rsState.on === true, JSON.stringify(rsState.on));
      check('没有降级', !rsState.degraded, String(rsState.degraded || ''));

      // ① 面板下发的策略组必须整体消失，只剩 结构组 + 本地启用组 + 自定义组
      const localNames = new Set([
        rulesets.GROUP_SELECTOR, rulesets.GROUP_AUTO, rulesets.GROUP_FALLBACK, rulesets.GROUP_FINAL,
        ...rulesets.TABLE.map((g) => g.name),
        ...(rsState.custom || []).map((g) => g.name),
      ]);
      const cfgGroups = (cfg['proxy-groups'] || []).map((g) => g.name);
      const strangers = cfgGroups.filter((n) => !localNames.has(n));
      console.log(`  配置里的策略组 ${cfgGroups.length} 个: ${cfgGroups.slice(0, 6).join(' | ')}`);
      check('面板下发的策略组一个不剩（只剩本地方案）', strangers.length === 0, strangers.join(','));
      check('主选择组在第一位（安卓端 selectorGroup 契约）', cfgGroups[0] === rulesets.GROUP_SELECTOR, cfgGroups.slice(0, 3).join(','));
      check('四个结构组都在', [rulesets.GROUP_SELECTOR, rulesets.GROUP_AUTO, rulesets.GROUP_FALLBACK, rulesets.GROUP_FINAL]
        .every((n) => cfgGroups.includes(n)), cfgGroups.join(','));
      check('主组首位成员是「自动选择」（默认出口）',
        JSON.stringify((cfg['proxy-groups'][0] || {}).proxies || []) === JSON.stringify([rulesets.GROUP_AUTO, rulesets.GROUP_FALLBACK, 'DIRECT']),
        JSON.stringify((cfg['proxy-groups'][0] || {}).proxies));
      check('各组都是 include-all（节点不写死在配置里，订阅更新不用重拼）',
        (cfg['proxy-groups'] || []).filter((g) => g.name !== rulesets.GROUP_FINAL).every((g) => g['include-all'] === true),
        JSON.stringify((cfg['proxy-groups'] || []).map((g) => [g.name, !!g['include-all']])));
      check('兜底组刻意不 include-all（成员只有 主组+DIRECT）',
        (cfg['proxy-groups'].find((g) => g.name === rulesets.GROUP_FINAL) || {})['include-all'] !== true);

      // ② rule-provider 全部改成 http + 24h 在线更新，路径落在 data/polaris-rules/
      const rp = cfg['rule-providers'] || {};
      const rpKeys = Object.keys(rp);
      console.log(`  rule-provider ${rpKeys.length} 个，全部 type:http？${rpKeys.every((k) => rp[k].type === 'http')}`);
      check('rule-provider 全部是 type:http（在线更新，不再是随包 file）', rpKeys.length > 0 && rpKeys.every((k) => rp[k].type === 'http'),
        JSON.stringify(rpKeys.slice(0, 3).map((k) => [k, rp[k].type])));
      check('rule-provider 的 interval 都是 86400（24 小时，与安卓端同值）', rpKeys.every((k) => Number(rp[k].interval) === rulesets.PROVIDER_INTERVAL),
        JSON.stringify(rpKeys.slice(0, 3).map((k) => [k, rp[k].interval])));
      check('rule-provider 的 path 落在 polaris-rules/ 下（内核按 -d 解析）',
        rpKeys.every((k) => String(rp[k].path || '').startsWith(`${rulesets.CACHE_DIR}/`)),
        JSON.stringify(rpKeys.slice(0, 3).map((k) => [k, rp[k].path])));
      check('rule-provider 的 url 是 jsDelivr 上的 https 规则库',
        rpKeys.every((k) => /^https:\/\//.test(String(rp[k].url || '')) && String(rp[k].url).includes('jsdelivr')),
        JSON.stringify(rpKeys.slice(0, 2).map((k) => rp[k].url)));
      check('rule-provider 的键与启用组的规则集一一对应',
        rpKeys.length === (rsState.groups || []).filter((g) => g.enabled).reduce((n, g) => n + g.count, 0),
        `${rpKeys.length} vs ${(rsState.groups || []).filter((g) => g.enabled).reduce((n, g) => n + g.count, 0)}`);

      // ③ 面板下发的 rules / sub-rules 必须整体丢掉
      const rules = cfg.rules || [];
      check('面板下发的规则已丢弃（没有 GEOIP,CN 这类面板自带规则）',
        !rules.some((r) => /^GEOIP,CN$/i.test(String(r)) || /^RULE-SET,chinadomain/i.test(String(r))),
        rules.filter((r) => /GEOIP|chinadomain/i.test(String(r))).slice(0, 3).join(' | '));
      check('最后一条是 MATCH,🐟 漏网之鱼', String(rules[rules.length - 1] || '') === `MATCH,${rulesets.GROUP_FINAL}`, String(rules[rules.length - 1]));
      check('sub-rules 已删除（面板 sub-rules 只被面板 rules 引用）', cfg['sub-rules'] === undefined || cfg['sub-rules'] === null);
      check('内网/私有地址永远直连（与安卓端 lanDirectRules 同款）',
        rules.some((r) => String(r).startsWith('IP-CIDR,192.168.0.0/16')) && rules.some((r) => String(r).startsWith('IP-CIDR,127.0.0.0/8')));
      const dd = core.S.directDomains || [];
      check('自有域名直连排在规则最前面', !dd.length || rules.slice(0, dd.length).every((r) => String(r).startsWith('DOMAIN-SUFFIX,')),
        rules.slice(0, 3).join(' | '));

      // ④ 种子只补缺失：改过的缓存文件绝不能被随包种子覆盖回去
      const seedDir = paths.polarisRules();
      const seedFiles = rulesets.allProviderKeys().map((k) => rulesets.seedFile(k));
      const missing = seedFiles.filter((f) => !nodeFs.existsSync(nodePath.join(seedDir, f)));
      check(`规则集缓存目录已预播种（${seedFiles.length} 个文件）`, missing.length === 0, missing.slice(0, 5).join(','));
      const probeKey = 'gs_google_play';
      const probeFile = nodePath.join(seedDir, rulesets.seedFile(probeKey));
      const original = nodeFs.readFileSync(probeFile);
      try {
        nodeFs.writeFileSync(probeFile, Buffer.concat([original, Buffer.from('\n# polaris-online-update-marker\n')]));
        core.prepareConfig({ reuse: true });          // 等价于下次启动/重拉订阅时的播种
        const after = nodeFs.readFileSync(probeFile, 'utf8');
        check('内核下载到的新版规则不会被随包种子打回旧版（种子只补缺失）', after.includes('polaris-online-update-marker'),
          `size=${after.length} vs seed=${original.length}`);
      } finally {
        nodeFs.writeFileSync(probeFile, original);    // 原样还回去
      }
      check('探测用的规则集文件已还原', nodeFs.readFileSync(probeFile).length === original.length);

      // ⑤ 内核真的把每个 rule-provider 加载进来了（异步初始化，必须轮询）
      // 注意响应形状：mihomo v1.19 返回 {"providers": {<key>: {ruleCount, ...}}}，
      // 老版本是直接把 provider 铺在顶层 —— 两种都吃。
      const unwrapProv = (raw) => (raw && raw.providers && typeof raw.providers === 'object' ? raw.providers : raw);
      let prov = null;
      for (let i = 0; i < 30; i += 1) {
        try { prov = unwrapProv(await core.S.controller.get('/providers/rules')); } catch (_) { prov = null; }
        if (prov && rpKeys.every((k) => prov[k] && Number(prov[k].ruleCount) > 0)) break;
        await new Promise((r) => setTimeout(r, 500));
      }
      const zero = rpKeys.filter((k) => !prov || !prov[k] || !(Number(prov[k].ruleCount) > 0));
      const total = prov ? rpKeys.reduce((n, k) => n + Number((prov[k] || {}).ruleCount || 0), 0) : 0;
      console.log(`  内核已加载规则数合计 ${total}${zero.length ? `（未就绪: ${zero.slice(0, 4).join(',')}）` : ''}`);
      check('内核把每个 rule-provider 都加载了（ruleCount 全 > 0）', zero.length === 0, zero.slice(0, 6).join(','));
      check('规则总数超过 1 万条（真的是一整套分流表，不是空壳）', total > 10000, String(total));
      check('rule-provider 是内核在线拉取的（vehicleType=HTTP）',
        !prov || rpKeys.every((k) => !prov[k] || prov[k].vehicleType === 'HTTP'),
        JSON.stringify(rpKeys.slice(0, 3).map((k) => [k, prov && prov[k] && prov[k].vehicleType])));
    }

    /* ---------------- 三个特殊出口（用户第 2 条） ---------------- */
    // 主组的前三个成员不是节点，是内核的三个特殊出口。用户问「这三个实际生效吗」——
    // 光看界面看不出来（它们没有"延迟"这个概念，界面显示「未测」正是这个原因），
    // 必须让内核真的从它们出去一次，并且把内核里的类型也读出来对照。
    section('三个特殊出口（自动选择 / 故障转移 / DIRECT）真的生效吗');
    if (main) {
      const enc = (n) => encodeURIComponent(n);
      const proxyInfo = async (name) => {
        try { return await core.S.controller.get(`/proxies/${enc(name)}`); } catch (_) { return null; }
      };
      const infoAuto = await proxyInfo(builder.AUTO_GROUP);
      const infoFall = await proxyInfo(builder.FALLBACK_GROUP);
      const infoDirect = await proxyInfo('DIRECT');
      console.log(`  内核里的类型: ${builder.AUTO_GROUP}=${infoAuto && infoAuto.type} / ${builder.FALLBACK_GROUP}=${infoFall && infoFall.type} / DIRECT=${infoDirect && infoDirect.type}`);
      check('「自动选择」在内核里是 URLTest（真会自己挑最快的）', !!infoAuto && infoAuto.type === 'URLTest', String(infoAuto && infoAuto.type));
      check('「故障转移」在内核里是 Fallback（真会掉头换下一个）', !!infoFall && infoFall.type === 'Fallback', String(infoFall && infoFall.type));
      check('DIRECT 在内核里是 Direct（真直连，不绕节点）', !!infoDirect && infoDirect.type === 'Direct', String(infoDirect && infoDirect.type));

      const nodeNames = new Set(((await commands.get_nodes()) || []).map((n) => n.name));
      // 界面里点那三行就是这个动作：把主组的出口切过去
      const viaGroup = async (name) => {
        const sel = await commands.select_node({ name, group: main.name });
        await new Promise((r) => setTimeout(r, 900));
        const g = await proxyInfo(main.name);
        return { sel, now: g && g.now };
      };

      const a = await viaGroup(builder.AUTO_GROUP);
      check('把主组切到「自动选择」内核认了', !!a.sel && a.sel.ok === true && a.now === builder.AUTO_GROUP, JSON.stringify(a).slice(0, 160));
      const autoNow = ((await proxyInfo(builder.AUTO_GROUP)) || {}).now;
      check('「自动选择」自己挑出了真实节点', !!autoNow && nodeNames.has(autoNow), String(autoNow));
      try {
        const r = await httpThroughProxy(core.mixedPort(), 'http://www.gstatic.com/generate_204', 25000);
        check('经「自动选择」真的能出网（204）', r.status === 204, `status=${r.status} 出口=${autoNow}`);
      } catch (e) { check('经「自动选择」真的能出网（204）', false, e.message); }

      const f = await viaGroup(builder.FALLBACK_GROUP);
      check('把主组切到「故障转移」内核认了', !!f.sel && f.sel.ok === true && f.now === builder.FALLBACK_GROUP, JSON.stringify(f).slice(0, 160));
      const fallNow = ((await proxyInfo(builder.FALLBACK_GROUP)) || {}).now;
      check('「故障转移」也有当前出口（第一个可用节点）', !!fallNow && nodeNames.has(fallNow), String(fallNow));
      try {
        const r = await httpThroughProxy(core.mixedPort(), 'http://www.gstatic.com/generate_204', 25000);
        check('经「故障转移」真的能出网（204）', r.status === 204, `status=${r.status} 出口=${fallNow}`);
      } catch (e) { check('经「故障转移」真的能出网（204）', false, e.message); }

      const d = await viaGroup('DIRECT');
      check('把主组切到「DIRECT」内核认了', !!d.sel && d.sel.ok === true && d.now === 'DIRECT', JSON.stringify(d).slice(0, 160));
      // 直连的判据用国内站点：本机能不能直连外网取决于所在网络，
      // 但"走 DIRECT = 走本机网络"这件事，用一定能直连的国内站点验最干净。
      let dr = null;
      try { dr = await httpThroughProxy(core.mixedPort(), 'http://www.baidu.com', 20000); } catch (e) { dr = { status: `ERR:${e.message}` }; }
      check('经「DIRECT」真的走本机网络直连（百度可达）', [200, 301, 302].includes(dr.status), `status=${dr.status}`);

      // 还原成配置默认（主组首位 = 自动选择），别把测试状态留给你
      const back = await commands.select_node({ name: builder.AUTO_GROUP, group: main.name });
      check('测完把主组还原成「自动选择」', !!back && back.ok === true, JSON.stringify(back).slice(0, 120));
    } else {
      check('找得到主选择组（三个特殊出口挂在它下面）', false, '没有主组');
    }

    /* ---------------- 收尾 ---------------- */
    section('收尾');
    if (!KEEP) {
      await commands.disconnect();
      const after = await commands.get_status();
      check('断开后状态归零', after.connected === false && after.core_alive === false, JSON.stringify(after));
      if (RESTORE) {
        console.log('  --restore 模式不动登录态（那是你自己登录的会话）');
      } else {
        await commands.logout();
        const st2 = await commands.get_settings();
        check('退出登录后 authed=false', st2.authed === false, JSON.stringify(st2.authed));
      }
    }
  } catch (e) {
    check('整体流程没抛异常', false, (e && e.stack) || String(e));
  }
  finish();

  function finish() {
    try { require('../electron/core/traffic').flush(); } catch (_) {}
    try { core.shutdown(); } catch (_) {}
    // 端口设置要还原：否则下一轮自检会捡起这个固定端口，
    // 一旦它被别的软件占着（真机 7890 = BettboxCore），自检会因为环境而红。
    try {
      const store = require('../electron/store');
      if (portBefore !== undefined && store.get('mixed_port') !== portBefore) {
        store.set('mixed_port', portBefore);
        console.log(`  已还原本机代理端口设置：${portBefore}`);
      }
    } catch (_) {}
    console.log(`\n结果：${pass} 通过 / ${fail} 失败`);
    if (failures.length) console.log('失败项:\n  - ' + failures.join('\n  - '));
    process.exit(fail ? 1 : 0);
  }
});