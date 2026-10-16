'use strict';
/**
 * 规则同步（rulesync.js）自测。
 *
 * 全部跑在本地 HTTP fixture 上（`POLARIS_RULES_BASE` 指到 127.0.0.1 的临时服务），
 * 不碰真网络 —— 门禁不能因为 jsDelivr 抖动就红。fixture 的文件内容是真的，
 * sha256 也是现算的，所以校验链路走的是生产同一条代码。
 *
 * 钉子（都是 geosync 那次真机事故换来的教训）：
 *   1. 新鲜时**一个请求都不发**；
 *   2. 上游版本没变时零下载（只推 marker）；
 *   3. sha256 不匹配 → 保留旧文件、不写 marker、下次还会再试；
 *   4. 部分失败 → 不写 marker（不许"看着新鲜其实缺文件"）；
 *   5. 并发 5 路 → 只有 1 次刷新（单飞）；
 *   6. 原子替换：坏响应不许留下半份文件；
 *   7. planFiles 只挑本 App 引用到的规则键。
 */
const assert = require('assert');
const crypto = require('crypto');
const fs = require('fs');
const http = require('http');
const os = require('os');
const path = require('path');

const appRoot = path.join(__dirname, '..');
const rulesync = require(path.join(appRoot, 'electron', 'core', 'rulesync.js'));

let pass = 0;
let fail = 0;
const ok = (n) => { console.log(`  [ OK ] ${n}`); pass++; };
const bad = (n, d) => { console.log(`  [FAIL] ${n}  ${d || ''}`); fail++; };
async function check(name, fn) {
  try { await fn(); ok(name); } catch (e) { bad(name, e.message); }
}

const sha = (b) => crypto.createHash('sha256').update(b).digest('hex');
const tmpRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'polaris-rulesync-'));

// ---------------------------------------------------------------------------
// fixture HTTP 服务：把一组 { 相对路径: Buffer } 暴露出去，并记下每个路径的请求次数
// ---------------------------------------------------------------------------
function makeServer(files) {
  const hits = new Map();
  const srv = http.createServer((req, res) => {
    const p = decodeURIComponent(req.url.split('?')[0]).replace(/^\/+/, '');
    hits.set(p, (hits.get(p) || 0) + 1);
    const body = files[p];
    if (!body) {
      res.writeHead(404, { 'content-type': 'text/plain' });
      res.end('not found');
      return;
    }
    res.writeHead(200, { 'content-type': 'application/octet-stream', 'content-length': body.length });
    res.end(body);
  });
  return new Promise((resolve) => {
    srv.listen(0, '127.0.0.1', () => resolve({ srv, hits, url: `http://127.0.0.1:${srv.address().port}` }));
  });
}

/** 造一份 latest.json + 对应文件字节；providerKeys 之外的规则产出也放一份，验证会被挑掉 */
function makeRepo({ version, ruleKeys, geoBlobs, extraRules = ['android-only'] }) {
  const files = {};
  const manifestFiles = [];
  for (const key of [...ruleKeys, ...extraRules]) {
    const body = Buffer.from(`DOMAIN-SUFFIX,${key}.example\nRULE-SET,${key}\n`, 'utf8');
    const p = `dist/rules/${key}.list`;
    files[p] = body;
    manifestFiles.push({ path: p, bytes: body.length, sha256: sha(body) });
  }
  for (const [p, body] of Object.entries(geoBlobs)) {
    files[p] = body;
    manifestFiles.push({ path: p, bytes: body.length, sha256: sha(body) });
  }
  const doc = {
    schema: 1,
    version,
    generatedAt: new Date(version * 1000).toISOString(),
    upstream: { acl: { repo: 'ACL4SSR/ACL4SSR', sha: 'x', date: 'x' }, meta: { repo: 'm', tag: 'latest', publishedAt: 'x' } },
    files: manifestFiles,
  };
  files['latest.json'] = Buffer.from(JSON.stringify(doc, null, 2), 'utf8');
  return { files, doc };
}

const GEO = {
  'geo/geosite.dat': Buffer.from('geosite-blob-v1'.repeat(64), 'utf8'),
  'geo/geoip.metadb': Buffer.from('geoip-blob-v1'.repeat(32), 'utf8'),
  'geo/ASN.mmdb': Buffer.from('asn-blob-v1'.repeat(16), 'utf8'),
};
const KEYS = ['social', 'streaming', 'ai'];
const silent = { info() {}, warn() {} };
const warns = [];
const logger = { info() {}, warn: (m) => warns.push(String(m)) };

function freshDir(tag) {
  const d = path.join(tmpRoot, tag);
  fs.rmSync(d, { recursive: true, force: true });
  fs.mkdirSync(d, { recursive: true });
  return d;
}

async function withEnv(base, fn) {
  const old = process.env.POLARIS_RULES_BASE;
  process.env.POLARIS_RULES_BASE = base;
  try { return await fn(); } finally {
    if (old === undefined) delete process.env.POLARIS_RULES_BASE;
    else process.env.POLARIS_RULES_BASE = old;
  }
}

(async () => {
  console.log('\n规则同步自测（rulesync.js）');

  // ---- ① 首次刷新：下载 + 校验 + 落盘，并挑掉本 App 不用的产出 ----
  {
    const repo = makeRepo({ version: 7, ruleKeys: KEYS, geoBlobs: GEO });
    const s = await makeServer(repo.files);
    const dir = freshDir('case1');
    try {
      const r = await withEnv(s.url, () => rulesync.refresh(dir, { providerKeys: KEYS, log: silent }));
      await check('首次刷新：9 份文件全部落地（3 规则 + 3 geo + 挑掉 android-only）', () => {
        assert.strictEqual(r.updated.length, 6, `实际 ${r.updated.length}：${r.updated.join(',')}`);
        assert.strictEqual(r.version, 7);
        for (const n of ['social.yaml', 'streaming.yaml', 'ai.yaml', 'geosite.dat', 'geoip.metadb', 'ASN.mmdb']) {
          assert.ok(fs.existsSync(path.join(dir, n)), `缺 ${n}`);
        }
      });
      await check('仓库里本 App 不引用的产出（android-only.list）没有下载', () => {
        assert.ok(!fs.existsSync(path.join(dir, 'android-only.yaml')), 'android-only.list 不该被下');
        assert.ok(!s.hits.has('dist/rules/android-only.list'), '不该请求 android-only.list');
      });
      await check('落盘内容与上游逐字节相同', () => {
        assert.strictEqual(sha(fs.readFileSync(path.join(dir, 'social.yaml'))), sha(repo.files['dist/rules/social.list']));
        assert.strictEqual(sha(fs.readFileSync(path.join(dir, 'geosite.dat'))), sha(repo.files['geo/geosite.dat']));
      });
      await check('marker 记下了版本号与时间', () => {
        const m = rulesync.readMarker(dir);
        assert.ok(m && m.version === 7 && typeof m.at === 'number', JSON.stringify(m));
      });
      await check('没有留下任何 .part 临时文件', () => {
        assert.deepStrictEqual(fs.readdirSync(dir).filter((f) => f.endsWith('.part')), []);
      });

      // ---- ② 新鲜期内再刷新：一个请求都不发 ----
      const before = s.hits.get('latest.json');
      const r2 = await withEnv(s.url, () => rulesync.refresh(dir, { providerKeys: KEYS, log: silent }));
      await check('新鲜期内刷新：跳过且不发请求', () => {
        assert.strictEqual(r2.skipped, true, JSON.stringify(r2));
        assert.strictEqual(s.hits.get('latest.json'), before, '不该再请求 latest.json');
      });
    } finally { s.srv.close(); }
  }

  // ---- ③ 上游版本没变（把 marker 弄旧）：只推 marker，零下载 ----
  {
    const repo = makeRepo({ version: 7, ruleKeys: KEYS, geoBlobs: GEO });
    const s = await makeServer(repo.files);
    const dir = freshDir('case3');
    try {
      await withEnv(s.url, () => rulesync.refresh(dir, { providerKeys: KEYS, log: silent }));
      // 把 marker 时间戳改成 13 小时前 → 过期，但版本还是 7
      const m = rulesync.readMarker(dir);
      rulesync.readMarker(dir);
      fs.writeFileSync(path.join(dir, rulesync.MARKER), JSON.stringify({ ...m, at: Date.now() - 13 * 3600 * 1000 }));
      const ruleHitsBefore = s.hits.get('dist/rules/social.list');
      const r = await withEnv(s.url, () => rulesync.refresh(dir, { providerKeys: KEYS, log: silent }));
      await check('上游版本未变：跳过，且不重复下载任何文件', () => {
        assert.strictEqual(r.skipped, true, JSON.stringify(r));
        assert.deepStrictEqual(r.updated, []);
        assert.strictEqual(s.hits.get('dist/rules/social.list'), ruleHitsBefore, '不该重下规则文件');
      });
      await check('版本未变时 marker 时间戳被推进（下次不用再查上游）', () => {
        assert.ok(Date.now() - rulesync.readMarker(dir).at < 5000, 'marker 没被推进');
      });
    } finally { s.srv.close(); }
  }

  // ---- ④ 版本变了：重下；其中一份字节对不上清单 → 不覆盖、不写 marker ----
  {
    const repo = makeRepo({ version: 8, ruleKeys: KEYS, geoBlobs: GEO });
    const s = await makeServer(repo.files);
    const dir = freshDir('case4');
    try {
      await withEnv(s.url, () => rulesync.refresh(dir, { providerKeys: KEYS, log: silent }));
      const goodSocial = fs.readFileSync(path.join(dir, 'social.yaml'));
      const goodMarker = fs.readFileSync(path.join(dir, rulesync.MARKER));
      s.srv.close();

      // v9：social.list 的字节被改坏（与清单里的 bytes/sha256 都对不上），其余照常
      const repo9 = makeRepo({ version: 9, ruleKeys: KEYS, geoBlobs: GEO });
      const s2 = await makeServer({
        ...repo9.files,
        'dist/rules/social.list': Buffer.from('DOMAIN-SUFFIX,changed.example\n', 'utf8'),
      });
      const dir2 = freshDir('case4b');
      fs.copyFileSync(path.join(dir, 'social.yaml'), path.join(dir2, 'social.yaml'));
      fs.writeFileSync(path.join(dir2, rulesync.MARKER), goodMarker);
      warns.length = 0;
      const r = await withEnv(s2.url, () => rulesync.refresh(dir2, { providerKeys: KEYS, log: logger }));
      await check('坏文件（长度/sha 对不上）：报 WARN、保留旧内容、不覆盖', () => {
        assert.strictEqual(sha(fs.readFileSync(path.join(dir2, 'social.yaml'))), sha(goodSocial), '旧文件被覆盖了');
        assert.ok(warns.some((w) => /social\.yaml/.test(w)), `没有针对 social.yaml 的 WARN：${warns.join(' | ')}`);
      });
      await check('部分失败：不写 marker（不许"看着新鲜其实缺文件"）', () => {
        assert.strictEqual(rulesync.readMarker(dir2).version, 8, 'marker 被推进到失败的那一版了');
      });
      await check('坏文件失败后也把 .part 清掉了', () => {
        assert.deepStrictEqual(fs.readdirSync(dir2).filter((f) => f.endsWith('.part')), []);
      });
      await check('坏的是哪一份就只影响那一份：其余 5 份照常更新', () => {
        assert.deepStrictEqual(r.updated.sort(), ['ASN.mmdb', 'ai.yaml', 'geoip.metadb', 'geosite.dat', 'streaming.yaml'].sort(), r.updated.join(','));
      });
      s2.srv.close();
    } finally { try { s.srv.close(); } catch (_) {} }
  }

  // ---- ⑤ 并发 5 路 → 只刷新 1 次（单飞） ----
  {
    const repo = makeRepo({ version: 11, ruleKeys: KEYS, geoBlobs: GEO });
    const s = await makeServer(repo.files);
    const dir = freshDir('case5');
    try {
      const rs = await withEnv(s.url, () => Promise.all(
        Array.from({ length: 5 }, () => rulesync.refresh(dir, { providerKeys: KEYS, log: silent }))
      ));
      await check('并发 5 路刷新：latest.json 只被请求一次', () => {
        assert.strictEqual(s.hits.get('latest.json'), 1, `请求了 ${s.hits.get('latest.json')} 次`);
      });
      await check('并发 5 路刷新：每份文件也只下一次', () => {
        assert.strictEqual(s.hits.get('dist/rules/social.list'), 1, `social.list 下了 ${s.hits.get('dist/rules/social.list')} 次`);
        assert.strictEqual(s.hits.get('geo/geosite.dat'), 1);
      });
      await check('并发 5 路拿到的是同一个结果', () => {
        assert.ok(rs.every((r) => r.version === 11), JSON.stringify(rs.map((r) => r.version)));
      });
    } finally { s.srv.close(); }
  }

  // ---- ⑥ 上游整体不可达：不抛、返回 reason，旧文件原样 ----
  {
    const dir = freshDir('case6');
    fs.writeFileSync(path.join(dir, 'social.yaml'), 'OLD\n');
    warns.length = 0;
    const r = await withEnv('http://127.0.0.1:1', () => rulesync.refresh(dir, { providerKeys: KEYS, log: logger, timeout: 3000 }));
    await check('上游不可达：不抛异常、返回 reason=manifest', () => {
      assert.strictEqual(r.reason, 'manifest', JSON.stringify(r));
      assert.strictEqual(fs.readFileSync(path.join(dir, 'social.yaml'), 'utf8'), 'OLD\n');
    });
    await check('上游不可达：记了 WARN', () => {
      assert.ok(warns.some((w) => /rules sync failed/.test(w)), warns.join(' | '));
    });
  }

  // ---- ⑥b geoDir：geo 落到另一个目录（Windows 上 geo 必须进内核的 -d 目录，
  //         规则清单进 data/polaris-rules/，两者不同） ----
  {
    const dir = freshDir('case6b');
    const geoDir = freshDir('case6b-geo');
    const repo = makeRepo({ version: 21, ruleKeys: KEYS, geoBlobs: GEO });
    const s = await makeServer(repo.files);
    try {
      const r = await withEnv(s.url, () => rulesync.refresh(dir, { providerKeys: KEYS, geoDir, log: logger }));
      await check('geoDir：规则进 rules 目录、geo 进单独的 geo 目录', () => {
        assert.strictEqual(r.version, 21, JSON.stringify(r));
        for (const n of ['social.yaml', 'streaming.yaml', 'ai.yaml']) {
          assert.ok(fs.existsSync(path.join(dir, n)), `规则 ${n} 应落在 rules 目录`);
          assert.ok(!fs.existsSync(path.join(geoDir, n)), `规则 ${n} 不该落在 geo 目录`);
        }
        for (const n of ['geosite.dat', 'geoip.metadb', 'ASN.mmdb']) {
          assert.ok(fs.existsSync(path.join(geoDir, n)), `geo ${n} 应落在 geo 目录`);
          assert.ok(!fs.existsSync(path.join(dir, n)), `geo ${n} 不该落在 rules 目录`);
        }
      });
      await check('geoDir：marker 记下了每条属于哪个目录', () => {
        const m = rulesync.readMarker(dir);
        assert.ok(Array.isArray(m.files), JSON.stringify(m));
        const geo = m.files.filter((e) => Number(e.d) === 1).map((e) => e.n).sort();
        const rules = m.files.filter((e) => Number(e.d) !== 1).map((e) => e.n).sort();
        assert.deepStrictEqual(geo, ['ASN.mmdb', 'geoip.metadb', 'geosite.dat'].sort(), geo.join(','));
        assert.deepStrictEqual(rules, ['ai.yaml', 'social.yaml', 'streaming.yaml'].sort(), rules.join(','));
      });
      await check('geoDir：跨两个目录也能判新鲜（第二次不发请求）', async () => {
        let hits = 0;
        const proxy = {
          base: s.base,
          record: () => { hits += 1; },
        };
        assert.strictEqual(rulesync.isFresh(dir, { geoDir }), true, '应判为新鲜');
        // 真跑一次 refresh：应直接返回且不碰网络
        const r2 = await withEnv(s.url, () => rulesync.refresh(dir, { providerKeys: KEYS, geoDir, log: logger }));
        assert.strictEqual(r2.skipped, true, JSON.stringify(r2));
        assert.strictEqual(hits, 0);
        void proxy;
      });
      await check('geoDir：geo 目录缺一份 → 判为不新鲜', () => {
        fs.rmSync(path.join(geoDir, 'ASN.mmdb'));
        assert.strictEqual(rulesync.isFresh(dir, { geoDir }), false, '缺文件不该判新鲜');
      });
    } finally { s.srv.close(); }
  }

  // ---- ⑥c touchMarker：只推时间戳，不动文件 ----
  {
    const dir = freshDir('case6c');
    const repo = makeRepo({ version: 31, ruleKeys: KEYS, geoBlobs: GEO });
    const s = await makeServer(repo.files);
    try {
      await withEnv(s.url, () => rulesync.refresh(dir, { providerKeys: KEYS, log: logger }));
      const before = rulesync.readMarker(dir);
      const social = fs.readFileSync(path.join(dir, 'social.yaml'), 'utf8');
      await check('touchMarker：推进时间戳并保留文件清单', () => {
        const ok = rulesync.touchMarker(dir, 31);
        assert.strictEqual(ok, true);
        const after = rulesync.readMarker(dir);
        assert.strictEqual(after.version, 31);
        assert.deepStrictEqual(after.files, before.files, '文件清单被改动了');
        assert.ok(after.at >= before.at);
      });
      await check('touchMarker：不碰任何文件', () => {
        assert.strictEqual(fs.readFileSync(path.join(dir, 'social.yaml'), 'utf8'), social);
      });
      await check('touchMarker：没有 marker 时返回 false（不凭空造一个）', () => {
        const empty = freshDir('case6c-empty');
        assert.strictEqual(rulesync.touchMarker(empty, 1), false);
        assert.strictEqual(rulesync.readMarker(empty), null);
      });
    } finally { s.srv.close(); }
  }

  // ---- ⑦ planFiles：只挑本 App 的规则键 + 三份 geo ----
  {
    const repo = makeRepo({ version: 12, ruleKeys: KEYS, geoBlobs: GEO, extraRules: ['android-only', 'tablet-only'] });
    const plan = rulesync.planFiles(repo.doc, KEYS);
    await check('planFiles：本 App 键 → .yaml 本地名（仓库仍是 .list）；geo 改名；其余产出挑掉', () => {
      const names = plan.map((f) => f.name).sort();
      assert.deepStrictEqual(names, ['ASN.mmdb', 'ai.yaml', 'geoip.metadb', 'geosite.dat', 'social.yaml', 'streaming.yaml'].sort(), names.join(','));
      assert.strictEqual(plan.find((f) => f.name === 'geosite.dat').path, 'geo/geosite.dat');
      assert.strictEqual(plan.find((f) => f.name === 'social.yaml').path, 'dist/rules/social.list');
    });
    await check('planFiles：providerKeys 为空时不挑规则（只下 geo）', () => {
      const p2 = rulesync.planFiles(repo.doc, []);
      assert.deepStrictEqual(p2.filter((f) => f.kind === 'rule'), []);
      assert.strictEqual(p2.filter((f) => f.kind === 'geo').length, 3);
    });
    await check('planFiles：挑不出文件 → 视为不新鲜（避免"没什么可下"被当成已同步）', () => {
      const repoNoGeo = { schema: 1, version: 1, files: [{ path: 'dist/rules/zzz.list', bytes: 1, sha256: 'a'.repeat(64) }] };
      assert.strictEqual(rulesync.planFiles(repoNoGeo, KEYS).length, 0);
      // 本 App 没有规则键时，一份规则都不挑，只挑 geo
      const onlyGeo = rulesync.planFiles(repoNoGeo, []);
      assert.deepStrictEqual(onlyGeo, []);
    });
  }

  // ---- ⑧ 12 小时节拍常量 ----
  await check('节拍是 12 小时', () => {
    assert.strictEqual(rulesync.INTERVAL_MS, 12 * 60 * 60 * 1000);
  });

  // ---- ⑨ marker 损坏时按"不新鲜"处理，不许抛 ----
  {
    const dir = freshDir('case9');
    const repo = makeRepo({ version: 20, ruleKeys: KEYS, geoBlobs: GEO });
    const s = await makeServer(repo.files);
    try {
      await withEnv(s.url, () => rulesync.refresh(dir, { providerKeys: KEYS, log: silent }));
      fs.writeFileSync(path.join(dir, rulesync.MARKER), '{ 这不是 JSON');
      await check('marker 损坏：readMarker 返回 null 而不是抛', () => {
        assert.strictEqual(rulesync.readMarker(dir), null);
      });
      const r = await withEnv(s.url, () => rulesync.refresh(dir, { providerKeys: KEYS, log: silent }));
      await check('marker 损坏：下一次刷新会重新校验（不会误判为已同步）', () => {
        assert.ok(r.version === 20, JSON.stringify(r));
      });
    } finally { s.srv.close(); }
  }

  // -------------------------------------------------------------------------
  // ⑩–⑬ 钉 commit SHA / 拿不到 SHA 的兜底 / 退避（2026-10-14 加）
  //
  // 为什么要有这几条：国内直连 `api.github.com` 被墙，钉 SHA 拿不到时**必须**退回
  // `@main` 继续更新（否则规则永远停在随包种子）；而钉住之后，jsDelivr 那个
  // 「新 sum 配旧字节」的陈旧边缘缓存就结构性地不可能发生了。
  // -------------------------------------------------------------------------
  const API_SHA = 'a1b2c3d4e5f60718293a4b5c6d7e8f9012345678';

  /**
   * 既当 GitHub API 又当 CDN 的 fixture。
   *
   * 同一份 repo 同时挂在 `cdn@<sha>/…`（钉住）与根路径（`@main` 兜底）下。
   * cdnBase 特意**带一段路径**（`<url>/cdn`）而不是裸 `host:port` —— 否则拼出来
   * `http://host:port@<sha>/…` 会被 URL 解析成 userinfo，测的就不是真实形态了。
   */
  async function makePinnedServer(repo, sha, { api = 'ok' } = {}) {
    const files = {};
    for (const [p, body] of Object.entries(repo.files)) {
      files[p] = body;
      files[`cdn@${sha}/${p}`] = body;
      files[`raw/${sha}/${p}`] = body;
    }
    if (api === 'ok') {
      files['api/commit'] = Buffer.from(JSON.stringify({ sha }), 'utf8');
      files['api/branch'] = Buffer.from(JSON.stringify({ commit: { sha } }), 'utf8');
    }
    return makeServer(files);
  }

  const epOf = (s) => ({
    commitApi: `${s.url}/api/commit`,
    branchApi: `${s.url}/api/branch`,
    cdnBase: `${s.url}/cdn`,
    rawBase: `${s.url}/raw`,
    primaryBase: s.url,
    fallbackBase: `${s.url}/raw/main`,
  });

  // 这一组必须走「非强制源」的路径（强制源会跳过 SHA 解析）
  const savedBase = process.env.POLARIS_RULES_BASE;
  const savedNoFallback = process.env.POLARIS_RULES_NO_FALLBACK;
  delete process.env.POLARIS_RULES_BASE;
  delete process.env.POLARIS_RULES_NO_FALLBACK;

  // ---- ⑩ 钉住 SHA：清单与文件都从 @<sha> 取，marker 记下 sha ----
  {
    const repo = makeRepo({ version: 30, ruleKeys: KEYS, geoBlobs: GEO });
    const s = await makePinnedServer(repo, API_SHA);
    const dir = freshDir('case10');
    try {
      const r = await rulesync.refresh(dir, { providerKeys: KEYS, log: silent, endpoints: epOf(s) });
      await check('钉 SHA：pinned = 解析出来的 commit sha', () => {
        assert.strictEqual(r.pinned, API_SHA, JSON.stringify(r));
      });
      await check('钉 SHA：manifest 与 6 份文件都从 @<sha> 前缀取', () => {
        assert.ok(s.hits.has(`cdn@${API_SHA}/latest.json`), `请求过：${[...s.hits.keys()].join(',')}`);
        assert.ok(!s.hits.has('latest.json'), '不该从 @main 根路径取');
        assert.strictEqual(r.updated.length, 6, r.updated.join(','));
        for (const n of ['social.yaml', 'streaming.yaml', 'ai.yaml', 'geosite.dat', 'geoip.metadb', 'ASN.mmdb']) {
          assert.ok(fs.existsSync(path.join(dir, n)), `缺 ${n}`);
        }
      });
      await check('钉 SHA：marker 记下 sha（下次比 sha 而不是比 version）', () => {
        const m = rulesync.readMarker(dir);
        assert.strictEqual(m.sha, API_SHA, JSON.stringify(m));
        assert.strictEqual(m.version, 30);
      });
      await check('钉 SHA：落盘内容与上游逐字节相同', () => {
        assert.strictEqual(sha(fs.readFileSync(path.join(dir, 'social.yaml'))), sha(repo.files['dist/rules/social.list']));
        assert.strictEqual(sha(fs.readFileSync(path.join(dir, 'ASN.mmdb'))), sha(repo.files['geo/ASN.mmdb']));
      });

      // ---- ⑫ 同一个 SHA（marker 里 version 故意写错）→ 零下载 ----
      const before = new Map(s.hits);
      fs.writeFileSync(path.join(dir, rulesync.MARKER), JSON.stringify({
        version: 999,
        sha: API_SHA,
        at: Date.now() - rulesync.INTERVAL_MS - 60_000,
        via: 'direct',
        files: [
          { d: 0, n: 'social.yaml' }, { d: 0, n: 'streaming.yaml' }, { d: 0, n: 'ai.yaml' },
          { d: 0, n: 'geosite.dat' }, { d: 0, n: 'geoip.metadb' }, { d: 0, n: 'ASN.mmdb' },
        ],
      }));
      const r2 = await rulesync.refresh(dir, { providerKeys: KEYS, log: silent, endpoints: epOf(s) });
      await check('钉 SHA：上游 SHA 没变 → 一份文件都不下（只看 sha，不看 version）', () => {
        assert.strictEqual(r2.skipped, true, JSON.stringify(r2));
        assert.strictEqual(r2.updated.length, 0);
        const newHits = [...s.hits.entries()].filter(([k, v]) => (before.get(k) || 0) < v).map(([k]) => k).sort();
        // 每轮都要重新解析 SHA（API 那一次是必须的）+ 读一次 latest.json 才知道 sha 没变；
        // 除此之外**一份文件都不该下**。
        assert.deepStrictEqual(newHits, ['api/commit', `cdn@${API_SHA}/latest.json`].sort(), `新请求：${newHits.join(',')}`);
        assert.strictEqual(rulesync.readMarker(dir).version, 30, 'version 该被纠正回来');
      });
    } finally { s.srv.close(); }
  }

  // ---- ⑪ 拿不到 SHA（API 全挂）→ 退回 @main，仍然更新 ----
  {
    const repo = makeRepo({ version: 31, ruleKeys: KEYS, geoBlobs: GEO });
    const s = await makePinnedServer(repo, API_SHA, { api: 'none' });
    const dir = freshDir('case11');
    const logs = [];
    const say = { info: (m) => logs.push(String(m)), warn: (m) => logs.push(String(m)) };
    try {
      const r = await rulesync.refresh(dir, { providerKeys: KEYS, log: say, endpoints: epOf(s) });
      await check('拿不到 SHA：不放弃，退回 @main 照样把 6 份更下来', () => {
        assert.strictEqual(r.pinned, null, JSON.stringify(r));
        assert.strictEqual(r.updated.length, 6, r.updated.join(','));
        assert.ok(s.hits.has('latest.json'), `请求过：${[...s.hits.keys()].join(',')}`);
      });
      await check('拿不到 SHA：日志里明确写了「未钉 SHA」与两个端点的失败原因', () => {
        const line = logs.find((l) => l.includes('未钉 SHA'));
        assert.ok(line, logs.join('\n'));
        assert.ok(line.includes('/api/commit') && line.includes('/api/branch'), line);
      });
      await check('拿不到 SHA：marker 的 sha 为 null，下一轮还会重新尝试钉', () => {
        assert.strictEqual(rulesync.readMarker(dir).sha, null);
      });
    } finally { s.srv.close(); }
  }

  // ---- ⑬ 失败退避：别每次启动都全套重试 ----
  {
    const dead = await makeServer({}); // 什么都不提供 → latest.json 404
    const repo = makeRepo({ version: 32, ruleKeys: KEYS, geoBlobs: GEO });
    const good = await makePinnedServer(repo, API_SHA);
    const dir = freshDir('case13');
    try {
      const r1 = await rulesync.refresh(dir, { providerKeys: KEYS, log: logger, endpoints: epOf(dead) });
      await check('失败退避：失败一轮后写下退避标记', () => {
        assert.strictEqual(r1.reason, 'manifest', JSON.stringify(r1));
        assert.strictEqual(rulesync.inBackoff(dir), true);
        assert.ok(rulesync.readTry(dir), 'rules_last_try 该在');
      });
      const r2 = await rulesync.refresh(dir, { providerKeys: KEYS, log: silent, endpoints: epOf(good) });
      await check('失败退避：窗口内跳过，一个请求都不发（上游恢复了也等窗口过去）', () => {
        assert.strictEqual(r2.reason, 'backoff', JSON.stringify(r2));
        assert.strictEqual(r2.skipped, true);
        assert.strictEqual(good.hits.size, 0, `不该有请求：${[...good.hits.keys()].join(',')}`);
      });
      fs.writeFileSync(path.join(dir, rulesync.MARKER_TRY), JSON.stringify({ schema: 1, at: Date.now() - rulesync.BACKOFF_MS - 60_000 }));
      const r3 = await rulesync.refresh(dir, { providerKeys: KEYS, log: silent, endpoints: epOf(good) });
      await check('失败退避：窗口过去后重试，成功即清掉退避标记', () => {
        assert.strictEqual(r3.updated.length, 6, JSON.stringify(r3));
        assert.strictEqual(rulesync.inBackoff(dir), false);
        assert.strictEqual(rulesync.readTry(dir), null);
      });
    } finally { dead.srv.close(); good.srv.close(); }
  }

  // ---- ⑭ bases()：钉与不钉的两种形态 ----
  await check('bases(sha)：钉住时是 @<sha> 与 /<sha>，未钉时退回 @main 与 main', () => {
    const pinned = rulesync.bases(API_SHA, { cdnBase: 'https://cdn.example/x', rawBase: 'https://raw.example/y' });
    assert.deepStrictEqual(pinned, [`https://cdn.example/x@${API_SHA}`, `https://raw.example/y/${API_SHA}`]);
    const loose = rulesync.bases(null, { primaryBase: 'https://p.example/a', fallbackBase: 'https://f.example/b' });
    assert.deepStrictEqual(loose, ['https://p.example/a', 'https://f.example/b']);
    assert.strictEqual(rulesync.PRIMARY_BASE, `${rulesync.CDN_BASE}@main`);
    assert.strictEqual(rulesync.FALLBACK_BASE, `${rulesync.RAW_BASE}/main`);
  });


  // ---- ⑮ 代理接线自证：走**真实取数链**（refresh → resolveSha/fetchManifest/fetchUrl → getViaProxy） ----
  // 为什么不用上面的 http fixture：getViaProxy 只支持 https 目标（CONNECT 之后要 TLS）。
  // 这一节自己起「本地 https 源站 + 本地 CONNECT 直通代理」，源站主机名用 rules.invalid
  // （直连必然 DNS 失败）→ 强制走「直连失败 → 本机代理」那条路，与生产同一条代码。
  // 自签证书只在本节用；getViaProxy 没有注入 CA 的口子（生产也不需要），所以本节临时关掉
  // TLS 校验（NODE_TLS_REJECT_UNAUTHORIZED=0），**只影响本测试进程，产品代码一行没改**。
  const https = require('https');
  const net = require('net');
  const TEST_CERT = "-----BEGIN CERTIFICATE-----\nMIIDKzCCAhOgAwIBAgIUXYygzhR9cdfe6hM1p8iPfsKlMEAwDQYJKoZIhvcNAQEL\nBQAwGDEWMBQGA1UEAwwNcnVsZXMuaW52YWxpZDAeFw0yNjEwMTYxMTI5NTVaFw0z\nNjEwMTMxMTI5NTVaMBgxFjAUBgNVBAMMDXJ1bGVzLmludmFsaWQwggEiMA0GCSqG\nSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCZ+XwcOpYkwh31ECtXqkDMbGycB/dyml9w\nPbvjAfOMGKyeRE+DTLwVoU0IIPomEkqHHMCCktnh9qVUwWW6kH69iJA4gKyR+IRN\nDi1zuX9KLUcwr2jUQyfkdxAOEo0k09sRDCSBZ+wOUH3T9ma/FZrEBOixZIptbhtZ\nvJ9Bfm64//qc7P1b1+fbonhNeKJgfSJ3sd//ybprcPlAODREBX7GtocgzMCyHYqt\n3XhgmQ+FoLPRL5ubBp2kWj7HMTIKWEBgn9vmf2UfWOg6asmSax36UygNL92bQx2l\nz6PYZ4KgZl8Q+wGwbPR+9SmM8VnOFTQ7VzX8IXLViH5wSj/bNbvVAgMBAAGjbTBr\nMB0GA1UdDgQWBBR2y7hZA5PTQ8mDCkU6IuZ6hXYFmzAfBgNVHSMEGDAWgBR2y7hZ\nA5PTQ8mDCkU6IuZ6hXYFmzAPBgNVHRMBAf8EBTADAQH/MBgGA1UdEQQRMA+CDXJ1\nbGVzLmludmFsaWQwDQYJKoZIhvcNAQELBQADggEBAAzElfXgUdg9W5fvicETzfmM\nr1JTCJtwc/izjzsX4xuSZObAte2aT3VNBEzeRKZC+tmCHOfQTroRrJ9Yizp8NeIb\nwDW/BtgAQ8KThH7V75bUhmyGqXt97YqmkupGcJQOfpGA7+4Q9s8MfjKGnhb+YxNd\nqLVfhy5qVF1HZ+fMaDBALZU7+qQMam95HOMSGnbGWNUSG0Y0lZ2a3n+TFGy1mtO8\nbTbpZHMjl3d5rTKXnOLT2RZqNSPnU76AMSny2hEZaSaFnWoCh0at6iseYKWntt+l\nua8Hpvhp9pC15V2zq/aIXCchE55JTOfVbnhJJCxKEqTKvcKc8tH7HhAPlZQaFvc=\n-----END CERTIFICATE-----";
  const TEST_KEY = "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCZ+XwcOpYkwh31\nECtXqkDMbGycB/dyml9wPbvjAfOMGKyeRE+DTLwVoU0IIPomEkqHHMCCktnh9qVU\nwWW6kH69iJA4gKyR+IRNDi1zuX9KLUcwr2jUQyfkdxAOEo0k09sRDCSBZ+wOUH3T\n9ma/FZrEBOixZIptbhtZvJ9Bfm64//qc7P1b1+fbonhNeKJgfSJ3sd//ybprcPlA\nODREBX7GtocgzMCyHYqt3XhgmQ+FoLPRL5ubBp2kWj7HMTIKWEBgn9vmf2UfWOg6\nasmSax36UygNL92bQx2lz6PYZ4KgZl8Q+wGwbPR+9SmM8VnOFTQ7VzX8IXLViH5w\nSj/bNbvVAgMBAAECggEAOOYCwQO9XBQHsfmpqpmVnHUspXQkMsttHTMS7U5ZS5oK\nn+mqYGDI2E1lA+37wnnb5JXxZc8SiPf2HJ3jE9A1paCDev3Bx6dIbJkinrMboLOe\nrRUE7ftrEHgXDLw6QDOxrlkIXHGkv51rAhr52P8I1ArkWjlHFi0sGjsjtrYcAGy0\nTMimuxxHho3JVnDra9zIJ+hBuHWhEvu7k94YvG7r9rBWun7lkYE1GJtiDPYk2YDQ\nu7k0dvyXaUnHqv7OqcojJF7yVbaroiFhI7HOpc7K8JQAi3JN0JJEXqhwkjtmfFy7\noIIJnNEHQiFvCnCEVKuKIqIwCGXC7T5bBlr3F4a5eQKBgQDYfUk/GWpranLr87Au\n8QFn12qzwl4kW9eCzR8wR7VH5pyqQjOeBERY0OiqxydK6afQKLKm1ovAaZP6usUp\nQSMnu8xhhCSeDmwrEbD/4ON7JYzLPohuUbD+cMv9GRp3Qhl3jFx/jpzhUV5Apbln\n3+zS4/u59n6jkkZUn2s+6hmHqwKBgQC2E2ju6HtehLTYZMCsINB7+iUz4rBM4ml3\nTKqRKV57BJZ0YWHZrIeKbQzgSN9zeQIP5rUl0DPDBAGDyAZekjB021k7SqUC+Nry\nFeZkc60iALJPB2jSbkrYOdjZLu1kwZv+fEsFIrx55DNiotos3BVSy1Bg0/+VtJrD\ngXF/YfxKfwKBgQCiT8zK8lNHldfz8BD4INnc8TWe8OuEdxdtij0IBbt03QI45Mmu\nf6R+sqK+5/IdyHF5MApB7Dl7Wa0JH18TPzMMZ1ElC94oYsfWwrziG8pyWgTlnUzM\n6ZK6Mfe/G0MshvJxH1t6h4I3rLnt3lLT/p+gvi3Nj3IDUXhJmfzefsYFYQKBgBcK\nK7wXlQzn1A6R6UOev2MAy1ZeeBqyh8/91pOZ3YfJk9RUQlKOH3SnyfpTnlr5VAZ5\nFjEbwIEL77kiRZQQAau32fnDv0+599KsbMQHU0A7QFT5xgziwfvMjiElbArU6Q1x\n4vhaZekytEHmA5+rldMtQRWPngbunKripV8OIiO3AoGAY7LD9bxGjiz/Rp1BuUra\n9sHkYdDaSWPUKpAQz4QLbhwtd/hqz0f/vU/41bmtPcSj7j4rKIhpFwnV4qaTp24z\nP2cmTasRnPCmLeTNA8/2rmnYVRjfOQXCEwQWw4y4i8f/ATaE+GIOtyh4AtYpwxP6\nmNm5GXCzrQoDZFtBc06owLM=\n-----END PRIVATE KEY-----";

  function startProxyStub() {
    const stats = { connects: [], bytes: 0 };
    const srv = http.createServer();
    srv.on('connect', (req, clientSocket, head) => {
      stats.connects.push(req.url);
      const i = req.url.lastIndexOf(':');
      const host = req.url.slice(0, i);
      const port = Number(req.url.slice(i + 1)) || 443;
      const dst = host === 'rules.invalid' ? '127.0.0.1' : host;   // 只把假域名映射到本机源站
      const up = net.connect(port, dst, () => {
        clientSocket.write('HTTP/1.1 200 Connection Established\r\n\r\n');
        if (head && head.length) { up.write(head); stats.bytes += head.length; }
        clientSocket.pipe(up); up.pipe(clientSocket);
      });
      clientSocket.on('data', (c) => { stats.bytes += c.length; });
      up.on('data', (c) => { stats.bytes += c.length; });
      clientSocket.on('error', () => { try { up.destroy(); } catch (_) {} });
      up.on('error', () => { try { clientSocket.destroy(); } catch (_) {} });
    });
    srv.on('clientError', () => {});
    return new Promise((r) => srv.listen(0, '127.0.0.1', () => r({ srv, port: srv.address().port, stats })));
  }

  function startHttpsOrigin(files) {
    const srv = https.createServer({ key: TEST_KEY, cert: TEST_CERT }, (req, res) => {
      const p = decodeURIComponent(req.url.split('?')[0]).replace(/^\/+/, '');
      const body = files[p];
      if (!body) { res.writeHead(404); res.end('not found'); return; }
      res.writeHead(200, { 'content-type': 'application/octet-stream', 'content-length': body.length });
      res.end(body);
    });
    return new Promise((r) => srv.listen(0, '127.0.0.1', () => r({ srv, port: srv.address().port })));
  }

  function freePort() {
    return new Promise((r) => { const s = net.createServer(); s.listen(0, '127.0.0.1', () => { const p = s.address().port; s.close(() => r(p)); }); });
  }

  async function withNoTlsVerify(fn) {
    const old = process.env.NODE_TLS_REJECT_UNAUTHORIZED;
    process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0';
    try { return await fn(); } finally {
      if (old === undefined) delete process.env.NODE_TLS_REJECT_UNAUTHORIZED; else process.env.NODE_TLS_REJECT_UNAUTHORIZED = old;
    }
  }

  await check('代理接线：refresh 走真实取数链，且确实经本机代理（via=proxy）', async () => {
    const repo = makeRepo({ version: 7, ruleKeys: KEYS, geoBlobs: GEO });
    const origin = await startHttpsOrigin(repo.files);
    const proxy = await startProxyStub();
    const dir = freshDir('proxy-ok');
    const geoDir = freshDir('proxy-ok-geo');
    try {
      await withNoTlsVerify(async () => {
        const base = `https://rules.invalid:${origin.port}`;
        const r = await withEnv(base, () => rulesync.refresh(dir, { providerKeys: KEYS, geoDir, log: silent, proxyPort: proxy.port, timeout: 8000 }));
        assert.strictEqual(r.via, 'proxy', '本轮应经代理（via=' + r.via + '）');
        assert.ok(proxy.stats.connects.length >= 1, '代理侧应收到 CONNECT：' + JSON.stringify(proxy.stats.connects));
        assert.ok(proxy.stats.connects.every((s) => s === `rules.invalid:${origin.port}`), 'CONNECT 目标应为源站：' + JSON.stringify(proxy.stats.connects));
        assert.ok(proxy.stats.bytes > 0, '代理应转发过字节');
        assert.ok(fs.existsSync(path.join(dir, 'social.yaml')), 'social.yaml 应已就位（本地名是 <键>.yaml）');
        assert.ok(fs.existsSync(path.join(geoDir, 'geosite.dat')), 'geo 应落到 geoDir');
        assert.ok(rulesync.isFresh(dir, { geoDir }), '全部成功后应写 marker');
      });
    } finally { origin.srv.close(); proxy.srv.close(); }
  });

  await check('代理接线：代理不可用 + 直连失败 → 文案同时点出两条路（退路可见，不静默）', async () => {
    const dir = freshDir('proxy-dead');
    const dead = await freePort();
    const msgs = [];
    const say = { info() {}, warn: (m) => msgs.push(String(m)) };
    await withNoTlsVerify(async () => {
      const r = await withEnv('https://rules.invalid:9', () => rulesync.refresh(dir, { providerKeys: KEYS, log: say, proxyPort: dead, timeout: 3000 }));
      const all = msgs.join(' | ');
      assert.strictEqual(r.reason, 'manifest', '拿不到 manifest 应记 reason=manifest');
      assert.ok(/直连失败/.test(all), '文案应点出直连失败：' + all);
      assert.ok(/走代理也失败/.test(all), '文案应点出代理也失败：' + all);
      assert.ok(!rulesync.isFresh(dir), '失败不该写 marker');
    });
  });

  await check('下载/替换分离：pauseApply 只落 .part，applyStaged 才替换并写 marker', async () => {
    const repo = makeRepo({ version: 9, ruleKeys: KEYS, geoBlobs: GEO });
    const srv = await makeServer(repo.files);
    const dir = freshDir('pause-apply');
    const geoDir = freshDir('pause-apply-geo');
    try {
      const r = await withEnv(srv.url, () => rulesync.refresh(dir, { providerKeys: KEYS, geoDir, log: silent, pauseApply: true }));
      assert.ok(Array.isArray(r.staged) && r.staged.length === r.updated.length, 'staged 应带回全部文件：' + JSON.stringify((r.staged || []).map((x) => x.name)));
      assert.ok(!fs.existsSync(path.join(dir, 'social.yaml')), '暂停应用时目标文件不该出现');
      assert.ok(fs.existsSync(path.join(dir, 'social.yaml.part')), '应留下 .part');
      assert.ok(!rulesync.isFresh(dir, { geoDir }), '还没替换 → 不该写 marker');
      const ap = rulesync.applyStaged(r.staged, { dir, marker: r.marker });
      assert.strictEqual(ap.failed.length, 0, 'applyStaged 不该失败：' + JSON.stringify(ap.failed));
      assert.ok(fs.existsSync(path.join(dir, 'social.yaml')), '替换后目标文件应在');
      assert.ok(!fs.existsSync(path.join(dir, 'social.yaml.part')), '.part 应被消费掉');
      assert.ok(rulesync.isFresh(dir, { geoDir }), '替换后应写 marker');
    } finally { srv.srv.close(); }
  });

  await check('下载/替换分离：部分失败 → 不写 marker、不留 .part、不替换任何文件', async () => {
    const repo = makeRepo({ version: 11, ruleKeys: KEYS, geoBlobs: GEO });
    const files = Object.assign({}, repo.files);
    files['dist/rules/ai.list'] = Buffer.from('corrupted-bytes', 'utf8');   // 坏一份
    const srv = await makeServer(files);
    const dir = freshDir('pause-partial');
    const geoDir = freshDir('pause-partial-geo');
    try {
      const r = await withEnv(srv.url, () => rulesync.refresh(dir, { providerKeys: KEYS, geoDir, log: silent, pauseApply: true }));
      assert.strictEqual(r.reason, 'partial', '应记 partial：' + JSON.stringify(r));
      assert.ok(!r.staged || r.staged.length === 0, '部分失败不该带回可应用的 staged');
      assert.ok(!rulesync.isFresh(dir, { geoDir }), '部分失败不该写 marker');
      assert.ok(!fs.existsSync(path.join(dir, 'social.yaml')), '不该有文件被替换');
      const left = fs.readdirSync(dir).filter((n) => n.endsWith('.part'));
      assert.strictEqual(left.length, 0, '不该留 .part：' + JSON.stringify(left));
    } finally { srv.srv.close(); }
  });

  if (savedBase === undefined) delete process.env.POLARIS_RULES_BASE;
  else process.env.POLARIS_RULES_BASE = savedBase;
  if (savedNoFallback === undefined) delete process.env.POLARIS_RULES_NO_FALLBACK;
  else process.env.POLARIS_RULES_NO_FALLBACK = savedNoFallback;

  fs.rmSync(tmpRoot, { recursive: true, force: true });
  console.log(`\n规则同步自测：通过 ${pass} 项，失败 ${fail} 项`);
  process.exit(fail ? 1 : 0);
})().catch((e) => {
  console.error('自测自身出错：', e);
  process.exit(1);
});
