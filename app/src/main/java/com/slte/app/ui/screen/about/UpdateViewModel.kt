package com.slte.app.ui.screen.about

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slte.app.BuildConfig
import com.slte.app.R
import com.slte.app.data.local.SiteInfoStore
import com.slte.app.data.remote.config.RemoteConfig
import com.slte.app.di.IoDispatcher
import com.slte.app.domain.model.SiteInfo
import com.slte.app.kernel.KernelProxy
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient

internal fun shouldShowUpdateDialog(
    updateVersion: String,
    currentVersion: String,
    force: Boolean,
    dismissedInSession: Boolean,
    manual: Boolean,
): Boolean {
    if (updateVersion.isBlank() || compareVersions(updateVersion, currentVersion) <= 0) return false
    if (force) return true
    return manual || !dismissedInSession
}

internal fun compareVersions(
    a: String,
    b: String,
): Int {
    val pa = a.trimStart('v').split('.', '-').map { it.toIntOrNull() ?: 0 }
    val pb = b.trimStart('v').split('.', '-').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val x = pa.getOrElse(i) { 0 }
        val y = pb.getOrElse(i) { 0 }
        if (x != y) return x - y
    }
    return 0
}

sealed interface UpdateUiState {

    data object Idle : UpdateUiState

    data object Checking : UpdateUiState

    data class Available(
        val versionName: String,
        val changelogTitle: String?,
        val changelog: String?,
        val force: Boolean,
    ) : UpdateUiState

    data class Downloading(
        val progress: Int = 0,
    ) : UpdateUiState

    data class DownloadFailed(
        val messageRes: Int,
    ) : UpdateUiState

    data object Latest : UpdateUiState

    data object Error : UpdateUiState

    data class Failed(
        val messageRes: Int,
    ) : UpdateUiState
}

@HiltViewModel
class UpdateViewModel
@Inject
constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val remoteConfig: RemoteConfig,
    private val kernelProxy: KernelProxy,
    private val siteInfoStore: SiteInfoStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private val _siteInfo = MutableStateFlow<SiteInfo?>(null)
    val siteInfo: StateFlow<SiteInfo?> = _siteInfo.asStateFlow()

    private val _kernelVersion = MutableStateFlow<String?>(null)
    val kernelVersion: StateFlow<String?> = _kernelVersion.asStateFlow()

    private var dismissedInSession = false

    private var lastShownSignature: String? = null

    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    init {
        viewModelScope.launch {
            repeat(10) {
                _kernelVersion.value = kernelProxy.coreVersion()
                if (_kernelVersion.value != null) return@launch
                delay(1000)
            }
        }

        viewModelScope.launch {
            remoteConfig.dataFlow.collect {
                checkUpdate()
            }
        }

        // 站点名/描述由订阅生命周期维护（订阅头 + comm/config 写入 SiteInfoStore），
        // 关于页只观察回放
        viewModelScope.launch {
            siteInfoStore.siteInfo.collect { _siteInfo.value = it }
        }
    }

    fun checkUpdate(manual: Boolean = false) {
        if (_state.value is UpdateUiState.Checking) return

        if (manual && _state.value !is UpdateUiState.Available) {
            _state.value = UpdateUiState.Checking
        }
        viewModelScope.launch {
            if (manual) {
                withTimeoutOrNull(REFRESH_TIMEOUT_MS) {
                    withContext(ioDispatcher) { remoteConfig.refresh(force = true) }
                }
            }
            val cfg = remoteConfig.data
            val signature = "${cfg.updateVersion}|${cfg.updateForce}"
            if (_state.value is UpdateUiState.Available && signature == lastShownSignature) return@launch
            _state.value = UpdateUiState.Checking
            val show =
                shouldShowUpdateDialog(
                    updateVersion = cfg.updateVersion,
                    currentVersion = BuildConfig.VERSION_NAME,
                    force = cfg.updateForce,
                    dismissedInSession = dismissedInSession,
                    manual = manual,
                )
            if (!show) {
                lastShownSignature = null
                _state.value =
                    when {
                        manual && cfg.updateVersion.isBlank() -> UpdateUiState.Error
                        manual -> UpdateUiState.Latest
                        else -> UpdateUiState.Idle
                    }
                return@launch
            }
            AppLog.i("Polaris-Update", "发现新版 ${cfg.updateVersion} force=${cfg.updateForce} manual=$manual")
            lastShownSignature = signature
            _state.value =
                UpdateUiState.Available(
                    versionName = cfg.updateVersion,
                    changelogTitle = cfg.updateChangelogTitle.ifBlank { null },
                    changelog = cfg.updateChangelog.ifBlank { null },
                    force = cfg.updateForce,
                )
        }
    }

    fun updateNow() {
        val current = _state.value as? UpdateUiState.Available ?: return
        val url = remoteConfig.data.updateApkUrl
        if (!url.startsWith("https://")) {
            _state.value = UpdateUiState.Failed(R.string.update_apk_missing)
            return
        }
        if (_state.value is UpdateUiState.Downloading) return

        _state.value = UpdateUiState.Downloading(progress = 0)
        viewModelScope.launch {
            val result =
                withContext(ioDispatcher) {
                    runCatching { downloadApk(url) { pct -> _state.value = UpdateUiState.Downloading(progress = pct) } }
                }
            result.onSuccess { apkFile ->
                AppLog.i("Polaris-Update", "APK 下载完成: ${apkFile.name} size=${apkFile.length()}")
                val expectedSha = remoteConfig.data.updateApkSha256
                if (expectedSha.isNotBlank()) {
                    val actualSha = fileSha256(apkFile)
                    if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                        AppLog.w("Polaris-Update", "APK 哈希校验失败: expected=$expectedSha actual=$actualSha")
                        apkFile.delete()
                        _state.value = UpdateUiState.DownloadFailed(R.string.update_hash_mismatch)
                        return@launch
                    }
                    AppLog.i("Polaris-Update", "APK 哈希校验通过")
                } else {
                    AppLog.w("Polaris-Update", "APK 无 SHA-256 校验和，跳过完整性验证")
                }
                _state.value = UpdateUiState.Idle
                installApk(apkFile)
            }.onFailure { e ->
                AppLog.w("Polaris-Update", "APK 下载失败: ${sanitizeLog(e.message ?: "Unknown")}")
                _state.value = UpdateUiState.DownloadFailed(R.string.update_download_failed)
            }
        }
    }

    private fun downloadApk(
        url: String,
        onProgress: (Int) -> Unit,
    ): java.io.File {
        val dir =
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
        val target = java.io.File(dir, "polaris-update.apk")
        val request = okhttp3.Request.Builder().url(url).build()
        downloadClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw java.io.IOException("empty body")
            val total = body.contentLength()
            var readBytes = 0L
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        readBytes += n
                        if (total > 0L) {
                            onProgress(((readBytes * 100L) / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
            onProgress(100)
        }
        return target
    }

    private fun fileSha256(file: java.io.File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun installApk(file: java.io.File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            context.startActivity(intent)
        } catch (e: Exception) {
            AppLog.w("Polaris-Update", "拉起安装器失败: ${sanitizeLog(e.message ?: "Unknown")}")
            _state.value = UpdateUiState.DownloadFailed(R.string.update_download_failed)
        }
    }

    fun later() {
        dismissedInSession = true
        _state.value = UpdateUiState.Idle
    }

    fun dismiss() {
        dismissedInSession = true
        _state.value = UpdateUiState.Idle
    }

    fun consumeTip() {
        _state.value = UpdateUiState.Idle
    }

    private companion object {

        const val REFRESH_TIMEOUT_MS = 6_000L

        const val DOWNLOAD_TIMEOUT_SECONDS = 60L

        const val DOWNLOAD_BUFFER_SIZE = 8 * 1024
    }
}
