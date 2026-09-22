// SPDX-FileCopyrightText: The Clash Meta for Android Authors
// SPDX-FileCopyrightText: 2026 Polaris Contributors (modifications)
// SPDX-License-Identifier: GPL-3.0-only
//
// Derived from Clash Meta for Android (https://github.com/MetaCubeX/ClashMetaForAndroid).
// Upstream copyright retained per THIRD-PARTY-NOTICES.md.

package all

import (
	_ "cfa/native/app"
	_ "cfa/native/common"
	_ "cfa/native/config"
	_ "cfa/native/delegate"
	_ "cfa/native/platform"
	_ "cfa/native/proxy"
	_ "cfa/native/tun"
	_ "cfa/native/tunnel"

	_ "golang.org/x/sync/semaphore"

	_ "github.com/metacubex/mihomo/log"
)
