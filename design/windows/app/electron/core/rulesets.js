'use strict';
/**
 * 内置分流规则表（本地分流方案的规则数据源）。
 *
 * 与安卓端 kernel-core/src/main/golang/native/config/routing/routing_table.go
 * 同源同表：组名、顺序、默认开关、出站语义对齐 Karing 预设（cn.json），
 * 规则数据谱系为 MetaCubeX/meta-rules-dat@meta（geosite/geoip）与
 * ACL4SSR/ACL4SSR@master（Clash 文本规则）。两边保持逐字一致，改一处要同步另一处。
 *
 * rule-provider 与安卓端一样是 type: http：随包分发的种子文件是**预播种缓存**
 * （补到 mihomo 的缓存路径上，只补缺失、不覆盖内核已下载的更新版），联网后由
 * 内核按 interval（24h，与 karing-ruleset 每日构建节奏一致）自己刷新；断网冷启动
 * 直接用种子文件，规则立刻生效（provider 下载失败只记日志、不阻断连接）。
 * 文件统一 .yaml：mihomo 按声明的 format 解析内容，扩展名不参与解析。
 */

// mihomo 的 rule-provider 缓存子目录：path 相对 -d 目录（data/），
// 与安卓端 providerSubPath（routing_table.go:19）同名同语义
const CACHE_DIR = 'polaris-rules';
// 规则数据刷新间隔：与安卓端 providerInterval 一致（86400s = 24h）
const PROVIDER_INTERVAL = 86400;

// 结构组：本地分流方案自己生成的四个组（顺序即契约，见 builder.js）。
// 安卓端 routing_table.go:23-26 同名同义。
const GROUP_SELECTOR = '🚀 节点选择';   // 主选择组：节点页顶部那张卡，默认选中「自动选择」
const GROUP_AUTO = '自动选择';          // url-test 测速组
const GROUP_FALLBACK = '故障转移';      // fallback 组
const GROUP_FINAL = '🐟 漏网之鱼';      // MATCH 兜底组（刻意不 include-all）
// 测速 URL 必须与 mihomo 的 constant.DefaultTestURL 逐字一致（安卓端坑 10）
const TEST_URL = 'https://www.gstatic.com/generate_204';

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

/* ------------------------------------------------------------------ */
/* 用户自定义分流组                                                     */
/* ------------------------------------------------------------------ */

/**
 * mihomo 支持的规则类型白名单（MATCH 除外 —— 它会吃掉后面所有规则）。
 *
 * 为什么要白名单：一条拼错的规则会让 mihomo **拒绝整个配置**，内核直接起不来，
 * 用户表现是"点了连接没反应"。所以在保存时就拦住，而不是等内核报错。
 */
const RULE_TYPES = new Set([
  'DOMAIN', 'DOMAIN-SUFFIX', 'DOMAIN-KEYWORD', 'DOMAIN-REGEX', 'GEOSITE',
  'IP-CIDR', 'IP-CIDR6', 'IP-SUFFIX', 'IP-ASN', 'GEOIP',
  'SRC-IP-CIDR', 'SRC-PORT', 'DST-PORT', 'PROCESS-NAME', 'PROCESS-PATH', 'RULE-SET',
]);

// IP 类规则默认补 no-resolve：不补的话 mihomo 会为了匹配 IP 规则去解析每个域名
const NEED_RESOLVE_TYPES = new Set(['IP-CIDR', 'IP-CIDR6', 'IP-SUFFIX', 'GEOIP']);

const OUTS = new Set(['proxy', 'direct', 'block']);
const CUSTOM_MAX = 20;         // 自定义分流组数量上限
const CUSTOM_RULES_MAX = 200;  // 每组规则条数上限
const CUSTOM_NAME_MAX = 24;    // 组名长度上限

/** 解析一行用户写的规则 → {type, value, noResolve}；空行/注释返回 null；不合法抛错 */
function parseRuleLine(line) {
  const raw = String(line == null ? '' : line).trim();
  if (!raw || raw.startsWith('#')) return null;
  const parts = raw.split(',').map((s) => s.trim());
  const shown = parts[0] || '(空)';
  const type = shown.toUpperCase();
  if (type === 'MATCH') throw new Error('不能用 MATCH —— 它会吃掉后面所有规则');
  if (!RULE_TYPES.has(type)) throw new Error(`不支持的规则类型「${shown}」`);
  const value = parts[1] || '';
  if (!value) throw new Error(`${type} 缺少内容`);
  if (parts.length > 3) throw new Error(`${type} 参数太多（最多 3 段）`);
  if (/\s/.test(value)) throw new Error(`${type} 的内容里不能有空格`);
  let noResolve = false;
  if (parts.length === 3) {
    if (parts[2].toLowerCase() !== 'no-resolve') throw new Error('第三段只能是 no-resolve');
    noResolve = true;
  }
  if (!noResolve && NEED_RESOLVE_TYPES.has(type)) noResolve = true; // 自动补，省得用户忘
  return { type, value, noResolve };
}

/** 解析后的规则 → 用户看到/存储的写法（不带目标组） */
function ruleText(rule) {
  return `${rule.type},${rule.value}${rule.noResolve ? ',no-resolve' : ''}`;
}

/** 解析后的规则 → 配置里的规则行（目标组插在 no-resolve 前面） */
function ruleLine(rule, target) {
  return `${rule.type},${rule.value},${target}${rule.noResolve ? ',no-resolve' : ''}`;
}

/**
 * 规范化存储里的自定义分流组（**永不抛错**）。
 * 设置文件可能被手改坏，或者旧版本写过不认识的东西 —— 配置组装绝不能因此崩，
 * 所以这里只做"能用的留下、不能用的丢掉"；严格校验在 validateCustom（保存时）。
 */
function normalizeCustom(list) {
  if (!Array.isArray(list)) return [];
  const out = [];
  const seen = new Set();
  for (const raw of list.slice(0, CUSTOM_MAX)) {
    if (!raw || typeof raw !== 'object') continue;
    const name = String(raw.name == null ? '' : raw.name).trim().slice(0, CUSTOM_NAME_MAX);
    // 与保留名冲突的一律丢弃：内置组/结构组的语义是产品定义的，不能被顶掉
    if (!name || seen.has(name) || isReservedName(name)) continue;
    const rules = [];
    const lines = Array.isArray(raw.rules) ? raw.rules : String(raw.rules || '').split(/\r?\n/);
    for (const line of lines.slice(0, CUSTOM_RULES_MAX)) {
      let r = null;
      try {
        // 两种形状都要吃：存储里是文本行（settings.json），内存里可能是已解析的规则对象
        // （validateCustom 的返回值）。只认文本的话，saveCustomRuleset 存回去时会静默丢光。
        r = line && typeof line === 'object' && typeof line.type === 'string'
          ? parseRuleLine(ruleText(line))
          : parseRuleLine(line);
      } catch (_) { r = null; }
      if (r) rules.push(r);
    }
    if (!rules.length) continue;
    seen.add(name);
    out.push({ name, out: OUTS.has(raw.out) ? raw.out : 'proxy', enabled: raw.enabled !== false, rules });
  }
  return out;
}

/** 规范化结果 → 存储形状（规则回写成文本） */
function serializeCustom(list) {
  return normalizeCustom(list).map((g) => ({
    name: g.name, out: g.out, enabled: g.enabled, rules: g.rules.map(ruleText),
  }));
}

/**
 * 保存前的严格校验（**会抛错**，错误信息带行号，直接给用户看）。
 * @param {{name?:string,out?:string,rules?:string[]|string,enabled?:boolean}} input
 */
function validateCustom(input) {
  const src = input || {};
  const name = String(src.name == null ? '' : src.name).trim();
  if (!name) throw new Error('分流组名字不能为空');
  if (name.length > CUSTOM_NAME_MAX) throw new Error(`名字太长（最多 ${CUSTOM_NAME_MAX} 个字）`);
  if (/[,:{}[\]"'\\#\r\n\t]/.test(name)) throw new Error('名字里不能有 , : { } [ ] " \' \\ # 这些字符');
  if (isReservedName(name)) throw new Error(`「${name}」是保留名（内置分流组/结构组），换个名字`);
  const out = OUTS.has(src.out) ? src.out : 'proxy';
  const lines = Array.isArray(src.rules) ? src.rules : String(src.rules || '').split(/\r?\n/);
  if (lines.length > CUSTOM_RULES_MAX * 2) throw new Error('规则行太多了');
  const rules = [];
  lines.forEach((line, i) => {
    let r = null;
    try { r = parseRuleLine(line); } catch (e) { throw new Error(`第 ${i + 1} 行：${e.message}`); }
    if (r) rules.push(r);
  });
  if (!rules.length) throw new Error('至少要写一条规则');
  if (rules.length > CUSTOM_RULES_MAX) throw new Error(`规则太多（最多 ${CUSTOM_RULES_MAX} 条）`);
  return { name, out, enabled: src.enabled !== false, rules };
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

/**
 * rule-provider 的 path 字段：相对 mihomo 的 -d 目录，自动落在安全路径内
 * （mihomo 侧 C.Path.Resolve + IsSafePath，出界会被拒）。
 * type: http 时它同时是**缓存文件路径**——种子预播种的位置就是这里。
 */
function seedPath(key) { return `${CACHE_DIR}/${seedFile(key)}`; }

/**
 * 规则集 -> mihomo rule-provider 配置（与安卓端 ProviderRawMap 逐字段一致）。
 * url/behavior/format/interval 均在表里声明；path 指向随包种子播种的位置，
 * 内核起不来网时直接读它，联网后按 interval 自己刷新。
 */
function providerConfig(key) {
  const p = PROVIDERS[key];
  if (!p) return null;
  return {
    type: 'http',
    behavior: p.behavior,
    format: p.format,
    url: p.url,
    path: seedPath(key),
    interval: PROVIDER_INTERVAL,
  };
}

/* ------------------------------------------------------------------ */
/* 保留名（本地分流生成的组名会与面板节点共处一个 mihomo 命名空间）      */
/* ------------------------------------------------------------------ */

/**
 * mihomo 预注册的出站 + provider 保留名（安卓端 reserved.go:18-24）。
 * 面板（或恶意订阅）下发一个叫「自动选择」的节点就能让 mihomo 报重名硬失败，
 * 整个配置加载不了 —— 所以生成前先扫一遍，命中就整体降级回面板配置。
 */
const MIHOMO_RESERVED = [
  'DIRECT', 'REJECT', 'REJECT-DROP', 'COMPATIBLE', 'PASS', 'PASS-RULE', 'GLOBAL', 'default',
];

/** 本地分流占用的全部名称（结构组 + 内置分流组 + mihomo 预注册出站） */
function reservedNames() {
  return [GROUP_SELECTOR, GROUP_AUTO, GROUP_FALLBACK, GROUP_FINAL, ...TABLE.map((g) => g.name), ...MIHOMO_RESERVED];
}

function isReservedName(name) {
  return reservedNames().includes(name);
}

module.exports = {
  CACHE_DIR,
  PROVIDER_INTERVAL,
  GROUP_SELECTOR,
  GROUP_AUTO,
  GROUP_FALLBACK,
  GROUP_FINAL,
  TEST_URL,
  PROVIDERS,
  TABLE,
  reservedNames,
  isReservedName,
  APPLE_PUSH_RULES,
  LAN_DIRECT_RULES,
  RULE_TYPES,
  CUSTOM_MAX,
  CUSTOM_RULES_MAX,
  CUSTOM_NAME_MAX,
  defaultEnabled,
  normalizeEnabled,
  orderedTable,
  providerKeys,
  allProviderKeys,
  seedFile,
  seedPath,
  providerConfig,
  parseRuleLine,
  ruleText,
  ruleLine,
  normalizeCustom,
  serializeCustom,
  validateCustom,
};
