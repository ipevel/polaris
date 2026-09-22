// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.compose.collectAsStateWithLifecycle

internal class PreloadNavigation(
    val pending: PendingNav?,
    val enterPage: (PendingNav) -> Unit,
    val cancel: () -> Unit,
)

@Composable
internal fun rememberPreloadNavigation(
    viewModels: LoggedInViewModels,
    pageStack: SnapshotStateList<Page>,
): PreloadNavigation {
    val inviteData by viewModels.invite.data.collectAsStateWithLifecycle()
    val noticeData by viewModels.notice.uiState.collectAsStateWithLifecycle()
    val ordersData by viewModels.orders.data.collectAsStateWithLifecycle()
    val plansData by viewModels.plans.data.collectAsStateWithLifecycle()
    val trafficData by viewModels.traffic.data.collectAsStateWithLifecycle()

    var pending by remember { mutableStateOf<PendingNav?>(null) }

    fun enterPage(target: PendingNav) {
        pending = target
        when (target) {
            PendingNav.Invite -> viewModels.invite.enterAndRefresh()
            PendingNav.Notice -> viewModels.notice.enterAndRefresh()
            PendingNav.Orders -> viewModels.orders.enterAndRefresh()
            PendingNav.Plans -> viewModels.plans.enterAndRefresh()
            PendingNav.Traffic -> viewModels.traffic.load()
        }
    }

    val loaded =
        when (pending) {
            PendingNav.Invite -> !inviteData.isEntering
            PendingNav.Notice -> !noticeData.isEntering
            PendingNav.Orders -> !ordersData.isEntering
            PendingNav.Plans -> !plansData.isEntering
            PendingNav.Traffic -> !trafficData.isLoading
            null -> false
        }

    LaunchedEffect(pending, loaded) {
        val target = pending
        if (target != null && loaded) {
            pending = null
            if (pageStack.last() != target.page) pageStack.add(target.page)
        }
    }

    return PreloadNavigation(
        pending = pending,
        enterPage = ::enterPage,
        cancel = { pending = null },
    )
}
