package com.slte.app.ui.navigation

import androidx.lifecycle.ViewModel
import com.slte.app.data.local.LocaleStore
import com.slte.app.data.local.SessionManager
import com.slte.app.domain.model.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class AppViewModel
@Inject
constructor(
    sessionManager: SessionManager,
    val localeStore: LocaleStore,
) : ViewModel() {
    val sessionState: StateFlow<SessionState> = sessionManager.sessionState

    val sessionExpiredEvents: SharedFlow<Unit> = sessionManager.sessionExpiredEvents

    val locale: StateFlow<Locale?> = localeStore.locale
}
