package com.slte.app.data.remote.config

import android.content.Context
import android.content.SharedPreferences
import com.slte.app.BuildConfig
import com.slte.app.kernel.AppRemoteConfig
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import com.slte.app.utils.AppLog

/** OSS 远程下发的运行时可调配置 */
@Serializable
data class RemoteConfigData(
    /** 竞速选出的主 API 地址 */
    val apiBaseUrl: String = BuildConfig.API_BASE_URL,
    /** 全部可用 API 候选（含主地址），运行时 failover 轮询用 */
    val apiBaseUrls: List<String> = emptyList(),
    /** 直连域名列表（清洗注入 + 内核兜底用），为空时回退 apiBaseUrl 域名 */
    val directDomains: List<String> = emptyList(),
    val apiType: String = BuildConfig.API_TYPE,
    val crispWebsiteId: String = BuildConfig.CRISP_WEBSITE_ID,
    val crispEnabled: Boolean = BuildConfig.CRISP_ENABLED,
    /** 远程更新：新版本号（空 = 无更新） */
    val updateVersion: String = "",
    /** 远程更新：更新日志标题（如"更新日志"） */
    val updateChangelogTitle: String = "",
    /** 远程更新：更新日志内容 */
    val updateChangelog: String = "",
    /** 远程更新：是否强制更新 */
    val updateForce: Boolean = false,
    /** 远程更新：APK 下载地址 */
    val updateApkUrl: String = ""
)

/** OSS 配置文件原始 JSON（缺失字段回退 BuildConfig 默认值） */
@Serializable
private data class RemoteConfigDto(
    @SerialName("api_base_url") val apiBaseUrl: String? = null,
    /** 单值或数组均可：字符串按单地址解析，数组按多地址竞速解析 */
    @SerialName("api_base_urls") val apiBaseUrls: JsonElement? = null,
    @SerialName("direct_domains") val directDomains: JsonElement? = null,
    @SerialName("api_type") val apiType: String? = null,
    @SerialName("crisp_website_id") val crispWebsiteId: String? = null,
    @SerialName("crisp_enabled") val crispEnabled: Boolean? = null,
    @SerialName("update_version") val updateVersion: String? = null,
    @SerialName("update_changelog_title") val updateChangelogTitle: String? = null,
    @SerialName("update_changelog") val updateChangelog: String? = null,
    @SerialName("update_force") val updateForce: Boolean? = null,
    @SerialName("update_apk_url") val updateApkUrl: String? = null
)

/**
 * 远程配置：启动时从多个 OSS URL（REMOTE_CONFIG_URLS）随机轮询拉取 JSON。
 *
 * 容错层级：
 * 1. 配置 URL 随机轮询，逐个尝试，首个成功即用；
 * 2. 配置中的多个 API 地址并发竞速（延迟最小者为主地址），运行期连接失败自动 failover 到下一个；
 * 3. 全部失败回退本地 BuildConfig 硬编码默认值。
 */
@Singleton
class RemoteConfig @Inject constructor(
    @ApplicationContext private val context: Context
) : AppRemoteConfig {

    override val apiBaseUrl: String get() = data.apiBaseUrl

    override val directDomains: List<String> get() = data.directDomains
    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 配置源拉取客户端（复用连接池） */
    private val configClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    /** API 竞速探测客户端（复用连接池） */
    private val speedClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    /** 配置流：启动即发射缓存值，远程拉取完成后发射新值（更新检查/UI 响应式跟随） */
    private val _dataFlow = MutableStateFlow(loadCached())
    val dataFlow: StateFlow<RemoteConfigData> = _dataFlow.asStateFlow()
    val data: RemoteConfigData get() = _dataFlow.value

    /** 运行期 failover 候选列表：主地址在前，其余按序轮转 */
    fun apiCandidates(primary: String): List<String> = buildList {
        add(primary)
        addAll(dataFlow.value.apiBaseUrls.filter { it != primary })
    }

    fun startFetch() {
        scope.launch { refresh() }
    }

    /**
     * 拉取并应用远程配置：成功返回 true；全部配置源失败返回 false（保留现有缓存）。
     * 手动"检测更新"与启动自动拉取共用此入口。
     */
    suspend fun refresh(): Boolean {
        // 1. 配置 URL 顺序轮询，首个可用的配置为准
        val raw = fetchConfig() ?: return false
        val dto = try {
            json.decodeFromString<RemoteConfigDto>(raw)
        } catch (e: Exception) {
            AppLog.w("SLTE-Config", "RemoteConfig: 配置解析失败: ${e.message}")
            return false
        }

        val candidates = resolveApiCandidates(dto)
        // 2. 多 API 并发竞速选主；全部不可达时用本地硬编码默认
        val primary = pickFastest(candidates) ?: BuildConfig.API_BASE_URL

        val merged = RemoteConfigData(
            apiBaseUrl = primary,
            apiBaseUrls = candidates.ifEmpty { listOf(BuildConfig.API_BASE_URL) },
            directDomains = resolveDirectDomains(dto.directDomains),
            // 后端类型只认构建期内置值
            apiType = dto.apiType?.trim()?.takeIf { it == BuildConfig.API_TYPE } ?: BuildConfig.API_TYPE,
            crispWebsiteId = dto.crispWebsiteId?.trim()?.takeIf { it.isNotBlank() }
                ?: BuildConfig.CRISP_WEBSITE_ID,
            crispEnabled = dto.crispEnabled ?: BuildConfig.CRISP_ENABLED,
            updateVersion = dto.updateVersion?.trim() ?: "",
            updateChangelogTitle = dto.updateChangelogTitle?.trim() ?: "",
            updateChangelog = dto.updateChangelog ?: "",
            updateForce = dto.updateForce ?: false,
            updateApkUrl = dto.updateApkUrl?.trim()?.let { takeIfAllowed(it) } ?: ""
        )
        prefs.edit().putString(KEY_CACHED, json.encodeToString(merged)).apply()
        _dataFlow.value = merged
        AppLog.i("SLTE-Config", "RemoteConfig: 已更新 api=$primary candidates=${candidates.size} crisp=${merged.crispEnabled}")
        return true
    }

    /** 从 DTO 合并出 API 候选列表（单值/数组/默认值，去重） */
    private fun resolveApiCandidates(dto: RemoteConfigDto): List<String> {
        val fromArray = dto.apiBaseUrls?.let { el ->
            when (el) {
                is JsonPrimitive -> listOf(el.content)
                else -> el.jsonArray.mapNotNull { (it as? JsonPrimitive)?.content }
            }
        } ?: emptyList()
        val list = buildList {
            dto.apiBaseUrl?.let { takeIfAllowed(it) }?.let { add(it) }
            fromArray.mapNotNull { takeIfAllowed(it) }.forEach { if (it !in this) add(it) }
            if (isEmpty()) add(BuildConfig.API_BASE_URL)
        }
        return list
    }

    /** 仅接受 https 且主机在自有域名白名单内的 API 地址（凭据只发往受信域） */
    private fun takeIfAllowed(value: String): String? {
        val url = value.trim().toHttpUrlOrNull() ?: return null
        if (url.scheme != "https") return null
        val host = url.host.lowercase()
        return if (ALLOWED_HOST_SUFFIXES.any { host == it || host.endsWith(".$it") }) {
            url.toString()
        } else {
            null
        }
    }

    /** 直连域名：单值或数组均可，白名单校验后去重 */
    private fun resolveDirectDomains(element: JsonElement?): List<String> = buildList {
        val values = when (element) {
            null -> emptyList()
            is JsonPrimitive -> listOf(element.content)
            else -> element.jsonArray.mapNotNull { (it as? JsonPrimitive)?.content }
        }
        values.mapNotNull { takeDomain(it) }.forEach { if (it !in this) add(it) }
    }

    /** 直连域名校验：注册域名格式（两段以上 label），且在自有域名白名单内 */
    private fun takeDomain(value: String): String? {
        val host = value.trim().lowercase().trimEnd('.')
        if (host.isEmpty() || host.length > 253) return null
        val labels = host.split(".")
        if (labels.size < 2 || labels.any { it.isEmpty() || it.length > 63 }) return null
        return if (ALLOWED_HOST_SUFFIXES.any { host == it || host.endsWith(".$it") }) host else null
    }

    /** 配置 URL 顺序轮询：固定顺序逐个尝试（多进程行为一致，避免双进程命中不同源），返回首个成功响应体 */
    private suspend fun fetchConfig(): String? {
        val urls = BuildConfig.REMOTE_CONFIG_URLS.split(',').map { it.trim() }
            .filter { it.startsWith("https://") }
        for (url in urls) {
            try {
                val body = configClient.newCall(Request.Builder().url(url).build())
                    .execute().use { resp ->
                        if (!resp.isSuccessful) null
                        else resp.body?.byteStream()?.use { readLimited(it, MAX_CONFIG_BYTES) }
                    }
                if (body != null) return body.toString(Charsets.UTF_8)
            } catch (e: Exception) {
                AppLog.w("SLTE-Config", "RemoteConfig: 配置源不可用 $url: ${e.message}")
            }
        }
        return null
    }

    /** 限流读取响应体：超过上限返回 null（防御恶意/异常配置源 OOM） */
    private fun readLimited(input: java.io.InputStream, max: Int): ByteArray? {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(4096)
        var total = 0
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            total += n
            if (total > max) return null
            buffer.write(chunk, 0, n)
        }
        return buffer.toByteArray()
    }

    /** 多 API 并发竞速：延迟最小且可达者为主地址；全部不可达返回 null */
    private suspend fun pickFastest(urls: List<String>): String? {
        if (urls.size == 1) {
            return if (reachable(urls.first())) urls.first() else null
        }
        return coroutineScope {
            urls.map { url ->
                async(Dispatchers.IO) {
                    val start = System.currentTimeMillis()
                    try {
                        speedClient.newCall(Request.Builder().url(url.trimEnd('/') + "/api/v1/guest/comm/config").build())
                            .execute().use { resp ->
                                if (resp.code in 200..499) url to (System.currentTimeMillis() - start) else null
                            }
                    } catch (e: Exception) {
                        null
                    }
                }
            }.awaitAll().filterNotNull().minByOrNull { it.second }?.first
        }
    }

    private suspend fun reachable(url: String): Boolean {
        return try {
            speedClient.newCall(Request.Builder().url(url.trimEnd('/') + "/api/v1/guest/comm/config").build())
                .execute().use { it.code in 200..499 }
        } catch (e: Exception) {
            false
        }
    }

    private fun loadCached(): RemoteConfigData {
        val raw = prefs.getString(KEY_CACHED, null) ?: return RemoteConfigData()
        return try {
            json.decodeFromString<RemoteConfigData>(raw)
        } catch (e: Exception) {
            RemoteConfigData()
        }
    }

    private companion object {
        const val PREFS_NAME = "slte_remote_config"
        const val KEY_CACHED = "cached"
        const val MAX_CONFIG_BYTES = 256 * 1024
        /** API 域名白名单：内置自有域 + 构建期 SLTE_ALLOWED_DOMAINS 追加；配置只能在这些域内切换 */
        val ALLOWED_HOST_SUFFIXES: List<String> = buildList {
            // 占位域：部署者替换为自有 API 域名后缀（与 kernel-core process.go directDomains 保持同步）
            add("example.com")
            BuildConfig.ALLOWED_DOMAINS.split(',')
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .forEach { if (it !in this) add(it) }
        }
    }
}
