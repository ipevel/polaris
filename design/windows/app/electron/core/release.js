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

/** 取一个 URL 的文本内容；`label` 只影响报错话术（摘要文件与发布接口各自成句）。 */
function getRaw(url, label, timeoutMs = 10000, redirects = 0) {
  return new Promise((resolve, reject) => {
    let u;
    try { u = new URL(url); } catch (_) { reject(new Error(label + '地址不合法')); return; }
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
        getRaw(new URL(res.headers.location, u).toString(), label, timeoutMs, redirects + 1).then(resolve, reject);
        return;
      }
      if (code !== 200) { res.resume(); reject(new Error(`${label} HTTP ${code}`)); return; }
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    });
    req.on('timeout', () => req.destroy(new Error(label + '超时')));
    req.on('error', (e) => reject(new Error(label + '连不上：' + (e && e.message))));
  });
}

function getJson(url, timeoutMs = 10000, redirects = 0) {
  return getRaw(url, '发布接口', timeoutMs, redirects).then((text) => {
    try { return JSON.parse(text); } catch (e) { throw new Error('发布信息不是合法 JSON'); }
  });
}

/** SHA256SUMS.txt 与 zip 同目录：发版 workflow 的 release job 已经把它写在那儿了。 */
function getText(url, timeoutMs = 10000, redirects = 0) {
  return getRaw(url, '摘要文件', timeoutMs, redirects);
}

/**
 * 解析 SHA256SUMS.txt：`<64位小写hex>␣␣<名字>`，允许 `*` 二进制标记与 CRLF。
 * 认不出的行直接跳过 —— 同一个文件里还有安卓的 APK 行。
 */
function parseSums(text) {
  const map = new Map();
  for (const line of String(text || '').split(/\r?\n/)) {
    const m = /^([0-9a-f]{64})\s+\*?(.+?)\s*$/i.exec(line.trim());
    if (m) map.set(m[2].replace(/\\/g, '/'), m[1].toLowerCase());
  }
  return map;
}

/** 与某个资产同目录的 SHA256SUMS.txt 地址；地址不合法返回空串（调用方按「没有摘要」处理）。 */
function sumsUrlFor(assetUrl) {
  try {
    const u = new URL(String(assetUrl));
    u.pathname = u.pathname.replace(/[^/]*$/, 'SHA256SUMS.txt');
    return u.toString();
  } catch (_) { return ''; }
}

/**
 * 查最新发布。返回的形状与 remote.updateInfo() 保持一致，便于 ipc 层择优：
 *   { has_update, version, url, size, notes, title }
 * `url` 取 `Polaris-portable-*.zip` 资产（没有就退回任意 .zip，再退回发布页）。
 */
/**
 * 摘要状态（纯函数，可离线单测）：
 *   'ok'      = 拿到了可比对的 sha256
 *   'missing' = **本次没做包校验**（取不到 / 摘要里没有该条 / 发布里没有 zip 资产）
 * 抽出来是为了两件事：UI 能拿到一句人话的原因；单测不依赖网络。
 */
function shaStateFor(zipName, sums, err) {
  if (err) return { state: 'missing', reason: '取发布侧摘要失败（' + ((err && err.message) || err) + '）', sha256: '' };
  if (!zipName) return { state: 'missing', reason: '发布里没有 zip 资产', sha256: '' };
  const h = sums && typeof sums.get === 'function' ? sums.get(zipName) : '';
  if (!h) return { state: 'missing', reason: '发布侧摘要里没有 ' + zipName, sha256: '' };
  return { state: 'ok', reason: '', sha256: String(h) };
}

async function checkLatest(currentVersion) {
  const url = `${apiBase()}/repos/${REPO}/releases/latest`;
  const json = await getJson(url);
  const latest = String(json.tag_name || '').replace(/^v/i, '').trim();
  if (!latest) throw new Error('发布信息里没有 tag_name');

  const assets = Array.isArray(json.assets) ? json.assets : [];
  const pick = (re) => assets.find((a) => re.test(String((a && a.name) || '')));
  const zip = pick(/^Polaris-portable-.*\.zip$/i) || pick(/\.zip$/i);

  // 发布侧摘要在同一个 Release 里（release job 写的 SHA256SUMS.txt，与 zip 同目录）。
  // 取不到**不算失败**（镜像 / 自建 CDN 没有这份文件），但**必须让用户看见**：
  // 决策主管硬条件——摘要取不到时更新页要显示「本次未做包校验」，不许静默 warn。
  let sums = null; let sumsErr = null;
  if (zip && zip.browser_download_url) {
    const su = sumsUrlFor(zip.browser_download_url);
    if (su) {
      try { sums = parseSums(await getText(su)); } catch (e) { sumsErr = e; }
    }
  }
  const sha = shaStateFor(zip ? String(zip.name || '') : '', sums, sumsErr);
  const sha256 = sha.sha256 || '';
  if (sha.state === 'ok') log.info(`release sums: ${zip.name} sha256=${sha256.slice(0, 12)}…`);
  else log.warn('本次未做包校验：' + sha.reason);

  const info = {
    has_update: fmt.cmpVersion(latest, currentVersion) > 0,
    version: latest,
    size: zip ? String(zip.size || '') : '',
    url: zip ? String(zip.browser_download_url || '') : String(json.html_url || ''),
    notes: String(json.body || '').trim(),
    title: String(json.name || '').trim(),
    // 下载后用它逐字节核对 zip（空串 = 没有可核对的摘要，客户端只 warn 不拦）
    sha256: sha256,
    // 硬条件：'missing' = 本次没做包校验，UI 必须显示出来（不许静默）
    sha_state: sha.state,
    sha_reason: sha.reason,
  };
  log.info(`release check: latest=${latest} current=${currentVersion} has_update=${info.has_update}` +
    (zip ? ` asset=${zip.name} sha256=${sha256 ? sha256.slice(0, 12) + '…' : '(无)'}` : ' (没有 zip 资产)'));
  return info;
}

module.exports = { checkLatest, apiBase, REPO, DEFAULT_API, parseSums, sumsUrlFor, getText, shaStateFor };
