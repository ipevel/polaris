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
const net = require('net');
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

const MAIN_GROUP = builder.DIRECT_GROUP;      // 🚀 节点选择（节点页那张主卡）
const FINAL_GROUP = builder.FINAL_GROUP;      // 🐟 漏网之鱼（兜底组）
// 结构组：只是配置骨架，不是给用户选的分类 —— 节点页不列它们
// （与安卓 RoutingReservedNames 一致：主组/自动选择/故障转移/兜底组）
const STRUCTURAL_GROUPS = new Set([FINAL_GROUP, '自动选择', '故障转移', 'GLOBAL']);
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
  groupLatency: new Map(),     // 策略组名 -> 延迟（内核 history 之外的兜底）
  needTunGrace: false,         // TUN 模式下停内核要给足收尾时间
  routingDegraded: '',         // 本地方案降级原因（非空 = 已回退面板方案）
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
 * 用户指定的本机代理端口（0/空 = 交给 builder 随机）。
 * 端口被别的程序占用时 mihomo 会直接退出，这里不做探测（同步上下文里没法试听），
 * 由 startKernel 捕获内核的 bind 报错并给出人话提示。
 */
function wantedMixedPort() {
  const n = Number(store.get('mixed_port'));
  return Number.isInteger(n) && n > 0 && n < 65536 ? n : undefined;
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
    // 用户指定的本机代理端口（设置 → 本机代理端口，0/空 = 随机）。
    // 热重载时必须沿用 runtime.json 里的旧端口，否则控制面和系统代理都会指向旧端口。
    mixedPort: fixed.mixedPort || wantedMixedPort(),
    controllerPort: fixed.controllerPort,
    secret: fixed.secret,
    mode: store.get('proxy_mode'),
    tun: wantTun,
    tunStack: store.get('tun'),
    allowLan: store.get('allow_lan'),
    ipv6: store.get('ipv6'),
    directDomains: S.directDomains,
    routing: {
      // 总开关：默认开 —— 与安卓端一致，屏蔽面板下发的分流方案、只用本地内置方案
      on: store.get('routing_on') !== false,
      enabled: store.get('routing_rules'),
      order: store.get('routing_order'),
      custom: store.get('custom_rulesets'),
    },
  });
  for (const w of result.warnings || []) log.warn(`routing: ${w}`);
  S.routingDegraded = result.degraded || '';
  fs.mkdirSync(paths.data(), { recursive: true });
  fs.writeFileSync(configFile(), yaml.dump(result.config, { lineWidth: -1 }), 'utf8');
  // 混合端口/控制端口每次生成都不同 → 落盘给主进程复用
  fs.writeFileSync(paths.file('runtime.json'), JSON.stringify({
    mixedPort: result.mixedPort, controllerPort: result.controllerPort, secret: result.secret,
  }), 'utf8');
  const count = Array.isArray(doc.proxies) ? doc.proxies.length : 0;
  const ruleSets = Object.keys(result.config['rule-providers'] || {}).length;
  log.info(`config prepared: ${count} proxies, renames=${report.renamed}, droppedInfo=${report.droppedInfo}, droppedKeys=${report.droppedKeys.join(',') || '-'}, ruleSets=${ruleSets}, localRouting=${result.local ? 'on' : 'off'}${result.degraded ? `, degraded=${result.degraded}` : ''}`);
  return { count, report };
}

/**
 * 内置分流规则种子：**只补缺失**，绝不覆盖已存在的文件。
 *
 * rule-provider 现在是 type:http + path 指向本目录（rulesets.CACHE_DIR），mihomo 会把
 * 下载到的新版规则**写回同一个文件**（mihomo 的 HTTPVehicle 用 path 当缓存）。如果这里
 * 每次启动都按体积差异重拷，用户联网更新过的规则集会被随包种子反复打回旧版
 * —— 那就等于"在线更新"永远不生效。所以种子只负责"冷启动/断网时也有文件可用"。
 *
 * 必须落在 data/ 内：mihomo 只允许读取 -d 目录（或 SAFE_PATHS）下的规则集文件，
 * 直接引用安装目录会被 IsSafePath 拒掉（path is not subpath of home directory）。
 */
function ensureRuleSeeds() {
  const src = paths.rules();
  const dst = paths.polarisRules();
  // 旧版本把种子铺在 data/rules/（type:file 时代）。改成 http 之后那个目录没人读了，
  // 留着只会占 1.5 MB 并让人以为"规则还在这儿"——顺手清掉（只清我们自己建的目录名）。
  const legacy = path.join(paths.data(), 'rules');
  if (legacy !== dst && fs.existsSync(legacy)) {
    try { fs.rmSync(legacy, { recursive: true, force: true }); log.info('已清理旧规则集目录 data/rules'); } catch (_) {}
  }
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
    const to = path.join(dst, name);
    if (fs.existsSync(to)) continue;          // 已有（可能是内核下载的新版）→ 不动
    if (!fs.existsSync(from)) {
      log.warn(`规则集种子缺失: ${name}`);
      continue;
    }
    try {
      fs.copyFileSync(from, to);
      copied += 1;
    } catch (e) {
      log.warn(`拷贝规则集 ${name} 失败:`, e && e.message);
    }
  }
  if (copied) log.info(`内置分流规则种子已预播种（${copied} 个文件）`);
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

/**
 * 起内核之前自己先占一下这个端口。
 * 真机上踩过（用户把本机代理端口设成 7890，7890 被别的代理软件占着）：
 * 内核的 mixed 监听 bind 失败，但控制面端口是随机的好好的，
 * waitReady 于是"成功"，界面显示已连接、实际上根本没有代理可用。
 * 自己 bind 一次，能把这件事在启动前就变成一句人话。
 */
function assertPortFree(port) {
  return new Promise((resolve, reject) => {
    const srv = net.createServer();
    const fail = (e) => {
      try { srv.close(); } catch (_) {}
      if (e && e.code === 'EADDRINUSE') {
        reject(new Error(`本机代理端口 ${port} 已被其它程序占用，请在「设置 → 本机代理端口」换一个（或改成自动）`));
        return;
      }
      reject(e);
    };
    srv.once('error', fail);
    srv.once('listening', () => srv.close(() => resolve()));
    try { srv.listen(port, '127.0.0.1'); } catch (e) { fail(e); }
  });
}

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

  // 起之前先自己占一下这个端口。真机上踩过：用户把本机代理端口设成 7890，
  // 而 7890 已经被另一个代理软件占着 —— 内核的 mixed 监听 bind 失败，
  // 但控制面端口是随机的好好的，waitReady 于是"成功"，界面显示已连接，
  // 实际上根本没有代理可用。自己先 bind 一次，报错给的是人话。
  if (runtime.mixedPort > 0) {
    await assertPortFree(runtime.mixedPort);
  }

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
  try {
    await controller.waitReady(20000);
  } catch (e) {
    // 内核起不来最常见的原因就是端口被占（用户把本机代理端口设成 7890 之后
    // 很可能撞上别的代理软件）。把内核自己那句话翻成人话再抛出去。
    const tail = ring.join('');
    const taken = /address already in use|bind:\s|listen tcp .*bind/i.test(tail);
    if (taken) {
      throw new Error(`本机代理端口 ${runtime.mixedPort} 已被其它程序占用，请在「设置 → 本机代理端口」换一个（或改成自动）`);
    }
    throw e;
  }
  // 控制面起来了 ≠ mixed 端口起来了（控制面端口是随机的，mixed 可能是用户指定的）。
  // 扫一遍启动日志：mixed 监听 bind 失败就当启动失败，别让用户对着"已连接"空等。
  if (/Start Mixed\(http\+socks\) server error/i.test(ring.join(''))) {
    try { proc.kill(); } catch (_) {}
    try { sysproxy.disable(S.proxySnapshot); } catch (_) {}
    S.proxySnapshot = null;
    S.proc = null;
    S.controller = null;
    S.phase = 'idle';
    throw new Error(`本机代理端口 ${runtime.mixedPort} 已被其它程序占用，请在「设置 → 本机代理端口」换一个（或改成自动）`);
  }
  S.controller = controller;
  S.phase = 'connected';
  S.startedAt = Date.now();
  S.upTotal = 0;
  S.downTotal = 0;
  S.groupLatency.clear();      // 新内核的延迟要重新测，别显示上一轮的
  S.lastTick = Date.now();

  // 应用上次选择
  const lastGroup = store.get('last_group') || MAIN_GROUP;
  const lastNode = store.get('last_node');
  try {
    const cfg = await controller.get('/configs');
    if (cfg && cfg.mode) S.mode = cfg.mode;
  } catch (_) {}
  try {
    if (lastNode) {
      const r = await selectNodeInGroup(controller, lastGroup, lastNode);
      store.set('last_group', r.group);
    }
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
 * store 里记的分组名可能是旧版本留下的（旧主组叫「节点选择」，现在与安卓对齐叫「🚀 节点选择」）。
 * 内核里没有这个名字就落到主组 —— 否则「记住上次选的分组」会变成一条静默失败。
 */
function resolveGroup(proxies, name) {
  if (name && proxies && proxies[name]) return name;
  if (name && name !== MAIN_GROUP) log.warn(`group ${name} 不在内核里，回落到 ${MAIN_GROUP}`);
  return MAIN_GROUP;
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
    const allNames = proxies.map((p) => p.name);

    // 本地方案下各组是 include-all：proxies 字段里只有结构成员（自动选择/故障转移/DIRECT），
    // 节点是内核按 include-all 并进去的。所以预览不能只数 proxies —— 那样会显示
    // 「3 个可选出口」甚至 0 个节点（A-29：连接前节点页是空的）。
    const membersOf = (g) => {
      const list = Array.isArray(g.proxies) ? g.proxies : [];
      if (g['include-all'] === true) return allNames.slice();
      const real = list.filter((n) => byName.has(n));
      return real.length > 0 ? real : allNames.slice();
    };

    S.groups = groups.map((g) => {
      const members = membersOf(g);
      return {
        name: g.name,
        type: g.type,
        now: '',
        count: members.length,
        options: members,
        builtin: g.name === 'GLOBAL',
        structural: STRUCTURAL_GROUPS.has(g.name),
      };
    });

    const main = groups.find((g) => g.name === MAIN_GROUP) || groups[0];
    const names = main ? membersOf(main) : allNames;
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
  const groupName = resolveGroup(proxies, store.get('last_group'));
  if (store.get('last_group') !== groupName) store.set('last_group', groupName);
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
  const resolved = resolveGroup(proxies, groupName);
  const group = proxies && proxies[resolved];
  const members = group && Array.isArray(group.all) ? group.all : Object.keys(proxies || {});
  if (!members.includes(nodeName)) {
    throw new Error(`节点「${nodeName}」不在分组「${resolved}」中`);
  }
  await controller.put(`/proxies/${encodeURIComponent(resolved)}`, { name: nodeName });
  // 回读确认，避免内核静默忽略
  const after = await controller.get(`/proxies/${encodeURIComponent(resolved)}`);
  if (!after || after.now !== nodeName) {
    throw new Error(`切换节点未被内核确认（当前 ${after ? after.now : '未知'}）`);
  }
  return { node: nodeName, group: resolved };
}

async function selectNode(name, groupName) {
  if (S.phase !== 'connected') throw new Error('尚未连接');
  // 节点页的分组手风琴里点某一项时，要切的是**那个分组**的出口，
  // 不是上次记住的分组（用户点「自动选择」里的节点却改了「节点选择」的出口是 bug）。
  const want = groupName || store.get('last_group') || MAIN_GROUP;
  const r = await selectNodeInGroup(S.controller, want, name);
  if (r.group === MAIN_GROUP) {
    S.node = r.node;
    store.set('last_node', r.node);
  }
  store.set('last_group', r.group);
  await loadNodes(true);
  emit();
  return r.node;
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

  // 每个策略组也测一遍：用户要的是"延迟测试每个分组都显示"。
  // 内核的 /proxies/<组名>/delay 对 URLTest/Fallback 会真的触发一次组内测试，
  // 对 Selector 返回当前出口的延迟 —— 两种都是分组那一行该显示的数字。
  await testGroupDelays();

  await loadNodes(true);
  emit();
  return S.nodes;
}

/** 逐组测延迟（并发 4），结果进 S.groupLatency 供 routingGroups() 带出 */
async function testGroupDelays() {
  if (S.phase !== 'connected' || !S.controller) return [];
  const url = encodeURIComponent('https://www.gstatic.com/generate_204');
  let groups = [];
  try { groups = await routingGroups(); } catch (_) { groups = []; }
  const list = groups.filter((g) => !g.builtin && !g.structural).map((g) => g.name);
  const done = [];
  const worker = async () => {
    for (;;) {
      const name = list.shift();
      if (!name) return;
      try {
        const r = await S.controller.get(`/proxies/${encodeURIComponent(name)}/delay?timeout=5000&url=${url}`, undefined, 8000);
        const d = r && typeof r.delay === 'number' && r.delay > 0 ? r.delay : -1;
        S.groupLatency.set(name, d);
        done.push({ name, latency: d });
      } catch (_) {
        S.groupLatency.set(name, -1);
        done.push({ name, latency: -1 });
      }
    }
  };
  await Promise.all(Array.from({ length: 4 }, worker));
  return done;
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
    // 分组的延迟：内核会在 history 里记它自己最近一次探测的结果
    // （URLTest/Fallback 每次请求都可能刷新；Selector 就是当前出口的延迟）。
    const hist = Array.isArray(p.history) && p.history.length ? p.history[p.history.length - 1] : null;
    const cached = S.groupLatency.get(name);
    out.push({
      name,
      type: p.type,
      now: p.now || '',
      count: options.length,
      // 只把真实节点作为候选项；嵌套分组也允许选，恢复默认时会有用
      options,
      latency: hist && typeof hist.delay === 'number' && hist.delay > 0 ? hist.delay : (cached || -1),
      builtin: name === 'GLOBAL',
      structural: STRUCTURAL_GROUPS.has(name),
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
    if (g.builtin || g.structural || !g.options.length) continue;
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
 * 分流页需要的全部数据：总开关 + 内置表的每一组 + 当前开关 + 运行中内核里的实际出口。
 * 规则集由内核按 type:http 在线更新（24h），随包种子只负责离线可用（见 ensureRuleSeeds）。
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
      group: g.name,                        // 本地方案下组名就是内核里的组名（不再有 alias）
      out: g.defaultOut,                    // direct | block | proxy
      enabled: enabled.has(g.name),
      count: rulesets.providerKeys(g).length,
      inline: (g.inlineRules || []).length,
      default_on: !!g.defaultOn,
      now: p && p.now ? p.now : '',
      live: !!p,
    };
  });
  const custom = rulesets.normalizeCustom(store.get('custom_rulesets')).map((g) => {
    const p = live ? live[g.name] : null;
    return {
      name: g.name,
      out: g.out,
      enabled: g.enabled,
      count: 0,
      inline: g.rules.length,
      default_on: false,
      now: p && p.now ? p.now : '',
      live: !!p,
      custom: true,
      rules: g.rules.map(rulesets.ruleText),   // 编辑弹窗直接拿来预填
    };
  });
  return {
    groups,
    on: store.get('routing_on') !== false,
    degraded: S.routingDegraded || '',
    enabled: [...enabled],
    total: rulesets.TABLE.length,
    custom,
    custom_total: custom.length,
  };
}

/**
 * 本地分流总开关：开 = 屏蔽面板下发的分流方案、只用本地内置方案（默认）；
 * 关 = 面板配置原样生效（应急开关，面板自带分流出问题时也能救回来）。
 */
async function setLocalRouting(on) {
  store.set('routing_on', !!on);
  await reloadConfig();
  emit();
  return { ok: true, on: !!on };
}

/** 开关一个分流组（内置或自定义）：写设置 → 重新生成配置 → 在线时热重载 */
async function setRuleset(name, on) {
  const group = rulesets.TABLE.find((g) => g.name === name);
  if (group) {
    const next = new Set(rulesets.normalizeEnabled(store.get('routing_rules')));
    if (on) next.add(name); else next.delete(name);
    store.set('routing_rules', rulesets.normalizeEnabled([...next]));
    const r = await reloadConfig();
    return { ok: true, enabled: next.has(name), applied: !!r.applied };
  }
  const list = rulesets.normalizeCustom(store.get('custom_rulesets'));
  const mine = list.find((g) => g.name === name);
  if (!mine) throw new Error(`分流组「${name}」不存在`);
  mine.enabled = !!on;
  store.set('custom_rulesets', rulesets.serializeCustom(list));
  const r = await reloadConfig();
  return { ok: true, enabled: !!on, applied: !!r.applied };
}

/**
 * 拖动排序：把某个分流组直接挪到第 to 位（0 起，越靠前越先匹配）。
 * 界面是拖出来的，位置是一次到位的，不是"按一次动一格"。
 * 内置组与自定义组各自成一段，不会互相穿越（见 builder）。
 */
async function reorderRuleset(name, to) {
  const builtin = rulesets.TABLE.some((g) => g.name === name);
  const cur = builtin
    ? rulesets.orderedTable(store.get('routing_order')).map((g) => g.name)
    : rulesets.normalizeCustom(store.get('custom_rulesets')).map((g) => g.name);
  const i = cur.indexOf(name);
  if (i < 0) throw new Error(`分流组「${name}」不存在`);
  const j = Math.max(0, Math.min(cur.length - 1, Number(to) || 0));
  if (i === j) return { ok: true, moved: false, order: cur };
  const next = cur.slice();
  next.splice(i, 1);
  next.splice(j, 0, name);
  if (builtin) store.set('routing_order', next);
  else {
    const list = rulesets.normalizeCustom(store.get('custom_rulesets'));
    const it = list.find((g) => g.name === name);
    list.splice(list.indexOf(it), 1);
    list.splice(j, 0, it);
    store.set('custom_rulesets', rulesets.serializeCustom(list));
  }
  const r = await reloadConfig();
  return { ok: true, moved: true, order: next, applied: !!r.applied };
}

/** 新建/修改一个自定义分流组（保存前严格校验，错误信息直接给用户看） */
async function saveCustomRuleset(input) {
  const clean = rulesets.validateCustom(input);   // 会抛错：行号 + 原因
  const original = String((input && input.original) || '').trim();
  const list = rulesets.normalizeCustom(store.get('custom_rulesets'));
  const at = original ? list.findIndex((g) => g.name === original) : -1;
  if (original && at < 0) throw new Error(`要修改的分流组「${original}」已经不在了`);
  const clash = list.findIndex((g) => g.name === clean.name);
  if (clash >= 0 && clash !== at) throw new Error(`已经有同名的分流组「${clean.name}」了`);
  if (at < 0 && list.length >= rulesets.CUSTOM_MAX) {
    throw new Error(`自定义分流组最多 ${rulesets.CUSTOM_MAX} 个`);
  }
  if (at >= 0) list[at] = clean; else list.push(clean);
  store.set('custom_rulesets', rulesets.serializeCustom(list));
  const r = await reloadConfig();
  return { ok: true, name: clean.name, renamed: !!original && original !== clean.name, applied: !!r.applied };
}

/** 删除一个自定义分流组 */
async function deleteCustomRuleset(name) {
  const list = rulesets.normalizeCustom(store.get('custom_rulesets'));
  const at = list.findIndex((g) => g.name === name);
  if (at < 0) throw new Error(`分流组「${name}」不存在`);
  list.splice(at, 1);
  store.set('custom_rulesets', rulesets.serializeCustom(list));
  const r = await reloadConfig();
  return { ok: true, applied: !!r.applied };
}

/** 恢复内置分流的默认开关与顺序（**不动**用户自己写的自定义分流组） */
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
  prepareConfig, loadNodes, cachedNodes, selectNode, speedTest, testGroupDelays, setMode,
  routingGroups, setRoutingGroup, resetRoutingGroups,
  rulesetState, setRuleset, resetRulesets, reloadConfig, setLocalRouting,
  reorderRuleset, saveCustomRuleset, deleteCustomRuleset,
  requireCore, setDirectDomains, mixedPort: () => S.mixedPort,
  wantedMixedPort, assertPortFree,
  S,
};
