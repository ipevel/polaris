package com.slte.app.support

import com.github.kr328.clash.service.remote.IClashManager
import com.slte.app.kernel.KernelManager
import com.slte.app.kernel.KernelProxy
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk

/**
 * 桩化 KernelProxy 的内核桥接。
 *
 * @param ready true 时表示内核已就绪（clash() 非空且策略组可读），
 *   用于覆盖 MainViewModel 的连接就绪门控（awaitTunnelReady）通过路径；
 *   false 时 clash() 返回 null，代表内核尚未就绪。
 */
fun KernelProxy.stubKernelBridge(ready: Boolean = false): KernelProxy {
    val inner = mockk<KernelManager>(relaxed = true)
    every { manager } returns inner
    if (ready) {
        val clash = mockk<IClashManager>(relaxed = true)
        every { clash.queryProxyGroupNames(any()) } returns listOf("PROXY")
        every { inner.clash() } returns clash
    } else {
        every { inner.clash() } returns null
    }
    coEvery { safe<Any?>(any(), any(), any(), any()) } coAnswers {
        try {
            arg<suspend () -> Any?>(3).invoke()
        } catch (e: Exception) {
            firstArg<Any?>()
        }
    }
    return this
}
