package com.slte.app.kernel.binder

import android.content.Context
import com.slte.app.kernel.api.KernelType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** mihomo 实现：共享 Binder 门面，仅声明内核类型。 */
@Singleton
class MihomoBinderKernelApi
@Inject
constructor(
    @ApplicationContext context: Context,
) : BinderKernelBase(context) {
    override val type: KernelType = KernelType.MIHOMO
}
