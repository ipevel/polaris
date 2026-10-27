package com.slte.app.kernel

import com.github.kr328.clash.core.model.OverrideSlot
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.remote.IClashManager
import com.github.kr328.clash.service.remote.IProfileManager
import com.slte.app.kernel.api.KernelClash
import com.slte.app.kernel.api.KernelProfile
import com.slte.app.kernel.api.KernelProfileType
import com.slte.app.kernel.api.KernelProfiles
import com.slte.app.kernel.api.KernelProxyGroupSnapshot
import com.slte.app.kernel.api.KernelProxySnapshot
import com.slte.app.kernel.api.KernelProxySort
import com.slte.app.kernel.api.KernelTunnelMode
import com.slte.app.kernel.api.KernelUrlTestResult
import java.util.UUID

/** IClashManager → KernelClash 适配：把 kaidl 模型转成内核无关模型。 */
internal class BinderKernelClash(
    private val remote: IClashManager,
) : KernelClash {
    override fun queryTunnelMode(): KernelTunnelMode = remote.queryTunnelState().mode.toKernelMode()

    override fun coreVersion(): String = remote.coreVersion()

    override fun queryProxyGroupNames(excludeNotSelectable: Boolean): List<String> = remote.queryProxyGroupNames(excludeNotSelectable)

    override fun queryProxyGroup(name: String, sort: KernelProxySort): KernelProxyGroupSnapshot = remote.queryProxyGroup(
        name,
        when (sort) {
            KernelProxySort.DEFAULT -> ProxySort.Default
            KernelProxySort.DELAY -> ProxySort.Delay
        },
    ).let { group ->
        KernelProxyGroupSnapshot(
            type = group.type,
            proxies = group.proxies.map { KernelProxySnapshot(it.name, it.isGroup, it.delay, it.measured) },
            now = group.now,
        )
    }

    override suspend fun loadActiveProfile() = remote.loadActiveProfile()

    override fun patchSelector(group: String, name: String): Boolean = remote.patchSelector(group, name)

    override fun urlTest(name: String, timeoutMs: Int): KernelUrlTestResult = KernelUrlTestResult(remote.urlTest(name, timeoutMs).delay)

    override suspend fun healthCheck(group: String) = remote.healthCheck(group)

    override fun healthCheckAll() = remote.healthCheckAll()

    override fun queryPersistedProxyMode(): KernelTunnelMode? = remote.queryOverride(OverrideSlot.Persist).mode?.toKernelMode()

    override fun setPersistedProxyMode(mode: KernelTunnelMode) {
        val override = remote.queryOverride(OverrideSlot.Persist)
        override.mode = mode.toClashMode()
        remote.patchOverride(OverrideSlot.Persist, override)
    }

    override fun tunStackMode(): String = remote.tunStackMode()

    override fun setTunStackMode(mode: String) = remote.setTunStackMode(mode)
}

private fun TunnelState.Mode.toKernelMode(): KernelTunnelMode = when (this) {
    TunnelState.Mode.Global -> KernelTunnelMode.GLOBAL
    TunnelState.Mode.Rule -> KernelTunnelMode.RULE
    TunnelState.Mode.Direct -> KernelTunnelMode.DIRECT
    TunnelState.Mode.Script -> KernelTunnelMode.SCRIPT
}

private fun KernelTunnelMode.toClashMode(): TunnelState.Mode = when (this) {
    KernelTunnelMode.GLOBAL -> TunnelState.Mode.Global
    KernelTunnelMode.RULE -> TunnelState.Mode.Rule
    KernelTunnelMode.DIRECT -> TunnelState.Mode.Direct
    KernelTunnelMode.SCRIPT -> TunnelState.Mode.Script
}

/** IProfileManager → KernelProfiles 适配。 */
internal class BinderKernelProfiles(
    private val remote: IProfileManager,
) : KernelProfiles {
    override suspend fun create(type: KernelProfileType, name: String, source: String): UUID = remote.create(
        when (type) {
            KernelProfileType.URL -> Profile.Type.Url
        },
        name,
        source,
    )

    override suspend fun commit(uuid: UUID) = remote.commit(uuid)

    override suspend fun delete(uuid: UUID) = remote.delete(uuid)

    override suspend fun queryByUUID(uuid: UUID): KernelProfile? = remote.queryByUUID(uuid)?.toKernelProfile()

    override suspend fun queryAll(): List<KernelProfile> = remote.queryAll().map { it.toKernelProfile() }

    override suspend fun queryActive(): KernelProfile? = remote.queryActive()?.toKernelProfile()

    override suspend fun setActive(uuid: UUID) {
        remote.queryByUUID(uuid)?.let { profile -> remote.setActive(profile) }
    }

    private fun Profile.toKernelProfile() = KernelProfile(uuid, name, source, imported)
}
