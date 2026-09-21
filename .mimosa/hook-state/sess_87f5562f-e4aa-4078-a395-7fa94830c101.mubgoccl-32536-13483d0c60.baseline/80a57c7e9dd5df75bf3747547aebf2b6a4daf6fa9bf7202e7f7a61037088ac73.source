package com.slte.app.ui.screen.notice

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slte.app.R
import com.slte.app.data.repository.SubscribeRepository
import com.slte.app.domain.model.Notice
import com.slte.app.ui.ContentPhase
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoticeUiState(
    val phase: ContentPhase = ContentPhase.Loading,
    val notices: List<Notice> = emptyList(),
    @StringRes val errorMessageRes: Int? = null,
    val errorMessage: String? = null,

    @StringRes val toastRes: Int? = null,

    val isEntering: Boolean = false,
)

@HiltViewModel
class NoticeViewModel
@Inject
constructor(
    private val subscribeRepository: SubscribeRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NoticeUiState())
    val uiState: StateFlow<NoticeUiState> = _uiState.asStateFlow()

    fun enterAndRefresh() {
        _uiState.update { it.copy(isEntering = true) }
        loadNotices()
    }

    fun loadNotices() {
        _uiState.update { it.copy(phase = ContentPhase.Loading, errorMessageRes = null, errorMessage = null) }
        viewModelScope.launch {
            subscribeRepository.fetchNotices().fold(
                onSuccess = { notices ->
                    AppLog.d("Polaris-Notice", "fetchNotices: ${notices.size} 条")
                    _uiState.update {
                        it.copy(phase = ContentPhase.Idle, notices = notices, errorMessage = null, isEntering = false)
                    }
                },
                onFailure = { e ->
                    AppLog.w("Polaris-Notice", "fetchNotices 失败: ${sanitizeLog(e.message ?: "Unknown")}")
                    _uiState.update {
                        it.copy(
                            phase = ContentPhase.Idle,
                            errorMessageRes = R.string.notice_error,
                            errorMessage = e.message?.let { msg -> sanitizeLog(msg) },
                            isEntering = false,
                        )
                    }
                },
            )
        }
    }

    fun refresh() {
        if (_uiState.value.phase == ContentPhase.Refreshing) return
        _uiState.update { it.copy(phase = ContentPhase.Refreshing) }
        viewModelScope.launch {
            subscribeRepository.fetchNotices().fold(
                onSuccess = { notices ->
                    AppLog.d("Polaris-Notice", "refresh: ${notices.size} 条")
                    _uiState.update { it.copy(phase = ContentPhase.Idle, notices = notices, errorMessageRes = null, errorMessage = null) }
                },
                onFailure = { e ->
                    AppLog.w("Polaris-Notice", "refresh 失败: ${sanitizeLog(e.message ?: "Unknown")}")

                    val hasData = _uiState.value.notices.isNotEmpty()
                    _uiState.update {
                        if (hasData) {
                            it.copy(phase = ContentPhase.Idle, toastRes = R.string.notice_refresh_failed, errorMessage = e.message?.let { msg -> sanitizeLog(msg) })
                        } else {
                            it.copy(phase = ContentPhase.Idle, errorMessageRes = R.string.notice_error, errorMessage = e.message?.let { msg -> sanitizeLog(msg) })
                        }
                    }
                },
            )
        }
    }

    fun clearToast() = _uiState.update { it.copy(toastRes = null) }
}
