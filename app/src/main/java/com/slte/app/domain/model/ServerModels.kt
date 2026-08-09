package com.slte.app.domain.model

import kotlinx.serialization.Serializable

/** 服务器节点（来自 V2Board API） */
@Serializable
data class ServerNode(
    val id: Int,
    val name: String,
    val type: ServerType,
    val host: String,
    val port: Int,
    val cipher: String = "",
    val password: String = "",
    val uuid: String = "",
    val alterId: Int = 0,
    val network: String = "tcp",
    val networkSettings: String? = null,
    val tls: Boolean = false,
    val tlsSettings: String? = null,
    val serverPort: Int = 0,
    val obfs: String = "",
    val obfsSettings: String? = null,
    val obfsPassword: String = "",
    val flow: String = "",
    val sni: String = "",
    val groupId: Int = 0
)

@Serializable
enum class ServerType {
    SHADOWSOCKS, VMESS, VLESS, TROJAN, TUIC, HYSTERIA, HYSTERIA2, ANYTLS
}
