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
import com.slte.app.utils.AppLog
import com.slte.app.utils.sanitizeLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class KernelManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
) {

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var remote: IRemoteService? = null

    @Volatile
    private var bound = false
    private var receiverRegistered = false
    private var rebindJob: Job? = null
    private var rebindAttempts = 0

    @Volatile
    private var syncSeq = 0

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _profileLoaded = MutableStateFlow(0)

    val profileLoaded: StateFlow<Int> = _profileLoaded.asStateFlow()

    private val kernelLogObserver =
        object : ILogObserver {
            override fun newItem(log: LogMessage) {
                AppLog.kernel(log)
            }
        }

    private val connection: ServiceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder,
            ) {
                AppLog.d("SLTE-Kernel", "onServiceConnected: $name")

                bound = true
                rebindJob?.cancel()
                rebindJob = null
                rebindAttempts = 0
                remote = service.unwrap(IRemoteService::class)

                scope.launch {
                    runCatching { remote?.clash()?.setLogObserver(kernelLogObserver) }
                        .onFailure { AppLog.w("SLTE-Kernel", "setLogObserver failed: ${sanitizeLog(it.message ?: "Unknown")}") }
                }
                syncConnectedState()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                handleBindingLost("onServiceDisconnected: $name")
            }

            override fun onBindingDied(name: ComponentName?) {
                handleBindingLost("onBindingDied: $name")
            }

            override fun onNullBinding(name: ComponentName?) {
                handleBindingLost("onNullBinding: $name")
            }
        }

    private fun handleBindingLost(reason: String) {
        AppLog.w("SLTE-Kernel", "$reason → 复位绑定状态并进入退避重绑")
        remote = null
        bound = false
        rebindAttempts = 0
        _connected.value = false
        unbindQuietly()
        scheduleRebind()
    }

    private fun unbindQuietly() {
        try {
            context.unbindService(connection)
        } catch (e: IllegalArgumentException) {
            AppLog.d("SLTE-Kernel", "unbindService: ${sanitizeLog(e.message ?: "Unknown")}")
        }
    }

    private val statusReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                when (intent?.action) {
                    Intents.ACTION_CLASH_STARTED -> _connected.value = true
                    Intents.ACTION_CLASH_STOPPED -> _connected.value = false
                    Intents.ACTION_PROFILE_LOADED -> _profileLoaded.value += 1
                }
            }
        }

    fun bind() {
        mainScope.launch { bindLocked() }
    }

    private fun bindLocked() {
        if (bound) return
        if (doBind()) {
            bound = true
            rebindAttempts = 0
            registerStatusReceiver()
            syncConnectedState()
        } else {
            scheduleRebind()
        }
    }

    private fun doBind(): Boolean = try {
        context.bindService(
            Intent(context, RemoteService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
    } catch (e: Exception) {
        AppLog.w("SLTE-Kernel", "bindService failed: ${sanitizeLog(e.message ?: "Unknown")}")
        false
    }

    private fun registerStatusReceiver() {
        if (receiverRegistered) return
        receiverRegistered = true
        ContextCompat.registerReceiver(
            context,
            statusReceiver,
            IntentFilter().apply {
                addAction(Intents.ACTION_CLASH_STARTED)
                addAction(Intents.ACTION_CLASH_STOPPED)
                addAction(Intents.ACTION_PROFILE_LOADED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun scheduleRebind() {
        if (bound || rebindJob?.isActive == true) return
        rebindJob =
            mainScope.launch {
                while (!bound && remote == null && rebindAttempts < MAX_REBIND_ATTEMPTS) {
                    val backoffMs = minOf(INITIAL_REBIND_DELAY_MS shl rebindAttempts, MAX_REBIND_DELAY_MS)
                    AppLog.d("SLTE-Kernel", "rebind #$rebindAttempts in ${backoffMs}ms")
                    delay(backoffMs)

                    if (bound || remote != null) return@launch
                    rebindAttempts++
                    if (doBind()) {
                        bound = true
                        registerStatusReceiver()

                        return@launch
                    }
                }
                if (!bound && remote == null) {
                    AppLog.w("SLTE-Kernel", "rebind 已重试 $MAX_REBIND_ATTEMPTS 次仍失败，等待下次按需 bind()")
                }
            }
    }

    private fun ensureBound() {
        if (!bound) bind()
    }

    private fun syncConnectedState() {
        val seq = ++syncSeq
        scope.launch {
            val result =
                try {
                    context.contentResolver.call(
                        Uri
                            .Builder()
                            .scheme("content")
                            .authority(Authorities.STATUS_PROVIDER)
                            .build(),
                        StatusProvider.METHOD_CURRENT_PROFILE,
                        null,
                        null,
                    ) != null
                } catch (e: Exception) {
                    AppLog.w("SLTE-Kernel", "syncConnectedState 查询失败: ${sanitizeLog(e.message ?: "Unknown")}")
                    false
                }

            if (seq == syncSeq) _connected.value = result
        }
    }

    fun vpnRequestIntent(): Intent? = VpnService.prepare(context)

    fun startVpn() {
        AppLog.i("SLTE-Kernel", "startVpn: 请求启动 TUN")
        context.startForegroundService(Intent(context, TunService::class.java))
    }

    fun stopVpn() {
        AppLog.i("SLTE-Kernel", "stopVpn: 请求停止 TUN")
        context.sendBroadcastSelf(Intent(Intents.ACTION_CLASH_REQUEST_STOP))
    }

    internal fun clash(): IClashManager? {
        ensureBound()
        return remote?.clash()
    }

    internal fun profile(): IProfileManager? {
        ensureBound()
        return remote?.profile()
    }

    companion object {

        private const val INITIAL_REBIND_DELAY_MS = 1_000L

        private const val MAX_REBIND_DELAY_MS = 30_000L

        private const val MAX_REBIND_ATTEMPTS = 5
    }
}
