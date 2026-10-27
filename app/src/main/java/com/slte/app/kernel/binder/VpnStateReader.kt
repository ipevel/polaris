package com.slte.app.kernel.binder

import android.content.Context
import android.net.Uri
import com.github.kr328.clash.common.constants.Authorities
import com.github.kr328.clash.service.StatusProvider
import com.slte.app.kernel.api.KernelVpnState
import com.slte.app.kernel.api.KernelVpnStatus
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog

/** 只读查询 :background 进程的 VPN 状态，避免把 ContentResolver 细节写进连接管理。 */
internal class VpnStateReader(
    private val context: Context,
) {
    fun query(): KernelVpnStatus = try {
        val result = context.contentResolver.call(
            Uri
                .Builder()
                .scheme("content")
                .authority(Authorities.STATUS_PROVIDER)
                .build(),
            StatusProvider.METHOD_KERNEL_STATE,
            null,
            null,
        )
        val state = when (result?.getString(StatusProvider.KEY_STATE)) {
            "Starting" -> KernelVpnState.STARTING
            "Running" -> KernelVpnState.RUNNING
            "Stopping" -> KernelVpnState.STOPPING
            "Failed" -> KernelVpnState.FAILED
            else -> KernelVpnState.STOPPED
        }
        KernelVpnStatus(state, result?.getString(StatusProvider.KEY_REASON))
    } catch (e: Exception) {
        AppLog.w("SLTE-Kernel", "syncVpnState 查询失败: ${sanitizeLog(e.message ?: "Unknown")}")
        KernelVpnStatus()
    }
}
