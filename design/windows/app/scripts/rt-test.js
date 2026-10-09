'use strict';
/**
 * 运行时功能测试（不是「元素存不存在」，是真的把应用跑起来用一遍）
 *
 * 方法：真实界面操作 → 真 IPC → 真内核，每一步都从**内核回读**校验，
 * 不信界面上的乐观更新。最后一节是长时间保持连接的浸泡采样，
 * 用来对照安卓端「长后台断网降速」那类问题。
 *
 * 用法：
 *   Polaris.exe --rttest                 只跑功能（约 3-5 分钟）
 *   Polaris.exe --rttest --soak 40       功能 + 40 分钟浸泡
 *   Polaris.exe --rttest --keep          结束时不断开（默认会断开并把系统代理还原干净）
 *
 * 报告写到 <data>/rt-report.txt，浸泡采样逐条追加到 <data>/rt-samples.jsonl。
 */

const fs = require('fs');
const net = require('net');
const { execFileSync, spawn } = require('child_process');
const paths = require('../electron/paths');
const core = require('../electron/core/manager');
const mockPanel = require('./mock-panel');
const sysproxy = require('../electron/net/sysproxy');
const ipc = require('../electron/ipc');

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/* ---------------- 报告器 ---------------- */
function makeReporter(log) {
  const r = { pass: 0, fail: 0, failures: [], steps: [], notes: [] };
  return {
    check(name, cond, detail) {
      if (cond) { r.pass++; r.steps.push(`  ✓ ${name}`); }
      else { r.fail++; r.failures.push(name + (detail ? ` :: ${detail}` : '')); r.steps.push(`  ✗ ${name}${detail ? ` :: ${detail}` : ''}`); }
      if (detail && cond) r.steps.push(`      ${detail}`);
      return !!cond;
    },
    note(msg) { r.notes.push(msg); r.steps.push(`  · ${msg}`); log(`  · ${msg}`); },
    section(t) { r.steps.push(`\n【${t}】`); log(`\n【${t}】`); },
    result() { return r; },
  };
}

/* ---------------- 内核回读工具 ---------------- */
async function controllerOf() {
  const S = core.S;
  if (!S.controller) throw new Error('内核控制面还没起来');
  return S.controller;
}
function portOpen(port, timeoutMs = 800) {
  return new Promise((resolve) => {
    const s = net.connect({ host: '127.0.0.1', port });
    const done = (v) => { try { s.destroy(); } catch (_) {} resolve(v); };
    s.setTimeout(timeoutMs, () => done(false));
    s.once('connect', () => done(true));
    s.once('error', () => done(false));
  });
}
/** 从内核 /connections 里找一条刚发起的连接，读它的 rule 与 chains
 *  excludeId：同一域名第二次抓连接时要排掉上一条（内核的连接表里旧连接还在，
 *  否则会抓到切换模式之前那条，报出"切了全局还走直连"这种假 bug） */
async function findConnection(hostPart, timeoutMs = 8000, excludeId = null) {
  const c = await controllerOf();
  const t0 = Date.now();
  while (Date.now() - t0 < timeoutMs) {
    try {
      const data = await c.get('/connections');
      const list = (data && data.connections) || [];
      const hit = list.find((x) => ((x.metadata && (x.metadata.host || x.metadata.destinationIP)) || '').includes(hostPart)
        && (!excludeId || x.id !== excludeId));
      if (hit) return hit;
    } catch (_) {}
    await sleep(250);
  }
  return null;
}
/** 起一个真实的外网请求（走代理），返回 unref 的句柄供调用方 kill */
function curlAsync(args) {
  const p = spawn('curl.exe', ['-s', '--noproxy', '', ...args], { windowsHide: true, stdio: 'ignore' });
  p.on('error', () => {});
  return p;
}
function curlSync(args, timeoutMs = 20000) {
  return execFileSync('curl.exe', ['-s', '--noproxy', '', ...args],
    { encoding: 'utf8', timeout: timeoutMs, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
}
/** 进程指标（mihomo / 或任意 pid）：工作集、线程数、句柄数 */
function procMetrics(pid) {
  try {
    const out = execFileSync('powershell',
      ['-NoProfile', '-NonInteractive', '-Command',
        `$p=Get-Process -Id ${pid} -ErrorAction SilentlyContinue; if($p){'{0} {1} {2} {3}' -f [int64]$p.WorkingSet64,$p.Threads.Count,$p.HandleCount,[int]$p.PrivateMemorySize64}else{'gone'}`],
      { encoding: 'utf8', timeout: 15000, windowsHide: true, stdio: ['ignore', 'pipe', 'ignore'] });
    const t = String(out).trim();
    if (!t || t === 'gone') return null;
    const [ws, th, hh, pv] = t.split(/\s+/).map(Number);
    return { ws_mb: +(ws / 1048576).toFixed(1), threads: th, handles: hh, private_mb: +(pv / 1048576).toFixed(1) };
  } catch (_) { return null; }
}
function appMetrics() {
  try {
    const m = require('electron').app.getAppMetrics();
    const out = {};
    for (const x of m) {
      const k = x.type === 'Browser' ? 'main' : x.type === 'Tab' ? 'renderer' : x.type.toLowerCase();
      const cur = out[k] || { ws_mb: 0, cpu: 0, pid: x.pid };
      cur.ws_mb = +(((cur.ws_mb * 1024 + (x.memory ? x.memory.workingSetSize : 0)) / 1024)).toFixed(1);
      cur.cpu = +(cur.cpu + (x.cpu ? x.cpu.percentCPUUsage : 0)).toFixed(1);
      out[k] = cur;
    }
    return out;
  } catch (_) { return {}; }
}

/* ---------------- 主流程 ---------------- */
async function run(win, { log = console.log, keep = false, soakMinutes = 0 } = {}) {
  const R = makeReporter(log);
  const { check, note, section } = R;
  const js = (code) => win.webContents.executeJavaScript(code, true);
  const consoleErrors = () => (win.__consoleErrors || []).slice();
  // core.status() 是同步的（ipc 层才包成 async），别在它上面挂 .catch
  const status = () => { try { return core.status() || {}; } catch (_) { return {}; } };
  async function waitFor(expr, timeoutMs = 15000, label = expr) {
    const t0 = Date.now();
    while (Date.now() - t0 < timeoutMs) {
      if (await js('!!(' + expr + ')').catch(() => false)) return true;
      await sleep(150);
    }
    throw new Error(`等待超时：${label}`);
  }
  async function until(fn, timeoutMs = 15000, label = '条件') {
    const t0 = Date.now();
    while (Date.now() - t0 < timeoutMs) {
      try { const v = await fn(); if (v) return v; } catch (_) {}
      await sleep(250);
    }
    throw new Error(`等待超时：${label}`);
  }
  // 侧边栏只有 home/nodes/traffic/me/settings 五项；routing 这类子页面没有侧边栏入口，
  // 得走页面里的 [data-click="nav-<route>"]。第一版直接点 .nav-item[data-route="routing"]
  // 静默 no-op，界面停在别处，整节分流页的点击全部落空 —— 白报了一轮假 bug。
  async function clickNav(route) {
    const hit = await js(`(() => {
      const el = document.querySelector('.nav-item[data-route="${route}"]') || document.querySelector('[data-click="nav-${route}"]');
      if (!el) return false; el.click(); return true;
    })()`);
    if (!hit) missedClicks.push(`nav:${route}`);
    await sleep(600);
    return hit;
  }
  /** 分流页入口：节点页右上角的「分流规则」按钮（首页/我的页也有入口） */
  async function openRouting() {
    await clickNav('nodes');
    await closeOverlays();
    const ok = await click('[data-click="nav-routing"]');
    await sleep(700);
    return ok;
  }
  // 点击必须命中元素：静默 no-op 会让「点了没生效」看起来像产品 bug
  // （这一版就是因为没查这个，白追了一轮「全局模式切不过去」）
  const missedClicks = [];
  async function click(sel) {
    const hit = await js(`(() => { const el = document.querySelector(${JSON.stringify(sel)}); if (!el) return false; el.click(); return true; })()`);
    if (!hit) missedClicks.push(sel);
    return hit;
  }
  /** 关掉可能开着的弹窗（点遮罩），避免下一次「打开弹窗」其实是在关它 */
  async function closeOverlays() {
    for (let i = 0; i < 3; i++) {
      const had = await js("(() => { const o = document.querySelector('[data-overlay]'); if (!o) return false; o.dispatchEvent(new MouseEvent('mousedown', { bubbles: true })); return true; })()");
      if (!had) return;
      await sleep(200);
    }
  }

  const baselineSys = sysproxy.snapshot();
  const mock = await mockPanel.start(0);
  const panelUrl = 'http://127.0.0.1:' + mock.port;
  let connectedAtStart = false;

  try {
    /* ============ 一、基线 ============ */
    section('一、测试基线');
    const pkg = require('../package.json');
    const S = core.S;
    note(`版本 ${pkg.version} | packaged=${paths.isPackaged} portable=${paths.isPortable()}`);
    note(`root=${paths.root()}`);
    check('便携数据目录与程序目录分离（不写 AppData）', !paths.root().toLowerCase().includes('appdata'), paths.root());
    const mihomo = fs.existsSync(paths.core() + '\\mihomo.exe');
    check('内核二进制在位', mihomo, paths.core() + '\\mihomo.exe');
    check('wintun.dll 在位（TUN 用）', fs.existsSync(paths.core() + '\\wintun.dll'));
    const seedDir = paths.ruleSeeds();
    const seeds = fs.existsSync(seedDir) ? fs.readdirSync(seedDir).filter((f) => f.endsWith('.yaml')) : [];
    check('内置分流种子已铺开（48 个）', seeds.length === 48, `${seeds.length} 个 @ ${seedDir}`);
    const t0metrics = procMetrics(process.pid);
    note(`主进程基线 ${t0metrics ? JSON.stringify(t0metrics) : 'n/a'}`);
    note(`系统代理基线 ${JSON.stringify(baselineSys)}`);

    /* ============ 二、逐页遍历（真点） ============ */
    section('二、逐页遍历（真填真点）');
    await waitFor("document.querySelector('#btn-login')", 15000, '登录页');
    const setField = (id, v) => js(`(() => { const el = document.querySelector(${JSON.stringify(id)}); if (!el) return false;
      el.focus(); el.value = ${JSON.stringify(v)}; el.dispatchEvent(new Event('input', { bubbles: true })); el.dispatchEvent(new Event('change', { bubbles: true })); return true; })()`);
    await setField('#login-panel', panelUrl);
    await setField('#login-email', 'rt-tester@example.com');
    await setField('#login-pass', 'rt-test-password');
    await click('#btn-login');
    const loggedIn = await until(async () => await js("!!document.querySelector('.nav-item.active')"), 20000, '登录后进入主界面').then(() => true).catch(() => false);
    check('界面登录成功（走真 IPC → 面板 → 拿订阅）', loggedIn);

    const pages = [
      ['home', 'home'],
      ['nodes', 'nodes'],
      ['traffic', 'traffic'],
      ['me', 'me'],
      ['settings', 'settings'],
    ];
    for (const [route, key] of pages) {
      const before = consoleErrors().length;
      await clickNav(route);
      const info = await js(`(() => {
        const nav = document.querySelector('.nav-item.active');
        return {
          active: nav ? nav.dataset.route : null,
          title: (document.querySelector('.page-title') || {}).textContent || '',
          text: (document.querySelector('#content') || {}).innerText || '',
          nodes: document.querySelectorAll('.node-row').length,
          switches: document.querySelectorAll('.switch').length,
          cards: document.querySelectorAll('.card').length,
        };
      })()`);
      check(`页面 ${route} 高亮正确`, info.active === key, `active=${info.active}`);
      check(`页面 ${route} 有真实内容（非空壳）`, info.text.length > 80, `${info.text.length} 字`);
      check(`页面 ${route} 渲染期没有报错`, consoleErrors().length === before, consoleErrors().slice(before).join(' | '));
      if (route === 'nodes') {
        const names = await js(`Array.from(document.querySelectorAll('.node-row')).map((e) => e.dataset.node)`);
        check('节点页列出真实节点（不是空列表）', names.length >= 3, names.join(', '));
        check('节点页清掉了「剩余流量」这类伪节点', !names.some((n) => /剩余流量|套餐|到期/.test(n)), names.join(', '));
        check('节点页节点名带地区（香港/日本/新加坡）', ['香港', '日本', '新加坡'].some((k) => names.some((n) => n.includes(k))), names.join(', '));
      }
      if (route === 'me') {
        check('我的页显示面板套餐数据', /套餐|订单|邀请|礼品卡/.test(info.text));
      }
      if (route === 'home') {
        check('首页有连接按钮', await js("!!document.querySelector('#power')"));
      }
    }

    /* ============ 三、连接 + 内核回读 ============ */
    section('三、连接并回读内核（不信界面乐观更新）');
    await clickNav('home');
    await click('#power');
    const connected = await until(async () => status().connected, 45000, '状态变为已连接').then(() => true).catch(() => false);
    check('点连接按钮后内核真的起来了', connected, JSON.stringify(status()));
    connectedAtStart = connected;
    if (connected) {
      const st = status();
      const c = await controllerOf();
      const ver = await c.get('/version');
      check('内核控制面可读 /version', !!(ver && ver.version), ver && ver.version);
      const cfg = await c.get('/configs');
      check('内核 mixed-port 与 App 报告一致', Number(cfg['mixed-port']) === core.mixedPort(), `kernel=${cfg['mixed-port']} app=${core.mixedPort()}`);
      check('控制面只绑本机（管理端口不可从外部访问）', c.host === '127.0.0.1', `${c.host}:${c.port}`);
      const cfgText = fs.readFileSync(paths.file('config.yaml'), 'utf8');
      const m = cfgText.match(/^secret:\s*(\S+)/m);
      check('config.yaml 里的 secret 与控制面用的 secret 一致', !!m && m[1] === c.secret, m ? `secret 长度 ${String(m[1]).length}` : '没找到 secret');
      const proxies = await c.get('/proxies');
      const pnames = Object.keys(proxies.proxies || {});
      check('内核里真的加载了订阅节点', pnames.some((n) => n.includes('香港')), pnames.filter((n) => n.includes('香港')).join(','));
      check('内核里真的建了内置策略组', ['节点选择', '国内直连', '国外穿墙'].every((g) => pnames.includes(g)) || pnames.filter((n) => /直连|穿墙|选择/.test(n)).length >= 3,
        pnames.filter((n) => /直连|穿墙|选择|苹果|广告/.test(n)).join(','));
      const proc = procMetrics(S.proc && S.proc.pid);
      check('mihomo 进程真实存在且指标可读', !!proc, JSON.stringify(proc));
      const ws = await c.connectWS('/traffic').then((s) => s).catch(() => null);
      let gotTraffic = false;
      if (ws) {
        gotTraffic = await new Promise((resolve) => {
          const timer = setTimeout(() => resolve(false), 8000);
          ws.on('message', () => { clearTimeout(timer); resolve(true); });
        });
        try { ws.close(); } catch (_) {}
      }
      check('/traffic WebSocket 能连且真的推数据', gotTraffic);
      // 真实外网请求（经代理）
      let code = '';
      try { code = curlSync(['--max-time', '20', '-o', 'NUL', '-w', '%{http_code}', '-x', `http://127.0.0.1:${core.mixedPort()}`, 'https://www.gstatic.com/generate_204']).trim(); } catch (e) { code = 'ERR:' + e.message; }
      check('经代理访问外网真的通', code === '204', `http_code=${code}`);
      // 大文件请求让 /connections 里留得住连接（下面分流命中要用）
      const uiConn = await js(`(() => { const el = document.querySelector('#power'); return { cls: el ? el.className : null, title: el ? el.title : null }; })()`);
      check('界面连接态与内核一致（按钮显示"断开"）', uiConn.title === '断开', JSON.stringify(uiConn));
      const upText = await js(`(document.querySelector('#content')||{}).innerText || ''`);
      check('首页显示真实流量数字', /MB|KB|GB|B\/s/.test(upText), upText.slice(0, 120).replace(/\n/g, ' '));
    } else {
      note('连接失败，后续需要连接的项目跳过');
    }

    /* ============ 四、分流命中（读内核 connections） ============ */
    if (connected) {
      section('四、分流命中与出口（读内核 /connections 与 /proxies）');
      const c = await controllerOf();
      const proxy = `http://127.0.0.1:${core.mixedPort()}`;
      // 规则模式下的国外域名 → 应该走代理，不该 DIRECT
      const curl1 = curlAsync(['--max-time', '25', '-o', 'NUL', '-x', proxy, 'https://speed.cloudflare.com/__down?bytes=30000000']);
      const conn1 = await findConnection('speed.cloudflare.com');
      curl1.kill();
      if (conn1) {
        const chains = (conn1.chains || []).join(' → ');
        check('规则模式下国外域名不是直连', (conn1.chains || [])[0] !== 'DIRECT', `rule=${conn1.rule}(${conn1.rulePayload || ''}) chains=${chains}`);
        // 内核回报的规则类型是 RuleSet(gs_xxx)（大写 R、连字符），早先按 RULE-SET 匹配是错的
        check('国外域名命中了内置分流规则集', /RuleSet|RULE-SET|GEOSITE|GEOIP|MATCH/i.test(String(conn1.rule)), `rule=${conn1.rule}(${conn1.rulePayload || ''}) chains=${chains}`);
        check('命中的是内置规则集文件（gs_/acl_/gp_ 前缀）', /^(gs|acl|gp)_/.test(String(conn1.rulePayload || '')), `rulePayload=${conn1.rulePayload}`);
        note(`国外域名实测：rule=${conn1.rule}(${conn1.rulePayload || ''}) chains=${chains}`);
      } else {
        check('国外域名能建立连接（/connections 可见）', false, '没在 /connections 里看到 speed.cloudflare.com');
      }
      // 规则模式下的国内域名 → 应该直连
      const curl2 = curlAsync(['--max-time', '12', '-o', 'NUL', '-x', proxy, 'https://www.baidu.com/']);
      const conn2 = await findConnection('baidu.com');
      curl2.kill();
      if (conn2) {
        const chains = (conn2.chains || []).join(' → ');
        const isDirect = (conn2.chains || [])[0] === 'DIRECT';
        check('规则模式下国内域名走直连', isDirect, `rule=${conn2.rule}(${conn2.rulePayload || ''}) chains=${chains}`);
        note(`国内域名实测：rule=${conn2.rule}(${conn2.rulePayload || ''}) chains=${chains}`);
      } else {
        note('国内域名没在 /connections 里抓到（可能太快），跳过这一条');
      }
      // 界面切模式 → 内核 mode 必须是那个值（每次先关掉可能残留的弹窗）
      // 注意：机器被别的重活占满时（例如同时在打包压缩），内核应答会明显变慢，
      // 10s 窗口里读到的还是上一个模式 —— 这时重试一次，并把"重试过"记进 note，
      // 既不当成产品缺陷，也不把这种慢吞掉不说。
      const modeViaUI = async (label, expect) => {
        await clickNav('home');
        await closeOverlays();
        let m = null;
        let tries = 0;
        for (; tries < 2; tries++) {
          await click('[data-click="proxy-mode"]');
          await sleep(400);
          const opened = await js(`!!document.querySelector('.overlay .opt[data-mode=${JSON.stringify(label)}]')`);
          if (tries === 0) check(`代理模式弹窗里有「${label}」选项`, opened);
          if (!opened) { await closeOverlays(); continue; }
          await click(`.opt[data-mode="${label}"]`);
          m = await until(async () => ((await c.get('/configs')).mode), 10000, 'mode 变 ' + expect).catch(() => null);
          await closeOverlays();
          if (m === expect) break;
        }
        if (tries > 0 && m === expect) note(`切「${label}」重试了 ${tries} 次才生效（机器当时在忙）`);
        check(`界面切「${label}」后内核 mode=${expect}`, m === expect, String(m));
        return m;
      };
      await modeViaUI('全局模式', 'global');
      const curl3 = curlAsync(['--max-time', '12', '-o', 'NUL', '-x', proxy, 'https://www.baidu.com/']);
      const conn3 = await findConnection('baidu.com', 8000, conn2 && conn2.id);
      curl3.kill();
      if (conn3) check('全局模式下国内域名也走代理（不再 DIRECT）', (conn3.chains || [])[0] !== 'DIRECT', `chains=${(conn3.chains || []).join(' → ')}`);
      await modeViaUI('直连模式', 'direct');
      await modeViaUI('规则模式', 'rule');

      // 分流分组：界面开关 → config.yaml → 内核热重载
      await openRouting();
      const rs0 = await ipc.commands.get_rulesets();
      const onGroup = (rs0.groups || []).find((g) => g.enabled);
      // 必须挑一个「非内联」的关闭组：内联组只有内联规则、不带 rule-provider 文件，
      // 打开它 provider 数不会变（第一版挑到内联组，白报一条假 bug）
      const offGroup = (rs0.groups || []).find((g) => !g.enabled && !g.inline && (g.count || 0) > 0);
      check('分流页拿到 27 个分类', (rs0.groups || []).length === 27, `total=${rs0.total} enabled=${(rs0.enabled || []).length}`);
      const cnt0 = (fs.readFileSync(paths.file('config.yaml'), 'utf8').match(/^\s{2}[A-Za-z0-9_]+:\s*$/gm) || []).length;
      if (offGroup) {
        const sel = `.switch[data-ruleset="${offGroup.name}"]`;
        await click(sel);
        await sleep(1500);
        const p0 = await c.get('/providers/rules');
        const key = Object.keys((p0 && p0.providers) || {}).length;
        check(`界面上打开「${offGroup.name}」后内核 provider 数增加`, key > 16, `providers=${key}`);
        await click(sel);
        await sleep(1200);
        const p1 = await c.get('/providers/rules');
        check('再关掉后内核 provider 数回落', Object.keys((p1 && p1.providers) || {}).length === 16, `providers=${Object.keys((p1 && p1.providers) || {}).length}`);
      } else {
        check('分流页里有可开关的非内联分类', false, '27 个分类里没找到非内联的关闭组');
      }
      // 内置规则集是否真被内核加载（异步初始化，必须轮询）
      const ruleOk = await until(async () => {
        const p = await c.get('/providers/rules');
        const provs = (p && p.providers) || {};
        const keys = Object.keys(provs);
        const zero = keys.filter((k) => !(provs[k].ruleCount > 0));
        return keys.length >= 16 && zero.length === 0 ? keys : null;
      }, 20000, '所有 rule-provider 都加载出规则').catch(() => null);
      const pFinal = await c.get('/providers/rules');
      const totalRules = Object.values((pFinal && pFinal.providers) || {}).reduce((a, b) => a + (b.ruleCount || 0), 0);
      check('内核真的把 16 个内置规则集解析成规则（不是 0 条）', !!ruleOk, `规则总数=${totalRules}`);
      note(`内核规则总数 ${totalRules}`);

      // 单组出口覆盖：界面点行 → 选 DIRECT → 内核 /proxies 回读
      if (onGroup) {
        await openRouting();
        const g0 = await c.get('/proxies/' + encodeURIComponent(onGroup.name));
        const before = g0 && g0.now;
        await click(`[data-ruleset-pick="${onGroup.name}"]`);
        await sleep(400);
        const picked = await click('.opt[data-pick="DIRECT"]');
        if (picked) {
          const after = await until(async () => {
            const g = await c.get('/proxies/' + encodeURIComponent(onGroup.name));
            return g && g.now === 'DIRECT' ? g : null;
          }, 8000, '分组出口变 DIRECT').catch(() => null);
          check(`界面把「${onGroup.name}」出口改成直连后内核真的生效`, !!after, `before=${before} after=${after && after.now}`);
          // 还原
          await openRouting();
          await click(`[data-ruleset-pick="${onGroup.name}"]`);
          await sleep(400);
          await click(`.opt[data-pick="${String(before).replace(/"/g, '')}"]`);
          await sleep(800);
          const back = await c.get('/proxies/' + encodeURIComponent(onGroup.name));
          note(`出口已还原：${back && back.now}`);
        } else {
          note('该分组没有 DIRECT 可选项，跳过出口覆盖测试');
        }
      }
    }

    /* ============ 五、异常路径与自愈 ============ */
    section('五、异常路径与自愈');
    if (connected) {
      const c = await controllerOf();
      const proxy = `http://127.0.0.1:${core.mixedPort()}`;

      // 5.1 系统代理：连接态下「关」必须被拦住（关了就绕过内核），注册表与快照都不许动；
      //     真正的还原发生在断开时 —— 那一条在收尾里断言。
      await clickNav('settings');
      await closeOverlays();
      const hadProxy = await js("!!document.querySelector('.switch[data-setting=\"sys_proxy\"]')");
      if (hadProxy) {
        const switchOn = () => js(`(() => { const s = document.querySelector('.switch[data-setting="sys_proxy"]'); return !!(s && s.classList.contains('on')); })()`);
        const snapCore = () => { try { return JSON.stringify(core.S.proxySnapshot); } catch (_) { return 'n/a'; } };
        note(`5.1 起始：switch=${await switchOn() ? 'on' : 'off'} registry=${JSON.stringify(sysproxy.snapshot())} core.proxySnapshot=${snapCore()}`);
        if (!(await switchOn())) { await click('.switch[data-setting="sys_proxy"]'); await sleep(2500); }
        const on = sysproxy.snapshot();
        check('系统代理开关打开后注册表指向内核端口', !!on && on.ProxyEnable === 1 && String(on.ProxyServer) === `127.0.0.1:${core.mixedPort()}`,
          `ProxyEnable=${on && on.ProxyEnable} ProxyServer=${on && on.ProxyServer} 内核端口=${core.mixedPort()}`);
        await clickNav('settings');
        await closeOverlays();
        await click('.switch[data-setting="sys_proxy"]');
        await sleep(1200);
        const guardToast = String(await js(`(document.querySelector('.toast')||{}).textContent || ''`));
        const stillOn = await switchOn();
        const after = sysproxy.snapshot();
        note(`5.1 连接态点关：switch=${stillOn ? 'on' : 'off'} toast=${JSON.stringify(guardToast)} registry=${JSON.stringify(after)}`);
        check('连接态下关闭系统代理被拦住（否则流量绕过内核）',
          /断开连接后/.test(guardToast) && stillOn && String(after && after.ProxyServer) === `127.0.0.1:${core.mixedPort()}`,
          `toast=${guardToast} switch=${stillOn ? 'on' : 'off'} ProxyServer=${after && after.ProxyServer}`);
      }

      // 5.2 窗口收进托盘（对照安卓「长后台」）：内核/连接不能受影响，恢复后界面数字要对得上
      await clickNav('home');
      const uptimeOf = () => js(`(() => { const t = (document.querySelector('#content')||{}).innerText || ''; const m = t.match(/\\d{2}:\\d{2}:\\d{2}/); return m ? m[0] : null; })()`);
      const u1 = await uptimeOf();
      win.hide();
      await sleep(13000);
      const u2 = await uptimeOf();
      win.show();
      await sleep(2500);
      const u3 = await uptimeOf();
      const secs = (s) => (s ? s.split(':').reduce((a, b) => a * 60 + Number(b), 0) : -1);
      const delta = secs(u2) - secs(u1);
      note(`托盘期间界面计时 ${u1} → ${u2}（+${delta}s，隐藏时 Chromium 会节流渲染层，数字停住是预期的）`);
      // 真正的要求：恢复窗口后显示值立刻追上内核回读的 uptime，不能停在旧值
      const kUptime = String(status().uptime || '');
      check('恢复窗口后界面计时与内核回读一致（没停在隐藏前的旧值）', secs(u3) >= secs(u2) + 1 && Math.abs(secs(u3) - secs(kUptime)) <= 3,
        `界面 ${u3} / 内核 ${kUptime}`);
      const stillOk = (() => { try { return curlSync(['--max-time', '15', '-o', 'NUL', '-w', '%{http_code}', '-x', proxy, 'https://www.gstatic.com/generate_204']).trim(); } catch (e) { return 'ERR'; } })();
      check('托盘期间代理仍可用', stillOk === '204', `http_code=${stillOk}`);

      // 5.3 内核被杀：应用不能继续谎报"已连接"
      const pidDead = core.S.proc && core.S.proc.pid;
      let noticed = false;
      if (pidDead) {
        try { execFileSync('taskkill', ['/PID', String(pidDead), '/F'], { windowsHide: true, stdio: 'ignore', timeout: 8000 }); } catch (_) {}
        const st2 = await until(async () => {
          const s = status();
          if (!s.connected || !s.core_alive) return s;
          return null;
        }, 20000, '应用发现内核已死').catch(() => null);
        noticed = !!st2;
        const sNow = status();
        check('内核被强杀后应用能发现（不再谎报已连接）', noticed, JSON.stringify(sNow));
        note(`内核死后状态：${JSON.stringify(sNow)}`);
        // 自愈：按「当前真实状态」点，而不是盲点两下。
        // 第一版盲点两下：应用已经自己发现内核死了（connected=false），
        // 于是第一下是「连接」、第二下是「断开」——把刚连上的又断掉，白报一条假 bug。
        let healed = false;
        for (let attempt = 0; attempt < 3 && !healed; attempt++) {
          if (status().connected) { healed = true; break; }
          await clickNav('home');
          await closeOverlays();
          await click('#power');   // 当前是「未连接」→ 这一下就是连接
          healed = await until(async () => status().connected, 25000, '重新连上').then(() => true).catch(() => false);
          if (!healed) note(`自愈第 ${attempt + 1} 次没连上，状态=${JSON.stringify(status())}`);
        }
        check('内核被强杀后能重新连上（自愈）', healed, JSON.stringify(status()));
        if (healed) {
          const hc = await controllerOf();
          const hv = await hc.get('/version');
          check('自愈后控制面正常且端口是新的', !!(hv && hv.version) && (core.S.proc.pid !== pidDead), `old_pid=${pidDead} new_pid=${core.S.proc.pid}`);
        }
      }

      // 5.4 面板不可用：不能把已建立的连接带崩
      if (!status().connected) {
        note('内核当前未连接，跳过「面板不可用」这一节（避免把上一条失败的后果重复报一遍）');
      } else {
      const beforeDown = (() => { try { return curlSync(['--max-time', '15', '-o', 'NUL', '-w', '%{http_code}', '-x', `http://127.0.0.1:${core.mixedPort()}`, 'https://www.gstatic.com/generate_204']).trim(); } catch (e) { return 'ERR'; } })();
      await new Promise((r) => mock.server.close(r));
      await clickNav('nodes');
      const before = consoleErrors().length;
      await click('#btn-refresh-sub');
      await sleep(4000);
      const downOk = await js(`!!document.querySelector('#content')`);
      const afterDown = (() => { try { return curlSync(['--max-time', '15', '-o', 'NUL', '-w', '%{http_code}', '-x', `http://127.0.0.1:${core.mixedPort()}`, 'https://www.gstatic.com/generate_204']).trim(); } catch (e) { return 'ERR'; } })();
      check('面板挂了以后界面没崩', !!downOk);
      check('面板挂了以后代理照常可用（不受影响）', beforeDown === '204' && afterDown === '204', `${beforeDown} → ${afterDown}`);
      // 只统计真错误：受控失败（guard 里 catch 住的）现在走 console.warn，不该进 __consoleErrors
      check('面板挂了的错误没有变成渲染层异常', consoleErrors().length === before, consoleErrors().slice(before).join(' | '));
      const toastText = await js(`(document.querySelector('#toast')||{}).innerText || (document.body.innerText.match(/订阅[^\\n]{0,40}/)||[''])[0]`);
      note(`面板不可用时界面提示：${String(toastText).trim().slice(0, 80) || '（没抓到文案）'}`);
      }

      check('所有界面点击都命中了元素（没有静默 no-op）', missedClicks.length === 0, missedClicks.join(' | '));
    }

    /* ============ 六、浸泡（长后台） ============ */
    if (soakMinutes > 0 && connected) {
      section(`六、浸泡 ${soakMinutes} 分钟（内存/线程/句柄 + 延迟/吞吐）`);
      await soak(win, { log, minutes: soakMinutes, reporter: R });
    }
  } finally {
    // 收尾：默认断开并把系统代理还原干净，别把用户的网络留在半路
    try {
      if (!keep) {
        const st = status();
        if (st && st.connected) await core.disconnect().catch(() => {});
        await sleep(1500);
      }
      const now = sysproxy.snapshot();
      // 断开后必须自己还原成进入本次测试前的值 —— 这是用户最容易被搞坏的地方：
      // 旧代码快照一丢，就把 Polaris 自己写的端口当成"用户原值"写回注册表。
      R.check('断开后系统代理自动还原到进入本次测试前的值', JSON.stringify(now) === JSON.stringify(baselineSys),
        `now=${JSON.stringify(now)} baseline=${JSON.stringify(baselineSys)}`);
      if (baselineSys && JSON.stringify(now) !== JSON.stringify(baselineSys)) {
        sysproxy.restore(baselineSys);
        log('  · 系统代理已还原（收尾）');
      }
    } catch (e) { log('收尾失败：' + (e && e.message)); }
    try { mock.server.close(); } catch (_) {}
  }
  return R.result();
}

/* ---------------- 浸泡采样 ---------------- */
async function soak(win, { log = console.log, minutes = 30, reporter } = {}) {
  const R = reporter || makeReporter(log);
  const { check, note } = R;
  const js = (code) => win.webContents.executeJavaScript(code, true);
  // soak 是模块级函数，拿不到 run() 里的 status() 助手 —— 第一次跑 20 分钟浸泡就是
  // 在这里 ReferenceError 秒退的（报告只有一条「运行时测试未运行」）。
  const status = () => { try { return core.status() || {}; } catch (_) { return {}; } };
  const samples = [];
  const out = paths.file('rt-samples.jsonl');
  // 回到首页再开始采样：否则 #content 里没有会话计时，uptime_ui 会一直是 null。
  await js(`(() => { const n = document.querySelector('.nav-item[data-route="home"]'); if (n) n.click(); return !!n; })()`).catch(() => false);
  await sleep(400);
  const proxy = () => `http://127.0.0.1:${core.mixedPort()}`;
  const nodeName = () => (core.S.node || '').trim();
  async function latency() {
    try {
      const c = core.S.controller;
      const n = nodeName();
      if (!c || !n) return null;
      const r = await c.get(`/proxies/${encodeURIComponent(n)}/delay?timeout=3000&url=http://www.gstatic.com/generate_204`);
      return r && typeof r.delay === 'number' ? r.delay : null;
    } catch (_) { return null; }
  }
  async function throughput() {
    try {
      const t0 = Date.now();
      const v = execFileSync('curl.exe',
        ['-s', '--noproxy', '', '--max-time', '25', '-o', 'NUL', '-w', '%{speed_download}',
          '-x', proxy(), 'https://speed.cloudflare.com/__down?bytes=10000000'],
        { encoding: 'utf8', timeout: 35000, windowsHide: true, stdio: ['ignore', 'pipe', 'ignore'] });
      const sp = Number(String(v).trim());
      return { mbps: +((sp * 8) / 1e6).toFixed(2), sec: +((Date.now() - t0) / 1000).toFixed(1) };
    } catch (_) { return null; }
  }
  const rounds = Math.max(1, Math.round((minutes * 60) / 60));
  let i = 0;
  for (; i < rounds; i++) {
    const st = status();
    const appM = appMetrics();
    const km = procMetrics(core.S.proc && core.S.proc.pid);
    const lat = await latency();
    const tp = (i % 5 === 4 || i === 0) ? await throughput() : null;
    const hidden = i % 4 === 3;
    if (hidden) win.hide(); else win.show();
    const uptimeText = await js(`(() => { const t=(document.querySelector('#content')||{}).innerText||''; const m=t.match(/\\d{2}:\\d{2}:\\d{2}/); return m?m[0]:null; })()`).catch(() => null);
    const s = {
      t: new Date().toISOString(), i, hidden,
      connected: !!st.connected, core_alive: !!st.core_alive, phase: st.phase || null,
      node: st.node || null, uptime: st.uptime || null, uptime_ui: uptimeText,
      up: st.up_total || null, down: st.down_total || null,
      main_ws_mb: appM.main ? appM.main.ws_mb : null, renderer_ws_mb: appM.renderer ? appM.renderer.ws_mb : null,
      gpu_ws_mb: appM.gpu ? appM.gpu.ws_mb : null,
      cpu: (appM.main ? appM.main.cpu : 0) + (appM.renderer ? appM.renderer.cpu : 0),
      kernel: km, kernel_pid: (core.S.proc && core.S.proc.pid) || null, latency_ms: lat, throughput: tp,
    };
    samples.push(s);
    try { fs.appendFileSync(out, JSON.stringify(s) + '\n', 'utf8'); } catch (_) {}
    log(`  [${i + 1}/${rounds}] ${s.connected ? '已连接' : '已断开'} ws=${s.main_ws_mb}/${s.renderer_ws_mb}MB kernel=${km ? km.ws_mb + 'MB/' + km.threads + '线程/' + km.handles + '句柄' : 'n/a'} lat=${lat == null ? 'n/a' : lat + 'ms'}${tp ? ' 吞吐=' + tp.mbps + 'Mbps' : ''}${hidden ? ' [托盘]' : ''}`);
    if (i < rounds - 1) await sleep(60000);
  }
  win.show();
  // 结论
  const nums = (arr, f) => arr.map(f).filter((x) => typeof x === 'number');
  const mean = (a) => (a.length ? a.reduce((p, c) => p + c, 0) / a.length : null);
  const q = Math.max(1, Math.round(samples.length / 4));
  const first = samples.slice(0, q);
  const last = samples.slice(-q);
  const drift = (f) => {
    const a = mean(nums(first, f)); const b = mean(nums(last, f));
    return a == null || b == null ? null : +(b - a).toFixed(1);
  };
  check('浸泡期间始终保持连接', samples.every((s) => s.connected), `${samples.filter((s) => !s.connected).length} 次掉线`);
  check('浸泡期间内核进程始终存活', samples.every((s) => s.core_alive));
  const pids = [...new Set(samples.map((s) => s.kernel_pid).filter(Boolean))];
  check('浸泡期间内核 pid 没变过（没有反复重启内核）', pids.length <= 1, `pids=${pids.join(',')}`);
  const memDrift = drift((s) => s.main_ws_mb);
  const kDriftMb = drift((s) => s.kernel && s.kernel.ws_mb);
  const hDrift = drift((s) => s.kernel && s.kernel.handles);
  const tDrift = drift((s) => s.kernel && s.kernel.threads);
  note(`内存/句柄漂移：主进程 ${memDrift} MB，内核 ${kDriftMb} MB，内核句柄 ${hDrift} 个，内核线程 ${tDrift} 个`);
  check('主进程内存没有持续增长（漂移 < 80MB）', memDrift == null || Math.abs(memDrift) < 80, `${memDrift} MB`);
  check('内核内存没有持续增长（漂移 < 80MB）', kDriftMb == null || Math.abs(kDriftMb) < 80, `${kDriftMb} MB`);
  check('内核句柄没有泄漏（漂移 < 500）', hDrift == null || Math.abs(hDrift) < 500, `${hDrift} 个`);
  const latFirst = mean(nums(first, (s) => s.latency_ms));
  const latLast = mean(nums(last, (s) => s.latency_ms));
  const latAll = samples.map((s) => s.latency_ms).filter((x) => typeof x === 'number');
  if (latFirst != null && latLast != null) {
    note(`延迟：首段 ${latFirst.toFixed(0)}ms → 末段 ${latLast.toFixed(0)}ms（${latAll.length} 次采样）`);
    check('长时间保持连接后延迟没有劣化（末段 ≤ 首段 3 倍）', latLast <= Math.max(latFirst * 3, latFirst + 150), `${latFirst.toFixed(0)} → ${latLast.toFixed(0)}`);
  }
  const tps = samples.map((s) => s.throughput && s.throughput.mbps).filter((x) => typeof x === 'number');
  if (tps.length >= 3) {
    note(`吞吐采样 ${tps.join(' / ')} Mbps`);
    check('长时间保持连接后吞吐没有塌到 0', tps.slice(-2).some((x) => x > 0.2), tps.join(','));
  }
  return R.result();
}

module.exports = { run, soak };
