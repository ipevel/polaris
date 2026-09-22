// SPDX-FileCopyrightText: The Clash Meta for Android Authors
// SPDX-FileCopyrightText: 2026 Polaris Contributors (modifications)
// SPDX-License-Identifier: GPL-3.0-only
//
// Derived from Clash Meta for Android (https://github.com/MetaCubeX/ClashMetaForAndroid).
// Upstream copyright retained per THIRD-PARTY-NOTICES.md.

//go:build windows

package app

import "syscall"

// Windows 下 syscall.SetNonblock 接收 syscall.Handle，与 POSIX 的 int 签名不同。
// 该路径在桌面端实际不可达（openContentImpl 默认未实现，仅供编译与未来扩展）。

func setNonblock(fd int) error {
	return syscall.SetNonblock(syscall.Handle(fd), true)
}
