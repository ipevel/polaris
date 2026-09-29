// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package config

import (
	"testing"

	"github.com/metacubex/mihomo/config"
)

// patchGeoXUrl 必须把订阅携带的 geox-url 无条件复位为官方地址：
// 该键可控即 SSRF（内核会主动请求这些 URL 并把返回内容当规则加载）。
func TestPatchGeoXUrlResetsAttackerUrls(t *testing.T) {
	cfg := &config.RawConfig{
		GeoXUrl: config.RawGeoXUrl{
			GeoIp:   "https://evil.example.com/geoip.dat",
			GeoSite: "https://evil.example.com/geosite.dat",
			Mmdb:    "https://evil.example.com/geoip.metadb",
			ASN:     "https://evil.example.com/GeoLite2-ASN.mmdb",
		},
	}

	if err := patchGeoXUrl(cfg, ""); err != nil {
		t.Fatalf("patchGeoXUrl 返回错误: %v", err)
	}

	if cfg.GeoXUrl != officialGeoXUrls {
		t.Fatalf("geox-url 未被复位为官方地址，实际: %+v", cfg.GeoXUrl)
	}
}

// 订阅把 geox-url 显式置空时同样复位：空值会让 geodata 失去数据源，
// 导致 GEOIP/GEOSITE 规则全部失效，属功能损失，不能作为「无风险」放行。
func TestPatchGeoXUrlResetsEmptyUrls(t *testing.T) {
	cfg := &config.RawConfig{GeoXUrl: config.RawGeoXUrl{}}

	if err := patchGeoXUrl(cfg, ""); err != nil {
		t.Fatalf("patchGeoXUrl 返回错误: %v", err)
	}

	if cfg.GeoXUrl != officialGeoXUrls {
		t.Fatalf("空的 geox-url 未被复位，实际: %+v", cfg.GeoXUrl)
	}
	if cfg.GeoXUrl.GeoIp == "" || cfg.GeoXUrl.GeoSite == "" || cfg.GeoXUrl.Mmdb == "" || cfg.GeoXUrl.ASN == "" {
		t.Fatalf("复位后仍有空字段: %+v", cfg.GeoXUrl)
	}
}

// 幂等：官方值再次通过处理器不应发生变化，避免重复处理时抖动。
func TestPatchGeoXUrlIsIdempotent(t *testing.T) {
	cfg := &config.RawConfig{GeoXUrl: officialGeoXUrls}
	before := cfg.GeoXUrl

	if err := patchGeoXUrl(cfg, ""); err != nil {
		t.Fatalf("patchGeoXUrl 返回错误: %v", err)
	}
	if err := patchGeoXUrl(cfg, ""); err != nil {
		t.Fatalf("patchGeoXUrl 第二次返回错误: %v", err)
	}

	if cfg.GeoXUrl != before {
		t.Fatalf("重复处理改变了官方地址，实际: %+v", cfg.GeoXUrl)
	}
}

// 处理器必须已注册：漏注册会让兜底静默失效（编译通过、单跑测试也过）。
func TestGeoXUrlProcessorRegistered(t *testing.T) {
	for _, p := range processors {
		if p == nil {
			continue
		}
		cfg := &config.RawConfig{
			GeoXUrl: config.RawGeoXUrl{GeoIp: "https://evil.example.com/x"},
		}
		if err := p(cfg, ""); err != nil {
			continue
		}
		if cfg.GeoXUrl == officialGeoXUrls {
			return
		}
	}
	t.Fatal("processors 中没有任何处理器会把 geox-url 复位为官方地址（patchGeoXUrl 未注册？）")
}