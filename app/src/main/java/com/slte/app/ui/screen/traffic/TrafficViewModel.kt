// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screen.traffic

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slte.app.R
import com.slte.app.data.repository.TrafficRepository
import com.slte.app.domain.model.TrafficLogRecord
import com.slte.app.utils.AppLog
import com.slte.app.utils.Diagnostics
import com.slte.app.utils.sanitizeLog
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 流量日志单次加载的最长等待。
 *
 * 面板流量日志是次要数据，但"一直转圈"比失败更糟：底层 OkHttp 的
 * `API_CALL_TIMEOUT_SECONDS = 45`，面板不可达时用户要盯着「加载中…」45 秒才知道失败，
 * 期间没有任何出口（这正是用户反馈"流量页一直加载中"的观感来源）。
 * 收紧到 15 秒后走统一失败分支——错误横幅 + 重试按钮。
 */
private const val TRAFFIC_LOAD_TIMEOUT_MS = 15_000L

data class TrafficData(
    val records: List<TrafficLogRecord> = emptyList(),
    val isLoading: Boolean = true,
    @StringRes val errorMessageRes: Int? = null,
    @StringRes val toastRes: Int? = null,
    /** 失败的具体原因（HTTP 状态码 / 服务端 message），仅用于诊断展示；写入前已脱敏。 */
    val errorDetail: String? = null,
    /** 当前面板是否提供每日流量明细；false 时页面说明「面板不支持」，而不是伪装成「暂无记录」。 */
    val backendSupportsLog: Boolean = true,
)

@HiltViewModel
class TrafficViewModel
@Inject
constructor(
    private val trafficRepository: TrafficRepository,
    private val diagnostics: Diagnostics,
) : ViewModel() {
    private val _data = MutableStateFlow(TrafficData())
    val data: StateFlow<TrafficData> = _data.asStateFlow()

    /** 导出诊断时附带的上下文（后端类型/面板/构建类型/ABI）；失败时用户一键就能把它发出来。 */
    fun diagnosticsExtra(): Map<String, String> = diagnostics.extra()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        // 在途守卫：重试按钮可以连点，切 Tab 又会再触发一次 load()，
        // 不守卫就会出现并发请求 + 状态互相覆盖（后到的失败会把先到的成功结果盖掉）。
        if (loadJob?.isActive == true) return
        // 面板没有每日明细接口时不去发一个注定为空的请求：直接落到"能力缺失"态，由 UI 说明原因。
        // 此前 xiaov2b 的空实现返回空列表，页面显示「暂无流量记录」——把"面板没这个接口"
        // 伪装成"你没有流量"，用户无从判断是面板问题还是自己没用量。
        if (!trafficRepository.supportsTrafficLog()) {
            _data.update {
                it.copy(
                    records = emptyList(),
                    isLoading = false,
                    errorMessageRes = null,
                    toastRes = null,
                    errorDetail = null,
                    backendSupportsLog = false,
                )
            }
            return
        }
        _data.update {
            it.copy(isLoading = true, errorMessageRes = null, errorDetail = null, backendSupportsLog = true)
        }
        loadJob =
            viewModelScope.launch {
                // withTimeoutOrNull 而不是 withTimeout：超时是**预期分支**，不该抛
                // CancellationException 穿透 fold 的 onFailure（那会把"请求取消"和"请求超时"搅在一起）。
                // 返回 null 即超时，合成一个失败结果，与真实请求失败走同一条出口。
                val result =
                    withTimeoutOrNull(TRAFFIC_LOAD_TIMEOUT_MS) { trafficRepository.fetchTrafficLog() }
                        ?: Result.failure(IOException("traffic log 请求超时（${TRAFFIC_LOAD_TIMEOUT_MS / 1000}s）"))
                result.fold(
                    onSuccess = { records ->
                        AppLog.d("Polaris-Traffic", "fetchTrafficLog: ${records.size} 条")
                        _data.update {
                            it.copy(records = records, isLoading = false, errorMessageRes = null, toastRes = null, errorDetail = null)
                        }
                    },
                    onFailure = { e ->
                        AppLog.w("Polaris-Traffic", "fetchTrafficLog 失败: ${sanitizeLog(e.message ?: "Unknown")}")
                        val hasData = _data.value.records.isNotEmpty()
                        // 展示失败的真实原因：此前只显示通用文案，用户和我们拿不到 HTTP 状态码 /
                        // 服务端 message，排查成本极高（见 AdapterExecute 的同款注释）。
                        val detail = sanitizeLog(e.message.orEmpty()).trim().takeIf { it.isNotEmpty() }
                        _data.update {
                            it.copy(
                                isLoading = false,
                                errorMessageRes = if (hasData) null else R.string.traffic_load_failed,
                                toastRes = if (hasData) R.string.traffic_load_failed else null,
                                errorDetail = detail,
                            )
                        }
                    },
                )
            }
    }

    fun clearToast() = _data.update { it.copy(toastRes = null) }
}
