// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only
'use strict';
/**
 * 本机 HTTP 代理的 CONNECT 隧道（**共用实现**）。
 *
 * 背景（用户 2026-10-16 实测）：Windows 端应用内更新「开着代理仍直连」——
 * 下载走 Node 裸 https，既不读设置里的本机代理端口，也不读 Windows 系统代理，
 * 于是 188.9MB 的便携版在需要代理的网络里慢且失败。规则同步（rulesync.js）早就有
 * 「直连失败 → 走 127.0.0.1:<mixed-port>」的兜底，但那段 CONNECT 是手写的；这里把它
 * 抽成一个实现给两处用，免得 CONNECT 响应头 / TLS SNI / 超时这些细节各写一遍、
 * 各踩一次坑。
 *
 * 只做两件事：
 *   connectViaProxy()   在 proxyHost:proxyPort 上建 CONNECT 隧道；https 目标在隧道上
 *                       再做 TLS，resolve 出隧道末端的 socket。
 *   createTunnelAgent() 把上面的 socket 包成 Node 的 http/https Agent。调用方照常
 *                       mod.get(url, { agent })，拿到的就是正常的 IncomingMessage ——
 *                       content-length / 分块编码 / 连接管理全交给 Node 的 HTTP 栈，
 *                       不用自己解析响应头（188MB 的包尤其不能自己拼 body）。
 *
 * 安全：TLS 默认走 Node 的严格校验（rejectUnauthorized=true + 系统 CA）。只有自检
 * 会通过 opts.tlsOptions 注入自签 CA（见 scripts/updater-proxy-test.js）；生产不注入。
 */

const http = require('http');
const https = require('https');
const net = require('net');
const tls = require('tls');

const DEFAULT_TIMEOUT = 120000;
/** CONNECT 响应头上限：正常几十字节，给 64KB 兜底，别让对端拿垃圾把内存撑爆 */
const MAX_CONNECT_HEAD = 64 * 1024;

/**
 * 在代理上建立到 targetHost:targetPort 的 CONNECT 隧道。
 *
 * @param {object} opts
 * @param {string} opts.targetHost   目标主机（不含端口）
 * @param {number} opts.targetPort   目标端口
 * @param {string} [opts.protocol]   'https:'（默认）= 隧道上再 TLS；'http:' = 裸隧道
 * @param {string} [opts.proxyHost]  代理主机，默认 127.0.0.1
 * @param {number} opts.proxyPort    代理端口（必填）
 * @param {number} [opts.timeout]    CONNECT+TLS 建连总超时
 * @param {object} [opts.tlsOptions] 传给 tls.connect 的选项（ca / rejectUnauthorized…）
 * @param {string} [opts.servername] SNI；目标是 IP 字面量时强制不传（IP 不能当 SNI）
 * @returns {Promise<import('net').Socket>} 隧道末端 socket
 */
function connectViaProxy(opts) {
  const o = opts || {};
  const proxyPort = Number(o.proxyPort);
  const proxyHost = o.proxyHost || '127.0.0.1';
  const timeout = Number(o.timeout) > 0 ? Number(o.timeout) : DEFAULT_TIMEOUT;
  const isHttp = String(o.protocol || 'https:').indexOf('http:') === 0;
  const port = Number(o.targetPort) || (isHttp ? 80 : 443);
  if (!proxyPort || proxyPort <= 0 || proxyPort > 65535) {
    return Promise.reject(new Error('代理端口不合法：' + o.proxyPort));
  }
  const target = String(o.targetHost || '');
  if (!target) return Promise.reject(new Error('目标主机为空'));

  return new Promise(function (resolve, reject) {
    let done = false;
    const sock = net.connect(proxyPort, proxyHost);
    let timer = null;
    function fail(e) {
      if (done) return;
      done = true;
      if (timer) clearTimeout(timer);
      try { sock.destroy(); } catch (_) {}
      reject(e);
    }
    timer = setTimeout(function () {
      fail(new Error('代理请求超时（' + timeout + 'ms 内没有完成 CONNECT ' + target + ':' + port + '）'));
    }, timeout);
    sock.on('error', function (e) {
      fail(new Error('代理不可用（' + proxyHost + ':' + proxyPort + '）：' + e.message));
    });
    sock.on('connect', function () {
      sock.write('CONNECT ' + target + ':' + port + ' HTTP/1.1\r\nHost: ' + target + ':' + port + '\r\n\r\n');
    });

    let head = Buffer.alloc(0);
    function onData(c) {
      head = Buffer.concat([head, c]);
      const idx = head.indexOf('\r\n\r\n');
      if (idx < 0) {
        if (head.length > MAX_CONNECT_HEAD) fail(new Error('代理返回的 CONNECT 响应头过大'));
        return;
      }
      sock.removeListener('data', onData);
      const statusLine = head.slice(0, head.indexOf('\r\n')).toString('latin1');
      if (!/^HTTP\/1\.[01] 200/.test(statusLine)) {
        fail(new Error('代理拒绝 CONNECT：' + statusLine));
        return;
      }
      const rest = head.slice(idx + 4);
      if (isHttp) {
        if (done) return;
        done = true;
        if (timer) clearTimeout(timer);
        if (rest.length) sock.unshift(rest);
        resolve(sock);
        return;
      }
      // IP 字面量不能当 SNI（Node 会直接报错）；域名才发 SNI。
      const servername = net.isIP(target) ? undefined : (o.servername || target);
      const tlsOptions = Object.assign({}, o.tlsOptions || {}, { socket: sock, servername: servername });
      const tlsSock = tls.connect(tlsOptions, function () {
        if (done) return;
        done = true;
        if (timer) clearTimeout(timer);
        resolve(tlsSock);
      });
      if (rest.length) tlsSock.unshift(rest);
      tlsSock.on('error', function (e) { fail(e); });
    }
    sock.on('data', onData);
  });
}

/**
 * 把 CONNECT 隧道包成 http/https Agent。
 *
 * @param {string} protocol 目标协议（'http:' 或 'https:'；决定 Agent 类型与是否 TLS）
 * @param {object} opts proxyHost / proxyPort / timeout / tlsOptions
 * @returns {import('http').Agent} keepAlive:false —— 下载是一次性大流量，连接池没意义，
 *          而且回退直连/失败重试时能干净地把隧道 socket 一起销毁。
 */
function createTunnelAgent(protocol, opts) {
  const o = opts || {};
  const isHttp = String(protocol || '').indexOf('http:') === 0;
  const Base = isHttp ? http.Agent : https.Agent;
  const targetProtocol = isHttp ? 'http:' : 'https:';
  function TunnelAgent() { Base.call(this, { keepAlive: false, maxSockets: 4 }); }
  TunnelAgent.prototype = Object.create(Base.prototype);
  TunnelAgent.prototype.constructor = TunnelAgent;
  TunnelAgent.prototype.createConnection = function (options, cb) {
    const opt = options || {};
    const tlsOptions = Object.assign({}, o.tlsOptions || {});
    if (opt.ca) tlsOptions.ca = opt.ca;
    if (opt.rejectUnauthorized !== undefined) tlsOptions.rejectUnauthorized = opt.rejectUnauthorized;
    connectViaProxy({
      targetHost: opt.host,
      targetPort: opt.port,
      protocol: targetProtocol,
      proxyHost: o.proxyHost,
      proxyPort: o.proxyPort,
      timeout: o.timeout,
      tlsOptions: tlsOptions,
      servername: opt.servername,
    }).then(function (sock) { cb(null, sock); }, function (e) { cb(e); });
  };
  return new TunnelAgent();
}

module.exports = { connectViaProxy, createTunnelAgent, DEFAULT_TIMEOUT, MAX_CONNECT_HEAD };
