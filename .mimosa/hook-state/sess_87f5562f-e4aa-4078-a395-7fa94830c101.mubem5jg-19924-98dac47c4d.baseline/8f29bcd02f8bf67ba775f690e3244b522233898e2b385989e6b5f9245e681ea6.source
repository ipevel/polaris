package com.slte.app.ui.navigation

import com.slte.app.data.local.LocaleStore
import com.slte.app.data.local.SessionManager
import com.slte.app.domain.model.SessionState
import io.mockk.every
import io.mockk.mockk
import java.util.Locale
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class AppViewModelTest {
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val localeStore = mockk<LocaleStore>(relaxed = true)

    @Test
    fun `会话状态与语言偏好直接透传给界面`() {
        every { sessionManager.sessionState } returns MutableStateFlow(SessionState.LoggedOut)
        every { sessionManager.sessionExpiredEvents } returns MutableSharedFlow()
        every { localeStore.locale } returns MutableStateFlow(Locale.SIMPLIFIED_CHINESE)

        val vm = AppViewModel(sessionManager, localeStore)

        assertEquals(SessionState.LoggedOut, vm.sessionState.value)
        assertEquals(Locale.SIMPLIFIED_CHINESE, vm.locale.value)
    }
}
