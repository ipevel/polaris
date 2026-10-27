package com.slte.app.kernel.factory

import android.content.Context
import androidx.core.content.edit
import com.github.kr328.clash.common.constants.Authorities
import com.slte.app.BuildConfig
import com.slte.app.kernel.api.KernelApi
import com.slte.app.kernel.api.KernelType
import com.slte.app.kernel.binder.MihomoBinderKernelApi
import com.slte.app.kernel.mihomo.MihomoSmartKernelApi
import com.slte.app.utils.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import rikka.preference.MultiProcessPreference

/** 唯一构造点：读编译期集合，恢复持久化选择，创建对应实现。 */
@Singleton
class DefaultKernelFactory
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val mihomoProvider: Provider<MihomoBinderKernelApi>,
    private val smartProvider: Provider<MihomoSmartKernelApi>,
) : KernelFactory {
    // 与服务进程共用 PreferenceProvider 存储（跨进程同步），不能各自用普通 SharedPreferences
    private val prefs = MultiProcessPreference(context, Authorities.SETTINGS_PROVIDER)

    private val available: List<KernelType> = parseAvailable()

    @Volatile
    private var cached: KernelApi? = null

    override fun availableKernels(): List<KernelType> = available

    override fun current(): KernelApi = cached ?: synchronized(this) {
        cached ?: create(restoredType()).also { cached = it }
    }

    override fun create(type: KernelType): KernelApi = when (type) {
        KernelType.MIHOMO -> mihomoProvider.get()
        KernelType.MIHOMO_SMART -> smartProvider.get()
    }

    override suspend fun switchTo(type: KernelType) {
        require(type in available) { "kernel ${type.storageKey} is not available in this build" }
        // 双写兜底：provider 同步到服务进程，本地 SP 防 provider 老化
        // 先同步写本地 SP：进程随后会被杀掉，provider 写入可能来不及落盘
        context.getSharedPreferences(SERVICE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_KERNEL_TYPE, type.storageKey)
            .commit()
        runCatching { prefs.edit().putString(KEY_KERNEL_TYPE, type.storageKey).commit() }
        cached = null
        AppLog.i("SLTE-Kernel", "kernel switched to ${type.storageKey}; VPN restart required")
    }

    private fun restoredType(): KernelType {
        val local = context.getSharedPreferences(SERVICE_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_KERNEL_TYPE, null)
        val saved = KernelType.fromStorageKey(local ?: prefs.getString(KEY_KERNEL_TYPE, "mihomo"))
        return saved?.takeIf { it in available } ?: available.first()
    }

    private fun parseAvailable(): List<KernelType> {
        val configured =
            BuildConfig.AVAILABLE_KERNELS
                .split(',')
                .mapNotNull { KernelType.fromStorageKey(it.trim().lowercase()) }
                .distinct()
        // 空/非法配置回退 mihomo，避免只装入不支持的枚举导致启动崩溃
        return configured.ifEmpty { listOf(KernelType.MIHOMO) }
    }

    private companion object {
        const val KEY_KERNEL_TYPE = "kernel_type"
        const val SERVICE_PREFS = "service"
    }
}
