package com.slte.app.data.local

import android.content.SharedPreferences
import androidx.core.content.edit
import com.slte.app.domain.model.SiteInfo
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 站点品牌信息专用偏好（非敏感，普通 SharedPreferences 即可） */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SiteInfoPrefs

/**
 * 面板站点名称/描述的持久化存储。
 *
 * 数据来源与生命周期（与订阅挂钩）：
 * - 站点名称：订阅响应头 `profile-title`（Clash 客户端通用约定，支持 base64 前缀），
 *   由 [com.slte.app.data.remote.SubscribeSourceImpl] 在每次订阅拉取成功时写入；
 * - 描述/官网：面板 `guest/comm/config`，在订阅更新成功后随拉取刷新；
 * - 首次登录订阅时拉取并记住，之后每次重新拉取订阅时更新。
 */
@Singleton
class SiteInfoStore
@Inject
constructor(
    @SiteInfoPrefs private val prefs: SharedPreferences,
) {
    private val _siteInfo = MutableStateFlow(read())

    val siteInfo: StateFlow<SiteInfo> = _siteInfo.asStateFlow()

    /** 订阅头携带的站点名/官网（每次拉取订阅都会更新） */
    fun updateFromSubscription(
        name: String?,
        url: String?,
    ) {
        if (name.isNullOrBlank() && url.isNullOrBlank()) return
        val current = _siteInfo.value
        val next =
            current.copy(
                appName = name?.takeIf { it.isNotBlank() } ?: current.appName,
                appUrl = url?.takeIf { it.isNotBlank() } ?: current.appUrl,
            )
        if (next == current) return
        persist(next)
        _siteInfo.value = next
    }

    /** 面板 comm/config 的描述（订阅更新成功后刷新），名称以订阅头为准不覆盖 */
    fun updateFromPanelConfig(
        description: String?,
        url: String? = null,
    ) {
        if (description.isNullOrBlank() && url.isNullOrBlank()) return
        val current = _siteInfo.value
        val next =
            current.copy(
                appDescription = description?.takeIf { it.isNotBlank() } ?: current.appDescription,
                appUrl = url?.takeIf { it.isNotBlank() } ?: current.appUrl,
            )
        if (next == current) return
        persist(next)
        _siteInfo.value = next
    }

    private fun read(): SiteInfo = SiteInfo(
        appName = prefs.getString(KEY_NAME, null)?.takeIf { it.isNotBlank() },
        appDescription = prefs.getString(KEY_DESCRIPTION, null)?.takeIf { it.isNotBlank() },
        appUrl = prefs.getString(KEY_URL, null)?.takeIf { it.isNotBlank() },
    )

    private fun persist(value: SiteInfo) {
        prefs.edit {
            if (value.appName.isNullOrBlank()) remove(KEY_NAME) else putString(KEY_NAME, value.appName)
            if (value.appDescription.isNullOrBlank()) remove(KEY_DESCRIPTION) else putString(KEY_DESCRIPTION, value.appDescription)
            if (value.appUrl.isNullOrBlank()) remove(KEY_URL) else putString(KEY_URL, value.appUrl)
        }
    }

    companion object {
        const val PREFS_NAME = "site_info"

        private const val KEY_NAME = "site_name"
        private const val KEY_DESCRIPTION = "site_description"
        private const val KEY_URL = "site_url"
    }
}
