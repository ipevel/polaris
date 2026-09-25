// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package routing

import (
	"fmt"
	"strings"

	"github.com/metacubex/mihomo/config"
	"github.com/metacubex/mihomo/log"
)

// Build 用本地分流方案整体替换 cfg 的 proxy-groups/rules/rule-providers，
// 面板节点（cfg.Proxy）与 proxy-providers 原样保留，通过 include-all 成为
// 本地组的成员。这就是「屏蔽网站下发的分流方案、只用本地」的执行点。
// directDomains 为构建期注入的自家后端域名（与 App 侧白名单同步）。
func Build(cfg *config.RawConfig, state *State, directDomains []string) error {
	if len(cfg.Proxy) == 0 && len(cfg.ProxyProvider) == 0 {
		return fmt.Errorf("profile has no proxies/providers")
	}

	groups := make([]map[string]any, 0, len(Table)+5)

	// 主选择组：默认选中自动选择（首位成员即默认项），用户可切手动节点。
	// 必须排在组列表第一位：App 侧 selectorGroup() 取第一个 Selector 组作为主选择。
	groups = append(groups, map[string]any{
		"name":        GroupNameSelector,
		"type":        groupTypeSelect,
		"proxies":     []string{GroupNameAuto, GroupNameFallback, outboundDirect},
		"include-all": true,
	})
	groups = append(groups, map[string]any{
		"name":        GroupNameAuto,
		"type":        groupTypeURLTest,
		"url":         proxyGroupURL,
		"interval":    proxyGroupInterval,
		"tolerance":   proxyGroupTolerance,
		"include-all": true,
	})
	groups = append(groups, map[string]any{
		"name":        GroupNameFallback,
		"type":        groupTypeFallback,
		"url":         proxyGroupURL,
		"interval":    proxyGroupInterval,
		"include-all": true,
	})

	ruleProviders := map[string]map[string]any{}
	rules := make([]string, 0, 256)

	// 自家后端域名直连最前置（patchRules 仍保留为兜底，其查重保证不重复）。
	for _, domain := range directDomains {
		rules = append(rules, "DOMAIN-SUFFIX,"+domain+","+outboundDirect)
	}
	rules = append(rules, lanDirectRules...)

	for _, item := range Table {
		if !state.GroupEnabled(item) {
			continue
		}
		// 成员顺序即默认出站（select 组默认选首位），对齐 Karing 预设 outbound 语义
		var members []string
		switch item.DefaultOut {
		case "direct":
			members = []string{outboundDirect, GroupNameSelector, outboundReject}
		case "block":
			members = []string{outboundReject, GroupNameSelector, outboundDirect}
		default:
			members = []string{GroupNameSelector, outboundDirect, outboundReject}
		}
		groups = append(groups, map[string]any{
			"name":    item.Name,
			"type":    groupTypeSelect,
			"proxies": members,
		})
		for _, key := range item.Providers {
			p, ok := Providers[key]
			if !ok {
				return fmt.Errorf("unknown provider %q in group %q", key, item.Name)
			}
			ruleProviders[key] = ProviderRawMap(p)
			rule := "RULE-SET," + key + "," + item.Name
			if p.NoResolve {
				rule += ",no-resolve"
			}
			rules = append(rules, rule)
		}
		for _, r := range item.InlineRules {
			rules = append(rules, strings.ReplaceAll(r, "{t}", item.Name))
		}
	}

	// 用户自定义分流组：一个规则集 URL 一个组；非法输入跳过不影响整体。
	for _, c := range state.Custom {
		if err := appendCustomGroup(&groups, ruleProviders, &rules, c); err != nil {
			log.Warnln("routing: skip custom group %q: %s", c.Name, err.Error())
			continue
		}
	}

	groups = append(groups, map[string]any{
		"name":    GroupNameFinal,
		"type":    groupTypeSelect,
		"proxies": []string{GroupNameSelector, outboundDirect},
	})
	rules = append(rules, "MATCH,"+GroupNameFinal)

	cfg.ProxyGroup = groups
	cfg.RuleProvider = ruleProviders
	cfg.Rule = rules
	// 面板 sub-rules 只被面板 rules 引用，规则已整体替换，一并清空
	cfg.SubRules = nil
	return nil
}

// ProviderRawMap 生成 mihomo rule-provider 原始配置。path 相对内核 profileDir，
// patchProviders 会改写为 <profileDir>/providers/<path>，App 侧按同路径预播种。
func ProviderRawMap(p Provider) map[string]any {
	return map[string]any{
		"type":     "http",
		"behavior": p.Behavior,
		"format":   p.Format,
		"url":      p.URL,
		"path":     providerSubPath + "/" + p.Key + FileExt(p),
		"interval": providerInterval,
	}
}

// FileExt 预播种文件扩展名：text 格式 .list，其余 .yaml。
func FileExt(p Provider) string {
	if p.Format == "text" {
		return ".list"
	}
	return ".yaml"
}

// appendCustomGroup 追加用户自定义规则组；非法输入报错跳过，不影响整体。
func appendCustomGroup(
	groups *[]map[string]any,
	ruleProviders map[string]map[string]any,
	rules *[]string,
	c CustomGroup,
) error {
	name := strings.TrimSpace(c.Name)
	if name == "" || len(name) > 32 {
		return fmt.Errorf("invalid name")
	}
	if name == GroupNameSelector || name == GroupNameAuto || name == GroupNameFallback || name == GroupNameFinal {
		return fmt.Errorf("name conflicts with builtin group")
	}
	for _, item := range Table {
		if item.Name == name {
			return fmt.Errorf("name conflicts with builtin group")
		}
	}
	if !strings.HasPrefix(c.URL, "https://") {
		return fmt.Errorf("url must be https")
	}
	behavior := c.Behavior
	if behavior == "" {
		behavior = "classical"
	}
	if behavior != "classical" && behavior != "domain" && behavior != "ipcidr" {
		return fmt.Errorf("invalid behavior %q", behavior)
	}
	key := customProviderKey(name, ruleProviders)
	interval := c.Interval
	if interval < 600 {
		interval = providerInterval
	}
	ruleProviders[key] = map[string]any{
		"type":     "http",
		"behavior": behavior,
		"format":   "yaml",
		"url":      c.URL,
		"path":     providerSubPath + "/" + key + ".yaml",
		"interval": interval,
	}
	*groups = append(*groups, map[string]any{
		"name":    name,
		"type":    groupTypeSelect,
		"proxies": []string{GroupNameSelector, outboundDirect, outboundReject},
	})
	*rules = append(*rules, "RULE-SET,"+key+","+name)
	return nil
}

func customProviderKey(name string, ruleProviders map[string]map[string]any) string {
	base := "custom_" + sanitizeProviderKey(name)
	key := base
	for i := 2; ; i++ {
		if _, exists := ruleProviders[key]; !exists {
			if _, builtin := Providers[key]; !builtin {
				return key
			}
		}
		key = fmt.Sprintf("%s#%d", base, i)
	}
}

// sanitizeProviderKey 把组名压成适合做文件名/键名的 ASCII 串；非 ASCII 字符
// 直接丢弃（emoji/中文），只保留字母数字与连字符。
func sanitizeProviderKey(name string) string {
	var b strings.Builder
	for _, r := range strings.ToLower(name) {
		switch {
		case r >= 'a' && r <= 'z', r >= '0' && r <= '9', r == '-':
			b.WriteRune(r)
		}
	}
	out := b.String()
	if out == "" {
		return "group"
	}
	return out
}
