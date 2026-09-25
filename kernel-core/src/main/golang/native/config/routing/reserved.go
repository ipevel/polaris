// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package routing

import (
	"fmt"
	"strings"

	"github.com/metacubex/mihomo/config"
)

// 本地分流生成的组名会与面板节点/Provider 处在同一个 mihomo 命名空间里，
// 而 mihomo 对重名是**硬失败**（config.go 的 "is the duplicate name"、
// parser.go 的 errDuplicateProvider）——整个 profile 加载失败、连不上。
// 面板（或恶意订阅）只要下发一个叫「自动选择」的节点就能瘫痪客户端，
// 所以这里在生成之前先扫一遍，命中就整体降级回面板配置。
var reservedMihomoNames = []string{
	// mihomo 预注册的出站
	"DIRECT", "REJECT", "REJECT-DROP", "COMPATIBLE", "PASS", "PASS-RULE",
	"GLOBAL",
	// provider 保留名（provider 包内 default）
	"default",
}

// ReservedNames 返回本地分流占用的全部名称（结构组 + 内置分流组 +
// mihomo 预注册出站）。App 侧的自定义组名校验应与之保持一致。
func ReservedNames() []string {
	names := make([]string, 0, len(Table)+len(reservedMihomoNames)+4)
	names = append(names,
		GroupNameSelector, GroupNameAuto, GroupNameFallback, GroupNameFinal,
	)
	for _, item := range Table {
		names = append(names, item.Name)
	}
	names = append(names, reservedMihomoNames...)
	return names
}

// NameCollisionError 面板节点/Provider 与本地分流保留名冲突。上层据此
// 走「降级为面板配置」的回退，并把原因写到降级标记文件供 UI 提示。
type NameCollisionError struct {
	Kind string // proxy | provider
	Name string
}

func (e *NameCollisionError) Error() string {
	return fmt.Sprintf("panel %s name %q collides with local routing reserved name", e.Kind, e.Name)
}

// checkNameCollisions 在生成本地组之前调用：命中即返回 typed error，
// 由 patchLocalRouting 统一降级（不新增错误路径，复用既有回退）。
func checkNameCollisions(cfg *config.RawConfig) error {
	reserved := make(map[string]struct{}, len(Table)+8)
	for _, name := range ReservedNames() {
		reserved[name] = struct{}{}
	}

	for _, proxy := range cfg.Proxy {
		raw, ok := proxy["name"]
		if !ok {
			continue
		}
		name, ok := raw.(string)
		if !ok {
			continue
		}
		if _, hit := reserved[name]; hit {
			return &NameCollisionError{Kind: "proxy", Name: name}
		}
	}

	for name := range cfg.ProxyProvider {
		if _, hit := reserved[name]; hit {
			return &NameCollisionError{Kind: "provider", Name: name}
		}
	}

	return nil
}

// validGroupName 自定义分流组名校验（SEC-03）。名字会被拼进
// "RULE-SET,<key>,<name>" 规则串，而 mihomo 用 strings.Split(ruleRaw, ",")
// 分列——名字里出现逗号会让字段错位：多数情况整个配置加载失败，
// 形如 "DIRECT,x" 还会静默把规则指向别的出口。控制字符会污染日志与
// provider 文件名，一并拒绝。
func validGroupName(name string) error {
	if name == "" {
		return fmt.Errorf("empty name")
	}
	if len([]rune(name)) > 32 {
		return fmt.Errorf("name too long (%d runes > 32)", len([]rune(name)))
	}
	if strings.ContainsAny(name, ",\"\t\r\n") {
		return fmt.Errorf("name contains reserved character")
	}
	if strings.TrimSpace(name) != name {
		return fmt.Errorf("name has leading/trailing spaces")
	}
	for _, r := range name {
		if r < 0x20 || r == 0x7f {
			return fmt.Errorf("name contains control character")
		}
	}
	if isReservedName(name) {
		return fmt.Errorf("name conflicts with builtin group")
	}
	return nil
}

func isReservedName(name string) bool {
	for _, reserved := range ReservedNames() {
		if reserved == name {
			return true
		}
	}
	return false
}

// validRuleDomain 直连域名校验（SEC-03）。domain 同样被拼进
// "DOMAIN-SUFFIX,<domain>,DIRECT"，逗号会让 target 字段错位到别的出口。
func validRuleDomain(domain string) error {
	if domain == "" {
		return fmt.Errorf("empty domain")
	}
	if len(domain) > 253 {
		return fmt.Errorf("domain too long (%d > 253)", len(domain))
	}
	if strings.ContainsAny(domain, ",\"\t\r\n/:*? ") {
		return fmt.Errorf("domain contains reserved character")
	}
	for _, label := range strings.Split(domain, ".") {
		if label == "" {
			return fmt.Errorf("domain has empty label")
		}
		if len(label) > 63 {
			return fmt.Errorf("domain label too long (%d > 63)", len(label))
		}
		if strings.HasPrefix(label, "-") || strings.HasSuffix(label, "-") {
			return fmt.Errorf("domain label starts or ends with hyphen")
		}
		for _, r := range label {
			if (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || (r >= '0' && r <= '9') || r == '-' {
				continue
			}
			return fmt.Errorf("domain label has invalid character %q", r)
		}
	}
	return nil
}
