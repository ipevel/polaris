// SPDX-FileCopyrightText: The Clash Meta for Android Authors
// SPDX-FileCopyrightText: 2026 Polaris Contributors (modifications)
// SPDX-License-Identifier: GPL-3.0-only
//
// Derived from Clash Meta for Android (https://github.com/MetaCubeX/ClashMetaForAndroid).
// Upstream copyright retained per THIRD-PARTY-NOTICES.md.

package tunnel

func Suspend(s bool) {
	// cause by ACTION_SCREEN_OFF/ACTION_SCREEN_ON,
	// but we don't know what should do so just ignored.
	//
	// WARNING: don't call core's Tunnel.OnSuspend/OnRunning at here,
	// this will cause the core to stop processing new incoming connections when the screen is locked.
}
