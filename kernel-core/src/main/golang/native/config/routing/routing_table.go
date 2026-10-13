// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package routing

// 内置分流表：组名、顺序、默认开关与默认出站全部沿用既有实机预设（顺序即规则
// 匹配优先级），**规则数据源全部本地，启动与运行期不拉任何远程规则**：
//
//   - GEOSITE,<分类> → 读 app 内置的 geosite.dat（v2fly 全量分类）
//   - GEOIP,CN        → 读 app 内置的 geoip.metadb
//   - RULE-SET        → 只允许 type:file 的本地清单（种子已随包内置）
//
// 为什么彻底抛弃远程 provider：每个 RULE-SET 都是 type:http，启动后 24h 内必定拉一次；
// 拉取失败、被中间盒改写、上游每日变更都会让规则**静默失效**。用户 2026-10-12 反馈
// 「抖音/快手/小红书/番茄小说连上 VPN 就断网、断开才好」正是这种依赖的代价——旧方案里
// 国内域名只有 ACL4SSR ChinaDomain.list 的 700 多条，主流 App 域名一旦缺失就落到
// 「🐟 漏网之鱼」走代理。现在 GEOSITE,cn 有 111,873 条、geolocation-!cn 有 27,037 条，
// 并额外给「🎯 国内直连」补 GEOIP,CN，兜住 HTTPDNS/直连 IP 的场景。
//
// 已用内置 geosite.dat 实跑核对（见 zz_geodata_probe_test.go）：
// cn 111,873 条且覆盖 douyin/kuaishou/xiaohongshu/fanqienovel 等全部用户点名域名；
// 平台分类 telegram/youtube/netflix/x/tiktok/instagram/facebook/whatsapp/openai/github/
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

// Providers 全部内置规则集。种子文件按 <Key> 打包进 APK assets，
// 命名 = Key + (text 格式 ? ".list" : ".yaml")，全部为本地文件 provider。
var Providers = map[string]Provider{
	// --- ACL4SSR 文本清单（种子随包内置，type:file 只读本地）---
	"acl_banad":          {"acl_banad", "", "classical", "text", false},
	"acl_banprogramad":   {"acl_banprogramad", "", "classical", "text", false},
	
	
	"acl_chinadomain":    {"acl_chinadomain", "", "classical", "text", false},
	"acl_chinacompanyip": {"acl_chinacompanyip", "", "classical", "text", true},
	"acl_unban":          {"acl_unban", "", "classical", "text", false},
	"acl_steamcn":        {"acl_steamcn", "", "classical", "text", false},
	"acl_download":       {"acl_download", "", "classical", "text", false},
	"acl_chinamedia":     {"acl_chinamedia", "", "classical", "text", false},
	"acl_proxygfwlist":   {"acl_proxygfwlist", "", "classical", "text", false},
	"acl_proxymedia":     {"acl_proxymedia", "", "classical", "text", false},
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

// Table 内置分流组，顺序与默认开关对齐实机预设（2026-10-10 用户指定的
// 分组顺序与开启状态）：高频翻墙目标在前，系统服务居中，国内直连类在后，
// 拦截类垫底。顺序即规则匹配优先级，改动会影响命中结果，非纯展示调整。
// 「恶意软件」组缺失：其引用的 geosite:malware/phishing 类别仅存在于
// karing-ruleset 的 Iran 专用源，meta-rules-dat 无对应类别，暂不提供。
// 「Google FCM」「苹果推送通知」两个组已移除（2026-10-10）：排在 Google 与
// 苹果服务之后时，它们的域名与 IP 规则 100%/部分被前面的组提前命中，分组开关
// 设成什么出口都不生效（配置与实际行为不符）。Google 统一走「🌏 Google」，
// 苹果统一走「🍎 苹果服务」，不再单列。
// 「♊️ Google Gemini」「💬 Claude」「💬 OpenAI」三个组已合并为「🤖 AI」
// （2026-10-13），是上面同一类问题的第三例：旧 Gemini 组里带着
// GEOSITE,google 与 GEOSITE,google-play，排在「🌏 Google」「🌏 Google Play」
// 之前，把这两个组整体吃掉（那两个开关设成什么都不生效）。合并后只保留
// GEOSITE,category-ai-!cn + openai/anthropic/google-gemini，Google 相关域名
// 重新落回「🌏 Google」「🌏 Google Play」，两个开关恢复生效。
// 旧组名的用户设置由 legacyGroupAliases 迁移，见其注释。
// 「🌏 Google Play」已挪到「🌏 Google」之前（2026-10-13）：见该组上方的说明。
var Table = []RuleGroup{
	{Name: "📲 电报消息", DefaultOn: true, DefaultOut: "proxy",
		InlineRules: []string{"GEOSITE,telegram,{t}"}},
	{Name: "📹 油管视频", DefaultOn: true, DefaultOut: "proxy",
		InlineRules: []string{"GEOSITE,youtube,{t}"}},
	{Name: "🎥 奈飞视频", DefaultOn: true, DefaultOut: "proxy",
		InlineRules: []string{"GEOSITE,netflix,{t}"}},
	{Name: "📲 X", DefaultOn: true, DefaultOut: "proxy",
		InlineRules: []string{"GEOSITE,x,{t}"}},
	{Name: "🎧 TikTok", DefaultOn: true, DefaultOut: "proxy",
		InlineRules: []string{"GEOSITE,tiktok,{t}"}},
	{Name: "📸 Instagram", DefaultOn: true, DefaultOut: "proxy",
		InlineRules: []string{"GEOSITE,instagram,{t}"}},
	{Name: "📲 Facebook", DefaultOn: true, DefaultOut: "proxy",
		InlineRules: []string{"GEOSITE,facebook,{t}"}},
	{Name: "📲 WhatsApp", DefaultOn: true, DefaultOut: "proxy",
		InlineRules: []string{"GEOSITE,whatsapp,{t}"}},
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
		}},
	{Name: "🐱 GitHub", DefaultOn: true, DefaultOut: "proxy",
		InlineRules: []string{"GEOSITE,github,{t}"}},
	// 「🌏 Google Play」必须排在「🌏 Google」之前（顺序即优先级）：geosite 的
	// google-play 只有 10 条，其中 8 条（play.google.com、play-lh.googleusercontent.com、
	// play.googleapis.com …）同时也在 google 分类里，排在后面就是死开关——开关设成
	// 什么都不生效，与 2026-10-10 移除 Google FCM / 苹果推送、2026-10-13 合并 AI 组同因。
	// 挪到前面只会从 google 分类抢走 4 条 Play 自己的域名，两组 DefaultOut 都是 proxy，
	// 连通性零影响，只改归类归属（routing_decision_test.go 里钉死了这个顺序）。
	{Name: "🌏 Google Play", DefaultOn: true, DefaultOut: "proxy",
		InlineRules: []string{"GEOSITE,google-play,{t}"}},
	{Name: "🌏 Google", DefaultOn: true, DefaultOut: "proxy",
		InlineRules: []string{"GEOSITE,google,{t}"}},
	{Name: "Ⓜ️ 微软Bing", DefaultOn: true, DefaultOut: "proxy",
		InlineRules: []string{"GEOSITE,bing,{t}"}},
	// 微软云盘放在微软服务之前（顺序即优先级），但 geosite 的 onedrive 分类只有 19 条，
	// 真正传文件字节的内容主机 docs.live.net / content.live.net 不在里面，会漏到下一组
	// 「Ⓜ️ 微软服务」。那一组默认走代理，于是「微软云盘选直连」对这几个域名无效 ——
	// 实测（routing_decision_test.go 的 TestOnedriveCannotLeakToMicrosoftGroup）确认过。
	// 所以这里补四条内联域名兜底，保证云盘相关流量不会跑到别的组去。
	{Name: "Ⓜ️ 微软云盘", DefaultOn: true, DefaultOut: "direct",
		InlineRules: []string{
			"GEOSITE,onedrive,{t}",
			"DOMAIN-SUFFIX,docs.live.net,{t}",
			"DOMAIN-SUFFIX,content.live.net,{t}",
			"DOMAIN-SUFFIX,odc.officeapps.live.com,{t}",
			"DOMAIN-SUFFIX,officeapps.live.com,{t}",
		}},
	{Name: "Ⓜ️ 微软服务", DefaultOn: true, DefaultOut: "proxy",
		InlineRules: []string{"GEOSITE,microsoft,{t}"}},
	{Name: "🍎 苹果服务", DefaultOn: true, DefaultOut: "direct",
		InlineRules: []string{"GEOSITE,apple,{t}"}},
	{Name: "🎮 游戏平台", DefaultOn: true, DefaultOut: "proxy",
		InlineRules: []string{
			"GEOSITE,steam,{t}", "GEOSITE,epicgames,{t}", "GEOSITE,sony,{t}",
			"GEOSITE,nintendo,{t}", "GEOSITE,category-games,{t}"}},
	{Name: "📺 哔哩哔哩", DefaultOn: true, DefaultOut: "direct",
		InlineRules: []string{"GEOSITE,bilibili,{t}"}},
	{Name: "🎶 网易音乐", DefaultOn: true, DefaultOut: "direct",
		InlineRules: []string{"GEOSITE,netease,{t}"}},
	{Name: "🎯 国内直连", DefaultOn: true, DefaultOut: "direct",
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
		InlineRules: append(append([]string{
			// 抖音 / 字节跳动
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
			// 快手
			"DOMAIN-SUFFIX,kuaishou.com,{t}",
			"DOMAIN-SUFFIX,kuaishouzt.cn,{t}",
			"DOMAIN-SUFFIX,gifshow.com,{t}",
			"DOMAIN-SUFFIX,kwaicdn.com,{t}",
			"DOMAIN-SUFFIX,ksapisrv.com,{t}",
			// 小红书
			"DOMAIN-SUFFIX,xiaohongshu.com,{t}",
			"DOMAIN-SUFFIX,xhscdn.com,{t}",
			"DOMAIN-SUFFIX,xhslink.com,{t}",
			// 番茄小说
			"DOMAIN-SUFFIX,fanqienovel.com,{t}",
			"DOMAIN-SUFFIX,sigfanqie.com,{t}",
			// 微信 / QQ
			"DOMAIN-SUFFIX,weixin.qq.com,{t}",
			"DOMAIN-SUFFIX,qlogo.cn,{t}",
			"DOMAIN-SUFFIX,qpic.cn,{t}",
			"DOMAIN-SUFFIX,gtimg.com,{t}",
			"DOMAIN-SUFFIX,myqcloud.com,{t}",
			// 淘宝 / 阿里
			"DOMAIN-SUFFIX,taobaocdn.com,{t}",
			"DOMAIN-SUFFIX,alipayobjects.com,{t}",
			"DOMAIN-SUFFIX,aliyuncs.com,{t}",
			"DOMAIN-SUFFIX,aliyun.com.cn,{t}",
			"DOMAIN-SUFFIX,dingtalk.com,{t}",
			"DOMAIN-SUFFIX,alibaba-inc.com,{t}",
			// 京东
			"DOMAIN-SUFFIX,jd.hk,{t}",
			"DOMAIN-SUFFIX,jdcloud.com,{t}",
			"DOMAIN-SUFFIX,360buyimg.com,{t}",
			// 百度
			"DOMAIN-SUFFIX,baidubce.com,{t}",
			"DOMAIN-SUFFIX,bdimg.com,{t}",
			"DOMAIN-SUFFIX,baidupcs.com,{t}",
			// 网易
			"DOMAIN-SUFFIX,netease.im,{t}",
			"DOMAIN-SUFFIX,126.com,{t}",
			"DOMAIN-SUFFIX,126.net,{t}",
			// 微博 / 新浪
			"DOMAIN-SUFFIX,sina.com,{t}",
			"DOMAIN-SUFFIX,sina.cn,{t}",
			"DOMAIN-SUFFIX,weibo.com.cn,{t}",
			"DOMAIN-SUFFIX,sinaimg.com,{t}",
			"DOMAIN-SUFFIX,sinaedge.com,{t}",
			// 美团 / 点评
			"DOMAIN-SUFFIX,meituan.com,{t}",
			"DOMAIN-SUFFIX,dpfile.com,{t}",
			// 拼多多
			"DOMAIN-SUFFIX,yangkeduo.com,{t}",
			// 滴滴
			"DOMAIN-SUFFIX,didichuxing.com,{t}",
			"DOMAIN-SUFFIX,didistatic.com,{t}",
			// 华为 / 小米 / OPPO / vivo
			"DOMAIN-SUFFIX,vmall.com,{t}",
			"DOMAIN-SUFFIX,oppo.com,{t}",
			"DOMAIN-SUFFIX,opposhop.cn,{t}",
			"DOMAIN-SUFFIX,vivo.com.cn,{t}",
			// 其他常见国内站点
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
		}, "GEOSITE,cn,{t}"), "GEOIP,CN,{t},no-resolve"),
		Providers: []string{"acl_chinadomain", "acl_chinacompanyip", "acl_unban", "acl_steamcn", "acl_download", "acl_chinamedia"}},
	{Name: "🌏 国外穿墙", DefaultOn: true, DefaultOut: "proxy",
		InlineRules: []string{"GEOSITE,geolocation-!cn,{t}"},
		Providers:   []string{"acl_proxygfwlist", "acl_proxymedia"}},
	{Name: "🍃 应用净化", DefaultOn: false, DefaultOut: "block",
		Providers: []string{"acl_banprogramad"}},
	{Name: "🛑 广告拦截", DefaultOn: false, DefaultOut: "block",
		InlineRules: []string{"GEOSITE,category-ads-all,{t}"},
		Providers:   []string{"acl_banad"}},
}

// AIGroupName 合并后的 AI 组名（唯一真源；App 侧 RoutingGroups.kt 必须逐字一致）。
const AIGroupName = "🤖 AI"

// legacyGroupAliases 已改名的历史分流组名 → 当前组名。
//
// 2026-10-13 把「♊️ Google Gemini」「💬 Claude」「💬 OpenAI」合并成「🤖 AI」。
// 用户已保存的 routing.json 里 groups / order 仍是旧名：直接当未知名丢弃的话，
// 用户自己调过的开关会退回表内默认值、拖动顺序会退回内置顺序——静默丢设置。
// 所以读取侧统一映射：任一旧名为开 → 新组为开；顺序取三者中最靠前的位置。
var legacyGroupAliases = map[string]string{
	"♊️ Google Gemini": AIGroupName,
	"💬 Claude":         AIGroupName,
	"💬 OpenAI":         AIGroupName,
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