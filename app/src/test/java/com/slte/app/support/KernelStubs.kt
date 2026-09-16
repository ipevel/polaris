package com.slte.app.support

import com.slte.app.kernel.KernelManager
import com.slte.app.kernel.KernelProxy
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk

fun KernelProxy.stubKernelBridge(): KernelProxy {
    val inner = mockk<KernelManager>(relaxed = true)
    every { manager } returns inner
    every { inner.clash() } returns null
    coEvery { safe<Any?>(any(), any(), any(), any()) } coAnswers {
        try {
            arg<suspend () -> Any?>(3).invoke()
        } catch (e: Exception) {
            firstArg<Any?>()
        }
    }
    return this
}
