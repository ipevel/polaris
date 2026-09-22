// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.slte.app.ui.component.rememberToast
import com.slte.app.ui.screen.about.ForceUpdateDialog
import com.slte.app.ui.screen.about.UpdateDownloadingDialog
import com.slte.app.ui.screen.about.UpdateSheet
import com.slte.app.ui.screen.about.UpdateUiState
import com.slte.app.ui.screen.about.UpdateViewModel

@Composable
internal fun GlobalToastHosts(
    purchaseToast: Int?,
    onPurchaseToastShown: () -> Unit,
    mainErrorRes: Int?,
    onMainErrorShown: () -> Unit,
    updateState: UpdateUiState,
    onUpdateTipShown: () -> Unit,
) {
    val toast = rememberToast()

    purchaseToast?.let { resId ->
        LaunchedEffect(resId) {
            toast.show(resId)
            onPurchaseToastShown()
        }
    }
    LaunchedEffect(mainErrorRes) {
        if (mainErrorRes != null) {
            toast.show(mainErrorRes)
            onMainErrorShown()
        }
    }
    LaunchedEffect(updateState) {
        val failedRes = (updateState as? UpdateUiState.Failed)?.messageRes
            ?: (updateState as? UpdateUiState.DownloadFailed)?.messageRes
        if (failedRes != null) {
            toast.show(failedRes)
            onUpdateTipShown()
        }
    }
}

@Composable
internal fun UpdateHost(
    updateState: UpdateUiState,
    updateViewModel: UpdateViewModel,
) {
    if (updateState is UpdateUiState.Downloading) {
        UpdateDownloadingDialog(progress = updateState.progress)
        return
    }
    val available = updateState as? UpdateUiState.Available ?: return
    if (available.force) {
        ForceUpdateDialog(onUpdateNow = updateViewModel::updateNow)
    } else {
        UpdateSheet(
            state = available,
            onDismiss = updateViewModel::dismiss,
            onUpdateNow = updateViewModel::updateNow,
            onLater = updateViewModel::later,
        )
    }
}
