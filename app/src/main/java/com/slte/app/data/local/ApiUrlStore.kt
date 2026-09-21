package com.slte.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 运行时面板地址存储：登录页提交的自定义面板地址是唯一来源。
 *
 * - 不内置、不兜底任何默认地址：未保存地址时 [effectiveBaseUrl] 为空串，
 *   由网络层视为「面板地址未配置」并快速失败；
 * - 地址在登录提交时写入，FailoverConfig / AuthInterceptor 在请求期实时读取，
 *   改完地址无需重启进程即可生效。
 */
@Singleton
class ApiUrlStore
@Inject
constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = SecurePreferences.create(context, PREFS_NAME, KEY_ALIAS)

    private val _urlFlow = MutableStateFlow(readPersistedUrl())

    /** 已保存的用户面板地址流；null 表示未配置 */
    val urlFlow: StateFlow<String?> = _urlFlow.asStateFlow()

    /** 当前生效的用户面板地址（同步读取，供拦截器在请求线程使用） */
    val currentUrl: String?
        get() = _urlFlow.value

    /** 实际生效的 API 基地址；未配置时为空串（不回退任何内置地址） */
    val effectiveBaseUrl: String
        get() = currentUrl?.takeIf { it.toHttpUrlOrNull() != null }.orEmpty()

    /**
     * 保存自定义面板地址（null / 空白 = 清除，回退默认地址）。
     * 先落盘再更新内存流，保证后续请求立刻使用新地址。
     */
    suspend fun setUrl(url: String?) {
        val normalized = normalize(raw = url)
        withContext(Dispatchers.IO) {
            prefs.edit {
                if (normalized == null) remove(KEY_PANEL_URL) else putString(KEY_PANEL_URL, normalized)
            }
        }
        _urlFlow.value = normalized
    }

    /**
     * 判断 host 是否属于当前自定义面板的主机（含子域名）。
     * AuthInterceptor 用它把自定义面板并入 token 注入白名单，否则登录会失败。
     */
    fun isCurrentHost(host: String?): Boolean {
        val customHost = currentUrl?.toHttpUrlOrNull()?.host?.lowercase() ?: return false
        val target = host?.lowercase() ?: return false
        return target == customHost || target.endsWith(".$customHost")
    }

    private fun readPersistedUrl(): String? = normalize(raw = prefs.getString(KEY_PANEL_URL, null))

    private fun normalize(raw: String?): String? = raw?.trim().orEmpty().trimEnd('/').takeIf { it.isNotEmpty() }

    private companion object {
        const val PREFS_NAME = "polaris_api_url"
        const val KEY_ALIAS = "polaris_api_url_master_key"
        const val KEY_PANEL_URL = "panel_url"
    }
}
