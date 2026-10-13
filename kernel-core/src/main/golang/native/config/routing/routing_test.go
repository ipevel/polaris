// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package routing

import (
	"encoding/json"
	"errors"
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

	// 默认开启的组必须生成规则；默认关闭的组不生成
	expectRule := "GEOSITE,apple,🍎 苹果服务"
	if !containsRule(cfg.Rule, expectRule) {
		t.Fatalf("missing rule %q", expectRule)
	}
	// 默认关闭的组（唯一一个：🍃 应用净化）不生成规则、不声明 provider
	if containsRule(cfg.Rule, "banprogramad") {
		t.Fatal("disabled group (🍃 应用净化) rules must not be generated")
	}
	if _, ok := cfg.RuleProvider["banprogramad"]; ok {
		t.Fatal("disabled group provider must not be declared")
	}
	if _, ok := cfg.RuleProvider["china-domain"]; !ok {
		t.Fatal("enabled group provider missing")
	}
	// 内置规则不再有任何远程 provider：全部 type:file 本地清单
	for key, raw := range cfg.RuleProvider {
		if raw["type"] != "file" {
			t.Fatalf("provider %q must be local file provider, got %v", key, raw["type"])
		}
		if _, ok := raw["url"]; ok {
			t.Fatalf("local provider %q must not carry url", key)
		}
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
			"🎧 TikTok": false,
			"📲 电报消息": true,
		},
	}
	if err := Build(cfg, state, nil); err != nil {
		t.Fatalf("Build: %v", err)
	}
	if containsRule(cfg.Rule, "GEOSITE,tiktok,") {
		t.Fatal("explicitly disabled group must not generate rules")
	}
	if !containsRule(cfg.Rule, "GEOSITE,telegram,📲 电报消息") {
		t.Fatal("explicitly enabled group rules missing")
	}
	// 国内直连组必须同时有域名（GEOSITE,cn）与 IP（GEOIP,CN）两层覆盖，
	// 并给 IP 层加 no-resolve，否则 HTTPDNS/直连 IP 的国内流量漏到代理
	if !containsRule(cfg.Rule, "GEOIP,CN,🎯 国内直连,no-resolve") {
		t.Fatal("direct group must carry no-resolve GEOIP,CN rule")
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
	proxy := groupByName(cfg, "🎧 TikTok")["proxies"].([]string)
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
			// 内置规则集必须全部是本地文件 provider（URL 为空）：任何非空 URL
			// 都会让内核重新变回 type:http 的远程规则，拉取失败即静默失效
			//（用户 2026-10-12 要求：规则内置本地，不读远程）。
			if p.URL != "" {
				t.Fatalf("builtin provider %q must be local (URL empty), got %q", key, p.URL)
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

// TestBuildProxyGroupURL 锁住结构组的测速 URL：必须与 mihomo 默认一致且为 https，
// 否则 addTestUrlToProviders 会给每个 proxy-provider 登记额外 health-check，
// 同一节点被两个 URL 各测一次，UI 延迟在两条历史之间抖动。
func TestBuildProxyGroupURL(t *testing.T) {
	cfg := testRawConfig()
	if err := Build(cfg, defaultEnabledState(), nil); err != nil {
		t.Fatalf("Build: %v", err)
	}

	if !strings.HasPrefix(proxyGroupURL, "https://") {
		t.Fatalf("proxyGroupURL must be https, got %q", proxyGroupURL)
	}
	if proxyGroupURL != constant.DefaultTestURL {
		t.Fatalf("proxyGroupURL %q must equal mihomo DefaultTestURL %q", proxyGroupURL, constant.DefaultTestURL)
	}
	for _, name := range []string{GroupNameAuto, GroupNameFallback} {
		g := groupByName(cfg, name)
		if g == nil {
			t.Fatalf("group %q missing", name)
		}
		if g["url"] != proxyGroupURL {
			t.Fatalf("group %q url = %v, want %q", name, g["url"], proxyGroupURL)
		}
		// 不能是 "*"：任意响应（含中间盒拦截页）都算通，会伪造出低延迟
		if g["expected-status"] != proxyGroupExpectedStatus {
			t.Fatalf("group %q expected-status = %v, want %q", name, g["expected-status"], proxyGroupExpectedStatus)
		}
	}
}

// TestBuildHealthCheckBudgetDefaults 锁住每组健康检查的默认参数来源
// （healthcheck.go: timeout==0 → 5000ms、errgroup limit 10）：App 侧算预算
// 依赖这两个常量，一旦生成层开始写 timeout，预算公式必须同步改。
func TestBuildHealthCheckBudgetDefaults(t *testing.T) {
	cfg := testRawConfig()
	if err := Build(cfg, defaultEnabledState(), nil); err != nil {
		t.Fatalf("Build: %v", err)
	}
	for _, g := range cfg.ProxyGroup {
		if _, ok := g["timeout"]; ok {
			t.Fatalf("group %v must not set timeout (默认 5000ms 是预算公式的来源)", g["name"])
		}
	}
}

// TestBuildEveryGroupIncludeAll 记录当前契约：每个生成组都 include-all
// （全部节点并入），这是「每条分类可指定任意节点」的实现方式。
// 同时这决定了 healthCheckAll 的扇出（组数 × 节点数），改动必须同步回归。
func TestBuildEveryGroupIncludeAll(t *testing.T) {
	cfg := testRawConfig()
	state := defaultEnabledState()
	state.Groups = map[string]bool{}
	if err := Build(cfg, state, nil); err != nil {
		t.Fatalf("Build: %v", err)
	}
	if len(cfg.ProxyGroup) == 0 {
		t.Fatal("no groups generated")
	}
	for _, g := range cfg.ProxyGroup {
		// 兜底组（🐟 漏网之鱼）刻意只含 主组 + DIRECT，不并入全部节点
		if g["name"] == GroupNameFinal {
			continue
		}
		if g["include-all"] != true {
			t.Fatalf("group %v missing include-all", g["name"])
		}
	}
	if g := groupByName(cfg, GroupNameSelector); g["include-all"] != true {
		t.Fatal("selector group must include-all")
	}
}

// TestBuildNameCollisionProxy：面板把节点命名成保留组名时，mihomo 会因重名
// 硬失败（整个 profile 连不上），必须在生成前拦下并降级回面板配置。
func TestBuildNameCollisionProxy(t *testing.T) {
	cfg := testRawConfig()
	cfg.Proxy = append(cfg.Proxy, map[string]any{"name": GroupNameAuto, "type": "ss"})

	err := Build(cfg, defaultEnabledState(), nil)
	if err == nil {
		t.Fatal("name collision must fail the build")
	}
	var collision *NameCollisionError
	if !errors.As(err, &collision) {
		t.Fatalf("want NameCollisionError, got %T: %v", err, err)
	}
	if collision.Kind != "proxy" || collision.Name != GroupNameAuto {
		t.Fatalf("unexpected collision: %+v", collision)
	}
}

// TestBuildNameCollisionProvider：provider 名与生成组名重名同样会
// 触发 mihomo 的 errDuplicateProvider。
func TestBuildNameCollisionProvider(t *testing.T) {
	cfg := testRawConfig()
	cfg.ProxyProvider = map[string]map[string]any{"REJECT": {"type": "http"}}

	err := Build(cfg, defaultEnabledState(), nil)
	if err == nil {
		t.Fatal("provider name collision must fail the build")
	}
	var collision *NameCollisionError
	if !errors.As(err, &collision) {
		t.Fatalf("want NameCollisionError, got %T: %v", err, err)
	}
	if collision.Kind != "provider" || collision.Name != "REJECT" {
		t.Fatalf("unexpected collision: %+v", collision)
	}
}

// TestValidGroupName：名字会被拼进 "RULE-SET,<key>,<name>"，逗号会让字段错位
// （轻则整份配置加载失败，形如 "DIRECT,x" 还会静默改指向）。
func TestValidGroupName(t *testing.T) {
	valid := []string{"我的规则", "My Rules 2", "日本🇯🇵-01", "a.b(c)"}
	for _, name := range valid {
		if err := validGroupName(name); err != nil {
			t.Fatalf("validGroupName(%q) unexpected error: %v", name, err)
		}
	}

	invalid := []string{
		"", "a,b", "DIRECT,x", "a\nb", "a\tb", `a"b`,
		" a", "a ", strings.Repeat("x", 33),
		GroupNameSelector, GroupNameAuto, GroupNameFallback, GroupNameFinal,
		"DIRECT", "REJECT",
	}
	for _, name := range invalid {
		if err := validGroupName(name); err == nil {
			t.Fatalf("validGroupName(%q) must be rejected", name)
		}
	}
}

// TestValidRuleDomain：domain 被拼进 "DOMAIN-SUFFIX,<domain>,DIRECT"。
func TestValidRuleDomain(t *testing.T) {
	valid := []string{"example.com", "a.b.example.com", "xn--fiq228c.cn"}
	for _, d := range valid {
		if err := validRuleDomain(d); err != nil {
			t.Fatalf("validRuleDomain(%q) unexpected error: %v", d, err)
		}
	}

	invalid := []string{
		"", "x,MATCH,DIRECT.example.com", "a..b.com", "-a.com", "a-.com",
		"a b.com", "http://a.com", "a/b.com", strings.Repeat("a", 64) + ".com",
	}
	for _, d := range invalid {
		if err := validRuleDomain(d); err == nil {
			t.Fatalf("validRuleDomain(%q) must be rejected", d)
		}
	}
}

// TestBuildSkipsInvalidDirectDomains：非法直连域名不能继续 emit 成规则串，
// 否则整份配置加载失败；必须跳过并保留其余规则。
func TestBuildSkipsInvalidDirectDomains(t *testing.T) {
	cfg := testRawConfig()
	domains := []string{"good.example.com", "bad,MATCH,DIRECT.example.com"}
	if err := Build(cfg, defaultEnabledState(), domains); err != nil {
		t.Fatalf("Build: %v", err)
	}
	if !containsRule(cfg.Rule, "DOMAIN-SUFFIX,good.example.com,DIRECT") {
		t.Fatalf("valid domain rule missing: %v", cfg.Rule)
	}
	for _, r := range cfg.Rule {
		if strings.Contains(r, "bad,MATCH") {
			t.Fatalf("invalid domain must be skipped, got rule %q", r)
		}
	}
}

func TestOrderedTable(t *testing.T) {
	if got := OrderedTable(nil); len(got) != len(Table) || got[0].Name != Table[0].Name {
		t.Fatalf("empty order must fall back to Table, got %d groups first=%q", len(got), got[0].Name)
	}

	first := Table[len(Table)-1].Name
	second := Table[len(Table)-2].Name
	got := OrderedTable([]string{first, "不存在的组", second, first})
	if len(got) != len(Table) {
		t.Fatalf("len=%d, want %d（不得丢组）", len(got), len(Table))
	}
	if got[0].Name != first || got[1].Name != second {
		t.Fatalf("head=%q,%q, want %q,%q", got[0].Name, got[1].Name, first, second)
	}
	if got[0].DefaultOut != Table[len(Table)-1].DefaultOut {
		t.Fatalf("组内容被篡改: %+v", got[0])
	}

	want := make([]string, 0, len(Table)-2)
	for _, item := range Table {
		if item.Name == first || item.Name == second {
			continue
		}
		want = append(want, item.Name)
	}
	rest := got[2:]
	if len(rest) != len(want) {
		t.Fatalf("rest len=%d, want %d", len(rest), len(want))
	}
	for i, item := range rest {
		if item.Name != want[i] {
			t.Fatalf("rest[%d]=%q, want %q", i, item.Name, want[i])
		}
	}
}

func TestBuildAppliesGroupOrder(t *testing.T) {
	state := defaultEnabledState()
	// 必须挑一个默认开启的组：默认关闭的组不生成规则，不会出现在结果里。
	last := ""
	for i := len(Table) - 1; i >= 0; i-- {
		if Table[i].DefaultOn {
			last = Table[i].Name
			break
		}
	}
	if last == "" {
		t.Fatal("Table 中没有默认开启的组")
	}
	state.Order = []string{last}
	cfg := testRawConfig()
	if err := Build(cfg, state, nil); err != nil {
		t.Fatalf("Build: %v", err)
	}
	names := groupNames(cfg)
	// 前三个是结构组（选择器/自动选择/故障转移），其后才是内置分流组。
	if len(names) < 4 {
		t.Fatalf("groups=%v", names)
	}
	if names[3] != last {
		t.Fatalf("group[3]=%q, want %q (names=%v)", names[3], last, names)
	}
}

// TestAIGroupMerge 锁死 2026-10-13 的「AI 组三合一」结构：组名、位置、默认开关、
// 内联规则条数与顺序，以及「🌏 国外穿墙 排在所有具体平台组之后」这条结构约束
// （2026-10-14 从 23 组简化到 12 组后，原来的 Google/Google Play 约束随之改写）。
//
// 不依赖 geo 资源即可运行（CI 上也会跑），因此它同时守住「两端逐字一致」——
// 安卓表是唯一真源，Windows 的 routing-sync-test.js 指过来比对的就是这份 Table。
func TestAIGroupMerge(t *testing.T) {
	idx := func(name string) int {
		for i, g := range Table {
			if g.Name == name {
				return i
			}
		}
		return -1
	}

	// 1) 三个旧组名必须从表里消失
	for _, legacy := range []string{"♊️ Google Gemini", "💬 Claude", "💬 OpenAI"} {
		if idx(legacy) >= 0 {
			t.Fatalf("旧组名 %q 仍在 Table 中", legacy)
		}
	}

	ai := idx(AIGroupName)
	if ai < 0 {
		t.Fatalf("%q 组缺失", AIGroupName)
	}
	// 2) 位置：📺 流媒体 之后、🎮 游戏平台 之前
	stream, games := idx("📺 流媒体"), idx("🎮 游戏平台")
	if stream < 0 || games < 0 {
		t.Fatalf("「📺 流媒体」/「🎮 游戏平台」组缺失：stream=%d games=%d", stream, games)
	}
	if stream >= ai || ai >= games {
		t.Fatalf("%q 位置不对：流媒体=%d AI=%d 游戏=%d", AIGroupName, stream, ai, games)
	}

	// 3) 默认开、走代理、内联 GEOSITE + 兜底域名，并声明本地规则集 ai
	group := Table[ai]
	if !group.DefaultOn || group.DefaultOut != "proxy" {
		t.Fatalf("AI 组默认开关/出口不对：on=%v out=%q", group.DefaultOn, group.DefaultOut)
	}
	if len(group.Providers) != 1 || group.Providers[0] != "ai" {
		t.Fatalf("AI 组应且只应声明 provider %q：%v", "ai", group.Providers)
	}

	// 4) 内联规则 4 条 GEOSITE + 147 条兜底域名 = 151，顺序与规格一致
	wantHead := []string{
		"GEOSITE,category-ai-!cn,{t}",
		"GEOSITE,openai,{t}",
		"GEOSITE,anthropic,{t}",
		"GEOSITE,google-gemini,{t}",
	}
	if len(group.InlineRules) != 151 {
		t.Fatalf("AI 组内联规则 %d 条，期望 151", len(group.InlineRules))
	}
	for i, w := range wantHead {
		if group.InlineRules[i] != w {
			t.Fatalf("AI 组内联规则[%d]=%q，期望 %q", i, group.InlineRules[i], w)
		}
	}
	seen := make(map[string]bool, len(group.InlineRules))
	for _, r := range group.InlineRules {
		if seen[r] {
			t.Fatalf("AI 组内联规则重复：%q", r)
		}
		seen[r] = true
		// 5) 绝不能带 GEOSITE,google / GEOSITE,google-play
		if r == "GEOSITE,google,{t}" || r == "GEOSITE,google-play,{t}" {
			t.Fatalf("AI 组不得带 Google 分类（会把 🌏 Google / 🌏 Google Play 整体吃掉）：%q", r)
		}
	}

	// 6) 结构约束：「🌏 国外穿墙」声明的 geolocation-!cn 是兜底型分类，必须排在所有
	// 具体平台组之后；否则 AI / 社交媒体 / 流媒体 / 游戏的域名会先被它命中，
	// 那几个开关设成什么都不生效。只允许两个 block 组排在它后面。
	abroad := idx("🌏 国外穿墙")
	if abroad < 0 {
		t.Fatalf("「🌏 国外穿墙」组缺失")
	}
	if ai >= abroad {
		t.Fatalf("AI 组必须排在「🌏 国外穿墙」之前：AI=%d abroad=%d", ai, abroad)
	}
	for i := abroad + 1; i < len(Table); i++ {
		if n := Table[i].Name; n != "🍃 应用净化" && n != "🛑 广告拦截" {
			t.Fatalf("组 %q 排在「🌏 国外穿墙」之后，会被 geolocation-!cn 整体吃掉", n)
		}
	}
	for i := 0; i < abroad; i++ {
		for _, r := range Table[i].InlineRules {
			if r == "GEOSITE,geolocation-!cn,{t}" {
				t.Fatalf("组 %q 排在「🌏 国外穿墙」之前却声明了 %q，国外穿墙组又会被整体吃掉",
					Table[i].Name, r)
			}
		}
	}
}

// TestLegacyAIGroupAliases 锁死旧组名的设置迁移：一次改名不能让用户已保存的
// 开关与拖动顺序静默丢失（开关退回表内默认、顺序退回内置顺序）。
func TestLegacyAIGroupAliases(t *testing.T) {
	ai := RuleGroup{Name: AIGroupName, DefaultOn: true, DefaultOut: "proxy"}

	cases := []struct {
		why    string
		groups map[string]bool
		want   bool
	}{
		{"旧名任一为开 → 新组为开", map[string]bool{"💬 Claude": false, "💬 OpenAI": true}, true},
		{"旧名全关 → 新组关", map[string]bool{"💬 Claude": false, "♊️ Google Gemini": false}, false},
		{"单个旧名为开", map[string]bool{"♊️ Google Gemini": true}, true},
		{"无任何设置 → 表内默认（开）", map[string]bool{}, true},
		{"显式新组名优先于残留旧键", map[string]bool{AIGroupName: false, "💬 OpenAI": true}, false},
	}
	for _, c := range cases {
		state := &State{Version: 1, Enabled: true, Groups: c.groups}
		if got := state.GroupEnabled(ai); got != c.want {
			t.Errorf("%s：GroupEnabled=%v，期望 %v（groups=%v）", c.why, got, c.want, c.groups)
		}
	}

	// 非 AI 的合并组同样遵循「任一旧名为开 → 新组为开」
	social := RuleGroup{Name: "📲 社交媒体", DefaultOn: true, DefaultOut: "proxy"}
	st := &State{Version: 1, Enabled: true, Groups: map[string]bool{"📲 Facebook": false, "📸 Instagram": true}}
	if !st.GroupEnabled(social) {
		t.Error("合并组「📲 社交媒体」未继承旧组的开启状态")
	}

	// 2026-10-14 的合并：旧名一律落到合并后的新组
	merged := []struct{ legacy, current string }{
		{"📲 X", "📲 社交媒体"},
		{"📸 Instagram", "📲 社交媒体"},
		{"📲 Facebook", "📲 社交媒体"},
		{"📲 WhatsApp", "📲 社交媒体"},
		{"📹 油管视频", "📺 流媒体"},
		{"🎥 奈飞视频", "📺 流媒体"},
		{"Ⓜ️ 微软云盘", "Ⓜ️ 微软"},
		{"📺 哔哩哔哩", "🎯 国内直连"},
		{"🎶 网易音乐", "🎯 国内直连"},
	}
	for _, m := range merged {
		if got := canonicalGroupName(m.legacy); got != m.current {
			t.Errorf("旧组名 %q 映射到 %q，期望 %q", m.legacy, got, m.current)
		}
	}
	// 已取消但**刻意不映射**的四个组名：硬映射会把「我关掉 Google」变成「我关掉整个穿墙」
	for _, keep := range []string{"🐱 GitHub", "🌏 Google Play", "🌏 Google", "Ⓜ️ 微软Bing"} {
		if got := canonicalGroupName(keep); got != keep {
			t.Errorf("已取消的 %q 不应被映射，实际映射到 %q", keep, got)
		}
	}

	// order：三个旧名折叠到同一个组，取最靠前的位置，且绝不丢组
	got := OrderedTable([]string{"💬 Claude", "📲 电报消息", "♊️ Google Gemini"})
	if len(got) != len(Table) {
		t.Fatalf("order 迁移后组数 %d，期望 %d（不得丢组）", len(got), len(Table))
	}
	if got[0].Name != AIGroupName {
		t.Fatalf("order 首位 = %q，期望 %q（三个旧名中最靠前的位置）", got[0].Name, AIGroupName)
	}
	if got[1].Name != "📲 电报消息" {
		t.Fatalf("order 次位 = %q，期望 %q", got[1].Name, "📲 电报消息")
	}
	// 直接用新组名的 order 行为不变
	plain := OrderedTable([]string{AIGroupName})
	if len(plain) != len(Table) || plain[0].Name != AIGroupName {
		t.Fatalf("新组名 order 失效：len=%d first=%q", len(plain), plain[0].Name)
	}

	// 合并组同理：两个旧名指向同一组只占一个位置（取最靠前的），且绝不丢组
	order := OrderedTable([]string{"📲 X", "📲 Facebook", "🎶 网易音乐", "🎯 国内直连"})
	if len(order) != len(Table) {
		t.Fatalf("order 迁移后组数 %d，期望 %d（不得丢组）", len(order), len(Table))
	}
	if order[0].Name != "📲 社交媒体" || order[1].Name != "🎯 国内直连" {
		t.Fatalf("order 前两位 = %q / %q，期望 %q / %q",
			order[0].Name, order[1].Name, "📲 社交媒体", "🎯 国内直连")
	}
}
