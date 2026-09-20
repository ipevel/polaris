package com.slte.app.ui.screen.traffic

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slte.app.R
import com.slte.app.data.repository.SubscribeRepository
import com.slte.app.data.repository.TrafficRepository
import com.slte.app.domain.model.SubscribeInfo
import com.slte.app.domain.model.TrafficLogRecord
import com.slte.app.domain.model.isPlanValid
import com.slte.app.domain.usecase.DaysUntilExpiryUseCase
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrafficData(
    val records: List<TrafficLogRecord> = emptyList(),
    val isLoading: Boolean = true,
    @StringRes val errorMessageRes: Int? = null,
    @StringRes val toastRes: Int? = null,

    // 用量总览（来自缓存的订阅信息）
    val planName: String = "",
    val usedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val isValid: Boolean = false,
    val hasPlan: Boolean = false,
    val daysUntilExpired: Int? = null,
    val expiredAt: Long = 0L,
)

@HiltViewModel
class TrafficViewModel
@Inject
constructor(
    private val trafficRepository: TrafficRepository,
    private val subscribeRepository: SubscribeRepository,
    private val expiryUseCase: DaysUntilExpiryUseCase,
) : ViewModel() {
    private val _data = MutableStateFlow(TrafficData())
    val data: StateFlow<TrafficData> = _data.asStateFlow()

    init {
        applySubscribeInfo(subscribeRepository.getCachedSubscribeInfo())
    }

    fun load() {
        _data.update { it.copy(isLoading = true, errorMessageRes = null) }
        viewModelScope.launch {
            trafficRepository.fetchTrafficLog().fold(
                onSuccess = { records ->
                    AppLog.d("SLTE-Traffic", "fetchTrafficLog: ${records.size} 条")
                    _data.update { it.copy(records = records, isLoading = false, errorMessageRes = null) }
                },
                onFailure = { e ->
                    AppLog.w("SLTE-Traffic", "fetchTrafficLog 失败: ${sanitizeLog(e.message ?: "Unknown")}")
                    val hasData = _data.value.records.isNotEmpty()
                    _data.update {
                        it.copy(
                            isLoading = false,
                            errorMessageRes = if (hasData) null else R.string.traffic_load_failed,
                            toastRes = if (hasData) R.string.traffic_load_failed else null,
                        )
                    }
                },
            )
        }
    }

    fun clearToast() = _data.update { it.copy(toastRes = null) }

    private fun applySubscribeInfo(info: SubscribeInfo?) {
        _data.update {
            it.copy(
                planName = info?.planName ?: "",
                usedBytes = info?.usedTraffic ?: 0L,
                totalBytes = info?.transferEnable ?: 0L,
                isValid = isPlanValid(info),
                hasPlan = info?.hasPlan == true,
                daysUntilExpired = expiryDays(info),
                expiredAt = info?.expiredAt ?: 0L,
            )
        }
    }

    private fun expiryDays(info: SubscribeInfo?): Int? = info?.expiredAt?.takeIf { it > 0L }?.let { expiryUseCase(it) }
}
