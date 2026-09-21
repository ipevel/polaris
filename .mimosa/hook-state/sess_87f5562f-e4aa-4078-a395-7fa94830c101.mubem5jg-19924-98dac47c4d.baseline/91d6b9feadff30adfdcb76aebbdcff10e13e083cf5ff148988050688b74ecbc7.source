package com.slte.app.ui.screen.plans

import com.slte.app.R
import com.slte.app.data.remote.ApiException
import com.slte.app.data.remote.api.dto.CheckoutResultDto
import com.slte.app.data.remote.api.dto.CouponCheckResultDto
import com.slte.app.data.remote.api.dto.CreateOrderResultDto
import com.slte.app.data.remote.api.dto.OrderInfoDto
import com.slte.app.data.remote.api.dto.PaymentMethodDto
import com.slte.app.data.repository.OrderRepository
import com.slte.app.domain.model.PlanInfo
import com.slte.app.support.FakeAuthApi
import com.slte.app.support.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PurchaseViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val api = FakeAuthApi()
    private val repository = OrderRepository(api)

    private fun viewModel() = PurchaseViewModel(
        couponChecker = CouponChecker(repository),
        paymentLoader = OrderPaymentLoader(repository),
        poller = OrderPaymentPoller(repository),
        orderCreator = OrderCreator(repository),
        paymentCheckout = PaymentCheckout(repository),
    )

    private fun plan() = PlanInfo(id = 7, name = "进阶套餐", periodPrices = emptyList())

    private fun orderDetail(status: Int) = OrderInfoDto(
        id = 1,
        tradeNo = "TN-1",
        planName = "进阶套餐",
        totalAmount = 5_000,
        status = status,
        createdAt = 1_700_000_000L,
        expiredAt = 1_800_000_000L,
    )

    @Test
    fun `选择套餐进入选周期态，改优惠券会清空已验证标记`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()

        vm.startPurchase(plan())
        assertTrue(vm.step.value is PurchaseStep.SelectPeriod)

        vm.selectPeriod("month_price")
        api.couponResult = CouponCheckResultDto(name = "八折", type = 2, value = 20)
        vm.updateCouponCode("SAVE")
        vm.verifyCoupon()
        advanceUntilIdle()

        val verified = vm.step.value as PurchaseStep.SelectPeriod
        assertTrue(verified.couponVerified)
        assertEquals(R.string.purchase_coupon_applied, vm.toastRes.value)

        vm.updateCouponCode("SAVE2")
        val reset = vm.step.value as PurchaseStep.SelectPeriod
        assertTrue("改码后应重新验证", !reset.couponVerified)
        assertEquals(0, reset.couponDiscount)
    }

    @Test
    fun `未验证的优惠券不允许进入下单`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        vm.startPurchase(plan())
        vm.selectPeriod("month_price")
        vm.updateCouponCode("SAVE")

        vm.showConfirmWarning()
        advanceUntilIdle()

        assertEquals(R.string.purchase_coupon_verify_first, vm.toastRes.value)
        val step = vm.step.value as PurchaseStep.SelectPeriod
        assertTrue("不应打开确认警告", !step.showWarning)
    }

    @Test
    fun `确认下单成功返回订单号并结束流程`() = runTest(mainRule.dispatcher) {
        api.createOrderResult = CreateOrderResultDto(tradeNo = "TN-9")
        val vm = viewModel()
        vm.startPurchase(plan())
        vm.selectPeriod("month_price")

        vm.confirmWarning()
        advanceUntilIdle()

        assertEquals("TN-9", vm.createdTradeNo.value)
        assertEquals(PurchaseStep.Idle, vm.step.value)

        vm.clearCreatedTradeNo()
        assertEquals(null, vm.createdTradeNo.value)
    }

    @Test
    fun `存在未支付订单时进入错误态，返回可回到原选择`() = runTest(mainRule.dispatcher) {
        api.createOrderError = ApiException("您有未支付的订单")
        api.couponResult = CouponCheckResultDto(name = "满减", type = 1, value = 100)
        val vm = viewModel()
        vm.startPurchase(plan())
        vm.selectPeriod("quarter_price")
        vm.updateCouponCode("SAVE")
        vm.verifyCoupon()
        advanceUntilIdle()

        vm.confirmWarning()
        advanceUntilIdle()

        assertTrue(vm.step.value is PurchaseStep.ExistingOrderError)

        vm.goBack()
        val restored = vm.step.value as PurchaseStep.SelectPeriod
        assertEquals("季度周期应保留", "quarter_price", restored.selectedPeriod)
        assertEquals("SAVE", restored.couponCode)
    }

    @Test
    fun `下单失败进入创建错误态`() = runTest(mainRule.dispatcher) {
        api.createOrderError = java.io.IOException("boom")
        val vm = viewModel()
        vm.startPurchase(plan())
        vm.selectPeriod("month_price")

        vm.confirmWarning()
        advanceUntilIdle()

        assertTrue(vm.step.value is PurchaseStep.OrderCreateError)
    }

    @Test
    fun `加载已完成订单的支付信息时直接提示已支付`() = runTest(mainRule.dispatcher) {
        api.orderDetail = orderDetail(status = 3)
        val vm = viewModel()

        vm.loadPaymentForOrder("TN-1")
        advanceUntilIdle()

        assertEquals(R.string.order_already_paid, vm.toastRes.value)
    }

    @Test
    fun `加载待支付订单进入支付态并默认选中第一个方式`() = runTest(mainRule.dispatcher) {
        api.orderDetail = orderDetail(status = 0)
        api.paymentMethods = listOf(PaymentMethodDto(id = 3, name = "支付宝"), PaymentMethodDto(id = 4, name = "微信"))
        val vm = viewModel()

        vm.loadPaymentForOrder("TN-1")
        advanceUntilIdle()

        val step = vm.step.value as PurchaseStep.OrderPayment
        assertEquals(3, step.selectedMethod)
        assertEquals("进阶套餐", step.planName)

        vm.selectPaymentMethod(4)
        assertEquals(4, (vm.step.value as PurchaseStep.OrderPayment).selectedMethod)
    }

    @Test
    fun `余额支付成功结束流程并发出完成事件`() = runTest(mainRule.dispatcher) {
        api.orderDetail = orderDetail(status = 0)
        api.paymentMethods = listOf(PaymentMethodDto(id = 3, name = "支付宝"))
        api.checkoutResult = CheckoutResultDto(type = -1)
        val vm = viewModel()
        vm.loadPaymentForOrder("TN-1")
        advanceUntilIdle()

        vm.confirmPayment()
        advanceUntilIdle()

        assertEquals(R.string.order_pay_success, vm.toastRes.value)
        assertEquals(PurchaseStep.Idle, vm.step.value)
        assertEquals("TN-1", vm.paymentCompleted.first())
    }

    @Test
    fun `需要跳转浏览器时进入支付中态`() = runTest(mainRule.dispatcher) {
        api.orderDetail = orderDetail(status = 0)
        api.paymentMethods = listOf(PaymentMethodDto(id = 3, name = "支付宝"))
        api.checkoutResult = CheckoutResultDto(type = 1, redirectUrl = "https://pay.example.com/x")
        val vm = viewModel()
        vm.loadPaymentForOrder("TN-1")
        advanceUntilIdle()

        vm.confirmPayment()
        advanceUntilIdle()

        val step = vm.step.value as PurchaseStep.Paying
        assertEquals("https://pay.example.com/x", step.redirectUrl)
    }

    @Test
    fun `结算拿不到跳转地址时复位支付按钮`() = runTest(mainRule.dispatcher) {
        api.orderDetail = orderDetail(status = 0)
        api.paymentMethods = listOf(PaymentMethodDto(id = 3, name = "支付宝"))
        api.checkoutResult = CheckoutResultDto(type = 1, redirectUrl = null)
        val vm = viewModel()
        vm.loadPaymentForOrder("TN-1")
        advanceUntilIdle()

        vm.confirmPayment()
        advanceUntilIdle()

        val step = vm.step.value as PurchaseStep.OrderPayment
        assertEquals(R.string.order_pay_failed, vm.toastRes.value)
        assertTrue("必须复位，否则支付按钮永久禁用", !step.isPaying)
    }

    @Test
    fun `结算失败复位支付中并优先使用适配器错误资源`() = runTest(mainRule.dispatcher) {
        api.orderDetail = orderDetail(status = 0)
        api.paymentMethods = listOf(PaymentMethodDto(id = 3, name = "支付宝"))
        api.checkoutError = ApiException("余额不足", stringResId = R.string.error_coupon_invalid)
        val vm = viewModel()
        vm.loadPaymentForOrder("TN-1")
        advanceUntilIdle()

        vm.confirmPayment()
        advanceUntilIdle()

        assertEquals(R.string.error_coupon_invalid, vm.toastRes.value)
        assertTrue(!(vm.step.value as PurchaseStep.OrderPayment).isPaying)
    }

    @Test
    fun `从浏览器返回时停止流程回到空闲`() = runTest(mainRule.dispatcher) {
        api.orderDetail = orderDetail(status = 0)
        api.paymentMethods = listOf(PaymentMethodDto(id = 3, name = "支付宝"))
        val vm = viewModel()
        vm.loadPaymentForOrder("TN-1")
        advanceUntilIdle()

        vm.onPaymentReturn()

        assertEquals(PurchaseStep.Idle, vm.step.value)
    }
}
