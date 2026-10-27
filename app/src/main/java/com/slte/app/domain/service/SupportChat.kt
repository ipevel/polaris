package com.slte.app.domain.service

/** 应用内客服：实现方自行持有启动所需的上下文。 */
interface SupportChat {
    fun openChat(email: String? = null)
}
