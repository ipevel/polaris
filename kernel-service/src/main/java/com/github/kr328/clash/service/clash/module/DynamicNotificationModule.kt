package com.github.kr328.clash.service.clash.module

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.compat.getColorCompat
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.constants.Components
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.util.trafficDownload
import com.github.kr328.clash.core.util.trafficUpload
import com.github.kr328.clash.service.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import java.util.concurrent.TimeUnit

class DynamicNotificationModule(service: Service) : Module<Unit>(service) {
    private val builder = NotificationCompat.Builder(service, StaticNotificationModule.CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_logo_service)
        .setOngoing(true)
        .setColor(service.getColorCompat(R.color.color_clash))
        .setOnlyAlertOnce(true)
        .setShowWhen(false)
        .setContentTitle(notificationTitle(service))
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(
            PendingIntent.getActivity(
                service,
                R.id.nf_clash_status,
                Intent().setComponent(Components.MAIN_ACTIVITY)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
            )
        )
        .addAction(
            R.drawable.ic_action_stop,
            service.getText(R.string.notification_action_stop),
            PendingIntent.getBroadcast(
                service,
                R.id.nf_clash_stop,
                Intent(Intents.ACTION_CLASH_REQUEST_STOP).setPackage(service.packageName),
                pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT),
            ),
        )

    private val notificationManager = NotificationManagerCompat.from(service)

    // 仅当配置确实装载完成（收到 PROFILE_LOADED）后才展示上行/下行速率，
    // 在此之前只显示"载入中"，避免"界面/通知在刷速率但配置尚未装载"的假代理信号。
    private var profileLoaded = false

    private fun update() {
        val notification =
            if (profileLoaded) {
                val now = Clash.queryTrafficNow()

                val uploading = now.trafficUpload()
                val downloading = now.trafficDownload()

                builder
                    .setContentText(
                        service.getString(
                            R.string.clash_notification_content,
                            "$uploading/s", "$downloading/s"
                        )
                    )
                    // 明确表达"正在代理"：配置确已装载后才显示该标签
                    .setSubText(service.getText(R.string.notification_proxying))
            } else {
                builder.setContentText(service.getText(R.string.loading))
            }.build()

        notificationManager.notify(R.id.nf_clash_status, notification)
    }

    override suspend fun run() = coroutineScope {
        var shouldUpdate = service.getSystemService<PowerManager>()?.isInteractive ?: true

        val screenToggle = receiveBroadcast(false, Channel.CONFLATED) {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }

        val profileLoadedSignal = receiveBroadcast(Channel.CONFLATED) {
            addAction(Intents.ACTION_PROFILE_LOADED)
        }

        val ticker = ticker(TimeUnit.SECONDS.toMillis(1))

        while (true) {
            select<Unit> {
                screenToggle.onReceive {
                    when (it.action) {
                        Intent.ACTION_SCREEN_ON ->
                            shouldUpdate = true
                        Intent.ACTION_SCREEN_OFF ->
                            shouldUpdate = false
                    }
                }
                profileLoadedSignal.onReceive {
                    profileLoaded = true
                    // 配置就绪即刷新一次，让"正在代理"标识尽快出现在通知栏
                    update()
                }
                if (shouldUpdate) {
                    ticker.onReceive {
                        update()
                    }
                }
            }
        }
    }
}
