'use strict';
/**
 * 自证：**更新下载是否真的经本机代理**。
 *
 * 做法：起一个本地 CONNECT 直通代理（记录每一条 CONNECT），把 updater 的代理端口指向它，
 * 再去下载一个真实 https 目标；断言：
 *   ① 代理侧确实收到了 github.com:443 的 CONNECT（证明走代理、不是裸奔）；
 *   ② 下到的字节与直连拿到的一致；
 *   ③ 对照：清掉端口提供者（=核心没跑）→ 代理侧收不到任何 CONNECT（证明是代理分支在起作用）。
 *
 * 用法：node scripts/updater-proxy-test.js
 */
const net = require('net');
const https = require('https');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const updater = require(path.join(ROOT, 'electron/core/updater'));

const TARGET = process.env.PROXY_TEST_URL
  || 'https://github.com/ipevel/polaris/releases/download/v1.12.2/SHA256SUMS.txt';

function startPassthroughProxy() {
  const seen = [];
  const server = net.createServer((client) => {
    let buf = Buffer.alloc(0);
    const onData = (c) => {
      buf = Buffer.concat([buf, c]);
      const idx = buf.indexOf('\r\n\r\n');
      if (idx < 0) return;
      client.removeListener('data', onData);
      const head = buf.slice(0, idx).toString('latin1').split('\r\n');
      const m = /^CONNECT ([^ ]+) HTTP\/1\.1/.exec(head[0] || '');
      if (!m) { client.destroy(); return; }
      seen.push(m[1]);
      const [host, port] = m[1].split(':');
      const upstream = net.connect(Number(port) || 443, host, () => {
        client.write('HTTP/1.1 200 Connection Established\r\n\r\n');
        if (buf.length > idx + 4) upstream.write(buf.slice(idx + 4));
        client.pipe(upstream);
        upstream.pipe(client);
      });
      upstream.on('error', () => client.destroy());
      client.on('error', () => upstream.destroy());
    };
    client.on('data', onData);
    client.on('error', () => {});
  });
  return new Promise((resolve) => {
    server.listen(0, '127.0.0.1', () => resolve({ server, port: server.address().port, seen }));
  });
}

function directFetch(url) {
  return new Promise((resolve, reject) => {
    https.get(url, { headers: { 'User-Agent': 'Polaris-Windows' } }, (res) => {
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => resolve(Buffer.concat(chunks)));
    }).on('error', reject);
  });
}

function readAll(stream) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    stream.on('data', (c) => chunks.push(c));
    stream.on('end', () => resolve(Buffer.concat(chunks)));
    stream.on('error', reject);
  });
}

(async () => {
  const proxy = await startPassthroughProxy();
  let fail = 0;
  try {
    // ① 代理分支
    updater.setProxyPortProvider(() => proxy.port);
    const viaProxy = await readAll(await updater.fetchStream(TARGET));
    console.log('[1] 经代理下载字节数 =', viaProxy.length, '｜ 代理侧记录到的 CONNECT =', JSON.stringify(proxy.seen));
    if (!proxy.seen.some((s) => s.startsWith('github.com:'))) { console.log('[FAIL] 代理侧没有收到 github.com 的 CONNECT'); fail++; }
    else console.log('[ OK ] 下载确实经本机代理');

    // ② 与直连内容一致（直连基线用同一个 fetchStream，只把端口提供者清掉 —— 裸 https.get 不跟随 302，会被当成 0 字节）
    updater.setProxyPortProvider(() => 0);
    const direct = await readAll(await updater.fetchStream(TARGET));
    const same = viaProxy.length > 0 && viaProxy.equals(direct);
    console.log('[2] 直连字节数 =', direct.length, '｜ 经代理/直连一致 =', same);
    if (!same) { console.log('[FAIL] 经代理与直连的内容不一致'); fail++; }

    // ③ 对照：核心没跑（端口提供者返回 0）→ 代理侧不应再有新 CONNECT
    updater.setProxyPortProvider(() => 0);
    const before = proxy.seen.length;
    const direct2 = await readAll(await updater.fetchStream(TARGET));
    console.log('[3] 无代理分支字节数 =', direct2.length, '｜ 期间新增 CONNECT =', proxy.seen.length - before);
    if (proxy.seen.length !== before) { console.log('[FAIL] 无代理分支竟走了代理'); fail++; }
    else console.log('[ OK ] 无代理分支保持直连（对照成立）');
  } catch (e) {
    console.log('[FAIL] 异常：' + (e && e.message));
    fail++;
  } finally {
    proxy.server.close();
  }
  console.log(fail === 0 ? 'RESULT: PASS（更新下载经本机代理，且对照成立）' : 'RESULT: FAIL(' + fail + ')');
  process.exit(fail === 0 ? 0 : 1);
})();
