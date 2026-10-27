package com.slte.app.data.remote.adapter.v2board

import com.slte.app.data.remote.api.ApiHeaders
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * 两个面板路径与出入参完全一致的接口。
 *
 * 面板各接口继承这里，只声明真正不同的端点（会话列表、礼品卡兑换等），
 * 避免同一份端点定义写两遍、改一处漏一处。
 */

interface V2BoardAuthRetrofit {
    @POST("passport/auth/login")
    suspend fun login(
        @Body request: V2BoardLoginRequest,
    ): V2BoardResponse<V2BoardLoginData>

    @POST("passport/auth/register")
    suspend fun register(
        @Body request: V2BoardRegisterRequest,
    ): V2BoardResponse<V2BoardLoginData>

    @GET("guest/comm/config")
    suspend fun fetchConfig(): V2BoardResponse<V2BoardSiteConfig>

    @POST("passport/auth/forget")
    suspend fun forgotPassword(
        @Body request: V2BoardForgotRequest,
    ): V2BoardResponse<Boolean>

    @POST("passport/comm/sendEmailVerify")
    suspend fun sendEmailCode(
        @Body request: V2BoardSendCodeRequest,
    ): V2BoardResponse<Boolean>
}

interface V2BoardUserRetrofit {
    // 用户信息 / 套餐 / 公告 / 邀请码的开关字段两个面板类型不同（0/1 vs 布尔），
    // 相关端点与模型由各面板接口自行声明

    @GET("user/getSubscribe")
    suspend fun fetchSubscribe(): V2BoardResponse<V2BoardSubscribeData>

    @GET("user/order/fetch")
    suspend fun fetchOrders(): V2BoardResponse<List<V2BoardOrderData>>

    @POST("user/order/save")
    suspend fun createOrder(
        @Body request: V2BoardCreateOrderRequest,
    ): V2BoardResponse<String>

    @GET("user/order/detail")
    suspend fun getOrderDetail(
        @Query("trade_no") tradeNo: String,
    ): V2BoardResponse<V2BoardOrderData>

    @POST("user/coupon/check")
    suspend fun checkCoupon(
        @Body request: V2BoardCouponCheckRequest,
    ): V2BoardResponse<V2BoardCouponData>

    @POST("user/order/checkout")
    suspend fun checkoutOrder(
        @Body request: V2BoardCheckoutRequest,
    ): ResponseBody

    @GET("user/order/getPaymentMethod")
    suspend fun getPaymentMethods(): V2BoardResponse<List<V2BoardPaymentMethodData>>

    @POST("user/order/cancel")
    suspend fun cancelOrder(
        @Body request: V2BoardCancelOrderRequest,
    ): V2BoardResponse<Boolean>

    @GET("user/invite/save")
    suspend fun generateInviteCode(): V2BoardResponse<Boolean>

    @GET("user/invite/details")
    suspend fun fetchCommissionRecords(
        @Query("current") page: Int,
        @Query("page_size") pageSize: Int,
    ): V2BoardResponse<List<V2BoardCommissionRecordData>>

    @POST("user/transfer")
    suspend fun transferCommission(
        @Body request: V2BoardTransferRequest,
    ): V2BoardResponse<Boolean>

    @POST("user/ticket/withdraw")
    suspend fun withdrawCommission(
        @Body request: V2BoardWithdrawRequest,
    ): V2BoardResponse<Boolean>

    @GET("user/comm/config")
    suspend fun fetchUserCommConfig(): V2BoardResponse<V2BoardUserCommConfigData>

    @POST("user/removeActiveSession")
    suspend fun removeActiveSession(
        @Header("Authorization") authData: String?,
        @Body request: V2BoardRemoveSessionRequest,
    ): V2BoardResponse<Boolean>

    @GET
    @Headers(
        ApiHeaders.USER_AGENT_HEADER,
        ApiHeaders.NO_FAILOVER_HEADER,
    )
    suspend fun fetchSubscribeYaml(
        @Url url: String,
    ): ResponseBody

    /** 会话探测：只取状态码，不解析 body；两个面板共用 user/info。 */
    @GET("user/info")
    suspend fun probeSession(): Response<ResponseBody>

    @POST("user/update")
    suspend fun updateUserSettings(
        @Body request: V2BoardUpdateUserRequest,
    ): V2BoardResponse<Boolean>

    @POST("user/changePassword")
    suspend fun changePassword(
        @Body request: V2BoardChangePasswordRequest,
    ): V2BoardResponse<Boolean>
}
