// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

/**
 * 分流规则 + 地理库统一刷新（P1）。
 *
 * 用户 2026-10-13 定的口径（原话）：
 *   「现在没有不更新的规则，让所有规则全部随仓库更新，不再把规则写死了」
 *   「新建一个专门给 Polaris 用的规则仓库，把需要的所有规则都放进去，
 *     每 2 小时自动同步上游，有更新就跟着更新并添加校验，没更新等下次」
 * 仓库就是 `ipevel/cboard-rulesets`（P0 已建，每 15 分钟同步上游 ACL4SSR + MetaCubeX）。
 *
 * ## 与 geosync.js 的关系
 * geosync.js 只管三份 geodata，且从**第三方** `appshubcc/bett-rules` 拉；
 * 这里接管 geodata **和**分流清单，全部来自我们自己的仓库 —— 一个来源、一套校验、
 * 一个节拍。geosync.js 保留但不再被调用（`fetch-geo.py` 仍要用它的清单定义）。
 *
 * ## 算法（两端同一套，安卓 RuleResources.kt 逐条对齐）
 * ```
 * isFresh(dir)：全部文件存在且非空，且 marker 未过期（12h）→ 一个请求都不发
 * refresh(dir)：
 *   if (isFresh) return
 *   if (退避窗口内) return                             ← 上一轮失败过，别每次启动全套重试
 *   if (inflight) return inflight.promise              ← 单飞（geosync 那次并发踩踏的教训）
 *   sha = GET api.github.com/.../commits/main          ← 钉 SHA；拿不到则 sha=null（退回 @main）
 *   latest = GET <base>/latest.json                    ← base = @<sha>（未钉时 @main）
 *   if (marker.sha === sha) { 只更新 marker 时间戳; return }        ← 无变化零下载（未钉时比 version）
 *   for each f in latest.files:
 *     GET <base>/<f.path> → 写 .part → 校验（逐字节长度 + sha256，基准就是 latest.json 自己）
 *     通过才原子 rename 覆盖；不通过删 .part、保留旧文件、记 WARN
 *   全部成功才写 marker（含 version + sha）；失败写退避标记
 * ```
 * **校验基准天生一致**：latest.json 和它列的文件都是从同一个 `<base>` 取的，钉了 SHA 时
 * 这个 base 就是同一个 commit，不可能错配。这也顺手解掉安卓端那个「jsDelivr 新 sum 配旧
 * 字节」的坑 —— 那次错配的根因正是 sum 与字节分属两次不同的 CDN 抓取（我实测复现过）。
 * 拿不到 SHA 时（国内直连 api.github.com 被墙）退回 `@main`，此时仍是「同一次抓取的两份」，
 * 只是没有 commit 级强一致 —— 弱一致但能更新，好过强一致却永远不更新。
 *
 * ## 三条硬约束（沿用 geosync 的）
 * 1. **不碰内核正在读的文件**：写 `.part` 再 `rename` 原子替换，绝不半写。
 * 2. **校验不过绝不覆盖**：保留旧文件 + 记 WARN，绝不写 marker（下次再来）。
 * 3. **失败不抛**：离线、上游抖动、限流都只记日志，继续用随包那份。
 */

const crypto = require('crypto');
const fs = require('fs');
const http = require('http');
const https = require('https');
const net = require('net');
const path = require('path');
const tls = require('tls');

const SCHEMA = 1;

/**
 * 下载源。
 *
 * **优先钉 commit SHA**：用 `@<sha>` 取 `latest.json` 与它列的文件，参照物与被校验物
 * 天生同源。为什么必须钉：jsDelivr 的 `@main` 会命中陈旧的边缘缓存，而「清单与字节分属
 * 两次不同的 CDN 抓取」正是安卓端旧实现踩过的坑（新 sum 配旧字节，我实测复现过：
 * 同一时刻 `cdn.jsdelivr.net` 的 `.sha256sum` 是新的、`geosite.dat` 字节是旧的，
 * 而 `fastly.jsdelivr.net` 两份都对）。
 *
 * SHA 从 GitHub API 拿（`api.github.com` 国内直连被墙 → 走 `fetchUrl` 的直连→本机代理兜底）。
 * **拿不到 SHA 时退回未钉 SHA 的 `@main`**：jsDelivr 国内可直连，此时仍逐份按 latest.json
 * 的 sha256 校验（自洽），只是放弃 commit 级强一致 —— 宁可「弱一致但能更新」，也不要
 * 「强一致但永远拿不到更新」（规则会一直停在随包种子上）。
 *
 * 测试/覆盖用 `POLARIS_RULES_BASE` 指定单一源：此时不查 SHA、不钉、也不退 raw。
 */
const CDN_BASE = 'https://cdn.jsdelivr.net/gh/ipevel/cboard-rulesets';
const RAW_BASE = 'https://raw.githubusercontent.com/ipevel/cboard-rulesets';
const PRIMARY_BASE = `${CDN_BASE}@main`;
const FALLBACK_BASE = `${RAW_BASE}/main`;

/** commit SHA 解析源：两个端点同一台主机，互为兜底（`commits/main` 返回顶层 `.sha`）。 */
const COMMIT_API = 'https://api.github.com/repos/ipevel/cboard-rulesets/commits/main';
const BRANCH_API = 'https://api.github.com/repos/ipevel/cboard-rulesets/branches/main';

/** 强制单一源（测试与排障用）：给了它就不查 SHA、不钉，也不退 raw。 */
function forcedBase() {
  const forced = (process.env.POLARIS_RULES_BASE || '').trim().replace(/\/+$/, '');
  return forced || null;
}

/**
 * 本次要按顺序试的 base 列表。
 *
 * @param {string|null} sha 钉住的 commit SHA；null = 未钉，退回 `@main`
 * @param {object} [ep] 端点覆盖（自测用；生产不传）
 * @returns {string[]}
 */
function bases(sha, ep) {
  const forced = forcedBase();
  if (forced) return [forced];
  const e = ep || {};
  const cdn = e.cdnBase || CDN_BASE;
  const raw = e.rawBase || RAW_BASE;
  const noFallback = process.env.POLARIS_RULES_NO_FALLBACK === '1';
  if (sha) {
    const list = [`${cdn}@${sha}`];
    if (!noFallback) list.push(`${raw}/${sha}`);
    return list;
  }
  const list = [e.primaryBase || PRIMARY_BASE];
  if (!noFallback) list.push(e.fallbackBase || FALLBACK_BASE);
  return list;
}

/** 节拍：12 小时（geosync 是 24h）。启动时 + 运行期每 12h，无变化时只有一次 latest.json 查询。 */
const INTERVAL_MS = 12 * 60 * 60 * 1000;
const MARKER = 'rules_last_sync';

/**
 * 失败退避：失败**不写** marker（下次还会再来），但也不能「每次启动都全套重试」——
 * 用户上一份 Windows 日志骂的正是「每次启动拉一遍、每次失败刷屏」。
 * 失败时写 `rules_last_try`，退避窗口内直接跳过（只记一行 info）。
 */
const BACKOFF_MS = Number(process.env.POLARIS_RULES_BACKOFF_MS || 30 * 60 * 1000);
const MARKER_TRY = 'rules_last_try';

/** 单文件上限：geosite.dat ~4.3MB、geoip.metadb ~8.4MB、ASN.mmdb ~9MB。给足余量。 */
const MAX_BYTES = 64 * 1024 * 1024;
/** latest.json 上限：现在 ~10KB，给 2MB。 */
const MAX_MANIFEST_BYTES = 2 * 1024 * 1024;

/**
 * 需要落地的三份 geodata 的本地名（消费端 mihomo 按这些名字读）
 * ← 仓库里的文件名。规则清单一律「本地名 === 仓库 dist/rules/<键>.list 的文件名」，
 * 所以规则那部分不用映射（见 rulesets.PROVIDERS 的键名契约）。
 */
const GEO_RENAME = {
  'geo/geosite.dat': 'geosite.dat',
  'geo/geoip.metadb': 'geoip.metadb',
  'geo/ASN.mmdb': 'ASN.mmdb',
};

/**
 * 只需要覆盖内核规则清单的产出目录前缀：仓库里规则产出的路径是
 * `dist/rules/<key>.list`；落到本地就去掉 `dist/rules/` 前缀，**并把扩展名换成 `.yaml`**
 * （见 `planFiles`：本地名必须是 `<key>.yaml`，与随包种子名、Go 侧 `FileExt()` 一致）。
 * 这与 `rulesets.CACHE_DIR = 'polaris-rules'` + `seedFile(key) = '<key>.yaml'` 对齐。
 */
const RULES_PREFIX = 'dist/rules/';

// ---------------------------------------------------------------------------
// HTTP：带 CONNECT 代理支持的最小实现（直连优先，失败且配了本机代理再走代理）
// ---------------------------------------------------------------------------

/**
 * 直连 GET。返回 { status, body, headers }。
 */
function getDirect(url, { maxBytes, timeout = 120000 }) {
  return new Promise((resolve, reject) => {
    const mod = url.startsWith('http://') ? http : https;
    const req = mod.get(url, { timeout }, (res) => {
      const chunks = [];
      let size = 0;
      res.on('data', (c) => {
        size += c.length;
        if (size > maxBytes) {
          req.destroy(new Error('响应过大'));
          return;
        }
        chunks.push(c);
      });
      res.on('end', () => resolve({ status: res.statusCode, body: Buffer.concat(chunks), headers: res.headers }));
      res.on('error', reject);
    });
    req.on('timeout', () => req.destroy(new Error('请求超时')));
    req.on('error', reject);
  });
}

/**
 * 走本机 HTTP 代理的 GET（`CONNECT` 隧道 —— 只支持 https 目标，代理入口本身就是明文）。
 *
 * 为什么需要它：国内直连 `cdn.jsdelivr.net` / `raw.githubusercontent.com` 不稳定，
 * 而 App 自己就是一个代理客户端 —— 用户已经把代理跑起来了，规则同步没理由还在裸奔。
 * 注意只**在本机**代理端口上建隧道（默认取设置里的 mixed_port），不碰系统代理设置。
 */
function getViaProxy(url, proxyPort, { maxBytes, timeout = 120000 }) {
  return new Promise((resolve, reject) => {
    const target = new URL(url);
    if (target.protocol !== 'https:') {
      reject(new Error('代理隧道只支持 https 目标'));
      return;
    }
    const port = Number(proxyPort) || 7890;
    const sock = net.connect(port, '127.0.0.1');
    let done = false;
    const fail = (e) => {
      if (done) return;
      done = true;
      try { sock.destroy(); } catch (_) {}
      reject(e);
    };
    const timer = setTimeout(() => fail(new Error('代理请求超时')), timeout);
    sock.on('error', (e) => { clearTimeout(timer); fail(new Error(`代理不可用（127.0.0.1:${port}）：${e.message}`)); });
    sock.on('connect', () => {
      sock.write(`CONNECT ${target.hostname}:${target.port || 443} HTTP/1.1\r\nHost: ${target.hostname}:${target.port || 443}\r\n\r\n`);
    });
    let head = Buffer.alloc(0);
    const onData = (c) => {
      head = Buffer.concat([head, c]);
      const idx = head.indexOf('\r\n\r\n');
      if (idx < 0) return;
      sock.removeListener('data', onData);
      const statusLine = head.slice(0, head.indexOf('\r\n')).toString('latin1');
      if (!/^HTTP\/1\.[01] 200/.test(statusLine)) {
        clearTimeout(timer);
        fail(new Error(`代理拒绝 CONNECT：${statusLine}`));
        return;
      }
      const rest = head.slice(idx + 4);
      const tlsSock = tls.connect({ socket: sock, servername: target.hostname }, () => {
        const pathPart = `${target.pathname}${target.search}`;
        tlsSock.write(
          `GET ${pathPart} HTTP/1.1\r\nHost: ${target.hostname}\r\n` +
          'User-Agent: Polaris-RuleSync\r\nAccept: */*\r\nConnection: close\r\n\r\n'
        );
      });
      if (rest.length) tlsSock.unshift(rest);
      const chunks = [];
      let size = 0;
      tlsSock.on('data', (c) => {
        size += c.length;
        if (size > maxBytes + 65536) {
          clearTimeout(timer);
          try { tlsSock.destroy(); } catch (_) {}
          fail(new Error('响应过大'));
          return;
        }
        chunks.push(c);
      });
      tlsSock.on('error', (e) => { clearTimeout(timer); fail(e); });
      tlsSock.on('end', () => {
        clearTimeout(timer);
        if (done) return;
        done = true;
        const raw = Buffer.concat(chunks);
        const sep = raw.indexOf('\r\n\r\n');
        const headerText = sep < 0 ? raw.toString('latin1') : raw.slice(0, sep).toString('latin1');
        const body = sep < 0 ? Buffer.alloc(0) : raw.slice(sep + 4);
        const m = /^HTTP\/1\.[01] (\d{3})/.exec(headerText);
        resolve({ status: m ? Number(m[1]) : 0, body, headers: {} });
      });
    };
    sock.on('data', onData);
  });
}

/**
 * 取一个 URL：**先直连**；直连失败（或非 2xx）且给了 proxyPort 时再走本机代理重试一次。
 * 结果里带 `via`，日志里能看出这次走的哪条路。
 */
async function fetchUrl(url, opts) {
  const { maxBytes, proxyPort, timeout } = opts;
  let directErr = null;
  try {
    const r = await getDirect(url, { maxBytes, timeout });
    if (r.status === 200) return { ...r, via: 'direct' };
    directErr = new Error(`HTTP ${r.status}`);
    if (r.status >= 400 && r.status < 500) throw directErr; // 4xx 不是网络问题，代理也救不了
  } catch (e) {
    directErr = e;
  }
  if (proxyPort) {
    try {
      const r = await getViaProxy(url, proxyPort, { maxBytes, timeout });
      if (r.status === 200) return { ...r, via: 'proxy' };
      throw new Error(`HTTP ${r.status}`);
    } catch (e) {
      throw new Error(`直连失败（${directErr.message}），走代理也失败（${e.message}）`);
    }
  }
  throw directErr;
}

/**
 * 取 `ipevel/cboard-rulesets` 当前 main 的 commit SHA —— 钉住它的前提。
 *
 * 两个端点同一台主机（`commits/main` 返回顶层 `.sha`，`branches/main` 返回 `.commit.sha`），
 * 互为兜底。**拿不到就返回 `{sha:null, errors}`，不是抛错**：调用方要退回未钉 SHA 的 `@main`。
 *
 * @returns {Promise<{sha:string|null, via?:string, errors?:string[]}>}
 */
async function resolveSha(opts) {
  if (forcedBase()) return { sha: null, errors: ['POLARIS_RULES_BASE 指定了单一源，跳过 SHA 解析'] };
  const o = opts || {};
  const e = o.endpoints || {};
  const urls = [e.commitApi || COMMIT_API, e.branchApi || BRANCH_API];
  const errors = [];
  for (const url of urls) {
    try {
      const r = await fetchUrl(url, {
        maxBytes: MAX_MANIFEST_BYTES,
        proxyPort: o.proxyPort,
        timeout: o.timeout || 20000,
      });
      const doc = JSON.parse(r.body.toString('utf8'));
      const sha = (doc && doc.sha) || (doc && doc.commit && doc.commit.sha) || '';
      if (/^[0-9a-f]{40}$/.test(String(sha))) return { sha: String(sha), via: r.via };
      errors.push(`${url}: 没有合法的 commit sha`);
    } catch (err) {
      errors.push(`${url}: ${(err && err.message) || err}`);
    }
  }
  return { sha: null, errors };
}

/**
 * 依次试各个 base，返回第一个能拿到 latest.json 的。
 * `via` 记录是直连还是代理，`base` 记录是哪个源 —— 两者都进日志，排障要用。
 *
 * @param {object} opts 传输选项（proxyPort / timeout / endpoints）
 * @param {string|null} sha 钉住的 commit SHA；null = 未钉
 */
async function fetchManifest(opts, sha) {
  const errors = [];
  for (const base of bases(sha, (opts && opts.endpoints) || null)) {
    try {
      const r = await fetchUrl(`${base}/latest.json`, { ...opts, maxBytes: MAX_MANIFEST_BYTES });
      let doc;
      try {
        doc = JSON.parse(r.body.toString('utf8'));
      } catch (_) {
        throw new Error('latest.json 不是合法 JSON');
      }
      if (!doc || doc.schema !== SCHEMA) throw new Error(`latest.json schema 不是 ${SCHEMA}（收到 ${doc && doc.schema}）`);
      if (!Array.isArray(doc.files) || !doc.files.length) throw new Error('latest.json 里没有 files');
      return { base, via: r.via, doc };
    } catch (e) {
      errors.push(`${base}: ${(e && e.message) || e}`);
    }
  }
  throw new Error(`没有可用的规则源 —— ${errors.join('；')}`);
}

// ---------------------------------------------------------------------------
// 清单 → 落盘映射
// ---------------------------------------------------------------------------

/**
 * 把 latest.json 的 files[] 翻成「本地文件名 → 远端相对路径 + 期望 sha256/bytes」。
 *
 * 仓库里有的产出这个 App 不用（比如只给安卓的），所以**以本 App 的规则键为准**去挑，
 * 而不是把仓库文件全下下来。`providerKeys` 由调用方（manager）从 rulesets 传进来，
 * 避免这里 import rulesets 造成循环依赖。
 *
 * @param {object} doc latest.json
 * @param {string[]} providerKeys rulesets 里的规则键（= 仓库 dist/rules/<键>.list 的文件名）
 * @returns {Array<{name:string, path:string, sha256:string, bytes:number, kind:'rule'|'geo'}>}
 */
function planFiles(doc, providerKeys) {
  const wanted = [];
  const keys = new Set(providerKeys || []);

  for (const f of doc.files) {
    const p = String(f && f.path ? f.path : '');
    if (!p || !f.sha256) continue;

    if (p.startsWith(RULES_PREFIX)) {
      const name = p.slice(RULES_PREFIX.length);
      if (!/^[a-z0-9-]+\.list$/.test(name)) continue;
      const key = name.slice(0, -'.list'.length);
      // 只要本 App 真的引用了这个键的产出；`providerKeys` 为空表示"本 App 没有规则键"，
      // 这时一份规则都不挑（而不是把仓库里所有产出都下下来）。
      if (!keys.has(key)) continue;
      // 仓库里是 `dist/rules/<键>.list`，本地必须叫 `<键>.yaml` ——
      // mihomo 按 rule-provider 里声明的 format 解析内容，扩展名只用来对齐种子名
      // （rulesets.seedFile / 安卓 FileExt 都是 `<键>.yaml`）。写错名字等于白下：
      // 内核会继续读随包那份旧的。
      wanted.push({ name: `${key}.yaml`, path: p, sha256: String(f.sha256), bytes: Number(f.bytes) || 0, kind: 'rule' });
      continue;
    }

    const local = GEO_RENAME[p];
    if (local) {
      wanted.push({ name: local, path: p, sha256: String(f.sha256), bytes: Number(f.bytes) || 0, kind: 'geo' });
    }
  }
  return wanted;
}

// ---------------------------------------------------------------------------
// 新鲜度与 marker
// ---------------------------------------------------------------------------

function readMarker(dir) {
  try {
    const doc = JSON.parse(fs.readFileSync(path.join(dir, MARKER), 'utf8'));
    if (!doc || typeof doc !== 'object') return null;
    return doc;
  } catch (_) {
    return null;
  }
}

function tryFile(dir) {
  return path.join(dir, MARKER_TRY);
}

function readTry(dir) {
  try {
    const doc = JSON.parse(fs.readFileSync(tryFile(dir), 'utf8'));
    return doc && typeof doc.at === 'number' ? doc : null;
  } catch (_) {
    return null;
  }
}

/** 记下「这一轮失败了」；窗口内不再重试。成功时清掉（见 clearTry）。 */
function writeTry(dir) {
  try {
    fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(tryFile(dir), JSON.stringify({ schema: SCHEMA, at: Date.now() }));
  } catch (_) {}
}

function clearTry(dir) {
  try { fs.unlinkSync(tryFile(dir)); } catch (_) {}
}

/** 失败退避窗口内？只看本地，不发请求。 */
function inBackoff(dir, now) {
  const doc = readTry(dir);
  if (!doc) return false;
  const t = now == null ? Date.now() : now;
  return t - doc.at < BACKOFF_MS;
}

/**
 * 本地是否已经是最新且未过期 —— **只看本地状态，不发任何请求**。
 *
 * 判据全部来自 marker：
 *   - marker 未过期（< 12h）；
 *   - marker 记下的文件清单，每一个都在本地存在且非空。
 * marker 是上一次**全部文件都成功**之后才写的（见 `doRefresh` 末尾），所以
 * 「marker 新鲜 + 文件都在」就意味着上一轮确实完整落地了。
 *
 * 这是"新鲜时一个请求都不发"这条硬约束的实现点：调用方在 `refresh()` 里
 * 先问它，fresh 就直接返回，连 latest.json 都不查。
 */
function isFresh(dir, opts) {
  const geoDir = (opts && opts.geoDir) || dir;
  const marker = readMarker(dir);
  if (!marker || typeof marker.at !== 'number') return false;
  if (Date.now() - marker.at >= INTERVAL_MS) return false;
  const entries = Array.isArray(marker.files) ? marker.files : null;
  if (!entries || !entries.length) return false;
  for (const e of entries) {
    const name = e && e.n;
    // 防御：marker 是可写的，别让它变成路径穿越
    if (!name || !/^[A-Za-z0-9._-]+$/.test(name)) return false;
    const base = Number(e.d) === 1 ? geoDir : dir;
    try {
      if (fs.statSync(path.join(base, name)).size <= 0) return false;
    } catch (_) {
      return false;
    }
  }
  return true;
}

/**
 * 同一目录的刷新单飞。理由与 geosync.js 里那段完全一样（那次真机实测到
 * 「1 次成功 + 3 次 ENOENT」×3 份）：`.part` 文件名固定，多路并发互踩。
 */
let inflight = null;

async function refresh(dir, opts) {
  const options = opts || {};
  const say = options.log || { info() {}, warn() {} };
  const geoDir = options.geoDir || dir;
  // 先问本地：新鲜就一个请求都不发（不是"发了再比对"）
  if (isFresh(dir, { geoDir })) {
    say.info(`rules sync: 本地未过期（${MARKER}），跳过 —— 未发任何请求`);
    return { version: readMarker(dir).version, updated: [], skipped: true, via: null, reason: 'fresh' };
  }
  // 失败退避：上一轮失败过就等窗口过去 —— 别每次启动都全套重试
  if (inBackoff(dir)) {
    const left = Math.max(1, Math.ceil((BACKOFF_MS - (Date.now() - readTry(dir).at)) / 60000));
    say.info(`rules sync: 上一轮失败，退避窗口内（约 ${left} 分钟后重试），跳过`);
    const m = readMarker(dir);
    return { version: m ? m.version : null, updated: [], skipped: true, via: null, reason: 'backoff' };
  }
  if (inflight && inflight.dir === dir) return inflight.promise;

  const promise = doRefresh(dir, { ...options, log: say }).finally(() => {
    if (inflight && inflight.promise === promise) inflight = null;
  });
  inflight = { dir, promise };
  return promise;
}

/**
 * 真正的刷新逻辑。
 *
 * @returns {Promise<{version:number|null, updated:string[], skipped:boolean, via:string|null, reason?:string}>}
 */
async function doRefresh(dir, opts) {
  const say = opts.log || { info() {}, warn() {} };
  const result = { version: null, updated: [], skipped: false, via: null, pinned: null };
  const manifestOpts = { proxyPort: opts.proxyPort, timeout: opts.timeout, endpoints: opts.endpoints };

  // ① 先钉 SHA。拿不到不是错误，退回未钉的 @main（见文件头注释），
  //    否则国内直连 api.github.com 被墙 = 规则永远停在随包种子。
  const resolved = await resolveSha({ ...manifestOpts, timeout: opts.shaTimeout || 20000 });
  const sha = resolved.sha || null;
  result.pinned = sha;
  if (sha) {
    say.info(`rules sync: 钉住 commit ${sha.slice(0, 8)}（via ${resolved.via}）`);
  } else {
    say.warn(`rules sync: 取不到 commit sha，本轮未钉 SHA，退回 @main 兜底 —— ${(resolved.errors || []).join('；')}`);
  }

  let manifest;
  try {
    manifest = await fetchManifest(manifestOpts, sha);
  } catch (e) {
    say.warn(`rules sync failed: ${(e && e.message) || e}`);
    writeTry(dir);
    return { ...result, reason: 'manifest' };
  }

  const { base, via, doc } = manifest;
  result.via = via;
  const version = Number(doc.version) || 0;
  result.version = version;

  // geo 三份必须落在内核真正读的那个目录（`-d` 指向的 data/），规则清单落在
  // rule-provider 的 path 前缀目录（data/polaris-rules/）。两个目录不同，
  // 所以 marker 里每一条都记下自己属于哪个目录（`d: 1` = geo）。
  const geoDir = opts.geoDir || dir;
  const entriesOf = (list) =>
    list.map((f) => ({ d: f.kind === 'geo' && geoDir !== dir ? 1 : 0, n: f.name }));

  const plan = planFiles(doc, opts.providerKeys);
  if (!plan.length) {
    say.warn('rules sync: 清单里没有本 App 需要的文件（仓库产出名与规则键对不上？）');
    return { ...result, reason: 'empty-plan' };
  }

  // 上游没变：只把 marker 时间戳推进，不重复下载。
  // （走到这里说明 `refresh()` 已经判定本地过期；上游一致就说明只是"时间到了"。）
  // 钉住 SHA 时比 SHA（更强）；没钉时退化成比 version（与旧行为一致）。
  const marker = readMarker(dir);
  const sameUpstream =
    (sha && marker && marker.sha === sha) ||
    (!sha && marker && Number(marker.version) === version);
  if (sameUpstream) {
    writeMarker(dir, { version, sha: sha || marker.sha || null, at: Date.now(), via, files: entriesOf(plan) });
    clearTry(dir);
    say.info(`rules sync: 上游仍是 v${version}${sha ? ` @ ${sha.slice(0, 8)}` : ''}，无变化（不重复下载）`);
    result.skipped = true;
    return result;
  }

  for (const d of new Set([dir, geoDir])) {
    try {
      fs.mkdirSync(d, { recursive: true });
    } catch (e) {
      say.warn(`rules sync failed: 建目录失败 ${(e && e.message) || e}`);
      writeTry(dir);
      return { ...result, reason: 'mkdir' };
    }
  }

  const updated = [];
  // pauseApply：只把下好的字节落到 .part，**替换与写 marker 交给调用方**（applyStaged）。
  // 为什么必须分开：替换前要停内核（Windows 上覆盖被 mmap 的文件会 EBUSY），
  // 而**下载**必须在内核还在跑时做 —— 否则本机 mixed-port 已经关掉，代理接线等于白接。
  const pauseApply = !!opts.pauseApply;
  const staged = [];
  for (const f of plan) {
    const target = f.kind === 'geo' ? geoDir : dir;
    const tmp = path.join(target, `${f.name}.part`);
    const dest = path.join(target, f.name);
    try {
      const r = await fetchUrl(`${base}/${f.path}`, { ...manifestOpts, maxBytes: MAX_BYTES });
      const body = r.body;
      if (!body || body.length === 0) throw new Error('下载到 0 字节');
      if (f.bytes && body.length !== f.bytes) {
        throw new Error(`长度不符：期望 ${f.bytes}，实际 ${body.length}`);
      }
      const actual = crypto.createHash('sha256').update(body).digest('hex');
      if (actual.toLowerCase() !== String(f.sha256).toLowerCase()) {
        throw new Error(`sha256 不匹配：期望 ${f.sha256}，实际 ${actual}`);
      }
      fs.writeFileSync(tmp, body);
      if (pauseApply) staged.push({ tmp, dest, name: f.name });
      else fs.renameSync(tmp, dest); // 原子替换：内核正开着旧文件也不会看到半份内容
      updated.push(f.name);
      say.info(`rules sync: updated ${f.name} (${body.length} bytes, ${r.via})`);
    } catch (e) {
      say.warn(`rules sync failed: ${f.name}: ${(e && e.message) || e}`);
      try { fs.unlinkSync(tmp); } catch (_) {}
    }
  }

  const markerDoc = { version, sha: sha || null, at: Date.now(), via, files: entriesOf(plan) };
  // 只有全部成功才推进 —— 差一份就下次再来，避免"看着新鲜其实缺文件"
  if (updated.length === plan.length) {
    if (pauseApply) {
      // 下载完了，但**还没换**：marker 与替换一起交给 applyStaged()（内核停下的那一刻）
      result.staged = staged;
      result.marker = markerDoc;
    } else {
      writeMarker(dir, markerDoc);
      clearTry(dir);
    }
    result.updated = updated;
  } else {
    say.warn(`rules sync: 本次只成功 ${updated.length}/${plan.length} 份，不推进版本标记，下次继续`);
    // 不应用就把 .part 清掉：别在规则目录里留半份文件
    for (const s of staged) { try { fs.unlinkSync(s.tmp); } catch (_) {} }
    writeTry(dir);
    result.updated = updated;
    result.reason = 'partial';
  }
  return result;
}

/**
 * 应用 `pauseApply` 阶段下好的文件：逐个原子替换，全成功才写 marker。
 *
 * **调用方必须在「内核已停」时调用**：Windows 上覆盖被内核占着的文件（geoip.metadb 是 mmap 的）
 * 会 EBUSY —— 这正是把下载与替换拆成两步的原因（下载时内核必须在跑，替换时内核必须停）。
 *
 * @returns {{applied:string[], failed:Array<{name:string,error:string}>}}
 */
function applyStaged(staged, opts) {
  const o = opts || {};
  const list = Array.isArray(staged) ? staged : [];
  const applied = [];
  const failed = [];
  for (const it of list) {
    try {
      fs.renameSync(it.tmp, it.dest);
      applied.push(it.name);
    } catch (e) {
      failed.push({ name: it.name, error: (e && e.message) || String(e) });
      try { fs.unlinkSync(it.tmp); } catch (_) {}
    }
  }
  if (o.dir) {
    if (failed.length) writeTry(o.dir);
    else if (o.marker) { writeMarker(o.dir, o.marker); clearTry(o.dir); }
  }
  return { applied, failed };
}

function writeMarker(dir, doc) {
  try {
    fs.writeFileSync(path.join(dir, MARKER), JSON.stringify(doc));
  } catch (_) {}
}

/**
 * 只推进 marker（时间戳，必要时带版本号），**不动任何文件**。
 *
 * 用途：调用方已经用 `checkLatest()` 确认上游版本与 marker 一致 —— 那这一轮就没有
 * 任何东西要下载，只是 12 小时节拍到点了。把时间戳推一下，下次 12 小时内不用再查。
 * 保留 marker 里原有的 `files` 清单（那些文件仍原样在盘上）。
 */
function touchMarker(dir, version) {
  const m = readMarker(dir);
  if (!m) return false;
  const next = { ...m, at: Date.now() };
  if (version != null) next.version = version;
  writeMarker(dir, next);
  return true;
}

/** 仅探测上游版本，不下载。给「检查更新」按钮和自测用（同样优先钉 SHA）。 */
async function checkLatest(opts) {
  const o = opts || {};
  const ep = { proxyPort: o.proxyPort, timeout: o.timeout || 20000, endpoints: o.endpoints };
  const resolved = await resolveSha({ ...ep, timeout: o.shaTimeout || 20000 });
  const manifest = await fetchManifest(ep, resolved.sha || null);
  return {
    version: Number(manifest.doc.version) || 0,
    generatedAt: manifest.doc.generatedAt || null,
    base: manifest.base,
    via: manifest.via,
    pinned: resolved.sha || null,
  };
}

module.exports = {
  refresh,
  applyStaged,
  checkLatest,
  isFresh,
  planFiles,
  readMarker,
  touchMarker,
  bases,
  resolveSha,
  inBackoff,
  readTry,
  writeTry,
  clearTry,
  INTERVAL_MS,
  BACKOFF_MS,
  MARKER,
  MARKER_TRY,
  SCHEMA,
  CDN_BASE,
  RAW_BASE,
  PRIMARY_BASE,
  FALLBACK_BASE,
  COMMIT_API,
  BRANCH_API,
  GEO_RENAME,
  RULES_PREFIX,
  MAX_BYTES,
};
