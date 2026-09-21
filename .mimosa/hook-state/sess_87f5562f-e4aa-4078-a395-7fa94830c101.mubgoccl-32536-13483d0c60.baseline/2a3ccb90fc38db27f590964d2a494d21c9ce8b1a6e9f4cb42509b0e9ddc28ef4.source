//go:build windows

package dns

import (
	"github.com/metacubex/mihomo/component/resolver"
)

// Windows 桌面端补丁：与 patch_android.go 对等的符号面。
//
// systemClient 的系统 DNS 发现已由 system_windows.go 原生实现
// （GetAdaptersAddresses + 周期刷新 SystemDnsFlushTime），
// 因此无需 Android 式静态 systemResolver 注入：
//   - UpdateSystemDNS 保持 no-op（cfa/native/app.NotifyDnsChanged 可安全调用）；
//   - FlushCacheWithDefaultResolver 与 Android 端同语义：清空解析缓存并重置连接。

func FlushCacheWithDefaultResolver() {
	resolver.ClearCache()
	resolver.ResetConnection()
}

func UpdateSystemDNS(_ []string) {}
