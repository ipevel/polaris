// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only
//
// Derived from Clash Meta for Android (https://github.com/MetaCubeX/ClashMetaForAndroid).
// Upstream copyright retained per THIRD-PARTY-NOTICES.md.

package tunnel

import (
	"context"
	"strings"
	"time"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
)

// UrlTestResult 单节点测速结论。
//
// Delay 为实测毫秒；Kind 为失败分类：空 = 存活，"timeout" = 在但不回包，
// "offline" = 后端已不存在（应用侧只把 offline 标成离线）。
type UrlTestResult struct {
	Delay int    `json:"delay"`
	Kind  string `json:"kind"`
}

// 离线判定只认明确指向"服务器不存在"的错误；解析超时、TLS 异常等一律保守按超时，
// 避免把活节点误标成离线。与 Kotlin 侧 UrlTestResult.KIND_* 对应。
var urlTestOfflineHints = []string{
	"no such host",           // 域名已失效（NXDOMAIN）
	"connection refused",     // 端口没人监听
	"no route",               // 路由不可达
	"network is unreachable", // 网络不可达
}

func classifyUrlTestError(err error) string {
	if err == nil {
		return ""
	}

	// 第三方库的错误文本大小写不统一（如 "Connection refused"），统一小写后再匹配
	msg := strings.ToLower(err.Error())

	for _, hint := range urlTestOfflineHints {
		if strings.Contains(msg, hint) {
			return "offline"
		}
	}

	return "timeout"
}

// UrlTest 对单个节点跑一次真实测速（与组健康检查同源的 URLTest）。
// 期望状态码传 nil，与内核 REST /proxies/{name}/delay 的默认行为一致（任意状态码都算响应）。
func UrlTest(name string, timeoutMs int) *UrlTestResult {
	p := tunnel.Proxies()[name]

	if p == nil {
		log.Warnln("Request url test for `%s`: not found", name)

		return &UrlTestResult{Kind: "timeout"}
	}

	if _, isGroup := p.Adapter().(outboundgroup.ProxyGroup); isGroup {
		return &UrlTestResult{Kind: "timeout"}
	}

	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(timeoutMs)*time.Millisecond)
	defer cancel()

	delay, err := p.URLTest(ctx, latestDelayTestURL(p), nil)
	if err != nil {
		return &UrlTestResult{Kind: classifyUrlTestError(err)}
	}

	return &UrlTestResult{Delay: int(delay)}
}
