// SPDX-FileCopyrightText: The Clash Meta for Android Authors
// SPDX-FileCopyrightText: 2026 Polaris Contributors (modifications)
// SPDX-License-Identifier: GPL-3.0-only
//
// Derived from Clash Meta for Android (https://github.com/MetaCubeX/ClashMetaForAndroid).
// Upstream copyright retained per THIRD-PARTY-NOTICES.md.
// CMA 上游组件：SAF 文档访问接口，Profile 文件读写用

package com.github.kr328.clash.service.document

interface Document {
    val id: String
    val name: String
    val mimeType: String
    val size: Long
    val updatedAt: Long
    val flags: Set<Flag>
}