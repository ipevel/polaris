// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.remote.adapter.xboard

import com.slte.app.domain.model.Notice
import com.slte.app.domain.model.ServerNode
import com.slte.app.domain.model.ServerType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class XboardNoticeData(
    val id: Int = 0,
    val title: String = "",
    val content: String = "",
    val tags: List<String>? = null,
    val show: Boolean = true,
    @SerialName("img_url")
    val imgUrl: String? = null,
    @SerialName("created_at")
    val createdAt: Long = 0,
    @SerialName("updated_at")
    val updatedAt: Long = 0,
)

fun XboardNoticeData.toDomain() = Notice(
    id = id,
    title = title,
    body = content,
    tags = tags ?: emptyList(),
    createdAt = createdAt,
)

@Serializable
data class XboardServerData(
    val id: Int = 0,

    val type: String = "",
    val version: String? = null,
    val name: String = "",

    val rate: JsonElement? = null,
    val tags: List<String>? = null,
    @SerialName("is_online")
    val isOnline: Int = 1,
) {
    private fun resolveType(): ServerType = when (type) {
        "shadowsocks" -> ServerType.SHADOWSOCKS
        "vmess" -> ServerType.VMESS
        "vless" -> ServerType.VLESS
        "trojan" -> ServerType.TROJAN
        "tuic" -> ServerType.TUIC
        "hysteria" -> ServerType.HYSTERIA
        "hysteria2" -> ServerType.HYSTERIA2
        "anytls" -> ServerType.ANYTLS
        else -> ServerType.SHADOWSOCKS
    }

    fun toServerNode() = ServerNode(
        id = id,
        name = name,
        type = resolveType(),
        host = "",
        port = 0,
    )
}
