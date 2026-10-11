'use strict';
/**
 * 应用内更新的版本来源：GitHub Releases（与安卓端 AppUpdateChecker 同一套口径）。
 *
 * 安卓端直接查 `repos/ipevel/polaris/releases/latest`，取第一个 `.apk` 资产，不看远程配置。
 * Windows 端原先只认远程配置里的 `update_version`，而那个字段一直是空的 —— 结果就是
 * "红点永远不会亮"（2026-10-11 第 13 轮发现）。这里补上同一条链路：发完版两端都能自己
 * 发现新版本，不需要每次发版再手动去改 `config/remote.json`。
 *
 * 远程配置里的 `update_windows_url` 仍然优先（可以指向镜像 / 自建 CDN）—— 见 core/remote.js
 * 的 updateInfo()，ipc.js 的 check_update 负责择优。
 */

const https = require('https');
const http = require('http');
const { URL } = require('url');
const log = require('../logger');
const store = require('../store');
const fmt = require('../util/format');

const DEFAULT_API = 'https://api.github.com';
const REPO = 'ipevel/polaris';

/** 允许改基址：公司镜像 / 内网代理 / 自检里指向本地假 GitHub。空串 = 官方 api.github.com */
function apiBase() {
  return String(store.get('release_api_base') || DEFAULT_API).replace(/\/+$/, '');
}

function getJson(url, timeoutMs = 10000, redirects = 0) {
  return new Promise((resolve, reject) => {
    let u;
    try { u = new URL(url); } catch (_) { reject(new Error('发布接口地址不合法')); return; }
    const mod = u.protocol === 'https:' ? https : http;
    const req = mod.get(u, {
      timeout: timeoutMs,
      headers: {
        // GitHub API 必须带 UA，否则 403
        'User-Agent': 'Polaris-Windows',
        Accept: 'application/vnd.github+json',
      },
    }, (res) => {
      const code = res.statusCode || 0;
      if (code >= 300 && code < 400 && res.headers.location) {
        res.resume();
        if (redirects >= 5) { reject(new Error('重定向次数过多')); return; }
        getJson(new URL(res.headers.location, u).toString(), timeoutMs, redirects + 1).then(resolve, reject);
        return;
      }
      if (code !== 200) { res.resume(); reject(new Error(`发布接口 HTTP ${code}`)); return; }
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => {
        try { resolve(JSON.parse(Buffer.concat(chunks).toString('utf8'))); } catch (e) { reject(new Error('发布信息不是合法 JSON')); }
      });
    });
    req.on('timeout', () => req.destroy(new Error('发布接口超时')));
    req.on('error', (e) => reject(new Error('发布接口连不上：' + (e && e.message))));
  });
}

/**
 * 查最新发布。返回的形状与 remote.updateInfo() 保持一致，便于 ipc 层择优：
 *   { has_update, version, url, size, notes, title }
 * `url` 取 `Polaris-portable-*.zip` 资产（没有就退回任意 .zip，再退回发布页）。
 */
async function checkLatest(currentVersion) {
  const url = `${apiBase()}/repos/${REPO}/releases/latest`;
  const json = await getJson(url);
  const latest = String(json.tag_name || '').replace(/^v/i, '').trim();
  if (!latest) throw new Error('发布信息里没有 tag_name');

  const assets = Array.isArray(json.assets) ? json.assets : [];
  const pick = (re) => assets.find((a) => re.test(String((a && a.name) || '')));
  const zip = pick(/^Polaris-portable-.*\.zip$/i) || pick(/\.zip$/i);

  const info = {
    has_update: fmt.cmpVersion(latest, currentVersion) > 0,
    version: latest,
    size: zip ? String(zip.size || '') : '',
    url: zip ? String(zip.browser_download_url || '') : String(json.html_url || ''),
    notes: String(json.body || '').trim(),
    title: String(json.name || '').trim(),
  };
  log.info(`release check: latest=${latest} current=${currentVersion} has_update=${info.has_update}` +
    (zip ? ` asset=${zip.name}` : ' (没有 zip 资产)'));
  return info;
}

module.exports = { checkLatest, apiBase, REPO, DEFAULT_API };
