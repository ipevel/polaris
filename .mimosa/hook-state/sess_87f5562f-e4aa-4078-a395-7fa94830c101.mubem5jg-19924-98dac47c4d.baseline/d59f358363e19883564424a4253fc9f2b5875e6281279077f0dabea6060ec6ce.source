package com.slte.app.data.repository

import com.slte.app.data.remote.api.AuthApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GiftCardRepository
@Inject
constructor(
    private val authApi: AuthApi,
) {
    /** 兑换礼品卡：成功静默返回；失败抛带后端文案的 ApiException（RepositoryUtils.runApi 包装为 Result）。 */
    suspend fun redeem(code: String): Result<Unit> = runApi {
        authApi.redeemGiftCard(code.trim())
    }
}
