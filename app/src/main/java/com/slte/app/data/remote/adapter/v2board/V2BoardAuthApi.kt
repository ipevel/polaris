package com.slte.app.data.remote.adapter.v2board

import com.slte.app.BuildConfig
import com.slte.app.data.remote.ApiException
import com.slte.app.data.remote.adapter.AdapterExecute
import com.slte.app.data.remote.adapter.orEmptyLogged
import com.slte.app.data.remote.adapter.orFalseLogged
import com.slte.app.data.remote.adapter.orNullLogged
import com.slte.app.data.remote.adapter.parseEmailWhitelistSuffixes
import com.slte.app.data.remote.api.AuthApi
import com.slte.app.data.remote.api.SessionProbeResult
import com.slte.app.data.remote.api.dto.CheckoutResultDto
import com.slte.app.data.remote.api.dto.CouponCheckResultDto
import com.slte.app.data.remote.api.dto.CreateOrderResultDto
import com.slte.app.data.remote.api.dto.LoginResponseDto
import com.slte.app.data.remote.api.dto.OrderInfoDto
import com.slte.app.data.remote.api.dto.PaymentMethodDto
import com.slte.app.data.remote.api.dto.SubscribeInfoDto
import com.slte.app.domain.model.CommissionRecord
import com.slte.app.domain.model.EmailCodePurpose
import com.slte.app.domain.model.EmailWhitelist
import com.slte.app.domain.model.RegisterConfig
import com.slte.app.domain.model.ServerNode
import com.slte.app.utils.ApiErrors
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
import kotlinx.coroutines.CancellationException

/**
 * V2Board / Xboard 共用的 [AuthApi] 实现。
 *
 * 两个面板的接口语义与业务规则一致，差异只有三处，由子类实现：
 * 1. [activeSessionIds]：会话列表结构不同（v2board 是 map、Xboard 是数组）；
 * 2. [redeemGiftCardRemote]：兑换端点与成功判定不同；
 * 3. [fetchServerNodes]：节点列表字段结构不同。
 *
 * 其余请求、错误映射与日志口径全部在这里写一次，避免两边各改一份而分叉。
 */
abstract class V2BoardAuthApi<U : V2BoardUserRetrofit>(
    protected val authApi: V2BoardAuthRetrofit,
    protected val userApi: U,
) : AuthApi {
    /** 服务端会话 id 列表：v2board 返回 map（键即 id），Xboard 返回令牌数组。 */
    protected abstract suspend fun activeSessionIds(authData: String): List<String>

    /** 礼品卡兑换：端点与成功判定由各面板实现。 */
    protected abstract suspend fun redeemGiftCardRemote(code: String)

    /** 节点列表：字段结构不同，由各面板解析成领域模型。 */
    protected abstract suspend fun fetchServerNodes(): List<ServerNode>

    override suspend fun login(
        email: String,
        password: String,
    ): LoginResponseDto {
        val response = AdapterExecute.typed { authApi.login(V2BoardLoginRequest(email, password)) }
        val data = response.data ?: throw ApiException("服务器返回数据为空", ApiErrors.EMPTY_DATA)
        AppLog.i("SLTE-Api", "login success")
        return data.toDomainLoginResponse()
    }

    override suspend fun register(
        email: String,
        password: String,
        emailCode: String?,
        inviteCode: String?,
    ): LoginResponseDto {
        val response =
            AdapterExecute.typed {
                authApi.register(
                    V2BoardRegisterRequest(
                        email = email,
                        password = password,
                        email_code = emailCode?.takeIf { it.isNotBlank() },
                        invite_code = inviteCode?.takeIf { it.isNotBlank() },
                    ),
                )
            }
        val data = response.data ?: throw ApiException("服务器返回数据为空", ApiErrors.EMPTY_DATA)
        AppLog.i("SLTE-Api", "register success")
        return data.toDomainLoginResponse()
    }

    override suspend fun fetchRegisterConfig(): RegisterConfig {
        val response = AdapterExecute.typed { authApi.fetchConfig() }
        val data = response.data ?: throw ApiException("获取注册配置失败", ApiErrors.REGISTER_CONFIG)
        return RegisterConfig(
            emailVerifyEnabled = data.is_email_verify == 1,
            inviteForceEnabled = data.is_invite_force == 1,
            emailWhitelist = EmailWhitelist(parseEmailWhitelistSuffixes(data.email_whitelist_suffix)),
        )
    }

    override suspend fun forgotPassword(
        email: String,
        emailCode: String,
        password: String,
    ) {
        AdapterExecute.typed {
            authApi.forgotPassword(V2BoardForgotRequest(email, password, emailCode))
        }
        AppLog.i("SLTE-Api", "forgotPassword success")
    }

    override suspend fun sendEmailCode(
        email: String,
        purpose: EmailCodePurpose,
    ) {
        val isForget =
            when (purpose) {
                EmailCodePurpose.REGISTER -> 0
                EmailCodePurpose.FORGOT_PASSWORD -> 1
            }
        AdapterExecute.typed {
            authApi.sendEmailCode(V2BoardSendCodeRequest(email, isforget = isForget))
        }
        AppLog.i("SLTE-Api", "sendEmailCode success purpose=$purpose")
    }

    override suspend fun revokeActiveSessions(authData: String) {
        val sessionIds = activeSessionIds(authData)
        if (sessionIds.isEmpty()) {
            AppLog.w("SLTE-Api", "revokeActiveSessions: 会话列表解析为空，登出后服务端会话可能未吊销")
            return
        }
        sessionIds.forEach { sessionId ->
            try {
                AdapterExecute.typed {
                    userApi.removeActiveSession(authData, V2BoardRemoveSessionRequest(sessionId))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w(
                    "SLTE-Api",
                    "revokeActiveSessions: 会话吊销失败，继续吊销其余会话 " +
                        "${sanitizeLog(e.javaClass.simpleName)}: ${sanitizeLog(e.message ?: "")}",
                )
            }
        }
    }

    override suspend fun updateRemindExpire(enabled: Boolean) {
        AdapterExecute.typed {
            userApi.updateUserSettings(V2BoardUpdateUserRequest(remindExpire = if (enabled) 1 else 0))
        }
    }

    override suspend fun updateRemindTraffic(enabled: Boolean) {
        AdapterExecute.typed {
            userApi.updateUserSettings(V2BoardUpdateUserRequest(remindTraffic = if (enabled) 1 else 0))
        }
    }

    override suspend fun changePassword(
        oldPassword: String,
        newPassword: String,
    ) {
        AdapterExecute.typed {
            userApi.changePassword(V2BoardChangePasswordRequest(oldPassword, newPassword))
        }
    }

    /** v2board 的退款金额取 refund_amount，Xboard 取 surplus_credit。 */
    protected abstract val surplusCreditAsRefund: Boolean

    override suspend fun fetchSubscribeInfo(): SubscribeInfoDto {
        val response = AdapterExecute.typed { userApi.fetchSubscribe() }
        // data 为空说明服务器没返回订阅数据（异常响应）：按失败处理；无套餐的账号返回的是带字段的空对象，不在这里
        val data =
            response.data
                ?: throw ApiException("服务器未返回订阅数据", ApiErrors.EMPTY_DATA)
        if (BuildConfig.DEBUG) {
            AppLog.d("SLTE-Api", "fetchSubscribeInfo: planId=${data.planId}, planName=${data.plan?.name}, expiredAt=${data.expiredAt}")
        }
        return data.toDomainSubscribeInfo()
    }

    override suspend fun fetchOrders(): List<OrderInfoDto> {
        val response = AdapterExecute.typed { userApi.fetchOrders() }
        val data = response.data.orEmptyLogged("fetchOrders")
        if (BuildConfig.DEBUG) {
            AppLog.d("SLTE-Api", "fetchOrders: 共 ${data.size} 条订单")
        }
        return data.map { it.toDomainOrder(surplusCreditAsRefund) }
    }

    override suspend fun createOrder(
        planId: Int,
        period: String,
        couponCode: String?,
    ): CreateOrderResultDto {
        val response =
            AdapterExecute.typed {
                userApi.createOrder(V2BoardCreateOrderRequest(planId, period, couponCode))
            }
        val tradeNo = response.data ?: throw ApiException("创建订单失败", ApiErrors.CREATE_ORDER)
        if (BuildConfig.DEBUG) {
            AppLog.d("SLTE-Api", "createOrder success: tradeNo=$tradeNo")
        }
        return CreateOrderResultDto(tradeNo)
    }

    override suspend fun getOrderDetail(tradeNo: String): OrderInfoDto {
        val response = AdapterExecute.typed { userApi.getOrderDetail(tradeNo) }
        val data = response.data ?: throw ApiException("获取订单详情失败", ApiErrors.ORDER_DETAIL)
        return data.toDomainOrder(surplusCreditAsRefund)
    }

    override suspend fun checkCoupon(
        code: String,
        planId: Int?,
    ): CouponCheckResultDto {
        val response = AdapterExecute.typed { userApi.checkCoupon(V2BoardCouponCheckRequest(code, planId)) }
        val data = response.data ?: throw ApiException("优惠券无效", ApiErrors.COUPON_INVALID)
        if (BuildConfig.DEBUG) {
            AppLog.d("SLTE-Api", "checkCoupon raw: type=${data.type} value=${data.value} name=${data.name}")
        }
        return data.toDomainCouponCheck()
    }

    override suspend fun checkoutOrder(
        tradeNo: String,
        paymentMethod: Int,
    ): CheckoutResultDto {
        val body =
            AdapterExecute.raw {
                userApi.checkoutOrder(V2BoardCheckoutRequest(tradeNo, paymentMethod))
            }
        return body.use { CheckoutResultDto.fromRawJson(it.string()) }
            ?: throw ApiException("结算响应无法解析", ApiErrors.CHECKOUT)
    }

    override suspend fun getPaymentMethods(): List<PaymentMethodDto> {
        val response = AdapterExecute.typed { userApi.getPaymentMethods() }
        return response.data.orEmptyLogged("getPaymentMethods").map { it.toDomainPaymentMethod() }
    }

    override suspend fun cancelOrder(tradeNo: String) {
        AdapterExecute.typed { userApi.cancelOrder(V2BoardCancelOrderRequest(tradeNo)) }
        if (BuildConfig.DEBUG) {
            AppLog.d("SLTE-Api", "cancelOrder: tradeNo=$tradeNo")
        }
    }

    override suspend fun redeemGiftCard(code: String) {
        redeemGiftCardRemote(code)
        AppLog.i("SLTE-Api", "redeemGiftCard success")
    }

    override suspend fun generateInviteCode(): Boolean {
        val response = AdapterExecute.typed { userApi.generateInviteCode() }
        return response.data.orFalseLogged("generateInviteCode")
    }

    override suspend fun fetchCommissionRecords(
        page: Int,
        pageSize: Int,
    ): List<CommissionRecord> {
        val response = AdapterExecute.typed { userApi.fetchCommissionRecords(page, pageSize) }
        val data = response.data.orEmptyLogged("fetchCommissionRecords")
        if (BuildConfig.DEBUG) {
            AppLog.d("SLTE-Api", "fetchCommissionRecords: ${data.size} 条记录")
        }
        return data.map { it.toDomain() }
    }

    override suspend fun transferCommission(transferAmount: Int): Boolean {
        val response =
            AdapterExecute.typed {
                userApi.transferCommission(V2BoardTransferRequest(transferAmount))
            }
        return response.data.orFalseLogged("transferCommission")
    }

    override suspend fun withdrawCommission(
        withdrawMethod: String,
        withdrawAccount: String,
    ): Boolean {
        val response =
            AdapterExecute.typed {
                userApi.withdrawCommission(V2BoardWithdrawRequest(withdrawMethod, withdrawAccount))
            }
        if (BuildConfig.DEBUG) {
            AppLog.d("SLTE-Api", "withdrawCommission: method=$withdrawMethod")
        }
        return response.data.orFalseLogged("withdrawCommission")
    }

    override suspend fun fetchWithdrawMethods(): List<String> {
        val response = AdapterExecute.typed { userApi.fetchUserCommConfig() }
        val data = response.data.orNullLogged("fetchWithdrawMethods") ?: return emptyList()
        if (data.withdrawClose == 1) return emptyList()
        return data.withdrawMethods.orEmpty()
    }

    override suspend fun probeSession(): SessionProbeResult = try {
        val response = userApi.probeSession()
        // 不读 body，直接关闭释放连接（Retrofit 包装后 raw() 不可直接关闭）
        (response.body() ?: response.errorBody())?.close()
        when {
            response.isSuccessful -> SessionProbeResult.VALID
            response.code() == 401 || response.code() == 403 -> SessionProbeResult.INVALID
            else -> SessionProbeResult.UNKNOWN
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLog.w("SLTE-Api", "probeSession 请求异常，按未知处理: ${sanitizeLog(e.javaClass.simpleName)}")
        SessionProbeResult.UNKNOWN
    }

    override suspend fun fetchServers(): List<ServerNode> = fetchServerNodes()

    override suspend fun fetchSubscribeYaml(url: String): okhttp3.ResponseBody? = userApi.fetchSubscribeYaml(url)
}
