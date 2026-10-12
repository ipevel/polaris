// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

/**
 * 规则库（geodata）刷新。
 *
 * 背景（用户 2026-10-12 要求）：分流规则必须内置本地、不依赖远程规则源，
 * 内核只读本地 geosite.dat / geoip.metadb，所以启动与连接都不需要网络；
 * 但本地这份要能跟上上游，于是「每次打开 App 检查一次，超过 24 小时才拉」。
 *
 * 与安卓端 `app/src/main/java/com/slte/app/data/geo/GeoResources.kt` 的
 * `GeoResources.sync()` 是同一套策略与同一份清单，两端不会漂移。
 *
 * 三个硬约束：
 * 1. **不碰内核正在读的文件**：刷新只写 `resources/geo/`（源），
 *    `manager.ensureGeodata()` 在下次起内核前把它拷进 `data/`。
 *    Windows 上替换内核已打开的文件会 EBUSY，硬写会让更新失败。
 * 2. **校验不过绝不覆盖**：上游每个资产都发 `<name>.sha256sum`，比对不上就丢弃。
 * 3. **失败不抛**：离线、上游抖动、限流都只记一行日志，继续用内置那份。
 */

const crypto = require('crypto');
const fs = require('fs');
const http = require('http');
const https = require('https');
const path = require('path');

/** 上游原始规则仓库（面板「资源」页那一套）。测试用 POLARIS_GEO_BASE 指到本地。 */
const BASE = process.env.POLARIS_GEO_BASE || 'https://fastly.jsdelivr.net/gh/appshubcc/bett-rules@release';

/** 本地文件名 → 远端地址。与安卓 GeoResources.ALL 一一对应。 */
const FILES = {
  'geosite.dat': `${BASE}/geosite.dat`,
  'geoip.metadb': `${BASE}/geoip.metadb`,
  'ASN.mmdb': `${BASE}/GeoLite2-ASN.mmdb`,
};

const INTERVAL_MS = 24 * 60 * 60 * 1000;
const MARKER = 'geo_last_sync';

/** 下载上限保护：单个文件超过 64 MB 视为异常响应，直接放弃。 */
const MAX_BYTES = 64 * 1024 * 1024;

function get(url, redirects = 0) {
  return new Promise((resolve, reject) => {
    if (redirects > 5) {
      reject(new Error('重定向次数过多'));
      return;
    }
    const mod = url.startsWith('http://') ? http : https;
    const req = mod.get(url, { timeout: 120000 }, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        res.resume();
        resolve(get(new URL(res.headers.location, url).toString(), redirects + 1));
        return;
      }
      if (res.statusCode !== 200) {
        res.resume();
        reject(new Error(`HTTP ${res.statusCode}`));
        return;
      }
      const chunks = [];
      let size = 0;
      res.on('data', (c) => {
        size += c.length;
        if (size > MAX_BYTES) {
          req.destroy(new Error('响应过大'));
          return;
        }
        chunks.push(c);
      });
      res.on('end', () => resolve(Buffer.concat(chunks)));
      res.on('error', reject);
    });
    req.on('timeout', () => req.destroy(new Error('请求超时')));
    req.on('error', reject);
  });
}

/** 三份文件齐全且标记未过期 → 一个请求都不发。 */
function isFresh(dir) {
  for (const name of Object.keys(FILES)) {
    try {
      if (fs.statSync(path.join(dir, name)).size <= 0) return false;
    } catch (_) {
      return false;
    }
  }
  try {
    return Date.now() - fs.statSync(path.join(dir, MARKER)).mtimeMs < INTERVAL_MS;
  } catch (_) {
    return false;
  }
}

/**
 * 按 24 小时周期刷新 `geoDir`（打包后是 `resources/geo/`）。
 *
 * @param {string} geoDir 目标目录
 * @param {{info:Function,warn:Function}} log 日志器
 * @returns {Promise<string[]>} 本次真正更新的文件名
 */
async function refresh(geoDir, log) {
  const say = log || { info() {}, warn() {} };
  if (isFresh(geoDir)) return [];
  try {
    fs.mkdirSync(geoDir, { recursive: true });
  } catch (_) {
    return [];
  }

  const updated = [];
  for (const [name, url] of Object.entries(FILES)) {
    const tmp = path.join(geoDir, `${name}.part`);
    try {
      const body = await get(url);
      if (!body || body.length === 0) throw new Error('下载到 0 字节');
      fs.writeFileSync(tmp, body);

      let expected = null;
      try {
        const sum = await get(`${url}.sha256sum`);
        expected = sum.toString('utf8').trim().split(/\s+/)[0];
      } catch (_) {
        expected = null;
      }
      if (expected) {
        const actual = crypto.createHash('sha256').update(body).digest('hex');
        if (actual.toLowerCase() !== expected.toLowerCase()) {
          throw new Error(`sha256 不匹配：期望 ${expected}，实际 ${actual}`);
        }
      }

      fs.renameSync(tmp, path.join(geoDir, name));
      updated.push(name);
      say.info(`geo sync: updated ${name} (${body.length} bytes)`);
    } catch (e) {
      say.warn(`geo sync failed: ${name}: ${(e && e.message) || e}`);
      try {
        fs.unlinkSync(tmp);
      } catch (_) {}
    }
  }

  // 至少一份成功才推进时间戳；全部失败下次启动再试。
  if (updated.length) {
    try {
      fs.writeFileSync(path.join(geoDir, MARKER), String(Date.now()));
    } catch (_) {}
  }
  return updated;
}

module.exports = { refresh, isFresh, FILES, INTERVAL_MS, MARKER };
