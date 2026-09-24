// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.slte.app.ui.screen.about.AboutScreen
import com.slte.app.ui.screen.invite.InviteScreen
import com.slte.app.ui.screen.invite.InviteViewModel
import com.slte.app.ui.screen.main.DashboardData
import com.slte.app.ui.screen.main.MainScreen
import com.slte.app.ui.screen.main.MainViewModel
import com.slte.app.ui.screen.notice.NoticeScreen
import com.slte.app.ui.screen.notice.NoticeViewModel
import com.slte.app.ui.screen.order.OrdersScreen
import com.slte.app.ui.screen.order.OrdersViewModel
import com.slte.app.ui.screen.plans.PlansScreen
import com.slte.app.ui.screen.plans.PlansViewModel
import com.slte.app.ui.screen.plans.PurchaseFlow
import com.slte.app.ui.screen.plans.PurchaseStep
import com.slte.app.ui.screen.plans.PurchaseViewModel
import com.slte.app.ui.screen.profile.ProfileScreen
import com.slte.app.ui.screen.profile.ProfileViewModel
import com.slte.app.ui.screen.server.ServerScreen
import com.slte.app.ui.screen.server.ServerViewModel
import com.slte.app.ui.screen.settings.SettingsScreen
import com.slte.app.ui.screen.ticket.TicketScreen
import com.slte.app.ui.screen.ticket.TicketViewModel
import com.slte.app.ui.screen.traffic.TrafficScreen
import com.slte.app.ui.screen.traffic.TrafficViewModel

@Composable
internal fun OrdersPageContent(
    ordersViewModel: OrdersViewModel,
    purchaseViewModel: PurchaseViewModel,
    purchaseStep: PurchaseStep,
    onBack: () -> Unit,
    pendingPaymentTradeNo: String?,
    onPendingPaymentConsumed: () -> Unit,
) {
    OrdersScreen(
        onBack = onBack,
        onPay = { tradeNo -> purchaseViewModel.loadPaymentForOrder(tradeNo) },
        viewModel = ordersViewModel,
    )
    PurchaseFlow(
        step = purchaseStep,
        onSelectPeriod = purchaseViewModel::selectPeriod,
        onUpdateCoupon = purchaseViewModel::updateCouponCode,
        onVerifyCoupon = purchaseViewModel::verifyCoupon,
        onConfirmOrder = purchaseViewModel::showConfirmWarning,
        onCancelWarning = purchaseViewModel::cancelWarning,
        onConfirmWarning = purchaseViewModel::confirmWarning,
        onSelectPayment = purchaseViewModel::selectPaymentMethod,
        onConfirmPayment = purchaseViewModel::confirmPayment,
        onPaymentReturn = {
            purchaseViewModel.onPaymentReturn()
            ordersViewModel.refresh()
        },
        onDismiss = purchaseViewModel::goBack,
    )

    val payingTradeNo = (purchaseStep as? PurchaseStep.OrderPayment)?.tradeNo
    LaunchedEffect(payingTradeNo) {
        if (payingTradeNo != null) purchaseViewModel.startOrderPolling(payingTradeNo)
    }
    pendingPaymentTradeNo?.let { tradeNo ->
        LaunchedEffect(tradeNo) {
            purchaseViewModel.loadPaymentForOrder(tradeNo)
            onPendingPaymentConsumed()
        }
    }
}

@Composable
internal fun DashboardPageContent(
    mainViewModel: MainViewModel,
    mainData: DashboardData,
    onRenew: () -> Unit,
) {
    MainScreen(
        mainViewModel = mainViewModel,
        data = mainData,
        onRenew = onRenew,
    )
}

@Composable
internal fun ProfilePageContent(
    profileViewModel: ProfileViewModel,
    onNotice: () -> Unit,
    onOrders: () -> Unit,
    onInvite: () -> Unit,
    onRenew: () -> Unit,
    onTickets: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    LaunchedEffect(Unit) { profileViewModel.refresh() }
    ProfileScreen(
        onNotice = onNotice,
        onOrders = onOrders,
        onInvite = onInvite,
        onRenew = onRenew,
        onTickets = onTickets,
        onSettings = onSettings,
        onAbout = onAbout,
        onLogout = profileViewModel::logout,
        viewModel = profileViewModel,
    )
}

@Composable
internal fun ServerPageContent(
    serverViewModel: ServerViewModel,
    onUpdateSubscription: () -> Unit,
) {
    LaunchedEffect(Unit) { serverViewModel.loadNodes() }
    ServerScreen(
        onUpdateSubscription = onUpdateSubscription,
        viewModel = serverViewModel,
    )
}

@Composable
internal fun InvitePageContent(
    inviteViewModel: InviteViewModel,
    onBack: () -> Unit,
) {
    InviteScreen(
        onBack = onBack,
        viewModel = inviteViewModel,
    )
}

@Composable
internal fun NoticePageContent(
    noticeViewModel: NoticeViewModel,
    onBack: () -> Unit,
) {
    NoticeScreen(
        onBack = onBack,
        viewModel = noticeViewModel,
    )
}

@Composable
internal fun TicketPageContent(
    ticketViewModel: TicketViewModel,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) { ticketViewModel.enterAndRefresh() }
    TicketScreen(
        onBack = onBack,
        viewModel = ticketViewModel,
    )
}

@Composable
internal fun PlansPageContent(
    plansViewModel: PlansViewModel,
    purchaseViewModel: PurchaseViewModel,
    onBack: () -> Unit,
    onGoToOrders: () -> Unit,
) {
    PlansScreen(
        onBack = onBack,
        viewModel = plansViewModel,
        purchaseViewModel = purchaseViewModel,
        onGoToOrders = onGoToOrders,
    )
}

@Composable
internal fun SettingsPageContent(onBack: () -> Unit) {
    SettingsScreen(onBack = onBack)
}

@Composable
internal fun AboutPageContent(onBack: () -> Unit) {
    AboutScreen(onBack = onBack)
}

@Composable
internal fun TrafficPageContent(
    trafficViewModel: TrafficViewModel,
) {
    TrafficScreen(
        viewModel = trafficViewModel,
    )
}
