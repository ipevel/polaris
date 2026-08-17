package com.slte.app.kernel

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.net.Uri
import android.net.VpnService
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.github.kr328.clash.common.constants.Authorities
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.service.RemoteService
import com.github.kr328.clash.service.StatusProvider
import com.github.kr328.clash.service.TunService
import com.github.kr328.clash.service.remote.IClashManager
import com.github.kr328.clash.service.remote.ILogObserver
import com.github.kr328.clash.service.remote.IProfileManager
import com.github.kr328.clash.service.remote.IRemoteService
import com.github.kr328.clash.service.remote.unwrap
import com.github.kr328.clash.service.util.sendBroadcastSelf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog

/** 内核生命周期管理：绑定后台服务、连接状态、VPN 启停 */
@Singleton
class KernelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var remote: IRemoteService? = null
    private var bound = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _profileLoaded = MutableStateFlow(0)
    /** 内核配置每次（重新）加载完成时 +1，用于驱动 UI 重新同步 */
    val profileLoaded: StateFlow<Int> = _profileLoaded.asStateFlow()

    /** 内核（mihomo）日志观察者：桥接到 AppLog 缓冲区，供日志导出排查问题 */
    private val kernelLogObserver = object : ILogObserver {
        override fun newItem(log: LogMessage) {
            AppLog.kernel(log)
        }
    }

    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder) {
            AppLog.d("SLTE-Kernel", "onServiceConnected: $name")
            remote = service.unwrap(IRemoteService::class)
            // 订阅内核日志（mihomo），失败不影响连接
            scope.launch {
                runCatching { remote?.clash()?.setLogObserver(kernelLogObserver) }
                    .onFailure { AppLog.w("SLTE-Kernel", "setLogObserver failed: ${sanitizeLog(it.message ?: "Unknown")}") }
            }
            syncConnectedState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            AppLog.d("SLTE-Kernel", "onServiceDisconnected: $name")
            remote = null
            _connected.value = false
            // 延迟后尝试重绑
            scope.launch {
                delay(1000)
                if (remote != null) return@launch
                try {
                    context.bindService(
                        Intent(context, RemoteService::class.java),
                        connection,
                        Context.BIND_AUTO_CREATE
                    )
                } catch (e: Exception) {
                    AppLog.w("SLTE-Kernel", "rebind failed: ${sanitizeLog(e.message ?: "")}")
                }
            }
        }
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intents.ACTION_CLASH_STARTED -> _connected.value = true
                Intents.ACTION_CLASH_STOPPED -> _connected.value = false
                Intents.ACTION_PROFILE_LOADED -> _profileLoaded.value += 1
            }
        }
    }

    /** 绑定后台内核服务并监听连接状态广播（应用主进程调用一次即可） */
    fun bind() {
        if (bound) return
        bound = true

        context.bindService(
            Intent(context, RemoteService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
        ContextCompat.registerReceiver(
            context,
            statusReceiver,
            IntentFilter().apply {
                addAction(Intents.ACTION_CLASH_STARTED)
                addAction(Intents.ACTION_CLASH_STOPPED)
                addAction(Intents.ACTION_PROFILE_LOADED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // 启动时主动查询一次真实状态
        syncConnectedState()
    }

    /** 查询后台 StatusProvider：内核服务正在运行且已加载配置时返回 true */
    private fun syncConnectedState() {
        scope.launch {
            _connected.value = try {
                context.contentResolver.call(
                    Uri.Builder()
                        .scheme("content")
                        .authority(Authorities.STATUS_PROVIDER)
                        .build(),
                    StatusProvider.METHOD_CURRENT_PROFILE,
                    null,
                    null
                ) != null
            } catch (e: Exception) {
                AppLog.w("SLTE-Kernel", "syncConnectedState 查询失败: ${sanitizeLog(e.message ?: "Unknown")}")
                false
            }
        }
    }

    /** 需要先弹 VPN 授权时返回授权 Intent，否则返回 null */
    fun vpnRequestIntent(): Intent? = VpnService.prepare(context)

    /** 启动 TUN 模式内核服务（需先通过 VPN 授权） */
    fun startVpn() {
        AppLog.i("SLTE-Kernel", "startVpn: 请求启动 TUN")
        context.startForegroundService(Intent(context, TunService::class.java))
    }

    fun stopVpn() {
        AppLog.i("SLTE-Kernel", "stopVpn: 请求停止 TUN")
        context.sendBroadcastSelf(Intent(Intents.ACTION_CLASH_REQUEST_STOP))
    }

    internal fun clash(): IClashManager? = remote?.clash()

    internal fun profile(): IProfileManager? = remote?.profile()
}
