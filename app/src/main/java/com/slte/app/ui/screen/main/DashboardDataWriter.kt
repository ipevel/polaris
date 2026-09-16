package com.slte.app.ui.screen.main

import com.slte.app.data.repository.ServerRepository
import com.slte.app.data.repository.SubscribeRepository
import com.slte.app.domain.model.SubscribeInfo
import com.slte.app.domain.model.isPlanValid
import com.slte.app.domain.usecase.DaysUntilExpiryUseCase
import com.slte.app.utils.AppLog
import com.slte.app.utils.Constants
import com.slte.app.utils.sanitizeLog
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardDataWriter
@Inject
constructor(
    private val subscribeRepository: SubscribeRepository,
    private val serverRepository: ServerRepository,
    private val daysUntilExpiryUseCase: DaysUntilExpiryUseCase,
) {

    fun seedServerName(data: MutableStateFlow<DashboardData>) {
        val cached = serverRepository.getCachedServers()
        if (!cached.isNullOrEmpty()) {
            data.update { it.copy(serverName = cached.first().name) }
        }
    }

    fun loadServers(
        scope: CoroutineScope,
        data: MutableStateFlow<DashboardData>,
    ) {
        scope.launch {
            serverRepository.fetchServers().fold(
                onSuccess = { servers ->
                    if (servers.isNotEmpty()) {
                        data.update { it.copy(serverName = servers.first().name) }
                    } else {
                        data.update {
                            it.copy(
                                serverName = Constants.PLACEHOLDER_DASH,
                                currentIp = Constants.PLACEHOLDER_DASH,
                            )
                        }
                    }
                },
                onFailure = { e ->
                    AppLog.w("SLTE-Main", "loadServers failed: ${sanitizeLog(e.message ?: "Unknown")}")
                },
            )
        }
    }

    fun finishPurchaseRefresh(data: MutableStateFlow<DashboardData>) {
        data.update { it.copy(isUpdating = false, isRefreshing = false, dataLoaded = true) }
    }

    fun applyCached(data: MutableStateFlow<DashboardData>) {
        subscribeRepository.getCachedSubscribeInfo()?.let {
            applySubscribeInfo(data, it, errorMessageRes = null)
        }
    }

    fun applySubscribeInfo(
        data: MutableStateFlow<DashboardData>,
        info: SubscribeInfo?,
        errorMessageRes: Int?,
    ) {
        if (!isPlanValid(info)) serverRepository.invalidateCache()
        val daysUntilExpired = daysUntilExpiryUseCase(info?.expiredAt ?: 0L)
        data.update { it.withSubscribeInfo(info, daysUntilExpired, errorMessageRes) }
    }
}

internal fun DashboardData.withSubscribeInfo(
    info: SubscribeInfo?,
    daysUntilExpired: Int,
    errorMessageRes: Int?,
): DashboardData = copy(
    usedBytes = info?.usedTraffic ?: 0L,
    totalBytes = info?.transferEnable ?: 0L,
    isValid = isPlanValid(info),
    hasPlan = info?.hasPlan == true,
    planName = info?.planName ?: "",
    daysUntilExpired = daysUntilExpired,
    expiredAt = info?.expiredAt ?: 0L,
    isRefreshing = false,
    isUpdating = false,
    dataLoaded = true,
    errorMessageRes = errorMessageRes,
)
