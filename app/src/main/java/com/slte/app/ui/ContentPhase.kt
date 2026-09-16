package com.slte.app.ui

sealed interface ContentPhase {

    object Loading : ContentPhase

    object Refreshing : ContentPhase

    object Idle : ContentPhase
}
