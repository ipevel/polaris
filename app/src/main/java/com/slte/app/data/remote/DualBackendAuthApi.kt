// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.remote

import com.slte.app.data.local.ApiUrlStore
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
import com.slte.app.domain.model.SiteInfo
import com.slte.app.domain.model.Ticket
import com.slte.app.domain.model.TicketDetail
import com.slte.app.domain.model.TrafficLogRecord
import retrofit2.Response

/**
 * 双后端运行时代理：同时持有 XiaoV2b 与 Xboard 两套 [AuthApi] 适配器，
 * 每次调用按 [ApiUrlStore.backendType]（登录页用户选择，实时读取）路由到对应适配器。
 *
 * 相比旧的编译期单选（BuildConfig.API_TYPE），这里把两套适配器都实例化进包内，
 * 用户无需换包即可在登录页切换后端，切换后下一次请求即生效。
 */
class DualBackendAuthApi(
    private val xiaov2b: AuthApi,
    private val xboard: AuthApi,
    private val apiUrlStore: ApiUrlStore,
) : AuthApi {
    private fun active(): AuthApi = when (apiUrlStore.backendType) {
        "xboard" -> xboard
        else -> xiaov2b
    }

    override suspend fun login(
        email: String,
        password: String,
    ): LoginResponseDto = active().login(email, password)

    override suspend fun register(
        email: String,
        password: String,
        emailCode: String?,
        inviteCode: String?,
    ): LoginResponseDto = active().register(email, password, emailCode, inviteCode)

    override suspend fun fetchRegisterConfig(): RegisterConfig = active().fetchRegisterConfig()

    override suspend fun fetchSiteInfo(): SiteInfo = active().fetchSiteInfo()

    override suspend fun forgotPassword(
        email: String,
        emailCode: String,
        password: String,
    ) = active().forgotPassword(email, emailCode, password)

    override suspend fun sendEmailCode(
        email: String,
        purpose: EmailCodePurpose,
    ) = active().sendEmailCode(email, purpose)

    override suspend fun revokeActiveSessions(authData: String) = active().revokeActiveSessions(authData)

    override suspend fun fetchUserInfo(): UserInfoDto = active().fetchUserInfo()

    override suspend fun fetchSubscribeInfo(): SubscribeInfoDto = active().fetchSubscribeInfo()

    override suspend fun updateRemindExpire(enabled: Boolean) = active().updateRemindExpire(enabled)

    override suspend fun updateRemindTraffic(enabled: Boolean) = active().updateRemindTraffic(enabled)

    override suspend fun changePassword(
        oldPassword: String,
        newPassword: String,
    ) = active().changePassword(oldPassword, newPassword)

    override suspend fun fetchOrders(): List<OrderInfoDto> = active().fetchOrders()

    override suspend fun fetchPlans(): List<PlanInfoDto> = active().fetchPlans()

    override suspend fun createOrder(
        planId: Int,
        period: String,
        couponCode: String?,
    ): CreateOrderResultDto = active().createOrder(planId, period, couponCode)

    override suspend fun getOrderDetail(tradeNo: String): OrderInfoDto = active().getOrderDetail(tradeNo)

    override suspend fun checkCoupon(
        code: String,
        planId: Int?,
    ): CouponCheckResultDto = active().checkCoupon(code, planId)

    override suspend fun checkoutOrder(
        tradeNo: String,
        paymentMethod: Int,
    ): CheckoutResultDto = active().checkoutOrder(tradeNo, paymentMethod)

    override suspend fun getPaymentMethods(): List<PaymentMethodDto> = active().getPaymentMethods()

    override suspend fun cancelOrder(tradeNo: String) = active().cancelOrder(tradeNo)

    override suspend fun redeemGiftCard(code: String) = active().redeemGiftCard(code)

    override suspend fun fetchInviteInfo(): InviteInfo = active().fetchInviteInfo()

    override suspend fun generateInviteCode(): Boolean = active().generateInviteCode()

    override suspend fun fetchCommissionRecords(
        page: Int,
        pageSize: Int,
    ): List<CommissionRecord> = active().fetchCommissionRecords(page, pageSize)

    override suspend fun transferCommission(transferAmount: Int): Boolean = active().transferCommission(transferAmount)

    override suspend fun withdrawCommission(
        withdrawMethod: String,
        withdrawAccount: String,
    ): Boolean = active().withdrawCommission(withdrawMethod, withdrawAccount)

    override suspend fun fetchWithdrawMethods(): List<String> = active().fetchWithdrawMethods()

    override suspend fun fetchTelegramDiscussLink(): String? = active().fetchTelegramDiscussLink()

    override suspend fun fetchNotices(
        page: Int,
        pageSize: Int,
    ): List<Notice> = active().fetchNotices(page, pageSize)

    override suspend fun fetchServers(): List<ServerNode> = active().fetchServers()

    override suspend fun fetchSubscribeYaml(url: String): Response<okhttp3.ResponseBody>? = active().fetchSubscribeYaml(url)

    override suspend fun fetchTickets(): List<Ticket> = active().fetchTickets()

    override suspend fun fetchTicketDetail(id: Int): TicketDetail = active().fetchTicketDetail(id)

    override suspend fun createTicket(
        subject: String,
        level: Int,
        message: String,
    ): Boolean = active().createTicket(subject, level, message)

    override suspend fun replyTicket(
        id: Int,
        message: String,
    ): Boolean = active().replyTicket(id, message)

    override suspend fun closeTicket(id: Int): Boolean = active().closeTicket(id)

    override suspend fun fetchTrafficLog(): List<TrafficLogRecord> = active().fetchTrafficLog()
}
