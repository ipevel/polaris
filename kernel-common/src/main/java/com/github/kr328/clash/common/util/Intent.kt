// SPDX-FileCopyrightText: The Clash Meta for Android Authors
// SPDX-FileCopyrightText: 2026 Polaris Contributors (modifications)
// SPDX-License-Identifier: GPL-3.0-only
//
// Derived from Clash Meta for Android (https://github.com/MetaCubeX/ClashMetaForAndroid).
// Upstream copyright retained per THIRD-PARTY-NOTICES.md.
// CMA 上游组件：Intent UUID/文件名/权限扩展，Profile 传递用

package com.github.kr328.clash.common.util

import android.content.Intent
import android.net.Uri
import java.util.*

fun Intent.grantPermissions(read: Boolean = true, write: Boolean = true): Intent {
    var flags = 0

    if (read)
        flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION

    if (write)
        flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    addFlags(flags)

    return this
}

var Intent.fileName: String?
    get() {
        return data?.takeIf { it.scheme == "file" }?.schemeSpecificPart
    }
    set(value) {
        data = Uri.fromParts("file", value, null)
    }

var Intent.uuid: UUID?
    get() {
        return data?.takeIf { it.scheme == "uuid" }?.schemeSpecificPart?.let(UUID::fromString)
    }
    set(value) {
        data = Uri.fromParts("uuid", value.toString(), null)
    }

fun Intent.setUUID(uuid: UUID): Intent {
    this.uuid = uuid

    return this
}

fun Intent.setFileName(fileName: String): Intent {
    this.fileName = fileName

    return this
}