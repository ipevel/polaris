// SPDX-FileCopyrightText: The Clash Meta for Android Authors
// SPDX-FileCopyrightText: 2026 Polaris Contributors (modifications)
// SPDX-License-Identifier: GPL-3.0-only
//
// Derived from Clash Meta for Android (https://github.com/MetaCubeX/ClashMetaForAndroid).
// Upstream copyright retained per THIRD-PARTY-NOTICES.md.

package com.github.kr328.clash.service.document

import android.provider.DocumentsContract
import java.io.File

class FileDocument(
    val file: File,
    override val flags: Set<Flag>,
    private val idOverride: String? = null,
    private val nameOverride: String? = null,
) : Document {
    override val id: String
        get() = idOverride ?: file.name
    override val name: String
        get() = nameOverride ?: file.name
    override val mimeType: String
        get() = if (file.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else "text/plain"
    override val size: Long
        get() = file.length()
    override val updatedAt: Long
        get() = file.lastModified()
}