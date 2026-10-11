'use strict';
/**
 * mihomo external-controller 客户端。
 *
 * 控制面只绑 127.0.0.1，端口随机、secret 随机生成后写进 config.yaml，
 * 不对外暴露。所有请求都带 Authorization: Bearer <secret>。
 */

const http = require('http');
const WebSocket = require('ws');
const log = require('../logger');

function sleep(ms) { return new Promise((r) => setTimeout(r, ms)); }

class Controller {
  constructor(port, secret) {
    this.host = '127.0.0.1';
    this.port = port;
    this.secret = secret;
  }

  get base() { return `http://${this.host}:${this.port}`; }

  request(method, path, body, timeoutMs = 15000) {
    return new Promise((resolve, reject) => {
      const payload = body === undefined ? null : Buffer.from(JSON.stringify(body));
      const req = http.request(
        {
          host: this.host,
          port: this.port,
          path,
          method,
          headers: {
            Authorization: `Bearer ${this.secret}`,
            'Content-Type': 'application/json',
            ...(payload ? { 'Content-Length': payload.length } : {}),
          },
        },
        (res) => {
          const chunks = [];
          res.on('data', (c) => chunks.push(c));
          res.on('end', () => {
            const text = Buffer.concat(chunks).toString('utf8');
            if (res.statusCode >= 400) {
              reject(new Error(`mihomo ${method} ${path} -> ${res.statusCode} ${text.slice(0, 200)}`));
              return;
            }
            if (!text) { resolve(null); return; }
            try { resolve(JSON.parse(text)); } catch (_) { resolve(text); }
          });
        },
      );
      req.setTimeout(timeoutMs, () => req.destroy(new Error('mihomo 请求超时')));
      req.on('error', reject);
      if (payload) req.write(payload);
      req.end();
    });
  }

  get(p) { return this.request('GET', p); }
  put(p, b) { return this.request('PUT', p, b); }
  patch(p, b) { return this.request('PATCH', p, b); }
  post(p, b) { return this.request('POST', p, b); }

  /** 等待控制面就绪 */
  async waitReady(timeoutMs = 20000) {
    const deadline = Date.now() + timeoutMs;
    let lastErr;
    while (Date.now() < deadline) {
      try {
        const v = await this.get('/version');
        if (v && v.version) return v;
      } catch (e) { lastErr = e; }
      await sleep(150);
    }
    throw new Error(`内核控制面未就绪${lastErr ? `：${lastErr.message}` : ''}`);
  }

  /** 建立 WebSocket（/traffic、/connections、/logs） */
  connectWS(path) {
    return new Promise((resolve, reject) => {
      const ws = new WebSocket(`ws://${this.host}:${this.port}${path}`, {
        headers: { Authorization: `Bearer ${this.secret}` },
        handshakeTimeout: 8000,
      });
      ws.once('open', () => resolve(ws));
      ws.once('error', (e) => reject(new Error(`ws ${path} 连接失败: ${e.message}`)));
    });
  }
}

module.exports = { Controller, sleep };
