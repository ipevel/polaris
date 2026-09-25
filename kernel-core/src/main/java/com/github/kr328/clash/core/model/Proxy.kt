// SPDX-FileCopyrightText: The Clash Meta for Android Authors
// SPDX-FileCopyrightText: 2026 Polaris Contributors (modifications)
// SPDX-License-Identifier: GPL-3.0-only
//
// Derived from Clash Meta for Android (https://github.com/MetaCubeX/ClashMetaForAndroid).
// Upstream copyright retained per THIRD-PARTY-NOTICES.md.

package com.github.kr328.clash.core.model

import android.os.Parcel
import android.os.Parcelable
import com.github.kr328.clash.core.util.Parcelizer
import kotlinx.serialization.Serializable

@Serializable
data class Proxy(
    val name: String,
    val title: String,
    val subtitle: String,
    val type: String,
    val delay: Int,
    var isGroup: Boolean,
    /**
     * 该节点是否已跑过至少一次测速。与 [delay] 组合区分三态：
     * `tested=false` → 从未测过（「未测」）；`tested=true && delay=0xffff` →
     * 测过但不存活（「超时」）；否则为真实延迟。
     * 内核侧由 `Proxy.Tested` 填充（native/tunnel/proxies.go）。
     * 默认值 false 保证与旧版内核混跑时可解析（未测）。
     */
    val tested: Boolean = false,
) : Parcelable {
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        Parcelizer.encodeToParcel(serializer(), parcel, this)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Proxy> {
        override fun createFromParcel(parcel: Parcel): Proxy {
            return Parcelizer.decodeFromParcel(serializer(), parcel)
        }

        override fun newArray(size: Int): Array<Proxy?> {
            return arrayOfNulls(size)
        }
    }
}
