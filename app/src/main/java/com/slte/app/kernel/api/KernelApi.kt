package com.slte.app.kernel.api

import android.content.Intent
import java.util.UUID
import kotlinx.coroutines.flow.StateFlow

/** 业务层唯一入口；实现由 [com.slte.app.kernel.factory.KernelFactory] 创建。 */
interface KernelApi {
    val type: KernelType
    val capabilities: Set<KernelCapability>

    val bindingState: StateFlow<KernelBindingState>
    val vpnConnected: StateFlow<Boolean>
    val vpnStatus: StateFlow<KernelVpnStatus>
    val profileLoaded: StateFlow<Int>

    fun bind()
    fun vpnRequestIntent(): Intent?
    fun startVpn()
    fun stopVpn()
    fun notifyProfileChanged(uuid: UUID)
    fun notifyOverrideChanged()

    suspend fun awaitClash(): KernelClash?
    suspend fun awaitProfile(): KernelProfiles?

    // ---- 阶段 2：从业务层下沉的编排逻辑 ----

    suspend fun selectorGroup(): String?
    suspend fun waitForSelectorGroup(): String?
    suspend fun selectNode(name: String): Boolean
    suspend fun nodeNames(): List<String>
    suspend fun selectionInfo(): KernelServerInfo?
    suspend fun applyAutoSelection(): Boolean
    suspend fun applyFallbackSelection(): Boolean
    suspend fun ensureGlobalSelection()
    suspend fun currentNodeOf(groupType: SelectionGroupType): String?
    suspend fun triggerHealthCheckAll()
}
