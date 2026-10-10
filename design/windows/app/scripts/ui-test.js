'use strict';
/**
 * 真面板 · 真窗口 · 真鼠标点击 的界面自检。
 *
 * 为什么又把它捡回来：用户提的 8 条里有 6 条是**界面层**的（手风琴、返回键、
 * 分流页内容、延迟测试显示、流量页数据、我的页布局）。`real-test.js` 只在主进程里
 * 调 IPC 命令、不开窗口，验不到界面；旧的 `--uitest` 又是配假面板的，只能证明自洽
 * （假面板下建工单也"成功"，真面板却是空工单，见 DEVNOTES A-22）。
 * 所以这里走第三条路：**开真窗口、连真面板、用真账号真登录、用真的鼠标事件去点**。
 *
 * 用法：
 *   electron.exe . --uitest --panel=https://xxx --email=a@b.c --password=***
 *                [--uitest-data=<数据根目录>]   不给就用开发态的 .devdata
 *
 * 退出码：0 = 全过，1 = 有失败（由 main.js 决定）。
 */

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/* ---------------- 驱动渲染层的三个原语 ---------------- */

/** 在页面里求值（返回可 JSON 化的结果） */
async function ev(win, expr) {
  return win.webContents.executeJavaScript(expr, true);
}

/** 轮询直到表达式为真 */
async function waitFor(win, expr, { timeout = 15000, every = 200, label = '' } = {}) {
  const t0 = Date.now();
  let last = null;
  for (;;) {
    try {
      last = await ev(win, expr);
      if (last) return last;
    } catch (e) { last = 'ERR ' + (e && e.message); }
    if (Date.now() - t0 > timeout) {
      throw new Error(`等超时 ${timeout}ms：${label || expr}（最后一次=${JSON.stringify(last)}）`);
    }
    await sleep(every);
  }
}

/**
 * 真的用鼠标点：先滚到可视区，再取中心坐标，发 mouseDown/mouseUp。
 * 不用 element.click() —— 那样点不到「无边框窗口 + 自绘标题栏 + 拖拽区」这类
 * 真实布局问题，也测不出「这个按钮其实被上面的层盖住了」。
 */
async function realClick(win, sel, { nth = 0, label = '' } = {}) {
  const q = JSON.stringify(sel);
  const found = await ev(win, `(()=>{const els=[...document.querySelectorAll(${q})];const el=els[${nth}];
    if(!el) return {ok:false,why:'没找到'};
    el.scrollIntoView({block:'center',inline:'center'});
    const r=el.getBoundingClientRect();
    return {ok:true,w:r.width,h:r.height,text:(el.textContent||'').trim().slice(0,30)};})()`);
  if (!found || !found.ok) throw new Error(`点不到元素 ${sel} ${label}（${found && found.why}）`);
  if (found.w < 2 || found.h < 2) throw new Error(`元素尺寸异常 ${sel} ${label}（${found.w}×${found.h}，可能被隐藏）`);
  // 坐标和"这个坐标最上面的是谁"必须在同一次执行里取。原来分两次 executeJavaScript，
  // 中间隔了一次跨进程往返：这期间页面可能已经 render() 过，元素被换掉，
  // 拿到的旧坐标就点到了别处 —— 表现成"点了没反应/点错了地方"，极难定位（踩过一次）。
  let pt = null;
  for (let attempt = 1; attempt <= 3; attempt++) {
    await sleep(120);
    pt = await ev(win, `(()=>{const el=[...document.querySelectorAll(${q})][${nth}];
      if(!el) return {ok:false,why:'没找到（可能刚被重绘掉）'};
      el.scrollIntoView({block:'center',inline:'center'});
      const r=el.getBoundingClientRect();
      const x=Math.round(r.left+r.width/2), y=Math.round(r.top+r.height/2);
      const hit=document.elementFromPoint(x,y);
      return {ok: !!hit&&(el===hit||el.contains(hit)), x, y,
        why:'该坐标最上面是 <'+((hit&&hit.tagName||'null')+'').toLowerCase()+' class="'+((hit&&hit.className)||'')+'">，目标是 <'+(el.tagName||'').toLowerCase()+'> 文本「'+(el.textContent||'').trim().slice(0,12)+'」'};})()`);
    if (pt && pt.ok) break;
  }
  if (!pt || !pt.ok) throw new Error(`点不到元素 ${sel} ${label}（${(pt && pt.why) || '未知'}）`);
  win.webContents.sendInputEvent({ type: 'mouseMove', x: pt.x, y: pt.y });
  win.webContents.sendInputEvent({ type: 'mouseDown', x: pt.x, y: pt.y, button: 'left', clickCount: 1 });
  await sleep(45);
  win.webContents.sendInputEvent({ type: 'mouseUp', x: pt.x, y: pt.y, button: 'left', clickCount: 1 });
  await sleep(120);
}

/**
 * 真的用鼠标拖：按住 fromSel 的中心，分几步移到 toSel 的中心，再松手。
 * 中途必须**多步移动**——一步到位的 mouseMove 在 Chromium 里只算一次
 * pointermove，界面的拖动逻辑靠"移动过"来判定是否真的在拖。
 */
async function realDrag(win, fromSel, toSel, { label = '' } = {}) {
  const q = (s) => JSON.stringify(s);
  const pt = await ev(win, `(()=>{const a=document.querySelector(${q(fromSel)}),b=document.querySelector(${q(toSel)});
    if(!a||!b) return {ok:false,why:(a?'':'起点没找到 ')+(b?'':'终点没找到')};
    a.scrollIntoView({block:'center',inline:'center'});
    const ra=a.getBoundingClientRect(),rb=b.getBoundingClientRect();
    return {ok:true,ax:Math.round(ra.left+ra.width/2),ay:Math.round(ra.top+ra.height/2),
      bx:Math.round(rb.left+rb.width/2),by:Math.round(rb.top+rb.height/2)};})()`);
  if (!pt || !pt.ok) throw new Error(`拖不动 ${fromSel} → ${toSel} ${label}（${pt && pt.why}）`);
  win.webContents.sendInputEvent({ type: 'mouseMove', x: pt.ax, y: pt.ay });
  win.webContents.sendInputEvent({ type: 'mouseDown', x: pt.ax, y: pt.ay, button: 'left', clickCount: 1 });
  await sleep(60);
  const steps = 6;
  for (let i = 1; i <= steps; i++) {
    const x = Math.round(pt.ax + (pt.bx - pt.ax) * (i / steps));
    const y = Math.round(pt.ay + (pt.by - pt.ay) * (i / steps));
    win.webContents.sendInputEvent({ type: 'mouseMove', x, y, button: 'left' });
    await sleep(35);
  }
  await sleep(60);
  win.webContents.sendInputEvent({ type: 'mouseUp', x: pt.bx, y: pt.by, button: 'left', clickCount: 1 });
  await sleep(200);
}

/**
 * 等界面安静下来：渲染计数连续 quiet 毫秒不再变化。
 * 启动/连上内核之后 refreshAll 还会回来重绘一次（面板查询要几秒），
 * 那次重绘会把"探测坐标 → 点击"之间的元素换掉，点击就被吞了 ——
 * 表现成"点了没反应"，而且只偶发（实测在节点页手风琴与分流页拖动上各中过一次）。
 */
async function settle(win, { quiet = 1500, timeout = 40000 } = {}) {
  const t0 = Date.now();
  let last = -1;
  let lastChange = Date.now();
  for (;;) {
    const c = await ev(win, 'window.__polarisRenderCount || 0');
    if (c !== last) { last = c; lastChange = Date.now(); }
    if (Date.now() - lastChange > quiet) return c;
    if (Date.now() - t0 > timeout) return c;   // 别把整轮测试卡死
    await sleep(250);
  }
}

/** 点一下、等效果；没效果就再点（应对"这一下正好被重绘吞掉"） */
async function clickUntil(win, sel, cond, { tries = 3, timeout = 6000, label = '' } = {}) {
  let err = null;
  for (let i = 1; i <= tries; i++) {
    await realClick(win, sel, { label: `${label}（第 ${i} 次）` });
    try { await waitFor(win, cond, { timeout, label }); return i; } catch (e) { err = e; }
  }
  throw err;
}

async function setInput(win, sel, value) {
  const ok = await ev(win, `(()=>{const el=document.querySelector(${JSON.stringify(sel)});
    if(!el) return false; el.focus(); el.value=${JSON.stringify(value)};
    el.dispatchEvent(new Event('input',{bubbles:true}));
    el.dispatchEvent(new Event('change',{bubbles:true})); return true;})()`);
  if (!ok) throw new Error(`找不到输入框 ${sel}`);
}

const PAGE_TEXT = 'document.body.innerText';

/* ---------------- 主流程 ---------------- */

async function run(win, opts) {
  const panel = opts.panel;
  const email = opts.email;
  const password = opts.password;

  const lines = [];
  let pass = 0, fail = 0;
  const errorsBefore = (win.__consoleErrors || []).length;

  const out = (s) => { lines.push(s); };
  const section = (t) => out(`\n== ${t} ==`);
  const check = (name, cond, detail) => {
    if (cond) { pass++; out(`  PASS  ${name}${detail ? '  ' + detail : ''}`); }
    else { fail++; out(`  FAIL  ${name}${detail ? '  :: ' + detail : ''}`); }
    return !!cond;
  };
  /** 断言包一层：崩了也算失败，不中断整场 */
  const step = async (name, fn) => {
    try { await fn(); } catch (e) { check(name, false, (e && e.message) || String(e)); }
  };

  out(`真面板界面自检  ${new Date().toLocaleString('zh-CN')}`);
  out(`面板 ${panel}   账号 ${email}`);

  /* ---- 0. 外壳 ---- */
  section('0. 窗口外壳');
  await step('外壳加载', async () => {
    await waitFor(win, 'document.readyState === "complete"', { timeout: 20000, label:'页面加载完' });
    check('页面加载完成', true);
    // 抓未捕获异常的堆栈：console-message 只给一行 message，
    // 光看 "Cannot read properties of null (reading 'classList')" 定位不到位置。
    await ev(win, `window.__polarisStacks = [];
      window.addEventListener('error', (e) => {
        try { window.__polarisStacks.push((e.error && e.error.stack) || e.message); } catch (_) {}
      });
      window.addEventListener('unhandledrejection', (e) => {
        try { window.__polarisStacks.push('unhandledrejection: ' + ((e.reason && e.reason.stack) || e.reason)); } catch (_) {}
      });
      true`);
    const navN = await ev(win, 'document.querySelectorAll(".nav-item[data-route]").length');
    check('侧边栏 5 个入口', navN === 5, `实际 ${navN}`);
    const drag = await ev(win, '!!document.querySelector(".titlebar")');
    check('自绘标题栏在', drag);
    const ov = await ev(win, '(()=>{const c=document.querySelector("#content");return c.scrollWidth<=c.clientWidth+1})()');
    check('内容区无横向溢出', ov);
  });

  /* ---- 1. 退出旧会话，保证每次都是真登录 ---- */
  section('1. 登录（真面板 · 真账号 · 走界面）');
  await step('准备登录态', async () => {
    const authed = await ev(win, '!!(window.__polarisState && window.__polarisState.settings.authed)');
    if (authed) {
      // 上一次跑剩的：先在界面上点「退出登录」，顺带把退出流程也测了
      await ev(win, 'window.PolarisNav("me")');
      await waitFor(win, 'window.__polarisRoute === "me"', { timeout: 10000, label:'进我的页' });
      await sleep(300);
      await settle(win);            // 启动时的 refreshAll 还在飞，等它重绘完再点
      // 上一轮这里失败过（点了没反应），先留一份现场，省得下次还要靠猜
      const diag = await ev(win, `(()=>{const el=document.querySelector('[data-click="logout"]');
        if(!el) return {found:false, 总数:document.querySelectorAll('[data-click="logout"]').length};
        const r=el.getBoundingClientRect();
        const x=Math.round(r.left+r.width/2), y=Math.round(r.top+r.height/2);
        const hit=document.elementFromPoint(x,y);
        return {found:true, 坐标:[x,y], 视口高:window.innerHeight,
          滚动:document.querySelector('#content').scrollTop,
          命中:hit?(hit.tagName+'.'+hit.className):'null',
          命中就是它:el===hit||el.contains(hit)};})()`);
      out('  （退出登录按钮现场：' + JSON.stringify(diag) + '）');
      // 记下到底是谁收到了这次点击：光看"点了没反应"分不清是没点到、
      // 点到了别的元素、还是点到了但事件没绑上（三种都真踩过）。
      await ev(win, `window.__clickLog=[];
        document.addEventListener('mousedown', e => { try { window.__clickLog.push('down '+(e.target.className||e.target.tagName)+' | '+(e.target.textContent||'').trim().slice(0,12)); } catch(_){} }, true);
        document.addEventListener('click', e => { try { window.__clickLog.push('click '+(e.target.className||e.target.tagName)+' | '+(e.target.textContent||'').trim().slice(0,12)); } catch(_){} }, true); true`);
      await clickUntil(win, '[data-click="logout"]', '!!document.querySelector("#btn-logout-confirm")',
        { label:'我的页·退出登录', timeout: 5000 });
      const after = await ev(win, `(()=>({route:window.__polarisRoute,
        滚动:document.querySelector('#content').scrollTop,
        点击日志:window.__clickLog,
        遮罩子元素:document.querySelector('#overlay-root').children.length,
        确认按钮:!!document.querySelector('#btn-logout-confirm')}))()`);
      out('  （点完：' + JSON.stringify(after) + '）');
      await waitFor(win, '!!document.querySelector("#btn-logout-confirm")', { timeout: 8000, label:'退出确认框' });
      await realClick(win, '#btn-logout-confirm', { label:'确认退出' });
      out('  （检测到旧会话，已先在界面上退出）');
    }
    await waitFor(win, 'window.__polarisRoute === "login"', { timeout: 15000, label:'停在登录页' });
    check('退出后停在登录页', true);
    const f = await ev(win, '["#login-panel","#login-email","#login-pass","#btn-login"].filter(s=>document.querySelector(s)).length');
    check('登录表单 4 个控件齐全', f === 4, `实际 ${f}/4`);

    await setInput(win, '#login-panel', panel);
    await setInput(win, '#login-email', email);
    await setInput(win, '#login-pass', password);
    await realClick(win, '#btn-login', { label:'登录按钮' });
    await waitFor(win, 'window.__polarisRoute === "home"', { timeout: 40000, label:'登录后进首页' });
    const who = await ev(win, 'window.__polarisState.email');
    check('真登录成功（进首页）', true, `账号 ${who}`);
    const sb = await ev(win, 'getComputedStyle(document.querySelector("#sidebar")).display !== "none"');
    check('登录后侧边栏回来了', sb);
  });

  /* ---- 2. 首页 ---- */
  section('2. 首页（套餐不能是「未订阅」）');
  await step('首页套餐', async () => {
    const p = await ev(win, 'window.__polarisState.plan');
    check('套餐名不是"未订阅"', p && p.name && p.name !== '—' && p.name !== '未订阅', JSON.stringify(p && p.name));
    check('套餐总额有值', p && Number(p.total) > 0, `total=${p && p.total}`);
    check('套餐用量按字节渲染（不是 0 GB）', !!(p && p.used_text), `used_text=${p && p.used_text}`);
    const pw = await ev(win, '!!document.querySelector("#power")');
    check('首页有连接按钮', pw);
    const bar = await ev(win, '(()=>{const d=document.querySelector(".progress > div");return d?d.style.width:""})()');
    // 不能断言 >0%：小用量（14.9 MB / 500 GB）算出来就是 0%，那是对的。
    check('套餐进度条是百分比', /^\d+(\.\d+)?%$/.test(bar), `width=${bar}`);
    const usedTxt = await ev(win, 'document.body.innerText.match(/已使用[^\\n]*/)?.[0] || ""');
    check('首页显示的是真实用量（不是 0 GB）', /已使用/.test(usedTxt) && !/已使用\s*0 GB/.test(usedTxt), usedTxt);
  });

  /* ---- 3. 连接（真内核） ---- */
  section('3. 连接（真内核真配置）');
  await step('点连接', async () => {
    await realClick(win, '#power', { label:'连接按钮' });
    await waitFor(win, `/已连接/.test(document.querySelector(".status-line .t")?.textContent||"")`,
      { timeout: 60000, label:'状态变成已连接' });
    const st = await ev(win, 'window.__polarisState');
    check('界面显示已连接', st.connected === true, `phase=${st.phase}`);
    const nd = await ev(win, 'document.querySelector(".node-line")?.textContent.trim()');
    check('首页显示当前节点', !!nd && nd !== '点击上方按钮开始连接', nd);
    // 连接后主进程还会拉一轮面板数据（几秒），等它回来再进下一段 ——
    // 否则下一段读到的是连接前的本地预览（旧的 config.yaml），会误报"分组重复"。
    await settle(win);
  });

  /* ---- 4. 节点页（用户第 1、4 条） ---- */
  section('4. 节点页（分组手风琴 + 延迟测试）');
  await step('节点页', async () => {
    await realClick(win, '.nav-item[data-route="nodes"]', { label:'侧边栏·节点' });
    await waitFor(win, 'window.__polarisRoute === "nodes"', { label:'进节点页' });
    await waitFor(win, 'document.querySelectorAll(".acc-head[data-acc]").length > 0',
      { timeout: 20000, label:'分组卡出现' }).catch(async (e) => {
      const d = await ev(win, `(()=>{const st=window.__polarisState;
        return {route:window.__polarisRoute,connected:st.connected,phase:st.phase,
          nodes:(st.nodes||[]).length,groups:(st.groups||[]).length,
          accs:document.querySelectorAll('.acc-head').length,
          sub:document.querySelector('.page-sub')?.textContent.trim()||''};})()`);
      throw new Error(`${e.message} | ${JSON.stringify(d)}`);
    });

    const n = await ev(win, `(()=>{const st=window.__polarisState;
      const vis=(st.groups||[]).filter(g=>!g.builtin&&!g.structural);
      const titles=[...document.querySelectorAll('.acc-head[data-acc] .acc-title')].map(t=>t.textContent.trim());
      return {accs:document.querySelectorAll('.acc-head[data-acc]').length,
              want:vis.length,
              first:vis[0]?vis[0].name:'',
              firstShown:titles[0]||'',
              dup:titles.filter(t=>t==='节点选择').length,
              nodes:(st.nodes||[]).length,
              // 「所有 .acc-title 都在可折叠的分组头里」= 旧的扁平重复卡没了
              orphan:[...document.querySelectorAll('.acc-title')].filter(e=>!e.closest('[data-acc]')).length,
              sub:document.querySelector('.page-sub')?.textContent.trim()};})()`);
    check('分组卡数量 = 可见分组数', n.accs === n.want, `页面 ${n.accs} / 状态 ${n.want}（${n.sub}）`);
    check('没有不可折叠的重复「节点选择」卡', n.orphan === 0, `游离标题 ${n.orphan} 个`);
    // 用户第二轮反馈：顶部的重复分类卡不要，主组「🚀 节点选择」要在最上面
    check('主组「🚀 节点选择」是节点页第一张卡', n.first === '🚀 节点选择' && n.firstShown === '🚀 节点选择',
      `状态里第一组=${n.first} / 页面上第一张=${n.firstShown}`);
    check('页面上不存在第二张叫「节点选择」的卡', n.dup === 0, `重名卡 ${n.dup} 张`);
    // 用户第三轮反馈：两张「直连」分类卡重复了，只留一张
    const bothDirect = await ev(win, `(()=>{const st=window.__polarisState;
      const names=(st.groups||[]).filter(g=>!g.builtin&&!g.structural).map(g=>g.name);
      return {cn:names.indexOf('🎯 国内直连')>=0, gl:names.indexOf('🎯 全球直连')>=0,
        direct:names.filter(x=>/直连$/.test(x))};})()`);
    check('两张「直连」分类卡不再并存', !(bothDirect.cn && bothDirect.gl), `直连类分组：${bothDirect.direct.join(' / ') || '无'}`);
    check('节点列表非空', n.nodes > 0, `${n.nodes} 个`);

    // 手风琴：点第一个分组头 → 收起；再点 → 展开
    // 注意 .acc-body 是 .acc-head 的**兄弟**（都在 .card 里），不是子元素 ——
    // 写成 ".acc-head .acc-body" 永远选不中，会把"收起"断言假绿。
    const BODY = '!!document.querySelector(".acc-head[data-acc]").parentElement.querySelector(".acc-body")';
    const first = await ev(win, 'document.querySelector(".acc-head[data-acc]")?.dataset.acc');
    const open1 = await ev(win, BODY);
    check('默认第一个分组是展开的', open1, `分组=${first}`);
    await clickUntil(win, '.acc-head[data-acc]', '!' + BODY, { label:`分组头 ${first}·收起` });
    check('点分组头能收起', true);
    await clickUntil(win, '.acc-head[data-acc]', BODY, { label:`分组头 ${first}·展开` });
    const rows = await ev(win, 'document.querySelectorAll(".acc-body .node-row").length');
    check('展开后里面有节点', rows > 0, `${rows} 行`);

    // 用户第 5 轮第 2 条：主组里的「自动选择 / 故障转移 / DIRECT」不是节点，
    // 显示「其他 / 未测」会让人以为这三个不生效 —— 现在给中文标签 + 当前出口小字。
    const sp = await ev(win, `(()=>{const st=window.__polarisState;
      const head=document.querySelector('.acc-head[data-acc]');
      const body=head.parentElement.querySelector('.acc-body');
      const rows=[...body.querySelectorAll('.node-row')].slice(0,3).map(r=>({
        name:((r.querySelector('.nm')||{}).textContent||'').trim(),
        badge:((r.querySelector('.badge')||{}).textContent||'').trim(),
        sub:((r.querySelector('.nm-sub')||{}).textContent||'').trim()}));
      const auto=(st.groups||[]).find(g=>g.name==='自动选择')||{};
      return {group:head.dataset.acc, rows, autoNow:auto.now||''};})()`);
    check('主组第一张卡里前三个就是特殊出口', sp.group === '🚀 节点选择' && sp.rows.length === 3, JSON.stringify(sp.rows).slice(0, 220));
    check('「自动选择」显示「自动」而不是「未测」', sp.rows[0].name === '自动选择' && sp.rows[0].badge === '自动', JSON.stringify(sp.rows[0]));
    check('「故障转移」显示「备用」而不是「未测」', sp.rows[1].name === '故障转移' && sp.rows[1].badge === '备用', JSON.stringify(sp.rows[1]));
    check('「DIRECT」显示「直连」而不是「未测」', sp.rows[2].name === 'DIRECT' && sp.rows[2].badge === '直连', JSON.stringify(sp.rows[2]));
    // 内核的健康检查是异步的：刚连上那几秒 URLTest/Fallback 的 now 可能还是空的。
    // 空着不显示会让人以为坏了 —— 现在会说「内核正在挑选出口…」，所以这里只要求"有话可说"，
    // 然后轮询等内核真的挑出来（真面板上实测几秒内就有）。
    check('「自动选择」写出了当前出口（或明说还在挑）', sp.rows[0].sub.length > 0, `小字=「${sp.rows[0].sub}」`);
    let autoNow = sp.autoNow;
    if (!autoNow) {
      for (let i = 0; i < 20 && !autoNow; i++) {
        await sleep(1500);
        autoNow = await ev(win, `((window.__polarisState.groups||[]).find(g=>g.name==='自动选择')||{}).now||''`);
      }
    }
    check('内核真的挑出了「自动选择」的出口（不是永远空着）', !!autoNow, `now=${autoNow || '(等 30 秒还是空)'}`);
    if (autoNow) {
      const subNow = await ev(win, `(()=>{const b=document.querySelector('.acc-head[data-acc]').parentElement.querySelector('.acc-body');
        const r=b&&b.querySelector('.node-row');const s=r&&r.querySelector('.nm-sub');return s?s.textContent.trim():'';})()`);
      check('「自动选择」的小字写的就是内核挑中的那个节点', subNow.indexOf(autoNow) >= 0, `小字=「${subNow}」 内核 now=${autoNow}`);
    }

    // 第 4 条：按钮叫「延迟测试」，不是「测速」
    const label = await ev(win, 'document.querySelector("#btn-speedtest")?.textContent.trim()');
    check('按钮文案是「延迟测试」', label === '延迟测试', `实际「${label}」`);
    await realClick(win, '#btn-speedtest', { label:'延迟测试' });
    await waitFor(win, '/测试中/.test(document.querySelector("#btn-speedtest")?.textContent||"")',
      { timeout: 10000, label:'进入测试中' });
    check('点下去真的在跑（不是空按钮）', true);
    await waitFor(win, '!/测试中/.test(document.querySelector("#btn-speedtest")?.textContent||"")',
      { timeout: 120000, label:'延迟测试跑完' });
    const g = await ev(win, `(()=>{const st=window.__polarisState;
      const all=st.groups||[];
      const vis=all.filter(x=>!x.builtin&&!x.structural);
      const byName={}; for(const x of all) byName[x.name]=x;
      const latOf={}; for(const n of (st.nodes||[])) latOf[n.name]=n.latency;
      const EXIT=/^(DIRECT|COMPATIBLE|PASS|REJECT|REJECT-DROP)$/;
      // 分组的出口可能是一个分组（本地方案里分流组首位就是「🚀 节点选择」），要剥到真实出口
      const exitOf=(name)=>{let cur=name,hop=0;while(hop++<6){const gg=byName[cur];if(!gg)return cur;
        if(EXIT.test(gg.now||''))return gg.now;if(!gg.now||gg.now===cur)return '';cur=gg.now;}return '';};
      const rows=vis.map(x=>{const e=exitOf(x.name);return {name:x.name,lat:x.latency,exit:e,
        exitLat:e?(latOf[e]===undefined?null:latOf[e]):null};});
      const direct=rows.filter(r=>EXIT.test(r.exit));
      const node=rows.filter(r=>!EXIT.test(r.exit));
      // 不变式：出口节点的延迟测出来了 → 这个组也必须显示得出来；
      // 出口是直连/拦截 → 徽标必须是「直连/拦截」，不是「未测」。
      const badNode=node.filter(r=>r.exitLat>0&&!(r.lat>0)).map(r=>r.name+'(出口'+r.exit+'='+r.exitLat+'ms 组='+r.lat+')');
      const badDirect=direct.filter(r=>!(r.lat>0)).map(r=>r.name+'('+r.exit+')');
      const cards=[...document.querySelectorAll('.card')].map(c=>{const h=c.querySelector('.acc-head');if(!h)return null;
        const t=(h.querySelector('.acc-title')||{}).textContent||'';const b=(h.querySelector('.badge')||{}).textContent||'';
        return {name:t.trim(),badge:b.trim()};}).filter(Boolean);
      return {rows:rows.length,direct:direct.map(r=>r.name),node:node.length,badNode,badDirect,cards,
        noLat:node.filter(r=>!(r.lat>0)).map(r=>r.name+'→'+(r.exit||'?')).slice(0,6)};})()`);
    check('每个「出口节点测得通」的分组都显示出了延迟', g.badNode.length === 0,
      `${g.rows - g.badNode.length - g.badDirect.length}/${g.rows}${g.badNode.length ? `；没跟上：${g.badNode.slice(0, 4).join('、')}` : ''}${g.noLat.length ? `；出口本身不通（显示未测是对的）：${g.noLat.join('、')}` : ''}`);
    check('直连/拦截型分组显示的是「直连/拦截」而不是「未测」', g.badDirect.length === 0,
      g.direct.length ? `直连/拦截型：${g.direct.slice(0, 4).join('、')}${g.badDirect.length ? `；徽标错的：${g.badDirect.join('、')}` : ''}` : '（本轮没有直连/拦截型分组）');
    const directCards = g.cards.filter((c) => g.direct.indexOf(c.name) >= 0);
    check('直连/拦截型分组头上的徽标就是「直连」或「拦截」',
      directCards.every((c) => c.badge === '直连' || c.badge === '拦截'),
      directCards.map((c) => `${c.name}=${c.badge}`).join(' '));
    const badge = g.cards.map((c) => c.badge);
    check('分组头上看得见延迟', badge.some((t) => /\d+ms/.test(t)), badge.slice(0, 4).join(' '));
  });

  /* ---- 5. 分流规则页（用户第 2、3 条） ---- */
  section('5. 分流规则页（有返回键 · 没有杂质）');
  await step('分流规则', async () => {
    await realClick(win, '[data-click="nav-routing"]', { label:'节点页·分流规则' });
    await waitFor(win, 'window.__polarisRoute === "routing"', { label:'进分流规则页' });
    const back = await ev(win, '!!document.querySelector("[data-nav-back]")');
    check('分流规则页有「‹ 返回」', back);
    const txt = await ev(win, PAGE_TEXT);
    check('页面上没有「策略组出口」这类杂质', txt.indexOf('策略组出口') < 0);
    check('只剩自定义分流组与内置分流', txt.indexOf('自定义分流组') >= 0 && txt.indexOf('内置分流') >= 0);
    const sw = await ev(win, 'document.querySelectorAll(".switch[data-ruleset]").length');
    check('内置分流开关渲染出来了', sw > 0, `${sw} 个`);

    // 本地分流总开关（与安卓端同一模型：屏蔽面板下发的分流方案）
    const lrOn = await ev(win, '(()=>{const e=document.querySelector(".switch[data-click=\'local-routing\']");return !!e && e.classList.contains("on");})()');
    check('有「使用本地分流方案」开关且默认打开', lrOn === true);
    const lrTxt = await ev(win, PAGE_TEXT);
    check('页面上说明了它会屏蔽面板下发的规则', lrTxt.indexOf('屏蔽面板下发的分流规则') >= 0);
    // 关掉再打开：验证开关真的能切换（顺带验证降级回面板方案再切回来不会把连接弄断）
    await realClick(win, '.switch[data-click="local-routing"]', { label: '关掉本地分流' });
    await sleep(1200);
    await settle(win, { quiet: 900, timeout: 20000 });
    const lrOff = await ev(win, '(()=>{const e=document.querySelector(".switch[data-click=\'local-routing\']");return !!e && !e.classList.contains("on");})()');
    check('关掉后开关是关的状态（面板方案生效）', lrOff === true);
    await realClick(win, '.switch[data-click="local-routing"]', { label: '打开本地分流' });
    await sleep(1200);
    await settle(win, { quiet: 900, timeout: 20000 });
    const lrBack = await ev(win, '(()=>{const e=document.querySelector(".switch[data-click=\'local-routing\']");return !!e && e.classList.contains("on");})()');
    check('再打开又回到本地分流方案（不留测试状态）', lrBack === true);
    const stillOn = await ev(win, '!!(window.__polarisState && window.__polarisState.connected)');
    check('切换分流方案没有把连接弄断', stillOn === true);

    // 顺序：拖动而不是 ↑↓ 按钮（用户明确要求）
    const mv = await ev(win, 'document.querySelectorAll("[data-ruleset-move]").length');
    check('没有上下移动按钮了（改成拖动）', mv === 0, `还剩 ${mv} 个`);
    const grips = await ev(win, 'document.querySelectorAll("[data-ruleset-grip]").length');
    check('每行都有拖动手柄', grips >= 2, `${grips} 个`);
    const order0 = await ev(win, '[...document.querySelectorAll(".ruleset-row")].map(e=>e.dataset.rulesetRow)');
    check('内置分流有可排序的多行', order0.length >= 3, `${order0.length} 行`);
    // 被拖的必须是**已启用**的组：节点页只显示启用的分流组，拖一个关掉的组到第一，
    // 节点页根本看不见它（第一版断言就是这么假红的）。
    const enabled0 = await ev(win, `[...document.querySelectorAll('.ruleset-row')].filter(r=>{const s=r.querySelector('.switch[data-ruleset]');return !!s&&s.classList.contains('on');}).map(r=>r.dataset.rulesetRow)`);
    check('有至少两个已启用的分流组可以拖', enabled0.length >= 2, `启用 ${enabled0.length} 个：${enabled0.slice(0, 3).join('、')}`);
    const moving = enabled0[1] || order0[2];
    const orderExpr = `[...document.querySelectorAll(".ruleset-row")].map(e=>e.dataset.rulesetRow)`;
    for (let i = 1; i <= 3; i++) {
      await settle(win, { quiet: 900, timeout: 15000 });
      await realDrag(win, `[data-ruleset-grip="${moving}"]`, `[data-ruleset-row="${order0[0]}"]`, { label: `拖动 ${moving}（第 ${i} 次）` });
      try {
        await waitFor(win, `(()=>{const r=${orderExpr};return r[0]===${JSON.stringify(moving)};})()`,
          { timeout: 12000, label: '拖动后顺序生效' });
        break;
      } catch (e) { if (i === 3) throw e; }
    }
    const order1 = await ev(win, orderExpr);
    check('拖动真的改了匹配顺序（被拖的排到第一）', order1[0] === moving, `${order0.slice(0, 4).join(' | ')} → ${order1.slice(0, 4).join(' | ')}`);
    check('拖动没有丢行', order1.length === order0.length, `${order0.length} → ${order1.length}`);

    // 用户第 5 轮第 3 条：「分组调好了位置，为什么外面的节点也不跟着变？」
    // 节点页的分组卡顺序来自主进程的 getRoutingGroups()，必须与分流页同一顺序源。
    // 这里在"拖到第一位"的状态下直接问节点页的数据源，看它有没有跟着改。
    await sleep(800);
    // 注意要排掉主组「🚀 节点选择」：它是配置骨架（永远排第一），不在分流表里，
    // 也不参与拖动排序 —— 不排掉的话第一个永远是它，断言假红。
    const nodesOrder = await ev(win, 'window.PolarisAPI.getRoutingGroups().then(gs=>(gs||[]).filter(g=>!g.builtin&&!g.structural&&g.name!=="🚀 节点选择").map(g=>g.name))');
    check('节点页的分组顺序跟着分流顺序变（拖到第一的组在节点页也排第一）',
      Array.isArray(nodesOrder) && nodesOrder[0] === moving,
      `节点页前 4: ${(nodesOrder || []).slice(0, 4).join(' | ')}`);

    // 拖回原位，别把用户的顺序留在测试状态
    await realDrag(win, `[data-ruleset-grip="${moving}"]`, `[data-ruleset-row="${order0[1]}"]`, { label: `拖回 ${moving}` });
    await sleep(600);

    await realClick(win, '[data-nav-back]', { label:'返回键' });
    await waitFor(win, 'window.__polarisRoute === "nodes"', { label:'返回到节点页' });
    check('点返回真的退回上一页', true);

    // 还原之后，节点页的分组顺序也必须跟着回退（顺序源是同一个，不能只改一边）
    await settle(win, { quiet: 900, timeout: 15000 });
    const nodesBack = await ev(win, `(()=>{const st=window.__polarisState;
      const titles=[...document.querySelectorAll('.acc-head[data-acc] .acc-title')].map(t=>t.textContent.trim());
      const rs=(((st.rulesets||{}).groups)||[]).map(g=>g.name);
      const got=titles.filter(n=>rs.indexOf(n)>=0);
      const want=rs.filter(n=>titles.indexOf(n)>=0);
      return {got, want};})()`);
    check('节点页卡片顺序 = 分流页列表顺序（两个界面同一个顺序源）',
      JSON.stringify(nodesBack.got) === JSON.stringify(nodesBack.want),
      `页面 ${nodesBack.got.slice(0, 5).join(' | ')} ←→ 分流 ${nodesBack.want.slice(0, 5).join(' | ')}`);
  });

  /* ---- 6. 流量页（用户第 5 条） ---- */
  section('6. 流量页（今天/本周/本月取面板明细）');
  await step('流量页', async () => {
    await realClick(win, '.nav-item[data-route="traffic"]', { label:'侧边栏·流量' });
    await waitFor(win, 'window.__polarisRoute === "traffic"', { label:'进流量页' });
    const read = async (range) => {
      const before = await ev(win, `(()=>({route:window.__polarisRoute,segs:document.querySelectorAll('[data-range]').length}))()`);
      // 分两步等，别混在一起：①按钮高亮（点下去立刻就有，没有就说明这一下被重绘吞了 → 再点）
      // ②数据真的换成了这个区间（state.traffic.range 由 get_traffic 回显，面板明细慢，给足时间）。
      // 混在一起等会分不清"点没点中"和"数据慢"——实测有一次 month 等了 90 秒，
      // 事后查日志那一下根本没发出 get_traffic，是点被吞了。
      const tries = await clickUntil(win, `[data-range="${range}"]`,
        `(()=>{const el=document.querySelector('[data-range="${range}"]');return !!el&&el.classList.contains('btn-primary');})()`,
        { tries: 3, timeout: 4000, label: `区间 ${range} 按钮高亮` });
      check(`点「${range}」按钮真的生效了`, true, tries > 1 ? `第 ${tries} 次才点中（前一次被重绘吞了）` : '');
      try {
        await waitFor(win, `(()=>{const el=document.querySelector('[data-range="${range}"]');
          const t=window.__polarisState.traffic||{};
          return !!el&&el.classList.contains('btn-primary')&&t.range==='${range}';})()`,
          { timeout: 60000, label:`切到 ${range} 的数据` });
      } catch (e) {
        const after = await ev(win, `(()=>({route:window.__polarisRoute,segs:document.querySelectorAll('[data-range]').length,
          title:document.querySelector('.page-title')?.textContent.trim()||'',bodyLen:document.body.innerHTML.length,
          tr:window.__polarisState.traffic&&window.__polarisState.traffic.range}))()`);
        throw new Error(`${e.message} | 点前 ${JSON.stringify(before)} 点后 ${JSON.stringify(after)}`);
      }
      return ev(win, `(()=>{const st=window.__polarisState;const t=st.traffic||{};const site=t.site||null;
        const labels=[...document.querySelectorAll('.section-label')].map(e=>e.textContent.trim());
        return {site, label:labels[0]||'', notLogged:t.site?0:1};})()`);
    };
    for (const r of ['today', 'week', 'month']) {
      const d = await read(r);
      check(`${r} 区间的数据来自面板（站点用量）`, !d.notLogged && /站点用量/.test(d.label), d.label);
      check(`${r} 区间有合计值`, !!(d.site && d.site.total_text), d.site ? `${d.site.total_text} / ${d.site.days} 天` : '无');
    }
  });

  /* ---- 7. 我的页（用户第 6、7 条） ---- */
  section('7. 我的页（套餐/订单读真数据 · 入口各归各位）');
  await step('我的页', async () => {
    await realClick(win, '.nav-item[data-route="me"]', { label:'侧边栏·我的' });
    await waitFor(win, 'window.__polarisRoute === "me"', { label:'进我的页' });
    const badge = await ev(win, 'document.querySelector(".card .badge")?.textContent.trim()');
    check('我的页徽标不是「未订阅」', badge && badge !== '未订阅', `「${badge}」`);
    const body = await ev(win, PAGE_TEXT);
    check('我的页没有「分流规则」入口（已挪到设置）', body.indexOf('分流规则') < 0);
    // 用户第 5 条：自己的邮箱不打码
    const mail = await ev(win, 'window.__polarisState.email');
    check('我的页邮箱不打码', !!mail && mail.indexOf('*') < 0 && mail.indexOf('@') > 0, `「${mail}」`);
    // 与手机端「我的服务」逐项对齐（用户第 2 条）
    for (const [k, label] of [['nav-plans', '订阅套餐'], ['nav-giftcard', '礼品卡兑换'], ['nav-orders', '我的订单'],
      ['nav-invite', '邀请返利'], ['nav-tickets', '我的工单'], ['nav-notices', '公告通知']]) {
      const has = await ev(win, `!!document.querySelector('[data-click="${k}"]')`);
      check(`我的页有「${label}」入口`, has);
      const txt = await ev(win, `document.querySelector('[data-click="${k}"]')?.textContent||''`);
      check(`「${label}」文案与手机端一致`, txt.indexOf(label) >= 0, `实际「${txt.trim().slice(0, 20)}」`);
    }
    // Telegram：面板下发了链接就必须有入口（安卓端同样逻辑）
    const tg = await ev(win, '(window.__polarisState.siteInfo||{}).telegramUrl||""');
    const tgRow = await ev(win, '!!document.querySelector(\'[data-click="open-telegram"]\')');
    check('面板给了 Telegram 链接就有入口（没给就隐藏）', tg ? tgRow : !tgRow, tg ? `链接 ${tg}` : '面板未配置');
  });

  section('7b. 二级页面（都有返回键 · 数据是面板的）');
  const SECOND = [
    { click: '[data-click="nav-orders"]', route: 'orders', title: '我的订单', need: (d) => d.orders && d.orders.length > 0 },
    // 用户第 5 轮第 4 条：入口写「订阅套餐」，点进去也必须写「订阅套餐」（原来页头是「购买套餐」）
    { click: '[data-click="nav-plans"]', route: 'plans', title: '订阅套餐', need: (d) => d.plans && d.plans.length > 0 },
    { click: '[data-click="nav-tickets"]', route: 'tickets', title: '我的工单', need: null },
    { click: '[data-click="nav-invite"]', route: 'invite', title: '邀请返利', need: null },
    { click: '[data-click="nav-giftcard"]', route: 'giftcard', title: '礼品卡兑换', need: null },
    { click: '[data-click="nav-notices"]', route: 'notices', title: '公告通知', need: (d) => d.notices && d.notices.length > 0 },
  ];
  for (const s of SECOND) {
    await step(`二级页 ${s.title}`, async () => {
      await ev(win, 'window.PolarisNav("me")');
      await sleep(350);
      // 入口文案与页头标题必须逐字一致（用户第 5 轮第 4 条报的就是这个）
      const entry = await ev(win, `(()=>{const e=document.querySelector(${JSON.stringify(s.click)});
        return e?e.textContent.replace(/\\s+/g,'').trim():'';})()`);
      check(`「我的」页入口文案是「${s.title}」`, entry.indexOf(s.title) >= 0, `实际「${entry}」`);
      await realClick(win, s.click, { label:`我的页·${s.title}` });
      await waitFor(win, `window.__polarisRoute === "${s.route}"`, { timeout: 15000, label:`进 ${s.title}` });
      const back = await ev(win, '!!document.querySelector("[data-nav-back]")');
      check(`${s.title} 有「‹ 返回」`, back);
      const t = await ev(win, 'document.querySelector(".page-title")?.textContent.trim()');
      check(`${s.title} 标题正确`, t === s.title, `实际「${t}」`);
      if (s.need) {
        const d = await ev(win, 'window.__polarisState');
        check(`${s.title} 读到面板数据`, s.need(d), JSON.stringify({
          orders: (d.orders || []).length, plans: (d.plans || []).length, notices: (d.notices || []).length,
        }));
      }

      // 用户第 5 轮第 5 条：套餐说明是面板下发的 Markdown，必须渲染而不是原样吐出来
      if (s.route === 'plans') {
        const mi = await ev(win, `(()=>{const st=window.__polarisState;
          const raw=(st.plans||[]).map(p=>String(p.content||'')).filter(x=>x.trim());
          const box=document.querySelector('.plan-md');
          return {rawCount:raw.length, box:!!box,
            blocks:document.querySelectorAll('.plan-md .md-h,.plan-md .md-list,.plan-md .md-table-wrap,.plan-md .md-hr,.plan-md .md-p').length,
            links:document.querySelectorAll('.plan-md .md-link').length,
            text:(box?box.textContent:'').replace(/\\s+/g,' ').slice(0,80)};})()`);
        check('套餐说明按 Markdown 渲染出块级标签', mi.rawCount === 0 || (mi.box && mi.blocks > 0), JSON.stringify(mi));
        check('套餐说明里的链接渲染成可点的 md-link', mi.rawCount === 0 || mi.links > 0, JSON.stringify(mi));
        check('套餐卡里看不到残留的 Markdown 记号（** / |--- / 裸链接）',
          mi.rawCount === 0 || !/[*]{2}|[|]---|\]\(/.test(mi.text), JSON.stringify(mi.text));
      }

      // 公告详情：正文也是 Markdown（真面板那条带 # 标题、* 列表、|表格|）
      if (s.route === 'notices') {
        await realClick(win, '.row[data-notice="0"]', { label:'打开第一条公告' });
        await waitFor(win, '!!document.querySelector(".overlay .md")', { timeout: 10000, label:'公告正文渲染' });
        const mi = await ev(win, `(()=>{const o=document.querySelector('.overlay');const md=o.querySelector('.md');
          return {md:!!md, blocks:o.querySelectorAll('.md-h,.md-list,.md-table-wrap,.md-hr,.md-quote,.md-p').length,
            links:o.querySelectorAll('.md-link').length,
            rawMarks:o.querySelectorAll('.md').length ? /(^|\\s)#{1,6}\\s|\\*\\*|\\|---/.test(md.textContent) : true};})()`);
        check('公告正文渲染出块级标签（标题/列表/表格）', mi.md && mi.blocks > 0, JSON.stringify(mi));
        check('公告正文里没有残留的 Markdown 记号（# 标题 / ** / |---）', mi.rawMarks === false, JSON.stringify(mi));
        await realClick(win, '.overlay [data-overlay-close]', { label:'关掉公告' });
        await sleep(250);
      }

      await realClick(win, '[data-nav-back]', { label:`${s.title}·返回` });
      await waitFor(win, 'window.__polarisRoute === "me"', { label:'退回我的页' });
      check(`${s.title} 返回键有效`, true);
    });
  }

  /* ---- 8. 设置页（入口该在哪在哪 · 用户第 7 条） ---- */
  section('8. 设置页（分流与订阅归这里）');
  await step('设置页', async () => {
    await realClick(win, '.nav-item[data-route="settings"]', { label:'侧边栏·设置' });
    await waitFor(win, 'window.__polarisRoute === "settings"', { timeout: 15000, label:'进设置页' });
    check('点侧边栏真的到了设置页', true, await ev(win, 'document.querySelector(".page-title")?.textContent.trim()'));
    const has = await ev(win, '!!document.querySelector(\'[data-click="nav-routing"]\')');
    check('设置页不再有「分流规则」入口（与节点页重复，用户第 3 条）', !has);
    const hasSub = await ev(win, '!!document.querySelector(\'[data-click="show-subscribe-url"]\')');
    check('设置页有「订阅链接」入口', hasSub);
    const txt = await ev(win, PAGE_TEXT);
    check('设置页有「分流与订阅」分段', txt.indexOf('分流与订阅') >= 0);
    // 用户第 4 条：设置里不要「面板」段
    const labels = await ev(win, '[...document.querySelectorAll(".section-label")].map(e=>e.textContent.trim())');
    check('设置页没有「面板」分段', labels.indexOf('面板') < 0, labels.join(' / '));
    check('设置页没有「面板地址」行', txt.indexOf('面板地址') < 0);
    check('设置页没有「当前账号」行', txt.indexOf('当前账号') < 0);
  });

  /* ---- 9. 渲染层报错 ---- */
  section('9. 渲染层');
  await step('console', async () => {
    const errs = (win.__consoleErrors || []).slice(errorsBefore);
    check('全程渲染层没有 error', errs.length === 0, errs.slice(0, 5).join(' | '));
    const stacks = await ev(win, '(window.__polarisStacks || []).slice(0, 5)');
    if (stacks && stacks.length) out('  （未捕获异常堆栈：\n    ' + stacks.join('\n    ') + '）');
  });

  /* ---- 10. 收尾：断开（不退出登录，方便你接着看） ---- */
  section('10. 收尾');
  await step('断开', async () => {
    await ev(win, 'window.PolarisNav("home")');
    await sleep(300);
    const st = await ev(win, 'window.__polarisState.connected');
    if (st) {
      await realClick(win, '#power', { label:'断开按钮' });
      await waitFor(win, '!/已连接/.test(document.querySelector(".status-line .t")?.textContent||"连接中…")',
        { timeout: 30000, label:'断开' });
      check('断开成功', true);
    } else {
      check('断开成功（本来就没连）', true);
    }
  });

  out(`\n结果：${pass} 通过 / ${fail} 失败`);
  return { pass, fail, lines };
}

module.exports = { run };
