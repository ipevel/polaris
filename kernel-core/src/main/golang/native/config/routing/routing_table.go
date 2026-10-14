// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package routing

// 内置分流表：组名、顺序、默认开关与默认出站（顺序即规则匹配优先级）。
//
// 规则数据有两类来源，都在本地被 mihomo 直接读：
//
//   - GEOSITE,<分类> → 读随包的 geosite.dat（v2fly 全量分类）
//   - GEOIP,CN        → 读随包的 geoip.metadb
//   - RULE-SET        → 只允许 type:file 的本地清单
//
// **规则数据本身是活的**：清单与 geodata 由 ipevel/polaris-rules 每 15 分钟从上游
// （ACL4SSR/ACL4SSR 与 MetaCubeX/meta-rules-dat）同步、算 SHA-256 后发布；
// App 每 12 小时比一次版本号，只在真的变了才下载，校验通过后原子替换并热加载。
// 这里写的 providers 键名 = 仓库 dist/rules/<键>.list 的文件名，改这里必须同步仓库产出。
//
// 为什么不再把规则写死在包里：旧方案每个 RULE-SET 都是 type:http、启动后 24h 内必拉一次，
// 拉取失败、被中间盒改写、上游每日变更都会让规则**静默失效**。用户 2026-10-12 反馈
// 「抖音/快手/小红书/番茄小说连上 VPN 就断网、断开才好」正是这种依赖的代价 —— 旧方案里
// 国内域名只有 ACL4SSR ChinaDomain.list 的 700 多条，主流 App 域名一旦缺失就落到
// 「🐟 漏网之鱼」走代理。现在 GEOSITE,cn 有 111,873 条、geolocation-!cn 有 27,037 条，
// 并额外给「🎯 国内直连」补 GEOIP,CN，兜住 HTTPDNS/直连 IP 的场景。
//
// 已用 geosite.dat 实跑核对（见 zz_geodata_probe_test.go）：
// cn 111,873 条且覆盖 douyin/kuaishou/xiaohongshu/fanqienovel 等全部用户点名域名；
// 平台分类 telegram/youtube/netflix/x/tiktok/instagram/facebook/whatsapp/openai/
// google/google-play/google-gemini/bing/onedrive/microsoft/apple/steam/epicgames/sony/
// nintendo/bilibili/netease/anthropic 全部存在。

const (
	providerInterval = 86400 // 秒；仅用于用户自定义组的刷新节奏，内置规则不联网
	providerSubPath  = "polaris-rules"
)

const (
	GroupNameSelector = "🚀 节点选择"
	GroupNameAuto     = "自动选择"
	GroupNameFallback = "故障转移"
	GroupNameFinal    = "🐟 漏网之鱼"

	groupTypeSelect   = "select"
	groupTypeURLTest  = "url-test"
	groupTypeFallback = "fallback"

	outboundDirect = "DIRECT"
	outboundReject = "REJECT"

	// proxyGroupURL 与 mihomo 的 constant.DefaultTestURL 必须逐字一致：
	// 生成组显式声明 url 时，addTestUrlToProviders 会把它注册为 proxy-provider
	// 的额外 health-check 任务（healthcheck.go:url != hc.url 才登记）；两者不同
	// 会让同一节点被两个 URL 各测一次，而 LatestDelayTestUrl 只取「最近有结果」
	// 的那条 → UI 延迟在两条历史之间抖动、甚至把好节点读成超时。
	// 之前这里是明文 http://，既与被阻断的明文链路冲突，也与默认 URL 不一致。
	proxyGroupURL       = "https://www.gstatic.com/generate_204"
	proxyGroupInterval  = 300
	proxyGroupTolerance = 50
	// proxyGroupExpectedStatus 只把 204 视为存活。留空时 mihomo 默认 "*"，
	// 任意响应（含中间盒的拦截页 200）都算通，会伪造出「低延迟」。
	proxyGroupExpectedStatus = "204"
)

// Provider 一个规则集数据源。
//
// 只有两种取值：URL 非空 = 远程 http provider（仅用户自定义组使用）；
// URL 为空 = 本地文件 provider（种子随包内置，内核只读本地、永不联网）。
type Provider struct {
	Key       string // 稳定 ASCII 键：RULE-SET 引用名 + 预播种文件名
	URL       string // 空串表示本地文件 provider
	Behavior  string // domain | ipcidr | classical
	Format    string // yaml | text
	NoResolve bool   // IP 类规则集附加 no-resolve
}

// RuleGroup 一条内置分流规则组（组名与规则集沿用实机预设）。
type RuleGroup struct {
	Name        string   // 组名（与实机预设一致；改名会丢用户已保存的顺序与开关）
	DefaultOn   bool     // 默认开关；用户改过则以 routing.json 的 groups 为准
	DefaultOut  string   // 默认 outbound：proxy | direct | block
	Providers   []string // 本地文件 provider 键；为空表示纯 GEOSITE/GEOIP 规则
	InlineRules []string // 内联规则，{t} 替换为本组组名
}

// Providers 全部内置规则集，**全部是本地文件 provider（内核只读本地、永不联网）**。
//
// 键名 = ipevel/polaris-rules 仓库 dist/rules/<键>.list 的文件名，也是 RULE-SET 引用名，
// 三处必须是同一个字符串：这里的键、仓库的产出文件名、随包种子文件名。
// 改这里必须同时改仓库产出与两端种子，否则规则会整组静默失效。
//
// 种子文件按 <键> 打包进 APK assets：运行期由 RuleResources 校验 sha256 后覆盖，
// 随包那份只是首次启动/断网兜底。
var Providers = map[string]Provider{
	"social":        {"social", "", "classical", "text", false},
	"streaming":     {"streaming", "", "classical", "text", false},
	"ai":            {"ai", "", "classical", "text", false},
	"games":         {"games", "", "classical", "text", false},
	"telegram":      {"telegram", "", "classical", "text", false},
	"tiktok":        {"tiktok", "", "classical", "text", false},
	"china-domain":  {"china-domain", "", "classical", "text", false},
	"china-ip":      {"china-ip", "", "classical", "text", true},
	"china-media":   {"china-media", "", "classical", "text", false},
	"onedrive":      {"onedrive", "", "classical", "text", false},
	"apple":         {"apple", "", "classical", "text", false},
	"proxy-gfwlist": {"proxy-gfwlist", "", "classical", "text", false},
	"proxy-media":   {"proxy-media", "", "classical", "text", false},
	"banad":         {"banad", "", "classical", "text", false},
	"banprogramad":  {"banprogramad", "", "classical", "text", false},
}

// 本地网络/私有地址直连（固定 DIRECT，不占用分流组）。
var lanDirectRules = []string{
	"DOMAIN-SUFFIX,local,DIRECT",
	"DOMAIN-SUFFIX,lan,DIRECT",
	"DOMAIN-SUFFIX,localhost,DIRECT",
	"IP-CIDR,127.0.0.0/8,DIRECT,no-resolve",
	"IP-CIDR,10.0.0.0/8,DIRECT,no-resolve",
	"IP-CIDR,172.16.0.0/12,DIRECT,no-resolve",
	"IP-CIDR,192.168.0.0/16,DIRECT,no-resolve",
	"IP-CIDR,100.64.0.0/10,DIRECT,no-resolve",
	"IP-CIDR,169.254.0.0/16,DIRECT,no-resolve",
	"IP-CIDR,224.0.0.0/4,DIRECT,no-resolve",
	"IP-CIDR6,::1/128,DIRECT,no-resolve",
	"IP-CIDR6,fc00::/7,DIRECT,no-resolve",
	"IP-CIDR6,fe80::/10,DIRECT,no-resolve",
	"IP-CIDR6,fd00::/8,DIRECT,no-resolve",
}

// Table 内置分流组，顺序即规则匹配优先级。2026-10-14 按用户要求从 23 组简化到
// 12 组，原则是「一个开关对应一个用户能说清的东西」：
//
//   - 合并：X/Instagram/Facebook/WhatsApp → 「📲 社交媒体」；
//     油管/奈飞/迪士尼/HBO/Hulu/AppleTV/BBC/Abema/DAZN/Twitch/Spotify/Amazon → 「📺 流媒体」。
//   - 云盘与服务合并成「Ⓜ️ 微软」并默认直连（用户 2026-10-13：「要不太费流量了」）。
//   - 取消 GitHub / Google Play / Google / 微软Bing：它们本来就都是 proxy，规则已被
//     「🌏 国外穿墙」的 geolocation-!cn 覆盖，出口行为不变，却消灭了两个死开关
//     （Google Play 与微软Bing 的规则会被前面的组提前命中，开关设成什么都不生效）。
//   - 取消 哔哩哔哩 / 网易音乐：并入「🎯 国内直连」（走直连，不需要单独开关）。
//   - 2026-10-14 用户裁定「微软组只留云盘直连，其余走兜底」：上面那条宽口径的
//     GEOSITE,microsoft（752 条，含 github.com / githubusercontent.com / github.io /
//     bing / office / outlook / teams）会把已取消的 GitHub 系列重新吞成 **直连**，
//     所以把「Ⓜ️ 微软」收窄成「云盘 + 登录 + 系统更新 + Azure」四类。收窄后 GitHub 系列
//     按原设计落回「🌏 国外穿墙」（proxy-gfwlist 里本就有 github 的 10 条规则），
//     Bing / Office / Outlook / Teams 同样落回穿墙 —— 这正是取消这些组时的预期。
//   - 同日第二轮：该组声明的种子（onedrive provider）原本还并入了 ACL4SSR 的宽口径
//     Microsoft.list（DOMAIN-KEYWORD,microsoft + bing/office/outlook/msn + 裸 live.com）。
//     规则链里 RULE-SET 排在内联规则之前，于是上面删掉的 GEOSITE 所抓的域名又被它接回
//     直连 —— 单看表看不出来，是 harness 修好「RULE-SET 未注册 provider 时静默返回
//     false」这个假阴性之后才暴露的。已在规则仓库源头去掉那次合并
//     （polaris-rules b0cf02b：onedrive 92 → 17 条，只留 OneDrive 本体），并把 6 条
//     身份/认证域（msftauth.net / msauth.net / msftauthimages.net / msauthimages.net /
//     msftidentity.com / msidentity.com）补进本组内联规则：它们旧表靠 Microsoft.list
//     覆盖，那份不再并入后只能写在这里。
//   - 保留「🍎 苹果服务」且默认直连（用户已定）；「🛑 广告拦截」默认打开。
//
// 「🍎 苹果服务」「Ⓜ️ 微软」两组默认 **direct**：它们和翻墙无关，走代理纯属浪费流量。
// 用户在分流页仍可整体切到 proxy。
//
// 已取消的组名**不做别名映射**：旧开关状态无处安放，硬映射会把「我关掉 Google」变成
// 「我关掉整个穿墙」。历史改名（三个 AI 组 → 🤖 AI）才由 legacyGroupAliases 迁移。
// 顺序即优先级，非纯展示调整。
var Table = []RuleGroup{
	{Name: "📲 电报消息", DefaultOn: true, DefaultOut: "proxy",
		InlineRules: []string{"GEOSITE,telegram,{t}"},
		Providers:   []string{"telegram"}},
	{Name: "🎧 TikTok", DefaultOn: true, DefaultOut: "proxy",
		InlineRules: []string{"GEOSITE,tiktok,{t}"},
		Providers:   []string{"tiktok"}},
	{Name: "📲 社交媒体", DefaultOn: true, DefaultOut: "proxy",
		InlineRules: []string{"GEOSITE,x,{t}", "GEOSITE,instagram,{t}", "GEOSITE,facebook,{t}", "GEOSITE,whatsapp,{t}"},
		Providers:   []string{"social"}},
	{Name: "📺 流媒体", DefaultOn: true, DefaultOut: "proxy",
		InlineRules: []string{"GEOSITE,youtube,{t}", "GEOSITE,netflix,{t}"},
		Providers:   []string{"streaming"}},
	{Name: "🤖 AI", DefaultOn: true, DefaultOut: "proxy",
		InlineRules: []string{
			"GEOSITE,category-ai-!cn,{t}",
			"GEOSITE,openai,{t}",
			"GEOSITE,anthropic,{t}",
			"GEOSITE,google-gemini,{t}",
			"DOMAIN-SUFFIX,chatgpt.com,{t}",
			"DOMAIN-SUFFIX,openai.com,{t}",
			"DOMAIN-SUFFIX,sora.com,{t}",
			"DOMAIN-SUFFIX,claude.ai,{t}",
			"DOMAIN-SUFFIX,anthropic.com,{t}",
			"DOMAIN-SUFFIX,gemini.google.com,{t}",
			"DOMAIN-SUFFIX,aistudio.google.com,{t}",
			"DOMAIN-SUFFIX,notebooklm.google.com,{t}",
			"DOMAIN-SUFFIX,generativelanguage.googleapis.com,{t}",
			"DOMAIN-SUFFIX,aiplatform.googleapis.com,{t}",
			"DOMAIN-SUFFIX,ai.google.dev,{t}",
			"DOMAIN-SUFFIX,labs.google,{t}",
			"DOMAIN-SUFFIX,grok.com,{t}",
			"DOMAIN-SUFFIX,x.ai,{t}",
			"DOMAIN-SUFFIX,muse.ai,{t}",
			"DOMAIN-SUFFIX,meta.ai,{t}",
			"DOMAIN-SUFFIX,llama.com,{t}",
			"DOMAIN-SUFFIX,copilot.microsoft.com,{t}",
			"DOMAIN-SUFFIX,perplexity.ai,{t}",
			"DOMAIN-SUFFIX,poe.com,{t}",
			"DOMAIN-SUFFIX,character.ai,{t}",
			"DOMAIN-SUFFIX,mistral.ai,{t}",
			"DOMAIN-SUFFIX,pi.ai,{t}",
			"DOMAIN-SUFFIX,inflection.ai,{t}",
			"DOMAIN-SUFFIX,you.com,{t}",
			"DOMAIN-SUFFIX,cohere.com,{t}",
			"DOMAIN-SUFFIX,groq.com,{t}",
			"DOMAIN-SUFFIX,openrouter.ai,{t}",
			"DOMAIN-SUFFIX,together.ai,{t}",
			"DOMAIN-SUFFIX,huggingface.co,{t}",
			"DOMAIN-SUFFIX,midjourney.com,{t}",
			"DOMAIN-SUFFIX,runwayml.com,{t}",
			"DOMAIN-SUFFIX,leonardo.ai,{t}",
			"DOMAIN-SUFFIX,ideogram.ai,{t}",
			"DOMAIN-SUFFIX,stability.ai,{t}",
			"DOMAIN-SUFFIX,elevenlabs.com,{t}",
			"DOMAIN-SUFFIX,elevenlabs.io,{t}",
			"DOMAIN-SUFFIX,suno.com,{t}",
			"DOMAIN-SUFFIX,phind.com,{t}",
			"DOMAIN-SUFFIX,blackbox.ai,{t}",
			"DOMAIN-SUFFIX,cursor.com,{t}",
			"DOMAIN-SUFFIX,replit.com,{t}",
			"DOMAIN-SUFFIX,repl.co,{t}",
			"DOMAIN-SUFFIX,chat.com,{t}",
			"DOMAIN-SUFFIX,oaistatic.com,{t}",
			"DOMAIN-SUFFIX,oaiusercontent.com,{t}",
			"DOMAIN-SUFFIX,claude.com,{t}",
			"DOMAIN-SUFFIX,claudeusercontent.com,{t}",
			"DOMAIN-SUFFIX,bard.google.com,{t}",
			"DOMAIN-SUFFIX,makersuite.google.com,{t}",
			"DOMAIN-SUFFIX,gemini.google,{t}",
			"DOMAIN-SUFFIX,ai.studio,{t}",
			"DOMAIN-SUFFIX,deepmind.com,{t}",
			"DOMAIN-SUFFIX,deepmind.google,{t}",
			"DOMAIN-SUFFIX,jules.google.com,{t}",
			"DOMAIN-SUFFIX,notebooklm.google,{t}",
			"DOMAIN-SUFFIX,grokipedia.com,{t}",
			"DOMAIN-SUFFIX,copilot.cloud.microsoft,{t}",
			"DOMAIN-SUFFIX,githubcopilot.com,{t}",
			"DOMAIN-SUFFIX,pplx.ai,{t}",
			"DOMAIN-SUFFIX,perplexity.com,{t}",
			"DOMAIN-SUFFIX,poecdn.net,{t}",
			"DOMAIN-SUFFIX,cohere.ai,{t}",
			"DOMAIN-SUFFIX,together.xyz,{t}",
			"DOMAIN-SUFFIX,hf.co,{t}",
			"DOMAIN-SUFFIX,hf.space,{t}",
			"DOMAIN-SUFFIX,suno.ai,{t}",
			"DOMAIN-SUFFIX,dreamstudio.ai,{t}",
			"DOMAIN-SUFFIX,cursor.sh,{t}",
			"DOMAIN-SUFFIX,cursorapi.com,{t}",
			"DOMAIN-SUFFIX,replit.dev,{t}",
			"DOMAIN-SUFFIX,manus.im,{t}",
			"DOMAIN-SUFFIX,manuscdn.com,{t}",
			"DOMAIN-SUFFIX,jetbrains.ai,{t}",
			"DOMAIN-SUFFIX,grazie.ai,{t}",
			"DOMAIN-SUFFIX,windsurf.com,{t}",
			"DOMAIN-SUFFIX,windsurf.build,{t}",
			"DOMAIN-SUFFIX,codeium.com,{t}",
			"DOMAIN-SUFFIX,codeiumdata.com,{t}",
			"DOMAIN-SUFFIX,tabnine.com,{t}",
			"DOMAIN-SUFFIX,qodo.ai,{t}",
			"DOMAIN-SUFFIX,coderabbit.ai,{t}",
			"DOMAIN-SUFFIX,continue.dev,{t}",
			"DOMAIN-SUFFIX,aider.chat,{t}",
			"DOMAIN-SUFFIX,devin.ai,{t}",
			"DOMAIN-SUFFIX,magic.dev,{t}",
			"DOMAIN-SUFFIX,factory.ai,{t}",
			"DOMAIN-SUFFIX,lovable.dev,{t}",
			"DOMAIN-SUFFIX,bolt.new,{t}",
			"DOMAIN-SUFFIX,v0.dev,{t}",
			"DOMAIN-SUFFIX,v0.app,{t}",
			"DOMAIN-SUFFIX,cerebras.ai,{t}",
			"DOMAIN-SUFFIX,replicate.com,{t}",
			"DOMAIN-SUFFIX,fireworks.ai,{t}",
			"DOMAIN-SUFFIX,fal.ai,{t}",
			"DOMAIN-SUFFIX,deepinfra.com,{t}",
			"DOMAIN-SUFFIX,novita.ai,{t}",
			"DOMAIN-SUFFIX,anyscale.com,{t}",
			"DOMAIN-SUFFIX,baseten.co,{t}",
			"DOMAIN-SUFFIX,runpod.io,{t}",
			"DOMAIN-SUFFIX,lambdalabs.com,{t}",
			"DOMAIN-SUFFIX,vast.ai,{t}",
			"DOMAIN-SUFFIX,sambanova.ai,{t}",
			"DOMAIN-SUFFIX,ai21.com,{t}",
			"DOMAIN-SUFFIX,voyageai.com,{t}",
			"DOMAIN-SUFFIX,ollama.com,{t}",
			"DOMAIN-SUFFIX,lmstudio.ai,{t}",
			"DOMAIN-SUFFIX,jan.ai,{t}",
			"DOMAIN-SUFFIX,openwebui.com,{t}",
			"DOMAIN-SUFFIX,librechat.ai,{t}",
			"DOMAIN-SUFFIX,anythingllm.com,{t}",
			"DOMAIN-SUFFIX,dify.ai,{t}",
			"DOMAIN-SUFFIX,langchain.com,{t}",
			"DOMAIN-SUFFIX,llamaindex.ai,{t}",
			"DOMAIN-SUFFIX,deepset.ai,{t}",
			"DOMAIN-SUFFIX,wandb.ai,{t}",
			"DOMAIN-SUFFIX,pinecone.io,{t}",
			"DOMAIN-SUFFIX,weaviate.io,{t}",
			"DOMAIN-SUFFIX,qdrant.tech,{t}",
			"DOMAIN-SUFFIX,trychroma.com,{t}",
			"DOMAIN-SUFFIX,civitai.com,{t}",
			"DOMAIN-SUFFIX,recraft.ai,{t}",
			"DOMAIN-SUFFIX,krea.ai,{t}",
			"DOMAIN-SUFFIX,lumalabs.ai,{t}",
			"DOMAIN-SUFFIX,pika.art,{t}",
			"DOMAIN-SUFFIX,nightcafe.studio,{t}",
			"DOMAIN-SUFFIX,comfy.org,{t}",
			"DOMAIN-SUFFIX,comfyregistry.org,{t}",
			"DOMAIN-SUFFIX,heygen.com,{t}",
			"DOMAIN-SUFFIX,synthesia.io,{t}",
			"DOMAIN-SUFFIX,d-id.com,{t}",
			"DOMAIN-SUFFIX,captions.ai,{t}",
			"DOMAIN-SUFFIX,opus.pro,{t}",
			"DOMAIN-SUFFIX,descript.com,{t}",
			"DOMAIN-SUFFIX,lalal.ai,{t}",
			"DOMAIN-SUFFIX,moises.ai,{t}",
			"DOMAIN-SUFFIX,murf.ai,{t}",
			"DOMAIN-SUFFIX,play.ht,{t}",
			"DOMAIN-SUFFIX,resemble.ai,{t}",
			"DOMAIN-SUFFIX,speechify.com,{t}",
			"DOMAIN-SUFFIX,udio.com,{t}",
			"DOMAIN-SUFFIX,sider.ai,{t}",
			"DOMAIN-SUFFIX,jasper.ai,{t}",
			"DOMAIN-SUFFIX,copy.ai,{t}",
			"DOMAIN-SUFFIX,writesonic.com,{t}",
			"DOMAIN-SUFFIX,gamma.app,{t}",
			"DOMAIN-SUFFIX,tome.app,{t}",
		},
		Providers: []string{"ai"}},
	{Name: "🎮 游戏平台", DefaultOn: true, DefaultOut: "proxy",
		InlineRules: []string{
			"GEOSITE,steam,{t}",
			"GEOSITE,epicgames,{t}",
			"GEOSITE,sony,{t}",
			"GEOSITE,nintendo,{t}",
			"GEOSITE,category-games,{t}",
		},
		Providers: []string{"games"}},
	{Name: "Ⓜ️ 微软", DefaultOn: true, DefaultOut: "direct",
		InlineRules: []string{
			// ① 云盘：OneDrive 本体与内容主机（geosite 的 onedrive 分类只有 19 条）
			"GEOSITE,onedrive,{t}",
			"DOMAIN-SUFFIX,docs.live.net,{t}",
			"DOMAIN-SUFFIX,content.live.net,{t}",
			"DOMAIN-SUFFIX,odc.officeapps.live.com,{t}",
			"DOMAIN-SUFFIX,officeapps.live.com,{t}",
			// ② 账号登录：OneDrive 取文件前要先登录，漏了它云盘照样用不了。
			// 这三条是「云盘能正常用」的最小集，收窄前本来就被 GEOSITE,microsoft 覆盖成
			// 直连，不能因为收窄把登录踢到代理。到此为止，不再加 login.windows.net 之类。
			"DOMAIN-SUFFIX,login.live.com,{t}",
			"DOMAIN-SUFFIX,login.microsoftonline.com,{t}",
			"DOMAIN-SUFFIX,login.microsoft.com,{t}",
			// ②b 身份/认证族：零流量、与 login.* 同族，业务版 OneDrive / SharePoint 登录会走到。
			// 旧表靠宽口径 Microsoft.list 覆盖，那份不再并入种子后只能写在这里。
			// 不补的 4 条（aadrm.com / msappproxy.net / public-trust.com / omniroot.com）
			// 与云盘无关，走「🌏 国外穿墙」兜底。
			"DOMAIN-SUFFIX,msftauth.net,{t}",
			"DOMAIN-SUFFIX,msauth.net,{t}",
			"DOMAIN-SUFFIX,msftauthimages.net,{t}",
			"DOMAIN-SUFFIX,msauthimages.net,{t}",
			"DOMAIN-SUFFIX,msftidentity.com,{t}",
			"DOMAIN-SUFFIX,msidentity.com,{t}",
			// ③ 系统更新：geosite 里没有 windowsupdate 分类，只能逐条写
			"DOMAIN-SUFFIX,update.microsoft.com,{t}",
			"DOMAIN-SUFFIX,windowsupdate.microsoft.com,{t}",
			"DOMAIN-SUFFIX,windowsupdate.com,{t}",
			// ④ Azure：geosite 的 azure 分类 151 条，全部是 azure 自家域
			"GEOSITE,azure,{t}",
		},
		Providers: []string{"onedrive"}},
	{Name: "🍎 苹果服务", DefaultOn: true, DefaultOut: "direct",
		InlineRules: []string{"GEOSITE,apple,{t}"},
		Providers:   []string{"apple"}},
	{Name: "🎯 国内直连", DefaultOn: true, DefaultOut: "direct",
		InlineRules: []string{
			"DOMAIN-SUFFIX,douyin.com,{t}",
			"DOMAIN-SUFFIX,iesdouyin.com,{t}",
			"DOMAIN-SUFFIX,amemv.com,{t}",
			"DOMAIN-SUFFIX,byteimg.com,{t}",
			"DOMAIN-SUFFIX,bytedance.com,{t}",
			"DOMAIN-SUFFIX,bytetos.com,{t}",
			"DOMAIN-SUFFIX,toutiao.com,{t}",
			"DOMAIN-SUFFIX,toutiaocdn.com,{t}",
			"DOMAIN-SUFFIX,volcengine.com,{t}",
			"DOMAIN-SUFFIX,volces.com,{t}",
			"DOMAIN-SUFFIX,zjcdn.com,{t}",
			"DOMAIN-SUFFIX,kuaishou.com,{t}",
			"DOMAIN-SUFFIX,kuaishouzt.cn,{t}",
			"DOMAIN-SUFFIX,gifshow.com,{t}",
			"DOMAIN-SUFFIX,kwaicdn.com,{t}",
			"DOMAIN-SUFFIX,ksapisrv.com,{t}",
			"DOMAIN-SUFFIX,xiaohongshu.com,{t}",
			"DOMAIN-SUFFIX,xhscdn.com,{t}",
			"DOMAIN-SUFFIX,xhslink.com,{t}",
			"DOMAIN-SUFFIX,fanqienovel.com,{t}",
			"DOMAIN-SUFFIX,sigfanqie.com,{t}",
			"DOMAIN-SUFFIX,weixin.qq.com,{t}",
			"DOMAIN-SUFFIX,qlogo.cn,{t}",
			"DOMAIN-SUFFIX,qpic.cn,{t}",
			"DOMAIN-SUFFIX,gtimg.com,{t}",
			"DOMAIN-SUFFIX,myqcloud.com,{t}",
			"DOMAIN-SUFFIX,taobaocdn.com,{t}",
			"DOMAIN-SUFFIX,alipayobjects.com,{t}",
			"DOMAIN-SUFFIX,aliyuncs.com,{t}",
			"DOMAIN-SUFFIX,aliyun.com.cn,{t}",
			"DOMAIN-SUFFIX,dingtalk.com,{t}",
			"DOMAIN-SUFFIX,alibaba-inc.com,{t}",
			"DOMAIN-SUFFIX,jd.hk,{t}",
			"DOMAIN-SUFFIX,jdcloud.com,{t}",
			"DOMAIN-SUFFIX,360buyimg.com,{t}",
			"DOMAIN-SUFFIX,baidubce.com,{t}",
			"DOMAIN-SUFFIX,bdimg.com,{t}",
			"DOMAIN-SUFFIX,baidupcs.com,{t}",
			"DOMAIN-SUFFIX,netease.im,{t}",
			"DOMAIN-SUFFIX,126.com,{t}",
			"DOMAIN-SUFFIX,126.net,{t}",
			"DOMAIN-SUFFIX,sina.com,{t}",
			"DOMAIN-SUFFIX,sina.cn,{t}",
			"DOMAIN-SUFFIX,weibo.com.cn,{t}",
			"DOMAIN-SUFFIX,sinaimg.com,{t}",
			"DOMAIN-SUFFIX,sinaedge.com,{t}",
			"DOMAIN-SUFFIX,meituan.com,{t}",
			"DOMAIN-SUFFIX,dpfile.com,{t}",
			"DOMAIN-SUFFIX,yangkeduo.com,{t}",
			"DOMAIN-SUFFIX,didichuxing.com,{t}",
			"DOMAIN-SUFFIX,didistatic.com,{t}",
			"DOMAIN-SUFFIX,vmall.com,{t}",
			"DOMAIN-SUFFIX,oppo.com,{t}",
			"DOMAIN-SUFFIX,opposhop.cn,{t}",
			"DOMAIN-SUFFIX,vivo.com.cn,{t}",
			"DOMAIN-SUFFIX,360.cn,{t}",
			"DOMAIN-SUFFIX,360.com,{t}",
			"DOMAIN-SUFFIX,sohu.com,{t}",
			"DOMAIN-SUFFIX,58.com,{t}",
			"DOMAIN-SUFFIX,ganji.com,{t}",
			"DOMAIN-SUFFIX,qcloud.com,{t}",
			"DOMAIN-SUFFIX,tencent.com,{t}",
			"DOMAIN-SUFFIX,tencentcs.com,{t}",
			"DOMAIN-SUFFIX,zhipin.com,{t}",
			"DOMAIN-SUFFIX,zhihu.com.cn,{t}",
			"GEOSITE,cn,{t}",
			"GEOIP,CN,{t},no-resolve",
		},
		Providers: []string{"china-domain", "china-ip", "china-media"}},
	{Name: "🌏 国外穿墙", DefaultOn: true, DefaultOut: "proxy",
		InlineRules: []string{"GEOSITE,geolocation-!cn,{t}"},
		Providers:   []string{"proxy-gfwlist", "proxy-media"}},
	{Name: "🍃 应用净化", DefaultOn: false, DefaultOut: "block",
		Providers: []string{"banprogramad"}},
	{Name: "🛑 广告拦截", DefaultOn: true, DefaultOut: "block",
		InlineRules: []string{"GEOSITE,category-ads-all,{t}"},
		Providers:   []string{"banad"}},
}

// AIGroupName 合并后的 AI 组名（唯一真源；App 侧 RoutingGroups.kt 必须逐字一致）。
const AIGroupName = "🤖 AI"

// legacyGroupAliases 已改名的历史分流组名 → 当前组名。
//
// 2026-10-13 把「♊️ Google Gemini」「💬 Claude」「💬 OpenAI」合并成「🤖 AI」。
// 2026-10-14 又合并了 X/Instagram/Facebook/WhatsApp → 「📲 社交媒体」、
// 油管/奈飞 → 「📺 流媒体」、微软云盘 → 「Ⓜ️ 微软」、
// 哔哩哔哩/网易音乐 → 「🎯 国内直连」；已取消的 GitHub / Google Play / Google /
// 微软Bing **刻意不映射**（理由见 Table 上方注释）。
//
// 「Ⓜ️ 微软服务」同样**刻意不映射**，只映射「Ⓜ️ 微软云盘」：旧表里「微软服务」是
// **proxy** 组（云盘直连、服务走代理），而「Ⓜ️ 微软」的默认出站是 **direct**；硬映射
// 会把用户「这一组要代理」的意图翻成直连。不映射时这些域名走兜底落「🌏 国外穿墙」
// （proxy），反而保住了旧意图。
// 用户已保存的 routing.json 里 groups / order 仍是旧名：直接当未知名丢弃的话，
// 用户自己调过的开关会退回表内默认值、拖动顺序会退回内置顺序——静默丢设置。
// 所以读取侧统一映射：任一旧名为开 → 新组为开；顺序取三者中最靠前的位置。
var legacyGroupAliases = map[string]string{
	"♊️ Google Gemini": AIGroupName,
	"💬 Claude":         AIGroupName,
	"💬 OpenAI":         AIGroupName,
	"📲 X":              "📲 社交媒体",
	"📸 Instagram":      "📲 社交媒体",
	"📲 Facebook":       "📲 社交媒体",
	"📲 WhatsApp":       "📲 社交媒体",
	"📹 油管视频":           "📺 流媒体",
	"🎥 奈飞视频":           "📺 流媒体",
	"Ⓜ️ 微软云盘":          "Ⓜ️ 微软",
	"📺 哔哩哔哩":           "🎯 国内直连",
	"🎶 网易音乐":           "🎯 国内直连",
}

// canonicalGroupName 把历史组名映射为当前组名；非历史名原样返回。
func canonicalGroupName(name string) string {
	if current, ok := legacyGroupAliases[name]; ok {
		return current
	}
	return name
}

// OrderedTable 按用户自定义顺序返回内置分流表。
//
// order 中出现的组名按给定次序排在前，未出现的组按 Table 的默认顺序追加在后；
// 未知、重复、空名一律忽略，绝不丢组。空 order 直接返回 Table。
// 顺序决定「规则匹配优先级」与 App 侧展示顺序，不影响各组开关。
// 历史组名先经 canonicalGroupName 归一（改名后用户保存的顺序不能丢）。
func OrderedTable(order []string) []RuleGroup {
	if len(order) == 0 {
		return Table
	}
	byName := make(map[string]RuleGroup, len(Table))
	for _, item := range Table {
		byName[item.Name] = item
	}
	picked := make(map[string]bool, len(order))
	out := make([]RuleGroup, 0, len(Table))
	for _, name := range order {
		item, ok := byName[canonicalGroupName(name)]
		if !ok || picked[item.Name] {
			continue
		}
		picked[item.Name] = true
		out = append(out, item)
	}
	for _, item := range Table {
		if picked[item.Name] {
			continue
		}
		out = append(out, item)
	}
	return out
}
