package com.github.kr328.clash.service.clash.module

import android.app.Service
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.kr328.clash.common.compat.getColorCompat
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.compat.startForegroundCompat
import com.github.kr328.clash.common.constants.Components
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.service.BuildConfig
import com.github.kr328.clash.service.R
import kotlinx.coroutines.channels.Channel

/** 通知标题：环境变量优先，否则跟随应用名 */
internal fun notificationTitle(service: Service): String {
    val custom = BuildConfig.NOTIFICATION_TITLE
    return if (custom.isNotBlank()) custom
    else service.packageManager.getApplicationLabel(service.applicationInfo).toString()
}

class StaticNotificationModule(service: Service) : Module<Unit>(service) {
    private val builder = NotificationCompat.Builder(service, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_logo_service)
        .setOngoing(true)
        .setColor(service.getColorCompat(R.color.color_clash))
        .setOnlyAlertOnce(true)
        .setShowWhen(false)
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

    override suspend fun run() {
        val loaded = receiveBroadcast(capacity = Channel.CONFLATED) {
            addAction(Intents.ACTION_PROFILE_LOADED)
        }

        while (true) {
            loaded.receive()

            // 标题固定应用名（环境变量可覆盖），不暴露内核配置名
            val notification = builder
                .setContentTitle(notificationTitle(service))
                .setContentText(service.getText(R.string.running))
                .build()

            service.startForegroundCompat(R.id.nf_clash_status, notification)
        }
    }

    companion object {
        const val CHANNEL_ID = "clash_status_channel"

        fun createNotificationChannel(service: Service) {
            NotificationManagerCompat.from(service).createNotificationChannel(
                NotificationChannelCompat.Builder(
                    CHANNEL_ID,
                    NotificationManagerCompat.IMPORTANCE_LOW
                ).setName(service.getText(R.string.clash_service_status_channel)).build()
            )
        }

        fun notifyLoadingNotification(service: Service) {
            val notification =
                NotificationCompat.Builder(service, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_logo_service)
                    .setOngoing(true)
                    .setColor(service.getColorCompat(R.color.color_clash))
                    .setOnlyAlertOnce(true)
                    .setShowWhen(false)
                    .setContentTitle(notificationTitle(service))
                    .build()

            service.startForegroundCompat(R.id.nf_clash_status, notification)
        }
    }
}