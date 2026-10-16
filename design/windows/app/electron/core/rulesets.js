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
 * 内核只读本地文件、自己不联网。运行期由 electron/core/rulesync.js 每 12 小时从
 * `ipevel/polaris-rules` 拉一次：**清单与 geodata（geosite.dat / geoip.metadb /
 * ASN.mmdb）同一个节拍**，逐文件校验 sha256 后再替换 —— 规则数据源只有这一个仓库，
 * 不再直接依赖任何第三方上游，也不再有任何远程 rule-provider URL。
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
  // 键名 = ipevel/polaris-rules 仓库里 dist/rules/<键>.list 的文件名。
  // 运行时由 electron/core/rulesync.js 按同一个 commit 拉取并校验 sha256 后覆盖；
  // 随包种子只作首次启动/断网兜底。**改这里必须同步仓库的产出文件名。**
  social:         { behavior: 'classical', format: 'text', noResolve: false, url: '' },
  streaming:      { behavior: 'classical', format: 'text', noResolve: false, url: '' },
  ai:             { behavior: 'classical', format: 'text', noResolve: false, url: '' },
  games:          { behavior: 'classical', format: 'text', noResolve: false, url: '' },
  telegram:       { behavior: 'classical', format: 'text', noResolve: false, url: '' },
  tiktok:         { behavior: 'classical', format: 'text', noResolve: false, url: '' },
  'china-domain': { behavior: 'classical', format: 'text', noResolve: false, url: '' },
  'china-ip':     { behavior: 'classical', format: 'text', noResolve: true,  url: '' },
  'china-media':  { behavior: 'classical', format: 'text', noResolve: false, url: '' },
  onedrive:       { behavior: 'classical', format: 'text', noResolve: false, url: '' },
  apple:          { behavior: 'classical', format: 'text', noResolve: false, url: '' },
  'proxy-gfwlist': { behavior: 'classical', format: 'text', noResolve: false, url: '' },
  'proxy-media':   { behavior: 'classical', format: 'text', noResolve: false, url: '' },
  banad:          { behavior: 'classical', format: 'text', noResolve: false, url: '' },
  banprogramad:   { behavior: 'classical', format: 'text', noResolve: false, url: '' },
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
 *   归类顺序：流媒体与社交 → AI → GitHub → Google（Play 在 Google 前）→ 微软 → Apple →
 *   游戏 → 国内直连/穿墙 → 拦截。
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
 * 「♊️ Google Gemini」「💬 Claude」「💬 OpenAI」三个组合并成「🤖 AI」（2026-10-13，用户定）：
 * 旧 Gemini 组带着 `GEOSITE,google` / `GEOSITE,google-play` 并排在「🌏 Google」
 * 「🌏 Google Play」前面，把这两个组整体吃掉 —— 同一个病，合并时去掉，它们恢复生效。
 * 旧组名靠 LEGACY_GROUP_ALIASES 迁移（见下），用户已保存的开关与顺序不会静默丢失。
 *
 * 规则数据源全部本地的原因：旧方案每个 RULE-SET 都是 type:http，启动后 24h 内
 * 必定拉一次；拉取失败、被中间盒改写、上游每日变更都会让规则**静默失效**。
 * 用户 2026-10-12 反馈「抖音/快手/小红书/番茄小说连上 VPN 就断网、断开才好」
 * 正是这种依赖的代价 —— 旧方案里国内域名只有 ACL4SSR ChinaDomain.list 的
 * 700 多条，主流 App 域名一旦缺失就落到「🐟 漏网之鱼」走代理。
 * 现在 geosite 的 cn 分类有 111,873 条、geolocation-!cn 有 27,037 条（已内置），
 * 并额外给「🎯 国内直连」补 GEOIP,CN，兜住 HTTPDNS/直连 IP 的场景。
 */
/**
 * 内置分流组（**2026-10-13 用户定稿的分组表**）。
 *
 * 顺序 = 规则表的顺序（用户没拖过时就是它），也是分流页与节点页的默认展示顺序。
 * defaultOut: proxy | direct | block —— 决定该组默认出站与成员顺序。
 * defaultOn: 全新安装默认开启的组。
 *
 * **本表与安卓端 routing_table.go 的 Table 逐条一致**（组名、顺序、默认开关、
 * 默认出站、内联规则、providers 全同），scripts/routing-sync-test.js 会校验。
 * 为什么必须一致：两端是同一份产品的两个实现，任何一处不一致都会出现
 * 「手机上这样、电脑上那样」的分流差异，而且极难查。
 *
 * --- 2026-10-13 分组简化（用户 1–6 条）---
 * 目标：组数从 23 降到 12，把「用户根本不单独调」的组收掉，规则不丢。
 *
 * 合并：
 *   X + Instagram + Facebook + WhatsApp → 「📲 社交媒体」
 *   油管视频 + 奈飞视频 + 迪士尼/HBO/Hulu/AppleTV/BBC/Abema/DAZN/Twitch/
 *     Spotify/Amazon → 「📺 流媒体」（规则从 2 份变成 1 份 streaming.list）
 *   微软云盘 + 微软服务 → 「Ⓜ️ 微软」（云盘必须直连省流量；2026-10-14 收窄成
 *     云盘 + 账号认证 + 系统更新 + Azure，微软其余域名回落「🌏 国外穿墙」）
 * 取消（规则改走「🌏 国外穿墙」兜底，出口不变，仍是代理）：
 *   GitHub / Google Play / Google / 微软Bing / 微软服务
 *   这五个组原本都是 defaultOut=proxy，「国外穿墙」也是 proxy，且它们的内联
 *   GEOSITE 分类（github / google-play / google / bing / microsoft）在
 *   geolocation-!cn 与 proxy-gfwlist.list 里有等价覆盖 → 出口行为不变。
 *   取消它们同时**消灭了两个死开关**：Google Play 与微软Bing 的规则被前面的组
 *   提前命中，开关设成什么都不生效（与 2026-10-10 移除 Google FCM 同一个病）。
 * 保留：
 *   「🍎 苹果服务」**必须保留**——它是 defaultOut=direct，简单删规则会让 iCloud /
 *   App Store 从直连变成走代理，是行为变更。用户 2026-10-13 定：保留该组且出口可选。
 *   「Ⓜ️ 微软」的云盘部分同理（direct）。
 * 并入：
 *   哔哩哔哩 / 网易音乐 → 国内直连（规则进 china-media.list，出口都 direct）
 * 改：
 *   「🛑 广告拦截」默认打开（用户 2026-10-13 定）
 *
 * 分组数量：12 个内置组 + 4 个结构组（🚀 节点选择 / 自动选择 / 故障转移 /
 * 🐟 漏网之鱼，见文件头）。
 *
 * 规则数据源全部本地的原因：旧方案每个 RULE-SET 都是 type:http，启动后 24h 内
 * 必定拉一次；拉取失败、被中间盒改写、上游每日变更都会让规则**静默失效**。
 * 用户 2026-10-12 反馈「抖音/快手/小红书/番茄小说连上 VPN 就断网、断开才好」
 * 正是这种依赖的代价 —— 旧方案里国内域名只有 ACL4SSR ChinaDomain.list 的
 * 700 多条，主流 App 域名一旦缺失就落到「🐟 漏网之鱼」走代理。
 * 现在 geosite 的 cn 分类有 111,873 条、geolocation-!cn 有 27,037 条（已内置），
 * 并额外给「🎯 国内直连」补 GEOIP,CN，兜住 HTTPDNS/直连 IP 的场景。
 *
 * 运行期刷新：规则清单与 geo 数据现在由 electron/core/rulesync.js 从
 * ipevel/polaris-rules 按同一个 commit 拉取并校验 sha256（12 小时一次），
 * 随包种子只作首次启动/断网兜底。providers 的键名就是仓库里的文件名。
 */
const TABLE = [
  { name: '📲 电报消息', defaultOn: true, defaultOut: 'proxy',
    inlineRules: ['GEOSITE,telegram,{t}'],
    providers: ['telegram'] },
  { name: '🎧 TikTok', defaultOn: true, defaultOut: 'proxy',
    inlineRules: ['GEOSITE,tiktok,{t}'],
    providers: ['tiktok'] },
  { name: '📲 社交媒体', defaultOn: true, defaultOut: 'proxy',
    inlineRules: ['GEOSITE,x,{t}', 'GEOSITE,instagram,{t}', 'GEOSITE,facebook,{t}',
      'GEOSITE,whatsapp,{t}'],
    providers: ['social'] },
  { name: '📺 流媒体', defaultOn: true, defaultOut: 'proxy',
    inlineRules: ['GEOSITE,youtube,{t}', 'GEOSITE,netflix,{t}'],
    providers: ['streaming'] },
  // 「🤖 AI」= 原「♊️ Google Gemini」+「💬 Claude」+「💬 OpenAI」三组合并（2026-10-13）。
  // 刻意**不带** GEOSITE,google / GEOSITE,google-play：旧 Gemini 组带着这两条排在
  // 「🌏 Google」「🌏 Google Play」前面，把这两个组整体吃掉 —— 它们的开关设成什么都不
  // 生效（与 2026-10-10 移除 Google FCM / 苹果推送那两个组是同一个病）。2026-10-13
  // 分组简化时 Google 那两个组本身也取消了，但这条经验仍然是本组不写它们的理由。
  // 规则 = 4 条 GEOSITE 分类（geosite.dat 深度覆盖）+ 147 条内联域名（geosite 缺失/
  // 过期时兜底），其中前 43 条是用户 2026-10-13 点名的清单，顺序照抄；其余为补齐。
  // 刻意**不含** DeepSeek / 豆包 / Kimi / 通义等国内可直连的 AI 服务（category-ai-!cn
  // 本身就不含 CN 分类，加了会把它们强行推去走代理，与 2026-10-12「连上 VPN 就断网」同类）。
  // 注意：ai.list（仓库）里已有 ACL4SSR 的 AI / OpenAi / ClaudeAI / Gemini 四份清单，
  // 内联域名与它高度重叠 —— 保留内联是刻意的：它是 geosite.dat 与规则文件同时缺失
  // 时的最后兜底（GEOSITE 规则在 geosite.dat 损坏时会让整个 profile 加载失败）。
  { name: '🤖 AI', defaultOn: true, defaultOut: 'proxy',
    inlineRules: [
      'GEOSITE,category-ai-!cn,{t}',
      'GEOSITE,openai,{t}',
      'GEOSITE,anthropic,{t}',
      'GEOSITE,google-gemini,{t}',
      'DOMAIN-SUFFIX,chatgpt.com,{t}',
      'DOMAIN-SUFFIX,openai.com,{t}',
      'DOMAIN-SUFFIX,sora.com,{t}',
      'DOMAIN-SUFFIX,claude.ai,{t}',
      'DOMAIN-SUFFIX,anthropic.com,{t}',
      'DOMAIN-SUFFIX,gemini.google.com,{t}',
      'DOMAIN-SUFFIX,aistudio.google.com,{t}',
      'DOMAIN-SUFFIX,notebooklm.google.com,{t}',
      'DOMAIN-SUFFIX,generativelanguage.googleapis.com,{t}',
      'DOMAIN-SUFFIX,aiplatform.googleapis.com,{t}',
      'DOMAIN-SUFFIX,ai.google.dev,{t}',
      'DOMAIN-SUFFIX,labs.google,{t}',
      'DOMAIN-SUFFIX,grok.com,{t}',
      'DOMAIN-SUFFIX,x.ai,{t}',
      'DOMAIN-SUFFIX,muse.ai,{t}',
      'DOMAIN-SUFFIX,meta.ai,{t}',
      'DOMAIN-SUFFIX,llama.com,{t}',
      'DOMAIN-SUFFIX,copilot.microsoft.com,{t}',
      'DOMAIN-SUFFIX,perplexity.ai,{t}',
      'DOMAIN-SUFFIX,poe.com,{t}',
      'DOMAIN-SUFFIX,character.ai,{t}',
      'DOMAIN-SUFFIX,mistral.ai,{t}',
      'DOMAIN-SUFFIX,pi.ai,{t}',
      'DOMAIN-SUFFIX,inflection.ai,{t}',
      'DOMAIN-SUFFIX,you.com,{t}',
      'DOMAIN-SUFFIX,cohere.com,{t}',
      'DOMAIN-SUFFIX,groq.com,{t}',
      'DOMAIN-SUFFIX,openrouter.ai,{t}',
      'DOMAIN-SUFFIX,together.ai,{t}',
      'DOMAIN-SUFFIX,huggingface.co,{t}',
      'DOMAIN-SUFFIX,midjourney.com,{t}',
      'DOMAIN-SUFFIX,runwayml.com,{t}',
      'DOMAIN-SUFFIX,leonardo.ai,{t}',
      'DOMAIN-SUFFIX,ideogram.ai,{t}',
      'DOMAIN-SUFFIX,stability.ai,{t}',
      'DOMAIN-SUFFIX,elevenlabs.com,{t}',
      'DOMAIN-SUFFIX,elevenlabs.io,{t}',
      'DOMAIN-SUFFIX,suno.com,{t}',
      'DOMAIN-SUFFIX,phind.com,{t}',
      'DOMAIN-SUFFIX,blackbox.ai,{t}',
      'DOMAIN-SUFFIX,cursor.com,{t}',
      'DOMAIN-SUFFIX,replit.com,{t}',
      'DOMAIN-SUFFIX,repl.co,{t}',
      'DOMAIN-SUFFIX,chat.com,{t}',
      'DOMAIN-SUFFIX,oaistatic.com,{t}',
      'DOMAIN-SUFFIX,oaiusercontent.com,{t}',
      'DOMAIN-SUFFIX,claude.com,{t}',
      'DOMAIN-SUFFIX,claudeusercontent.com,{t}',
      'DOMAIN-SUFFIX,bard.google.com,{t}',
      'DOMAIN-SUFFIX,makersuite.google.com,{t}',
      'DOMAIN-SUFFIX,gemini.google,{t}',
      'DOMAIN-SUFFIX,ai.studio,{t}',
      'DOMAIN-SUFFIX,deepmind.com,{t}',
      'DOMAIN-SUFFIX,deepmind.google,{t}',
      'DOMAIN-SUFFIX,jules.google.com,{t}',
      'DOMAIN-SUFFIX,notebooklm.google,{t}',
      'DOMAIN-SUFFIX,grokipedia.com,{t}',
      'DOMAIN-SUFFIX,copilot.cloud.microsoft,{t}',
      'DOMAIN-SUFFIX,githubcopilot.com,{t}',
      'DOMAIN-SUFFIX,pplx.ai,{t}',
      'DOMAIN-SUFFIX,perplexity.com,{t}',
      'DOMAIN-SUFFIX,poecdn.net,{t}',
      'DOMAIN-SUFFIX,cohere.ai,{t}',
      'DOMAIN-SUFFIX,together.xyz,{t}',
      'DOMAIN-SUFFIX,hf.co,{t}',
      'DOMAIN-SUFFIX,hf.space,{t}',
      'DOMAIN-SUFFIX,suno.ai,{t}',
      'DOMAIN-SUFFIX,dreamstudio.ai,{t}',
      'DOMAIN-SUFFIX,cursor.sh,{t}',
      'DOMAIN-SUFFIX,cursorapi.com,{t}',
      'DOMAIN-SUFFIX,replit.dev,{t}',
      'DOMAIN-SUFFIX,manus.im,{t}',
      'DOMAIN-SUFFIX,manuscdn.com,{t}',
      'DOMAIN-SUFFIX,jetbrains.ai,{t}',
      'DOMAIN-SUFFIX,grazie.ai,{t}',
      'DOMAIN-SUFFIX,windsurf.com,{t}',
      'DOMAIN-SUFFIX,windsurf.build,{t}',
      'DOMAIN-SUFFIX,codeium.com,{t}',
      'DOMAIN-SUFFIX,codeiumdata.com,{t}',
      'DOMAIN-SUFFIX,tabnine.com,{t}',
      'DOMAIN-SUFFIX,qodo.ai,{t}',
      'DOMAIN-SUFFIX,coderabbit.ai,{t}',
      'DOMAIN-SUFFIX,continue.dev,{t}',
      'DOMAIN-SUFFIX,aider.chat,{t}',
      'DOMAIN-SUFFIX,devin.ai,{t}',
      'DOMAIN-SUFFIX,magic.dev,{t}',
      'DOMAIN-SUFFIX,factory.ai,{t}',
      'DOMAIN-SUFFIX,lovable.dev,{t}',
      'DOMAIN-SUFFIX,bolt.new,{t}',
      'DOMAIN-SUFFIX,v0.dev,{t}',
      'DOMAIN-SUFFIX,v0.app,{t}',
      'DOMAIN-SUFFIX,cerebras.ai,{t}',
      'DOMAIN-SUFFIX,replicate.com,{t}',
      'DOMAIN-SUFFIX,fireworks.ai,{t}',
      'DOMAIN-SUFFIX,fal.ai,{t}',
      'DOMAIN-SUFFIX,deepinfra.com,{t}',
      'DOMAIN-SUFFIX,novita.ai,{t}',
      'DOMAIN-SUFFIX,anyscale.com,{t}',
      'DOMAIN-SUFFIX,baseten.co,{t}',
      'DOMAIN-SUFFIX,runpod.io,{t}',
      'DOMAIN-SUFFIX,lambdalabs.com,{t}',
      'DOMAIN-SUFFIX,vast.ai,{t}',
      'DOMAIN-SUFFIX,sambanova.ai,{t}',
      'DOMAIN-SUFFIX,ai21.com,{t}',
      'DOMAIN-SUFFIX,voyageai.com,{t}',
      'DOMAIN-SUFFIX,ollama.com,{t}',
      'DOMAIN-SUFFIX,lmstudio.ai,{t}',
      'DOMAIN-SUFFIX,jan.ai,{t}',
      'DOMAIN-SUFFIX,openwebui.com,{t}',
      'DOMAIN-SUFFIX,librechat.ai,{t}',
      'DOMAIN-SUFFIX,anythingllm.com,{t}',
      'DOMAIN-SUFFIX,dify.ai,{t}',
      'DOMAIN-SUFFIX,langchain.com,{t}',
      'DOMAIN-SUFFIX,llamaindex.ai,{t}',
      'DOMAIN-SUFFIX,deepset.ai,{t}',
      'DOMAIN-SUFFIX,wandb.ai,{t}',
      'DOMAIN-SUFFIX,pinecone.io,{t}',
      'DOMAIN-SUFFIX,weaviate.io,{t}',
      'DOMAIN-SUFFIX,qdrant.tech,{t}',
      'DOMAIN-SUFFIX,trychroma.com,{t}',
      'DOMAIN-SUFFIX,civitai.com,{t}',
      'DOMAIN-SUFFIX,recraft.ai,{t}',
      'DOMAIN-SUFFIX,krea.ai,{t}',
      'DOMAIN-SUFFIX,lumalabs.ai,{t}',
      'DOMAIN-SUFFIX,pika.art,{t}',
      'DOMAIN-SUFFIX,nightcafe.studio,{t}',
      'DOMAIN-SUFFIX,comfy.org,{t}',
      'DOMAIN-SUFFIX,comfyregistry.org,{t}',
      'DOMAIN-SUFFIX,heygen.com,{t}',
      'DOMAIN-SUFFIX,synthesia.io,{t}',
      'DOMAIN-SUFFIX,d-id.com,{t}',
      'DOMAIN-SUFFIX,captions.ai,{t}',
      'DOMAIN-SUFFIX,opus.pro,{t}',
      'DOMAIN-SUFFIX,descript.com,{t}',
      'DOMAIN-SUFFIX,lalal.ai,{t}',
      'DOMAIN-SUFFIX,moises.ai,{t}',
      'DOMAIN-SUFFIX,murf.ai,{t}',
      'DOMAIN-SUFFIX,play.ht,{t}',
      'DOMAIN-SUFFIX,resemble.ai,{t}',
      'DOMAIN-SUFFIX,speechify.com,{t}',
      'DOMAIN-SUFFIX,udio.com,{t}',
      'DOMAIN-SUFFIX,sider.ai,{t}',
      'DOMAIN-SUFFIX,jasper.ai,{t}',
      'DOMAIN-SUFFIX,copy.ai,{t}',
      'DOMAIN-SUFFIX,writesonic.com,{t}',
      'DOMAIN-SUFFIX,gamma.app,{t}',
      'DOMAIN-SUFFIX,tome.app,{t}',
    ],
    providers: ['ai'] },
  { name: '🎮 游戏平台', defaultOn: true, defaultOut: 'proxy',
    inlineRules: ['GEOSITE,steam,{t}', 'GEOSITE,epicgames,{t}', 'GEOSITE,sony,{t}',
      'GEOSITE,nintendo,{t}', 'GEOSITE,category-games,{t}'],
    providers: ['games'] },
  // 微软云盘与微软服务合成一组（2026-10-13 分组简化）。
  //
  // 2026-10-14 收窄（用户拍板）：旧表用 GEOSITE,microsoft + GEOSITE,bing 的宽口径把
  // GitHub / Bing / Office / Outlook / Teams 一并拉成直连；现在这些回落「🌏 国外穿墙」
  // （代理），本组只留三块 —— 云盘、账号/认证、系统更新，外加 Azure。
  // 随包种子 onedrive.yaml 也同步收窄（只含 OneDrive 的 17 条，不再并入 Microsoft.list），
  // 所以「微软服务」不再有 provider 兜底，组内每条规则都写死在这里。
  //
  // 为什么不能简单删掉这一组：它 defaultOut=direct，而云盘走代理太费流量
  // （用户 2026-10-13 明确：「要不太费流量了」）。删了会让云盘落回代理。
  // 组出口给用户一个开关整体切换：选 direct 时云盘直连，选 proxy 时整组走代理。
  // 注意 geosite 的 onedrive 分类只有 19 条，真正传文件字节的内容主机
  // docs.live.net / content.live.net 不在里面，会漏到下一组，所以必须补内联兜底
  // （实测见 routing_decision_test.go 的 TestOnedriveCannotLeakToMicrosoftGroup）。
  { name: 'Ⓜ️ 微软云盘', defaultOn: true, defaultOut: 'direct',
    // 2026-10-14 真网实测（docs/DEVNOTES.md A-64）：这一组的域名不是同一种命运 ——
    //   onedrive.live.com / skyapi.onedrive.live.com / api.onedrive.com → **TLS 被重置**（直连不通）
    //   docs.live.net / storage.live.com / my.microsoftpersonalcontent.com / login.live.com /
    //   graph.microsoft.com / sharepoint.com → 直连可用
    // 所以「默认直连」是按用户要求省流量（真正传字节的内容主机直连），但云盘 API 走直连会失败，
    // 用户打不开云盘时把本组切到「节点选择」即可。note 会显示在分流规则页那一行下面。
    note: 'onedrive.live.com 直连会被重置；云盘打不开就把本组切到「节点选择」',
    inlineRules: [
      // ① 云盘：OneDrive 本体与内容主机
      'GEOSITE,onedrive,{t}',
      'DOMAIN-SUFFIX,docs.live.net,{t}',
      'DOMAIN-SUFFIX,content.live.net,{t}',
      'DOMAIN-SUFFIX,odc.officeapps.live.com,{t}',
      'DOMAIN-SUFFIX,officeapps.live.com,{t}',
      // ② 账号登录：取文件前要先登录，漏了它云盘照样用不了
      'DOMAIN-SUFFIX,login.live.com,{t}',
      'DOMAIN-SUFFIX,login.microsoftonline.com,{t}',
      'DOMAIN-SUFFIX,login.microsoft.com,{t}',
      // ②b 身份/认证族：零流量、与 login.* 同族，业务版 OneDrive / SharePoint 登录会走到
      'DOMAIN-SUFFIX,msftauth.net,{t}',
      'DOMAIN-SUFFIX,msauth.net,{t}',
      'DOMAIN-SUFFIX,msftauthimages.net,{t}',
      'DOMAIN-SUFFIX,msauthimages.net,{t}',
      'DOMAIN-SUFFIX,msftidentity.com,{t}',
      'DOMAIN-SUFFIX,msidentity.com,{t}',
      // ③ 系统更新：geosite 里没有 windowsupdate 分类，只能逐条写
      'DOMAIN-SUFFIX,update.microsoft.com,{t}',
      'DOMAIN-SUFFIX,windowsupdate.microsoft.com,{t}',
      'DOMAIN-SUFFIX,windowsupdate.com,{t}',
      // ④ Azure
      'GEOSITE,azure,{t}',
    ],
    providers: ['onedrive'] },
  // 「🍎 苹果服务」保留（用户 2026-10-13 定：保留该组且出口可选）。
  // 它是 defaultOut=direct：简单删规则会让 iCloud / App Store 从直连变成走代理，
  // 是行为变更，可能让这些服务变慢或出问题。
  // 规则来自仓库的 apple.list（ACL4SSR Ruleset/Apple.list，29 条），随仓库更新。
  { name: '🍎 苹果服务', defaultOn: true, defaultOut: 'direct',
    inlineRules: ['GEOSITE,apple,{t}'],
    providers: ['apple'] },
  {
    name: '🎯 国内直连',
    defaultOn: true,
    defaultOut: 'direct',
    // 国内覆盖分三层，缺一不可：
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
    // providers 覆盖：国内域名（含 UnBan 白名单）、国内 IP 段、国内流媒体与下载
    // （哔哩哔哩 / 网易音乐 2026-10-13 并入，出口都是 direct）。
    // 注意 GEOIP,CN 放最前：先按 IP 归类国内直连，避免域名规则漏网后走代理，
    // 但域名规则优先级更高（DOMAIN 类永远先于 GEOIP 匹配）。
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
    providers: ['china-domain', 'china-ip', 'china-media'],
  },
  // 取消的五个组（GitHub / Google Play / Google / 微软Bing / 微软服务）的规则在此兜底：
  //   GEOSITE,geolocation-!cn 覆盖 google / github / bing / microsoft 等分类的绝大多数域名，
  //   proxy-gfwlist.list（6,883 条）与 proxy-media.list（357 条）再兜一层长尾。
  // 五个组原本都是 defaultOut=proxy，本组也是 proxy → 出口行为不变。
  // 失去的只是「单独切换某个服务走不走代理」的能力，这与用户「组数要少」的诉求一致。
  { name: '🌏 国外穿墙', defaultOn: true, defaultOut: 'proxy',
    inlineRules: ['GEOSITE,geolocation-!cn,{t}'],
    providers: ['proxy-gfwlist', 'proxy-media'] },
  { name: '🍃 应用净化', defaultOn: false, defaultOut: 'block',
    providers: ['banprogramad'] },
  { name: '🛑 广告拦截', defaultOn: true, defaultOut: 'block',
    inlineRules: ['GEOSITE,category-ads-all,{t}'],
    providers: ['banad'] },
];

/**
 * 内置分流表的结构版本：分组结构大改时 +1。
 * 消费方是 `electron/store.js` 的 migrate() —— settings.json 里记着上次见到的这个值，
 * 对不上就重置用户自定义的组顺序（开关不重置，靠 LEGACY_GROUP_ALIASES 迁移）。
 * 2026-10-13：23 组 → 12 组，2 → 3。
 */
const SCHEMA_VERSION = 3;

const BY_NAME = new Map(TABLE.map((g) => [g.name, g]));

/**
 * 旧组名 → 现组名（2026-10-13 分组简化 + 更早的「🤖 AI」合并）。
 *
 * 改名/合并后，用户 settings.json（`routing_rules` / `routing_order`）里存着的旧名
 * 会变成"未知名"被丢弃 —— 他自己调过的开关与顺序就静默丢了。所以读设置时先映射一次，
 * 而不是让用户重调一遍。
 *
 * 迁移语义：旧组**任一为开则新组为开**（映射后去重自然得到），
 * **顺序取若干旧组中最靠前的位置**（orderedTable 首次出现即定位，自然得到）。
 * 与安卓端 RoutingStateStore.kt / RoutingInputValidator.kt 的映射同一语义。
 *
 * 落点选在这里而不是各调用侧：`normalizeEnabled` / `orderedTable` 是所有读设置
 * 路径的收口（builder.js 组配置、manager.js 的列表/开关/拖动），只改一处不够。
 *
 * --- 2026-10-13 分组简化（23 → 12 组）的映射 ---
 * 合并：X / Instagram / Facebook / WhatsApp → 「📲 社交媒体」
 *       油管视频 / 奈飞视频 → 「📺 流媒体」
 *       微软云盘 / 微软服务 → 「Ⓜ️ 微软」（2026-10-15 该组改名「Ⓜ️ 微软云盘」，见下）
 * 取消（**刻意不映射**，映射表里查不到就按未知名丢弃）：
 *       🐱 GitHub / 🌏 Google Play / 🌏 Google / Ⓜ️ 微软Bing / Ⓜ️ 微软服务
 *   刻意不映射的理由：这五个组取消后规则并进「🌏 国外穿墙」，用户在它们上面的开关
 *   已经无处安放；**把任何旧组的开关状态带到新组都是错的** ——
 *   例如用户关掉「🌏 Google」，若映射成「国外穿墙」为关，会把他整个穿墙能力关掉。
 *   所以让这五个名字自然消失，新组按 defaultOn 走。
 *   注：「Ⓜ️ 微软服务」不映射到本组（现名「Ⓜ️ 微软云盘」），因为它必须默认直连，
 *   而微软服务的开关状态无从对应；两个旧名都丢弃、新组按 defaultOn 走。
 */
const LEGACY_GROUP_ALIASES = {
  // 2026-10-13 三组 AI 合并
  '♊️ Google Gemini': '🤖 AI',
  '💬 Claude': '🤖 AI',
  '💬 OpenAI': '🤖 AI',
  // 2026-10-13 分组简化：社交媒体四合一
  '📲 X': '📲 社交媒体',
  '📸 Instagram': '📲 社交媒体',
  '📲 Facebook': '📲 社交媒体',
  '📲 WhatsApp': '📲 社交媒体',
  // 2026-10-13 分组简化：流媒体二合一
  '📹 油管视频': '📺 流媒体',
  '🎥 奈飞视频': '📺 流媒体',
  // 2026-10-13 分组简化：微软云盘 + 微软服务合成一组；2026-10-15 该组改名「Ⓜ️ 微软云盘」，别名方向随之翻转
  'Ⓜ️ 微软': 'Ⓜ️ 微软云盘',
  // 2026-10-13 分组简化：哔哩哔哩 / 网易音乐并入国内直连
  '📺 哔哩哔哩': '🎯 国内直连',
  '🎶 网易音乐': '🎯 国内直连',
};

/** 旧组名映射到现组名；不是旧名（或不是字符串）就原样返回 */
function canonicalName(name) {
  return typeof name === 'string' && Object.prototype.hasOwnProperty.call(LEGACY_GROUP_ALIASES, name)
    ? LEGACY_GROUP_ALIASES[name]
    : name;
}

/** 默认开启的分流组名（用户从未改过设置时用它） */
function defaultEnabled() {
  return TABLE.filter((g) => g.defaultOn).map((g) => g.name);
}

/** 规范化用户设置：旧组名迁移、未知组名丢弃、去重，保持表内顺序 */
function normalizeEnabled(list) {
  if (!Array.isArray(list)) return defaultEnabled();
  const want = new Set(list.filter((n) => typeof n === 'string').map(canonicalName));
  return TABLE.filter((g) => want.has(g.name)).map((g) => g.name);
}

/**
 * 按用户自定义顺序排列内置分流表。
 * order 中出现的组名按给定次序排在前，未出现的按 TABLE 默认顺序追加在后；
 * 未知、重复、空名一律忽略，绝不丢组。空 order 直接返回 TABLE。
 * 顺序 = 规则匹配优先级。旧组名先迁移（否则三个旧名会被当未知名丢掉）。
 */
function orderedTable(order) {
  if (!Array.isArray(order) || order.length === 0) return TABLE;
  const picked = new Set();
  const out = [];
  for (const raw of order) {
    const name = canonicalName(raw);
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
const CUSTOM_NAME_MAX = 32;    // 组名长度上限（宽松侧：Windows 原为 24，与安卓 32 对齐；只放宽，不隐藏存量）

/** 名字里会破坏 YAML/JSON 结构或 mihomo 规则串分列的字符（观察名单 | > & * ! % @ ; = 先不禁，等真机实测内核能否加载） */
const NAME_FORBIDDEN_RE = /[,:{}[\]'"\\#\r\n\t]/;

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
/**
 * 把存储里的自定义分流组分成「可用」与「被隐藏」两类（**永不抛错**）。
 * 被隐藏 = 名字与保留名冲突 / 名字非法 / 重名 / 规则全废 / 超出数量上限。
 * **条目不删**：只是不参与配置组装与列表渲染；UI 必须按 hidden 清单每次读都提示。
 * 判定集合 = 结构组 + 当前内置表名 + mihomo 预注册（旧别名只用于迁移，不参与判定）。
 */
function classifyCustom(list) {
  const out = [];
  const hidden = [];
  const seen = new Set();
  if (!Array.isArray(list)) return { list: out, hidden: hidden };
  for (const raw of list) {
    if (!raw || typeof raw !== 'object') continue;
    const rawName = String(raw.name == null ? '' : raw.name).trim();
    // 长度按**原始**名字判：先截断再判会把超长名字静默改名后继续用（旧行为）
    if (rawName.length > CUSTOM_NAME_MAX) { hidden.push({ name: rawName.slice(0, 64), reason: 'invalid' }); continue; }
    const name = rawName;
    if (!name) { hidden.push({ name: String(raw.name || ''), reason: 'invalid' }); continue; }
    if (seen.has(name)) { hidden.push({ name: name, reason: 'invalid' }); continue; }
    if (isReservedName(name)) { hidden.push({ name: name, reason: 'reserved' }); continue; }
    // 存量判据：只看「实测会让内核配置加载失败」的（长度/字符集；观察名单除外）
    if (name.length > CUSTOM_NAME_MAX || NAME_FORBIDDEN_RE.test(name)) { hidden.push({ name: name, reason: 'invalid' }); continue; }
    if (out.length >= CUSTOM_MAX) { hidden.push({ name: name, reason: 'invalid' }); continue; }
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
    if (!rules.length) { hidden.push({ name: name, reason: 'invalid' }); continue; }
    seen.add(name);
    out.push({ name: name, out: OUTS.has(raw.out) ? raw.out : 'proxy', enabled: raw.enabled !== false, rules: rules });
  }
  return { list: out, hidden: hidden };
}

/** 只取可用的那一半（配置组装/列表渲染用）；被隐藏条目一律不进内核配置 */
function normalizeCustom(list) { return classifyCustom(list).list; }

/**
 * 存储形状的序列化（规则回写成文本）。**绝不过滤**：
 * 保存路径必须原样保留被隐藏条目，否则「对齐更严的判定」会追溯删掉用户已有的组。
 */
function serializeCustom(list) {
  return (Array.isArray(list) ? list : []).map((g) => ({
    name: String((g && g.name) == null ? '' : g.name),
    out: OUTS.has(g && g.out) ? g.out : 'proxy',
    enabled: !(g && g.enabled === false),
    rules: (Array.isArray(g && g.rules) ? g.rules : []).map((r) => (typeof r === 'string' ? r : ruleText(r))),
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
 * 内置规则全是 type:file：只给 path，**不给 url 也不给 interval** —— 内核只读本地
 * 播种的种子，启动与运行期都不联网。刷新（清单与 geodata 一起）由
 * electron/core/rulesync.js 每 12 小时做一次，见本文件头部的说明。
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
  SCHEMA_VERSION,
  LEGACY_GROUP_ALIASES,
  canonicalName,
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
  classifyCustom,
  serializeCustom,
  validateCustom,
};
