// SPDX-FileCopyrightText: The Clash Meta for Android Authors
// SPDX-FileCopyrightText: 2026 Polaris Contributors (modifications)
// SPDX-License-Identifier: GPL-3.0-only
//
// Derived from Clash Meta for Android (https://github.com/MetaCubeX/ClashMetaForAndroid).
// Upstream copyright retained per THIRD-PARTY-NOTICES.md.

package com.github.kr328.clash.core.model

import android.os.Parcel
import android.os.Parcelable
import kotlinx.serialization.Serializable

/**
 * 单节点测速结论。
 *
 * [kind] 为失败分类（见 [KIND_ALIVE] / [KIND_TIMEOUT] / [KIND_OFFLINE]）。
 * 只有 [KIND_OFFLINE] 才代表"这个节点确实没了"，应用侧据此打离线角标；
 * 超时可能只是网络抖动，标离线会误伤活节点。
 */
@Serializable
data class UrlTestResult(
    val delay: Int,
    val kind: String,
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readString()!!,
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(delay)
        parcel.writeString(kind)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<UrlTestResult> {
        /** 存活：拿到了延迟，没有失败分类。 */
        const val KIND_ALIVE = ""

        /** 在节点表里但没回包（解析超时、TLS 异常等）。保守起见不标离线。 */
        const val KIND_TIMEOUT = "timeout"

        /** 明确不可达（域名不存在 / 连接被拒 / 无路由）。 */
        const val KIND_OFFLINE = "offline"

        override fun createFromParcel(parcel: Parcel): UrlTestResult {
            return UrlTestResult(parcel)
        }

        override fun newArray(size: Int): Array<UrlTestResult?> {
            return arrayOfNulls(size)
        }
    }
}
