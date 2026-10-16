'use strict';
/**
 * 弹窗与重绘自测（纯 Node + vm + 最小假 DOM，不联网、不碰真面板）。
 *   用法：node scripts/dialog-render-test.js
 *
 * 覆盖两条缺陷：
 *   W4 views.js 的 Dialogs 对象里有两个同名 notice 键，后者覆盖前者 ——
 *      公告弹窗因此丢掉了日期与 Markdown（拿到的是「端口被占用」那套模板）。
 *      现在第二份改名 portNotice，两边必须各自都在、且各带各的内容。
 *   W5 scheduleRender 漏了「弹窗开着时不重绘」的保护（live 状态回调一直有）：
 *      render() 第一行就是 overlayRoot.innerHTML = ""，会把用户正在看的弹窗抹掉。
 *      修法是弹窗期间只记账、closeDialog() 时补绘那次 —— 数据不能因此停更。
 */
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const APP = path.join(__dirname, '..');
const read = (p) => fs.readFileSync(path.join(APP, p), 'utf8');

let pass = 0;
let fail = 0;
function check(name, ok, detail) {
  if (ok) { pass += 1; console.log('  [ OK ] ' + name + (detail ? '  ' + detail : '')); }
  else { fail += 1; console.log('  [FAIL] ' + name + '  ' + (detail || '')); }
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/* ---------------- 最小假 DOM ----------------
 * 只要撑住 app.js 用到的那些：querySelector 永不返回 null（每个选择器缓存同一个
 * 桩元素），innerHTML 的 setter 会把 children 重建 —— overlayRoot.children.length
 * 正是弹窗保护判断的那个量。
 */
const doc = {
  readyState: 'loading',
  _cache: new Map(),
  querySelector(sel) {
    if (!doc._cache.has(sel)) doc._cache.set(sel, makeEl('div'));
    return doc._cache.get(sel);
  },
  querySelectorAll() { return []; },
  addEventListener() {},
  removeEventListener() {},
  createElement(tag) { return makeEl(tag); },
};
doc.body = makeEl('body');
doc.documentElement = makeEl('html');

function makeEl(tag) {
  const el = {
    tagName: String(tag || 'div').toUpperCase(),
    children: [], childNodes: [], dataset: {}, style: {},
    parentNode: null, _html: '', _text: '',
    classList: {
      _s: new Set(),
      add() { Array.prototype.forEach.call(arguments, (c) => this._s.add(c)); },
      remove() { Array.prototype.forEach.call(arguments, (c) => this._s.delete(c)); },
      toggle(c, on) { if (on === undefined) on = !this._s.has(c); on ? this._s.add(c) : this._s.delete(c); return on; },
      contains(c) { return this._s.has(c); },
    },
    addEventListener() {}, removeEventListener() {}, dispatchEvent() { return true; },
    appendChild(c) { c.parentNode = el; el.children.push(c); el.childNodes.push(c); return c; },
    insertBefore(c) { c.parentNode = el; el.children.unshift(c); el.childNodes.unshift(c); return c; },
    removeChild(c) {
      const i = el.children.indexOf(c);
      if (i >= 0) { el.children.splice(i, 1); el.childNodes.splice(el.childNodes.indexOf(c), 1); c.parentNode = null; }
      return c;
    },
    append() { Array.prototype.forEach.call(arguments, (c) => el.appendChild(c)); },
    remove() { if (el.parentNode) el.parentNode.removeChild(el); },
    setAttribute() {}, getAttribute() { return null; }, removeAttribute() {},
    closest() { return null; },
    contains(c) { return el.children.indexOf(c) >= 0; },
    querySelector(sel) { return doc.querySelector(sel); },
    querySelectorAll() { return []; },
    focus() {}, blur() {}, click() {}, scrollIntoView() {},
  };
  Object.defineProperty(el, 'innerHTML', {
    get() { return el._html; },
    set(v) {
      el._html = String(v);
      el.children = [];
      el.childNodes = [];
      if (el._html) el.children.push(makeEl('div'));   // 非空才算「有内容」
    },
  });
  Object.defineProperty(el, 'textContent', {
    get() { return el._text; },
    set(v) { el._text = String(v); },
  });
  return el;
}

/* ---------------- 在 vm 里按 index.html 的顺序加载真实脚本 ---------------- */
const win = {};
win.window = win;
win.document = doc;
win.polaris = { invoke: () => Promise.resolve(null) };
win.matchMedia = () => ({ matches: false, addEventListener() {}, addListener() {} });
win.addEventListener = () => {};

const sandbox = {
  window: win, document: doc, console,
  Date, Math, JSON, Map, Set, Array, Number, String, Object, Boolean, Error, RegExp, Promise,
  setTimeout, clearTimeout, setInterval, clearInterval,
  requestAnimationFrame: (fn) => setTimeout(fn, 0),
  parseInt, parseFloat, isNaN, encodeURIComponent, decodeURIComponent,
};
sandbox.globalThis = sandbox;
vm.createContext(sandbox);
for (const f of ['src/js/format.js', 'src/js/flags.js', 'src/js/md.js', 'src/js/api.js', 'src/js/views.js', 'src/js/app.js']) {
  vm.runInContext(read(f), sandbox, { filename: f });
}

const Dialogs = win.PolarisDialogs;
const overlayRoot = doc.querySelector('#overlay-root');
// 旧代码根本没有 window.PolarisScheduleRender（这是 W5 修复时加的自检出口）：
// 直接调用会让自检自己 TypeError 崩掉，失败信息只剩一句「自检自身崩了」，
// 看不出是「出口没加」还是「保护没生效」。这里退化成空函数，让各条断言各自干净地红/绿。
const sched = typeof win.PolarisScheduleRender === 'function' ? win.PolarisScheduleRender : () => {};

(async () => {
  check('脚本加载后 PolarisDialogs / PolarisDialog / PolarisScheduleRender 都在',
    !!Dialogs && !!win.PolarisDialog && typeof win.PolarisScheduleRender === 'function');

  /* ---------- W4：两个模板各归各位 ---------- */
  check('W4 Dialogs.notice 还在（公告模板）', typeof Dialogs.notice === 'function');
  check('W4 Dialogs.portNotice 存在（端口占用模板，原来是第二个 notice 键）',
    typeof Dialogs.portNotice === 'function');
  check('W4 两个模板不再是同一个函数（旧代码第二个键把第一个覆盖掉了）',
    Dialogs.notice !== Dialogs.portNotice);

  const noticeArg = { title: '维护公告', date: '2026-10-14', body: '今晚 **02:00** 维护' };
  // 模板缺失时不做 typeof 守卫的话，自检自己先 TypeError 崩掉，失败信息只剩一句
  // 「自检自身崩了」，看不出到底是哪个模板没了（旧代码第二个 notice 键覆盖第一个）。
  const noticeHtml = typeof Dialogs.notice === 'function' ? String(Dialogs.notice(noticeArg)) : '';
  check('W4 公告模板带标题', noticeHtml.indexOf('维护公告') >= 0);
  check('W4 公告模板带日期（旧代码被端口占用模板覆盖，没有日期）',
    noticeHtml.indexOf('2026-10-14') >= 0, noticeHtml.slice(0, 80));
  check('W4 公告模板走 Markdown 渲染（正文进 class="md" 的容器、裸 ** 已被渲染成 <b>）',
    noticeHtml.indexOf('class="md"') >= 0 && noticeHtml.indexOf('**') < 0 && noticeHtml.indexOf('<b>02:00</b>') >= 0,
    noticeHtml.slice(noticeHtml.indexOf('class="md"'), noticeHtml.indexOf('class="md"') + 120));

  const portArg = { title: '本机代理端口被占用', hint: '端口 7890 被其它程序占用' };
  const portHtml = typeof Dialogs.portNotice === 'function' ? String(Dialogs.portNotice(portArg)) : '';
  check('W4 端口占用模板带标题', portHtml.indexOf('本机代理端口被占用') >= 0);
  check('W4 端口占用模板保留 hint（原来 hint 就在这一份里，改名不许弄丢）',
    portHtml.indexOf('端口 7890 被其它程序占用') >= 0, portHtml.slice(0, 80));

  /* ---------- PR-A 硬条件：摘要取不到时更新弹窗必须显示「本次未做包校验」 ---------- */
  const updArg = { version: '1.12.2', current: '1.12.1', notes: '修了些东西', sha_state: 'missing', sha_reason: '发布侧摘要里没有 Polaris-portable-1.12.2.zip', sha_streak: 1 };
  const updHtml = typeof Dialogs.update === 'function' ? String(Dialogs.update(updArg)) : '';
  check('PR-A 更新弹窗：摘要缺失时显示「本次未做包校验」', updHtml.indexOf('本次未做包校验') >= 0, updHtml.slice(0, 120));
  check('PR-A 更新弹窗：说清「取不到摘要 ≠ 包有问题，但本次没有校验」', updHtml.indexOf('≠ 包有问题') >= 0 && updHtml.indexOf('但本次没有校验') >= 0);
  check('PR-A 更新弹窗：带上人话原因', updHtml.indexOf('发布侧摘要里没有') >= 0);
  const updEsc = typeof Dialogs.update === 'function' ? String(Dialogs.update(Object.assign({}, updArg, { sha_streak: 3 }))) : '';
  check('PR-A 更新弹窗：连续 3 次未校验 → 换醒目说法（含「连续 3 次」）', updEsc.indexOf('连续 3 次未做包校验') >= 0 && updEsc.indexOf('留意下载来源') >= 0, updEsc.slice(0, 140));
  const updOk = typeof Dialogs.update === 'function' ? String(Dialogs.update({ version: '1.12.2', current: '1.12.1', notes: 'x', sha_state: 'ok' })) : '';
  check('PR-A 更新弹窗：摘要正常时不显示该提示', updOk.indexOf('本次未做包校验') < 0);

  /* ---------- W5：弹窗开着时不重绘，关掉补绘 ---------- */
  win.PolarisRender();                       // 基线渲染
  const base = win.__polarisRenderCount;
  check('W5 基线渲染计数可用', base > 0, 'renderCount=' + base);

  win.PolarisDialog.open('notice', noticeArg);
  check('W5 打开公告弹窗后 overlayRoot 非空', overlayRoot.children.length > 0,
    'children=' + overlayRoot.children.length);

  sched();               // 面板数据回来了（旧代码：直接抹掉弹窗）
  await sleep(80);
  check('W5 弹窗开着时 scheduleRender 不重绘（overlayRoot 没被清空）',
    overlayRoot.children.length > 0, 'children=' + overlayRoot.children.length);
  check('W5 弹窗开着时 scheduleRender 只记账、不多算渲染次数',
    win.__polarisRenderCount === base, 'renderCount=' + win.__polarisRenderCount + ' base=' + base);

  sched();               // 连续两次也只该攒一次
  await sleep(80);
  check('W5 弹窗开着时连打两次 scheduleRender 也只攒一次',
    win.__polarisRenderCount === base && overlayRoot.children.length > 0);

  win.PolarisDialog.close();
  check('W5 closeDialog 清空弹窗', overlayRoot.children.length === 0,
    'children=' + overlayRoot.children.length);
  check('W5 closeDialog 补上被推迟的那次重绘（数据不会因此停更）',
    win.__polarisRenderCount === base + 1,
    'renderCount=' + win.__polarisRenderCount + ' base=' + base);

  sched();
  await sleep(80);
  check('W5 补绘后 pending 标志复位，之后 scheduleRender 恢复正常重绘',
    win.__polarisRenderCount === base + 2, 'renderCount=' + win.__polarisRenderCount);
  check('W5 恢复后仍会对新的 scheduleRender 去重（30ms 内多次只绘一次）',
    await (async () => {
      const b = win.__polarisRenderCount;
      sched(); sched(); sched();
      await sleep(80);
      return win.__polarisRenderCount === b + 1;
    })());

  // 弹窗开着期间没有弹窗时不该误攒（普通路径仍要正常绘）
  win.PolarisRender();
  const b2 = win.__polarisRenderCount;
  sched();
  await sleep(80);
  check('W5 没有弹窗时 scheduleRender 照常立即重绘', win.__polarisRenderCount === b2 + 1);

  console.log('');
  console.log(pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})().catch((e) => {
  console.error('自检自身崩了：', e);
  process.exit(1);
});
