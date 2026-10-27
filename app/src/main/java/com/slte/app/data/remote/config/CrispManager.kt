package com.slte.app.data.remote.config

import android.content.Context
import android.content.Intent
import android.util.Patterns
import com.slte.app.domain.service.SupportChat
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
constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) : SupportChat {
    private var config: CrispConfig? = null
    private var appContext: Context? = null
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
        val applicationContext = context.applicationContext
        appContext = applicationContext
        if (!config.enabled || config.websiteId.isNotBlank().not()) return
        if (initialized && this.config?.websiteId == config.websiteId) return
        this.config = config
        if (initialized) {
            clearUser()
        }
        Crisp.configure(applicationContext, config.websiteId)
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

    override fun openChat(email: String?) {
        if (!initialized) return

        email?.let { setUser(it, null) }
        // 单例持有的是 Application Context，启动 Activity 必须带 NEW_TASK
        context.startActivity(
            Intent(context, ChatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /**
     * 登出清理：重置客服会话（SDK 官方接口）。
     * 不能用 setUserEmail("") 代替——空邮箱会让之后的匿名会话渲染不出聊天页。
     */
    fun clearUser() {
        val context = appContext ?: return
        lastEmail = null
        if (!initialized) return
        try {
            Crisp.resetChatSession(context)
        } catch (e: Exception) {
            AppLog.w(TAG, "Crisp 登出清理失败（已忽略）: ${sanitizeLog(e.message ?: "Unknown")}")
        }
    }

    private companion object {
        const val TAG = "SLTE-Crisp"
    }
}
