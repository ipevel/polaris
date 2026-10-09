'use strict';
/**
 * 内核管理：订阅 → 配置 → 拉起 mihomo → 控制面 → 系统代理/TUN。
 *
 * 状态机：idle → starting → connected → stopping → idle
 * 所有对外方法都是幂等的；异常一律转成中文 Error 抛给 IPC 层。
 */

const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');
const crypto = require('crypto');
const yaml = require('js-yaml');

const paths = require('../paths');
const log = require('../logger');
const store = require('../store');
const sysproxy = require('../net/sysproxy');
const { Controller, sleep } = require('./controller');
const { sanitize, SanitizeError } = require('./sanitizer');
const builder = require('./builder');
const rulesets = require('./rulesets');
const region = require('./region');
const traffic = require('./traffic');
const fmt = require('../util/format');

const MAIN_GROUP = builder.DIRECT_GROUP;
const TRAFFIC_TICK_MS = 1000;

const S = {
  phase: 'idle',              // idle | starting | connected | stopping
  proc: null,
  controller: null,
  trafficWs: null,
  logsWs: null,
  mixedPort: 0,
  startedAt: 0,
  upTotal: 0,
  downTotal: 0,
  upSpeed: 0,
  downSpeed: 0,
  nodes: [],
  node: '',
  latency: 0,
  mode: 'rule',
  lastTick: null,
  proxySnapshot: null,
  directDomains: [],
  speedCache: new Map(),       // name -> {latency, alive, at}
  offlineCache: new Set(),     // 确认不可达的节点（区别于"未测/超时"）
  needTunGrace: false,         // TUN 模式下停内核要给足收尾时间
  listeners: new Set(),        // 状态推送订阅者
};

function coreExe() { return path.join(paths.core(), 'mihomo.exe'); }
function configFile() { return paths.file('config.yaml'); }
function subscribeFile() { return path.join(paths.profiles(), 'subscribe.yaml'); }

/* ------------------------------------------------------------------ */
/* 配置                                                                */
/* ------------------------------------------------------------------ */

/** 读取订阅原文；没有就抛错，提示先登录面板 */
function readSubscribe() {
  if (!fs.existsSync(subscribeFile())) {
    throw new Error('尚未拉取订阅，请先登录面板');
  }
  return fs.readFileSync(subscribeFile(), 'utf8');
}

/**
 * 订阅 → 清洗 → 组装 → 写盘。返回 {count, report}。
 * 不改动线上内核；需要 reload 由调用方决定。
 */
function prepareConfig(opts = {}) {
  const raw = readSubscribe();
  const { doc, report } = sanitize(raw, { directDomains: S.directDomains });
  const wantTun = !!store.get('tun_mode');
  ensureRuleSeeds();
  // 热重载时端口/密钥必须保持不变，否则控制面与系统代理都会指向旧端口
  let fixed = {};
  if (opts.reuse) {
    try { fixed = JSON.parse(fs.readFileSync(paths.file('runtime.json'), 'utf8')); } catch (_) { fixed = {}; }
  }
  const result = builder.build(doc, {
    mixedPort: fixed.mixedPort,
    controllerPort: fixed.controllerPort,
    secret: fixed.secret,
    mode: store.get('proxy_mode'),
    tun: wantTun,
    tunStack: store.get('tun'),
    allowLan: store.get('allow_lan'),
    ipv6: store.get('ipv6'),
    directDomains: S.directDomains,
    routing: { enabled: store.get('routing_rules'), order: store.get('routing_order') },
  });
  for (const w of result.warnings || []) log.warn(`routing: ${w}`);
  fs.mkdirSync(paths.data(), { recursive: true });
  fs.writeFileSync(configFile(), yaml.dump(result.config, { lineWidth: -1 }), 'utf8');
  // 混合端口/控制端口每次生成都不同 → 落盘给主进程复用
  fs.writeFileSync(paths.file('runtime.json'), JSON.stringify({
    mixedPort: result.mixedPort, controllerPort: result.controllerPort, secret: result.secret,
  }), 'utf8');
  const count = Array.isArray(doc.proxies) ? doc.proxies.length : 0;
  const ruleSets = Object.keys(result.config['rule-providers'] || {}).length;
  log.info(`config prepared: ${count} proxies, renames=${report.renamed}, droppedInfo=${report.droppedInfo}, droppedKeys=${report.droppedKeys.join(',') || '-'}, ruleSets=${ruleSets}`);
  return { count, report };
}

/**
 * 内置分流规则种子随包分发，拷进 data/rules/ 一次即可。
 * 必须落在 data/ 内：mihomo 只允许读取 -d 目录（或 SAFE_PATHS）下的规则集文件，
 * 直接引用安装目录会被 IsSafePath 拒掉（path is not subpath of home directory）。
 */
function ensureRuleSeeds() {
  const src = paths.rules();
  const dst = paths.ruleSeeds();
  if (!fs.existsSync(src)) {
    log.warn('内置分流规则目录不存在，分流规则将不可用:', src);
    return false;
  }
  try { fs.mkdirSync(dst, { recursive: true }); } catch (e) {
    log.warn('创建规则集目录失败:', e && e.message);
    return false;
  }
  let copied = 0;
  for (const f of rulesets.allProviderKeys()) {
    const name = rulesets.seedFile(f);
    const from = path.join(src, name);
    if (!fs.existsSync(from)) {
      log.warn(`规则集种子缺失: ${name}`);
      continue;
    }
    const to = path.join(dst, name);
    try {
      if (fs.existsSync(to) && fs.statSync(to).size === fs.statSync(from).size) continue;
      fs.copyFileSync(from, to);
      copied += 1;
    } catch (e) {
      log.warn(`拷贝规则集 ${name} 失败:`, e && e.message);
    }
  }
  if (copied) log.info(`内置分流规则已就位（${copied} 个文件）`);
  return true;
}

/**
 * 规则库随包分发，拷进 data/ 一次即可。
 * 不这么做的话，首次连接时 mihomo 会自己去 GitHub 下载 GeoIP.dat ——
 * 实测要 19 秒，而且没网就直接失败。
 */
function ensureGeodata() {
  const src = paths.geo();
  if (!fs.existsSync(src)) {
    log.warn('规则库目录不存在，首次连接可能需要联网下载:', src);
    return false;
  }
  let copied = 0;
  for (const f of ['geoip.metadb', 'geosite.dat', 'ASN.mmdb', 'GeoSite.dat', 'GeoIP.dat', 'country.mmdb']) {
    const from = path.join(src, f);
    if (!fs.existsSync(from)) continue;
    const to = path.join(paths.data(), f);
    try {
      if (fs.existsSync(to) && fs.statSync(to).size === fs.statSync(from).size) continue;
      fs.copyFileSync(from, to);
      copied += 1;
    } catch (e) {
      log.warn(`拷贝规则库 ${f} 失败:`, e && e.message);
    }
  }
  if (copied) log.info(`规则库已就位（${copied} 个文件）`);
  return true;
}

/* ------------------------------------------------------------------ */
/* 内核生命周期                                                        */
/* ------------------------------------------------------------------ */

async function startKernel() {
  if (S.phase !== 'idle') return;
  const exe = coreExe();
  if (!fs.existsSync(exe)) {
    throw new Error(`未找到内核 mihomo.exe（${paths.core()}），请重新解压完整目录`);
  }
  ensureGeodata();
  prepareConfig();

  const runtime = JSON.parse(fs.readFileSync(paths.file('runtime.json'), 'utf8'));
  S.mixedPort = runtime.mixedPort;

  const args = ['-d', paths.data(), '-f', configFile()];
  const proc = spawn(exe, args, {
    cwd: paths.data(),
    windowsHide: true,
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  S.proc = proc;
  S.phase = 'starting';

  const ring = [];
  const capture = (buf) => {
    const text = buf.toString('utf8');
    ring.push(text);
    if (ring.length > 200) ring.shift();
    for (const line of text.split(/\r?\n/)) {
      if (line.trim()) log.info('[mihomo]', line.trim());
    }
  };
  proc.stdout.on('data', capture);
  proc.stderr.on('data', capture);
  proc.on('exit', (code) => {
    log.info(`mihomo exited code=${code}`);
    if (S.phase !== 'idle') {
      S.phase = 'idle';
      S.proc = null;
      S.controller = null;
      sysproxy.disable(S.proxySnapshot);
      S.proxySnapshot = null;
      emit();
    }
  });
  proc.on('error', (e) => log.error('mihomo spawn error:', e.message));

  const controller = new Controller(runtime.controllerPort, runtime.secret);
  await controller.waitReady(20000);
  S.controller = controller;
  S.phase = 'connected';
  S.startedAt = Date.now();
  S.upTotal = 0;
  S.downTotal = 0;
  S.lastTick = Date.now();

  // 应用上次选择
  const lastGroup = store.get('last_group') || MAIN_GROUP;
  const lastNode = store.get('last_node');
  try {
    const cfg = await controller.get('/configs');
    if (cfg && cfg.mode) S.mode = cfg.mode;
  } catch (_) {}
  try {
    if (lastNode) await selectNodeInGroup(controller, lastGroup, lastNode);
  } catch (e) {
    log.warn('restore last node failed:', e.message);
  }

  await startTrafficStream();
  log.info(`kernel ready: mixed=${S.mixedPort} controller=${runtime.controllerPort}`);
  return controller;
}

async function stopKernel() {
  if (!S.proc) { S.phase = 'idle'; return; }
  S.phase = 'stopping';
  for (const ws of [S.trafficWs, S.logsWs]) {
    try { ws && ws.close(); } catch (_) {}
  }
  S.trafficWs = null;
  S.logsWs = null;
  // 给内核一点时间自己收尾 —— TUN 模式下它要摘路由和虚拟网卡
  await require('../net/tun').stopProcess(S.proc, S.needTunGrace ? 1500 : 400);
  S.proc = null;
  S.controller = null;
  S.startedAt = 0;
  S.phase = 'idle';
}

function startTrafficStream() {
  if (!S.controller) return Promise.resolve();
  return S.controller.connectWS('/traffic').then((ws) => {
    S.trafficWs = ws;
    ws.on('message', (raw) => {
      let d;
      try { d = JSON.parse(raw.toString()); } catch (_) { return; }
      const now = Date.now();
      const up = Number(d.up) || 0;
      const down = Number(d.down) || 0;
      S.upTotal += up;
      S.downTotal += down;
      traffic.add(up, down);
      const dt = S.lastTick ? now - S.lastTick : TRAFFIC_TICK_MS;
      S.lastTick = now;
      // 非对称 EMA：下行重攻击升、缓降，上行相反（与 Android 端一致）
      S.downSpeed = ema(S.downSpeed, (down * 1000) / dt, 0.45, 0.08);
      S.upSpeed = ema(S.upSpeed, (up * 1000) / dt, 0.45, 0.08);
      emit();
    });
    ws.on('error', () => {});
  }).catch((e) => log.warn('traffic stream failed:', e.message));
}

function ema(prev, sample, rise, fall) {
  if (!Number.isFinite(sample) || sample < 0) return prev;
  const alpha = sample > prev ? rise : fall;
  return prev + alpha * (sample - prev);
}

/* ------------------------------------------------------------------ */
/* 节点                                                                */
/* ------------------------------------------------------------------ */

function nodeRecord(name) {
  const cache = S.speedCache.get(name);
  const r = region.detect(name);
  return {
    name,
    region: r ? r.label : '其他',
    region_code: r ? r.code : '',
    latency: cache ? cache.latency : -1,
    offline: S.offlineCache.has(name),
    group: store.get('last_group') || MAIN_GROUP,
  };
}

/** mihomo 的 /proxies 在不同版本可能是裸 map，也可能裹一层 {proxies:{}}，两种都吃 */
function unwrapProxies(raw) {
  if (!raw || typeof raw !== 'object') return {};
  if (raw.proxies && typeof raw.proxies === 'object') return raw.proxies;
  return raw;
}

/**
 * 内核没起来时，从本地 config.yaml 直接读出节点与分组。
 * 不解这个的话，登录后到连接前节点页是空的 —— 用户会以为订阅没生效。
 */
function previewNodes() {
  try {
    if (!fs.existsSync(configFile())) return S.nodes;
    const cfg = yaml.load(fs.readFileSync(configFile(), 'utf8'));
    const proxies = Array.isArray(cfg && cfg.proxies) ? cfg.proxies : [];
    const groups = Array.isArray(cfg && cfg['proxy-groups']) ? cfg['proxy-groups'] : [];
    const byName = new Map(proxies.map((p) => [p.name, p]));

    S.groups = groups.map((g) => ({
      name: g.name,
      type: g.type,
      now: '',
      count: Array.isArray(g.proxies) ? g.proxies.length : 0,
      options: Array.isArray(g.proxies) ? g.proxies.slice() : [],
      builtin: g.name === 'GLOBAL',
    }));

    const main = groups.find((g) => g.name === MAIN_GROUP) || groups[0];
    const names = main && Array.isArray(main.proxies)
      ? main.proxies.filter((n) => byName.has(n))
      : proxies.map((p) => p.name);
    S.nodes = names.map(nodeRecord);
    return S.nodes;
  } catch (e) {
    log.warn('previewNodes failed:', e && e.message);
    return S.nodes;
  }
}

async function loadNodes(force = false) {
  if (!S.controller) return previewNodes();
  if (!force && S.nodes.length > 0) return S.nodes;
  const proxies = unwrapProxies(await S.controller.get('/proxies'));  if (!proxies || Object.keys(proxies).length === 0) return S.nodes;
  const groupName = store.get('last_group') || MAIN_GROUP;
  const group = proxies[groupName];
  let memberNames;
  if (group && Array.isArray(group.all) && group.all.length > 0) {
    memberNames = group.all.filter((n) => proxies[n]);
  } else {
    memberNames = Object.keys(proxies).filter((n) => proxies[n] && proxies[n].type && !['Selector', 'URLTest', 'Fallback', 'LoadBalance', 'Direct', 'Reject', 'Pass', 'Compatible'].includes(proxies[n].type));
  }
  S.nodes = memberNames.map(nodeRecord);
  S.node = group && group.now ? group.now : S.node;
  const cur = proxies[S.node];
  if (cur && cur.history && cur.history.length > 0) {
    S.latency = cur.history[cur.history.length - 1].delay || 0;
  }
  return S.nodes;
}

function cachedNodes() { return S.nodes; }

async function selectNodeInGroup(controller, groupName, nodeName) {
  const proxies = unwrapProxies(await controller.get('/proxies'));
  const group = proxies && proxies[groupName];
  const members = group && Array.isArray(group.all) ? group.all : Object.keys(proxies || {});
  if (!members.includes(nodeName)) {
    throw new Error(`节点「${nodeName}」不在分组「${groupName}」中`);
  }
  await controller.put(`/proxies/${encodeURIComponent(groupName)}`, { name: nodeName });
  // 回读确认，避免内核静默忽略
  const after = await controller.get(`/proxies/${encodeURIComponent(groupName)}`);
  if (!after || after.now !== nodeName) {
    throw new Error(`切换节点未被内核确认（当前 ${after ? after.now : '未知'}）`);
  }
  return nodeName;
}

async function selectNode(name) {
  if (S.phase !== 'connected') throw new Error('尚未连接');
  const group = store.get('last_group') || MAIN_GROUP;
  const picked = await selectNodeInGroup(S.controller, group, name);
  S.node = picked;
  store.set('last_node', picked);
  await loadNodes(true);
  emit();
  return picked;
}

async function speedTest() {
  if (S.phase !== 'connected') throw new Error('尚未连接');
  const nodes = await loadNodes();
  const url = encodeURIComponent('https://www.gstatic.com/generate_204');
  const CONCURRENCY = 8;
  const list = nodes.slice();
  const now = Date.now();

  const worker = async () => {
    for (;;) {
      const n = list.shift();
      if (!n) return;
      try {
        const r = await S.controller.get(`/proxies/${encodeURIComponent(n.name)}/delay?timeout=5000&url=${url}`, undefined, 8000);
        if (r && typeof r.delay === 'number' && r.delay > 0) {
          S.speedCache.set(n.name, { latency: r.delay, at: now });
          S.offlineCache.delete(n.name);
        } else {
          S.speedCache.set(n.name, { latency: -1, at: now });
        }
      } catch (e) {
        // 测速超时 ≠ 离线；离线需要连续多轮失败才判定，保持与 Android 的口径一致
        const prev = S.speedCache.get(n.name);
        if (prev && prev.latency > 0) {
          S.speedCache.set(n.name, { latency: prev.latency, at: now });
        } else {
          S.speedCache.set(n.name, { latency: -1, at: now });
        }
      }
    }
  };
  await Promise.all(Array.from({ length: CONCURRENCY }, worker));

  // 连续两轮都 -1 的记为离线
  for (const n of nodes) {
    const cur = S.speedCache.get(n.name);
    if (!cur) continue;
    const hits = S.speedCache.get(`${n.name}#miss`);
    const miss = (hits ? hits.count : 0) + (cur.latency < 0 ? 1 : 0);
    S.speedCache.set(`${n.name}#miss`, { count: miss });
    if (miss >= 2) S.offlineCache.add(n.name);
    else if (cur.latency > 0) S.offlineCache.delete(n.name);
  }

  await loadNodes(true);
  emit();
  return S.nodes;
}

async function setMode(mode) {
  const map = { rule: 'rule', global: 'global', direct: 'direct', 规则模式: 'rule', 全局模式: 'global', 直连模式: 'direct' };
  const m = map[mode] || mode;
  if (S.controller) await S.controller.patch('/configs', { mode: m });
  S.mode = m;
  store.set('proxy_mode', m);
  emit();
  return m;
}

/* ------------------------------------------------------------------ */
/* 分流分组                                                            */
/* ------------------------------------------------------------------ */

const GROUP_TYPES = new Set(['Selector', 'URLTest', 'Fallback', 'LoadBalance', 'Relay']);

/** 从内核读回所有策略组，供分流页展示与切出口 */
async function routingGroups() {
  const controller = S.controller;
  if (!controller) return previewNodes() && S.groups;
  const proxies = unwrapProxies(await controller.get('/proxies'));
  const out = [];
  for (const [name, p] of Object.entries(proxies)) {
    if (!p || !GROUP_TYPES.has(p.type)) continue;
    const options = Array.isArray(p.all) ? p.all.slice() : [];
    out.push({
      name,
      type: p.type,
      now: p.now || '',
      count: options.length,
      // 只把真实节点作为候选项；嵌套分组也允许选，恢复默认时会有用
      options,
      builtin: name === 'GLOBAL',
    });
  }
  // 主选择组排最前，其余按名字稳定排序
  out.sort((a, b) => (a.name === MAIN_GROUP ? -1 : b.name === MAIN_GROUP ? 1 : a.name.localeCompare(b.name)));
  return out;
}

async function setRoutingGroup(name, node) {
  const controller = requireCore();
  const proxies = unwrapProxies(await controller.get('/proxies'));
  const group = proxies[name];
  if (!group) throw new Error(`分组「${name}」不存在`);
  if (!GROUP_TYPES.has(group.type)) throw new Error(`「${name}」不是策略组，无法切换`);
  if (group.all && !group.all.includes(node)) throw new Error(`「${node}」不在分组「${name}」中`);
  await controller.put(`/proxies/${encodeURIComponent(name)}`, { name: node });
  const after = unwrapProxies(await controller.get('/proxies'))[name];
  if (!after || after.now !== node) {
    throw new Error(`切换未被内核确认（当前 ${after ? after.now : '未知'}）`);
  }
  if (name === MAIN_GROUP) {
    S.node = node;
    store.set('last_node', node);
    await loadNodes(true);
  }
  emit();
  return { ok: true, now: after.now };
}

/** 恢复默认：把每个策略组切回它的第一个候选项 */
async function resetRoutingGroups() {
  const controller = requireCore();
  const groups = await routingGroups();
  const done = [];
  for (const g of groups) {
    if (g.builtin || !g.options.length) continue;
    if (g.now === g.options[0]) continue;
    try {
      await controller.put(`/proxies/${encodeURIComponent(g.name)}`, { name: g.options[0] });
      done.push(g.name);
    } catch (e) {
      log.warn(`reset group ${g.name} failed:`, e.message);
    }
  }
  await loadNodes(true);
  emit();
  return { ok: true, reset: done };
}

/* ------------------------------------------------------------------ */
/* 内置分流规则（离线 rule-provider）                                   */
/* ------------------------------------------------------------------ */

/**
 * 分流页需要的全部数据：内置表的每一组 + 当前开关 + 运行中内核里的实际出口。
 * 规则集文件随包分发（见 ensureRuleSeeds），不联网、解压即用。
 */
async function rulesetState() {
  const enabled = new Set(rulesets.normalizeEnabled(store.get('routing_rules')));
  let live = null;
  if (S.phase === 'connected' && S.controller) {
    try { live = unwrapProxies(await S.controller.get('/proxies')); } catch (_) { live = null; }
  }
  const groups = rulesets.orderedTable(store.get('routing_order')).map((g) => {
    const p = live ? live[g.name] : null;
    return {
      name: g.name,
      out: g.defaultOut,                    // direct | block | proxy
      enabled: enabled.has(g.name),
      count: rulesets.providerKeys(g).length,
      inline: (g.inlineRules || []).length,
      default_on: !!g.defaultOn,
      now: p && p.now ? p.now : '',
      live: !!p,
    };
  });
  return { groups, enabled: [...enabled], total: rulesets.TABLE.length };
}

/** 开关一个内置分流组（写设置 → 重新生成配置 → 在线时热重载） */
async function setRuleset(name, on) {
  const group = rulesets.TABLE.find((g) => g.name === name);
  if (!group) throw new Error(`内置分流组「${name}」不存在`);
  const next = new Set(rulesets.normalizeEnabled(store.get('routing_rules')));
  if (on) next.add(name); else next.delete(name);
  store.set('routing_rules', rulesets.normalizeEnabled([...next]));
  const r = await reloadConfig();
  return { ok: true, enabled: next.has(name), applied: !!r.applied };
}

/** 恢复内置分流的默认开关与顺序 */
async function resetRulesets() {
  store.set('routing_rules', null);
  store.set('routing_order', null);
  const r = await reloadConfig();
  return { ok: true, applied: !!r.applied };
}

/**
 * 重新生成配置并让运行中的内核热重载。
 * 必须复用同一组端口与控制密钥，否则 PUT /configs 之后控制面自己就连不上了。
 */
async function reloadConfig() {
  await prepareConfig({ reuse: true });
  if (S.phase !== 'connected' || !S.controller) return { ok: true, applied: false };
  await S.controller.put('/configs?force=true', { path: configFile() });
  // 策略组变了，节点/分组缓存要跟着刷新
  try { await loadNodes(true); } catch (e) { log.warn('loadNodes after reload failed:', e && e.message); }
  emit();
  return { ok: true, applied: true };
}

/* ------------------------------------------------------------------ */
/* 对外                                                                */
/* ------------------------------------------------------------------ */

function status() {
  const running = S.phase === 'connected';
  return {
    connected: running,
    node: S.node || '',
    latency: running ? S.latency : 0,
    up_speed: running ? Number(fmt.speed(S.upSpeed)) : 0,
    down_speed: running ? Number(fmt.speed(S.downSpeed)) : 0,
    up_total: fmt.bytes(S.upTotal),
    down_total: fmt.bytes(S.downTotal),
    uptime: running ? fmt.duration((Date.now() - S.startedAt) / 1000) : '00:00:00',
    mode: fmt.modeLabel(S.mode),
    phase: S.phase,
    core_alive: !!S.proc,
  };
}

async function connect() {
  if (S.phase === 'connected') return status();
  S.directDomains = require('./remote').directDomains();
  await startKernel();

  if (store.get('tun_mode')) {
    if (!(await require('../net/elevate').isAdmin())) {
      await stopKernel();
      throw new Error('TUN 模式需要管理员权限，请从设置里以管理员身份重启');
    }
    S.needTunGrace = true;
    log.info('TUN mode active (kernel owns tun device)');
  } else if (store.get('sys_proxy')) {
    S.proxySnapshot = sysproxy.enable('127.0.0.1', S.mixedPort, S.directDomains);
  }
  await loadNodes(true);
  emit();
  return status();
}

async function disconnect() {
  // 只有我们真的改过系统代理才去还原 —— 否则每次断开都白写一遍注册表
  if (S.proxySnapshot) {
    sysproxy.disable(S.proxySnapshot);
    S.proxySnapshot = null;
  }
  await stopKernel();
  S.nodes = [];
  S.node = '';
  emit();
  return status();
}

async function shutdown(reason) {
  log.info(`shutdown: ${reason}`);
  try {
    if (S.proxySnapshot) sysproxy.disable(S.proxySnapshot);
  } catch (_) {}
  S.proxySnapshot = null;
  try { await stopKernel(); } catch (_) {}
  try { require('./traffic').flush(); } catch (_) {}
}

function emit() {
  const st = status();
  for (const fn of S.listeners) {
    try { fn(st); } catch (e) { log.warn('status listener failed:', e && e.message); }
  }
}

function onStatus(fn) {
  if (typeof fn !== 'function') return () => {};
  S.listeners.add(fn);
  return () => S.listeners.delete(fn);
}

function setDirectDomains(list) { S.directDomains = list; }

function requireCore() {
  if (S.phase !== 'connected') throw new Error('尚未连接');
  return S.controller;
}

module.exports = {
  status, connect, disconnect, shutdown, onStatus,
  prepareConfig, loadNodes, cachedNodes, selectNode, speedTest, setMode,
  routingGroups, setRoutingGroup, resetRoutingGroups,
  rulesetState, setRuleset, resetRulesets, reloadConfig,
  requireCore, setDirectDomains, mixedPort: () => S.mixedPort,
  S,
};
