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
		// 平台组
		{host: "telegram.org", want: "📲 电报消息", why: "电报"},
		{host: "www.youtube.com", want: "📹 油管视频", why: "YouTube"},
		{host: "www.netflix.com", want: "🎥 奈飞视频", why: "Netflix"},
		{host: "x.com", want: "📲 X", why: "X"},
		{host: "www.tiktok.com", want: "🎧 TikTok", why: "TikTok"},
		{host: "www.instagram.com", want: "📸 Instagram", why: "Instagram"},
		{host: "www.facebook.com", want: "📲 Facebook", why: "Facebook"},
		{host: "www.whatsapp.com", want: "📲 WhatsApp", why: "WhatsApp"},
		{host: "gemini.google.com", want: "🤖 AI", why: "Gemini"},
		{host: "claude.ai", want: "🤖 AI", why: "Claude"},
		{host: "api.openai.com", want: "🤖 AI", why: "OpenAI"},
		{host: "github.com", want: "🐱 GitHub", why: "GitHub"},
		{host: "www.bing.com", want: "Ⓜ️ 微软Bing", why: "Bing"},
		{host: "music.163.com", want: "🎶 网易音乐", why: "网易音乐"},
		{host: "www.bilibili.com", want: "📺 哔哩哔哩", why: "哔哩哔哩"},
		{host: "store.steampowered.com", want: "🎮 游戏平台", why: "Steam"},
		// 系统服务
		{host: "www.apple.com", want: "🍎 苹果服务", why: "苹果"},
		{host: "onedrive.live.com", want: "Ⓜ️ 微软云盘", why: "OneDrive"},
		// OneDrive 真正传文件字节的内容主机。geosite 的 onedrive 分类里没有，
		// 漏到下一组「Ⓜ️ 微软服务」的话，「微软云盘选直连」对它们就是失效的。
		{host: "docs.live.net", want: "Ⓜ️ 微软云盘", why: "OneDrive 内容主机"},
		{host: "xyz.docs.live.net", want: "Ⓜ️ 微软云盘", why: "OneDrive 租户内容主机"},
		{host: "contoso-my.sharepoint.com", want: "Ⓜ️ 微软云盘", why: "OneDrive/SharePoint 内容主机"},
		{host: "contoso.livefilestore.com", want: "Ⓜ️ 微软云盘", why: "OneDrive for Business 内容主机"},
		{host: "files.1drv.com", want: "Ⓜ️ 微软云盘", why: "OneDrive 内容分发"},
		{host: "cdn.odc.officeapps.live.com", want: "Ⓜ️ 微软云盘", why: "Office 网页版 CDN"},
		// Google 相关域名必须落回「🌏 Google」：合并前 Gemini 组带着
		// GEOSITE,google 排在前面，把这一组整体吃掉（开关设成什么都不生效）。
		{host: "www.google.com", want: "🌏 Google", why: "Google 组恢复生效（2026-10-13 合并 AI 组）"},
		{host: "accounts.google.com", want: "🌏 Google", why: "Google 组恢复生效"},
		{host: "drive.google.com", want: "🌏 Google", why: "Google 组恢复生效"},
		// Play 域名必须先落「🌏 Google Play」：它排在「🌏 Google」之后时，
		// google-play 那 10 条里有 8 条会被 google 分类先命中，开关设成什么都不生效。
		{host: "play.google.com", want: "🌏 Google Play", why: "Google Play 组恢复生效"},
		{host: "play.googleapis.com", want: "🌏 Google Play", why: "Google Play 组恢复生效"},
		{host: "play-lh.googleusercontent.com", want: "🌏 Google Play", why: "Google Play 组恢复生效"},
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

// TestSplitMicrosoftGroupsKeepTheirOwnDomains 锁死「同一家的两个分组不会互相吞掉」。
//
// 用户 2026-10-12 问：微软云盘选直连，会不会被别的规则接管走代理？答案是结构性的 ——
// 表的顺序就是优先级，「Ⓜ️ 微软云盘」必须排在「Ⓜ️ 微软服务」和「🌏 国外穿墙」之前，
// 否则 onedrive 分类里的域名会先被 microsoft / geolocation-!cn 命中，用户在节点页
// 给云盘组选什么出站都白搭。这里把顺序本身钉死，以后有人调表顺序会立刻红。
func TestSplitMicrosoftGroupsKeepTheirOwnDomains(t *testing.T) {
	idx := func(name string) int {
		for i, g := range Table {
			if g.Name == name {
				return i
			}
		}
		return -1
	}
	cloud, service, abroad := idx("Ⓜ️ 微软云盘"), idx("Ⓜ️ 微软服务"), idx("🌏 国外穿墙")
	if cloud < 0 || service < 0 || abroad < 0 {
		t.Fatalf("微软两组或国外穿墙组不见了：cloud=%d service=%d abroad=%d", cloud, service, abroad)
	}
	if !(cloud < service && cloud < abroad) {
		t.Errorf("「Ⓜ️ 微软云盘」必须排在「Ⓜ️ 微软服务」(%d) 和「🌏 国外穿墙」(%d) 之前，实际 cloud=%d",
			service, abroad, cloud)
	}

	// 三组默认出站本来就不同，钉住：云盘直连、服务与国外穿墙走代理
	for name, want := range map[string]string{"Ⓜ️ 微软云盘": "direct", "Ⓜ️ 微软服务": "proxy", "🌏 国外穿墙": "proxy"} {
		for _, g := range Table {
			if g.Name == name {
				if g.DefaultOut != want {
					t.Errorf("%s 的默认出站 = %q，期望 %q", name, g.DefaultOut, want)
				}
			}
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