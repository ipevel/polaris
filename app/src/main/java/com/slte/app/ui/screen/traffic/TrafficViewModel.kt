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
)

@HiltViewModel
class TrafficViewModel
@Inject
constructor(
    private val trafficRepository: TrafficRepository,
) : ViewModel() {
    private val _data = MutableStateFlow(TrafficData())
    val data: StateFlow<TrafficData> = _data.asStateFlow()

    init {
        load()
    }

    fun load() {
        _data.update { it.copy(isLoading = true, errorMessageRes = null) }
        viewModelScope.launch {
            trafficRepository.fetchTrafficLog().fold(
                onSuccess = { records ->
                    AppLog.d("Polaris-Traffic", "fetchTrafficLog: ${records.size} 条")
                    _data.update { it.copy(records = records, isLoading = false, errorMessageRes = null) }
                },
                onFailure = { e ->
                    AppLog.w("Polaris-Traffic", "fetchTrafficLog 失败: ${sanitizeLog(e.message ?: "Unknown")}")
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
}
