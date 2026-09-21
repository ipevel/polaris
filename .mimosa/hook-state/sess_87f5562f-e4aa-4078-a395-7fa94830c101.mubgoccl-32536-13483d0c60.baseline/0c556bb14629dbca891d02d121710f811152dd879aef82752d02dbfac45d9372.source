package com.slte.app.data.remote.config

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.slte.app.BuildConfig
import com.slte.app.data.local.SecurePreferences
import com.slte.app.utils.AppLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class CachedConfig(
    val config: RemoteConfigData,

    val version: String = "",

    val fetchedAt: Long = 0L,

    val sourceUrl: String = "",

    val etag: String = "",
)

internal class RemoteConfigStore(
    context: Context,
    private val json: Json,
) {
    private val prefs: SharedPreferences =
        SecurePreferences.create(context, PREFS_NAME, KEY_ALIAS)

    fun load(): CachedConfig? {
        val raw = prefs.getString(KEY_CACHE, null) ?: return null
        return try {
            json.decodeFromString<CachedConfig>(raw)
        } catch (e: Exception) {
            AppLog.w("Polaris-Config", "RemoteConfig: 缓存解析失败，回退默认配置")
            null
        }
    }

    fun save(cached: CachedConfig) {
        prefs.edit {
            putString(KEY_CACHE, json.encodeToString(cached))
            putString(KEY_LAST_URL, cached.sourceUrl)
        }
    }

    fun orderedConfigUrls(): List<String> {
        val urls =
            BuildConfig.REMOTE_CONFIG_URLS
                .split(',')
                .map { it.trim() }
                .filter { it.startsWith("https://") }
        val last = prefs.getString(KEY_LAST_URL, null)
        if (last != null && last in urls) {
            return listOf(last) + urls.filter { it != last }
        }
        return urls
    }

    private companion object {
        const val PREFS_NAME = "polaris_remote_config"
        const val KEY_ALIAS = "polaris_remote_config_master_key"
        const val KEY_CACHE = "cached_config"
        const val KEY_LAST_URL = "last_url"
    }
}
