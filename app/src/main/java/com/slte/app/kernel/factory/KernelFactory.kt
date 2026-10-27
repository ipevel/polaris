package com.slte.app.kernel.factory

import com.slte.app.kernel.api.KernelApi
import com.slte.app.kernel.api.KernelType

interface KernelFactory {
    fun availableKernels(): List<KernelType>

    fun current(): KernelApi

    fun create(type: KernelType): KernelApi

    /** 阶段 1 只做同类型重建；跨类型切换在阶段 3 落地。 */
    suspend fun switchTo(type: KernelType)
}
