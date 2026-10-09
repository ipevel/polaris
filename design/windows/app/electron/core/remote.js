'use strict';
/**
 * 远程配置：多源并发拉取、按 config_version 择优、单源失效不影响。
 * 字段与 Android 端 data/remote/config/RemoteConfig.kt + CONFIG.md 一致。
 */

const https = require('https');
const http = require('http');
const { URL } = require('url');
const log = require('../logger');
const store = require('../store');

let cache = null;
let lastFetch = 0;
let inflight = null;

const TTL_MS = 30 * 60 * 1000;

function getText(url, timeoutMs = 8000) {
  return new Promise((resolve, reject) => {
    let u;
    try { u = new URL(url); } catch (e) { reject(new Error('非法 URL')); return; }
    if (u.protocol !== 'https:' && u.protocol !== 'http:') { reject(new Error('仅支持 http/https')); return; }
    const mod = u.protocol === 'https:' ? https : http;
    const req = mod.get(url, {
      timeout: timeoutMs,
      headers: { 'User-Agent': 'Polaris-Windows/1.0', Accept: 'application/json' },
      rejectUnauthorized: true,
    }, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        res.resume();
        getText(new URL(res.headers.location, url).toString(), timeoutMs).then(resolve, reject);
        return;
      }
      if (res.statusCode !== 200) { res.resume(); reject(new Error(`HTTP ${res.statusCode}`)); return; }
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => {
        const raw = Buffer.concat(chunks).toString('utf8');
        // 托管平台常把 https 地址做 base64 混用
        try {
          if (/^[A-Za-z0-9+/=\s]{16,}$/.test(raw) && !raw.trim().startsWith('{')) {
            const decoded = Buffer.from(raw.replace(/\s/g, ''), 'base64').toString('utf8');
            if (/^https?:\/\//.test(decoded)) return resolve(decoded);
          }
        } catch (_) {}
        resolve(raw);
      });
    });
    req.on('timeout', () => req.destroy(new Error('超时')));
    req.on('error', reject);
  });
}

function versionOf(cfg) {
  const n = parseInt(cfg && cfg.config_version, 10);
  return Number.isFinite(n) ? n : 0;
}

async function load(force = false) {
  if (cache && !force && Date.now() - lastFetch < TTL_MS) return cache;
  if (inflight) return inflight;

  const urls = String(store.get('remote_config_urls') || '')
    .split(',').map((s) => s.trim()).filter(Boolean);

  inflight = (async () => {
    if (urls.length === 0) { cache = cache || {}; lastFetch = Date.now(); inflight = null; return cache; }
    const results = await Promise.all(urls.map(async (u) => {
      try { return JSON.parse(await getText(u)); } catch (e) { log.warn('remote config source failed:', u, e.message); return null; }
    }));
    const ok = results.filter(Boolean);
    if (ok.length === 0) { inflight = null; return cache || {}; }
    ok.sort((a, b) => versionOf(b) - versionOf(a));
    cache = ok[0];
    lastFetch = Date.now();
    log.info(`remote config v${versionOf(cache)} from ${ok.length}/${urls.length} source(s)`);
    inflight = null;
    return cache;
  })();
  return inflight;
}

/** 直连域名：远程配置 + 面板域名 + 见过的订阅域名（三者都必须直连，否则开代理后自己就联系不上了） */
function directDomains() {
  const list = [];
  const cfg = cache || {};
  const raw = cfg.direct_domains == null ? [] : cfg.direct_domains;
  for (const d of (Array.isArray(raw) ? raw : String(raw).split(','))) {
    const v = String(d || '').trim();
    if (v) list.push(v);
  }
  const panel = store.get('panel_url');
  if (panel) {
    try { list.push(new URL(panel).hostname); } catch (_) {}
  }
  const subs = store.get('subscribe_hosts');
  if (Array.isArray(subs)) for (const s of subs) if (s) list.push(String(s).trim());
  return Array.from(new Set(list.filter(Boolean)));
}

/**
 * 记住一个必须直连的域名（订阅地址的 host）。订阅链接经常和面板不同域，
 * 且常带 CDN —— 不直连的话，开了代理后刷新订阅会打到自己身上绕圈。
 * 持久化到 settings.json，最多留 20 条。
 */
function rememberDirectHost(host) {
  const v = String(host || '').trim().toLowerCase();
  if (!v) return false;
  const list = Array.isArray(store.get('subscribe_hosts')) ? store.get('subscribe_hosts').slice() : [];
  if (list.includes(v)) return false;
  list.push(v);
  store.set('subscribe_hosts', list.slice(-20));
  log.info(`direct host remembered: ${v}`);
  return true;
}

function apiBaseUrls() {
  const cfg = cache || {};
  const raw = cfg.api_base_urls != null ? cfg.api_base_urls : cfg.api;
  const arr = Array.isArray(raw) ? raw : (raw ? String(raw).split(',') : []);
  return arr
    .map((s) => {
      const v = String(s || '').trim();
      if (!v) return null;
      if (/^https?:\/\//.test(v)) return v;
      try {
        const d = Buffer.from(v, 'base64').toString('utf8');
        return /^https?:\/\//.test(d) ? d : null;
      } catch (_) { return null; }
    })
    .filter(Boolean);
}

function updateInfo() {
  const cfg = cache || {};
  const version = String(cfg.update_version || '').trim();
  // Windows 端**不能**用 update_url / update_apk_url —— 那两个字段在面板配置里
  // 指的是安卓 APK，Windows 用户点了会下到一个装不上的安装包。
  // 优先 Windows 专用字段，再退到本机设置里的 download_url，最后才用通用字段。
  const winUrl = String(
    cfg.update_windows_url || cfg.update_win_url || cfg.update_exe_url || cfg.update_pc_url || '',
  ).trim();
  const fallback = String(store.get('download_url') || '').trim();
  return {
    has_update: !!version && version !== store.get('version'),
    version: version || store.get('version'),
    size: String(cfg.update_windows_size || cfg.update_size || ''),
    url: winUrl || fallback,
    android_url: String(cfg.update_url || cfg.update_apk_url || ''),
    notes: String(cfg.update_windows_changelog || cfg.update_changelog || ''),
    title: String(cfg.update_changelog_title || ''),
    force: !!cfg.update_force,
  };
}

module.exports = { load, directDomains, rememberDirectHost, apiBaseUrls, updateInfo, current: () => cache };
