package com.slte.app.data.remote.config

import android.content.Context
import android.content.Intent
import android.util.Patterns
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
import im.crisp.client.external.ChatActivity
import im.crisp.client.external.Crisp
import im.crisp.client.external.EventsCallback
import im.crisp.client.external.data.message.Message
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrispManager
@Inject
constructor() {
    private var config: CrispConfig? = null
    private var initialized = false
    private var lastEmail: String? = null

    private val sessionCallback =
        object : EventsCallback {
            override fun onSessionLoaded(sessionId: String) {
                val email = lastEmail ?: return
                if (Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Crisp.setUserEmail(email)
                }
            }

            override fun onChatOpened() = Unit

            override fun onChatClosed() = Unit

            override fun onMessageSent(message: Message) = Unit

            override fun onMessageReceived(message: Message) = Unit

            override fun onNotificationReceived(notification: Map<String, String>) = Unit
        }

    fun init(
        context: Context,
        config: CrispConfig,
    ) {
        if (!config.enabled || config.websiteId.isNotBlank().not()) return
        if (initialized && this.config?.websiteId == config.websiteId) return
        this.config = config
        if (initialized) {
            clearUser()
        }
        Crisp.configure(context.applicationContext, config.websiteId)
        if (!initialized) {
            Crisp.addCallback(sessionCallback)
        }
        initialized = true
    }

    fun setUser(
        email: String?,
        nickname: String?,
    ) {
        if (!initialized) return
        if (!email.isNullOrBlank()) {
            val normalized = email.trim()
            lastEmail = normalized

            if (Patterns.EMAIL_ADDRESS.matcher(normalized).matches()) {
                val ok = Crisp.setUserEmail(normalized)
                if (!ok) {
                    AppLog.w(TAG, "Crisp setUserEmail 返回 false（会话可能未就绪，会在会话就绪/打开聊天时重试）")
                }
            } else {
                AppLog.w(TAG, "Crisp 邮箱格式校验失败，跳过")
            }
        }
        nickname?.let { Crisp.setUserNickname(it) }
    }

    fun openChat(
        context: Context,
        email: String? = null,
    ) {
        if (!initialized) return

        email?.let { setUser(it, null) }
        context.startActivity(Intent(context, ChatActivity::class.java))
    }

    fun clearUser() {
        if (!initialized) return
        try {
            Crisp.setUserEmail("")
            Crisp.setUserNickname("")
        } catch (e: Exception) {
            AppLog.w(TAG, "Crisp 登出清理失败（已忽略）: ${sanitizeLog(e.message ?: "Unknown")}")
        }
    }

    fun isEnabled(): Boolean = config?.enabled == true && initialized

    private companion object {
        const val TAG = "SLTE-Crisp"
    }
}
