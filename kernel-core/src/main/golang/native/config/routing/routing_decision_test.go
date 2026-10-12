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
		{host: "gemini.google.com", want: "♊️ Google Gemini", why: "Gemini"},
		{host: "claude.ai", want: "💬 Claude", why: "Claude"},
		{host: "api.openai.com", want: "💬 OpenAI", why: "OpenAI"},
		{host: "github.com", want: "🐱 GitHub", why: "GitHub"},
		{host: "www.bing.com", want: "Ⓜ️ 微软Bing", why: "Bing"},
		{host: "music.163.com", want: "🎶 网易音乐", why: "网易音乐"},
		{host: "www.bilibili.com", want: "📺 哔哩哔哩", why: "哔哩哔哩"},
		{host: "store.steampowered.com", want: "🎮 游戏平台", why: "Steam"},
		// 系统服务
		{host: "www.apple.com", want: "🍎 苹果服务", why: "苹果"},
		{host: "onedrive.live.com", want: "Ⓜ️ 微软云盘", why: "OneDrive"},
		{host: "www.google.com", want: "♊️ Google Gemini", why: "Google 统一走 Gemini 组（表内顺序在前）"},
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