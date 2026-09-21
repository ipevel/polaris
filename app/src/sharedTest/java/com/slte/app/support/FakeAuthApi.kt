package com.slte.app.support

import com.slte.app.data.remote.api.AuthApi
import com.slte.app.data.remote.api.dto.CheckoutResultDto
import com.slte.app.data.remote.api.dto.CouponCheckResultDto
import com.slte.app.data.remote.api.dto.CreateOrderResultDto
import com.slte.app.data.remote.api.dto.LoginResponseDto
import com.slte.app.data.remote.api.dto.OrderInfoDto
import com.slte.app.data.remote.api.dto.PaymentMethodDto
import com.slte.app.data.remote.api.dto.PlanInfoDto
import com.slte.app.data.remote.api.dto.SubscribeInfoDto
import com.slte.app.data.remote.api.dto.UserInfoDto
import com.slte.app.domain.model.CommissionRecord
import com.slte.app.domain.model.EmailCodePurpose
import com.slte.app.domain.model.InviteInfo
import com.slte.app.domain.model.Notice
import com.slte.app.domain.model.RegisterConfig
import com.slte.app.domain.model.ServerNode
import com.slte.app.domain.model.Ticket
import com.slte.app.domain.model.TicketDetail
import com.slte.app.domain.model.TrafficLogRecord
import okhttp3.ResponseBody
import retrofit2.Response

open class FakeAuthApi : AuthApi {
    var couponResult: CouponCheckResultDto? = null
    var couponError: Throwable? = null
    var couponCode: String? = null

    var orderDetail: OrderInfoDto? = null
    var orderDetailError: Throwable? = null
    var orderDetailCalls = 0

    var paymentMethods: List<PaymentMethodDto> = emptyList()
    var orders: List<OrderInfoDto> = emptyList()
    var ordersError: Throwable? = null
    var plans: List<PlanInfoDto> = emptyList()
    var plansError: Throwable? = null
    var cancelError: Throwable? = null

    var redeemGiftCardError: Throwable? = null
    var createOrderResult: CreateOrderResultDto? = null
    var createOrderError: Throwable? = null
    var checkoutResult: CheckoutResultDto? = null
    var checkoutError: Throwable? = null
    var inviteInfo: InviteInfo? = null
    var inviteError: Throwable? = null
    var commissionRecords: List<CommissionRecord> = emptyList()
    var withdrawMethods: List<String> = emptyList()
    var withdrawMethodsError: Throwable? = null
    var servers: List<ServerNode> = emptyList()

    override suspend fun checkCoupon(
        code: String,
        planId: Int?,
    ): CouponCheckResultDto {
        couponCode = code
        couponError?.let { throw it }
        return couponResult ?: unsupported()
    }

    override suspend fun getOrderDetail(tradeNo: String): OrderInfoDto {
        orderDetailCalls++
        orderDetailError?.let { throw it }
        return orderDetail ?: unsupported()
    }

    override suspend fun getPaymentMethods(): List<PaymentMethodDto> = paymentMethods

    override suspend fun fetchServers(): List<ServerNode> = servers

    override suspend fun login(
        email: String,
        password: String,
    ): LoginResponseDto = unsupported()

    override suspend fun register(
        email: String,
        password: String,
        emailCode: String?,
        inviteCode: String?,
    ): LoginResponseDto = unsupported()

    override suspend fun fetchRegisterConfig(): RegisterConfig = unsupported()

    override suspend fun fetchSiteInfo(): com.slte.app.domain.model.SiteInfo = com.slte.app.domain.model.SiteInfo()

    override suspend fun forgotPassword(
        email: String,
        emailCode: String,
        password: String,
    ) = unsupported()

    override suspend fun sendEmailCode(
        email: String,
        purpose: EmailCodePurpose,
    ) = unsupported()

    override suspend fun revokeActiveSessions(authData: String) = unsupported()

    override suspend fun fetchUserInfo(): UserInfoDto = unsupported()

    override suspend fun fetchSubscribeInfo(): SubscribeInfoDto = unsupported()

    override suspend fun updateRemindExpire(enabled: Boolean) = unsupported()

    override suspend fun updateRemindTraffic(enabled: Boolean) = unsupported()

    override suspend fun changePassword(
        oldPassword: String,
        newPassword: String,
    ) = unsupported()

    override suspend fun fetchOrders(): List<OrderInfoDto> {
        ordersError?.let { throw it }
        return orders
    }

    override suspend fun fetchPlans(): List<PlanInfoDto> {
        plansError?.let { throw it }
        return plans
    }

    override suspend fun createOrder(
        planId: Int,
        period: String,
        couponCode: String?,
    ): CreateOrderResultDto {
        createOrderError?.let { throw it }
        return createOrderResult ?: unsupported()
    }

    override suspend fun checkoutOrder(
        tradeNo: String,
        paymentMethod: Int,
    ): CheckoutResultDto {
        checkoutError?.let { throw it }
        return checkoutResult ?: unsupported()
    }

    override suspend fun cancelOrder(tradeNo: String) {
        cancelError?.let { throw it }
    }

    override suspend fun redeemGiftCard(code: String) {
        redeemGiftCardError?.let { throw it }
    }

    override suspend fun fetchInviteInfo(): InviteInfo {
        inviteError?.let { throw it }
        return inviteInfo ?: unsupported()
    }

    override suspend fun generateInviteCode(): Boolean = unsupported()

    override suspend fun fetchCommissionRecords(
        page: Int,
        pageSize: Int,
    ): List<CommissionRecord> = commissionRecords

    override suspend fun transferCommission(transferAmount: Int): Boolean = unsupported()

    override suspend fun withdrawCommission(
        withdrawMethod: String,
        withdrawAccount: String,
    ): Boolean = unsupported()

    override suspend fun fetchWithdrawMethods(): List<String> {
        withdrawMethodsError?.let { throw it }
        return withdrawMethods
    }

    override suspend fun fetchNotices(
        page: Int,
        pageSize: Int,
    ): List<Notice> = unsupported()

    override suspend fun fetchSubscribeYaml(url: String): Response<ResponseBody>? = unsupported()

    override suspend fun fetchTrafficLog(): List<TrafficLogRecord> = emptyList()

    override suspend fun fetchTickets(): List<Ticket> = unsupported()

    override suspend fun fetchTelegramDiscussLink(): String? = null

    override suspend fun fetchTicketDetail(id: Int): TicketDetail = unsupported()

    override suspend fun createTicket(
        subject: String,
        level: Int,
        message: String,
    ): Boolean = unsupported()

    override suspend fun replyTicket(
        id: Int,
        message: String,
    ): Boolean = unsupported()

    override suspend fun closeTicket(id: Int): Boolean = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("测试未配置该接口")
}
