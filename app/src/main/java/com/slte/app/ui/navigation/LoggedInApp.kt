// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slte.app.ui.component.LoadingOverlay
import com.slte.app.ui.component.SlteBottomNavBar
import com.slte.app.utils.Dimens
import com.slte.app.utils.findActivity

@Composable
fun LoggedInApp(
    accountKey: String,
) {
    val context = LocalContext.current
    val viewModels = rememberLoggedInViewModels(accountKey)
    val mainData by viewModels.main.data.collectAsStateWithLifecycle()
    val purchaseStep by viewModels.purchase.step.collectAsStateWithLifecycle()
    val purchaseToast by viewModels.purchase.toastRes.collectAsStateWithLifecycle()
    val updateState by viewModels.update.state.collectAsStateWithLifecycle()

    GlobalToastHosts(
        purchaseToast = purchaseToast,
        onPurchaseToastShown = viewModels.purchase::clearToast,
        mainErrorRes = mainData.errorMessageRes,
        onMainErrorShown = viewModels.main::clearError,
        updateState = updateState,
        onUpdateTipShown = viewModels.update::consumeTip,
    )

    val pageStack = rememberSaveablePageStack()
    var currentTab by rememberSaveable { mutableStateOf(RootTab.Home) }
    val preload = rememberPreloadNavigation(viewModels, pageStack)

    fun pushPage(page: Page) {
        if (pageStack.lastOrNull() != page) pageStack.add(page)
    }

    fun popPage() {
        if (pageStack.isNotEmpty()) pageStack.removeAt(pageStack.lastIndex)
    }

    var lastBackPress by remember { mutableLongStateOf(0L) }
    BackHandler {
        if (pageStack.isNotEmpty()) {
            pageStack.removeAt(pageStack.lastIndex)
        } else if (currentTab != RootTab.Home) {
            currentTab = RootTab.Home
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackPress < 2000L) {
                context.findActivity()?.finish()
            } else {
                lastBackPress = now
            }
        }
    }

    var pendingPaymentTradeNo by remember { mutableStateOf<String?>(null) }
    PurchaseCompletionHost(
        viewModels = viewModels,
        pageStack = pageStack,
        onPendingPaymentTradeNo = { pendingPaymentTradeNo = it },
    )

    val transitionSpec =
        remember {
            {
                (slideInHorizontally(tween(PAGE_TRANSITION_DURATION)) { it / 3 } + fadeIn(tween(PAGE_TRANSITION_DURATION)))
                    .togetherWith(
                        slideOutHorizontally(tween(PAGE_TRANSITION_DURATION)) { -it / 3 } + fadeOut(tween(PAGE_TRANSITION_DURATION)),
                    )
            }
        }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentTab to pageStack.lastOrNull(),
            transitionSpec = { transitionSpec() },
            contentKey = { it },
            modifier = Modifier.fillMaxSize(),
        ) { (tab, leaf) ->
            if (leaf != null) {
                LeafPageContent(
                    leaf = leaf,
                    viewModels = viewModels,
                    purchaseStep = purchaseStep,
                    pendingPaymentTradeNo = pendingPaymentTradeNo,
                    onPendingPaymentConsumed = { pendingPaymentTradeNo = null },
                    onBack = ::popPage,
                    onGoToOrders = {
                        viewModels.purchase.goBack()
                        viewModels.orders.refresh()
                        pushPage(Page.Orders)
                    },
                    onRoutingRules = { pushPage(Page.RoutingRules) },
                )
            } else {
                Box(
                    modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = Dimens.bottomNavHeight),
                ) {
                    RootTabContent(
                        tab = tab,
                        viewModels = viewModels,
                        mainData = mainData,
                        onNotice = { preload.enterPage(PendingNav.Notice) },
                        onOrders = { preload.enterPage(PendingNav.Orders) },
                        onInvite = { preload.enterPage(PendingNav.Invite) },
                        onRenew = { preload.enterPage(PendingNav.Plans) },
                        onTickets = { pushPage(Page.Ticket) },
                        onSettings = { pushPage(Page.Settings) },
                        onAbout = { pushPage(Page.About) },
                        onRoutingRules = { pushPage(Page.RoutingRules) },
                        onTabSelected = { currentTab = it },
                    )
                }
            }
        }

        if (pageStack.isEmpty()) {
            SlteBottomNavBar(
                currentTab = currentTab,
                onTabSelected = { currentTab = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    LoadingOverlay(
        visible = preload.pending != null || mainData.isUpdating,
        onDismiss = {
            preload.cancel()
            viewModels.main.cancelUpdating()
        },
    )

    UpdateHost(updateState = updateState, updateViewModel = viewModels.update)
}

@Composable
private fun RootTabContent(
    tab: RootTab,
    viewModels: LoggedInViewModels,
    mainData: com.slte.app.ui.screen.main.DashboardData,
    onNotice: () -> Unit,
    onOrders: () -> Unit,
    onInvite: () -> Unit,
    onRenew: () -> Unit,
    onTickets: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onRoutingRules: () -> Unit,
    onTabSelected: (RootTab) -> Unit,
) {
    val onNavSelect: (com.slte.app.ui.v5.NavTab) -> Unit = { navTab ->
        onTabSelected(
            when (navTab) {
                com.slte.app.ui.v5.NavTab.HOME -> RootTab.Home
                com.slte.app.ui.v5.NavTab.NODES -> RootTab.Server
                com.slte.app.ui.v5.NavTab.TRAFFIC -> RootTab.Traffic
                com.slte.app.ui.v5.NavTab.ME -> RootTab.Profile
            },
        )
    }
    when (tab) {
        RootTab.Home ->
            DashboardPageContent(
                mainViewModel = viewModels.main,
                mainData = mainData,
                onRenew = onRenew,
                onNavSelect = onNavSelect,
            )

        RootTab.Server ->
            ServerPageContent(
                serverViewModel = viewModels.server,
                onUpdateSubscription = viewModels.main::updateSubscription,
                onRoutingRules = onRoutingRules,
                onNavSelect = onNavSelect,
            )

        RootTab.Traffic ->
            TrafficPageContent(
                trafficViewModel = viewModels.traffic,
                onNavSelect = onNavSelect,
            )

        RootTab.Profile ->
            ProfilePageContent(
                profileViewModel = viewModels.profile,
                onNotice = onNotice,
                onOrders = onOrders,
                onInvite = onInvite,
                onRenew = onRenew,
                onTickets = onTickets,
                onSettings = onSettings,
                onAbout = onAbout,
                onNavSelect = onNavSelect,
            )
    }
}

@Composable
private fun LeafPageContent(
    leaf: Page,
    viewModels: LoggedInViewModels,
    purchaseStep: com.slte.app.ui.screen.plans.PurchaseStep,
    pendingPaymentTradeNo: String?,
    onPendingPaymentConsumed: () -> Unit,
    onBack: () -> Unit,
    onGoToOrders: () -> Unit,
    onRoutingRules: () -> Unit,
) {
    when (leaf) {
        Page.Invite ->
            InvitePageContent(
                inviteViewModel = viewModels.invite,
                onBack = onBack,
            )

        Page.Notice ->
            NoticePageContent(
                noticeViewModel = viewModels.notice,
                onBack = onBack,
            )

        Page.Ticket ->
            TicketPageContent(
                ticketViewModel = viewModels.ticket,
                onBack = onBack,
            )

        Page.Orders ->
            OrdersPageContent(
                ordersViewModel = viewModels.orders,
                purchaseViewModel = viewModels.purchase,
                purchaseStep = purchaseStep,
                onBack = onBack,
                pendingPaymentTradeNo = pendingPaymentTradeNo,
                onPendingPaymentConsumed = onPendingPaymentConsumed,
            )

        Page.Plans ->
            PlansPageContent(
                plansViewModel = viewModels.plans,
                purchaseViewModel = viewModels.purchase,
                onBack = onBack,
                onGoToOrders = onGoToOrders,
            )

        Page.Settings ->
            SettingsPageContent(
                onBack = onBack,
                onRoutingRules = onRoutingRules,
            )

        Page.RoutingRules -> RoutingRulesPageContent(onBack = onBack)

        Page.About -> AboutPageContent(onBack = onBack)
    }
}
