// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package routing

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/config"
)

func testRawConfig() *config.RawConfig {
	return &config.RawConfig{
		Proxy: []map[string]any{
			{"name": "node-1", "type": "ss"},
			{"name": "node-2", "type": "ss"},
		},
		ProxyProvider: map[string]map[string]any{},
		SubRules:      map[string][]string{"panel-sub": {}},
	}
}

func defaultEnabledState() *State {
	return &State{Version: 1, Enabled: true}
}

func groupNames(cfg *config.RawConfig) []string {
	names := make([]string, 0, len(cfg.ProxyGroup))
	for _, g := range cfg.ProxyGroup {
		names = append(names, g["name"].(string))
	}
	return names
}

func groupByName(cfg *config.RawConfig, name string) map[string]any {
	for _, g := range cfg.ProxyGroup {
		if g["name"] == name {
			return g
		}
	}
	return nil
}

func containsRule(rules []string, substr string) bool {
	for _, r := range rules {
		if strings.Contains(r, substr) {
			return true
		}
	}
	return false
}

func TestBuildStructure(t *testing.T) {
	cfg := testRawConfig()
	if err := Build(cfg, defaultEnabledState(), []string{"example.com"}); err != nil {
		t.Fatalf("Build: %v", err)
	}

	names := groupNames(cfg)
	// 组顺序契约：主选择组第一，其后自动/故障转移，末位漏网之鱼
	if len(names) < 5 {
		t.Fatalf("too few groups: %v", names)
	}
	if names[0] != GroupNameSelector || names[1] != GroupNameAuto || names[2] != GroupNameFallback {
		t.Fatalf("first groups mismatch: %v", names[:3])
	}
	if names[len(names)-1] != GroupNameFinal {
		t.Fatalf("last group must be final: %v", names)
	}

	selector := groupByName(cfg, GroupNameSelector)
	if selector["type"] != groupTypeSelect {
		t.Fatalf("selector group type: %v", selector["type"])
	}
	proxies := selector["proxies"].([]string)
	if proxies[0] != GroupNameAuto {
		t.Fatalf("selector default must be auto group: %v", proxies)
	}
	if selector["include-all"] != true {
		t.Fatal("selector group must include all proxies")
	}

	// 规则：自家域名直连最前，MATCH 兜底最后
	if cfg.Rule[0] != "DOMAIN-SUFFIX,example.com,DIRECT" {
		t.Fatalf("first rule: %q", cfg.Rule[0])
	}
	last := cfg.Rule[len(cfg.Rule)-1]
	if last != "MATCH,"+GroupNameFinal {
		t.Fatalf("last rule: %q", last)
	}
	// 面板 sub-rules 已清空
	if len(cfg.SubRules) != 0 {
		t.Fatalf("sub rules must be cleared: %v", cfg.SubRules)
	}
}

func TestDirectDomainsFromState(t *testing.T) {
	cfg := testRawConfig()
	state := &State{Version: 1, Enabled: true, DirectDomains: []string{"panel.example.cn"}}
	if err := Build(cfg, state, []string{"example.com"}); err != nil {
		t.Fatalf("Build: %v", err)
	}

	// App 侧注入的真实域名清单优先于内核编译期占位清单
	if cfg.Rule[0] != "DOMAIN-SUFFIX,panel.example.cn,DIRECT" {
		t.Fatalf("first rule must use state direct domain: %q", cfg.Rule[0])
	}
	for _, r := range cfg.Rule {
		if strings.Contains(r, "example.com,") {
			t.Fatalf("placeholder domain must not appear: %q", r)
		}
	}
}

func TestDefaultOnGroups(t *testing.T) {
	cfg := testRawConfig()
	if err := Build(cfg, defaultEnabledState(), nil); err != nil {
		t.Fatalf("Build: %v", err)
	}

	// Karing 预设默认开启的组必须生成规则；默认关闭的组不生成
	expectRule := "RULE-SET,gs_apple,🍎 苹果服务"
	if !containsRule(cfg.Rule, expectRule) {
		t.Fatalf("missing rule %q", expectRule)
	}
	if containsRule(cfg.Rule, "acl_banad") {
		t.Fatal("disabled group (广告拦截) rules must not be generated")
	}
	if _, ok := cfg.RuleProvider["acl_banad"]; ok {
		t.Fatal("disabled group provider must not be declared")
	}
	if _, ok := cfg.RuleProvider["gs_apple"]; !ok {
		t.Fatal("enabled group provider missing")
	}
	// 组数 = 3 个结构组 + 默认开启分流组 + 漏网之鱼
	defaultOn := 0
	for _, item := range Table {
		if item.DefaultOn {
			defaultOn++
		}
	}
	if got := len(groupNames(cfg)) - 4; got != defaultOn {
		t.Fatalf("rule group count %d != default-on %d", got, defaultOn)
	}
}

func TestGroupStateOverride(t *testing.T) {
	cfg := testRawConfig()
	state := &State{
		Version: 1,
		Enabled: true,
		Groups: map[string]bool{
			"📺 哔哩哔哩": false,
			"📲 电报消息": true,
		},
	}
	if err := Build(cfg, state, nil); err != nil {
		t.Fatalf("Build: %v", err)
	}
	if containsRule(cfg.Rule, "acl_bilibili") {
		t.Fatal("explicitly disabled group must not generate rules")
	}
	if !containsRule(cfg.Rule, "RULE-SET,gs_telegram,📲 电报消息") {
		t.Fatal("explicitly enabled group rules missing")
	}
	if !containsRule(cfg.Rule, "RULE-SET,gp_telegram,📲 电报消息,no-resolve") {
		t.Fatal("ipcidr provider rule must carry no-resolve")
	}
}

func TestMemberOrder(t *testing.T) {
	cfg := testRawConfig()
	if err := Build(cfg, defaultEnabledState(), nil); err != nil {
		t.Fatalf("Build: %v", err)
	}
	// 直连默认组 DIRECT 首位；代理默认组节点选择首位
	direct := groupByName(cfg, "🍎 苹果服务")["proxies"].([]string)
	if direct[0] != outboundDirect {
		t.Fatalf("direct group first member: %v", direct)
	}
	proxy := groupByName(cfg, "🌏 Google")["proxies"].([]string)
	if proxy[0] != GroupNameSelector {
		t.Fatalf("proxy group first member: %v", proxy)
	}
	// 全部开启（含默认关闭的 block 组）验证 block 首位
	full := &State{Version: 1, Enabled: true, Groups: map[string]bool{}}
	for _, item := range Table {
		full.Groups[item.Name] = true
	}
	cfg2 := testRawConfig()
	if err := Build(cfg2, full, nil); err != nil {
		t.Fatalf("Build: %v", err)
	}
	block := groupByName(cfg2, "🛑 广告拦截")["proxies"].([]string)
	if block[0] != outboundReject {
		t.Fatalf("block group first member: %v", block)
	}
}

func TestBuildNoProxies(t *testing.T) {
	cfg := &config.RawConfig{}
	if err := Build(cfg, defaultEnabledState(), nil); err == nil {
		t.Fatal("expected error for empty proxies/providers")
	}
}

func TestCustomGroups(t *testing.T) {
	cfg := testRawConfig()
	state := &State{
		Version: 1,
		Enabled: true,
		Custom: []CustomGroup{
			{Name: "我的规则", URL: "https://example.com/rules.yaml", Behavior: "domain"},
			{Name: "我的规则2", URL: "https://example.com/rules2.yaml", Behavior: "ipcidr"},
			{Name: GroupNameAuto, URL: "https://example.com/x.yaml"}, // 与内置组冲突 → 跳过
			{Name: "http-only", URL: "http://example.com/x.yaml"},    // 非 https → 跳过
		},
	}
	if err := Build(cfg, state, nil); err != nil {
		t.Fatalf("Build: %v", err)
	}
	// 「我的规则」全中文 sanitize 得 custom_group；「我的规则2」含数字得 custom_2
	if !containsRule(cfg.Rule, "RULE-SET,custom_group,我的规则") {
		t.Fatalf("custom rule missing: %v", cfg.Rule)
	}
	if !containsRule(cfg.Rule, "RULE-SET,custom_2,我的规则2") {
		t.Fatalf("custom rule 2 missing: %v", cfg.Rule)
	}
	if groupByName(cfg, "我的规则") == nil || groupByName(cfg, "我的规则2") == nil {
		t.Fatal("custom group missing")
	}
	if groupByName(cfg, "http-only") != nil {
		t.Fatal("invalid custom group must be skipped")
	}
	// MATCH 仍然最后
	if cfg.Rule[len(cfg.Rule)-1] != "MATCH,"+GroupNameFinal {
		t.Fatalf("MATCH not last: %q", cfg.Rule[len(cfg.Rule)-1])
	}
}

func TestReadState(t *testing.T) {
	dir := t.TempDir()
	constant.SetHomeDir(dir)

	// 文件缺失 → 关闭
	if s := ReadState(); s.Enabled {
		t.Fatal("missing file must be disabled")
	}

	// 损坏 JSON → 关闭
	if err := os.WriteFile(filepath.Join(dir, "routing.json"), []byte("{bad"), 0600); err != nil {
		t.Fatal(err)
	}
	if s := ReadState(); s.Enabled {
		t.Fatal("corrupt file must be disabled")
	}

	// 合法 JSON → 解析
	valid, _ := json.Marshal(State{
		Version: 1,
		Enabled: true,
		Groups:  map[string]bool{"📺 哔哩哔哩": false},
		Custom:  []CustomGroup{{Name: "x", URL: "https://a/b.yaml", Behavior: "domain", Interval: 3600}},
	})
	if err := os.WriteFile(filepath.Join(dir, "routing.json"), valid, 0600); err != nil {
		t.Fatal(err)
	}
	s := ReadState()
	if !s.Enabled || len(s.Custom) != 1 || s.Custom[0].Name != "x" {
		t.Fatalf("state parsed wrong: %+v", s)
	}
	if v, ok := s.Groups["📺 哔哩哔哩"]; !ok || v {
		t.Fatalf("group override not parsed: %+v", s.Groups)
	}
}

func TestTableIntegrity(t *testing.T) {
	seenGroups := map[string]bool{}
	seenProviders := map[string]bool{}
	for _, item := range Table {
		if item.Name == "" {
			t.Fatal("group name empty")
		}
		if seenGroups[item.Name] {
			t.Fatalf("duplicate group name %q", item.Name)
		}
		seenGroups[item.Name] = true
		if item.DefaultOut != "proxy" && item.DefaultOut != "direct" && item.DefaultOut != "block" {
			t.Fatalf("group %q invalid default out %q", item.Name, item.DefaultOut)
		}
		for _, key := range item.Providers {
			p, ok := Providers[key]
			if !ok {
				t.Fatalf("group %q references unknown provider %q", item.Name, key)
			}
			if !strings.HasPrefix(p.URL, "https://") {
				t.Fatalf("provider %q url not https", key)
			}
			if seenProviders[key] {
				t.Fatalf("provider %q declared twice", key)
			}
			seenProviders[key] = true
			switch p.Behavior {
			case "domain":
				if p.NoResolve {
					t.Fatalf("provider %q (domain) must not set no-resolve", key)
				}
			case "ipcidr", "classical":
				// classical 清单可能包含 IP-CIDR 条目（如 ChinaCompanyIp），
				// no-resolve 同样适用
			default:
				t.Fatalf("provider %q invalid behavior %q", key, p.Behavior)
			}
			if p.Format != "yaml" && p.Format != "text" {
				t.Fatalf("provider %q invalid format %q", key, p.Format)
			}
		}
		// 内联规则必须带目标占位符
		for _, r := range item.InlineRules {
			if !strings.Contains(r, "{t}") {
				t.Fatalf("group %q inline rule missing target placeholder: %q", item.Name, r)
			}
		}
	}
	// 未被引用的 provider 不应存在（避免静默下载数据）
	for key := range Providers {
		if !seenProviders[key] {
			t.Fatalf("provider %q declared but never referenced", key)
		}
	}
}
