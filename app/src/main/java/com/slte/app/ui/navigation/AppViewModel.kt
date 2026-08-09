package com.slte.app.ui.navigation

import androidx.lifecycle.ViewModel
import com.slte.app.data.remote.config.CrispManager
import com.slte.app.domain.model.SessionManager
import com.slte.app.domain.model.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    sessionManager: SessionManager,
    val crispManager: CrispManager
) : ViewModel() {
    val sessionState: StateFlow<SessionState> = sessionManager.sessionState
}
