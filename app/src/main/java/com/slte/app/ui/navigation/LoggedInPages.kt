// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slte.app.ui.component.rememberToast
import com.slte.app.ui.screen.about.AboutScreen
import com.slte.app.ui.screen.giftcard.GiftCardRedeemSheet
import com.slte.app.ui.screen.giftcard.GiftCardRedeemViewModel
import com.slte.app.ui.screen.invite.InviteScreen
import com.slte.app.ui.screen.invite.InviteViewModel
import com.slte.app.ui.screen.main.DashboardData
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
import com.slte.app.ui.screen.profile.ProfileViewModel
import com.slte.app.ui.screen.server.ServerViewModel
import com.slte.app.ui.screen.settings.AppearanceMode
import com.slte.app.ui.screen.settings.AppearanceModeSheet
import com.slte.app.ui.screen.settings.ChangePasswordSheet
import com.slte.app.ui.screen.settings.ChangePasswordState
import com.slte.app.ui.screen.settings.LanguageMode
import com.slte.app.ui.screen.settings.LanguageModeSheet
import com.slte.app.ui.screen.settings.SettingsViewModel
import com.slte.app.ui.screen.settings.TunStackModeSheet
import com.slte.app.ui.screen.ticket.TicketScreen
import com.slte.app.ui.screen.ticket.TicketViewModel
import com.slte.app.ui.screen.traffic.TrafficViewModel
import com.slte.app.ui.v5.NavTab
import com.slte.app.ui.v5.screens.V5HomeScreen
import com.slte.app.ui.v5.screens.V5MeScreen
import com.slte.app.ui.v5.screens.V5NodesScreen
import com.slte.app.ui.v5.screens.V5RoutingRulesScreen
import com.slte.app.ui.v5.screens.V5SettingsScreen
import com.slte.app.ui.v5.screens.V5TrafficScreen

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
    onNavSelect: (com.slte.app.ui.v5.NavTab) -> Unit,
) {
    V5HomeScreen(
        data = mainData,
        onToggleConnection = mainViewModel::toggleConnection,
        onVpnPermissionDenied = mainViewModel::onVpnPermissionDenied,
        vpnRequestIntent = mainViewModel::vpnRequestIntent,
        onRenew = onRenew,
        onNavSelect = onNavSelect,
        refreshKernelInfo = mainViewModel::refreshKernelInfo,
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
    onNavSelect: (com.slte.app.ui.v5.NavTab) -> Unit,
    // 礼品卡兑换：ViewModel 与 Sheet 组件在 v5 重写时被留成死代码，这里恢复挂载点。
    // 用无 key 的 hiltViewModel()（与设置页同款），实例落在 Activity store，切 Tab 不丢状态。
    giftCardViewModel: GiftCardRedeemViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { profileViewModel.refresh() }
    val data by profileViewModel.data.collectAsStateWithLifecycle()

    val giftCardState by giftCardViewModel.state.collectAsStateWithLifecycle()
    val giftCardTip by giftCardViewModel.tip.collectAsStateWithLifecycle()
    val giftCardRedeemed by giftCardViewModel.redeemed.collectAsStateWithLifecycle()
    val toast = rememberToast()

    LaunchedEffect(giftCardTip) {
        giftCardTip?.messageRes?.let { toast.show(it) }
        if (giftCardTip != null) giftCardViewModel.clearTip()
    }
    // 兑换成功会改变余额/流量/到期，必须回拉个人中心，否则用户看到的是旧数据
    LaunchedEffect(giftCardRedeemed) {
        if (giftCardRedeemed > 0) profileViewModel.refresh()
    }

    V5MeScreen(
        data = data,
        onPlans = onRenew,
        onGiftCard = giftCardViewModel::open,
        onOrders = onOrders,
        onInvite = onInvite,
        onTickets = onTickets,
        onNotices = onNotice,
        onSettings = onSettings,
        onAbout = onAbout,
        onLogout = profileViewModel::logout,
        onNavSelect = onNavSelect,
    )

    if (giftCardState.visible) {
        GiftCardRedeemSheet(
            state = giftCardState,
            onCodeChange = giftCardViewModel::updateCode,
            onSubmit = giftCardViewModel::submit,
            onDismiss = giftCardViewModel::dismiss,
        )
    }
}

@Composable
internal fun ServerPageContent(
    serverViewModel: ServerViewModel,
    onUpdateSubscription: () -> Unit,
    onRoutingRules: () -> Unit,
    onNavSelect: (com.slte.app.ui.v5.NavTab) -> Unit,
) {
    LaunchedEffect(Unit) {
        serverViewModel.loadNodes()
        // 策略组（含分流规则组的出口）来自运行中的内核，进入节点页即拉一次
        serverViewModel.loadProxyGroups()
    }
    val data by serverViewModel.data.collectAsStateWithLifecycle()
    val groups by serverViewModel.proxyGroups.collectAsStateWithLifecycle()
    val isLoadingGroups by serverViewModel.isLoadingGroups.collectAsStateWithLifecycle()
    val testingGroup by serverViewModel.testingGroup.collectAsStateWithLifecycle()
    val isTestingAll by serverViewModel.isTestingAll.collectAsStateWithLifecycle()
    // 折叠态放 ViewModel：切 Tab 会销毁本屏组合，屏幕内 remember/rememberSaveable 都会丢
    val collapsedSections by serverViewModel.collapsedSections.collectAsStateWithLifecycle()
    // 本地分流开关从设置页迁到节点页；用无 key 的 hiltViewModel()（与 SettingsPageContent 同款），
    // 全仓无 NavHost → 落在同一 Activity store、按类名 key，因此两页拿到**同一实例**，
    // 不会出现"一处拨动另一处显示旧值"的双真相。
    val settingsViewModel: com.slte.app.ui.screen.settings.SettingsViewModel = hiltViewModel()
    val settingsData by settingsViewModel.data.collectAsStateWithLifecycle()
    V5NodesScreen(
        data = data,
        groups = groups,
        isLoadingGroups = isLoadingGroups,
        testingGroup = testingGroup,
        isTestingAll = isTestingAll,
        // 节点选择卡是主组切换：走 selectPrimary（额外同步全局模式下的 GLOBAL），
        // 分流规则组仍走 selectInGroup（不应改 GLOBAL）。
        onSelectPrimary = serverViewModel::selectPrimary,
        onSelectInGroup = serverViewModel::selectInGroup,
        onTestGroup = serverViewModel::testGroup,
        onStartSpeedTest = serverViewModel::startSpeedTest,
        onRefreshSubscription = onUpdateSubscription,
        onRoutingRules = onRoutingRules,
        onNavSelect = onNavSelect,
        collapsedSections = collapsedSections,
        onToggleSection = serverViewModel::toggleSection,
        localRoutingEnabled = settingsData.localRoutingEnabled,
        localRoutingBusy = settingsData.routingSync != com.slte.app.ui.screen.settings.RemindSync.Idle,
        onToggleLocalRouting = { settingsViewModel.setLocalRouting(!settingsData.localRoutingEnabled) },
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
internal fun SettingsPageContent(
    onBack: () -> Unit,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    var showLanguage by rememberSaveable { mutableStateOf(false) }
    var showTunStack by rememberSaveable { mutableStateOf(false) }
    V5SettingsScreen(
        viewModel = viewModel,
        onBack = onBack,
        onAppearance = { showAppearance = true },
        onLanguage = { showLanguage = true },
        onTunStack = { showTunStack = true },
        onChangePassword = viewModel::showChangePassword,
    )
    if (showAppearance) {
        AppearanceModeSheet(
            currentMode = AppearanceMode.fromThemeMode(viewModel.themeMode.collectAsStateWithLifecycle().value),
            onDismiss = { showAppearance = false },
            onSelect = {
                viewModel.setThemeMode(it.mode)
                showAppearance = false
            },
        )
    }
    if (showLanguage) {
        LanguageModeSheet(
            currentMode = LanguageMode.fromLocale(viewModel.data.collectAsStateWithLifecycle().value.locale),
            onDismiss = { showLanguage = false },
            onSelect = { viewModel.setLocale(it.locale) },
        )
    }
    if (showTunStack) {
        TunStackModeSheet(
            currentMode = viewModel.data.collectAsStateWithLifecycle().value.tunStackMode,
            onDismiss = { showTunStack = false },
            onSelect = viewModel::setTunStackMode,
        )
    }
    val changePasswordState = viewModel.changePasswordState.collectAsStateWithLifecycle().value
    val editing = changePasswordState as? ChangePasswordState.Editing
    if (editing != null) {
        ChangePasswordSheet(
            state = editing,
            onOldPasswordChange = viewModel::onOldPasswordChange,
            onNewPasswordChange = viewModel::onNewPasswordChange,
            onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
            onSubmit = viewModel::submitChangePassword,
            onDismiss = viewModel::dismissChangePassword,
        )
    }
}

@Composable
internal fun RoutingRulesPageContent(onBack: () -> Unit) {
    V5RoutingRulesScreen(onBack = onBack)
}

@Composable
internal fun AboutPageContent(onBack: () -> Unit) {
    AboutScreen(onBack = onBack)
}

@Composable
internal fun TrafficPageContent(
    trafficViewModel: TrafficViewModel,
    onNavSelect: (com.slte.app.ui.v5.NavTab) -> Unit,
) {
    val data by trafficViewModel.data.collectAsStateWithLifecycle()
    V5TrafficScreen(data = data, onNavSelect = onNavSelect)
}
