'use strict';
/**
 * 经本机 HTTP 代理（mihomo mixed-port）的 CONNECT 隧道 —— 给自更新下载用。
 *
 * 为什么需要它：自更新下载原来用 Node 原生 https.get 直连 GitHub，
 * **既不读系统代理、也不走本机 mixed-port**；而 App 自己就是代理客户端，
 * 用户明明开着代理，更新包却在裸奔 —— 慢、且经常失败。
 * core/rulesync.js 早就用同一思路（getViaProxy）解决了规则同步的同类问题，
 * 这里只是把它做成**返回流**的版本：更新包 188MB，不能整包进内存。
 *
 * 只在本机代理端口上建隧道，不碰系统代理设置。
 */
const net = require('net');
const tls = require('tls');

function parseHead(buf) {
  const idx = buf.indexOf('\r\n\r\n');
  if (idx < 0) return null;
  const head = buf.slice(0, idx).toString('latin1');
  const lines = head.split('\r\n');
  const m = /^HTTP\/(\d\.\d) (\d{3})/.exec(lines[0] || '');
  if (!m) return null;
  const headers = {};
  for (const line of lines.slice(1)) {
    const i = line.indexOf(':');
    if (i > 0) headers[line.slice(0, i).trim().toLowerCase()] = line.slice(i + 1).trim();
  }
  return { statusCode: Number(m[2]), headers, rest: buf.slice(idx + 4) };
}

function openTunnel(url, proxyPort, opts = {}) {
  const timeoutMs = Number(opts.timeoutMs) || 30000;
  const userAgent = String(opts.userAgent || 'Polaris-Windows');
  return new Promise((resolve, reject) => {
    let u;
    try { u = new URL(url); } catch (_) { return reject(new Error('更新地址不是合法 URL')); }
    if (u.protocol !== 'https:') return reject(new Error('代理隧道只支持 https 下载'));
    const port = Number(proxyPort) || 0;
    if (!(port > 0)) return reject(new Error('没有可用的本机代理端口'));
    const hostport = `${u.hostname}:${u.port || 443}`;
    const sock = net.connect(port, '127.0.0.1');
    let done = false;
    const timer = setTimeout(() => fail(new Error(`连接超时（经 127.0.0.1:${port} 的 ${timeoutMs}ms 内没有连上 ${u.hostname}）`)), timeoutMs);
    function fail(e) {
      if (done) return;
      done = true;
      clearTimeout(timer);
      try { sock.destroy(); } catch (_) {}
      reject(e);
    }
    sock.on('error', (e) => fail(new Error(`代理不可用（127.0.0.1:${port}）：${e.message}`)));
    sock.on('connect', () => {
      sock.write(`CONNECT ${hostport} HTTP/1.1\r\nHost: ${hostport}\r\n\r\n`);
    });
    let head = Buffer.alloc(0);
    const onData = (c) => {
      head = Buffer.concat([head, c]);
      const parsed = parseHead(head);
      if (!parsed) {
        if (head.length > 65536) fail(new Error('代理响应头异常'));
        return;
      }
      sock.removeListener('data', onData);
      if (parsed.statusCode !== 200) return fail(new Error(`代理拒绝 CONNECT：HTTP ${parsed.statusCode}`));
      const tlsSock = tls.connect({ socket: sock, servername: u.hostname }, () => {
        clearTimeout(timer);
        const pathPart = `${u.pathname}${u.search}`;
        tlsSock.write(
          `GET ${pathPart} HTTP/1.1\r\nHost: ${u.hostname}\r\nUser-Agent: ${userAgent}\r\n` +
          'Accept: */*\r\nConnection: close\r\n\r\n'
        );
      });
      if (parsed.rest.length) tlsSock.unshift(parsed.rest);
      let respHead = Buffer.alloc(0);
      let settled = false;
      const onRespData = (d) => {
        respHead = Buffer.concat([respHead, d]);
        const r = parseHead(respHead);
        if (!r) return;
        tlsSock.removeListener('data', onRespData);
        settled = true;
        if (r.rest.length) tlsSock.unshift(r.rest);
        resolve({ statusCode: r.statusCode, headers: r.headers, stream: tlsSock, destroy: () => { try { tlsSock.destroy(); } catch (_) {} } });
      };
      tlsSock.on('data', onRespData);
      tlsSock.on('error', (e) => { if (!settled) fail(new Error('经代理建立 TLS 失败：' + e.message)); });
    };
    sock.on('data', onData);
  });
}

/** 与 updater.fetchStream 同语义：返回 200 的响应流；3xx 自己跟随（最多 5 跳）。 */
async function fetchStreamVia(url, proxyPort, opts = {}) {
  const maxRedirects = Number(opts.maxRedirects ?? 5);
  let cur = url;
  for (let i = 0; i <= maxRedirects; i++) {
    const r = await openTunnel(cur, proxyPort, opts);
    if (r.statusCode >= 300 && r.statusCode < 400 && r.headers.location) {
      r.destroy();
      try { cur = new URL(r.headers.location, cur).toString(); } catch (_) { throw new Error('重定向地址不合法'); }
      continue;
    }
    if (r.statusCode !== 200) {
      r.destroy();
      throw new Error(`下载失败：HTTP ${r.statusCode}`);
    }
    return r.stream;
  }
  throw new Error('重定向次数过多');
}

module.exports = { openTunnel, fetchStreamVia };
