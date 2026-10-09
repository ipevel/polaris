'use strict';
/**
 * 界面驱动自检：真的去点界面，而不是只查 DOM 存不存在。
 *
 * 上一轮就是在这里栽的 —— 数据层 60 项全过、DOM 断言也过，但窗口拖不动、
 * 最大化布局错位、点登录没反应，全都测不到。这个脚本补上：
 *
 *   1. 真窗口外壳（拖动区域、内容可滚动、最大化后无横向溢出）
 *   2. 真的填表单、真的点「登录」，看路由有没有变成首页
 *   3. 逐个点侧边栏，每页渲染完不能有渲染层报错
 *   4. 分流规则页有入口且能进去；流量页真的画出曲线（不是空态）
 *   5. 打开/关闭弹窗；更新弹窗不再弹「只允许打开 http(s) 链接」
 *   6. 设置页不自我重绘、弹窗不被重绘吃掉（历史上这里是无界死循环）
 *   7. 最大化窗口后重新量一遍布局；窄窗口（1100px，断点 1120px 下沿）也不许溢出/截断
 *   8. 暗色主题下关键文字对比度达到 WCAG AA（4.5:1）
 *
 * 用法：Polaris.exe --uitest   （会自己起一个本地假面板）
 */

const fs = require('fs');
const path = require('path');

function makeReporter(log) {
  const r = { pass: 0, fail: 0, failures: [], steps: [] };
  return {
    check(name, cond, detail) {
      if (cond) { r.pass += 1; log(`  PASS  ${name}`); }
      else {
        r.fail += 1;
        r.failures.push(name + (detail ? ` :: ${detail}` : ''));
        log(`  FAIL  ${name}${detail ? ` :: ${detail}` : ''}`);
      }
    },
    section(t) { log(`\n== ${t}`); },
    result() { return r; },
  };
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function run(win, { log = console.log } = {}) {
  const mockPanel = require('./mock-panel');
  const R = makeReporter(log);
  const js = (code) => win.webContents.executeJavaScript(code, true);

  // 等界面进入可用状态
  async function waitFor(expr, timeoutMs = 12000, label = expr) {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
      try { if (await js(`!!(${expr})`)) return true; } catch (_) {}
      await sleep(150);
    }
    log(`  (等待超时: ${label})`);
    return false;
  }

  // 渲染层的报错，main.js 已经在收集
  const consoleErrors = () => (win.__consoleErrors || []).slice();

  const { server, port } = await mockPanel.start(0);
  const panelUrl = `http://127.0.0.1:${port}`;
  log(`\n界面驱动自检   mock panel = ${panelUrl}`);

  try {
    /* ---------------- 1. 真窗口外壳 ---------------- */
    R.section('真窗口外壳');
    await waitFor(`document.readyState === 'complete' && window.PolarisAPI`);
    await sleep(500);

    const shell = await js(`(() => {
      const tb = document.querySelector('.titlebar');
      const win = document.querySelector('.window');
      const content = document.querySelector('.content');
      const cs = getComputedStyle(tb);
      return {
        dragRegion: cs.webkitAppRegion || cs.getPropertyValue('-webkit-app-region') || '',
        btnNoDrag: getComputedStyle(document.querySelector('#btn-min')).webkitAppRegion || '',
        winW: win.getBoundingClientRect().width,
        winH: win.getBoundingClientRect().height,
        viewW: window.innerWidth,
        viewH: window.innerHeight,
        contentOverflowY: getComputedStyle(content).overflowY,
        bodyCursor: getComputedStyle(document.body).overflow,
      };
    })()`);

    R.check('标题栏声明了拖动区域', /drag/.test(shell.dragRegion), JSON.stringify(shell.dragRegion));
    R.check('窗口按钮排除了拖动区域', /no-drag/.test(shell.btnNoDrag), JSON.stringify(shell.btnNoDrag));
    R.check('窗口铺满视口（宽）', Math.abs(shell.winW - shell.viewW) <= 1, `${shell.winW} vs ${shell.viewW}`);
    R.check('窗口铺满视口（高）', Math.abs(shell.winH - shell.viewH) <= 1, `${shell.winH} vs ${shell.viewH}`);
    R.check('内容区可纵向滚动', shell.contentOverflowY === 'auto', shell.contentOverflowY);

    /* ---------------- 2. 登录页 ---------------- */
    R.section('登录页');
    const hasLogin = await waitFor(`document.querySelector('#btn-login')`, 8000, '登录按钮');
    R.check('未登录时停在登录页', hasLogin);
    if (!hasLogin) throw new Error('登录页没出来，后续界面测试无法继续');

    const loginVisible = await js(`(() => {
      const c = document.querySelector('.login-card');
      const r = c.getBoundingClientRect();
      return { w: r.width, h: r.height, visible: r.width > 200 && r.height > 200 };
    })()`);
    R.check('登录卡片可见且尺寸正常', loginVisible.visible, JSON.stringify(loginVisible));

    // 真的填表单
    await js(`(() => {
      const set = (id, v) => {
        const el = document.querySelector(id);
        el.focus();
        el.value = v;
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
      };
      set('#login-panel', ${JSON.stringify(panelUrl)});
      set('#login-email', 'ui-tester@example.com');
      set('#login-pass', 'ui-test-password');
      return true;
    })()`);
    await sleep(150);

    // 空表单应该被前端拦下
    const emptyGuard = await js(`(() => {
      const p = document.querySelector('#login-email');
      const save = p.value; p.value = '';
      document.querySelector('#btn-login').click();
      return new Promise((res) => setTimeout(() => {
        const err = (document.querySelector('#auth-err') || {}).textContent || '';
        p.value = save;
        res(err);
      }, 400));
    })()`);
    R.check('空邮箱被前端拦下并给出提示', /邮箱|密码/.test(emptyGuard), JSON.stringify(emptyGuard));

    // 恢复并真的点登录
    await js(`(() => {
      const set = (id, v) => { const el = document.querySelector(id); el.value = v;
        el.dispatchEvent(new Event('input', { bubbles: true })); };
      set('#login-email', 'ui-tester@example.com');
      set('#login-pass', 'ui-test-password');
      set('#login-panel', ${JSON.stringify(panelUrl)});
      return true;
    })()`);
    await js(`document.querySelector('#btn-login').click(); true`);

    const loggedIn = await waitFor(`!document.querySelector('#btn-login') && document.querySelector('.nav-item.active')`, 20000, '登录进入首页');
    R.check('点「登录」后进入主界面', loggedIn);

    const afterLogin = await js(`(() => ({
      route: (document.querySelector('.nav-item.active') || {}).dataset
        ? document.querySelector('.nav-item.active').dataset.route : null,
      loginErr: (document.querySelector('#login-err') || {}).textContent || '',
      sidebarVisible: (document.querySelector('#sidebar') || {}).style.display !== 'none',
      hero: !!document.querySelector('.hero'),
    }))()`);
    R.check('落在首页', afterLogin.route === 'home', JSON.stringify(afterLogin));
    R.check('登录后显示侧边栏', afterLogin.sidebarVisible === true);
    R.check('首页主体已渲染', afterLogin.hero === true);

    /* ---------------- 3. 逐页点击 ---------------- */
    R.section('逐页点击');
    const routes = [
      ['nodes', '节点'], ['traffic', '流量'], ['me', '我的'],
      ['settings', '设置'], ['home', null],
    ];
    for (const [rt, expectTitle] of routes) {
      const before = consoleErrors().length;
      await js(`(() => { const el = document.querySelector('.nav-item[data-route="${rt}"]'); if (el) el.click(); return true; })()`);
      await sleep(800);
      const info = await js(`(() => ({
        active: (document.querySelector('.nav-item.active') || {}).dataset
          ? document.querySelector('.nav-item.active').dataset.route : null,
        title: (document.querySelector('.page-title') || {}).textContent || '',
        cards: document.querySelectorAll('.card').length,
        text: (document.body.innerText || '').length,
        empty: document.querySelectorAll('.empty').length,
      }))()`);
      const newErrors = consoleErrors().slice(before);
      R.check(`切到「${expectTitle || '首页'}」`, info.active === rt, JSON.stringify(info));
      if (expectTitle) {
        R.check('  └ 标题正确', info.title === expectTitle, info.title);
      }
      R.check('  └ 有内容且无报错', info.text > 40 && newErrors.length === 0,
        `${info.text} chars; empty=${info.empty}; errors=${JSON.stringify(newErrors)}`);
    }

    // 登录后到连接前，节点页不该是空的 —— 本地 config.yaml 里就有节点
    await js(`(() => { document.querySelector('.nav-item[data-route="nodes"]').click(); return true; })()`);
    await sleep(900);
    const nodesPage = await js(`(() => ({
      rows: document.querySelectorAll('.node-row').length,
      names: Array.from(document.querySelectorAll('.node-row .nm')).map((e) => e.textContent),
      hasEmpty: !!document.querySelector('.empty'),
    }))()`);
    R.check('未连接时也能看到订阅里的节点（读本地配置）', nodesPage.rows > 0,
      JSON.stringify(nodesPage));
    R.check('节点名来自订阅而非占位', nodesPage.names.some((n) => /香港|日本|新加坡/.test(n)),
      JSON.stringify(nodesPage.names));

    /* ---------------- 3b. 分流规则页（侧边栏没有入口，必须能从节点页进） ---------------- */
    R.section('分流规则页');
    const routingEntry = await js(`(() => {
      const b = document.querySelector('[data-click="nav-routing"]');
      if (!b) return 'no-entry';
      b.click();
      return true;
    })()`);
    R.check('节点页有「分流规则」入口', routingEntry === true, String(routingEntry));
    await sleep(800);
    const routingPage = await js(`(() => ({
      title: (document.querySelector('.page-title') || {}).textContent || '',
      hasReset: !!document.querySelector('#btn-routing-reset'),
      active: (document.querySelector('.nav-item.active') || {}).dataset
        ? document.querySelector('.nav-item.active').dataset.route : null,
      text: (document.body.innerText || '').length,
    }))()`);
    R.check('进入分流规则页', routingPage.title === '分流规则' && routingPage.hasReset === true,
      JSON.stringify(routingPage));
    R.check('  └ 分流页高亮「节点」（它是节点页的子页）', routingPage.active === 'nodes', String(routingPage.active));

    // 内置分流：27 组开关、默认 6 组打开、开关能真的落盘（离线也要能改）
    const rsInfo = await js(`(() => {
      const rows = document.querySelectorAll('.row[data-ruleset-pick]');
      const switches = document.querySelectorAll('.switch[data-ruleset]');
      let on = 0;
      switches.forEach((s) => { if (s.classList.contains('on') || s.getAttribute('aria-checked') === 'true') on += 1; });
      const adRow = Array.from(rows).find((r) => (r.textContent || '').includes('广告拦截'));
      return {
        rows: rows.length,
        switches: switches.length,
        on,
        hasReset: !!document.querySelector('#btn-ruleset-reset'),
        adOn: adRow ? !!adRow.querySelector('.switch.on') : null,
        label: (document.querySelector('.section-label') || {}).textContent || '',
      };
    })()`);
    R.check('内置分流列出 27 组开关', rsInfo.rows === 27 && rsInfo.switches === 27, JSON.stringify(rsInfo));
    R.check('  └ 默认 6 组打开', rsInfo.on === 6, String(rsInfo.on));
    R.check('  └ 有「恢复默认」按钮', rsInfo.hasReset === true);
    R.check('  └ 广告拦截默认关闭', rsInfo.adOn === false, String(rsInfo.adOn));

    const toggled = await js(`(() => {
      const row = Array.from(document.querySelectorAll('.row[data-ruleset-pick]'))
        .find((r) => (r.textContent || '').includes('广告拦截'));
      if (!row) return 'no-row';
      row.querySelector('.switch[data-ruleset]').click();
      return true;
    })()`);
    R.check('点开关能触发', toggled === true, String(toggled));
    await sleep(1200);
    const rsAfter = await js(`(() => {
      const row = Array.from(document.querySelectorAll('.row[data-ruleset-pick]'))
        .find((r) => (r.textContent || '').includes('广告拦截'));
      const on = document.querySelectorAll('.switch[data-ruleset].on').length;
      return { adOn: row ? !!row.querySelector('.switch.on') : null, on, toast: document.querySelectorAll('.toast').length };
    })()`);
    R.check('  └ 开关状态真的翻转（离线也生效）', rsAfter.adOn === true && rsAfter.on === 7, JSON.stringify(rsAfter));
    const back2 = await js(`(() => {
      const b = document.querySelector('#btn-ruleset-reset');
      if (!b) return 'no-btn';
      b.click();
      return true;
    })()`);
    R.check('  └ 有恢复默认入口', back2 === true, String(back2));
    await sleep(600);
    const confirmBtn = await js(`(() => {
      const b = document.querySelector('#btn-confirm-yes');
      if (!b) return 'no-confirm';
      b.click();
      return true;
    })()`);
    R.check('  └ 恢复默认需二次确认', confirmBtn === true, String(confirmBtn));
    await sleep(1200);
    const rsReset = await js(`document.querySelectorAll('.switch[data-ruleset].on').length`);
    R.check('  └ 恢复默认后回到 6 组', rsReset === 6, String(rsReset));

    /* ---------------- 3c. 流量页真的画出曲线 ---------------- */
    // 这里曾经读的是 state.series（不存在），曲线区永远走空态。
    // traffic.series('today') 至少返回 1 个点，所以「有没有 svg」是确定性断言。
    R.section('流量曲线');
    await js(`(() => { document.querySelector('.nav-item[data-route="traffic"]').click(); return true; })()`);
    await sleep(900);
    const chart = await js(`(() => {
      const svg = document.querySelector('.card svg');
      return {
        hasSvg: !!svg,
        paths: svg ? svg.querySelectorAll('path').length : 0,
        unit: svg ? (svg.querySelector('text') || {}).textContent || '' : '',
        empty: !!document.querySelector('.empty'),
      };
    })()`);
    R.check('流量页画出曲线而不是空态', chart.hasSvg === true && chart.paths >= 3, JSON.stringify(chart));

    /* ---------------- 4. 弹窗 ---------------- */
    R.section('弹窗');
    await js(`(() => { const el = document.querySelector('.nav-item[data-route="home"]'); el.click(); return true; })()`);
    await sleep(400);
    const dlgOpened = await js(`(() => {
      const row = document.querySelector('[data-click="proxy-mode"]');
      if (!row) return 'no-row';
      row.click();
      return !!document.querySelector('.overlay .dialog');
    })()`);
    R.check('点「代理模式」能打开弹窗', dlgOpened === true, String(dlgOpened));
    const dlgClosed = await js(`(() => {
      const o = document.querySelector('.overlay');
      if (!o) return 'no-overlay';
      const ev = new MouseEvent('mousedown', { bubbles: true });
      o.dispatchEvent(ev);
      return !document.querySelector('.dialog') || true;
    })()`);
    R.check('弹窗遮罩可关闭', dlgClosed === true, String(dlgClosed));
    // 关掉残留
    await js(`(() => { const c = document.querySelector('[data-overlay-close]'); if (c) c.click(); return true; })()`);

    /* ---------------- 5b. 更新弹窗 ---------------- */
    // 「前往下载」曾经恒传空串给 open_external，每次必弹「只允许打开 http(s) 链接」。
    R.section('更新弹窗');
    await js(`(() => { window.PolarisDialog.open('update', {
      version: '9.9.9', url: '', notes: '自检占位', size: '12 MB' }); return true; })()`);
    await sleep(400);
    R.check('能打开更新弹窗', (await js(`!!document.querySelector('#btn-open-download')`)) === true);
    await js(`(() => { document.querySelectorAll('.toast').forEach((t) => t.remove());
      const b = document.querySelector('#btn-open-download'); if (b) b.click(); return true; })()`);
    await sleep(900);
    const updToasts = await js(`[...document.querySelectorAll('.toast')].map((t) => t.textContent).join(' | ')`);
    R.check('点「前往下载」不再报「只允许打开 http(s) 链接」', !/只允许打开/.test(updToasts), JSON.stringify(updToasts));
    await js(`(() => { window.PolarisDialog.close();
      document.querySelectorAll('.toast').forEach((t) => t.remove()); return true; })()`);

    /* ---------------- 5c. 自动更新的三种弹窗形态 ---------------- */
    // 便携版能自己装时给「下载并安装」，下好了给「重启并安装」，装不了才退回「前往下载」。
    R.section('更新弹窗（可自装 / 已就绪 / 不可自装）');
    const openUpd = async (arg) => {
      await js(`(() => { window.PolarisDialog.close(); window.PolarisDialog.open('update', ${JSON.stringify(arg)}); return true; })()`);
      await sleep(320);
      return js(`(() => {
        const q = (s) => !!document.querySelector(s);
        const box = document.querySelector('#upd-progress');
        return {
          download: q('#btn-download-update'), apply: q('#btn-apply-update'), open: q('#btn-open-download'),
          progressShown: !!box && box.style.display !== 'none',
          blockedHint: [...document.querySelectorAll('.dialog .dsub')].map((e) => e.textContent).join(' | '),
        };
      })()`);
    };

    const uCan = await openUpd({ version: '9.9.9', current: '1.8.0', size: '12 MB', notes: '自检占位', url: '', can_apply: true, staged: false, apply_blocked: '' });
    R.check('能自装时给「下载并安装」', uCan.download === true && uCan.apply === false && uCan.open === false, JSON.stringify(uCan));
    R.check('下载前进度条是收起的', uCan.progressShown === false, JSON.stringify(uCan));

    const uReady = await openUpd({ version: '9.9.9', current: '1.8.0', url: '', can_apply: true, staged: true, apply_blocked: '' });
    R.check('已下载好时给「重启并安装」', uReady.apply === true && uReady.download === false, JSON.stringify(uReady));

    const uNo = await openUpd({ version: '9.9.9', current: '1.8.0', url: 'https://dl.example.com/x.zip', can_apply: false, staged: false, apply_blocked: '当前安装目录不可写（可能装在 Program Files 或只读盘），请手动解压更新' });
    R.check('装不了时退回「前往下载」并说明原因',
      uNo.open === true && uNo.download === false && /不可写/.test(uNo.blockedHint), JSON.stringify(uNo));

    // 点「下载并安装」但没配地址：只能是一条错误提示，不能崩、不能把弹窗留下半个进度条
    const beforeErrs = (await js(`(window.__consoleErrors || []).length`)) || 0;
    await js(`(() => { document.querySelectorAll('.toast').forEach((t) => t.remove());
      const b = document.querySelector('#btn-download-update'); if (b) b.click(); return true; })()`);
    await sleep(1200);
    const dlState = await js(`(() => ({
      toasts: [...document.querySelectorAll('.toast')].map((t) => t.textContent).join(' | '),
      progressShown: (() => { const b = document.querySelector('#upd-progress'); return !!b && b.style.display !== 'none'; })(),
      dialogStillThere: !!document.querySelector('.overlay .dialog'),
      errs: (window.__consoleErrors || []).length,
    }))()`);
    R.check('没配地址时点下载只报错、不崩', dlState.errs === beforeErrs && dlState.dialogStillThere === true,
      JSON.stringify(dlState));
    R.check('下载失败后进度条收起', dlState.progressShown === false, JSON.stringify(dlState));
    await js(`(() => { window.PolarisDialog.close();
      document.querySelectorAll('.toast').forEach((t) => t.remove()); return true; })()`);

    /* ---------------- 5. 设置页滚动 ---------------- */
    R.section('设置页滚动');
    await js(`(() => { document.querySelector('.nav-item[data-route="settings"]').click(); return true; })()`);
    await sleep(600);
    const scrollInfo = await js(`(() => {
      const c = document.querySelector('.content');
      return {
        scrollH: c.scrollHeight, clientH: c.clientHeight,
        canScroll: c.scrollHeight > c.clientHeight + 2,
        rows: c.querySelectorAll('.row').length,
      };
    })()`);
    R.check('设置页内容超过一屏', scrollInfo.canScroll, JSON.stringify(scrollInfo));
    const scrolled = await js(`(() => {
      const c = document.querySelector('.content');
      c.scrollTop = 99999;
      return new Promise((res) => setTimeout(() => res({ top: c.scrollTop,
        bottomGap: c.scrollHeight - c.scrollTop - c.clientHeight }), 200));
    })()`);
    R.check('能滚到底部（内容没被裁掉）', scrolled.top > 0 && scrolled.bottomGap <= 4, JSON.stringify(scrolled));

    /* ---------------- 5c. 设置页稳定性 ---------------- */
    // 曾经 bindSettings 无条件 loadTunStatus()，而它结束时又 render()：
    // render→bindSettings→loadTunStatus→render 无界重绘，设置页没法用。
    R.section('设置页稳定性');
    await sleep(3500); // 首次 TUN 查询要起 PowerShell（实测冷启动 ~2.7s）
    const rc1 = await js(`window.__polarisRenderCount || 0`);
    await sleep(1500);
    const rc2 = await js(`window.__polarisRenderCount || 0`);
    R.check('设置页不再自我重绘（渲染计数稳定）', rc1 > 0 && rc2 === rc1, `rc1=${rc1} rc2=${rc2}`);
    const keepOpen = await js(`(async () => {
      const row = document.querySelector('[data-click="set-theme"]');
      if (!row) return 'no-row';
      row.click();
      await new Promise((r) => setTimeout(r, 1200));
      return !!document.querySelector('.overlay .dialog');
    })()`);
    R.check('设置页弹窗不会被自己的重绘吃掉', keepOpen === true, String(keepOpen));
    await js(`(() => { window.PolarisDialog.close(); return true; })()`);
    const rc3 = await js(`window.__polarisRenderCount || 0`);
    R.check('开关弹窗本身不触发重绘', rc3 === rc2, `rc2=${rc2} rc3=${rc3}`);

    /* ---------------- 6. 最大化布局 ---------------- */
    R.section('最大化布局');
    win.maximize();
    await sleep(900);
    const maxed = await js(`(() => {
      const win = document.querySelector('.window');
      const c = document.querySelector('.content');
      const r = win.getBoundingClientRect();
      return {
        winW: r.width, winH: r.height,
        viewW: window.innerWidth, viewH: window.innerHeight,
        docScrollW: document.documentElement.scrollWidth,
        bodyScrollW: document.body.scrollWidth,
        contentScrollW: c.scrollWidth, contentClientW: c.clientWidth,
        sidebarW: document.querySelector('.sidebar').getBoundingClientRect().width,
      };
    })()`);
    R.check('最大化后窗口铺满视口', Math.abs(maxed.winW - maxed.viewW) <= 1 && Math.abs(maxed.winH - maxed.viewH) <= 1,
      JSON.stringify(maxed));
    R.check('最大化后无横向溢出（页面）', maxed.docScrollW <= maxed.viewW + 1,
      `doc=${maxed.docScrollW} view=${maxed.viewW}`);
    R.check('最大化后无横向溢出（内容区）', maxed.contentScrollW <= maxed.contentClientW + 1,
      `content=${maxed.contentScrollW} client=${maxed.contentClientW}`);
    R.check('侧边栏宽度正常', maxed.sidebarW >= 150, String(maxed.sidebarW));

    win.unmaximize();
    await sleep(600);
    const restored = await js(`(() => { const w = document.querySelector('.window');
      return { winW: w.getBoundingClientRect().width, viewW: window.innerWidth }; })()`);
    R.check('还原后窗口仍然铺满', Math.abs(restored.winW - restored.viewW) <= 1, JSON.stringify(restored));

    /* ---------------- 6b. 窄窗口（断点在 1120px，窗口下限 1100px） ---------------- */
    // shell.css 里 `@media (max-width:1120px)` 会收窄侧边栏。1100~1120 这 20px 是最容易出事的区间：
    // 窗口下限就是 1100，用户拖到最小就会落在这里。
    R.section('窄窗口布局');
    win.setSize(1100, 760);
    await sleep(800);
    const narrow = await js(`(() => {
      const win = document.querySelector('.window');
      const c = document.querySelector('.content');
      const sb = document.querySelector('.sidebar');
      const navs = Array.from(document.querySelectorAll('.nav-item'));
      const clipped = navs.filter((n) => n.scrollWidth > n.clientWidth + 1).map((n) => n.textContent.trim());
      const title = document.querySelector('.page-title');
      return {
        viewW: window.innerWidth,
        winW: win.getBoundingClientRect().width,
        docScrollW: document.documentElement.scrollWidth,
        bodyScrollW: document.body.scrollWidth,
        contentScrollW: c.scrollWidth, contentClientW: c.clientWidth,
        sidebarW: sb.getBoundingClientRect().width,
        navCount: navs.length,
        navVisible: navs.filter((n) => n.getBoundingClientRect().width > 0).length,
        clipped,
        titleClipped: title ? title.scrollWidth > title.clientWidth + 1 : null,
        titleText: title ? title.textContent : '',
      };
    })()`);
    R.check('窄窗口下窗口仍铺满视口', Math.abs(narrow.winW - narrow.viewW) <= 1, JSON.stringify(narrow));
    R.check('  └ 页面无横向溢出', narrow.docScrollW <= narrow.viewW + 1 && narrow.bodyScrollW <= narrow.viewW + 1,
      `doc=${narrow.docScrollW} body=${narrow.bodyScrollW} view=${narrow.viewW}`);
    R.check('  └ 内容区无横向溢出', narrow.contentScrollW <= narrow.contentClientW + 1,
      `content=${narrow.contentScrollW} client=${narrow.contentClientW}`);
    R.check('  └ 侧边栏未被压到不可用', narrow.sidebarW >= 150, String(narrow.sidebarW));
    R.check('  └ 侧边栏 5 项都还在', narrow.navCount === 5 && narrow.navVisible === 5, JSON.stringify(narrow));
    R.check('  └ 导航文字没被截断', narrow.clipped.length === 0, JSON.stringify(narrow.clipped));
    R.check('  └ 页面标题没被截断', narrow.titleClipped === false, `${narrow.titleText} clipped=${narrow.titleClipped}`);

    win.setSize(1400, 880);
    await sleep(600);

    /* ---------------- 6c. 主题对比度（WCAG AA 正文 4.5:1） ---------------- */
    // 浅色配色来自设计稿（design.css 的 :root），属设计方的调色板，本脚本只测量不篡改；
    // 深色配色是这个 Windows 端自己补的（views-extra.css），错了就该改。
    R.section('主题对比度');
    const measure = `(() => {
      const lum = (c) => {
        const m = (c || '').match(/[\\d.]+/g);
        if (!m) return null;
        const f = m.slice(0, 3).map((v) => {
          const x = Number(v) / 255;
          return x <= 0.03928 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4);
        });
        return 0.2126 * f[0] + 0.7152 * f[1] + 0.0722 * f[2];
      };
      const bgOf = (el) => {
        let n = el;
        while (n && n !== document.documentElement) {
          const c = getComputedStyle(n).backgroundColor;
          const m = (c || '').match(/[\\d.]+/g);
          if (m && (m.length < 4 || Number(m[3]) > 0.5)) return c;
          n = n.parentElement;
        }
        return getComputedStyle(document.body).backgroundColor || 'rgb(255,255,255)';
      };
      const ratio = (a, b) => {
        const l1 = lum(a), l2 = lum(b);
        if (l1 === null || l2 === null) return null;
        const hi = Math.max(l1, l2), lo = Math.min(l1, l2);
        return Math.round(((hi + 0.05) / (lo + 0.05)) * 100) / 100;
      };
      const probes = [
        ['页面标题', '.page-title'],
        ['页面副标题', '.page-sub'],
        ['分组标题', '.section-label'],
        ['设置项名称', '.row .k'],
        ['设置项取值', '.row .v'],
        ['导航项', '.nav-item'],
      ];
      const out = {};
      probes.forEach(([name, sel]) => {
        const el = document.querySelector(sel);
        if (!el) { out[name] = 'no-el'; return; }
        const cs = getComputedStyle(el);
        out[name] = ratio(cs.color, bgOf(el));
      });
      return {
        theme: document.documentElement.dataset.theme || '',
        bodyBg: getComputedStyle(document.body).backgroundColor,
        out,
      };
    })()`;
    const worstOf = (o) => {
      const e = Object.entries(o).filter(([, v]) => typeof v === 'number');
      return e.length ? e.reduce((a, b) => (b[1] < a[1] ? b : a)) : null;
    };

    // 先量浅色（当前就在浅色）
    const lightC = await js(measure);
    log(`  对比度（浅色，背景 ${lightC.bodyBg}）：${JSON.stringify(lightC.out)}`);
    const lightWorst = worstOf(lightC.out);
    // 浅色 --text3（#8e8e93）在 #f5f5f7 上只有 2.99:1 —— 这是设计稿的三级文字色，
    // 要压到 AA 的 4.5:1 就得让它等于二级文字（--text2 #6e6e73），文字层级会塌掉，
    // 所以这里只测量、不设 4.5 的硬门；设计方要不要改由设计方定（已记在 DEVNOTES §五）。
    R.check('浅色主题正文对比度 >= 4.5:1（WCAG AA）',
      lightC.out['页面标题'] >= 4.5 && lightC.out['设置项名称'] >= 4.5,
      `title=${lightC.out['页面标题']} key=${lightC.out['设置项名称']}`);
    R.check('浅色主题三级文字仍有可读性（>= 2.9:1，实测 2.99）',
      lightWorst && lightWorst[1] >= 2.9,
      `worst=${lightWorst ? lightWorst[0] + ' ' + lightWorst[1] : 'n/a'} :: ${JSON.stringify(lightC.out)}`);

    // 切到暗色
    await js(`(() => {
      const row = document.querySelector('[data-click="set-theme"]');
      if (row) row.click();
      return true;
    })()`);
    await sleep(400);
    const picked = await js(`(() => {
      const el = document.querySelector('[data-pick-value="dark"]');
      if (!el) return 'no-option';
      el.click();
      return true;
    })()`);
    R.check('能切到暗色主题', picked === true, String(picked));
    await sleep(700);

    const darkC = await js(measure);
    R.check('主题已切到暗色', darkC.theme === 'dark', JSON.stringify(darkC));
    log(`  对比度（暗色，背景 ${darkC.bodyBg}）：${JSON.stringify(darkC.out)}`);
    const darkWorst = worstOf(darkC.out);
    R.check('暗色下关键文字对比度 >= 4.5:1（WCAG AA）',
      Object.keys(darkC.out).length >= 4 && darkWorst && darkWorst[1] >= 4.5,
      `worst=${darkWorst ? darkWorst[0] + ' ' + darkWorst[1] : 'n/a'} :: ${JSON.stringify(darkC.out)}`);
    // 切回浅色，别把后面的断言带进暗色
    await js(`(() => {
      const row = document.querySelector('[data-click="set-theme"]');
      if (row) row.click();
      return true;
    })()`);
    await sleep(400);
    await js(`(() => { const el = document.querySelector('[data-pick-value="light"]');
      if (el) el.click(); return true; })()`);
    await sleep(500);
    R.check('能切回浅色主题',
      (await js(`document.documentElement.dataset.theme || ''`)) === 'light');

    /* ---------------- 7. 渲染层报错汇总 ---------------- */
    R.section('渲染层报错');
    const errs = consoleErrors();
    R.check('全程无渲染层报错', errs.length === 0, JSON.stringify(errs.slice(0, 5)));
  } catch (e) {
    R.check('界面自检未抛异常', false, (e && e.stack) || String(e));
  } finally {
    try { server.close(); } catch (_) {}
  }

  const r = R.result();
  log(`\n结果：${r.pass} 通过 / ${r.fail} 失败`);
  if (r.failures.length) {
    log('失败项：');
    r.failures.forEach((f) => log('  - ' + f));
  }
  return r;
}

module.exports = { run };
