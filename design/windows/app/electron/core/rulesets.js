'use strict';
/**
 * 内置分流规则表（本地分流方案的规则数据源）。
 *
 * 与安卓端 kernel-core/src/main/golang/native/config/routing/routing_table.go
 * 同源同表：组名、顺序、默认开关、出站语义对齐 Karing 预设（cn.json），
 * 规则数据谱系为 MetaCubeX/meta-rules-dat@meta（geosite/geoip）与
 * ACL4SSR/ACL4SSR@master（Clash 文本规则）。两边保持逐字一致，改一处要同步另一处。
 *
 * rule-provider 与安卓端一样是 type: file：内置规则全是**本地文件 provider**，
 * 只读随包种子、启动与运行期都不联网。要联网刷新的是 geosite.dat / geoip.metadb
 * （资源文件，不是 rule-provider），由 electron/core/geosync.js 每 24 小时拉一次，
 * 校验 sha256 后再替换 —— 规则数据源从根上不再依赖任何远程 URL。
 * 文件统一 .yaml：mihomo 按声明的 format 解析内容，扩展名只用于对齐种子名。
 */

// mihomo 的 rule-provider 缓存子目录：path 相对 -d 目录（data/），
// 与安卓端 providerSubPath（routing_table.go:19）同名同语义
const CACHE_DIR = 'polaris-rules';

// 结构组：本地分流方案自己生成的四个组（顺序即契约，见 builder.js）。
// 安卓端 routing_table.go:23-26 同名同义。
const GROUP_SELECTOR = '🚀 节点选择';   // 主选择组：节点页顶部那张卡，默认选中「自动选择」
const GROUP_AUTO = '自动选择';          // url-test 测速组
const GROUP_FALLBACK = '故障转移';      // fallback 组
const GROUP_FINAL = '🐟 漏网之鱼';      // MATCH 兜底组（刻意不 include-all）
// 测速 URL 必须与 mihomo 的 constant.DefaultTestURL 逐字一致（安卓端坑 10）
const TEST_URL = 'https://www.gstatic.com/generate_204';

/**
 * 全部内置规则集 —— **都是本地文件 provider**（type:file），启动与运行期不拉任何远程规则。
 *
 * 与安卓端 routing_table.go 的 Providers 表逐条一致；scripts/routing-sync-test.js
 * 会在有 kernel-core 的分支上真的解析 Go 源码来比对，防止两端再次漂移。
 *
 * behavior: domain | ipcidr | classical
 * format:   yaml | text（ACL 的 .list 是纯文本规则行，mihomo 的 text 解析会跳过 # 注释）
 * noResolve: IP 类规则集附加 no-resolve，避免为了匹配 IP 规则去解析域名
 * url:      恒为空串（本地文件）。远程 http provider 只属于用户自定义组，
 *           内置组一条都没有 —— 见 routing_decision_test.go 的 TestNoRemoteRuleSource。
 */
const PROVIDERS = {
  // --- ACL4SSR 文本清单（种子随包内置，type:file 只读本地）---
  acl_banad:          { behavior: 'classical', format: 'text', noResolve: false, url: '' },
  acl_banprogramad:   { behavior: 'classical', format: 'text', noResolve: false, url: '' },
  acl_chinadomain:    { behavior: 'classical', format: 'text', noResolve: false, url: '' },
  acl_chinacompanyip: { behavior: 'classical', format: 'text', noResolve: true, url: '' },
  acl_unban:          { behavior: 'classical', format: 'text', noResolve: false, url: '' },
  acl_steamcn:        { behavior: 'classical', format: 'text', noResolve: false, url: '' },
  acl_download:       { behavior: 'classical', format: 'text', noResolve: false, url: '' },
  acl_chinamedia:     { behavior: 'classical', format: 'text', noResolve: false, url: '' },
  acl_proxygfwlist:   { behavior: 'classical', format: 'text', noResolve: false, url: '' },
  acl_proxymedia:     { behavior: 'classical', format: 'text', noResolve: false, url: '' },
};

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
 * 内置分流组。
 * 顺序 = 规则表的顺序（用户没拖过时就是它），也是分流页与节点页的默认展示顺序。
 * defaultOut: proxy | direct | block —— 决定该组默认出站与成员顺序。
 * defaultOn: 全新安装默认开启的组（2026-10-10 用户定的口径：常用服务先开，
 *   拦截类与社交类留给他自己决定）。
 *   归类顺序：AI 与流媒体 → 社交 → Google → Apple → 微软 → 游戏 → 国内直连/穿墙 → 拦截。
 *
 * **本表与安卓端 routing_table.go 的 Table 逐条一致**（组名、顺序、默认开关、
 * 默认出站、内联规则、providers 全同），scripts/routing-sync-test.js 会校验。
 * 为什么必须一致：两端是同一份产品的两个实现，任何一处不一致都会出现
 * 「手机上这样、电脑上那样」的分流差异，而且极难查。
 *
 * 「恶意软件」组缺失：其引用的 geosite:malware/phishing 类别仅存在于
 * karing-ruleset 的 Iran 专用源，meta-rules-dat 无对应类别，暂不提供。
 * 「Google FCM」「苹果推送通知」两个组已移除（2026-10-10）：排在 Google 与
 * 苹果服务之后时，它们的域名与 IP 规则 100%/部分被前面的组提前命中，分组开关
 * 设成什么出口都不生效（配置与实际行为不符）。Google 统一走「🌏 Google」，
 * 苹果统一走「🍎 苹果服务」，不再单列。
 *
 * 规则数据源全部本地的原因：旧方案每个 RULE-SET 都是 type:http，启动后 24h 内
 * 必定拉一次；拉取失败、被中间盒改写、上游每日变更都会让规则**静默失效**。
 * 用户 2026-10-12 反馈「抖音/快手/小红书/番茄小说连上 VPN 就断网、断开才好」
 * 正是这种依赖的代价 —— 旧方案里国内域名只有 ACL4SSR ChinaDomain.list 的
 * 700 多条，主流 App 域名一旦缺失就落到「🐟 漏网之鱼」走代理。
 * 现在 geosite 的 cn 分类有 111,873 条、geolocation-!cn 有 27,037 条（已内置），
 * 并额外给「🎯 国内直连」补 GEOIP,CN，兜住 HTTPDNS/直连 IP 的场景。
 */
const TABLE = [
  { name: '📲 电报消息', defaultOn: true, defaultOut: 'proxy', inlineRules: ['GEOSITE,telegram,{t}'] },
  { name: '📹 油管视频', defaultOn: true, defaultOut: 'proxy', inlineRules: ['GEOSITE,youtube,{t}'] },
  { name: '🎥 奈飞视频', defaultOn: true, defaultOut: 'proxy', inlineRules: ['GEOSITE,netflix,{t}'] },
  { name: '📲 X', defaultOn: true, defaultOut: 'proxy', inlineRules: ['GEOSITE,x,{t}'] },
  { name: '🎧 TikTok', defaultOn: true, defaultOut: 'proxy', inlineRules: ['GEOSITE,tiktok,{t}'] },
  { name: '📸 Instagram', defaultOn: true, defaultOut: 'proxy', inlineRules: ['GEOSITE,instagram,{t}'] },
  { name: '📲 Facebook', defaultOn: true, defaultOut: 'proxy', inlineRules: ['GEOSITE,facebook,{t}'] },
  { name: '📲 WhatsApp', defaultOn: true, defaultOut: 'proxy', inlineRules: ['GEOSITE,whatsapp,{t}'] },
  { name: '♊️ Google Gemini', defaultOn: true, defaultOut: 'proxy',
    inlineRules: ['GEOSITE,google-gemini,{t}', 'GEOSITE,google,{t}', 'GEOSITE,google-play,{t}'] },
  { name: '💬 Claude', defaultOn: true, defaultOut: 'proxy', inlineRules: ['GEOSITE,anthropic,{t}'] },
  { name: '💬 OpenAI', defaultOn: true, defaultOut: 'proxy', inlineRules: ['GEOSITE,openai,{t}'] },
  { name: '🐱 GitHub', defaultOn: true, defaultOut: 'proxy', inlineRules: ['GEOSITE,github,{t}'] },
  { name: '🌏 Google', defaultOn: true, defaultOut: 'proxy', inlineRules: ['GEOSITE,google,{t}'] },
  { name: '🌏 Google Play', defaultOn: true, defaultOut: 'proxy', inlineRules: ['GEOSITE,google-play,{t}'] },
  { name: 'Ⓜ️ 微软Bing', defaultOn: true, defaultOut: 'proxy', inlineRules: ['GEOSITE,bing,{t}'] },
  // 微软云盘放在微软服务之前（顺序即优先级），但 geosite 的 onedrive 分类只有 19 条，
  // 真正传文件字节的内容主机 docs.live.net / content.live.net 不在里面，会漏到下一组
  // 「Ⓜ️ 微软服务」。那一组默认走代理，于是「微软云盘选直连」对这几个域名无效 ——
  // 实测（routing_decision_test.go 的 TestOnedriveCannotLeakToMicrosoftGroup）确认过。
  // 所以这里补四条内联域名兜底，保证云盘相关流量不会跑到别的组去。
  { name: 'Ⓜ️ 微软云盘', defaultOn: true, defaultOut: 'direct',
    inlineRules: [
      'GEOSITE,onedrive,{t}',
      'DOMAIN-SUFFIX,docs.live.net,{t}',
      'DOMAIN-SUFFIX,content.live.net,{t}',
      'DOMAIN-SUFFIX,odc.officeapps.live.com,{t}',
      'DOMAIN-SUFFIX,officeapps.live.com,{t}',
    ] },
  { name: 'Ⓜ️ 微软服务', defaultOn: true, defaultOut: 'proxy', inlineRules: ['GEOSITE,microsoft,{t}'] },
  { name: '🍎 苹果服务', defaultOn: true, defaultOut: 'direct', inlineRules: ['GEOSITE,apple,{t}'] },
  { name: '🎮 游戏平台', defaultOn: true, defaultOut: 'proxy',
    inlineRules: ['GEOSITE,steam,{t}', 'GEOSITE,epicgames,{t}', 'GEOSITE,sony,{t}',
      'GEOSITE,nintendo,{t}', 'GEOSITE,category-games,{t}'] },
  { name: '📺 哔哩哔哩', defaultOn: true, defaultOut: 'direct', inlineRules: ['GEOSITE,bilibili,{t}'] },
  { name: '🎶 网易音乐', defaultOn: true, defaultOut: 'direct', inlineRules: ['GEOSITE,netease,{t}'] },
  {
    name: '🎯 国内直连',
    defaultOn: true,
    defaultOut: 'direct',
    // 国内覆盖分两层，缺一不可：
    // 1) GEOSITE,cn 读内置 geosite.dat 的 cn 分类（111,873 条，聚合 v2fly/ACL4SSR/
    //    Loyalsoldier 等来源），是长尾覆盖的主力。原先这个组只有 gp_cn（geoip/cn，
    //    纯 IP 段）加 ACL4SSR 的 ChinaDomain.list（700 多条），**完全没有成规模的国内
    //    域名表**，所以抖音/快手/小红书/番茄小说这类主流 App 一旦不在
    //    ChinaDomain.list 里就漏到「🐟 漏网之鱼」走代理，表现为「连上 VPN 就断网、
    //    断开就好」（用户 2026-10-11 反馈）。
    // 2) GEOIP,CN 读内置 geoip.metadb：国内 App 普遍用 HTTPDNS/直连 IP，纯域名规则
    //    命中不了它们，必须按 IP 兜一层。
    // 3) 内联规则兜底：geosite.dat 虽已内置，但文件缺失/损坏时 GEOSITE 规则会直接
    //    让 profile 加载失败，所以把用户点名的主流 App 域名再内联一遍。
    inlineRules: [
      // 抖音 / 字节跳动
      'DOMAIN-SUFFIX,douyin.com,{t}',
      'DOMAIN-SUFFIX,iesdouyin.com,{t}',
      'DOMAIN-SUFFIX,amemv.com,{t}',
      'DOMAIN-SUFFIX,byteimg.com,{t}',
      'DOMAIN-SUFFIX,bytedance.com,{t}',
      'DOMAIN-SUFFIX,bytetos.com,{t}',
      'DOMAIN-SUFFIX,toutiao.com,{t}',
      'DOMAIN-SUFFIX,toutiaocdn.com,{t}',
      'DOMAIN-SUFFIX,volcengine.com,{t}',
      'DOMAIN-SUFFIX,volces.com,{t}',
      'DOMAIN-SUFFIX,zjcdn.com,{t}',
      // 快手
      'DOMAIN-SUFFIX,kuaishou.com,{t}',
      'DOMAIN-SUFFIX,kuaishouzt.cn,{t}',
      'DOMAIN-SUFFIX,gifshow.com,{t}',
      'DOMAIN-SUFFIX,kwaicdn.com,{t}',
      'DOMAIN-SUFFIX,ksapisrv.com,{t}',
      // 小红书
      'DOMAIN-SUFFIX,xiaohongshu.com,{t}',
      'DOMAIN-SUFFIX,xhscdn.com,{t}',
      'DOMAIN-SUFFIX,xhslink.com,{t}',
      // 番茄小说
      'DOMAIN-SUFFIX,fanqienovel.com,{t}',
      'DOMAIN-SUFFIX,sigfanqie.com,{t}',
      // 微信 / QQ
      'DOMAIN-SUFFIX,weixin.qq.com,{t}',
      'DOMAIN-SUFFIX,qlogo.cn,{t}',
      'DOMAIN-SUFFIX,qpic.cn,{t}',
      'DOMAIN-SUFFIX,gtimg.com,{t}',
      'DOMAIN-SUFFIX,myqcloud.com,{t}',
      // 淘宝 / 阿里
      'DOMAIN-SUFFIX,taobaocdn.com,{t}',
      'DOMAIN-SUFFIX,alipayobjects.com,{t}',
      'DOMAIN-SUFFIX,aliyuncs.com,{t}',
      'DOMAIN-SUFFIX,aliyun.com.cn,{t}',
      'DOMAIN-SUFFIX,dingtalk.com,{t}',
      'DOMAIN-SUFFIX,alibaba-inc.com,{t}',
      // 京东
      'DOMAIN-SUFFIX,jd.hk,{t}',
      'DOMAIN-SUFFIX,jdcloud.com,{t}',
      'DOMAIN-SUFFIX,360buyimg.com,{t}',
      // 百度
      'DOMAIN-SUFFIX,baidubce.com,{t}',
      'DOMAIN-SUFFIX,bdimg.com,{t}',
      'DOMAIN-SUFFIX,baidupcs.com,{t}',
      // 网易
      'DOMAIN-SUFFIX,netease.im,{t}',
      'DOMAIN-SUFFIX,126.com,{t}',
      'DOMAIN-SUFFIX,126.net,{t}',
      // 微博 / 新浪
      'DOMAIN-SUFFIX,sina.com,{t}',
      'DOMAIN-SUFFIX,sina.cn,{t}',
      'DOMAIN-SUFFIX,weibo.com.cn,{t}',
      'DOMAIN-SUFFIX,sinaimg.com,{t}',
      'DOMAIN-SUFFIX,sinaedge.com,{t}',
      // 美团 / 点评
      'DOMAIN-SUFFIX,meituan.com,{t}',
      'DOMAIN-SUFFIX,dpfile.com,{t}',
      // 拼多多
      'DOMAIN-SUFFIX,yangkeduo.com,{t}',
      // 滴滴
      'DOMAIN-SUFFIX,didichuxing.com,{t}',
      'DOMAIN-SUFFIX,didistatic.com,{t}',
      // 华为 / 小米 / OPPO / vivo
      'DOMAIN-SUFFIX,vmall.com,{t}',
      'DOMAIN-SUFFIX,oppo.com,{t}',
      'DOMAIN-SUFFIX,opposhop.cn,{t}',
      'DOMAIN-SUFFIX,vivo.com.cn,{t}',
      // 其他常见国内站点
      'DOMAIN-SUFFIX,360.cn,{t}',
      'DOMAIN-SUFFIX,360.com,{t}',
      'DOMAIN-SUFFIX,sohu.com,{t}',
      'DOMAIN-SUFFIX,58.com,{t}',
      'DOMAIN-SUFFIX,ganji.com,{t}',
      'DOMAIN-SUFFIX,qcloud.com,{t}',
      'DOMAIN-SUFFIX,tencent.com,{t}',
      'DOMAIN-SUFFIX,tencentcs.com,{t}',
      'DOMAIN-SUFFIX,zhipin.com,{t}',
      'DOMAIN-SUFFIX,zhihu.com.cn,{t}',
      'GEOSITE,cn,{t}',
      'GEOIP,CN,{t},no-resolve',
    ],
    providers: ['acl_chinadomain', 'acl_chinacompanyip', 'acl_unban', 'acl_steamcn', 'acl_download', 'acl_chinamedia'],
  },
  { name: '🌏 国外穿墙', defaultOn: true, defaultOut: 'proxy',
    inlineRules: ['GEOSITE,geolocation-!cn,{t}'],
    providers: ['acl_proxygfwlist', 'acl_proxymedia'] },
  { name: '🍃 应用净化', defaultOn: false, defaultOut: 'block', providers: ['acl_banprogramad'] },
  { name: '🛑 广告拦截', defaultOn: false, defaultOut: 'block',
    inlineRules: ['GEOSITE,category-ads-all,{t}'],
    providers: ['acl_banad'] },
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
 * 内置规则全是 type:file，这里就是内核唯一会读的那个文件的位置。
 */
function seedPath(key) { return `${CACHE_DIR}/${seedFile(key)}`; }

/**
 * 规则集 -> mihomo rule-provider 配置（与安卓端 ProviderRawMap 逐字段一致）。
 *
 * 内置规则全是 type:file：只给 path，**不给 url 也不给 interval** —— 内核只读随包
 * 播种的种子，启动与运行期都不联网。真正需要联网刷新的是 geosite.dat / geoip.metadb
 * （资源文件，不是 rule-provider），由 electron/core/geosync.js 每 24 小时拉一次。
 *
 * path 指向随包种子播种的位置：内核按 -d（即 data/）解析，最终落在
 * data/polaris-rules/<key>.yaml —— 与安卓端 seedRoutingProviders 落点一致。
 */
function providerConfig(key) {
  const p = PROVIDERS[key];
  if (!p) return null;
  return { type: 'file', behavior: p.behavior, format: p.format, path: seedPath(key) };
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
  GROUP_SELECTOR,
  GROUP_AUTO,
  GROUP_FALLBACK,
  GROUP_FINAL,
  TEST_URL,
  PROVIDERS,
  TABLE,
  reservedNames,
  isReservedName,
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
