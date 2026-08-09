package com.slte.app.ui.screen.notice

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slte.app.R
import com.slte.app.data.repository.SubscribeRepository
import com.slte.app.domain.model.Notice
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NoticeUiState(
    val isLoading: Boolean = true,
    val notices: List<Notice> = emptyList(),
    @StringRes val errorMessageRes: Int? = null,
    /** 为 true 时表示正在预加载，为 false 后才切换到公告页面 */
    val isEntering: Boolean = false
)

@HiltViewModel
class NoticeViewModel @Inject constructor(
    private val subscribeRepository: SubscribeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoticeUiState())
    val uiState: StateFlow<NoticeUiState> = _uiState.asStateFlow()

    /** 预加载入口 */
    fun enterAndRefresh() {
        _uiState.update { it.copy(isEntering = true) }
        loadNotices()
    }

    fun loadNotices() {
        _uiState.update { it.copy(isLoading = true, errorMessageRes = null) }
        viewModelScope.launch {
            subscribeRepository.fetchNotices().fold(
                onSuccess = { notices ->
                    AppLog.d("SLTE-Notice", "fetchNotices: ${notices.size} 条")
                    _uiState.update {
                        it.copy(isLoading = false, notices = notices, isEntering = false)
                    }
                },
                onFailure = { e ->
                    AppLog.w("SLTE-Notice", "fetchNotices 失败: ${sanitizeLog(e.message ?: "Unknown")}")
                    _uiState.update {
                        it.copy(isLoading = false, errorMessageRes = R.string.notice_error, isEntering = false)
                    }
                }
            )
        }
    }
}
