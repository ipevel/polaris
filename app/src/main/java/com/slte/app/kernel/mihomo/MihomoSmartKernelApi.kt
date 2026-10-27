package com.slte.app.kernel.mihomo

import android.content.Context
import com.slte.app.kernel.api.KernelType
import com.slte.app.kernel.binder.BinderKernelBase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 智能内核：独立二进制（libclash_smart.so），配置层注入 smart 分组。 */
@Singleton
class MihomoSmartKernelApi
@Inject
constructor(
    @ApplicationContext context: Context,
) : BinderKernelBase(context) {
    override val type: KernelType = KernelType.MIHOMO_SMART
}
