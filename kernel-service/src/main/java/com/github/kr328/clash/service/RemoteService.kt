// SPDX-FileCopyrightText: The Clash Meta for Android Authors
// SPDX-FileCopyrightText: 2026 Polaris Contributors (modifications)
// SPDX-License-Identifier: GPL-3.0-only
//
// Derived from Clash Meta for Android (https://github.com/MetaCubeX/ClashMetaForAndroid).
// Upstream copyright retained per THIRD-PARTY-NOTICES.md.

package com.github.kr328.clash.service

import android.content.Intent
import android.os.IBinder
import com.github.kr328.clash.service.remote.IClashManager
import com.github.kr328.clash.service.remote.IRemoteService
import com.github.kr328.clash.service.remote.IProfileManager
import com.github.kr328.clash.service.remote.wrap
import com.github.kr328.clash.service.util.AtomicFs
import com.github.kr328.clash.service.util.cancelAndJoinBlockingAsync
import com.github.kr328.clash.service.util.importedDir
import kotlinx.coroutines.CoroutineScope

class RemoteService : BaseService(), IRemoteService {
    private val binder = this.wrap()

    /** 仅 mihomo 分支使用；用 Any 避免 sing-box 分支加载 ClashManager 类。 */
    private var clashHolder: Any? = null
    private var profile: ProfileManager? = null
    private var clashBinder: IClashManager? = null
    private var profileBinder: IProfileManager? = null

    override fun onCreate() {
        super.onCreate()

        AtomicFs.recover(importedDir)

        profile = ProfileManager(this)
        profileBinder = profile?.wrap() as IProfileManager?

        clashBinder = ClashManager(this).also { clashHolder = it }.wrap() as IClashManager?
    }

    override fun onDestroy() {
        super.onDestroy()

        cancelAndJoinBlockingAsync(*listOfNotNull<CoroutineScope>(clashHolder as? CoroutineScope, profile).toTypedArray())
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun clash(): IClashManager {
        return clashBinder!!
    }

    override fun profile(): IProfileManager {
        return profileBinder!!
    }
}