// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

/**
 * geosync 自测：全部走本地 HTTP 服务器，不碰外网。
 *
 *   node scripts/geosync-test.js
 *
 * 钉死五件事：
 *   1. isFresh 的判定（缺文件 / 无标记 / 未过期 / 已过期）
 *   2. 正常刷新：三份文件落地 + 标记写入
 *   3. sha256 不匹配时**绝不覆盖**旧文件（这是「坏文件不能让内核起不来」的保证）
 *   4. 上游 404 时不抛异常、旧文件保留
 *   5. 未过期时一个请求都不发（24 小时周期真的生效，不是每次启动都拉 24 MB）
 */

'use strict';

const assert = require('assert');
const crypto = require('crypto');
const fs = require('fs');
const http = require('http');
const os = require('os');
const path = require('path');

const { isFresh, FILES: BUILTIN_FILES, MARKER } = require('../electron/core/geosync');

/** 本地名 → 上游远端名。ASN.mmdb 在上游叫 GeoLite2-ASN.mmdb。 */
const REMOTE_NAME = {
  'geosite.dat': 'geosite.dat',
  'geoip.metadb': 'geoip.metadb',
  'ASN.mmdb': 'GeoLite2-ASN.mmdb',
};
const NAMES = Object.keys(BUILTIN_FILES);
const remote = (local) => REMOTE_NAME[local];
const sha256 = (buf) => crypto.createHash('sha256').update(buf).digest('hex');

let passed = 0;
let failed = 0;
function ok(name) { passed += 1; console.log('  ok   ' + name); }

function tmpdir() {
  return fs.mkdtempSync(path.join(os.tmpdir(), 'geosync-test-'));
}

/** 往目录里铺三份本地文件（模拟「已有一份能用的规则库」）。 */
function seed(dir) {
  fs.mkdirSync(dir, { recursive: true });
  for (const name of NAMES) {
    fs.writeFileSync(path.join(dir, name), Buffer.from('v1-' + name));
  }
}

function age(dir, ms) {
  const t = new Date(Date.now() - ms);
  fs.utimesSync(path.join(dir, MARKER), t, t);
}

function startServer(bodies) {
  const hits = [];
  const server = http.createServer((req, res) => {
    const name = decodeURIComponent(req.url.split('/').pop());
    hits.push(name);
    const body = bodies[name];
    if (!body) {
      res.writeHead(404).end('not found');
      return;
    }
    res.writeHead(200, { 'content-type': 'application/octet-stream' });
    res.end(body);
  });
  return new Promise((resolve_) => {
    server.listen(0, '127.0.0.1', () => resolve_({ server, hits, port: server.address().port }));
  });
}

async function main() {
  const bodies = {};
  for (const name of NAMES) {
    bodies[remote(name)] = Buffer.from('v1-' + name);
  }
  const { server, hits, port } = await startServer(bodies);
  // BASE 在模块加载时读取：先设环境变量、清 require 缓存，再重新加载
  process.env.POLARIS_GEO_BASE = `http://127.0.0.1:${port}/repo`;
  const modPath = require.resolve('../electron/core/geosync');
  delete require.cache[modPath];
  const { refresh } = require(modPath);
  const log = { info(m) { console.log('  .. ' + m); }, warn(m) { console.log('  !! ' + m); } };

  const putSums = () => {
    for (const name of NAMES) {
      bodies[remote(name) + '.sha256sum'] = Buffer.from(sha256(bodies[remote(name)]) + '  ' + remote(name) + '\n');
    }
  };
  putSums();

  try {
    /* ---- 1. isFresh ---- */
    {
      const d = tmpdir();
      assert.strictEqual(isFresh(d), false);
      ok('isFresh：空目录 → 需要刷新');

      seed(d);
      assert.strictEqual(isFresh(d), false);
      ok('isFresh：缺标记 → 需要刷新');

      fs.writeFileSync(path.join(d, MARKER), String(Date.now()));
      assert.strictEqual(isFresh(d), true);
      ok('isFresh：未过期 → 跳过');

      age(d, 25 * 3600 * 1000);
      assert.strictEqual(isFresh(d), false);
      ok('isFresh：超过 24 小时 → 需要刷新');

      fs.unlinkSync(path.join(d, 'geosite.dat'));
      fs.writeFileSync(path.join(d, MARKER), String(Date.now()));
      assert.strictEqual(isFresh(d), false);
      ok('isFresh：文件缺失 → 需要刷新');
    }

    /* ---- 2. 正常刷新 ---- */
    {
      const d = tmpdir();
      const updated = await refresh(d, log);
      assert.deepStrictEqual([...updated].sort(), [...NAMES].sort(), '三份都应更新');
      for (const name of NAMES) {
        assert.deepStrictEqual(fs.readFileSync(path.join(d, name)), bodies[remote(name)], name + ' 内容应一致');
      }
      assert.ok(fs.existsSync(path.join(d, MARKER)), '应写入时间标记');
      ok('正常刷新：三份文件落地 + 标记写入');

      assert.deepStrictEqual(await refresh(d, log), [], '未过期时不应再下载');
      ok('未过期：第二次刷新直接返回空');
    }

    /* ---- 3. sha256 不匹配 → 绝不覆盖 ---- */
    {
      const d = tmpdir();
      seed(d);
      fs.writeFileSync(path.join(d, MARKER), String(Date.now()));
      const before = fs.readFileSync(path.join(d, 'geosite.dat'));

      bodies['geosite.dat'] = Buffer.from('v2-geosite');
      bodies['geosite.dat.sha256sum'] = Buffer.from('0'.repeat(64) + '  geosite.dat\n');
      age(d, 25 * 3600 * 1000);

      // 单份校验失败只应挡住它自己，另外两份照常更新
      const updated = await refresh(d, log);
      assert.deepStrictEqual([...updated].sort(), ['ASN.mmdb', 'geoip.metadb'], '只应更新校验通过的两份');
      assert.deepStrictEqual(fs.readFileSync(path.join(d, 'geosite.dat')), before, '旧文件必须原样保留');
      assert.deepStrictEqual(fs.readFileSync(path.join(d, 'geoip.metadb')), bodies['geoip.metadb']);
      ok('sha256 不匹配：挡住该份、旧文件保留，其余两份照常更新');

      bodies['geosite.dat.sha256sum'] = Buffer.from(sha256(bodies['geosite.dat']) + '  geosite.dat\n');
      age(d, 25 * 3600 * 1000);
      const fixed = await refresh(d, log);
      assert.ok(fixed.includes('geosite.dat'), '校验通过后 geosite.dat 应被更新');
      assert.deepStrictEqual(fs.readFileSync(path.join(d, 'geosite.dat')), bodies['geosite.dat']);
      ok('sha256 改对后：正常覆盖');

      // 恢复成第一版，供后续用例复用
      bodies['geosite.dat'] = Buffer.from('v1-geosite.dat');
      putSums();
    }

    /* ---- 4. 上游 404 → 不抛、不覆盖 ---- */
    {
      const d = tmpdir();
      seed(d);
      fs.writeFileSync(path.join(d, MARKER), String(Date.now()));
      const before = fs.readFileSync(path.join(d, 'ASN.mmdb'));
      delete bodies['GeoLite2-ASN.mmdb'];
      delete bodies['GeoLite2-ASN.mmdb.sha256sum'];
      age(d, 25 * 3600 * 1000);

      const updated = await refresh(d, log);
      assert.ok(!updated.includes('ASN.mmdb'), '404 的文件不应报告更新');
      assert.deepStrictEqual(fs.readFileSync(path.join(d, 'ASN.mmdb')), before, '旧文件必须保留');
      ok('上游 404：不抛异常，旧文件保留');

      bodies['GeoLite2-ASN.mmdb'] = Buffer.from('v1-ASN.mmdb');
      putSums();
    }

    /* ---- 5. 未过期时真的一个请求都不发 ---- */
    {
      const d = tmpdir();
      await refresh(d, log);
      const mark = hits.length;
      await refresh(d, log);
      await refresh(d, log);
      assert.strictEqual(hits.length, mark, '未过期时不应再发任何请求');
      ok('未过期：连续三次刷新，请求数不增加');
    }
  } finally {
    server.close();
  }

  console.log(`\ngeosync 自测：通过 ${passed} 项 / 失败 ${failed} 项`);
  process.exit(failed ? 1 : 0);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});