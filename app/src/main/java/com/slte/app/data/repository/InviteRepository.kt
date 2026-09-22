// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package com.slte.app.data.repository

import com.slte.app.data.remote.api.AuthApi
import com.slte.app.domain.model.CommissionRecord
import com.slte.app.domain.model.InviteInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InviteRepository
@Inject
constructor(
    private val authApi: AuthApi,
) {

    suspend fun fetchInviteInfo(): Result<InviteInfo> = runApi {
        authApi.fetchInviteInfo()
    }

    suspend fun generateInviteCode(): Result<Boolean> = runApi {
        authApi.generateInviteCode()
    }

    suspend fun fetchCommissionRecords(
        page: Int = 1,
        pageSize: Int = 10,
    ): Result<List<CommissionRecord>> = runApi {
        authApi.fetchCommissionRecords(page, pageSize)
    }

    suspend fun transferCommission(transferAmountCents: Int): Result<Boolean> = runApi {
        authApi.transferCommission(transferAmountCents)
    }

    suspend fun withdrawCommission(
        method: String,
        account: String,
    ): Result<Boolean> = runApi {
        authApi.withdrawCommission(method, account)
    }

    suspend fun fetchWithdrawMethods(): Result<List<String>> = runApi {
        authApi.fetchWithdrawMethods()
    }
}
