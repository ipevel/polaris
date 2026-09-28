// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.about

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slte.app.BuildConfig
import com.slte.app.R
import com.slte.app.data.local.SiteInfoStore
import com.slte.app.data.remote.config.RemoteConfig
import com.slte.app.di.IoDispatcher
import com.slte.app.domain.model.SiteInfo
import com.slte.app.kernel.KernelProxy
import com.slte.app.ui.component.openExternalUrl
import com.slte.app.utils.AppLog
import com.slte.app.utils.Diagnostics
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

/**
 * 更新载荷自洽性：版本号、APK 直链、SHA-256 必须同时指向同一个已发布的包。
 *
 * 存在的理由：发布流程曾允许在 Release 产出**之前**先把 remote.json 的 update_version 改成新版本，
 * 此时直链与校验和仍是上一版的。App 只看版本号就提示更新，用户会跳到 Releases 页却找不到对应版本
 * 的下载项（历史上应用内安装时则会下载到旧包，装完版本没变又提示更新，形成无限循环）。这里要求三者
 * 同源，任一不自洽就不提示更新：宁可少提示，也不能把未发布的版本当新版本提示。
 */
internal fun isUpdatePayloadConsistent(
    updateVersion: String,
    apkUrl: String?,
    apkSha256: String?,
): Boolean {
    val version = updateVersion.trim().trimStart('v')
    if (version.isBlank()) return false
    val url = apkUrl?.trim().orEmpty()
    if (!url.startsWith("https://")) return false
    // 直链必须指向所声明版本的 tag 或资产名，且版本号后不能再接数字/点（防 1.4.14 误配 v1.4.140）
    val versionInUrl = Regex("""v${Regex.escape(version)}(?![\d.])""")
    if (!versionInUrl.containsMatchIn(url)) return false
    val sha = apkSha256?.trim().orEmpty()
    return sha.length == SHA256_HEX_LENGTH && sha.all { it.isHexDigit() }
}

/**
 * 从 APK 直链推导 GitHub Release 页地址。
 *
 * 更新方式已从「应用内下载并安装」改为「跳转 Releases 页由用户自行下载」：直链本身不再用于下载，
 * 但仍作为「对应版本确实已发布」的自洽性凭证，并据此定位下载页：
 * `https://github.com/<owner>/<repo>/releases/download/<tag>/<asset>` → `https://github.com/<owner>/<repo>/releases`。
 *
 * 只接受 https，且 `/releases` 段必须位于主机之后（防止把 `https://releases.example.com/...`
 * 这类域名误当成 release 路径）；无法识别时返回 null，由调用方报错，而不是打开可疑地址。
 */
internal fun releasePageUrl(apkUrl: String?): String? {
    val url = apkUrl?.trim().orEmpty()
    if (!url.startsWith("https://")) return null
    val idx = url.indexOf("/releases")
    if (idx <= "https://".length) return null
    return url.substring(0, idx) + "/releases"
}

private const val SHA256_HEX_LENGTH = 64

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

internal fun shouldShowUpdateDialog(
    updateVersion: String,
    currentVersion: String,
    force: Boolean,
    dismissedInSession: Boolean,
    manual: Boolean,
    apkUrl: String?,
    apkSha256: String?,
): Boolean {
    if (updateVersion.isBlank() || compareVersions(updateVersion, currentVersion) <= 0) return false
    if (!isUpdatePayloadConsistent(updateVersion, apkUrl, apkSha256)) return false
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
    private val siteInfoStore: SiteInfoStore,
    @ApplicationContext private val context: Context,
    private val diagnostics: Diagnostics,
) : ViewModel() {
    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private val _siteInfo = MutableStateFlow<SiteInfo?>(null)
    val siteInfo: StateFlow<SiteInfo?> = _siteInfo.asStateFlow()

    private val _kernelVersion = MutableStateFlow<String?>(null)
    val kernelVersion: StateFlow<String?> = _kernelVersion.asStateFlow()

    /** 导出诊断时附带的上下文；内核版本由本页负责查询，所以在基础字段上补一条。 */
    fun diagnosticsExtra(): Map<String, String> = diagnostics.extra() + mapOf("内核版本" to (_kernelVersion.value ?: "-"))

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
            val newer =
                cfg.updateVersion.isNotBlank() &&
                    compareVersions(cfg.updateVersion, BuildConfig.VERSION_NAME) > 0
            val consistent = isUpdatePayloadConsistent(cfg.updateVersion, cfg.updateApkUrl, cfg.updateApkSha256)
            val show =
                shouldShowUpdateDialog(
                    updateVersion = cfg.updateVersion,
                    currentVersion = BuildConfig.VERSION_NAME,
                    force = cfg.updateForce,
                    dismissedInSession = dismissedInSession,
                    manual = manual,
                    apkUrl = cfg.updateApkUrl,
                    apkSha256 = cfg.updateApkSha256,
                )
            if (!show) {
                lastShownSignature = null
                _state.value =
                    when {
                        // 确有新版本但发布元数据不自洽：这是发布侧故障，明确报错而不是谎报「已是最新」
                        manual && newer && !consistent -> UpdateUiState.Error
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

    /**
     * 跳转到 GitHub Releases 页，由用户自行下载并覆盖安装。
     *
     * 不再在应用内下载/安装 APK：应用内安装要清理并校验中间产物、依赖 FileProvider 与「安装未知应用」
     * 授权，且发布元数据一旦不自洽就会陷入「装完还是旧版」的循环。改由系统浏览器 + 系统安装器承接后，
     * 用户以**覆盖安装**方式升级即可 —— 同一包名、同一签名且 versionCode 更高时，Android 会保留其应用
     * 数据与配置；只有「先卸载再安装」才会清空，故 UI 侧同时提示用户不要先卸载。
     */
    fun openReleasePage() {
        if (_state.value !is UpdateUiState.Available) return
        val url = releasePageUrl(remoteConfig.data.updateApkUrl)
        if (url == null) {
            AppLog.w("Polaris-Update", "APK 直链无法推导 Release 页地址，已阻止跳转")
            _state.value = UpdateUiState.Failed(R.string.update_apk_missing)
            return
        }
        if (!openExternalUrl(context, url)) {
            _state.value = UpdateUiState.Failed(R.string.update_open_failed)
            return
        }
        AppLog.i("Polaris-Update", "已跳转 Release 页: $url")
        // 用户已离开去下载页，本次会话不再自动弹窗（force 更新不受影响）
        dismissedInSession = true
        _state.value = UpdateUiState.Idle
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
