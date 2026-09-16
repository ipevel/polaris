package com.slte.app.ui.screen.about

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slte.app.BuildConfig
import com.slte.app.R
import com.slte.app.data.remote.config.RemoteConfig
import com.slte.app.di.IoDispatcher
import com.slte.app.kernel.KernelProxy
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private val _kernelVersion = MutableStateFlow<String?>(null)
    val kernelVersion: StateFlow<String?> = _kernelVersion.asStateFlow()

    private var dismissedInSession = false

    private var lastShownSignature: String? = null

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
            AppLog.i("SLTE-Update", "发现新版 ${cfg.updateVersion} force=${cfg.updateForce} manual=$manual")
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

        AppLog.i("SLTE-Update", "跳转浏览器下载 ${remoteConfig.data.updateVersion}")
        try {
            val intent =
                Intent(Intent.ACTION_VIEW, url.toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            AppLog.w("SLTE-Update", "打开下载页失败: ${sanitizeLog(e.message ?: "Unknown")}")
            _state.value = UpdateUiState.Failed(R.string.update_download_failed)
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
    }
}
