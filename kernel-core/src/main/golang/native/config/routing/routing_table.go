// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package routing

// 内置分流表：组名、顺序、默认开关与出站语义全部对齐 Karing 预设
// （KaringX/karing assets/datas/preset/cn.json），规则数据源沿用其数据谱系：
//   - geosite/geoip 类别 → MetaCubeX/meta-rules-dat@meta（karing-ruleset 的 geo
//     数据同源上游，KaringX/meta-rules-dat README 指定的 mihomo 格式获取渠道）
//   - ACL4SSR 清单 → ACL4SSR/ACL4SSR@master Clash 目录（karing-ruleset workflow
//     每日转换 srs 的同一来源）
// karing-ruleset 本体仅发布 sing-box .srs 格式，mihomo 无法读取，因此按数据谱系
// 而非仓库本体引用；两路上游均每日自动更新，provider interval 与其对齐（24h）。

const (
	providerBaseGeo  = "https://fastly.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@meta/geo/"
	providerBaseACL  = "https://fastly.jsdelivr.net/gh/ACL4SSR/ACL4SSR@master/Clash/"
	providerInterval = 86400 // 秒；与 karing-ruleset 每日构建节奏一致
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
type Provider struct {
	Key       string // 稳定 ASCII 键：RULE-SET 引用名 + 预播种文件名
	URL       string
	Behavior  string // domain | ipcidr | classical
	Format    string // yaml | text
	NoResolve bool   // IP 类规则集附加 no-resolve
}

// RuleGroup 一条内置分流规则组（对应 Karing 预设的一行）。
type RuleGroup struct {
	Name        string   // 组名（与 Karing 预设一致）
	DefaultOn   bool     // Karing 预设的 switch 默认值
	DefaultOut  string   // Karing 预设的 outbound：proxy | direct | block
	Providers   []string // 规则集键（按 Karing 预设的 rule_set_build_in 顺序）
	InlineRules []string // 内联规则，{t} 替换为本组组名
}

// Providers 全部内置规则集。种子文件按 <Key> 打包进 APK assets，
// 命名 = Key + (text 格式 ? ".list" : ".yaml")。
var Providers = map[string]Provider{
	// --- geosite（meta-rules-dat，payload 裸域名 → behavior: domain）---
	"gs_category_ads_all": {"gs_category_ads_all", providerBaseGeo + "geosite/category-ads-all.yaml", "domain", "yaml", false},
	"gs_apple":            {"gs_apple", providerBaseGeo + "geosite/apple.yaml", "domain", "yaml", false},
	"gs_youtube":          {"gs_youtube", providerBaseGeo + "geosite/youtube.yaml", "domain", "yaml", false},
	"gs_google_play":      {"gs_google_play", providerBaseGeo + "geosite/google-play.yaml", "domain", "yaml", false},
	"gs_google":           {"gs_google", providerBaseGeo + "geosite/google.yaml", "domain", "yaml", false},
	"gs_facebook":         {"gs_facebook", providerBaseGeo + "geosite/facebook.yaml", "domain", "yaml", false},
	"gs_x":                {"gs_x", providerBaseGeo + "geosite/x.yaml", "domain", "yaml", false},
	"gs_tiktok":           {"gs_tiktok", providerBaseGeo + "geosite/tiktok.yaml", "domain", "yaml", false},
	"gs_instagram":        {"gs_instagram", providerBaseGeo + "geosite/instagram.yaml", "domain", "yaml", false},
	"gs_netflix":          {"gs_netflix", providerBaseGeo + "geosite/netflix.yaml", "domain", "yaml", false},
	"gs_whatsapp":         {"gs_whatsapp", providerBaseGeo + "geosite/whatsapp.yaml", "domain", "yaml", false},
	"gs_telegram":         {"gs_telegram", providerBaseGeo + "geosite/telegram.yaml", "domain", "yaml", false},
	"gs_openai":           {"gs_openai", providerBaseGeo + "geosite/openai.yaml", "domain", "yaml", false},
	"gs_github":           {"gs_github", providerBaseGeo + "geosite/github.yaml", "domain", "yaml", false},
	"gs_bing":             {"gs_bing", providerBaseGeo + "geosite/bing.yaml", "domain", "yaml", false},
	"gs_onedrive":         {"gs_onedrive", providerBaseGeo + "geosite/onedrive.yaml", "domain", "yaml", false},
	"gs_microsoft":        {"gs_microsoft", providerBaseGeo + "geosite/microsoft.yaml", "domain", "yaml", false},
	"gs_geolocation_ncn":  {"gs_geolocation_ncn", providerBaseGeo + "geosite/geolocation-%21cn.yaml", "domain", "yaml", false},
	// --- geoip（payload 裸 CIDR → behavior: ipcidr，全部 no-resolve）---
	"gp_google":   {"gp_google", providerBaseGeo + "geoip/google.yaml", "ipcidr", "yaml", true},
	"gp_facebook": {"gp_facebook", providerBaseGeo + "geoip/facebook.yaml", "ipcidr", "yaml", true},
	"gp_twitter":  {"gp_twitter", providerBaseGeo + "geoip/twitter.yaml", "ipcidr", "yaml", true},
	"gp_netflix":  {"gp_netflix", providerBaseGeo + "geoip/netflix.yaml", "ipcidr", "yaml", true},
	"gp_telegram": {"gp_telegram", providerBaseGeo + "geoip/telegram.yaml", "ipcidr", "yaml", true},
	"gp_cn":       {"gp_cn", providerBaseGeo + "geoip/cn.yaml", "ipcidr", "yaml", true},
	// --- ACL4SSR（Clash 文本规则 → behavior: classical, format: text）---
	"acl_banad":          {"acl_banad", providerBaseACL + "BanAD.list", "classical", "text", false},
	"acl_banprogramad":   {"acl_banprogramad", providerBaseACL + "BanProgramAD.list", "classical", "text", false},
	"acl_gemini":         {"acl_gemini", providerBaseACL + "Ruleset/Gemini.list", "classical", "text", false},
	"acl_googlefcm":      {"acl_googlefcm", providerBaseACL + "Ruleset/GoogleFCM.list", "classical", "text", false},
	"acl_facebook":       {"acl_facebook", providerBaseACL + "Ruleset/Facebook.list", "classical", "text", false},
	"acl_twitter":        {"acl_twitter", providerBaseACL + "Ruleset/Twitter.list", "classical", "text", false},
	"acl_whatsapp":       {"acl_whatsapp", providerBaseACL + "Ruleset/Whatsapp.list", "classical", "text", false},
	"acl_claude":         {"acl_claude", providerBaseACL + "Ruleset/Claude.list", "classical", "text", false},
	"acl_steam":          {"acl_steam", providerBaseACL + "Ruleset/Steam.list", "classical", "text", false},
	"acl_epic":           {"acl_epic", providerBaseACL + "Ruleset/Epic.list", "classical", "text", false},
	"acl_origin":         {"acl_origin", providerBaseACL + "Ruleset/Origin.list", "classical", "text", false},
	"acl_sony":           {"acl_sony", providerBaseACL + "Ruleset/Sony.list", "classical", "text", false},
	"acl_nintendo":       {"acl_nintendo", providerBaseACL + "Ruleset/Nintendo.list", "classical", "text", false},
	"acl_bilibili":       {"acl_bilibili", providerBaseACL + "Ruleset/Bilibili.list", "classical", "text", false},
	"acl_bilibilihmt":    {"acl_bilibilihmt", providerBaseACL + "Ruleset/BilibiliHMT.list", "classical", "text", false},
	"acl_neteasemusic":   {"acl_neteasemusic", providerBaseACL + "Ruleset/NetEaseMusic.list", "classical", "text", false},
	"acl_chinadomain":    {"acl_chinadomain", providerBaseACL + "ChinaDomain.list", "classical", "text", false},
	"acl_chinacompanyip": {"acl_chinacompanyip", providerBaseACL + "ChinaCompanyIp.list", "classical", "text", true},
	"acl_unban":          {"acl_unban", providerBaseACL + "UnBan.list", "classical", "text", false},
	"acl_steamcn":        {"acl_steamcn", providerBaseACL + "Ruleset/SteamCN.list", "classical", "text", false},
	"acl_download":       {"acl_download", providerBaseACL + "Download.list", "classical", "text", false},
	"acl_chinamedia":     {"acl_chinamedia", providerBaseACL + "ChinaMedia.list", "classical", "text", false},
	"acl_proxygfwlist":   {"acl_proxygfwlist", providerBaseACL + "ProxyGFWlist.list", "classical", "text", false},
	"acl_proxymedia":     {"acl_proxymedia", providerBaseACL + "ProxyMedia.list", "classical", "text", false},
}

// 苹果推送内联规则：与 Karing 预设 cn.json 的「📢 苹果推送通知」逐条一致。
var applePushInlineRules = []string{
	"DOMAIN-SUFFIX,push.apple.com,{t}",
	"DOMAIN-SUFFIX,akadns.net,{t}",
	"DOMAIN-KEYWORD,apple.com.edgekey.net,{t}",
	"IP-CIDR,17.249.0.0/16,{t},no-resolve",
	"IP-CIDR,17.252.0.0/16,{t},no-resolve",
	"IP-CIDR,17.57.144.0/22,{t},no-resolve",
	"IP-CIDR,17.188.128.0/18,{t},no-resolve",
	"IP-CIDR,17.188.20.0/23,{t},no-resolve",
	"IP-CIDR6,2620:149:a44::/48,{t},no-resolve",
	"IP-CIDR6,2403:300:a42::/48,{t},no-resolve",
	"IP-CIDR6,2403:300:a51::/48,{t},no-resolve",
	"IP-CIDR6,2a01:b740:a42::/48,{t},no-resolve",
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

// Table 内置分流组，顺序/默认值对齐 Karing 预设 cn.json。
// 「恶意软件」组缺失：其引用的 geosite:malware/phishing 类别仅存在于
// karing-ruleset 的 Iran 专用源，meta-rules-dat 无对应类别，暂不提供。
var Table = []RuleGroup{
	{Name: "🛑 广告拦截", DefaultOn: false, DefaultOut: "block",
		Providers: []string{"gs_category_ads_all", "acl_banad"}},
	{Name: "🍃 应用净化", DefaultOn: false, DefaultOut: "block",
		Providers: []string{"acl_banprogramad"}},
	{Name: "📢 苹果推送通知", DefaultOn: false, DefaultOut: "proxy",
		InlineRules: applePushInlineRules},
	{Name: "🍎 苹果服务", DefaultOn: true, DefaultOut: "direct",
		Providers: []string{"gs_apple"}},
	{Name: "📹 油管视频", DefaultOn: false, DefaultOut: "proxy",
		Providers: []string{"gs_youtube"}},
	{Name: "♊️ Google Gemini", DefaultOn: false, DefaultOut: "proxy",
		Providers: []string{"acl_gemini"}},
	{Name: "🌏 Google Play", DefaultOn: true, DefaultOut: "proxy",
		Providers: []string{"gs_google_play"}},
	{Name: "📢 Google FCM", DefaultOn: false, DefaultOut: "direct",
		Providers: []string{"acl_googlefcm"}},
	{Name: "🌏 Google", DefaultOn: true, DefaultOut: "proxy",
		Providers: []string{"gs_google", "gp_google"}},
	{Name: "📲 Facebook", DefaultOn: false, DefaultOut: "proxy",
		Providers: []string{"gs_facebook", "gp_facebook", "acl_facebook"}},
	{Name: "📲 X", DefaultOn: false, DefaultOut: "proxy",
		Providers: []string{"gs_x", "gp_twitter", "acl_twitter"}},
	{Name: "🎧 TikTok", DefaultOn: false, DefaultOut: "proxy",
		Providers: []string{"gs_tiktok"}},
	{Name: "📸 Instagram", DefaultOn: false, DefaultOut: "proxy",
		Providers: []string{"gs_instagram"}},
	{Name: "🎥 奈飞视频", DefaultOn: false, DefaultOut: "proxy",
		Providers: []string{"gs_netflix", "gp_netflix"}},
	{Name: "📲 WhatsApp", DefaultOn: false, DefaultOut: "proxy",
		Providers: []string{"gs_whatsapp", "acl_whatsapp"}},
	{Name: "📲 电报消息", DefaultOn: false, DefaultOut: "proxy",
		Providers: []string{"gs_telegram", "gp_telegram"}},
	{Name: "💬 Claude", DefaultOn: false, DefaultOut: "proxy",
		Providers: []string{"acl_claude"}},
	{Name: "💬 OpenAI", DefaultOn: false, DefaultOut: "proxy",
		Providers: []string{"gs_openai"}},
	{Name: "🐱 GitHub", DefaultOn: false, DefaultOut: "proxy",
		Providers: []string{"gs_github"}},
	{Name: "Ⓜ️ 微软Bing", DefaultOn: false, DefaultOut: "proxy",
		Providers: []string{"gs_bing"}},
	{Name: "Ⓜ️ 微软云盘", DefaultOn: false, DefaultOut: "direct",
		Providers: []string{"gs_onedrive"}},
	{Name: "Ⓜ️ 微软服务", DefaultOn: false, DefaultOut: "proxy",
		Providers: []string{"gs_microsoft"}},
	{Name: "🎮 游戏平台", DefaultOn: false, DefaultOut: "proxy",
		Providers: []string{"acl_steam", "acl_epic", "acl_origin", "acl_sony", "acl_nintendo"}},
	{Name: "📺 哔哩哔哩", DefaultOn: true, DefaultOut: "direct",
		Providers: []string{"acl_bilibilihmt", "acl_bilibili"}},
	{Name: "🎶 网易音乐", DefaultOn: false, DefaultOut: "direct",
		Providers: []string{"acl_neteasemusic"}},
	{Name: "🎯 国内直连", DefaultOn: true, DefaultOut: "direct",
		Providers: []string{"gp_cn", "acl_chinadomain", "acl_chinacompanyip", "acl_unban", "acl_steamcn", "acl_download", "acl_chinamedia"}},
	{Name: "🌏 国外穿墙", DefaultOn: true, DefaultOut: "proxy",
		Providers: []string{"gs_geolocation_ncn", "acl_proxygfwlist", "acl_proxymedia"}},
}
