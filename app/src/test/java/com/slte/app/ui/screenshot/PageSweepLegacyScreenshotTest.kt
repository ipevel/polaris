// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.ui.screenshot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.slte.app.R
import com.slte.app.data.local.CredentialStore
import com.slte.app.data.local.InMemoryPreferences
import com.slte.app.data.local.LocaleStore
import com.slte.app.data.local.SessionManager
import com.slte.app.data.local.SessionStore
import com.slte.app.data.local.ThemePreference
import com.slte.app.data.remote.api.dto.OrderInfoDto
import com.slte.app.data.remote.api.dto.PlanInfoDto
import com.slte.app.data.repository.AuthRepository
import com.slte.app.data.repository.InviteRepository
import com.slte.app.data.repository.OrderRepository
import com.slte.app.data.repository.SubscribeRepository
import com.slte.app.data.repository.TicketRepository
import com.slte.app.domain.model.CommissionRecord
import com.slte.app.domain.model.InviteCodeInfo
import com.slte.app.domain.model.InviteInfo
import com.slte.app.domain.model.InviteStat
import com.slte.app.domain.model.Notice
import com.slte.app.domain.model.RegisterConfig
import com.slte.app.domain.model.SessionState
import com.slte.app.domain.model.SiteInfo
import com.slte.app.domain.model.Ticket
import com.slte.app.domain.model.User
import com.slte.app.domain.usecase.CountdownUseCase
import com.slte.app.kernel.KernelConfig
import com.slte.app.kernel.KernelProxy
import com.slte.app.kernel.RoutingStateStore
import com.slte.app.support.FakeAuthApi
import com.slte.app.support.RobolectricTestApplication
import com.slte.app.ui.ContentPhase
import com.slte.app.ui.screen.about.AboutScreen
import com.slte.app.ui.screen.about.UpdateUiState
import com.slte.app.ui.screen.about.UpdateViewModel
import com.slte.app.ui.screen.forgot.ForgotPasswordScreen
import com.slte.app.ui.screen.forgot.ForgotPasswordUiState
import com.slte.app.ui.screen.forgot.ForgotPasswordViewModel
import com.slte.app.ui.screen.invite.InviteScreen
import com.slte.app.ui.screen.invite.InviteViewModel
import com.slte.app.ui.screen.login.LoginScreen
import com.slte.app.ui.screen.login.LoginUiState
import com.slte.app.ui.screen.login.LoginViewModel
import com.slte.app.ui.screen.login.PendingAction
import com.slte.app.ui.screen.notice.NoticeScreen
import com.slte.app.ui.screen.notice.NoticeViewModel
import com.slte.app.ui.screen.order.OrdersScreen
import com.slte.app.ui.screen.order.OrdersViewModel
import com.slte.app.ui.screen.plans.CouponChecker
import com.slte.app.ui.screen.plans.OrderCreator
import com.slte.app.ui.screen.plans.OrderPaymentLoader
import com.slte.app.ui.screen.plans.OrderPaymentPoller
import com.slte.app.ui.screen.plans.PaymentCheckout
import com.slte.app.ui.screen.plans.PlansScreen
import com.slte.app.ui.screen.plans.PlansViewModel
import com.slte.app.ui.screen.plans.PurchaseViewModel
import com.slte.app.ui.screen.register.RegisterScreen
import com.slte.app.ui.screen.register.RegisterUiState
import com.slte.app.ui.screen.register.RegisterViewModel
import com.slte.app.ui.screen.settings.RoutingRulesViewModel
import com.slte.app.ui.screen.settings.SettingsViewModel
import com.slte.app.ui.screen.ticket.TicketScreen
import com.slte.app.ui.screen.ticket.TicketViewModel
import com.slte.app.ui.theme.SlteTheme
import com.slte.app.ui.v5.screens.V5RoutingRulesScreen
import com.slte.app.ui.v5.screens.V5SettingsScreen
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v4 叶子页 + 认证流的逐页渲染体检（评审用，不改产品代码）。
 *
 * 覆盖 8 个二级叶子页中的 6 个 v4 页（Orders/Plans/Invite/Notice/Ticket/About）、
 * 2 个已 v5 化的叶子页（Settings/RoutingRules）与 3 个认证页（Login/Register/Forgot）。
 * 与 [PageSweepV5ScreenshotTest] 合成"每个页面都正常"的截图证据。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-420dpi", application = RobolectricTestApplication::class)
class PageSweepLegacyScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val outDir: File by lazy {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val root = if (File(cwd, "settings.gradle.kts").isFile) cwd else cwd.parentFile ?: cwd
        File(root, "design/screenshots-sweep").apply { mkdirs() }
    }

    private fun snapshot(tag: String, dark: Boolean, page: @Composable () -> Unit) {
        composeRule.setContent {
            SlteTheme(darkTheme = dark) {
                Box(modifier = Modifier.fillMaxSize()) {
                    page()
                }
            }
        }
        composeRule.waitForIdle()

        val decor = composeRule.activity.window.decorView
        val metrics = composeRule.activity.resources.displayMetrics
        if (decor.width <= 0 || decor.height <= 0) {
            decor.measure(
                View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY),
            )
            decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
        }
        val bitmap =
            Bitmap.createBitmap(
                decor.width.coerceAtLeast(1),
                decor.height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
        decor.draw(Canvas(bitmap))
        val file = File(outDir, "$tag-${if (dark) "dark" else "light"}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val counts = HashMap<Int, Int>()
        pixels.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        val top = counts.maxByOrNull { it.value }?.value ?: pixels.size
        val nonBgRatio = 1.0 - top.toDouble() / pixels.size.toDouble()
        val blank = if (counts.size <= 2 || nonBgRatio < 0.002) " SWEEP_BLANK=YES" else ""
        println(
            "SWEEP_OK=$tag size=${bitmap.width}x${bitmap.height} uniq=${counts.size} " +
                "nonBg=${"%.4f".format(nonBgRatio)} file=${file.absolutePath}$blank",
        )
    }

    /**
     * 正常返回数据的假面板（用于渲染正常/空态而非错误态）。
     *
     * 这里**不再 override `fetchTickets`**：工单数据走 [FakeAuthApi.tickets]/`ticketsError`，
     * 这样"空态"与"列表态"是同一个真实数据通路下的两种结果。
     */
    private val api = object : FakeAuthApi() {
        override suspend fun fetchRegisterConfig(): RegisterConfig =
            RegisterConfig(emailVerifyEnabled = true, inviteForceEnabled = false)
    }

    private val prefs = InMemoryPreferences()
    private val sessionStore = SessionStore(prefs)
    private val sessionManager = mockk<SessionManager>(relaxed = true)

    /**
     * 会话固定为"已登录"。
     *
     * `SubscribeRepository.fetchNotices()` 等方法在未登录时**直接**返回失败（"未登录"），
     * 而 relaxed mock 的 `sessionState.value` 不是 `SessionState.LoggedIn`——不桩住的话，
     * 公告/工单这类页面的"空态"截图实际拍的是错误态（名实不符的假证据）。
     * 这里必须用真实 `MutableStateFlow`：relaxed mock 的 StateFlow 被 collect 时会抛
     * `KotlinNothingValueException`（既有测试踩过这个坑）。
     */
    private val sessionState = MutableStateFlow<SessionState>(
        SessionState.LoggedIn(User(id = "sweep-user", displayName = "sweep@example.com")),
    )

    init {
        every { sessionManager.sessionState } returns sessionState
        every { sessionManager.logoutEvents } returns MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    }

    private val subscribeRepository = SubscribeRepository(api, sessionStore, sessionManager)
    private val orderRepository = OrderRepository(api)
    private val authRepository =
        AuthRepository(api, sessionStore, CredentialStore(prefs), sessionManager, subscribeRepository)

    private val kernelProxy = mockk<KernelProxy>(relaxed = true)
    private val routingStateStore = mockk<RoutingStateStore>(relaxed = true)
    private val kernelConfig = mockk<KernelConfig>(relaxed = true)

    private fun order(id: Int) = OrderInfoDto(
        id = id,
        tradeNo = "TN-$id",
        planName = "进阶套餐",
        totalAmount = 5_000,
        status = 3,
        createdAt = 1_700_000_000L,
        expiredAt = 1_800_000_000L,
    )

    private fun plan(id: Int) = PlanInfoDto(
        id = id,
        name = "进阶套餐 $id",
        monthPrice = 5_000,
        transferEnable = 400,
        show = true,
    )

    private fun commissionRecord(id: Int) = CommissionRecord(
        id = id,
        tradeNo = "TN-$id",
        orderAmount = 5_000,
        getAmount = 1_500,
        createdAt = 1_700_000_000L + id * 86_400L,
    )

    private fun purchaseViewModel() = PurchaseViewModel(
        couponChecker = CouponChecker(orderRepository),
        paymentLoader = OrderPaymentLoader(orderRepository),
        poller = OrderPaymentPoller(orderRepository),
        orderCreator = OrderCreator(orderRepository),
        paymentCheckout = PaymentCheckout(orderRepository),
    )

    // ==================== 订单页 ====================

    /**
     * 订单/套餐页的三态截图。
     *
     * 状态落地靠 [settle] 而不是裸 `waitForIdle()`：`OrdersViewModel.load()` 的赋值发生在
     * `viewModelScope` 协程里，而 `waitForIdle()` 只保证 Compose 的 recomposition 与主线程消息
     * 队列排空，协程是否跑完**不保证**——三张"三态"截图会随机塌成同一个中间态（工单页本轮
     * 就踩过同类坑，那次更彻底：三张图 MD5 完全相同）。
     *
     * [settle] 用 `onIdle` 反复驱动主线程：`viewModelScope` 跑在 `Dispatchers.Main.immediate` 上，
     * Robolectric 的主线程就是测试线程的 looper，所以"进主线程队列 + 让 looper 跑一遍"就能推进它；
     * 协程恢复后会继续把结果写回 StateFlow。不能用 `runBlocking { while (...) delay() }`：
     * 那会把 looper 一起阻塞住，形成死锁（试过）。
     */
    private fun ordersViewModel(): OrdersViewModel {
        val vm = OrdersViewModel(orderRepository)
        vm.enterAndRefresh()
        return vm
    }

    private fun settle(vm: OrdersViewModel) {
        val deadline = System.currentTimeMillis() + 5_000
        while (vm.data.value.phase != ContentPhase.Idle && System.currentTimeMillis() < deadline) {
            composeRule.waitForIdle()
        }
    }

    @Test
    fun `30-订单-有订单-亮色`() {
        api.ordersError = null
        api.orders = listOf(order(1), order(2))
        val vm = ordersViewModel()
        settle(vm)
        snapshot("30-orders-light", dark = false) { OrdersScreen(onBack = {}, viewModel = vm) }
    }

    @Test
    fun `31-订单-空态-亮色`() {
        api.ordersError = null
        api.orders = emptyList()
        val vm = ordersViewModel()
        settle(vm)
        snapshot("31-orders-empty-light", dark = false) { OrdersScreen(onBack = {}, viewModel = vm) }
    }

    @Test
    fun `32-订单-加载失败-亮色`() {
        api.orders = emptyList()
        api.ordersError = java.io.IOException("连接超时，请稍后重试")
        val vm = ordersViewModel()
        settle(vm)
        snapshot("32-orders-error-light", dark = false) { OrdersScreen(onBack = {}, viewModel = vm) }
    }

    /** 加载态：VM 初始 `phase = Loading` 且列表为空 ⇒ 整页加载态（不需要等任何东西落地）。 */
    @Test
    fun `32a-订单-加载态-亮色`() {
        api.ordersError = null
        api.orders = emptyList()
        snapshot("32a-orders-loading-light", dark = false) {
            OrdersScreen(onBack = {}, viewModel = OrdersViewModel(orderRepository))
        }
    }

    @Test
    fun `32b-订单-有订单-暗色`() {
        api.ordersError = null
        api.orders = listOf(order(3))
        val vm = ordersViewModel()
        settle(vm)
        snapshot("32b-orders-dark", dark = true) { OrdersScreen(onBack = {}, viewModel = vm) }
    }

    // ==================== 套餐页 ====================

    private fun plansViewModel(): PlansViewModel {
        val vm = PlansViewModel(orderRepository)
        vm.enterAndRefresh()
        return vm
    }

    private fun settle(vm: PlansViewModel) {
        val deadline = System.currentTimeMillis() + 5_000
        while (vm.data.value.phase != ContentPhase.Idle && System.currentTimeMillis() < deadline) {
            composeRule.waitForIdle()
        }
    }

    @Test
    fun `33-套餐-有套餐-亮色`() {
        api.plansError = null
        api.plans = listOf(plan(1), plan(2))
        val vm = plansViewModel()
        settle(vm)
        snapshot("33-plans-light", dark = false) {
            PlansScreen(onBack = {}, viewModel = vm, purchaseViewModel = purchaseViewModel())
        }
    }

    @Test
    fun `34-套餐-有套餐-暗色`() {
        api.plansError = null
        api.plans = listOf(plan(1), plan(2))
        val vm = plansViewModel()
        settle(vm)
        snapshot("34-plans-dark", dark = true) {
            PlansScreen(onBack = {}, viewModel = vm, purchaseViewModel = purchaseViewModel())
        }
    }

    @Test
    fun `35-套餐-空态-亮色`() {
        api.plansError = null
        api.plans = emptyList()
        val vm = plansViewModel()
        settle(vm)
        snapshot("35-plans-empty-light", dark = false) {
            PlansScreen(onBack = {}, viewModel = vm, purchaseViewModel = purchaseViewModel())
        }
    }

    @Test
    fun `35a-套餐-错误态-亮色`() {
        api.plans = emptyList()
        api.plansError = java.io.IOException("连接超时，请稍后重试")
        val vm = plansViewModel()
        settle(vm)
        snapshot("35a-plans-error-light", dark = false) {
            PlansScreen(onBack = {}, viewModel = vm, purchaseViewModel = purchaseViewModel())
        }
    }

    @Test
    fun `35b-套餐-加载态-亮色`() {
        api.plansError = null
        api.plans = emptyList()
        snapshot("35b-plans-loading-light", dark = false) {
            PlansScreen(onBack = {}, viewModel = PlansViewModel(orderRepository), purchaseViewModel = purchaseViewModel())
        }
    }

    // ==================== 邀请页 ====================

    /**
     * 邀请页三态。
     *
     * 这一页的「三态」与公告/工单不同：它**没有整页错误态与空态**——`InviteViewModel.refresh()`
     * 在失败时只把 `isRefreshing` 复位（既不弹提示也不进错误页），所以真实的失败观感是
     * 「金额为 0 + 邀请码为空 + 佣金记录为空」的**零值态**。这里就按真实行为取证：
     * 有数据态、零值/面板未返回态、加载中态（下拉刷新中）。
     */
    private fun inviteViewModel(): InviteViewModel = InviteViewModel(InviteRepository(api))

    @Test
    fun `36-邀请-有数据-亮色`() {
        api.inviteInfo =
            InviteInfo(
                stat = InviteStat(availableBalance = 56_700, registeredUsers = 3, commissionRate = 25),
                codes = listOf(InviteCodeInfo(code = "ABC123", pv = 0)),
            )
        api.commissionRecords = emptyList()
        api.withdrawMethods = listOf("USDT")
        val vm = inviteViewModel().also { it.enterAndRefresh() }
        snapshot("36-invite-light", dark = false) { InviteScreen(viewModel = vm, onBack = {}) }
    }

    @Test
    fun `37-邀请-有数据-暗色`() {
        val vm = inviteViewModel().also { it.enterAndRefresh() }
        snapshot("37-invite-dark", dark = true) { InviteScreen(viewModel = vm, onBack = {}) }
    }

    @Test
    fun `36a-邀请-零值态-亮色`() {
        api.inviteInfo = InviteInfo(stat = InviteStat(), codes = emptyList())
        api.commissionRecords = emptyList()
        api.withdrawMethods = emptyList()
        val vm = inviteViewModel().also { it.enterAndRefresh() }
        snapshot("36a-invite-zero-light", dark = false) { InviteScreen(viewModel = vm, onBack = {}) }
    }

    @Test
    fun `36b-邀请-佣金记录-亮色`() {
        api.inviteInfo =
            InviteInfo(
                stat = InviteStat(availableBalance = 12_300, registeredUsers = 12, commissionRate = 30),
                codes = listOf(InviteCodeInfo(code = "POLARIS8", pv = 41)),
            )
        api.commissionRecords = listOf(commissionRecord(1), commissionRecord(2))
        api.withdrawMethods = listOf("USDT", "支付宝")
        val vm = inviteViewModel().also { it.enterAndRefresh() }
        snapshot("36b-invite-records-light", dark = false) { InviteScreen(viewModel = vm, onBack = {}) }
    }

    // ==================== 公告页 ====================

    private fun notice(id: Int) = Notice(
        id = id,
        title = "线路升级公告 $id",
        body = "<p>为提供更稳定的服务，我们将于今日凌晨对 9929 线路进行升级维护，预计耗时 30 分钟，期间连接可能短暂中断。</p>",
        tags = listOf("公告", "维护"),
        createdAt = 1_700_000_000L + id * 86_400L,
    )

    /**
     * 公告页三态：加载态用 VM 初始状态（`phase = Loading`），其余用真实 [NoticeViewModel] +
     * [FakeAuthApi]（`notices`/`noticesError`）。假面板是同步返回的，`loadNotices()` 在
     * `viewModelScope`（Main.immediate）里当拍完成，`snapshot()` 里 `waitForIdle()` 会清空
     * 主线程队列，因此截图不会落在"还没加载完"的中间态。
     */
    private fun noticeViewModel(): NoticeViewModel = NoticeViewModel(subscribeRepository)

    @Test
    fun `38a-公告-加载态-亮色`() {
        snapshot("38a-notice-loading-light", dark = false) {
            NoticeScreen(onBack = {}, viewModel = noticeViewModel())
        }
    }

    @Test
    fun `38b-公告-空态-亮色`() {
        api.notices = emptyList()
        val vm = noticeViewModel().also { it.enterAndRefresh() }
        snapshot("38b-notice-empty-light", dark = false) { NoticeScreen(onBack = {}, viewModel = vm) }
    }

    @Test
    fun `38c-公告-错误态-亮色`() {
        api.noticesError = java.io.IOException("连接超时，请稍后重试")
        val vm = noticeViewModel().also { it.enterAndRefresh() }
        snapshot("38c-notice-error-light", dark = false) { NoticeScreen(onBack = {}, viewModel = vm) }
    }

    @Test
    fun `38d-公告-列表-亮色`() {
        api.notices = listOf(notice(1), notice(2), notice(3))
        val vm = noticeViewModel().also { it.enterAndRefresh() }
        snapshot("38d-notice-list-light", dark = false) { NoticeScreen(onBack = {}, viewModel = vm) }
    }

    @Test
    fun `38e-公告-列表-暗色`() {
        api.notices = listOf(notice(1), notice(2), notice(3))
        val vm = noticeViewModel().also { it.enterAndRefresh() }
        snapshot("38e-notice-list-dark", dark = true) { NoticeScreen(onBack = {}, viewModel = vm) }
    }

    // ==================== 工单页 ====================

    /**
     * 工单页三态。
     *
     * 加载态用 VM 初始状态（`phase = Loading`）；空态与列表态走真实 [TicketViewModel] +
     * [FakeAuthApi]（`tickets`/`ticketsError`）。工单仓库不校验登录态，所以这里不需要会话桩。
     */
    private fun ticketViewModel(): TicketViewModel = TicketViewModel(TicketRepository(api, sessionManager))

    private fun ticket(id: Int, closed: Boolean = false) = Ticket(
        id = id,
        level = 1,
        replyStatus = 0,
        status = if (closed) 1 else 0,
        subject = "无法连接节点 $id",
        createdAt = 1_700_000_000L + id * 86_400L,
        updatedAt = 1_700_000_000L + id * 86_400L,
    )

    @Test
    fun `39a-工单-加载态-亮色`() {
        snapshot("39a-ticket-loading-light", dark = false) {
            TicketScreen(onBack = {}, viewModel = ticketViewModel())
        }
    }

    @Test
    fun `39-工单-空态-亮色`() {
        api.tickets = emptyList()
        val vm = ticketViewModel().also { it.enterAndRefresh() }
        snapshot("39-ticket-empty-light", dark = false) { TicketScreen(onBack = {}, viewModel = vm) }
    }

    @Test
    fun `39b-工单-错误态-亮色`() {
        api.ticketsError = java.io.IOException("连接超时，请稍后重试")
        val vm = ticketViewModel().also { it.enterAndRefresh() }
        snapshot("39b-ticket-error-light", dark = false) { TicketScreen(onBack = {}, viewModel = vm) }
    }

    @Test
    fun `39c-工单-列表-亮色`() {
        api.tickets = listOf(ticket(1), ticket(2, closed = true), ticket(3))
        val vm = ticketViewModel().also { it.enterAndRefresh() }
        snapshot("39c-ticket-list-light", dark = false) { TicketScreen(onBack = {}, viewModel = vm) }
    }

    @Test
    fun `39d-工单-列表-暗色`() {
        api.tickets = listOf(ticket(1), ticket(2, closed = true))
        val vm = ticketViewModel().also { it.enterAndRefresh() }
        snapshot("39d-ticket-list-dark", dark = true) { TicketScreen(onBack = {}, viewModel = vm) }
    }

    // ==================== 关于页 ====================

    /**
     * 关于页用 mock VM 渲染：真实 [UpdateViewModel] 的 init 里有一个 `collect` 永不结束的协程与
     * 一个 `delay` 重试循环，在 compose 测试作用域 teardown 时被取消，会被 kotlinx-coroutines-test
     * 报成 `KotlinNothingValueException`（脚手架产物，与页面渲染无关）。这里只喂三个 StateFlow，
     * 用于验证页面本身能否正常渲染。
     */
    private fun aboutViewModel(
        state: UpdateUiState = UpdateUiState.Idle,
        siteInfo: SiteInfo = SiteInfo(),
        kernelVersion: String? = "1.9.2-alpha",
    ): UpdateViewModel {
        val vm = mockk<UpdateViewModel>(relaxed = true)
        every { vm.state } returns MutableStateFlow(state)
        every { vm.kernelVersion } returns MutableStateFlow(kernelVersion)
        every { vm.siteInfo } returns MutableStateFlow<SiteInfo?>(siteInfo)
        return vm
    }
    @Test
    fun `40-关于-亮色`() {
        val vm = aboutViewModel()
        snapshot("40-about-light", dark = false) { AboutScreen(onBack = {}, viewModel = vm) }
    }

    /**
     * 关于页没有整页三态：加载态体现为「检查更新」行内的转圈（[UpdateUiState.Checking]），
     * 错误态是系统 Toast 气泡（`LaunchedEffect` 里弹，不在本页位图内，且系统 Toast 是独立窗口
     * 无法被 Robolectric 位图捕获），空态不存在（页面内容为静态版本信息 + 面板下发的站点名）。
     * 因此这里用 `Checking`（loading 类）与"站点信息缺失/下发"两种数据态补齐可截图证据，
     * 其余在交付报告的对账清单里逐条注明取证方式。
     */
    @Test
    fun `40a-关于-检查更新中-亮色`() {
        val vm = aboutViewModel(state = UpdateUiState.Checking)
        snapshot("40a-about-checking-light", dark = false) { AboutScreen(onBack = {}, viewModel = vm) }
    }

    @Test
    fun `40b-关于-站点信息下发-亮色`() {
        val vm = aboutViewModel(
            siteInfo = SiteInfo(appName = "北辰 Polaris", appDescription = "面向开发者的稳定加速客户端"),
        )
        snapshot("40b-about-siteinfo-light", dark = false) { AboutScreen(onBack = {}, viewModel = vm) }
    }

    @Test
    fun `40c-关于-暗色`() {
        val vm = aboutViewModel()
        snapshot("40c-about-dark", dark = true) { AboutScreen(onBack = {}, viewModel = vm) }
    }

    // ==================== 设置页（v5） ====================

    private fun settingsViewModel() = SettingsViewModel(
        authRepository = authRepository,
        subscribeRepository = subscribeRepository,
        kernelProxy = kernelProxy,
        localeStore = LocaleStore(context),
        themePreference = ThemePreference(context),
        routingStateStore = routingStateStore,
        kernelConfig = kernelConfig,
    )

    @Test
    fun `41-设置-亮色`() {
        val vm = settingsViewModel()
        snapshot("41-settings-light", dark = false) {
            V5SettingsScreen(viewModel = vm, onBack = {}, onAppearance = {}, onLanguage = {}, onTunStack = {}, onChangePassword = {})
        }
    }

    @Test
    fun `42-设置-暗色`() {
        val vm = settingsViewModel()
        snapshot("42-settings-dark", dark = true) {
            V5SettingsScreen(viewModel = vm, onBack = {}, onAppearance = {}, onLanguage = {}, onTunStack = {}, onChangePassword = {})
        }
    }

    // ==================== 分流规则页（v5） ====================

    @Test
    fun `43-分流规则-亮色`() {
        val vm = RoutingRulesViewModel(routingStateStore, kernelConfig)
        snapshot("43-routingrules-light", dark = false) { V5RoutingRulesScreen(onBack = {}, viewModel = vm) }
    }

    // ==================== 认证流 ====================

    /**
     * 登录页用 mock VM：真实 [LoginViewModel] 的 init 会 `collect` 永不结束的
     * `authRepository.sessionState`，teardown 取消时会污染测试报告（同上）。
     *
     * [state] 参数让"加载态"也能取证：[LoginUiState.LoggingIn] 会让按钮与整页进入提交中，
     * 是这一页唯一可截图的 loading 态（认证页没有列表式的空态）。
     */
    private fun loginViewModel(
        state: LoginUiState = LoginUiState.Form(),
    ): LoginViewModel {
        val vm = mockk<LoginViewModel>(relaxed = true)
        every { vm.uiState } returns MutableStateFlow(state)
        return vm
    }

    @Test
    fun `44-登录-亮色`() {
        snapshot("44-login-light", dark = false) { LoginScreen(viewModel = loginViewModel()) }
    }

    @Test
    fun `45-登录-暗色`() {
        snapshot("45-login-dark", dark = true) { LoginScreen(viewModel = loginViewModel()) }
    }

    @Test
    fun `44a-登录-提交中-亮色`() {
        val vm = loginViewModel(LoginUiState.LoggingIn(LoginUiState.Form()))
        snapshot("44a-login-loading-light", dark = false) { LoginScreen(viewModel = vm) }
    }

    @Test
    fun `44b-登录-字段校验错误-亮色`() {
        val vm =
            loginViewModel(
                LoginUiState.Error(
                    form = LoginUiState.Form(),
                    messageRes = R.string.error_email_required,
                ),
            )
        snapshot("44b-login-error-light", dark = false) { LoginScreen(viewModel = vm) }
    }

    /*
     * 面板地址确认弹窗**不做截图**，原因已实证：
     * `44c` 与 `44` 两张 PNG **字节完全相同**（MD5 都是 346ffb9f4fc0），且画面没有变暗
     * ——说明 `AlertDialog` 没有出现在被捕获的 decor view 里。Material3 的 `AlertDialog`
     * 渲染在**独立的 Window** 上，而 `snapshot()` 只抓 Activity 的 decorView 位图，抓不到它。
     *
     * 不能留一张和普通表单一模一样的图来冒充"弹窗已渲染"的凭证，所以这里改成用语义断言
     * 证明弹窗确实拉起（见 LoginScreenJvmTest `面板地址确认弹窗显示私网警告`）。
     * 像素级凭证留到雷电模拟器走查时补。
     */

    @Test
    fun `44c-登录-面板地址确认-亮色（仅证明页面不崩，弹窗本体见单测）`() {
        val vm =
            loginViewModel(
                LoginUiState.ConfirmPanelUrl(
                    form = LoginUiState.Form(),
                    normalizedUrl = "https://panel.example.com",
                    isPrivateHost = true,
                    pendingAction = PendingAction.LOGIN,
                ),
            )
        snapshot("44c-login-confirm-light", dark = false) { LoginScreen(viewModel = vm) }
    }

    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    @Test
    fun `46-登录-英文-亮色`() {
        snapshot("46-login-en-light", dark = false) { LoginScreen(viewModel = loginViewModel()) }
    }

    @Test
    fun `47-注册-邀请码选填-亮色`() {
        val vm = RegisterViewModel(authRepository, CountdownUseCase())
        snapshot("47-register-light", dark = false) {
            RegisterScreen(emailVerifyEnabled = false, inviteForceEnabled = false, onBackToLogin = {}, viewModel = vm)
        }
    }

    /**
     * 注册页的两种面板配置。
     *
     * 注意 `47`（选填 / 无验证码）与 `48`（必填 / 有验证码）**看起来"邀请码文案自相矛盾"**，
     * 其实不是：这一页的邀请码标签与验证码字段都由面板下发的
     * `inviteForceEnabled` / `emailVerifyEnabled` 驱动，两张图是**两种不同配置**的证据。
     * （第 4 轮的视觉复核提过这条"矛盾"，核对源码后确认为测试夹具差异。）
     */
    @Test
    fun `48-注册-邀请码必填与邮箱验证-亮色`() {
        val vm = RegisterViewModel(authRepository, CountdownUseCase())
        snapshot("48-register-verify-light", dark = false) {
            RegisterScreen(emailVerifyEnabled = true, inviteForceEnabled = true, onBackToLogin = {}, viewModel = vm)
        }
    }

    @Test
    fun `49-忘记密码-亮色`() {
        val vm = ForgotPasswordViewModel(authRepository, CountdownUseCase())
        snapshot("49-forgot-light", dark = false) {
            ForgotPasswordScreen(onBackToLogin = {}, onResetSuccess = {}, viewModel = vm)
        }
    }

    /**
     * 忘记密码页的"加载态"用 mock VM 精确喂 [ForgotPasswordUiState.Resetting]：
     * 真实 VM 的倒计时/发码都有协程时序，截图会不稳定。认证页没有列表式空态。
     */
    @Test
    fun `49a-忘记密码-重置中-亮色`() {
        val vm = mockk<ForgotPasswordViewModel>(relaxed = true)
        every { vm.uiState } returns
            MutableStateFlow<ForgotPasswordUiState>(
                ForgotPasswordUiState.Resetting(ForgotPasswordUiState.Form()),
            )
        snapshot("49a-forgot-loading-light", dark = false) {
            ForgotPasswordScreen(onBackToLogin = {}, onResetSuccess = {}, viewModel = vm)
        }
    }

    @Test
    fun `49b-忘记密码-倒计时-亮色`() {
        val vm = mockk<ForgotPasswordViewModel>(relaxed = true)
        every { vm.uiState } returns
            MutableStateFlow<ForgotPasswordUiState>(
                ForgotPasswordUiState.Countdown(ForgotPasswordUiState.Form(), seconds = 42),
            )
        snapshot("49b-forgot-countdown-light", dark = false) {
            ForgotPasswordScreen(onBackToLogin = {}, onResetSuccess = {}, viewModel = vm)
        }
    }

    @Test
    fun `47a-注册-注册中-亮色`() {
        val vm = mockk<RegisterViewModel>(relaxed = true)
        every { vm.uiState } returns
            MutableStateFlow<RegisterUiState>(RegisterUiState.Registering(RegisterUiState.Form()))
        snapshot("47a-register-loading-light", dark = false) {
            RegisterScreen(emailVerifyEnabled = true, inviteForceEnabled = false, onBackToLogin = {}, viewModel = vm)
        }
    }

    @Test
    fun `47b-注册-暗色`() {
        val vm = mockk<RegisterViewModel>(relaxed = true)
        every { vm.uiState } returns MutableStateFlow<RegisterUiState>(RegisterUiState.Form())
        snapshot("47b-register-dark", dark = true) {
            RegisterScreen(emailVerifyEnabled = true, inviteForceEnabled = true, onBackToLogin = {}, viewModel = vm)
        }
    }
}
