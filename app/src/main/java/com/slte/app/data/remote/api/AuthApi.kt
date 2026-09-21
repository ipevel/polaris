package com.slte.app.data.remote.api

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

interface AuthApi {
    suspend fun login(
        email: String,
        password: String,
    ): LoginResponseDto

    suspend fun register(
        email: String,
        password: String,
        emailCode: String? = null,
        inviteCode: String? = null,
    ): LoginResponseDto

    suspend fun fetchRegisterConfig(): RegisterConfig

    suspend fun forgotPassword(
        email: String,
        emailCode: String,
        password: String,
    )

    suspend fun sendEmailCode(
        email: String,
        purpose: EmailCodePurpose = EmailCodePurpose.FORGOT_PASSWORD,
    )

    suspend fun revokeActiveSessions(authData: String)

    suspend fun fetchUserInfo(): UserInfoDto

    suspend fun fetchSubscribeInfo(): SubscribeInfoDto

    suspend fun updateRemindExpire(enabled: Boolean)

    suspend fun updateRemindTraffic(enabled: Boolean)

    suspend fun changePassword(
        oldPassword: String,
        newPassword: String,
    )

    suspend fun fetchOrders(): List<OrderInfoDto>

    suspend fun fetchPlans(): List<PlanInfoDto>

    suspend fun createOrder(
        planId: Int,
        period: String,
        couponCode: String? = null,
    ): CreateOrderResultDto

    suspend fun getOrderDetail(tradeNo: String): OrderInfoDto

    suspend fun checkCoupon(
        code: String,
        planId: Int? = null,
    ): CouponCheckResultDto

    suspend fun checkoutOrder(
        tradeNo: String,
        paymentMethod: Int,
    ): CheckoutResultDto

    suspend fun getPaymentMethods(): List<PaymentMethodDto>

    suspend fun cancelOrder(tradeNo: String)

    /** 礼品卡兑换：成功静默返回，失败抛带后端文案的 [com.slte.app.data.remote.ApiException]。 */
    suspend fun redeemGiftCard(code: String)

    suspend fun fetchInviteInfo(): InviteInfo

    suspend fun generateInviteCode(): Boolean

    suspend fun fetchCommissionRecords(
        page: Int,
        pageSize: Int,
    ): List<CommissionRecord>

    suspend fun transferCommission(transferAmount: Int): Boolean

    suspend fun withdrawCommission(
        withdrawMethod: String,
        withdrawAccount: String,
    ): Boolean

    suspend fun fetchWithdrawMethods(): List<String>

    /** 面板后台配置的 Telegram 讨论组链接（未配置返回 null） */
    suspend fun fetchTelegramDiscussLink(): String?

    suspend fun fetchNotices(
        page: Int = 1,
        pageSize: Int = 20,
    ): List<Notice>

    suspend fun fetchServers(): List<ServerNode>

    suspend fun fetchSubscribeYaml(url: String): okhttp3.ResponseBody?

    suspend fun fetchTickets(): List<Ticket>

    suspend fun fetchTicketDetail(id: Int): TicketDetail

    suspend fun createTicket(
        subject: String,
        level: Int,
        message: String,
    ): Boolean

    suspend fun replyTicket(
        id: Int,
        message: String,
    ): Boolean

    suspend fun closeTicket(id: Int): Boolean

    suspend fun fetchTrafficLog(): List<TrafficLogRecord>
}
