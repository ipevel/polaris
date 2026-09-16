package com.slte.app.domain.model

sealed interface SessionState {
    data object Loading : SessionState

    data object LoggedOut : SessionState

    data class LoggedIn(
        val user: User,
    ) : SessionState
}
