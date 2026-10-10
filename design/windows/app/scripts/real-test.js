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

// 数据层门禁验的是**首次运行的默认值**（用户第 1 条：TUN 堆栈默认 system），
// 而 store 的规则是「存过的值优先于默认值」（见 DEVNOTES A-33）：测试 data 目录里
// 可能留着上一次界面自检点过的 gvisor，于是这里报红——红的是脏数据，不是产品。
// 非 --restore（用给定账号真登录）且没指定 --data 时，先从干净设置开始。
if (!process.argv.includes('--restore') && !DATA_DIR) {
  try {
    const settingsPath = require('../electron/paths').file('settings.json');
    if (nodeFs.existsSync(settingsPath)) {
      nodeFs.rmSync(settingsPath, { force: true });
      console.log(`（已重置测试设置，本次跑的是首次运行默认值：${settingsPath}）`);
    }
  } catch (e) { console.log(`（重置测试设置失败：${e && e.message}）`); }
}

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
    // 用户第 5 条：自己的邮箱不打码（原来是 c*@… 那种掩码）
    check('登录态认出来了且邮箱不打码', st.authed === true && /@/.test(String(st.email)) && String(st.email).indexOf('*') < 0,
      JSON.stringify({ authed: st.authed, email: st.email }));
    if (EMAIL) check('邮箱就是登录用的那个', String(st.email) === String(EMAIL), `「${st.email}」vs「${EMAIL}」`);
    // 用户第 6 轮第 1 条：TUN 堆栈默认 system（Windows 上性能最好）。
    // 只在全新数据目录（--data 指向的空目录）里才有意义 —— 已有设置会被沿用。
    check('TUN 堆栈默认是 system（用户第 1 条）', st.tun === 'system', `当前「${st.tun}」`);

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

    // 用户第 6 轮第 6 条：流量页「面板累计」写成 0 B / 未订阅。
    // 根因是流量页自己又拼了一套口径（u.u + u.d、transfer_enable），和「我的」页
    // （get_plan）不同源 —— 面板一抖动就退化成"看起来没订阅"。这里锁死"同源"。
    const trafficToday = await commands.get_traffic({ range: 'today' });
    const planAgain = await commands.get_plan();
    check('流量页与「我的」页的已用流量同源（字节口径，不再显示 0 B）',
      trafficToday.account && trafficToday.account.used_text === planAgain.used_text,
      `流量「${trafficToday.account && trafficToday.account.used_text}」 vs 我的「${planAgain.used_text}」`);
    check('流量页与「我的」页的额度同源',
      trafficToday.account && trafficToday.account.quota_text === planAgain.total_text,
      `流量「${trafficToday.account && trafficToday.account.quota_text}」 vs 我的「${planAgain.total_text}」`);
    check('流量页的套餐名与「我的」页一致且不是「未订阅」',
      trafficToday.account && trafficToday.account.plan_name === planAgain.name && planAgain.name !== '未订阅',
      `「${trafficToday.account && trafficToday.account.plan_name}」`);
    check('账号用量是字节数（946 KB 这种小用量不会变成 0）',
      Number(trafficToday.account && trafficToday.account.used_bytes) > 0,
      `${trafficToday.account && trafficToday.account.used_bytes} 字节`);
    check('「本次峰值」「在线节点」两个字段已经不再下发给界面（用户第 6 条说不要）',
      !('peak' in trafficToday) && !('online_nodes' in trafficToday),
      Object.keys(trafficToday).join(','));

    // userInfo 的短缓存：45 秒内重复调用必须复用同一份数据（流量页/首页/我的页同时要），
    // 但**不能**把"未订阅"缓存住（一次抖动不该让界面一直是未订阅）。
    const client = require('../electron/panel/client');
    client.clearUserInfoCache();
    const u1 = await client.userInfo();
    const u2 = await client.userInfo();
    check('userInfo 有短缓存（同一份数据不重复打面板）', u1 === u2);
    client.clearUserInfoCache();
    const u3 = await client.userInfo();
    check('清掉缓存后会重新拉取', u3 !== u1 && u3.plan_name === u1.plan_name, `「${u3.plan_name}」`);
    check('真实面板拿到的套餐名不是「未订阅」', u3.plan_name !== '未订阅', `「${u3.plan_name}」`);

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

    /* ---- 用户第 8 条：节点顺序照网站下发，不能被重排 ---- */
    // 内核会把 include-all 的成员按名字排序（config.go 的 slices.Sort(AllProxies)），
    // 所以本地方案改成显式列节点。这里拿订阅原文的顺序与内核里分组的成员顺序逐项比。
    {
      const yaml = require('js-yaml');
      const mainName = require('../electron/core/rulesets').GROUP_SELECTOR;
      let want = [];
      try {
        const raw = yaml.load(nodeFs.readFileSync(nodePath.join(paths.profiles(), 'subscribe.yaml'), 'utf8')) || {};
        want = (Array.isArray(raw.proxies) ? raw.proxies : [])
          .map((p) => p && p.name)
          .filter((n) => n && !/剩余流量|到期|官网|expire/i.test(n));
      } catch (e) {
        console.log(`  （读订阅原文失败：${e.message}）`);
      }
      const groups = await commands.get_routing_groups();
      const main = (Array.isArray(groups) ? groups : []).find((g) => g.name === mainName);
      const members = main && Array.isArray(main.options) ? main.options.slice() : [];
      const structural = ['🚀 节点选择', '自动选择', '故障转移', 'DIRECT', 'REJECT'];
      const got = members.filter((n) => structural.indexOf(n) < 0);
      const inConfig = got.slice().sort();
      const wantInConfig = want.filter((n) => inConfig.indexOf(n) >= 0);
      check('节点顺序 = 网站下发的顺序（不是内核按名字排的）',
        want.length > 0 && JSON.stringify(got) === JSON.stringify(wantInConfig),
        `订阅前 4: ${want.slice(0, 4).join(' | ')} ／ 内核前 4: ${got.slice(0, 4).join(' | ')}`);
      check('分组里不是按名字排序的（第一项不是字节序最小的那个）',
        got.length > 1 && got[0] !== got.slice().sort()[0],
        `第一项=${got[0]} ／ 排序后第一项=${got.slice().sort()[0]}`);
    }

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

    /* ---------------- 内核操作串行化（连接与拉订阅不打架） ---------------- */
    // 真事故：开机后自动拉订阅和用户点的那次连接叠在一起跑。订阅刷新内部是
    // "断开 → 重连"，于是它把用户刚拉起来的内核拆掉，用户那次 connect 往已经死掉的
    // 控制面发请求 —— 日志里就是 `ERROR command connect failed: read ECONNRESET`
    // 紧跟 `mihomo exited`，最后内核是死的、延迟全空。这里故意把两个并发发出去。
    section('内核操作串行化（连接与拉订阅同时来）');
    {
      const logFile = nodePath.join(paths.logs(), 'polaris.log');
      const logBefore = nodeFs.existsSync(logFile) ? nodeFs.statSync(logFile).size : 0;
      await commands.disconnect();
      const pRefresh = commands.refresh_subscription();   // 先发拉订阅
      const pConnect = commands.connect();                // 紧接着发连接
      const rs = await Promise.allSettled([pRefresh, pConnect]);
      const bad = rs.filter((r) => r.status === 'rejected').map((r) => String(r.reason && r.reason.message));
      check('两个并发请求都不报错（不该出现 read ECONNRESET）',
        bad.length === 0, bad.join(' | ') || JSON.stringify(rs.map((r) => r.status)));
      const st2 = await commands.get_status();
      check('并发之后内核还活着（不是"连上了但其实已经死了"）', st2.connected === true, JSON.stringify(st2));
      let ver = '';
      try { ver = String((await core.S.controller.get('/version')).version || ''); } catch (e) { ver = 'ERR ' + e.message; }
      check('并发之后控制面真的能应答（/version）', !!ver && !/^ERR/.test(ver), ver);
      await new Promise((r) => setTimeout(r, 300));
      const tail2 = nodeFs.existsSync(logFile) ? nodeFs.readFileSync(logFile, 'utf8').slice(logBefore) : '';
      check('日志里没有 read ECONNRESET', !/read ECONNRESET/.test(tail2),
        (tail2.match(/.*read ECONNRESET.*/g) || []).join(' | '));
    }

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
      // 用户第 8 条：不能再用 include-all —— 内核合并 include-all 成员前会
      // slices.Sort(AllProxies)（config.go:943），顺序就不是网站下发的了。
      // 所以改成把节点显式写进 groups，顺序 = 订阅顺序（上面「订阅与节点」段已逐项比过）。
      const main = cfg['proxy-groups'][0] || {};
      const mainProxies = Array.isArray(main.proxies) ? main.proxies : [];
      check('主组前三个成员是「自动选择 / 故障转移 / DIRECT」（默认出口）',
        JSON.stringify(mainProxies.slice(0, 3)) === JSON.stringify([rulesets.GROUP_AUTO, rulesets.GROUP_FALLBACK, 'DIRECT']),
        JSON.stringify(mainProxies.slice(0, 5)));
      check('主组把节点显式写进去了（不是 include-all）',
        main['include-all'] !== true && mainProxies.length > 3, `include-all=${!!main['include-all']} 成员 ${mainProxies.length} 个`);
      check('所有组都不再使用 include-all（否则内核会按名字重排）',
        (cfg['proxy-groups'] || []).every((g) => g['include-all'] !== true),
        JSON.stringify((cfg['proxy-groups'] || []).filter((g) => g['include-all'] === true).map((g) => g.name)));
      check('兜底组只有 主组+DIRECT（刻意不给它节点）',
        JSON.stringify(((cfg['proxy-groups'].find((g) => g.name === rulesets.GROUP_FINAL) || {}).proxies) || []) ===
          JSON.stringify([rulesets.GROUP_SELECTOR, 'DIRECT']),
        JSON.stringify((cfg['proxy-groups'].find((g) => g.name === rulesets.GROUP_FINAL) || {}).proxies));

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

    /* ---------------- 端口占用与端口保存（用户第 9、10 条） ---------------- */
    section('端口占用与端口保存（用户第 9、10 条）');
    {
      const net = require('net');
      const { spawn } = require('child_process');
      const freePort = () => new Promise((resolve) => {
        const s = net.createServer();
        s.listen(0, '127.0.0.1', () => { const p = s.address().port; s.close(() => resolve(p)); });
      });

      const p1 = await freePort();
      const none = await commands.port_owner({ port: p1 });
      check('空闲端口上查不到占用者', !!none && none.busy === false, JSON.stringify(none));

      // 起一个"别的代理软件"占住端口（纯 listener，用 electron 的 node 模式跑）
      const dummy = spawn(process.execPath,
        ['-e', `require('net').createServer().listen(${p1},'127.0.0.1');setTimeout(()=>{},600000)`],
        { env: Object.assign({}, process.env, { ELECTRON_RUN_AS_NODE: '1' }), stdio: 'ignore', windowsHide: true });
      await new Promise((r) => setTimeout(r, 1500));
      const own = await commands.port_owner({ port: p1 });
      check('能查出占着端口的进程名和 PID',
        !!own && own.busy === true && Number(own.pid) === dummy.pid && !!own.name, JSON.stringify(own));

      const closed = await commands.close_port_owner({ pid: dummy.pid });
      check('请它退出之后它真的没了', !!closed && closed.closed === true, JSON.stringify(closed));
      const after = await commands.port_owner({ port: p1 });
      check('关掉之后端口就空出来了', !!after && after.busy === false, JSON.stringify(after));
      try { dummy.kill(); } catch (_) {}

      // 第 9 条：端口设好要长久保存，而且下次连接真的用它
      const fixed = await freePort();
      const sv = await commands.set_setting({ key: 'mixed_port', value: fixed });
      check('端口保存成功', !!sv && sv.ok === true && sv.mixed_port === fixed, JSON.stringify(sv));
      const st1 = await commands.get_settings();
      check('端口长久保存（重新读设置还在）', Number(st1.mixed_port) === fixed, `mixed_port=${st1.mixed_port}`);
      if ((await commands.get_status()).connected) await commands.disconnect();
      await commands.connect();
      const st2 = await commands.get_settings();
      check('连接后内核真的跑在这个端口上',
        Number(st2.running_port) === fixed, `running_port=${st2.running_port} 期望=${fixed}`);
      await commands.set_setting({ key: 'mixed_port', value: 0 });
    }

    /* ---------------- 规则库 24 小时自动更新（用户第 1 条） ---------------- */
    // 用户问「本地规则会不会按设计好的 24 小时更新」。配置里写着 interval=86400 不等于
    // 内核真的会去拉，这里做两件事：
    //   ① 把缓存文件 mtime 拨到 25 小时前，再重载配置 —— 内核的 Initial() 见到
    //      time.Since(mtime) > interval 就会立刻强制刷新（日志有 "not updated for a
    //      long time, force refresh"），这同时证明了「规则 CDN 真的能连上」；
    //   ② 刷新成功后 updatedAt 前进、文件 mtime 变成现在 —— 下一个 24 小时从这一刻算，
    //      不是从进程启动算，所以重启不会打断计时。
    section('规则库 24 小时自动更新（计时锚点 + 真拉一次）');
    {
      const rulesets = require('../electron/core/rulesets');
      const yaml2 = require('js-yaml');
      const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
      // 下面要真重载内核配置，先确保内核在跑
      if (!(await commands.get_status()).connected) { await commands.connect(); await sleep(2500); }
      check('内核在跑（下面要真重载配置、真拉规则）', (await commands.get_status()).connected);
      const cacheDir = paths.polarisRules();
      const cfg2 = yaml2.load(nodeFs.readFileSync(paths.file('config.yaml'), 'utf8')) || {};
      const rp = cfg2['rule-providers'] || {};
      const keys2 = Object.keys(rp);

      check('config 里每个 rule-provider 都是 http + 24 小时',
        keys2.length > 0 && keys2.every((k) => rp[k].type === 'http' && Number(rp[k].interval) === rulesets.PROVIDER_INTERVAL),
        `${keys2.length} 个，interval=${rulesets.PROVIDER_INTERVAL}`);
      check('每个 provider 的缓存路径都在 data/polaris-rules/ 里（随包种子就铺在这里）',
        keys2.every((k) => String(rp[k].path || '').startsWith(`${rulesets.CACHE_DIR}/`)),
        keys2.slice(0, 2).map((k) => rp[k].path).join(' , '));
      check('所有 provider 的 url 都是 https 的规则 CDN',
        keys2.every((k) => /^https:\/\//.test(String(rp[k].url || ''))),
        keys2.slice(0, 2).map((k) => rp[k].url).join(' , '));

      // 日志读取：只看这一段之后新增的内容
      const logFile = nodePath.join(paths.logs(), 'polaris.log');
      const logSize = () => { try { return nodeFs.statSync(logFile).size; } catch (_) { return 0; } };
      const logFrom = (off) => {
        try {
          const st = nodeFs.statSync(logFile);
          if (st.size <= off) return '';
          const fd = nodeFs.openSync(logFile, 'r');
          const buf = Buffer.alloc(st.size - off);
          nodeFs.readSync(fd, buf, 0, buf.length, off);
          nodeFs.closeSync(fd);
          return buf.toString('utf8');
        } catch (_) { return ''; }
      };
      const provNow = async () => {
        try {
          const raw = await core.S.controller.get('/providers/rules');
          return (raw && raw.providers && typeof raw.providers === 'object') ? raw.providers : raw;
        } catch (_) { return null; }
      };

      const probeKeys = keys2.slice(0, 3);
      const old = (Date.now() - 25 * 3600 * 1000) / 1000;
      const probeFiles = probeKeys.map((k) => nodePath.join(cacheDir, rulesets.seedFile(k)));
      for (const f of probeFiles) { if (nodeFs.existsSync(f)) nodeFs.utimesSync(f, old, old); }
      const p0 = await provNow();
      const at0 = probeKeys.map((k) => String((p0 && p0[k] && p0[k].updatedAt) || ''));

      const mark = logSize();
      await core.reloadConfig();
      await sleep(8000);
      const text = logFrom(mark);
      const forced = /not updated for a long time, force refresh/.test(text);
      check('缓存超过 24 小时 → 内核立刻强制刷新（日志为证）', forced,
        forced ? (text.split('\n').find((l) => l.includes('force refresh')) || '').slice(0, 160)
          : `日志里没有 force refresh（可能是规则 CDN 连不上）: ${text.split('\n').filter((l) => l.includes('[Provider]')).slice(0, 3).join(' | ').slice(0, 200)}`);
      const pullErr = text.split('\n').filter((l) => l.includes('[Provider]') && l.includes('pull error'));
      check('这次强制刷新没有报拉取失败（规则 CDN 可达）', pullErr.length === 0,
        pullErr.slice(0, 2).map((l) => l.slice(0, 160)).join(' | '));

      const p1 = await provNow();
      const at1 = probeKeys.map((k) => String((p1 && p1[k] && p1[k].updatedAt) || ''));
      check('刷新后 updatedAt 前进到最近（说明真的走了一次在线拉取）',
        probeKeys.every((k, i) => at1[i] && Date.parse(at1[i]) > Date.parse(at0[i] || 0)),
        `${at0.join(' , ')} → ${at1.join(' , ')}`);
      check('刷新后缓存文件 mtime 是现在（下一个 24 小时从这一刻起算）',
        probeFiles.every((f) => nodeFs.existsSync(f) && Date.now() - nodeFs.statSync(f).mtimeMs < 180000),
        probeFiles.map((f) => (nodeFs.existsSync(f) ? `${nodeFs.statSync(f).mtimeMs.toFixed(0)}` : 'missing')).join(' , '));
      check('拉回来之后规则集仍然有效（ruleCount > 0）',
        probeKeys.every((k) => p1 && p1[k] && Number(p1[k].ruleCount) > 0),
        JSON.stringify(probeKeys.map((k) => [k, p1 && p1[k] && p1[k].ruleCount])));

      // 随包种子只补缺失：内核拉回来的新版不会被我们下次启动覆盖
      const oneKey = probeKeys[0];
      const oneFile = nodePath.join(cacheDir, rulesets.seedFile(oneKey));
      const seedFile = nodePath.join(paths.rules(), rulesets.seedFile(oneKey));
      if (nodeFs.existsSync(oneFile) && nodeFs.existsSync(seedFile)) {
        const a = nodeFs.readFileSync(oneFile);
        const b = nodeFs.readFileSync(seedFile);
        console.log(`  ${oneKey}: 在线版 ${a.length} 字节 / 随包种子 ${b.length} 字节${a.equals(b) ? '（内容相同）' : '（在线版已覆盖种子）'}`);
        check('在线拉回来的文件和随包种子都在，且内核用的是缓存目录那份', a.length > 0 && b.length > 0);
      }
    }

    /* ---------------- 分流落点实测（用户第 1 条：分流能不能正确地分） ---------------- */
    // 光看规则表看不出「到底分对没分对」——必须真的把请求发出去，再读内核日志里
    // 那一行 `match RuleSet(<key>) using <组名>[<出口>]`，逐组核对落点。
    // 代表域名直接从每个组的规则集文件里取（所以在线更新过的新规则也一起被验到）。
    section('分流落点实测（每个启用的分流组真发一次请求）');
    if (main) {
      const rulesets = require('../electron/core/rulesets');
      const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
      const logFile = nodePath.join(paths.logs(), 'polaris.log');
      const logSize = () => { try { return nodeFs.statSync(logFile).size; } catch (_) { return 0; } };
      const logFrom = (off) => {
        try {
          const st = nodeFs.statSync(logFile);
          if (st.size <= off) return '';
          const fd = nodeFs.openSync(logFile, 'r');
          const buf = Buffer.alloc(st.size - off);
          nodeFs.readSync(fd, buf, 0, buf.length, off);
          nodeFs.closeSync(fd);
          return buf.toString('utf8');
        } catch (_) { return ''; }
      };
      // 从规则集文件里挑「像正经域名」的条目。两种文件格式都要认：
      //   · behavior=classical/text（acl_*）：DOMAIN,x / DOMAIN-SUFFIX,x 一行一条
      //   · behavior=domain（gs_*，geosite yaml）：payload 里是纯域名，可能带 +. 前缀
      const domainsOf = (key) => {
        const f = nodePath.join(paths.polarisRules(), rulesets.seedFile(key));
        let txt = '';
        try { txt = nodeFs.readFileSync(f, 'utf8'); } catch (_) { return []; }
        const exact = [];
        const suffix = [];
        const push = (raw, kind) => {
          let d = String(raw).trim().toLowerCase().replace(/^['"]|['"]$/g, '');
          if (d.startsWith('+.')) d = d.slice(2);
          if (!d || d.includes('*') || d.includes(' ') || d.length < 5 || !d.includes('.')) return;
          if (/^\d/.test(d) || !/^[a-z0-9.-]+$/.test(d)) return;
          (kind === 'exact' ? exact : suffix).push(d);
        };
        let m;
        const reClassical = /^\s*-?\s*(DOMAIN|DOMAIN-SUFFIX),(.+?)\s*$/gm;
        while ((m = reClassical.exec(txt))) push(m[2], m[1] === 'DOMAIN' ? 'exact' : 'suffix');
        const reDomain = /^\s*-\s*([a-z0-9][a-z0-9.+-]*\.[a-z]{2,})\s*$/gmi;
        while ((m = reDomain.exec(txt))) push(m[1], 'suffix');
        const uniq = (a) => a.filter((d, i) => a.indexOf(d) === i);
        return uniq(exact).slice(0, 2).concat(uniq(suffix).slice(0, 2));
      };
      // 内核日志里同一件事有两种写法：
      //   · 建连成功：--> host:80 match RuleSet(gs_x) using 组名[出口]
      //   · 拨号失败（直连组 DNS 解析不了等）：dial 组名 (match RuleSet/gs_x) 127.0.0.1:xx --> host:80 error: ...
      const matchOf = (text, host) => {
        const esc = host.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        let m = new RegExp(`-->\\s+${esc}:\\d+\\s+match\\s+(.+?)\\s+using\\s+(.+?)\\[`).exec(text);
        if (m) return { rule: m[1], group: m[2] };
        m = new RegExp(`dial\\s+(.+?)\\s+\\(match\\s+(.+?)\\)[^\\n]*-->\\s+${esc}:\\d+`).exec(text);
        if (m) return { rule: m[2], group: m[1] };
        return null;
      };

      const state = await core.rulesetState();
      const enabled = state.groups.filter((g) => g.enabled);
      console.log(`  启用的分流组 ${enabled.length} 个 / 内置共 ${state.total} 个`);
      check('内置分流表里已经没有 Google FCM 与苹果推送通知两个组',
        !state.groups.some((g) => /FCM|推送通知/.test(g.name)),
        state.groups.map((g) => g.name).join(' | ').slice(0, 120));

      // 组的出口语义（proxy / direct / block）：判断"被别的组抢走"到底算不算错。
      // 同出口（都是 proxy）只是归类不同，不影响走不走代理；出口不同就是真错了
      // —— 用户报的 Google FCM 就是这个：写着直连，实际被前面的 Google 组抢去走代理。
      const outMap = new Map(state.groups.map((g) => [g.name, g.out]));
      const outOf = (n) => outMap.get(n) || 'proxy';
      const shadowed = [];

      let hit = 0;
      let noDomain = 0;
      for (const g of enabled) {
        const def = rulesets.TABLE.find((x) => x.name === g.name);
        const keys = def ? rulesets.providerKeys(def) : [];
        let doms = [];
        for (const k of keys) { doms = doms.concat(domainsOf(k)); }
        doms = doms.filter((d, i) => doms.indexOf(d) === i).slice(0, 3);
        if (!doms.length) {
          // 纯 IP 段的组（gp_*）没有域名可挑，跳过但说清楚
          noDomain += 1;
          console.log(`  ${g.name}: 该组只有 IP 段规则，跳过域名落点测试`);
          continue;
        }
        let found = null;
        let other = null;   // 被别的组抢走了（规则遮蔽），失败时要能说清楚是谁抢的
        for (const d of doms) {
          const off = logSize();
          // 匹配行是请求一进来就打出来的，不用等请求真的通 —— 2 秒足够
          try { await httpThroughProxy(core.mixedPort(), `http://${d}/`, 2000); } catch (_) { /* 连不上也要看日志里的匹配行 */ }
          await sleep(350);
          const mm = matchOf(logFrom(off), d);
          if (!mm) continue;
          if (mm.group === g.name) { found = { domain: d, ...mm }; break; }
          if (!other) other = { domain: d, ...mm };
        }
        if (found) {
          hit += 1;
          check(`「${g.name}」的域名真的落在它自己这个组`, true, `${found.domain} → ${found.rule} → ${found.group}`);
        } else if (other) {
          const sameOut = outOf(other.group) === outOf(g.name);
          if (sameOut) {
            hit += 1;
            shadowed.push(`${g.name} ← ${other.group}（同为 ${outOf(g.name)}）`);
            check(`「${g.name}」的代表域名被同出口的「${other.group}」先命中（只是归类不同，不影响分流结果）`,
              true, `${other.domain} → ${other.rule} → ${other.group}`);
          } else {
            check(`「${g.name}」的域名真的落在它自己这个组`, false,
              `被出口不同的「${other.group}」（${outOf(other.group)}）抢走了，本该是 ${outOf(g.name)}：${other.domain} → ${other.rule}`);
          }
        } else {
          check(`「${g.name}」的域名真的落在它自己这个组`, false,
            `试过 ${doms.join(', ')}，日志里没看到匹配行`);
        }
      }
      check('至少 8 个组完成了落点实测（不是空跑）', hit >= 8, `实测到落点的组 ${hit} 个`);
      if (shadowed.length) {
        console.log(`  规则遮蔽（同出口，无害但值得知道）：${shadowed.join(' / ')}`);
      }

      // 兜底：没被任何规则命中的域名必须落到「🐟 漏网之鱼」
      {
        const host = `polaris-probe-${Date.now().toString(36)}.invalid`;
        const off = logSize();
        try { await httpThroughProxy(core.mixedPort(), `http://${host}/`, 6000); } catch (_) {}
        await sleep(400);
        const mm = matchOf(logFrom(off), host);
        check('没命中任何规则的域名落到「🐟 漏网之鱼」（末尾 MATCH 真的在）',
          !!mm && mm.rule === 'Match' && mm.group === builder.FINAL_GROUP,
          mm ? `${mm.rule} → ${mm.group}` : '日志里没有匹配行');
      }

      // 苹果推送：合并进「🍎 苹果服务」后，内联规则必须指向该组并且真能命中
      {
        const off0 = logSize();
        await commands.set_ruleset({ name: '🍎 苹果服务', on: true });
        await sleep(1500);
        const yaml3 = require('js-yaml');
        const cfg3 = yaml3.load(nodeFs.readFileSync(paths.file('config.yaml'), 'utf8')) || {};
        const rules3 = Array.isArray(cfg3.rules) ? cfg3.rules : [];
        const push = rules3.filter((r) => String(r).includes('push.apple.com') || String(r).includes('akadns.net'));
        check('苹果推送的 12 条内联规则挂在「🍎 苹果服务」组下',
          push.length === 2 && push.every((r) => String(r).endsWith(',🍎 苹果服务')),
          push.join(' | '));
        const off = logSize();
        try { await httpThroughProxy(core.mixedPort(), 'http://push.apple.com/', 6000); } catch (_) {}
        await sleep(400);
        const mm = matchOf(logFrom(off), 'push.apple.com');
        check('push.apple.com 真的走「🍎 苹果服务」（不再有单独一组，也没被别组抢走）',
          !!mm && mm.group === '🍎 苹果服务',
          mm ? `${mm.rule} → ${mm.group}` : '日志里没有匹配行');
        await commands.set_ruleset({ name: '🍎 苹果服务', on: false });
        await sleep(1200);
        void off0;
      }
    }

    /* ---------------- 全功能覆盖（用户第 4 条：不能只测固定的那几项） ---------------- */
    // 把 IPC 面上还没被真调用过的命令逐条走一遍。破坏性的（真下单、真支付、
    // 真下载更新包并自我替换、提权重启、TUN 接管网络）**不做**，只做只读或
    // 自还原的调用 + 错误路径，并在报告里说明为什么跳过。
    section('全功能覆盖（其余命令逐条真调用）');
    {
      const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
      const yaml = require('js-yaml');
      {
        const seen = [];
        for (const m of ['global', 'direct', 'rule']) {
          const r = await commands.set_proxy_mode({ mode: m });
          await sleep(300);
          let now = '';
          try { now = String((await core.S.controller.get('/configs')).mode || ''); } catch (_) {}
          seen.push(`${m}→${(r && r.mode) || '?'}/${now}`);
        }
        check('代理模式三种都能切，且内核里真的换了',
          seen.every((x) => !/→\?\/$/.test(x)) && /rule/i.test(seen[2]), seen.join(' , '));
      }

      // ② 策略组出口：改一个组的出口再读回来，然后恢复默认
      {
        const groups = (await commands.get_routing_groups()) || [];
        const target = groups.find((g) => !g.builtin && !g.structural && Array.isArray(g.options) && g.options.length > 1);
        if (target) {
          const pickNode = target.options.find((n) => n !== target.now) || target.options[0];
          const r = await commands.set_routing_group({ name: target.name, node: pickNode });
          await sleep(500);
          const after = ((await commands.get_routing_groups()) || []).find((g) => g.name === target.name);
          check(`策略组出口能改（${target.name} → ${pickNode}）`,
            !!after && after.now === pickNode, `改完读到的是 ${after && after.now}`);
          const rr = await commands.reset_routing_groups();
          check('「恢复默认」能把它还原', !!rr && rr.ok !== false, JSON.stringify(rr));
        } else {
          check('策略组出口能改（没找到可改的组，跳过）', false, '没有非结构组');
        }
      }

      // ③ 自定义分流组：加一个 → 配置里真的有 → 删掉 → 配置里没了
      {
        const name = `测试分流组-${Date.now().toString(36).slice(-4)}`;
        const add = await commands.save_custom_ruleset({
          name, out: 'direct', rules: ['DOMAIN-SUFFIX,polaris-custom-probe.example', 'DOMAIN-KEYWORD,polarisprobe'],
        });
        check('能新建自定义分流组', !!add && add.ok === true && add.name === name, JSON.stringify(add));
        await sleep(1200);
        const cfgC = yaml.load(nodeFs.readFileSync(paths.file('config.yaml'), 'utf8')) || {};
        const hasGroup = (cfgC['proxy-groups'] || []).some((g) => g.name === name);
        const hasRule = (cfgC.rules || []).some((r) => String(r).includes('polaris-custom-probe.example'));
        check('新建的自定义分流组和它的规则真的进了内核配置', hasGroup && hasRule, `组=${hasGroup} 规则=${hasRule}`);
        const bad = await commands.save_custom_ruleset({ name: '🚀 节点选择', out: 'proxy', rules: ['DOMAIN-SUFFIX,x.com'] })
          .then(() => null).catch((e) => e.message);
        check('拿保留名建组会被拒绝（不会顶掉内置组）', !!bad && /保留名|已经/.test(bad), String(bad));
        const del = await commands.delete_custom_ruleset({ name });
        check('能删掉自定义分流组', !!del && del.ok === true, JSON.stringify(del));
        await sleep(1200);
        const cfgD = yaml.load(nodeFs.readFileSync(paths.file('config.yaml'), 'utf8')) || {};
        check('删掉之后配置里也没有了',
          !(cfgD['proxy-groups'] || []).some((g) => g.name === name));
      }

      // ④ 本地分流总开关：关掉→面板方案，打开→本地方案
      {
        const off = await commands.set_local_routing({ on: false });
        await sleep(1500);
        const cfgOff = yaml.load(nodeFs.readFileSync(paths.file('config.yaml'), 'utf8')) || {};
        const panelish = (cfgOff['proxy-groups'] || []).some((g) => /Instagram|Facebook|WhatsApp/.test(g.name));
        check('关掉「本地分流」后内核用的是面板下发的分组', !!off && off.on === false && panelish,
          `on=${off && off.on} 面板组在=${panelish}`);
        const on = await commands.set_local_routing({ on: true });
        await sleep(1500);
        const cfgOn = yaml.load(nodeFs.readFileSync(paths.file('config.yaml'), 'utf8')) || {};
        const localish = (cfgOn['proxy-groups'] || []).some((g) => g.name === builder.FINAL_GROUP);
        check('再打开又回到本地方案', !!on && on.on === true && localish,
          `on=${on && on.on} 兜底组在=${localish}`);
      }

      // ⑤ 本机采样曲线（流量页画图用的那份数据）
      {
        const series = await commands.get_traffic_series({ range: 'today' });
        check('本机采样曲线能取到（数组 + 有 unit）',
          !!series && Array.isArray(series.points) && typeof series.unit === 'string',
          JSON.stringify({ points: series && series.points && series.points.length, unit: series && series.unit }));
      }

      // ⑥ 注册 / 找回 / 发验证码 / 改密码：只走"会被拒绝"的路，避免真改账号
      //    注意这几个命令在 ipc 里是 `return fail(msg)` 而不是抛错 —— 两种形状都要认。
      {
        const asFail = (r) => (r && typeof r === 'object' ? `${r.ok === false ? 'FAIL' : 'OK'}:${r.msg || ''}` : String(r));
        const regR = await commands.register({ email: EMAIL, password: 'x'.repeat(10), code: '', invite: '' })
          .then(asFail).catch((e) => `THROW:${e.message}`);
        check('拿已注册的邮箱注册会被明确拒绝（不是崩）',
          /^(FAIL|THROW):/.test(regR) && regR.length > 6, regR.slice(0, 90));

        const codeR = await commands.send_email_code({ email: `nobody-${Date.now().toString(36)}@example.invalid`, purpose: 'register' })
          .then(asFail).catch((e) => `THROW:${e.message}`);
        check('给不存在的邮箱发验证码会给出人话错误（不是崩、也不是假装成功）',
          /^(FAIL|THROW):/.test(codeR) && codeR.length > 6, codeR.slice(0, 90));

        const forgotR = await commands.forgot_password({ email: `nobody-${Date.now().toString(36)}@example.invalid`, code: '000000', password: 'x'.repeat(10) })
          .then(asFail).catch((e) => `THROW:${e.message}`);
        check('找回密码用错验证码会被拒绝（不会真改密码）',
          /^(FAIL|THROW):/.test(forgotR) && forgotR.length > 6, forgotR.slice(0, 90));

        const pwdR = await commands.change_password({ old_password: 'definitely-wrong-old', new_password: 'whatever12345' })
          .then(asFail).catch((e) => `THROW:${e.message}`);
        check('改密码用错旧密码会被拒绝（不会真改密码）',
          /^(FAIL|THROW):/.test(pwdR) && pwdR.length > 6, pwdR.slice(0, 90));

        const stillOk = (await commands.get_settings()).authed;
        check('上面几次失败之后会话仍然有效（没被踢下线）', stillOk === true, String(stillOk));
      }

      // ⑦ 支付方式 / 礼品卡 / 应用信息 / 管理员
      {
        const pm = await commands.get_payment_methods();
        check('支付方式接口能返回数组（面板没配就是空数组）', Array.isArray(pm), JSON.stringify(pm).slice(0, 80));
        const gh = await commands.get_gift_history();
        check('礼品卡历史能返回数组（面板没这个接口就是空数组）', Array.isArray(gh), JSON.stringify(gh).slice(0, 60));
        const bad1 = await commands.redeem_gift({ code: '123' });
        check('礼品卡填格式不对的卡密会被挡下（不发请求）',
          !!bad1 && bad1.ok === false && /格式/.test(String(bad1.msg)), JSON.stringify(bad1));
        const bad2 = await commands.redeem_gift({ code: `POLARIS-PROBE-${Date.now().toString(36)}` });
        check('礼品卡填一个不存在的卡密会给出面板的错误（不崩）',
          !!bad2 && bad2.ok === false && !!bad2.msg, JSON.stringify(bad2).slice(0, 100));
        const app = await commands.get_app_info();
        // version 取自 store（真跑应用时由 main.js 落盘）；这个自检的入口是
        // scripts/real-test.js，没走 main.js，所以 version 允许是空串。
        check('应用信息能读到（数据目录 / 便携标记 / 运行时版本）',
          !!app && typeof app.data_dir === 'string' && app.data_dir.length > 0
            && typeof app.portable === 'boolean' && !!app.electron && !!app.node,
          JSON.stringify({ version: app && app.version, portable: app && app.portable, electron: app && app.electron, node: app && app.node }).slice(0, 130));
        const adm = await commands.is_admin();
        check('管理员判定返回布尔', typeof adm === 'boolean', String(adm));
      }

      // ⑧ TUN 状态与残留清理（不改网络：TUN 没开的时候清理是无害的）
      {
        const t = await commands.get_tun_status();
        check('TUN 状态能读到（网卡名 / 是否残留）', !!t && typeof t === 'object', JSON.stringify(t).slice(0, 120));
        const c = await commands.cleanup_tun();
        check('清理 TUN 残留不会抛异常（只动名为 Polaris 的网卡）', !!c, JSON.stringify(c).slice(0, 100));
      }

      // ⑨ 更新链路：只查、不下载、不自我替换
      {
        const u = await commands.check_update();
        check('检查更新能返回结果（有/无新版本都算通过）',
          !!u && typeof u.has_update === 'boolean', JSON.stringify(u).slice(0, 120));
        const st = await commands.get_update_state();
        // updater.info() 的形状是 {phase, version, url, file, received, total, percent, ...}
        check('更新状态能读到（phase + 进度 + 安装目录）',
          !!st && typeof st.phase === 'string' && typeof st.percent === 'number' && typeof st.install_dir === 'string',
          JSON.stringify(st).slice(0, 140));
        const d = await commands.discard_update();
        check('丢弃更新包不会抛异常（没有暂存时就是空操作）', !!d, JSON.stringify(d).slice(0, 80));
        console.log('  说明：download_update / apply_update 会真下 186MB 并真替换程序本体，按用户规矩不自动跑');
      }

      // ⑩ 打开外部链接：只验校验逻辑（不真开浏览器）
      {
        const bad = await commands.open_external({ url: 'file:///C:/Windows/System32/calc.exe' })
          .then(() => '').catch((e) => e.message);
        check('open_external 只放行 http(s)（file:// 被挡下）', !!bad && /http/.test(bad), String(bad));
        console.log('  说明：open_external / open_download / open_telegram 的"真打开"会弹系统浏览器，留给真人体验；export_logs 会弹目录选择框，无头跑会卡住，也留给真人');
      }
    }

    /* ---------------- 面板拨号健壮性（用户第 9 轮第 3 条） ---------------- */
    // 现象：面板域名解析出来的地址里有黑洞（实测某面板的域名解出两条 SYN 无应答的
    // 地址），谁先连它谁就卡到 20s，
    // 界面上是「网络错误：面板请求超时」。这里验证 client.js 的三层兜底。
    section('面板拨号健壮性（黑洞地址 / 换地址重试）');
    {
      const c = require('../electron/panel/client');
      const dns = require('dns');
      const BLACKHOLE = '203.0.113.1';   // RFC 5737 保留段，永远连不上（只在自检里用）

      // 1) 纯函数：坏地址记忆 + 排序
      c.clearBadAddresses();
      c.markBadAddr(BLACKHOLE);
      check('连不上的地址会被记住', c.isBadAddr(BLACKHOLE) === true);
      const ordered = c.orderAddresses([
        { address: BLACKHOLE, family: 4 },
        { address: '198.51.100.9', family: 4 },
      ]).map((a) => a.address);
      check('已知连不上的地址排在最后（只降级不排除）',
        ordered[0] === '198.51.100.9' && ordered[1] === BLACKHOLE, JSON.stringify(ordered));
      c.clearBadAddresses();
      check('清空后不再认为是坏地址', c.isBadAddr(BLACKHOLE) === false);
      check('坏地址记忆有有效期（5 分钟）', c.BAD_ADDR_TTL === 5 * 60 * 1000, String(c.BAD_ADDR_TTL));

      // 2) 解析要同时走 getaddrinfo 与 c-ares（本机实测两者答案不同：
      //    getaddrinfo 给黑洞，c-ares 给真地址）。把 getaddrinfo 换成只回黑洞，
      //    合并结果里必须还有别的地址，否则这一层兜底就是假的。
      const origLookup = dns.lookup;
      try {
        dns.lookup = function (host, opts, cb) {
          if (typeof opts === 'function') { cb = opts; opts = {}; }
          if (opts && opts.all) return cb(null, [{ address: BLACKHOLE, family: 4 }]);
          return cb(null, BLACKHOLE, 4);
        };
        let merged = [];
        try { merged = await c.resolveAll(new URL(PANEL).hostname); } catch (_) { merged = []; }
        const addrs = merged.map((a) => a.address);
        check('getaddrinfo 只回黑洞时，合并结果里还有其它地址（c-ares 那条路）',
          addrs.length > 0 && addrs.some((a) => a !== BLACKHOLE), JSON.stringify(addrs));
      } finally {
        dns.lookup = origLookup;
      }

      // 3) 第三条路：本机 DNS 会间歇性只回黑洞（实测整整 20s 都只回那两个地址，
      //    应用就报「面板请求超时」）。这时要能直接问公共 DNS 拿真地址。
      let pub = [];
      try { pub = await c.resolveViaPublic(new URL(PANEL).hostname); } catch (_) { pub = []; }
      check('本机 DNS 不行时，公共 DNS 能解析出地址（最后一道兜底）',
        pub.length > 0, JSON.stringify(pub.map((a) => a.address).slice(0, 4)) + ` 服务器=${JSON.stringify(c.PUBLIC_DNS)}`);
      const mergedPub = await c.resolveAll(new URL(PANEL).hostname, { public: true }).catch(() => []);
      check('公共 DNS 的答案会并进地址表，且排在最前面（本机 DNS 已经失败过）',
        mergedPub.length > 0 && pub.some((a) => a.address === mergedPub[0].address),
        JSON.stringify(mergedPub.map((a) => a.address).slice(0, 4)));

      // 4) 解析本身不能挂住：本机路由器偶尔让 DNS 请求永远不回（实测一批面板请求
      //    全停在"地址还没解析出来"，日志只能写"试过 未知地址"）。三条路都不回话时，
      //    resolveAll 必须在 DNS_TIMEOUT 量级内给出结论，而不是让整个请求干等。
      const origLookup2 = dns.lookup;
      const origResolve4 = dns.resolve4;
      const origResolve6 = dns.resolve6;
      const hang = () => { /* 永远不回调 */ };
      try {
        dns.lookup = hang; dns.resolve4 = hang; dns.resolve6 = hang;
        const t1 = Date.now();
        let msg = '';
        try { await c.resolveAll(new URL(PANEL).hostname); } catch (e) { msg = String(e && e.message); }
        const took = Date.now() - t1;
        check('DNS 挂住时解析会自己收尾（不会无限等）', took < 6000 && /DNS 没有解析出地址/.test(msg),
          `${took}ms ${msg}`);
      } finally {
        dns.lookup = origLookup2; dns.resolve4 = origResolve4; dns.resolve6 = origResolve6;
      }

      // 面板答复了 4xx 就说明"连得上、只是面板不认这个请求"，绝不能换地址重试
      // （曾经因为响应路径的 reject 没盖 phase 章，把 HTTP 400 也重试了 3 次）。
      const logFile = nodePath.join(paths.logs(), 'polaris.log');
      const logBefore = nodeFs.existsSync(logFile) ? nodeFs.statSync(logFile).size : 0;
      let httpCode = '';
      try { await c.request('POST', '/user/changePassword', { body: { old_password: 'wrong-xyz', new_password: 'Abc123456!' } }); }
      catch (e) { httpCode = e.code || e.message; }
      await new Promise((r) => setTimeout(r, 300));
      const logTail = nodeFs.existsSync(logFile) ? nodeFs.readFileSync(logFile, 'utf8').slice(logBefore) : '';
      check('面板回了 4xx 不当成连接失败去换地址重试（日志里不该出现"换地址重试"）',
        httpCode !== '' && !/changePassword 连接失败[\s\S]*换地址重试/.test(logTail), `错误码=${httpCode}`);

      // 4) 真实请求：面板域名现在就有黑洞地址，请求必须还能成
      const url = new URL(PANEL);
      c.session.panelUrl = url.origin;
      c.clearBadAddresses();
      const t0 = Date.now();
      let okReq = false;
      let reqErr = '';
      try {
        await c.request('GET', '/guest/comm/config', { noAuth: true, timeoutMs: 20000 });
        okReq = true;
      } catch (e) { reqErr = `${e.kind}: ${e.message}`; }
      const ms = Date.now() - t0;
      check('面板域名带黑洞地址时，请求照样成功（不是死等 20s 超时）', okReq, reqErr || `${ms}ms`);
      console.log(`  实测耗时 ${ms}ms，连不上名单 = ${JSON.stringify(c.badAddresses())}`);
      c.clearBadAddresses();
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