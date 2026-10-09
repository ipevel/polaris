'use strict';
/**
 * 内置分流规则表（离线 rule-provider）。
 *
 * 与安卓端 kernel-core/src/main/golang/native/config/routing/routing_table.go
 * 同源同表：组名、顺序、默认开关、出站语义对齐 Karing 预设（cn.json），
 * 规则数据谱系为 MetaCubeX/meta-rules-dat@meta（geosite/geoip）与
 * ACL4SSR/ACL4SSR@master（Clash 文本规则）。两边保持逐字一致，改一处要同步另一处。
 *
 * 与安卓端的差异只有一处：安卓端 provider 是 type: http（首次联网下载，assets
 * 里的同名文件只是预播种缓存）；桌面端要求"解压即用、离线可用"，所以直接用
 * type: file 读随包分发的种子文件（resources/rules/<key>.yaml → data/rules/）。
 * 种子文件名统一 .yaml：mihomo 按声明的 format 解析内容，扩展名不参与解析。
 */

const SEED_DIR = 'rules'; // 相对 mihomo 的 -d 目录（data/），同时也是 resources/ 下的子目录名

// 上游地址仅用于记录数据来源（离线分发，不联网下载）
const BASE_GEO = 'https://fastly.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@meta/geo/';
const BASE_ACL = 'https://fastly.jsdelivr.net/gh/ACL4SSR/ACL4SSR@master/Clash/';

/**
 * 全部内置规则集。
 * behavior: domain | ipcidr | classical
 * format:   yaml | text（ACL 的 .list 是纯文本规则行，mihomo 的 text 解析会跳过 # 注释）
 * noResolve: IP 类规则集附加 no-resolve，避免为了匹配 IP 规则去解析域名
 */
const PROVIDERS = {
  // --- geosite（payload 裸域名 → behavior: domain）---
  gs_category_ads_all: { behavior: 'domain', format: 'yaml', noResolve: false, url: `${BASE_GEO}geosite/category-ads-all.yaml` },
  gs_apple: { behavior: 'domain', format: 'yaml', noResolve: false, url: `${BASE_GEO}geosite/apple.yaml` },
  gs_youtube: { behavior: 'domain', format: 'yaml', noResolve: false, url: `${BASE_GEO}geosite/youtube.yaml` },
  gs_google_play: { behavior: 'domain', format: 'yaml', noResolve: false, url: `${BASE_GEO}geosite/google-play.yaml` },
  gs_google: { behavior: 'domain', format: 'yaml', noResolve: false, url: `${BASE_GEO}geosite/google.yaml` },
  gs_facebook: { behavior: 'domain', format: 'yaml', noResolve: false, url: `${BASE_GEO}geosite/facebook.yaml` },
  gs_x: { behavior: 'domain', format: 'yaml', noResolve: false, url: `${BASE_GEO}geosite/x.yaml` },
  gs_tiktok: { behavior: 'domain', format: 'yaml', noResolve: false, url: `${BASE_GEO}geosite/tiktok.yaml` },
  gs_instagram: { behavior: 'domain', format: 'yaml', noResolve: false, url: `${BASE_GEO}geosite/instagram.yaml` },
  gs_netflix: { behavior: 'domain', format: 'yaml', noResolve: false, url: `${BASE_GEO}geosite/netflix.yaml` },
  gs_whatsapp: { behavior: 'domain', format: 'yaml', noResolve: false, url: `${BASE_GEO}geosite/whatsapp.yaml` },
  gs_telegram: { behavior: 'domain', format: 'yaml', noResolve: false, url: `${BASE_GEO}geosite/telegram.yaml` },
  gs_openai: { behavior: 'domain', format: 'yaml', noResolve: false, url: `${BASE_GEO}geosite/openai.yaml` },
  gs_github: { behavior: 'domain', format: 'yaml', noResolve: false, url: `${BASE_GEO}geosite/github.yaml` },
  gs_bing: { behavior: 'domain', format: 'yaml', noResolve: false, url: `${BASE_GEO}geosite/bing.yaml` },
  gs_onedrive: { behavior: 'domain', format: 'yaml', noResolve: false, url: `${BASE_GEO}geosite/onedrive.yaml` },
  gs_microsoft: { behavior: 'domain', format: 'yaml', noResolve: false, url: `${BASE_GEO}geosite/microsoft.yaml` },
  gs_geolocation_ncn: { behavior: 'domain', format: 'yaml', noResolve: false, url: `${BASE_GEO}geosite/geolocation-%21cn.yaml` },
  // --- geoip（payload 裸 CIDR → behavior: ipcidr，全部 no-resolve）---
  gp_google: { behavior: 'ipcidr', format: 'yaml', noResolve: true, url: `${BASE_GEO}geoip/google.yaml` },
  gp_facebook: { behavior: 'ipcidr', format: 'yaml', noResolve: true, url: `${BASE_GEO}geoip/facebook.yaml` },
  gp_twitter: { behavior: 'ipcidr', format: 'yaml', noResolve: true, url: `${BASE_GEO}geoip/twitter.yaml` },
  gp_netflix: { behavior: 'ipcidr', format: 'yaml', noResolve: true, url: `${BASE_GEO}geoip/netflix.yaml` },
  gp_telegram: { behavior: 'ipcidr', format: 'yaml', noResolve: true, url: `${BASE_GEO}geoip/telegram.yaml` },
  gp_cn: { behavior: 'ipcidr', format: 'yaml', noResolve: true, url: `${BASE_GEO}geoip/cn.yaml` },
  // --- ACL4SSR（Clash 文本规则 → behavior: classical, format: text）---
  acl_banad: { behavior: 'classical', format: 'text', noResolve: false, url: `${BASE_ACL}BanAD.list` },
  acl_banprogramad: { behavior: 'classical', format: 'text', noResolve: false, url: `${BASE_ACL}BanProgramAD.list` },
  acl_gemini: { behavior: 'classical', format: 'text', noResolve: false, url: `${BASE_ACL}Ruleset/Gemini.list` },
  acl_googlefcm: { behavior: 'classical', format: 'text', noResolve: false, url: `${BASE_ACL}Ruleset/GoogleFCM.list` },
  acl_facebook: { behavior: 'classical', format: 'text', noResolve: false, url: `${BASE_ACL}Ruleset/Facebook.list` },
  acl_twitter: { behavior: 'classical', format: 'text', noResolve: false, url: `${BASE_ACL}Ruleset/Twitter.list` },
  acl_whatsapp: { behavior: 'classical', format: 'text', noResolve: false, url: `${BASE_ACL}Ruleset/Whatsapp.list` },
  acl_claude: { behavior: 'classical', format: 'text', noResolve: false, url: `${BASE_ACL}Ruleset/Claude.list` },
  acl_steam: { behavior: 'classical', format: 'text', noResolve: false, url: `${BASE_ACL}Ruleset/Steam.list` },
  acl_epic: { behavior: 'classical', format: 'text', noResolve: false, url: `${BASE_ACL}Ruleset/Epic.list` },
  acl_origin: { behavior: 'classical', format: 'text', noResolve: false, url: `${BASE_ACL}Ruleset/Origin.list` },
  acl_sony: { behavior: 'classical', format: 'text', noResolve: false, url: `${BASE_ACL}Ruleset/Sony.list` },
  acl_nintendo: { behavior: 'classical', format: 'text', noResolve: false, url: `${BASE_ACL}Ruleset/Nintendo.list` },
  acl_bilibili: { behavior: 'classical', format: 'text', noResolve: false, url: `${BASE_ACL}Ruleset/Bilibili.list` },
  acl_bilibilihmt: { behavior: 'classical', format: 'text', noResolve: false, url: `${BASE_ACL}Ruleset/BilibiliHMT.list` },
  acl_neteasemusic: { behavior: 'classical', format: 'text', noResolve: false, url: `${BASE_ACL}Ruleset/NetEaseMusic.list` },
  acl_chinadomain: { behavior: 'classical', format: 'text', noResolve: false, url: `${BASE_ACL}ChinaDomain.list` },
  acl_chinacompanyip: { behavior: 'classical', format: 'text', noResolve: true, url: `${BASE_ACL}ChinaCompanyIp.list` },
  acl_unban: { behavior: 'classical', format: 'text', noResolve: false, url: `${BASE_ACL}UnBan.list` },
  acl_steamcn: { behavior: 'classical', format: 'text', noResolve: false, url: `${BASE_ACL}Ruleset/SteamCN.list` },
  acl_download: { behavior: 'classical', format: 'text', noResolve: false, url: `${BASE_ACL}Download.list` },
  acl_chinamedia: { behavior: 'classical', format: 'text', noResolve: false, url: `${BASE_ACL}ChinaMedia.list` },
  acl_proxygfwlist: { behavior: 'classical', format: 'text', noResolve: false, url: `${BASE_ACL}ProxyGFWlist.list` },
  acl_proxymedia: { behavior: 'classical', format: 'text', noResolve: false, url: `${BASE_ACL}ProxyMedia.list` },
};

// 苹果推送内联规则：与 Karing 预设 cn.json 的「📢 苹果推送通知」逐条一致。
// {t} 在生成时替换为所属分流组名 —— 不能直接拼在行尾，IP-CIDR 的 no-resolve
// 必须留在最后，写成 IP-CIDR,<cidr>,<target>,no-resolve。
const APPLE_PUSH_RULES = [
  'DOMAIN-SUFFIX,push.apple.com,{t}',
  'DOMAIN-SUFFIX,akadns.net,{t}',
  'DOMAIN-KEYWORD,apple.com.edgekey.net,{t}',
  'IP-CIDR,17.249.0.0/16,{t},no-resolve',
  'IP-CIDR,17.252.0.0/16,{t},no-resolve',
  'IP-CIDR,17.57.144.0/22,{t},no-resolve',
  'IP-CIDR,17.188.128.0/18,{t},no-resolve',
  'IP-CIDR,17.188.20.0/23,{t},no-resolve',
  'IP-CIDR6,2620:149:a44::/48,{t},no-resolve',
  'IP-CIDR6,2403:300:a42::/48,{t},no-resolve',
  'IP-CIDR6,2403:300:a51::/48,{t},no-resolve',
  'IP-CIDR6,2a01:b740:a42::/48,{t},no-resolve',
];

// 本地网络/私有地址直连（固定 DIRECT，不占用分流组）
const LAN_DIRECT_RULES = [
  'DOMAIN-SUFFIX,local,DIRECT',
  'DOMAIN-SUFFIX,lan,DIRECT',
  'DOMAIN-SUFFIX,localhost,DIRECT',
  'IP-CIDR,127.0.0.0/8,DIRECT,no-resolve',
  'IP-CIDR,10.0.0.0/8,DIRECT,no-resolve',
  'IP-CIDR,172.16.0.0/12,DIRECT,no-resolve',
  'IP-CIDR,192.168.0.0/16,DIRECT,no-resolve',
  'IP-CIDR,100.64.0.0/10,DIRECT,no-resolve',
  'IP-CIDR,169.254.0.0/16,DIRECT,no-resolve',
  'IP-CIDR,224.0.0.0/4,DIRECT,no-resolve',
  'IP-CIDR6,::1/128,DIRECT,no-resolve',
  'IP-CIDR6,fc00::/7,DIRECT,no-resolve',
  'IP-CIDR6,fe80::/10,DIRECT,no-resolve',
  'IP-CIDR6,fd00::/8,DIRECT,no-resolve',
];

/**
 * 内置分流组，顺序/默认值对齐 Karing 预设 cn.json。
 * defaultOut: proxy | direct | block —— 决定该组默认出站与成员顺序。
 * 「恶意软件」组缺失：其引用的 geosite:malware/phishing 类别仅存在于
 * karing-ruleset 的 Iran 专用源，meta-rules-dat 无对应类别，暂不提供。
 */
const TABLE = [
  { name: '🛑 广告拦截', defaultOn: false, defaultOut: 'block', providers: ['gs_category_ads_all', 'acl_banad'] },
  { name: '🍃 应用净化', defaultOn: false, defaultOut: 'block', providers: ['acl_banprogramad'] },
  { name: '📢 苹果推送通知', defaultOn: false, defaultOut: 'proxy', providers: [], inlineRules: APPLE_PUSH_RULES },
  { name: '🍎 苹果服务', defaultOn: true, defaultOut: 'direct', providers: ['gs_apple'] },
  { name: '📹 油管视频', defaultOn: false, defaultOut: 'proxy', providers: ['gs_youtube'] },
  { name: '♊️ Google Gemini', defaultOn: false, defaultOut: 'proxy', providers: ['acl_gemini'] },
  { name: '🌏 Google Play', defaultOn: true, defaultOut: 'proxy', providers: ['gs_google_play'] },
  { name: '📢 Google FCM', defaultOn: false, defaultOut: 'direct', providers: ['acl_googlefcm'] },
  { name: '🌏 Google', defaultOn: true, defaultOut: 'proxy', providers: ['gs_google', 'gp_google'] },
  { name: '📲 Facebook', defaultOn: false, defaultOut: 'proxy', providers: ['gs_facebook', 'gp_facebook', 'acl_facebook'] },
  { name: '📲 X', defaultOn: false, defaultOut: 'proxy', providers: ['gs_x', 'gp_twitter', 'acl_twitter'] },
  { name: '🎧 TikTok', defaultOn: false, defaultOut: 'proxy', providers: ['gs_tiktok'] },
  { name: '📸 Instagram', defaultOn: false, defaultOut: 'proxy', providers: ['gs_instagram'] },
  { name: '🎥 奈飞视频', defaultOn: false, defaultOut: 'proxy', providers: ['gs_netflix', 'gp_netflix'] },
  { name: '📲 WhatsApp', defaultOn: false, defaultOut: 'proxy', providers: ['gs_whatsapp', 'acl_whatsapp'] },
  { name: '📲 电报消息', defaultOn: false, defaultOut: 'proxy', providers: ['gs_telegram', 'gp_telegram'] },
  { name: '💬 Claude', defaultOn: false, defaultOut: 'proxy', providers: ['acl_claude'] },
  { name: '💬 OpenAI', defaultOn: false, defaultOut: 'proxy', providers: ['gs_openai'] },
  { name: '🐱 GitHub', defaultOn: false, defaultOut: 'proxy', providers: ['gs_github'] },
  { name: 'Ⓜ️ 微软Bing', defaultOn: false, defaultOut: 'proxy', providers: ['gs_bing'] },
  { name: 'Ⓜ️ 微软云盘', defaultOn: false, defaultOut: 'direct', providers: ['gs_onedrive'] },
  { name: 'Ⓜ️ 微软服务', defaultOn: false, defaultOut: 'proxy', providers: ['gs_microsoft'] },
  { name: '🎮 游戏平台', defaultOn: false, defaultOut: 'proxy', providers: ['acl_steam', 'acl_epic', 'acl_origin', 'acl_sony', 'acl_nintendo'] },
  { name: '📺 哔哩哔哩', defaultOn: true, defaultOut: 'direct', providers: ['acl_bilibilihmt', 'acl_bilibili'] },
  { name: '🎶 网易音乐', defaultOn: false, defaultOut: 'direct', providers: ['acl_neteasemusic'] },
  {
    name: '🎯 国内直连',
    defaultOn: true,
    defaultOut: 'direct',
    providers: ['gp_cn', 'acl_chinadomain', 'acl_chinacompanyip', 'acl_unban', 'acl_steamcn', 'acl_download', 'acl_chinamedia'],
  },
  {
    name: '🌏 国外穿墙',
    defaultOn: true,
    defaultOut: 'proxy',
    providers: ['gs_geolocation_ncn', 'acl_proxygfwlist', 'acl_proxymedia'],
  },
];

const BY_NAME = new Map(TABLE.map((g) => [g.name, g]));

/** 默认开启的分流组名（用户从未改过设置时用它） */
function defaultEnabled() {
  return TABLE.filter((g) => g.defaultOn).map((g) => g.name);
}

/** 规范化用户设置：未知组名丢弃、去重，保持表内顺序 */
function normalizeEnabled(list) {
  if (!Array.isArray(list)) return defaultEnabled();
  const want = new Set(list.filter((n) => typeof n === 'string'));
  return TABLE.filter((g) => want.has(g.name)).map((g) => g.name);
}

/**
 * 按用户自定义顺序排列内置分流表。
 * order 中出现的组名按给定次序排在前，未出现的按 TABLE 默认顺序追加在后；
 * 未知、重复、空名一律忽略，绝不丢组。空 order 直接返回 TABLE。
 * 顺序 = 规则匹配优先级。
 */
function orderedTable(order) {
  if (!Array.isArray(order) || order.length === 0) return TABLE;
  const picked = new Set();
  const out = [];
  for (const name of order) {
    const g = BY_NAME.get(name);
    if (!g || picked.has(name)) continue;
    picked.add(name);
    out.push(g);
  }
  for (const g of TABLE) {
    if (picked.has(g.name)) continue;
    out.push(g);
  }
  return out;
}

/** 某个分流组用到的规则集键（去重，保持声明顺序） */
function providerKeys(group) {
  const seen = new Set();
  const out = [];
  for (const key of group.providers || []) {
    if (PROVIDERS[key] && !seen.has(key)) { seen.add(key); out.push(key); }
  }
  return out;
}

/** 表里引用到的全部规则集键 */
function allProviderKeys() {
  const seen = new Set();
  for (const g of TABLE) for (const k of providerKeys(g)) seen.add(k);
  return [...seen];
}

/** 种子文件名（与安卓端 FileExt 一致：统一 .yaml） */
function seedFile(key) { return `${key}.yaml`; }

/** rule-provider 的 path 字段：相对 mihomo 的 -d 目录，自动落在安全路径内 */
function seedPath(key) { return `${SEED_DIR}/${seedFile(key)}`; }

/** 规则集 -> mihomo rule-provider 配置（type: file，离线读取随包种子） */
function providerConfig(key) {
  const p = PROVIDERS[key];
  if (!p) return null;
  return {
    type: 'file',
    behavior: p.behavior,
    format: p.format,
    path: seedPath(key),
  };
}

module.exports = {
  SEED_DIR,
  PROVIDERS,
  TABLE,
  APPLE_PUSH_RULES,
  LAN_DIRECT_RULES,
  defaultEnabled,
  normalizeEnabled,
  orderedTable,
  providerKeys,
  allProviderKeys,
  seedFile,
  seedPath,
  providerConfig,
};
