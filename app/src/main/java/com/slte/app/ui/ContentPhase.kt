// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui

sealed interface ContentPhase {

    object Loading : ContentPhase

    object Refreshing : ContentPhase

    object Idle : ContentPhase
}
