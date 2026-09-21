package com.slte.app.data.remote.config

import android.content.Context
import com.slte.app.BuildConfig
import com.slte.app.data.local.ApiUrlStore
import com.slte.app.kernel.AppRemoteConfig
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class RemoteConfigData(

    val apiBaseUrl: String = BuildConfig.API_BASE_URL,

    val apiBaseUrls: List<String> = emptyList(),

    val directDomains: List<String> = emptyList(),
    val apiType: String = BuildConfig.API_TYPE,

    val updateVersion: String = "",

    val updateChangelogTitle: String = "",

    val updateChangelog: String = "",

    val updateForce: Boolean = false,

    val updateApkUrl: String = "",

    val updateApkSha256: String = "",
)

@Singleton
class RemoteConfig
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val apiUrlStore: ApiUrlStore,
) : AppRemoteConfig,
    FailoverConfig {

    /**
     * 实际生效的 API 基地址：唯一来源是用户在登录页输入并保存的面板地址，
     * App 不内置、不兜底任何默认面板地址（未配置时为空串，网络层快速失败）。
     * ApiFailoverInterceptor 每次请求都会读它来改写主机，改地址即时生效。
     */
    override val apiBaseUrl: String get() = apiUrlStore.effectiveBaseUrl

    override val directDomains: List<String> get() = data.directDomains

    private val selector = EndpointSelector()

    val endpointSelector: EndpointSelector get() = selector

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val store = RemoteConfigStore(context, json)

    private val prober = RemoteConfigProber(selector)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val etagByUrl = ConcurrentHashMap<String, String>()

    private val configClient: OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(CONFIG_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(CONFIG_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CONFIG_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    private val _dataFlow = MutableStateFlow(sanitizeCachedConfig(store.load()?.config))
    val dataFlow: StateFlow<RemoteConfigData> = _dataFlow.asStateFlow()
    val data: RemoteConfigData get() = _dataFlow.value

    override fun apiCandidates(primary: String): List<String> {
        // 仅以用户输入并保存的面板地址为准，不提供任何内置候选、不做兜底：
        // 未配置地址时返回空列表，由 ApiFailoverInterceptor 快速失败。
        return apiUrlStore.currentUrl?.let { listOf(it) } ?: emptyList()
    }

    fun startFetch() {
        scope.launch { refresh(force = true) }
    }

    fun startProbeLoop() {
        scope.launch { prober.loop(PROBE_LOOP_INTERVAL_MS) }
    }

    suspend fun refresh(force: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        val cached = store.load()
        if (!force && cached != null && ConfigValidation.isCacheFresh(cached.fetchedAt, now, CONFIG_CACHE_TTL_MS)) {
            _dataFlow.value = sanitizeCachedConfig(cached.config)
            return true
        }

        val urls = store.orderedConfigUrls()
        if (urls.isEmpty()) return false
        val result =
            try {
                withTimeout(CONFIG_FETCH_TIMEOUT_MS) {
                    ConfigRace.race(urls) { url -> fetchOne(url, cached) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Polaris-Config", "RemoteConfig: 配置竞速失败: ${sanitize(e.message)}")
                return false
            }
        val chosen = result.chosen ?: return false

        if (chosen.notModified && cached != null) {
            store.save(cached.copy(fetchedAt = now, sourceUrl = chosen.url))
            _dataFlow.value = sanitizeCachedConfig(cached.config)
            AppLog.i("Polaris-Config", "RemoteConfig: 304 命中，复用缓存")
            return true
        }

        val dto =
            try {
                json.decodeFromString<RemoteConfigDto>(chosen.raw)
            } catch (e: Exception) {
                AppLog.w("Polaris-Config", "RemoteConfig: 配置解析失败: ${sanitize(e.message)}")
                return false
            }

        val candidates = RemoteConfigParser.resolveApiCandidates(dto)
        val probes = prober.probeAll(candidates)
        probes.forEach { (url, latency) -> selector.recordProbe(url, latency) }
        val primary =
            selector.pickPrimary(
                candidates = candidates,
                probes = probes,
                currentPrimary = data.apiBaseUrl.takeIf { it in candidates },
            ) ?: BuildConfig.API_BASE_URL
        selector.updatePrimary(primary)

        val compatible = candidates.filter { ConfigValidation.hasSamePath(it, primary) }
        if (compatible.size != candidates.size) {
            AppLog.w(
                "Polaris-Config",
                "RemoteConfig: ${candidates.size - compatible.size} 个候选与主地址 path 不一致，已剔除（failover 只重写主机）",
            )
        }

        val merged = RemoteConfigParser.buildMergedConfig(dto, primary, compatible)
        store.save(
            CachedConfig(
                config = merged,
                version = chosen.version,
                fetchedAt = now,
                sourceUrl = chosen.url,
                etag = etagByUrl[chosen.url] ?: "",
            ),
        )
        _dataFlow.value = merged
        AppLog.i(
            "Polaris-Config",
            "RemoteConfig: 已更新 version=${chosen.version} candidates=${candidates.size}",
        )
        return true
    }

    private suspend fun fetchOne(
        url: String,
        cached: CachedConfig?,
    ): FetchedConfig? {
        val start = System.currentTimeMillis()
        return try {
            val builder = Request.Builder().url(url)
            etagByUrl[url]?.takeIf { it.isNotBlank() }?.let { builder.header("If-None-Match", it) }
            configClient.newCall(builder.build()).execute().use { resp ->
                val elapsed = System.currentTimeMillis() - start
                when {
                    resp.code == 304 -> {
                        if (cached != null && cached.sourceUrl == url && cached.etag == etagByUrl[url]) {
                            FetchedConfig(url, "", cached.version, elapsed, notModified = true)
                        } else {
                            null
                        }
                    }
                    resp.isSuccessful -> {
                        val body =
                            resp.body?.byteStream()?.use { readLimited(it, MAX_CONFIG_BYTES) }
                                ?: return@use null
                        val raw = body.toString(Charsets.UTF_8)
                        val dto =
                            try {
                                json.decodeFromString<RemoteConfigDto>(raw)
                            } catch (_: Exception) {
                                null
                            } ?: return@use null
                        if (!RemoteConfigParser.validateDto(dto)) return@use null
                        etagByUrl[url] = resp.header("ETag") ?: ""
                        FetchedConfig(url, raw, dto.configVersion ?: "", elapsed)
                    }
                    else -> null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("Polaris-Config", "RemoteConfig: 配置源不可用: ${sanitize(e.message)}")
            null
        }
    }

    private fun readLimited(
        input: java.io.InputStream,
        max: Int,
    ): ByteArray? {
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

    private fun sanitize(message: String?): String = message?.let { sanitizeLog(it) } ?: "Unknown"

    /**
     * 缓存端点必须重新过白名单：旧版本可能缓存了已下线的域名，
     * 直接进内存会被 AppModule 当作 baseUrl，而 AuthInterceptor 会因主机
     * 不在白名单而静默剥离 Authorization，导致所有 user 路径下的请求失败。
     * 主域名不合法时丢弃整份缓存、回落 BuildConfig 默认值；候选项同样过滤。
     */
    private fun sanitizeCachedConfig(cached: RemoteConfigData?): RemoteConfigData {
        if (cached == null) return RemoteConfigData()

        val allowedCandidates = cached.apiBaseUrls.filter { AllowedHosts.isAllowedHost(hostOf(it)) }
        val staleHost = hostOf(cached.apiBaseUrl)

        if (AllowedHosts.isAllowedHost(staleHost)) {
            if (allowedCandidates.size == cached.apiBaseUrls.size) return cached
            AppLog.w("Polaris-Config", "RemoteConfig: 已剔除 ${cached.apiBaseUrls.size - allowedCandidates.size} 个不在白名单的候选端点")
            return cached.copy(apiBaseUrls = allowedCandidates)
        }

        AppLog.w("Polaris-Config", "RemoteConfig: 丢弃非法缓存端点 ${sanitizeLog(staleHost ?: "无法解析")}，回落 BuildConfig 默认配置")
        return RemoteConfigData().copy(apiBaseUrls = allowedCandidates)
    }

    private fun hostOf(url: String?): String? = url?.let { runCatching { java.net.URI(it).host }.getOrNull() }?.takeIf { it.isNotBlank() }

    private companion object {
        const val MAX_CONFIG_BYTES = 256 * 1024
        const val CONFIG_TIMEOUT_SECONDS = 3L
        const val CONFIG_FETCH_TIMEOUT_MS = 5_000L

        const val CONFIG_CACHE_TTL_MS = 5 * 60_000L

        const val PROBE_LOOP_INTERVAL_MS = 15_000L
    }
}
