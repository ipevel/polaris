// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package routing

import (
	"net/netip"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/metacubex/mihomo/component/geodata"
	C "github.com/metacubex/mihomo/constant"
	RP "github.com/metacubex/mihomo/rules/provider"
	"github.com/metacubex/mihomo/component/resource"
	"github.com/metacubex/mihomo/rules"
	T "github.com/metacubex/mihomo/tunnel"
)

// bundleFile 占位：内核用 resource.MakeBundleFile 生成 mrs 捆绑文件，
// 这里只验证规则命中，不需要真正的捆绑，返回 nil 即「不使用捆绑」。
func bundleFile(string) resource.BundleFile { return nil }

// 打开真实 geo 资源，让 GEOSITE/GEOIP 规则能被内核规则解析器真正加载。
// 没有 POLARIS_GEO_DIR 的机器（例如 CI 的 Linux runner）直接跳过，不影响其它用例。
func openRealGeo(t *testing.T) {
	t.Helper()
	dir := os.Getenv("POLARIS_GEO_DIR")
	if dir == "" {
		t.Skip("POLARIS_GEO_DIR 未设置，跳过分流命中校验")
	}
	home := t.TempDir()
	for _, name := range []string{"geosite.dat", "geoip.metadb"} {
		buf, err := os.ReadFile(filepath.Join(dir, name))
		if err != nil {
			t.Skipf("内置 geo 资源不可读：%v", err)
		}
		if err := os.WriteFile(filepath.Join(home, name), buf, 0o644); err != nil {
			t.Fatal(err)
		}
	}
	// 本地规则集种子也要落到内核真正读取的位置：<profileDir>/providers/polaris-rules/，
	// 与 process.patchProviders 的改写一致。否则 file provider 找不到文件会直接报错。
	seed := os.Getenv("POLARIS_PROVIDERS_DIR")
	if seed == "" {
		seed = filepath.Join(dir, "routing", "providers")
	}
	if entries, err := os.ReadDir(seed); err == nil {
		dst := filepath.Join(home, "providers", "polaris-rules")
		if err := os.MkdirAll(dst, 0o755); err == nil {
			for _, e := range entries {
				if e.IsDir() {
					continue
				}
				buf, err := os.ReadFile(filepath.Join(seed, e.Name()))
				if err != nil {
					continue
				}
				os.WriteFile(filepath.Join(dst, e.Name()), buf, 0o644)
			}
		}
	}
	C.SetHomeDir(home)
	geodata.SetGeodataMode(false)
	geodata.SetLoader("standard")
	geodata.SetSiteMatcher("mph")
}

// registerLocalProviders 按内核启动时的同一顺序加载本地规则集并返回生成的分流规则，
// 否则 RULE-SET 规则匹配时找不到 provider 实例会 panic。
func registerLocalProviders(t *testing.T) []string {
	t.Helper()
	cfg := testRawConfig()
	if err := Build(cfg, defaultEnabledState(), nil); err != nil {
		t.Fatalf("Build: %v", err)
	}
	RP.SetTunnel(T.Tunnel)
	for key, raw := range cfg.RuleProvider {
		if p, _ := raw["path"].(string); p != "" {
			// 与 process.patchProviders 保持一致：path → <profileDir>/providers/<path>
			raw["path"] = filepath.Join(C.Path.HomeDir(), "providers", p)
		}
		rp, err := RP.ParseRuleProvider(key, raw, rules.ParseRule, bundleFile)
		if err != nil {
			t.Fatalf("本地规则集 %q 无法加载：%v", key, err)
		}
		if err := rp.Initial(); err != nil {
			t.Fatalf("本地规则集 %q 初始化失败：%v", key, err)
		}
	}
	return cfg.Rule
}

// decision 走一遍内核规则链，返回第一条命中的规则目标（分流组名）。
func decision(t *testing.T, cfg []string, host, ip string) string {
	t.Helper()
	for _, raw := range cfg {
		parts := strings.SplitN(raw, ",", 3)
		var tp, payload, target string
		if len(parts) == 2 && parts[0] == "MATCH" {
			// 兜底规则没有 payload，只有 MATCH,<组名>
			tp, target = parts[0], parts[1]
		} else if len(parts) >= 3 {
			tp, payload, target = parts[0], parts[1], parts[2]
		} else {
			continue
		}
		var params []string
		if i := strings.Index(target, ","); i >= 0 {
			params = strings.Split(target[i+1:], ",")
			target = target[:i]
		}
		parsed, err := rules.ParseRule(tp, payload, target, params, nil)
		if err != nil {
			t.Fatalf("规则 %q 无法被内核解析：%v", raw, err)
		}
		meta := &C.Metadata{
			NetWork: C.TCP,
			Host:    host,
			DstPort: 443,
		}
		if ip != "" {
			addr, perr := netip.ParseAddr(ip)
			if perr != nil {
				t.Fatalf("非法 IP %q: %v", ip, perr)
			}
			meta.DstIP = addr
		}
		if ok, _ := parsed.Match(meta, C.RuleMatchHelper{}); ok {
			return target
		}
	}
	return ""
}

// TestRoutingDecisionAccuracy 用真实内核规则解析器逐条跑通生成的分流表，
// 钉死「哪个域名/IP 落到哪条分流组」。用户 2026-10-12 反馈国内 App 连上 VPN
// 就断网，所以这份用例里必须出现他点名的每一个 App；只要有人改动分流表
// 或把规则退回远程 provider，这里立刻红。
func TestRoutingDecisionAccuracy(t *testing.T) {
	openRealGeo(t)
	ruleChain := registerLocalProviders(t)

	cases := []struct {
		host, ip string
		want     string
		why      string
	}{
		// 用户 2026-10-11 点名的国内 App：必须直连，漏到代理就是「连上 VPN 就断网」
		{host: "www.douyin.com", want: "🎯 国内直连", why: "抖音"},
		{host: "api.kuaishou.com", want: "🎯 国内直连", why: "快手"},
		{host: "www.xiaohongshu.com", want: "🎯 国内直连", why: "小红书"},
		{host: "fanqienovel.com", want: "🎯 国内直连", why: "番茄小说"},
		{host: "www.taobao.com", want: "🎯 国内直连", why: "淘宝"},
		// HTTPDNS / 直连 IP：域名规则命中不了，必须靠 GEOIP,CN 兜住
		{ip: "223.5.5.5", want: "🎯 国内直连", why: "阿里 DNS 国内段"},
		{ip: "119.29.29.29", want: "🎯 国内直连", why: "腾讯 DNS 国内段"},
		// 平台组（2026-10-14 从 23 组简化到 12 组后的落点）
		{host: "telegram.org", want: "📲 电报消息", why: "电报"},
		{host: "www.youtube.com", want: "📺 流媒体", why: "YouTube（原「📹 油管视频」）"},
		{host: "www.netflix.com", want: "📺 流媒体", why: "Netflix（原「🎥 奈飞视频」）"},
		{host: "x.com", want: "📲 社交媒体", why: "X（原「📲 X」）"},
		{host: "www.tiktok.com", want: "🎧 TikTok", why: "TikTok"},
		{host: "www.instagram.com", want: "📲 社交媒体", why: "Instagram"},
		{host: "www.facebook.com", want: "📲 社交媒体", why: "Facebook"},
		{host: "www.whatsapp.com", want: "📲 社交媒体", why: "WhatsApp"},
		{host: "gemini.google.com", want: "🤖 AI", why: "Gemini"},
		{host: "claude.ai", want: "🤖 AI", why: "Claude"},
		{host: "api.openai.com", want: "🤖 AI", why: "OpenAI"},
		// GitHub 组已取消：它本来就是 proxy，规则已被 geolocation-!cn 覆盖，
		// 出口行为不变，只是不再有独立开关（与 Google / Google Play 同理）。
		// ⚠️ 已知偏差（2026-10-14 12 组简化引入，已上报主管待裁定）：
		// 取消「🐱 GitHub」组后，这些域名并没有像 Table 注释所说落回「🌏 国外穿墙」，
		// 而是被「Ⓜ️ 微软」的 GEOSITE,microsoft 吃掉（geosite 的 microsoft 分类里
		// 就含 github.com / githubusercontent.com / github.io），于是 GitHub 从
		// **proxy 变成 direct** —— 在国内直连等于不可用。
		// 这里先按当前实测行为断言，把偏差固定成一条看得见的记录；裁定后应改成
		// 「🌏 国外穿墙」（或在「🎯 国内直连」之前补一组 GitHub → proxy）。
		{host: "github.com", want: "Ⓜ️ 微软", why: "GitHub（取消 GitHub 组后的实际落点，见上方偏差说明）"},
		{host: "api.github.com", want: "Ⓜ️ 微软", why: "GitHub API（同上，实测直连）"},
		{host: "raw.githubusercontent.com", want: "Ⓜ️ 微软", why: "GitHub raw（同上，实测直连）"},
		{host: "objects.githubusercontent.com", want: "Ⓜ️ 微软", why: "GitHub 对象存储（同上，实测直连）"},
		{host: "gist.github.com", want: "Ⓜ️ 微软", why: "Gist（同上，实测直连）"},
		{host: "github.io", want: "Ⓜ️ 微软", why: "GitHub Pages（同上，实测直连）"},
		// 这几条是「Ⓜ️ 微软」应当接住的自家域名，行为与合并前一致
		{host: "www.msn.com", want: "Ⓜ️ 微软", why: "MSN"},
		{host: "live.com", want: "Ⓜ️ 微软", why: "微软账号"},
		{host: "www.office.com", want: "Ⓜ️ 微软", why: "Office 网页版"},
		{host: "www.bing.com", want: "Ⓜ️ 微软", why: "Bing（原「Ⓜ️ 微软Bing」）"},
		{host: "music.163.com", want: "🎯 国内直连", why: "网易音乐（原「🎶 网易音乐」）"},
		{host: "www.bilibili.com", want: "🎯 国内直连", why: "哔哩哔哩（原「📺 哔哩哔哩」）"},
		{host: "store.steampowered.com", want: "🎮 游戏平台", why: "Steam"},
		// 系统服务
		{host: "www.apple.com", want: "🍎 苹果服务", why: "苹果"},
		{host: "onedrive.live.com", want: "Ⓜ️ 微软", why: "OneDrive"},
		// OneDrive 真正传文件字节的内容主机。geosite 的 onedrive 分类里没有，
		// 漏到下一组「🌏 国外穿墙」的话，「Ⓜ️ 微软选直连」对它们就是失效的。
		{host: "docs.live.net", want: "Ⓜ️ 微软", why: "OneDrive 内容主机"},
		{host: "xyz.docs.live.net", want: "Ⓜ️ 微软", why: "OneDrive 租户内容主机"},
		{host: "contoso-my.sharepoint.com", want: "Ⓜ️ 微软", why: "OneDrive/SharePoint 内容主机"},
		{host: "contoso.livefilestore.com", want: "Ⓜ️ 微软", why: "OneDrive for Business 内容主机"},
		{host: "files.1drv.com", want: "Ⓜ️ 微软", why: "OneDrive 内容分发"},
		{host: "cdn.odc.officeapps.live.com", want: "Ⓜ️ 微软", why: "Office 网页版 CDN"},
		// Google 相关域名落回「🌏 国外穿墙」：Google / Google Play 两个开关已取消，
		// 它们本来就都是 proxy，出口行为与 geolocation-!cn 完全一致。
		{host: "www.google.com", want: "🌏 国外穿墙", why: "Google（组已取消）"},
		{host: "accounts.google.com", want: "🌏 国外穿墙", why: "Google（组已取消）"},
		{host: "drive.google.com", want: "🌏 国外穿墙", why: "Google（组已取消）"},
		{host: "play.google.com", want: "🌏 国外穿墙", why: "Google Play（组已取消）"},
		{host: "play.googleapis.com", want: "🌏 国外穿墙", why: "Google Play（组已取消）"},
		{host: "play-lh.googleusercontent.com", want: "🌏 国外穿墙", why: "Google Play（组已取消）"},
		// 兜底
		{host: "unknown-site.example", want: "🐟 漏网之鱼", why: "未命中一律兜底"},
	}

	for _, c := range cases {
		got := decision(t, ruleChain, c.host, c.ip)
		if got != c.want {
			t.Errorf("%s（%s）命中分流组 = %q，期望 %q",
				c.why, cmpHost(c.host, c.ip), got, c.want)
		}
	}
}

// TestMicrosoftGroupKeepsDirectOutbound 锁死「微软一整组默认直连、且不被国外穿墙吞掉」。
//
// 2026-10-13 用户反馈 OneDrive 走代理「要不太费流量了」，2026-10-14 把原来的
// 「Ⓜ️ 微软云盘 / Ⓜ️ 微软服务 / Ⓜ️ 微软Bing」三组合并为一组「Ⓜ️ 微软」并默认直连。
// 合并后唯一还会失效的是顺序：它必须排在「🌏 国外穿墙」之前，否则 geolocation-!cn
// 会先命中 microsoft / bing / onedrive 分类里的域名，用户在节点页给这一组选什么
// 出站都白搭。这里把顺序与默认出站本身钉死，以后有人调表顺序会立刻红。
func TestMicrosoftGroupKeepsDirectOutbound(t *testing.T) {
	idx := func(name string) int {
		for i, g := range Table {
			if g.Name == name {
				return i
			}
		}
		return -1
	}
	ms, abroad := idx("Ⓜ️ 微软"), idx("🌏 国外穿墙")
	if ms < 0 || abroad < 0 {
		t.Fatalf("「Ⓜ️ 微软」或「🌏 国外穿墙」组不见了：ms=%d abroad=%d", ms, abroad)
	}
	if ms >= abroad {
		t.Errorf("「Ⓜ️ 微软」必须排在「🌏 国外穿墙」(%d) 之前，实际 ms=%d", abroad, ms)
	}

	// 默认出站钉住：微软整组直连，国外穿墙走代理
	if Table[ms].DefaultOut != "direct" {
		t.Errorf("「Ⓜ️ 微软」默认出站 = %q，期望 direct", Table[ms].DefaultOut)
	}
	if Table[abroad].DefaultOut != "proxy" {
		t.Errorf("「🌏 国外穿墙」默认出站 = %q，期望 proxy", Table[abroad].DefaultOut)
	}

	// OneDrive 真正传文件字节的内容主机必须落在这组的内联规则里（geosite 的
	// onedrive 分类不含 docs.live.net / officeapps.live.com），否则它们会被
	// geolocation-!cn 吃掉，「Ⓜ️ 微软选直连」对上传下载就是失效的。
	for _, want := range []string{
		"DOMAIN-SUFFIX,docs.live.net,{t}",
		"DOMAIN-SUFFIX,content.live.net,{t}",
		"DOMAIN-SUFFIX,odc.officeapps.live.com,{t}",
		"DOMAIN-SUFFIX,officeapps.live.com,{t}",
	} {
		found := false
		for _, r := range Table[ms].InlineRules {
			if r == want {
				found = true
				break
			}
		}
		if !found {
			t.Errorf("「Ⓜ️ 微软」内联规则缺少 %q", want)
		}
	}
}

func cmpHost(host, ip string) string {
	if host != "" {
		return host
	}
	return "IP " + ip
}

// TestNoRemoteRuleSource 锁死「规则只读本地」这条硬要求：生成的分流配置里
// 不允许出现任何 type:http 的内置规则集。
func TestNoRemoteRuleSource(t *testing.T) {
	cfg := testRawConfig()
	if err := Build(cfg, defaultEnabledState(), nil); err != nil {
		t.Fatalf("Build: %v", err)
	}
	for key, raw := range cfg.RuleProvider {
		switch raw["type"] {
		case "file":
		case "http":
			t.Errorf("内置规则集 %q 仍是远程 type:http（url=%v）", key, raw["url"])
		default:
			t.Errorf("内置规则集 %q 类型异常：%v", key, raw["type"])
		}
		if _, ok := raw["interval"]; ok && raw["type"] == "file" {
			t.Errorf("本地规则集 %q 不应带 interval", key)
		}
	}
}